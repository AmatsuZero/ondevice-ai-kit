package io.ondeviceai.face

import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class FixtureBackend(override val backendId: String, private val available: Boolean = true) : FaceDetector {
    var availabilityCalls = 0
    var closeCalls = 0
    override suspend fun availability(): CapabilityAvailability {
        availabilityCalls++
        return CapabilityAvailability(if (available) CapabilityStatus.AVAILABLE else CapabilityStatus.UNAVAILABLE,
            if (available) null else UnavailableReason.UNSUPPORTED_DEVICE, backendId)
    }
    override suspend fun detect(input: ImageInput, options: FaceOptions): FaceResult = error("selection test never runs inference")
    override suspend fun close() { closeCalls++ }
}

class FaceDetectionClientTest {
    @Test fun sharedSelectionContractAndNoFallback() = runBlocking {
        val fixture = JSONObject(File("../../fixtures/contracts/backend-selection.json").readText())
        val localId = fixture.getString("defaultBackendId")
        val cloudId = fixture.getString("alternateBackendId")
        val local = FixtureBackend(localId)
        val cloud = FixtureBackend(cloudId, available = false)
        val client = FaceDetectionClient(localId, listOf(
            FaceDetectionRegistration("face.detection", localId, local),
            FaceDetectionRegistration("face.detection", cloudId, cloud)
        ))
        assertEquals(localId, client.select().backendId)
        assertEquals(localId, client.select(FaceBackendSelection.explicit(localId)).backendId)
        assertEquals(FaceDetectionClientError.BackendUnavailable("face.detection", cloudId, UnavailableReason.UNSUPPORTED_DEVICE),
            assertFailsWith<FaceDetectionClientError.BackendUnavailable> { client.select(FaceBackendSelection.explicit(cloudId)) })
        assertEquals(FaceDetectionClientError.BackendNotRegistered("face.detection", fixture.getString("missingBackendId")),
            assertFailsWith<FaceDetectionClientError.BackendNotRegistered> { client.select(FaceBackendSelection.explicit(fixture.getString("missingBackendId"))) })
        assertEquals(FaceDetectionClientError.UnsupportedOption("face.detection", localId, "vendor.unknown"),
            assertFailsWith<FaceDetectionClientError.UnsupportedOption> { client.select(optionIds = setOf("vendor.unknown")) })
        client.close()
        assertEquals(1, local.closeCalls)
        assertEquals(1, cloud.closeCalls)
    }

    @Test fun duplicateAndWrongCapabilityAreRejected() {
        val backend = FixtureBackend("test.face.local")
        val registration = FaceDetectionRegistration("face.detection", backend.backendId, backend)
        assertFailsWith<IllegalArgumentException> { FaceDetectionClient(backend.backendId, listOf(registration, registration)) }
        assertFailsWith<IllegalArgumentException> {
            FaceDetectionClient(backend.backendId, listOf(FaceDetectionRegistration("face.verification", backend.backendId, backend)))
        }
    }

}
