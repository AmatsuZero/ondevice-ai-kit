    private lateinit var reportFile: android.util.AtomicFile
    private lateinit var runId: String
    private var reportMode = "inference"

    private fun configureReport(arguments: Bundle?) {
        runId = requireNotNull(arguments?.getString("runId"))
        require(runId.matches(Regex("[A-Za-z0-9-]{1,80}")))
        reportMode = arguments?.getString("reportMode") ?: "inference"
        require(reportMode in listOf("inference", "recorder-pass", "recorder-fail"))
        check(BuildConfig.ODAI_SDK_COMMIT == "e0438c030a987ceeff9f9207c2475684749e4d8b")
        val directory = java.io.File(targetContext.filesDir, "odai-candidate-reports")
        check(directory.isDirectory || directory.mkdir())
        val file = java.io.File(directory, "$runId.json")
        check(!file.exists() && !java.io.File(file.path + ".new").exists() &&
            !java.io.File(file.path + ".bak").exists()) { "Run ID already exists" }
        check(file.createNewFile()) { "Run ID already reserved" }
        reportFile = android.util.AtomicFile(file)
        persist(false, Bundle())
    }

    private fun persist(terminal: Boolean, result: Bundle) {
        val fields = org.json.JSONObject()
        // Only fixed diagnostic primitives; never media, transcriptions or stacks.
        for (key in listOf(@RESULT_KEYS@)) {
            if (result.containsKey(key)) {
                val value = result.get(key)
                if (value is String || value is Boolean || value is Int || value is Long) {
                    fields.put(key, value)
                }
            }
        }
        val passed = (reportMode == "inference" && result.getString("status") == "passed") ||
            (reportMode == "recorder-pass" && result.getString("status") == "recorder-pass")
        val status = if (!terminal) "running" else if (passed) "completed" else "failed"
        val report = org.json.JSONObject().put("schemaVersion", 1).put("runId", runId)
            .put("sdkCommit", BuildConfig.ODAI_SDK_COMMIT).put("kind", "@KIND@")
            .put("mode", reportMode).put("pid", android.os.Process.myPid())
            .put("terminal", terminal).put("status", status).put("result", fields)
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
        // Persist before Binder delivery; a host disconnect cannot imply success.
        persist(true, results ?: Bundle())
        super.finish(resultCode, results)
    }

    private fun recorderCheck(): Boolean {
        if (reportMode == "inference") return false
        val result = Bundle()
        try {
            check(reportMode != "recorder-fail") { "Intentional recorder failure" }
            result.putString("status", "recorder-pass")
        } catch (error: Throwable) {
            result.putString("status", "failed")
        }
        finish(if (reportMode == "recorder-pass") 0 else 1, result)
        return true
    }
