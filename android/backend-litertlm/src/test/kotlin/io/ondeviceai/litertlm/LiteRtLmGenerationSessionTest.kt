package io.ondeviceai.litertlm

import io.ondeviceai.modelstore.*
import io.ondeviceai.multimodal.MultimodalRequest
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.asCoroutineDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import io.ondeviceai.multimodal.MultimodalClientError

class LiteRtLmGenerationSessionTest {
    @Test
    fun secondGenerationIsBusyUntilFirstDrainCompletes() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var opened = 0
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                opened++
                object : LiteRtLmSession {
                    override val callbackMode = LiteRtLmCallbackMode.CUMULATIVE
                    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
                        onSnapshot: (String) -> Unit): String {
                        started.complete(Unit)
                        release.await()
                        return "text"
                    }
                    override fun cancelProcess() = Unit
                    override suspend fun awaitTerminated() = Unit
                    override suspend fun close() = Unit
                }
            })
            val first = async { backend.generate(MultimodalRequest(fixture.image, "first")) }
            started.await()
            assertFailsWith<MultimodalClientError.Busy> {
                backend.generate(MultimodalRequest(fixture.image, "second"))
            }
            assertEquals(1, opened)
            release.complete(Unit)
            first.await()
            backend.close()
        } finally { fixture.root.deleteRecursively() }
    }

    @Test
    fun verifiedDeltaCallbacksBecomeCumulativeUpdates() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                object : LiteRtLmSession {
                    override val callbackMode = LiteRtLmCallbackMode.DELTA
                    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
                        onSnapshot: (String) -> Unit): String {
                        listOf("a", "b", "c").forEach(onSnapshot)
                        return "abc"
                    }
                    override fun cancelProcess() = Unit
                    override suspend fun awaitTerminated() = Unit
                    override suspend fun close() = Unit
                }
            }, LiteRtLmProbeState.PROVEN_DELTA)
            val updates = mutableListOf<String>()
            val output = backend.generate(MultimodalRequest(fixture.image, "describe")) { updates += it.text }
            assertEquals(listOf("a", "ab", "abc"), updates)
            assertEquals("abc", output.text)
            backend.close()
        } finally { fixture.root.deleteRecursively() }
    }

    @Test
    fun nativeOpenGenerateCancelDrainAndCloseLeaveCallerDispatcher() = runBlocking {
        val fixture = BackendFixture()
        val caller = Executors.newSingleThreadExecutor { task -> Thread(task, "litertlm-caller") }.asCoroutineDispatcher()
        try {
            fixture.install()
            val threads = mutableListOf<String>()
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                threads += Thread.currentThread().name
                object : LiteRtLmSession {
                    override val callbackMode = LiteRtLmCallbackMode.CUMULATIVE
                    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
                        onSnapshot: (String) -> Unit): String {
                        threads += Thread.currentThread().name
                        return "text"
                    }
                    override fun cancelProcess() { threads += Thread.currentThread().name }
                    override suspend fun awaitTerminated() { threads += Thread.currentThread().name }
                    override suspend fun close() { threads += Thread.currentThread().name }
                }
            })
            assertTrue(withContext(caller) { Thread.currentThread().name.startsWith("litertlm-caller") })
            withContext(caller) { backend.generate(MultimodalRequest(fixture.image, "describe")) }
            assertEquals(5, threads.size)
            assertFalse(threads.any { it.contains("litertlm-caller") }, "$threads")
            backend.close()
        } finally {
            caller.close()
            fixture.root.deleteRecursively()
        }
    }

    @Test
    fun callbackAfterCallerCancellationButBeforeNativeDrainIsNotDelivered() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var callback: ((String) -> Unit)? = null
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                object : LiteRtLmSession {
                    override val callbackMode = LiteRtLmCallbackMode.CUMULATIVE
                    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
                        onSnapshot: (String) -> Unit): String {
                        callback = onSnapshot
                        onSnapshot("a")
                        started.complete(Unit)
                        try { awaitCancellation() }
                        catch (failure: CancellationException) {
                            cancelled.complete(Unit)
                            withContext(NonCancellable) { release.await() }
                            throw failure
                        }
                    }
                    override fun cancelProcess() = Unit
                    override suspend fun awaitTerminated() = Unit
                    override suspend fun close() = Unit
                }
            })
            val updates = mutableListOf<String>()
            val generation = async {
                backend.generate(MultimodalRequest(fixture.image, "describe")) { updates += it.text }
            }
            started.await()
            generation.cancel()
            cancelled.await()
            callback?.invoke("ab")
            release.complete(Unit)
            generation.join()
            assertEquals(listOf("a"), updates)
            backend.close()
        } finally { fixture.root.deleteRecursively() }
    }

    @Test
    fun closeCancelsActiveGenerationAndRejectsLateCallbacks() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            val events = mutableListOf<String>()
            val started = CompletableDeferred<Unit>()
            var lateCallback: ((String) -> Unit)? = null
            val backend = fixture.backend(LiteRtLmEngineFactory { _, _ ->
                object : LiteRtLmSession {
                    override val callbackMode = LiteRtLmCallbackMode.CUMULATIVE
                    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
                        onSnapshot: (String) -> Unit): String {
                        events += "generate"
                        lateCallback = onSnapshot
                        onSnapshot("a")
                        started.complete(Unit)
                        awaitCancellation()
                    }
                    override fun cancelProcess() { events += "cancel" }
                    override suspend fun awaitTerminated() { events += "drain" }
                    override suspend fun close() { events += "native-close" }
                }
            })
            val updates = mutableListOf<String>()
            val generation = async {
                backend.generate(MultimodalRequest(fixture.image, "describe")) { updates += it.text }
            }
            started.await()
            backend.close()
            generation.join()
            lateCallback?.invoke("ab")
            assertEquals(listOf("a"), updates)
            assertEquals(listOf("generate", "cancel", "drain", "native-close"), events)
            assertFailsWith<MultimodalClientError.Closed> {
                backend.generate(MultimodalRequest(fixture.image, "again"))
            }
            fixture.store.remove(fixture.ref)
        } finally { fixture.root.deleteRecursively() }
    }

    @Test
    fun generationDrainsBeforeClosingNativeAndReleasesLeaseForReentry() = runBlocking {
        val fixture = BackendFixture()
        try {
            fixture.install()
            val events = mutableListOf<String>()
            val factory = LiteRtLmEngineFactory { _, _ ->
                events += "open"
                object : LiteRtLmSession {
                    override val callbackMode = LiteRtLmCallbackMode.CUMULATIVE
                    override suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int,
                        onSnapshot: (String) -> Unit): String {
                        events += "generate"
                        assertEquals("describe", prompt)
                        assertEquals(32, maxOutputTokens)
                        onSnapshot("a")
                        onSnapshot("ab")
                        onSnapshot("ab")
                        return "ab"
                    }
                    override fun cancelProcess() { events += "cancel" }
                    override suspend fun awaitTerminated() { events += "drain" }
                    override suspend fun close() { events += "native-close" }
                }
            }
            val backend = fixture.backend(factory)
            val updates = mutableListOf<String>()
            repeat(2) {
                val output = backend.generate(MultimodalRequest(fixture.image, "describe", 32)) { updates += it.text }
                assertEquals("ab", output.text)
                assertEquals("fixed-revision", output.model.modelRevision)
                assertEquals("litertlm-android:0.18.1", output.model.runtimeRevision)
            }
            assertEquals(listOf("a", "ab", "a", "ab"), updates)
            assertEquals(listOf("open", "generate", "cancel", "drain", "native-close",
                "open", "generate", "cancel", "drain", "native-close"), events)
            backend.drain()
            backend.close()
            fixture.store.remove(fixture.ref)
            assertFalse(File(fixture.cache, "litertlm").walkTopDown().any { it.isFile })
        } finally { fixture.root.deleteRecursively() }
    }
}

