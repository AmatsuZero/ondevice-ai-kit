package io.ondeviceai.identity

import java.nio.file.Files
import java.util.Comparator
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdentityGalleryOperationTest {
    private val roots = mutableListOf<java.nio.file.Path>()

    @AfterTest fun cleanup() { roots.forEach { root ->
        Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    } }

    @Test fun publicationRechecksEveryMemberAndOperationDomainBeforeSynchronousCommit() {
        val authority = GalleryAuthority()
        val store = store(tempRoot(), authority)
        val enrollment = grant("enroll", "template.domain", IdentityScope.ENROLLMENT)
        val candidate = store.createCandidate(enrollment)
        val ref = enroll(store, candidate, enrollment)
        val member = grant("member", "template.domain", IdentityScope.IDENTIFICATION)
        val operation = grant("operation", "operation.domain", IdentityScope.IDENTIFICATION)
        val ticket = store.beginGalleryOperation(operation, listOf(IdentityGalleryMember(ref, member)), ref.embeddingSpaceId)

        store.revokeGrant("member", OwnershipAuthorization(store.storeId, "owner"))

        var committed = false
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { store.consumeGalleryOperation(ticket) { committed = true } }
        assertFalse(committed)
        store.close()
    }

    @Test fun operationAndMemberGrantsPersistForRevocationAndDoNotBorrowEnrollmentScope() {
        val root = tempRoot(); val authority = GalleryAuthority(); val keys = GalleryKeys()
        val store = IdentityStore(root.toFile(), "login", authority, keys, GalleryClock, GalleryMono)
        val enrollment = grant("enroll", "template.domain", IdentityScope.ENROLLMENT)
        val candidate = store.createCandidate(enrollment)
        val ref = enroll(store, candidate, enrollment)
        assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) {
            store.beginGalleryOperation(
                grant("operation", "operation.domain", IdentityScope.IDENTIFICATION),
                listOf(IdentityGalleryMember(ref, enrollment)), ref.embeddingSpaceId
            )
        }
        val ticket = store.beginGalleryOperation(
            grant("operation", "operation.domain", IdentityScope.IDENTIFICATION),
            listOf(IdentityGalleryMember(ref, grant("member", "template.domain", IdentityScope.IDENTIFICATION))), ref.embeddingSpaceId
        )
        store.consumeGalleryOperation(ticket) { Unit }
        store.close()

        val reopened = IdentityStore(root.toFile(), "login", authority, keys, GalleryClock, GalleryMono)
        val receipt = reopened.revokeGrant("operation", OwnershipAuthorization(reopened.storeId, "owner"))
        assertEquals(reopened.storeId, receipt.storeId)
        reopened.close()
    }

    @Test fun closedStoreStopsGalleryOperationAndRetainsWriterUntilTicketDrains() {
        val store = store(tempRoot(), GalleryAuthority())
        val enrollment = grant("enroll", "template.domain", IdentityScope.ENROLLMENT)
        val candidate = store.createCandidate(enrollment)
        val ref = enroll(store, candidate, enrollment)
        val ticket = store.beginGalleryOperation(
            grant("operation", "operation.domain", IdentityScope.IDENTIFICATION),
            listOf(IdentityGalleryMember(ref, grant("member", "template.domain", IdentityScope.IDENTIFICATION))), ref.embeddingSpaceId
        )
        val stopped = java.util.concurrent.CountDownLatch(1)
        val registration = ticket.stopSignal.registerStopHandler { stopped.countDown() }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val closing = executor.submit { store.close() }
            assertTrue(stopped.await(2, TimeUnit.SECONDS))
            assertCode(IdentityErrorCode.CLOSED) { store.consumeGalleryOperation(ticket) { error("must not publish") } }
            closing.get(2, TimeUnit.SECONDS)
        } finally {
            registration.close(); executor.shutdownNow()
        }
    }

    @Test fun trustedNewerMemberEpochPersistsTemplateTombstoneBeforeRejectingPublication() {
        val root = tempRoot(); val authority = GalleryAuthority(); val keys = GalleryKeys()
        val store = IdentityStore(root.toFile(), "login", authority, keys, GalleryClock, GalleryMono)
        val enrollment = grant("enroll", "shared.domain", IdentityScope.ENROLLMENT)
        val candidate = store.createCandidate(enrollment)
        val ref = enroll(store, candidate, enrollment)

        assertCode(IdentityErrorCode.TEMPLATE_REVOKED) {
            store.beginGalleryOperation(
                AuthorizationGrant("operation", "stale.domain", setOf(IdentityScope.IDENTIFICATION), "login", 0, 200, 1, "audit"),
                listOf(IdentityGalleryMember(ref, AuthorizationGrant("member", "shared.domain", setOf(IdentityScope.IDENTIFICATION), "login", 0, 200, 1, "audit"))),
                ref.embeddingSpaceId
            )
        }
        assertCode(IdentityErrorCode.TEMPLATE_REVOKED) { store.recordedTemplateCandidate(ref) }
        store.close()
        val reopened = IdentityStore(root.toFile(), "login", authority, keys, GalleryClock, GalleryMono)
        assertCode(IdentityErrorCode.TEMPLATE_REVOKED) { reopened.recordedTemplateCandidate(ref) }
        reopened.close()
    }

    @Test fun memberGrantMustMatchTheTemplateAuthorizationDomainPurposeAndEpoch() {
        val store = store(tempRoot(), GalleryAuthority())
        val enrollment = grant("enroll", "template.domain", IdentityScope.ENROLLMENT)
        val candidate = store.createCandidate(enrollment)
        val ref = enroll(store, candidate, enrollment)
        assertCode(IdentityErrorCode.AUTHORIZATION_REQUIRED) {
            store.beginGalleryOperation(
                grant("operation", "operation.domain", IdentityScope.IDENTIFICATION),
                listOf(IdentityGalleryMember(ref, grant("member", "other.domain", IdentityScope.IDENTIFICATION))), ref.embeddingSpaceId
            )
        }
        store.close()
    }

    @Test fun templateAndCandidateDeletionStopBoundGalleryOperationsBeforePurge() {
        val store = store(tempRoot(), GalleryAuthority())
        val enrollment = grant("enroll", "template.domain", IdentityScope.ENROLLMENT)
        val candidate = store.createCandidate(enrollment)
        val ref = enroll(store, candidate, enrollment)
        val templateTicket = ticket(store, ref)
        val templateStopped = java.util.concurrent.CountDownLatch(1)
        val templateRegistration = templateTicket.stopSignal.registerStopHandler { templateStopped.countDown() }
        try {
            store.deleteTemplate(ref, OwnershipAuthorization(store.storeId, "owner"))
            assertTrue(templateStopped.await(2, TimeUnit.SECONDS))
            assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { store.consumeGalleryOperation(templateTicket) { error("must not publish") } }
        } finally { templateRegistration.close() }

        val other = store.createCandidate(grant("other", "other.domain", IdentityScope.ENROLLMENT))
        val otherRef = enroll(store, other, grant("other", "other.domain", IdentityScope.ENROLLMENT))
        val candidateTicket = ticket(store, otherRef, "other.domain", "other")
        val candidateStopped = java.util.concurrent.CountDownLatch(1)
        val candidateRegistration = candidateTicket.stopSignal.registerStopHandler { candidateStopped.countDown() }
        try {
            store.deleteCandidate(other, OwnershipAuthorization(store.storeId, "owner"))
            assertTrue(candidateStopped.await(2, TimeUnit.SECONDS))
            assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { store.consumeGalleryOperation(candidateTicket) { error("must not publish") } }
        } finally { candidateRegistration.close(); store.close() }
    }

    @Test fun operationGrantIsRecheckedImmediatelyBeforePublication() {
        val authority = GalleryAuthority().also { it.advanceOperationOnSecondCheck = true }
        val store = store(tempRoot(), authority)
        val enrollment = grant("enroll", "template.domain", IdentityScope.ENROLLMENT)
        val candidate = store.createCandidate(enrollment)
        val ref = enroll(store, candidate, enrollment)
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { ticket(store, ref) }
        store.close()
    }

    @Test fun emptyMemberListProducesOperationOnlyTicket() {
        val store = store(tempRoot(), GalleryAuthority())
        val ticket = store.beginGalleryOperation(grant("operation", "operation.domain", IdentityScope.IDENTIFICATION), emptyList(), FixtureIdentityBackend.system().embeddingSpace)
        assertTrue(ticket.members.isEmpty())
        store.consumeGalleryOperation(ticket) { Unit }
        store.close()
    }

    @Test fun publicationRechecksStopSignalAfterProviderCallbacks() {
        val authority = GalleryAuthority()
        val store = store(tempRoot(), authority)
        val enrollment = grant("enroll", "template.domain", IdentityScope.ENROLLMENT)
        val candidate = store.createCandidate(enrollment)
        val ref = enroll(store, candidate, enrollment)
        val ticket = ticket(store, ref)
        authority.onAuthorize = { ticket.stopSignal.requestStop(IdentityStopReason.CANCELLED) }
        assertCode(IdentityErrorCode.CANCELLED) { store.consumeGalleryOperation(ticket) { error("must not publish") } }
        store.close()
    }

    @Test fun cleanupCallbackRevocationPreventsTicketAdmission() {
        val root = tempRoot(); val authority = GalleryAuthority(); val keys = GalleryKeys()
        val store = IdentityStore(root.toFile(), "login", authority, keys, GalleryClock, GalleryMono)
        val staleGrant = grant("stale", "stale.domain", IdentityScope.ENROLLMENT)
        val staleCandidate = store.createCandidate(staleGrant)
        enroll(store, staleCandidate, staleGrant)
        val enrollment = grant("enroll", "member.domain", IdentityScope.ENROLLMENT)
        val candidate = store.createCandidate(enrollment)
        val ref = enroll(store, candidate, enrollment)
        keys.onDelete = { authority.epochOverride = 2 }
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) {
            store.beginGalleryOperation(
                AuthorizationGrant("operation", "stale.domain", setOf(IdentityScope.IDENTIFICATION), "login", 0, 200, 1, "audit"),
                listOf(IdentityGalleryMember(ref, grant("member", "member.domain", IdentityScope.IDENTIFICATION))),
                ref.embeddingSpaceId
            )
        }
        store.close()
    }

    private fun ticket(store: IdentityStore, ref: TemplateRef, domain: String = "template.domain", id: String = "default") = store.beginGalleryOperation(
        grant("operation-$id", "operation.domain", IdentityScope.IDENTIFICATION),
        listOf(IdentityGalleryMember(ref, grant("member-$id", domain, IdentityScope.IDENTIFICATION))), ref.embeddingSpaceId
    )

    private fun enroll(store: IdentityStore, candidate: CandidateRef, grant: AuthorizationGrant): TemplateRef = requireNotNull(
        store.enroll(IdentityProbe("fixture", listOf(1f, 2f)), candidate, grant,
            EnrollmentOptions(0.8, "quality", "q1"), FixtureIdentityBackend.system()).templateRef
    )
    private fun store(root: java.nio.file.Path, authority: GalleryAuthority) =
        IdentityStore(root.toFile(), "login", authority, GalleryKeys(), GalleryClock, GalleryMono)
    private fun tempRoot() = Files.createTempDirectory("identity-gallery-operation").also(roots::add)
    private fun grant(id: String, domain: String, scope: IdentityScope) =
        AuthorizationGrant(id, domain, setOf(scope), "login", 0, 200, 0, "audit")
    private fun assertCode(code: IdentityErrorCode, block: () -> Any?) =
        assertEquals(code, assertFailsWith<IdentityError> { block() }.code)
}

