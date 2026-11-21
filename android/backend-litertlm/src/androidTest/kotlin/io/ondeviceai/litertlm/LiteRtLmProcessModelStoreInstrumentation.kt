package io.ondeviceai.litertlm

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import androidx.test.platform.app.InstrumentationRegistry
import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.modelstore.ModelCompatibility
import io.ondeviceai.modelstore.ModelFile
import io.ondeviceai.modelstore.ModelInUseException
import io.ondeviceai.modelstore.ModelManifest
import io.ondeviceai.modelstore.ModelProcessing
import io.ondeviceai.modelstore.ModelRef
import io.ondeviceai.modelstore.ModelResources
import io.ondeviceai.modelstore.ModelStore
import io.ondeviceai.modelstore.VersionRange
import io.ondeviceai.multimodal.MultimodalClientError
import io.ondeviceai.multimodal.MultimodalRequest
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiteRtLmProcessModelStoreInstrumentation {
    @Test
    fun terminalSuccessRetainsRealStoreLeaseAndPrivateCacheUntilOriginalBinderDeath() = runBlocking {
        val fixture = StoreFixture()
        val coordinator = LiteRtLmProcessCoordinator()
        fixture.install()
        val endpoint = fixture.endpoint(coordinator)
        val backend = fixture.backend(endpoint)
        endpoint.binder.addStatus(status("GENERATING", "RUNNING", listOf("a")))
        endpoint.binder.addStatus(status("GENERATING", "RUNNING", listOf("a", "b")))
        endpoint.binder.addStatus(status("TERMINAL", "SUCCEEDED", listOf("a", "b", "!")))
        val updates = CopyOnWriteArrayList<String>()
        val finalUpdate = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val generation = async {
            try {
                backend.generate(MultimodalRequest(fixture.image, "describe", 8)) {
                    updates += it.text
                    if (it.text == "ab!") finalUpdate.complete(Unit)
                }
            } finally {
                finished.complete(Unit)
            }
        }

        try {
            endpoint.binder.awaitCode(START)
            finalUpdate.await()
            endpoint.binder.awaitCode(CANCEL)
            endpoint.binder.awaitCode(RETIRE)

            val request = checkNotNull(endpoint.binder.startRequest.get())
            val privateCache = File(request.cacheAbsolutePath)
            assertEquals(
                File(File(File(File(fixture.storeRoot, "installed"), fixture.ref.modelId),
                    fixture.ref.revision), fixture.spec.modelPathRelative).absolutePath,
                request.modelAbsolutePath,
            )
            assertEquals(File(privateCache, "input.image").absolutePath, request.imageAbsolutePath)
            assertTrue(File(privateCache, "input.image").isFile)
            assertInUse(fixture.store, fixture.ref)
            assertPending(finished)
            onMain {
                assertFalse(endpoint.binder.transactedOnMain)
                assertFalse(endpoint.context.bindOnMain)
            }

            endpoint.binder.fireDeath()
            withTimeout(TEST_TIMEOUT_MILLIS) { finished.await() }
            val output = generation.await()
            assertEquals("ab!", output.text)
            assertEquals(listOf("a", "ab", "ab!"), updates)
            assertFalse(privateCache.exists())
            assertEquals(1, endpoint.context.unbindCalls)
            onMain { assertFalse(endpoint.context.unbindOnMain) }
            fixture.store.remove(fixture.ref)
        } finally {
            endpoint.releaseGates()
            endpoint.binder.fireDeathIfRegistered()
            try {
                withTimeout(TEST_TIMEOUT_MILLIS) { backend.close() }
            } finally {
                fixture.close()
            }
        }
    }

    @Test
    fun separateBackendsShareCoordinatorAndStoreWithoutLeakingRejectedLeaseOrCache() = runBlocking {
        val fixture = StoreFixture()
        fixture.install()
        val coordinator = LiteRtLmProcessCoordinator()
        val firstEndpoint = fixture.endpoint(coordinator)
        val secondEndpoint = fixture.endpoint(coordinator)
        val firstBackend = fixture.backend(firstEndpoint)
        val secondBackend = fixture.backend(secondEndpoint)
        firstEndpoint.binder.addStatus(status("TERMINAL", "SUCCEEDED", listOf("first")))
        firstEndpoint.binder.gateStatusCall = 1
        val finished = CompletableDeferred<Unit>()
        val firstGeneration = async {
            try {
                firstBackend.generate(MultimodalRequest(fixture.image, "first", 8))
            } finally {
                finished.complete(Unit)
            }
        }

        try {
            firstEndpoint.binder.awaitStatusGate()
            val firstRequest = checkNotNull(firstEndpoint.binder.startRequest.get())
            assertTrue(File(firstRequest.cacheAbsolutePath, "input.image").isFile)

            val busy = try {
                secondBackend.generate(MultimodalRequest(fixture.image, "second", 8))
                null
            } catch (failure: MultimodalClientError.Busy) {
                failure
            }
            assertTrue("the second factory must reject the shared process claim", busy != null)
            assertEquals(0, secondEndpoint.context.bindCalls)
            assertTrue(secondEndpoint.binder.codes.isEmpty())
            assertEquals(CapabilityStatus.AVAILABLE, secondBackend.availability().status)
            assertPending(finished)
            assertTrue(File(firstRequest.cacheAbsolutePath, "input.image").isFile)
            assertEquals(1, fixture.privateCacheDirectories().size)
            assertInUse(fixture.store, fixture.ref)

            firstEndpoint.binder.releaseStatusGate()
            firstEndpoint.binder.awaitCode(RETIRE)
            assertPending(finished)
            firstEndpoint.binder.fireDeath()
            withTimeout(TEST_TIMEOUT_MILLIS) { finished.await() }
            assertEquals("first", firstGeneration.await().text)
            assertFalse(File(firstRequest.cacheAbsolutePath).exists())
            fixture.store.remove(fixture.ref)
        } finally {
            firstEndpoint.releaseGates()
            secondEndpoint.releaseGates()
            firstEndpoint.binder.fireDeathIfRegistered()
            secondEndpoint.binder.fireDeathIfRegistered()
            try {
                withTimeout(TEST_TIMEOUT_MILLIS) { firstBackend.close() }
            } finally {
                try {
                    withTimeout(TEST_TIMEOUT_MILLIS) { secondBackend.close() }
                } finally {
                    fixture.close()
                }
            }
        }
    }

    @Test
    fun callerCancellationSuppressesLateUpdatesAndKeepsLeaseUntilExactDeath() = runBlocking {
        val fixture = StoreFixture()
        fixture.install()
        val endpoint = fixture.endpoint()
        val backend = fixture.backend(endpoint)
        endpoint.binder.addStatus(status("GENERATING", "RUNNING", listOf("before")))
        endpoint.binder.addStatus(status("GENERATING", "RUNNING", listOf("before", "late")))
        endpoint.binder.gateStatusCall = 2
        endpoint.binder.holdCancel = true
        val updates = CopyOnWriteArrayList<String>()
        val firstUpdate = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val generation = async {
            try {
                backend.generate(MultimodalRequest(fixture.image, "cancel", 8)) {
                    updates += it.text
                    if (it.text == "before") firstUpdate.complete(Unit)
                }
            } finally {
                finished.complete(Unit)
            }
        }

        try {
            firstUpdate.await()
            endpoint.binder.awaitStatusGate()
            val request = checkNotNull(endpoint.binder.startRequest.get())
            generation.cancel()
            endpoint.binder.releaseStatusGate()
            endpoint.binder.awaitCancelGate()

            assertTrue(generation.isCancelled)
            assertEquals(listOf("before"), updates)
            assertTrue(File(request.cacheAbsolutePath, "input.image").isFile)
            assertInUse(fixture.store, fixture.ref)
            endpoint.binder.releaseCancelGate()
            endpoint.binder.awaitCode(RETIRE)
            assertPending(finished)
            assertEquals(listOf("before"), updates)
            assertInUse(fixture.store, fixture.ref)
            assertTrue(File(request.cacheAbsolutePath, "input.image").isFile)

            endpoint.binder.fireDeath()
            withTimeout(TEST_TIMEOUT_MILLIS) { finished.await() }
            val failure = runCatching { generation.await() }.exceptionOrNull()
            assertTrue("caller cancellation must remain cancellation: $failure",
                failure is java.util.concurrent.CancellationException)
            assertEquals(listOf("before"), updates)
            assertFalse(File(request.cacheAbsolutePath).exists())
            fixture.store.remove(fixture.ref)
        } finally {
            endpoint.releaseGates()
            endpoint.binder.fireDeathIfRegistered()
            try {
                withTimeout(TEST_TIMEOUT_MILLIS) { backend.close() }
            } finally {
                fixture.close()
            }
        }
    }

    private class StoreFixture {
        private val context = targetContext()
        private val root = File(
            context.cacheDir, "litertlm-process-modelstore-${UUID.randomUUID()}",
        ).apply { check(mkdirs()) }
        private val source = File(root, "source")
        val storeRoot = File(root, "store")
        private val cacheRoot = File(root, "cache")
        val image = File(root, "fixture.image")
        val ref = ModelRef("process-fixture", "r1")
        val spec = LiteRtLmModelSpec("model.bin", setOf("tokenizer.json"))
        val store = ModelStore(
            storeRoot,
            ModelCompatibility("litertlm.android", "0.18.1", "35", "arm64-v8a"),
        )

        init {
            check(source.mkdirs())
            check(cacheRoot.mkdirs())
            image.writeBytes(byteArrayOf(4, 5, 6))
        }

        suspend fun install() {
            val files = listOf("model.bin", "tokenizer.json").map { path ->
                val bytes = "fixture-$path".toByteArray()
                File(source, path).writeBytes(bytes)
                ModelFile(path, "https://models.example/$path", bytes.size.toLong(), sha256(bytes))
            }
            val total = files.sumOf { it.bytes }
            store.install(
                ModelManifest(
                    modelId = ref.modelId,
                    revision = ref.revision,
                    upstreamURL = "https://models.example/fixture",
                    upstreamRevision = "fixed-revision",
                    capability = "multimodal.image",
                    backendId = "litertlm.android",
                    backendVersion = VersionRange("0.18.1", "0.19.0"),
                    minimumOS = "26",
                    architectures = listOf("arm64-v8a"),
                    formatVersion = "litertlm-model-v1",
                    files = files,
                    processing = ModelProcessing(
                        "image-file+text-prompt-v1", "text-v1", "preprocess-v1",
                        "tokenizer-v1", "prompt-v1", 1,
                    ),
                    resources = ModelResources(total, total, total),
                    licenseIdentifier = "Apache-2.0",
                    licenseURL = "https://www.apache.org/licenses/LICENSE-2.0",
                    attribution = "test fixture",
                    redistribution = "permitted",
                ),
                source,
            )
        }

        fun endpoint(coordinator: LiteRtLmProcessCoordinator = LiteRtLmProcessCoordinator()) =
            Endpoint(context, coordinator)

        fun backend(endpoint: Endpoint) =
            LiteRtLmBackend(
                store = store,
                ref = ref,
                spec = spec,
                cacheRoot = cacheRoot,
                factory = endpoint.factory,
                apiLevel = 35,
                supportedAbi = true,
                probeState = LiteRtLmProbeState.PROVEN_DELTA,
                availabilitySource = {
                    CapabilityAvailability(
                        CapabilityStatus.AVAILABLE, null, "litertlm.android", "26",
                    )
                },
            )

        fun privateCacheDirectories(): List<File> =
            File(cacheRoot, "litertlm/${ref.modelId}/${ref.revision}")
                .listFiles()?.filter { it.isDirectory }.orEmpty()

        fun close() {
            check(!root.exists() || root.deleteRecursively())
        }
    }

    private class Endpoint(
        context: Context,
        coordinator: LiteRtLmProcessCoordinator,
    ) {
        val context = ScriptedContext(context)
        val binder = ScriptedBinder("${context.packageName}:odai_litertlm")
        val factory = LiteRtLmProcessEngineFactory(
            this.context, LiteRtLmCallbackMode.DELTA, coordinator,
        )

        init {
            this.context.binderProvider = { binder }
        }

        fun releaseGates() {
            binder.releaseStatusGate()
            binder.releaseCancelGate()
        }
    }

    private class ScriptedContext(base: Context) : ContextWrapper(base) {
        @Volatile var binderProvider: (() -> ScriptedBinder)? = null
        @Volatile var bindCalls = 0
        @Volatile var unbindCalls = 0
        @Volatile var bindOnMain = false
        @Volatile var unbindOnMain = false

        override fun getApplicationContext(): Context = this

        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
            bindCalls++
            bindOnMain = Looper.myLooper() == Looper.getMainLooper()
            checkNotNull(binderProvider).invoke().also {
                conn.onServiceConnected(ComponentName(packageName, "SyntheticProcessService"), it)
            }
            return true
        }

        override fun unbindService(conn: ServiceConnection) {
            unbindCalls++
            unbindOnMain = Looper.myLooper() == Looper.getMainLooper()
        }
    }

    private class ScriptedBinder(private val processName: String) : IBinder by Binder() {
        val codes = CopyOnWriteArrayList<Int>()
        val startRequest = AtomicReference<LiteRtLmProcessStart?>()
        private val statuses = ConcurrentLinkedQueue<String>()
        private val death = AtomicReference<IBinder.DeathRecipient?>()
        private val activeNonce = AtomicReference<String?>()
        private val statusCalls = AtomicInteger()
        private val codeSignals = ConcurrentHashMap<Int, CompletableDeferred<Unit>>()
        @Volatile var gateStatusCall = 0
        @Volatile var holdCancel = false
        @Volatile var transactedOnMain = false
        val statusGateEntered = CountDownLatch(1)
        val statusGateRelease = CountDownLatch(1)
        val cancelGateEntered = CountDownLatch(1)
        val cancelGateRelease = CountDownLatch(1)
        private val deathFired = java.util.concurrent.atomic.AtomicBoolean()

        fun addStatus(value: String) {
            statuses.add(value)
        }

        override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) {
            death.set(recipient)
        }

        override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int): Boolean =
            death.compareAndSet(recipient, null)

        override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            transactedOnMain = transactedOnMain || Looper.myLooper() == Looper.getMainLooper()
            codes += code
            signal(code).complete(Unit)
            data.setDataPosition(0)
            data.enforceInterface(DESCRIPTOR)
            val nonce = checkNotNull(data.readString())
            val output = checkNotNull(reply)

            when (code) {
                HELLO -> {
                    checkNoTrailing(data)
                    activeNonce.set(nonce)
                    output.writeInt(OK)
                    output.writeString(nonce)
                    output.writeInt(12345)
                    output.writeString(processName)
                }
                START -> {
                    val request = LiteRtLmProcessStart(
                        modelAbsolutePath = checkNotNull(data.readString()),
                        imageAbsolutePath = checkNotNull(data.readString()),
                        cacheAbsolutePath = checkNotNull(data.readString()),
                        prompt = checkNotNull(data.readString()),
                        maxOutputTokens = data.readInt(),
                        backendPolicy = LiteRtLmBackendPolicy.valueOf(checkNotNull(data.readString())),
                    )
                    checkNoTrailing(data)
                    startRequest.set(request)
                    output.writeInt(OK)
                }
                STATUS -> {
                    checkNoTrailing(data)
                    if (statusCalls.incrementAndGet() == gateStatusCall) {
                        statusGateEntered.countDown()
                        check(statusGateRelease.await(GATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                    }
                    output.writeInt(OK)
                    output.writeString(
                        JSONObject(checkNotNull(statuses.poll()))
                            .put("nonce", checkNotNull(activeNonce.get())).toString(),
                    )
                }
                CANCEL -> {
                    checkNoTrailing(data)
                    if (holdCancel) {
                        cancelGateEntered.countDown()
                        check(cancelGateRelease.await(GATE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                    }
                    output.writeInt(OK)
                }
                RETIRE -> {
                    checkNoTrailing(data)
                    output.writeInt(OK)
                }
                else -> error("unexpected Binder transaction $code")
            }
            output.setDataPosition(0)
            return true
        }

        suspend fun awaitCode(code: Int) {
            withTimeout(TEST_TIMEOUT_MILLIS) { signal(code).await() }
        }

        suspend fun awaitStatusGate() = awaitLatch(statusGateEntered, "STATUS gate")
        suspend fun awaitCancelGate() = awaitLatch(cancelGateEntered, "CANCEL gate")

        fun releaseStatusGate() {
            statusGateRelease.countDown()
        }

        fun releaseCancelGate() {
            cancelGateRelease.countDown()
        }

        fun fireDeath() {
            val recipient = checkNotNull(death.get()) { "no original death recipient was registered" }
            check(deathFired.compareAndSet(false, true)) { "death already fired" }
            recipient.binderDied()
        }

        fun fireDeathIfRegistered() {
            val recipient = death.get() ?: return
            if (deathFired.compareAndSet(false, true)) recipient.binderDied()
        }

        private fun signal(code: Int) =
            codeSignals.getOrPut(code) { CompletableDeferred() }

        private fun checkNoTrailing(data: Parcel) {
            check(data.dataAvail() == 0) { "unexpected trailing Binder request data" }
        }

        private suspend fun awaitLatch(latch: CountDownLatch, name: String) {
            withTimeout(TEST_TIMEOUT_MILLIS) {
                val reached = withContext(Dispatchers.IO) {
                    latch.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                }
                check(reached) {
                    "$name was not reached"
                }
            }
        }
    }

    private suspend fun assertInUse(store: ModelStore, ref: ModelRef) {
        val failure = runCatching { store.remove(ref) }.exceptionOrNull()
        assertTrue(
            "ModelStore.remove must reject a live generation lease: $failure",
            failure is ModelInUseException,
        )
    }

    private suspend fun assertPending(completion: Deferred<Unit>) {
        assertEquals(null, withTimeoutOrNull(PENDING_ASSERT_MILLIS) {
            completion.await()
            Unit
        })
    }

    private fun onMain(block: () -> Unit) {
        val error = AtomicReference<Throwable?>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                block()
            } catch (failure: Throwable) {
                error.set(failure)
            }
        }
        error.get()?.let { throw it }
    }

    private fun status(phase: String, generation: String, snapshots: List<String>): String =
        JSONObject()
            .put("nonce", "process-session-test")
            .put("phase", phase)
            .put("generation", generation)
            .put("snapshots", JSONArray().apply {
                snapshots.forEachIndexed { index, text ->
                    put(JSONObject().put("sequence", index).put("text", text))
                }
            })
            .put("nativeAttempted", phase == "GENERATING" || phase == "TERMINAL")
            .put("nativeInitialized", phase == "GENERATING" || phase == "TERMINAL")
            .put("errorCode", JSONObject.NULL)
            .put("callbackMode", "UNPROVEN")
            .toString()

    companion object {
        private const val HELLO = 1
        private const val START = 2
        private const val STATUS = 3
        private const val CANCEL = 4
        private const val RETIRE = 5
        private const val DESCRIPTOR = "io.ondeviceai.litertlm.process.v1"
        private const val OK = 0
        private const val TEST_TIMEOUT_MILLIS = 20_000L
        private const val GATE_TIMEOUT_MILLIS = 30_000L
        private const val PENDING_ASSERT_MILLIS = 100L

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        private fun targetContext(): Context =
            InstrumentationRegistry.getInstrumentation().targetContext
    }
}
