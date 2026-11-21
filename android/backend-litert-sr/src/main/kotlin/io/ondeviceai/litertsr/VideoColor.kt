package io.ondeviceai.litertsr

import kotlin.math.roundToInt

/** SDR BT.709, limited-range 8-bit YUV ↔ RGB. */
internal object VideoColor {
    fun rgb(y: Int, u: Int, v: Int): Int {
        val luma = (y - 16) * (255.0 / 219)
        val cb = (u - 128) * (255.0 / 224)
        val cr = (v - 128) * (255.0 / 224)
        fun clip(value: Double) = value.roundToInt().coerceIn(0, 255)
        return (255 shl 24) or (clip(luma + 1.5748 * cr) shl 16) or
            (clip(luma - 0.187324 * cb - 0.468124 * cr) shl 8) or clip(luma + 1.8556 * cb)
    }
    fun yuv(pixel: Int): IntArray {
        val r = (pixel shr 16) and 255; val g = (pixel shr 8) and 255; val b = pixel and 255
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        return intArrayOf((16 + y * 219 / 255).roundToInt().coerceIn(16, 235),
            (128 + (b - y) / 1.8556 * 224 / 255).roundToInt().coerceIn(16, 240),
            (128 + (r - y) / 1.5748 * 224 / 255).roundToInt().coerceIn(16, 240))
    }
}
