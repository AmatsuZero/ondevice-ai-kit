package io.ondeviceai.demo

import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import io.ondeviceai.litertlm.LiteRtLm
import io.ondeviceai.litertlm.LiteRtLmModel
import io.ondeviceai.litertlm.LiteRtLmRuntimeEnvironment
import io.ondeviceai.modelstore.ModelManifest
import io.ondeviceai.modelstore.ModelStore
import io.ondeviceai.multimodal.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Installs only the explicitly staged local bundle; never downloads or accepts model terms. */
internal class LiteRtLmDemoBackend(
    private val imported: File,
    private val store: ModelStore,
    private val cacheRoot: File,
    private val environment: LiteRtLmRuntimeEnvironment,
    private val create: (ModelStore, LiteRtLmModel, File, LiteRtLmRuntimeEnvironment) -> MultimodalGenerationBackend =
        LiteRtLm::createBackend,
) : MultimodalGenerationBackend {
    override val backendId = "litertlm.android"
    private val mutex = Mutex()
    private var backend: MultimodalGenerationBackend? = null
    private var closed = false

    private suspend fun prepare(): MultimodalGenerationBackend? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (closed) throw MultimodalClientError.Closed()
            backend?.let { return@withLock it }
            val manifestFile = File(imported, "manifest.json")
            val descriptorFile = File(imported, "demo-model.json")
            if (!manifestFile.isFile || !descriptorFile.isFile) return@withLock null
            val manifest = ModelManifest.parse(manifestFile.readText())
            val descriptor = JSONObject(descriptorFile.readText())
            require(descriptor.getInt("schemaVersion") == 1)
            val paths = descriptor.getJSONArray("metadataPaths")
            val model = LiteRtLmModel(manifest.ref, descriptor.getString("modelPath"),
                (0 until paths.length()).map { paths.getString(it) }.toSet())
            // Validate paths before installing the bundle or constructing any native runtime.
            require(model.modelPathRelative in manifest.files.map { it.path })
            require(model.metadataPathsRelative.isNotEmpty() &&
                manifest.files.map { it.path }.containsAll(model.metadataPathsRelative))
            store.install(manifest, File(imported, "files"))
            create(store, model, cacheRoot, environment).also { backend = it }
        }
    }

    override suspend fun availability(): CapabilityAvailability = try {
        prepare()?.availability() ?: CapabilityAvailability(CapabilityStatus.REQUIRES_ASSETS,
            UnavailableReason.MISSING_ASSETS, backendId, "26")
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: MultimodalClientError.Closed) {
        CapabilityAvailability(CapabilityStatus.UNAVAILABLE, UnavailableReason.CLOSED, backendId, "26")
    } catch (_: Exception) {
        CapabilityAvailability(CapabilityStatus.UNAVAILABLE, UnavailableReason.UNSUPPORTED_DEVICE, backendId, "26")
    }

    override suspend fun generate(request: MultimodalRequest,
        update: ((MultimodalTextUpdate) -> Unit)?): MultimodalTextOutput {
        val native = prepare() ?: throw MultimodalClientError.BackendUnavailable(backendId, UnavailableReason.MISSING_ASSETS)
        return native.generate(request, update)
    }

    override suspend fun drain() { mutex.withLock { backend }?.drain() }

    override suspend fun close(): Unit = withContext(NonCancellable) {
        val native = mutex.withLock { closed = true; backend }
        native?.close()
    }
}
