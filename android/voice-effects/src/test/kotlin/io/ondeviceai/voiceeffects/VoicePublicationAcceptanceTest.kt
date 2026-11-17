package io.ondeviceai.voiceeffects
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.*
import org.junit.Test
import kotlin.test.*
class VoicePublicationAcceptanceTest {
 @Test fun privateReservationContainsClosedWaveAndIsRemovedOnRelease() = runBlocking {
  val root=Files.createTempDirectory("voice-acceptance").toFile()
  try {
   val input=File(root,"input.wav").apply { writeBytes(wave()) }
   val out=File(root,"out");val client=VoiceEffectsClient(WindowedPitchVoiceEffectsBackend(),out)
   val result=client.process(VoiceEffectsRequest(input,VoiceEffectsPreset.ORIGINAL))
   assertEquals(File(out,result.id+".wav"),result.audioFile.parentFile)
   assertEquals("audio.wav",result.audioFile.name);assertContentEquals(input.readBytes(),result.audioFile.readBytes())
   client.close();assertTrue(result.audioFile.exists());client.releaseOutput(result.id);assertTrue(out.listFiles()!!.isEmpty())
  } finally {root.deleteRecursively()}
 }
 @Test fun reservationDirectoryDeletionFailureRetainsOwnershipForRetry() = runBlocking {
  val root=Files.createTempDirectory("voice-acceptance").toFile()
  try {
   val input=File(root,"input.wav").apply {writeBytes(wave())};val out=File(root,"out")
   var denied=true
   val files=object:VoiceEffectsFiles() {
    override fun delete(file:File) {if(denied && file.isDirectory)throw IOException("deny reservation delete");super.delete(file)}
   }
   val client=VoiceEffectsClient(WindowedPitchVoiceEffectsBackend(),out,files)
   val result=client.process(VoiceEffectsRequest(input,VoiceEffectsPreset.ORIGINAL));client.close()
   assertFailsWith<VoiceEffectsError.Platform>{client.releaseOutput(result.id)}
   assertFalse(result.audioFile.exists());assertTrue(checkNotNull(result.audioFile.parentFile).exists())
   denied=false;client.releaseOutput(result.id);assertTrue(out.listFiles()!!.isEmpty())
  } finally {root.deleteRecursively()}
 }
}
