package io.ondeviceai.demo

import java.io.File
import java.io.InputStream
import java.util.UUID

/** A viewer/share intent outlives the Demo's output lease and background cleanup. */
object VideoOutputExport {
    fun snapshot(source: File, directory: File): File {
        check(source.isFile)
        return source.inputStream().use { snapshot(it, directory) }
    }
    fun snapshot(source: InputStream, directory: File): File {
        check(directory.isDirectory || directory.mkdirs())
        // Previously shared copies remain valid for a day, independent of current selection.
        directory.listFiles()?.filter { it.isFile && it.lastModified() < System.currentTimeMillis() - 86_400_000L }?.forEach { it.delete() }
        val output = directory.resolve("${UUID.randomUUID()}.mp4")
        try { output.outputStream().use { source.copyTo(it) } }
        catch (error: Exception) { output.delete(); throw error }
        return output
    }
}
