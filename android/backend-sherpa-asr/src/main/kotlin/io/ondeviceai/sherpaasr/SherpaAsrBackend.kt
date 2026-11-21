package io.ondeviceai.sherpaasr

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.*
import io.ondeviceai.core.*
import io.ondeviceai.speech.*
import kotlinx.coroutines.*

/** Optional CPU backend. No network access, implicit model download or audio persistence. */
class SherpaAsrBackend(context: Context, private val model: SherpaWhisperModel) : SpeechRecognitionBackend {
    private val app = context.applicationContext
    private val session = SherpaSession({ NativeWhisper(model) })
    override val backendId: String = SherpaSession.BACKEND_ID
    override val capability = SpeechCapability.ASR

    override suspend fun availability(): CapabilityAvailability = withContext(Dispatchers.IO) {
        val reason = when {
            session.isClosed() -> UnavailableReason.CLOSED
            !model.isPresent() -> UnavailableReason.MISSING_ASSETS
            app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED -> UnavailableReason.PERMISSION_DENIED
            else -> null
        }
        CapabilityAvailability(if (reason == null) CapabilityStatus.AVAILABLE else CapabilityStatus.UNAVAILABLE,
            reason = reason, backendId = backendId, minOS = "26", offline = true)
    }

    override suspend fun recognize(request: SpeechRecognitionRequest): SpeechRecognitionResult {
        SherpaSession.language(request.locale)
        val status = availability()
        status.reason?.let { throw SpeechBackendUnavailableException(it) }
        return session.transcribe(request.locale) {
            model.verify()
            currentCoroutineContext().ensureActive()
            capture()
        }
    }

    /** Caller-provided mono16kHz audio, at most10seconds; caller retains ownership. */
    suspend fun transcribe(samples: FloatArray, locale: String): SpeechRecognitionResult =
        session.transcribe(locale) { model.verify(); WhisperSpeechGate.prepare(model, samples) }

    override suspend fun close() = session.close()

    @SuppressLint("MissingPermission") // Permission is checked before session admission.
    private suspend fun capture(): FloatArray {
        val minimum = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "unsupported microphone format" }
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16_000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 6400))
        val chunk = ShortArray(1600)
        var speechGate: WhisperSpeechGate? = null
        var noiseSuppressor: NoiseSuppressor? = null
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "microphone initialization failed" }
            val utterance = WhisperSpeechGate(model).also { speechGate = it }.buffer
            // Best effort per-session preprocessing. Some devices already enable it by default.
            noiseSuppressor = createNoiseSuppressor(recorder.audioSessionId)
            currentCoroutineContext().ensureActive()
            recorder.startRecording()
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (SystemClock.elapsedRealtime() < deadline) {
                currentCoroutineContext().ensureActive()
                val count = recorder.read(chunk, 0, chunk.size, AudioRecord.READ_NON_BLOCKING)
                check(count >= 0) { "microphone read failed" }
                if (count > 0 && utterance.append(chunk, count)) break
                delay(20)
            }
            return utterance.samples()
        } finally {
            try { if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() }
            finally {
                try { noiseSuppressor?.release() }
                finally {
                    try { recorder.release() }
                    finally { chunk.fill(0); speechGate?.close() }
                }
            }
        }
    }

    private fun createNoiseSuppressor(sessionId: Int): NoiseSuppressor? {
        val effect = runCatching {
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(sessionId) else null
        }.getOrNull() ?: return null
        return if (runCatching { effect.enabled || effect.setEnabled(true) == AudioEffect.SUCCESS }.getOrDefault(false)) {
            effect
        } else {
            effect.release()
            null
        }
    }

    private class NativeWhisper(model: SherpaWhisperModel) : SherpaDecoder {
        private val recognizer = OfflineRecognizer(config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = model.directory.resolve("tiny-encoder.int8.onnx").absolutePath,
                    decoder = model.directory.resolve("tiny-decoder.int8.onnx").absolutePath,
                    language = "en", task = "transcribe"),
                tokens = model.directory.resolve("tiny-tokens.txt").absolutePath,
                numThreads = 4, debug = false, provider = "cpu")))
        override fun decode(samples: FloatArray, language: String): String {
            val config = recognizer.config.copy(modelConfig = recognizer.config.modelConfig.copy(
                whisper = recognizer.config.modelConfig.whisper.copy(language = language)))
            recognizer.setConfig(config)
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(samples, 16_000)
                recognizer.decode(stream)
                return recognizer.getResult(stream).text.trim()
            } finally { stream.release() }
        }
        override fun close() = recognizer.release()
    }
}
