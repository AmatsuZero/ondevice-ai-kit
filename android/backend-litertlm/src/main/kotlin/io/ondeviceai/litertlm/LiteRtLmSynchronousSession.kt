package io.ondeviceai.litertlm

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Blocking native boundary; a normal return, unlike an exception, joins this send. */
internal interface LiteRtLmSynchronousEngine {
    fun generate(image: File, prompt: String, maxOutputTokens: Int): String
    fun close()
}

/** Cancellation discards output and waits for the blocking sender before releasing ownership. */
internal class LiteRtLmSynchronousSession(
    private val engine: LiteRtLmSynchronousEngine,
) : LiteRtLmSession {
    private enum class State { READY, SENDING, RETURNED, FAILED, CLOSED }
    private val lock = Any()
    private var state = State.READY
    override val callbackMode = LiteRtLmCallbackMode.CUMULATIVE

    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
        onSnapshot: (String) -> Unit): String = withContext(Dispatchers.IO) {
        synchronized(lock) { check(state == State.READY); state = State.SENDING }
        val text = try {
            engine.generate(image, prompt, maxOutputTokens).also {
                synchronized(lock) { state = State.RETURNED }
            }
        } catch (failure: Throwable) {
            synchronized(lock) { state = State.FAILED }
            throw failure
        }
        onSnapshot(text)
        text
    }

    // No asynchronous generation remains after a successful blocking call.
    // Cancellation during that call is logical; do not race native close/cancel with send.
    override fun cancelProcess() = Unit

    override suspend fun awaitTerminated() {
        synchronized(lock) {
            if (state == State.SENDING || state == State.FAILED) throw LiteRtLmError.DrainFailure()
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        val close = synchronized(lock) {
            when (state) {
                State.SENDING, State.FAILED -> throw LiteRtLmError.DrainFailure()
                State.CLOSED -> false
                else -> { state = State.CLOSED; true }
            }
        }
        if (close) engine.close()
    }
}
