package io.ondeviceai.litertsr

import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Remove only the two owned entries; failures remain visible so release can be retried. */
internal fun deleteSrOutput(output: File, directory: File, processingFailure: Throwable? = null) {
    var failure: IOException? = null
    for (entry in listOf(output, directory)) {
        val error = try {
            Files.deleteIfExists(entry.toPath())
            null
        } catch (error: IOException) { error }
        catch (error: SecurityException) { IOException("SR output cleanup denied", error) }
        if (error != null) {
            if (failure == null) failure = error else failure.addSuppressed(error)
        }
    }
    failure?.let {
        processingFailure?.let(it::addSuppressed)
        throw it
    }
}
