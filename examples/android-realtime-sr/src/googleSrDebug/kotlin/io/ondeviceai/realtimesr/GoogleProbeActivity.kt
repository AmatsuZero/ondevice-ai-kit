package io.ondeviceai.realtimesr

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONObject

class GoogleProbeActivity : Activity() {
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var session: GoogleSurfaceSession? = null
    private var finished = false
    private var runId: String? = null
    private val watchdog = Runnable { complete("UNVERIFIED", "Probe timed out") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val id = intent.getStringExtra(EXTRA_RUN_ID)
        if (id == null || !RUN_ID_PATTERN.matches(id)) {
            finishWithError("Missing or invalid runId")
            return
        }
        runId = id
        mainHandler.postDelayed(watchdog, 15_000)
        try {
            val ownedSession = GoogleSurfaceSession(applicationContext)
            session = ownedSession
            ownedSession.probe(1920, 1080) { availability ->
                if (availability.state != "CHECKING") {
                    mainHandler.post {
                        if (!finished) complete(availability.state, availability.detail)
                    }
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Google SR probe failed", error)
            complete("UNVERIFIED", error.message ?: "Google SR probe failed")
        }
    }

    private fun complete(state: String, detail: String) {
        if (finished) return
        finished = true
        mainHandler.removeCallbacks(watchdog)
        closeSession()
        try {
            writeResult(runId ?: return, state, detail)
            setResult(RESULT_OK)
            finish()
        } catch (error: Exception) {
            Log.e(TAG, "Unable to write Google SR probe result", error)
            finishWithError(error.message ?: "Unable to write probe result")
        }
    }

    private fun closeSession() {
        val ownedSession = session ?: return
        session = null
        try {
            ownedSession.close()
        } catch (error: Exception) {
            Log.e(TAG, "Unable to close Google SR session", error)
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(watchdog)
        if (!finished) finished = true
        closeSession()
        super.onDestroy()
    }

    private fun writeResult(runId: String, state: String, detail: String) {
        val directory = File(filesDir, "sr-google")
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Unable to create probe result directory")
        }

        val destination = File(directory, "$runId.json")
        val lock = File(directory, "$runId.json.lock")
        if (!lock.createNewFile()) {
            throw IOException("A probe with this runId is already writing a result")
        }

        try {
            if (destination.exists()) {
                throw IOException("A probe result already exists for this runId")
            }

            val result = JSONObject()
                .put("runId", runId)
                .put("width", 1920)
                .put("height", 1080)
                .put("state", state)
                .put("detail", detail)
                .put("sdkInt", Build.VERSION.SDK_INT)

            val gmsVersion = try {
                packageManager.getPackageInfo("com.google.android.gms", 0).versionName
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
            if (gmsVersion != null) {
                result.put("gmsVersion", gmsVersion)
            }

            val temporary = File.createTempFile("$runId.", ".tmp", directory)
            try {
                FileOutputStream(temporary).bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(result.toString())
                }
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } finally {
                if (temporary.exists() && !temporary.delete()) {
                    Log.w(TAG, "Unable to remove temporary probe result file")
                }
            }
        } finally {
            if (!lock.delete()) {
                Log.w(TAG, "Unable to remove probe result lock")
            }
        }
    }

    private fun finishWithError(message: String) {
        setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_ERROR, message))
        finish()
    }

    private companion object {
        const val TAG = "GoogleProbeActivity"
        const val EXTRA_RUN_ID = "runId"
        const val EXTRA_ERROR = "error"
        val RUN_ID_PATTERN = Regex("[0-9a-f]{32}")
    }
}
