package io.ondeviceai.demo
import io.ondeviceai.binding.*
import kotlin.test.*
class FakeBridge:FaceBridge {
    val callbacks=mutableListOf<FaceCallback>();var cancellations=0;var closed=false
    val selections=mutableListOf<BridgeBackendSelection>()
    override fun backendId()="fake"
    override fun availability()=BridgeAvailability(true,null,"fake","15.0")
    override fun detect(inputId:String,selection:BridgeBackendSelection,callback:FaceCallback):BridgeTask {selections+=selection;callbacks+=callback;return object:BridgeTask {override fun cancel(){cancellations++}}}
    override fun close(){closed=true}
}
class FaceViewModelTest {
    @Test fun backendSelectionReachesBridgeWithoutFallback() {
        val bridge=FakeBridge();val vm=FaceViewModel(bridge)
        vm.detect("default")
        vm.detect("explicit", BridgeBackendSelection("test.face.cloud"))
        assertEquals(listOf(null,"test.face.cloud"),bridge.selections.map{it.backendId})
    }
    @Test fun nextCameraFrameKeepsLastResultAndStableControls() {
        val bridge=FakeBridge();val vm=FaceViewModel(bridge)
        val result=BridgeFaceResult("first",640,480,"fake",emptyList(),10.0,null)
        vm.detectFrame("first");bridge.callbacks[0].onResult(result)
        vm.detectFrame("second")
        assertTrue(vm.state.streaming);assertTrue(vm.state.busy)
        assertEquals(result,vm.state.result)
        assertFalse(vm.state.showProgress);assertTrue(vm.state.canSelectInput)
        bridge.callbacks[1].onResult(result.copy(frameId="second"))
        assertTrue(vm.state.streaming);assertFalse(vm.state.showProgress);assertTrue(vm.state.canSelectInput)
        vm.cancel();assertFalse(vm.state.streaming)
    }
    @Test fun selectingPhotoExitsStreamingAndShowsSingleImageProgress() {
        val bridge=FakeBridge();val vm=FaceViewModel(bridge)
        vm.detectFrame("camera");vm.detect("photo")
        assertFalse(vm.state.streaming);assertTrue(vm.state.showProgress);assertFalse(vm.state.canSelectInput)
        bridge.callbacks[0].onError("late camera error");assertNull(vm.state.error)
    }
    @Test fun staleResultCannotReplaceNewSelection() {
        val bridge=FakeBridge();val vm=FaceViewModel(bridge)
        vm.detect("old");vm.detect("new")
        bridge.callbacks[0].onError("late failure")
        assertTrue(vm.state.busy);assertNull(vm.state.error)
        vm.cancel();assertFalse(vm.state.busy);assertEquals(2,bridge.cancellations)
        bridge.callbacks[1].onError("late after cancel");assertNull(vm.state.error)
    }
    @Test fun permissionAndLifecycleAreExplicit() {
        val bridge=FakeBridge();val vm=FaceViewModel(bridge)
        vm.permissionDenied();assertEquals("permissionDenied",vm.state.error)
        vm.close();assertTrue(bridge.closed)
    }
}
