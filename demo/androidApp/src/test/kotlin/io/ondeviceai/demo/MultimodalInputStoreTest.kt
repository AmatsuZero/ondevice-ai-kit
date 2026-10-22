package io.ondeviceai.demo

import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class MultimodalInputStoreTest {
    @get:Rule val temporary = TemporaryFolder()

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
