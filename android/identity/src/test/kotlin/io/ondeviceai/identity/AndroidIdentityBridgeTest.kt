package io.ondeviceai.identity

import io.ondeviceai.binding.AndroidIdentityBridge
import io.ondeviceai.binding.IdentityCallback
import io.ondeviceai.binding.IdentityBridgeFailure
import io.ondeviceai.binding.IdentityAuthorizationGrant
import io.ondeviceai.binding.IdentityProbeReference
import io.ondeviceai.binding.IdentityEnrollmentOptions
import io.ondeviceai.binding.IdentityVerificationOptions
import io.ondeviceai.binding.IdentityGalleryOptions
import io.ondeviceai.binding.IdentityIdentificationOptions
import io.ondeviceai.binding.IdentityThresholdPolicy
import io.ondeviceai.binding.IdentityOwnershipAuthorization
import io.ondeviceai.binding.IdentityDeletionWaitOptions
import io.ondeviceai.binding.IdentityBridgeTask
import io.ondeviceai.binding.IdentityBackendSelection as BridgeSelection
import io.ondeviceai.binding.IdentityErrorCode as BridgeError
import io.ondeviceai.binding.IdentityDeletionStatus as BridgeDeletion
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.*

class AndroidIdentityBridgeTest {
    @Test fun realStoresRoundTripEnrollmentVerificationGalleryAndDeletion(): Unit = Fixture().use { f ->
        val candidate = f.call { f.bridge.createCandidate(f.grantWire, it) }
        val enrollment = f.call { f.bridge.enroll(f.probe, candidate, f.grantWire, IdentityEnrollmentOptions(0.5, "quality-v1", "enroll-v2"), callback = it) }
        val template = assertNotNull(enrollment.template)
        assertEquals(f.backend.embeddingSpace.canonicalJSON(), enrollment.embeddingSpace.canonicalValue)
        assertEquals("enroll-v2", enrollment.metadata.optionsRevision)
        assertEquals("fixture-p1", enrollment.metadata.preprocessingRevision)
        assertEquals(1, enrollment.quality.faceCount)
        val verification = f.call { f.bridge.verify(f.probe, template, f.grantWire, IdentityVerificationOptions(f.policy(IdentityPolicyKind.VERIFICATION), "verify-v3"), callback = it) }
        assertEquals(io.ondeviceai.binding.IdentityVerificationOutcome.MATCH, verification.outcome)
        assertEquals(1.0, verification.scoreFields.score)
        assertEquals("verify-v3", verification.metadata.optionsRevision)
        assertEquals(TemplateRef.decodeJSON(template.canonicalValue).revision, verification.templateRevision)
        val builder = f.call { f.bridge.createGalleryBuilder(null, f.grantWire, IdentityGalleryOptions(f.policy(IdentityPolicyKind.IDENTIFICATION), "subject-max-v1"), callback = it) }
        f.call { f.bridge.addTemplate(builder, template, f.grantWire, it) }
        f.call { f.bridge.removeTemplate(builder, template, f.grantWire, it) }
        f.call { f.bridge.addTemplate(builder, template, f.grantWire, it) }
        val gallery = f.call { f.bridge.publishGallery(builder, it) }
        f.call { f.bridge.closeGalleryBuilder(builder, it) }
        val result = f.call { f.bridge.identify(f.probe, gallery, f.grantWire, IdentityIdentificationOptions(4, null, "identify-v4"), callback = it) }
        assertEquals(candidate, result.candidates.single().candidate)
        assertEquals(1, result.galleryCandidateCount); assertEquals(1, result.galleryTemplateCount)
        assertEquals(4, result.grouping.topK)
        assertEquals("synthetic-data", result.grouping.datasetRevision)
        assertEquals("fixture-p1", result.metadata.preprocessingRevision)
        assertEquals(1, result.enrollmentCounts.single().templateCount)
        assertEquals(64, result.galleryContentDigest.length)
        val owner = IdentityOwnershipAuthorization(f.store.storeId, "owner")
        val indexReceipt = f.call { f.bridge.deleteGallery(gallery, owner, it) }
        assertEquals(BridgeDeletion.PURGED, f.call { f.bridge.awaitDeletion(indexReceipt, IdentityDeletionWaitOptions(null), it) })
        val templateReceipt = f.call { f.bridge.deleteTemplate(template, owner, it) }
        assertEquals(BridgeDeletion.PURGED, f.call { f.bridge.awaitDeletion(templateReceipt, IdentityDeletionWaitOptions(1_000_000), it) })
        val candidateReceipt = f.call { f.bridge.deleteCandidate(candidate, owner, it) }
        assertEquals(BridgeDeletion.PURGED, f.call { f.bridge.awaitDeletion(candidateReceipt, IdentityDeletionWaitOptions(null), it) })
        val revocation = f.call { f.bridge.revokeGrant(f.grant.grantId, owner, it) }
        assertEquals(BridgeDeletion.PURGED, f.call { f.bridge.awaitRevocation(revocation, IdentityDeletionWaitOptions(null), it) })
        assertTrue(f.callbackThreads.all { it == "identity-bridge-test-ui" })
    }

