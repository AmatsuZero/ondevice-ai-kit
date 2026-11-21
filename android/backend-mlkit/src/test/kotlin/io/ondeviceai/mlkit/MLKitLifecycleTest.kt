package io.ondeviceai.mlkit
import kotlin.test.*
import kotlinx.coroutines.*
import io.ondeviceai.core.*
class MLKitLifecycleTest {
    @Test fun closeRejectsLaterTasksWithoutTouchingPlatformImages() = runBlocking<Unit> {
        val detector=MLKitFaceDetector()
        assertEquals(CapabilityStatus.AVAILABLE,detector.availability().status)
        detector.close();detector.close()
        assertEquals(UnavailableReason.CLOSED,detector.availability().reason)
    }
}
