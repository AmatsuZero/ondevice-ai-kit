package io.ondeviceai.litertsr
import kotlin.test.*
class VideoColorTest {
 @Test fun limitedBt709BlackAndWhiteAndRedRoundTrip() {
  assertEquals(0xff000000.toInt(), VideoColor.rgb(16, 128, 128))
  assertEquals(0xffffffff.toInt(), VideoColor.rgb(235, 128, 128))
  val red = VideoColor.yuv(0xffff0000.toInt())
  val roundTrip = VideoColor.rgb(red[0], red[1], red[2])
  assertTrue(((roundTrip shr 16) and 255) >= 253)
  assertTrue(((roundTrip shr 8) and 255) <= 2)
  assertTrue((roundTrip and 255) <= 2)
 }
}
