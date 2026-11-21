package io.ondeviceai.litertsr

/** Fixed 50×50 model tiles with an 8-pixel halo; never downscale the source frame. */
internal object EsrganTiles {
    fun upscale(source: IntArray, width: Int, height: Int, checkpoint: () -> Unit,
                infer: (IntArray) -> IntArray): IntArray {
        require(width > 0 && height > 0 && source.size == width * height)
        val result = IntArray(width * height * 16)
        for (top in 0 until height step 34) for (left in 0 until width step 34) {
            checkpoint()
            val tile = IntArray(50 * 50) { index ->
                val x = (left + index % 50 - 8).coerceIn(0, width - 1)
                val y = (top + index / 50 - 8).coerceIn(0, height - 1)
                source[y * width + x]
            }
            val output = infer(tile)
            check(output.size == 200 * 200)
            val count = minOf(34, width - left) * 4
            for (y in 0 until minOf(34, height - top) * 4)
                output.copyInto(result, (top * 4 + y) * width * 4 + left * 4, (y + 32) * 200 + 32, (y + 32) * 200 + 32 + count)
        }
        return result
    }
}
