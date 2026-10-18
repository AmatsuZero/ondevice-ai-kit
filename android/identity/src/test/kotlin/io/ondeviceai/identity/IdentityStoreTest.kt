package io.ondeviceai.identity

import kotlin.test.*
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus

class IdentityStoreTest {
    private val roots = mutableListOf<java.nio.file.Path>()

    @AfterTest fun cleanup() {
        roots.forEach { root ->
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } }
        }
    }
    @Test fun candidateCreationPersistsZeroEpochStoreIdAndGrantBinding() {
        val root = tempRoot()
        val keys = StoreKeys(); val provider = StoreAuthority()
        val store = store(root, keys, provider)
        val candidate = store.createCandidate(grant())
        val id = store.storeId
        store.close()
        val reopened = store(root, keys, provider)
        assertEquals(id, reopened.storeId)
        assertEquals(grant().canonicalJSON(), reopened.recordedCandidateGrant(candidate).canonicalJSON())
        reopened.close()
    }

    @Test fun authorityMismatchAndWrongPurposeDoNotPublish() {
        val root = tempRoot()
        val keys = StoreKeys(); val provider = StoreAuthority()
        val store = store(root, keys, provider)
        provider.epoch = 1
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { store.createCandidate(grant()) }
        provider.epoch = 0
        assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) { store.createCandidate(grant(purpose = "other")) }
        val candidate = store.createCandidate(grant())
        assertEquals("g1", store.recordedCandidateGrant(candidate).grantId)
        store.close()
    }

    @Test fun candidateReferencesAreRandomStoreBoundAndForgedRejected() {
        val keys = StoreKeys(); val provider = StoreAuthority()
        val first = store(tempRoot(), keys, provider)
        val second = store(tempRoot(), keys, provider)
        val candidate = first.createCandidate(grant())
        assertNotEquals(candidate, first.createCandidate(grant(id = "g2")))
        assertCode(IdentityErrorCode.CANDIDATE_NOT_FOUND) { second.recordedCandidateGrant(candidate) }
        assertCode(IdentityErrorCode.CANDIDATE_NOT_FOUND) { first.recordedCandidateGrant(CandidateRef("forged", first.storeId)) }
        first.close(); second.close()
    }

    @Test fun closeRejectsAdmissionAndCiphertextHidesMetadata() {
        val root = tempRoot()
        val store = store(root, StoreKeys(), StoreAuthority())
        store.createCandidate(grant())
        store.close(); store.close()
        assertCode(IdentityErrorCode.CLOSED) { store.createCandidate(grant(id = "later")) }
        assertFalse(Files.readAllBytes(root.resolve("identity-journal-store.bin")).decodeToString().contains("tenant.subject"))
    }

    @Test fun wrongPurposeRestorePreservesCiphertextAndReleasesWriter() {
        val root = tempRoot()
        val keys = StoreKeys(); val provider = StoreAuthority()
        val store = store(root, keys, provider)
        store.createCandidate(grant()); store.close()
        val file = root.resolve("identity-journal-store.bin"); val before = Files.readAllBytes(file)
        assertCode(IdentityErrorCode.INVALID_INPUT) { IdentityStore(root.toFile(), "other", provider, keys, StoreClock(100), StoreMono()) }
        assertContentEquals(before, Files.readAllBytes(file))
        store(root, keys, provider).close()
    }

    @Test fun failedCommitRetiresWriterAndKeepsLastCommittedCandidate() {
        val root = tempRoot()
        val keys = StoreKeys(); val provider = StoreAuthority()
        val store = store(root, keys, provider)
        val committed = store.createCandidate(grant())
        keys.reject = true
        assertCode(IdentityErrorCode.RE_ENROLLMENT_REQUIRED) { store.createCandidate(grant(id = "uncommitted")) }
        keys.reject = false
        assertCode(IdentityErrorCode.CLOSED) { store.createCandidate(grant(id = "later")) }
        val reopened = store(root, keys, provider)
        store.close()
        assertCode(IdentityErrorCode.BUSY) { store(root, keys, provider) }
        assertEquals("g1", reopened.recordedCandidateGrant(committed).grantId)
        reopened.close()
    }

    @Test fun publicationRechecksAuthorizationAfterStagingBeforeCommit() {
        val root = tempRoot()
        val keys = StoreKeys(); val provider = StoreAuthority()
        val store = IdentityStore(root.toFile(), "login", provider, keys, AdvancingClock(), StoreMono())
        val file = root.resolve("identity-journal-store.bin"); val before = Files.readAllBytes(file)
        assertCode(IdentityErrorCode.AUTHORIZATION_EXPIRED) { store.createCandidate(grant()) }
        assertContentEquals(before, Files.readAllBytes(file))
        store.close()
    }

    @Test fun reopenedPersistedEpochRejectsNewGrantBelowBound() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(4)
        val store = store(root, keys, provider)
        store.createCandidate(grant(epoch = 4)); store.close()
        provider.epoch = 3
        val reopened = store(root, keys, provider)
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { reopened.createCandidate(grant(id = "new", epoch = 3)) }
        reopened.close()
    }

    @Test fun concurrentCandidateCreationPersistsEveryUniqueReference() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority()
        val store = store(root, keys, provider)
        val executor = Executors.newFixedThreadPool(16)
        val refs = try {
            executor.invokeAll((0 until 16).map { index -> Callable { store.createCandidate(grant(id = "g$index")) } }).map { it.get() }
        } finally { executor.shutdownNow() }
        assertEquals(16, refs.toSet().size)
        store.close()
        val reopened = store(root, keys, provider)
        refs.forEachIndexed { index, ref -> assertEquals("g$index", reopened.recordedCandidateGrant(ref).grantId) }
        reopened.close()
    }

    @Test fun revokePersistsTargetsAliasesAndStoreBoundReceiptWithoutTouchingOtherDomains() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority()
        val store = store(root, keys, provider)
        val first = store.createCandidate(grant())
        val second = store.createCandidate(grant(id = "g2"))
        val other = store.createCandidate(grant(id = "other", domain = "tenant.other"))
        val ownership = OwnershipAuthorization(store.storeId, "owner")
        val receipt = store.revokeGrant("g1", ownership)
        assertEquals(store.storeId, receipt.storeId)
        assertEquals(receipt, store.revokeGrant("g2", ownership))
        listOf(first, second).forEach { ref -> assertCode(IdentityErrorCode.CANDIDATE_REVOKED) { store.recordedCandidateGrant(ref) } }
        assertEquals("other", store.recordedCandidateGrant(other).grantId)
        store.close()
        val reopened = store(root, keys, provider)
        assertEquals(receipt, reopened.revokeGrant("g2", ownership))
        assertCode(IdentityErrorCode.CANDIDATE_REVOKED) { reopened.recordedCandidateGrant(first) }
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { reopened.createCandidate(grant(id = "bypass")) }
        provider.epoch = 1
        val fresh = reopened.createCandidate(grant(id = "fresh", epoch = 1))
        assertEquals(receipt, reopened.revokeGrant("g1", ownership))
        assertEquals("fresh", reopened.recordedCandidateGrant(fresh).grantId)
        val nextReceipt = reopened.revokeGrant("fresh", ownership)
        assertNotEquals(receipt, nextReceipt)
        reopened.close()
        val twiceReopened = store(root, keys, provider)
        assertEquals(receipt, twiceReopened.revokeGrant("g1", ownership))
        assertEquals(nextReceipt, twiceReopened.revokeGrant("fresh", ownership))
        twiceReopened.close()
    }

    @Test fun revokeNeedsIndependentOwnershipEvenForRepeatedRequests() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority()
        val store = store(root, keys, provider)
        val ref = store.createCandidate(grant())
        val ownership = OwnershipAuthorization(store.storeId, "owner")
        assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) { store.revokeGrant("g1", OwnershipAuthorization("wrong", "owner")) }
        provider.denyOwnership = true
        assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) { store.revokeGrant("g1", ownership) }
        provider.denyOwnership = false
        assertEquals("g1", store.recordedCandidateGrant(ref).grantId)
        store.revokeGrant("g1", ownership)
        provider.denyOwnership = true
        assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) { store.revokeGrant("g1", ownership) }
        store.close()
    }

    @Test fun failedRevokeCommitRetiresWriterWithoutPublishingTombstone() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority()
        val store = store(root, keys, provider)
        val ref = store.createCandidate(grant())
        val file = root.resolve("identity-journal-store.bin"); val before = Files.readAllBytes(file)
        keys.reject = true
        assertCode(IdentityErrorCode.RE_ENROLLMENT_REQUIRED) { store.revokeGrant("g1", OwnershipAuthorization(store.storeId, "owner")) }
        keys.reject = false
        assertContentEquals(before, Files.readAllBytes(file))
        assertCode(IdentityErrorCode.CLOSED) { store.recordedCandidateGrant(ref) }
        val reopened = store(root, keys, provider)
        store.close()
        assertCode(IdentityErrorCode.BUSY) { store(root, keys, provider) }
        assertEquals("g1", reopened.recordedCandidateGrant(ref).grantId)
        reopened.close()
    }

    @Test fun unknownGrantAndClosedStoreRejectRevocation() {
        val store = store(tempRoot(), StoreKeys(), StoreAuthority())
        val ownership = OwnershipAuthorization(store.storeId, "owner")
        assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) { store.revokeGrant("unknown", ownership) }
        store.close()
        assertCode(IdentityErrorCode.CLOSED) { store.revokeGrant("unknown", ownership) }
    }

    @Test fun receiptWireIsStrictAndStoreBound() {
        val receipt = RevocationReceipt("receipt", "store")
        assertEquals(receipt, RevocationReceipt.decodeJSON(receipt.canonicalJSON()))
        assertCode(IdentityErrorCode.INVALID_INPUT) { RevocationReceipt.decodeJSON("""{"id":"r","storeId":"s","status":"purged"}""") }
        assertCode(IdentityErrorCode.INVALID_INPUT) { RevocationReceipt.decodeJSON("""{"id":"","storeId":"s"}""") }
    }

    @Test fun versionOneStoreMigratesWithReceiptOnNextCommit() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority()
        val auth = IdentityAuthorizationState("login").admitting(grant(), IdentityScope.ENROLLMENT, provider, 100)
        IdentityJournal(root, "store", keys).use { journal -> journal.commit(IdentityContractJSON.encode(mapOf(
            "version" to 1, "storeId" to "legacy", "authorization" to auth.canonicalJSON(), "candidates" to mapOf("candidate" to "g1")
        )).toByteArray()) }
        val store = store(root, keys, provider)
        assertEquals("g1", store.recordedCandidateGrant(CandidateRef("candidate", "legacy")).grantId)
        val ownership = OwnershipAuthorization("legacy", "owner")
        val receipt = store.revokeGrant("g1", ownership)
        store.close()
        val reopened = store(root, keys, provider)
        assertEquals(receipt, reopened.revokeGrant("g1", ownership))
        reopened.close()
    }

    @Test fun providerReentryCannotRevokeAndThenBeOverwrittenByStagedAdmission() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority()
        val store = store(root, keys, provider)
        store.createCandidate(grant())
        var nestedFailure: Throwable? = null
        provider.onAuthorize = {
            provider.onAuthorize = null
            nestedFailure = runCatching { store.revokeGrant("g1", OwnershipAuthorization(store.storeId, "owner")) }.exceptionOrNull()
        }
        try {
            store.createCandidate(grant(id = "g2"))
            assertEquals(IdentityErrorCode.BUSY, (nestedFailure as? IdentityError)?.code)
        } finally { store.close() }
    }

    @Test fun restoreRejectsIncompleteOrInconsistentRevocationTargetSets() {
        val provider = StoreAuthority()
        var auth = IdentityAuthorizationState("login").admitting(grant(), IdentityScope.ENROLLMENT, provider, 100)
        auth = auth.admitting(grant(id = "g2"), IdentityScope.ENROLLMENT, provider, 100).revoking("g1")
        val valid = mapOf("domainId" to "tenant.subject", "epoch" to 1, "grantIds" to listOf("g1", "g2"), "candidateIds" to listOf("c1", "c2"))
        val invalidRecords = listOf(
            mapOf("r" to valid + ("grantIds" to listOf("g1")) + ("candidateIds" to listOf("c1"))),
            mapOf("r" to valid + ("candidateIds" to listOf("c1"))),
            mapOf("r" to valid + ("candidateIds" to listOf("c1", "c1"))),
            mapOf("r" to valid + ("grantIds" to emptyList<String>())),
            mapOf("r" to valid + ("domainId" to "wrong")),
            mapOf("r" to valid + ("epoch" to 0)),
            mapOf("r" to valid + ("epoch" to 2)),
            mapOf("r" to valid + ("extra" to true)),
            mapOf("r" to valid, "duplicate" to valid)
        )
        for (records in invalidRecords) {
            val root = tempRoot(); val keys = StoreKeys()
            IdentityJournal(root, "store", keys).use { journal -> journal.commit(IdentityContractJSON.encode(mapOf(
                "version" to 2, "storeId" to "store", "authorization" to auth.canonicalJSON(),
                "candidates" to mapOf("c1" to "g1", "c2" to "g2"), "revocations" to records
            )).toByteArray()) }
            assertCode(IdentityErrorCode.INVALID_INPUT) { store(root, keys, provider).use { } }
        }
    }

    @Test fun concurrentRevocationsReturnOnePersistentReceipt() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority()
        val store = store(root, keys, provider)
        store.createCandidate(grant())
        val ownership = OwnershipAuthorization(store.storeId, "owner")
        val executor = Executors.newFixedThreadPool(16)
        val receipts = try {
            executor.invokeAll((0 until 16).map { Callable { store.revokeGrant("g1", ownership) } }).map { it.get() }
        } finally { executor.shutdownNow() }
        assertEquals(16, receipts.size)
        assertEquals(1, receipts.toSet().size)
        store.close()
        val reopened = store(root, keys, provider)
        assertEquals(receipts.first(), reopened.revokeGrant("g1", ownership))
        reopened.close()
    }

    @Test fun revocationStopsMatchingOperationAndSuppressesFinish() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority()
        val store = store(root, keys, provider); val authorization = grant(); val candidate = store.createCandidate(authorization)
        val ticket = store.beginOperation(candidate, authorization, IdentityScope.ENROLLMENT)
        var stopped = false
        val registration = ticket.stopSignal.registerStopHandler { stopped = true }
        store.revokeGrant("g1", OwnershipAuthorization(store.storeId, "owner"))
        assertTrue(stopped)
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { ticket.stopSignal.check() }
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { store.finishOperation(ticket) }
        registration.close(); store.close()
    }

    @Test fun operationFromOtherDomainSurvivesRevocation() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority()
        val store = store(root, keys, provider); val same = grant(); val other = grant("other", domain = "tenant.other")
        val sameRef = store.createCandidate(same); val otherRef = store.createCandidate(other)
        val ticket = store.beginOperation(otherRef, other, IdentityScope.ENROLLMENT)
        store.revokeGrant("g1", OwnershipAuthorization(store.storeId, "owner"))
        ticket.stopSignal.check(); store.finishOperation(ticket)
        assertCode(IdentityErrorCode.CANDIDATE_REVOKED) { store.recordedCandidateGrant(sameRef) }
        store.close()
    }

    @Test fun operationTicketRejectsMismatchedCandidateOrGrant() {
        val store = store(tempRoot(), StoreKeys(), StoreAuthority()); val first = grant(); val second = grant("g2", domain = "tenant.other")
        val ref = store.createCandidate(first)
        assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) { store.beginOperation(ref, second, IdentityScope.ENROLLMENT) }
        store.close()
    }

    @Test fun enrollmentPersistsTemplateAndRestoresCandidateMapping() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val authorization = grant(); val candidate = store.createCandidate(authorization)
        val result = store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, authorization,
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system())
        assertEquals(EnrollmentOutcome.ENROLLED, result.outcome); assertTrue(result.durationNanos >= 0)
        val ref = requireNotNull(result.templateRef); assertEquals(candidate, store.recordedTemplateCandidate(ref)); store.close()
        val reopened = store(root, keys, provider); assertEquals(candidate, reopened.recordedTemplateCandidate(ref)); reopened.close()
    }

    @Test fun enrollmentOptionsValidateExplicitQualityFloor() {
        assertCode(IdentityErrorCode.INVALID_INPUT) { EnrollmentOptions(Double.NaN, "host.quality", "q1") }
        assertCode(IdentityErrorCode.INVALID_INPUT) { EnrollmentOptions(1.1, "host.quality", "q1") }
    }

    @Test fun enrollmentRejectsSpaceDriftWrongEmbeddingAndMultipleFacesWithoutFallback() {
        val root = tempRoot(); val store = store(root, StoreKeys(), StoreAuthority()); val authorization = grant()
        val candidate = store.createCandidate(authorization); val probe = IdentityProbe("p", listOf(1f, 0f))
        val options = EnrollmentOptions(0.8, "host.quality", "q1")
        for ((mode, expected) in listOf("multiple" to IdentityErrorCode.MULTIPLE_FACES, "drift" to IdentityErrorCode.EMBEDDING_SPACE_MISMATCH, "wrong" to IdentityErrorCode.EMBEDDING_SPACE_MISMATCH)) {
            val backend = EnrollmentVariantTestBackend(mode)
            val issue = assertFailsWith<IdentityError> { store.enroll(probe, candidate, authorization, options, backend) }
            assertEquals(expected, issue.code)
            assertEquals(FixtureIdentityBackend.system().modelBinding, issue.modelBinding)
        }
        assertCode(IdentityErrorCode.BACKEND_UNAVAILABLE) { store.enroll(probe, candidate, authorization, options, FixtureIdentityBackend.optionalUnavailable()) }
        assertEquals(0, root.toFile().listFiles()!!.count { it.name.startsWith("identity-template-") }); store.close()
    }

    @Test fun higherEpochCannotResurrectOldTemplateAcrossRestart() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val authorization = grant(); val candidate = store.createCandidate(authorization)
        val ref = requireNotNull(store.enroll(IdentityProbe("p", listOf(1f, 0f)), candidate, authorization, EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        provider.epoch = 1; val fresh = store.createCandidate(grant("fresh", epoch = 1))
        assertCode(IdentityErrorCode.TEMPLATE_REVOKED) { store.recordedTemplateCandidate(ref) }; store.close()
        val reopened = store(root, keys, provider)
        assertCode(IdentityErrorCode.TEMPLATE_REVOKED) { reopened.recordedTemplateCandidate(ref) }
        assertEquals("fresh", reopened.recordedCandidateGrant(fresh).grantId); reopened.close()
    }

    @Test fun enrollmentCanUseAnotherGrantInSameDomainAndEpochAcrossRestart() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val candidate = store.createCandidate(grant())
        val result = store.enroll(IdentityProbe("p", listOf(1f, 0f)), candidate, grant("g2"),
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system())
        store.close()
        val reopened = store(root, keys, provider)
        assertEquals(candidate, reopened.recordedTemplateCandidate(requireNotNull(result.templateRef))); reopened.close()
    }

    @Test fun revokeWhileEnrollmentBackendBlockedSuppressesEveryBackendOutcome() {
        for (mode in 0..2) {
            val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
            val authorization = grant(); val candidate = store.createCandidate(authorization)
            val backend = EnrollmentTestBackend(quality = IdentityQuality(1, if (mode == 1) 0.1 else 1.0), failure = mode == 2, block = true)
            val executor = Executors.newSingleThreadExecutor()
            try {
                val job = executor.submit(Callable { runCatching { store.enroll(IdentityProbe("p", listOf(1f, 0f)), candidate, authorization,
                    EnrollmentOptions(0.8, "host.quality", "q1"), backend) }.exceptionOrNull() })
                assertTrue(backend.started.await(2, TimeUnit.SECONDS))
                store.revokeGrant("g1", OwnershipAuthorization(store.storeId, "owner"))
                assertEquals(IdentityErrorCode.AUTHORIZATION_REVOKED, (job.get(2, TimeUnit.SECONDS) as? IdentityError)?.code)
                assertEquals(0, root.toFile().listFiles()!!.count { it.name.startsWith("identity-template-") })
            } finally { backend.release.countDown(); executor.shutdownNow(); store.close() }
        }
    }

    @Test fun lowQualityDoesNotEmbedOrWriteAndResolvedErrorsHaveContext() {
        val root = tempRoot(); val keys = StoreKeys(); val store = store(root, keys, StoreAuthority())
        val authorization = grant(); val candidate = store.createCandidate(authorization)
        val low = EnrollmentTestBackend(quality = IdentityQuality(1, 0.1))
        val options = EnrollmentOptions(0.8, "host.quality", "q1"); val probe = IdentityProbe("p", listOf(1f, 0f))
        val result = store.enroll(probe, candidate, authorization, options, low)
        assertEquals(EnrollmentOutcome.INSUFFICIENT_QUALITY, result.outcome); assertNull(result.templateRef)
        assertEquals(0, low.embeddingCalls); assertEquals(0, root.toFile().listFiles()!!.count { it.name.startsWith("identity-template-") })
        val backend = EnrollmentTestBackend(failure = true)
        val error = assertFailsWith<IdentityError> { store.enroll(probe, candidate, authorization, options, backend) }
        assertEquals(IdentityErrorCode.BACKEND_UNAVAILABLE, error.code); assertEquals(backend.backendId, error.backendId)
        assertEquals(backend.modelBinding, error.modelBinding); assertNotNull(error.sessionId); store.close()
    }

    @Test fun cancellationCloseAndFatalBackendErrorAlwaysReleaseEnrollmentTicket() {
        for (closing in listOf(false, true)) {
            val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
            val authorization = grant(); val candidate = store.createCandidate(authorization)
            val backend = EnrollmentTestBackend(block = true); val signal = IdentityStopSignal()
            val executor = Executors.newSingleThreadExecutor()
            try {
                val job = executor.submit(Callable { runCatching { store.enroll(IdentityProbe("p", listOf(1f, 0f)), candidate, authorization,
                    EnrollmentOptions(0.8, "host.quality", "q1"), backend, signal) }.exceptionOrNull() })
                assertTrue(backend.started.await(2, TimeUnit.SECONDS))
                if (closing) store.close() else signal.requestStop(IdentityStopReason.CANCELLED)
                assertEquals(if (closing) IdentityErrorCode.CLOSED else IdentityErrorCode.CANCELLED, (job.get(2, TimeUnit.SECONDS) as? IdentityError)?.code)
                store.close(); store(root, keys, provider).close()
            } finally { backend.release.countDown(); executor.shutdownNow(); store.close() }
        }
        val store = store(tempRoot(), StoreKeys(), StoreAuthority()); val authorization = grant(); val candidate = store.createCandidate(authorization)
        val fatal = object : IdentityBackend by FixtureIdentityBackend.system() {
            override fun evaluateQuality(probe: IdentityProbe): IdentityQuality = throw AssertionError("fatal fixture")
            override fun evaluateQuality(probe: IdentityProbe, stopSignal: IdentityStopSignal): IdentityQuality = throw AssertionError("fatal fixture")
        }
        assertFailsWith<AssertionError> { store.enroll(IdentityProbe("p", listOf(1f, 0f)), candidate, authorization, EnrollmentOptions(0.8, "host.quality", "q1"), fatal) }
        store.close()
    }

    @Test fun recoveryCleansOnlyDurableStageAfterKeyOrPublicationFailure() {
        for (publication in listOf(false, true)) {
            val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
            val authorization = grant(); val candidate = store.createCandidate(authorization)
            keys.rejectTemplateCreation = !publication; keys.failJournalAfterTemplateCreation = publication
            assertCode(IdentityErrorCode.RE_ENROLLMENT_REQUIRED) { store.enroll(IdentityProbe("p", listOf(1f, 0f)), candidate, authorization,
                EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()) }
            keys.rejectJournal = false; keys.rejectTemplateCreation = false; keys.failJournalAfterTemplateCreation = false; store.close()
            val metadata = IdentityJournal(root, "store", keys).use { IdentityContractJSON.parse(it.read()!!.decodeToString()) }
            val pending = metadata["stages"] as List<*>; assertEquals(1, pending.size)
            assertTrue((metadata["templates"] as Map<*, *>).isEmpty())
            val unowned = root.resolve("identity-template-${java.util.UUID.randomUUID()}.bin"); Files.write(unowned, byteArrayOf(9))
            val reopened = store(root, keys, provider)
            assertFalse(Files.exists(root.resolve("identity-template-${pending.single()}.bin")))
            assertCode(IdentityErrorCode.RE_ENROLLMENT_REQUIRED) { keys.key("template.${store.storeId}.${pending.single()}", false) }
            assertContentEquals(byteArrayOf(9), Files.readAllBytes(unowned))
            assertNotNull(reopened.enroll(IdentityProbe("p", listOf(1f, 0f)), candidate, authorization,
                EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef); reopened.close()
        }
    }

    @Test fun restoreRejectsDuplicateStagesAndMalformedTemplateMembership() {
        val provider = StoreAuthority(); val auth = IdentityAuthorizationState("login").admitting(grant(), IdentityScope.ENROLLMENT, provider, 100)
        val id = java.util.UUID.randomUUID().toString()
        val ref = TemplateRef(id, FixtureIdentityBackend.system().embeddingSpace, 0, "r1")
        val valid = mapOf("ref" to ref.canonicalJSON(), "candidateId" to "c1", "grantId" to "g1")
        val variants = listOf(
            mapOf<String, Any?>() to listOf(id, id), mapOf(id to valid) to listOf(id),
            mapOf("wrong" to valid) to emptyList(), mapOf(id to (valid + ("grantId" to "unknown"))) to emptyList(),
            mapOf(id to (valid + ("candidateId" to "unknown"))) to emptyList(), mapOf(id to (valid + ("extra" to "x"))) to emptyList()
        )
        for ((templates, stages) in variants) {
            val root = tempRoot(); val keys = StoreKeys()
            IdentityJournal(root, "store", keys).use { it.commit(IdentityContractJSON.encode(mapOf("version" to 3, "storeId" to "store",
                "authorization" to auth.canonicalJSON(), "candidates" to mapOf("c1" to "g1"), "revocations" to emptyMap<String, Any>(),
                "templates" to templates, "stages" to stages)).encodeToByteArray()) }
            assertCode(IdentityErrorCode.INVALID_INPUT) { store(root, keys, provider).use { } }
        }
    }

    @Test fun blockedEnrollmentRevocationSuppressesResultAndLeavesGateResponsive() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val authorization = grant(); val candidate = store.createCandidate(authorization); val backend = BlockingEnrollmentBackend()
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit<EnrollmentResult> {
            store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, authorization,
                EnrollmentOptions(0.8, "host.quality", "q1"), backend)
        }
        assertTrue(backend.started.await(2, TimeUnit.SECONDS))
        store.revokeGrant("g1", OwnershipAuthorization(store.storeId, "owner"))
        assertCode(IdentityErrorCode.CANDIDATE_REVOKED) { store.recordedCandidateGrant(candidate) }
        backend.release.countDown()
        val issue = assertFailsWith<java.util.concurrent.ExecutionException> { future.get(2, TimeUnit.SECONDS) }
        assertEquals(IdentityErrorCode.AUTHORIZATION_REVOKED, (issue.cause as? IdentityError)?.code)
        executor.shutdownNow(); store.close()
    }

    @Test fun closeStopsAndDrainsOperationBeforeReleasingWriter() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority()
        val store = store(root, keys, provider); val authorization = grant(); val candidate = store.createCandidate(authorization)
        val ticket = store.beginOperation(candidate, authorization, IdentityScope.ENROLLMENT)
        val stopped = CountDownLatch(1)
        val registration = ticket.stopSignal.registerStopHandler { stopped.countDown() }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val closing = executor.submit { store.close() }
            assertTrue(stopped.await(2, TimeUnit.SECONDS))
            val secondClosing = executor.submit { store.close() }
            assertCode(IdentityErrorCode.BUSY) { store(root, keys, provider) }
            assertCode(IdentityErrorCode.CLOSED) { store.finishOperation(ticket) }
            closing.get(2, TimeUnit.SECONDS)
            secondClosing.get(2, TimeUnit.SECONDS)
            registration.close()
            val reopened = store(root, keys, provider)
            store.close()
            assertCode(IdentityErrorCode.BUSY) { store(root, keys, provider) }
            reopened.close()
        } finally { executor.shutdownNow() }
    }

    @Test fun newAuthoritativeEpochStopsOldOperationAndSuppressesBackendError() {
        val provider = StoreAuthority(); val store = store(tempRoot(), StoreKeys(), provider)
        val old = grant(); val ref = store.createCandidate(old); val ticket = store.beginOperation(ref, old, IdentityScope.ENROLLMENT)
        provider.epoch = 1
        store.createCandidate(grant("new", epoch = 1))
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { ticket.stopSignal.check() }
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { store.finishOperation(ticket, IdentityError(IdentityErrorCode.BACKEND_UNAVAILABLE)) }
        store.close()
    }

    @Test fun finishedOperationIsNotRetroactivelyStopped() {
        val store = store(tempRoot(), StoreKeys(), StoreAuthority()); val authorization = grant()
        val ref = store.createCandidate(authorization); val ticket = store.beginOperation(ref, authorization, IdentityScope.ENROLLMENT)
        store.finishOperation(ticket)
        store.revokeGrant("g1", OwnershipAuthorization(store.storeId, "owner"))
        ticket.stopSignal.check(); store.close()
    }

    @Test fun stopCallbackCanQueryStoreFromAnotherThread() {
        val store = store(tempRoot(), StoreKeys(), StoreAuthority()); val authorization = grant()
        val ref = store.createCandidate(authorization); val other = store.createCandidate(grant("other", domain = "tenant.other"))
        val ticket = store.beginOperation(ref, authorization, IdentityScope.ENROLLMENT)
        val callbackWasOutsideGate = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        val registration = ticket.stopSignal.registerStopHandler {
            val result = executor.submit(Callable { store.recordedCandidateGrant(other) }).get(2, TimeUnit.SECONDS)
            callbackWasOutsideGate.set(result.grantId == "other")
        }
        try {
            store.revokeGrant("g1", OwnershipAuthorization(store.storeId, "owner"))
            assertTrue(callbackWasOutsideGate.get())
            assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { store.finishOperation(ticket, IdentityError(IdentityErrorCode.BACKEND_UNAVAILABLE)) }
            registration.close(); store.close()
        } finally { executor.shutdownNow() }
    }

    @Test fun operationRechecksExpiryAndPersistsNewGrantDeclaration() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority()
        var now = 100L
        val clock = object : IdentityWallClock { override val nowMillis: Long get() = now }
        val store = IdentityStore(root.toFile(), "login", provider, keys, clock, StoreMono())
        val ref = store.createCandidate(grant())
        val ticket = store.beginOperation(ref, grant("g2"), IdentityScope.ENROLLMENT)
        now = 200
        assertCode(IdentityErrorCode.AUTHORIZATION_EXPIRED) { store.finishOperation(ticket) }
        store.close()
        val reopened = store(root, keys, provider)
        val changed = AuthorizationGrant("g2", "tenant.subject", setOf(IdentityScope.ENROLLMENT), "login", 1, 200, 0, "audit")
        assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) { reopened.beginOperation(ref, changed, IdentityScope.ENROLLMENT) }
        reopened.close()
    }

    @Test fun foreignStoreCannotConsumeOperationTicket() {
        val first = store(tempRoot(), StoreKeys(), StoreAuthority()); val second = store(tempRoot(), StoreKeys(), StoreAuthority())
        val authorization = grant(); val ref = first.createCandidate(authorization)
        val ticket = first.beginOperation(ref, authorization, IdentityScope.ENROLLMENT)
        assertCode(IdentityErrorCode.INVALID_INPUT) { second.finishOperation(ticket) }
        first.finishOperation(ticket)
        first.close(); second.close()
    }

    @Test fun interruptedCloseStillDrainsAndRestoresInterruptStatus() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority()
        val store = store(root, keys, provider); val authorization = grant()
        val ref = store.createCandidate(authorization); val ticket = store.beginOperation(ref, authorization, IdentityScope.ENROLLMENT)
        val stopped = CountDownLatch(1); val finished = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(); val interruptedAfterClose = AtomicBoolean(false)
        val registration = ticket.stopSignal.registerStopHandler { stopped.countDown() }
        val closer = Thread {
            try { store.close(); interruptedAfterClose.set(Thread.currentThread().isInterrupted) }
            catch (error: Throwable) { failure.set(error) }
            finally { finished.countDown() }
        }
        closer.start()
        try {
            assertTrue(stopped.await(2, TimeUnit.SECONDS))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (closer.state != Thread.State.WAITING && finished.count > 0 && System.nanoTime() < deadline) Thread.yield()
            assertEquals(Thread.State.WAITING, closer.state)
            closer.interrupt()
            assertFalse(finished.await(100, TimeUnit.MILLISECONDS), "close returned before its ticket drained")
            assertCode(IdentityErrorCode.BUSY) { store(root, keys, provider) }
            assertCode(IdentityErrorCode.CLOSED) { store.finishOperation(ticket) }
            assertTrue(finished.await(2, TimeUnit.SECONDS))
            assertNull(failure.get())
            assertTrue(interruptedAfterClose.get())
            store(root, keys, provider).close()
        } finally {
            runCatching { store.finishOperation(ticket) }
            store.close(); registration.close(); closer.join(2000)
        }
    }

    private fun store(root: java.nio.file.Path, keys: StoreKeys, provider: StoreAuthority) =
        IdentityStore(root.toFile(), "login", provider, keys, StoreClock(100), StoreMono())
    private fun grant(id: String = "g1", purpose: String = "login", epoch: Long = 0, domain: String = "tenant.subject") =
        AuthorizationGrant(id, domain, setOf(IdentityScope.ENROLLMENT), purpose, 0, 200, epoch, "audit")
    private fun tempRoot() = Files.createTempDirectory("identity-store").also { roots.add(it) }
    private fun assertCode(code: IdentityErrorCode, block: () -> Any?) =
        assertEquals(code, assertFailsWith<IdentityError> { block() }.code)
}

