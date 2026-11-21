package io.ondeviceai.demo
import kotlin.test.*
class ImageSamplingTest {
 @Test fun retainsSmallImagesAndDownsamplesLargeImages() {
  assertEquals(1, imageSampleSize(640, 480))
  assertEquals(2, imageSampleSize(4096, 2048))
  assertEquals(4, imageSampleSize(8192, 6000))
 }
 @Test fun rejectsInvalidDimensions() { assertFailsWith<IllegalArgumentException>{imageSampleSize(0,100)} }
}
