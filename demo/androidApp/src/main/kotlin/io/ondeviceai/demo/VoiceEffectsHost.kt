package io.ondeviceai.demo

import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import io.ondeviceai.binding.*
import io.ondeviceai.voiceeffects.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

/** Reusable platform audio owner for the unified CMP page. */
internal class VoiceEffectsHost(private val activity: ComponentActivity) : VoiceEffectsDemoActions, LiveVoiceOwner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val liveAudio: RealtimeVoiceAudio = RealtimeVoiceAudio(
        activity.applicationContext,
        Handler(Looper.getMainLooper()),
        object : RealtimeVoiceAudio.Events {
            override fun onBeforeStart(): Boolean =
                foreground && !destroyed && model.state.controlsEnabled &&
                    recorder == null && capture == null && player == null &&
                    exportReference == null && !copyingExport
            override fun onPermissionNeeded(id: Long) {
                pendingLiveId = id
                livePermission.launch(Manifest.permission.RECORD_AUDIO)
            }
            override fun onReady(id: Long) { liveSession.ready(id) }
            override fun onEnded(id: Long, message: String) { liveSession.ended(id, message) }
            override fun onRouteLabel(label: String, headphonesAvailable: Boolean) {
                liveRouteLabel = label
                liveHeadphonesAvailable = headphonesAvailable
                liveSession.route(if (headphonesAvailable) LiveDestination.HEADPHONES else LiveDestination.BUILTIN_SPEAKER)
            }
        },
    )
    override val liveSession: RealtimeVoiceSession = RealtimeVoiceSession(liveAudio)
    var liveRouteLabel: String = "内置扬声器"
        private set
    var liveHeadphonesAvailable: Boolean = false
        private set
    private var pendingLiveId: Long = RealtimeVoiceAudio.IDLE
    private val livePermission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingLiveId = RealtimeVoiceAudio.IDLE
        // Permission callbacks never auto-start a live session; the user presses again.
        if (!granted && foreground && !destroyed) liveSession.refresh()
    }
    private lateinit var bridge: AndroidVoiceEffectsBridge
    lateinit var model: VoiceEffectsViewModel
        private set
    private val inputs = mutableMapOf<String, File>()
    private lateinit var inputProvider: AndroidVoiceEffectsInputProvider
    private val leased = mutableMapOf<String, Int>()
    private val retired = mutableSetOf<String>()
    private var destroyed = false
    private var foreground = false
    private var recorder: AudioRecord? = null
    private var capture: Job? = null
    private val capturing = AtomicBoolean(false)
    private var discardRecording = false
    private var player: MediaPlayer? = null
    private var playingReference: String? = null
    private val timbres: AndroidOpenVoiceTimbres
    private var focus: AudioFocusRequest? = null
    private var exportReference: String? = null
    private var copyingExport = false
    private data class InputRequest(val ticket: Long, val referenceSlot: Boolean, val requiresHalfSecond: Boolean)

    private var inputTicket = 0L
    private var waveGeneration = 0L
    private var pendingRequest: InputRequest? = null
    private var playbackIsOutput = false
    private var pendingSeekFraction = -1f
    private var playerReady = false
    private val audio by lazy { activity.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    private val picker = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val request = pendingRequest
        pendingRequest = null
        if (request == null || request.referenceSlot || request.ticket != inputTicket) return@registerForActivityResult
        if (uri == null) { model.setImporting(false); return@registerForActivityResult }
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val bytes = activity.contentResolver.openInputStream(uri)?.use(VoiceEffectsWave::normalize) ?: error("input")
                    if (!acceptableDuration(bytes, request.requiresHalfSecond)) error("duration")
                    newInput(bytes)
                }
                if (destroyed || request.ticket != inputTicket) withContext(Dispatchers.IO) { file.delete() }
                else installInput(file, "导入 WAV")
            } catch (_: Exception) { if (!destroyed && request.ticket == inputTicket) model.inputFailed("unsupportedOrInvalidWav") }
            finally { if (request.ticket == inputTicket) model.setImporting(false) }
        }
    }
    private val permission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val request = pendingRequest
        pendingRequest = null
        if (request == null || request.referenceSlot || request.ticket != inputTicket) return@registerForActivityResult
        if (granted && foreground && !destroyed) beginCapture(request)
        else { model.setImporting(false); model.inputFailed("microphonePermissionDenied") }
    }
    private val referencePermission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val request = pendingRequest
        pendingRequest = null
        if (request == null || !request.referenceSlot || request.ticket != inputTicket) return@registerForActivityResult
        if (granted && foreground && !destroyed) beginCapture(request)
        else { model.setReferenceImporting(false); if (foreground && !destroyed) model.inputFailed("microphonePermissionDenied") }
    }
    private val referencePicker = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val request = pendingRequest
        pendingRequest = null
        if (request == null || !request.referenceSlot || request.ticket != inputTicket) return@registerForActivityResult
        if (uri == null) { model.setReferenceImporting(false); return@registerForActivityResult }
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    val data = activity.contentResolver.openInputStream(uri)?.use(VoiceEffectsWave::normalize) ?: error("input")
                    if ((VoiceWaves.decode(data)?.durationSeconds ?: 0.0) !in 0.5..30.0) error("duration")
                    data
                }
                val file = withContext(Dispatchers.IO) { newInput(bytes) }
                if (destroyed || request.ticket != inputTicket) withContext(Dispatchers.IO) { file.delete() }
                else installReference(file, "导入参考音频.wav")
            } catch (_: Exception) {
                if (!destroyed && request.ticket == inputTicket) model.inputFailed("unsupportedOrInvalidWav")
            } finally { if (request.ticket == inputTicket) model.setReferenceImporting(false) }
        }
    }
    private val exporter = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri ->
        val reference = exportReference ?: return@registerForActivityResult
        val file = outputFile(reference)
        if (uri == null || file == null) { finishExport(reference); return@registerForActivityResult }
        copyingExport = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    activity.contentResolver.openOutputStream(uri, "wt")?.use { output -> file.inputStream().use { it.copyTo(output) } }
                        ?: error("output")
                }
            } catch (_: Exception) { model.inputFailed("audioExportFailed") }
            finally { copyingExport = false; finishExport(reference) }
        }
    }
    init {
        val provider = object : AndroidVoiceEffectsInputProvider {
            override fun acquire(reference: String): AndroidVoiceEffectsInputLease {
                val file = inputs[reference] ?: error("unknown input")
                leased[reference] = (leased[reference] ?: 0) + 1
                return object : AndroidVoiceEffectsInputLease {
                    override val file = file
                    private var released = false
                    override suspend fun release() {
                        if (released) return
                        released = true; leased[reference] = checkNotNull(leased[reference]) - 1
                        cleanupInput(reference)
                    }
                }
            }
        }
        inputProvider = provider
        bridge = AndroidVoiceEffectsBridge(VoiceEffectsClient(WindowedPitchVoiceEffectsBackend(),
            File(activity.cacheDir, "voice-output-${UUID.randomUUID()}")), provider, scope)
        timbres = AndroidOpenVoiceTimbres(activity.applicationContext, provider, scope)
        model = VoiceEffectsViewModel(bridge, timbres)
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    activity.assets.open("OpenVoice/original.wav").use { newInput(it.readBytes()) }
                }
                if (destroyed) file.delete() else model.setExample(registerInput(file))
            } catch (_: Exception) { if (!destroyed) model.inputFailed("无法读取内置示例") }
        }
    }
    fun foreground() {
        if (destroyed) return
        foreground = true; liveAudio.attach()
        if (::model.isInitialized && !model.state.referenceMode && model.state.inputReference == null && model.state.exampleReference != null) model.useExample()
    }
    fun background() { foreground = false; stopPlayback(); stopCapture(true); model.cancel(); liveAudio.release() }
    fun close() {
        destroyed = true; inputTicket++; waveGeneration++
        liveAudio.close()
        stopPlayback(); stopCapture(true)
        model.setRecording(false); model.setReferenceRecording(false)
        if (!copyingExport) exportReference?.let(::finishExport)
        model.close()
        inputs.keys.toList().forEach { reference -> retired += reference; scope.launch { cleanupInput(reference) } }
        // The capture worker owns AudioRecord release and focus cleanup in its finally;
        // when no worker exists stopPlayback has already abandoned focus.
    }
    override fun selectAudioFile() {
        if (!model.state.controlsEnabled) return
        if (newRequest(false, model.state.referenceMode) == null) return
        model.setImporting(true)
        try { picker.launch(arrayOf("audio/wav", "audio/x-wav", "audio/wave")) }
        catch (_: Exception) { pendingRequest = null; model.setImporting(false); model.inputFailed("audioPickerUnavailable") }
    }
    override fun startRecording() {
        if (!foreground || !model.state.controlsEnabled) return
        if (newRequest(false, model.state.referenceMode) == null) return
        model.setImporting(true)
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val request = checkNotNull(pendingRequest); pendingRequest = null
            beginCapture(request)
        } else try { permission.launch(Manifest.permission.RECORD_AUDIO) }
        catch (_: Exception) { pendingRequest = null; model.setImporting(false); model.inputFailed("microphonePermissionDenied") }
    }
    private fun newRequest(referenceSlot: Boolean, requiresHalfSecond: Boolean): InputRequest? {
        if (pendingRequest != null || capture != null) return null
        return InputRequest(++inputTicket, referenceSlot, requiresHalfSecond).also { pendingRequest = it }
    }
    @Suppress("MissingPermission")
    private fun beginCapture(request: InputRequest) {
        if (request.referenceSlot) model.setReferenceImporting(false) else model.setImporting(false)
        if (!foreground || destroyed || request.ticket != inputTicket || capture != null) return
        stopPlayback()
        if (!takeFocus()) { model.inputFailed("audioInUse"); return }
        try {
            val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            require(minimum > 0)
            val value = AudioRecord(MediaRecorder.AudioSource.MIC,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(minimum,4096))
            recorder = value; check(value.state == AudioRecord.STATE_INITIALIZED)
            value.startRecording()
            discardRecording = false; capturing.set(true)
            if (request.referenceSlot) model.setReferenceRecording(true) else model.setRecording(true)
            capture = scope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        val pcm = ByteArrayOutputStream(); val buffer = ShortArray(2048); val encoded = ByteArray(4096)
                        val limit = 16000*2*30
                        try {
                            while (capturing.get() && pcm.size() < limit) {
                                val count = value.read(buffer,0,minOf(buffer.size,(limit-pcm.size())/2),AudioRecord.READ_BLOCKING)
                                if (count < 0) { if (capturing.get()) error("record"); break }
                                if (count == 0) error("record")
                                for (i in 0 until count) {
                                    encoded[i*2] = buffer[i].toByte()
                                    encoded[i*2+1] = (buffer[i].toInt() shr 8).toByte()
                                }
                                pcm.write(encoded,0,count*2)
                            }
                        } finally { runCatching { value.stop() }; value.release() }
                        val canonical = VoiceEffectsWave.canonical(16000,pcm.toByteArray())
                        if (!acceptableDuration(canonical, request.requiresHalfSecond)) error("duration")
                        canonical
                    }
                    if (!discardRecording && !destroyed && request.ticket == inputTicket) {
                        val file = withContext(Dispatchers.IO) { newInput(bytes) }
                        if (!destroyed && request.ticket == inputTicket) {
                            if (request.referenceSlot) installReference(file, "参考录音.wav") else installInput(file, "录音.wav")
                        } else withContext(Dispatchers.IO) { file.delete() }
                    }
                } catch (_: Exception) {
                    if (!discardRecording && !destroyed && request.ticket == inputTicket) model.inputFailed("audioRecordingFailed")
                } finally {
                    withContext(Dispatchers.Main) {
                        if (recorder === value) {
                            recorder = null; capture = null; capturing.set(false)
                            model.setRecording(false); model.setReferenceRecording(false)
                            releaseFocus()
                        }
                    }
                }
            }
        } catch (_: Exception) {
            recorder?.release(); recorder = null; capturing.set(false)
            model.setRecording(false); model.setReferenceRecording(false)
            releaseFocus(); model.inputFailed("audioRecordingFailed")
        }
    }
    override fun stopRecording() = stopCapture(false)
    private fun stopCapture(discard: Boolean) {
        if (capture == null) return
        discardRecording = discardRecording || discard; capturing.set(false)
        runCatching { recorder?.stop() } // unblock worker; only that worker releases
    }
    override fun startReferenceRecording() {
        if (!foreground || !model.state.controlsEnabled) return
        if (newRequest(true, true) == null) return
        model.setReferenceImporting(true)
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val request = checkNotNull(pendingRequest); pendingRequest = null
            beginCapture(request)
        } else try { referencePermission.launch(Manifest.permission.RECORD_AUDIO) }
        catch (_: Exception) { pendingRequest = null; model.setReferenceImporting(false); model.inputFailed("microphonePermissionDenied") }
    }
    override fun stopReferenceRecording() = stopCapture(false)
    override fun playOriginal() {
        val reference = model.state.inputReference ?: return
        val file = inputs[reference] ?: return
        play(file, reference, false)
    }
    override fun useExample() {
        val old = model.state.inputReference
        if (!model.state.controlsEnabled || old == model.state.exampleReference) return
        model.useExample()
        if (old != null) retireInput(old)
    }
    override fun loadReferenceExample() {
        if (!foreground || !model.state.controlsEnabled) return
        val example = model.state.exampleReference ?: return
        val request = newRequest(true, true) ?: return
        model.setReferenceImporting(true)
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val bytes = activity.assets.open("OpenVoice/reference-content.wav").use(VoiceEffectsWave::normalize)
                    if ((VoiceWaves.decode(bytes)?.durationSeconds ?: 0.0) !in 0.5..30.0) error("duration")
                    newInput(bytes)
                }
                if (destroyed || request.ticket != inputTicket) withContext(Dispatchers.IO) { file.delete() }
                else {
                    val oldReference = model.state.referenceReference
                    model.selectReferenceInput(example, "内置普通话示例")
                    if (oldReference != null && oldReference != example) retireInput(oldReference)
                    installInput(file, "参考内容示例.wav")
                }
            } catch (_: Exception) {
                if (!destroyed && request.ticket == inputTicket) model.inputFailed("无法读取内置示例")
            } finally { pendingRequest = null; if (request.ticket == inputTicket) model.setReferenceImporting(false) }
        }
    }
    override fun clearVoiceInput(referenceSlot: Boolean) {
        if (!model.state.controlsEnabled) return
        inputTicket++
        stopPlayback(); stopCapture(true)
        model.setRecording(false); model.setReferenceRecording(false)
        releaseFocus()
        if (referenceSlot) {
            val old = model.state.referenceReference
            model.clearReference()
            if (old != null) retireInput(old)
        } else {
            val old = model.state.inputReference
            model.clearInput()
            if (old != null) retireInput(old)
        }
    }
    override fun readVoiceWave(reference: String, reply: VoiceWaveReply) {
        if (destroyed) { scope.launch { reply.ready(null) }; return }
        val outputHold = model.holdOutput(reference)
        val lease = if (outputHold) null else try { inputProvider.acquire(reference) } catch (_: Exception) { null }
        val file = if (outputHold) outputFile(reference) else lease?.file
        if (file == null) {
            scope.launch {
                try {
                    reply.ready(null)
                } finally {
                    if (outputHold) model.endOutputUse(reference)
                    else lease?.release()
                }
            }
            return
        }
        scope.launch {
            val wave = withContext(Dispatchers.IO) {
                try { VoiceWaves.decode(file.readBytes()) } catch (_: Exception) { null }
            }
            try {
                withContext(Dispatchers.Main) { reply.ready(if (destroyed) null else wave) }
            } finally {
                if (outputHold) model.endOutputUse(reference)
                else lease?.release()
            }
        }
    }

    override fun selectReferenceAudioFile() {
        if (!foreground || !model.state.controlsEnabled) return
        if (newRequest(true, true) == null) return
        model.setReferenceImporting(true)
        try { referencePicker.launch(arrayOf("audio/wav", "audio/x-wav", "audio/wave")) }
        catch (_: Exception) { pendingRequest = null; model.setReferenceImporting(false); model.inputFailed("audioPickerUnavailable") }
    }
    override fun playReference() {
        val reference = model.state.referenceReference ?: return
        val file = inputs[reference] ?: return
        play(file, reference, false)
    }
    override fun playVoiceAt(reference: String, fraction: Float) {
        if (!foreground) return
        if (!model.state.controlsEnabled && !(model.state.mediaBusy && player != null &&
            exportReference == null && !copyingExport && capture == null)) return
        val clamped = fraction.coerceIn(0f, 1f)
        if (clamped >= 1f) { stopPlayback(); return } // end of waveform stops, never restarts
        val current = player
        if (current != null && playingReference == reference) {
            scope.launch {
                val active = player
                if (active !== current || playingReference != reference) return@launch
                if (!playerReady) { pendingSeekFraction = clamped; return@launch }
                val duration = try { active.duration } catch (_: Exception) { -1 }
                if (duration > 0) {
                    try {
                        active.seekTo((duration * clamped).toInt())
                        if (!active.isPlaying) active.start()
                    } catch (_: Exception) { stopPlayback(); model.inputFailed("audioPlaybackFailed") }
                }
            }
            return
        }
        val outputHold = model.holdOutput(reference)
        val file = if (outputHold) outputFile(reference) else inputs[reference]
        if (file == null) { if (outputHold) model.endOutputUse(reference); return }
        play(file, reference, outputHold, clamped)
    }

    override fun playbackFraction(reference: String): Float {
        val current = player ?: return -1f
        if (playingReference != reference || !playerReady) return -1f
        val duration = try { current.duration } catch (_: Exception) { return -1f }
        if (duration <= 0) return -1f
        val position = try { current.currentPosition } catch (_: Exception) { return -1f }
        return (position.toFloat() / duration).coerceIn(0f, 1f)
    }
    private fun outputFile(reference: String) = timbres.outputFile(reference) ?: bridge.outputFile(reference)
    override fun playResult(reference: String) {
        val file = outputFile(reference) ?: return
        if (model.holdOutput(reference)) play(file,reference)
    }
    private fun play(file: File, reference: String?, output: Boolean = true, startFraction: Float = -1f) {
        stopPlayback()
        if (!foreground || !model.state.controlsEnabled || !takeFocus()) {
            if (output) reference?.let(model::endOutputUse)
            model.inputFailed("audioInUse"); return
        }
        playingReference = reference; playbackIsOutput = output; pendingSeekFraction = startFraction; playerReady = false; model.setMediaBusy(true)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setDataSource(file.path)
                setOnPreparedListener {
                    if (player === it && foreground) {
                        playerReady = true
                        val seek = pendingSeekFraction; pendingSeekFraction = -1f
                        if (seek >= 0f && it.duration > 0) it.seekTo((it.duration * seek).toInt())
                        it.start()
                    } else if (player === it) stopPlayback()
                }
                setOnCompletionListener { if (player === it) stopPlayback() }
                setOnErrorListener { failed,_,_ ->
                    if (player === failed) { stopPlayback(); model.inputFailed("audioPlaybackFailed") }
                    true
                }
                prepareAsync()
            }
        } catch (_: Exception) { stopPlayback(); model.inputFailed("audioPlaybackFailed") }
    }
    override fun stopPlayback() {
        player?.let { active -> player = null; runCatching { active.release() } }
        playerReady = false
        if (playbackIsOutput) {
            playingReference?.let(model::endOutputUse)
            playbackIsOutput = false
        }
        playingReference = null; pendingSeekFraction = -1f
        if (::model.isInitialized && exportReference == null) model.setMediaBusy(false)
        if (capture == null) releaseFocus()
    }
    override fun exportAudio(reference: String) {
        if (!model.state.controlsEnabled || !model.holdOutput(reference)) return
        stopPlayback()
        exportReference = reference; model.setMediaBusy(true)
        try { exporter.launch("voice-effect.wav") }
        catch (_: Exception) { finishExport(reference); model.inputFailed("audioExportFailed") }
    }
    private fun finishExport(reference: String) {
        model.endOutputUse(reference)
        if (exportReference == reference) exportReference = null
        model.setMediaBusy(false)
    }
    override fun leavePage() {
        foreground = false; inputTicket++; waveGeneration++
        liveAudio.release()
        stopPlayback(); stopCapture(true)
        model.setRecording(false); model.setReferenceRecording(false); model.setImporting(false); model.setReferenceImporting(false)
        releaseFocus()
        val example = model.state.exampleReference
        model.leavePage()
        model.clearReference(); model.clearInput()
        if (example != null) model.selectInput(example, "内置普通话示例")
        retireTemps(example)
    }
    val idle: Boolean get() = model.state.controlsEnabled && model.state.pendingReleases == 0 && capture == null && !copyingExport && !liveSession.busy

    private fun takeFocus(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { if (it < 0) { stopPlayback(); stopCapture(true) } }.build()
        val granted = audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) focus = request else audio.abandonAudioFocusRequest(request)
        return granted
    }
    private fun releaseFocus() { focus?.let { audio.abandonAudioFocusRequest(it) }; focus = null }
    private fun newInput(bytes: ByteArray): File {
        val directory = File(activity.cacheDir,"voice-input"); check(directory.isDirectory || directory.mkdirs())
        val file = File.createTempFile("input-",".wav",directory)
        try { file.outputStream().use { it.write(bytes) }; return file }
        catch (error: Exception) { file.delete(); throw error }
    }
    private fun installInput(file: File, name: String) {
        val old = model.state.inputReference
        model.selectInput(registerInput(file), name)
        if (old != null && old != model.state.exampleReference) retireInput(old)
    }
    private fun registerInput(file: File): String {
        val reference = "input-${UUID.randomUUID()}"
        inputs[reference] = file
        return reference
    }
    private fun retireInput(reference: String, keep: String? = model.state.exampleReference) {
        if (reference == keep && !destroyed) return
        retired += reference
        scope.launch { cleanupInput(reference) }
    }
    private fun retireTemps(keep: String?) {
        inputs.keys.toList().forEach { reference ->
            if (reference != keep) retireInput(reference, keep)
        }
    }
    private fun installReference(file: File, name: String) {
        val old = model.state.referenceReference
        model.selectReferenceInput(registerInput(file), name)
        if (old != null && old != model.state.exampleReference) retireInput(old)
    }
    private fun acceptableDuration(bytes: ByteArray, requiresHalfSecond: Boolean = model.state.referenceMode): Boolean {
        val minimum = if (requiresHalfSecond) 0.5 else 0.05
        val duration = VoiceWaves.decode(bytes)?.durationSeconds ?: return false
        return duration >= minimum
    }
    private suspend fun cleanupInput(reference: String) {
        if (reference !in retired || (leased[reference] ?: 0) > 0) return
        val file = inputs[reference] ?: return
        val deleted = withContext(Dispatchers.IO) { file.delete() || !file.exists() }
        if (deleted) { inputs.remove(reference); leased.remove(reference); retired.remove(reference) }
    }
}
