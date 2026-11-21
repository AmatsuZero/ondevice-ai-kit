package io.ondeviceai.identity

import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.TaskTerminal
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Comparator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.test.*

class FaceLivenessTest {

    @Test fun timeoutRequestsNativeStopAndDrainsCallback() {
        val f = fixture(); val stopped = CountDownLatch(1)
        f.backend.hook = { _, signal ->
            val registration = signal.registerStopHandler { stopped.countDown() }
            try {
                assertTrue(stopped.await(1, TimeUnit.SECONDS), "timeout did not request native stop")
                LivenessDecision(LivenessOutcome.LIVE, IdentityQuality(1, 1.0))
            } finally { registration.close() }
        }
        val s = f.open(duration = 50_000_000); s.start(); s.submitFrame(f.frame())
        assertCode(IdentityErrorCode.SESSION_TIMEOUT) { s.result() }
        assertEquals(0, stopped.count); assertEquals(TaskTerminal.FAILED, s.terminal)
    }


    @Test fun activeIdleExpiryUsesChallengeExpiredAndAdmissionFailuresCarryMetadata() {
        val f = fixture()
        val expired = AuthorizationGrant("expired", "expired-domain", setOf(IdentityScope.LIVENESS), "identity", 0, 50, 0, "fixture")
        val error = assertFailsWith<IdentityError> { f.engine.openLivenessSession(expired, f.options()) }
        assertEquals(IdentityErrorCode.AUTHORIZATION_EXPIRED, error.code); assertEquals(f.backend.backendId, error.backendId)
        assertEquals(f.backend.modelBinding, error.modelBinding); assertEquals("pad", error.policyId)
        val s = f.open(mode = PADMode.ACTIVE, duration = 10_000_000); s.start()
        assertCode(IdentityErrorCode.CHALLENGE_EXPIRED) { s.result() }
    }


    @Test fun finalPublicationRejectsDeadlinePassedInsideAuthorizationProvider() {
        val f = fixture(); val s = f.open()
        f.epochHook = { f.clock.nowNanos = 1_000_000_001 }
        s.start(); s.submitFrame(f.frame())
        assertCode(IdentityErrorCode.SESSION_TIMEOUT) { s.result() }
    }


    @Test fun admissionDiagnosticsIncludeResolvedBackendAndPolicy() {
        val f = fixture()
        val error = assertFailsWith<IdentityError> { f.open(status = CalibrationStatus.UNVERIFIED) }
        assertEquals(f.backend.backendId, error.backendId); assertEquals(f.backend.modelBinding, error.modelBinding); assertEquals("pad", error.policyId)
    }

