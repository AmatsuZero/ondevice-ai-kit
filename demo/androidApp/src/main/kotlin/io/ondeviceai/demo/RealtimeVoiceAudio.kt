package io.ondeviceai.demo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Handler
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** JNI bridge implemented by RealtimeVoiceJni.cpp, packaged in libodai_livevoice.so. */
internal object LiveVoiceNative {
    init { System.loadLibrary("odai_livevoice") }

    @JvmStatic external fun create(sampleRate: Int, role: Int): Long

    /** One call: process mic->output, then (speaker only) guard the SAME processed output. Returns 0 safe / 1 trip. */
    @JvmStatic external fun render(handle: Long, mic: FloatArray, output: FloatArray, frames: Int, guard: Boolean): Int

    @JvmStatic external fun destroy(handle: Long)
}

/** Pure device description so route policy is unit-testable on the JVM. */
internal enum class LiveDeviceType {
    WIRED_HEADSET, WIRED_HEADPHONES, USB_HEADSET, BLUETOOTH_SCO, BLUETOOTH_A2DP,
    BLE_HEADSET, USB_DEVICE, BUILTIN_EARPIECE, BUILTIN_SPEAKER, VEHICLE, OTHER,
}

internal data class LiveDevice(val type: LiveDeviceType, val productName: String?, val sink: Boolean)

internal enum class LiveRouteClass {
    WIRED_HEADPHONES, USB_HEADSET_IDENTIFIED, BT_HEADPHONE_RECOGNIZED,
    GENERIC_USB, EARPIECE, VEHICLE, BUILTIN_SPEAKER, OTHER;

    val usableHeadphones: Boolean
        get() = this == WIRED_HEADPHONES || this == USB_HEADSET_IDENTIFIED || this == BT_HEADPHONE_RECOGNIZED
}

internal object RoutePolicy {
    private val BT_HINTS = listOf("headset", "headphone", "airpod", "buds", "wh-", "quietcomfort")
    private val CAR_HINTS = listOf("car", "vehicle", "automotive")

    fun classify(device: LiveDevice): LiveRouteClass {
        if (!device.sink) return LiveRouteClass.OTHER
        val name = device.productName
        if (name != null && CAR_HINTS.any { name.lowercase().contains(it) }) return LiveRouteClass.VEHICLE
        return when (device.type) {
            LiveDeviceType.WIRED_HEADSET, LiveDeviceType.WIRED_HEADPHONES -> LiveRouteClass.WIRED_HEADPHONES
            LiveDeviceType.USB_HEADSET -> if (identified(name)) LiveRouteClass.USB_HEADSET_IDENTIFIED else LiveRouteClass.GENERIC_USB
            LiveDeviceType.BLUETOOTH_SCO -> LiveRouteClass.BT_HEADPHONE_RECOGNIZED
            LiveDeviceType.BLUETOOTH_A2DP, LiveDeviceType.BLE_HEADSET ->
                if (recognizedBt(name)) LiveRouteClass.BT_HEADPHONE_RECOGNIZED else LiveRouteClass.OTHER
            LiveDeviceType.USB_DEVICE -> LiveRouteClass.GENERIC_USB
            LiveDeviceType.BUILTIN_EARPIECE -> LiveRouteClass.EARPIECE
            LiveDeviceType.BUILTIN_SPEAKER -> LiveRouteClass.BUILTIN_SPEAKER
            else -> LiveRouteClass.OTHER
        }
    }

    fun rank(device: LiveDevice): Int = when (classify(device)) {
        LiveRouteClass.WIRED_HEADPHONES -> 0
        LiveRouteClass.USB_HEADSET_IDENTIFIED -> 1
        LiveRouteClass.BT_HEADPHONE_RECOGNIZED -> 2
        else -> Int.MAX_VALUE
    }

    fun isHeadphones(device: LiveDevice): Boolean = classify(device).usableHeadphones

    fun pickHeadphones(devices: List<LiveDevice>): LiveDevice? =
        devices.filter(::isHeadphones).minByOrNull(::rank)

    private fun identified(name: String?): Boolean = !name.isNullOrBlank() && name.trim().length >= 3

    private fun recognizedBt(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.lowercase()
        return BT_HINTS.any(lower::contains)
    }
}

/** Pure gate for the speaker path; every condition must hold before any speaker audio is rendered. */
internal object SpeakerGuardPolicy {
    data class Conditions(
        val modifyAudioSettingsGranted: Boolean,
        val communicationMode: Boolean,
        val voiceCommunicationSource: Boolean,
        val communicationRouteVerified: Boolean,
        val aecEnabled: Boolean,
        val headphonesNotSelected: Boolean,
    )

