package io.ondeviceai.demo

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import io.ondeviceai.modelstore.*
import io.ondeviceai.opencvidentity.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*

internal object DemoIdentityBackendFactory {
    fun create(activity: ComponentActivity, scope: CoroutineScope, configure: ConfigureIdentityPrototype,
        reset: () -> Unit): DemoIdentityRuntime = OpenCvIdentityDemoRuntime(activity, scope, configure, reset)
}

private class OpenCvIdentityDemoRuntime(
    private val activity: ComponentActivity,
    private val scope: CoroutineScope,
    private val configure: ConfigureIdentityPrototype,
    private val reset: () -> Unit,
) : DemoIdentityRuntime {
    override var busy by mutableStateOf(true); private set
    private var setupStatus by mutableStateOf("正在检查身份模型…")
    private val diagnostics = IdentityDiagnosticPresenter()
    override val status: String get() = diagnostics.message ?: setupStatus
    override var modelReady by mutableStateOf(false); private set
    override var sessionReady by mutableStateOf(false); private set
    private var closed = false
    private var opening = false
    private var job: Job? = null
    private var session: OpenCvPrototypeSession? = null
    private val models = ModelStore(File(activity.noBackupFilesDir, "opencv-identity/models"),
        ModelCompatibility(OpenCvIdentityModel.BACKEND_ID, OpenCvIdentityModel.VERSION, Build.VERSION.SDK_INT.toString(),
            Build.SUPPORTED_ABIS.first()))
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (closed) return@registerForActivityResult
        if (uri == null) { busy = false; return@registerForActivityResult }
        job = scope.launch {
            val staging = File(activity.noBackupFilesDir, "opencv-identity/import-${UUID.randomUUID()}")
            try {
                setupStatus = "正在校验并安装 YuNet/SFace…"
                withContext(Dispatchers.IO) {
                    check(staging.mkdirs())
                    activity.contentResolver.openInputStream(uri).use { input ->
                        IdentityModelBundle.unpack(requireNotNull(input), staging,
                            OpenCvIdentityModel.manifest().files.associate { it.path to it.bytes }) { ensureActive() }
                    }
                    models.install(OpenCvIdentityModel.manifest(), staging)
                }
                modelReady = true
                setupStatus = "身份模型已安装；启用本机注册后选择图片"
            } catch (error: CancellationException) { throw error }
            catch (failure: Exception) {
                diagnostics.report(OpenCvIdentityDiagnostic.from(OpenCvIdentityStage.MODEL_IMPORT, failure))
                setupStatus = diagnostics.message ?: "本机注册准备失败"
            }
            finally {
                withContext(NonCancellable + Dispatchers.IO) { staging.deleteRecursively() }
                busy = false
            }
        }
    }

    init {
        job = scope.launch {
            try {
                modelReady = models.status(OpenCvIdentityModel.ref) == ModelStatus.INSTALLED
                setupStatus = if (modelReady) "身份模型已安装；可启用本机注册" else "请导入固定版本 YuNet/SFace ZIP 模型包"
            } finally { busy = false }
        }
    }
    override fun importModels() {
        if (closed || busy || session != null) return
        busy = true
        diagnostics.clear()
        try { launcher.launch(arrayOf("application/zip", "application/octet-stream")) }
        catch (_: Exception) { busy = false; setupStatus = "无法打开模型选择器" }
    }
    override fun enableLocalEnrollment() {
        if (closed || busy || !modelReady || session != null) return
        busy = true; opening = true
        job = scope.launch {
            var opened: OpenCvPrototypeSession? = null
            try {
                setupStatus = "正在打开本机注册会话…"
                diagnostics.clear()
                opened = OpenCvPrototypeSession.open(activity, models, diagnostics::report)
                ensureActive()
                if (closed) return@launch
                configure(opened.bridge, opened.configuration, opened::probe)
                session = opened; opened = null
                sessionReady = true
                setupStatus = "本机注册已启用（30 分钟）；验证、识别和 PAD 待校准"
            } catch (error: CancellationException) { setupStatus = "本机注册启用已取消"; throw error }
            catch (failure: Exception) {
                if (diagnostics.latest == null) diagnostics.report(OpenCvIdentityDiagnostic.from(OpenCvIdentityStage.SESSION, failure))
                setupStatus = diagnostics.message ?: "本机注册准备失败"
            }
            finally {
                opened?.deleteAndClose()
                busy = false; opening = false
            }
        }
    }
    override fun deleteLocalEnrollment() {
        if (closed || busy) return
        val previous = session ?: return
        busy = true
        reset()
        cleanupScope.launch {
            try {
                previous.deleteAndClose()
                withContext(Dispatchers.Main) {
                    diagnostics.clear(); session = null; sessionReady = false; setupStatus = "本次注册已删除并停用"
                }
            } catch (_: Exception) { withContext(Dispatchers.Main) { setupStatus = "删除未完成，模型保持停用" } }
            finally { withContext(Dispatchers.Main) { busy = false } }
        }
    }
    override fun stop() { if (opening) job?.cancel() }
    override fun close() {
        if (closed) return
        closed = true; job?.cancel()
        val previous = session; session = null
        if (previous != null) cleanupScope.launch { try { previous.deleteAndClose() } finally { cleanupScope.cancel() } }
        else cleanupScope.cancel()
    }
}

/** Observable safe messages are read directly by the Identity controls, including mid-session failures. */
internal class IdentityDiagnosticPresenter {
    var latest by mutableStateOf<OpenCvIdentityDiagnostic?>(null)
        private set
    val message: String? get() {
        val failure = latest ?: return null
        return when (failure.stage) {
            OpenCvIdentityStage.ALIGNMENT -> "人脸对齐失败，请选择清晰的正面单人照"
            OpenCvIdentityStage.EMBEDDING -> "人脸特征提取失败，请检查模型包与设备兼容性"
            OpenCvIdentityStage.DETECTION -> "人脸检测失败，请重新选择清晰图片"
            OpenCvIdentityStage.IMAGE_DECODE -> "无法解码图片，请使用支持的图片格式"
            else -> when (failure.kind) {
                OpenCvIdentityFailureKind.MODEL_INTEGRITY -> "模型哈希校验失败，请使用固定版本模型包"
                OpenCvIdentityFailureKind.IO -> "模型读取失败，请重新选择文件并检查可用空间"
                OpenCvIdentityFailureKind.RUNTIME_LINKAGE -> "设备无法加载身份运行库，请检查设备兼容性"
                OpenCvIdentityFailureKind.NATIVE -> "身份模型加载失败，请检查模型兼容性"
                OpenCvIdentityFailureKind.INVALID_INPUT -> "模型包或会话参数不兼容，请检查文件格式"
                else -> "本机注册准备失败，请稍后重试"
            }
        }
    }
    fun report(value: OpenCvIdentityDiagnostic) { latest = value }
    fun clear() { latest = null }
}
