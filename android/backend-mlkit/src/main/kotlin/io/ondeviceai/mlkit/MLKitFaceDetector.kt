package io.ondeviceai.mlkit
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import io.ondeviceai.core.*
import io.ondeviceai.face.*
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/** Offline bundled ML Kit model; the detector is constructed lazily on first inference. */
class MLKitFaceDetector: FaceDetector {
    private val client=lazy {FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE).setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL).build())}
    private val session=DetectorSession {if(client.isInitialized())client.value.close()}
    override suspend fun availability():CapabilityAvailability {
        val closed=session.isClosed()
        return CapabilityAvailability(if(closed)CapabilityStatus.UNAVAILABLE else CapabilityStatus.AVAILABLE,if(closed)UnavailableReason.CLOSED else null,"android.mlkit.face.bundled","26")
    }
    override suspend fun detect(input:ImageInput,options:FaceOptions):FaceResult = session.execute {
        val faces=runBackend(input,options)
        FaceResult(input.frameId,input.timestampNs,input.orientedWidth,input.orientedHeight,ImageGeometry.preview(input.orientedWidth,input.orientedHeight,input.orientedWidth.toDouble(),input.orientedHeight.toDouble(),false),"android.mlkit.face.bundled",faces)
    }
    private suspend fun runBackend(input:ImageInput,options:FaceOptions):List<FaceObservation> = withContext(NonCancellable) {
        val image=InputImage.fromBitmap(input.bitmap,input.rotationDegrees)
        val faces=suspendCoroutine { continuation ->
            client.value.process(image).addOnSuccessListener {continuation.resume(it)}.addOnFailureListener {continuation.resumeWithException(it)}
        }
        val names=mapOf(FaceLandmark.LEFT_EYE to "leftEye",FaceLandmark.RIGHT_EYE to "rightEye",FaceLandmark.NOSE_BASE to "nose",FaceLandmark.MOUTH_LEFT to "mouthLeft",FaceLandmark.MOUTH_RIGHT to "mouthRight",FaceLandmark.MOUTH_BOTTOM to "mouthBottom",FaceLandmark.LEFT_EAR to "leftEar",FaceLandmark.RIGHT_EAR to "rightEar",FaceLandmark.LEFT_CHEEK to "leftCheek",FaceLandmark.RIGHT_CHEEK to "rightCheek")
        faces.map {face ->
            val b=face.boundingBox
            val landmarks=if(options.landmarks)names.mapNotNull {(type,name)->face.getLandmark(type)?.let {name to listOf(FacePoint(it.position.x/input.orientedWidth.toDouble(),it.position.y/input.orientedHeight.toDouble()))}}.toMap() else emptyMap()
            // Pose axes retain backend semantics (radians); no cross-backend quality-score equivalence.
            FaceObservation(ImageGeometry.pixelRect(b.left.toDouble(),b.top.toDouble(),b.width().toDouble(),b.height().toDouble(),input.orientedWidth,input.orientedHeight),landmarks,"mlkit.semantic-points",Math.toRadians(face.headEulerAngleY.toDouble()),Math.toRadians(face.headEulerAngleX.toDouble()),Math.toRadians(face.headEulerAngleZ.toDouble()))
        }
    }
    override suspend fun close() = session.close()
}
