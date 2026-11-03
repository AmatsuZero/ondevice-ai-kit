package io.ondeviceai.demo

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*

internal class LiteRtLmDemoModelImport(
    activity: ComponentActivity,
    private val scope: CoroutineScope,
    private val backend: LiteRtLmDemoBackend,
    private val canImport: () -> Boolean,
    private val onImported: () -> Unit,
) : MultimodalModelSetupDemoActions {
    override val supportsModelSetup = true
    override var modelSetupBusy by mutableStateOf(false)
        private set
    override var modelSetupStatus by mutableStateOf<String?>(null)
        private set
    private var closed = false
    private var job: Job? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (closed) return@registerForActivityResult
        if (uri == null) {
            modelSetupBusy = false
            return@registerForActivityResult
        }
        job = scope.launch {
            val parent = File(activity.filesDir, "litertlm").apply { mkdirs() }
            val incoming = File(parent, "incoming-${UUID.randomUUID()}")
            try {
                modelSetupStatus = "正在校验并安装本地模型…"
                withContext(Dispatchers.IO) {
                    check(incoming.mkdir())
                    activity.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input)
                        LiteRtLmModelBundle.unpack(input, incoming) { ensureActive() }
                    }
                    backend.replaceImportedBundle(incoming)
                }
                modelSetupStatus = "模型已安装，可以选择图片并生成"
                onImported()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                modelSetupStatus = "导入失败，请检查模型包格式、文件校验和设备兼容性"
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { incoming.deleteRecursively() }
                modelSetupBusy = false
            }
        }
    }

    override fun selectModelBundle() {
        if (closed || modelSetupBusy || !canImport()) return
        modelSetupBusy = true
        try { launcher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
        catch (_: Exception) { modelSetupBusy = false; modelSetupStatus = "无法打开模型文件选择器" }
    }

    override fun closeModelSetup() { closed = true; job?.cancel() }
}
