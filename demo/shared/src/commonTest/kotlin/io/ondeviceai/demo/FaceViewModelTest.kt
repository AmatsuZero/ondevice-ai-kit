package io.ondeviceai.demo
import io.ondeviceai.binding.*
import kotlin.test.*
class FakeBridge:FaceBridge {
    val callbacks=mutableListOf<FaceCallback>();var cancellations=0;var closed=false
    override fun backendId()="fake"
    override fun availability()=BridgeAvailability(true,null,"fake","15.0")
    override fun detect(inputId:String,callback:FaceCallback):BridgeTask {callbacks+=callback;return object:BridgeTask {override fun cancel(){cancellations++}}}
    override fun close(){closed=true}
}
class FaceViewModelTest {
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
