package io.ondeviceai.example.multimodal

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import io.ondeviceai.core.CapabilityAvailability
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.core.UnavailableReason
import io.ondeviceai.multimodal.*
import java.io.File

/** Compile/link consumer only; does not read media, load a model or launch generation. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = MultimodalRequest(File("/nonexistent/linking-sample.png"), "Describe this image.")
        val unavailable = CapabilityAvailability(CapabilityStatus.UNAVAILABLE, UnavailableReason.BACKEND_NOT_LINKED,
            "example.multimodal.unavailable")
        val backend = object : MultimodalGenerationBackend {
            override val backendId = unavailable.backendId
            override suspend fun availability() = unavailable
            override suspend fun generate(request: MultimodalRequest, update: ((MultimodalTextUpdate) -> Unit)?): MultimodalTextOutput {
                throw MultimodalClientError.BackendUnavailable(backendId, UnavailableReason.BACKEND_NOT_LINKED)
            }
            override suspend fun drain() {}
            override suspend fun close() {}
        }
        MultimodalGenerationClient(backend.backendId, listOf(backend))
        setContentView(TextView(this).apply {
            text = "Multimodal SDK linked. No generation backend is installed. Budget: ${request.maxOutputTokens}"
        })
    }
}
