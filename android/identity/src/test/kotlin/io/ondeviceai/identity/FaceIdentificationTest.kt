package io.ondeviceai.identity

import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.TaskTerminal
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.test.AfterTest
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FaceIdentificationTest {
    @Test fun aggregateBeforeTopKPreservesTheSecondDistinctCandidate() {
        val snapshot = snapshot(listOf("t1" to "A", "t2" to "A", "t3" to "B", "t4" to "C"))
        val values = rankIdentificationCandidates(snapshot, mapOf("t1" to 0.9, "t2" to 0.8, "t3" to 0.7, "t4" to 0.6), 2)
        assertEquals(listOf("A", "B"), values.map { it.candidateRef.id })
        assertEquals(listOf(0.9, 0.7), values.map { it.score })
        assertEquals(listOf(1, 2), values.map { it.rank })
    }

    @Test fun subjectMaximumIsIndependentOfTemplateOrder() {
        val value = snapshot(listOf("a-first" to "A", "z-last" to "A", "middle" to "B"))
        val values = rankIdentificationCandidates(value, mapOf("a-first" to 0.5, "z-last" to 0.9, "middle" to 0.8))
        assertEquals(listOf("A", "B"), values.map { it.candidateRef.id })
        assertEquals(listOf(0.9, 0.8), values.map { it.score })
    }

    @Test fun equalScoresUseOpaqueByteOrder() {
        val value = snapshot(listOf("t1" to "a", "t2" to "Z", "t3" to "A"))
        val values = rankIdentificationCandidates(value, mapOf("t1" to 0.9, "t2" to 0.9, "t3" to 0.9))
        assertEquals(listOf("A", "Z", "a"), values.map { it.candidateRef.id })
    }

    @Test fun thresholdIsInclusiveAndUnknownLeaksNoBelowThresholdCandidate() {
        val value = snapshot(listOf("t1" to "A", "t2" to "B"))
        val atThreshold = rankIdentificationCandidates(value, mapOf("t1" to 0.5, "t2" to 0.49))
        assertEquals(listOf("A"), atThreshold.map { it.candidateRef.id })
        assertTrue(rankIdentificationCandidates(value, mapOf("t1" to -0.2, "t2" to 0.49)).isEmpty())
    }

    @Test fun defaultTopKReturnsAtMost32DistinctCandidates() {
        val pairs = (0 until 34).map { "t-$it" to "c%02d".format(it) }
        val values = rankIdentificationCandidates(snapshot(pairs), pairs.associate { it.first to 0.9 })
        assertEquals(32, values.size)
        assertEquals("c00", values.first().candidateRef.id)
        assertEquals("c31", values.last().candidateRef.id)
        assertEquals(32, values.last().rank)
    }

    @Test fun invalidTopKAndIncompleteOrNonfiniteScoresFailClosed() {
        val value = snapshot(listOf("t1" to "A"))
        for (topK in listOf(0, 33)) assertEquals(IdentityErrorCode.INVALID_INPUT,
            assertFailsWith<IdentityError> { rankIdentificationCandidates(value, mapOf("t1" to 0.9), topK) }.code)
        for (scores in listOf(emptyMap(), mapOf("t1" to 0.9, "extra" to 0.9), mapOf("t1" to Double.NaN), mapOf("t1" to Double.POSITIVE_INFINITY), mapOf("t1" to 1.1))) {
            assertEquals(IdentityErrorCode.INVALID_INPUT, assertFailsWith<IdentityError> { rankIdentificationCandidates(value, scores) }.code)
        }
    }

    @Test fun unknownAggregationPolicyNeverUsesAnImplicitFallback() {
        val value = snapshot(listOf("t1" to "A"), "unsupported-v1")
        assertEquals(IdentityErrorCode.UNSUPPORTED_OPTION,
            assertFailsWith<IdentityError> { rankIdentificationCandidates(value, mapOf("t1" to 0.9)) }.code)
    }

    private val fixtures = mutableListOf<IdentificationEngineFixture>()
    @AfterTest fun cleanup() { fixtures.forEach { it.close() } }
    private fun fixture(payload: ByteArray? = null) = IdentificationEngineFixture(payload).also(fixtures::add)

    @Test fun independentQueryDomainEpochDoesNotReplaceGalleryPolicyEpoch() {
        val f = fixture(); val engine = f.engine()
        val query = AuthorizationGrant("query-new", "independent-query-domain", setOf(IdentityScope.IDENTIFICATION),
            "identity", 0, null, 7, "fixture")
        val result = engine.identify(f.probe, f.gallery, query, IdentificationOptions(revision = "1")).result()
        assertEquals(IdentificationOutcome.CANDIDATES, result.outcome)
        assertEquals(f.gallery.revision, result.galleryRevision)
        assertEquals("identify-policy", result.thresholdPolicyId)
        engine.close()
    }

    @Test fun engineAggregatesTemplatesAndReportsFixedSnapshotConditions() {
        val f = fixture(); val engine = f.engine()
        val task = engine.identify(f.probe, f.gallery, f.query, IdentificationOptions(topK = 2, revision = "search-1"))
        val result = task.result()
        assertEquals(IdentificationOutcome.CANDIDATES, result.outcome)
        assertEquals(f.candidates, result.candidates.map { it.candidateRef })
        assertEquals(listOf(1, 2), result.candidates.map { it.rank })
        assertEquals(f.gallery.revision, result.galleryRevision)
        assertEquals(f.gallery.revision, result.galleryContentDigest)
        assertEquals(2, result.galleryCandidateCount); assertEquals(3, result.galleryTemplateCount)
        assertEquals(listOf(1, 2), result.enrollmentCounts.map { it.templateCount }.sorted())
        assertEquals("synthetic-only", result.fpirFnirGrouping.datasetRevision)
        assertEquals("fixture", result.fpirFnirGrouping.operatingPoint)
        assertEquals(2, result.fpirFnirGrouping.topK)
        assertEquals("identify-policy", result.thresholdPolicyId); assertEquals("1", result.thresholdPolicyRevision)
        assertEquals("subject-max-v1", result.aggregationPolicyId); assertEquals("search-1", result.optionsRevision)
        assertEquals(f.base.embeddingSpace.preprocessRevision, result.preprocessingRevision)
        assertEquals(f.base.modelBinding, result.modelBinding); assertEquals(f.base.backendId, result.backendId)
        assertEquals(task.runId, result.sessionId); assertEquals(0L, result.durationNanos)
        assertEquals(TaskTerminal.COMPLETED, task.terminal)
        f.identity.revokeGrant(f.origin.grantId, f.owner); task.cancel(); task.close()
        assertEquals(2, task.result().candidates.size); assertEquals(TaskTerminal.COMPLETED, task.terminal)
        engine.close()
    }

    @Test fun unknownHasNoBelowThresholdCandidateOrScoreLeak() {
        val f = fixture(); val engine = f.engine()
        val result = engine.identify(IdentityProbe("unknown", listOf(-1f, 0f)), f.gallery, f.query,
            IdentificationOptions(revision = "1")).result()
        assertEquals(IdentificationOutcome.UNKNOWN, result.outcome); assertTrue(result.candidates.isEmpty())
        assertNull(result.scoreFields.score); assertEquals(2, result.enrollmentCounts.size)
        assertEquals("cosine", result.thresholdFields?.metric)
        assertEquals(0.7, result.thresholdFields?.threshold)
        engine.close()
    }

    @Test fun qualityRejectionSkipsEmbeddingAndKeepsDecisionFieldsNull() {
        val f = fixture(); val backend = IdentificationTestBackend(f.base, IdentityQuality(1, 0.1)); val engine = f.engine(backend)
        val result = engine.identify(f.probe, f.gallery, f.query, IdentificationOptions(revision = "1")).result()
        assertEquals(IdentificationOutcome.INSUFFICIENT_QUALITY, result.outcome)
        assertTrue(result.candidates.isEmpty()); assertNull(result.scoreFields.score)
        assertNull(result.scoreFields.metric); assertNull(result.scoreFields.threshold)
        assertNull(result.thresholdFields)
        assertEquals(2, result.enrollmentCounts.size); assertFalse(backend.didEmbed)
        engine.close()
    }

    @Test fun multiFaceAndUnsupportedIndexFailWithSafeMetadata() {
        val f = fixture(byteArrayOf(1)); val backend = IdentificationTestBackend(f.base, IdentityQuality(2, 1.0))
        val engine = f.engine(backend)
        val task = engine.identify(f.probe, f.gallery, f.query, IdentificationOptions(revision = "1"))
        val error = assertFailsWith<IdentityError> { task.result() }
        assertEquals(IdentityErrorCode.MULTIPLE_FACES, error.code); assertEquals(f.base.backendId, error.backendId)
        engine.close()
        val next = f.engine()
        val unsupported = next.identify(f.probe, f.gallery, f.query, IdentificationOptions(revision = "1"))
        val indexError = assertFailsWith<IdentityError> { unsupported.result() }
        assertEquals(IdentityErrorCode.FEATURE_FORMAT_UNSUPPORTED, indexError.code)
        assertEquals("identify-policy", indexError.policyId); next.close()
    }

    @Test fun invalidOptionsExplicitBackendPolicyAndSpaceNeverFallback() {
        listOf(0, 33).forEach { assertFailsWith<IdentityError> { IdentificationOptions(it, revision = "1") } }
        val f = fixture(); val unavailable = FixtureIdentityBackend.optionalUnavailable()
        val engine = FaceIdentification(f.identity, f.index, f.base.backendId, listOf(f.base, unavailable))
        listOf(IdentityBackendSelection.Explicit("missing"), IdentityBackendSelection.Explicit(unavailable.backendId)).forEach { backend ->
            val error = assertFailsWith<IdentityError> { engine.identify(f.probe, f.gallery, f.query,
                IdentificationOptions(revision = "1"), backend) }
            assertTrue(error.code in listOf(IdentityErrorCode.BACKEND_NOT_REGISTERED, IdentityErrorCode.BACKEND_UNAVAILABLE))
        }
        assertEquals(IdentityErrorCode.THRESHOLD_UNVERIFIED, assertFailsWith<IdentityError> {
            engine.identify(f.probe, f.gallery, f.query, IdentificationOptions(policy = ThresholdPolicyRef("identify-policy", "2"), revision = "1"))
        }.code)
        engine.close()
        val different = f.engine(FixtureIdentityBackend.system(revision = "different-space"))
        assertEquals(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH, assertFailsWith<IdentityError> {
            different.identify(f.probe, f.gallery, f.query, IdentificationOptions(revision = "1"))
        }.code)
        different.close()
    }

    @Test fun deletedGalleryAndEmptyBuilderAreStableErrors() {
        val f = fixture(); val empty = f.index.createGalleryBuilder(grant = f.origin, policy = f.policy)
        assertEquals(IdentityErrorCode.INVALID_INPUT, assertFailsWith<IdentityError> { empty.publish() }.code); empty.close()
        f.index.deleteGallery(f.gallery, f.owner)
        val engine = f.engine()
        assertEquals(IdentityErrorCode.GALLERY_REVISION_GONE, assertFailsWith<IdentityError> {
            engine.identify(f.probe, f.gallery, f.query, IdentificationOptions(revision = "1"))
        }.code); engine.close()
    }

    @Test fun cancelAndOriginRevokeStopBackendAndChooseFinalGateWinner() {
        for (revoke in listOf(false, true)) {
            val f = fixture(); val backend = IdentificationTestBackend(f.base, blocks = true); val engine = f.engine(backend)
            val task = engine.identify(f.probe, f.gallery, f.query, IdentificationOptions(revision = "1"))
            assertTrue(backend.entered.await(2, TimeUnit.SECONDS))
            if (revoke) f.identity.revokeGrant(f.origin.grantId, f.owner) else task.cancel()
            assertEquals(if (revoke) IdentityErrorCode.AUTHORIZATION_REVOKED else IdentityErrorCode.CANCELLED,
                assertFailsWith<IdentityError> { task.result() }.code)
            assertEquals(if (revoke) TaskTerminal.FAILED else TaskTerminal.CANCELLED, task.terminal)
            task.close(); engine.close()
        }
    }

    @Test fun finalProviderCancellationSuppressesPublicationAndCloseIsIdempotent() {
        val f = fixture(); val backend = IdentificationTestBackend(f.base, blocks = true); val engine = f.engine(backend)
        val task = engine.identify(f.probe, f.gallery, f.query, IdentificationOptions(revision = "1"))
        assertTrue(backend.entered.await(2, TimeUnit.SECONDS))
        f.authority.nextQuery = { task.cancel() }; backend.release.countDown()
        assertEquals(IdentityErrorCode.CANCELLED, assertFailsWith<IdentityError> { task.result() }.code)
        engine.close(); engine.close(); assertEquals(1, backend.closeCount.get())
    }

    @Test fun memberRevokeOrGalleryDeleteInvalidatesWholeRunningSnapshot() {
        for (delete in listOf(false, true)) {
            val f = fixture(); val backend = IdentificationTestBackend(f.base, blocks = true); val engine = f.engine(backend)
            val task = engine.identify(f.probe, f.gallery, f.query, IdentificationOptions(revision = "1"))
            assertTrue(backend.entered.await(2, TimeUnit.SECONDS))
            if (delete) f.index.deleteGallery(f.gallery, f.owner) else f.identity.revokeGrant("member-0", f.owner)
            assertEquals(IdentityErrorCode.AUTHORIZATION_REVOKED, assertFailsWith<IdentityError> { task.result() }.code)
            assertEquals(TaskTerminal.FAILED, task.terminal); engine.close()
        }
    }

    @Test fun busyEngineCloseStopsRunAndDrainsReservedNativeCallback() {
        val f = fixture(); val stopped = CountDownLatch(1); val permit = CountDownLatch(1)
        val backend = IdentificationTestBackend(f.base, blocks = true, onStop = { stopped.countDown(); permit.await(3, TimeUnit.SECONDS) })
        val engine = f.engine(backend)
        val task = engine.identify(f.probe, f.gallery, f.query, IdentificationOptions(revision = "1"))
        assertTrue(backend.entered.await(2, TimeUnit.SECONDS))
        assertEquals(IdentityErrorCode.BUSY, assertFailsWith<IdentityError> {
            engine.identify(f.probe, f.gallery, f.query, IdentificationOptions(revision = "1"))
        }.code)
        val executor = Executors.newSingleThreadExecutor()
        val closing = executor.submit { engine.close() }
        try {
            assertTrue(stopped.await(2, TimeUnit.SECONDS)); assertFalse(closing.isDone); assertEquals(0, backend.closeCount.get())
            permit.countDown(); closing.get(2, TimeUnit.SECONDS)
            assertEquals(IdentityErrorCode.CLOSED, assertFailsWith<IdentityError> { task.result() }.code)
            engine.close(); assertEquals(1, backend.closeCount.get())
        } finally { permit.countDown(); executor.shutdownNow() }
    }

    @Test fun closeWaitsForAdmissionAndStopsTheAdmittedTaskBeforeBackendWork() {
        val f = fixture(); val backend = IdentificationTestBackend(f.base); val engine = f.engine(backend)
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        f.authority.nextQuery = { entered.countDown(); release.await(3, TimeUnit.SECONDS) }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val admission = executor.submit<IdentificationTask> { engine.identify(f.probe, f.gallery, f.query, IdentificationOptions(revision = "1")) }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val closing = executor.submit { engine.close() }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            var observedClosed = false
            while (System.nanoTime() < deadline) {
                val error = assertFailsWith<IdentityError> { engine.identify(f.probe, f.gallery, f.query, IdentificationOptions(revision = "1")) }
                if (error.code == IdentityErrorCode.CLOSED) { observedClosed = true; break }
                assertEquals(IdentityErrorCode.BUSY, error.code); Thread.yield()
            }
            assertTrue(observedClosed); assertEquals(0, backend.closeCount.get()); assertFalse(closing.isDone)
            release.countDown()
            val task = admission.get(2, TimeUnit.SECONDS); closing.get(2, TimeUnit.SECONDS)
            assertEquals(IdentityErrorCode.CLOSED, assertFailsWith<IdentityError> { task.result() }.code)
            assertFalse(backend.didEmbed); assertEquals(1, backend.closeCount.get())
        } finally { release.countDown(); executor.shutdownNow(); engine.close() }
    }

    @Test fun resolvedBindingFailureIncludesSafeBackendMetadata() {
        val f = fixture(); val backend = IdentificationTestBackend(f.base, declaredId = "incompatible.fixture")
        val engine = f.engine(backend)
        val error = assertFailsWith<IdentityError> { engine.identify(f.probe, f.gallery, f.query, IdentificationOptions(revision = "1")) }
        assertEquals(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH, error.code)
        assertEquals(backend.backendId, error.backendId); assertEquals(backend.modelBinding, error.modelBinding); assertNull(error.sessionId)
        engine.close()
    }

    private fun snapshot(pairs: List<Pair<String, String>>, aggregation: String = "subject-max-v1"): GallerySnapshotRecord {
        val space = FixtureIdentityBackend.system().embeddingSpace
        val grant = AuthorizationGrant("grant", "domain", setOf(IdentityScope.IDENTIFICATION), "identity", 0, null, 0, "fixture")
        val policy = ThresholdPolicy("identify", "1", space, "cosine", ScoreDirection.HIGHER_IS_BETTER,
            CalibrationStatus.VERIFIED, IdentityPolicyKind.IDENTIFICATION, 0.5, 0.8, "fixture", "fixture", 1..64, "fixture-multiple", 0)
        return GallerySnapshotRecord.create("index", "store", "gallery", null,
            pairs.map { (template, candidate) -> GallerySnapshotMember(TemplateRef(template, space, 0, "1"), CandidateRef(candidate, "store"), grant) },
            grant, policy, aggregation)
    }
}

