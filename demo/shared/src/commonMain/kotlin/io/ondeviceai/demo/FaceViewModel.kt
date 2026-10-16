package io.ondeviceai.demo
import androidx.compose.runtime.*
import io.ondeviceai.binding.*
data class FaceState(val busy:Boolean=false,val error:String?=null,val result:BridgeFaceResult?=null,val cancelled:Boolean=false,val streaming:Boolean=false) {
    val showProgress:Boolean get()=busy && !streaming
    val canSelectInput:Boolean get()=!busy || streaming
}
/** All entry points and bridge callbacks are serialized on the platform UI thread. */
class FaceViewModel(val bridge:FaceBridge) {
    var state by mutableStateOf(FaceState());private set
    private var generation=0L
    private var active:BridgeTask?=null
    private var closed=false
    fun detect(inputId:String,selection:BridgeBackendSelection=BridgeBackendSelection()) = detect(inputId,selection,streaming=false)
    fun detectFrame(inputId:String,selection:BridgeBackendSelection=BridgeBackendSelection()) = detect(inputId,selection,streaming=true)
    private fun detect(inputId:String,selection:BridgeBackendSelection,streaming:Boolean) {
        if(closed)return
        val previous=if(streaming)state.result else null
        cancel()
        val available=bridge.availability()
        if(!available.available){state=FaceState(error=available.reason?:"unavailable");return}
        val current=++generation;state=FaceState(busy=true,result=previous,streaming=streaming)
        val task=bridge.detect(inputId,selection,object:FaceCallback {
            override fun onResult(result:BridgeFaceResult){if(current==generation){state=FaceState(result=result,streaming=streaming);active=null}}
            override fun onError(message:String){if(current==generation){state=FaceState(error=message,result=previous,streaming=streaming);active=null}}
            override fun onCancelled(){if(current==generation){state=FaceState(cancelled=true);active=null}}
        })
        // A synchronous error callback may already have finished the task.
        if(state.busy && generation==current)active=task
    }
    fun cancel(){generation++;active?.cancel();active=null;state=state.copy(busy=false,error=null,cancelled=true,streaming=false)}
    fun permissionDenied(){cancel();state=FaceState(error="permissionDenied")}
    fun close(){if(!closed){closed=true;cancel();bridge.close()}}
}
