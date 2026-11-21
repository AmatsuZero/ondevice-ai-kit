package io.ondeviceai.realtimesr

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.media.effect.enhancement.Enhancement
import com.google.android.gms.tasks.Task
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

class GoogleSurfaceSession(context: Context) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var generation = 0L
    private var pending: Future<*>? = null
    private var closed = false

    @Suppress("UNUSED_PARAMETER")
    fun probe(width: Int, height: Int, notify: (GoogleSrAvailability) -> Unit) {
        synchronized(lock) {
            if (closed) {
                mainHandler.post {
                    notify(GoogleSrAvailability("UNVERIFIED", "The session is closed."))
                }
                return
            }
            generation += 1
            val token = generation
            pending?.cancel(true)
            pending = worker.submit { inspect(token, notify) }
        }
    }

    private fun inspect(token: Long, notify: (GoogleSrAvailability) -> Unit) {
        if (!isCurrent(token)) return
        try {
            val servicesStatus = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(appContext)
            if (servicesStatus != ConnectionResult.SUCCESS) {
                deliver(token, GoogleSrAvailability("MISSING_SERVICES", "Google Play services is unavailable."), notify)
                return
            }

            val client = Enhancement.getClient(appContext)
            client.isDeviceSupported().addOnCompleteListener(worker) device@{ supportTask ->
                if (!isCurrent(token)) return@device
                if (!supportTask.isSuccessful) {
                    deliver(token, GoogleSrAvailability("UNVERIFIED", "Could not determine device support."), notify)
                    return@device
                }
                if (supportTask.result != true) {
                    deliver(token, GoogleSrAvailabilityDecision.decide(true, false, null), notify)
                    return@device
                }

                try {
                    client.isModuleInstalled().addOnCompleteListener(worker) module@{ moduleTask ->
                        if (!isCurrent(token)) return@module
                        if (!moduleTask.isSuccessful) {
                            deliver(token, GoogleSrAvailability("UNVERIFIED", "Could not determine module installation state."), notify)
                        } else {
                            deliver(
                                token,
                                GoogleSrAvailabilityDecision.decide(true, true, moduleTask.result == true),
                                notify
                            )
                        }
                    }
                } catch (error: Exception) {
                    deliver(token, GoogleSrAvailability("UNVERIFIED", "Could not check module installation: ${error.message ?: error.javaClass.simpleName}."), notify)
                }
            }
        } catch (error: Exception) {
            deliver(token, GoogleSrAvailability("UNVERIFIED", "Could not check Google SR availability: ${error.message ?: error.javaClass.simpleName}."), notify)
        }
    }

    private fun deliver(token: Long, result: GoogleSrAvailability, notify: (GoogleSrAvailability) -> Unit) {
        mainHandler.post {
            if (isCurrent(token)) notify(result)
        }
    }

    private fun isCurrent(token: Long): Boolean = synchronized(lock) {
        !closed && generation == token
    }

    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            generation += 1
            pending?.cancel(true)
            pending = null
            worker.shutdownNow()
        }
    }
}
