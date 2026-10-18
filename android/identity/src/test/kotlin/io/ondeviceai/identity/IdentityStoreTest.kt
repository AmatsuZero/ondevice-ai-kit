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

    @Test fun revokeSharesTemplatePurgeFactsAndWaitsForEveryLease() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val enrollment = grant(); val candidate = store.createCandidate(enrollment)
        val template = requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, enrollment,
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val verify = grantWithScope("verify", IdentityScope.VERIFICATION)
        val first = store.acquireTemplateLease(template, verify, IdentityScope.VERIFICATION, template.embeddingSpaceId)
        val last = store.acquireTemplateLease(template, verify, IdentityScope.VERIFICATION, template.embeddingSpaceId)
        val receipt = store.revokeGrant("g1", OwnershipAuthorization(store.storeId, "owner"))
        assertEquals(DeletionStatus.PURGE_PENDING, store.awaitDeletion(receipt))
        first.close(); assertEquals(DeletionStatus.PURGE_PENDING, store.awaitDeletion(receipt))
        last.close(); assertEquals(DeletionStatus.PURGED, store.awaitDeletion(receipt))
        store.close()
    }

    @Test fun v5RevocationMigratesTargetsBeforeRecoveryPurge() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val candidate = store.createCandidate(grant())
        val template = requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, grant(), EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        keys.rejectTemplateDeletion = true
        val receipt = store.revokeGrant("g1", OwnershipAuthorization(store.storeId, "owner")); store.close()
        IdentityJournal(root, "store", keys).use { journal ->
            val fields = IdentityContractJSON.parse(requireNotNull(journal.read()).decodeToString()).toMutableMap()
            @Suppress("UNCHECKED_CAST") val records = (fields["revocations"] as Map<String, Any?>).mapValues { (_, value) ->
                @Suppress("UNCHECKED_CAST") (value as Map<String, Any?>).filterKeys { it != "templateIds" }
            }
            fields["version"] = 5L; fields["revocations"] = records; fields["deletions"] = emptyMap<String, Any?>()
            journal.commit(IdentityContractJSON.encode(fields).encodeToByteArray())
        }
        keys.rejectTemplateDeletion = false
        keys.onTemplateDelete = { keys.rejectJournal = true }
        assertCode(IdentityErrorCode.RE_ENROLLMENT_REQUIRED) { store(root, keys, provider).use { } }
        keys.onTemplateDelete = null; keys.rejectJournal = false
        IdentityJournal(root, "store", keys).use { journal ->
            val fields = IdentityContractJSON.parse(requireNotNull(journal.read()).decodeToString())
            assertEquals(6L, fields.long("version"))
            @Suppress("UNCHECKED_CAST") val record = (fields["revocations"] as Map<String, Any?>).values.single() as Map<String, Any?>
            assertEquals(listOf(template.id), record["templateIds"])
        }
        val reopened = store(root, keys, provider); assertEquals(DeletionStatus.PURGED, reopened.awaitDeletion(receipt)); reopened.close()
    }

    @Test fun malformedV6RevocationTargetsFailBeforeRecoveryIO() {
        for (mode in listOf("wrong", "duplicate", "missing")) {
            val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
            val candidate = store.createCandidate(grant())
            val template = requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, grant(), EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
            keys.rejectTemplateDeletion = true; store.revokeGrant("g1", OwnershipAuthorization(store.storeId, "owner")); store.close(); keys.rejectTemplateDeletion = false
            IdentityJournal(root, "store", keys).use { journal ->
                val fields = IdentityContractJSON.parse(requireNotNull(journal.read()).decodeToString()).toMutableMap()
                @Suppress("UNCHECKED_CAST") val revocations = (fields["revocations"] as Map<String, Any?>).toMutableMap()
                val key = revocations.keys.single(); @Suppress("UNCHECKED_CAST") val record = (revocations[key] as Map<String, Any?>).toMutableMap()
                when (mode) {
                    "wrong" -> record["templateIds"] = listOf(java.util.UUID.randomUUID().toString())
                    "duplicate" -> record["templateIds"] = listOf(template.id, template.id)
                    "missing" -> fields["deletions"] = emptyMap<String, Any?>()
                }
                revocations[key] = record; fields["revocations"] = revocations
                journal.commit(IdentityContractJSON.encode(fields).encodeToByteArray())
            }
            val before = Files.exists(root.resolve("identity-template-${template.id}.bin"))
            assertCode(IdentityErrorCode.INVALID_INPUT) { store(root, keys, provider).use { } }
            assertEquals(before, Files.exists(root.resolve("identity-template-${template.id}.bin")))
        }
    }

    @Test fun legacyTrustedEpochInvalidationStagesEveryStaleTemplateWithoutReceipt() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val candidate = store.createCandidate(grant())
        val template = requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, grant(), EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        keys.rejectTemplateDeletion = true; provider.epoch = 1
        store.createCandidate(grant("new-epoch", epoch = 1)); store.close()
        IdentityJournal(root, "store", keys).use { journal ->
            val fields = IdentityContractJSON.parse(requireNotNull(journal.read()).decodeToString()).toMutableMap()
            fields["version"] = 5L; fields["deletions"] = emptyMap<String, Any?>()
            journal.commit(IdentityContractJSON.encode(fields).encodeToByteArray())
        }
        keys.rejectTemplateDeletion = false
        store(root, keys, provider).use { recovered ->
            assertFalse(Files.exists(root.resolve("identity-template-${template.id}.bin")))
            assertEquals(1, keys.templateDeletions)
            assertCode(IdentityErrorCode.TEMPLATE_REVOKED) { recovered.recordedTemplateCandidate(template) }
        }
        store(root, keys, provider).close()
    }

    @Test fun v6DuplicateTemplateReceiptIdsAreRejectedBeforePurge() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val candidate = store.createCandidate(grant())
        repeat(2) { store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, grant(), EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()) }
        keys.rejectTemplateDeletion = true; store.revokeGrant("g1", OwnershipAuthorization(store.storeId, "owner")); store.close()
        IdentityJournal(root, "store", keys).use { journal ->
            val fields = IdentityContractJSON.parse(requireNotNull(journal.read()).decodeToString()).toMutableMap()
            @Suppress("UNCHECKED_CAST") val facts = (fields["deletions"] as Map<String, Map<String, Any?>>).mapValues { (_, record) -> record + ("receiptId" to "duplicate-receipt") }
            fields["deletions"] = facts
            journal.commit(IdentityContractJSON.encode(fields).encodeToByteArray())
        }
        keys.rejectTemplateDeletion = false
        val count = keys.templateDeletions
        assertCode(IdentityErrorCode.INVALID_INPUT) { store(root, keys, provider).use { } }
        assertEquals(count, keys.templateDeletions)
    }

    @Test fun revokeLogicalCommitFailurePublishesNoStopAndNoPurge() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val candidate = store.createCandidate(grant()); val template = requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, grant(), EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val ticket = store.beginOperation(candidate, grant(), IdentityScope.ENROLLMENT); var stopped = false
        ticket.stopSignal.registerStopHandler { stopped = true }; keys.rejectJournal = true
        assertCode(IdentityErrorCode.RE_ENROLLMENT_REQUIRED) { store.revokeGrant("g1", OwnershipAuthorization(store.storeId, "owner")) }
        assertFalse(stopped); assertTrue(Files.exists(root.resolve("identity-template-${template.id}.bin"))); keys.rejectJournal = false
        assertCode(IdentityErrorCode.CLOSED) { store.finishOperation(ticket) }
        store.close()
    }

    @Test fun revokePurgeFailureStillStopsMatchingOperation() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val candidate = store.createCandidate(grant()); store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, grant(), EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system())
        val ticket = store.beginOperation(candidate, grant(), IdentityScope.ENROLLMENT); var stoppedAtPurge = false
        ticket.stopSignal.registerStopHandler { stoppedAtPurge = true }
        keys.onTemplateDelete = { assertTrue(stoppedAtPurge); keys.rejectJournal = true }
        assertCode(IdentityErrorCode.RE_ENROLLMENT_REQUIRED) { store.revokeGrant("g1", OwnershipAuthorization(store.storeId, "owner")) }
        assertTrue(stoppedAtPurge); assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { store.finishOperation(ticket) }
        keys.onTemplateDelete = null; keys.rejectJournal = false; store.close(); store(root, keys, provider).close()
    }

    @Test fun oldRevocationReceiptIsolatesNewEpochTemplatesAndOtherDomains() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val first = store.createCandidate(grant()); val template = enrollFixture(store, first, grant())
        val aliasGrant = grant("alias"); val alias = store.createCandidate(aliasGrant)
        enrollFixture(store, alias, aliasGrant)
        val otherGrant = grant("other", domain = "other-domain")
        val other = store.createCandidate(otherGrant); val otherTemplate = enrollFixture(store, other, otherGrant)
        val verify = grantWithScope("verify", IdentityScope.VERIFICATION)
        val lease1 = store.acquireTemplateLease(template, verify, IdentityScope.VERIFICATION, template.embeddingSpaceId)
        val lease2 = store.acquireTemplateLease(template, verify, IdentityScope.VERIFICATION, template.embeddingSpaceId)
        val owner = OwnershipAuthorization(store.storeId, "owner")
        val templateReceipt = store.deleteTemplate(template, owner)
        val receipt = store.revokeGrant("g1", owner)
        assertEquals(receipt, store.revokeGrant("alias", owner))
        provider.epoch = 1
        val newGrant = grant("new", epoch = 1); val fresh = store.createCandidate(newGrant)
        val freshTemplate = enrollFixture(store, fresh, newGrant)
        lease1.close(); assertEquals(DeletionStatus.PURGE_PENDING, store.awaitDeletion(receipt))
        lease2.close(); assertEquals(DeletionStatus.PURGED, store.awaitDeletion(receipt))
        assertEquals(DeletionStatus.PURGED, store.awaitDeletion(templateReceipt))
        assertEquals(fresh, store.recordedTemplateCandidate(freshTemplate))
        provider.epoch = 0
        val otherVerify = grantWithScope("other-verify", IdentityScope.VERIFICATION, domain = "other-domain")
        store.acquireTemplateLease(otherTemplate, otherVerify, IdentityScope.VERIFICATION, otherTemplate.embeddingSpaceId).close()
        store.close()
        store(root, keys, provider).use { reopened ->
            assertEquals(DeletionStatus.PURGED, reopened.awaitDeletion(receipt))
            assertEquals(receipt, reopened.revokeGrant("alias", owner))
            assertEquals(fresh, reopened.recordedTemplateCandidate(freshTemplate))
        }
    }

    @Test fun revocationKeyPurgeFailureRetriesFromFixedFactsAfterRestart() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val candidate = store.createCandidate(grant()); enrollFixture(store, candidate, grant())
        keys.rejectTemplateDeletion = true
        val receipt = store.revokeGrant("g1", OwnershipAuthorization(store.storeId, "owner"))
        assertEquals(DeletionStatus.PURGE_FAILED, store.awaitDeletion(receipt)); store.close()
        keys.rejectTemplateDeletion = false
        store(root, keys, provider).use { reopened ->
            assertEquals(DeletionStatus.PURGED, reopened.awaitDeletion(receipt))
            assertEquals(1, keys.templateDeletions)
        }
    }

    @Test fun repeatedRevocationCannotPurgeWhileFirstStopCallbackIsStillRunning() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val candidate = store.createCandidate(grant()); enrollFixture(store, candidate, grant())
        val ticket = store.beginOperation(candidate, grant(), IdentityScope.ENROLLMENT)
        val entered = CountDownLatch(1); val resume = CountDownLatch(1)
        val registration = ticket.stopSignal.registerStopHandler { entered.countDown(); assertTrue(resume.await(5, TimeUnit.SECONDS)) }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val first = executor.submit<RevocationReceipt> { store.revokeGrant("g1", OwnershipAuthorization(store.storeId, "owner")) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val repeated = store.revokeGrant("g1", OwnershipAuthorization(store.storeId, "owner"))
            assertEquals(0, keys.templateDeletions)
            resume.countDown()
            assertEquals(repeated, first.get(5, TimeUnit.SECONDS))
            assertEquals(DeletionStatus.PURGED, store.awaitDeletion(repeated))
        } finally {
            resume.countDown(); executor.shutdown(); executor.awaitTermination(5, TimeUnit.SECONDS)
            assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { store.finishOperation(ticket) }
            registration.close(); store.close()
        }
    }

    @Test fun pendingStopDeliveryBlocksConcurrentEpochCleanupAndLeaseRelease() {
        for (holdLease in listOf(false, true)) {
            val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
            val candidate = store.createCandidate(grant()); val template = enrollFixture(store, candidate, grant())
            val lease = if (holdLease) store.acquireTemplateLease(template, grantWithScope("verify", IdentityScope.VERIFICATION), IdentityScope.VERIFICATION, template.embeddingSpaceId) else null
            val ticket = store.beginOperation(candidate, grant(), IdentityScope.ENROLLMENT)
            val entered = CountDownLatch(1); val resume = CountDownLatch(1)
            val registration = ticket.stopSignal.registerStopHandler { entered.countDown(); assertTrue(resume.await(5, TimeUnit.SECONDS)) }
            val executor = Executors.newSingleThreadExecutor()
            try {
                provider.epoch = 1
                val newer = grant("epoch-one", epoch = 1)
                val first = executor.submit<CandidateRef> { store.createCandidate(newer) }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                store.createCandidate(newer); lease?.close()
                assertEquals(0, keys.templateDeletions, "purge preceded stop delivery; lease=$holdLease")
                resume.countDown(); first.get(5, TimeUnit.SECONDS)
                assertEquals(1, keys.templateDeletions)
            } finally {
                resume.countDown(); lease?.close(); executor.shutdown(); executor.awaitTermination(5, TimeUnit.SECONDS)
                assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { store.finishOperation(ticket) }
                registration.close(); store.close()
            }
        }
    }

    private fun enrollFixture(store: IdentityStore, candidate: CandidateRef, grant: AuthorizationGrant): TemplateRef =
        requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, grant,
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)

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

    @Test fun templateLeaseValidatesScopeSpaceAndFullAuthorization() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val enrollment = grant(); val candidate = store.createCandidate(enrollment)
        val template = requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, enrollment,
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val verification = grantWithScope("verify", IdentityScope.VERIFICATION)
        val lease = store.acquireTemplateLease(template, verification, IdentityScope.VERIFICATION, template.embeddingSpaceId)
        assertEquals(template.embeddingSpaceId, lease.embedding.embeddingSpace)
        lease.close()
        assertCode(IdentityErrorCode.UNSUPPORTED_OPTION) {
            store.acquireTemplateLease(template, enrollment, IdentityScope.ENROLLMENT, template.embeddingSpaceId)
        }
        assertCode(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH) {
            store.acquireTemplateLease(template, verification, IdentityScope.VERIFICATION, FixtureIdentityBackend.system("other").embeddingSpace)
        }
        assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) {
            store.acquireTemplateLease(template, grantWithScope("foreign", IdentityScope.VERIFICATION, domain = "tenant.other"), IdentityScope.VERIFICATION, template.embeddingSpaceId)
        }
        store.close()
    }

    @Test fun templateLeaseAcceptsDifferentGrantSameDomainEpochAndPersistsItForRevoke() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val enrollment = grant(); val candidate = store.createCandidate(enrollment)
        val template = requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, enrollment,
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val verification = grantWithScope("verify", IdentityScope.VERIFICATION)
        store.acquireTemplateLease(template, verification, IdentityScope.VERIFICATION, template.embeddingSpaceId).close()
        store.close()
        val reopened = store(root, keys, provider)
        assertEquals(reopened.storeId, reopened.revokeGrant("verify", OwnershipAuthorization(reopened.storeId, "owner")).storeId)
        reopened.close()
    }

    @Test fun templateLeaseEmbeddingIsClearedAfterCloseAndCloseWaitsForLeaseDrain() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val enrollment = grant(); val candidate = store.createCandidate(enrollment)
        val template = requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, enrollment,
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val verification = grantWithScope("verify", IdentityScope.VERIFICATION)
        val lease = store.acquireTemplateLease(template, verification, IdentityScope.VERIFICATION, template.embeddingSpaceId)
        val ticket = store.beginOperation(candidate, verification, IdentityScope.VERIFICATION)
        val enteredClose = CountDownLatch(1)
        val registration = ticket.stopSignal.registerStopHandler { enteredClose.countDown() }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val closing = executor.submit { store.close() }
            assertTrue(enteredClose.await(2, TimeUnit.SECONDS))
            assertCode(IdentityErrorCode.CLOSED) { store.finishOperation(ticket) }
            assertCode(IdentityErrorCode.BUSY) { store(root, keys, provider) }
            lease.close()
            closing.get(2, TimeUnit.SECONDS)
            assertCode(IdentityErrorCode.CLOSED) { lease.embedding }
            val reopened = store(root, keys, provider)
            lease.close()
            assertCode(IdentityErrorCode.BUSY) { store(root, keys, provider) }
            assertEquals(candidate, reopened.recordedTemplateCandidate(template))
            reopened.close()
            store.close()
        } finally { lease.close(); registration.close(); executor.shutdownNow() }
    }

    @Test fun templateLeaseProviderReentryCannotOverwriteRevocation() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val enrollment = grant(); val candidate = store.createCandidate(enrollment)
        val template = requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, enrollment,
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        var nestedFailure: Throwable? = null
        provider.onAuthorize = {
            provider.onAuthorize = null
            nestedFailure = runCatching { store.revokeGrant("g1", OwnershipAuthorization(store.storeId, "owner")) }.exceptionOrNull()
        }
        val lease = store.acquireTemplateLease(template, grantWithScope("verify", IdentityScope.VERIFICATION), IdentityScope.VERIFICATION, template.embeddingSpaceId)
        lease.close()
        assertEquals(IdentityErrorCode.BUSY, (nestedFailure as? IdentityError)?.code)
        store.close()
        store(root, keys, provider).close()
    }

    @Test fun templateLeaseRejectsRevokedReferenceWithTemplateError() {
        val store = store(tempRoot(), StoreKeys(), StoreAuthority()); val enrollment = grant()
        val candidate = store.createCandidate(enrollment)
        val template = requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, enrollment,
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        store.revokeGrant("g1", OwnershipAuthorization(store.storeId, "owner"))
        assertCode(IdentityErrorCode.TEMPLATE_REVOKED) {
            store.acquireTemplateLease(template, grantWithScope("verify", IdentityScope.VERIFICATION), IdentityScope.VERIFICATION, template.embeddingSpaceId)
        }
        store.close()
    }

    @Test fun templateLeaseRejectsExpiredAlteredAndProviderStaleGrants() {
        val provider = StoreAuthority(); val store = store(tempRoot(), StoreKeys(), provider)
        val enrollment = grant(); val candidate = store.createCandidate(enrollment)
        val template = requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, enrollment,
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val verification = grantWithScope("verify", IdentityScope.VERIFICATION)
        val expired = AuthorizationGrant("expired", "tenant.subject", setOf(IdentityScope.VERIFICATION), "login", 0, 100, 0, "audit")
        assertCode(IdentityErrorCode.AUTHORIZATION_EXPIRED) { store.acquireTemplateLease(template, expired, IdentityScope.VERIFICATION, template.embeddingSpaceId) }
        assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) { store.acquireTemplateLease(template, enrollment, IdentityScope.VERIFICATION, template.embeddingSpaceId) }
        store.acquireTemplateLease(template, verification, IdentityScope.VERIFICATION, template.embeddingSpaceId).close()
        val altered = AuthorizationGrant("verify", "tenant.subject", setOf(IdentityScope.VERIFICATION), "login", 1, 200, 0, "audit")
        assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) { store.acquireTemplateLease(template, altered, IdentityScope.VERIFICATION, template.embeddingSpaceId) }
        provider.epoch = 1
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { store.acquireTemplateLease(template, verification, IdentityScope.VERIFICATION, template.embeddingSpaceId) }
        store.close()
    }

    @Test fun templateLeaseRechecksEpochAfterDecryptAndReleasesWithoutTouchingNewWriter() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val enrollment = grant(); val candidate = store.createCandidate(enrollment)
        val template = requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, enrollment,
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val verification = grantWithScope("verify", IdentityScope.VERIFICATION)
        keys.onTemplateRead = { provider.epoch = 1 }
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { store.acquireTemplateLease(template, verification, IdentityScope.VERIFICATION, template.embeddingSpaceId) }
        keys.onTemplateRead = null; provider.epoch = 0
        val lease = store.acquireTemplateLease(template, verification, IdentityScope.VERIFICATION, template.embeddingSpaceId)
        keys.rejectJournal = true
        assertCode(IdentityErrorCode.RE_ENROLLMENT_REQUIRED) { store.createCandidate(grant("uncommitted")) }
        keys.rejectJournal = false
        val reopened = store(root, keys, provider)
        lease.close(); lease.close(); store.close()
        assertCode(IdentityErrorCode.BUSY) { store(root, keys, provider) }
        assertEquals(candidate, reopened.recordedTemplateCandidate(template))
        reopened.close()
    }

    @Test fun identificationLeaseRejectsVerificationOnlyEmbeddingSpace() {
        val store = store(tempRoot(), StoreKeys(), StoreAuthority())
        val enrollment = grant(); val candidate = store.createCandidate(enrollment)
        val backend = VerificationOnlyBackend()
        val ref = requireNotNull(store.enroll(IdentityProbe("verification-only", listOf(1f, 2f)), candidate, enrollment,
            EnrollmentOptions(0.8, "host.quality", "q1"), backend).templateRef)
        assertCode(IdentityErrorCode.EMBEDDING_SPACE_MISMATCH) {
            store.acquireTemplateLease(ref, grantWithScope("identify", IdentityScope.IDENTIFICATION), IdentityScope.IDENTIFICATION, ref.embeddingSpaceId).close()
        }
        store.close()
    }

    @Test fun deleteTemplateTombstonesOnlyItsTargetAndReturnsTheSameReceiptAfterRestart() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val candidate = store.createCandidate(grant())
        val first = requireNotNull(store.enroll(IdentityProbe("first", listOf(1f, 2f)), candidate, grant(),
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val second = requireNotNull(store.enroll(IdentityProbe("second", listOf(1f, 2f)), candidate, grant("g2"),
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val ownership = OwnershipAuthorization(store.storeId, "owner")

        val receipt = store.deleteTemplate(first, ownership)

        assertEquals(store.storeId, receipt.storeId)
        assertEquals(first.id, receipt.templateId)
        assertEquals(DeletionStatus.PURGED, store.awaitDeletion(receipt))
        assertEquals(receipt, store.deleteTemplate(first, ownership))
        assertCode(IdentityErrorCode.TEMPLATE_REVOKED) {
            store.acquireTemplateLease(first, grantWithScope("verify", IdentityScope.VERIFICATION), IdentityScope.VERIFICATION, first.embeddingSpaceId)
        }
        assertEquals(candidate, store.recordedTemplateCandidate(second))
        store.close()

        val reopened = store(root, keys, provider)
        assertEquals(receipt, reopened.deleteTemplate(first, OwnershipAuthorization(reopened.storeId, "owner")))
        assertEquals(DeletionStatus.PURGED, reopened.awaitDeletion(receipt))
        assertEquals(candidate, reopened.recordedTemplateCandidate(second))
        reopened.close()
    }

    @Test fun awaitDeletionDeadlineCancellationAndLeaseReleaseUseCommittedFacts() {
        val clock = MutableStoreMono()
        val store = store(tempRoot(), StoreKeys(), StoreAuthority(), clock)
        val enrollment = grant(); val candidate = store.createCandidate(enrollment)
        val ref = requireNotNull(store.enroll(IdentityProbe("await", listOf(1f, 2f)), candidate, enrollment,
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val lease = store.acquireTemplateLease(ref, grantWithScope("verify", IdentityScope.VERIFICATION), IdentityScope.VERIFICATION, ref.embeddingSpaceId)
        val receipt = store.deleteTemplate(ref, OwnershipAuthorization(store.storeId, "owner"))
        val deadline = IdentityDeadline(clock, 10); clock.now = 11
        assertEquals(DeletionStatus.PURGE_PENDING, store.awaitDeletion(receipt, deadline))
        val cancelled = IdentityStopSignal().also { it.requestStop(IdentityStopReason.CANCELLED) }
        assertCode(IdentityErrorCode.CANCELLED) { store.awaitDeletion(receipt, IdentityDeadline(clock, 10), cancelled) }
        val started = CountDownLatch(1)
        val caught = CountDownLatch(1)
        val interrupted = AtomicBoolean(false)
        val cancellation = AtomicReference<IdentityErrorCode>()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val waiting = executor.submit<DeletionStatus> {
                started.countDown()
                try { store.awaitDeletion(receipt, IdentityDeadline(clock, 1_000_000_000)) }
                catch (error: IdentityError) {
                    interrupted.set(Thread.currentThread().isInterrupted)
                    cancellation.set(error.code)
                    caught.countDown()
                    throw error
                }
            }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            waiting.cancel(true)
            assertTrue(caught.await(2, TimeUnit.SECONDS))
            assertEquals(IdentityErrorCode.CANCELLED, cancellation.get())
            assertTrue(interrupted.get())
            val completed = executor.submit<DeletionStatus> { store.awaitDeletion(receipt, IdentityDeadline(clock, 1_000_000_000)) }
            lease.close()
            assertEquals(DeletionStatus.PURGED, completed.get(2, TimeUnit.SECONDS))
        } finally {
            lease.close()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
            store.close()
        }
    }

    @Test fun deleteTemplateRejectsForgedCrossStoreReferenceAndOwnership() {
        val keys = StoreKeys(); val provider = StoreAuthority()
        val first = store(tempRoot(), keys, provider); val second = store(tempRoot(), keys, provider)
        val candidate = first.createCandidate(grant())
        val template = requireNotNull(first.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, grant(),
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val forged = TemplateRef(template.id, template.embeddingSpaceId, template.authorizationEpoch, "forged")

        assertCode(IdentityErrorCode.TEMPLATE_NOT_FOUND) { first.deleteTemplate(forged, OwnershipAuthorization(first.storeId, "owner")) }
        assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) { first.deleteTemplate(template, OwnershipAuthorization("wrong", "owner")) }
        assertCode(IdentityErrorCode.TEMPLATE_NOT_FOUND) { second.deleteTemplate(template, OwnershipAuthorization(second.storeId, "owner")) }
        first.close(); second.close()
    }

    @Test fun deleteCandidateIsolatesCandidatesAndPurgesEveryOwnedTemplate() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val firstGrant = grant(); val secondGrant = grant("g2")
        val first = store.createCandidate(firstGrant); val second = store.createCandidate(secondGrant)
        val firstTemplate = requireNotNull(store.enroll(IdentityProbe("first-1", listOf(1f, 2f)), first, firstGrant,
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val secondFirst = requireNotNull(store.enroll(IdentityProbe("first-2", listOf(1f, 2f)), first, secondGrant,
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val otherTemplate = requireNotNull(store.enroll(IdentityProbe("other", listOf(1f, 2f)), second, secondGrant,
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)

        val receipt = store.deleteCandidate(first, OwnershipAuthorization(store.storeId, "owner"))

        assertEquals(first.id, receipt.candidateId); assertNull(receipt.templateId)
        assertEquals(DeletionStatus.PURGED, store.awaitDeletion(receipt))
        assertCode(IdentityErrorCode.CANDIDATE_REVOKED) { store.recordedCandidateGrant(first) }
        assertCode(IdentityErrorCode.TEMPLATE_REVOKED) { store.recordedTemplateCandidate(firstTemplate) }
        assertCode(IdentityErrorCode.TEMPLATE_REVOKED) { store.recordedTemplateCandidate(secondFirst) }
        assertEquals(second, store.recordedTemplateCandidate(otherTemplate))
        store.close()
    }

    @Test fun deleteEmptyCandidatePersistsTombstoneAndDeniesEnrollmentAfterRestart() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val authorization = grant(); val candidate = store.createCandidate(authorization)
        val receipt = store.deleteCandidate(candidate, OwnershipAuthorization(store.storeId, "owner"))

        assertEquals(candidate.id, receipt.candidateId); assertNull(receipt.templateId)
        assertEquals(DeletionStatus.PURGED, store.awaitDeletion(receipt))
        assertCode(IdentityErrorCode.CANDIDATE_REVOKED) {
            store.enroll(IdentityProbe("after-delete", listOf(1f, 2f)), candidate, authorization,
                EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system())
        }
        store.close()
        val reopened = store(root, keys, provider)
        assertEquals(receipt, reopened.deleteCandidate(candidate, OwnershipAuthorization(reopened.storeId, "owner")))
        assertEquals(DeletionStatus.PURGED, reopened.awaitDeletion(receipt)); reopened.close()
    }

    @Test fun candidateDeletionReceiptWireIsStrictAndStoreBound() {
        val store = store(tempRoot(), StoreKeys(), StoreAuthority()); val candidate = store.createCandidate(grant())
        val receipt = store.deleteCandidate(candidate, OwnershipAuthorization(store.storeId, "owner"))
        assertEquals(receipt, DeletionReceipt.decodeJSON(receipt.canonicalJSON()))
        assertCode(IdentityErrorCode.INVALID_INPUT) {
            DeletionReceipt.decodeJSON("{\"id\":\"r\",\"storeId\":\"s\",\"templateId\":\"t\",\"candidateId\":\"c\"}")
        }
        assertCode(IdentityErrorCode.INVALID_INPUT) { store.awaitDeletion(receipt.copy(candidateId = "forged")) }
        assertCode(IdentityErrorCode.INVALID_INPUT) { store.awaitDeletion(receipt.copy(storeId = "other")) }
        store.close()
    }

    @Test fun repeatedCandidateDeletionRevalidatesOwnershipAndPreservesReceiptAfterRestart() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val candidate = store.createCandidate(grant()); val owner = OwnershipAuthorization(store.storeId, "owner")
        assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) { store.deleteCandidate(candidate, OwnershipAuthorization("wrong", "owner")) }
        provider.denyOwnership = true
        assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) { store.deleteCandidate(candidate, owner) }
        provider.denyOwnership = false
        val receipt = store.deleteCandidate(candidate, owner)
        provider.denyOwnership = true
        assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) { store.deleteCandidate(candidate, owner) }
        provider.denyOwnership = false; store.close()
        val reopened = store(root, keys, provider)
        assertEquals(receipt, reopened.deleteCandidate(candidate, OwnershipAuthorization(reopened.storeId, "owner")))
        assertEquals(DeletionStatus.PURGED, reopened.awaitDeletion(receipt)); reopened.close()
    }

    @Test fun candidateDeletionWaitsForEveryHeldLeaseAndSharesPriorTemplateReceipt() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val authorization = grant(); val candidate = store.createCandidate(authorization)
        val first = requireNotNull(store.enroll(IdentityProbe("first", listOf(1f, 2f)), candidate, authorization,
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val second = requireNotNull(store.enroll(IdentityProbe("second", listOf(1f, 2f)), candidate, grant("g2"),
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val owner = OwnershipAuthorization(store.storeId, "owner")
        val prior = store.deleteTemplate(first, owner)
        val verification = grantWithScope("verify", IdentityScope.VERIFICATION)
        val firstLease = store.acquireTemplateLease(second, verification, IdentityScope.VERIFICATION, second.embeddingSpaceId)
        val lastLease = store.acquireTemplateLease(second, verification, IdentityScope.VERIFICATION, second.embeddingSpaceId)
        val receipt = store.deleteCandidate(candidate, owner)

        assertNotEquals(prior.id, receipt.id); assertEquals(DeletionStatus.PURGE_PENDING, store.awaitDeletion(receipt))
        firstLease.close(); assertEquals(DeletionStatus.PURGE_PENDING, store.awaitDeletion(receipt))
        lastLease.close(); assertEquals(DeletionStatus.PURGED, store.awaitDeletion(receipt))
        assertEquals(prior, store.deleteTemplate(first, owner)); store.close()
    }

    @Test fun failedCandidatePurgeRemainsRetryableAfterRestart() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val authorization = grant(); val candidate = store.createCandidate(authorization)
        store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, authorization,
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system())
        keys.rejectTemplateDeletion = true
        val receipt = store.deleteCandidate(candidate, OwnershipAuthorization(store.storeId, "owner"))
        assertEquals(DeletionStatus.PURGE_FAILED, store.awaitDeletion(receipt)); store.close()
        keys.rejectTemplateDeletion = false
        val reopened = store(root, keys, provider)
        assertEquals(DeletionStatus.PURGED, reopened.awaitDeletion(receipt)); reopened.close()
    }

    @Test fun candidateDeletionStopsInFlightEnrollmentAndSuppressesPublication() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val authorization = grant(); val candidate = store.createCandidate(authorization); val backend = BlockingEnrollmentBackend()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val job = executor.submit(Callable {
                runCatching { store.enroll(IdentityProbe("blocked", listOf(1f, 2f)), candidate, authorization,
                    EnrollmentOptions(0.8, "host.quality", "q1"), backend) }.exceptionOrNull()
            })
            assertTrue(backend.started.await(2, TimeUnit.SECONDS))
            store.deleteCandidate(candidate, OwnershipAuthorization(store.storeId, "owner"))
            assertEquals(IdentityErrorCode.AUTHORIZATION_REVOKED, (job.get(2, TimeUnit.SECONDS) as? IdentityError)?.code)
            assertEquals(0, root.toFile().listFiles()!!.count { it.name.startsWith("identity-template-") })
        } finally { backend.release.countDown(); executor.shutdownNow(); store.close() }
    }

    @Test fun committedCandidateTombstoneSurvivesLaterCreateRevokeEnrollAndLeaseCommits() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val deletedGrant = grant(); val deleted = store.createCandidate(deletedGrant)
        val deletion = store.deleteCandidate(deleted, OwnershipAuthorization(store.storeId, "owner"))
        val otherGrant = grant("other", domain = "tenant.other")
        val other = store.createCandidate(otherGrant)
        val template = requireNotNull(store.enroll(IdentityProbe("other", listOf(1f, 2f)), other, otherGrant,
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val verification = grantWithScope("verify", IdentityScope.VERIFICATION, domain = "tenant.other")
        store.acquireTemplateLease(template, verification, IdentityScope.VERIFICATION, template.embeddingSpaceId).close()
        store.revokeGrant("other", OwnershipAuthorization(store.storeId, "owner")); store.close()
        val reopened = store(root, keys, provider)
        assertEquals(deletion, reopened.deleteCandidate(deleted, OwnershipAuthorization(reopened.storeId, "owner")))
        assertEquals(DeletionStatus.PURGED, reopened.awaitDeletion(deletion))
        assertCode(IdentityErrorCode.CANDIDATE_REVOKED) { reopened.recordedCandidateGrant(deleted) }
        reopened.close()
    }

    @Test fun restoreRejectsCandidateDeletionMissingTemplateFactBeforeRecoveryIO() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val authorization = grant(); val candidate = store.createCandidate(authorization)
        val template = requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, authorization,
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        store.close()
        IdentityJournal(root, "store", keys).use { journal ->
            val fields = IdentityContractJSON.parse(requireNotNull(journal.read()).decodeToString()).toMutableMap()
            fields["version"] = 5
            fields["deletions"] = emptyMap<String, Any?>()
            fields["candidateDeletions"] = mapOf(candidate.id to mapOf("receiptId" to "candidate-receipt", "templateIds" to listOf(template.id)))
            journal.commit(IdentityContractJSON.encode(fields).encodeToByteArray())
        }
        assertCode(IdentityErrorCode.INVALID_INPUT) { store(root, keys, provider).use { } }
        assertTrue(Files.exists(root.resolve("identity-template-${template.id}.bin")))
    }

    @Test fun deleteTemplateWaitsForLeaseThenPurgesWhenLeaseCloses() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val candidate = store.createCandidate(grant())
        val template = requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, grant(),
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val lease = store.acquireTemplateLease(template, grantWithScope("verify", IdentityScope.VERIFICATION), IdentityScope.VERIFICATION, template.embeddingSpaceId)

        val receipt = store.deleteTemplate(template, OwnershipAuthorization(store.storeId, "owner"))

        assertEquals(DeletionStatus.PURGE_PENDING, store.awaitDeletion(receipt))
        assertCode(IdentityErrorCode.TEMPLATE_REVOKED) { store.recordedTemplateCandidate(template) }
        lease.close(); lease.close()
        assertEquals(DeletionStatus.PURGED, store.awaitDeletion(receipt))
        store.close()
    }

    @Test fun failedTemplatePayloadPurgePersistsRetryableStatusAndRetriesAfterRestart() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val candidate = store.createCandidate(grant())
        val template = requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, grant(),
            EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        keys.rejectTemplateDeletion = true

        val receipt = store.deleteTemplate(template, OwnershipAuthorization(store.storeId, "owner"))

        assertEquals(DeletionStatus.PURGE_FAILED, store.awaitDeletion(receipt))
        store.close()
        keys.rejectTemplateDeletion = false
        val reopened = store(root, keys, provider)
        assertEquals(DeletionStatus.PURGED, reopened.awaitDeletion(receipt))
        reopened.close()
    }

    @Test fun deletionWaitsForAllLeasesBeforeRemovingExactPayloadAndKey() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val candidate = store.createCandidate(grant())
        val ref = requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, grant(), EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val verify = grantWithScope("verify", IdentityScope.VERIFICATION)
        val first = store.acquireTemplateLease(ref, verify, IdentityScope.VERIFICATION, ref.embeddingSpaceId)
        val last = store.acquireTemplateLease(ref, verify, IdentityScope.VERIFICATION, ref.embeddingSpaceId)
        val receipt = store.deleteTemplate(ref, OwnershipAuthorization(store.storeId, "owner"))
        first.close()
        assertEquals(DeletionStatus.PURGE_PENDING, store.awaitDeletion(receipt))
        assertTrue(Files.exists(root.resolve("identity-template-${ref.id}.bin")))
        assertEquals(0, keys.templateDeletions)
        last.close()
        assertFalse(Files.exists(root.resolve("identity-template-${ref.id}.bin")))
        assertEquals(1, keys.templateDeletions)
        store.createCandidate(grant("after-delete"))
        store.close()
        val reopened = store(root, keys, provider)
        assertEquals(DeletionStatus.PURGED, reopened.awaitDeletion(receipt))
        reopened.close()
    }

    @Test fun repeatedDeletionRevalidatesOwnershipAndReceiptAfterBiometricRevocation() {
        val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(tempRoot(), keys, provider)
        val candidate = store.createCandidate(grant())
        val ref = requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, grant(), EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val owner = OwnershipAuthorization(store.storeId, "owner")
        store.revokeGrant("g1", owner)
        val receipt = store.deleteTemplate(ref, owner)
        provider.denyOwnership = true
        assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) { store.deleteTemplate(ref, owner) }
        provider.denyOwnership = false
        assertCode(IdentityErrorCode.TEMPLATE_NOT_FOUND) { store.deleteTemplate(ref.copy(revision = "forged"), owner) }
        assertEquals(receipt, DeletionReceipt.decodeJSON(receipt.canonicalJSON()))
        assertCode(IdentityErrorCode.INVALID_INPUT) { store.awaitDeletion(receipt.copy(templateId = "forged")) }
        assertCode(IdentityErrorCode.INVALID_INPUT) { store.awaitDeletion(receipt.copy(storeId = "other")) }
        store.close()
    }

    @Test fun purgeKeyDeletionCallbackCannotReenterStoreMutation() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val candidate = store.createCandidate(grant())
        val ref = requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, grant(), EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val reentry = AtomicBoolean(false)
        keys.onTemplateDelete = {
            reentry.set(true)
            assertCode(IdentityErrorCode.BUSY) { store.createCandidate(grant("reentrant")) }
        }
        val receipt = store.deleteTemplate(ref, OwnershipAuthorization(store.storeId, "owner"))
        assertTrue(reentry.get())
        assertEquals(DeletionStatus.PURGED, store.awaitDeletion(receipt))
        store.createCandidate(grant("after-reentry"))
        store.close()
    }

    @Test fun failedLogicalDeletionDoesNotRemovePayloadOrPublishTombstone() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val candidate = store.createCandidate(grant())
        val ref = requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, grant(), EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        keys.rejectJournal = true
        assertCode(IdentityErrorCode.RE_ENROLLMENT_REQUIRED) { store.deleteTemplate(ref, OwnershipAuthorization(store.storeId, "owner")) }
        assertEquals(0, keys.templateDeletions)
        keys.rejectJournal = false
        val reopened = store(root, keys, provider)
        val lease = reopened.acquireTemplateLease(ref, grantWithScope("verify", IdentityScope.VERIFICATION), IdentityScope.VERIFICATION, ref.embeddingSpaceId)
        lease.close(); reopened.close(); store.close()
    }

    @Test fun purgeCommitFailureRetiresWriterAndReopenRetriesDurableTombstone() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val candidate = store.createCandidate(grant())
        val ref = requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, grant(), EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val lease = store.acquireTemplateLease(ref, grantWithScope("verify", IdentityScope.VERIFICATION), IdentityScope.VERIFICATION, ref.embeddingSpaceId)
        val receipt = store.deleteTemplate(ref, OwnershipAuthorization(store.storeId, "owner"))
        keys.onTemplateDelete = { keys.rejectJournal = true }
        lease.close()
        assertCode(IdentityErrorCode.CLOSED) { store.awaitDeletion(receipt) }
        keys.onTemplateDelete = null; keys.rejectJournal = false
        val reopened = store(root, keys, provider)
        assertEquals(DeletionStatus.PURGED, reopened.awaitDeletion(receipt))
        val count = keys.templateDeletions
        lease.close(); store.close()
        assertEquals(count, keys.templateDeletions)
        assertCode(IdentityErrorCode.BUSY) { store(root, keys, provider) }
        reopened.close()
    }

    @Test fun retiredPendingLeaseReleaseCannotCleanFilesOwnedByNewWriter() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val candidate = store.createCandidate(grant())
        val ref = requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, grant(), EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val lease = store.acquireTemplateLease(ref, grantWithScope("verify", IdentityScope.VERIFICATION), IdentityScope.VERIFICATION, ref.embeddingSpaceId)
        val receipt = store.deleteTemplate(ref, OwnershipAuthorization(store.storeId, "owner"))
        keys.rejectJournal = true
        assertCode(IdentityErrorCode.RE_ENROLLMENT_REQUIRED) { store.createCandidate(grant("retire")) }
        keys.rejectJournal = false
        val reopened = store(root, keys, provider)
        val count = keys.templateDeletions
        lease.close(); store.close()
        assertEquals(count, keys.templateDeletions)
        assertEquals(DeletionStatus.PURGED, reopened.awaitDeletion(receipt))
        reopened.close()
    }

    @Test fun restoreRejectsInvalidDeletionMetadataBeforeRecoveryCleanup() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val candidate = store.createCandidate(grant())
        val ref = requireNotNull(store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, grant(), EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        keys.rejectTemplateDeletion = true
        store.deleteTemplate(ref, OwnershipAuthorization(store.storeId, "owner")); store.close()
        IdentityJournal(root, "store", keys).use { journal ->
            val fields = IdentityContractJSON.parse(requireNotNull(journal.read()).decodeToString()).toMutableMap()
            @Suppress("UNCHECKED_CAST")
            val deletions = (fields["deletions"] as Map<String, Any?>).toMutableMap()
            deletions[java.util.UUID.randomUUID().toString()] = mapOf("receiptId" to "unowned", "status" to "purgePending")
            fields["deletions"] = deletions
            journal.commit(IdentityContractJSON.encode(fields).encodeToByteArray())
        }
        keys.rejectTemplateDeletion = false
        val count = keys.templateDeletions
        assertCode(IdentityErrorCode.INVALID_INPUT) { store(root, keys, provider) }
        assertEquals(count, keys.templateDeletions)
    }

    @Test fun candidateDeletionStopsTicketsBeforePurgeAndBeforePurgeCommitFailure() {
        val root = tempRoot(); val keys = StoreKeys(); val provider = StoreAuthority(); val store = store(root, keys, provider)
        val candidate = store.createCandidate(grant())
        store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, grant(), EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system())
        val ticket = store.beginOperation(candidate, grantWithScope("verify", IdentityScope.VERIFICATION), IdentityScope.VERIFICATION)
        val stoppedAtPurge = AtomicReference<IdentityErrorCode?>()
        keys.onTemplateDelete = {
            try { ticket.stopSignal.check() } catch (error: IdentityError) { stoppedAtPurge.set(error.code) }
            keys.rejectJournal = true
        }
        assertCode(IdentityErrorCode.RE_ENROLLMENT_REQUIRED) { store.deleteCandidate(candidate, OwnershipAuthorization(store.storeId, "owner")) }
        assertEquals(IdentityErrorCode.AUTHORIZATION_REVOKED, stoppedAtPurge.get())
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { store.finishOperation(ticket, IdentityError(IdentityErrorCode.BACKEND_UNAVAILABLE)) }
        keys.onTemplateDelete = null; keys.rejectJournal = false
        store.close()
        val reopened = store(root, keys, provider)
        assertCode(IdentityErrorCode.CANDIDATE_REVOKED) { reopened.recordedCandidateGrant(candidate) }
        reopened.close()
    }

    @Test fun templateDeletionStopsOnlyTicketBoundToDeletedTemplate() {
        val store = store(tempRoot(), StoreKeys(), StoreAuthority())
        val authorization = grant(); val candidate = store.createCandidate(authorization)
        val first = requireNotNull(store.enroll(IdentityProbe("first", listOf(1f, 2f)), candidate, authorization, EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val second = requireNotNull(store.enroll(IdentityProbe("second", listOf(3f, 4f)), candidate, authorization, EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val firstTicket = store.beginOperation(candidate, authorization, IdentityScope.ENROLLMENT, listOf(first, second))
        val secondTicket = store.beginOperation(candidate, authorization, IdentityScope.ENROLLMENT, listOf(second))
        val candidateOnly = store.beginOperation(candidate, authorization, IdentityScope.ENROLLMENT)

        store.deleteTemplate(first, OwnershipAuthorization(store.storeId, "owner"))

        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { firstTicket.stopSignal.check() }
        secondTicket.stopSignal.check(); candidateOnly.stopSignal.check()
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { store.finishOperation(firstTicket, IdentityError(IdentityErrorCode.BACKEND_UNAVAILABLE)) }
        store.finishOperation(secondTicket); store.finishOperation(candidateOnly)
        assertCode(IdentityErrorCode.TEMPLATE_REVOKED) { store.beginOperation(candidate, authorization, IdentityScope.ENROLLMENT, listOf(first)) }
        store.close()
    }

    @Test fun candidateDeletionStopsOnlyItsCandidateAndTemplateTickets() {
        val store = store(tempRoot(), StoreKeys(), StoreAuthority())
        val authorization = grant(); val firstCandidate = store.createCandidate(authorization); val secondCandidate = store.createCandidate(authorization)
        val first = requireNotNull(store.enroll(IdentityProbe("first", listOf(1f, 2f)), firstCandidate, authorization, EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        val second = requireNotNull(store.enroll(IdentityProbe("second", listOf(3f, 4f)), secondCandidate, authorization, EnrollmentOptions(0.8, "host.quality", "q1"), FixtureIdentityBackend.system()).templateRef)
        assertCode(IdentityErrorCode.TEMPLATE_NOT_FOUND) {
            store.beginOperation(firstCandidate, authorization, IdentityScope.ENROLLMENT, listOf(first.copy(revision = "forged")))
        }
        assertCode(IdentityErrorCode.TEMPLATE_REVOKED) {
            store.beginOperation(firstCandidate, authorization, IdentityScope.ENROLLMENT, listOf(second))
        }
        val firstTicket = store.beginOperation(firstCandidate, authorization, IdentityScope.ENROLLMENT, listOf(first))
        val secondTicket = store.beginOperation(secondCandidate, authorization, IdentityScope.ENROLLMENT, listOf(second))

        store.deleteCandidate(firstCandidate, OwnershipAuthorization(store.storeId, "owner"))

        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { firstTicket.stopSignal.check() }
        secondTicket.stopSignal.check()
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { store.finishOperation(firstTicket) }
        store.finishOperation(secondTicket)
        store.close()
    }

    @Test fun exactTemplateStopDeliveryBlocksRepeatedDeletionCandidateCleanupAndLeaseRelease() {
        val keys = StoreKeys(); val store = store(tempRoot(), keys, StoreAuthority())
        val authorization = grant(); val candidate = store.createCandidate(authorization)
        val template = enrollFixture(store, candidate, authorization)
        val lease = store.acquireTemplateLease(template, grantWithScope("verify", IdentityScope.VERIFICATION), IdentityScope.VERIFICATION, template.embeddingSpaceId)
        val ticket = store.beginOperation(candidate, authorization, IdentityScope.ENROLLMENT, listOf(template))
        val entered = CountDownLatch(1); val resume = CountDownLatch(1)
        val registration = ticket.stopSignal.registerStopHandler { entered.countDown(); resume.await(5, TimeUnit.SECONDS) }
        val executor = Executors.newSingleThreadExecutor()
        val owner = OwnershipAuthorization(store.storeId, "owner")
        try {
            val deleting = executor.submit<DeletionReceipt> { store.deleteTemplate(template, owner) }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            store.deleteTemplate(template, owner)
            store.deleteCandidate(candidate, owner)
            lease.close()
            assertEquals(0, keys.templateDeletions, "cleanup overtook exact stop delivery")
            resume.countDown()
            val receipt = deleting.get(5, TimeUnit.SECONDS)
            assertEquals(DeletionStatus.PURGED, store.awaitDeletion(receipt))
        } finally {
            resume.countDown(); lease.close(); executor.shutdown(); executor.awaitTermination(5, TimeUnit.SECONDS)
            assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { store.finishOperation(ticket, IdentityError(IdentityErrorCode.BACKEND_UNAVAILABLE)) }
            registration.close(); store.close()
        }
    }

    @Test fun exactTemplateStopCallbackRunsOutsideStoreGate() {
        val store = store(tempRoot(), StoreKeys(), StoreAuthority())
        val authorization = grant(); val candidate = store.createCandidate(authorization)
        val template = enrollFixture(store, candidate, authorization)
        val ticket = store.beginOperation(candidate, authorization, IdentityScope.ENROLLMENT, listOf(template))
        val queried = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        val registration = ticket.stopSignal.registerStopHandler {
            val query = executor.submit<CandidateRef> { store.recordedTemplateCandidate(template) }
            try { query.get(1, TimeUnit.SECONDS) }
            catch (error: java.util.concurrent.ExecutionException) { queried.set((error.cause as? IdentityError)?.code == IdentityErrorCode.TEMPLATE_REVOKED) }
            catch (_: java.util.concurrent.TimeoutException) { }
        }
        try {
            store.deleteTemplate(template, OwnershipAuthorization(store.storeId, "owner"))
            assertTrue(queried.get(), "stop callback held the gate and blocked a concurrent state query")
        } finally {
            executor.shutdown(); executor.awaitTermination(5, TimeUnit.SECONDS)
            assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { store.finishOperation(ticket) }
            registration.close(); store.close()
        }
    }

    private fun store(root: java.nio.file.Path, keys: StoreKeys, provider: StoreAuthority, monotonicClock: IdentityMonotonicClock = StoreMono()) =
        IdentityStore(root.toFile(), "login", provider, keys, StoreClock(100), monotonicClock)
    private fun grant(id: String = "g1", purpose: String = "login", epoch: Long = 0, domain: String = "tenant.subject") =
        AuthorizationGrant(id, domain, setOf(IdentityScope.ENROLLMENT), purpose, 0, 200, epoch, "audit")
    private fun grantWithScope(id: String, scope: IdentityScope, epoch: Long = 0, domain: String = "tenant.subject") =
        AuthorizationGrant(id, domain, setOf(scope), "login", 0, 200, epoch, "audit")
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
    var rejectTemplateDeletion = false
    var templateDeletions = 0
    var onTemplateDelete: (() -> Unit)? = null
    var onTemplateRead: (() -> Unit)? = null
    override fun key(id: String, createIfMissing: Boolean): SecretKey = synchronized(values) {
        if (reject) throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
        if (rejectJournal && id.startsWith("journal.")) throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
        if (!createIfMissing && id.startsWith("template.")) onTemplateRead?.invoke()
        if (rejectTemplateCreation && createIfMissing && id.startsWith("template.")) throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
        values[id] ?: if (createIfMissing) KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also {
            values[id] = it
            if (failJournalAfterTemplateCreation && id.startsWith("template.")) rejectJournal = true
        }
        else throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
    }
    override fun deleteKey(id: String) { synchronized(values) {
        if (rejectTemplateDeletion && id.startsWith("template.")) throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
        values.remove(id)
        if (id.startsWith("template.")) { templateDeletions++; onTemplateDelete?.invoke() }
    } }
}

private class VerificationOnlyBackend : IdentityBackend {
    private val base = FixtureIdentityBackend.system()
    override val backendId = base.backendId
    override val backendVersion = base.backendVersion
    override val modelBinding = base.modelBinding
    override val embeddingSpace = EmbeddingSpaceId(setOf(IdentityCapability.VERIFICATION), base.embeddingSpace.backendId,
        base.embeddingSpace.backendVersion, base.embeddingSpace.modelBinding, base.embeddingSpace.featureFormatVersion,
        base.embeddingSpace.dimension, base.embeddingSpace.dtype, base.embeddingSpace.endianness, base.embeddingSpace.normalization,
        base.embeddingSpace.preprocessRevision, base.embeddingSpace.scoreMetric, base.embeddingSpace.scoreDirection)
    override fun availability() = base.availability()
    override fun evaluateQuality(probe: IdentityProbe) = base.evaluateQuality(probe)
    override fun embed(probe: IdentityProbe) = IdentityEmbedding(embeddingSpace, base.embed(probe).values)
    override fun close() = base.close()
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
private class MutableStoreMono(var now: Long = 1) : IdentityMonotonicClock { override val nowNanos get() = now }
private class AdvancingClock : IdentityWallClock {
    private var current = 100L
    override val nowMillis: Long get() = current.also { current = 200L }
}
