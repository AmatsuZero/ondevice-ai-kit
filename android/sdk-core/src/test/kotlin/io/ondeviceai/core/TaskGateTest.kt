package io.ondeviceai.core
import kotlin.test.*
import kotlinx.coroutines.*
class TaskGateTest {
    @Test fun firstTerminalWinsUntilBackendRelease() = runBlocking<Unit> {
        val gate = TaskGate(); gate.accept("one")
        assertFailsWith<BusyException> { gate.accept("two") }
        val wins = (0..99).map { i -> async { gate.finish("one", if (i % 2 == 0) TaskTerminal.COMPLETED else TaskTerminal.CANCELLED) } }.awaitAll().count { it }
        assertEquals(1, wins)
        assertFailsWith<BusyException> { gate.accept("two") }
        gate.release("one"); gate.close(); gate.close()
        assertFailsWith<ClosedException> { gate.accept("two") }
    }
    @Test fun onlyLatestFrameIsKept() = runBlocking<Unit> {
        val released = mutableListOf<Int>(); val queue = LatestFrameQueue<Int> { released += it }
        queue.offer(0); assertEquals(0, queue.next())
        (1..100).forEach { queue.offer(it) }
        assertNull(queue.next()); assertEquals(99, queue.droppedCount)
        queue.close(); assertFalse(0 in released)
        queue.complete(); assertTrue(0 in released); assertTrue(100 in released)
    }
    @Test fun releaseCallbackCanReplacePendingFrameWithoutDoubleRelease() {
        val released=mutableListOf<Int>()
        lateinit var queue:LatestFrameQueue<Int>
        queue=LatestFrameQueue { frame ->
            released+=frame
            if(frame==1 && released.count{it==1}==1) queue.offer(3)
        }
        queue.offer(1);queue.offer(2)
        assertEquals(listOf(1,2),released)
        assertEquals(3,queue.next())
        queue.complete();queue.close()
        assertEquals(listOf(1,2,3),released)
    }
    @Test fun completeAndCloseCommitStateBeforeReleaseCallback() {
        val released=mutableListOf<Int>()
        lateinit var queue:LatestFrameQueue<Int>
        queue=LatestFrameQueue { frame ->
            released+=frame
            if(released.count{it==frame}==1) {queue.complete();queue.close()}
        }
        queue.offer(1);queue.next();queue.offer(2)
        queue.complete();queue.complete();queue.close()
        assertEquals(listOf(1,2),released)
        assertNull(queue.next())
    }
}
