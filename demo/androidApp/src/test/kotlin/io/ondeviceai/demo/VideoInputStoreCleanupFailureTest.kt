package io.ondeviceai.demo

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoInputStoreCleanupFailureTest {
    @Test
    fun repeatedCloseRetriesCleanupAfterFinalLeaseDeletionFails() = runBlocking {
        val parent = Files.createTempDirectory("video-input-close-cleanup").toFile()
        try {
            val unrelated = parent.resolve("unrelated.txt").apply { writeText("keep") }
            val ownedRoot = parent.resolve("owned")
            val store = VideoInputStore(ownedRoot)
            val input = store.import("clip.mp4", open = { byteArrayOf(1, 2, 3).inputStream() })
            val lease = store.acquire(input.reference)

            store.close()
            assertContentEquals(byteArrayOf(1, 2, 3), lease.file.readBytes())
            assertTrue(lease.file.delete(), "fixture must replace the exact leased output")
            assertTrue(lease.file.mkdir(), "fixture must block deletion at the owned output path")
            val sentinel = lease.file.resolve("sentinel").apply { writeText("keep") }

            lease.release()
            lease.release()

            assertTrue(ownedRoot.isDirectory, "failed deletion must not lose the containing root")
            assertTrue(lease.file.isDirectory, "the nonempty blocking directory proves deletion failed")
            assertEquals("keep", sentinel.readText(), "cleanup must not recursively remove unowned contents")

            assertTrue(sentinel.delete())
            assertTrue(lease.file.isDirectory, "leave the empty blocking directory at the owned path")
            store.close()

            assertFalse(lease.file.exists(), "a repeated close must retry deletion of the owned path")
            assertFalse(ownedRoot.exists(), "a repeated close must remove the exact empty root")
            assertEquals("keep", unrelated.readText(), "cleanup must preserve an unrelated sibling")
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun removeRetainsOwnershipForRetryAfterFinalLeaseDeletionFails() = runBlocking {
        val parent = Files.createTempDirectory("video-input-remove-cleanup").toFile()
        try {
            val unrelated = parent.resolve("unrelated.txt").apply { writeText("keep") }
            val ownedRoot = parent.resolve("owned")
            val store = VideoInputStore(ownedRoot)
            val input = store.import("clip.mp4", open = { byteArrayOf(4, 5, 6).inputStream() })
            val lease = store.acquire(input.reference)

            store.remove(input.reference)
            assertContentEquals(byteArrayOf(4, 5, 6), lease.file.readBytes())
            assertTrue(lease.file.delete(), "fixture must replace the exact leased output")
            assertTrue(lease.file.mkdir(), "fixture must block deletion at the owned output path")
            val sentinel = lease.file.resolve("sentinel").apply { writeText("keep") }

            lease.release()
            lease.release()

            assertTrue(ownedRoot.isDirectory, "failed deletion must leave the owned root available")
            assertTrue(lease.file.isDirectory, "the nonempty blocking directory proves deletion failed")
            assertEquals("keep", sentinel.readText(), "cleanup must not recursively remove unowned contents")

            assertTrue(sentinel.delete())
            assertTrue(lease.file.isDirectory, "leave the empty blocking directory at the owned path")
            store.remove(input.reference)

            assertFalse(lease.file.exists(), "a later remove must retry deletion of the owned path")
            assertTrue(ownedRoot.isDirectory, "an open store retains its empty root until close")
            store.close()

            assertFalse(ownedRoot.exists(), "close must remove the exact empty owned root")
            assertEquals("keep", unrelated.readText(), "cleanup must preserve an unrelated sibling")
        } finally {
            parent.deleteRecursively()
        }
    }
}
