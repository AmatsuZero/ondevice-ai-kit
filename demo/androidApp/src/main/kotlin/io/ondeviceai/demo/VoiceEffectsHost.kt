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
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

/** Reusable platform audio owner for the unified CMP page. */
internal class VoiceEffectsHost(private val activity: ComponentActivity) : VoiceEffectsDemoActions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var bridge: AndroidVoiceEffectsBridge
    lateinit var model: VoiceEffectsViewModel
        private set
    private val inputs = mutableMapOf<String, File>()
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
    private var importGeneration = 0L
    private val audio by lazy { activity.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    private val picker = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) { model.setImporting(false); return@registerForActivityResult }
        val generation = ++importGeneration
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val bytes = activity.contentResolver.openInputStream(uri)?.use(VoiceEffectsWave::normalize) ?: error("input")
                    newInput(bytes)
                }
                if (destroyed || generation != importGeneration) withContext(Dispatchers.IO) { file.delete() }
                else installInput(file, "导入 WAV")
            } catch (_: Exception) { if (!destroyed) model.inputFailed("unsupportedOrInvalidWav") }
            finally { model.setImporting(false) }
        }
    }
    private val permission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && foreground && !destroyed) beginRecording()
        else { model.setImporting(false); model.inputFailed("microphonePermissionDenied") }
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
    fun foreground() { if (!destroyed) foreground = true }
    fun background() { foreground = false; stopPlayback(); stopCapture(true); model.cancel() }
    fun close() {
        destroyed = true; importGeneration++
        stopPlayback(); stopCapture(true)
        if (!copyingExport) exportReference?.let(::finishExport)
        model.close()
        inputs.keys.toList().forEach { reference -> retired += reference; scope.launch { cleanupInput(reference) } }
        // Cleanup scope survives until native input drain and active export finish.
    }
    override fun selectAudioFile() {
        if (!model.state.controlsEnabled) return
        model.setImporting(true)
        try { picker.launch(arrayOf("audio/wav", "audio/x-wav", "audio/wave")) }
        catch (_: Exception) { model.setImporting(false); model.inputFailed("audioPickerUnavailable") }
    }
    override fun startRecording() {
        if (!foreground || !model.state.controlsEnabled) return
        model.setImporting(true)
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) beginRecording()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    @Suppress("MissingPermission")
    private fun beginRecording() {
        model.setImporting(false)
        if (!foreground || destroyed || capture != null) return
        stopPlayback()
        if (!takeFocus()) { model.inputFailed("audioInUse"); return }
        try {
            val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            require(minimum > 0)
            val value = AudioRecord(MediaRecorder.AudioSource.MIC,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(minimum,4096))
            recorder = value; check(value.state == AudioRecord.STATE_INITIALIZED)
            value.startRecording()
            discardRecording = false; capturing.set(true); model.setRecording(true)
            capture = scope.launch {
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        val pcm = ByteArrayOutputStream(); val buffer = ShortArray(2048); val encoded = ByteArray(4096)
                        try {
                            while (capturing.get() && pcm.size() < 16000*60) {
                                val count = value.read(buffer,0,minOf(buffer.size,(16000*60-pcm.size())/2),AudioRecord.READ_BLOCKING)
                                if (count < 0) { if (capturing.get()) error("record"); break }
                                if (count == 0) error("record")
                                for (i in 0 until count) {
                                    encoded[i*2] = buffer[i].toByte()
                                    encoded[i*2+1] = (buffer[i].toInt() shr 8).toByte()
                                }
                                pcm.write(encoded,0,count*2)
                            }
                        } finally { runCatching { value.stop() }; value.release() }
                        VoiceEffectsWave.canonical(16000,pcm.toByteArray())
                    }
                    if (!discardRecording && !destroyed) {
                        val file = withContext(Dispatchers.IO) { newInput(bytes) }
                        if (!destroyed) installInput(file,"录音.wav") else withContext(Dispatchers.IO) { file.delete() }
                    }
                } catch (_: Exception) { if (!discardRecording && !destroyed) model.inputFailed("audioRecordingFailed") }
                finally {
                    recorder = null; capture = null; capturing.set(false); model.setRecording(false); releaseFocus()
                }
            }
        } catch (_: Exception) {
            recorder?.release(); recorder = null; capturing.set(false); model.setRecording(false)
            releaseFocus(); model.inputFailed("audioRecordingFailed")
        }
    }
    override fun stopRecording() = stopCapture(false)
    private fun stopCapture(discard: Boolean) {
        if (capture == null) return
        discardRecording = discardRecording || discard; capturing.set(false)
        runCatching { recorder?.stop() } // unblock worker; only that worker releases
    }
    override fun playOriginal() { inputs[model.state.inputReference]?.let { play(it,null) } }
    override fun useExample() {
        val old = model.state.inputReference ?: return
        if (!model.state.controlsEnabled || old == model.state.exampleReference) return
        model.useExample()
        retireInput(old)
    }
    private fun outputFile(reference: String) = timbres.outputFile(reference) ?: bridge.outputFile(reference)
    override fun playResult(reference: String) {
        val file = outputFile(reference) ?: return
        if (model.holdOutput(reference)) play(file,reference)
    }
    private fun play(file: File, reference: String?) {
        stopPlayback()
        if (!foreground || !model.state.controlsEnabled || !takeFocus()) {
            reference?.let(model::endOutputUse); model.inputFailed("audioInUse"); return
        }
        playingReference = reference; model.setMediaBusy(true)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                setDataSource(file.path)
                setOnPreparedListener {
                    if (player === it && foreground) {
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
        player?.release(); player = null
        playingReference?.let(model::endOutputUse); playingReference = null
        if (::model.isInitialized && exportReference == null) model.setMediaBusy(false)
        if (capture == null) releaseFocus()
    }
    override fun exportAudio(reference: String) {
        if (!model.state.controlsEnabled || !model.holdOutput(reference)) return
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
        foreground = false; importGeneration++
        stopPlayback(); stopCapture(true); model.leavePage()
    }
    val idle: Boolean get() = model.state.controlsEnabled && model.state.pendingReleases == 0 && capture == null && !copyingExport

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
    private fun retireInput(reference: String) {
        retired += reference
        scope.launch { cleanupInput(reference) }
    }
    private suspend fun cleanupInput(reference: String) {
        if (reference !in retired || (leased[reference] ?: 0) > 0) return
        val file = inputs[reference] ?: return
        val deleted = withContext(Dispatchers.IO) { file.delete() || !file.exists() }
        if (deleted) { inputs.remove(reference); leased.remove(reference); retired.remove(reference) }
    }
}
