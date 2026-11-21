package io.ondeviceai.demo
import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.ondeviceai.binding.*
import io.ondeviceai.face.*
import io.ondeviceai.mlkit.MLKitFaceDetector
import io.ondeviceai.systemtts.AndroidTextToSpeechBackend
import io.ondeviceai.systemasr.AndroidSpeechRecognizerBackend
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.io.File

class MainActivity:ComponentActivity(),DemoActions,IdentityDemoActions {
    private val detector=MLKitFaceDetector()
    private lateinit var bridge:AndroidFaceBridge
    private lateinit var model:FaceViewModel
    private lateinit var speechHost: SpeechHost
    private lateinit var videoHost: VideoHost
    private lateinit var voiceHost: VoiceEffectsHost
    private var unifiedModel: UnifiedDemoModel? = null
    private var unifiedCleaning by mutableStateOf(false)
    private lateinit var playbackHost: PlaybackEnhancementHost
    private var playbackPickerEpoch = 0L
    private val playbackPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (::playbackHost.isInitialized) {
            val name = runCatching { uri?.let { selected -> contentResolver.query(selected, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } } }.getOrNull()
            playbackHost.picked(uri, name, playbackPickerEpoch)
        }
    }
    private var multimodalHost: MultimodalHost? = null
    private var multimodalPickerReply: ((File?) -> Unit)? = null
    private var multimodalPickerFailure: (() -> Unit)? = null
    private var multimodalImport: Job? = null
    // DocumentsUI gives the multimodal demo a stable openable document contract,
    // including test and user document providers, before the app copies it into
    // its own private store.
    private val multimodalPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val reply = multimodalPickerReply; multimodalPickerReply = null
        val failure = multimodalPickerFailure; multimodalPickerFailure = null
        if (reply != null) {
            if (uri == null) reply(null)
            else multimodalImport = lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        } ?: "selected-image"
                        val copy = MultimodalPickerCopy.create(File(cacheDir, "multimodal-picker-${UUID.randomUUID()}"), name,
                            open = { checkNotNull(contentResolver.openInputStream(uri)) }, checkpoint = { ensureActive() })
                        try {
                            withContext(Dispatchers.Main.immediate) {
                                reply(copy.file)
                                // The host's second private copy is asynchronous. Keep this source until joined.
                                withContext(NonCancellable) { multimodalHost?.awaitImports() }
                            }
                        } finally { copy.close() }
                    }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { failure?.invoke() }
                finally { multimodalImport = null }
            }
        }
    }
    private var videoInputs: VideoInputStore? = null
    private var videoBridge: AndroidVideoBridge? = null
    private var videoImport: Job? = null
    private val videoModelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && ::videoHost.isInitialized && videoHost.model.state.controlsEnabled) {
            videoHost.model.setImporting(true)
            videoImport = lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val copy = MultimodalPickerCopy.create(File(cacheDir, "sr-model-${UUID.randomUUID()}"), "ESRGAN.tflite", 5_000_000,
                            open = { checkNotNull(contentResolver.openInputStream(uri)) }, checkpoint = { ensureActive() })
                        try { DemoVideoBackendFactory.install(copy.file, File(noBackupFilesDir, "video-sr/ESRGAN.tflite")) }
                        finally { copy.close() }
                    }
                    videoHost.model.refreshBackends()
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { message = "超分模型导入失败，请选择官方 ESRGAN.tflite"; videoHost.model.inputFailed() }
                finally { videoHost.model.setImporting(false); videoImport = null }
            }
        }
    }
    private var videoPickerReply: ((String?, String?) -> Unit)? = null
    private val videoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val reply = videoPickerReply; videoPickerReply = null
        val store = videoInputs
        if (reply != null) {
            if (uri == null) reply(null, null)
            else if (store == null) reply(UUID.randomUUID().toString(), "已选择的视频（Android 后端待接入）")
            else {
                videoHost.model.setImporting(true)
                videoImport = lifecycleScope.launch {
                try {
                    // Keep ownership until the UI callback either selects or retires the token.
                    withContext(Dispatchers.IO) {
                        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        } ?: "video"
                        val imported = store.import(name, open = { checkNotNull(contentResolver.openInputStream(uri)) }, checkpoint = { ensureActive() })
                        var delivered = false
                        try { withContext(Dispatchers.Main.immediate) { reply(imported.reference, imported.displayName); delivered = true } }
                        finally { if (!delivered) store.remove(imported.reference) }
                    }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { videoHost.model.inputFailed() }
                finally { videoHost.model.setImporting(false); videoImport = null }
                }
            }
        }
    }
    private val speechPermission: androidx.activity.result.ActivityResultLauncher<String> = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        speechPermissionGate.complete(granted)
    }
    private val speechPermissionGate: SpeechPermissionGate = SpeechPermissionGate { speechPermission.launch(Manifest.permission.RECORD_AUDIO) }
    private var identityModel by mutableStateOf(IdentityViewModel(UnavailableIdentityBridge()))
    private var identityProbeFactory:((Bitmap)->IdentityProbeReference)?=null
    private var identityRuntime: DemoIdentityRuntime? = null
    private var latestIdentityCameraFrame:Triple<Bitmap,Long,Int>?=null
    private var livenessCapture:Job?=null
    private val cameraExecutor=Executors.newSingleThreadExecutor()
    private var provider:ProcessCameraProvider?=null
    private var previewView:PreviewView?=null
    private var cameraRunning by mutableStateOf(false)
    private var photo by mutableStateOf<Bitmap?>(null)
    private var result by mutableStateOf<FaceResult?>(null)
    private var report by mutableStateOf<String?>(null)
    private var message by mutableStateOf<String?>(null)
    private var benchmark:Job?=null
    private val photoPicker=registerForActivityResult(ActivityResultContracts.GetContent()) {uri-> if(uri!=null)loadPhoto(uri)}
    private val cameraPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()) {granted->if(granted)enableCamera() else model.permissionDenied()}
    private val reportPicker=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {uri->
        if(uri!=null) lifecycleScope.launch(Dispatchers.IO) {try {contentResolver.openOutputStream(uri)?.use{it.write((report?:"").toByteArray())}}catch(error:Exception){withContext(Dispatchers.Main){message=error.message}}}
    }
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        bridge=AndroidFaceBridge(detector,lifecycleScope){result=it};model=FaceViewModel(bridge)
        identityRuntime = DemoIdentityBackendFactory.create(this, lifecycleScope, ::configureIdentity) {
            identityModel.close()
            identityModel = IdentityViewModel(UnavailableIdentityBridge())
            identityProbeFactory = null
        }
        if (DemoVideoBackendFactory.enabled) {
            val store = VideoInputStore(File(cacheDir, "video-input-${UUID.randomUUID()}"))
            videoInputs = store
            videoBridge = AndroidVideoBridge(DemoVideoBackendFactory.create(
                File(noBackupFilesDir, "video-sr/ESRGAN.tflite"), File(cacheDir, "video-output")), store, lifecycleScope)
        }
        videoHost = VideoHost(bridge = videoBridge ?: UnavailableVideoBridge(),
            selectFile = { reply ->
                if (videoPickerReply != null || videoImport?.isActive == true) reply(null, null)
                else { videoPickerReply = reply; videoPicker.launch("video/*") }
            }, openOutput = { exportVideo(it, false) }, shareOutput = { exportVideo(it, true) },
            releaseInput = { videoInputs?.remove(it) }, closeInputs = { videoInputs?.close() },
            importModel = if (DemoVideoBackendFactory.enabled) ({ videoModelPicker.launch(arrayOf("*/*")) }) else null)
        if (DemoMultimodalBackendFactory.enabled || intent.getBooleanExtra("odai.multimodal.enabled", false)) {
            val selectMultimodal: (String, (File?) -> Unit, () -> Unit) -> Unit = { mime, reply, failure ->
                    if (multimodalPickerReply != null || multimodalImport?.isActive == true) reply(null)
                    else {
                        multimodalPickerReply = reply
                        multimodalPickerFailure = failure
                        try { multimodalPicker.launch(arrayOf(mime)) }
                        catch (error: Exception) { multimodalPickerReply = null; multimodalPickerFailure = null; throw error }
                    }
            }
            val multimodalInputs = MultimodalInputStore(File(cacheDir, "multimodal-input-${UUID.randomUUID()}"))
            val multimodalRuntime = DemoMultimodalBackendFactory.create(this, multimodalInputs, lifecycleScope,
                canImport = { multimodalHost?.model?.state?.controlsEnabled == true },
                onImported = { multimodalHost?.model?.refreshBackends() })
            multimodalHost = MultimodalHost(
                multimodalInputs,
                bridge = multimodalRuntime.bridge,
                selectFile = { reply, failure -> selectMultimodal("image/*", reply, failure) },
                videoStore = if (DemoMultimodalBackendFactory.imageOnly) null else
                    MultimodalInputStore(File(cacheDir, "multimodal-video-${UUID.randomUUID()}")),
                selectVideo = if (DemoMultimodalBackendFactory.imageOnly) null else
                    { reply, failure -> selectMultimodal("video/*", reply, failure) },
                importScope = lifecycleScope,
                modelSetup = multimodalRuntime.modelSetup,
                canSelectInput = { multimodalPickerReply == null && multimodalImport?.isActive != true &&
                    multimodalRuntime.modelSetup?.modelSetupBusy != true })
        }
        val speechResources = io.ondeviceai.systemasr.AndroidAsrResources(this)
        val speechResourceHost = AsrResourceHost(lifecycleScope, speechResources.canObserveDownload,
            query = speechResources::check, requestDownload = speechResources::download,
            canStart = { speechHost.canManageAsrResources })
        speechHost = SpeechHost(lifecycleScope,
            createTts = { AndroidTextToSpeechBackend.create(this) }, createAsr = { AndroidSpeechRecognizerBackend(this) },
            createAdditionalAsr = { DemoOfflineAsrFactory.create(this) },
            defaultAsrBackendId = DemoOfflineAsrFactory.defaultBackendId,
            hasPermission = { ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED },
            requestPermission = speechPermissionGate::request,
            openSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) },
            asrResources = speechResourceHost)
        voiceHost = VoiceEffectsHost(this)
        playbackHost = PlaybackEnhancementHost(this) {
            playbackPickerEpoch = playbackHost.selectionEpoch
            playbackPicker.launch(arrayOf("video/*"))
        }
        setContent {
            val unifiedModel = remember {
                UnifiedDemoModel(onLeave = ::leaveUnifiedCapability, isIdle = ::unifiedDemoIdle).also { this@MainActivity.unifiedModel = it }
            }
            val bindings = remember {
                object : UnifiedDemoBindings {
                    override val platformLabel get() = "Android"
                    override val faceModel get() = model
                    override val faceActions get() = this@MainActivity
                    override val identityModel get() = this@MainActivity.identityModel
                    override val identityActions get() = this@MainActivity
                    override val identitySetup get() = identityRuntime
                    override val speechModel get() = speechHost.model
                    override val speechActions get() = speechHost
                    override val videoModel get() = videoHost.model
                    override val videoActions get() = videoHost
                    override val multimodalModel get() = multimodalHost?.model
                    override val multimodalActions get() = multimodalHost
                    override val voiceEffectsModel get() = voiceHost.model
                    override val voiceEffectsActions get() = voiceHost
                    override val realtimeSr: RealtimeSRDemoActions get() = playbackHost
                    override val playbackEnhancement get() = playbackHost
                    override val nowMillis get() = System.currentTimeMillis()
                    override fun setRecordSink(sink: DemoTerminalObserver?) {
                        model.onTerminal = sink
                        speechHost.model.onTerminal = sink
                        videoHost.model.onTerminal = sink
                        multimodalHost?.model?.onTerminal = sink
                        voiceHost.model.onTerminal = sink
                    }
                    override fun enterCapability(id: DemoCapabilityId) {
                        if (id == DemoCapabilityId.VOICE_EFFECTS) voiceHost.foreground()
                        if (id == DemoCapabilityId.PLAYBACK_ENHANCEMENT) playbackHost.enter()
                    }
                    override fun retryCleanup() { videoHost.model.retryReleases(); voiceHost.model.retryReleases() }
                    override fun modelSetup(id: DemoCapabilityId) = unifiedModelSetup(id)
                    override fun status(id: DemoCapabilityId) = capabilityStatus(id)
                    override fun openVoiceEffects() = this@MainActivity.openVoiceEffects()
                    override fun leaveCapability(id: DemoCapabilityId) = leaveUnifiedCapability(id)
                }
            }
            BackHandler(enabled = unifiedModel.route != UnifiedDemoRoute.Explore) {
                if (unifiedModel.route.topLevel() == unifiedModel.route) unifiedModel.navigateTopLevel(DemoTab.EXPLORE)
                else unifiedModel.back()
            }
            UnifiedDemoTheme {
                UnifiedDemoScreen(
                    model = unifiedModel,
                    bindings = bindings,
                    modifier = Modifier.fillMaxSize().safeDrawingPadding().semantics { testTagsAsResourceId = true },
                    playbackContent = {
                        AndroidView(factory = { playbackHost.surfaceView }, modifier = Modifier.fillMaxWidth().height(240.dp),
                            onRelease = { playbackHost.leave() })
                    },
                    facePreview = {
                        Box(Modifier.fillMaxWidth().height(240.dp).background(Color.Black).testTag("face-preview")) {
                            if (cameraRunning) AndroidView(
                                factory = { context -> PreviewView(context).also { previewView = it; bindCamera(it) } },
                                modifier = Modifier.fillMaxSize(),
                            ) else photo?.let { Image(it.asImageBitmap(), "selected image", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                            val current = result
                            if (current != null) Canvas(Modifier.fillMaxSize()) {
                                val transform = ImageGeometry.preview(current.orientedWidth, current.orientedHeight,
                                    size.width.toDouble(), size.height.toDouble(), cameraRunning)
                                for (face in current.faces) {
                                    val point = transform.apply(FacePoint(face.box.x, face.box.y))
                                    val width = (face.box.width * kotlin.math.abs(transform.scaleX)).toFloat()
                                    val height = (face.box.height * transform.scaleY).toFloat()
                                    drawRect(Color.Green, Offset((if (transform.scaleX < 0) point.x - width else point.x).toFloat(), point.y.toFloat()),
                                        Size(width, height), style = Stroke(3f))
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    private fun unifiedDemoIdle(): Boolean =
        !unifiedCleaning && (!::playbackHost.isInitialized || playbackHost.idle) && (!::voiceHost.isInitialized || voiceHost.idle) &&
        (!::speechHost.isInitialized || speechHost.model.state.controlsEnabled) &&
            (!::videoHost.isInitialized || (videoHost.model.state.controlsEnabled && videoHost.model.state.pendingReleases == 0)) &&
            (multimodalHost?.model?.state?.controlsEnabled != false) &&
            multimodalHost?.modelSetupBusy != true &&
            identityRuntime?.busy != true && identityModel.navigationIdle &&
            !cameraRunning && livenessCapture?.isActive != true

    private fun leaveUnifiedCapability(id: DemoCapabilityId) {
        when (id) {
            DemoCapabilityId.ASR, DemoCapabilityId.TTS -> if (::speechHost.isInitialized) speechHost.leavePage()
            DemoCapabilityId.VOICE_EFFECTS -> voiceHost.leavePage()
            DemoCapabilityId.FACE_DETECTION -> { stopCamera(); model.cancel(); identityModel.cancel() }
            DemoCapabilityId.IMAGE_UNDERSTANDING -> { multimodalHost?.cancelModelSetup(); multimodalHost?.leaveMultimodalPage() }
            DemoCapabilityId.FILE_SUPER_RESOLUTION -> if (::videoHost.isInitialized) videoHost.leavePage()
            DemoCapabilityId.PLAYBACK_ENHANCEMENT -> playbackHost.leave()
            DemoCapabilityId.VIDEO_UNDERSTANDING -> Unit
            DemoCapabilityId.IDENTITY_LIVENESS -> identityModel.leavePage()
        }
        unifiedCleaning = true
        lifecycleScope.launch {
            try {
                if (id == DemoCapabilityId.FILE_SUPER_RESOLUTION) { videoImport?.cancelAndJoin(); videoHost.model.setImporting(false) }
                if (id == DemoCapabilityId.IMAGE_UNDERSTANDING) { multimodalImport?.cancelAndJoin(); multimodalHost?.awaitImports() }
                if (id == DemoCapabilityId.FACE_DETECTION) bridge.awaitIdle()
            } finally { unifiedCleaning = false }
        }
    }

    private fun capabilityStatus(id: DemoCapabilityId): DemoCapabilityStatus = when (id) {
        DemoCapabilityId.ASR -> speechStatus(speechHost.model.state.asr)
        DemoCapabilityId.TTS -> speechStatus(speechHost.model.state.tts)
        DemoCapabilityId.VOICE_EFFECTS -> DemoCapabilityStatus(DemoCapabilityAvailabilityKind.READY, "本机离线转换")
        DemoCapabilityId.FACE_DETECTION -> bridge.availability().let {
            if (it.available) DemoCapabilityStatus(DemoCapabilityAvailabilityKind.READY, it.backendId)
            else DemoCapabilityStatus(DemoCapabilityAvailabilityKind.MISSING_BACKEND, it.reason)
        }
        DemoCapabilityId.IMAGE_UNDERSTANDING -> multimodalStatus()
        DemoCapabilityId.FILE_SUPER_RESOLUTION -> videoStatus()
        DemoCapabilityId.PLAYBACK_ENHANCEMENT -> DemoCapabilityStatus(
            DemoCapabilityAvailabilityKind.READY,
            "CMP 内嵌实时播放器；支持原画/自动/CuNNy/Spatial",
        )
        DemoCapabilityId.VIDEO_UNDERSTANDING, DemoCapabilityId.IDENTITY_LIVENESS -> DemoCapabilityStatus(DemoCapabilityAvailabilityKind.FROZEN_PREVIEW, "研发暂停")
    }

    private fun unifiedModelSetup(id: DemoCapabilityId): UnifiedDemoModelActions? = when (id) {
        DemoCapabilityId.FILE_SUPER_RESOLUTION -> (if (::videoHost.isInitialized) videoHost else null)?.let { host ->
            object : UnifiedDemoModelActions {
                override val status get() = host.videoProfileDescription
                override val busy get() = host.model.state.importing
                override val canPrepare get() = host.canImportVideoModel
                override fun prepare() = host.importVideoModel()
                override fun cancel() { videoImport?.cancel() }
            }
        }
        DemoCapabilityId.IMAGE_UNDERSTANDING -> multimodalHost?.let { host ->
            object : UnifiedDemoModelActions {
                override val status get() = host.modelSetupStatus ?: "当前构建未提供模型导入"
                override val busy get() = host.modelSetupBusy
                override val canPrepare get() = host.supportsModelSetup
                override fun prepare() = host.selectModelBundle()
                override fun cancel() = host.cancelModelSetup()
            }
        }
        else -> null
    }

    private fun speechStatus(state: SpeechBackendState): DemoCapabilityStatus = when {
        state.backends.isEmpty() && state.failure == null -> DemoCapabilityStatus(DemoCapabilityAvailabilityKind.NOT_QUERIED)
        state.backends.any { it.availability.available } -> DemoCapabilityStatus(DemoCapabilityAvailabilityKind.READY)
        else -> DemoCapabilityStatus(DemoCapabilityAvailabilityKind.MISSING_BACKEND, state.failure?.reason)
    }

    private fun multimodalStatus(): DemoCapabilityStatus {
        val host = multimodalHost ?: return DemoCapabilityStatus(DemoCapabilityAvailabilityKind.MISSING_HOST, "图像理解 Host 未接入")
        val state = host.model.state
        return when {
            state.backends.isEmpty() && state.queryFailure == null -> DemoCapabilityStatus(DemoCapabilityAvailabilityKind.NOT_QUERIED)
            state.backends.any { it.availability.available } -> DemoCapabilityStatus(DemoCapabilityAvailabilityKind.READY)
            else -> DemoCapabilityStatus(DemoCapabilityAvailabilityKind.MISSING_BACKEND, state.queryFailure?.reason)
        }
    }

    private fun videoStatus(): DemoCapabilityStatus = when {
        !DemoVideoBackendFactory.enabled -> DemoCapabilityStatus(DemoCapabilityAvailabilityKind.MISSING_HOST, "视频 Host 未接入")
        videoHost.model.state.backends.any { it.availability.available } -> DemoCapabilityStatus(DemoCapabilityAvailabilityKind.READY)
        videoHost.model.state.backends.isEmpty() && videoHost.model.state.queryFailure == null -> DemoCapabilityStatus(DemoCapabilityAvailabilityKind.NOT_QUERIED)
        else -> DemoCapabilityStatus(DemoCapabilityAvailabilityKind.MISSING_BACKEND, videoHost.model.state.queryFailure?.reason)
    }

    private fun openVoiceEffects() { unifiedModel?.openCapability(DemoCapabilityId.VOICE_EFFECTS) }

    fun configureIdentity(bridge:IdentityBridge, configuration:IdentityDemoConfiguration,
        probeFactory:(Bitmap)->IdentityProbeReference) {
        identityModel.close()
        identityProbeFactory=probeFactory
        identityModel=IdentityViewModel(bridge).also { replacement ->
            replacement.configure(configuration)
            photo?.let { replacement.selectProbe(probeFactory(it)) }
        }
    }
    override fun submitLivenessFrame() {
        val captured=if(cameraRunning)latestIdentityCameraFrame else photo?.let{Triple(it,System.nanoTime(),0)}
        if(captured==null){message="先选择图片或启动相机";return}
        if(livenessCapture?.isActive==true)return
        val sessionId=identityModel.livenessSessionId?:return
        val challenge=identityModel.livenessChallenge
        val target=identityModel
        livenessCapture=lifecycleScope.launch {
            try {
                val bytes=withContext(Dispatchers.IO) { java.io.ByteArrayOutputStream().use { output ->
                    val upright=if(captured.third==0) captured.first else Bitmap.createBitmap(captured.first,0,0,captured.first.width,captured.first.height,Matrix().apply{postRotate(captured.third.toFloat())},true)
                    check(upright.compress(Bitmap.CompressFormat.PNG,100,output)){"无法编码活体帧"};output.toByteArray()
                } }
                if(target!==identityModel)return@launch
                val accepted=target.submitLivenessFrame(IdentityLivenessFrame(bytes,captured.second,"demo-upright-frame-v1",challenge),sessionId)
                message=if(accepted)"活体帧已接收" else "活体帧未接收"
            } catch(_:CancellationException){} catch(error:Exception){message=error.message}
        }
    }
    override fun selectPhoto(){stopCamera();model.cancel();identityModel.selectProbe(null);photoPicker.launch("image/*")}
    private fun loadPhoto(uri:Uri) {lifecycleScope.launch {
        try {
            val bitmap=withContext(Dispatchers.IO) {
                val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}
                require(bounds.outWidth>0 && bounds.outHeight>0){"invalid image"}
                val options=BitmapFactory.Options().apply{inSampleSize=imageSampleSize(bounds.outWidth,bounds.outHeight)}
                val decoded=contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}?:error("invalid image")
                val orientation=contentResolver.openFileDescriptor(uri,"r")?.use{ExifInterface(it.fileDescriptor).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}?:ExifInterface.ORIENTATION_NORMAL
                val matrix=Matrix()
                when(orientation){ExifInterface.ORIENTATION_ROTATE_90->matrix.postRotate(90f);ExifInterface.ORIENTATION_ROTATE_180->matrix.postRotate(180f);ExifInterface.ORIENTATION_ROTATE_270->matrix.postRotate(270f);ExifInterface.ORIENTATION_FLIP_HORIZONTAL->matrix.postScale(-1f,1f);ExifInterface.ORIENTATION_FLIP_VERTICAL->matrix.postScale(1f,-1f);ExifInterface.ORIENTATION_TRANSPOSE->{matrix.postRotate(90f);matrix.postScale(-1f,1f)};ExifInterface.ORIENTATION_TRANSVERSE->{matrix.postRotate(270f);matrix.postScale(-1f,1f)}}
                Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true)
            }
            photo=bitmap;result=null;report=null
            identityModel.selectProbe(identityProbeFactory?.invoke(bitmap))
            model.detect(bridge.addInput(bitmap))
        } catch(error:Exception){message=error.message}
    }}
    override fun startCamera(){identityModel.selectProbe(null);cameraPermission.launch(Manifest.permission.CAMERA)}
    private fun enableCamera(){stopCamera();cameraRunning=true;previewView?.let{bindCamera(it)}}
    private fun bindCamera(view:PreviewView) {
        val future=ProcessCameraProvider.getInstance(this)
        future.addListener({
            if(!cameraRunning)return@addListener
            try {
                provider=future.get();provider?.unbindAll()
                val preview=Preview.Builder().build().also{it.surfaceProvider=view.surfaceProvider}
                val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).setTargetResolution(android.util.Size(640,480)).build()
                analysis.setAnalyzer(cameraExecutor) {frame->
                    try {val bitmap=frame.toBitmap();val rotation=frame.imageInfo.rotationDegrees
                        lifecycleScope.launch(Dispatchers.Main.immediate) {
                            if(cameraRunning) {
                                latestIdentityCameraFrame=Triple(bitmap,System.nanoTime(),rotation)
                                model.detectFrame(bridge.addInput(bitmap,rotation){frame.close()})
                            } else frame.close()
                        }
                    } catch(_:Exception){frame.close()}
                }
                provider?.bindToLifecycle(this,CameraSelector.DEFAULT_FRONT_CAMERA,preview,analysis)
            } catch(error:Exception){message=error.message;cameraRunning=false}
        },ContextCompat.getMainExecutor(this))
    }
    override fun stopCamera(){livenessCapture?.cancel();cameraRunning=false;latestIdentityCameraFrame=null;identityModel.cancel();provider?.unbindAll();result=null;benchmark?.cancel();benchmark=null}
    override fun runBenchmark(){
        stopCamera();model.cancel();val bitmap=photo?:run{message="先选择图片";return}
        benchmark=lifecycleScope.launch {
            try {
                bridge.awaitIdle();val warm=mutableListOf<Double>();val samples=mutableListOf<Double>()
                repeat(35){index->ensureActive();val start=System.nanoTime();detector.detect(ImageInput(bitmap));val elapsed=(System.nanoTime()-start)/1e6;if(index<5)warm+=elapsed else samples+=elapsed}
                val bytes=java.io.ByteArrayOutputStream().apply{bitmap.compress(Bitmap.CompressFormat.PNG,100,this)}.toByteArray()
                val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
                report=JSONObject().put("schemaVersion",1).put("runId",UUID.randomUUID().toString()).put("device",Build.MODEL).put("OS",Build.VERSION.RELEASE).put("sdkCommit",BuildConfig.ODAI_SDK_COMMIT).put("capability","face").put("actualBackend",bridge.backendId()).put("modelRevision",JSONObject.NULL).put("preprocessRevision","upright-v1").put("fixtureHash",hash).put("units","ms").put("timingsMs",JSONObject().put("coldStart",JSONArray()).put("modelLoad",JSONArray()).put("warmup",JSONArray(warm)).put("inference",JSONArray(samples))).put("quality",JSONObject()).put("peakMemory",JSONObject.NULL).put("energy",JSONObject.NULL).put("energyStatus","unmeasured").put("thermal",JSONObject.NULL).put("measurementMethod","native-detect-monotonic-includes-preprocess").put("validationStatus",if(Build.FINGERPRINT.contains("generic"))"simulator" else "device").toString(2)
                message="Benchmark 完成：5 次预热，30 次采样"
            } catch(_:CancellationException){message="Benchmark 已取消"}catch(error:Exception){message=error.message}
        }
    }
    override fun exportReport(json:String){report=json;reportPicker.launch("odai-benchmark.json")}
    private fun exportVideo(reference: String, share: Boolean) {
        val source = videoBridge?.outputFile(reference) ?: return
        val reader = try { source.inputStream() } catch (_: Exception) { message = "无法读取视频结果"; return }
        // Opening the descriptor while the UI owns the lease keeps bytes readable after unlink.
        @OptIn(ExperimentalCoroutinesApi::class)
        lifecycleScope.launch(start = CoroutineStart.ATOMIC) {
            try {
                val copy = withContext(Dispatchers.IO) { VideoOutputExport.snapshot(reader, File(cacheDir, "video-exports")) }
                val uri = androidx.core.content.FileProvider.getUriForFile(this@MainActivity, "$packageName.videooutputs", copy)
                val intent = if (share) Intent(Intent.ACTION_SEND).apply { type = "video/mp4"; putExtra(Intent.EXTRA_STREAM, uri) }
                    else Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.clipData = android.content.ClipData.newRawUri("Video result", uri)
                startActivity(if (share) Intent.createChooser(intent, "分享视频") else intent)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { message = "无法打开或分享视频结果" }
            finally { reader.close() }
        }
    }
    override fun onStart(){if(::playbackHost.isInitialized)playbackHost.foreground();if(::voiceHost.isInitialized && (unifiedModel?.route as? UnifiedDemoRoute.Capability)?.id == DemoCapabilityId.VOICE_EFFECTS)voiceHost.foreground();if(::videoHost.isInitialized)videoHost.foreground();super.onStart();if(::speechHost.isInitialized)speechHost.onForeground();multimodalHost?.onForeground()}
    override fun onStop(){if(::playbackHost.isInitialized)playbackHost.background();if(::voiceHost.isInitialized)voiceHost.background();identityRuntime?.stop();if(::speechHost.isInitialized)speechHost.onBackground();if(::videoHost.isInitialized)videoHost.background(preservePicker=videoPickerReply!=null || videoImport?.isActive==true);multimodalHost?.background(preservePicker=multimodalPickerReply!=null || multimodalImport?.isActive==true);super.onStop();if(::model.isInitialized){stopCamera();model.cancel()}}
    override fun onDestroy(){if(::playbackHost.isInitialized)playbackHost.close();if(::voiceHost.isInitialized)voiceHost.close();identityRuntime?.close();multimodalPickerReply=null;multimodalPickerFailure=null;multimodalImport?.cancel();multimodalHost?.close();if(::speechHost.isInitialized)speechHost.close();if(::videoHost.isInitialized)videoHost.close();videoPickerReply=null;videoImport?.cancel();speechPermissionGate.clear();if(::model.isInitialized)model.close();identityModel.close();identityProbeFactory=null;latestIdentityCameraFrame=null;cameraExecutor.shutdown();super.onDestroy()}
}
