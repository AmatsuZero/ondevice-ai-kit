package io.ondeviceai.modelstore

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

class ModelLeaseCancellationTest {
    @Test
    fun cancellationBeforeLeaseDeliveryDoesNotPinInstalledModel() = checkCancelledDelivery(false)

    @Test
    fun cancellationBeforeLeaseDeliveryPreservesOtherOwnersLease() = checkCancelledDelivery(true)

    private fun checkCancelledDelivery(withExistingLease: Boolean) = runBlocking {
        val root = Files.createTempDirectory("odai-lease-cancellation").toFile()
        try {
            val source = File(root, "source").apply { mkdirs() }
            val bytes = "synthetic model fixture".toByteArray()
            File(source, "weights.bin").writeBytes(bytes)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            val manifest = fixture(ModelFile("weights.bin", "https://models.example/weights.bin",
                bytes.size.toLong(), hash))
            val store = ModelStore(File(root, "store"), ModelCompatibility("fixture", "1.0.0", "26", "arm64"))
            store.install(manifest, source)
            val existing = if (withExistingLease) store.acquire(manifest.ref) else null

            val caller = QueuedCallerDispatcher()
            val acquisition = async(caller) { store.acquire(manifest.ref) }
            caller.next().run() // Start acquire; verification and lease registration run on IO.
            val delivery = caller.next() // IO completed, but the caller has not received the lease.
            try {
                assertFailsWith<ModelInUseException> { store.remove(manifest.ref) }
            } finally {
                acquisition.cancel()
                delivery.run()
            }
            assertFailsWith<CancellationException> { acquisition.await() }

            if (existing != null) {
                assertFailsWith<ModelInUseException> { store.remove(manifest.ref) }
                existing.close()
            }
            store.remove(manifest.ref)
            assertEquals(ModelStatus.ABSENT, store.status(manifest.ref))
            assertTrue(acquisition.isCancelled)
        } finally {
            root.deleteRecursively()
        }
    }

    private class QueuedCallerDispatcher : CoroutineDispatcher() {
        private val tasks = LinkedBlockingQueue<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
        fun next(): Runnable = assertNotNull(tasks.poll(10, TimeUnit.SECONDS), "caller continuation not queued")
    }
}
