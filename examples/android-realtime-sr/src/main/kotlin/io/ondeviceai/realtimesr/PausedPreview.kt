package io.ondeviceai.realtimesr

import android.content.res.AssetManager

class PausedPreview(private val assets: AssetManager) : AutoCloseable {
    data class Sample(val source: SrDraw.Source, val id: FrameId, val ptsUs: Long)

    var pinned: Sample? = null
        private set

    var frozen: Boolean = false
        private set

    private var processor: GlesCunny? = null
    private var cachedTexture: Int? = null
    private var cachedSpatial: Boolean? = null

    fun remember(source: SrDraw.Source, id: FrameId, ptsUs: Long) {
        if (frozen) return

        val sample = Sample(source, id, ptsUs)
        if (pinned != sample) {
            releasePreview()
            pinned = sample
        }
    }

    fun freeze() {
        frozen = pinned != null
    }

    fun resume() {
        frozen = false
        releasePreview()
    }

    fun clear() {
        pinned = null
        frozen = false
        releasePreview()
    }

    fun enhancedTexture(spatial: Boolean = false): Int {
        val sample = pinned ?: return 0
        if (cachedSpatial != null && cachedSpatial != spatial) releasePreview()
        cachedTexture?.let { return it }

        return try {
            val previewProcessor = processor ?: (if (spatial) {
                GlesCunny(assets, spatialOnly = true)
            } else {
                GlesCunny(assets)
            }).also { processor = it }
            previewProcessor.prepare(sample.source.width, sample.source.height)
            previewProcessor.dispatch(sample.source.tex).also {
                cachedTexture = it
                cachedSpatial = spatial
            }
        } catch (_: Exception) {
            releasePreview()
            0
        }
    }

    override fun close() {
        clear()
    }

    private fun releasePreview() {
        cachedTexture = null
        cachedSpatial = null
        val previewProcessor = processor
        processor = null
        previewProcessor?.close()
    }
}
