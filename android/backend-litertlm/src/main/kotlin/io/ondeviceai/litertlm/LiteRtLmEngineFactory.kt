package io.ondeviceai.litertlm

import java.io.File

/** Native ownership stays behind this seam; tests never load the AAR on a host JVM. */
internal fun interface LiteRtLmEngineFactory {
    suspend fun open(modelPath: File, config: LiteRtLmEngineConfig): LiteRtLmSession
}

internal enum class LiteRtLmCallbackMode { CUMULATIVE, DELTA }

internal interface LiteRtLmSession {
    /** The mode must be established by a real device probe before production construction. */
    val callbackMode: LiteRtLmCallbackMode
    suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
        onSnapshot: (String) -> Unit): String
    fun cancelProcess()
    /** Abstract tested drain barrier; 0.18.1 isAlive is NOT a production implementation of it. */
    suspend fun awaitTerminated()
    /** Closes conversation, then engine, exactly once after a successful drain. */
    suspend fun close()
}
