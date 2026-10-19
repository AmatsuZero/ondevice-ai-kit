package io.ondeviceai.speech

import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

enum class SpeechCapability { ASR, TTS }
sealed class SpeechBackendSelection {
    data object Default : SpeechBackendSelection()
    data class Explicit(val backendId: String) : SpeechBackendSelection()
    companion object { fun explicit(backendId: String): SpeechBackendSelection = Explicit(backendId) }
}
interface SpeechBackend {
    val backendId: String
    val capability: SpeechCapability
    suspend fun availability(): CapabilityAvailability
    suspend fun close()
}
data class SpeechBackendRegistration(val capability: SpeechCapability, val backend: SpeechBackend)
data class SpeechBackendDescriptor(val capability: SpeechCapability, val backendId: String, val availability: CapabilityAvailability)
sealed class SpeechClientError(message: String) : IllegalStateException(message) {
    data class BackendNotRegistered(val capability: SpeechCapability, val backendId: String) : SpeechClientError("backend not registered: $backendId")
    data class BackendUnavailable(val capability: SpeechCapability, val backendId: String, val reason: UnavailableReason, val diagnostic: Throwable? = null) : SpeechClientError("backend unavailable: $backendId") {
        init { diagnostic?.let(::initCause) }
    }
}

class SpeechBackendUnavailableException(val reason: UnavailableReason, cause: Throwable? = null) : IllegalStateException("backend unavailable: $reason", cause)

/** Capability-scoped registry; task and audio contracts are added with concrete providers. */
class SpeechClient(val capability: SpeechCapability, private val defaultBackendId: String,
                   registrations: List<SpeechBackendRegistration>) {
    private val table: Map<String, SpeechBackend>
    private val closeLock = Any()
    @Volatile private var closed = false
    private var closeCompletion: CompletableDeferred<Unit>? = null
    init {
        require(defaultBackendId.isNotBlank())
        require(registrations.all { it.capability == capability && it.backend.capability == capability && it.backend.backendId.isNotBlank() })
        require(registrations.map { it.backend.backendId }.toSet().size == registrations.size)
        table = registrations.associate { it.backend.backendId to it.backend }
        require(defaultBackendId in table)
    }
    suspend fun listBackends(): List<SpeechBackendDescriptor> = table.values.map {
        SpeechBackendDescriptor(capability, it.backendId, it.availability())
    }.sortedBy { it.backendId }
    suspend fun select(selection: SpeechBackendSelection = SpeechBackendSelection.Default): SpeechBackendDescriptor {
        check(!closed) { "closed" }
        val id = when (selection) {
            SpeechBackendSelection.Default -> defaultBackendId
            is SpeechBackendSelection.Explicit -> selection.backendId
        }
        val backend = table[id] ?: throw SpeechClientError.BackendNotRegistered(capability, id)
        val availability = backend.availability()
        check(!closed) { "closed" }
        if (availability.status != CapabilityStatus.AVAILABLE) {
            throw SpeechClientError.BackendUnavailable(capability, id, availability.reason ?: UnavailableReason.UNSUPPORTED_DEVICE)
        }
        return SpeechBackendDescriptor(capability, id, availability)
    }
    suspend fun close() {
        val (completion, ownsClose) = synchronized(closeLock) {
            closed = true
            closeCompletion?.let { it to false } ?: CompletableDeferred<Unit>().let {
                closeCompletion = it
                it to true
            }
        }
        if (ownsClose) {
            withContext(NonCancellable) {
                try {
                    table.values.forEach { it.close() }
                    completion.complete(Unit)
                } catch (error: Throwable) {
                    completion.completeExceptionally(error)
                }
            }
        }
        completion.await()
    }
}

data class SpeechSynthesisRequest(val text: String, val locale: String, val voiceId: String? = null)
data class SpeechSynthesisResult(val backendId: String, val voiceId: String? = null)
interface SpeechSynthesisBackend : SpeechBackend {
    suspend fun speak(request: SpeechSynthesisRequest): SpeechSynthesisResult
}

/** Typed TTS facade over the capability registry; explicit IDs never fall back. */
class SpeechSynthesisClient(defaultBackendId: String, backends: List<SpeechSynthesisBackend>) {
    private val registry = SpeechClient(
        SpeechCapability.TTS, defaultBackendId,
        backends.map { SpeechBackendRegistration(SpeechCapability.TTS, it) }
    )
    private val table = backends.associateBy { it.backendId }
    init { require(backends.map { it.backendId }.toSet().size == backends.size) }
    suspend fun speak(request: SpeechSynthesisRequest, selection: SpeechBackendSelection = SpeechBackendSelection.Default): SpeechSynthesisResult {
        require(request.text.isNotEmpty()) { "empty text" }
        val descriptor = registry.select(selection)
        return table.getValue(descriptor.backendId).speak(request)
    }
    suspend fun close() = registry.close()
}

data class SpeechRecognitionRequest(val locale: String) {
    init { require(locale.isNotBlank()) { "locale must not be blank" } }
}

data class SpeechRecognitionResult(
    val backendId: String,
    val transcript: String,
    val confidence: Float? = null
)

interface SpeechRecognitionBackend : SpeechBackend {
    suspend fun recognize(request: SpeechRecognitionRequest): SpeechRecognitionResult
}

/** Typed ASR facade. A selected backend is queried and invoked exactly once. */
class SpeechRecognitionClient(defaultBackendId: String, backends: List<SpeechRecognitionBackend>) {
    private val registry = SpeechClient(
        SpeechCapability.ASR, defaultBackendId,
        backends.map { SpeechBackendRegistration(SpeechCapability.ASR, it) }
    )
    private val table = backends.associateBy { it.backendId }
    init { require(backends.map { it.backendId }.toSet().size == backends.size) }
    suspend fun recognize(
        request: SpeechRecognitionRequest,
        selection: SpeechBackendSelection = SpeechBackendSelection.Default
    ): SpeechRecognitionResult {
        val descriptor = registry.select(selection)
        return try {
            table.getValue(descriptor.backendId).recognize(request)
        } catch (error: SpeechBackendUnavailableException) {
            throw SpeechClientError.BackendUnavailable(capability = SpeechCapability.ASR, backendId = descriptor.backendId, reason = error.reason, diagnostic = error)
        }
    }
    suspend fun close() = registry.close()
}
