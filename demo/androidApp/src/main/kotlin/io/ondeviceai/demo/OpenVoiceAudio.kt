package io.ondeviceai.demo

import android.content.Context
import io.ondeviceai.binding.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

internal object OpenVoiceAudio {
    init { System.loadLibrary("openvoice_demo") }
    private external fun render(assets: String, input: String, voice: String, output: String): Int

    // Serialize cache publication and limit ONNX working memory across demo hosts.
    @Synchronized
    fun renderTo(context: Context, input: File, key: String, output: File) {
        require(key in setOf("voiceA", "voiceB", "voiceC"))
        val assets = File(context.cacheDir, "OpenVoice-Demo-v1")
        if (!assets.isDirectory) {
            val stage = File(context.cacheDir, "OpenVoice-${UUID.randomUUID()}")
            check(stage.mkdir())
            try {
                for (name in listOf("converter.onnx", "ref_encoder.onnx", "voiceA.f32", "voiceB.f32", "voiceC.f32")) {
                    context.assets.open("OpenVoice/$name").use { source ->
                        File(stage, name).outputStream().use(source::copyTo)
                    }
                }
                check(stage.renameTo(assets))
            } finally { stage.deleteRecursively() }
        }
        check(render(assets.path, input.path, key, output.path) == 0)
    }
}

internal class AndroidOpenVoiceTimbres(
    private val context: Context,
    private val inputs: AndroidVoiceEffectsInputProvider,
    private val scope: CoroutineScope,
) : DemoVoiceTimbres {
    private val outputs = mutableMapOf<String, File>()

    override fun render(inputReference: String, key: String, callback: VoiceEffectsBridgeProcessCallback) {
        val input = try { inputs.acquire(inputReference) } catch (_: Exception) {
            callback.onFailure(failure("无法读取当前音频"))
            return
        }
        val id = UUID.randomUUID().toString()
        val output = File(context.cacheDir, "$key-$id.wav")
        scope.launch {
            val frames = try {
                withContext(Dispatchers.IO) {
                    OpenVoiceAudio.renderTo(context, input.file, key, output)
                    // The native writer emits a 44-byte header and mono PCM16 at 22050 Hz.
                    (output.length() - 44) / 2
                }
            } catch (_: Exception) {
                output.delete()
                null
            } finally { input.release() }
            if (frames == null) {
                callback.onFailure(failure("音色转换失败，请使用 0.05 至 30 秒的单声道语音。"))
            } else {
                outputs[id] = output
                callback.onResult(VoiceEffectsBridgeOutput(id, "openvoice.onnx", 22050, frames, frames / 22050.0))
            }
        }
    }

    fun outputFile(reference: String) = outputs[reference]
    override fun ownsOutput(reference: String) = reference in outputs
    override fun releaseOutput(reference: String, callback: VoiceEffectsBridgeReleaseCallback) {
        val file = outputs[reference]
        if (file != null && !file.delete() && file.exists()) {
            callback.onFailure(failure("无法清理音色文件"))
            return
        }
        outputs.remove(reference)
        callback.onReleased()
    }
    private fun failure(reason: String) = VoiceEffectsBridgeFailure(VoiceEffectsBridgeFailureCode.INVALID_INPUT, reason)
}
