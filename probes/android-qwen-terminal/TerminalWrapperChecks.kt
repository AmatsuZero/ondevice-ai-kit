private fun terminalInstrumentation() = QwenTerminalInstrumentation(
    readProcessStat = { pid ->
        Runtime.terminalStatReads++
        val tail = (listOf("S") + List(18) { "0" } + "6789").joinToString(" ")
        when (Runtime.terminalStatMode) {
            "empty" -> byteArrayOf()
            "oversize" -> ByteArray(65537) { 32 }
            "pid" -> "9999 (other) $tail\n".toByteArray()
            "ticks" -> "$pid (own) ${tail.removeSuffix("6789")}9223372036854775808\n".toByteArray()
            "short" -> "$pid (own) S 0\n".toByteArray()
            "nonascii" -> byteArrayOf(-1)
            "nul" -> "$pid (own) $tail\u0000\n".toByteArray()
            "zero" -> "$pid (own) ${tail.removeSuffix("6789")}0\n".toByteArray()
            "leading" -> "$pid (own) ${tail.removeSuffix("6789")}06789\n".toByteArray()
            "state" -> "$pid (own) ${tail.replaceFirst("S", "SS")}\n".toByteArray()
            "delimiter" -> "$pid (own $tail\n".toByteArray()
            "failure" -> throw java.io.IOException("injected stat read failure")
            else -> "$pid (own ) worker) $tail\n".toByteArray()
        }
    },
    waitForTerminalAck = { ackFile, binding ->
        Runtime.terminalWaits++
        val bytes = report().readBytes()
        Runtime.terminalObservedBytes = bytes
        val j = JSONObject(String(bytes, Charsets.UTF_8))
        expect(j.getBoolean("terminal"), "wait before terminal persistence")
        expect(j.getString("processStartTicks") == "6789")
        expect(j.getString("probeVariant") == "cpu-arena-off-heap-terminal-ack-v1")
        expect(j.getString("terminalObservationProtocol") == "ODAI-TERMINAL-ACK-v1")
        expect(j.getInt("terminalAckTimeoutMillis") == 30000)
        expect(binding.pid == 1234 && binding.startTicks == "6789")
        expect(binding.runId == j.getString("runId"))
        expect(binding.sourceFingerprint == j.getString("sourceFingerprint"))
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        expect(binding.terminalReportSha256 == hash, "binding differs from exact persisted bytes")
        expect(ackFile.file == File(report().parentFile, "ack-${binding.runId}.txt"))
        expect(Runtime.code == null, "finish before ACK wait")
        if (j.getBoolean("constructorReturned")) {
            expect(Runtime.releases == 1 && j.getInt("recognizerReleaseAttempts") == 1)
        }
        if (j.getString("status") == "completed") {
            expect(j.getBoolean("recognizerReleaseSucceeded"))
            expect(j.getJSONObject("foreground").getBoolean("teardownAck"))
        }
        when (Runtime.terminalAckMode) {
            "timeout" -> throw java.util.concurrent.TimeoutException("injected ACK timeout")
            "invalid" -> throw java.io.IOException("injected invalid ACK")
        }
    }
)

