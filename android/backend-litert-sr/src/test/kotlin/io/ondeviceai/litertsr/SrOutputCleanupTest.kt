package io.ondeviceai.litertsr

import java.io.File
import java.io.IOException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import kotlin.test.*

class SrOutputCleanupTest {
    @Test fun deletesOutputAndOwnerAndRepeatedReleaseSucceeds() = withRoot { root ->
        val directory = root.resolve("request").apply { mkdir() }
        val output = directory.resolve("output.mp4").apply { writeText("video") }

        deleteSrOutput(output, directory)
        deleteSrOutput(output, directory)

        assertFalse(output.exists())
        assertFalse(directory.exists())
        assertTrue(root.isDirectory)
    }

    @Test fun alreadyMissingOutputDoesNotPreventOwnerRemoval() = withRoot { root ->
        val directory = root.resolve("request").apply { mkdir() }
        deleteSrOutput(directory.resolve("output.mp4"), directory)
        assertFalse(directory.exists())
    }

    @Test fun outputFailurePreservesBothErrorsAndAllowsRetry() = withRoot { root ->
        val directory = root.resolve("request").apply { mkdir() }
        val output = directory.resolve("output.mp4").apply { mkdir() }
        val retained = output.resolve("retained").apply { writeText("keep") }

        val failure = assertFailsWith<IOException> { deleteSrOutput(output, directory) }

        assertEquals(output.path, assertIs<DirectoryNotEmptyException>(failure).file)
        assertEquals(directory.path, assertIs<DirectoryNotEmptyException>(failure.suppressed.single()).file)
        assertEquals("keep", retained.readText())
        assertTrue(retained.delete())
        deleteSrOutput(output, directory)
        assertFalse(directory.exists())
    }

    @Test fun ownerFailureDoesNotDeleteOtherEntriesAndAllowsRetry() = withRoot { root ->
        val directory = root.resolve("request").apply { mkdir() }
        val output = directory.resolve("output.mp4").apply { writeText("video") }
        val retained = directory.resolve("retained").apply { writeText("keep") }

        val failure = assertFailsWith<IOException> { deleteSrOutput(output, directory) }

        assertEquals(directory.path, assertIs<DirectoryNotEmptyException>(failure).file)
        assertFalse(output.exists())
        assertEquals("keep", retained.readText())
        assertTrue(failure.suppressed.isEmpty())
        assertTrue(retained.delete())
        deleteSrOutput(output, directory)
        assertFalse(directory.exists())
    }

    @Test fun firstPathFailureDoesNotSkipSecondPath() = withRoot { root ->
        val output = root.resolve("blocked").apply { mkdir() }
        val retained = output.resolve("retained").apply { writeText("keep") }
        val directory = root.resolve("empty").apply { mkdir() }

        assertFailsWith<IOException> { deleteSrOutput(output, directory) }

        assertFalse(directory.exists())
        assertEquals("keep", retained.readText())
    }

    @Test fun cleanupFailureRetainsProcessingFailureAndSuccessfulCleanupDoesNotRethrowIt() = withRoot { root ->
        val directory = root.resolve("request").apply { mkdir() }
        val output = directory.resolve("output.mp4").apply { writeText("video") }
        val retained = directory.resolve("retained").apply { writeText("keep") }
        val processingFailure = IllegalStateException("processing failed")

        val failure = assertFailsWith<IOException> { deleteSrOutput(output, directory, processingFailure) }

        assertSame(processingFailure, failure.suppressed.single())
        assertTrue(retained.delete())
        deleteSrOutput(output, directory, processingFailure)
        assertFalse(directory.exists())
    }

    private fun withRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory("sr-output-cleanup-").toFile()
        try { block(root) } finally { check(root.deleteRecursively()) }
    }
}
