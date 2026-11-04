package io.ondeviceai.systemasr

import kotlinx.coroutines.*
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** System-service metadata only; INSTALLED does not certify a successful transcription. */
enum class AsrAssetStatus { INSTALLED, PENDING, DOWNLOADABLE, NOT_REPORTED }

class AsrResourceException(val code: Int) : IllegalStateException("ASR resource service error: $code")
class AsrResourceTimeoutException : IllegalStateException("ASR resource service timed out")

internal fun normalizedAsrLocale(locale: String): String {
    val parsed = try { Locale.Builder().setLanguageTag(locale.replace('_', '-')).build() }
        catch (error: java.util.IllformedLocaleException) { throw IllegalArgumentException("Invalid language tag", error) }
    require(parsed.language.isNotBlank() && parsed.language != "und") { "A language tag is required" }
    return parsed.toLanguageTag()
}

internal data class AsrLanguageSupport(
    val installed: List<String>, val pending: List<String>, val supported: List<String>,
) {
    fun status(locale: String): AsrAssetStatus {
        val requested = normalizedAsrLocale(locale)
        fun List<String>.matches() = any {
            runCatching { normalizedAsrLocale(it).equals(requested, ignoreCase = true) }.getOrDefault(false)
        }
        return when {
            installed.matches() -> AsrAssetStatus.INSTALLED
            pending.matches() -> AsrAssetStatus.PENDING
            supported.matches() -> AsrAssetStatus.DOWNLOADABLE
            else -> AsrAssetStatus.NOT_REPORTED
        }
    }
}

internal interface AsrResourceListener {
    fun onSupport(support: AsrLanguageSupport)
    fun onProgress(percent: Int)
    fun onInstalled()
    fun onScheduled()
    fun onError(code: Int)
}

internal interface AsrResourceEngine {
    fun check(locale: String, listener: AsrResourceListener)
    fun download(locale: String, listener: AsrResourceListener)
    fun destroy()
}

/** Every operation owns one recognizer. All operations/callbacks use the owner dispatcher. */
internal class AsrResourceSession(
    private val factory: () -> AsrResourceEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val timeoutMillis: Long = 30_000,
) {
    suspend fun check(locale: String): AsrAssetStatus = run(locale, false) {}
    suspend fun download(locale: String, onProgress: (Int) -> Unit): AsrAssetStatus = run(locale, true, onProgress)

    private suspend fun run(locale: String, download: Boolean, progress: (Int) -> Unit): AsrAssetStatus =
        withContext(dispatcher) {
            val language = normalizedAsrLocale(locale)
            val engine = factory()
            var failure: Throwable? = null
            try {
                withTimeoutOrNull(timeoutMillis) {
                    suspendCancellableCoroutine { continuation ->
                        val listener = object : AsrResourceListener {
                            private fun complete(status: AsrAssetStatus) {
                                if (continuation.isActive) continuation.resume(status)
                            }
                            override fun onSupport(support: AsrLanguageSupport) = complete(support.status(language))
                            override fun onProgress(percent: Int) {
                                if (continuation.isActive) progress(percent.coerceIn(0, 100))
                            }
                            override fun onInstalled() = complete(AsrAssetStatus.INSTALLED)
                            override fun onScheduled() = complete(AsrAssetStatus.PENDING)
                            override fun onError(code: Int) {
                                if (continuation.isActive) continuation.resumeWithException(AsrResourceException(code))
                            }
                        }
                        try {
                            if (download) engine.download(language, listener) else engine.check(language, listener)
                        } catch (error: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                    }
                } ?: throw AsrResourceTimeoutException()
            } catch (error: Throwable) {
                failure = error
                throw error
            } finally {
                withContext(NonCancellable) {
                    try { engine.destroy() } catch (cleanup: Exception) {
                        if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
                    }
                }
            }
        }
}