// TERMINAL-CASES
    case("terminal wait follows exact final persistence and actual cleanup") {
        reset(); try {
            start()
            expect(Runtime.terminalStatReads == 1 && Runtime.terminalWaits == 1)
            expect(Runtime.code == -1)
            val b = Runtime.terminalBundle!!
            expect(b.getBoolean("terminalAckAttempted") && b.getBoolean("terminalAckReceived"))
            expect(b.getString("terminalReportSha256")?.length == 64)
            expect(b.getString("terminalAckErrorClass") == null)
            expect(Runtime.terminalObservedBytes!!.contentEquals(report().readBytes()))
        } finally { Runtime.root.deleteRecursively() }
    }
    case("truthful failed terminal is observed without recognition acceptance") {
        reset(); try {
            Runtime.decodeFail = true
            start(); failed()
            expect(Runtime.terminalWaits == 1 && Runtime.releases == 1)
            expect(Runtime.terminalBundle!!.getBoolean("terminalAckReceived"))
            expect(!json().isNull("errorClass"))
        } finally { Runtime.root.deleteRecursively() }
    }
    case("ACK failure preserves the original recognition error") {
        reset(); try {
            Runtime.decodeFail = true
            Runtime.terminalAckMode = "timeout"
            start(); failed()
            expect(Runtime.terminalWaits == 1 && Runtime.releases == 1)
            expect(Runtime.terminalObservedBytes!!.contentEquals(report().readBytes()))
            expect(json().getString("errorClass") != "java.util.concurrent.TimeoutException")
            val b = Runtime.terminalBundle!!
            expect(b.getString("terminalAckErrorClass") == "java.util.concurrent.TimeoutException")
            expect(b.getBoolean("terminalAckAttempted") && !b.getBoolean("terminalAckReceived"))
        } finally { Runtime.root.deleteRecursively() }
    }
    for (mode in listOf("timeout", "invalid")) case("ACK $mode cancels without terminal rewrite") {
        reset(); try {
            Runtime.terminalAckMode = mode
            start()
            expect(Runtime.code == 0 && json().getString("status") == "completed")
            expect(Runtime.terminalWaits == 1 && Runtime.creates == 1 && Runtime.releases == 1)
            expect(Runtime.terminalObservedBytes!!.contentEquals(report().readBytes()))
            val b = Runtime.terminalBundle!!
            expect(b.getBoolean("terminalAckAttempted") && !b.getBoolean("terminalAckReceived"))
            val wanted = if (mode == "timeout") "java.util.concurrent.TimeoutException" else "java.io.IOException"
            expect(b.getString("terminalAckErrorClass") == wanted)
            expect(b.getString("terminalAckErrorMessage") != null)
        } finally { Runtime.root.deleteRecursively() }
    }
    case("failed final persistence never enters ACK wait") {
        reset(); try {
            Runtime.failWhen = { it.getBoolean("terminal") }
            start()
            expect(Runtime.code == 0 && Runtime.terminalWaits == 0)
            expect(Runtime.releases == 1 && Runtime.streamReleases == 2)
            expect(!Runtime.terminalBundle!!.getBoolean("terminalAckAttempted"))
            expect(!Runtime.terminalBundle!!.getBoolean("terminalAckReceived"))
        } finally { Runtime.root.deleteRecursively() }
    }
    for (mode in listOf("empty", "oversize", "pid", "ticks", "short", "nonascii", "nul", "zero", "leading", "state", "delimiter", "failure")) case("bad own stat $mode blocks report and native") {
        reset(); try {
            Runtime.terminalStatMode = mode
            start()
            expect(Runtime.code == 0 && Runtime.writes == 0)
            expect(Runtime.creates == 0 && Runtime.streams == 0 && Runtime.releases == 0)
            expect(Runtime.terminalWaits == 0 && Runtime.terminalStatReads == 1)
            expect(!report().exists())
        } finally { Runtime.root.deleteRecursively() }
    }
    for (suffix in listOf("", ".stage")) for (kind in listOf("file", "directory", "symlink")) case("stale ACK $suffix $kind blocks report and native") {
        reset(); try {
            report().parentFile.mkdirs()
            val entry = File(report().parentFile, "ack-check01.txt$suffix")
            when (kind) {
                "file" -> entry.writeText("KEEP")
                "directory" -> entry.mkdir()
                else -> Files.createSymbolicLink(entry.toPath(), File(Runtime.root, "absent").toPath())
            }
            start()
            expect(Runtime.code == 0 && Runtime.writes == 0 && Runtime.creates == 0)
            expect(Runtime.terminalWaits == 0 && !report().exists())
            when (kind) {
                "file" -> expect(entry.readText() == "KEEP")
                "directory" -> expect(entry.isDirectory)
                else -> expect(Files.isSymbolicLink(entry.toPath()))
            }
        } finally { Runtime.root.deleteRecursively() }
    }
    case("Android instrumentation has a real JVM no-argument constructor") {
        expect(QwenTerminalInstrumentation::class.java.getDeclaredConstructor().parameterCount == 0)
    }
