package io.ondeviceai.identity

import io.ondeviceai.core.CapabilityAvailability
import kotlin.test.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class IdentityStopSignalTest {
    @Test fun stopIsIdempotentAndHandlersRunOnceWithStableReason() {
        val signal = IdentityStopSignal()
        val count = AtomicInteger()
        signal.registerStopHandler { count.incrementAndGet() }
        signal.requestStop(IdentityStopReason.CANCELLED)
        signal.requestStop(IdentityStopReason.CLOSED)
        assertEquals(1, count.get())
        assertEquals(IdentityErrorCode.CANCELLED, assertFailsWith<IdentityError> { signal.check() }.code)
    }

    @Test fun lateRegistrationRunsImmediatelyAndClosePreventsUnclaimedCallback() {
        val signal = IdentityStopSignal()
        val closed = AtomicInteger()
        val registration = signal.registerStopHandler { closed.incrementAndGet() }
        registration.close(); registration.close()
        signal.requestStop(IdentityStopReason.AUTHORIZATION_REVOKED)
        assertEquals(0, closed.get())
        val late = AtomicInteger()
        signal.registerStopHandler { late.incrementAndGet() }
        assertEquals(1, late.get())
        assertEquals(IdentityErrorCode.AUTHORIZATION_REVOKED, assertFailsWith<IdentityError> { signal.check() }.code)
    }

    @Test fun callbackCanReenterWithoutDeadlockAndBadHandlerDoesNotBlockOthers() {
        val signal = IdentityStopSignal()
        val called = AtomicInteger()
        signal.registerStopHandler { throw IllegalStateException("fixture") }
        signal.registerStopHandler { signal.check() }
        signal.registerStopHandler { called.incrementAndGet(); signal.registerStopHandler { called.incrementAndGet() } }
        signal.requestStop(IdentityStopReason.CLOSED)
        assertEquals(2, called.get())
    }

    @Test fun fatalErrorsFromCallbacksAndBackendAreNotMasked() {
        val signal = IdentityStopSignal()
        signal.registerStopHandler { throw AssertionError("fatal callback") }
        assertFailsWith<AssertionError> { signal.requestStop(IdentityStopReason.CLOSED) }
        val backend = FatalBackend()
        assertFailsWith<AssertionError> {
            backend.embed(IdentityProbe("fixture", listOf(1f, 2f)), IdentityStopSignal())
        }
    }

    @Test fun concurrentStopRequestsClaimHandlersOnce() {
        val signal = IdentityStopSignal()
        val count = AtomicInteger()
        repeat(50) { signal.registerStopHandler { count.incrementAndGet() } }
        val ready = CountDownLatch(2)
        val done = CountDownLatch(2)
        repeat(2) { Thread { ready.countDown(); ready.await(); signal.requestStop(IdentityStopReason.CANCELLED); done.countDown() }.start() }
        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertEquals(50, count.get())
    }

    @Test fun defaultBackendAdapterChecksBeforeAndAfterAndStopOverridesErrors() {
        val backend = RecordingBackend()
        val stopped = IdentityStopSignal().also { it.requestStop(IdentityStopReason.CANCELLED) }
        assertEquals(0, assertFailsWith<IdentityError> { backend.embed(IdentityProbe("fixture", listOf(1f, 2f)), stopped) }.code.let { backend.calls })
        val signal = IdentityStopSignal()
        backend.error = IdentityError(IdentityErrorCode.BACKEND_UNAVAILABLE)
        assertEquals(IdentityErrorCode.BACKEND_UNAVAILABLE, assertFailsWith<IdentityError> { backend.evaluateQuality(IdentityProbe("fixture", listOf(1f, 2f)), signal) }.code)
        signal.requestStop(IdentityStopReason.CLOSED)
        assertEquals(IdentityErrorCode.CLOSED, assertFailsWith<IdentityError> { backend.embed(IdentityProbe("fixture", listOf(1f, 2f)), signal) }.code)
    }

    @Test fun nativeStyleOverrideCanCooperativelyUnblockInFlightWork() {
        val backend = CooperativeBackend()
        val signal = IdentityStopSignal()
        val result = arrayOfNulls<Throwable>(1)
        val worker = Thread {
            try { backend.embed(IdentityProbe("fixture", listOf(1f, 2f)), signal) }
            catch (error: Throwable) { result[0] = error }
        }
        worker.start()
        assertTrue(backend.entered.await(2, TimeUnit.SECONDS))
        signal.requestStop(IdentityStopReason.CANCELLED)
        worker.join(2000)
        assertEquals(IdentityErrorCode.CANCELLED, (result[0] as IdentityError).code)
    }

    @Test fun defaultAdapterReportsStopAfterBlockedPrimitiveReturns() {
        val backend = BlockingBackend()
        val signal = IdentityStopSignal()
        val result = arrayOfNulls<Throwable>(1)
        val worker = Thread {
            try { backend.embed(IdentityProbe("fixture", listOf(1f, 2f)), signal) }
            catch (error: Throwable) { result[0] = error }
        }
        worker.start()
        assertTrue(backend.entered.await(2, TimeUnit.SECONDS))
        signal.requestStop(IdentityStopReason.AUTHORIZATION_REVOKED)
        backend.release.countDown()
        worker.join(2000)
        assertEquals(IdentityErrorCode.AUTHORIZATION_REVOKED, (result[0] as IdentityError).code)
    }
}

