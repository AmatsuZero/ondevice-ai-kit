package io.ondeviceai.demo

import android.app.Instrumentation
import android.graphics.*
import android.os.Bundle
import io.ondeviceai.binding.*
import io.ondeviceai.identity.IdentityError
import io.ondeviceai.identity.IdentityErrorCode as NativeError
import io.ondeviceai.identity.IdentityProbe
import io.ondeviceai.modelstore.*
import io.ondeviceai.opencvidentity.*
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.*
import org.json.JSONObject
import org.opencv.core.*
import org.opencv.objdetect.FaceRecognizerSF

/** Headless real-model test with one explicitly approved NASA public-domain fixture. No camera. */
class IdentityModelInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val root = targetContext.filesDir.resolve("identity-validation").apply { mkdirs() }
        val report = JSONObject().put("runId", java.util.UUID.randomUUID().toString()).put("status", "running")
            .put("detectionAlignmentVerdict", "unverified")
        val result = Bundle()
        try {
            runBlocking {
                val manifest = OpenCvIdentityModel.manifest()
                val models = ModelStore(root.resolve("store"), ModelCompatibility(manifest.backendId,
                    OpenCvIdentityModel.VERSION, android.os.Build.VERSION.SDK_INT.toString(), android.os.Build.SUPPORTED_ABIS.first()))
                models.install(manifest, root.resolve("models"))
                val session = OpenCvPrototypeSession.open(targetContext, models)
                try {
                    try { models.remove(manifest.ref); error("model lease not retained") } catch (_: ModelInUseException) { }
                    val blank = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
                    val bytes = encode(blank)
                    try {
                        check(session.backend.evaluateQuality(IdentityProbe(bytes, "procedural-blank-v1")).faceCount == 0)
                        try { session.backend.embed(IdentityProbe(bytes, "procedural-blank-v1")); error("blank embedded") }
                        catch (error: IdentityError) { check(error.code == NativeError.INVALID_INPUT) }
                    } finally { bytes.fill(0) }
                    val emptyResult = enroll(session, blank)
                    check(emptyResult.outcome == IdentityEnrollmentOutcome.INSUFFICIENT_QUALITY && emptyResult.template == null)
                    blank.recycle()
                    report.put("blankRejected", true).put("bridgeNoFaceEnrollment", true)

                    val fixture = root.resolve("authorized-astronaut.png")
                    val fixtureHash = java.security.MessageDigest.getInstance("SHA-256").digest(fixture.readBytes()).joinToString("") { "%02x".format(it) }
                    check(fixtureHash == "88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5")
                    val portrait = checkNotNull(BitmapFactory.decodeFile(fixture.absolutePath))
                    try {
                        val portraitBytes = encode(portrait)
                        try {
                            val quality = session.backend.evaluateQuality(IdentityProbe(portraitBytes, "approved-astronaut-v1"))
                            check(quality.faceCount == 1 && quality.score == 1.0)
                            val embedded = session.backend.embed(IdentityProbe(portraitBytes, "approved-astronaut-v1"))
                            check(embedded.values.size == 128)
                            check(kotlin.math.abs(embedded.values.sumOf { it.toDouble() * it } - 1.0) < .00001)
                        } finally { portraitBytes.fill(0) }
                        val enrolled = enroll(session, portrait)
                        check(enrolled.outcome == IdentityEnrollmentOutcome.ENROLLED && enrolled.template != null)
                        val failure = CompletableDeferred<IdentityBridgeFailure>()
                        session.bridge.verify(session.probe(portrait), checkNotNull(enrolled.template), session.configuration.grant,
                            checkNotNull(session.configuration.verificationOptions), callback = object : IdentityCallback<IdentityVerificationResult> {
                                override fun onSuccess(value: IdentityVerificationResult) { failure.completeExceptionally(IllegalStateException("uncalibrated identity outcome")) }
                                override fun onFailure(error: IdentityBridgeFailure) { failure.complete(error) }
                            })
                        check((failure.await() as? IdentityBridgeFailure.Rejected)?.code == io.ondeviceai.binding.IdentityErrorCode.THRESHOLD_UNVERIFIED)
                        val galleryFailure = CompletableDeferred<IdentityBridgeFailure>()
                        session.bridge.createGalleryBuilder(null, session.configuration.grant,
                            IdentityGalleryOptions(IdentityThresholdPolicy(OpenCvIdentityModel.unverifiedPolicy(
                                io.ondeviceai.identity.IdentityPolicyKind.IDENTIFICATION).canonicalJSON()), "subject-max-v1"),
                            callback = object : IdentityCallback<IdentityGalleryBuilderReference> {
                                override fun onSuccess(value: IdentityGalleryBuilderReference) { galleryFailure.completeExceptionally(IllegalStateException("uncalibrated gallery admitted")) }
                                override fun onFailure(error: IdentityBridgeFailure) { galleryFailure.complete(error) }
                            })
                        check((galleryFailure.await() as? IdentityBridgeFailure.Rejected)?.code == io.ondeviceai.binding.IdentityErrorCode.THRESHOLD_UNVERIFIED)
                        report.put("unverifiedGalleryRejected", true)
                        report.put("unverifiedPolicyRejected", true).put("detectionAlignmentVerdict", "passed")
                            .put("approvedFixtureEnrolled", true).put("backendEmbeddingDimension", 128)
                            .put("fixtureSha256", fixtureHash)
                    } finally { portrait.recycle() }

                    // Exercise the actual SFace weights on a procedural aligned RGB raster.
                    val recognizer = FaceRecognizerSF.create(root.resolve("models/${OpenCvIdentityModel.RECOGNIZER}").absolutePath, "")
                    val aligned = Mat(112, 112, CvType.CV_8UC3, Scalar(80.0, 120.0, 160.0))
                    val feature = Mat()
                    try {
                        recognizer.feature(aligned, feature)
                        check(feature.total() == 128L && feature.type() == CvType.CV_32FC1)
                        val values = FloatArray(128); feature.get(0, 0, values)
                        check(values.all { it.isFinite() } && values.any { it != 0f })
                        values.fill(0f)
                        report.put("nativeSFaceDimension", 128).put("nativeSFaceFiniteNonzero", true)
                    } finally { aligned.release(); feature.release() }
                } finally { session.deleteAndClose() }
                check(session.backend.availability().status.name == "UNAVAILABLE")
                session.deleteAndClose()
                models.remove(manifest.ref)
                check(models.status(manifest.ref) == ModelStatus.ABSENT)
                report.put("modelLeaseReleased", true).put("sessionDeletedAndClosed", true)
                    .put("backend", OpenCvIdentityModel.BACKEND_ID).put("runtime", Core.VERSION)
                    .put("device", android.os.Build.MODEL).put("sdk", android.os.Build.VERSION.SDK_INT)
                    .put("qualityVerdict", "unverified").put("status", "passed")
            }
            root.resolve("report.json").writeText(report.toString(2))
            result.putString("status", "passed"); finish(0, result)
        } catch (error: Throwable) {
            report.put("status", "failed").put("error", error.stackTraceToString())
            root.resolve("report.json").writeText(report.toString(2))
            result.putString("status", "failed"); result.putString("error", error.stackTraceToString()); finish(1, result)
        }
    }
    private suspend fun enroll(session: OpenCvPrototypeSession, image: Bitmap): IdentityEnrollmentResult {
        val reply = CompletableDeferred<IdentityEnrollmentResult>()
        session.bridge.enroll(session.probe(image), checkNotNull(session.configuration.candidate), session.configuration.grant,
            checkNotNull(session.configuration.enrollmentOptions), callback = object : IdentityCallback<IdentityEnrollmentResult> {
                override fun onSuccess(value: IdentityEnrollmentResult) { reply.complete(value) }
                override fun onFailure(error: IdentityBridgeFailure) { reply.completeExceptionally(IllegalStateException(error.toString())) }
            })
        return reply.await()
    }
    private fun encode(bitmap: Bitmap) = ByteArrayOutputStream().use { output -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); output.toByteArray() }

}
