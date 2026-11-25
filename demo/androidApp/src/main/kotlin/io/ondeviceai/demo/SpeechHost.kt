package io.ondeviceai.demo

import io.ondeviceai.binding.*
import io.ondeviceai.speech.*
import kotlinx.coroutines.*

/** One system prompt, with its result delivered to the latest admitted logical retry. */
internal class SpeechPermissionGate(private val launch: () -> Unit) {
    private var inFlight = false
    private var reply: ((Boolean) -> Unit)? = null
    fun request(callback: (Boolean) -> Unit) {
        reply = callback
        if (!inFlight) { inFlight = true; launch() }
    }
    fun complete(granted: Boolean) {
        inFlight = false
        val callback = reply; reply = null
        callback?.invoke(granted)
    }
    fun clear() { reply = null }
}

/** Owns initialization and cleanup independently of the Activity's cancelled lifecycle Job. */
class SpeechHost(
    scope: CoroutineScope,
    private val createTts: suspend () -> SpeechSynthesisBackend?,
    private val createAsr: () -> SpeechRecognitionBackend?,
    private val hasPermission: () -> Boolean,
    private val requestPermission: ((Boolean) -> Unit) -> Unit,
    private val openSettings: () -> Unit,
    override val asrResources: AsrResourceHost? = null,
    private val createAdditionalAsr: suspend () -> List<SpeechRecognitionBackend> = { emptyList() },
    private val defaultAsrBackendId: String? = null,
    private val launchAudioPicker: (((String?, String?, Boolean) -> Unit) -> Unit)? = null,
) : SpeechBridge, SpeechDemoActions, SpeechAsrResourceProvider {
    private val ownerJob = SupervisorJob()
    private val owner = CoroutineScope(scope.coroutineContext.minusKey(Job) + ownerJob)
    private var adapter: SpeechBridge = UnavailableSpeechBridge("initializing")
    val model = SpeechViewModel(this)
    private var closed = false
    private var foreground = true
    private var permission: PermissionTask? = null
    private var fileBackends: List<AndroidSpeechFileBackend> = emptyList()
    private var pendingAudioPicker: ((String?, String?, Boolean) -> Unit)? = null
    private val closeWaiters = mutableListOf<SpeechBridgeCloseCallback>()
    private var closing = false
    private var closeResult: SpeechBridgeFailure? = null
    private var closeFinished = false
    private val initializationFailures = mutableMapOf<SpeechBridgeCapability, SpeechBridgeFailure>()
    private val initialization = owner.launch {
        val ttsBackend = try { createTts() } catch (error: CancellationException) { throw error }
            catch (_: Exception) { recordInitializationFailure(SpeechBridgeCapability.TTS); null }
        if (closed) { ttsBackend?.close(); return@launch }
        val asrBackend = try { createAsr() } catch (error: CancellationException) { throw error }
            catch (_: Exception) { recordInitializationFailure(SpeechBridgeCapability.ASR); null }
        val asrBackends = buildList {
            asrBackend?.let(::add)
            try { addAll(createAdditionalAsr()) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { recordInitializationFailure(SpeechBridgeCapability.ASR) }
        }
        if (closed) { ttsBackend?.close(); asrBackends.forEach { it.close() }; return@launch }
        val selectedDefault = defaultAsrBackendId ?: asrBackends.firstOrNull()?.backendId
        val asrClient = if (asrBackends.any { it.backendId == selectedDefault }) {
            SpeechRecognitionClient(selectedDefault!!, asrBackends)
        } else {
            if (selectedDefault != null) recordInitializationFailure(SpeechBridgeCapability.ASR)
            asrBackends.forEach { it.close() }
            null
        }
        fileBackends = asrBackends.filterIsInstance<AndroidSpeechFileBackend>()
        adapter = AndroidSpeechBridge(
            tts = ttsBackend?.let { SpeechSynthesisClient(it.backendId, listOf(it)) },
            asr = asrClient,
            scope = owner,
            fileBackends = fileBackends)
        model.refreshBackends(SpeechBridgeCapability.TTS)
        model.refreshBackends(SpeechBridgeCapability.ASR)
    }

    fun onForeground() { if (!closed) foreground = true }
    val canManageAsrResources: Boolean get() = !closed && foreground && permission == null && model.state.controlsEnabled
    fun onBackground() { foreground = false; asrResources?.cancel(); permission?.cancel(); model.background() }
    fun close() {
        pendingAudioPicker = null
        model.close()
    }
    override fun selectAudioFile() {
        if (closed || pendingAudioPicker != null) return
        if (!foreground || permission != null || asrResources?.state?.busy == true ||
            !model.state.controlsEnabled
        ) {
            model.inputFailed()
            return
        }
        val launch = launchAudioPicker
        if (launch == null) {
            model.inputFailed()
            return
        }
        lateinit var reply: (String?, String?, Boolean) -> Unit
        reply = pickerReply@{ reference, displayName, failed ->
            if (pendingAudioPicker !== reply) return@pickerReply
            pendingAudioPicker = null
            if (failed) {
                model.inputFailed()
                return@pickerReply
            }
            if (reference == null) return@pickerReply
            if (closed || permission != null || asrResources?.state?.busy == true ||
                !model.state.controlsEnabled || !reference.startsWith("content://")
            ) {
                model.inputFailed()
                return@pickerReply
            }
            model.selectAudio(reference, displayName)
        }
        pendingAudioPicker = reply
        try {
            launch(reply)
        } catch (_: Exception) {
            if (pendingAudioPicker === reply) pendingAudioPicker = null
            model.inputFailed()
        }
    }
    override fun clearAudioFile() {
        pendingAudioPicker = null
        model.selectAudio(null, null)
    }
    override fun openSpeechSettings() { if (!closed) openSettings() }
    override fun leavePage() {
        pendingAudioPicker = null
        asrResources?.cancel()
        permission?.cancel()
        model.leavePage()
    }

    override fun queryBackends(capability: SpeechBridgeCapability, callback: SpeechBridgeBackendsCallback): BridgeTask {
        if (closed) { callback.onFailure(failure(SpeechBridgeFailureCode.CLOSED)); return CompletedTask }
        initializationFailures[capability]?.let { callback.onFailure(it); return CompletedTask }
        return adapter.queryBackends(capability, callback)
    }
    override fun speak(request: SpeechBridgeTtsRequest, selection: SpeechBridgeSelection, callback: SpeechBridgeTtsCallback): BridgeTask {
        if (closed) { callback.onFailure(failure(SpeechBridgeFailureCode.CLOSED)); return CompletedTask }
        initializationFailures[SpeechBridgeCapability.TTS]?.let { callback.onFailure(it); return CompletedTask }
        if (permission != null) { callback.onFailure(failure(SpeechBridgeFailureCode.BUSY)); return CompletedTask }
        return adapter.speak(request, selection, callback)
    }
    override fun recognize(request: SpeechBridgeAsrRequest, selection: SpeechBridgeSelection, callback: SpeechBridgeAsrCallback): BridgeTask {
        if (closed) { callback.onFailure(failure(SpeechBridgeFailureCode.CLOSED)); return CompletedTask }
        if (asrResources?.state?.busy == true) { callback.onFailure(failure(SpeechBridgeFailureCode.BUSY)); return CompletedTask }
        initializationFailures[SpeechBridgeCapability.ASR]?.let { callback.onFailure(it); return CompletedTask }
        if (permission != null) { callback.onFailure(failure(SpeechBridgeFailureCode.BUSY)); return CompletedTask }
        if (!foreground) { callback.onCancelled(); return CompletedTask }
        when (val input = request.input) {
            is SpeechBridgeInput.AudioFile -> {
                if (fileBackends.isEmpty()) {
                    callback.onFailure(failure(SpeechBridgeFailureCode.UNSUPPORTED_INPUT))
                    return CompletedTask
                }
                if (input.reference.isBlank() || input.reference != model.state.audioReference) {
                    callback.onFailure(failure(SpeechBridgeFailureCode.INVALID_INPUT))
                    return CompletedTask
                }
                return adapter.recognize(request, selection, callback)
            }
            SpeechBridgeInput.Microphone -> Unit
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
        pendingAudioPicker = null
        closed = true
        permission?.cancel()
        if (closing) return
        closing = true
        owner.launch {
            asrResources?.close()
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
    private fun recordInitializationFailure(capability: SpeechBridgeCapability) {
        initializationFailures[capability] = SpeechBridgeFailure(SpeechBridgeFailureCode.PLATFORM, null,
            domain = "demo.speech.initialization", platformCode = 1)
    }
    private fun failure(code: SpeechBridgeFailureCode, reason: String? = null) = SpeechBridgeFailure(code, reason)
}
