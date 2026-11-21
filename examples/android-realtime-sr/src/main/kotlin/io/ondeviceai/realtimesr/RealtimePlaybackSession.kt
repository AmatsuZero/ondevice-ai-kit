package io.ondeviceai.realtimesr

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import io.ondeviceai.binding.RealtimeSRBackend
import io.ondeviceai.binding.RealtimeSRMode
import io.ondeviceai.binding.RealtimeVideoSR
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger

/** One native pixel session shared by the standalone player and the embedded CMP host. */
class RealtimePlaybackSession(
    private val context: Context,
    private val useDirectOutput: () -> Boolean = { false },
    private val useEarlySubmit: () -> Boolean = { false },
    private val onChanged: () -> Unit = {},
    private val onFrameOffered: (Long, Long, FrameId) -> Unit = { _, _, _ -> },
    private val onMediaPosition: (Long) -> Unit = {},
    private val onRunEnded: () -> Unit = {},
    private val onRunError: (String) -> Unit = {},
    private val onSrEvent: (FrameId, String, Map<String, Any?>) -> Unit = { _, _, _ -> },
) {
    fun selectSource(uri: Uri) {
        if (closed) return
        sourceUri = uri
        openState.reset()
        metadataWarning = null
        metadataRequest.incrementAndGet()
        durationUs = 0L
        mediaPositionUs = 0L
        unsupportedReason = null
        savedMediaUs = 0L
        wasPlaying = true
        isPlaying = false
        lastAcceptedOrdinal = -1L
        host.select(policy.mode)
        requestOpen()
    }

    fun selectMode(mode: PlaybackMode) {
        val sameMode = policy.mode == mode
        val keepPreview = !isPlaying
        keepPreviewOnModeChange = keepPreview
        try {
            val accepted = playbackControl.selectMode(
                when (mode) {
                    PlaybackMode.ORIGINAL -> RealtimeSRMode.ORIGINAL
                    PlaybackMode.AUTO -> RealtimeSRMode.AUTO
                    PlaybackMode.CUNNY -> RealtimeSRMode.CUNNY
                    PlaybackMode.SPATIAL -> RealtimeSRMode.SPATIAL
                }
            )
            if (accepted && sameMode && keepPreview) host.select(mode)
        } finally {
            keepPreviewOnModeChange = false
        }
        refreshStatus()
    }

    private val policy = PresentationPolicy()
    private val autoCadence = AutoCadence()
    @Volatile private var autoCoolingDown = false
    private val main = Handler(android.os.Looper.getMainLooper())
    private lateinit var worker: Handler
    private lateinit var workerThread: HandlerThread
    private lateinit var host: AndroidRealtimeSRHost
    private val playbackControl by lazy { RealtimeVideoSR(host) }
    @Volatile private var sourceUri: Uri? = null
    @Volatile private var outputSurface: Surface? = null
    @Volatile private var codecPlayer: CodecPlayer? = null
    @Volatile private var renderer: SrRenderer? = null
    @Volatile private var started = false
    @Volatile private var codecEpoch = 0L
    @Volatile private var activeGeneration = 0L
    @Volatile var mediaPositionUs = 0L
    @Volatile private var savedMediaUs = 0L
    @Volatile var durationUs = 0L
    @Volatile var isPlaying = false
    @Volatile private var wasPlaying = false
    @Volatile private var inputEligibility = InputEligibility.evaluate(0, 0, 0, 0, 0)
    @Volatile private var keepPreviewOnModeChange = false
    @Volatile private var pausedPreviewPtsUs: Long? = null
    private val openState = PlaybackOpenState()
    private val metadataRequest = AtomicLong()
    private val pendingCloses = AtomicInteger()
    private var closed = false
    private var configuredWidth = 0
    private var configuredHeight = 0
    private var lastAcceptedOrdinal = -1L
    private val frameLock = Any()
    private val rendererLock = Any()
    private val presentationLock = Any()
    @Volatile private var activityResumed = false
    @Volatile private var enhancementAvailable = true
    private val swapLock = Any()
    private data class Swap(
        val eglFrameId: String,
        val backend: RealtimeSRBackend,
        val enhancement: Boolean,
        val submittedNs: Long
    )
    private val swaps = LinkedHashMap<FrameId, Swap>()
    private var unsupportedReason: String? = null
    private var metadataWarning: String? = null

    init {
        workerThread = HandlerThread("player-host").also { it.start() }
        worker = Handler(workerThread.looper)
        host = AndroidRealtimeSRHost(policy, rendererSwitch = RendererSwitch { mode, generation ->
            val keepPreview = keepPreviewOnModeChange
            if (!keepPreview) pausedPreviewPtsUs = null
            activeGeneration = generation
            autoCadence.reset(generation)
            synchronized(swapLock) { swaps.clear() }
            worker.post {
                synchronized(rendererLock) {
                    val current = renderer
                    current?.reset(generation, keepPreview)
                    if (keepPreview && current != null) {
                        val enhance = mode != PlaybackMode.ORIGINAL &&
                            inputEligibility.eligible
                        current.redrawPaused(
                            generation,
                            enhance,
                            spatial = mode == PlaybackMode.SPATIAL && enhance
                        )
                    }
                }
            }
            refreshStatus()
        })
        host.isModeAvailable = { it == PlaybackMode.ORIGINAL || (!useDirectOutput() && enhancementAvailable) }
        refreshStatus()
    }

    fun start() {
        if (closed || started) return
        started = true
        if (outputSurface?.isValid == true && sourceUri != null) requestOpen()
    }

    fun resume() {
        synchronized(presentationLock) {
            activityResumed = true
            val surface = outputSurface
            if (surface?.isValid == true) renderer?.enablePresentation(surface)
        }
    }

    fun pause() {
        synchronized(presentationLock) {
            activityResumed = false
            renderer?.disarmPresentation()
        }
    }

    fun stop() {
        if (!started) return
        started = false
        savedMediaUs = mediaPositionUs
        synchronized(presentationLock) {
            activityResumed = false
            renderer?.disarmPresentation()
        }
        closeSession()
    }

    fun close() {
        if (closed) return
        closed = true
        leave()
        workerThread.quitSafely()
    }

    fun surfaceCreated(surface: Surface) {
        synchronized(presentationLock) { outputSurface = surface }
        if (started && sourceUri != null) requestOpen()
    }

    fun surfaceChanged(surface: Surface) {
        outputSurface = surface
    }

    fun surfaceDestroyed() {
        val current = synchronized(presentationLock) {
            outputSurface = null
            renderer?.also { it.disarmPresentation() }
        }
        current?.drainPresentation()
        savedMediaUs = mediaPositionUs
        closeSession()
    }

    private fun requestOpen(directFallbackReason: String? = null) {
        pausedPreviewPtsUs = null
        refreshStatus()
        val uri = sourceUri ?: return
        val surface = outputSurface?.takeIf { it.isValid } ?: return
        host.select(policy.mode)
        val request = openState.begin()
        val token = ++codecEpoch
        worker.post {
            closeCodec()
            releaseRenderer()
            if (!openState.current(request) || !started || sourceUri != uri || outputSurface !== surface) return@post
            var player: CodecPlayer? = null
            try {
                val listener = codecListener(token, request)
                player = CodecPlayer(context.applicationContext, listener)
                codecPlayer = player
                val wantsRenderer = !useDirectOutput() && directFallbackReason == null
                var target: Surface = surface
                if (wantsRenderer) {
                    try {
                        val initiallyPresentable = synchronized(presentationLock) {
                            activityResumed && outputSurface === surface && surface.isValid
                        }
                        val created = SrRenderer(
                            context.assets,
                            surface,
                            { id, stage, fields -> rendererEvent(id, stage, fields) },
                            initiallyPresentable,
                            earlySubmitReady = useEarlySubmit()
                        )
                        synchronized(rendererLock) { renderer = created }
                        synchronized(presentationLock) {
                            if (activityResumed && outputSurface === surface && surface.isValid) {
                                created.enablePresentation(surface)
                            } else {
                                created.disarmPresentation()
                            }
                        }
                        configuredWidth = 0
                        configuredHeight = 0
                        created.reset(activeGeneration)
                        player.textureOutput = true
                        target = created.decoderSurface
                        unsupportedReason = null
                        enhancementAvailable = true
                    } catch (e: Exception) {
                        player.textureOutput = false
                        unsupportedReason = "GLES unavailable; using direct Surface: ${e.message ?: e.javaClass.simpleName}"
                        enhancementAvailable = false
                        releaseRenderer()
                    }
                } else {
                    player.textureOutput = false
                    unsupportedReason = directFallbackReason ?:
                        "Direct Surface output; enhancement is unavailable"
                    enhancementAvailable = false
                }
                player.open(uri, target)
                if (!openState.current(request) || token != codecEpoch || !started ||
                    sourceUri != uri || outputSurface !== surface) {
                    closeCodec()
                    releaseRenderer()
                    return@post
                }
                if (savedMediaUs > 0L) player.seekTo(savedMediaUs)
                if (wasPlaying) player.play() else player.pause()
            } catch (e: Exception) {
                if (codecPlayer === player) closeCodec()
                main.post {
                    if (token == codecEpoch && openState.failed(request, e.message ?: e.javaClass.simpleName)) {
                        isPlaying = false
                        wasPlaying = false
                        refreshStatus()
                        onRunError(e.message ?: e.javaClass.simpleName)
                    }
                }
            }
        }
        updateMetadata(uri)
    }

    private fun closeSession() {
        pausedPreviewPtsUs = null
        isPlaying = false
        refreshStatus()
        host.select(policy.mode)
        openState.begin()
        ++codecEpoch
        pendingCloses.incrementAndGet()
        worker.post {
            try { closeCodec(); releaseRenderer() }
            finally { pendingCloses.decrementAndGet(); main.post { onChanged() } }
        }
    }

    private fun closeCodec() {
        val old = codecPlayer
        codecPlayer = null
        old?.close()
    }

    private fun releaseRenderer() {
        val old = synchronized(rendererLock) {
            val current = renderer
            renderer = null
            configuredWidth = 0
            configuredHeight = 0
            current
        }
        old?.close()
        synchronized(swapLock) { swaps.clear() }
    }

    private fun codecListener(token: Long, request: Long) = object : CodecPlayer.Listener {
        private var opened = false
        override fun format(width: Int, height: Int, frameRate: Int, colorStandard: Int, colorRange: Int, rotation: Int, colorTransfer: Int) {
            if (token != codecEpoch) return
            val eligibility = InputEligibility.evaluate(width, height, frameRate, colorStandard, colorTransfer)
            inputEligibility = eligibility
            val current = renderer
            if (current != null) unsupportedReason = eligibility.reason
            if (current != null && width > 0 && height > 0 && (width != configuredWidth || height != configuredHeight)) {
                synchronized(rendererLock) {
                    if (renderer === current && (width != configuredWidth || height != configuredHeight)) {
                        current.configure(width, height)
                        configuredWidth = width
                        configuredHeight = height
                    }
                }
            }
            opened = true
            main.post {
                if (token == codecEpoch && started && openState.opened(request, direct = current == null)) {
                    isPlaying = wasPlaying
                    refreshStatus()
                }
            }
        }

        override fun beforeFrame(ptsUs: Long, targetNs: Long): Boolean {
            if (token != codecEpoch) return false
            val generation = activeGeneration
            synchronized(frameLock) {
                val nominal = (ptsUs.coerceAtLeast(0L) / 1_000_000L) * 30L +
                    ((ptsUs.coerceAtLeast(0L) % 1_000_000L) * 30L + 500_000L) / 1_000_000L
                val ordinal = maxOf(nominal, lastAcceptedOrdinal + 1L).coerceAtMost(Int.MAX_VALUE.toLong())
                val id = FrameId(generation, 0, ordinal.toInt())
                val current = renderer
                val mode = policy.mode
                val requestedEnhancement = mode != PlaybackMode.ORIGINAL && inputEligibility.eligible
                val autoAllowed = mode != PlaybackMode.AUTO ||
                    autoCadence.shouldTry(generation, System.nanoTime())
                updateAutoCooldown(generation, !autoAllowed)
                val enhancement = requestedEnhancement && autoAllowed
                val accepted = if (current == null) true else synchronized(rendererLock) {
                    renderer === current && current.offer(
                        id,
                        ptsUs,
                        targetNs,
                        enhancement,
                        spatial = mode == PlaybackMode.SPATIAL && enhancement
                    )
                }
                if (!accepted) return false
                lastAcceptedOrdinal = ordinal
                main.post { if (token == codecEpoch && generation == activeGeneration) onFrameOffered(ptsUs, targetNs, id) }
                return true
            }
        }

        override fun tick(mediaUs: Long) {
            if (token != codecEpoch) return
            mediaPositionUs = mediaUs
            savedMediaUs = mediaUs
            val generation = activeGeneration
            main.post {
                if (token != codecEpoch || generation != activeGeneration) return@post
                onMediaPosition(mediaUs)
                onChanged()
            }
        }

        override fun ended() {
            if (token == codecEpoch) main.post { if (token == codecEpoch) { wasPlaying = false; isPlaying = false; refreshStatus(); onRunEnded() } }
        }

        override fun error(message: String) {
            if (token == codecEpoch) main.post {
                if (token != codecEpoch || !openState.current(request)) return@post
                // CodecPlayer.open reports configuration failure asynchronously.
                if (!opened && renderer != null) {
                    requestOpen("GLES output unavailable; using direct Surface: $message")
                } else if (openState.failed(request, message)) {
                    wasPlaying = false; isPlaying = false; refreshStatus(); onRunError(message)
                }
            }
        }
    }

    private fun rendererEvent(id: FrameId, stage: String, fields: Map<String, Any?>) {
        if (id.generation != activeGeneration) return
        val normalized = stage.lowercase(Locale.ROOT)
        if (normalized == "swap" && fields["pausedPreview"] != true && policy.mode == PlaybackMode.AUTO) {
            val reason = fields["reason"] as? String
            val nowNs = System.nanoTime()
            autoCadence.record(
                id.generation,
                fields["enhancementRequested"] as? Boolean ?: false,
                parseBackend(fields["submittedBackend"])?.name,
                reason,
                nowNs,
                late = reason?.contains("late", ignoreCase = true) == true
            )
            updateAutoCooldown(id.generation, !autoCadence.shouldTry(id.generation, nowNs))
        }
        if (normalized == "swap" && fields["reason"] != "swap_failed" && fields["pausedPreview"] == true) {
            val ptsUs = (fields["ptsUs"] as? Number)?.toLong()
            if (ptsUs != null) {
                pausedPreviewPtsUs = ptsUs
                main.post {
                    if (id.generation == activeGeneration && pausedPreviewPtsUs == ptsUs) refreshStatus()
                }
            }
        }
        if (normalized.contains("swap")) {
            val eglId = fields["eglFrameId"]?.toString()
            val backend = parseBackend(fields["submittedBackend"]) ?: parseBackend(fields["backend"])
            if (eglId != null && backend != null && fields["reason"] != "swap_failed") {
                synchronized(swapLock) {
                    val nowNs = System.nanoTime()
                    pruneSwaps(nowNs)
                    swaps.remove(id)
                    while (swaps.size >= MAX_PENDING_SWAPS) {
                        val iterator = swaps.entries.iterator()
                        if (!iterator.hasNext()) break
                        iterator.next()
                        iterator.remove()
                    }
                    swaps[id] = Swap(
                        eglId,
                        backend,
                        fields["enhancementRequested"] as? Boolean ?: (
                            backend == RealtimeSRBackend.CUNNY || backend == RealtimeSRBackend.SPATIAL
                        ),
                        nowNs
                    )
                }
            }
        } else if (normalized.contains("display")) {
            val displayNs = (fields["displayNs"] as? Number)?.toLong() ?: 0L
            val eglId = fields["eglFrameId"]?.toString()
            if (displayNs > 0L && eglId != null) {
                val matchedSwap = synchronized(swapLock) {
                    pruneSwaps(System.nanoTime())
                    val entry = swaps.entries.firstOrNull { it.value.eglFrameId == eglId }
                    if (entry == null) null else {
                        swaps.remove(entry.key)
                        entry.key to entry.value
                    }
                }
                if (matchedSwap != null && matchedSwap.first == id && id.generation == activeGeneration &&
                    host.onPresented(
                        matchedSwap.first.generation,
                        matchedSwap.first.sourceOrdinal.toLong(),
                        matchedSwap.second.backend,
                        matchedSwap.second.enhancement
                    )
                ) {
                    srRendererUsed = matchedSwap.second.backend == RealtimeSRBackend.CUNNY ||
                        matchedSwap.second.backend == RealtimeSRBackend.SPATIAL
                    main.post {
                        if (matchedSwap.first == id && id.generation == activeGeneration) refreshStatus()
                    }
                }
            }
        }
        main.post { if (id.generation == activeGeneration) onSrEvent(id, stage, fields) }
    }

    private fun updateAutoCooldown(generation: Long, coolingDown: Boolean) {
        if (generation != activeGeneration || policy.mode != PlaybackMode.AUTO || autoCoolingDown == coolingDown) return
        autoCoolingDown = coolingDown
        main.post {
            if (generation == activeGeneration && policy.mode == PlaybackMode.AUTO) refreshStatus()
        }
    }

    private fun pruneSwaps(nowNs: Long) {
        val iterator = swaps.entries.iterator()
        while (iterator.hasNext()) {
            if (nowNs - iterator.next().value.submittedNs > SWAP_STALE_NS) iterator.remove()
        }
    }

    private fun parseBackend(value: Any?): RealtimeSRBackend? = when (value) {
        is RealtimeSRBackend -> value
        is String -> runCatching { RealtimeSRBackend.valueOf(value.uppercase(Locale.ROOT)) }.getOrNull()
        else -> null
    }

    var statusText: String = "尚未选择视频"
        private set
    val hasVideo: Boolean get() = sourceUri != null
    val supportedModes get() = playbackControl.supportedModes()
    val selectedMode get() = playbackControl.selectedMode() ?: RealtimeSRMode.ORIGINAL
    val srStatus get() = openState.status(selectedMode, playbackControl.status())
    var srRendererUsed = false
        private set
    val cleanupPending: Boolean get() = pendingCloses.get() != 0

    fun selectRealtimeMode(mode: RealtimeSRMode): Boolean {
        if (mode !in supportedModes) return false
        selectMode(when (mode) {
            RealtimeSRMode.AUTO -> PlaybackMode.AUTO
            RealtimeSRMode.CUNNY -> PlaybackMode.CUNNY
            RealtimeSRMode.SPATIAL -> PlaybackMode.SPATIAL
            else -> PlaybackMode.ORIGINAL
        })
        return true
    }

    fun togglePlayback() {
        if (!hasVideo) return
        val shouldPlay = !isPlaying
        if (shouldPlay) pausedPreviewPtsUs = null
        isPlaying = shouldPlay
        wasPlaying = shouldPlay
        if (shouldPlay && durationUs > 0L && mediaPositionUs >= durationUs - 100_000L) {
            savedMediaUs = 0L
            mediaPositionUs = 0L
            requestOpen()
            return
        }
        worker.post {
            if (shouldPlay) { renderer?.setPreviewPaused(false); codecPlayer?.play() }
            else { codecPlayer?.pause(); renderer?.setPreviewPaused(true) }
        }
        refreshStatus()
    }

    fun seekTo(targetUs: Long) {
        savedMediaUs = targetUs.coerceAtLeast(0L)
        host.select(policy.mode)
        requestOpen()
    }

    fun leave() {
        started = false
        activityResumed = false
        wasPlaying = false
        isPlaying = false
        sourceUri = null
        openState.reset()
        metadataWarning = null
        metadataRequest.incrementAndGet()
        savedMediaUs = 0L
        mediaPositionUs = 0L
        durationUs = 0L
        renderer?.disarmPresentation()
        host.select(PlaybackMode.ORIGINAL)
        closeSession()
    }

    private fun refreshStatus() {
        if (!::host.isInitialized) return
        statusText = if (sourceUri == null) "尚未选择视频" else
            (openState.error ?: if (isPlaying) "播放中" else "已暂停") +
            (unsupportedReason?.let { " · $it" } ?: "") +
            (metadataWarning?.let { " · $it" } ?: "") +
            (if (policy.mode == PlaybackMode.AUTO && autoCoolingDown) " · 自动模式暂时使用原画" else "") +
            (pausedPreviewPtsUs?.let { " · 暂停预览 ${formatTime(it)}" } ?: "")
        onChanged()
    }

    private fun updateMetadata(uri: Uri) {
        val request = metadataRequest.incrementAndGet()
        worker.post {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.times(1000L) ?: 0L
                main.post {
                    if (request == metadataRequest.get() && sourceUri == uri) {
                        durationUs = duration
                        metadataWarning = null
                        refreshStatus()
                    }
                }
            } catch (e: Exception) {
                main.post {
                    if (request == metadataRequest.get() && sourceUri == uri) {
                        metadataWarning = "读取视频时长失败：${e.message ?: e.javaClass.simpleName}"
                        refreshStatus()
                        onRunError(metadataWarning!!)
                    }
                }
            } finally {
                retriever.release()
            }
        }
    }

    private fun formatTime(us: Long): String {
        val seconds = us.coerceAtLeast(0L) / 1_000_000L
        return "%02d:%02d".format(Locale.ROOT, seconds / 60L, seconds % 60L)
    }

    companion object {
        private const val MAX_PENDING_SWAPS = 64
        private const val SWAP_STALE_NS = 2_000_000_000L
    }
}
