package io.ondeviceai.realtimesr

import android.content.res.AssetManager
import android.graphics.SurfaceTexture
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Surface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class SrRenderer(
    private val assets: AssetManager,
    private val window: Surface,
    private val onEvent: (FrameId, String, Map<String, Any?>) -> Unit,
    initiallyPresentable: Boolean = true
) : AutoCloseable {
    private data class Offer(val id: FrameId, val ptsUs: Long, val targetNs: Long, val enhance: Boolean, val spatial: Boolean)

    private data class PendingDisplay(
        val frameId: FrameId,
        val submittedNs: Long
    )

    private class Slot(val source: SrDraw.Source) {
        val monitor = java.lang.Object()
        var inUse = false
        var offer: Offer? = null
        var invalidated = false
        var captureFence = 0L
        var workerFence = 0L
        var renderFence = 0L
        var consumerFence = 0L
        var jobStarted = false
        var jobFinished = false
        var workerRetired = true
        var presentationComplete = true
        var decisionMade = true
        var resultTexture = 0
        var dispatchFailed = false
    }

    private val lock = Any()
    private val renderThread = HandlerThread("SrRenderer-render").apply { start() }
    private val workerThread = HandlerThread("SrRenderer-worker").apply { start() }
    private val renderHandler = Handler(renderThread.looper)
    private val workerHandler = Handler(workerThread.looper)
    private val binding = FrameBinding()
    private val preview = PausedPreview(assets)
    private val offers = LinkedHashMap<FrameId, Offer>()
    private val slots = mutableListOf<Slot>()
    private val displayFrames = LinkedHashMap<Long, PendingDisplay>()
    private val initFailure = AtomicReference<Throwable?>()
    private val workerFailure = AtomicReference<Throwable?>()

    @Volatile private var closed = false
    @Volatile private var presentationEnabled = initiallyPresentable
    private var workerBusy = false
    private var generation = 0L
    private var egl: SrEgl? = null
    private var draw: SrDraw? = null
    private var texture: SurfaceTexture? = null
    private var processor: GlesCunny? = null
    private var spatialProcessor: GlesCunny? = null
    private var spatialConfiguredWidth = 0
    private var spatialConfiguredHeight = 0
    private var timingEnabled = false
    private var requestedWidth = 0
    private var requestedHeight = 0
    private var configuredWidth = 0
    private var configuredHeight = 0
    private var workerConfiguredWidth = 0
    private var workerConfiguredHeight = 0
    private var skippedCaptures = 0L
    private var pollScheduled = false
    private var pollScheduledAtMs = 0L

    private val pollRunnable = Runnable {
        pollScheduled = false
        pollScheduledAtMs = 0L
        if (!closed) poll()
    }

    val decoderSurface: Surface
        get() = decoderSurfaceInternal

    private lateinit var decoderSurfaceInternal: Surface

    init {
        try {
            val renderReady = CountDownLatch(1)
            check(renderHandler.post {
                try {
                    val sharedEgl = SrEgl(window)
                    egl = sharedEgl
                    sharedEgl.renderCurrent()

                    val renderer = SrDraw()
                    draw = renderer
                    val surfaceTexture = SurfaceTexture(renderer.oes)
                    texture = surfaceTexture
                    decoderSurfaceInternal = Surface(surfaceTexture)
                    surfaceTexture.setOnFrameAvailableListener(
                        { onFrameAvailable() },
                        renderHandler
                    )
                    timingEnabled = EglTiming.enable()
                } catch (t: Throwable) {
                    initFailure.set(t)
                } finally {
                    renderReady.countDown()
                }
            })
            awaitInitialization(renderReady, "render initialization")

            val workerReady = CountDownLatch(1)
            check(workerHandler.post {
                try {
                    egl!!.workerCurrent()
                    processor = GlesCunny(assets)
                } catch (t: Throwable) {
                    initFailure.set(t)
                } finally {
                    workerReady.countDown()
                }
            })
            awaitInitialization(workerReady, "worker initialization")

            check(renderHandler.post { schedulePoll(0L) })
        } catch (t: Throwable) {
            closed = true
            cleanupFailedInitialization()
            throw t
        }
    }

    fun disarmPresentation() {
        synchronized(lock) { presentationEnabled = false }
    }

    fun drainPresentation() {
        val drained = CountDownLatch(1)
        if (!renderHandler.post { drained.countDown() }) {
            check(closed) { "render thread is unavailable" }
            return
        }
        check(drained.await(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "timed out waiting for presentation to stop"
        }
    }

    fun enablePresentation(expectedWindow: Surface) {
        synchronized(lock) {
            if (!closed && expectedWindow === window && expectedWindow.isValid) {
                presentationEnabled = true
            }
        }
    }

    private fun canPresent(): Boolean = !closed && presentationEnabled && window.isValid

    private fun skipPresentation(slot: Slot, offer: Offer, reason: String) {
        synchronized(lock) {
            slot.invalidated = true
            slot.presentationComplete = true
            decidePresentation(slot, null)
        }
        onEvent(offer.id, "capture_skipped", mapOf("reason" to reason))
    }

    fun configure(width: Int, height: Int) {
        require(width > 0 && height > 0) { "width and height must be positive" }
        runOnRenderSync {
            check(!closed) { "renderer is closed" }
            requestedWidth = width
            requestedHeight = height
            texture!!.setDefaultBufferSize(width, height)
            maybeApplyConfiguration()

            check(workerHandler.post {
                try {
                    processor!!.prepare(width, height)
                    synchronized(lock) {
                        workerConfiguredWidth = width
                        workerConfiguredHeight = height
                    }
                    workerFailure.set(null)
                } catch (t: Throwable) {
                    workerFailure.set(t)
                }
                schedulePollOnRender()
            }) { "worker thread is unavailable" }
        }
    }

    fun offer(id: FrameId, ptsUs: Long, targetNs: Long, enhance: Boolean, spatial: Boolean = false): Boolean {
        synchronized(lock) {
            if (closed || id.generation != generation ||
                requestedWidth == 0 || requestedHeight == 0
            ) {
                return false
            }
            if (!binding.offer(id, ptsUs)) return false
            offers[id] = Offer(id, ptsUs, targetNs, enhance, spatial)
            return true
        }
    }

    fun setPreviewPaused(enabled: Boolean) = runOnRenderSync {
        if (enabled) preview.freeze() else preview.resume()
        schedulePoll(0L)
    }

    fun reset(generation: Long, keepPreview: Boolean = false) {
        runOnRenderSync {
            if (!keepPreview || !preview.frozen) preview.clear()
            synchronized(lock) {
                this.generation = generation
                binding.reset(generation)
                offers.clear()
                displayFrames.clear()
                for (slot in slots) {
                    if (slot.offer?.id?.generation != generation) {
                        slot.invalidated = true
                        decidePresentation(slot, null)
                    }
                }
            }
            maybeApplyConfiguration()
            schedulePoll(0L)
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            binding.reset(generation + 1)
            offers.clear()
            for (slot in slots) {
                slot.invalidated = true
                decidePresentation(slot, null)
            }
        }

        var closeFailure: Throwable? = null
        try {
            runOnRenderSync {
                renderHandler.removeCallbacks(pollRunnable)
                pollScheduled = false
                texture?.setOnFrameAvailableListener(null)
                preview.clear()
            }

            val workerReleased = CountDownLatch(1)
            check(workerHandler.post {
                try {
                    processor?.close()
                } catch (t: Throwable) {
                    closeFailure = t
                }
                try {
                    spatialProcessor?.close()
                } catch (t: Throwable) {
                    val previous = closeFailure
                    if (previous == null) closeFailure = t else previous.addSuppressed(t)
                } finally {
                    try {
                        GLES30.glFinish()
                        egl?.releaseWorker()
                    } catch (t: Throwable) {
                        val previous = closeFailure
                        if (previous == null) closeFailure = t else previous.addSuppressed(t)
                    }
                    workerReleased.countDown()
                }
            }) { "worker thread is unavailable during close" }
            check(workerReleased.await(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                "timed out waiting for the SR worker to stop"
            }

            runOnRenderSync {
                GLES30.glFinish()
                preview.close()
                for (slot in slots) {
                    deleteSync(slot.captureFence)
                    deleteSync(slot.workerFence)
                    deleteSync(slot.renderFence)
                    deleteSync(slot.consumerFence)
                    draw?.release(slot.source)
                }
                slots.clear()
                if (::decoderSurfaceInternal.isInitialized) decoderSurfaceInternal.release()
                texture?.release()
                draw?.close()
                egl?.close()
            }
        } catch (t: Throwable) {
            val previous = closeFailure
            if (previous == null) closeFailure = t else previous.addSuppressed(t)
        } finally {
            workerThread.quitSafely()
            renderThread.quitSafely()
            try {
                workerThread.join(CLOSE_TIMEOUT_MILLIS)
                check(!workerThread.isAlive) { "timed out joining the SR worker" }
                if (Thread.currentThread() !== renderThread) {
                    renderThread.join(CLOSE_TIMEOUT_MILLIS)
                    check(!renderThread.isAlive) { "timed out joining the render thread" }
                }
            } catch (t: Throwable) {
                val previous = closeFailure
                if (previous == null) closeFailure = t else previous.addSuppressed(t)
            }
        }
        closeFailure?.let { throw IllegalStateException("SR renderer close failed", it) }
    }

    private fun onFrameAvailable() {
        if (closed) return

        val latchBeginNs = System.nanoTime()
        val callbackGeneration: Long
        val candidate: Offer?
        synchronized(lock) {
            callbackGeneration = generation
            candidate = offers.values.firstOrNull()
        }

        texture!!.updateTexImage()
        val timestampNs = texture!!.timestamp

        val offer: Offer?
        val mismatch: Offer?
        synchronized(lock) {
            val id = binding.confirm(callbackGeneration, timestampNs)
            offer = id?.let { offers.remove(it) }
            mismatch = if (id == null) candidate?.also { offers.remove(it.id) } else null
        }
        mismatch?.let {
            onEvent(it.id, "capture_skipped", mapOf("reason" to "timestamp_mismatch"))
        }
        val matchedOffer = offer ?: return

        onEvent(
            matchedOffer.id,
            "latch",
            mapOf(
                "latchBeginNs" to latchBeginNs,
                "targetNs" to matchedOffer.targetNs,
                "nextTargetNs" to matchedOffer.targetNs + FRAME_INTERVAL_NS
            )
        )

        val matrix = FloatArray(16)
        texture!!.getTransformMatrix(matrix)

        maybeApplyConfiguration()
        val renderer = draw!!
        val sourceSlot: Slot
        synchronized(lock) {
            if (configuredWidth != requestedWidth ||
                configuredHeight != requestedHeight
            ) {
                skippedCaptures++
                onEvent(
                    matchedOffer.id,
                    "capture_skipped",
                    mapOf(
                        "reason" to "configuration_pending",
                        "skippedCount" to skippedCaptures
                    )
                )
                return
            }

            sourceSlot = slots.firstOrNull {
                !it.inUse && it.source !== preview.pinned?.source
            } ?: run {
                skippedCaptures++
                onEvent(
                    matchedOffer.id,
                    "capture_skipped",
                    mapOf(
                        "reason" to "no_free_source_slot",
                        "skippedCount" to skippedCaptures
                    )
                )
                return
            }
            sourceSlot.inUse = true
            sourceSlot.offer = matchedOffer
            sourceSlot.invalidated = false
            sourceSlot.presentationComplete = false
            sourceSlot.jobStarted = false
            sourceSlot.jobFinished = true
            sourceSlot.workerRetired = true
            sourceSlot.decisionMade = false
            sourceSlot.resultTexture = 0
            sourceSlot.dispatchFailed = false
        }

        renderer.capture(sourceSlot.source, matrix)
        sourceSlot.captureFence = GLES30.glFenceSync(
            GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE,
            0
        )
        check(sourceSlot.captureFence != 0L) { "failed to create capture fence" }
        GLES30.glFlush()

        if (matchedOffer.enhance) {
            val prepared = matchedOffer.spatial || synchronized(lock) {
                workerConfiguredWidth == requestedWidth &&
                    workerConfiguredHeight == requestedHeight
            }
            val canSubmit = synchronized(lock) {
                if (!closed && prepared && !workerBusy) {
                    workerBusy = true
                    sourceSlot.jobStarted = true
                    sourceSlot.jobFinished = false
                    sourceSlot.workerRetired = false
                    true
                } else {
                    false
                }
            }

            if (canSubmit) {
                if (!workerHandler.post { runEnhancement(sourceSlot, matchedOffer) }) {
                    synchronized(lock) {
                        workerBusy = false
                        sourceSlot.jobStarted = false
                        sourceSlot.jobFinished = true
                        sourceSlot.workerRetired = true
                    }
                    onEvent(matchedOffer.id, "sr_skipped", mapOf("reason" to "worker_unavailable"))
                }
            } else {
                onEvent(
                    matchedOffer.id,
                    "sr_skipped",
                    mapOf("reason" to if (prepared) "worker_busy" else "configuration_pending")
                )
            }
        }

        schedulePoll(0L)
    }

    private fun runEnhancement(slot: Slot, offer: Offer) {
        var query = -1
        var outputTexture = 0
        var dispatchFailure: Throwable? = null
        var dispatched = false
        var workerFence = 0L
        var timingElapsedNs = 0L

        try {
            val captureFence = synchronized(lock) { slot.captureFence }
            if (captureFence != 0L) {
                GLES30.glWaitSync(captureFence, 0, GLES30.GL_TIMEOUT_IGNORED)
                GLES30.glDeleteSync(captureFence)
                synchronized(lock) {
                    if (slot.captureFence == captureFence) slot.captureFence = 0L
                }
            }

            val shouldDispatch = synchronized(lock) {
                !closed && !slot.invalidated && slot.offer === offer
            }
            if (shouldDispatch) {
                if (!offer.spatial) {
                    workerFailure.get()?.let {
                        throw IllegalStateException("SR preparation failed", it)
                    }
                }

                onEvent(offer.id, "srStart", emptyMap())
                query = EglTiming.startGpu()
                try {
                    outputTexture = if (offer.spatial) {
                        val spatial = spatialProcessor ?: GlesCunny(assets, spatialOnly = true).also {
                            spatialProcessor = it
                        }
                        if (spatialConfiguredWidth != slot.source.width ||
                            spatialConfiguredHeight != slot.source.height
                        ) {
                            try {
                                spatial.prepare(slot.source.width, slot.source.height)
                            } catch (t: Throwable) {
                                try {
                                    spatial.close()
                                } catch (closeError: Throwable) {
                                    t.addSuppressed(closeError)
                                }
                                spatialProcessor = null
                                spatialConfiguredWidth = 0
                                spatialConfiguredHeight = 0
                                throw t
                            }
                            spatialConfiguredWidth = slot.source.width
                            spatialConfiguredHeight = slot.source.height
                        }
                        spatial.dispatch(slot.source.tex)
                    } else {
                        processor!!.dispatch(slot.source.tex)
                    }
                    check(outputTexture != 0) { "SR dispatch returned an invalid texture" }
                    dispatched = true
                } finally {
                    if (query >= 0) EglTiming.endGpu(query)
                }

                workerFence = GLES30.glFenceSync(
                    GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE,
                    0
                )
                check(workerFence != 0L) { "failed to create worker fence" }
                GLES30.glFlush()
            }
        } catch (t: Throwable) {
            dispatchFailure = t
        }

        try {
            GLES30.glFinish()
        } catch (t: Throwable) {
            val previous = dispatchFailure
            if (previous == null) dispatchFailure = t else previous.addSuppressed(t)
        }

        if (query >= 0) {
            try {
                timingElapsedNs = EglTiming.elapsedGpuNs(query).coerceAtLeast(0L)
            } catch (t: Throwable) {
                val previous = dispatchFailure
                if (previous == null) dispatchFailure = t else previous.addSuppressed(t)
                timingElapsedNs = 0L
            } finally {
                EglTiming.deleteGpu(query)
            }
        }

        if (dispatchFailure != null || !dispatched) {
            if (workerFence != 0L) GLES30.glDeleteSync(workerFence)
            workerFence = 0L
            outputTexture = 0
        }

        synchronized(lock) {
            slot.resultTexture = outputTexture
            slot.workerFence = workerFence
            slot.jobFinished = true
            slot.dispatchFailed = dispatchFailure != null
        }

        if (dispatched) {
            val gpuTimingValid = dispatchFailure == null && query >= 0 && timingElapsedNs > 0L
            onEvent(
                offer.id,
                "srEnd",
                mapOf(
                    "gpuTimingValid" to gpuTimingValid,
                    "gpuElapsedNs" to if (gpuTimingValid) timingElapsedNs else 0L
                )
            )
        }
        dispatchFailure?.let {
            if (!offer.spatial) workerFailure.set(it)
            onEvent(offer.id, "sr_error", mapOf("message" to it.message))
        }

        if (dispatched && dispatchFailure == null) {
            var consumerFence = 0L
            synchronized(slot.monitor) {
                while (!slot.decisionMade && !closed) slot.monitor.wait()
                consumerFence = slot.consumerFence
            }
            if (consumerFence != 0L) {
                waitForSync(consumerFence)
                GLES30.glDeleteSync(consumerFence)
                synchronized(lock) {
                    if (slot.consumerFence == consumerFence) slot.consumerFence = 0L
                }
            }
        }

        synchronized(lock) {
            slot.workerRetired = true
            workerBusy = false
        }
        if (!closed) renderHandler.post { schedulePoll(0L) }
    }

    private fun poll() {
        if (closed) return

        pollDisplays()

        val nowNs = System.nanoTime()
        val snapshot = synchronized(lock) { slots.toList() }
        for (slot in snapshot) {
            val offer = synchronized(lock) {
                slot.offer?.takeIf { !slot.presentationComplete }
            }
            if (offer != null) {
                val invalid = synchronized(lock) {
                    slot.invalidated || closed || slot.offer !== offer ||
                        offer.id.generation != generation
                }
                if (invalid) {
                    synchronized(lock) {
                        slot.invalidated = true
                        if (!slot.presentationComplete) {
                            slot.presentationComplete = true
                            decidePresentation(slot, null)
                        }
                    }
                } else if (nowNs >= offer.targetNs - PRESENT_LEAD_NS) {
                    present(slot, offer)
                }
            }

            synchronized(lock) {
                if (!slot.jobStarted && slot.captureFence != 0L &&
                    isSignaled(slot.captureFence)
                ) {
                    deleteSync(slot.captureFence)
                    slot.captureFence = 0L
                }
                if (slot.presentationComplete && slot.workerFence != 0L &&
                    isSignaled(slot.workerFence)
                ) {
                    deleteSync(slot.workerFence)
                    slot.workerFence = 0L
                }
                if (slot.renderFence != 0L && isSignaled(slot.renderFence)) {
                    deleteSync(slot.renderFence)
                    slot.renderFence = 0L
                }

                if (slot.inUse && slot.presentationComplete &&
                    slot.workerRetired && slot.captureFence == 0L &&
                    slot.workerFence == 0L && slot.renderFence == 0L &&
                    slot.consumerFence == 0L
                ) {
                    slot.inUse = false
                    slot.offer = null
                    slot.jobStarted = false
                    slot.jobFinished = true
                    slot.resultTexture = 0
                    slot.dispatchFailed = false
                }
            }
        }

        maybeApplyConfiguration()
        scheduleNextPoll()
    }

    private fun present(slot: Slot, offer: Offer) {
        if (!canPresent()) {
            skipPresentation(
                slot,
                offer,
                if (window.isValid) "presentation_disabled" else "surface_invalid"
            )
            return
        }
        val valid = synchronized(lock) {
            !closed && !slot.invalidated && slot.offer === offer &&
                offer.id.generation == generation && !slot.presentationComplete
        }
        if (!valid) {
            synchronized(lock) {
                slot.invalidated = true
                slot.presentationComplete = true
                decidePresentation(slot, null)
            }
            return
        }
        if (preview.frozen) {
            synchronized(lock) {
                slot.invalidated = true
                slot.presentationComplete = true
                decidePresentation(slot, null)
            }
            return
        }

        var enhancedTexture = 0
        if (offer.enhance) {
            val workerState = synchronized(lock) {
                Triple(slot.jobFinished, slot.workerFence, slot.resultTexture)
            }
            if (workerState.first && workerState.second != 0L &&
                isSignaled(workerState.second)
            ) {
                enhancedTexture = workerState.third
            }
        }

        val renderer = draw!!
        val frameEglId = if (timingEnabled) EglTiming.nextId().takeIf { it >= 0 } else null
        val submittedBackend = when {
            enhancedTexture == 0 -> "ORIGINAL"
            offer.spatial -> "SPATIAL"
            else -> "CUNNY"
        }
        val reason = when {
            enhancedTexture != 0 -> "enhanced"
            !offer.enhance -> "enhancement_not_requested"
            synchronized(lock) { slot.dispatchFailed } -> "enhancement_failed"
            else -> "enhancement_not_ready"
        }

        if (!canPresent()) {
            skipPresentation(
                slot,
                offer,
                if (window.isValid) "presentation_disabled" else "surface_invalid"
            )
            return
        }
        val drawBeginNs = System.nanoTime()
        renderer.window(slot.source, enhancedTexture, egl!!.width(), egl!!.height())
        val attemptedSwap = canPresent()
        val swapBeginNs = System.nanoTime()
        val swapped = attemptedSwap && egl!!.swap(offer.targetNs)
        val swapEndNs = System.nanoTime()
        if (swapped) preview.remember(slot.source, offer.id, offer.ptsUs)

        slot.renderFence = GLES30.glFenceSync(
            GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE,
            0
        )
        check(slot.renderFence != 0L) { "failed to create render fence" }
        GLES30.glFlush()

        if (enhancedTexture != 0) {
            val consumerFence = GLES30.glFenceSync(
                GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE,
                0
            )
            check(consumerFence != 0L) { "failed to create consumer fence" }
            GLES30.glFlush()
            synchronized(lock) {
                slot.consumerFence = consumerFence
            }
        }

        synchronized(lock) {
            slot.presentationComplete = true
            decidePresentation(slot, slot.consumerFence.takeIf { enhancedTexture != 0 })
        }

        if (swapped && frameEglId != null && frameEglId >= 0) {
            while (displayFrames.size >= MAX_PENDING_DISPLAYS) {
                displayFrames.entries.iterator().run {
                    if (hasNext()) {
                        next()
                        remove()
                    }
                }
            }
            displayFrames[frameEglId] = PendingDisplay(offer.id, System.nanoTime())
        }

        if (attemptedSwap) {
            onEvent(
                offer.id,
                "swap",
                mapOf(
                    "submittedBackend" to submittedBackend,
                    "eglFrameId" to frameEglId,
                    "reason" to if (swapped) reason else "swap_failed",
                    "enhancementRequested" to offer.enhance,
                    "drawBeginNs" to drawBeginNs,
                    "swapBeginNs" to swapBeginNs,
                    "swapEndNs" to swapEndNs
                )
            )
        } else {
            onEvent(
                offer.id,
                "capture_skipped",
                mapOf("reason" to if (window.isValid) "presentation_disabled" else "surface_invalid")
            )
        }
    }

    fun redrawPaused(generation: Long, enhance: Boolean, spatial: Boolean = false): Boolean = runOnRenderSync {
        if (closed || generation != this.generation || !preview.frozen) {
            return@runOnRenderSync false
        }
        val pinned = preview.pinned ?: return@runOnRenderSync false
        if (!canPresent()) {
            onEvent(
                FrameId(generation, pinned.id.loop, pinned.id.sourceOrdinal),
                "capture_skipped",
                mapOf("reason" to if (window.isValid) "presentation_disabled" else "surface_invalid")
            )
            return@runOnRenderSync false
        }
        val previewFrameId = FrameId(generation, pinned.id.loop, pinned.id.sourceOrdinal)
        val enhancedTexture = if (enhance) preview.enhancedTexture(spatial) else 0
        val renderer = draw!!
        val frameEglId = if (timingEnabled) EglTiming.nextId().takeIf { it >= 0 } else null
        val submittedBackend = when {
            enhancedTexture == 0 -> "ORIGINAL"
            spatial -> "SPATIAL"
            else -> "CUNNY"
        }
        val reason = when {
            enhancedTexture != 0 -> "enhanced"
            !enhance -> "enhancement_not_requested"
            else -> "enhancement_not_ready"
        }

        val renderWidth = egl!!.width()
        val renderHeight = egl!!.height()
        val drawBeginNs = System.nanoTime()
        renderer.window(pinned.source, enhancedTexture, renderWidth, renderHeight)
        val attemptedSwap = canPresent()
        val swapBeginNs = System.nanoTime()
        val swapped = attemptedSwap && egl!!.swap(System.nanoTime())
        val swapEndNs = System.nanoTime()

        if (swapped && frameEglId != null && frameEglId >= 0) {
            while (displayFrames.size >= MAX_PENDING_DISPLAYS) {
                displayFrames.entries.iterator().run {
                    if (hasNext()) {
                        next()
                        remove()
                    }
                }
            }
            displayFrames[frameEglId] = PendingDisplay(previewFrameId, System.nanoTime())
            schedulePoll(0L)
        }

        if (attemptedSwap) {
            onEvent(
                previewFrameId,
                "swap",
                mapOf(
                    "submittedBackend" to submittedBackend,
                    "eglFrameId" to frameEglId,
                    "reason" to if (swapped) reason else "swap_failed",
                    "enhancementRequested" to enhance,
                    "drawBeginNs" to drawBeginNs,
                    "swapBeginNs" to swapBeginNs,
                    "swapEndNs" to swapEndNs,
                    "sourceOrdinal" to previewFrameId.sourceOrdinal,
                    "ptsUs" to pinned.ptsUs,
                    "pausedPreview" to true,
                    "renderWidth" to renderWidth,
                    "renderHeight" to renderHeight
                )
            )
        } else {
            onEvent(
                previewFrameId,
                "capture_skipped",
                mapOf("reason" to if (window.isValid) "presentation_disabled" else "surface_invalid")
            )
        }
        swapped
    }

    private fun pollDisplays() {
        val nowNs = System.nanoTime()
        val iterator = displayFrames.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val id = entry.key
            val pending = entry.value
            if (id < 0 || nowNs - pending.submittedNs > DISPLAY_STALE_NS) {
                iterator.remove()
                continue
            }

            val displayedNs = EglTiming.displayNs(id)
            if (displayedNs > 0L) {
                onEvent(
                    pending.frameId,
                    "display",
                    mapOf("eglFrameId" to id, "displayNs" to displayedNs)
                )
                iterator.remove()
            }
        }
    }

    private fun scheduleNextPoll() {
        if (closed) return
        val nowNs = System.nanoTime()
        var delayMs = POLL_INTERVAL_MILLIS
        val snapshot = synchronized(lock) { slots.toList() }
        for (slot in snapshot) {
            val offer = synchronized(lock) {
                slot.offer?.takeIf {
                    slot.inUse && !slot.presentationComplete && !slot.invalidated
                }
            } ?: continue
            val untilDeadlineMs = maxOf(
                0L,
                (offer.targetNs - nowNs - PRESENT_LEAD_NS) / NANOS_PER_MILLI
            )
            val adjustedDelay = if (untilDeadlineMs == 0L &&
                nowNs < offer.targetNs - PRESENT_LEAD_NS
            ) 1L else untilDeadlineMs
            delayMs = minOf(delayMs, adjustedDelay)
        }
        schedulePoll(delayMs)
    }

    private fun schedulePoll(delayMs: Long) {
        if (closed) return
        val requestedAt = android.os.SystemClock.uptimeMillis() + delayMs.coerceAtLeast(0L)
        if (pollScheduled && pollScheduledAtMs <= requestedAt) return
        if (pollScheduled) renderHandler.removeCallbacks(pollRunnable)
        pollScheduled = renderHandler.postDelayed(pollRunnable, delayMs.coerceAtLeast(0L))
        pollScheduledAtMs = if (pollScheduled) requestedAt else 0L
    }

    private fun schedulePollOnRender() {
        if (Looper.myLooper() == renderThread.looper) {
            schedulePoll(0L)
        } else if (!closed) {
            renderHandler.post { schedulePoll(0L) }
        }
    }

    private fun decidePresentation(slot: Slot, consumerFence: Long?) {
        synchronized(slot.monitor) {
            if (!slot.decisionMade) {
                if (consumerFence != null) slot.consumerFence = consumerFence
                slot.decisionMade = true
                slot.monitor.notifyAll()
            }
        }
    }

    private fun maybeApplyConfiguration() {
        if (requestedWidth == 0 || requestedHeight == 0) return
        if (configuredWidth == requestedWidth && configuredHeight == requestedHeight) return
        if (slots.any { it.inUse }) return

        val renderer = draw ?: return
        preview.clear()
        for (slot in slots) {
            deleteSync(slot.captureFence)
            deleteSync(slot.workerFence)
            deleteSync(slot.renderFence)
            deleteSync(slot.consumerFence)
            renderer.release(slot.source)
        }
        slots.clear()
        repeat(SOURCE_SLOT_COUNT) {
            slots += Slot(renderer.allocate(requestedWidth, requestedHeight))
        }
        configuredWidth = requestedWidth
        configuredHeight = requestedHeight
    }

    private fun waitForSync(sync: Long) {
        while (true) {
            val result = GLES30.glClientWaitSync(
                sync,
                GLES30.GL_SYNC_FLUSH_COMMANDS_BIT,
                SYNC_WAIT_NS
            )
            if (result == GLES30.GL_ALREADY_SIGNALED ||
                result == GLES30.GL_CONDITION_SATISFIED
            ) {
                return
            }
            check(result != GLES30.GL_WAIT_FAILED) { "failed waiting for GL sync" }
        }
    }

    private fun isSignaled(sync: Long): Boolean {
        if (sync == 0L) return true
        val result = GLES30.glClientWaitSync(sync, 0, 0L)
        return result == GLES30.GL_ALREADY_SIGNALED ||
            result == GLES30.GL_CONDITION_SATISFIED
    }

    private fun deleteSync(sync: Long) {
        if (sync != 0L) GLES30.glDeleteSync(sync)
    }

    private fun <T> runOnRenderSync(block: () -> T): T {
        if (Looper.myLooper() == renderThread.looper) return block()
        val task = FutureTask(block)
        check(renderHandler.post(task)) { "render thread is unavailable" }
        return try {
            task.get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (t: ExecutionException) {
            throw (t.cause ?: t)
        }
    }

    private fun awaitInitialization(latch: CountDownLatch, operation: String) {
        check(latch.await(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            "timed out waiting for $operation"
        }
        initFailure.get()?.let { throw IllegalStateException("$operation failed", it) }
    }

    private fun cleanupFailedInitialization() {
        try {
            val workerDone = CountDownLatch(1)
            if (workerHandler.post {
                    try {
                        processor?.close()
                    } finally {
                        try {
                            egl?.releaseWorker()
                        } catch (_: Throwable) {
                        }
                        workerDone.countDown()
                    }
                }
            ) {
                workerDone.await(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
        } catch (_: Throwable) {
        }

        try {
            val renderDone = CountDownLatch(1)
            if (renderHandler.post {
                    try {
                        texture?.setOnFrameAvailableListener(null)
                        if (::decoderSurfaceInternal.isInitialized) {
                            decoderSurfaceInternal.release()
                        }
                        texture?.release()
                        draw?.close()
                        egl?.close()
                    } finally {
                        renderDone.countDown()
                    }
                }
            ) {
                renderDone.await(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
        } catch (_: Throwable) {
        }

        workerThread.quitSafely()
        renderThread.quitSafely()
        try {
            workerThread.join(CLOSE_TIMEOUT_MILLIS)
            renderThread.join(CLOSE_TIMEOUT_MILLIS)
        } catch (_: Throwable) {
        }
    }

    private companion object {
        const val SOURCE_SLOT_COUNT = 3
        const val POLL_INTERVAL_MILLIS = 4L
        const val CLOSE_TIMEOUT_SECONDS = 10L
        const val CLOSE_TIMEOUT_MILLIS = 10_000L
        const val PRESENT_LEAD_NS = 2_000_000L
        const val FRAME_INTERVAL_NS = 33_333_333L
        const val NANOS_PER_MILLI = 1_000_000L
        const val SYNC_WAIT_NS = 1_000_000L
        const val MAX_PENDING_DISPLAYS = 64
        const val DISPLAY_STALE_NS = 2_000_000_000L
    }
}
