package io.ondeviceai.identity

import kotlin.test.*
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

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
    override fun key(id: String, createIfMissing: Boolean): SecretKey = synchronized(values) {
        if (reject) throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
        values[id] ?: if (createIfMissing) KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { values[id] = it }
        else throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
    }
    override fun deleteKey(id: String) { synchronized(values) { values.remove(id) } }
}
private class StoreAuthority(var epoch: Long = 0) : IdentityAuthorizationProvider {
    var denyOwnership = false
    var onAuthorize: (() -> Unit)? = null
    override fun authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Long, nowMillis: Long): Long { onAuthorize?.invoke(); return epoch }
    override fun validateOwnership(authorization: OwnershipAuthorization, nowMillis: Long) {
        if (denyOwnership) throw IdentityError(IdentityErrorCode.AUTHORIZATION_REQUIRED)
    }
}
private data class StoreClock(override val nowMillis: Long) : IdentityWallClock
private data class StoreMono(override val nowNanos: Long = 1) : IdentityMonotonicClock
private class AdvancingClock : IdentityWallClock {
    private var current = 100L
    override val nowMillis: Long get() = current.also { current = 200L }
}
