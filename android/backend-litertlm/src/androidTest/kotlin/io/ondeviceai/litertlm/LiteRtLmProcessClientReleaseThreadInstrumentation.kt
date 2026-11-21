package io.ondeviceai.litertlm

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.RemoteException
import androidx.test.platform.app.InstrumentationRegistry
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class LiteRtLmProcessClientReleaseThreadInstrumentation {
    @Test
    fun unreservedReleaseCleansOffMainAndCompletesOwnershipWaitAfterCleanup() = runBlocking {
        supervisorScope {
            val coordinator = LiteRtLmProcessCoordinator()
            val cleanupGate = CountDownLatch(1)
            val context = ControlledContext(targetContext(), cleanupGate)
            val client = LiteRtLmProcessClient(context, coordinator)
            var connect: Deferred<LiteRtLmProcessConnection>? = null
            var connection: LiteRtLmProcessConnection? = null
            var ownershipWait: Deferred<LiteRtLmProcessDeathOutcome?>? = null
            var failure: Throwable? = null

            try {
                val connecting = async(start = CoroutineStart.UNDISPATCHED) {
                    client.connect("release-threading").also { connection = it }
                }
                connect = connecting
                val serviceConnection = context.awaitBind(connecting)
                val binder = SyntheticBinder()
                serviceConnection.onServiceConnected(
                    ComponentName(context.packageName, "SyntheticProcessService"),
                    binder,
                )
                val ownedConnection = withTimeout(TEST_TIMEOUT_MILLIS) { connecting.await() }
                connection = ownedConnection
                assertEquals(1, binder.linkCalls.get())

                val releaseWait = async(start = CoroutineStart.UNDISPATCHED) {
                    ownedConnection.awaitOwnershipReleased()
                }
                ownershipWait = releaseWait

                var released = false
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    released = ownedConnection.releaseBeforeReservation()
                }
                assertTrue("unreserved release must succeed", released)

                withTimeout(TEST_TIMEOUT_MILLIS) { context.unbindEntered.await() }
                assertFalse("unlinkToDeath must run off main", binder.unlinkOnMain.get())
                assertFalse("unbindService must run off main", context.unbindOnMain.get())
                assertEquals(1, binder.unlinkCalls.get())
                assertEquals(1, context.unbindCalls.get())
                assertFalse(
                    "awaitOwnershipReleased must wait for gated cleanup to finish",
                    releaseWait.isCompleted,
                )

                cleanupGate.countDown()
                assertNull(withTimeout(TEST_TIMEOUT_MILLIS) { releaseWait.await() })
                releaseWait.join()
                assertFalse("cleanup gate unexpectedly timed out", context.cleanupGateTimedOut.get())
                assertEquals(1, binder.unlinkCalls.get())
                assertEquals(1, context.unbindCalls.get())

                val nextClaim = coordinator.claim("after-release-threading")
                assertTrue(coordinator.releaseBeforeRemoteReservation(nextClaim))
            } catch (t: Throwable) {
                failure = t
                throw t
            } finally {
                withContext(NonCancellable) {
                    cleanupGate.countDown()
                    preserveFailure(failure) {
                        ownershipWait?.cancelAndJoin()
                        connect?.cancelAndJoin()
                        connection?.let { ownedConnection ->
                            ownedConnection.releaseBeforeReservation()
                            withTimeout(TEST_TIMEOUT_MILLIS) {
                                ownedConnection.awaitOwnershipReleased()
                            }
                        }
                    }
                }
            }
        }
    }

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

    private class ControlledContext(
        base: Context,
        private val cleanupGate: CountDownLatch,
    ) : ContextWrapper(base) {
        val unbindEntered = CompletableDeferred<Unit>()
        val unbindCalls = AtomicInteger()
        val unbindOnMain = AtomicBoolean()
        val cleanupGateTimedOut = AtomicBoolean()
        private val bindEntered = CompletableDeferred<ServiceConnection>()

        override fun getApplicationContext(): Context = this

        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
            bindEntered.complete(conn)
            return true
        }

        override fun unbindService(conn: ServiceConnection) {
            unbindCalls.incrementAndGet()
            val onMain = Looper.myLooper() == Looper.getMainLooper()
            unbindOnMain.set(onMain)
            unbindEntered.complete(Unit)
            if (!onMain && !cleanupGate.await(CLEANUP_GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                cleanupGateTimedOut.set(true)
            }
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

    private class SyntheticBinder : IBinder by Binder() {
        val linkCalls = AtomicInteger()
        val unlinkCalls = AtomicInteger()
        val unlinkOnMain = AtomicBoolean()

        override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) {
            linkCalls.incrementAndGet()
        }

        override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int): Boolean {
            unlinkCalls.incrementAndGet()
            unlinkOnMain.set(Looper.myLooper() == Looper.getMainLooper())
            return true
        }

        override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            throw AssertionError("release test must not transact with the synthetic Binder")
        }
    }

    private companion object {
        const val TEST_TIMEOUT_MILLIS = 20_000L
        const val CLEANUP_GATE_TIMEOUT_SECONDS = 5L
    }
}