    @Test fun explicitSelectionAndPolicyValidationUseNativeErrorsWithoutFallback(): Unit = Fixture().use { f ->
        val candidate = f.call { f.bridge.createCandidate(f.grantWire, it) }
        val missing = f.failure { f.bridge.enroll(f.probe, candidate, f.grantWire, IdentityEnrollmentOptions(0.5, "q", "r1"), BridgeSelection.Explicit("missing"), it) }
        assertEquals(BridgeError.BACKEND_NOT_REGISTERED, (missing as IdentityBridgeFailure.Rejected).code)
        val unavailable = f.failure { f.bridge.enroll(f.probe, candidate, f.grantWire, IdentityEnrollmentOptions(0.5, "q", "r1"), BridgeSelection.Explicit("optional.fixture"), it) }
        assertEquals(BridgeError.BACKEND_UNAVAILABLE, (unavailable as IdentityBridgeFailure.Rejected).code)
        val wrongBackend = f.failure { f.bridge.createGalleryBuilder(null, f.grantWire, IdentityGalleryOptions(f.policy(IdentityPolicyKind.IDENTIFICATION), "subject-max-v1"), BridgeSelection.Explicit("optional.fixture"), it) }
        assertEquals(BridgeError.BACKEND_UNAVAILABLE, (wrongBackend as IdentityBridgeFailure.Rejected).code)
        val malformed = f.failure { f.bridge.createCandidate(IdentityAuthorizationGrant("{}"), it) }
        assertEquals(BridgeError.INVALID_INPUT, (malformed as IdentityBridgeFailure.Rejected).code)
        assertEquals(0, f.embeddings.get())
    }

    @Test fun enrollmentCancellationWaitsForNativeStopAndBridgeCloseBorrowsStores(): Unit = Fixture(block = true).use { f ->
        val candidate = f.call { f.bridge.createCandidate(f.grantWire, it) }
        val callback = Capture<io.ondeviceai.binding.IdentityEnrollmentResult>(f.callbackThreads)
        val task = f.bridge.enroll(f.probe, candidate, f.grantWire, IdentityEnrollmentOptions(0.5, "q", "r1"), callback = callback)
        assertTrue(f.entered.await(2, TimeUnit.SECONDS))
        task.cancel()
        assertTrue(f.stopped.await(2, TimeUnit.SECONDS), "cancel never reached real native signal")
        assertFalse(callback.done.await(30, TimeUnit.MILLISECONDS), "bridge invented terminal before backend drain")
        f.release.countDown()
        assertEquals(IdentityBridgeFailure.Cancelled, callback.failure())
        assertEquals(1, callback.terminals.get())
        f.bridge.close()
        val rejected = f.failure { f.bridge.createCandidate(f.grantWire, it) }
        assertEquals(BridgeError.CLOSED, (rejected as IdentityBridgeFailure.Rejected).code)
        // Native stores and engines belong to the host; adapter close must not close them.
        assertNotNull(f.store.createCandidate(f.grant))
    }

    @Test fun activeLivenessPreservesIssuedChallengeEventsResultAndSingleUseProof(): Unit = Fixture().use { f ->
        val session = f.call { f.bridge.openLivenessSession(f.grantWire, io.ondeviceai.binding.IdentityLivenessOptions(f.padPolicy, "login", "pad-options"), BridgeSelection.Explicit("pad.fixture"), it) }
        f.call { session.start(it) }
        assertEquals(io.ondeviceai.binding.IdentityLivenessEvent.Started, f.call<io.ondeviceai.binding.IdentityLivenessEvent?> { session.nextEvent(it) })
        val event = f.call { session.nextEvent(it) } as io.ondeviceai.binding.IdentityLivenessEvent.ChallengeIssued
        assertTrue(session.submitFrame(io.ondeviceai.binding.IdentityLivenessFrame(byteArrayOf(1, 2), System.nanoTime(), "frame-v1", event.challenge)))
        val result = f.call { session.result(it) }
        assertEquals(io.ondeviceai.binding.IdentityLivenessOutcome.LIVE, result.outcome)
        assertEquals("pad-options", result.metadata.optionsRevision)
        assertNull(result.metadata.preprocessingRevision, "PAD native result has no preprocessing revision")
        assertEquals("frame-v1", result.metadata.inputRevision)
        assertEquals(session.sessionId, result.metadata.sessionId)
        assertEquals(1, result.acceptedFrameCount)
        val proof = assertNotNull(result.proof)
        f.call { session.consumeProof(proof, result.captureDigest, "login", it) }
        val duplicate = f.failure { session.consumeProof(proof, result.captureDigest, "login", it) }
        assertEquals(BridgeError.INVALID_CHALLENGE, (duplicate as IdentityBridgeFailure.Rejected).code)
        session.close()
    }