private class StoreKeys : IdentityKeyProvider {
    private val values = mutableMapOf<String, SecretKey>()
    var reject = false
    var rejectTemplateCreation = false
    var failJournalAfterTemplateCreation = false
    var rejectJournal = false
    override fun key(id: String, createIfMissing: Boolean): SecretKey = synchronized(values) {
        if (reject) throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
        if (rejectJournal && id.startsWith("journal.")) throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
        if (rejectTemplateCreation && createIfMissing && id.startsWith("template.")) throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
        values[id] ?: if (createIfMissing) KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also {
            values[id] = it
            if (failJournalAfterTemplateCreation && id.startsWith("template.")) rejectJournal = true
        }
        else throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
    }
    override fun deleteKey(id: String) { synchronized(values) { values.remove(id) } }
}

private class EnrollmentTestBackend(val quality: IdentityQuality = IdentityQuality(1, 1.0), val failure: Boolean = false, val block: Boolean = false) : IdentityBackend {
    private val base = FixtureIdentityBackend.system()
    override val backendId get() = base.backendId
    override val backendVersion get() = base.backendVersion
    override val modelBinding get() = base.modelBinding
    override val embeddingSpace get() = base.embeddingSpace
    override fun availability() = base.availability()
    val started = CountDownLatch(1); val release = CountDownLatch(1)
    var embeddingCalls = 0
    override fun evaluateQuality(probe: IdentityProbe) = quality
    override fun evaluateQuality(probe: IdentityProbe, stopSignal: IdentityStopSignal): IdentityQuality {
        val registration = stopSignal.registerStopHandler { release.countDown() }
        try {
            if (block) { started.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw IdentityError(IdentityErrorCode.SESSION_TIMEOUT) }
            if (failure) throw IdentityError(IdentityErrorCode.BACKEND_UNAVAILABLE)
            return quality
        } finally { registration.close() }
    }
    override fun embed(probe: IdentityProbe): IdentityEmbedding { embeddingCalls++; return base.embed(probe) }
    override fun close() = base.close()
}

