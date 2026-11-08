package io.ondeviceai.litertlm

import io.ondeviceai.multimodal.MultimodalClientError
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiteRtLmProcessCoordinatorTest {
    @Test
    fun secondOwnerIsBusyUntilExactDeath() {
        val coordinator = LiteRtLmProcessCoordinator()
        val first = coordinator.claim()
        val remote = Any()
        coordinator.linkDeath(first, remote)
        coordinator.beginHello(first, remote)
        coordinator.reserveRemote(first)
        assertFailsWith<MultimodalClientError.Busy> { coordinator.claim() }
        assertEquals(LiteRtLmGenerationTerminal.LOST, coordinator.exactDeath(first, remote)?.terminal)
        coordinator.claim()
    }

    @Test
    fun explicitPreReservationFailureReleasesButAmbiguousOwnershipDoesNot() {
        val coordinator = LiteRtLmProcessCoordinator()
        val rejected = coordinator.claim()
        assertTrue(coordinator.releaseBeforeRemoteReservation(rejected))
        val anotherLocalFailure = coordinator.claim()
        assertTrue(coordinator.releaseBeforeRemoteReservation(anotherLocalFailure))

        val ambiguous = coordinator.claim()
        val remote = Any()
        coordinator.linkDeath(ambiguous, remote)
        coordinator.beginHello(ambiguous, remote)
        coordinator.markRemoteOwnershipAmbiguous(ambiguous)
        assertFalse(coordinator.releaseBeforeRemoteReservation(ambiguous))
        assertFailsWith<MultimodalClientError.Busy> { coordinator.claim() }
    }

    @Test
    fun helloAcceptedThenStartRejectedDoesNotReleaseBeforeExactDeath() {
        val coordinator = LiteRtLmProcessCoordinator()
        val claim = coordinator.claim()
        val remote = Any()
        coordinator.linkDeath(claim, remote)
        coordinator.beginHello(claim, remote)
        coordinator.reserveRemote(claim)
        assertFalse(coordinator.rejectStart(claim))
        assertFailsWith<MultimodalClientError.Busy> { coordinator.claim() }
        assertEquals(LiteRtLmGenerationTerminal.LOST, coordinator.exactDeath(claim, remote)?.terminal)
    }

    @Test
    fun missingHelloResponseFailsClosedButExactDeathDuringHandshakeReleasesLostClaim() {
        val coordinator = LiteRtLmProcessCoordinator()
        val claim = coordinator.claim()
        val remote = Any()
        coordinator.linkDeath(claim, remote)
        coordinator.beginHello(claim, remote)
        assertFalse(coordinator.releaseBeforeRemoteReservation(claim))
        assertFailsWith<MultimodalClientError.Busy> { coordinator.claim() }
        assertEquals(LiteRtLmGenerationTerminal.LOST, coordinator.exactDeath(claim, remote)?.terminal)

        val explicitlyRejected = coordinator.claim()
        val rejectedRemote = Any()
        coordinator.linkDeath(explicitlyRejected, rejectedRemote)
        coordinator.beginHello(explicitlyRejected, rejectedRemote)
        assertTrue(coordinator.helloRejected(explicitlyRejected))
        coordinator.claim()
    }

    @Test
    fun terminalCallbackBeforeHelloAcceptanceIsRejectedAndHandshakeRemainsClaimed() {
        val coordinator = LiteRtLmProcessCoordinator()
        val claim = coordinator.claim()
        val remote = Any()
        coordinator.linkDeath(claim, remote)
        coordinator.beginHello(claim, remote)
        assertFailsWith<IllegalStateException> {
            coordinator.recordTerminal(claim, LiteRtLmGenerationTerminal.SUCCEEDED)
        }
        assertFalse(coordinator.releaseBeforeRemoteReservation(claim))
        assertEquals(LiteRtLmGenerationTerminal.LOST, coordinator.exactDeath(claim, remote)?.terminal)
    }

    @Test
    fun terminalDoesNotReleaseAndCancelCannotBecomeSuccess() {
        val coordinator = LiteRtLmProcessCoordinator()
        val claim = coordinator.claim()
        val remote = Any()
        coordinator.linkDeath(claim, remote)
        coordinator.beginHello(claim, remote)
        coordinator.reserveRemote(claim)
        coordinator.acceptStart(claim)
        coordinator.requestCancel(claim)
        coordinator.recordTerminal(claim, LiteRtLmGenerationTerminal.SUCCEEDED)
        assertFailsWith<MultimodalClientError.Busy> { coordinator.claim() }
        assertEquals(LiteRtLmGenerationTerminal.CANCELED, coordinator.exactDeath(claim, remote)?.terminal)
    }

    @Test
    fun staleOrWrongDeathCannotReleaseNewClaim() {
        val coordinator = LiteRtLmProcessCoordinator()
        val first = coordinator.claim()
        val firstRemote = Any()
        coordinator.linkDeath(first, firstRemote)
        coordinator.beginHello(first, firstRemote)
        coordinator.reserveRemote(first)
        coordinator.exactDeath(first, firstRemote)

        val second = coordinator.claim()
        val secondRemote = Any()
        coordinator.linkDeath(second, secondRemote)
        coordinator.beginHello(second, secondRemote)
        coordinator.reserveRemote(second)
        assertNull(coordinator.exactDeath(first, firstRemote))
        assertNull(coordinator.exactDeath(second, Any()))
        assertFailsWith<MultimodalClientError.Busy> { coordinator.claim() }
        assertEquals(LiteRtLmGenerationTerminal.LOST, coordinator.exactDeath(second, secondRemote)?.terminal)
    }

    @Test
    fun sameNonceCannotBypassAndConcurrentClaimsHaveSingleWinner() {
        val coordinator = LiteRtLmProcessCoordinator()
        val entered = CountDownLatch(1)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = List(2) {
                pool.submit<Boolean> {
                    entered.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    try {
                        coordinator.claim("same-nonce")
                        true
                    } catch (_: MultimodalClientError.Busy) {
                        false
                    }
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            start.countDown()
            assertEquals(1, results.count { it.get(5, TimeUnit.SECONDS) })
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun sharedCoordinatorBlocksIndependentOwnersUntilExactDeath() {
        val first = LiteRtLmProcessCoordinator.shared.claim("owner-a")
        val remote = Any()
        LiteRtLmProcessCoordinator.shared.linkDeath(first, remote)
        LiteRtLmProcessCoordinator.shared.beginHello(first, remote)
        LiteRtLmProcessCoordinator.shared.reserveRemote(first)
        assertFailsWith<MultimodalClientError.Busy> {
            LiteRtLmProcessCoordinator.shared.claim("owner-b")
        }
        LiteRtLmProcessCoordinator.shared.exactDeath(first, remote)
    }

    @Test
    fun ambiguousHandshakeRequiresLinkedRemoteAndDoesNotMakeWrongOrderSticky() {
        val coordinator = LiteRtLmProcessCoordinator()
        val claim = coordinator.claim()
        assertFailsWith<IllegalStateException> { coordinator.markRemoteOwnershipAmbiguous(claim) }
        assertTrue(coordinator.releaseBeforeRemoteReservation(claim))
    }

    @Test
    fun duplicateBeginHelloRejectedWithoutReleasingClaim() {
        val coordinator = LiteRtLmProcessCoordinator()
        val claim = coordinator.claim()
        val remote = Any()
        coordinator.linkDeath(claim, remote)
        coordinator.beginHello(claim, remote)

        assertFailsWith<IllegalStateException> { coordinator.beginHello(claim, remote) }
        assertFalse(coordinator.releaseBeforeRemoteReservation(claim))
        assertFailsWith<MultimodalClientError.Busy> { coordinator.claim() }
        assertEquals(LiteRtLmGenerationTerminal.LOST, coordinator.exactDeath(claim, remote)?.terminal)
    }

    @Test
    fun reserveWithoutBeginHelloRejectedWithoutChangingState() {
        val coordinator = LiteRtLmProcessCoordinator()
        val unreserved = coordinator.claim()
        coordinator.linkDeath(unreserved, Any())

        assertFailsWith<IllegalStateException> { coordinator.reserveRemote(unreserved) }
        assertTrue(coordinator.releaseBeforeRemoteReservation(unreserved))

        val claim = coordinator.claim()
        val remote = Any()
        coordinator.linkDeath(claim, remote)
        coordinator.beginHello(claim, remote)
        coordinator.reserveRemote(claim)
        coordinator.acceptStart(claim)
        coordinator.recordTerminal(claim, LiteRtLmGenerationTerminal.SUCCEEDED)
        assertEquals(
            LiteRtLmGenerationTerminal.SUCCEEDED,
            coordinator.exactDeath(claim, remote)?.terminal,
        )
    }

    @Test
    fun conflictingTerminalIsRejectedButSameTerminalIsIdempotent() {
        val started = startedClaim()
        started.coordinator.recordTerminal(started.claim, LiteRtLmGenerationTerminal.ERROR)
        started.coordinator.recordTerminal(started.claim, LiteRtLmGenerationTerminal.ERROR)
        assertFailsWith<IllegalStateException> {
            started.coordinator.recordTerminal(started.claim, LiteRtLmGenerationTerminal.CANCELED)
        }
        assertEquals(LiteRtLmGenerationTerminal.ERROR,
            started.coordinator.exactDeath(started.claim, started.remote)?.terminal)
    }

    @Test
    fun lateHelloCallbacksAndForgedIdentityCannotAffectNewClaim() {
        val coordinator = LiteRtLmProcessCoordinator()
        val old = coordinator.claim("same")
        val remote = Any()
        coordinator.linkDeath(old, remote)
        coordinator.beginHello(old, remote)
        coordinator.exactDeath(old, remote)
        val newer = coordinator.claim("same")
        val newerRemote = Any()
        coordinator.linkDeath(newer, newerRemote)
        coordinator.beginHello(newer, newerRemote)
        coordinator.reserveRemote(newer)
        assertFailsWith<IllegalStateException> { coordinator.acceptStart(old) }
        assertFailsWith<IllegalStateException> {
            coordinator.recordTerminal(old, LiteRtLmGenerationTerminal.SUCCEEDED)
        }
        val forged = LiteRtLmProcessClaim(newer.generation, newer.nonce)
        assertNull(coordinator.exactDeath(forged, newerRemote))
        assertFailsWith<MultimodalClientError.Busy> { coordinator.claim() }
        coordinator.exactDeath(newer, newerRemote)
    }

    private data class Started(
        val coordinator: LiteRtLmProcessCoordinator,
        val claim: LiteRtLmProcessClaim,
        val remote: Any,
    )

    private fun startedClaim(): Started {
        val coordinator = LiteRtLmProcessCoordinator()
        val claim = coordinator.claim()
        val remote = Any()
        coordinator.linkDeath(claim, remote)
        coordinator.beginHello(claim, remote)
        coordinator.reserveRemote(claim)
        coordinator.acceptStart(claim)
        return Started(coordinator, claim, remote)
    }
}