private class RecordingBackend : IdentityBackend {
    private val delegate = FixtureIdentityBackend.system()
    var calls = 0
    var error: IdentityError? = null
    override val backendId get() = delegate.backendId
    override val backendVersion get() = delegate.backendVersion
    override val modelBinding get() = delegate.modelBinding
    override val embeddingSpace get() = delegate.embeddingSpace
    override fun availability(): CapabilityAvailability = delegate.availability()
    override fun embed(probe: IdentityProbe): IdentityEmbedding {
        calls++
        error?.let { throw it }
        return delegate.embed(probe)
    }
    override fun evaluateQuality(probe: IdentityProbe): IdentityQuality {
        error?.let { throw it }
        return delegate.evaluateQuality(probe)
    }
    override fun close() = delegate.close()
}

private class CooperativeBackend : IdentityBackend {
    private val delegate = FixtureIdentityBackend.system()
    val entered = CountDownLatch(1)
    private val release = CountDownLatch(1)
    override val backendId get() = delegate.backendId
    override val backendVersion get() = delegate.backendVersion
    override val modelBinding get() = delegate.modelBinding
    override val embeddingSpace get() = delegate.embeddingSpace
    override fun availability(): CapabilityAvailability = delegate.availability()
    override fun embed(probe: IdentityProbe) = delegate.embed(probe)
    override fun embed(probe: IdentityProbe, stopSignal: IdentityStopSignal): IdentityEmbedding {
        stopSignal.registerStopHandler { release.countDown() }
        entered.countDown()
        release.await(2, TimeUnit.SECONDS)
        stopSignal.check()
        return delegate.embed(probe)
    }
    override fun evaluateQuality(probe: IdentityProbe) = delegate.evaluateQuality(probe)
    override fun close() = delegate.close()
}

private class BlockingBackend : IdentityBackend {
    private val delegate = FixtureIdentityBackend.system()
    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    override val backendId get() = delegate.backendId
    override val backendVersion get() = delegate.backendVersion
    override val modelBinding get() = delegate.modelBinding
    override val embeddingSpace get() = delegate.embeddingSpace
    override fun availability(): CapabilityAvailability = delegate.availability()
    override fun embed(probe: IdentityProbe): IdentityEmbedding {
        entered.countDown()
        release.await(2, TimeUnit.SECONDS)
        return delegate.embed(probe)
    }
    override fun evaluateQuality(probe: IdentityProbe) = delegate.evaluateQuality(probe)
    override fun close() = delegate.close()
}

private class FatalBackend : IdentityBackend {
    private val delegate = FixtureIdentityBackend.system()
    override val backendId get() = delegate.backendId
    override val backendVersion get() = delegate.backendVersion
    override val modelBinding get() = delegate.modelBinding
    override val embeddingSpace get() = delegate.embeddingSpace
    override fun availability(): CapabilityAvailability = delegate.availability()
    override fun embed(probe: IdentityProbe): IdentityEmbedding = throw AssertionError("fatal backend")
    override fun evaluateQuality(probe: IdentityProbe) = delegate.evaluateQuality(probe)
    override fun close() = delegate.close()
}
