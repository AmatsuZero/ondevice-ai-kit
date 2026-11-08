package io.ondeviceai.demo

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VideoInputStoreFailedImportCleanupTest {
    @Test
    fun closeRetriesFailedImportDeletionAfterImportFailsWhileOpen() {
        val parent = Files.createTempDirectory("video-input-failed-import-open").toFile()
        try {
            val unrelated = parent.resolve("unrelated.txt").apply { writeText("keep") }
            val ownedRoot = parent.resolve("owned")
            val store = VideoInputStore(ownedRoot)
            val failure = IllegalStateException("fail import after blocking output deletion")
            var blockedPaths: Pair<File, File>? = null

            val thrown = assertFailsWith<IllegalStateException> {
                store.import(
                    "clip.mp4",
                    open = { byteArrayOf(1, 2, 3).inputStream() },
                    checkpoint = {
                        blockedPaths = replaceCreatedOutputWithBlockingDirectory(ownedRoot)
                        throw failure
                    },
                )
            }

            assertSame(failure, thrown, "the failed import must preserve its checkpoint exception")
            val (ownedPath, sentinel) = checkNotNull(blockedPaths)
            assertTrue(ownedRoot.isDirectory)
            assertTrue(ownedPath.isDirectory, "the failed import must leave the blocking directory")
            assertEquals("keep", sentinel.readText(), "cleanup must not recursively remove its contents")
            assertEquals("keep", unrelated.readText())

            assertTrue(sentinel.delete())
            assertTrue(ownedPath.isDirectory, "leave the empty directory for close to retry")
            store.close()

            assertFalse(ownedPath.exists(), "close must retry deletion of the failed import output")
            assertFalse(ownedRoot.exists(), "close must remove the now-empty owned root")
            store.close()
            assertEquals("keep", unrelated.readText(), "repeated close must preserve an unrelated sibling")
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun repeatedCloseRetriesFailedImportDeletionAfterCloseDuringCheckpoint() {
        val parent = Files.createTempDirectory("video-input-failed-import-closed").toFile()
        try {
            val unrelated = parent.resolve("unrelated.txt").apply { writeText("keep") }
            val ownedRoot = parent.resolve("owned")
            val store = VideoInputStore(ownedRoot)
            val failure = IllegalStateException("fail import after closing store")
            var blockedPaths: Pair<File, File>? = null

            val thrown = assertFailsWith<IllegalStateException> {
                store.import(
                    "clip.mp4",
                    open = { byteArrayOf(4, 5, 6).inputStream() },
                    checkpoint = {
                        blockedPaths = replaceCreatedOutputWithBlockingDirectory(ownedRoot)
                        store.close()
                        throw failure
                    },
                )
            }

            assertSame(failure, thrown, "the failed import must preserve its checkpoint exception")
            val (ownedPath, sentinel) = checkNotNull(blockedPaths)
            assertTrue(ownedRoot.isDirectory)
            assertTrue(ownedPath.isDirectory, "the failed import must leave the blocking directory")
            assertEquals("keep", sentinel.readText(), "cleanup must not recursively remove its contents")
            assertEquals("keep", unrelated.readText())

            assertTrue(sentinel.delete())
            assertTrue(ownedPath.isDirectory, "leave the empty directory for close to retry")
            store.close()

            assertFalse(ownedPath.exists(), "repeated close must retry deletion of the failed import output")
            assertFalse(ownedRoot.exists(), "repeated close must remove the now-empty owned root")
            store.close()
            assertEquals("keep", unrelated.readText(), "repeated close must preserve an unrelated sibling")
        } finally {
            parent.deleteRecursively()
        }
    }

    private fun replaceCreatedOutputWithBlockingDirectory(ownedRoot: File): Pair<File, File> {
        val created = ownedRoot.listFiles().orEmpty()
        assertEquals(1, created.size, "the checkpoint must identify the sole created media path")
        val ownedPath = created.single()
        assertTrue(ownedPath.isFile)
        assertTrue(ownedPath.name.endsWith(".media"))
        assertTrue(ownedPath.delete(), "fixture must replace the exact created output")
        assertTrue(ownedPath.mkdir(), "fixture must block deletion at the exact output path")
        val sentinel = ownedPath.resolve("sentinel").apply { writeText("keep") }
        return ownedPath to sentinel
    }
}
