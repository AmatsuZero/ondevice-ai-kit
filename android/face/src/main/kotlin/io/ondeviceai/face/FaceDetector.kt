package io.ondeviceai.face
import android.graphics.Bitmap
import io.ondeviceai.core.CapabilityAvailability
import java.util.UUID

data class FacePoint(val x: Double,val y: Double)
data class FaceRect(val x: Double,val y: Double,val width: Double,val height: Double)
data class PreviewTransform(val scaleX: Double,val scaleY: Double,val offsetX: Double,val offsetY: Double) {
    fun apply(p: FacePoint) = FacePoint(p.x*scaleX+offsetX,p.y*scaleY+offsetY)
}
data class FaceObservation(val box: FaceRect,val landmarks: Map<String,List<FacePoint>>,val landmarkTopology: String,val yaw: Double?=null,val pitch: Double?=null,val roll: Double?=null,val quality: Double?=null,val confidence: Double?=null,val trackingId: String?=null)
data class FaceResult(val frameId: String,val timestampNs: Long,val orientedWidth: Int,val orientedHeight: Int,val previewTransform: PreviewTransform,val backendId: String,val faces: List<FaceObservation>)
/** Caller must not recycle/mutate the bitmap until detect returns, including cancellation cleanup. */
data class ImageInput(val bitmap: Bitmap,val rotationDegrees: Int=0,val frameId: String=UUID.randomUUID().toString(),val timestampNs: Long=System.nanoTime()) {
    init { require(rotationDegrees in listOf(0,90,180,270)) }
    val orientedWidth: Int get() = if(rotationDegrees%180==0) bitmap.width else bitmap.height
    val orientedHeight: Int get() = if(rotationDegrees%180==0) bitmap.height else bitmap.width
}
data class FaceOptions(val landmarks: Boolean=true)
interface FaceDetector {
    suspend fun availability(): CapabilityAvailability
    suspend fun detect(input: ImageInput, options: FaceOptions=FaceOptions()): FaceResult
    suspend fun close()
}
object ImageGeometry {
    fun rotate(p: FacePoint, clockwiseDegrees: Int) = when(clockwiseDegrees) {
        0 -> p; 90 -> FacePoint(1-p.y,p.x); 180 -> FacePoint(1-p.x,1-p.y); 270 -> FacePoint(p.y,1-p.x)
        else -> throw IllegalArgumentException("rotation must be 0/90/180/270")
    }
    fun pixelRect(x:Double,y:Double,width:Double,height:Double,imageWidth:Int,imageHeight:Int): FaceRect {
        require(imageWidth>0 && imageHeight>0);return FaceRect(x/imageWidth,y/imageHeight,width/imageWidth,height/imageHeight)
    }
    fun preview(imageWidth:Int,imageHeight:Int,viewWidth:Double,viewHeight:Double,mirrored:Boolean): PreviewTransform {
        require(imageWidth>0 && imageHeight>0 && viewWidth>0 && viewHeight>0 && viewWidth.isFinite() && viewHeight.isFinite())
        val scale=maxOf(viewWidth/imageWidth,viewHeight/imageHeight);val w=imageWidth*scale;val h=imageHeight*scale
        return PreviewTransform(if(mirrored) -w else w,h,if(mirrored)(viewWidth+w)/2 else (viewWidth-w)/2,(viewHeight-h)/2)
    }
}
