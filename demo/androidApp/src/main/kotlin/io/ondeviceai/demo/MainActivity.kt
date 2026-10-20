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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

class MainActivity:ComponentActivity(),DemoActions,IdentityDemoActions {
    private val detector=MLKitFaceDetector()
    private lateinit var bridge:AndroidFaceBridge
    private lateinit var model:FaceViewModel
    private lateinit var speechHost: SpeechHost
    private val speechPermission: androidx.activity.result.ActivityResultLauncher<String> = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        speechPermissionGate.complete(granted)
    }
    private val speechPermissionGate: SpeechPermissionGate = SpeechPermissionGate { speechPermission.launch(Manifest.permission.RECORD_AUDIO) }
    private var identityModel by mutableStateOf(IdentityViewModel(UnavailableIdentityBridge()))
    private var identityProbeFactory:((Bitmap)->IdentityProbeReference)?=null
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
        speechHost = SpeechHost(lifecycleScope,
            createTts = { AndroidTextToSpeechBackend.create(this) }, createAsr = { AndroidSpeechRecognizerBackend(this) },
            hasPermission = { Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED },
            requestPermission = speechPermissionGate::request,
            openSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) })
        setContent {
            MaterialTheme {Column(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                    if(cameraRunning) AndroidView(factory={context->PreviewView(context).also{previewView=it;bindCamera(it)}},modifier=Modifier.fillMaxSize())
                    else photo?.let{Image(it.asImageBitmap(),"selected image",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}
                    val current=result
                    if(current!=null) Canvas(Modifier.fillMaxSize()) {
                        val transform=ImageGeometry.preview(current.orientedWidth,current.orientedHeight,size.width.toDouble(),size.height.toDouble(),cameraRunning)
                        for(face in current.faces) {
                            val p=transform.apply(FacePoint(face.box.x,face.box.y))
                            val width=(face.box.width*kotlin.math.abs(transform.scaleX)).toFloat();val height=(face.box.height*transform.scaleY).toFloat()
                            drawRect(Color.Green,Offset((if(transform.scaleX<0)p.x-width else p.x).toFloat(),p.y.toFloat()),Size(width,height),style=Stroke(3f))
                        }
                    }
                }
                FaceScreen(model,this@MainActivity,identityModel,this@MainActivity,speechHost.model,speechHost)
                message?.let{Text(it)}
                report?.let{json->TextButton(onClick={exportReport(json)}){Text("导出 Benchmark JSON")}}
            }}
        }
    }
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
    override fun onStart(){super.onStart();if(::speechHost.isInitialized)speechHost.onForeground()}
    override fun onStop(){if(::speechHost.isInitialized)speechHost.onBackground();super.onStop();if(::model.isInitialized){stopCamera();model.cancel()}}
    override fun onDestroy(){if(::speechHost.isInitialized)speechHost.close();speechPermissionGate.clear();if(::model.isInitialized)model.close();identityModel.close();identityProbeFactory=null;latestIdentityCameraFrame=null;cameraExecutor.shutdown();super.onDestroy()}
}
