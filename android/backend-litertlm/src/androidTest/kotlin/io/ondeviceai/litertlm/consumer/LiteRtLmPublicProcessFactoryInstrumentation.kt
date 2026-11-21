package io.ondeviceai.litertlm.consumer

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import io.ondeviceai.litertlm.LiteRtLm
import io.ondeviceai.litertlm.LiteRtLmBackend
import io.ondeviceai.litertlm.LiteRtLmModel
import io.ondeviceai.litertlm.LiteRtLmRuntimeEnvironment
import io.ondeviceai.modelstore.ModelCompatibility
import io.ondeviceai.modelstore.ModelRef
import io.ondeviceai.modelstore.ModelStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Public-consumer instrumentation for the explicit process-backed [LiteRtLm.createProcessBackend].
 *
 * Uses only public API; it never touches the internal engine/factory/session. The dedicated process
 * is never actually bound or opened: a [ContextWrapper] overrides [bindService]/[unbindService] to
 * count and fail loudly if ever invoked, proving construction, [LiteRtLmBackend.availability] and
 * [LiteRtLmBackend.close] stay bind-free. No native model, inference or service is spawned.
 */
class LiteRtLmPublicProcessFactoryInstrumentation {

    private class BindingContext(base: Context) : ContextWrapper(base) {
        var bindCount = 0
            private set
        var unbindCount = 0
            private set

        override fun getApplicationContext(): Context = this

        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
            bindCount++
            throw AssertionError("createProcessBackend must not bind the dedicated process")
        }

        override fun unbindService(conn: ServiceConnection) {
            unbindCount++
            throw AssertionError("createProcessBackend must not unbind the dedicated process")
        }
    }

    private fun newModel() = LiteRtLmModel(
        ref = ModelRef("public-consumer-test", "1"),
        modelPathRelative = "model.litertlm",
        metadataPathsRelative = setOf("model-card.txt"),
    )

    @Test
    fun constructsWithoutBindingAndReportsMissingAssetsThenClosed() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val context = BindingContext(target)
        val root = File(target.cacheDir, "litertlm-root-${UUID.randomUUID()}")
        check(root.mkdir()) { "test must own its exclusive cache root" }
        val store = ModelStore(
            File(root, "store"),
            ModelCompatibility("litertlm.android", "0.18.1", Build.VERSION.SDK_INT.toString(), "arm64-v8a"),
        )
        val cacheRoot = File(root, "cache")
        check(cacheRoot.mkdir()) { "test must own its cache child" }

        var backend: LiteRtLmBackend? = null
        try {
            val b = LiteRtLm.createProcessBackend(
                context, store, newModel(), cacheRoot,
                LiteRtLmRuntimeEnvironment(Build.VERSION.SDK_INT, setOf("arm64-v8a")),
            )
            backend = b

            assertEquals("no bind during construction", 0, context.bindCount)
            assertEquals("no unbind during construction", 0, context.unbindCount)

            val available = b.availability()
            assertEquals(CapabilityStatus.REQUIRES_ASSETS, available.status)
            assertEquals(UnavailableReason.MISSING_ASSETS, available.reason)

            b.close()

            val afterClose = b.availability()
            assertEquals(UnavailableReason.CLOSED, afterClose.reason)

            assertEquals("still no bind after availability+close", 0, context.bindCount)
            assertEquals("still no unbind after availability+close", 0, context.unbindCount)
        } finally {
            withContext(NonCancellable) {
                var closeErr: Throwable? = null
                try {
                    backend?.close()
                } catch (t: Throwable) {
                    closeErr = t
                }
                if (!root.deleteRecursively()) {
                    val delErr = AssertionError("failed to delete owned cache root: $root")
                    closeErr?.let { delErr.addSuppressed(it) }
                    throw delErr
                }
                closeErr?.let { throw it }
            }
        }
    }

    @Test
    fun lowOsVersionReportsRequiresOsVersionWithoutBinding() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val context = BindingContext(target)
        val root = File(target.cacheDir, "litertlm-root-${UUID.randomUUID()}")
        check(root.mkdir()) { "test must own its exclusive cache root" }
        val store = ModelStore(
            File(root, "store"),
            ModelCompatibility("litertlm.android", "0.18.1", Build.VERSION.SDK_INT.toString(), "arm64-v8a"),
        )
        val cacheRoot = File(root, "cache")
        check(cacheRoot.mkdir()) { "test must own its cache child" }

        var backend: LiteRtLmBackend? = null
        try {
            val b = LiteRtLm.createProcessBackend(
                context, store, newModel(), cacheRoot,
                LiteRtLmRuntimeEnvironment(25, setOf("arm64-v8a")),
            )
            backend = b
            val available = b.availability()
            assertEquals(UnavailableReason.REQUIRES_OS_VERSION, available.reason)
            assertEquals("no bind on unsupported OS version", 0, context.bindCount)
            assertEquals("no unbind on unsupported OS version", 0, context.unbindCount)
            b.close()
        } finally {
            withContext(NonCancellable) {
                var closeErr: Throwable? = null
                try {
                    backend?.close()
                } catch (t: Throwable) {
                    closeErr = t
                }
                if (!root.deleteRecursively()) {
                    val delErr = AssertionError("failed to delete owned cache root: $root")
                    closeErr?.let { delErr.addSuppressed(it) }
                    throw delErr
                }
                closeErr?.let { throw it }
            }
        }
    }

    @Test
    fun unsupportedAbiReportsUnsupportedDeviceWithoutBinding() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val context = BindingContext(target)
        val root = File(target.cacheDir, "litertlm-root-${UUID.randomUUID()}")
        check(root.mkdir()) { "test must own its exclusive cache root" }
        val store = ModelStore(
            File(root, "store"),
            ModelCompatibility("litertlm.android", "0.18.1", Build.VERSION.SDK_INT.toString(), "x86"),
        )
        val cacheRoot = File(root, "cache")
        check(cacheRoot.mkdir()) { "test must own its cache child" }

        var backend: LiteRtLmBackend? = null
        try {
            val b = LiteRtLm.createProcessBackend(
                context, store, newModel(), cacheRoot,
                LiteRtLmRuntimeEnvironment(Build.VERSION.SDK_INT, setOf("x86")),
            )
            backend = b
            val available = b.availability()
            assertEquals(UnavailableReason.UNSUPPORTED_DEVICE, available.reason)
            assertEquals("no bind on unsupported ABI", 0, context.bindCount)
            assertEquals("no unbind on unsupported ABI", 0, context.unbindCount)
            b.close()
        } finally {
            withContext(NonCancellable) {
                var closeErr: Throwable? = null
                try {
                    backend?.close()
                } catch (t: Throwable) {
                    closeErr = t
                }
                if (!root.deleteRecursively()) {
                    val delErr = AssertionError("failed to delete owned cache root: $root")
                    closeErr?.let { delErr.addSuppressed(it) }
                    throw delErr
                }
                closeErr?.let { throw it }
            }
        }
    }
}