private class EnrollmentVariantTestBackend(val mode: String) : IdentityBackend {
    private val base = FixtureIdentityBackend.system()
    private val other = FixtureIdentityBackend.system("other-model")
    private var drifted = false
    override val backendId get() = base.backendId
    override val backendVersion get() = base.backendVersion
    override val modelBinding get() = if (drifted) other.modelBinding else base.modelBinding
    override val embeddingSpace get() = if (drifted) other.embeddingSpace else base.embeddingSpace
    override fun availability() = base.availability()
    override fun evaluateQuality(probe: IdentityProbe): IdentityQuality {
        if (mode == "drift") drifted = true
        return IdentityQuality(if (mode == "multiple") 2 else 1, 1.0)
    }
    override fun embed(probe: IdentityProbe) = (if (mode == "wrong") other else base).embed(probe)
    override fun close() {}
}
private class StoreAuthority(var epoch: Long = 0) : IdentityAuthorizationProvider {
    var denyOwnership = false
    var onAuthorize: (() -> Unit)? = null
    override fun authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Long, nowMillis: Long): Long { onAuthorize?.invoke(); return epoch }
    override fun validateOwnership(authorization: OwnershipAuthorization, nowMillis: Long) {
        if (denyOwnership) throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED)
    }
}
private class BlockingEnrollmentBackend : IdentityBackend {
    override val backendId = "blocked.fixture"; override val backendVersion = "1"
    override val modelBinding = ModelBinding(ModelBindingKind.MODEL, backendId, "1")
    override val embeddingSpace = EmbeddingSpaceId(setOf(IdentityCapability.VERIFICATION), backendId, backendVersion, modelBinding, "1", 2, FeatureDType.FLOAT32, Endianness.LITTLE, "l2", "p1", "cosine", ScoreDirection.HIGHER_IS_BETTER)
    val started = CountDownLatch(1); val release = CountDownLatch(1)
    override fun availability() = CapabilityAvailability(CapabilityStatus.AVAILABLE, backendId = backendId)
    override fun evaluateQuality(probe: IdentityProbe) = IdentityQuality(1, 1.0)
    override fun embed(probe: IdentityProbe): IdentityEmbedding { started.countDown(); release.await(2, TimeUnit.SECONDS); return IdentityEmbedding(embeddingSpace, listOf(1f, 2f)) }
    override fun close() = Unit
}
private data class StoreClock(override val nowMillis: Long) : IdentityWallClock
private data class StoreMono(override val nowNanos: Long = 1) : IdentityMonotonicClock
private class AdvancingClock : IdentityWallClock {
    private var current = 100L
    override val nowMillis: Long get() = current.also { current = 200L }
}
