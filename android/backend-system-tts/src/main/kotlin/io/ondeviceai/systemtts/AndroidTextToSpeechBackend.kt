package io.ondeviceai.systemtts

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.ClosedException
import io.ondeviceai.core.UnavailableReason
import io.ondeviceai.speech.SpeechCapability
import io.ondeviceai.speech.SpeechSynthesisBackend
import io.ondeviceai.speech.SpeechSynthesisRequest
import io.ondeviceai.speech.SpeechSynthesisResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class SystemTtsError(message: String) : IllegalStateException(message) {
    data object Busy : SystemTtsError("tts backend busy")
    data class MissingVoice(val locale: String) : SystemTtsError("no installed non-network voice for $locale")
    data class EngineFailure(val code: Int) : SystemTtsError("tts engine failed: $code")
}

/** Owns one Android TextToSpeech engine. All mutable state is confined to the main looper. */
class AndroidTextToSpeechBackend private constructor(private val engine: TextToSpeech) : SpeechSynthesisBackend {
    override val backendId = "android.text-to-speech"
    override val capability = SpeechCapability.TTS
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private var active: Active? = null

    private data class Active(
        val id: String,
        val continuation: kotlinx.coroutines.CancellableContinuation<SpeechSynthesisResult>,
        val voiceId: String
    )

    init {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) { main.post { finish(utteranceId, null) } }
            override fun onError(utteranceId: String) { main.post { finish(utteranceId, SystemTtsError.EngineFailure(TextToSpeech.ERROR)) } }
            override fun onError(utteranceId: String, errorCode: Int) { main.post { finish(utteranceId, SystemTtsError.EngineFailure(errorCode)) } }
            override fun onStop(utteranceId: String, interrupted: Boolean) { main.post { finish(utteranceId, kotlinx.coroutines.CancellationException("tts stopped")) } }
        })
    }

    override suspend fun availability(): CapabilityAvailability = withContext(Dispatchers.Main.immediate) {
        val ready = !closed && engine.voices.orEmpty().any { !it.isNetworkConnectionRequired }
        CapabilityAvailability(if (ready) CapabilityStatus.AVAILABLE else CapabilityStatus.UNAVAILABLE,
            if (closed) UnavailableReason.CLOSED else if (ready) null else UnavailableReason.MISSING_ASSETS,
            backendId, minOS = "26", offline = true)
    }

    override suspend fun speak(request: SpeechSynthesisRequest): SpeechSynthesisResult = withContext(Dispatchers.Main.immediate) {
        if (closed) throw ClosedException()
        if (active != null) throw SystemTtsError.Busy
        require(request.text.isNotEmpty()) { "empty text" }
        require(request.text.length <= TextToSpeech.getMaxSpeechInputLength()) { "text exceeds system TTS limit" }
        val voice = chooseVoice(request) ?: throw SystemTtsError.MissingVoice(request.locale)
        val voiceStatus = engine.setVoice(voice)
        if (voiceStatus != TextToSpeech.SUCCESS) throw SystemTtsError.EngineFailure(voiceStatus)
        val id = UUID.randomUUID().toString()
        suspendCancellableCoroutine { continuation ->
            active = Active(id, continuation, voice.name)
            continuation.invokeOnCancellation {
                main.post {
                    if (active?.id == id) {
                        active = null
                        engine.stop()
                    }
                }
            }
            val queued = engine.speak(request.text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
            if (queued != TextToSpeech.SUCCESS) finish(id, SystemTtsError.EngineFailure(queued))
        }
    }

    override suspend fun close() = withContext(Dispatchers.Main.immediate) {
        if (closed) return@withContext
        closed = true
        active?.let { current ->
            active = null
            if (current.continuation.isActive) current.continuation.resumeWithException(ClosedException())
        }
        engine.stop()
        engine.shutdown()
    }

    private fun chooseVoice(request: SpeechSynthesisRequest): Voice? = engine.voices.orEmpty()
        .asSequence()
        .filter { !it.isNetworkConnectionRequired && it.locale.toLanguageTag() == Locale.forLanguageTag(request.locale).toLanguageTag() }
        .filter { request.voiceId == null || it.name == request.voiceId }
        .sortedBy { it.name }
        .firstOrNull()

    private fun finish(id: String, error: Throwable?) {
        val current = active?.takeIf { it.id == id } ?: return
        active = null
        if (!current.continuation.isActive) return
        if (error == null) current.continuation.resume(SpeechSynthesisResult(backendId, current.voiceId))
        else current.continuation.resumeWithException(error)
    }

    companion object {
        suspend fun create(context: Context): AndroidTextToSpeechBackend = withContext(Dispatchers.Main.immediate) {
            val initialized = CompletableDeferred<Int>()
            val engine = TextToSpeech(context.applicationContext) { status -> initialized.complete(status) }
            try {
                val status = withTimeout(15_000) { initialized.await() }
                if (status != TextToSpeech.SUCCESS) throw SystemTtsError.EngineFailure(status)
                AndroidTextToSpeechBackend(engine)
            } catch (error: Throwable) {
                engine.shutdown()
                throw error
            }
        }
    }
}
