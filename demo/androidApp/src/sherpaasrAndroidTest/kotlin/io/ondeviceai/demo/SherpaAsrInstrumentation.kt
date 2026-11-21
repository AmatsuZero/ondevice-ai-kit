package io.ondeviceai.demo

import android.app.Instrumentation
import android.content.Intent
import android.media.audiofx.NoiseSuppressor
import android.os.Bundle
import android.os.SystemClock
import io.ondeviceai.sherpaasr.SherpaAsrBackend
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Operator-only fixed synthetic samples. Never opens microphone or saves transcripts. */
class SherpaAsrInstrumentation : Instrumentation() {
    private var includeChinese = false
    private var includeNoise = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        includeChinese = arguments?.getString("includeChinese") == "true"
        includeNoise = arguments?.getString("includeNoise") == "true"
        start()
    }
    override fun onStart() {
        val result = Bundle().apply {
            putString("scope", "sdk-fixed-synthetic-samples")
            putInt("microphoneStarts", 0)
            putBoolean("audioPersisted", false)
            putBoolean("transcriptPersisted", false)
            putBoolean("noiseSuppressorAvailable", NoiseSuppressor.isAvailable())
        }
        try {
            // TECNO freezes background-only instrumentation; no capture starts on launch.
            startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            result.putBoolean("activityStarted", true)
            runBlocking {
                val model = DemoOfflineAsrFactory.prepareModel(targetContext)
                val backend = SherpaAsrBackend(targetContext, model)
                try {
                    val english = "this is an on device speech test"
                    val cases = buildList {
                        add(Case("en-US", "en-US", "english.pcm", english))
                        if (includeChinese) add(Case("zh-CN", "zh-CN", "chinese.pcm", "这是一次离线语音识别测试"))
                        if (includeNoise) {
                            add(Case("silence", "en-US", "silence.pcm", ""))
                            add(Case("noise", "en-US", "noise.pcm", ""))
                            add(Case("speech-snr10", "en-US", "speech-snr10.pcm", english))
                            add(Case("speech-snr0", "en-US", "speech-snr0.pcm", english))
                            add(Case("speech-padded", "en-US", "speech-padded.pcm", english))
                        }
                    }
                    for ((label, locale, name, expected) in cases) {
                        val bytes = context.assets.open(name).use { it.readBytes() }
                        check(bytes.size in 2..320_000 && bytes.size % 2 == 0)
                        val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val samples = FloatArray(input.remaining()) { input.get() / 32768f }
                        try {
                            val started = SystemClock.elapsedRealtime()
                            val value = try { backend.transcribe(samples, locale) }
                            catch (error: Exception) {
                                result.putString("$label.errorClass", error.javaClass.name)
                                result.putBoolean("$label.nonEmptyResult", false)
                                continue
                            }
                            result.putString("backendId", value.backendId)
                            result.putLong("$label.elapsedMillis", SystemClock.elapsedRealtime() - started)
                            result.putBoolean("$label.nonEmptyResult", value.transcript.isNotBlank())
                            val normalized = value.transcript.lowercase().filter { it.isLetterOrDigit() }
                            result.putBoolean("$label.expectedMatched", normalized == expected.filter { it.isLetterOrDigit() })
                            result.putBoolean("$label.containsHan", normalized.any { it in '\u4e00'..'\u9fff' })
                        } finally { samples.fill(0f); bytes.fill(0) }
                    }
                    result.putInt("completedLocales", cases.size)
                    result.putString("status", "transcribed-fixed-samples")
                } finally { backend.close() }
            }
        } catch (error: Exception) {
            result.putString("status", "failed")
            result.putString("errorClass", error.javaClass.name)
        }
        finish(-1, result)
    }
    private data class Case(val label: String, val locale: String, val name: String, val expected: String)
}