    @Test fun alteredChallengeReachesNativeSessionValidationAndFailsClosed() : Unit = Fixture().use { f ->
        val session = f.call { f.bridge.openLivenessSession(f.grantWire, io.ondeviceai.binding.IdentityLivenessOptions(f.padPolicy, "login", "pad-options"), callback = it) }
        f.call { session.start(it) }
        f.call { session.nextEvent(it) }
        val event = f.call { session.nextEvent(it) } as io.ondeviceai.binding.IdentityLivenessEvent.ChallengeIssued
        val challenge = LivenessChallenge.decodeJSON(event.challenge.canonicalValue)
        assertEquals(session.sessionId, challenge.sessionId)
        val altered = io.ondeviceai.binding.IdentityLivenessChallenge(challenge.copy(nonce = "altered").canonicalJSON())
        assertFalse(session.submitFrame(io.ondeviceai.binding.IdentityLivenessFrame(byteArrayOf(1), System.nanoTime(), "altered-frame", altered)))
        assertFalse(runCatching {
            session.submitFrame(io.ondeviceai.binding.IdentityLivenessFrame(byteArrayOf(1), System.nanoTime(), "malformed-frame", io.ondeviceai.binding.IdentityLivenessChallenge("{\"broken\":true}")))
        }.getOrDefault(true), "malformed opaque challenge must fail closed")
        val terminal = f.failure { session.result(it) }
        assertEquals(BridgeError.INVALID_CHALLENGE, (terminal as IdentityBridgeFailure.Rejected).code)
        session.close()
    }

    @Test fun callbackWaitsForNativeCancellationHandlerDrain() : Unit = Fixture(block = true, blockStop = true).use { f ->
        val candidate = f.call { f.bridge.createCandidate(f.grantWire, it) }
        val callback = Capture<io.ondeviceai.binding.IdentityEnrollmentResult>(f.callbackThreads)
        val task = f.bridge.enroll(f.probe, candidate, f.grantWire, IdentityEnrollmentOptions(0.5, "q", "r1"), callback = callback)
        assertTrue(f.entered.await(2, TimeUnit.SECONDS))
        task.cancel()
        assertTrue(f.stopped.await(2, TimeUnit.SECONDS))
        f.release.countDown()
        assertFalse(callback.done.await(100, TimeUnit.MILLISECONDS), "terminal callback overtook native stop handler drain")
        f.stopRelease.countDown()
        assertEquals(IdentityBridgeFailure.Cancelled, callback.failure())
        assertEquals(1, callback.terminals.get())
    }

    @Test fun nativeVerificationRevocationRemainsAuthorizationFailureAfterCallerCancel() : Unit = Fixture().use { f ->
        val candidate = f.call { f.bridge.createCandidate(f.grantWire, it) }
        val template = assertNotNull(f.call { f.bridge.enroll(f.probe, candidate, f.grantWire, IdentityEnrollmentOptions(0.5, "q", "r1"), callback = it) }.template)
        f.blocking.set(true)
        val callback = Capture<io.ondeviceai.binding.IdentityVerificationResult>(f.callbackThreads)
        val task = f.bridge.verify(f.probe, template, f.grantWire, IdentityVerificationOptions(f.policy(IdentityPolicyKind.VERIFICATION), "verify-revoked"), callback = callback)
        assertTrue(f.entered.await(2, TimeUnit.SECONDS))
        val receipt = f.call { f.bridge.revokeGrant(f.grant.grantId, IdentityOwnershipAuthorization(f.store.storeId, "owner"), it) }
        assertTrue(f.stopped.await(2, TimeUnit.SECONDS))
        task.cancel()
        f.release.countDown()
        val failure = callback.failure() as IdentityBridgeFailure.Rejected
        assertEquals(BridgeError.AUTHORIZATION_REVOKED, failure.code)
        assertEquals(f.backend.backendId, failure.backendId)
        assertEquals("policy", failure.policyId); assertNotNull(failure.sessionId)
        assertEquals(1, callback.terminals.get())
        assertEquals(BridgeDeletion.PURGED, f.call { f.bridge.awaitRevocation(receipt, IdentityDeletionWaitOptions(null), it) })
    }