internal class BackendFixture {
    val root: File = Files.createTempDirectory("litertlm-backend").toFile()
    val source = File(root, "source").apply { mkdirs() }
    val cache = File(root, "cache").apply { mkdirs() }
    val image = File(root, "fixture.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    val ref = ModelRef("fixture", "r1")
    val store = ModelStore(File(root, "store"), ModelCompatibility("litertlm.android", "0.18.1", "35", "arm64-v8a"))
    val spec = LiteRtLmModelSpec("model.bin", setOf("tokenizer.json"))

    suspend fun install() {
        val files = listOf("model.bin", "tokenizer.json").map { path ->
            val bytes = "fixture-$path".toByteArray()
            File(source, path).writeBytes(bytes)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            ModelFile(path, "https://models.example/$path", bytes.size.toLong(), hash)
        }
        val total = files.sumOf { it.bytes }
        store.install(ModelManifest(
            modelId = ref.modelId, revision = ref.revision,
            upstreamURL = "https://models.example/fixture", upstreamRevision = "fixed-revision",
            capability = "multimodal.image", backendId = "litertlm.android",
            backendVersion = VersionRange("0.18.1", "0.19.0"), minimumOS = "26",
            architectures = listOf("arm64-v8a"), formatVersion = "litertlm-model-v1", files = files,
            processing = ModelProcessing("image-file+text-prompt-v1", "text-v1", "preprocess-v1", "tokenizer-v1", "prompt-v1", 1),
            resources = ModelResources(total, total, total), licenseIdentifier = "Apache-2.0",
            licenseURL = "https://www.apache.org/licenses/LICENSE-2.0", attribution = "test fixture",
            redistribution = "permitted",
        ), source)
    }

    fun backend(factory: LiteRtLmEngineFactory,
        probeState: LiteRtLmProbeState = LiteRtLmProbeState.PROVEN_CUMULATIVE,
        cacheCleaner: LiteRtLmCacheCleaner = LiteRtLmCacheCleaner { it.deleteRecursively() }) = LiteRtLmBackend(
        store, ref, spec, cache, factory, apiLevel = 35, supportedAbi = true,
        probeState = probeState, cacheCleaner = cacheCleaner)
}
