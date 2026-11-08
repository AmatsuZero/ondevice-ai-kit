package io.ondeviceai.litertlm

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import androidx.test.platform.app.InstrumentationRegistry
import io.ondeviceai.multimodal.MultimodalClientError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtLmProcessClientLocalEdgesInstrumentation {
    @Test
    fun canceledConnectIgnoresLateServiceConnectionAndReleasesLocalClaim() = runBlocking {
        supervisorScope {
            val coordinator = LiteRtLmProcessCoordinator()
            val context = ControlledContext(targetContext())
            val client = LiteRtLmProcessClient(context, coordinator)
            var connected: LiteRtLmProcessConnection? = null
            val connect = async(start = CoroutineStart.UNDISPATCHED) {
                client.connect("cancel-before-connected").also { connected = it }
            }
            var failure: Throwable? = null
            try {
                val serviceConnection = context.awaitBind(connect)
                connect.cancelAndJoin()
                assertTrue(connect.isCancelled)
                assertEquals(1, context.bindCalls)
                assertEquals(1, context.unbindCalls)

                val lateBinder = SyntheticBinder()
                serviceConnection.onServiceConnected(
                    ComponentName(context.packageName, "SyntheticProcessService"),
                    lateBinder,
                )

                assertEquals(0, lateBinder.linkCalls)
                assertEquals(0, lateBinder.transactCalls)
                assertEquals(1, context.bindCalls)
                assertEquals(1, context.unbindCalls)

                val nextClaim = coordinator.claim("after-cancel")
                assertTrue(coordinator.releaseBeforeRemoteReservation(nextClaim))
            } catch (t: Throwable) {
                failure = t
                throw t
            } finally {
                withContext(NonCancellable) {
                    preserveFailure(failure) {
                        connect.cancelAndJoin()
                        connected?.let { connection ->
                            if (connection.releaseBeforeReservation()) {
                                assertNull(awaitRelease(connection))
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun linkToDeathRemoteExceptionRejectsBeforeHelloAndReleasesClaim() = runBlocking {
        supervisorScope {
            val coordinator = LiteRtLmProcessCoordinator()
            val context = ControlledContext(targetContext())
            val client = LiteRtLmProcessClient(context, coordinator)
            var connected: LiteRtLmProcessConnection? = null
            val connect = async(start = CoroutineStart.UNDISPATCHED) {
                client.connect("synthetic-link-rejection").also { connected = it }
            }
            var failure: Throwable? = null
            try {
                val serviceConnection = context.awaitBind(connect)
                val binder = SyntheticBinder(failLinkToDeath = true)
                serviceConnection.onServiceConnected(
                    ComponentName(context.packageName, "SyntheticProcessService"),
                    binder,
                )

                val rejection = try {
                    withTimeout(TEST_TIMEOUT_MILLIS) { connect.await() }
                    throw AssertionError("linkToDeath failure must reject before ownership")
                } catch (rejection: LiteRtLmPreOwnershipRejection) {
                    rejection
                }
                assertFalse(rejection.rejection is MultimodalClientError.Busy)
                assertEquals(1, binder.linkCalls)
                assertEquals(0, binder.transactCalls)
                assertEquals(1, context.bindCalls)
                assertEquals(1, context.unbindCalls)

                val nextClaim = coordinator.claim("after-link-rejection")
                assertTrue(coordinator.releaseBeforeRemoteReservation(nextClaim))
            } catch (t: Throwable) {
                failure = t
                throw t
            } finally {
                withContext(NonCancellable) {
                    preserveFailure(failure) {
                        connect.cancelAndJoin()
                        connected?.let { connection ->
                            if (connection.releaseBeforeReservation()) {
                                assertNull(awaitRelease(connection))
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun duplicateConnectedBinderIsIgnoredAndUnreservedReleaseIsLocal() = runBlocking {
        supervisorScope {
            val coordinator = LiteRtLmProcessCoordinator()
            val context = ControlledContext(targetContext())
            val client = LiteRtLmProcessClient(context, coordinator)
            var connection: LiteRtLmProcessConnection? = null
            var locallyReleased = false
            val connect = async(start = CoroutineStart.UNDISPATCHED) {
                client.connect("duplicate-connected").also { connection = it }
            }
            var failure: Throwable? = null
            try {
                val serviceConnection = context.awaitBind(connect)
                val firstBinder = SyntheticBinder()
                serviceConnection.onServiceConnected(
                    ComponentName(context.packageName, "SyntheticProcessService"),
                    firstBinder,
                )
                val ownedConnection = withTimeout(TEST_TIMEOUT_MILLIS) { connect.await() }

                assertEquals(1, firstBinder.linkCalls)
                assertEquals(0, firstBinder.transactCalls)

                val duplicateBinder = SyntheticBinder()
                serviceConnection.onServiceConnected(
                    ComponentName(context.packageName, "SyntheticProcessService"),
                    duplicateBinder,
                )
                assertEquals(1, firstBinder.linkCalls)
                assertEquals(0, firstBinder.transactCalls)
                assertEquals(0, duplicateBinder.linkCalls)
                assertEquals(0, duplicateBinder.transactCalls)

                assertTrue(ownedConnection.releaseBeforeReservation())
                locallyReleased = true
                assertNull(awaitRelease(ownedConnection))
                assertEquals(1, firstBinder.unlinkCalls)
                assertEquals(1, context.unbindCalls)

                val nextClaim = coordinator.claim("after-local-release")
                assertTrue(coordinator.releaseBeforeRemoteReservation(nextClaim))
            } catch (t: Throwable) {
                failure = t
                throw t
            } finally {
                withContext(NonCancellable) {
                    preserveFailure(failure) {
                        connect.cancelAndJoin()
                        connection?.let { ownedConnection ->
                            if (!locallyReleased) {
                                assertTrue(ownedConnection.releaseBeforeReservation())
                                locallyReleased = true
                            }
                            assertNull(awaitRelease(ownedConnection))
                        }
                    }
                }
            }
        }
    }

    private suspend fun awaitRelease(
        connection: LiteRtLmProcessConnection,
    ): LiteRtLmProcessDeathOutcome? =
        withTimeout(TEST_TIMEOUT_MILLIS) { connection.awaitOwnershipReleased() }

    private suspend fun preserveFailure(
        original: Throwable?,
        cleanup: suspend () -> Unit,
    ) {
        try {
            cleanup()
        } catch (cleanupFailure: Throwable) {
            if (original == null) {
                throw cleanupFailure
            }
            original.addSuppressed(cleanupFailure)
        }
    }

    private fun targetContext(): Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private class ControlledContext(base: Context) : ContextWrapper(base) {
        private val bindEntered = CompletableDeferred<ServiceConnection>()
        var bindCalls = 0
            private set
        var unbindCalls = 0
            private set

        override fun getApplicationContext(): Context = this

        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
            bindCalls++
            bindEntered.complete(conn)
            return true
        }

        override fun unbindService(conn: ServiceConnection) {
            unbindCalls++
        }

        suspend fun awaitBind(connect: Deferred<*>): ServiceConnection =
            withTimeout(TEST_TIMEOUT_MILLIS) {
                select {
                    bindEntered.onAwait { it }
                    connect.onAwait {
                        throw AssertionError(
                            "connect completed before the test supplied a ServiceConnection",
                        )
                    }
                }
            }
    }

    private class SyntheticBinder(
        private val failLinkToDeath: Boolean = false,
    ) : IBinder by Binder() {
        var linkCalls = 0
            private set
        var unlinkCalls = 0
            private set
        var transactCalls = 0
            private set

        override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) {
            linkCalls++
            if (failLinkToDeath) {
                throw RemoteException("synthetic local linkToDeath rejection")
            }
        }

        override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int): Boolean {
            unlinkCalls++
            return true
        }

        override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            transactCalls++
            return false
        }
    }

    private companion object {
        const val TEST_TIMEOUT_MILLIS = 20_000L
    }
}
