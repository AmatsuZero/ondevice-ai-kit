package io.ondeviceai.demo

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** A fixed flat ZIP. ModelStore performs SHA-256 verification before installation. */
internal object IdentityModelBundle {
    fun unpack(input: InputStream, destination: File, expected: Map<String, Long>, checkpoint: () -> Unit) {
        val seen = mutableSetOf<String>()
        ZipInputStream(input).use { zip ->
            while (true) {
                checkpoint()
                val entry = zip.nextEntry ?: break
                val name = entry.name
                val length = expected[name]
                require(!entry.isDirectory && length != null && seen.add(name))
                require(name.matches(Regex("[A-Za-z0-9_.-]+")) && name != "." && name != "..")
                File(destination, name).outputStream().use { output ->
                    var copied = 0L
                    val buffer = ByteArray(65_536)
                    while (true) {
                        checkpoint()
                        val count = zip.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= length)
                        output.write(buffer, 0, count)
                    }
                    require(copied == length)
                }
            }
        }
        require(seen == expected.keys)
    }
}