private class IdentificationEngineFixture(payload: ByteArray?) : AutoCloseable {
    val root = Files.createTempDirectory("identification-engine")
    val keys = IdentificationTestKeys(); val authority = IdentificationTestAuthority()
    val identity = IdentityStore(root.resolve("identity").toFile(), "identity", authority, keys, IdentificationTestWall, IdentificationTestMono)
    val base = FixtureIdentityBackend.system()
    val origin = identificationGrant("origin", "origin-domain"); val query = identificationGrant("query", "query-domain")
    val owner = OwnershipAuthorization(identity.storeId, "owner")
    val candidates = mutableListOf<CandidateRef>()
    val policy = ThresholdPolicy("identify-policy", "1", base.embeddingSpace, "cosine", ScoreDirection.HIGHER_IS_BETTER,
        CalibrationStatus.VERIFIED, IdentityPolicyKind.IDENTIFICATION, 0.7, 0.5, "fixture", "synthetic-only", 1..32, "fixture-multiple")
    val index = IndexStore(root.resolve("index"), identity, keys) { embeddings, _ -> payload ?: fixtureIdentificationIndex(embeddings) }
    val gallery: GalleryRef
    val probe get() = IdentityProbe("probe", listOf(1f, 0f))
    init {
        val builder = index.createGalleryBuilder(grant = origin, policy = policy)
        listOf(listOf(listOf(1f, 0f), listOf(0.9f, 0.1f)), listOf(listOf(0.8f, 0.6f))).forEachIndexed { i, vectors ->
            val grant = identificationGrant("member-$i", "member-domain-$i")
            val candidate = identity.createCandidate(grant); candidates.add(candidate)
            vectors.forEach { vector ->
                val result = identity.enroll(IdentityProbe("enroll", vector), candidate, grant, EnrollmentOptions(0.5, "q", "1"), base)
                builder.addTemplate(requireNotNull(result.templateRef), grant)
            }
        }
        gallery = builder.publish()
    }
    fun engine(backend: IdentityBackend = base) = FaceIdentification(identity, index, backend.backendId, listOf(backend), IdentificationTestMono)
    override fun close() {
        index.close(); identity.close()
        Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
}
private fun identificationGrant(id: String, domain: String) = AuthorizationGrant(id, domain,
    setOf(IdentityScope.ENROLLMENT, IdentityScope.IDENTIFICATION), "identity", 0, null, 0, "fixture")
private class IdentificationTestKeys : IdentityKeyProvider {
    private val keys = mutableMapOf<String, SecretKey>()
    @Synchronized override fun key(id: String, createIfMissing: Boolean): SecretKey = keys[id] ?: run {
        if (!createIfMissing) throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
        SecretKeySpec(ByteArray(32) { 7 }, "AES").also { keys[id] = it }
    }
    @Synchronized override fun deleteKey(id: String) { keys.remove(id) }
}
private class IdentificationTestAuthority : IdentityAuthorizationProvider {
    @Volatile var nextQuery: (() -> Unit)? = null
    override fun authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Long, nowMillis: Long): Long {
        val action = if (grant.grantId == "query") nextQuery.also { nextQuery = null } else null
        action?.invoke(); return grant.authorizationEpoch
    }
    override fun validateOwnership(authorization: OwnershipAuthorization, nowMillis: Long) {}
}
private object IdentificationTestWall : IdentityWallClock { override val nowMillis = 100L }
private object IdentificationTestMono : IdentityMonotonicClock { override val nowNanos = 1L }
private class IdentificationTestBackend(val base: FixtureIdentityBackend, val quality: IdentityQuality = IdentityQuality(1, 1.0),
    val blocks: Boolean = false, val onStop: (() -> Unit)? = null, val declaredId: String? = null) : IdentityBackend {
    val entered = CountDownLatch(1); val release = CountDownLatch(1); val closeCount = AtomicInteger()
    @Volatile var didEmbed = false
    override val backendId get() = declaredId ?: base.backendId; override val backendVersion get() = base.backendVersion
    override val modelBinding get() = base.modelBinding; override val embeddingSpace get() = base.embeddingSpace
    override fun availability() = base.availability()
    override fun evaluateQuality(probe: IdentityProbe) = quality
    override fun embed(probe: IdentityProbe) = base.embed(probe)
    override fun embed(probe: IdentityProbe, stopSignal: IdentityStopSignal): IdentityEmbedding {
        val registration = stopSignal.registerStopHandler { release.countDown(); onStop?.invoke() }
        try { didEmbed = true; entered.countDown(); if (blocks) release.await(3, TimeUnit.SECONDS)
            stopSignal.check(); return base.embed(probe)
        } finally { registration.close() }
    }
    override fun close() { closeCount.incrementAndGet() }
}
