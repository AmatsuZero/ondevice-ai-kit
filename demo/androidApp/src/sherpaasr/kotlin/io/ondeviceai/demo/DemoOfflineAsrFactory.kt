package io.ondeviceai.demo

import android.content.Context
import io.ondeviceai.sherpaasr.SherpaAsrBackend
import io.ondeviceai.sherpaasr.SherpaWhisperModel
import io.ondeviceai.speech.SpeechRecognitionBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object DemoOfflineAsrFactory {
    const val defaultBackendId = "sherpa.onnx.whisper"
    private val modelInstallation = Mutex()
    suspend fun create(context: Context): List<SpeechRecognitionBackend> = withContext(Dispatchers.IO) {
        val model = prepareModel(context)
        listOf(SherpaAsrBackend(context, model))
    }

    suspend fun prepareModel(context: Context): SherpaWhisperModel = withContext(Dispatchers.IO) {
        modelInstallation.withLock {
        val directory = context.noBackupFilesDir.resolve("sherpa-whisper-tiny-65176e2d-vad9e2449e1")
        directory.mkdirs()
        val model = SherpaWhisperModel(directory)
        if (runCatching { model.verify() }.isFailure) {
            for (name in SherpaWhisperModel.files.keys) {
                val temporary = directory.resolve("$name.tmp")
                context.assets.open("sherpa-whisper-tiny/$name").use { source ->
                    temporary.outputStream().use { source.copyTo(it) }
                }
                check(temporary.renameTo(directory.resolve(name))) { "model asset installation failed" }
            }
            model.verify()
        }
        model
        }
    }
}
