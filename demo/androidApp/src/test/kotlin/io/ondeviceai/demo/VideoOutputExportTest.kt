package io.ondeviceai.demo
import java.nio.file.Files
import kotlin.test.*
class VideoOutputExportTest {
 @Test fun openReadSurvivesLeaseRemovalBeforeAsyncCopy() {
  val root = Files.createTempDirectory("video-export-reader").toFile()
  try {
   val source = root.resolve("source.mp4").apply { writeBytes(byteArrayOf(4,5,6)) }
   val reader = source.inputStream()
   source.delete()
   val copy = reader.use { VideoOutputExport.snapshot(it, root.resolve("exports")) }
   assertContentEquals(byteArrayOf(4,5,6), copy.readBytes())
  } finally { root.deleteRecursively() }
 }
 @Test fun exportCopySurvivesOriginalLeaseRemoval() {
  val root = Files.createTempDirectory("video-export").toFile()
  try {
   val source = root.resolve("source.mp4").apply { writeBytes(byteArrayOf(1,2,3)) }
   val snapshot = VideoOutputExport.snapshot(source, root.resolve("exports"))
   source.delete()
   assertContentEquals(byteArrayOf(1,2,3), snapshot.readBytes())
   assertEquals("mp4", snapshot.extension)
  } finally { root.deleteRecursively() }
 }
}
