package io.ondeviceai.nativeconsumer
import android.app.Activity
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.TextView
import io.ondeviceai.mlkit.MLKitFaceDetector
import io.ondeviceai.face.ImageInput
import io.ondeviceai.face.FaceDetectionClient
import io.ondeviceai.face.FaceDetectionRegistration
import io.ondeviceai.face.FaceBackendSelection
import kotlinx.coroutines.*
class NativeActivity:Activity() {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private val detector=MLKitFaceDetector()
 private val client=FaceDetectionClient(detector.backendId,listOf(FaceDetectionRegistration(FaceDetectionClient.CAPABILITY_ID,detector.backendId,detector)))
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState);val label=TextView(this);label.text="Running local face detection";setContentView(label)
  scope.launch {try {val blank=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.WHITE)};val result=client.detect(ImageInput(blank),selection=FaceBackendSelection.explicit(detector.backendId));label.text="Native SDK: faces=${result.faces.size}, backend=${result.backendId}"} catch(error:Exception){label.text=error.toString()}}
 }
 override fun onDestroy(){scope.launch(NonCancellable){client.close()};scope.cancel();super.onDestroy()}
}
