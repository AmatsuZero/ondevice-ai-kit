package io.ondeviceai.probe.litertlm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.google.ai.edge.litertlm.Role

class LiteRtLmProbeInstrumentationTest {
    @Test
    fun inputMappingUsesRealLiteRtLmContentTypes() {
        val message = LiteRtLmProbe.userMessage("/data/local/tmp/fixture.png", "describe the image")
        assertEquals(Role.USER, message.role)
        val input = message.contents
        assertEquals(2, input.contents.size)
        assertTrue(input.contents[0] is com.google.ai.edge.litertlm.Content.ImageFile)
        assertEquals("/data/local/tmp/fixture.png", (input.contents[0] as com.google.ai.edge.litertlm.Content.ImageFile).absolutePath)
        assertTrue(input.contents[1] is com.google.ai.edge.litertlm.Content.Text)
        assertEquals("describe the image", (input.contents[1] as com.google.ai.edge.litertlm.Content.Text).text)
    }
}
