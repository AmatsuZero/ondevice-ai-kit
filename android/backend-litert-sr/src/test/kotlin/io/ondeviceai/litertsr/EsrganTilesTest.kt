package io.ondeviceai.litertsr

import kotlin.test.*

class EsrganTilesTest {
    @Test fun tiledRgbPreservesPlacementAndIncludesEdgesWithoutResizingInput() {
        val width = 71; val height = 37
        val source = IntArray(width * height) { 0xff000000.toInt() or it }
        var calls = 0
        val output = EsrganTiles.upscale(source, width, height, checkpoint = {}) { input ->
            calls++
            IntArray(200 * 200) { index -> input[(index / 200 / 4) * 50 + (index % 200 / 4)] }
        }
        assertEquals(6, calls)
        assertEquals(width * height * 16, output.size)
        for (y in 0 until height * 4) for (x in 0 until width * 4)
            assertEquals(source[(y / 4) * width + x / 4], output[y * width * 4 + x])
    }
}
