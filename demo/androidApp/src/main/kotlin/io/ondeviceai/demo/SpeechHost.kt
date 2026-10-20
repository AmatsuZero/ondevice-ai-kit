package io.ondeviceai.demo

import io.ondeviceai.binding.*
import io.ondeviceai.speech.*
import kotlinx.coroutines.*

/** Owns initialization and cleanup independently of the Activity's cancelled lifecycle Job. */
class SpeechHost(
    scope: CoroutineScope,
    private val createTts: suspend () -> SpeechSynthesisBackend?,
    private val createAsr: () -> SpeechRecognitionBackend?,
    private val hasPermission: () -> Boolean,
    private val requestPermission: ((Boolean) -> Unit) -> Unit,
    private val openSettings: () -> Unit,
) : SpeechBridge, SpeechDemoActions {
    private val ownerJob = SupervisorJob()
    private val owner = CoroutineScope(scope.coroutineContext.minusKey(Job) + ownerJob)
    private var adapter: SpeechBridge = UnavailableSpeechBridge("initializing")
    val model = SpeechViewModel(this)
    private var closed = false
    private var foreground = true
    private var permission: PermissionTask? = null
    private val closeWaiters = mutableListOf<SpeechBridgeCloseCallback>()
    private var closing = false
    private var closeResult: SpeechBridgeFailure? = null
    private var closeFinished = false
    private val initialization = owner.launch {
        val ttsBackend = try { createTts() } catch (_: Exception) { null }
        if (closed) { ttsBackend?.close(); return@launch }
        val asrBackend = try { createAsr() } catch (_: Exception) { null }
        adapter = AndroidSpeechBridge(
            tts = ttsBackend?.let { SpeechSynthesisClient(it.backendId, listOf(it)) },
            asr = asrBackend?.let { SpeechRecognitionClient(it.backendId, listOf(it)) }, scope = owner)
        model.refreshBackends(SpeechBridgeCapability.TTS)
        model.refreshBackends(SpeechBridgeCapability.ASR)
    }

    fun onForeground() { if (!closed) foreground = true }
    fun onBackground() { foreground = false; permission?.cancel(); model.background() }
    fun close() = model.close()
    override fun selectAudioFile() = Unit
    override fun clearAudioFile() = Unit
    override fun openSpeechSettings() { if (!closed) openSettings() }
    override fun leavePage() { permission?.cancel(); model.leavePage() }

    override fun queryBackends(capability: SpeechBridgeCapability, callback: SpeechBridgeBackendsCallback): BridgeTask {
        if (closed) { callback.onFailure(failure(SpeechBridgeFailureCode.CLOSED)); return CompletedTask }
        return adapter.queryBackends(capability, callback)
    }
    override fun speak(request: SpeechBridgeTtsRequest, selection: SpeechBridgeSelection, callback: SpeechBridgeTtsCallback): BridgeTask {
        if (closed) { callback.onFailure(failure(SpeechBridgeFailureCode.CLOSED)); return CompletedTask }
        if (permission != null) { callback.onFailure(failure(SpeechBridgeFailureCode.BUSY)); return CompletedTask }
        return adapter.speak(request, selection, callback)
    }
    override fun recognize(request: SpeechBridgeAsrRequest, selection: SpeechBridgeSelection, callback: SpeechBridgeAsrCallback): BridgeTask {
        if (closed) { callback.onFailure(failure(SpeechBridgeFailureCode.CLOSED)); return CompletedTask }
        if (permission != null) { callback.onFailure(failure(SpeechBridgeFailureCode.BUSY)); return CompletedTask }
        if (!foreground) { callback.onCancelled(); return CompletedTask }
        if (request.input !is SpeechBridgeInput.Microphone) {
            callback.onFailure(failure(SpeechBridgeFailureCode.UNSUPPORTED_INPUT)); return CompletedTask
        }
        if (hasPermission()) return adapter.recognize(request, selection, callback)
        val task = PermissionTask { callback.onCancelled() }
        permission = task
        requestPermission { granted ->
            if (permission !== task) return@requestPermission
            permission = null
            task.pending = null
            if (closed || !foreground) callback.onCancelled()
            else if (!granted) callback.onFailure(failure(SpeechBridgeFailureCode.BACKEND_UNAVAILABLE, "PERMISSION_DENIED"))
            else task.native = adapter.recognize(request, selection, callback)
        }
        return task
    }
    override fun close(callback: SpeechBridgeCloseCallback) {
        if (closeFinished) { deliverClose(callback); return }
        closeWaiters += callback
        closed = true
        permission?.cancel()
        if (closing) return
        closing = true
        owner.launch {
            initialization.join()
            adapter.close(object : SpeechBridgeCloseCallback {
                override fun onClosed() = finishClose(null)
                override fun onFailure(failure: SpeechBridgeFailure) = finishClose(failure)
            })
        }
    }
    private fun finishClose(failure: SpeechBridgeFailure?) {
        closeResult = failure; closeFinished = true
        val waiters = closeWaiters.toList(); closeWaiters.clear()
        ownerJob.complete()
        waiters.forEach(::deliverClose)
    }
    private fun deliverClose(callback: SpeechBridgeCloseCallback) {
        closeResult?.let { callback.onFailure(it) } ?: callback.onClosed()
    }
    private inner class PermissionTask(var pending: (() -> Unit)?) : BridgeTask {
        var native: BridgeTask? = null
        override fun cancel() {
            native?.let { it.cancel(); return }
            if (permission === this) permission = null
            val action = pending; pending = null; action?.invoke()
        }
    }
    private object CompletedTask : BridgeTask { override fun cancel() = Unit }
    private fun failure(code: SpeechBridgeFailureCode, reason: String? = null) = SpeechBridgeFailure(code, reason)
}
