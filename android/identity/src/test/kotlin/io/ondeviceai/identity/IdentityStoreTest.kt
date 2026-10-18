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

    private fun store(root: java.nio.file.Path, keys: StoreKeys, provider: StoreAuthority) =
        IdentityStore(root.toFile(), "login", provider, keys, StoreClock(100), StoreMono())
    private fun grant(id: String = "g1", purpose: String = "login", epoch: Long = 0) =
        AuthorizationGrant(id, "tenant.subject", setOf(IdentityScope.ENROLLMENT), purpose, 0, 200, epoch, "audit")
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
    override fun authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Long, nowMillis: Long) = epoch
    override fun validateOwnership(authorization: OwnershipAuthorization, nowMillis: Long) = Unit
}
private data class StoreClock(override val nowMillis: Long) : IdentityWallClock
private data class StoreMono(override val nowNanos: Long = 1) : IdentityMonotonicClock
private class AdvancingClock : IdentityWallClock {
    private var current = 100L
    override val nowMillis: Long get() = current.also { current = 200L }
}
