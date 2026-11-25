package io.ondeviceai.sherpaasr

import io.ondeviceai.core.BusyException
import io.ondeviceai.core.ClosedException
import io.ondeviceai.speech.SpeechRecognitionResult
import io.ondeviceai.speech.SpeechNotDetectedException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow

internal interface SherpaDecoder : AutoCloseable {
    fun decode(samples: FloatArray, language: String): String
    override fun close()
}

/** Owns one capture/decode at a time. Synchronous JNI is drained before releasing admission. */
internal class SherpaSession(
    private val decoder: () -> SherpaDecoder,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val gate = Any()
    private var closed = false
    private var active: Active? = null
    private data class Active(val job: Job, val done: CompletableDeferred<Unit>)

    private suspend fun <T> admitted(locale: String, action: suspend (String) -> T): T =
        withContext(dispatcher) {
            val language = language(locale)
            val current = Active(currentCoroutineContext().job, CompletableDeferred())
            synchronized(gate) {
                if (closed) throw ClosedException()
                if (active != null) throw BusyException()
                active = current
            }
            try { action(language) }
            finally {
                synchronized(gate) { if (active === current) active = null }
                current.done.complete(Unit)
            }
        }

    suspend fun transcribe(locale: String, samples: suspend () -> FloatArray): SpeechRecognitionResult =
        admitted(locale) { language ->
            val waveform = samples()
            try {
                currentCoroutineContext().ensureActive()
                require(waveform.isNotEmpty() && waveform.size <= 160_000 && waveform.all { it.isFinite() }) {
                    "expected at most ten seconds of finite mono 16kHz samples"
                }
                decoder().use { engine ->
                    currentCoroutineContext().ensureActive()
                    val text = engine.decode(waveform, language)
                    currentCoroutineContext().ensureActive()
                    check(text.isNotBlank()) { "speech recognizer returned no result" }
                    SpeechRecognitionResult(BACKEND_ID, text)
                }
            } finally { waveform.fill(0f) }
        }

    suspend fun transcribeFile(
        locale: String,
        chunks: Flow<FloatArray>,
        verifyModel: () -> Unit,
        speechGate: () -> WhisperSpeechGate,
    ): SpeechRecognitionResult = admitted(locale) { language ->
        currentCoroutineContext().ensureActive()
        verifyModel()
        currentCoroutineContext().ensureActive()

        var engine: SherpaDecoder? = null
        var voiced = false
        val texts = mutableListOf<String>()
        try {
            speechGate().use { gate ->
                chunks.collect { chunk ->
                    try {
                        currentCoroutineContext().ensureActive()
                        require(chunk.isNotEmpty() && chunk.size <= 160_000 && chunk.all { it.isFinite() }) {
                            "expected at most ten seconds of finite mono 16kHz samples"
                        }
                        gate.speechRange(chunk)?.let { range ->
                            voiced = true
                            val trimmed = chunk.copyOfRange(range.first, range.last + 1)
                            try {
                                val currentEngine = engine ?: decoder().also { engine = it }
                                currentCoroutineContext().ensureActive()
                                val text = currentEngine.decode(trimmed, language)
                                currentCoroutineContext().ensureActive()
                                text.trim().takeIf { it.isNotEmpty() }?.let(texts::add)
                            } finally { trimmed.fill(0f) }
                        }
                    } finally { chunk.fill(0f) }
                }
            }
            currentCoroutineContext().ensureActive()
            if (!voiced) throw SpeechNotDetectedException()
            check(texts.isNotEmpty()) { "speech recognizer returned no result" }
            SpeechRecognitionResult(BACKEND_ID, texts.joinToString(" "))
        } finally { engine?.close() }
        }

    suspend fun close() = withContext(NonCancellable) {
        val current = synchronized(gate) { closed = true; active }
        current?.job?.cancel()
        current?.done?.await()
        Unit
    }

    fun isClosed(): Boolean = synchronized(gate) { closed }

    companion object {
        const val BACKEND_ID = "sherpa.onnx.whisper"
        fun language(locale: String): String = when (locale.replace('_', '-').lowercase().split('-').first()) {
            "en" -> "en"
            "zh" -> "zh"
            else -> throw IllegalArgumentException("unsupported Whisper Demo locale")
        }
    }
}
