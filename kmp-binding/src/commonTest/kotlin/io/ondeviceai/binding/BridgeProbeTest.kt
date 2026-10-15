package io.ondeviceai.binding
import kotlin.test.*
class BridgeProbeTest {
 @Test fun injectedBackendIsUsed() {
  val bridge=object:FaceBridge {
   override fun backendId()="apple.vision"
   override fun availability()=BridgeAvailability(true,null,backendId(),"15.0")
   override fun detect(inputId:String,callback:FaceCallback):BridgeTask=error("not used")
   override fun close(){}
  }
  assertEquals("apple.vision",probeBackend(bridge))
 }
}