    @Test fun deletionWaitHonorsDurationCancellationAndDoesNotRestoreDeletedTemplate() : Unit = Fixture().use { f ->
        val candidate = f.call { f.bridge.createCandidate(f.grantWire, it) }
        val wire = assertNotNull(f.call { f.bridge.enroll(f.probe, candidate, f.grantWire, IdentityEnrollmentOptions(0.5, "q", "r1"), callback = it) }.template)
        val template = TemplateRef.decodeJSON(wire.canonicalValue)
        val lease = f.store.acquireTemplateLease(template, f.grant, IdentityScope.VERIFICATION, f.backend.embeddingSpace)
        try {
            val receipt = f.call { f.bridge.deleteTemplate(wire, IdentityOwnershipAuthorization(f.store.storeId, "owner"), it) }
            assertEquals(BridgeDeletion.PURGE_PENDING, f.call { f.bridge.awaitDeletion(receipt, IdentityDeletionWaitOptions(1_000_000), it) })
            val callback = Capture<io.ondeviceai.binding.IdentityDeletionStatus>(f.callbackThreads)
            val task = f.bridge.awaitDeletion(receipt, IdentityDeletionWaitOptions(5_000_000_000), callback)
            assertFalse(callback.done.await(30, TimeUnit.MILLISECONDS), "wait duration was ignored")
            task.cancel()
            assertEquals(IdentityBridgeFailure.Cancelled, callback.failure())
            assertEquals(BridgeDeletion.PURGE_PENDING, f.call { f.bridge.awaitDeletion(receipt, IdentityDeletionWaitOptions(null), it) })
        } finally { lease.close() }
        val rejected = f.failure { f.bridge.verify(f.probe, wire, f.grantWire, IdentityVerificationOptions(f.policy(IdentityPolicyKind.VERIFICATION), "deleted"), callback = it) }
        assertEquals(BridgeError.TEMPLATE_REVOKED, (rejected as IdentityBridgeFailure.Rejected).code)
    }

    private class Capture<T>(private val threads: MutableList<String>) : IdentityCallback<T> {
        val done = CountDownLatch(1); val terminals = AtomicInteger()
        private var value: T? = null; private var error: IdentityBridgeFailure? = null
        override fun onSuccess(value: T) { threads.add(Thread.currentThread().name); this.value = value; terminals.incrementAndGet(); done.countDown() }
        override fun onFailure(error: IdentityBridgeFailure) { threads.add(Thread.currentThread().name); this.error = error; terminals.incrementAndGet(); done.countDown() }
        fun value(): T { assertTrue(done.await(3, TimeUnit.SECONDS), "callback timed out"); assertNull(error); @Suppress("UNCHECKED_CAST") return value as T }
        fun failure(): IdentityBridgeFailure { assertTrue(done.await(3, TimeUnit.SECONDS), "callback timed out"); return assertNotNull(error) }
    }

