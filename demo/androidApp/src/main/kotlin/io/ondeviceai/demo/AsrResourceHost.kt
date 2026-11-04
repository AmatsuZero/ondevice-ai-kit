package io.ondeviceai.demo

import androidx.compose.runtime.*
import io.ondeviceai.speech.SpeechBackendUnavailableException
import io.ondeviceai.systemasr.*
import kotlinx.coroutines.*

/** UI-thread-owned resource controls. Download is admitted only for the last checked locale. */
class AsrResourceHost(
    private val scope: CoroutineScope,
    private val canDownload: Boolean,
    private val query: suspend (String) -> AsrAssetStatus,
    private val requestDownload: suspend (String, (Int) -> Unit) -> AsrAssetStatus,
    private val canStart: () -> Boolean = { true },
) : SpeechAsrResources {
    override var state by mutableStateOf(SpeechAsrResourceState()); private set
    private var operation: Job? = null
    private var closed = false

    override fun check(locale: String) = start(locale, download = false)
    override fun download(locale: String) {
        if (state.locale != locale || !state.canDownload) return
        start(locale, download = true)
    }

    private fun start(locale: String, download: Boolean) {
        if (closed || !scope.isActive || state.busy || !canStart() || locale.isBlank()) return
        state = SpeechAsrResourceState(locale, if (download) "DOWNLOADING" else "CHECKING", busy = true)
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val context = currentCoroutineContext()
                val result = if (download) requestDownload(locale) { percent ->
                    if (context.isActive && !closed)
                        state = state.copy(progress = percent.coerceIn(0, 100))
                } else query(locale)
                state = state.copy(status = result.name,
                    canDownload = canDownload && result == AsrAssetStatus.DOWNLOADABLE, progress = null)
            } catch (error: CancellationException) {
                state = state.copy(status = "MONITOR_CANCELLED", canDownload = false, progress = null)
                throw error
            } catch (error: AsrResourceException) {
                state = state.copy(status = if (download) "DOWNLOAD_ERROR" else "QUERY_ERROR", platformCode = error.code)
            } catch (error: SpeechBackendUnavailableException) {
                state = state.copy(status = error.reason.name)
            } catch (_: AsrResourceTimeoutException) {
                state = state.copy(status = "MONITOR_TIMEOUT")
            } catch (_: IllegalArgumentException) {
                state = state.copy(status = "INVALID_LOCALE")
            } catch (_: Exception) {
                state = state.copy(status = "PLATFORM_ERROR")
            } finally {
                state = state.copy(busy = false)
                operation = null
            }
        }
        if (job.isActive) operation = job
    }

    override fun cancel() { operation?.cancel() }
    suspend fun close() {
        closed = true
        operation?.cancelAndJoin()
    }
}
