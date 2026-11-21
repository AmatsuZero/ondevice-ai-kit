package io.ondeviceai.demo

import io.ondeviceai.systemasr.*
import io.ondeviceai.binding.*
import kotlinx.coroutines.*
import kotlin.test.*

class AsrResourceHostTest {
    @Test fun checkBindsLocaleAndOnlyExplicitMatchingDownloadCanStart() = runBlocking {
        var checks = 0; var downloads = 0
        val host = AsrResourceHost(this, canDownload = true,
            query = { checks++; AsrAssetStatus.DOWNLOADABLE },
            requestDownload = { _, _ -> downloads++; AsrAssetStatus.PENDING })
        assertEquals(0, checks)
        host.download("en-US")
        assertEquals(0, downloads)
        host.check("en-US"); yield()
        assertEquals(1, checks)
        assertTrue(host.state.canDownload)
        host.download("fr-FR"); yield()
        assertEquals(0, downloads)
        host.download("en-US"); yield()
        assertEquals(1, downloads)
        assertEquals("PENDING", host.state.status)
        assertFalse(host.state.canDownload)
        host.close()
    }

    @Test fun queryErrorsRemainUnknownAndApi33DoesNotOfferDownload() = runBlocking {
        val error = AsrResourceHost(this, true, query = { throw AsrResourceException(14) },
            requestDownload = { _, _ -> error("must not download") })
        error.check("en-US"); yield()
        assertEquals("QUERY_ERROR", error.state.status)
        assertEquals(14, error.state.platformCode)
        assertFalse(error.state.canDownload)
        error.close()
        val old = AsrResourceHost(this, false, query = { AsrAssetStatus.DOWNLOADABLE },
            requestDownload = { _, _ -> error("must not download") })
        old.check("en-US"); yield()
        assertEquals("DOWNLOADABLE", old.state.status)
        assertFalse(old.state.canDownload)
        old.close()
    }

    @Test fun busyMonitorRejectsRecognitionAndBackgroundWaitsForResourceRelease() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val released = CompletableDeferred<Unit>()
        val finishCleanup = CompletableDeferred<Unit>()
        val assets = AsrResourceHost(this, true,
            query = { entered.complete(Unit); try { awaitCancellation() } finally {
                withContext(NonCancellable) { finishCleanup.await(); released.complete(Unit) }
            } }, requestDownload = { _, _ -> error("must not download") })
        val host = SpeechHost(CoroutineScope(Dispatchers.Unconfined), createTts = { null }, createAsr = { null },
            hasPermission = { true }, requestPermission = { error("must not prompt") }, openSettings = {}, asrResources = assets)
        assets.check("en-US"); entered.await()
        host.model.recognize(SpeechBridgeAsrRequest("en-US", SpeechBridgeInput.Microphone))
        assertEquals(SpeechBridgeFailureCode.BUSY, host.model.state.failure?.code)
        host.onBackground(); yield()
        assertTrue(assets.state.busy)
        finishCleanup.complete(Unit); released.await(); yield()
        assertFalse(assets.state.busy)
        assertEquals("MONITOR_CANCELLED", assets.state.status)
        host.close()
    }

    @Test fun closeDetachesDownloadAndLateProgressCannotChangeRetiredState() = runBlocking {
        var report: ((Int) -> Unit)? = null
        val started = CompletableDeferred<Unit>()
        val host = AsrResourceHost(this, true, query = { AsrAssetStatus.DOWNLOADABLE },
            requestDownload = { _, progress -> report = progress; started.complete(Unit); awaitCancellation() })
        host.check("en-US"); yield()
        host.download("en-US"); started.await()
        report!!(33)
        assertEquals(33, host.state.progress)
        host.close()
        val retired = host.state
        report!!(90)
        host.check("en-US"); host.download("en-US")
        assertEquals(retired, host.state)
        assertFalse(host.state.busy)
    }

    @Test fun microphoneAdmissionGuardPreventsResourceOperations() = runBlocking {
        var calls = 0
        val host = AsrResourceHost(this, true, query = { calls++; AsrAssetStatus.DOWNLOADABLE },
            requestDownload = { _, _ -> calls++; AsrAssetStatus.INSTALLED }, canStart = { false })
        host.check("en-US"); yield()
        assertEquals(0, calls)
        assertFalse(host.state.busy)
        host.close()
    }

    @Test fun immediateCloseBeforeDispatchDoesNotLeaveControlsBusy() = runBlocking {
        val host = AsrResourceHost(this, true, query = { awaitCancellation() },
            requestDownload = { _, _ -> awaitCancellation() })
        host.check("en-US")
        assertTrue(host.state.busy)
        host.close()
        assertFalse(host.state.busy)
    }
}
