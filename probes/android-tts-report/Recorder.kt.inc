    private lateinit var reportFile: android.util.AtomicFile
    private lateinit var runId: String
    private var reportMode = "tts"
    private val phases = org.json.JSONArray()
    private var phase = "created"

    private fun configureReport(arguments: Bundle?) {
        runId = requireNotNull(arguments?.getString("runId"))
        require(runId.matches(Regex("[A-Za-z0-9-]{1,80}")))
        reportMode = arguments?.getString("reportMode") ?: "tts"
        require(reportMode in listOf("tts", "recorder-pass", "recorder-fail"))
        val directory = File(targetContext.filesDir, "odai-tts-reports")
        check(directory.isDirectory || directory.mkdir())
        val file = File(directory, "$runId.json")
        check(!file.exists() && !File(file.path + ".new").exists()) { "Run ID already exists" }
        check(BuildConfig.ODAI_SDK_COMMIT == "e0438c030a987ceeff9f9207c2475684749e4d8b")
        reportFile = android.util.AtomicFile(file)
    }

    private fun checkpoint(next: String) {
        phase = next
        phases.put(next)
        persist(false, Bundle())
    }

    private fun persist(terminal: Boolean, result: Bundle) {
        val fields = org.json.JSONObject()
        // These fields contain only fixed probe diagnostics, never user input.
        for (key in listOf("probeStatus", "probeError", "engine", "voiceCount", "languageStatus",
            "outputBytes", "sdkBackend", "sdkVoice", "sdkStatus", "sdkCancellationStatus", "sdkAfterCloseStatus")) {
            if (result.containsKey(key)) fields.put(key, result.get(key) ?: org.json.JSONObject.NULL)
        }
        val report = org.json.JSONObject().put("schemaVersion", 1).put("runId", runId)
            .put("sdkCommit", BuildConfig.ODAI_SDK_COMMIT).put("pid", android.os.Process.myPid())
            .put("mode", reportMode).put("phase", phase).put("phases", phases)
            .put("terminal", terminal).put("elapsedRealtimeMs", android.os.SystemClock.elapsedRealtime())
            .put("result", fields)
        val stream = reportFile.startWrite()
        try {
            stream.write((report.toString() + "\n").toByteArray(Charsets.UTF_8))
            reportFile.finishWrite(stream)
        } catch (error: Throwable) {
            reportFile.failWrite(stream)
            throw error
        }
    }

    override fun finish(resultCode: Int, results: Bundle?) {
        phase = "finished"
        phases.put(phase)
        // Commit/close the report before Binder/host result delivery can fail.
        persist(true, results ?: Bundle())
        super.finish(resultCode, results)
    }

    private fun recorderCheck(): Boolean {
        checkpoint("started")
        if (reportMode == "tts") return false
        val result = Bundle()
        try {
            check(reportMode != "recorder-fail") { "Intentional recorder assertion failure" }
            result.putString("probeStatus", "recorder-pass")
        } catch (error: Throwable) {
            result.putString("probeStatus", "fail")
            result.putString("probeError", error.toString())
        }
        finish(-1, result)
        return true
    }
