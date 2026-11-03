package io.ondeviceai.litertsr
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import io.ondeviceai.core.*
class LiteRtSrModelTest {
 @Test fun noWeightsIsExplicitlyUnavailableAndCloseRejectsFutureUse() = runBlocking {
  val root = Files.createTempDirectory("sr-unavailable").toFile()
  try {
   val backend = LiteRtVideoSuperResolutionBackend(root.resolve("absent"), root.resolve("outputs"))
   assertEquals(UnavailableReason.MISSING_ASSETS, backend.availability().reason)
   assertFalse(root.resolve("outputs").exists())
   backend.close()
   assertEquals(UnavailableReason.CLOSED, backend.availability().reason)
  } finally { root.deleteRecursively() }
 }

 @Test fun missingOrSubstitutedWeightsCannotBecomeAvailableOrReplaceInstalledModel() {
  val root = Files.createTempDirectory("sr-model").toFile()
  try {
   val destination = root.resolve("installed.tflite")
   assertFalse(LiteRtSrModel.valid(destination))
   destination.writeText("old")
   val substitute = root.resolve("bad.tflite").apply { writeText("not trained weights") }
   assertFails { LiteRtSrModel.install(substitute, destination) }
   assertEquals("old", destination.readText())
  } finally { root.deleteRecursively() }
 }
}
