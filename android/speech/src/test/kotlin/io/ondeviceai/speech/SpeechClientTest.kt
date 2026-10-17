package io.ondeviceai.speech
import io.ondeviceai.core.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*
private class Fake(override val backendId:String, private val ok:Boolean=true, override val capability: SpeechCapability = SpeechCapability.TTS): SpeechBackend {
 var closes=0
 override suspend fun availability()=CapabilityAvailability(if(ok) CapabilityStatus.AVAILABLE else CapabilityStatus.UNAVAILABLE, if(ok)null else UnavailableReason.UNSUPPORTED_DEVICE, backendId, offline=!backendId.contains("cloud"))
 override suspend fun close(){closes++}
}
class SpeechClientTest {
 @Test fun explicitSelectionIsStrictAndCloseIdempotent()=runBlocking {
  val local=Fake("apple.avspeech"); val cloud=Fake("microsoft.azure.speech",false)
  val client=SpeechClient(SpeechCapability.TTS,local.backendId,listOf(SpeechBackendRegistration(SpeechCapability.TTS,local),SpeechBackendRegistration(SpeechCapability.TTS,cloud)))
  assertEquals(local.backendId,client.select().backendId)
  assertEquals(listOf("apple.avspeech","microsoft.azure.speech"),client.listBackends().map{it.backendId})
  assertFailsWith<SpeechClientError.BackendUnavailable>{client.select(SpeechBackendSelection.explicit(cloud.backendId))}
  assertFailsWith<SpeechClientError.BackendNotRegistered>{client.select(SpeechBackendSelection.explicit("missing"))}
  client.close(); client.close(); assertFailsWith<IllegalStateException>{client.select()}; assertEquals(1,local.closes); assertEquals(1,cloud.closes)
 }
 @Test fun duplicateAndWrongCapabilityRejected(){
  val b=Fake("apple.avspeech"); val r=SpeechBackendRegistration(SpeechCapability.TTS,b)
  assertFailsWith<IllegalArgumentException>{SpeechClient(SpeechCapability.TTS,b.backendId,listOf(r,r))}
  assertFailsWith<IllegalArgumentException>{SpeechClient(SpeechCapability.ASR,b.backendId,listOf(SpeechBackendRegistration(SpeechCapability.TTS,b)))}
  val asr=Fake("android.speech.recognizer", capability=SpeechCapability.ASR)
  SpeechClient(SpeechCapability.ASR,asr.backendId,listOf(SpeechBackendRegistration(SpeechCapability.ASR,asr)))
  assertFailsWith<IllegalArgumentException>{SpeechClient(SpeechCapability.ASR,b.backendId,listOf(SpeechBackendRegistration(SpeechCapability.ASR,b)))}
 }
}
