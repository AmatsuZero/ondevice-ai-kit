package io.ondeviceai.realtimesr

import android.app.Activity
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import io.ondeviceai.binding.RealtimeSRBackend
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

open class PlayerActivity : Activity(), SurfaceHolder.Callback {
    protected open fun useDirectOutput(): Boolean = false
    protected open fun useEarlySubmit(): Boolean = false
    protected open fun onFrameOffered(ptsUs: Long, targetNs: Long, id: FrameId) {}
    protected open fun onMediaPosition(mediaUs: Long) {}
    protected open fun onRunEnded() {}
    protected open fun onRunError(message: String) {}
    protected open fun onSrEvent(id: FrameId, stage: String, fields: Map<String, Any?>) {}
    protected var srRendererUsed: Boolean = false

    protected open fun selectSource(uri: Uri) {
        sourceUri = uri
        savedMediaUs = 0L
        wasPlaying = true
        isPlaying = false
        lastAcceptedOrdinal = -1L
        host.select(policy.mode)
        requestOpen()
        updateMetadata(uri)
    }

    protected open fun selectMode(mode: PlaybackMode) {
        keepPreviewOnModeChange = !isPlaying
        try {
            host.select(mode)
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
    private lateinit var surfaceView: SurfaceView
    private lateinit var status: TextView
    private lateinit var positionLabel: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var playButton: Button
    @Volatile private var sourceUri: Uri? = null
    @Volatile private var outputSurface: Surface? = null
    @Volatile private var codecPlayer: CodecPlayer? = null
    @Volatile private var renderer: SrRenderer? = null
    @Volatile private var started = false
    @Volatile private var codecEpoch = 0L
    @Volatile private var activeGeneration = 0L
    @Volatile private var mediaPositionUs = 0L
    @Volatile private var savedMediaUs = 0L
    @Volatile private var durationUs = 0L
    @Volatile private var isPlaying = false
    @Volatile private var wasPlaying = false
    @Volatile private var inputEligibility = InputEligibility.evaluate(0, 0, 0, 0, 0)
    @Volatile private var keepPreviewOnModeChange = false
    @Volatile private var pausedPreviewPtsUs: Long? = null
    private val openRequest = AtomicLong()
    private var configuredWidth = 0
    private var configuredHeight = 0
    private var seekingUi = false
    private var lastAcceptedOrdinal = -1L
    private val frameLock = Any()
    private val rendererLock = Any()
    private val presentationLock = Any()
    @Volatile private var activityResumed = false
    private val swapLock = Any()
    private data class Swap(
        val eglFrameId: String,
        val backend: RealtimeSRBackend,
        val enhancement: Boolean,
        val submittedNs: Long
    )
    private val swaps = LinkedHashMap<FrameId, Swap>()
    private var unsupportedReason: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        buildUi()
        refreshStatus()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        surfaceView = SurfaceView(this).also { it.holder.addCallback(this) }
        root.addView(surfaceView, LinearLayout.LayoutParams(-1, 0, 1f))
        status = TextView(this).apply {
            setLines(3)
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        root.addView(status)
        positionLabel = TextView(this)
        root.addView(positionLabel)
        seekBar = SeekBar(this).apply {
            max = 1000
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(bar: SeekBar) { seekingUi = true }
                override fun onStopTrackingTouch(bar: SeekBar) {
                    seekingUi = false
                    val length = durationUs
                    if (length > 0L) {
                        val target = length * bar.progress / bar.max
                        savedMediaUs = target
                        wasPlaying = isPlaying
                        host.select(policy.mode)
                        requestOpen()
                    }
                }
            })
        }
        root.addView(seekBar)
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        Button(this).also { it.text = "Pick"; it.setOnClickListener { pickDocument() }; controls.addView(it) }
        playButton = Button(this).also { button ->
            button.text = "Play"
            button.setOnClickListener {
                val shouldPlay = !isPlaying
                if (shouldPlay) pausedPreviewPtsUs = null
                isPlaying = shouldPlay
                button.text = if (shouldPlay) "Pause" else "Play"
                worker.post {
                    if (shouldPlay) {
                        renderer?.setPreviewPaused(false)
                        codecPlayer?.play()
                    } else {
                        codecPlayer?.pause()
                        renderer?.setPreviewPaused(true)
                    }
                }
            }
            controls.addView(button)
        }
        root.addView(controls)
        val modes = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }
        listOf(
            "Original" to PlaybackMode.ORIGINAL,
            "Auto" to PlaybackMode.AUTO,
            "CuNNy" to PlaybackMode.CUNNY,
            "Spatial" to PlaybackMode.SPATIAL
        ).forEach { (label, mode) ->
            Button(this).also {
                it.text = label
                it.setOnClickListener { selectMode(mode) }
                modes.addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
        root.addView(modes)
        setContentView(root)
    }

    private fun pickDocument() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, PICK_VIDEO)
    }

    @Deprecated("Deprecated by Android; retained for a dependency-free Activity implementation")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_VIDEO && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) { }
                selectSource(uri)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        started = true
        if (outputSurface?.isValid == true && sourceUri != null) requestOpen()
    }

    override fun onResume() {
        super.onResume()
        synchronized(presentationLock) {
            activityResumed = true
            val surface = outputSurface
            if (surface?.isValid == true) renderer?.enablePresentation(surface)
        }
    }

    override fun onPause() {
        synchronized(presentationLock) {
            activityResumed = false
            renderer?.disarmPresentation()
        }
        super.onPause()
    }

    override fun onStop() {
        started = false
        wasPlaying = isPlaying
        savedMediaUs = mediaPositionUs
        synchronized(presentationLock) {
            activityResumed = false
            renderer?.disarmPresentation()
        }
        closeSession()
        super.onStop()
    }

    override fun onDestroy() {
        closeSession()
        workerThread.quitSafely()
        super.onDestroy()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        synchronized(presentationLock) { outputSurface = holder.surface }
        if (started && sourceUri != null) requestOpen()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        outputSurface = holder.surface
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        val current = synchronized(presentationLock) {
            outputSurface = null
            renderer?.also { it.disarmPresentation() }
        }
        current?.drainPresentation()
        wasPlaying = isPlaying
        savedMediaUs = mediaPositionUs
        closeSession()
    }

    private fun requestOpen() {
        pausedPreviewPtsUs = null
        refreshStatus()
        val uri = sourceUri ?: return
        val surface = outputSurface?.takeIf { it.isValid } ?: return
        host.select(policy.mode)
        val request = openRequest.incrementAndGet()
        val token = ++codecEpoch
        worker.post {
            closeCodec()
            releaseRenderer()
            if (request != openRequest.get() || !started || sourceUri != uri || outputSurface !== surface) return@post
            var player: CodecPlayer? = null
            try {
                val listener = codecListener(token)
                player = CodecPlayer(applicationContext, listener)
                codecPlayer = player
                val wantsRenderer = !useDirectOutput()
                var target: Surface = surface
                if (wantsRenderer) {
                    try {
                        val initiallyPresentable = synchronized(presentationLock) {
                            activityResumed && outputSurface === surface && surface.isValid
                        }
                        val created = SrRenderer(
                            assets,
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
                    } catch (e: Exception) {
                        player.textureOutput = false
                        unsupportedReason = "GLES unavailable; using direct Surface: ${e.message ?: e.javaClass.simpleName}"
                        releaseRenderer()
                    }
                } else {
                    player.textureOutput = false
                    if (useDirectOutput()) unsupportedReason = "Direct Surface output; enhancement is unavailable"
                }
                try {
                    player.open(uri, target)
                } catch (first: Exception) {
                    if (renderer == null) throw first
                    closeCodec()
                    releaseRenderer()
                    unsupportedReason = "GLES output unavailable; using direct Surface: ${first.message ?: first.javaClass.simpleName}"
                    player = CodecPlayer(applicationContext, listener)
                    codecPlayer = player
                    player.textureOutput = false
                    player.open(uri, surface)
                }
                if (savedMediaUs > 0L) player.seekTo(savedMediaUs)
                if (wasPlaying) { player.play(); isPlaying = true } else { player.pause(); isPlaying = false }
                main.post { playButton.text = if (isPlaying) "Pause" else "Play"; refreshStatus() }
            } catch (e: Exception) {
                if (codecPlayer === player) closeCodec()
                main.post { onRunError(e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    private fun closeSession() {
        pausedPreviewPtsUs = null
        refreshStatus()
        openRequest.incrementAndGet()
        ++codecEpoch
        worker.post {
            closeCodec()
            releaseRenderer()
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

    private fun codecListener(token: Long) = object : CodecPlayer.Listener {
        override fun format(width: Int, height: Int, frameRate: Int, colorStandard: Int, colorRange: Int, rotation: Int, colorTransfer: Int) {
            if (token != codecEpoch) return
            val eligibility = InputEligibility.evaluate(width, height, frameRate, colorStandard, colorTransfer)
            inputEligibility = eligibility
            unsupportedReason = eligibility.reason
            val current = renderer
            if (current != null && width > 0 && height > 0 && (width != configuredWidth || height != configuredHeight)) {
                synchronized(rendererLock) {
                    if (renderer === current && (width != configuredWidth || height != configuredHeight)) {
                        current.configure(width, height)
                        configuredWidth = width
                        configuredHeight = height
                    }
                }
            }
            main.post { if (token == codecEpoch) refreshStatus() }
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
                positionLabel.text = formatTime(mediaUs) + if (durationUs > 0L) " / ${formatTime(durationUs)}" else ""
                if (!seekingUi && durationUs > 0L) {
                    seekBar.progress = (mediaUs.coerceAtLeast(0L) * seekBar.max / durationUs).toInt().coerceIn(0, seekBar.max)
                }
            }
        }

        override fun ended() {
            if (token == codecEpoch) main.post { if (token == codecEpoch) { isPlaying = false; playButton.text = "Play"; onRunEnded() } }
        }

        override fun error(message: String) {
            if (token == codecEpoch) main.post { if (token == codecEpoch) onRunError(message) }
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

    private fun refreshStatus() {
        if (!::host.isInitialized || !::status.isInitialized) return
        val state = host.readStatus()
        val preferred = state.preferredBackend.name
        val actual = state.presentedBackend?.name ?: "no confirmed present"
        val note = unsupportedReason?.let { " — $it" }.orEmpty() +
            (if (policy.mode == PlaybackMode.AUTO && autoCoolingDown) " — Auto: temporarily using original" else "") +
            if (!isPlaying) " — paused" else ""
        val previewNote = pausedPreviewPtsUs?.let { " — paused preview submitted at ${formatTime(it)}" }.orEmpty()
        status.text = "Preferred: $preferred; actual: $actual$note$previewNote"
    }

    private fun updateMetadata(uri: Uri) {
        worker.post {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this, uri)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.times(1000L) ?: 0L
                durationUs = duration
                main.post {
                    seekBar.isEnabled = duration > 0L
                    seekBar.progress = 0
                    positionLabel.text = if (duration > 0L) "00:00 / ${formatTime(duration)}" else "Duration unavailable; seeking disabled"
                }
            } catch (e: Exception) {
                main.post { onRunError("Unable to read media duration: ${e.message ?: e.javaClass.simpleName}") }
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
        private const val PICK_VIDEO = 41
        private const val MAX_PENDING_SWAPS = 64
        private const val SWAP_STALE_NS = 2_000_000_000L
    }
}
