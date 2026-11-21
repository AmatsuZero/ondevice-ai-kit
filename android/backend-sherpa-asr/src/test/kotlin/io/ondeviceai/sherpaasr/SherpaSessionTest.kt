package io.ondeviceai.sherpaasr

import io.ondeviceai.core.BusyException
import io.ondeviceai.core.ClosedException
import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class SherpaSessionTest {
    @Test fun temporaryWaveformIsClearedOnSuccessAndFailure() = runBlocking {
        for (fail in listOf(false, true)) {
            val pcm = floatArrayOf(.1f, .2f)
            val session = SherpaSession({ Decoder { if (fail) error("failed") } })
            try { session.transcribe("en") { pcm } } catch (_: IllegalStateException) {}
            assertTrue(pcm.all { it == 0f })
        }
    }
    @Test fun returnsActualBackendAndLocaleAndReleasesEachDecode() = runBlocking {
        val decoder = Decoder()
        val session = SherpaSession({ decoder })
        val result = session.transcribe("en-US") { floatArrayOf(.1f) }
        assertEquals("sherpa.onnx.whisper", result.backendId)
        assertEquals("recognized", result.transcript)
        assertEquals("en", decoder.language)
        assertEquals(1, decoder.releases)
        session.transcribe("zh-CN") { floatArrayOf(.2f) }
        assertEquals("zh", decoder.language)
        assertEquals(2, decoder.releases)
    }

    @Test fun unsupportedLocaleDoesNotCaptureOrCreateNativeDecoder() = runBlocking {
        var captured = false
        var created = false
        val session = SherpaSession({ created = true; Decoder() })
        assertFailsWith<IllegalArgumentException> {
            session.transcribe("fr-FR") { captured = true; floatArrayOf(.1f) }
        }
        assertFalse(captured)
        assertFalse(created)
    }

    @Test fun cancellationStopsCaptureWithoutCreatingDecoderAndAllowsReentry() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var releasedCapture = false
        var creates = 0
        val session = SherpaSession({ creates++; Decoder() })
        val task = launch {
            session.transcribe("en-US") {
                try { started.complete(Unit); awaitCancellation() }
                finally { releasedCapture = true }
            }
        }
        started.await()
        task.cancelAndJoin()
        assertTrue(releasedCapture)
        assertEquals(0, creates)
        assertEquals("recognized", session.transcribe("en") { floatArrayOf(.1f) }.transcript)
    }

    @Test fun cancelledNativeDecodeDrainsBeforeReentryAndClose() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val decoder = Decoder { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        val session = SherpaSession({ decoder })
        var resultDelivered = false
        val task = launch(Dispatchers.Default) {
            session.transcribe("en") { floatArrayOf(.1f) }
            resultDelivered = true
        }
        assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
        task.cancel()
        assertFailsWith<BusyException> { session.transcribe("en") { floatArrayOf(.2f) } }
        val close = async { session.close() }
        yield()
        assertFalse(close.isCompleted)
        assertEquals(0, decoder.releases)
        release.countDown()
        withTimeout(5000) { task.join(); close.await() }
        assertFalse(resultDelivered)
        assertEquals(1, decoder.releases)
        assertFailsWith<ClosedException> { session.transcribe("en") { floatArrayOf(.1f) } }
        session.close()
    }

    @Test fun failedDecodeReleasesAndRejectsEmptyResult() = runBlocking {
        val decoder = Decoder { error("decode failed") }
        val session = SherpaSession({ decoder })
        assertFailsWith<IllegalStateException> { session.transcribe("en") { floatArrayOf(.1f) } }
        assertEquals(1, decoder.releases)
        val empty = SherpaSession({ Decoder(text = " ") })
        assertFailsWith<IllegalStateException> { empty.transcribe("en") { floatArrayOf(.1f) } }
        Unit
    }

    @Test fun decoderCleanupFailureKeepsDecodeFailureAndReleasesAdmission() = runBlocking<Unit> {
        val pcm = floatArrayOf(.1f, .2f)
        val decodeFailure = IllegalArgumentException("decode failed")
        val cleanupFailure = IllegalStateException("decoder close failed")
        var releases = 0
        var creates = 0
        val session = SherpaSession({
            if (creates++ == 0) object : SherpaDecoder {
                override fun decode(samples: FloatArray, language: String): String = throw decodeFailure
                override fun close() { releases++; throw cleanupFailure }
            } else Decoder()
        })

        val failure = assertFailsWith<IllegalArgumentException> { session.transcribe("en") { pcm } }

        assertEquals("decode failed", failure.message)
        assertEquals(listOf(cleanupFailure), decodeFailure.suppressed.toList())
        assertEquals(1, releases)
        assertTrue(pcm.all { it == 0f })
        assertEquals("recognized", session.transcribe("en") { floatArrayOf(.3f) }.transcript)
        session.close()
    }

    @Test fun decoderCleanupFailureCannotReplaceCancellation() = runBlocking<Unit> {
        val pcm = floatArrayOf(.1f)
        val cancellation = CancellationException("decode cancelled")
        val cleanupFailure = IllegalStateException("decoder close failed")
        var releases = 0
        val session = SherpaSession({ object : SherpaDecoder {
            override fun decode(samples: FloatArray, language: String): String = throw cancellation
            override fun close() { releases++; throw cleanupFailure }
        } })

        val failure = assertFailsWith<CancellationException> { session.transcribe("en") { pcm } }

        assertEquals("decode cancelled", failure.message)
        assertEquals(listOf(cleanupFailure), cancellation.suppressed.toList())
        assertEquals(1, releases)
        assertTrue(pcm.all { it == 0f })
        session.close()
    }

    private class Decoder(val text: String = "recognized", val action: () -> Unit = {}) : SherpaDecoder {
        var language: String? = null
        var releases = 0
        override fun decode(samples: FloatArray, language: String): String {
            this.language = language
            action()
            return text
        }
        override fun close() { releases++ }
    }
}
