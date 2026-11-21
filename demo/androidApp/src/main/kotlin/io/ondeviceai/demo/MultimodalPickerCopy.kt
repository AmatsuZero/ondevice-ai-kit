package io.ondeviceai.demo

import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Bounded staging copy for a picker result; the bridge receives only the later opaque store token. */
class MultimodalPickerCopy private constructor(val file: File) : AutoCloseable {
    private var closed = false

    override fun close() {
        if (closed) return
        closed = true
        file.delete()
        val directory = file.parentFile
        if (directory != null && directory.listFiles()?.isEmpty() == true) directory.delete()
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 32L * 1024L * 1024L

        fun create(directory: File, displayName: String, maxBytes: Long = DEFAULT_MAX_BYTES,
                   open: () -> InputStream, checkpoint: (Long) -> Unit = {}): MultimodalPickerCopy {
            require(maxBytes > 0) { "maxBytes must be positive" }
            check(!directory.exists()) { "staging directory must be fresh" }
            check(directory.mkdir()) { "cannot create staging directory" }
            val safeName = displayName.substringAfterLast('/').substringAfterLast('\\')
                .filterNot(Char::isISOControl).take(160).let { if (it.isBlank() || it == "." || it == "..") "image" else it }
            val file = directory.resolve(safeName)
            var complete = false
            try {
                open().use { input ->
                    file.outputStream().use { output ->
                        copyBounded(input, output, maxBytes, checkpoint)
                    }
                }
                complete = true
                return MultimodalPickerCopy(file)
            } finally {
                if (!complete) {
                    file.delete()
                    directory.delete()
                }
            }
        }

        private fun copyBounded(input: InputStream, output: OutputStream, maxBytes: Long,
                                checkpoint: (Long) -> Unit) {
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                check(total <= maxBytes) { "picker input exceeds $maxBytes bytes" }
                output.write(buffer, 0, count)
                checkpoint(total)
            }
            check(total > 0) { "picker input is empty" }
        }
    }
}
