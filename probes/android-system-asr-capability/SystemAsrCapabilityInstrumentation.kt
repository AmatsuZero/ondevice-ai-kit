package io.ondeviceai.systemasr

import android.Manifest
import android.app.Instrumentation
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.speech.SpeechRecognizer
import android.util.AtomicFile
import io.ondeviceai.core.CapabilityStatus
import io.ondeviceai.speech.SpeechBackendUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * W3 capability + installed-language METADATA probe (operator-only, query-only).
 * Reuses the current production backend/resources; never recognizes, captures audio,
 * grants permissions, downloads, or creates any extra recognizer outside the production
 * resource checks. The production check() creates and destroys its own query recognizer
 * (see AndroidAsrResources); a completed query is capability metadata only, not
 * recognition / offline / language-quality acceptance.
 */
class SystemAsrCapabilityInstrumentation : Instrumentation() {

    private lateinit var report: JSONObject
    private var reportFile: AtomicFile? = null
    private var preflightError: String? = null
    private var reportSetupError: String? = null
    private var runIdValue: String? = null

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        val runId = arguments?.getString("runId")
        val fingerprint = arguments?.getString("sourceFingerprint")
        val runIdOk = runId != null && Regex("[A-Za-z0-9_-]{1,80}").matches(runId)
        val fingerprintOk = fingerprint != null && Regex("[0-9a-f]{64}").matches(fingerprint)
        runIdValue = if (runIdOk) runId else null
        preflightError = when {
            !runIdOk -> "INVALID_RUN_ID"
            !fingerprintOk -> "INVALID_SOURCE_FINGERPRINT"
            else -> null
        }

        report = JSONObject()
            .put("schemaVersion", 1)
            .put("kind", "w3-system-asr-capability-probe")
            .put("runId", runIdValue ?: "INVALID")
            .put("sourceFingerprint", if (fingerprintOk) fingerprint else "INVALID")
            .put("sourceFingerprintValid", fingerprintOk)
            .put("sourceFingerprintNote",
                "caller-supplied source digest; does not self-prove installed APK bytes (see integration.md)")
            .put("packageName", targetContext.packageName)
            .put("pid", Process.myPid())
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("startedElapsedNs", SystemClock.elapsedRealtimeNanos())
            .put("status", "running")
            .put("terminal", false)
            .put("microphoneStarts", 0)
            .put("recognizeCalls", 0)
            .put("downloadsRequested", 0)
            .put("audioCaptured", false)
            .put("transcriptPersisted", false)
            .put("permissionMutated", false)
            .put("networkRecognizerUsed", false)
            .put("extraRecognizersOutsideProductionChecksCreated", false)
            .put("resourceChecksCanCreateRecognizer", true)
            .put("notes", "capability-metadata-only; no recognition, audio, transcript, download or permission changes")