    private class Fixture(block: Boolean = false, blockStop: Boolean = false) : AutoCloseable {
        val root = Files.createTempDirectory("identity-bridge-test")
        val entered = CountDownLatch(1); val stopped = CountDownLatch(1); val release = CountDownLatch(1); val stopRelease = CountDownLatch(1)
        val embeddings = AtomicInteger()
        val blocking = java.util.concurrent.atomic.AtomicBoolean(block)
        val callbackThreads = java.util.Collections.synchronizedList(mutableListOf<String>())
        private val ui = Executors.newSingleThreadExecutor { Thread(it, "identity-bridge-test-ui").apply { isDaemon = true } }
        private val keys = object : IdentityKeyProvider {
            val values = mutableMapOf<String, SecretKey>()
            @Synchronized override fun key(id: String, createIfMissing: Boolean): SecretKey = values[id] ?: run {
                if (!createIfMissing) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { values[id] = it }
            }
            @Synchronized override fun deleteKey(id: String) { values.remove(id) }
        }
        private val authority = object : IdentityAuthorizationProvider {
            override fun authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Long, nowMillis: Long) = maxOf(minimumEpoch, grant.authorizationEpoch)
            override fun validateOwnership(authorization: OwnershipAuthorization, nowMillis: Long) { if (authorization.evidenceRef != "owner") throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED) }
        }
        val store = IdentityStore(root.resolve("identity").toFile(), "login", authority, keys, object : IdentityWallClock { override val nowMillis = 10L }, SystemIdentityMonotonicClock)
        private val base = FixtureIdentityBackend.system()
        val backend = object : IdentityBackend by base {
            override fun embed(probe: IdentityProbe): IdentityEmbedding { embeddings.incrementAndGet(); return base.embed(probe) }
            override fun embed(probe: IdentityProbe, stopSignal: IdentityStopSignal): IdentityEmbedding {
                if (blocking.get()) {
                    val registration = stopSignal.registerStopHandler { stopped.countDown(); if (blockStop) check(stopRelease.await(3, TimeUnit.SECONDS)) }
                    entered.countDown()
                    try { check(release.await(3, TimeUnit.SECONDS)); stopSignal.check() } finally { registration.close() }
                }
                return embed(probe)
            }
        }
        private val backends = listOf(backend, FixtureIdentityBackend.optionalUnavailable())
        val index = IndexStore(root.resolve("index"), store, keys) { embeddings, signal -> signal.check(); fixtureIdentificationIndex(embeddings) }
        private val verifier = FaceVerification(store, backend.backendId, backends)
        private val identifier = FaceIdentification(store, index, backend.backendId, backends)
        private val padBackend = object : LivenessBackend {
            override val backendId = "pad.fixture"
            override val backendVersion = "pad-v1"
            override val modelBinding = ModelBinding(ModelBindingKind.SYSTEM, backendId, "os-test")
            override fun availability() = io.ondeviceai.core.CapabilityAvailability(io.ondeviceai.core.CapabilityStatus.AVAILABLE, backendId = backendId)
            override fun evaluate(frame: LivenessFrame, challenge: LivenessChallenge?, stopSignal: IdentityStopSignal): LivenessDecision {
                stopSignal.check(); assertNotNull(challenge)
                return LivenessDecision(LivenessOutcome.LIVE, IdentityQuality(1, 1.0))
            }
            override fun close() {}
        }
        private val liveness = FaceLiveness(store, listOf(padBackend))
        val padPolicy = io.ondeviceai.binding.IdentityPADPolicy(PADPolicy("pad-policy", "1", padBackend.backendId, padBackend.backendVersion, padBackend.modelBinding,
            CalibrationStatus.VERIFIED, PADMode.ACTIVE, "challenge-v1", 1, 5_000_000_000, 0.5, classificationPolicyRevision = "class-v1", datasetRevision = "synthetic-pad", operatingPoint = "synthetic-point", attackTypes = setOf("print")).canonicalJSON())
        val grant = AuthorizationGrant("grant", "domain", IdentityScope.entries.toSet(), "login", 0, null, 0, "evidence")
        val grantWire = IdentityAuthorizationGrant(grant.canonicalJSON())
        val probe = IdentityProbeReference("host-owned-input")
        val bridge = AndroidIdentityBridge(store, index, IdentityBackendRegistry(backend.backendId, backends.map { IdentityBackendRegistration(it.backendId, it) }), verifier, identifier, liveness,
            probeResolver = { reference -> if (reference == probe) IdentityProbe("probe", listOf(1f, 0f)) else throw IdentityError(IdentityErrorCode.INVALID_INPUT) }, callbackExecutor = ui)
        fun policy(kind: IdentityPolicyKind): IdentityThresholdPolicy = IdentityThresholdPolicy(ThresholdPolicy("policy", "r1", backend.embeddingSpace, "cosine", ScoreDirection.HIGHER_IS_BETTER, CalibrationStatus.VERIFIED, kind, 0.8, 0.5, "synthetic-point", "synthetic-data", if (kind == IdentityPolicyKind.IDENTIFICATION) 1..32 else null, "multi-template").canonicalJSON())
        fun <T> call(start: (IdentityCallback<T>) -> IdentityBridgeTask): T = Capture<T>(callbackThreads).also { start(it) }.value()
        fun <T> failure(start: (IdentityCallback<T>) -> IdentityBridgeTask): IdentityBridgeFailure = Capture<T>(callbackThreads).also { start(it) }.failure()
        override fun close() {
            release.countDown(); stopRelease.countDown(); bridge.close(); liveness.close(); verifier.close(); identifier.close(); index.close(); store.close(); ui.shutdown()
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
