package io.ondeviceai.binding

data class BridgeAvailability(val available:Boolean,val reason:String?,val backendId:String,val minimumOS:String?)
data class BridgeBox(val x:Double,val y:Double,val width:Double,val height:Double)
data class BridgePoint(val x:Double,val y:Double)
data class BridgeLandmark(val name:String,val points:List<BridgePoint>)
data class BridgeFace(val box:BridgeBox,val yaw:Double?,val pitch:Double?,val roll:Double?,val quality:Double?,val confidence:Double?,val landmarks:List<BridgeLandmark>,val topology:String)
data class BridgeFaceResult(val frameId:String,val width:Int,val height:Int,val backendId:String,val faces:List<BridgeFace>,val elapsedMs:Double,val benchmarkJson:String?)
interface FaceCallback {
    fun onResult(result:BridgeFaceResult)
    fun onError(message:String)
    fun onCancelled()
}
interface BridgeTask {fun cancel()}
interface FaceBridge {
    fun backendId():String
    fun availability():BridgeAvailability
    fun detect(inputId:String,callback:FaceCallback):BridgeTask
    fun close()
}
interface DemoActions {
    fun selectPhoto()
    fun startCamera()
    fun stopCamera()
    fun runBenchmark()
    fun exportReport(json:String)
}
fun probeBackend(bridge:FaceBridge):String=bridge.backendId()
