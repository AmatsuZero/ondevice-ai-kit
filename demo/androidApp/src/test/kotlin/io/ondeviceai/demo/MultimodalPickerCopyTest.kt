package io.ondeviceai.demo

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.test.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class MultimodalPickerCopyTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun streamIsClosedAndPrivateCopyLivesUntilExplicitRelease() {
        val root = temporary.root.resolve("copy")
        var closed = false
        val copy = MultimodalPickerCopy.create(root, "synthetic.png", open = {
            object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
                override fun close() { closed = true; super.close() }
            }
        })
        assertTrue(closed)
        assertEquals("synthetic.png", copy.file.name)
        assertContentEquals(byteArrayOf(1, 2, 3), copy.file.readBytes())
        copy.close(); copy.close()
        assertFalse(root.exists())
    }

    @Test fun overBudgetEmptyAndReadFailureRemovePartialStaging() {
        val opens = listOf<() -> InputStream>(
            { ByteArrayInputStream(ByteArray(5)) },
            { ByteArrayInputStream(byteArrayOf()) },
            { object : InputStream() { override fun read(): Int = throw IOException("private") } },
            { throw IOException("open failed") },
        )
        for ((index, open) in opens.withIndex()) {
            val root = temporary.root.resolve("copy-$index")
            assertFails { MultimodalPickerCopy.create(root, "synthetic.png", maxBytes = 4, open = open) }
            assertFalse(root.exists())
        }
    }

    @Test fun pathLikeDisplayNameAndExistingDirectoriesCannotEscapeOwnership() {
        val foreign = temporary.newFile("sentinel").apply { writeText("foreign") }
        val root = temporary.root.resolve("copy")
        val copy = MultimodalPickerCopy.create(root, "../../sentinel", open = { ByteArrayInputStream(byteArrayOf(7)) })
        assertEquals(root.canonicalFile, requireNotNull(copy.file.parentFile).canonicalFile)
        assertEquals("foreign", foreign.readText())
        assertFails { MultimodalPickerCopy.create(temporary.root, "sentinel", open = { ByteArrayInputStream(byteArrayOf(8)) }) }
        copy.close()
        assertEquals("foreign", foreign.readText())
    }

    @Test fun cancellationCheckpointCleansOwnedBytesWithoutRecursiveDeletion() {
        val root = temporary.root.resolve("copy")
        assertFails { MultimodalPickerCopy.create(root, "synthetic.png", open = { ByteArrayInputStream(byteArrayOf(1)) },
            checkpoint = { throw java.util.concurrent.CancellationException() }) }
        assertFalse(root.exists())
        val copy = MultimodalPickerCopy.create(root, "synthetic.png", open = { ByteArrayInputStream(byteArrayOf(1)) })
        val sentinel = root.resolve("foreign").apply { writeText("unowned") }
        copy.close()
        assertFalse(copy.file.exists())
        assertEquals("unowned", sentinel.readText())
    }
}
