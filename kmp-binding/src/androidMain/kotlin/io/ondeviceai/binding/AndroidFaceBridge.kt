package io.ondeviceai.binding
import android.graphics.Bitmap
import io.ondeviceai.face.*
import kotlinx.coroutines.*
import java.util.UUID
/** UI-thread adapter. Native pixel references are never exported through commonMain. */
class AndroidFaceBridge(val detector:FaceDetector,private val scope:CoroutineScope,private val onNativeResult:(FaceResult)->Unit={}):FaceBridge {
    private data class Entry(val bitmap:Bitmap,val rotation:Int,val release:()->Unit)
    private val inputs=mutableMapOf<String,Entry>();private val jobs=mutableMapOf<String,Job>();private var closed=false
    fun addInput(bitmap:Bitmap,rotation:Int=0,release:()->Unit={}):String {
        check(!closed);val id=UUID.randomUUID().toString();inputs[id]=Entry(bitmap,rotation,release);return id
    }
    override fun backendId()="android.mlkit.face.bundled"
    override fun availability()=BridgeAvailability(!closed,if(closed)"closed" else null,backendId(),"26")
    override fun detect(inputId:String,callback:FaceCallback):BridgeTask {
        val entry=inputs.remove(inputId)
        if(closed || entry==null){entry?.release?.invoke();callback.onError(if(closed)"closed" else "missingInput");return object:BridgeTask{override fun cancel(){}}}
        val id=UUID.randomUUID().toString()
        val job=scope.launch(Dispatchers.Main.immediate,start=CoroutineStart.LAZY) {
            try {
                val start=System.nanoTime();val result=detector.detect(ImageInput(entry.bitmap,entry.rotation));ensureActive()
                onNativeResult(result)
                callback.onResult(BridgeFaceResult(result.frameId,result.orientedWidth,result.orientedHeight,result.backendId,result.faces.map { face ->BridgeFace(BridgeBox(face.box.x,face.box.y,face.box.width,face.box.height),face.yaw,face.pitch,face.roll,face.quality,face.confidence,face.landmarks.map{(name,points)->BridgeLandmark(name,points.map{BridgePoint(it.x,it.y)})},face.landmarkTopology)},(System.nanoTime()-start)/1e6,null))
            } catch(_:CancellationException){callback.onCancelled()}
            catch(error:Exception){callback.onError(error.message?:error.javaClass.simpleName)}
            finally {entry.release();jobs.remove(id)}
        }
        jobs[id]=job;job.start()
        return object:BridgeTask{override fun cancel(){job.cancel()}}
    }
    suspend fun awaitIdle(){jobs.values.toList().joinAll()}
    override fun close(){
        if(closed)return;closed=true;inputs.values.forEach{it.release()};inputs.clear();jobs.values.toList().forEach{it.cancel()}
        scope.launch(NonCancellable){detector.close()}
    }
}
