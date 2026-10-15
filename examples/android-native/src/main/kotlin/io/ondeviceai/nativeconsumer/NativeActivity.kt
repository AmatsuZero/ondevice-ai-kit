package io.ondeviceai.nativeconsumer
import android.app.Activity
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.TextView
import io.ondeviceai.mlkit.MLKitFaceDetector
import io.ondeviceai.face.ImageInput
import kotlinx.coroutines.*
class NativeActivity:Activity() {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private val detector=MLKitFaceDetector()
 override fun onCreate(savedInstanceState:Bundle?) {
  super.onCreate(savedInstanceState);val label=TextView(this);label.text="Running local face detection";setContentView(label)
  scope.launch {try {val blank=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.WHITE)};val result=detector.detect(ImageInput(blank));label.text="Native SDK: faces=${result.faces.size}, backend=${result.backendId}"} catch(error:Exception){label.text=error.toString()}}
 }
 override fun onDestroy(){scope.launch(NonCancellable){detector.close()};scope.cancel();super.onDestroy()}
}
