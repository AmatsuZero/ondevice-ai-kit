package io.ondeviceai.demo
import androidx.compose.runtime.*
import io.ondeviceai.binding.*
data class FaceState(val busy:Boolean=false,val error:String?=null,val result:BridgeFaceResult?=null,val cancelled:Boolean=false)
/** All entry points and bridge callbacks are serialized on the platform UI thread. */
class FaceViewModel(val bridge:FaceBridge) {
    var state by mutableStateOf(FaceState());private set
    private var generation=0L
    private var active:BridgeTask?=null
    private var closed=false
    fun detect(inputId:String) {
        if(closed)return
        cancel()
        val available=bridge.availability()
        if(!available.available){state=FaceState(error=available.reason?:"unavailable");return}
        val current=++generation;state=FaceState(busy=true)
        val task=bridge.detect(inputId,object:FaceCallback {
            override fun onResult(result:BridgeFaceResult){if(current==generation){state=FaceState(result=result);active=null}}
            override fun onError(message:String){if(current==generation){state=FaceState(error=message);active=null}}
            override fun onCancelled(){if(current==generation){state=FaceState(cancelled=true);active=null}}
        })
        // A synchronous error callback may already have finished the task.
        if(state.busy && generation==current)active=task
    }
    fun cancel(){generation++;active?.cancel();active=null;state=state.copy(busy=false,error=null,cancelled=true)}
    fun permissionDenied(){cancel();state=FaceState(error="permissionDenied")}
    fun close(){if(!closed){closed=true;cancel();bridge.close()}}
}
