package io.ondeviceai.opencvidentity

import android.graphics.BitmapFactory
import io.ondeviceai.core.*
import io.ondeviceai.identity.*
import io.ondeviceai.modelstore.*
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.dnn.Dnn
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceDetectorYN
import org.opencv.objdetect.FaceRecognizerSF
import kotlin.math.roundToInt

/** CPU image primitives. Identity engines retain all authorization and policy decisions.
 * Inputs must be upright; EXIF orientation is intentionally ignored.
 * Cancellation waits for blocking DNN calls. Call close off the UI thread.
 */
class OpenCvIdentityBackend private constructor(private val lease: ModelLease,
    private val diagnostic: (OpenCvIdentityDiagnostic) -> Unit) : IdentityBackend {
    override val embeddingSpace = OpenCvIdentityModel.embeddingSpace
    override val backendId = embeddingSpace.backendId
    override val backendVersion = embeddingSpace.backendVersion
    override val modelBinding = embeddingSpace.modelBinding
    private val gate = Any()
    private val closed = AtomicBoolean(false)
    private val released = CompletableDeferred<Unit>()
    private var detector: FaceDetectorYN? = null
    private var recognizer: FaceRecognizerSF? = null

    init {
        var stage = OpenCvIdentityStage.RUNTIME_LOAD
        try {
        check(OpenCVLoader.initLocal() && Core.VERSION == OpenCvIdentityModel.VERSION)
        stage = OpenCvIdentityStage.DETECTOR_LOAD
        detector = FaceDetectorYN.create(File(lease.directory, OpenCvIdentityModel.DETECTOR).absolutePath, "",
            Size(640.0, 640.0), .9f, .3f, 5000, Dnn.DNN_BACKEND_OPENCV, Dnn.DNN_TARGET_CPU)
        stage = OpenCvIdentityStage.RECOGNIZER_LOAD
        recognizer = FaceRecognizerSF.create(File(lease.directory, OpenCvIdentityModel.RECOGNIZER).absolutePath, "",
            Dnn.DNN_BACKEND_OPENCV, Dnn.DNN_TARGET_CPU)
        } catch (failure: LinkageError) {
            reportIdentityDiagnostic(diagnostic, stage, failure); throw error(IdentityErrorCode.BACKEND_UNAVAILABLE)
        } catch (failure: Exception) {
            reportIdentityDiagnostic(diagnostic, stage, failure); throw error(IdentityErrorCode.BACKEND_UNAVAILABLE)
        }
    }

    companion object {
        suspend fun open(store: ModelStore, diagnostic: (OpenCvIdentityDiagnostic) -> Unit = {}): OpenCvIdentityBackend {
            val lease = try { store.acquire(OpenCvIdentityModel.ref) }
                catch (failure: CancellationException) { throw failure }
                catch (failure: Exception) { reportIdentityDiagnostic(diagnostic, OpenCvIdentityStage.MODEL_STORE, failure); throw failure }
            try {
                return withContext(Dispatchers.IO) {
                    if (lease.manifest != OpenCvIdentityModel.manifest()) {
                        val failure = IdentityError(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH)
                        reportIdentityDiagnostic(diagnostic, OpenCvIdentityStage.MODEL_BINDING, failure)
                        throw failure
                    }
                    ensureActive()
                    OpenCvIdentityBackend(lease, diagnostic)
                }
            } catch (error: Throwable) {
                withContext(NonCancellable) { lease.close() }
                throw error
            }
        }
    }

    override fun availability() = CapabilityAvailability(
        if (closed.get()) CapabilityStatus.UNAVAILABLE else CapabilityStatus.AVAILABLE,
        if (closed.get()) UnavailableReason.CLOSED else null, backendId)

    override fun evaluateQuality(probe: IdentityProbe) = evaluateQuality(probe, IdentityStopSignal())
    override fun evaluateQuality(probe: IdentityProbe, stopSignal: IdentityStopSignal): IdentityQuality =
        image(probe, stopSignal) { input, faces ->
            val usable = faces.rows() == 1 && FaceGeometry.usable(faceData(faces), input.cols(), input.rows())
            IdentityQuality(faces.rows(), if (usable) 1.0 else 0.0)
        }

    override fun embed(probe: IdentityProbe) = embed(probe, IdentityStopSignal())
    override fun embed(probe: IdentityProbe, stopSignal: IdentityStopSignal): IdentityEmbedding =
        image(probe, stopSignal) { input, faces ->
            if (faces.rows() > 1) throw error(IdentityErrorCode.MULTIPLE_FACES)
            if (faces.rows() != 1 || !FaceGeometry.usable(faceData(faces), input.cols(), input.rows())) throw error(IdentityErrorCode.INVALID_INPUT)
            val aligned = Mat()
            val feature = Mat()
            try {
                val runtime = checkNotNull(recognizer)
                native(OpenCvIdentityStage.ALIGNMENT) { runtime.alignCrop(input, faces, aligned) }
                stopSignal.check()
                native(OpenCvIdentityStage.EMBEDDING) { runtime.feature(aligned, feature) }
                stopSignal.check()
                if (feature.total() != 128L || feature.type() != CvType.CV_32FC1) throw error(IdentityErrorCode.INVALID_INPUT)
                val raw = FloatArray(128)
                feature.get(0, 0, raw)
                IdentityEmbedding(embeddingSpace, SFaceFeatures.normalized(raw))
            } finally { aligned.release(); feature.release() }
        }

    private fun faceData(faces: Mat): FloatArray = FloatArray(15).also { faces.get(0, 0, it) }
    private fun error(code: IdentityErrorCode) = IdentityError(code, backendId, modelBinding)
    private inline fun <T> native(stage: OpenCvIdentityStage, work: () -> T): T = try { work() }
        catch (failure: Exception) {
            reportIdentityDiagnostic(diagnostic, stage, failure)
            throw error(IdentityErrorCode.BACKEND_UNAVAILABLE)
        }

    private fun <T> image(probe: IdentityProbe, signal: IdentityStopSignal, body: (Mat, Mat) -> T): T {
        if (closed.get()) throw error(IdentityErrorCode.CLOSED)
        synchronized(gate) {
            if (closed.get()) throw error(IdentityErrorCode.CLOSED)
            signal.check()
            val bytes = probe.encodedImage ?: throw error(IdentityErrorCode.INVALID_INPUT)
            if (bytes.isEmpty() || bytes.size > 10 * 1024 * 1024) throw error(IdentityErrorCode.INVALID_INPUT)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 16..4096 || bounds.outHeight !in 16..4096 || bounds.outWidth.toLong() * bounds.outHeight > 4_194_304) throw error(IdentityErrorCode.INVALID_INPUT)
            val encoded = MatOfByte(*bytes)
            var decoded: Mat? = null
            val input = Mat()
            val faces = Mat()
            var stage = OpenCvIdentityStage.IMAGE_DECODE
            try {
                decoded = Imgcodecs.imdecode(encoded, Imgcodecs.IMREAD_COLOR or Imgcodecs.IMREAD_IGNORE_ORIENTATION)
                if (decoded.empty()) throw error(IdentityErrorCode.INVALID_INPUT)
                val scale = minOf(1.0, 640.0 / maxOf(decoded.cols(), decoded.rows()))
                Imgproc.resize(decoded, input, Size(maxOf(1, (decoded.cols() * scale).roundToInt()).toDouble(),
                    maxOf(1, (decoded.rows() * scale).roundToInt()).toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)
                signal.check()
                stage = OpenCvIdentityStage.DETECTION
                checkNotNull(detector).setInputSize(input.size())
                checkNotNull(detector).detect(input, faces)
                signal.check()
                val value = body(input, faces)
                signal.check()
                if (closed.get()) throw error(IdentityErrorCode.CLOSED)
                return value
            } catch (failure: IdentityError) { signal.check(); throw failure }
            catch (failure: Exception) {
                signal.check(); reportIdentityDiagnostic(diagnostic, stage, failure)
                throw error(IdentityErrorCode.BACKEND_UNAVAILABLE)
            }
            finally { faces.release(); input.release(); decoded?.release(); encoded.release(); bytes.fill(0) }
        }
    }

    /** Waits for admitted native work. Java network wrappers themselves are GC-finalized. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(gate) { detector = null; recognizer = null }
        CoroutineScope(Dispatchers.IO).launch {
            try { lease.close(); released.complete(Unit) }
            catch (error: Throwable) { released.completeExceptionally(error) }
        }
    }
    suspend fun closeAndWait() { close(); released.await() }
}