    @Test fun cancelledThenRevokedWhileBackendBlockedPublishesOnlyRevocation() {
        val f = fixture(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        f.backend.hook = { _, _ ->
            entered.countDown(); release.await(3, TimeUnit.SECONDS)
            LivenessDecision(LivenessOutcome.LIVE, IdentityQuality(1, 1.0))
        }
        val s = f.open(); s.start(); s.submitFrame(f.frame()); assertTrue(entered.await(2, TimeUnit.SECONDS)); s.cancel()
        f.store.revokeGrant("live", f.owner); release.countDown()
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { s.result() }; assertEquals(TaskTerminal.FAILED, s.terminal)
    }

    @Test fun droppedFramesNeverMeetMinimumProcessedCount() {
        val f = fixture(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        f.backend.hook = { frame, _ ->
            if (frame.timestampNanos == 2L) { entered.countDown(); release.await(3, TimeUnit.SECONDS) }
            LivenessDecision(LivenessOutcome.LIVE, IdentityQuality(1, 1.0))
        }
        val s = f.open(minimumFrames = 3); s.start(); s.submitFrame(f.frame()); assertTrue(entered.await(2, TimeUnit.SECONDS))
        s.submitFrame(f.frame(3)); s.submitFrame(f.frame(4)); s.finish(); release.countDown()
        val r = s.result()
        assertEquals(LivenessOutcome.INSUFFICIENT_QUALITY, r.outcome); assertEquals(3, r.acceptedFrameCount); assertEquals(1, r.droppedFrameCount)
        assertNull(r.proof)
    }

    @Test fun createdCloseAndStopCallbackReentryDoNotDeadlock() {
        val f = fixture(); val created = f.open(); created.close()
        assertEquals(TaskTerminal.FAILED, created.terminal); assertCode(IdentityErrorCode.CLOSED) { created.result() }
        val s = f.open(); val entered = CountDownLatch(1); val release = CountDownLatch(1); val reentered = CountDownLatch(1)
        f.backend.hook = { _, signal ->
            val registration = signal.registerStopHandler {
                f.engine.close()
                assertCode(IdentityErrorCode.BUSY) { s.result() }
                s.close(); reentered.countDown(); release.countDown()
            }
            try {
                entered.countDown(); release.await(3, TimeUnit.SECONDS)
                LivenessDecision(LivenessOutcome.LIVE, IdentityQuality(1, 1.0))
            } finally { registration.close() }
        }
        s.start(); s.submitFrame(f.frame()); assertTrue(entered.await(2, TimeUnit.SECONDS)); s.cancel()
        assertTrue(reentered.await(2, TimeUnit.SECONDS)); f.engine.close()
    }

    private val fixtures = mutableListOf<PublicLivenessFixture>()
    private fun fixture() = PublicLivenessFixture().also(fixtures::add)
    @AfterTest fun cleanup() { fixtures.forEach { it.close() } }
    private fun assertCode(code: IdentityErrorCode, run: () -> Unit) {
        assertEquals(code, assertFailsWith<IdentityError> { run() }.code)
    }

    @Test fun passiveFrameProducesLiveBoundProofAndSingleTerminal() {
        val f = fixture(); val s = f.open()
        assertEquals(LivenessSessionState.CREATED, s.currentState)
        s.start(); assertEquals(LivenessSessionState.RUNNING, s.currentState)
        assertTrue(s.submitFrame(f.frame()))
        val r = s.result()
        assertEquals(LivenessOutcome.LIVE, r.outcome); assertNotNull(r.proof)
        assertEquals(1, r.acceptedFrameCount); assertEquals(64, r.captureDigest.length)
        assertEquals(IdentityScoreFields(), r.scoreFields)
        assertEquals(TaskTerminal.COMPLETED, s.terminal)
        s.cancel(); s.close(); s.close()
        assertFalse(s.submitFrame(f.frame(3)))
        var terminals = 0
        while (true) {
            when (s.nextEvent() ?: break) {
                is LivenessEvent.Completed, is LivenessEvent.Failed, LivenessEvent.Cancelled -> terminals++
                else -> Unit
            }
        }
        assertEquals(1, terminals)
    }

    @Test fun finishWithoutFramesIsInsufficientQualityNotTimeout() {
        val f = fixture(); val s = f.open(); s.start(); s.finish(); s.finish()
        val r = s.result()
        assertEquals(LivenessOutcome.INSUFFICIENT_QUALITY, r.outcome)
        assertEquals(IdentityScoreFields(), r.scoreFields); assertNull(r.proof)
    }

    @Test fun activeChallengesAreUniqueAndFramesRequireOriginalSessionBinding() {
        val f = fixture(); val other = fixture()
        val a = f.open(mode = PADMode.ACTIVE); val b = other.open(mode = PADMode.ACTIVE)
        assertFalse(a.submitFrame(f.frame()))
        a.start(); b.start()
        assertEquals(LivenessEvent.Started, a.nextEvent()); assertEquals(LivenessEvent.Started, b.nextEvent())
        val ca = (a.nextEvent() as LivenessEvent.ChallengeIssued).challenge
        val cb = (b.nextEvent() as LivenessEvent.ChallengeIssued).challenge
        assertNotEquals(ca.nonce, cb.nonce); assertNotEquals(ca.id, cb.id)
        assertEquals(a.sessionId, ca.sessionId); assertEquals("identity", ca.purpose)
        assertTrue(a.submitFrame(f.frame(3, ca)))
        assertEquals(LivenessOutcome.LIVE, a.result().outcome)
        assertFalse(a.submitFrame(f.frame(4, ca)))
        assertFalse(b.submitFrame(other.frame(3, ca)))
        assertCode(IdentityErrorCode.INVALID_CHALLENGE) { b.result() }
    }

    @Test fun activeFrameWithoutChallengeIsRejected() {
        val f = fixture(); val s = f.open(mode = PADMode.ACTIVE); s.start()
        assertFalse(s.submitFrame(f.frame()))
        assertCode(IdentityErrorCode.INVALID_CHALLENGE) { s.result() }
    }

    @Test fun processingFrameAndOnlyLatestPendingAreRetainedWithAccurateCounters() {
        val f = fixture(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        f.backend.hook = { frame, _ ->
            if (frame.timestampNanos == 2L) { entered.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); null }
            else LivenessDecision(LivenessOutcome.LIVE, IdentityQuality(1, 1.0))
        }
        val s = f.open(); s.start(); assertTrue(s.submitFrame(f.frame()))
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        assertTrue(s.submitFrame(f.frame(3))); assertFalse(s.submitFrame(f.frame(3)))
        assertTrue(s.submitFrame(f.frame(4))); release.countDown()
        val r = s.result()
        assertEquals(3, r.acceptedFrameCount); assertEquals(1, r.droppedFrameCount); assertEquals(1, r.staleFrameCount)
        assertEquals(hex(MessageDigest.getInstance("SHA-256").digest(byteArrayOf(2, 4))), r.captureDigest)
    }

    @Test fun unverifiedMismatchedAndUnavailableBackendsFailClosed() {
        val f = fixture()
        assertCode(IdentityErrorCode.THRESHOLD_UNVERIFIED) { f.open(status = CalibrationStatus.UNVERIFIED) }
        assertCode(IdentityErrorCode.BACKEND_NOT_REGISTERED) { f.engine.openLivenessSession(f.grant(), f.options(), IdentityBackendSelection.Explicit("missing")) }
        val vendor = PublicLivenessBackend("vendor.pad", available = false)
        val engine = FaceLiveness(f.store, listOf(f.backend, vendor), monotonicClock = f.clock, wallClock = f.clock)
        assertCode(IdentityErrorCode.BACKEND_UNAVAILABLE) { engine.openLivenessSession(f.grant(), f.options(), IdentityBackendSelection.Explicit(vendor.backendId)) }
        engine.close()
    }

    @Test fun proofIsOneUseCapturePurposeBoundAndClosedInvalidatesIt() {
        val f = fixture(); val s = f.open(); s.start(); assertTrue(s.submitFrame(f.frame()))
        val r = s.result(); val p = requireNotNull(r.proof)
        assertCode(IdentityErrorCode.INVALID_CHALLENGE) { s.consumeProof(p, "0".repeat(64), "identity") }
        assertCode(IdentityErrorCode.INVALID_CHALLENGE) { s.consumeProof(p, r.captureDigest, "other") }
        s.consumeProof(p, r.captureDigest, "identity")
        assertCode(IdentityErrorCode.INVALID_CHALLENGE) { s.consumeProof(p, r.captureDigest, "identity") }
        s.close()
        assertCode(IdentityErrorCode.CLOSED) { s.consumeProof(p, r.captureDigest, "identity") }
    }

    @Test fun proofExpiryAndRevocationAreRecheckedButCompletedResultIsImmutable() {
        for (revoke in listOf(false, true)) {
            val f = fixture(); val s = f.open(); s.start(); s.submitFrame(f.frame())
            val r = s.result(); val p = requireNotNull(r.proof)
            if (revoke) f.store.revokeGrant("live", f.owner) else f.clock.nowMillis = p.expiresAtMillis
            assertCode(if (revoke) IdentityErrorCode.AUTHORIZATION_REVOKED else IdentityErrorCode.INVALID_CHALLENGE) {
                s.consumeProof(p, r.captureDigest, "identity")
            }
            assertEquals(LivenessOutcome.LIVE, s.result().outcome); assertEquals(TaskTerminal.COMPLETED, s.terminal)
        }
    }

    @Test fun revokedIdleSessionFailsAndCallerCancellationIsIdempotent() {
        for (revoke in listOf(false, true)) {
            val f = fixture(); val s = f.open(); s.start()
            if (revoke) f.store.revokeGrant("live", f.owner) else { s.cancel(); s.cancel() }
            assertCode(if (revoke) IdentityErrorCode.AUTHORIZATION_REVOKED else IdentityErrorCode.CANCELLED) { s.result() }
            assertEquals(if (revoke) TaskTerminal.FAILED else TaskTerminal.CANCELLED, s.terminal)
        }
    }

    @Test fun idleTimeoutAndActiveChallengeExpiryAreFailures() {
        val f = fixture(); val s = f.open(duration = 10_000_000); s.start()
        assertCode(IdentityErrorCode.SESSION_TIMEOUT) { s.result() }
        val other = fixture(); val a = other.open(mode = PADMode.ACTIVE); a.start(); a.nextEvent()
        val challenge = (a.nextEvent() as LivenessEvent.ChallengeIssued).challenge
        assertFalse(a.submitFrame(other.frame(challenge.expiresAtNanos, challenge)))
        assertCode(IdentityErrorCode.CHALLENGE_EXPIRED) { a.result() }
    }

    @Test fun minimumFramesRequiresProcessedFramesAndQualityRejectsBeforeScoring() {
        val f = fixture(); val s = f.open(minimumFrames = 2); s.start(); s.submitFrame(f.frame()); s.finish()
        assertEquals(LivenessOutcome.INSUFFICIENT_QUALITY, s.result().outcome)
        for (count in listOf(1, 2)) {
            val other = fixture()
            other.backend.hook = { _, _ -> LivenessDecision(LivenessOutcome.LIVE, IdentityQuality(count, 0.1), IdentityScoreFields(0.9, "pad", 0.5)) }
            val a = other.open(); a.start(); a.submitFrame(other.frame())
            if (count == 2) assertCode(IdentityErrorCode.MULTIPLE_FACES) { a.result() }
            else { assertEquals(LivenessOutcome.INSUFFICIENT_QUALITY, a.result().outcome); assertEquals(IdentityScoreFields(), a.result().scoreFields); assertNull(a.result().proof) }
        }
    }

    @Test fun categoricalAndContinuousFieldsMatchPinnedPolicyAndNonLiveHasNoProof() {
        for (continuous in listOf(false, true)) {
            val f = fixture()
            f.backend.hook = { _, _ -> LivenessDecision(LivenessOutcome.LIVE, IdentityQuality(1, 1.0), IdentityScoreFields(0.9, "pad", 0.5)) }
            val s = f.open(continuous = continuous); s.start(); s.submitFrame(f.frame())
            if (continuous) assertEquals(0.9, s.result().scoreFields.score)
            else assertCode(IdentityErrorCode.THRESHOLD_UNVERIFIED) { s.result() }
        }
        for (outcome in listOf(LivenessOutcome.SPOOF, LivenessOutcome.CHALLENGE_FAILED)) {
            val f = fixture(); f.backend.hook = { _, _ -> LivenessDecision(outcome, IdentityQuality(1, 1.0)) }
            val s = f.open(); s.start(); s.submitFrame(f.frame()); assertEquals(outcome, s.result().outcome); assertNull(s.result().proof)
        }
    }

    @Test fun backendBindingMutationFailsBeforePublication() {
        val f = fixture()
        f.backend.hook = { _, _ ->
            f.backend.modelBinding = ModelBinding(ModelBindingKind.SYSTEM, "different", "2")
            LivenessDecision(LivenessOutcome.LIVE, IdentityQuality(1, 1.0))
        }
        val s = f.open(); s.start(); s.submitFrame(f.frame())
        assertCode(IdentityErrorCode.THRESHOLD_UNVERIFIED) { s.result() }
    }

    @Test fun closeDrainsNativeWorkAndStopCallbacksAndClosesBackendOnlyOnce() {
        val f = fixture(); val entered = CountDownLatch(1); val stopped = CountDownLatch(1)
        val release = CountDownLatch(1); val stopRelease = CountDownLatch(1)
        f.backend.hook = { _, signal ->
            val registration = signal.registerStopHandler { stopped.countDown(); stopRelease.await(4, TimeUnit.SECONDS) }
            try {
                entered.countDown(); release.await(4, TimeUnit.SECONDS)
                LivenessDecision(LivenessOutcome.LIVE, IdentityQuality(1, 1.0))
            } finally { registration.close() }
        }
        val s = f.open(duration = 5_000_000_000); s.start(); s.submitFrame(f.frame()); assertTrue(entered.await(2, TimeUnit.SECONDS))
        val executor = Executors.newSingleThreadExecutor()
        try {
            val closing = executor.submit { f.engine.close() }
            assertTrue(stopped.await(2, TimeUnit.SECONDS)); assertFalse(closing.isDone)
            release.countDown(); assertFalse(closing.isDone); stopRelease.countDown()
            closing.get(3, TimeUnit.SECONDS); f.engine.close()
            assertEquals(1, f.backend.closes.get()); assertEquals(TaskTerminal.FAILED, s.terminal)
            assertEquals(LivenessSessionState.CLOSED, s.currentState)
        } finally { release.countDown(); stopRelease.countDown(); executor.shutdownNow() }
    }

    @Test fun futureFramesFailAndFramePayloadIsDefensivelyCopied() {
        val f = fixture(); val bytes = byteArrayOf(2)
        val frame = LivenessFrame(bytes, 2, "frame"); bytes[0] = 99
        val exposed = frame.encodedFrame; exposed[0] = 88
        assertContentEquals(byteArrayOf(2), frame.encodedFrame)
        val s = f.open(); s.start()
        assertFalse(s.submitFrame(frame))
        assertCode(IdentityErrorCode.SESSION_TIMEOUT) { s.result() }
    }
}

private class PublicLivenessFixture : AutoCloseable {
    @Volatile var epochHook: (() -> Unit)? = null
    val root = Files.createTempDirectory("odai-public-liveness")
    val clock = PublicLivenessClock()
    val backend = PublicLivenessBackend()
    val keys = object : IdentityKeyProvider {
        private val values = mutableMapOf<String, SecretKey>()
        override fun key(id: String, createIfMissing: Boolean): SecretKey = synchronized(values) {
            values[id] ?: if (createIfMissing) SecretKeySpec(ByteArray(32) { 3 }, "AES").also { values[id] = it }
            else throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
        }
        override fun deleteKey(id: String) { synchronized(values) { values.remove(id) } }
    }
    val provider = object : IdentityAuthorizationProvider {
        override fun authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Long, nowMillis: Long): Long { epochHook?.invoke(); return grant.authorizationEpoch }
        override fun validateOwnership(authorization: OwnershipAuthorization, nowMillis: Long) {}
    }
    val store = IdentityStore(root.toFile(), "identity", provider, keys, clock, clock)
    val owner = OwnershipAuthorization(store.storeId, "owner")
    val engine = FaceLiveness(store, listOf(backend), monotonicClock = clock, wallClock = clock)
    fun grant() = AuthorizationGrant("live", "domain", setOf(IdentityScope.LIVENESS), "identity", 0, null, 0, "fixture")
    fun options(mode: PADMode = PADMode.PASSIVE, status: CalibrationStatus = CalibrationStatus.VERIFIED,
        duration: Long = 1_000_000_000, minimumFrames: Int = 1, continuous: Boolean = false) = LivenessOptions(
        PADPolicy("pad", "1", backend.backendId, backend.backendVersion, backend.modelBinding, status, mode,
            challengePolicyRevision = if (mode == PADMode.ACTIVE) "challenge-1" else null,
            minimumFrames = minimumFrames, maximumDurationNanos = duration, qualityFloor = 0.5,
            scoreMetric = if (continuous) "pad" else null, threshold = if (continuous) 0.5 else null,
            classificationPolicyRevision = if (continuous) null else "fixture-categorical",
            datasetRevision = "fixture", operatingPoint = "fixture", attackTypes = setOf("fixture")), "identity", "options-1")
    fun open(mode: PADMode = PADMode.PASSIVE, status: CalibrationStatus = CalibrationStatus.VERIFIED,
        duration: Long = 1_000_000_000, minimumFrames: Int = 1, continuous: Boolean = false) =
        engine.openLivenessSession(grant(), options(mode, status, duration, minimumFrames, continuous))
    fun frame(timestamp: Long = 2, challenge: LivenessChallenge? = null): LivenessFrame {
        clock.nowNanos = timestamp
        return LivenessFrame(byteArrayOf(timestamp.toByte()), timestamp, "camera-1", challenge)
    }
    override fun close() {
        engine.close(); store.close()
        Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
}
private class PublicLivenessClock : IdentityWallClock, IdentityMonotonicClock {
    @Volatile override var nowMillis: Long = 100
    @Volatile override var nowNanos: Long = 1
}
private class PublicLivenessBackend(override val backendId: String = "fixture.pad", private val available: Boolean = true) : LivenessBackend {
    override val backendVersion = "1"
    @Volatile override var modelBinding = ModelBinding(ModelBindingKind.SYSTEM, backendId, "fixture")
    @Volatile var hook: ((LivenessFrame, IdentityStopSignal) -> LivenessDecision?)? = null
    val closes = AtomicInteger()
    override fun availability() = CapabilityAvailability(if (available) CapabilityStatus.AVAILABLE else CapabilityStatus.UNAVAILABLE, backendId = backendId)
    override fun evaluate(frame: LivenessFrame, challenge: LivenessChallenge?, stopSignal: IdentityStopSignal): LivenessDecision? {
        stopSignal.check()
        val current = hook
        return if (current != null) current(frame, stopSignal) else LivenessDecision(LivenessOutcome.LIVE, IdentityQuality(1, 1.0))
    }
    override fun close() { closes.incrementAndGet() }
}
private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }

