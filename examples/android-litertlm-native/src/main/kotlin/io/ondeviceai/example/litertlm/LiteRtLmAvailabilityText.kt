package io.ondeviceai.example.litertlm

import io.ondeviceai.core.CapabilityAvailability

internal object LiteRtLmAvailabilityText {
    fun format(value: CapabilityAvailability): String = buildString {
        append("Backend: ").append(value.backendId)
        append("\nStatus: ").append(value.status.name)
        append("\nReason: ").append(value.reason?.name ?: "NONE")
        append("\nMinimum Android API: ").append(value.minOS ?: "unknown")
    }
}
