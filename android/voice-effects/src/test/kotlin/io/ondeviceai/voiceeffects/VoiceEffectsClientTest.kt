package io.ondeviceai.voiceeffects

import java.io.File
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlinx.coroutines.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VoiceEffectsClientTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun backendTimeoutRetainsItsCancellationSubtype(): Unit = runBlocking {
        val root = temporary.newFolder()
        val backend = object : VoiceEffectsBackend {
            override val backendId = "android.windowed-pitch"
            override suspend fun render(input: FloatArray, sampleRate: Int, preset: VoiceEffectsPreset): FloatArray =
                withTimeout(1L) { awaitCancellation() }
        }
        val client = VoiceEffectsClient(backend, File(root, "out"))
        assertFailsWith<TimeoutCancellationException> {
            client.process(VoiceEffectsRequest(root.inputWave(), VoiceEffectsPreset.HIGH))
        }
        client.drain(); client.close()
    }

    @Test fun malformedInputAndCloseFailureNeverLeakRawSuppressedExceptions(): Unit = runBlocking {
        val root = temporary.newFolder()
        val input = root.inputWave().apply { writeBytes(ByteArray(46)) }
        val files = object : VoiceEffectsFiles() {
            override fun openInput(file: File): InputStream = object : FilterInputStream(super.openInput(file)) {
                override fun close() { super.close(); throw IOException("private=" + root.path) }
            }
        }
        val client = VoiceEffectsClient(ControlledBackend(), File(root, "out"), files)
        val error = assertFailsWith<VoiceEffectsError.InvalidInput> {
            client.process(VoiceEffectsRequest(input, VoiceEffectsPreset.HIGH))
        }
        assertNull(error.cause)
        assertFalse(error.message.orEmpty().contains(root.path))
        error.suppressed.forEach { assertIs<VoiceEffectsError.Platform>(it); assertNull(it.cause) }
        client.close()
    }

    @Test fun inputIoRenderAndOutputIoRunOffCallerThreadAndInputIsClosedBeforeRender(): Unit = runBlocking {
        val dispatcher = java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {
            withContext(dispatcher) {
                val callerThread = Thread.currentThread().id
                val openInputs = AtomicInteger()
                val readCount = AtomicInteger(); val writeCount = AtomicInteger(); val renderCount = AtomicInteger()
                val files = object : VoiceEffectsFiles() {
                    override fun openInput(file: File): InputStream {
                        assertNotEquals(callerThread, Thread.currentThread().id)
                        readCount.incrementAndGet()
                        val input = super.openInput(file)
                        openInputs.incrementAndGet()
                        return object : FilterInputStream(input) {
                            override fun close() { try { super.close() } finally { openInputs.decrementAndGet() } }
                        }
                    }
                    override fun openOutput(file: File): OutputStream {
                        assertNotEquals(callerThread, Thread.currentThread().id)
                        writeCount.incrementAndGet()
                        return super.openOutput(file)
                    }
                }
                val backend = object : VoiceEffectsBackend {
                    override val backendId = "android.windowed-pitch"
                    override suspend fun render(input: FloatArray, sampleRate: Int, preset: VoiceEffectsPreset): FloatArray {
                        assertNotEquals(callerThread, Thread.currentThread().id)
                        assertEquals(0, openInputs.get()); renderCount.incrementAndGet()
                        return input.copyOf()
                    }
                }
                val root = temporary.newFolder()
                val client = VoiceEffectsClient(backend, File(root, "out"), files)
                val output = client.process(VoiceEffectsRequest(root.inputWave(), VoiceEffectsPreset.HIGH))
                client.drain(); client.close(); client.releaseOutput(output.id)
                assertEquals(0, openInputs.get())
                assertEquals(1, readCount.get()); assertEquals(1, writeCount.get()); assertEquals(1, renderCount.get())
            }
        } finally { dispatcher.close() }
    }

    @Test fun failedCloseIsSharedAndNeverInvokesBackendCloseTwice(): Unit = runBlocking {
        val calls = AtomicInteger()
        val backend = object : VoiceEffectsBackend {
            override val backendId = "android.windowed-pitch"
            override suspend fun render(input: FloatArray, sampleRate: Int, preset: VoiceEffectsPreset) = input.copyOf()
            override suspend fun close() { calls.incrementAndGet(); throw IOException("private close path") }
        }
        val root = temporary.newFolder()
        val client = VoiceEffectsClient(backend, File(root, "out"))
        repeat(2) {
            val error = assertFailsWith<VoiceEffectsError.Platform> { client.close() }
            assertNull(error.cause)
        }
        assertEquals(1, calls.get())
        assertFailsWith<VoiceEffectsError.Closed> { client.process(VoiceEffectsRequest(root.inputWave(), VoiceEffectsPreset.HIGH)) }
    }

    @Test fun targetCreatedAtPublicationIsKeptAndNeverDeletedByFailureCleanup(): Unit = runBlocking {
        val root = temporary.newFolder()
        val out = File(root, "out")
        val files = object : VoiceEffectsFiles() {
            override fun nextId() = "collision"
            override fun publish(temporary: File, destination: File) {
                destination.writeText("competitor")
                super.publish(temporary, destination)
            }
        }
        val client = VoiceEffectsClient(ControlledBackend(), out, files)
        assertFailsWith<VoiceEffectsError.Platform> {
            client.process(VoiceEffectsRequest(root.inputWave(), VoiceEffectsPreset.HIGH))
        }
        assertEquals("competitor", File(out, "collision.wav").readText())
        assertEquals(listOf("collision.wav"), out.listFiles().orEmpty().map { it.name })
        client.close()
    }

    @Test fun cancelledCleanupFailureKeepsBusyAndRetriesOnlyOwnedPaths(): Unit = runBlocking {
        withTimeout(10000) {
            val root = temporary.newFolder()
            val out = File(root, "out").apply { mkdir() }
            val keep = File(out, "keep").apply { writeText("keep") }
            val published = CompletableDeferred<Unit>()
            val deletionDenied = CompletableDeferred<Unit>()
            val gate = CountDownLatch(1)
            var failDelete = true
            val files = object : VoiceEffectsFiles() {
                override fun publish(temporary: File, destination: File) {
                    super.publish(temporary, destination)
                    published.complete(Unit); check(gate.await(10, TimeUnit.SECONDS))
                }
                override fun delete(file: File) {
                    if (failDelete) { deletionDenied.complete(Unit); throw IOException("deletion denied") }
                    super.delete(file)
                }
            }
            val client = VoiceEffectsClient(ControlledBackend(), out, files)
            val request = VoiceEffectsRequest(root.inputWave(), VoiceEffectsPreset.HIGH)
            val work = async { client.process(request) }
            try {
                published.await(); work.cancel(); gate.countDown()
                deletionDenied.await()
                assertFalse(work.isCompleted)
                assertFailsWith<VoiceEffectsError.Busy> { client.process(request) }
                assertFailsWith<VoiceEffectsError.Platform> { client.drain() }
                assertFalse(work.isCompleted)
                failDelete = false
                client.drain()
                assertFailsWith<CancellationException> { work.await() }; work.join()
                assertEquals(listOf("keep"), out.listFiles().orEmpty().map { it.name })
                val next = client.process(request); client.releaseOutput(next.id)
                assertEquals("keep", keep.readText())
            } finally { gate.countDown(); failDelete = false; client.drain(); work.cancelAndJoin(); client.close() }
        }
    }

    @Test fun concurrentReleasesDeleteOwnedOutputOnceAndUnknownIdsPerformNoIo(): Unit = runBlocking {
        val root = temporary.newFolder()
        val deletions = AtomicInteger()
        val files = object : VoiceEffectsFiles() {
            override fun delete(file: File) {
                if (file.extension == "wav") deletions.incrementAndGet()
                super.delete(file)
            }
        }
        val client = VoiceEffectsClient(ControlledBackend(), File(root, "out"), files)
        val output = client.process(VoiceEffectsRequest(root.inputWave(), VoiceEffectsPreset.ORIGINAL))
        client.close()
        coroutineScope {
            listOf(async { client.releaseOutput(output.id) }, async { client.releaseOutput(output.id) }).awaitAll()
        }
        client.releaseOutput("unknown")
        assertEquals(1, deletions.get()); assertFalse(output.audioFile.exists())
    }

    @Test fun cancellationHoldsBusyUntilRealRenderAndDrainExit(): Unit = runBlocking {
        withTimeout(10000) {
            val backend = ControlledBackend(blockRender = true, blockDrain = true)
            val root = temporary.newFolder()
            val input = root.inputWave()
            val outputs = File(root, "outputs")
            val client = VoiceEffectsClient(backend, outputs)
            val work = async { client.process(VoiceEffectsRequest(input, VoiceEffectsPreset.HIGH)) }
            try {
                backend.rendering.entered.await()
                work.cancel()
                assertFalse(work.isCompleted)
                assertFailsWith<VoiceEffectsError.Busy> { client.process(VoiceEffectsRequest(input, VoiceEffectsPreset.LOW)) }
                val drain = async(start = CoroutineStart.UNDISPATCHED) { client.drain() }
                assertFalse(drain.isCompleted)
                backend.rendering.open()
                backend.draining.entered.await()
                assertFalse(work.isCompleted)
                assertFalse(drain.isCompleted)
                assertFailsWith<VoiceEffectsError.Busy> { client.process(VoiceEffectsRequest(input, VoiceEffectsPreset.ORIGINAL)) }
                backend.draining.open()
                assertFailsWith<CancellationException> { work.await() }
                work.join(); drain.await()
                assertEquals(1, backend.renderExits.get())
                assertTrue(outputs.listFiles().orEmpty().isEmpty())
                val next = client.process(VoiceEffectsRequest(input, VoiceEffectsPreset.LOW))
                client.releaseOutput(next.id)
            } finally { backend.openAll(); work.cancelAndJoin(); client.close() }
        }
    }

    @Test fun closeDuringRenderIsSharedWaitAndPreventsLateDelivery(): Unit = runBlocking {
        withTimeout(10000) {
            val backend = ControlledBackend(blockRender = true, blockDrain = true, blockClose = true)
            val root = temporary.newFolder()
            val client = VoiceEffectsClient(backend, File(root, "outputs"))
            val request = VoiceEffectsRequest(root.inputWave(), VoiceEffectsPreset.HIGH)
            val work = async { client.process(request) }
            try {
                backend.rendering.entered.await()
                val first = async(start = CoroutineStart.UNDISPATCHED) { client.close() }
                val second = async(start = CoroutineStart.UNDISPATCHED) { client.close() }
                assertFailsWith<VoiceEffectsError.Closed> { client.process(request) }
                assertFalse(first.isCompleted); assertFalse(second.isCompleted)
                backend.rendering.open(); backend.draining.entered.await()
                assertEquals(0, backend.closes.get())
                backend.draining.open(); backend.closing.entered.await()
                assertFailsWith<CancellationException> { work.await() }
                assertFalse(first.isCompleted); assertFalse(second.isCompleted)
                backend.closing.open(); first.await(); second.await(); client.close()
                assertEquals(1, backend.closes.get())
                assertTrue(File(root, "outputs").listFiles().orEmpty().isEmpty())
            } finally { backend.openAll(); work.cancelAndJoin(); client.close() }
        }
    }

    @Test fun cancelledCloseAndDrainWaitersStillWaitForActualExit(): Unit = runBlocking {
        withTimeout(10000) {
            val backend = ControlledBackend(blockRender = true)
            val root = temporary.newFolder()
            val client = VoiceEffectsClient(backend, File(root, "outputs"))
            val work = async { client.process(VoiceEffectsRequest(root.inputWave(), VoiceEffectsPreset.HIGH)) }
            try {
                backend.rendering.entered.await()
                val drain = launch(start = CoroutineStart.UNDISPATCHED) { client.drain() }
                val close = launch(start = CoroutineStart.UNDISPATCHED) { client.close() }
                drain.cancel(); close.cancel()
                assertFalse(drain.isCompleted); assertFalse(close.isCompleted)
                backend.rendering.open()
                drain.join(); close.join(); work.join()
                assertEquals(1, backend.renderExits.get())
                assertEquals(1, backend.closes.get())
            } finally { backend.openAll(); work.cancelAndJoin(); client.close() }
        }
    }

    @Test fun deliveredOutputSurvivesCloseReleaseRetriesAndIsClientScoped(): Unit = runBlocking {
        val root = temporary.newFolder()
        var failDelete = true
        val files = object : VoiceEffectsFiles() {
            override fun delete(file: File) {
                if (file.extension == "wav" && failDelete) throw IOException("private path must not escape")
                super.delete(file)
            }
        }
        val backend = ControlledBackend()
        val client = VoiceEffectsClient(backend, File(root, "outputs"), files)
        val other = VoiceEffectsClient(ControlledBackend(), File(root, "outputs"))
        val input = root.inputWave()
        val before = input.readBytes()
        val result = client.process(VoiceEffectsRequest(input, VoiceEffectsPreset.HIGH))
        client.close(); other.close()
        assertTrue(result.audioFile.isFile)
        other.releaseOutput(result.id); client.releaseOutput("../input.wav")
        assertTrue(result.audioFile.isFile)
        val error = assertFailsWith<VoiceEffectsError.Platform> { client.releaseOutput(result.id) }
        assertNull(error.cause)
        assertFalse(error.message.orEmpty().contains(root.path))
        assertTrue(result.audioFile.exists())
        failDelete = false
        client.releaseOutput(result.id); client.releaseOutput(result.id)
        assertFalse(result.audioFile.exists())
        assertContentEquals(before, input.readBytes())
        assertFailsWith<VoiceEffectsError.Closed> { client.process(VoiceEffectsRequest(input, VoiceEffectsPreset.ORIGINAL)) }
    }

    @Test fun closeWhilePublishingSuppressesLateFileAndPreservesExistingFiles(): Unit = runBlocking {
        withTimeout(10000) {
            val root = temporary.newFolder()
            val input = root.inputWave()
            val before = input.readBytes()
            val outputs = File(root, "outputs").apply { mkdir() }
            val existing = File(outputs, "existing.wav").apply { writeText("untouched") }
            val entered = CompletableDeferred<Unit>()
            val gate = CountDownLatch(1)
            val files = object : VoiceEffectsFiles() {
                override fun publish(temporary: File, destination: File) {
                    entered.complete(Unit)
                    check(gate.await(10, TimeUnit.SECONDS))
                    super.publish(temporary, destination)
                }
            }
            val client = VoiceEffectsClient(ControlledBackend(), outputs, files)
            val work = async { client.process(VoiceEffectsRequest(input, VoiceEffectsPreset.HIGH)) }
            try {
                entered.await()
                val close = async(start = CoroutineStart.UNDISPATCHED) { client.close() }
                assertFalse(close.isCompleted)
                gate.countDown()
                assertFailsWith<CancellationException> { work.await() }
                close.await()
                assertEquals(listOf(existing.name), outputs.listFiles().orEmpty().map { it.name })
                assertEquals("untouched", existing.readText()); assertContentEquals(before, input.readBytes())
            } finally { gate.countDown(); work.cancelAndJoin(); client.close() }
        }
    }

    @Test fun cancellationWhilePublishingDoesNotDeliverAndDoesNotFreeSlotEarly(): Unit = runBlocking {
        withTimeout(10000) {
            val root = temporary.newFolder()
            val entered = CompletableDeferred<Unit>()
            val gate = CountDownLatch(1)
            val outputs = File(root, "out")
            val files = object : VoiceEffectsFiles() {
                override fun publish(temporary: File, destination: File) {
                    entered.complete(Unit); check(gate.await(10, TimeUnit.SECONDS))
                    super.publish(temporary, destination)
                }
            }
            val client = VoiceEffectsClient(ControlledBackend(), outputs, files)
            val request = VoiceEffectsRequest(root.inputWave(), VoiceEffectsPreset.HIGH)
            val work = async { client.process(request) }
            try {
                entered.await(); work.cancel()
                assertFalse(work.isCompleted)
                assertFailsWith<VoiceEffectsError.Busy> { client.process(request) }
                gate.countDown(); assertFailsWith<CancellationException> { work.await() }; work.join()
                client.drain(); assertTrue(outputs.listFiles().orEmpty().isEmpty())
            } finally { gate.countDown(); work.cancelAndJoin(); client.close() }
        }
    }

    @Test fun collisionNeverOverwritesInputOrExistingDestination(): Unit = runBlocking {
        val root = temporary.newFolder()
        val input = File(root, "fixed.wav").apply { writeBytes(wave()) }
        val before = input.readBytes()
        val files = object : VoiceEffectsFiles() { override fun nextId() = "fixed" }
        val client = VoiceEffectsClient(ControlledBackend(), root, files)
        assertFailsWith<VoiceEffectsError.Platform> { client.process(VoiceEffectsRequest(input, VoiceEffectsPreset.ORIGINAL)) }
        assertContentEquals(before, input.readBytes())
        assertEquals(listOf("fixed.wav"), root.listFiles().orEmpty().map { it.name })
        client.close()
    }

    @Test fun renderReadCloseWriteCloseAndPublishFailuresLeaveNoPartialOutput(): Unit = runBlocking {
        for (fault in listOf("render", "read-close", "write-close", "publish", "drain")) {
            val root = temporary.newFolder()
            val input = root.inputWave()
            val before = input.readBytes()
            val outputs = File(root, "outputs").apply { mkdir() }
            val keep = File(outputs, "keep").apply { writeText("keep") }
            val files = object : VoiceEffectsFiles() {
                override fun openInput(file: File): InputStream = object : FilterInputStream(super.openInput(file)) {
                    override fun close() { super.close(); if (fault == "read-close") throw IOException("raw input path") }
                }
                override fun openOutput(file: File): OutputStream = object : FilterOutputStream(super.openOutput(file)) {
                    override fun close() { super.close(); if (fault == "write-close") throw IOException("raw output path") }
                }
                override fun publish(temporary: File, destination: File) {
                    if (fault == "publish") throw IOException("raw publication path")
                    super.publish(temporary, destination)
                }
            }
            val backend = object : VoiceEffectsBackend {
                override val backendId = "android.windowed-pitch"
                override suspend fun render(input: FloatArray, sampleRate: Int, preset: VoiceEffectsPreset): FloatArray {
                    if (fault == "render") throw IOException("private backend exception")
                    return input.copyOf()
                }
                override suspend fun drain() { if (fault == "drain") throw IOException("private drain failure") }
            }
            val client = VoiceEffectsClient(backend, outputs, files)
            val error = assertFailsWith<VoiceEffectsError.Platform> {
                client.process(VoiceEffectsRequest(input, VoiceEffectsPreset.HIGH))
            }
            assertNull(error.cause); assertEquals("voice effects platform failure", error.message)
            assertContentEquals(before, input.readBytes())
            assertEquals(listOf(keep.name), outputs.listFiles().orEmpty().map { it.name })
            client.close()
        }
    }

    @Test fun typedUnavailableAndCancellationAreNotConvertedToPlatformFailures(): Unit = runBlocking {
        val root = temporary.newFolder()
        val input = root.inputWave()
        for (cancel in listOf(false, true)) {
            val backend = object : VoiceEffectsBackend {
                override val backendId = "android.windowed-pitch"
                override suspend fun render(input: FloatArray, sampleRate: Int, preset: VoiceEffectsPreset): FloatArray {
                    if (cancel) throw CancellationException("cancelled")
                    throw VoiceEffectsError.BackendUnavailable()
                }
            }
            val client = VoiceEffectsClient(backend, File(root, "out"))
            if (cancel) assertFailsWith<CancellationException> { client.process(VoiceEffectsRequest(input, VoiceEffectsPreset.HIGH)) }
            else assertFailsWith<VoiceEffectsError.BackendUnavailable> { client.process(VoiceEffectsRequest(input, VoiceEffectsPreset.HIGH)) }
            client.drain(); client.close()
        }
    }
}

private class Gate(block: Boolean) {
    val entered = CompletableDeferred<Unit>()
    private val opened = CompletableDeferred<Unit>().apply { if (!block) complete(Unit) }
    suspend fun waitForRelease() = withContext(NonCancellable) { entered.complete(Unit); opened.await() }
    fun open() { opened.complete(Unit) }
}

private class ControlledBackend(blockRender: Boolean = false, blockDrain: Boolean = false, blockClose: Boolean = false) : VoiceEffectsBackend {
    override val backendId = "android.windowed-pitch"
    val rendering = Gate(blockRender); val draining = Gate(blockDrain); val closing = Gate(blockClose)
    val renderExits = AtomicInteger(); val closes = AtomicInteger()
    override suspend fun render(input: FloatArray, sampleRate: Int, preset: VoiceEffectsPreset): FloatArray {
        try { rendering.waitForRelease(); return input.copyOf() }
        finally { renderExits.incrementAndGet() }
    }
    override suspend fun drain() { currentCoroutineContext().ensureActive(); draining.waitForRelease() }
    override suspend fun close() { closes.incrementAndGet(); closing.waitForRelease() }
    fun openAll() { rendering.open(); draining.open(); closing.open() }
}
