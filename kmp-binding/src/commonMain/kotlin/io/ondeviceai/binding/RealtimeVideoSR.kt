package io.ondeviceai.binding

/** Controls one native player view. All calls are on the host UI thread; no frames cross KMP. */
enum class RealtimeSRBackend { ORIGINAL, SYSTEM, CUNNY, SPATIAL }
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
class RealtimeVideoSR(private val host: RealtimeSRHost) {
    fun setEnabled(enabled: Boolean) = host.applyEnabled(enabled)
    fun status(): RealtimeSRStatus = host.readStatus()
}
