package io.ondeviceai.demo

import io.ondeviceai.binding.*
import io.ondeviceai.voiceeffects.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import kotlin.test.*

class VoiceEffectsBridgeTest {
    @Test fun allPresetsUseTheRealBackendAndPreserveFrames() = checkBridge { root ->
        val samples = ShortArray(16000) { (12000*kotlin.math.sin(2*Math.PI*440*it/16000)).toInt().toShort() }
        val file = File(root,"tone.wav").apply { writeBytes(VoiceEffectsWaveTest.wave(16000,samples)) }
        val bridge = bridge(root,file) {}
        for (preset in VoiceEffectsBridgePreset.entries) {
            val reply = ProcessReply(); bridge.process(request(preset),reply); val output = reply.value.await()
            assertEquals(16000,output.sampleRate); assertEquals(16000,output.frameCount); assertEquals("android.windowed-pitch",output.backendId)
            val bytes = checkNotNull(bridge.outputFile(output.reference)).readBytes()
            if (preset == VoiceEffectsBridgePreset.ORIGINAL) assertContentEquals(file.readBytes(),bytes)
            else assertFalse(file.readBytes().contentEquals(bytes))
            val release = ReleaseReply(); bridge.releaseOutput(output.reference,release); assertNull(release.value.await())
        }
        val close = CloseReply(); bridge.close(close); close.value.await()
    }
    private fun checkBridge(body: suspend CoroutineScope.(File) -> Unit) {
        Executors.newSingleThreadExecutor { Thread(it,"voice-ui-test") }.asCoroutineDispatcher().use { ui ->
            val root = Files.createTempDirectory("voice-bridge-").toFile()
            try { runBlocking(ui) { withTimeout(10_000) { body(root) } } }
            finally { root.deleteRecursively() }
        }
    }
    @Test fun realOutputSurvivesCloseAndReleaseFailureCanRetry() = checkBridge { root ->
        val file = File(root,"in.wav").apply { writeBytes(VoiceEffectsWaveTest.wave(16000,shortArrayOf(1,2,3))) }
        var released = 0
        val bridge = bridge(root,file) { released++ }
        val callback = ProcessReply(); bridge.process(request(),callback)
        val output = callback.value.await(); assertEquals(1,released)
        val result = checkNotNull(bridge.outputFile(output.reference)); assertContentEquals(file.readBytes(),result.readBytes())
        val close = CloseReply(); bridge.close(close); close.value.await(); assertTrue(result.exists())
        val reject = ProcessReply(); bridge.process(request(),reject); assertEquals(VoiceEffectsBridgeFailureCode.CLOSED,reject.failure!!.code)
        val saved = File(root,"saved.wav"); check(result.renameTo(saved)); check(result.mkdir())
        File(result,"child").writeText("owned test obstacle")
        val failed = ReleaseReply(); bridge.releaseOutput(output.reference,failed)
        assertNotNull(failed.value.await()); assertEquals(result,bridge.outputFile(output.reference))
        check(File(result,"child").delete()); check(result.delete()); check(saved.renameTo(result))
        val release = ReleaseReply(); bridge.releaseOutput(output.reference,release); assertNull(release.value.await()); assertFalse(result.exists())
        val again = ReleaseReply(); bridge.releaseOutput(output.reference,again); assertNull(again.value.await())
        val unknown = ReleaseReply(); bridge.releaseOutput(file.path,unknown); assertEquals(VoiceEffectsBridgeFailureCode.INVALID_INPUT,unknown.value.await()!!.code); assertTrue(file.exists())
    }
    @Test fun cancelAndConcurrentCloseWaitForActualNativeDrain() = checkBridge { root ->
        val entered = CompletableDeferred<Unit>(); val gate = CompletableDeferred<Unit>(); var drained = false; var inputReleased = false
        val backend = object : VoiceEffectsBackend {
            override val backendId = "android.windowed-pitch"
            override suspend fun render(input: FloatArray,sampleRate: Int,preset: VoiceEffectsPreset): FloatArray {
                entered.complete(Unit); withContext(NonCancellable) { gate.await() }; return input
            }
            override suspend fun drain() { drained = true }
        }
        val file = File(root,"in.wav").apply { writeBytes(VoiceEffectsWaveTest.wave(16000,ShortArray(1600))) }
        val bridge = bridge(root,file,backend) { assertTrue(drained); inputReleased = true }
        val reply = ProcessReply(); val task = bridge.process(request(VoiceEffectsBridgePreset.HIGH),reply)
        entered.await(); val busy = ProcessReply(); bridge.process(request(),busy); assertEquals(VoiceEffectsBridgeFailureCode.BUSY,busy.failure!!.code)
        task.cancel(); val a = CloseReply(); val b = CloseReply(); bridge.close(a); bridge.close(b)
        yield(); assertFalse(inputReleased); assertEquals(0,reply.terminals); assertFalse(a.value.isCompleted)
        gate.complete(Unit); a.value.await(); b.value.await(); assertTrue(inputReleased); assertEquals(1,reply.terminals); assertTrue(reply.cancelled)
    }
    @Test fun failedInputLeaseReleaseWaitsForRetryInsteadOfLosingOwnership() = checkBridge { root ->
        val file = File(root,"in.wav").apply { writeBytes(VoiceEffectsWaveTest.wave(16000,shortArrayOf(1))) }
        var deny = true; var attempts = 0; val errors = mutableListOf<Throwable>()
        val scope = CoroutineScope(coroutineContext + CoroutineExceptionHandler { _, e -> errors += e })
        val bridge = AndroidVoiceEffectsBridge(VoiceEffectsClient(WindowedPitchVoiceEffectsBackend(),File(root,"out")),
            object : AndroidVoiceEffectsInputProvider {
                override fun acquire(reference: String) = object : AndroidVoiceEffectsInputLease {
                    override val file = file
                    override suspend fun release() { attempts++; if (deny) error("private path must never escape") }
                }
            },scope)
        val reply = ProcessReply(); val task = bridge.process(request(),reply)
        while (attempts == 0) delay(1)
        assertTrue(errors.isEmpty(),"cleanup failure must stay in the retained operation")
        assertEquals(0,reply.terminals); deny = false; task.cancel()
        while (reply.terminals == 0) delay(1)
        assertTrue(reply.cancelled); assertTrue(attempts >= 2)
        val close = CloseReply(); bridge.close(close); close.value.await()
    }
    private fun CoroutineScope.bridge(root: File,file: File,backend: VoiceEffectsBackend = WindowedPitchVoiceEffectsBackend(),release: suspend () -> Unit): AndroidVoiceEffectsBridge =
        AndroidVoiceEffectsBridge(VoiceEffectsClient(backend,File(root,"out")), object : AndroidVoiceEffectsInputProvider {
            override fun acquire(reference: String) = object : AndroidVoiceEffectsInputLease {
                override val file = file
                override suspend fun release() = release()
            }
        },this)
    private fun request(preset: VoiceEffectsBridgePreset = VoiceEffectsBridgePreset.ORIGINAL) = VoiceEffectsBridgeRequest("input",preset)
    private class ProcessReply : VoiceEffectsBridgeProcessCallback {
        private val owner = Thread.currentThread()
        val value = CompletableDeferred<VoiceEffectsBridgeOutput>(); var failure: VoiceEffectsBridgeFailure? = null
        var terminals = 0; var cancelled = false
        override fun onResult(output: VoiceEffectsBridgeOutput) { assertSame(owner,Thread.currentThread()); terminals++; value.complete(output) }
        override fun onFailure(failure: VoiceEffectsBridgeFailure) { terminals++; this.failure = failure }
        override fun onCancelled() { terminals++; cancelled = true }
    }
    private class ReleaseReply : VoiceEffectsBridgeReleaseCallback {
        val value = CompletableDeferred<VoiceEffectsBridgeFailure?>()
        override fun onReleased() { value.complete(null) }
        override fun onFailure(failure: VoiceEffectsBridgeFailure) { value.complete(failure) }
    }
    private class CloseReply : VoiceEffectsBridgeCloseCallback {
        val value = CompletableDeferred<Unit>()
        override fun onClosed() { value.complete(Unit) }
        override fun onFailure(failure: VoiceEffectsBridgeFailure) { value.completeExceptionally(AssertionError(failure.code)) }
    }
}
