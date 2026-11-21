package io.ondeviceai.demo
import androidx.compose.runtime.*
import io.ondeviceai.binding.*
data class FaceState(val busy:Boolean=false,val error:String?=null,val result:BridgeFaceResult?=null,val cancelled:Boolean=false,val streaming:Boolean=false) {
    val showProgress:Boolean get()=busy && !streaming
    val canSelectInput:Boolean get()=!busy || streaming
}
/** All entry points and bridge callbacks are serialized on the platform UI thread. */
class FaceViewModel(val bridge:FaceBridge) {
    var onTerminal: DemoTerminalObserver? = null
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
        if(!available.available){state=FaceState(error=available.reason?:"unavailable");if(!streaming)terminal(DemoRecordOutcome.FAILED);return}
        val current=++generation;state=FaceState(busy=true,result=previous,streaming=streaming)
        var delivered = false
        val task=bridge.detect(inputId,selection,object:FaceCallback {
            override fun onResult(result:BridgeFaceResult){if(current==generation && !delivered){delivered=true;state=FaceState(result=result,streaming=streaming);active=null;if(!streaming)terminal(DemoRecordOutcome.COMPLETED)}}
            override fun onError(message:String){if(current==generation && !delivered){delivered=true;state=FaceState(error=message,result=previous,streaming=streaming);active=null;if(!streaming)terminal(DemoRecordOutcome.FAILED)}}
            override fun onCancelled(){if(current==generation && !delivered){delivered=true;state=FaceState(cancelled=true);active=null;if(!streaming)terminal(DemoRecordOutcome.CANCELLED)}}
        })
        // A synchronous error callback may already have finished the task.
        if(state.busy && generation==current)active=task
    }
    fun cancel(){val record=state.busy && !state.streaming;generation++;active?.cancel();active=null;state=state.copy(busy=false,error=null,cancelled=true,streaming=false);if(record)terminal(DemoRecordOutcome.CANCELLED)}
    private fun terminal(outcome: DemoRecordOutcome) { onTerminal?.invoke(DemoCapabilityId.FACE_DETECTION, outcome, "图片人脸检测 · $outcome") }
    fun permissionDenied(){cancel();state=FaceState(error="permissionDenied")}
    fun close(){if(!closed){closed=true;cancel();bridge.close()}}
}
