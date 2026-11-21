package io.ondeviceai.identity

import java.nio.file.Files
import java.util.Comparator
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IdentityLivenessOperationTest {
    private val fixtures = mutableListOf<LivenessGateFixture>()
    private fun fixture() = LivenessGateFixture().also(fixtures::add)
    @AfterTest fun cleanup() { fixtures.forEach { it.cleanup() } }

    @Test fun standaloneGrantPersistsWithoutCandidateAndCannotBeReplayedOrBorrowedByAnotherStore() {
        val f = fixture(); val other = fixture()
        val ticket = f.store.beginLivenessOperation(f.grant())
        assertCode(IdentityErrorCode.INVALID_INPUT) { other.store.finishLivenessOperation(ticket) {} }
        assertEquals(42, f.store.finishLivenessOperation(ticket) { 42 })
        assertCode(IdentityErrorCode.INVALID_INPUT) { f.store.finishLivenessOperation(ticket) {} }
        f.store.close(); other.store.close()
        val reopened = f.reopen()
        reopened.revokeGrant("live", f.owner)
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { reopened.beginLivenessOperation(f.grant()) }
        reopened.close()
    }

    @Test fun admissionRequiresLivenessScopePurposeExpiryAndAuthoritativeEpoch() {
        val f = fixture()
        listOf(f.grant(scope = setOf(IdentityScope.IDENTIFICATION)) to IdentityErrorCode.AUTHORIZATION_REQUIRED,
            f.grant(purpose = "other") to IdentityErrorCode.AUTHORIZATION_REQUIRED,
            f.grant(expires = 100) to IdentityErrorCode.AUTHORIZATION_EXPIRED).forEach { (grant, code) ->
            assertCode(code) { f.store.beginLivenessOperation(grant) }
        }
        f.authority.epoch = 1
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { f.store.beginLivenessOperation(f.grant()) }
        f.store.close()
    }

    @Test fun finalGateRechecksExpiryAndProviderAndConsumesFailedTicket() {
        val f = fixture()
        val expiring = f.store.beginLivenessOperation(f.grant(expires = 200))
        f.clock.value = 200
        assertCode(IdentityErrorCode.AUTHORIZATION_EXPIRED) { f.store.finishLivenessOperation(expiring) { error("expired published") } }
        f.clock.value = 100
        val stale = f.store.beginLivenessOperation(f.grant(id = "stale"))
        f.authority.epoch = 1
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { f.store.finishLivenessOperation(stale) { error("revoked published") } }
        assertCode(IdentityErrorCode.INVALID_INPUT) { f.store.finishLivenessOperation(stale) {} }
        f.store.close()
    }

    @Test fun revocationWinsOverCooperativeCancelAndStopsOnlyItsDomain() {
        val f = fixture()
        val ticket = f.store.beginLivenessOperation(f.grant())
        val unrelated = f.store.beginLivenessOperation(f.grant(id = "other", domain = "other"))
        ticket.stopSignal.requestStop(IdentityStopReason.CANCELLED)
        f.store.revokeGrant("live", f.owner)
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { f.store.finishLivenessOperation(ticket) { error("revoked published") } }
        assertEquals(7, f.store.finishLivenessOperation(unrelated) { 7 })
        f.store.close()
    }

    @Test fun newerTrustedEpochStopsPriorSessionAndPersistsInvalidation() {
        val f = fixture()
        val old = f.store.beginLivenessOperation(f.grant())
        val stopped = CountDownLatch(1)
        val registration = old.stopSignal.registerStopHandler { stopped.countDown() }
        val newer = f.store.beginLivenessOperation(f.grant(id = "new", epoch = 1))
        assertTrue(stopped.await(2, TimeUnit.SECONDS))
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { f.store.finishLivenessOperation(old) { error("old published") } }
        f.store.finishLivenessOperation(newer) {}; registration.close(); f.store.close()
        val reopened = f.reopen()
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { reopened.beginLivenessOperation(f.grant()) }
        reopened.close()
    }

    @Test fun cancellationDuringFinalProviderCallbackSuppressesPublication() {
        val f = fixture(); val ticket = f.store.beginLivenessOperation(f.grant())
        f.authority.hook = { ticket.stopSignal.requestStop(IdentityStopReason.CANCELLED) }
        assertCode(IdentityErrorCode.CANCELLED) { f.store.finishLivenessOperation(ticket) { error("cancel ignored") } }
        f.store.close()
    }

    @Test fun closeStopsAndRetainsWriterUntilTicketAndReservedCallbackDrain() {
        val f = fixture(); val ticket = f.store.beginLivenessOperation(f.grant())
        val stopped = CountDownLatch(1); val release = CountDownLatch(1)
        val registration = ticket.stopSignal.registerStopHandler { stopped.countDown(); release.await(3, TimeUnit.SECONDS) }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val closing = executor.submit { f.store.close() }
            assertTrue(stopped.await(2, TimeUnit.SECONDS))
            assertCode(IdentityErrorCode.CLOSED) { f.store.finishLivenessOperation(ticket) { error("closed published") } }
            assertCode(IdentityErrorCode.BUSY) { f.reopen() }
            release.countDown(); closing.get(2, TimeUnit.SECONDS); registration.close()
            f.reopen().close()
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun revocationCallbackCanCloseAndConsumeWithoutSelfDeadlock() {
        val f = fixture(); val ticket = f.store.beginLivenessOperation(f.grant())
        val drained = CountDownLatch(1)
        val registration = ticket.stopSignal.registerStopHandler {
            f.store.close()
            assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { f.store.finishLivenessOperation(ticket) { error("revoked published") } }
            drained.countDown()
        }
        f.store.revokeGrant("live", f.owner)
        assertTrue(drained.await(2, TimeUnit.SECONDS))
        registration.close(); f.store.close(); f.reopen().close()
    }

    @Test fun newEnrollmentEpochInvalidatesLivenessWithoutMakingItCandidateScoped() {
        val f = fixture(); val ticket = f.store.beginLivenessOperation(f.grant())
        f.store.createCandidate(f.grant(id = "enroll", scope = setOf(IdentityScope.ENROLLMENT), epoch = 1))
        assertCode(IdentityErrorCode.AUTHORIZATION_REVOKED) { f.store.finishLivenessOperation(ticket) { error("old session published") } }
        f.store.close()
    }

    @Test fun publicationClosureFailureConsumesTicketAndCompletedValueIsNotRecalled() {
        val f = fixture(); val failed = f.store.beginLivenessOperation(f.grant())
        assertCode(IdentityErrorCode.INVALID_CHALLENGE) { f.store.finishLivenessOperation(failed) { throw IdentityError(IdentityErrorCode.INVALID_CHALLENGE) } }
        assertCode(IdentityErrorCode.INVALID_INPUT) { f.store.finishLivenessOperation(failed) {} }
        val completed = f.store.beginLivenessOperation(f.grant())
        val value = f.store.finishLivenessOperation(completed) { "immutable" }
        f.store.revokeGrant("live", f.owner)
        assertEquals("immutable", value); f.store.close()
    }

    private fun assertCode(code: IdentityErrorCode, action: () -> Any?) = assertEquals(code, assertFailsWith<IdentityError> { action() }.code)
}
private class LivenessGateFixture {
    val root = Files.createTempDirectory("liveness-gate")
    val keys = LivenessGateKeys(); val authority = LivenessGateAuthority(); val clock = LivenessGateClock()
    val store = reopen()
    val owner get() = OwnershipAuthorization(store.storeId, "owner")
    fun reopen() = IdentityStore(root.toFile(), "identity", authority, keys, clock, LivenessGateMono)
    fun grant(id: String = "live", domain: String = "live-domain", scope: Set<IdentityScope> = setOf(IdentityScope.LIVENESS),
        purpose: String = "identity", expires: Long? = null, epoch: Long = 0) =
        AuthorizationGrant(id, domain, scope, purpose, 0, expires, epoch, "fixture")
    fun cleanup() { Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
}
private class LivenessGateClock : IdentityWallClock { @Volatile var value = 100L; override val nowMillis get() = value }
private object LivenessGateMono : IdentityMonotonicClock { override val nowNanos get() = 1L }
private class LivenessGateAuthority : IdentityAuthorizationProvider {
    @Volatile var epoch: Long? = null
    @Volatile var hook: (() -> Unit)? = null
    override fun authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Long, nowMillis: Long): Long {
        val callback = hook; hook = null; callback?.invoke()
        return epoch ?: grant.authorizationEpoch
    }
    override fun validateOwnership(authorization: OwnershipAuthorization, nowMillis: Long) {}
}
private class LivenessGateKeys : IdentityKeyProvider {
    private val values = mutableMapOf<String, SecretKey>()
    @Synchronized override fun key(id: String, createIfMissing: Boolean): SecretKey = values[id] ?: run {
        if (!createIfMissing) throw IdentityError(IdentityErrorCode.RE_ENROLLMENT_REQUIRED)
        SecretKeySpec(ByteArray(32) { 7 }, "AES").also { values[id] = it }
    }
    @Synchronized override fun deleteKey(id: String) { values.remove(id) }
}
