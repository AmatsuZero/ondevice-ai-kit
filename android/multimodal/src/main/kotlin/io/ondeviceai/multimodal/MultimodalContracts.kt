package io.ondeviceai.multimodal

import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.UnavailableReason
import java.io.File

/** Value validation only; the backend validates and opens the image during admission. */
data class MultimodalRequest(val imageFile: File, val prompt: String, val maxOutputTokens: Int = 128) {
    init {
        if (prompt.isBlank()) throw MultimodalClientError.InvalidInput("prompt must not be blank")
        if (maxOutputTokens <= 0) throw MultimodalClientError.InvalidInput("output budget must be positive")
    }
}

/** Value validation only; video support and accepted media profiles are backend-specific. */
data class MultimodalVideoRequest(val videoFile: File, val prompt: String, val maxOutputTokens: Int = 128) {
    init {
        if (prompt.isBlank()) throw MultimodalClientError.InvalidInput("prompt must not be blank")
        if (maxOutputTokens <= 0) throw MultimodalClientError.InvalidInput("output budget must be positive")
    }
}

data class MultimodalModelIdentity(val modelId: String, val modelRevision: String, val runtimeRevision: String) {
    init {
        if (listOf(modelId, modelRevision, runtimeRevision).any { it.isBlank() }) {
            throw MultimodalClientError.InvalidInput("model identity fields must not be blank")
        }
    }
}

/** Cumulative text snapshot; sequence is an update ordinal, not a token count. */
data class MultimodalTextUpdate(val sequence: Int, val text: String) {
    init { if (sequence < 0) throw MultimodalClientError.InvalidInput("sequence must be non-negative") }
}

/** Immutable result value that remains valid after client.close(). */
data class MultimodalTextOutput(val text: String, val model: MultimodalModelIdentity) {
    init { if (text.isBlank()) throw MultimodalClientError.InvalidInput("output text must not be blank") }
}

data class MultimodalGenerationResult(val backendId: String, val output: MultimodalTextOutput) {
    init { if (backendId.isBlank()) throw MultimodalClientError.InvalidInput("backend ID must not be blank") }
}

sealed class MultimodalBackendSelection {
    data object Default : MultimodalBackendSelection()
    data class Explicit(val backendId: String) : MultimodalBackendSelection()
}

/** Owned by the client. Implementations honor request budgets and make close nonthrowing. */
interface MultimodalGenerationBackend {
    val backendId: String
    suspend fun availability(): CapabilityAvailability
    suspend fun generate(request: MultimodalRequest, update: ((MultimodalTextUpdate) -> Unit)? = null): MultimodalTextOutput
    suspend fun drain()
    suspend fun close()
}

/** Explicit video opt-in; general backend availability does not promise video support.
 * Image and video share the owning client's operation and cleanup barriers.
 */
interface MultimodalVideoGenerationBackend : MultimodalGenerationBackend {
    suspend fun generateVideo(request: MultimodalVideoRequest,
                              update: ((MultimodalTextUpdate) -> Unit)? = null): MultimodalTextOutput
}

data class MultimodalBackendDescriptor(val backendId: String, val availability: CapabilityAvailability)

sealed class MultimodalClientError(message: String) : IllegalStateException(message) {
    data class InvalidInput(val detail: String) : MultimodalClientError(detail)
    data class InvalidRegistration(val detail: String) : MultimodalClientError(detail)
    data class BackendNotRegistered(val backendId: String) : MultimodalClientError("backend not registered: $backendId")
    data class BackendUnavailable(val backendId: String, val reason: UnavailableReason) : MultimodalClientError("backend unavailable: $backendId")
    class Busy : MultimodalClientError("busy")
    class Closed : MultimodalClientError("closed")
}
