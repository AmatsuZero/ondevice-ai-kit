package io.ondeviceai.identity

import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.TaskTerminal
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.FutureTask
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FaceVerificationTest {
    private val roots = mutableListOf<java.nio.file.Path>()

    @AfterTest fun cleanup() {
        roots.forEach { root -> Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }

    @Test fun singleFaceReturnsMatchThenNonMatchWithoutIdentityPayload() {
        fixture().use { fixture ->
            val verifier = fixture.verifier()
            val match = verifier.verify(IdentityProbe("match", listOf(1f, 0f)), fixture.template, fixture.verifyGrant, VerificationOptions(fixture.policy, fixture.policy.revision)).result()
            assertEquals(VerificationOutcome.MATCH, match.outcome)
            assertEquals(1.0, match.score)
            assertEquals("cosine", match.scoreMetric)
            assertEquals(0.8, match.threshold)
            assertEquals(fixture.template.revision, match.templateRevision)
            assertEquals(fixture.policy.id, match.thresholdPolicyId)
            assertEquals(fixture.policy.revision, match.thresholdPolicyRevision)
            assertEquals("system.fixture", match.backendId)
            assertEquals("fixture-p1", match.preprocessingRevision)
            assertTrue(match.durationNanos >= 0)
            assertFalse(VerificationResult::class.java.declaredFields.any { it.name.contains("subject", ignoreCase = true) || it.name.contains("vector", ignoreCase = true) })

            val nonMatch = verifier.verify(IdentityProbe("non-match", listOf(0f, 1f)), fixture.template, fixture.verifyGrant, VerificationOptions(fixture.policy, fixture.policy.revision)).result()
            assertEquals(VerificationOutcome.NON_MATCH, nonMatch.outcome)
            assertEquals(0.0, nonMatch.score)
            verifier.close()
        }
    }

    @Test fun qualityRejectionHasNoScore() {
        fixture(quality = IdentityQuality(1, 0.4)).use { fixture ->
            val result = fixture.verifier().verify(IdentityProbe("low-quality", listOf(1f, 0f)), fixture.template, fixture.verifyGrant, VerificationOptions(fixture.policy, fixture.policy.revision)).result()
            assertEquals(VerificationOutcome.INSUFFICIENT_QUALITY, result.outcome)
            assertNull(result.score)
            assertNull(result.scoreMetric)
            assertNull(result.threshold)
            assertEquals(0, fixture.backend.embedCalls)
        }
    }

    @Test fun unverifiedPolicyFailsClosedBeforeBackendEvaluation() {
        fixture().use { fixture ->
            val policy = ThresholdPolicy("draft", "r1", fixture.backend.embeddingSpace, "cosine", ScoreDirection.HIGHER_IS_BETTER, CalibrationStatus.UNVERIFIED)
            val verifier = fixture.verifier()
            assertCode(IdentityErrorCode.THRESHOLD_UNVERIFIED) {
                verifier.verify(IdentityProbe("probe", listOf(1f, 0f)), fixture.template, fixture.verifyGrant, VerificationOptions(policy, policy.revision))
            }
            assertEquals(0, fixture.backend.qualityCalls)
            verifier.close()
        }
    }

    @Test fun multiFaceAndExplicitUnavailableBackendDoNotFallback() {
        fixture(quality = IdentityQuality(2, 1.0)).use { fixture ->
            val task = fixture.verifier().verify(IdentityProbe("multi", listOf(1f, 0f)), fixture.template, fixture.verifyGrant, VerificationOptions(fixture.policy, fixture.policy.revision))
            assertCode(IdentityErrorCode.MULTIPLE_FACES) { task.result() }
        }
        fixture().use { fixture ->
            val unavailable = TestVerificationBackend("optional.fixture", fixture.backend.embeddingSpace, available = false)
            val verifier = FaceVerification(fixture.store, fixture.backend.backendId,
                listOf(fixture.backend, unavailable))
            assertCode(IdentityErrorCode.BACKEND_UNAVAILABLE) {
                verifier.verify(IdentityProbe("probe", listOf(1f, 0f)), fixture.template, fixture.verifyGrant,
                    VerificationOptions(fixture.policy, fixture.policy.revision), IdentityBackendSelection.Explicit(unavailable.backendId))
            }
            assertEquals(0, fixture.backend.qualityCalls)
            verifier.close()
        }
    }

    @Test fun cancelAndCloseProduceOneTerminalResultAndDrain() {
        fixture(block = true).use { fixture ->
            val verifier = fixture.verifier()
            val task = verifier.verify(IdentityProbe("cancel", listOf(1f, 0f)), fixture.template, fixture.verifyGrant, VerificationOptions(fixture.policy, fixture.policy.revision))
            assertTrue(fixture.backend.started.await(2, TimeUnit.SECONDS))
            task.cancel()
            assertCode(IdentityErrorCode.CANCELLED) { task.result() }
            assertEquals(TaskTerminal.CANCELLED, task.terminal)
            assertCode(IdentityErrorCode.CANCELLED) { task.result() }
            verifier.close()
        }
        fixture(block = true).use { fixture ->
            val verifier = fixture.verifier()
            val task = verifier.verify(IdentityProbe("close", listOf(1f, 0f)), fixture.template, fixture.verifyGrant, VerificationOptions(fixture.policy, fixture.policy.revision))
            assertTrue(fixture.backend.started.await(2, TimeUnit.SECONDS))
            verifier.close()
            assertCode(IdentityErrorCode.CLOSED) { task.result() }
            assertEquals(TaskTerminal.FAILED, task.terminal)
        }
    }

    @Test fun revokeDuringBackendWorkFailsClosed() {
        fixture(block = true).use { fixture ->
            val verifier = fixture.verifier()
            val task = verifier.verify(IdentityProbe("revoke", listOf(1f, 0f)), fixture.template, fixture.verifyGrant, VerificationOptions(fixture.policy, fixture.policy.revision))
            assertTrue(fixture.backend.started.await(2, TimeUnit.SECONDS))
            fixture.store.revokeGrant("enrollment", OwnershipAuthorization(fixture.store.storeId, "owner"))
            val error = assertFailsWith<IdentityError> { task.result() }
            assertEquals(IdentityErrorCode.AUTHORIZATION_REVOKED, error.code)
            assertEquals("system.fixture", error.backendId)
            assertEquals("policy", error.policyId)
            assertNotNull(error.sessionId)
            assertEquals(TaskTerminal.FAILED, task.terminal)
            verifier.close()
        }
    }

    @Test fun closeWaitsForReservedNativeStopDeliveryBeforeClosingBackend() {
        fixture(block = true, blockStopCallback = true).use { fixture ->
            val verifier = fixture.verifier()
            val task = verifier.verify(IdentityProbe("stop-drain", listOf(1f, 0f)), fixture.template, fixture.verifyGrant,
                VerificationOptions(fixture.policy, fixture.policy.revision))
            assertTrue(fixture.backend.started.await(2, TimeUnit.SECONDS))
            task.cancel()
            assertTrue(fixture.backend.stopCallbackEntered.await(2, TimeUnit.SECONDS))
            val closed = CountDownLatch(1)
            Thread { verifier.close(); closed.countDown() }.start()
            assertFalse(closed.await(150, TimeUnit.MILLISECONDS))
            assertEquals(0, fixture.backend.closeCalls)
            fixture.backend.releaseStopCallback.countDown()
            assertTrue(closed.await(2, TimeUnit.SECONDS))
            assertCode(IdentityErrorCode.CANCELLED) { task.result() }
            assertEquals(1, fixture.backend.closeCalls)
        }
    }

    @Test fun taskCloseFromNativeStopCallbackDoesNotSelfWait() {
        val fixture = fixture(block = true)
        run {
            var task: VerificationTask? = null
            val callbackReturned = CountDownLatch(1)
            fixture.backend.onStop = {
                task?.close()
                callbackReturned.countDown()
            }
            val verifier = fixture.verifier()
            task = verifier.verify(IdentityProbe("reentry", listOf(1f, 0f)), fixture.template, fixture.verifyGrant,
                VerificationOptions(fixture.policy, fixture.policy.revision))
            assertTrue(fixture.backend.started.await(2, TimeUnit.SECONDS))
            task.cancel()
            assertTrue(callbackReturned.await(2, TimeUnit.SECONDS))
            assertCode(IdentityErrorCode.CANCELLED) { task.result() }
            verifier.close()
            fixture.close()
        }
    }

    @Test fun repeatedEngineCloseWaitsForOneBackendClose() {
        fixture(block = true, blockClose = true).use { fixture ->
            val verifier = fixture.verifier()
            val task = verifier.verify(IdentityProbe("close-race", listOf(1f, 0f)), fixture.template, fixture.verifyGrant,
                VerificationOptions(fixture.policy, fixture.policy.revision))
            assertTrue(fixture.backend.started.await(2, TimeUnit.SECONDS))
            task.cancel()
            val firstDone = CountDownLatch(1)
            val secondDone = CountDownLatch(1)
            Thread { verifier.close(); firstDone.countDown() }.start()
            assertTrue(fixture.backend.closeEntered.await(2, TimeUnit.SECONDS))
            Thread { verifier.close(); secondDone.countDown() }.start()
            try { assertFalse(secondDone.await(150, TimeUnit.MILLISECONDS)) }
            finally { fixture.backend.releaseClose.countDown() }
            assertTrue(firstDone.await(2, TimeUnit.SECONDS))
            assertTrue(secondDone.await(2, TimeUnit.SECONDS))
            assertEquals(1, fixture.backend.closeCalls)
        }
    }

    @Test fun finalGateCallerCancellationAlwaysSuppressesPublication() {
        fixture().use { fixture ->
            val verifier = fixture.verifier()
            repeat(20) {
                val allowEmbed = CountDownLatch(1)
                lateinit var task: VerificationTask
                fixture.backend.onEmbed = {
                    assertTrue(allowEmbed.await(2, TimeUnit.SECONDS))
                    fixture.authority.onNextEpoch = { task.cancel() }
                }
                task = verifier.verify(IdentityProbe("final-gate", listOf(1f, 0f)), fixture.template, fixture.verifyGrant,
                    VerificationOptions(fixture.policy, fixture.policy.revision))
                allowEmbed.countDown()
                assertCode(IdentityErrorCode.CANCELLED) { task.result() }
                assertEquals(TaskTerminal.CANCELLED, task.terminal)
            }
            verifier.close()
        }
    }

    @Test fun successfulTerminalSurvivesCancelAndRepeatClose() {
        fixture().use { fixture ->
            val verifier = fixture.verifier()
            val task = verifier.verify(IdentityProbe("match", listOf(1f, 0f)), fixture.template, fixture.verifyGrant,
                VerificationOptions(fixture.policy, "request-r1"))
            val first = task.result()
            assertEquals(TaskTerminal.COMPLETED, task.terminal)
            task.cancel(); task.close(); task.close()
            assertEquals(VerificationOutcome.MATCH, task.result().outcome)
            assertEquals(first.runId, task.result().runId)
            assertEquals(TaskTerminal.COMPLETED, task.terminal)
            verifier.close()
        }
    }

    @Test fun revocationDeliveryDrainsBeforeResultAndBackendClose() {
        fixture(block = true, blockStopCallback = true).use { fixture ->
            val verifier = fixture.verifier()
            val task = verifier.verify(IdentityProbe("revoke-drain", listOf(1f, 0f)), fixture.template, fixture.verifyGrant,
                VerificationOptions(fixture.policy, "request-r1"))
            assertTrue(fixture.backend.started.await(2, TimeUnit.SECONDS))
            val revoking = FutureTask { fixture.store.revokeGrant("enrollment", OwnershipAuthorization(fixture.store.storeId, "owner")) }
            Thread(revoking).start()
            assertTrue(fixture.backend.stopCallbackEntered.await(2, TimeUnit.SECONDS))
            val resultDone = CountDownLatch(1)
            val waiter = FutureTask { try { task.result() } finally { resultDone.countDown() } }
            Thread(waiter).start()
            val closing = FutureTask { verifier.close() }
            Thread(closing).start()
            try {
                assertFalse(resultDone.await(100, TimeUnit.MILLISECONDS))
                assertFalse(fixture.backend.closeEntered.await(100, TimeUnit.MILLISECONDS))
            } finally { fixture.backend.releaseStopCallback.countDown() }
            revoking.get(2, TimeUnit.SECONDS)
            closing.get(2, TimeUnit.SECONDS)
            assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { task.result() }
            assertEquals(TaskTerminal.FAILED, task.terminal)
            assertEquals(1, fixture.backend.closeCalls)
        }
    }

    @Test fun closeDrainsBlockedAdmissionBeforeClosingBackend() {
        fixture().use { fixture ->
            val verifier = fixture.verifier()
            val admitted = CountDownLatch(1)
            val allowAdmission = CountDownLatch(1)
            fixture.authority.onNextEpoch = { admitted.countDown(); allowAdmission.await(2, TimeUnit.SECONDS) }
            val admission = FutureTask {
                verifier.verify(IdentityProbe("admission", listOf(1f, 0f)), fixture.template, fixture.verifyGrant,
                    VerificationOptions(fixture.policy, "request-r1"))
            }
            Thread(admission).start()
            assertTrue(admitted.await(2, TimeUnit.SECONDS))
            val closeDone = CountDownLatch(1)
            val closer = Thread { verifier.close(); closeDone.countDown() }
            closer.start()
            closer.interrupt()
            try { assertFalse(fixture.backend.closeEntered.await(100, TimeUnit.MILLISECONDS)) }
            finally { allowAdmission.countDown() }
            val task = admission.get(2, TimeUnit.SECONDS)
            assertTrue(closeDone.await(2, TimeUnit.SECONDS))
            assertCode(IdentityErrorCode.CLOSED) { task.result() }
            assertEquals(TaskTerminal.FAILED, task.terminal)
            assertTrue(closer.isInterrupted)
            assertEquals(1, fixture.backend.closeCalls)
        }
    }

    @Test fun engineCloseFromStopCallbackSchedulesBackendDrain() {
        fixture(block = true).use { fixture ->
            val verifier = fixture.verifier()
            val returned = CountDownLatch(1)
            fixture.backend.onStop = { verifier.close(); returned.countDown() }
            val task = verifier.verify(IdentityProbe("callback-close", listOf(1f, 0f)), fixture.template, fixture.verifyGrant,
                VerificationOptions(fixture.policy, "request-r1"))
            assertTrue(fixture.backend.started.await(2, TimeUnit.SECONDS))
            task.cancel()
            assertTrue(returned.await(2, TimeUnit.SECONDS))
            assertTrue(fixture.backend.closeEntered.await(2, TimeUnit.SECONDS))
            assertCode(IdentityErrorCode.CANCELLED) { task.result() }
            verifier.close()
        }
    }

    @Test fun fatalBackendErrorRemainsObservableAndReleasesTicket() {
        val fixture = fixture()
        val verifier = fixture.verifier()
        val fatal = LinkageError("fixture backend linkage")
        fixture.backend.onEmbed = { throw fatal }
        val task = verifier.verify(IdentityProbe("fatal", listOf(1f, 0f)), fixture.template, fixture.verifyGrant,
            VerificationOptions(fixture.policy, "request-r1"))
        assertEquals(fatal, assertFailsWith<LinkageError> { task.result() })
        assertEquals(TaskTerminal.FAILED, task.terminal)
        verifier.close()
        val storeClose = FutureTask { fixture.close() }
        Thread(storeClose).apply { isDaemon = true }.start()
        storeClose.get(2, TimeUnit.SECONDS)
    }

    @Test fun revocationBeforeFinalConsumeWinsInBothCancelRequestOrders() {
        for (cancelFirst in listOf(true, false)) fixture().use { fixture ->
            val entered = CountDownLatch(1)
            val allowReturn = CountDownLatch(1)
            fixture.backend.onEmbed = { entered.countDown(); allowReturn.await(2, TimeUnit.SECONDS) }
            val verifier = fixture.verifier()
            val task = verifier.verify(IdentityProbe("ordering", listOf(1f, 0f)), fixture.template, fixture.verifyGrant,
                VerificationOptions(fixture.policy, "request-r1"))
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            if (cancelFirst) task.cancel()
            fixture.store.revokeGrant("enrollment", OwnershipAuthorization(fixture.store.storeId, "owner"))
            if (!cancelFirst) task.cancel()
            allowReturn.countDown()
            assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { task.result() }
            assertEquals(TaskTerminal.FAILED, task.terminal)
            task.cancel(); task.close()
            assertEquals(TaskTerminal.FAILED, task.terminal)
            verifier.close()
        }
    }

    @Test fun completedVerificationIsNotRetractedByLaterRevocation() {
        fixture().use { fixture ->
            val verifier = fixture.verifier()
            val task = verifier.verify(IdentityProbe("completed", listOf(1f, 0f)), fixture.template, fixture.verifyGrant,
                VerificationOptions(fixture.policy, "request-r1"))
            val result = task.result()
            fixture.store.revokeGrant("enrollment", OwnershipAuthorization(fixture.store.storeId, "owner"))
            task.cancel(); task.close()
            assertEquals(result.runId, task.result().runId)
            assertEquals(VerificationOutcome.MATCH, task.result().outcome)
            assertEquals(TaskTerminal.COMPLETED, task.terminal)
            verifier.close()
        }
    }

    @Test fun unknownBackendFailureReturnsOnlyStableMetadata() {
        fixture().use { fixture ->
            fixture.backend.onEmbed = { throw IllegalStateException("private-backend-input") }
            val verifier = fixture.verifier()
            val task = verifier.verify(IdentityProbe("failure", listOf(1f, 0f)), fixture.template, fixture.verifyGrant,
                VerificationOptions(fixture.policy, "request-r1"))
            val failure = assertFailsWith<IdentityError> { task.result() }
            assertEquals(IdentityErrorCode.BACKEND_UNAVAILABLE, failure.code)
            assertEquals("system.fixture", failure.backendId)
            assertEquals("policy", failure.policyId)
            assertNotNull(failure.sessionId)
            assertNull(failure.cause)
            assertFalse(failure.toString().contains("private-backend-input"))
            assertEquals(TaskTerminal.FAILED, task.terminal)
            verifier.close()
        }
    }

    @Test fun missingTemplateAndExpiredGrantFailAtAdmissionWithResolvedMetadata() {
        fixture().use { fixture ->
            val verifier = fixture.verifier()
            val missing = TemplateRef("missing-template", fixture.template.embeddingSpaceId, 0, fixture.template.revision)
            val expired = AuthorizationGrant("expired-query", "domain", setOf(IdentityScope.VERIFICATION), "login", 0, 5, 0, "evidence")
            for ((ref, grant, code) in listOf(Triple(missing, fixture.verifyGrant, IdentityErrorCode.TEMPLATE_NOT_FOUND),
                Triple(fixture.template, expired, IdentityErrorCode.AUTHORIZATION_EXPIRED))) {
                val failure = assertFailsWith<IdentityError> {
                    verifier.verify(IdentityProbe("probe", listOf(1f, 0f)), ref, grant, VerificationOptions(fixture.policy, "request-r1"))
                }
                assertEquals(code, failure.code)
                assertEquals("system.fixture", failure.backendId)
                assertEquals(fixture.backend.modelBinding, failure.modelBinding)
                assertEquals("policy", failure.policyId)
                assertNull(failure.sessionId)
            }
            verifier.close()
        }
    }

    @Test fun corruptProbePreservesInvalidInputAndReleasesTheRun() {
        fixture().use { fixture ->
            val backend = FixtureIdentityBackend.system()
            val verifier = FaceVerification(fixture.store, backend.backendId, listOf(backend))
            val task = verifier.verify(IdentityProbe(byteArrayOf(0), "corrupt-image"), fixture.template, fixture.verifyGrant,
                VerificationOptions(fixture.policy, "request-r1"))
            assertCode(IdentityErrorCode.INVALID_INPUT) { task.result() }
            assertEquals(TaskTerminal.FAILED, task.terminal)
            val next = verifier.verify(IdentityProbe("probe", listOf(1f, 0f)), fixture.template, fixture.verifyGrant,
                VerificationOptions(fixture.policy, "request-r1"))
            assertEquals(VerificationOutcome.MATCH, next.result().outcome)
            verifier.close()
        }
    }

    private fun fixture(quality: IdentityQuality = IdentityQuality(1, 1.0), block: Boolean = false,
        blockStopCallback: Boolean = false, blockClose: Boolean = false): VerificationFixture {
        val root = Files.createTempDirectory("face-verification").also(roots::add)
        val authority = VerificationAuthority()
        val store = IdentityStore(root.toFile(), "login", authority, VerificationKeys(), VerificationClock, VerificationMono)
        val enrollment = grant("enrollment", IdentityScope.ENROLLMENT)
        val candidate = store.createCandidate(enrollment)
        val backend = TestVerificationBackend("system.fixture", FixtureIdentityBackend.system().embeddingSpace, quality = quality,
            block = block, blockStopCallback = blockStopCallback, blockClose = blockClose)
        val template = requireNotNull(store.enroll(IdentityProbe("enroll", listOf(1f, 0f)), candidate, enrollment,
            EnrollmentOptions(0.8, "quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val verify = grant("verify", IdentityScope.VERIFICATION)
        val policy = ThresholdPolicy("policy", "r1", backend.embeddingSpace, "cosine", ScoreDirection.HIGHER_IS_BETTER,
            CalibrationStatus.VERIFIED, IdentityPolicyKind.VERIFICATION, 0.8, 0.5, "fixture", "dataset", enrollmentRule = "one", effectiveAuthorizationEpoch = 0)
        return VerificationFixture(store, backend, template, verify, policy, authority)
    }

    private fun grant(id: String, scope: IdentityScope) = AuthorizationGrant(id, "domain", setOf(scope), "login", 0, null, 0, "evidence")
    private fun assertCode(code: IdentityErrorCode, block: () -> Any?) = assertEquals(code, assertFailsWith<IdentityError> { block() }.code)
}

private class VerificationFixture(val store: IdentityStore, val backend: TestVerificationBackend, val template: TemplateRef,
    val verifyGrant: AuthorizationGrant, val policy: ThresholdPolicy, val authority: VerificationAuthority) : AutoCloseable {
    fun verifier() = FaceVerification(store, backend.backendId, listOf(backend))
    override fun close() { store.close() }
}

private object VerificationClock : IdentityWallClock { override val nowMillis = 10L }
private object VerificationMono : IdentityMonotonicClock { override val nowNanos = 10L }
private class VerificationAuthority : IdentityAuthorizationProvider {
    @Volatile var onNextEpoch: (() -> Unit)? = null
    override fun authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Long, nowMillis: Long): Long {
        val callback = onNextEpoch
        onNextEpoch = null
        callback?.invoke()
        return 0L
    }
    override fun validateOwnership(authorization: OwnershipAuthorization, nowMillis: Long) = Unit
}
private class VerificationKeys : IdentityKeyProvider {
    private val keys = mutableMapOf<String, SecretKey>()
    override fun key(id: String, createIfMissing: Boolean): SecretKey = keys[id] ?: if (createIfMissing)
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { keys[id] = it }
    else throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
    override fun deleteKey(id: String) { keys.remove(id) }
}
private class TestVerificationBackend(
    override val backendId: String,
    override val embeddingSpace: EmbeddingSpaceId,
    private val available: Boolean = true,
    private val quality: IdentityQuality = IdentityQuality(1, 1.0),
    private val block: Boolean = false,
    private val blockStopCallback: Boolean = false,
    private val blockClose: Boolean = false
) : IdentityBackend {
    override val backendVersion get() = embeddingSpace.backendVersion
    override val modelBinding get() = embeddingSpace.modelBinding
    val started = CountDownLatch(1)
    val stopCallbackEntered = CountDownLatch(1)
    val releaseStopCallback = CountDownLatch(1)
    var closeCalls = 0
    val closeEntered = CountDownLatch(1)
    val releaseClose = CountDownLatch(1)
    var onStop: (() -> Unit)? = null
    var onEmbed: (() -> Unit)? = null
    var qualityCalls = 0
    var embedCalls = 0
    override fun availability() = CapabilityAvailability(if (available) CapabilityStatus.AVAILABLE else CapabilityStatus.UNAVAILABLE, backendId = backendId)
    override fun evaluateQuality(probe: IdentityProbe): IdentityQuality { qualityCalls++; return quality }
    override fun evaluateQuality(probe: IdentityProbe, stopSignal: IdentityStopSignal): IdentityQuality {
        qualityCalls++
        if (!block) return quality
        val registration = stopSignal.registerStopHandler {
            started.countDown()
            if (blockStopCallback) {
                stopCallbackEntered.countDown()
                releaseStopCallback.await()
            }
            onStop?.invoke()
        }
        try {
            started.countDown()
            while (true) { stopSignal.check(); Thread.sleep(5) }
        } finally { registration.close() }
    }
    override fun embed(probe: IdentityProbe): IdentityEmbedding {
        embedCalls++
        onEmbed?.invoke()
        return IdentityEmbedding(embeddingSpace, requireNotNull(probe.vector))
    }
    override fun close() {
        closeCalls++
        closeEntered.countDown()
        if (blockClose) {
            releaseClose.await()
        }
    }
}
