package io.ondeviceai.demo

import android.content.Context
import android.graphics.Bitmap
import io.ondeviceai.binding.AndroidIdentityBridge
import io.ondeviceai.binding.IdentityAuthorizationGrant
import io.ondeviceai.binding.IdentityCandidateReference
import io.ondeviceai.binding.IdentityEnrollmentOptions
import io.ondeviceai.binding.IdentityProbeReference
import io.ondeviceai.binding.IdentityThresholdPolicy
import io.ondeviceai.binding.IdentityVerificationOptions
import io.ondeviceai.identity.*
import io.ondeviceai.modelstore.ModelStore
import io.ondeviceai.opencvidentity.*
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One user-enabled local session. Images remain task-scoped; only encrypted templates persist. */
internal class OpenCvPrototypeSession private constructor(
    val backend: OpenCvIdentityBackend,
    private val consent: PrototypeIdentityConsent,
    private val store: IdentityStore,
    private val indexes: IndexStore,
    private val candidate: CandidateRef,
) {
    private val input = AtomicReference<Pair<String, IdentityProbe>?>(null)
    private val cleanup = Mutex()
    private var cleanupResult: Result<Unit>? = null
    private val verification = FaceVerification(store, backend.backendId, listOf(backend))
    private val identification = FaceIdentification(store, indexes, backend.backendId, listOf(backend))
    val bridge = AndroidIdentityBridge(store, indexes,
        IdentityBackendRegistry(backend.backendId, listOf(IdentityBackendRegistration(backend.backendId, backend))),
        verification, identification, probeResolver = { reference ->
            input.get()?.takeIf { it.first == reference.canonicalValue }?.second ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        })
    val configuration = IdentityDemoConfiguration(
        grant = IdentityAuthorizationGrant(consent.grant.canonicalJSON()),
        candidate = IdentityCandidateReference(candidate.canonicalJSON()),
        enrollmentOptions = IdentityEnrollmentOptions(1.0, "geometric-only-uncalibrated", "v1"),
        verificationOptions = IdentityVerificationOptions(IdentityThresholdPolicy(
            OpenCvIdentityModel.unverifiedPolicy(IdentityPolicyKind.VERIFICATION).canonicalJSON()), "v1"))

    fun probe(bitmap: Bitmap): IdentityProbeReference {
        val bytes = ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)); output.toByteArray()
        }
        try {
            val id = UUID.randomUUID().toString()
            input.set(id to IdentityProbe(bytes, "demo-upright-png-v1"))
            return IdentityProbeReference(id)
        } finally { bytes.fill(0) }
    }

    suspend fun deleteAndClose(): Unit = withContext(NonCancellable + Dispatchers.IO) {
        cleanup.withLock {
            cleanupResult?.let { return@withLock it.getOrThrow() }
            val result = runCatching {
                bridge.close()
                consent.close()
                input.set(null)
                try {
                    val receipt = store.deleteCandidate(candidate, consent.ownership())
                    verification.close()
                    identification.close()
                    backend.closeAndWait()
                    check(store.awaitDeletion(receipt,
                        IdentityDeadline(SystemIdentityMonotonicClock, 30_000_000_000)) == DeletionStatus.PURGED)
                } finally {
                    verification.close()
                    identification.close()
                    backend.closeAndWait()
                    indexes.close()
                    store.close()
                }
            }
            cleanupResult = result
            result.getOrThrow()
        }
    }

    companion object {
        suspend fun open(context: Context, models: ModelStore,
            diagnostic: (OpenCvIdentityDiagnostic) -> Unit = {}): OpenCvPrototypeSession {
            var opened: OpenCvPrototypeSession? = null
            try {
                return withContext(Dispatchers.IO) {
                    val consent = PrototypeIdentityConsent(System.currentTimeMillis())
                    val backend = OpenCvIdentityBackend.open(models, diagnostic)
                    var store: IdentityStore? = null
                    var indexes: IndexStore? = null
                    try {
                        val keys = AndroidKeystoreIdentityKeyProvider("opencv-local-prototype")
                        store = IdentityStore.open(context, "opencv-local-prototype", consent.grant.purpose, consent, keys)
                        consent.bindStore(store.storeId)
                        indexes = IndexStore.open(context, "opencv-local-prototype", store, keys, buildIndex = { _, _ ->
                            throw IdentityError(IdentityErrorCode.INDEX_UNAVAILABLE)
                        })
                        val candidate = store.createCandidate(consent.grant)
                        OpenCvPrototypeSession(backend, consent, store, indexes, candidate).also { opened = it }
                    } catch (error: Throwable) {
                        indexes?.close()
                        store?.close()
                        withContext(NonCancellable) { backend.closeAndWait() }
                        throw error
                    }
                }
            } catch (error: Throwable) {
                opened?.deleteAndClose()
                throw error
            }
        }
    }
}
