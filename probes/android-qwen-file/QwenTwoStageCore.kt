package io.ondeviceai.acceptance.qwenfile

private class MutationException : RuntimeException("input-mutation-detected")

interface CaseStream {
    fun setLanguage(language: String)
    fun accept(samples: FloatArray, sampleRate: Int)
    fun decode()
    fun text(): String
    fun release()
}

data class StageState(
    val language: String,
    val status: String = "running",
    val createAttempts: Int = 0, val createReturned: Boolean = false,
    val optionAttempts: Int = 0, val acceptAttempts: Int = 0,
    val decodeAttempts: Int = 0, val resultAttempts: Int = 0,
    val releaseAttempts: Int = 0, val releaseSucceeded: Boolean = false,
    val rawText: String? = null, val decodeNanos: Long? = null,
    val errorClass: String? = null, val releaseErrorClass: String? = null)

data class CaseState(
    val status: String = "running", val phase: String = "initial",
    val admitted: Boolean? = null, val rawText: String? = null,
    val stages: List<StageState> = emptyList(),
    val errorClass: String? = null, val errorMessage: String? = null,
    val persistErrorClass: String? = null)

class TwoStageCaseRunner(
    private val factory: () -> CaseStream,
    private val gate: () -> Boolean,
    private val persist: (CaseState) -> Unit,
    private val now: () -> Long = System::nanoTime
) {
    fun execute(locale: String, samples: FloatArray): CaseState {
        // ---- input validation (before any native operation) ----
        val validLocale = locale == "en-US" || locale == "zh-CN"
        val validPcm = samples.size in 1..480000 &&
                samples.all { it.isFinite() && it >= -1f && it <= 1f }
        if (!validLocale || !validPcm) {
            val msg = if (!validLocale) "unsupported locale: $locale" else "invalid pcm"
            return CaseState(status = "error", phase = "initial",
                errorClass = "java.lang.IllegalArgumentException", errorMessage = msg)
        }

        // capture original bits (never mutate the caller's array)
        val originalBits = IntArray(samples.size) { samples[it].toBits() }

        // ---- clock liveness guard before first native dispatch ----
        try {
            now()
        } catch (e: Throwable) {
            return CaseState(status = "error", phase = "initial",
                errorClass = e::class.qualifiedName ?: e.javaClass.name, errorMessage = e.message)
        }

        val stages = mutableListOf<StageState>()
        val streams = mutableListOf<CaseStream>()
        var persistErrorClass: String? = null
        var primaryError: Throwable? = null
        var primaryClass: String? = null
        var primaryMessage: String? = null
        val forcedLanguage = if (locale == "zh-CN") "Chinese" else "English"
        var admitted: Boolean? = null
        var caseRawText: String? = null

        fun classOf(e: Throwable): String = e::class.qualifiedName ?: e.javaClass.name

        fun notePrimary(e: Throwable) {
            if (primaryError == null) {
                primaryError = e
                primaryClass = classOf(e)
                primaryMessage = e.message
            }
        }

        fun snapshot(status: String): CaseState {
            val phase = when {
                stages.isEmpty() -> "initial"
                stages.size >= 2 -> "forced"
                else -> "auto"
            }
            return CaseState(status = status, phase = phase, admitted = admitted,
                stages = stages.toList(), errorClass = primaryClass,
                errorMessage = primaryMessage, persistErrorClass = persistErrorClass)
        }

        fun doPersist(state: CaseState) {
            try {
                persist(state)
            } catch (e: Throwable) {
                if (persistErrorClass == null) persistErrorClass = classOf(e)
                notePrimary(e)
                throw e
            }
        }

        fun gateOrThrow() {
            val ok = try {
                gate()
            } catch (e: Throwable) {
                notePrimary(e)
                throw e
            }
            if (!ok) {
                val ex = RuntimeException("gate-closed")
                notePrimary(ex)
                throw ex
            }
        }

        fun checkMutation() {
            for (i in samples.indices) {
                if (samples[i].toBits() != originalBits[i]) throw MutationException()
            }
        }

        // Release acquired stream i exactly once. Records releaseSucceeded only after
        // release returns, and on a release fault marks the stage error without
        // overwriting a previously recorded primary errorClass.
        fun releaseOne(i: Int) {
            if (i >= streams.size || i >= stages.size) return
            val st = stages[i]
            if (st.releaseAttempts > 0) return
            stages[i] = st.copy(releaseAttempts = st.releaseAttempts + 1)
            try {
                streams[i].release()
                stages[i] = stages[i].copy(releaseSucceeded = true)
            } catch (e: Throwable) {
                val cur = stages[i]
                stages[i] = cur.copy(
                    status = "error",
                    errorClass = cur.errorClass ?: classOf(e),
                    releaseErrorClass = classOf(e)
                )
                notePrimary(e)
            }
        }

        fun releaseAll() {
            for (i in streams.indices) releaseOne(i)
        }

        // Run one full stage. Counters increment immediately before the matching
        // dispatch. Any fault records status=error + errorClass on the CURRENT stage
        // (copy from the live stage, never a stale receiver) before rethrowing, so the
        // acquired stream is still cleaned up by the caller. Stage is NOT marked
        // completed here; completion requires release + after-stage gate (see caller).
        fun runOneStage(idx: Int, language: String) {
            try {
                // create
                doPersist(snapshot("running"))
                gateOrThrow()
                stages[idx] = stages[idx].copy(createAttempts = stages[idx].createAttempts + 1)
                val stream = factory()
                stages[idx] = stages[idx].copy(createReturned = true)
                streams.add(stream)
                // option (setLanguage)
                doPersist(snapshot("running"))
                gateOrThrow()
                stages[idx] = stages[idx].copy(optionAttempts = stages[idx].optionAttempts + 1)
                stream.setLanguage(language)
                // accept
                doPersist(snapshot("running"))
                gateOrThrow()
                stages[idx] = stages[idx].copy(acceptAttempts = stages[idx].acceptAttempts + 1)
                stream.accept(samples, 16000)
                checkMutation()
                // decode
                doPersist(snapshot("running"))
                gateOrThrow()
                val t0 = now()
                stages[idx] = stages[idx].copy(decodeAttempts = stages[idx].decodeAttempts + 1)
                stream.decode()
                val t1 = now()
                val nanos = t1 - t0
                stages[idx] = stages[idx].copy(decodeNanos = nanos)
                checkMutation()
                // text (result)
                doPersist(snapshot("running"))
                gateOrThrow()
                stages[idx] = stages[idx].copy(resultAttempts = stages[idx].resultAttempts + 1)
                val txt = stream.text()
                stages[idx] = stages[idx].copy(rawText = txt)
            } catch (e: Throwable) {
                stages[idx] = stages[idx].copy(status = "error", errorClass = classOf(e))
                throw e
            }
        }

        // ---- initial persist before native ----
        try {
            doPersist(snapshot("running"))
        } catch (e: Throwable) {
            return CaseState(status = "error", phase = "initial",
                errorClass = primaryClass, errorMessage = primaryMessage,
                persistErrorClass = persistErrorClass)
        }

        try {
            stages.add(StageState(language = ""))
            runOneStage(0, "")
            val autoText = stages[0].rawText ?: ""
            if (autoText == "") {
                admitted = false
                caseRawText = ""
            } else {
                admitted = true
                caseRawText = stages[0].rawText
            }
            // Release the auto stream, then the after-stage foreground gate, BEFORE
            // any next-stage creation (empty-auto also performs this final gate).
            // A release or post-stage gate failure must NOT mark the stage completed
            // and must NOT start the forced stage: a successful completion requires
            // actual releaseSucceeded AND a successful gate AND zero prior primary error.
            releaseOne(0)
            if (primaryError != null || stages[0].status == "error") {
                // auto release failure already recorded: keep the error, skip gate,
                // skip completed, skip the forced stage entirely.
            } else {
                try {
                    gateOrThrow()
                } catch (e: Throwable) {
                    // Post-stage gate loss: mark the CURRENT stage error (preserve any
                    // prior errorClass) before outer handling; add no next-stage state.
                    stages[0] = stages[0].copy(
                        status = "error",
                        errorClass = stages[0].errorClass ?: classOf(e))
                    throw e
                }
                stages[0] = stages[0].copy(status = "completed")

                if (admitted) {
                    stages.add(StageState(language = forcedLanguage))
                    runOneStage(1, forcedLanguage)
                    caseRawText = stages[1].rawText
                    releaseOne(1)
                    if (primaryError != null || stages[1].status == "error") {
                        // forced release failure already recorded: keep the error.
                    } else {
                        try {
                            gateOrThrow()
                        } catch (e: Throwable) {
                            stages[1] = stages[1].copy(
                                status = "error",
                                errorClass = stages[1].errorClass ?: classOf(e))
                            throw e
                        }
                        stages[1] = stages[1].copy(status = "completed")
                    }
                }
            }
        } catch (e: Throwable) {
            notePrimary(e)
        } finally {
            releaseAll()
        }

        // ---- finalize ----
        val failed = primaryError != null || persistErrorClass != null
        val status = if (failed) "error" else "completed"
        val built = CaseState(
            status = status,
            phase = when {
                admitted == true -> "forced"
                admitted == false -> "auto"
                else -> "initial"
            },
            admitted = admitted,
            rawText = if (failed) null else caseRawText,
            stages = stages.toList(),
            errorClass = primaryClass,
            errorMessage = primaryMessage,
            persistErrorClass = persistErrorClass
        )
        return try {
            persist(built)
            built
        } catch (e: Throwable) {
            val pec = persistErrorClass ?: classOf(e)
            val pc = primaryClass ?: classOf(e)
            val pm = primaryMessage ?: e.message
            CaseState(status = "error", phase = built.phase, admitted = admitted,
                rawText = null, stages = stages.toList(),
                errorClass = pc, errorMessage = pm, persistErrorClass = pec)
        }
    }
}
