package io.ondeviceai.systemasr

import kotlinx.coroutines.*
import kotlin.test.*

class AsrResourcesTest {
    @Test fun exactLocaleMembershipDistinguishesInstalledPendingDownloadableAndUnknown() {
        val support = AsrLanguageSupport(listOf("EN_us"), listOf("fr-FR"), listOf("en-US", "de-DE", "es"))
        assertEquals(AsrAssetStatus.INSTALLED, support.status("en-US"))
        assertEquals(AsrAssetStatus.PENDING, support.status("fr-fr"))
        assertEquals(AsrAssetStatus.DOWNLOADABLE, support.status("de-DE"))
        assertEquals(AsrAssetStatus.NOT_REPORTED, support.status("es-MX"))
        assertEquals(AsrAssetStatus.NOT_REPORTED, support.status("zh-CN"))
        assertFailsWith<IllegalArgumentException> { support.status("not a locale") }
    }

    @Test fun checkNeverRequestsDownloadAndReleasesEngine() = runBlocking {
        val engine = ResourceEngine()
        val session = session(engine)
        val result = async(start = CoroutineStart.UNDISPATCHED) { session.check("en_us") }
        assertEquals("en-US", engine.locale)
        assertEquals(0, engine.downloads)
        engine.listener!!.onSupport(AsrLanguageSupport(emptyList(), emptyList(), listOf("en-US")))
        assertEquals(AsrAssetStatus.DOWNLOADABLE, result.await())
        assertEquals(1, engine.destroys)
    }

    @Test fun progressAndScheduledDoNotClaimInstallation() = runBlocking {
        val engine = ResourceEngine()
        val progress = mutableListOf<Int>()
        val result = async(start = CoroutineStart.UNDISPATCHED) { session(engine).download("fr-FR", progress::add) }
        engine.listener!!.onProgress(41)
        assertFalse(result.isCompleted)
        engine.listener!!.onScheduled()
        assertEquals(AsrAssetStatus.PENDING, result.await())
        engine.listener!!.onInstalled()
        engine.listener!!.onProgress(99)
        assertEquals(listOf(41), progress)
        assertEquals(1, engine.destroys)
    }

    @Test fun installedCallbackAndPlatformErrorAreDistinctAndReleaseEngines() = runBlocking { supervisorScope {
        val engine = ResourceEngine()
        val installed = async(start = CoroutineStart.UNDISPATCHED) { session(engine).download("en-US") {} }
        engine.listener!!.onInstalled()
        assertEquals(AsrAssetStatus.INSTALLED, installed.await())
        assertEquals(1, engine.destroys)
        val failed = ResourceEngine()
        val query = async(start = CoroutineStart.UNDISPATCHED) { session(failed).check("en-US") }
        failed.listener!!.onError(14)
        assertEquals(14, assertFailsWith<AsrResourceException> { query.await() }.code)
        assertEquals(1, failed.destroys)
    } }

    @Test fun cancelledMonitorDestroysItsRecognizerAndRejectsLateCallbacks() = runBlocking {
        val engine = ResourceEngine()
        val progress = mutableListOf<Int>()
        val task = async(start = CoroutineStart.UNDISPATCHED) { session(engine).download("en-US", progress::add) }
        task.cancelAndJoin()
        engine.listener!!.onProgress(60)
        engine.listener!!.onInstalled()
        assertTrue(progress.isEmpty())
        assertEquals(1, engine.destroys)
    }

    @Test fun timeoutAndCleanupFailureCannotReportSuccessfulMetadata() = runBlocking {
        val timed = ResourceEngine()
        assertFailsWith<AsrResourceTimeoutException> { session(timed, 10).check("en-US") }
        assertEquals(1, timed.destroys)
        val cleanup = ResourceEngine().apply { destroyFailure = IllegalStateException("cleanup") }
        val task = async(start = CoroutineStart.UNDISPATCHED) { runCatching { session(cleanup).check("en-US") } }
        cleanup.listener!!.onSupport(AsrLanguageSupport(listOf("en-US"), emptyList(), emptyList()))
        assertEquals("cleanup", task.await().exceptionOrNull()?.message)
    }

    private fun session(engine: ResourceEngine, timeout: Long = 5000) = AsrResourceSession(
        factory = { engine }, dispatcher = Dispatchers.Unconfined, timeoutMillis = timeout)
}

private class ResourceEngine : AsrResourceEngine {
    var listener: AsrResourceListener? = null
    var locale: String? = null
    var downloads = 0
    var destroys = 0
    var destroyFailure: Exception? = null
    override fun check(locale: String, listener: AsrResourceListener) { this.locale = locale; this.listener = listener }
    override fun download(locale: String, listener: AsrResourceListener) { downloads++; this.locale = locale; this.listener = listener }
    override fun destroy() { destroys++; destroyFailure?.let { throw it } }
}
