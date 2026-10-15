package io.ondeviceai.mlkit
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import io.ondeviceai.core.*
import io.ondeviceai.face.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import java.util.UUID

/** Offline bundled ML Kit model; the detector is constructed lazily on first inference. */
class MLKitFaceDetector: FaceDetector {
    private val gate=TaskGate()
    private val mutex=Mutex()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    private var closed=false
    private var active:Deferred<List<FaceObservation>>?=null
    private val client=lazy {FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE).setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL).build())}
    override suspend fun availability():CapabilityAvailability=mutex.withLock {
        CapabilityAvailability(if(closed)CapabilityStatus.UNAVAILABLE else CapabilityStatus.AVAILABLE,if(closed)UnavailableReason.CLOSED else null,"android.mlkit.face.bundled","26")
    }
    override suspend fun detect(input:ImageInput,options:FaceOptions):FaceResult {
        currentCoroutineContext().ensureActive();val id=UUID.randomUUID().toString()
        val worker=mutex.withLock {
            if(closed)throw ClosedException();gate.accept(id)
            scope.async {runBackend(input,options)}.also {active=it}
        }
        try {
            val faces=worker.await();currentCoroutineContext().ensureActive()
            gate.finish(id,TaskTerminal.COMPLETED)
            return FaceResult(input.frameId,input.timestampNs,input.orientedWidth,input.orientedHeight,ImageGeometry.preview(input.orientedWidth,input.orientedHeight,input.orientedWidth.toDouble(),input.orientedHeight.toDouble(),false),"android.mlkit.face.bundled",faces)
        } catch(error:Exception) {
            gate.finish(id,if(error is CancellationException)TaskTerminal.CANCELLED else TaskTerminal.FAILED);throw error
        } finally {
            // Google Task has no inference cancellation API; retain input until its completion callback.
            withContext(NonCancellable) {worker.cancel();worker.join();mutex.withLock {active=null;gate.release(id)}}
        }
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
    override suspend fun close() {
        mutex.withLock {closed=true;active?.cancel()};gate.close()
        mutex.withLock {if(client.isInitialized())client.value.close();scope.cancel()}
    }
}
