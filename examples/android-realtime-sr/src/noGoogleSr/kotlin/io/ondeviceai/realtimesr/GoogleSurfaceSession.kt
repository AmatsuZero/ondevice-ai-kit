package io.ondeviceai.realtimesr

import android.content.Context
import android.os.Handler
import android.os.Looper

class GoogleSurfaceSession(context: Context) {
    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var generation = 0L
    private var closed = false

    @Suppress("UNUSED_PARAMETER")
    fun probe(width: Int, height: Int, notify: (GoogleSrAvailability) -> Unit) {
        val token: Long
        synchronized(lock) {
            if (closed) {
                mainHandler.post {
                    notify(GoogleSrAvailability("UNVERIFIED", "The session is closed."))
                }
                return
            }
            generation += 1
            token = generation
        }
        mainHandler.post {
            val isCurrent = synchronized(lock) { !closed && generation == token }
            if (isCurrent) {
                notify(GoogleSrAvailability("MISSING_SERVICES", "Google SR support is not included in this build."))
            }
        }
    }

    fun close() {
        synchronized(lock) {
            closed = true
            generation += 1
        }
    }
}