private object GalleryClock : IdentityWallClock { override val nowMillis = 100L }
private object GalleryMono : IdentityMonotonicClock { override val nowNanos = 1L }
private class GalleryAuthority : IdentityAuthorizationProvider {
    var advanceOperationOnSecondCheck = false
    var onAuthorize: (() -> Unit)? = null
    var epochOverride: Long? = null
    private val checks = mutableMapOf<String, Int>()
    override fun authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Long, nowMillis: Long): Long {
        val check = (checks[grant.grantId] ?: 0) + 1
        checks[grant.grantId] = check
        onAuthorize?.also { onAuthorize = null; it() }
        return epochOverride ?: if (advanceOperationOnSecondCheck && grant.grantId.startsWith("operation") && check > 1) grant.authorizationEpoch + 1 else grant.authorizationEpoch
    }
    override fun validateOwnership(authorization: OwnershipAuthorization, nowMillis: Long) = Unit
}
private class GalleryKeys : IdentityKeyProvider {
    private val keys = mutableMapOf<String, SecretKey>()
    var onDelete: (() -> Unit)? = null
    override fun key(id: String, createIfMissing: Boolean): SecretKey = keys[id] ?: if (createIfMissing) {
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { keys[id] = it }
    } else throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
    override fun deleteKey(id: String) { keys.remove(id); onDelete?.invoke() }
}
