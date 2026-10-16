package io.ondeviceai.face
import android.graphics.Bitmap
import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
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
    val backendId: String
    suspend fun availability(): CapabilityAvailability
    suspend fun detect(input: ImageInput, options: FaceOptions=FaceOptions()): FaceResult
    suspend fun close()
}
enum class BackendSelection { Default, Explicit }
data class FaceBackendSelection(val selection: BackendSelection = BackendSelection.Default, val backendId: String? = null) {
    companion object { fun explicit(backendId: String) = FaceBackendSelection(BackendSelection.Explicit, backendId) }
}
data class FaceBackendDescriptor(val capabilityId: String, val backendId: String, val availability: CapabilityAvailability, val supportedOptionIds: Set<String>)
data class FaceDetectionRegistration(val capabilityId: String, val backendId: String, val detector: FaceDetector, val supportedOptionIds: Set<String> = emptySet())
sealed class FaceDetectionClientError(message: String): IllegalArgumentException(message) {
    data class BackendNotRegistered(val capabilityId: String, val backendId: String): FaceDetectionClientError("backend not registered: $backendId")
    data class BackendUnavailable(val capabilityId: String, val backendId: String, val reason: io.ondeviceai.core.UnavailableReason): FaceDetectionClientError("backend unavailable: $backendId")
    data class UnsupportedOption(val capabilityId: String, val backendId: String, val optionId: String): FaceDetectionClientError("unsupported option: $optionId")
}
class FaceDetectionClient(defaultBackendId: String, registrations: List<FaceDetectionRegistration>) {
    companion object { const val CAPABILITY_ID = "face.detection" }
    private val defaultId = defaultBackendId
    private val table: Map<String, FaceDetectionRegistration>
    private var closed = false
    init {
        require(defaultBackendId.isNotEmpty())
        require(registrations.all { it.capabilityId == CAPABILITY_ID && it.backendId == it.detector.backendId && it.backendId.isNotEmpty() })
        require(registrations.map { it.backendId }.toSet().size == registrations.size)
        require(registrations.any { it.backendId == defaultBackendId })
        table = registrations.associateBy { it.backendId }
    }
    suspend fun listBackends(): List<FaceBackendDescriptor> = table.values.map {
        FaceBackendDescriptor(CAPABILITY_ID, it.backendId, it.detector.availability(), it.supportedOptionIds)
    }.sortedBy { it.backendId }
    suspend fun select(selection: FaceBackendSelection = FaceBackendSelection(), optionIds: Set<String> = emptySet()): FaceBackendDescriptor {
        check(!closed) { "closed" }
        val id = if (selection.selection == BackendSelection.Default) defaultId else selection.backendId ?: ""
        val registration = table[id] ?: throw FaceDetectionClientError.BackendNotRegistered(CAPABILITY_ID, id)
        val availability = registration.detector.availability()
        if (availability.status != CapabilityStatus.AVAILABLE) {
            throw FaceDetectionClientError.BackendUnavailable(CAPABILITY_ID, id, availability.reason ?: UnavailableReason.UNSUPPORTED_DEVICE)
        }
        optionIds.firstOrNull { it !in registration.supportedOptionIds }?.let {
            throw FaceDetectionClientError.UnsupportedOption(CAPABILITY_ID, id, it)
        }
        return FaceBackendDescriptor(CAPABILITY_ID, id, availability, registration.supportedOptionIds)
    }
    suspend fun detect(input: ImageInput, options: FaceOptions = FaceOptions(), optionIds: Set<String> = emptySet(), selection: FaceBackendSelection = FaceBackendSelection()): FaceResult {
        val descriptor = select(selection, optionIds)
        return table.getValue(descriptor.backendId).detector.detect(input, options)
    }
    suspend fun close() {
        if (closed) return
        closed = true
        table.values.forEach { it.detector.close() }
    }
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
