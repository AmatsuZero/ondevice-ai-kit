package io.ondeviceai.core
enum class CapabilityStatus { AVAILABLE, UNAVAILABLE, REQUIRES_ASSETS }
enum class UnavailableReason { REQUIRES_OS_VERSION, BACKEND_NOT_LINKED, PERMISSION_DENIED, UNSUPPORTED_DEVICE, MISSING_ASSETS, CLOSED }
data class CapabilityAvailability(val status: CapabilityStatus, val reason: UnavailableReason? = null, val backendId: String, val minOS: String? = null, val offline: Boolean = true)
enum class TaskTerminal { COMPLETED, FAILED, CANCELLED }
class BusyException : IllegalStateException("busy")
class ClosedException : IllegalStateException("closed")