        // Reserve an exclusive new report whenever runId is safe (even for a refused run, so the
        // refusal is durable). A malformed runId is never used as a file name.
        if (runIdOk) {
            runCatching { reserveReport(runId!!) }.onFailure { reportSetupError = it.javaClass.name }
        }
        start() // MUST be called in onCreate (validated runner pattern).
    }

    private fun reserveReport(runId: String) {
        val dir = File(targetContext.noBackupFilesDir, REPORT_SUBDIR)
        if (!dir.isDirectory && !dir.mkdirs()) error("report-dir-unavailable")
        val file = File(dir, "$runId.json")
        if (file.exists() || File(file.path + ".new").exists() || File(file.path + ".bak").exists())
            error("run-already-reserved")
        if (!file.createNewFile()) error("run-already-reserved")
        reportFile = AtomicFile(file)
        persist() // initial running report is durable BEFORE any query
    }

    override fun onStart() {
        var backend: AndroidSpeechRecognizerBackend? = null
        var status = "failed"
        var outcomeCode = "HARNESS_FAILED"
        var queriesRan = false
        var backendAvailable = false
        var resourceQuerySupported = false
        var errorsPresent = false
        var terminalPersisted = false
        var resultCode = 1
        val results = Bundle()

        try {
            when {
                preflightError != null -> {
                    // Refuse malformed inputs WITHOUT constructing/invoking the backend.
                    status = "refused"
                    outcomeCode = "REFUSED_INVALID_INPUT"
                    report.put("refusalReason", preflightError)
                }
                reportFile == null -> {
                    status = "failed"
                    outcomeCode = "REPORT_SETUP_FAILED"
                    report.put("reportSetupErrorType", reportSetupError ?: "unknown")
                }
                else -> {
                    val resources = AndroidAsrResources(targetContext)
                    val activeBackend = AndroidSpeechRecognizerBackend(targetContext)
                    backend = activeBackend
                    val permState = targetContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    report.put("recordAudio", JSONObject()
                        .put("state", permState)
                        .put("granted", permState == PackageManager.PERMISSION_GRANTED)
                        .put("gapNote",
                            "denied RECORD_AUDIO is a recorded prerequisite gap, not proof the recognizer is absent"))

                    runBlocking {
                        // (1) Production backend availability (query-only; dispatched to Main internally).
                        try {
                            val availability = activeBackend.availability()
                            backendAvailable = availability.status == CapabilityStatus.AVAILABLE
                            report.put("backendAvailability", JSONObject()
                                .put("queried", true)
                                .put("stage", "availability")
                                .put("backendId", availability.backendId)
                                .put("status", availability.status.toString())
                                .put("reason", availability.reason?.toString() ?: "NONE")
                                .put("minOS", availability.minOS)
                                .put("offline", availability.offline)
                                .put("elapsedNs", SystemClock.elapsedRealtimeNanos()))
                        } catch (error: Throwable) {
                            errorsPresent = true
                            report.put("backendAvailability", JSONObject()
                                .put("queried", true)
                                .put("stage", "availability")
                                .put("status", "error")
                                .put("errorType", error.javaClass.name))
                        }

                        // (2) Resource-stage precheck. The API33 query needs the on-device service,
                        // but NOT RECORD_AUDIO (production check() never checks that permission), so
                        // resource checks stay eligible even when permission/availability is denied.
                        val atLeast33 = Build.VERSION.SDK_INT >= 33
                        val serviceAvailable = if (atLeast33) {
                            withContext(Dispatchers.Main.immediate) {
                                SpeechRecognizer.isOnDeviceRecognitionAvailable(targetContext)
                            }
                        } else false
                        val typedUnavailable: String? = when {
                            !atLeast33 -> "REQUIRES_OS_VERSION"
                            !serviceAvailable -> "UNSUPPORTED_DEVICE"
                            else -> null
                        }
                        resourceQuerySupported = typedUnavailable == null
                        report.put("resourceStage", JSONObject()
                            .put("canObserveDownload", resources.canObserveDownload)
                            .put("canObserveDownloadIsApiLevelFlagOnly", true)
                            .put("queryMinSdk", 33)
                            .put("sdkAtLeast33", atLeast33)
                            .put("onDeviceServiceQueried", atLeast33)
                            .put("onDeviceServiceAvailable", serviceAvailable)
                            .put("querySupported", resourceQuerySupported)
                            .put("unavailableTypedCode", typedUnavailable ?: "NONE")
                            .put("elapsedNs", SystemClock.elapsedRealtimeNanos()))

                        // (3) Serial locale queries; bounded per-locale errors continue to next locale.
                        val locales = JSONArray()
                        var checkpointFailures = 0
                        for (locale in LOCALES) {
                            val row = JSONObject()
                                .put("locale", locale)
                                .put("stage", "resource-check")
                                .put("elapsedNs", SystemClock.elapsedRealtimeNanos())
                            if (typedUnavailable != null) {
                                // Honest supported-unavailable: no fabricated INSTALLED, no service call.
                                row.put("status", "supported-unavailable")
                                    .put("queried", false)
                                    .put("typedCode", typedUnavailable)
                                    .put("reason", "production check() skipped: query requires API33 on-device service")
                            } else {
                                try {
                                    val asset = resources.check(locale) // query-only; owns/destroys its recognizer
                                    row.put("status", "observed")
                                        .put("queried", true)
                                        .put("assetStatus", asset.name)
                                } catch (error: AsrResourceException) {
                                    errorsPresent = true
                                    row.put("status", "error").put("queried", true)
                                        .put("errorType", "AsrResourceException")
                                        .put("errorCode", error.code)
                                } catch (error: AsrResourceTimeoutException) {
                                    errorsPresent = true
                                    row.put("status", "error").put("queried", true)
                                        .put("errorType", "AsrResourceTimeoutException")
                                } catch (error: SpeechBackendUnavailableException) {
                                    // On API33+ the production check() factory's only unavailable reason
                                    // is UNSUPPORTED_DEVICE; an anomalous runtime error, never a success.
                                    errorsPresent = true
                                    row.put("status", "supported-unavailable").put("queried", true)
                                        .put("errorType", "SpeechBackendUnavailableException")
                                        .put("typedCode", "UNSUPPORTED_DEVICE")
                                } catch (error: Throwable) {
                                    errorsPresent = true
                                    row.put("status", "error").put("queried", true)
                                        .put("errorType", error.javaClass.name)
                                }
                            }
                            locales.put(row)
                            report.put("locales", locales)
                            if (runCatching { persist() }.isFailure) checkpointFailures++
                        }
                        report.put("checkpointPersistFailures", checkpointFailures)
                        // A failed checkpoint persist is an error; keep outcome/result non-success.
                        if (checkpointFailures > 0) errorsPresent = true
                    }
                    queriesRan = true
                    status = "completed"
                    outcomeCode = if (errorsPresent) "METADATA_QUERY_EXECUTED_WITH_ERRORS"
                    else "METADATA_QUERY_EXECUTED"
                }
            }
        } catch (error: Throwable) {
            status = "failed"
            outcomeCode = "HARNESS_FAILED"
            report.put("harnessErrorType", error.javaClass.name)
        } finally {
            // NonCancellable + Main (close() is Main.immediate + NonCancellable), even on metadata failure.
            try {
                runBlocking(NonCancellable) { backend?.close() }
            } catch (error: Throwable) {
                status = "failed"
                outcomeCode = "HARNESS_FAILED"
                report.put("cleanupErrorType", error.javaClass.name)
            }
            val finished = SystemClock.elapsedRealtimeNanos()
            report.put("terminal", true).put("finishedElapsedNs", finished)
            runCatching { report.put("durationNs", finished - report.getLong("startedElapsedNs")) }
            // Write the definitive terminal result BEFORE the durable terminal persist.
            report.put("status", status)
                .put("outcome", outcome(outcomeCode, queriesRan, backendAvailable,
                    resourceQuerySupported, errorsPresent))
            if (reportFile != null) {
                try {
                    persist()
                    terminalPersisted = true
                } catch (error: Throwable) {
                    status = "failed"
                    outcomeCode = "HARNESS_FAILED"
                    report.put("terminalPersistErrorType", error.javaClass.name)
                        .put("status", status)
                        .put("outcome", outcome(outcomeCode, queriesRan, backendAvailable,
                            resourceQuerySupported, errorsPresent))
                }
            }
            // Any error stays an error: only a clean, fully persisted completed run gets RESULT_OK.
            resultCode = if (status == "completed" && !errorsPresent && terminalPersisted) -1 else 1
            results.putString("status", status)
            results.putString("outcome", outcomeCode)
            results.putString("reason", outcomeCode)
            results.putString("runId", runIdValue ?: "INVALID")
            finish(resultCode, results)
        }
    }

    private fun outcome(code: String, executed: Boolean, backendAvailable: Boolean,
                        resourceQuerySupported: Boolean, errorsPresent: Boolean) =
        JSONObject()
            .put("code", code)
            .put("probeExecuted", executed)
            .put("metadataQueryCompleted", executed)
            .put("backendAvailable", backendAvailable)
            .put("resourceQuerySupported", resourceQuerySupported)
            .put("errorsPresent", errorsPresent)
            .put("recognitionAttempted", false)
            .put("audioCaptured", false)
            .put("transcriptPersisted", false)
            .put("offlineQualityVerified", false)
            .put("wholeW3Approved", false)
            .put("interpretation",
                "capability-metadata-only: a completed query is not recognition, offline, or language-quality PASS and is not a whole-W3 approval")

    private fun persist() {
        val rf = reportFile ?: return
        val stream = rf.startWrite()
        try {
            stream.write((report.toString() + "\n").toByteArray(Charsets.UTF_8))
            rf.finishWrite(stream)
        } catch (error: Throwable) {
            rf.failWrite(stream)
            throw error
        }
    }

    private companion object {
        const val REPORT_SUBDIR = "w3-system-asr-capability-probe"
        val LOCALES = arrayOf("en-US", "zh-CN")
    }
}
