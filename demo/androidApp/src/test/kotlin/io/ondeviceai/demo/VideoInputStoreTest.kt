package io.ondeviceai.demo

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class VideoInputStoreTest {
    @Test fun importedBytesRemainUntilLastLeaseAfterRemovalAndClose() = runBlocking {
        val root = Files.createTempDirectory("video-input").toFile()
        try {
            val store = VideoInputStore(root.resolve("owned"))
            val input = store.import("clip.mp4", open = { byteArrayOf(1, 2, 3).inputStream() })
            val lease = store.acquire(input.reference)
            assertContentEquals(byteArrayOf(1, 2, 3), lease.file.readBytes())
            assertEquals("clip.mp4", input.displayName)
            store.remove(input.reference); store.close()
            assertTrue(lease.file.exists())
            assertFails { store.acquire(input.reference) }
            lease.release(); lease.release()
            assertFalse(lease.file.exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun oversizeAndCancelledImportsLeaveNoPublishedFiles() {
        val root = Files.createTempDirectory("video-input-failure").toFile()
        try {
            val store = VideoInputStore(root.resolve("owned"), maxBytes = 2)
            assertFails { store.import("big.mp4", open = { byteArrayOf(1, 2, 3).inputStream() }) }
            assertTrue(root.resolve("owned").listFiles().orEmpty().isEmpty())
            assertFails { store.import("cancel.mp4", open = { byteArrayOf(1).inputStream() }, checkpoint = { error("cancel") }) }
            assertTrue(root.resolve("owned").listFiles().orEmpty().isEmpty())
            store.close()
        } finally { root.deleteRecursively() }
    }
}
