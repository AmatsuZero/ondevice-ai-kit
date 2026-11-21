package io.ondeviceai.realtimesr

data class GoogleSrAvailability(val state: String, val detail: String)

internal object GoogleSrAvailabilityDecision {
    fun decide(
        googlePlayServicesAvailable: Boolean,
        deviceSupported: Boolean?,
        moduleInstalled: Boolean?
    ): GoogleSrAvailability = when {
        !googlePlayServicesAvailable -> GoogleSrAvailability("MISSING_SERVICES", "Google Play services is unavailable.")
        deviceSupported == null -> GoogleSrAvailability("UNVERIFIED", "Could not determine device support.")
        !deviceSupported -> GoogleSrAvailability("UNSUPPORTED", "This device is not supported.")
        moduleInstalled == null -> GoogleSrAvailability("UNVERIFIED", "Could not determine module installation state.")
        !moduleInstalled -> GoogleSrAvailability("MODULE_NOT_READY", "The enhancement module is not installed.")
        else -> GoogleSrAvailability(
            "UNVERIFIED",
            "Device support and module installation are confirmed; per-effect video output is unverified."
        )
    }
}