    fun canUseSpeaker(c: Conditions): Boolean =
        c.modifyAudioSettingsGranted && c.communicationMode && c.voiceCommunicationSource &&
            c.communicationRouteVerified && c.aecEnabled && c.headphonesNotSelected
}

/** Device callback is top-level, never nested in the host. */
private class RouteDeviceCallback(private val changed: () -> Unit) : android.media.AudioDeviceCallback() {
    override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = changed()
    override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = changed()
}

/** Android audio engine backing [RealtimeVoiceSession] through [LiveVoicePort]. */
internal class RealtimeVoiceAudio(
    private val context: Context,
    private val main: Handler,
    private val events: Events,
) : LiveVoicePort, Closeable {

    interface Events {
        fun onBeforeStart(): Boolean
        fun onPermissionNeeded(id: Long)
        fun onReady(id: Long)
        fun onEnded(id: Long, message: String)
        fun onRouteLabel(label: String, headphonesAvailable: Boolean)
    }

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val activeId = AtomicLong(IDLE)
    private val stopping = AtomicBoolean(false)
    private val endMessage = AtomicReference("已结束")
    private val currentRef = AtomicReference<Run?>(null)
    private val deviceCallback = RouteDeviceCallback { devicesChanged() }

    private var destination: LiveDestination = LiveDestination.HEADPHONES
    private var deviceCallbackRegistered = false
    private var pageShown = false
    private var closed = false

    companion object {
        const val IDLE = -1L
        private const val FRAMES = 480
        private const val ROUTE_TIMEOUT_MS = 600L
        private const val COMM_ROUTE_TIMEOUT_MS = 500L
        private const val WRITE_DEADLINE_NS = 100_000_000L
    }

    private fun idOf(device: AudioDeviceInfo): Int = device.id

    private fun AudioDeviceInfo.toLive(): LiveDevice = LiveDevice(
        type = when (type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> LiveDeviceType.WIRED_HEADSET
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> LiveDeviceType.WIRED_HEADPHONES
            AudioDeviceInfo.TYPE_USB_HEADSET -> LiveDeviceType.USB_HEADSET
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> LiveDeviceType.BLUETOOTH_SCO
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> LiveDeviceType.BLUETOOTH_A2DP
            AudioDeviceInfo.TYPE_BLE_HEADSET -> LiveDeviceType.BLE_HEADSET
            AudioDeviceInfo.TYPE_USB_DEVICE -> LiveDeviceType.USB_DEVICE
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> LiveDeviceType.BUILTIN_EARPIECE
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> LiveDeviceType.BUILTIN_SPEAKER
            else -> LiveDeviceType.OTHER
        },
        productName = productName?.toString(),
        sink = isSink,
    )

    fun attach() {
        if (closed || pageShown) return
        pageShown = true
        audio.registerAudioDeviceCallback(deviceCallback, main)
        deviceCallbackRegistered = true
        publishIdleLabel()
    }

    fun detach() {
        pageShown = false
        if (deviceCallbackRegistered) {
            audio.unregisterAudioDeviceCallback(deviceCallback)
            deviceCallbackRegistered = false
        }
    }

    private fun devicesChanged() {
        publishIdleLabel()
        if (activeId.get() != IDLE) stopInternal("音频设备已变更，已停止")
    }

    private fun publishIdleLabel() {
        if (!pageShown) return
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val headphones = outputs.any { RoutePolicy.isHeadphones(it.toLive()) }
        val label = if (headphones) "耳机已连接" else "内置扬声器"
        main.post { if (!closed && pageShown) events.onRouteLabel(label, headphones) }
    }

    override fun refreshRoute() = publishIdleLabel()

    override fun start(id: Long, role: LiveRole, target: LiveDestination) {
        if (closed || stopping.get() || !activeId.compareAndSet(IDLE, id)) {
            main.post { events.onEnded(id, "当前无法开始语音对话，请稍后重试") }
            return
        }
        if (!events.onBeforeStart()) {
            activeId.compareAndSet(id, IDLE)
            main.post { events.onEnded(id, "文件播放中，暂不能开始语音对话") }
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            activeId.compareAndSet(id, IDLE)
            main.post { events.onPermissionNeeded(id) }
            main.post { events.onEnded(id, "需要麦克风权限才能语音对话") }
            return
        }
        Thread { doStart(id, role, target) }.start()
    }

    @SuppressLint("MissingPermission")
    private fun doStart(id: Long, role: LiveRole, target: LiveDestination) {
        var rec: AudioRecord? = null
        var trk: AudioTrack? = null
        var handle = 0L
        var echo: AcousticEchoCanceler? = null
        var focus: AudioFocusRequest? = null
        var failure: String? = null
        var committed = false
        var modeChanged = false
        var commChanged = false
        val endSent = AtomicBoolean(false)
        fun cancelled(): Boolean = stopping.get() || activeId.get() != id
        val prevMode = audio.mode
        val prevDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio.communicationDevice else null

        try {
            val granted = takeFocus(id)
            if (granted == null) { failure = "音频焦点申请失败，已停止"; return }
            focus = granted

            val modifyGranted = context.checkSelfPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
            val speaker = target == LiveDestination.BUILTIN_SPEAKER
            if (speaker && !modifyGranted) {
                failure = "未获得修改音频设置权限，无法使用扬声器"
                return
            }

            val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val chosen: AudioDeviceInfo
            if (!speaker) {
                chosen = outputs.filter { RoutePolicy.isHeadphones(it.toLive()) }
                    .minByOrNull { RoutePolicy.rank(it.toLive()) }
                    ?: run { failure = "未检测到耳机，请连接后重试"; return }
            } else {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    failure = "当前系统不支持扬声器通话路由，已关闭"
                    return
                }
                chosen = audio.availableCommunicationDevices
                    .firstOrNull { it.toLive().type == LiveDeviceType.BUILTIN_SPEAKER && it.isSink }
                    ?: run { failure = "未检测到内置扬声器"; return }
            }

            val rate = negotiateRate() ?: run { failure = "不支持的采样率"; return }

            if (speaker) {
                audio.mode = AudioManager.MODE_IN_COMMUNICATION
                modeChanged = true
                if (audio.mode != AudioManager.MODE_IN_COMMUNICATION) {
                    failure = "通话模式未生效，已关闭扬声器"
                    return
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (!audio.setCommunicationDevice(chosen)) {
                        failure = "扬声器路由设置失败，已关闭"
                        return
                    }
                    commChanged = true
                    val routeDeadline = System.currentTimeMillis() + COMM_ROUTE_TIMEOUT_MS
                    var verified = false
                    while (System.currentTimeMillis() < routeDeadline) {
                        if (cancelled()) { failure = "语音对话已取消"; return }
                        val comm = audio.communicationDevice
                        if (comm != null && idOf(comm) == idOf(chosen)) { verified = true; break }
                        try { Thread.sleep(15) } catch (_: InterruptedException) { return }
                    }
                    if (!verified) {
                        failure = "扬声器通信路由未验证，已关闭"
                        return
                    }
                }
            }

            if (cancelled()) { failure = "语音对话已取消"; return }
            val source = if (speaker) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC
            val minRec = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minRec <= 0) { failure = "录音缓冲区不支持"; return }
            val created = AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, max(minRec, FRAMES * 4))
            rec = created
            if (created.state != AudioRecord.STATE_INITIALIZED) {
                rec = null
                created.release(); failure = "录音初始化失败"; return
            }
            created.startRecording()
            if (created.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                runCatching { created.stop() }; rec = null; created.release(); failure = "录音启动失败"; return
            }

            if (speaker) {
                if (!AcousticEchoCanceler.isAvailable()) {
                    failure = "回声消除不可用，已关闭扬声器"
                    return
                }
                val a = AcousticEchoCanceler.create(created.audioSessionId)
                if (a == null) { failure = "回声消除不可用，已关闭扬声器"; return }
                val rc = a.setEnabled(true)
                if (rc != AudioEffect.SUCCESS || !a.enabled) {
                    a.release(); failure = "回声消除启用失败，已关闭扬声器"; return
                }
                echo = a
                val allowed = SpeakerGuardPolicy.canUseSpeaker(
                    SpeakerGuardPolicy.Conditions(
                        modifyAudioSettingsGranted = modifyGranted,
                        communicationMode = audio.mode == AudioManager.MODE_IN_COMMUNICATION,
                        voiceCommunicationSource = source == MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        communicationRouteVerified = true,
                        aecEnabled = a.enabled,
                        headphonesNotSelected = true,
                    )
                )
                if (!allowed) { failure = "扬声器保护条件不满足，已关闭"; return }
            }

            val minTrack = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minTrack <= 0) { failure = "播放缓冲区不支持"; return }
            val usage = if (speaker) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA
            val built = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(max(minTrack, FRAMES * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            trk = built
            if (built.state != AudioTrack.STATE_INITIALIZED) {
                trk = null
                built.release(); failure = "播放轨道初始化失败"; return
            }
            built.setVolume(0f)
            if (!speaker) built.setPreferredDevice(chosen)
            built.play()
            built.write(ShortArray(FRAMES), 0, FRAMES)
            if (!awaitRoute(built, idOf(chosen))) { failure = "音频路由未就绪，已停止"; return }
            if (cancelled()) { failure = "语音对话已取消"; return }

            handle = LiveVoiceNative.create(rate, role.ordinal)
            if (handle == 0L) { failure = "语音引擎初始化失败"; return }

            endMessage.set("已结束")
            val run = Run(id, target, rate, created, built, handle, echo, focus,
                idOf(chosen), prevMode, prevDevice, modeChanged, commChanged)
            currentRef.set(run)
            committed = true
            destination = target
            runWorker(run)
        } catch (error: Throwable) {
            failure = "音频初始化失败"
        } finally {
            if (!committed) {
                rec?.let { runCatching { it.stop() }; it.release() }
                trk?.release()
                if (handle != 0L) LiveVoiceNative.destroy(handle)
                echo?.release()
                focus?.let { audio.abandonAudioFocusRequest(it) }
                if (modeChanged) audio.mode = prevMode
                if (commChanged && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audio.clearCommunicationDevice()
                    if (prevDevice != null) audio.setCommunicationDevice(prevDevice)
                }
                val message = failure ?: "音频初始化失败"
                main.post {
                    if (activeId.get() == id) activeId.set(IDLE)
                    stopping.set(false)
                    if (!endSent.getAndSet(true)) events.onEnded(id, message)
                }
            }
        }
    }

    private fun awaitRoute(track: AudioTrack, expectedId: Int): Boolean {
        val deadline = System.currentTimeMillis() + ROUTE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (stopping.get()) return false
            if (trackRoutedTo(track, expectedId)) return true
            try { Thread.sleep(15) } catch (_: InterruptedException) { return false }
        }
        return false
    }

    /** Actual route ID only; on API 36+ the routed device list must contain exactly the expected id. */
    private fun trackRoutedTo(track: AudioTrack, expectedId: Int): Boolean = try {
        if (Build.VERSION.SDK_INT >= 36) {
            val devices = track.routedDevices
            devices.size == 1 && idOf(devices[0]) == expectedId
        } else {
            val dev = track.routedDevice
            dev != null && idOf(dev) == expectedId
        }
    } catch (_: Throwable) {
        false
    }

    /** Confirm the microphone is actually recording from a valid source device at the negotiated rate. */
    private fun inputRouteOk(record: AudioRecord, track: AudioTrack, rate: Int): Boolean {
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) return false
        if (record.sampleRate != rate || track.sampleRate != rate) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val dev = record.routedDevice ?: return false
            if (dev.isSink) return false
            return idOf(dev) >= 0
        }
        return true
    }

    /** Every occupied block on the speaker path must still satisfy the guard conditions. */
    private fun speakerGuardsOk(run: Run): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        if (context.checkSelfPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS) != PackageManager.PERMISSION_GRANTED) return false
        if (run.aec?.enabled != true) return false
        if (audio.mode != AudioManager.MODE_IN_COMMUNICATION) return false
        val comm = audio.communicationDevice ?: return false
        return idOf(comm) == run.outputId
    }

    private fun negotiateRate(): Int? {
        val native = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
        for (rate in listOf(16000, 48000, 44100).sortedBy { abs(it - native) }) {
            val rec = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val trk = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (rec > 0 && trk > 0) return rate
        }
        return null
    }

    private fun takeFocus(id: Long): AudioFocusRequest? {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { change ->
                if (change != AudioManager.AUDIOFOCUS_GAIN) {
                    main.post { if (activeId.get() == id) stopInternal("音频焦点已丢失，已停止") }
                }
            }
            .build()
        return if (audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) request
        else { audio.abandonAudioFocusRequest(request); null }
    }

    private fun writeAll(track: AudioTrack, data: ShortArray, count: Int): Boolean {
        var offset = 0
        val deadline = System.nanoTime() + WRITE_DEADLINE_NS
        while (offset < count && System.nanoTime() < deadline) {
            if (stopping.get()) return false
            val written = track.write(data, offset, count - offset, AudioTrack.WRITE_NON_BLOCKING)
            if (written < 0) return false
            if (written == 0) {
                Thread.sleep(1)
                continue
            }
            offset += written
        }
        return offset >= count
    }

    private inner class Run(
        val id: Long,
        val target: LiveDestination,
        val rate: Int,
        val record: AudioRecord,
        val track: AudioTrack,
        val handle: Long,
        val aec: AcousticEchoCanceler?,
        val focus: AudioFocusRequest?,
        val outputId: Int,
        val prevMode: Int,
        val prevDevice: AudioDeviceInfo?,
        val modeChanged: Boolean,
        val commChanged: Boolean,
    ) {
        val released = AtomicBoolean(false)
        val ended = AtomicBoolean(false)

        fun mute() {
            try { track.setVolume(0f) } catch (_: Throwable) {}
            try { track.pause() } catch (_: Throwable) {}
            try { track.flush() } catch (_: Throwable) {}
        }

        fun release() {
            if (!released.compareAndSet(false, true)) return
            mute()
            try { record.stop() } catch (_: Throwable) {}
            record.release()
            try { track.stop() } catch (_: Throwable) {}
            track.release()
            if (handle != 0L) LiveVoiceNative.destroy(handle)
            try { aec?.release() } catch (_: Throwable) {}
            try { focus?.let { audio.abandonAudioFocusRequest(it) } } catch (_: Throwable) {}
            if (modeChanged) try { audio.mode = prevMode } catch (_: Throwable) {}
            if (commChanged && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try { audio.clearCommunicationDevice() } catch (_: Throwable) {}
                if (prevDevice != null) try { audio.setCommunicationDevice(prevDevice) } catch (_: Throwable) {}
            }
        }
    }

    private fun runWorker(run: Run) {
        val rec = run.record
        val trk = run.track
        val inShort = ShortArray(FRAMES)
        val inFloat = FloatArray(FRAMES)
        val outFloat = FloatArray(FRAMES)
        val outShort = ShortArray(FRAMES)
        val needGuard = run.target == LiveDestination.BUILTIN_SPEAKER
        var unmuted = false
        var readySent = false
        var failure: String? = null
        try {
            while (!stopping.get() && activeId.get() == run.id && !Thread.currentThread().isInterrupted) {
                val count = rec.read(inShort, 0, FRAMES, AudioRecord.READ_NON_BLOCKING)
                if (count < 0) { failure = "录音读取失败，已停止"; break }
                if (count == 0) { try { Thread.sleep(5) } catch (_: InterruptedException) {}; continue }
                if (stopping.get() || activeId.get() != run.id) break
                if (!inputRouteOk(rec, trk, run.rate)) { failure = "录音输入路由异常，已停止"; break }
                if (needGuard && !speakerGuardsOk(run)) { failure = "扬声器保护条件失效，已停止"; break }

                for (i in 0 until count) inFloat[i] = inShort[i] / 32768f

                val trip = LiveVoiceNative.render(run.handle, inFloat, outFloat, count, needGuard)
                if (trip != 0) { failure = "检测到回声或啸叫风险，已停止播放"; break }

                for (i in 0 until count) {
                    val v = min(1f, max(-1f, outFloat[i]))
                    outShort[i] = (v * 32767f).toInt().toShort()
                }

                if (!trackRoutedTo(trk, run.outputId)) {
                    failure = "音频输出路由已变更，已停止"
                    break
                }
                if (stopping.get() || activeId.get() != run.id) break
                if (!unmuted) {
                    trk.setVolume(1f)
                    unmuted = true
                    if (!readySent) {
                        readySent = true
                        main.post { if (activeId.get() == run.id && !stopping.get()) events.onReady(run.id) }
                    }
                }
                if (!writeAll(trk, outShort, count)) { failure = "音频输出写入失败，已停止"; break }
            }
        } catch (error: Throwable) {
            failure = "音频流处理异常，已停止"
        } finally {
            run.release()
            if (failure != null && activeId.get() == run.id) endMessage.set(failure)
            main.post {
                if (!run.ended.getAndSet(true)) {
                    if (activeId.get() == run.id) activeId.set(IDLE)
                    stopping.set(false)
                    currentRef.compareAndSet(run, null)
                    events.onEnded(run.id, endMessage.get())
                }
            }
        }
    }

    override fun stop(id: Long) {
        if (activeId.get() == id) stopInternal("已结束")
    }

    /** Main thread: cancel atomically and mute immediately; never wait for the worker. */
    private fun stopInternal(message: String) {
        val id = activeId.getAndSet(IDLE)
        if (id == IDLE) return
        stopping.set(true)
        endMessage.set(message)
        currentRef.get()?.mute()
    }

    fun release() {
        if (activeId.get() != IDLE) stopInternal("已释放")
        detach()
    }

    override fun close() {
        if (closed) return
        release()
        closed = true
    }
}
