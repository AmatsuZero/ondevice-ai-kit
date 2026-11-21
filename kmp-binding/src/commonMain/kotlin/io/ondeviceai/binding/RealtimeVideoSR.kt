package io.ondeviceai.binding

/** Controls one native player view. All calls are on the host UI thread; no frames cross KMP. */
enum class RealtimeSRBackend { ORIGINAL, SYSTEM, CUNNY, SPATIAL, LITERT }

enum class RealtimeSRMode { ORIGINAL, AUTO, SYSTEM, CUNNY, SPATIAL, LITERT }

data class RealtimeSRStatus(
    val enabled: Boolean,
    val preferredBackend: RealtimeSRBackend,
    /** Null before the first presentation after enabling, disabling or switching stream. */
    val presentedBackend: RealtimeSRBackend?,
    val fellBackToOriginal: Boolean,
)

interface RealtimeSRHost {
    fun applyEnabled(enabled: Boolean)
    fun readStatus(): RealtimeSRStatus
}

/**
 * Advertised modes are host capabilities, not the backend actually presented for each content.
 * Unsupported selections are rejected without changing host state. AUTO is never a presented
 * backend; LITERT is optional and is not implied by file export. Legacy hosts expose only ORIGINAL.
 */
interface SelectableRealtimeSRHost : RealtimeSRHost {
    fun supportedModes(): List<RealtimeSRMode>
    fun selectedMode(): RealtimeSRMode
    fun selectMode(mode: RealtimeSRMode): Boolean
}

class RealtimeVideoSR(private val host: RealtimeSRHost) {
    fun setEnabled(enabled: Boolean) = host.applyEnabled(enabled)
    fun status(): RealtimeSRStatus = host.readStatus()

    fun supportedModes(): List<RealtimeSRMode> =
        if (host is SelectableRealtimeSRHost) host.supportedModes().toList()
        else listOf(RealtimeSRMode.ORIGINAL)

    fun selectedMode(): RealtimeSRMode? =
        if (host is SelectableRealtimeSRHost) host.selectedMode()
        else if (!host.readStatus().enabled) RealtimeSRMode.ORIGINAL else null

    fun selectMode(mode: RealtimeSRMode): Boolean {
        if (host is SelectableRealtimeSRHost) {
            if (mode !in host.supportedModes()) return false
            return host.selectMode(mode)
        }

        if (mode != RealtimeSRMode.ORIGINAL) return false
        host.applyEnabled(false)
        return true
    }
}
