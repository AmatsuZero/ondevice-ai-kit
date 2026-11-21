package io.ondeviceai.demo

import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class MultimodalInputStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun cancelledImportReclaimsPartialCopy() {
        val directory = temporary.root.resolve("cancelled")
        val store = MultimodalInputStore(directory)
        val source = temporary.newFile().apply { writeBytes(ByteArray(128 * 1024) { 7 }) }
        var checkpoints = 0
        assertFailsWith<java.util.concurrent.CancellationException> {
            store.importImage(source) {
                if (++checkpoints == 3) throw java.util.concurrent.CancellationException()
            }
        }
        assertTrue(checkpoints >= 3)
        assertTrue(directory.listFiles()!!.isEmpty())
        assertEquals(128 * 1024, source.length().toInt())
        store.close()
        assertFalse(directory.exists())
    }

    @Test fun closeDoesNotWaitForBlockedCopyAndCopyCannotPublishAfterClose() {
        val directory = temporary.root.resolve("inflight")
        val store = MultimodalInputStore(directory)
        val source = temporary.newFile().apply { writeText("fixture") }
        val entered = java.util.concurrent.CountDownLatch(1)
        val resume = java.util.concurrent.CountDownLatch(1)
        val existing = store.importImage(source)
        val lease = store.acquire(existing)
        val closed = java.util.concurrent.CountDownLatch(1)
        val outcome = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val worker = Thread {
            try {
                var checkpoints = 0
                store.importImage(source) {
                    if (++checkpoints == 2) {
                        entered.countDown()
                        check(resume.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    }
                }
            } catch (error: Throwable) { outcome.set(error) }
        }
        worker.start()
        var closer: Thread? = null
        try {
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            closer = Thread { try { store.close() } finally { closed.countDown() } }
            closer.start()
            assertTrue(closed.await(2, java.util.concurrent.TimeUnit.SECONDS), "close must not wait for copy")
            assertTrue(directory.exists(), "in-flight copy owns its directory until join")
            assertEquals("fixture", lease.file.readText())
            runBlocking { lease.release() }
            assertFalse(lease.file.exists())
            assertTrue(directory.exists(), "worker, not the released lease, keeps the directory alive")
            assertFails { store.importImage(source) }
        } finally {
            resume.countDown()
            worker.join(5000)
            closer?.join(5000)
        }
        assertFalse(worker.isAlive)
        assertNotNull(outcome.get(), "closed store must reject import publication")
        assertFalse(directory.exists())
        assertEquals("fixture", source.readText())
    }

    @Test fun emptyAndOversizedImportsLeaveNoPartialFiles() {
        val directory = temporary.root.resolve("bounded")
        val store = MultimodalInputStore(directory, maxBytes = 4)
        val source = temporary.newFile("bounded-source")
        assertFails { store.importImage(source) }
        source.writeText("12345")
        assertFails { store.importImage(source) }
        assertTrue(directory.listFiles()!!.isEmpty())
        source.writeText("1234")
        assertNotNull(store.importImage(source))
        store.close()
    }

    @Test fun importedBytesArePrivateAndReferencesAreOpaque() = runBlocking {
        val source = temporary.newFile("source.png").apply { writeText("synthetic-before") }
        val directory = temporary.root.resolve("owned")
        val store = MultimodalInputStore(directory)
        val reference = store.importImage(source)
        assertNotEquals(source.path, reference)
        assertFalse(reference.contains('/'))
        source.writeText("synthetic-after")
        val lease = store.acquire(reference)
        assertEquals(directory.canonicalFile, requireNotNull(lease.file.parentFile).canonicalFile)
        assertEquals("synthetic-before", lease.file.readText())
        lease.release()
        assertTrue(lease.file.exists(), "selected file survives operation release")
        store.remove(reference)
        assertFalse(lease.file.exists())
        assertEquals("synthetic-after", source.readText())
        store.close()
        assertFalse(directory.exists())
    }

    @Test fun retirementAndCloseWaitForEveryIndependentLease() = runBlocking {
        val source = temporary.newFile().apply { writeText("synthetic") }
        val directory = temporary.root.resolve("owned")
        val store = MultimodalInputStore(directory)
        val reference = store.importImage(source)
        val first = store.acquire(reference)
        val second = store.acquire(reference)
        store.remove(reference)
        assertFails { store.acquire(reference) }
        first.release()
        first.release()
        assertTrue(second.file.exists())
        val replacement = store.importImage(source)
        val replacementLease = store.acquire(replacement)
        store.close()
        store.close()
        assertFails { store.acquire(replacement) }
        assertFails { store.importImage(source) }
        assertTrue(second.file.exists())
        assertTrue(replacementLease.file.exists())
        second.release()
        assertFalse(second.file.exists())
        assertTrue(replacementLease.file.exists())
        replacementLease.release()
        replacementLease.release()
        assertFalse(directory.exists())
        assertEquals("synthetic", source.readText())
    }

    @Test fun nonregularSourcesAndUnknownReferencesNeverTouchExternalFiles() {
        val source = temporary.newFile().apply { writeText("external-sentinel") }
        val link = temporary.root.resolve("link.png")
        Files.createSymbolicLink(link.toPath(), source.toPath())
        val directory = temporary.root.resolve("owned")
        val store = MultimodalInputStore(directory)
        assertFails { store.importImage(link) }
        assertFails { store.importImage(temporary.root) }
        assertFails { store.importImage(temporary.root.resolve("missing")) }
        for (reference in listOf(source.path, "../${source.name}", "unknown")) {
            assertFails { store.acquire(reference) }
            store.remove(reference)
        }
        assertEquals("external-sentinel", source.readText())
        assertTrue(directory.listFiles()!!.isEmpty())
        store.close()
        assertEquals("external-sentinel", source.readText())
    }

    @Test fun freshDirectoryRequiredAndForeignFilesSurviveClose() {
        val existing = temporary.newFolder("existing")
        val sentinel = existing.resolve("sentinel").apply { writeText("unowned") }
        assertFails { MultimodalInputStore(existing) }
        assertEquals("unowned", sentinel.readText())
        val directory = temporary.root.resolve("owned")
        val store = MultimodalInputStore(directory)
        val foreign = directory.resolve("foreign").apply { writeText("foreign") }
        store.close()
        store.close()
        assertEquals("foreign", foreign.readText())
        assertTrue(directory.exists())
    }
}
