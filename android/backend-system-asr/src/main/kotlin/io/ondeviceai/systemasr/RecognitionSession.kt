package io.ondeviceai.systemasr

import io.ondeviceai.speech.SpeechRecognitionRequest
import io.ondeviceai.speech.SpeechRecognitionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resumeWithException

internal interface RecognitionEngineListener {
    fun onResult(transcript: String, confidence: Float?)
    fun onError(code: Int)
}

internal interface RecognitionEngine {
    fun start(locale: String)
    fun cancel()
    fun destroy()
}

internal fun interface RecognitionEngineFactory {
    fun create(listener: RecognitionEngineListener): RecognitionEngine
}

class AsrBusyException : IllegalStateException("asr backend busy")
class AsrNoResultException : IllegalStateException("speech recognizer returned no result")
class AsrRecognitionException(val code: Int) : IllegalStateException("speech recognizer failed: $code")
class AsrTimeoutException : IllegalStateException("speech recognition timed out")

/** Main-thread-owned session controller. Engine callbacks are ignored after their task token is retired. */
internal class RecognitionSession(
    private val factory: RecognitionEngineFactory,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Main.immediate,
    private val backendId: String = "android.speech.recognizer"
) {
    private var closed = false
    private var active: Active? = null

    private class Active(
        val id: String,
        val engine: RecognitionEngine,
        val continuation: kotlinx.coroutines.CancellableContinuation<SpeechRecognitionResult>
    )

    suspend fun recognize(request: SpeechRecognitionRequest): SpeechRecognitionResult = withContext(dispatcher) {
        val id = UUID.randomUUID().toString()
        var failure: Throwable? = null
        try {
            val result = withTimeoutOrNull(timeoutMillis) {
                suspendCancellableCoroutine { continuation ->
                    if (closed) {
                        continuation.resumeWithException(io.ondeviceai.core.ClosedException())
                        return@suspendCancellableCoroutine
                    }
                    if (active != null) {
                        continuation.resumeWithException(AsrBusyException())
                        return@suspendCancellableCoroutine
                    }
                    val listener = object : RecognitionEngineListener {
                        override fun onResult(transcript: String, confidence: Float?) {
                            complete(id, if (transcript.isBlank()) AsrNoResultException() else null) {
                                SpeechRecognitionResult(backendId, transcript, confidence)
                            }
                        }
                        override fun onError(code: Int) { complete(id, AsrRecognitionException(code), null) }
                    }
                    val engine = try {
                        factory.create(listener)
                    } catch (error: Throwable) {
                        continuation.resumeWithException(error)
                        return@suspendCancellableCoroutine
                    }
                    active = Active(id, engine, continuation)
                    try {
                        engine.start(request.locale)
                    } catch (error: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            }
            if (result == null) throw AsrTimeoutException()
            result
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            // This is non-cancellable and confined to the owner dispatcher, so cancellation,
            // timeout, start errors, and normal completion all retire exactly their own engine.
            withContext(NonCancellable) {
                retire(id, cancelEngine = true)?.let { cleanupError ->
                    if (failure != null) failure.addSuppressed(cleanupError)
                    else throw cleanupError
                }
            }
        }
    }

    suspend fun close(): Unit = withContext(dispatcher + NonCancellable) {
        if (closed) return@withContext
        closed = true
        val current = active ?: return@withContext
        val cleanupError = retire(current.id, cancelEngine = true)
        if (current.continuation.isActive) {
            val error = io.ondeviceai.core.ClosedException()
            cleanupError?.let(error::addSuppressed)
            current.continuation.resumeWithException(error)
        }
        cleanupError?.let { throw it }
    }

    private fun complete(id: String, error: Throwable?, result: (() -> SpeechRecognitionResult)?) {
        val current = active?.takeIf { it.id == id } ?: return
        val cleanupError = retire(id, cancelEngine = false)
        if (!current.continuation.isActive) return
        if (error != null) {
            cleanupError?.let(error::addSuppressed)
            current.continuation.resumeWithException(error)
        } else if (cleanupError != null) {
            current.continuation.resumeWithException(cleanupError)
        }
        else current.continuation.resume(result!!.invoke(), onCancellation = { _, _, _ -> })
    }

    private fun retire(id: String, cancelEngine: Boolean): Throwable? {
        val current = active?.takeIf { it.id == id } ?: return null
        active = null
        var failure: Throwable? = null
        if (cancelEngine) runCatching { current.engine.cancel() }.onFailure { failure = it }
        runCatching { current.engine.destroy() }.onFailure { error ->
            val previous = failure
            if (previous == null) failure = error else previous.addSuppressed(error)
        }
        return failure
    }

    companion object { const val DEFAULT_TIMEOUT_MILLIS = 30_000L }
}
