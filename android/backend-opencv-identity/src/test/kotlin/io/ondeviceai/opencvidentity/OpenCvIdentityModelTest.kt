package io.ondeviceai.opencvidentity

import io.ondeviceai.identity.*
import kotlin.math.sqrt
import kotlin.test.*
import org.junit.Test

class OpenCvIdentityModelTest {
    @Test fun modelBindingIncludesBothExactAssetsAndPreprocessing() {
        val model = OpenCvIdentityModel.manifest()
        assertEquals(2, model.files.size)
        assertEquals(38_928_942L, model.resources.downloadBytes)
        assertEquals("8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4", model.files[0].sha256)
        assertEquals("0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79", model.files[1].sha256)
        val space = OpenCvIdentityModel.embeddingSpace
        assertEquals(model.modelId, space.modelBinding.id)
        assertEquals(model.revision, space.modelBinding.revision)
        assertEquals(model.processing.preprocessRevision, space.preprocessRevision)
        assertEquals(128, space.dimension)
        assertEquals("l2", space.normalization)
        assertEquals("cosine", space.scoreMetric)
    }

    @Test fun featuresAreNormalizedWithoutChangingDirection() {
        val raw = FloatArray(128).also { it[0] = 3f; it[1] = 4f }
        val result = SFaceFeatures.normalized(raw)
        assertEquals(.6f, result[0], .000001f)
        assertEquals(.8f, result[1], .000001f)
        assertEquals(1.0, sqrt(result.sumOf { it.toDouble() * it }), .000001)
        assertEquals(3f, raw[0])
    }

    @Test fun malformedNativeFeaturesCannotEnterIdentityStore() {
        for (raw in listOf(FloatArray(127), FloatArray(128), FloatArray(128) { Float.NaN }, FloatArray(128) { Float.POSITIVE_INFINITY })) {
            assertEquals(IdentityErrorCode.INVALID_INPUT, assertFailsWith<IdentityError> { SFaceFeatures.normalized(raw) }.code)
        }
    }

    @Test fun realModelDoesNotCreateAVerifiedRecognitionPolicy() {
        val policy = OpenCvIdentityModel.unverifiedPolicy(IdentityPolicyKind.VERIFICATION)
        assertEquals(CalibrationStatus.UNVERIFIED, policy.calibrationStatus)
        assertNull(policy.threshold)
        assertNull(policy.datasetRevision)
        assertEquals(IdentityErrorCode.THRESHOLD_UNVERIFIED, assertFailsWith<IdentityError> { policy.requireVerified() }.code)
    }

    @Test fun geometricScreenRejectsInvalidLandmarks() {
        val face = floatArrayOf(10f, 10f, 90f, 100f, 35f, 40f, 75f, 40f, 55f, 60f, 40f, 85f, 70f, 85f, .99f)
        assertTrue(FaceGeometry.usable(face, 128, 128))
        assertFalse(FaceGeometry.usable(face.copyOf().also { it[6] = it[4]; it[7] = it[5] }, 128, 128))
        assertFalse(FaceGeometry.usable(face.copyOf().also { it[8] = Float.NaN }, 128, 128))
        assertFalse(FaceGeometry.usable(face.copyOf().also { it[4] = -1f }, 128, 128))
    }

    @Test fun diagnosticsDistinguishFailuresWithoutCopyingPrivateMessages() {
        val failure = OpenCvIdentityDiagnostic.from(OpenCvIdentityStage.MODEL_IMPORT,
            java.io.IOException("private/input/path and raw content"))
        assertEquals(OpenCvIdentityFailureKind.IO, failure.kind)
        assertEquals(OpenCvIdentityStage.MODEL_IMPORT, failure.stage)
        assertFalse(failure.toString().contains("private"))
        assertEquals(OpenCvIdentityFailureKind.RUNTIME_LINKAGE,
            OpenCvIdentityDiagnostic.from(OpenCvIdentityStage.RUNTIME_LOAD, UnsatisfiedLinkError("private library path")).kind)
        assertEquals(OpenCvIdentityFailureKind.MODEL_INTEGRITY,
            OpenCvIdentityDiagnostic.from(OpenCvIdentityStage.MODEL_STORE, io.ondeviceai.modelstore.ModelIntegrityException()).kind)
    }
}
