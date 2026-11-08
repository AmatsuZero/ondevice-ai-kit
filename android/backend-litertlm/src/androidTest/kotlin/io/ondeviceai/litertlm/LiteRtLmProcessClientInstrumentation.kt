package io.ondeviceai.litertlm

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import io.ondeviceai.multimodal.MultimodalClientError
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class LiteRtLmProcessClientInstrumentation {
    @Test
    fun reserveUsesThePrivateServiceHelloIdentityAndCannotRepeat() = runBlocking {
        withConnection(LiteRtLmProcessClient(appContext()), nonce()) { owned ->
            val hello = owned.reserve()
            assertTrue(hello.accepted)
            assertEquals(owned.nonce, hello.nonce)
            assertTrue(hello.pid > 0)
            assertNotEquals(Process.myPid(), hello.pid)
            assertEquals("${appContext().packageName}:odai_litertlm", hello.processName)

            try {
                owned.connection.reserve()
                fail("a reserved connection must reject a second HELLO")
            } catch (_: IllegalStateException) {
                // Repeated reservation is a local state error.
            }
        }
    }

    @Test
    fun aCompetingDefaultClientIsBusyBeforeItsSecondBind() = runBlocking {
        val firstClient = LiteRtLmProcessClient(appContext())
        withConnection(firstClient, nonce()) { first ->
            first.reserve()
            val countingContext = CountingContext(appContext(), BindBehavior.DELEGATE)
            val competingClient = LiteRtLmProcessClient(countingContext)
            var unexpectedConnection: LiteRtLmProcessConnection? = null
            val rejection = try {
                unexpectedConnection =
                    withTimeout(TEST_TIMEOUT_MILLIS) { competingClient.connect(nonce()) }
                throw AssertionError("the competing default client must be rejected")
            } catch (rejection: LiteRtLmPreOwnershipRejection) {
                rejection
            } finally {
                unexpectedConnection?.let { connection ->
                    assertTrue(connection.releaseBeforeReservation())
                    assertNull(withTimeout(TEST_TIMEOUT_MILLIS) { connection.awaitOwnershipReleased() })
                }
            }
            assertTrue(rejection.rejection is MultimodalClientError.Busy)
            assertEquals(0, countingContext.bindCalls)
        }
    }

    @Test
    fun unreservedReleaseIsSafeAndAllowsAnotherRealConnection() = runBlocking {
        val client = LiteRtLmProcessClient(appContext())
        withConnection(client, nonce()) { first ->
            first.releaseBeforeReservationAndAwait()
        }
        withConnection(client, nonce()) { reopened ->
            val hello = reopened.reserve()
            assertTrue(hello.accepted)
            reopened.retireAndAwait()
        }
    }

    @Test
    fun cancelKeepsOwnershipUntilTheOriginalBinderActuallyDies() = runBlocking {
        withConnection(LiteRtLmProcessClient(appContext()), nonce()) { owned ->
            owned.reserve()
            assertTrue(owned.connection.cancel())
            assertNotNull(owned.connection.status())
            assertFalse(owned.connection.releaseBeforeReservation())

            val deathWait = async { owned.connection.awaitOwnershipReleased() }
            delay(250)
            assertFalse("CANCEL must not complete exact-death ownership wait", deathWait.isCompleted)
            owned.requestRetire()
            assertNotNull(withTimeout(TEST_TIMEOUT_MILLIS) { deathWait.await() })
            owned.markFinished()
        }
    }

    @Test
    fun rejectedStartDoesNotReleaseTheRemoteOwner() = runBlocking {
        withConnection(LiteRtLmProcessClient(appContext()), nonce()) { owned ->
            owned.reserve()
            val request = LiteRtLmProcessStart(
                modelAbsolutePath = "",
                imageAbsolutePath = "",
                cacheAbsolutePath = "",
                prompt = "",
                maxOutputTokens = 0,
                backendPolicy = LiteRtLmBackendPolicy.CPU_ONLY,
            )
            assertFalse(owned.connection.start(request))
            assertFalse(owned.connection.releaseBeforeReservation())
        }
    }

    @Test
    fun retireWaitsForExactDeathAndAReopenedServiceHasANewPid() = runBlocking {
        val client = LiteRtLmProcessClient(appContext())
        var oldPid = 0
        withConnection(client, nonce()) { first ->
            oldPid = first.reserve().pid
            first.retireAndAwait()
        }
        withConnection(client, nonce()) { second ->
            val hello = second.reserve()
            assertTrue(hello.accepted)
            assertNotEquals(oldPid, hello.pid)
            second.retireAndAwait()
        }
    }

    @Test
    fun bindReturningFalseIsASafePreOwnershipRejection() = runBlocking {
        val context = CountingContext(appContext(), BindBehavior.RETURN_FALSE)
        val coordinator = LiteRtLmProcessCoordinator()
        val client = LiteRtLmProcessClient(context, coordinator)
        trustedRejection { withTimeout(TEST_TIMEOUT_MILLIS) { client.connect(nonce()) } }
        assertEquals(1, context.bindCalls)
        assertEquals(1, context.unbindCalls)

        withConnection(LiteRtLmProcessClient(appContext(), coordinator), nonce()) { reopened ->
            reopened.releaseBeforeReservationAndAwait()
        }
    }

    @Test
    fun bindThrowingIsASafePreOwnershipRejection() = runBlocking {
        val context = CountingContext(appContext(), BindBehavior.THROW)
        val coordinator = LiteRtLmProcessCoordinator()
        val client = LiteRtLmProcessClient(context, coordinator)
        trustedRejection { withTimeout(TEST_TIMEOUT_MILLIS) { client.connect(nonce()) } }
        assertEquals(1, context.bindCalls)
        assertEquals(1, context.unbindCalls)

        withConnection(LiteRtLmProcessClient(appContext(), coordinator), nonce()) { reopened ->
            reopened.releaseBeforeReservationAndAwait()
        }
    }

    @Test
    fun nullBindingIsRejectedAndUnboundExactlyOnceBeforeHello() = runBlocking {
        val context = CountingContext(appContext(), BindBehavior.NULL_BINDING)
        val coordinator = LiteRtLmProcessCoordinator()
        val client = LiteRtLmProcessClient(context, coordinator)
        trustedRejection { withTimeout(TEST_TIMEOUT_MILLIS) { client.connect(nonce()) } }
        assertEquals(1, context.bindCalls)
        assertEquals(1, context.unbindCalls)

        withConnection(LiteRtLmProcessClient(appContext(), coordinator), nonce()) { reopened ->
            reopened.releaseBeforeReservationAndAwait()
        }
    }

    private suspend fun trustedRejection(block: suspend () -> Unit): LiteRtLmPreOwnershipRejection {
        try {
            block()
            throw AssertionError("expected a safe pre-ownership rejection")
        } catch (rejection: LiteRtLmPreOwnershipRejection) {
            assertFalse(rejection.rejection is MultimodalClientError.Busy)
            return rejection
        }
    }

    private suspend fun <T> withConnection(
        client: LiteRtLmProcessClient,
        nonce: String,
        block: suspend (OwnedConnection) -> T,
    ): T {
        val connection = withTimeout(TEST_TIMEOUT_MILLIS) { client.connect(nonce) }
        val owned = OwnedConnection(connection, nonce)
        var failure: Throwable? = null
        try {
            return block(owned)
        } catch (t: Throwable) {
            failure = t
            throw t
        } finally {
            if (!owned.finished) {
                try {
                    withContext(NonCancellable) { owned.cleanup() }
                } catch (cleanup: Throwable) {
                    val original = failure
                    if (original == null) throw cleanup else original.addSuppressed(cleanup)
                }
            }
        }
    }

    private fun appContext(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    private fun nonce(): String = UUID.randomUUID().toString()

    private class OwnedConnection(
        val connection: LiteRtLmProcessConnection,
        val nonce: String,
    ) {
        var finished = false
            private set
        private var locallyReleased = false
        private var retireRequested = false

        suspend fun reserve(): LiteRtLmProcessHello =
            connection.reserve().also { assertTrue(it.accepted) }

        suspend fun awaitReleased(): LiteRtLmProcessDeathOutcome? =
            withTimeout(TEST_TIMEOUT_MILLIS) { connection.awaitOwnershipReleased() }

        suspend fun retireAndAwait() {
            requestRetire()
            assertNotNull(awaitReleased())
            finished = true
        }

        suspend fun releaseBeforeReservationAndAwait() {
            locallyReleased = connection.releaseBeforeReservation()
            assertTrue(locallyReleased)
            assertNull(awaitReleased())
            finished = true
        }

        suspend fun requestRetire() {
            retireRequested = true
            assertTrue(connection.retire())
        }

        fun markFinished() {
            finished = true
        }

        suspend fun cleanup() {
            if (locallyReleased) {
                assertNull(awaitReleased())
            } else if (retireRequested) {
                assertNotNull(awaitReleased())
            } else if (connection.releaseBeforeReservation()) {
                locallyReleased = true
                assertNull(awaitReleased())
            } else {
                requestRetire()
                assertNotNull(awaitReleased())
            }
            finished = true
        }

    }

    private enum class BindBehavior { DELEGATE, RETURN_FALSE, THROW, NULL_BINDING }

    private class CountingContext(
        base: Context,
        private val behavior: BindBehavior,
    ) : ContextWrapper(base) {
        var bindCalls = 0
            private set
        var unbindCalls = 0
            private set

        override fun getApplicationContext(): Context = this

        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
            bindCalls++
            return when (behavior) {
                BindBehavior.DELEGATE -> super.bindService(service, conn, flags)
                BindBehavior.RETURN_FALSE -> false
                BindBehavior.THROW -> throw SecurityException("test bind failure")
                BindBehavior.NULL_BINDING -> {
                    conn.onNullBinding(service.component ?: ComponentName(packageName, "missing"))
                    true
                }
            }
        }

        override fun unbindService(conn: ServiceConnection) {
            unbindCalls++
            if (behavior == BindBehavior.DELEGATE) super.unbindService(conn)
        }
    }

    private companion object {
        const val TEST_TIMEOUT_MILLIS = 20_000L
    }
}
