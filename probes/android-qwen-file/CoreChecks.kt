package io.ondeviceai.acceptance.qwenfile

private var assertions = 0
private val failures = mutableListOf<String>()
private fun expect(ok: Boolean, msg: String = "assertion") { check(ok) { msg }; assertions++ }
private fun case(name: String, body: () -> Unit) {
    try { body(); println("PASS $name") } catch (e: Throwable) { failures += "$name: $e" }
}
private class Injected(val tag: String) : RuntimeException(tag)

private class Rig(
    val texts: List<String> = listOf("admission", "final"),
    val failAt: String? = null,
    val mutateAt: String? = null
) {
    val events = mutableListOf<String>()
    val snapshots = mutableListOf<CaseState>()
    val samples = floatArrayOf(-1f, -0.5f, 0f, 0.25f, 32767f / 32768f)
    var creates = 0
    var persistCalls = 0
    var persistFailure: ((CaseState) -> Boolean)? = null
    var gate: () -> Boolean = { true }
    var clock: () -> Long = System::nanoTime
    fun event(s: String) { events += s; if (s == failAt) throw Injected(s) }
    fun run(locale: String = "en-US", pcm: FloatArray = samples): CaseState = TwoStageCaseRunner(
        factory = {
            val n = ++creates
            event("$n.create")
            object : CaseStream {
                override fun setLanguage(language: String) { event("$n.option:$language") }
                override fun accept(samples: FloatArray, sampleRate: Int) {
                    expect(samples === pcm, "must pass exact same full PCM object")
                    expect(sampleRate == 16000 && samples.size == pcm.size, "whole 16kHz PCM")
                    event("$n.accept")
                    if (mutateAt == "$n.accept") samples[0] = 0.75f
                }
                override fun decode() {
                    event("$n.decode")
                    if (mutateAt == "$n.decode") pcm[0] = 0.75f
                }
                override fun text(): String { event("$n.result"); return texts[n - 1] }
                override fun release() { event("$n.release") }
            }
        },
        gate = { gate() },
        persist = { st ->
            persistCalls++
            if (persistFailure?.invoke(st) == true) throw Injected("persist")
            snapshots += st
        }, now = { clock() }
    ).execute(locale, pcm)
}

fun main() {
    case("nonempty auto then fresh English stream; release before success") {
        val f = Rig(); val r = f.run()
        expect(r.status == "completed" && r.admitted == true && r.rawText == "final", r.toString())
        expect(f.events == listOf("1.create", "1.option:", "1.accept", "1.decode", "1.result", "1.release",
            "2.create", "2.option:English", "2.accept", "2.decode", "2.result", "2.release"), f.events.toString())
        expect(r.stages.size == 2 && r.stages.map { it.language } == listOf("", "English"))
        for (s in r.stages) {
            expect(s.createAttempts == 1 && s.createReturned && s.optionAttempts == 1 && s.acceptAttempts == 1)
            expect(s.decodeAttempts == 1 && s.resultAttempts == 1 && s.releaseAttempts == 1 && s.releaseSucceeded)
            expect(s.status == "completed" && s.decodeNanos != null && s.decodeNanos!! >= 0)
            expect(s.errorClass == null && s.releaseErrorClass == null)
        }
        expect(f.snapshots.first().stages.sumOf { it.createAttempts } == 0)
        expect(f.snapshots.last() == r && f.snapshots.last().stages.all { it.releaseSucceeded })
    }
    case("Chinese locale and unprocessed output") {
        val f = Rig(listOf("语音", "  原文。\n")); val r = f.run("zh-CN")
        expect(r.rawText == "  原文。\n" && f.events.contains("2.option:Chinese"))
    }
    case("strict empty auto stops after exactly one decode") {
        val f = Rig(listOf("")); val r = f.run()
        expect(r.status == "completed" && r.admitted == false && r.rawText == "", r.toString())
        expect(f.creates == 1 && r.stages.size == 1 && r.stages.single().decodeAttempts == 1)
        expect(r.stages.single().releaseSucceeded)
    }
    case("whitespace auto is admitted without trimming") {
        val f = Rig(listOf(" \n", "x")); val r = f.run()
        expect(r.status == "completed" && r.admitted == true && r.rawText == "x" && f.creates == 2)
        expect(r.stages[0].rawText == " \n")
    }
    case("forced empty output remains an actual empty result") {
        val r = Rig(listOf("nonempty", "")).run()
        expect(r.status == "completed" && r.admitted == true && r.rawText == "")
        expect(r.stages.size == 2 && r.stages.last().rawText == "")
    }
    for (stage in 1..2) for (op in listOf("create", "option", "accept", "decode", "result", "release")) {
        case("stage $stage $op fault has actual counters and acquired cleanup") {
            val point = "$stage." + if (op == "option") "option:" + if(stage == 1) "" else "English" else op
            val f = Rig(failAt = point); val r = f.run()
            expect(r.status == "error" && r.errorClass?.endsWith("Injected") == true, r.toString())
            expect(r.errorMessage == point && r.rawText == null)
            val s = r.stages.last()
            expect(s.status == "error" && s.errorClass?.endsWith("Injected") == true, s.toString())
            expect(s.createAttempts == 1 && s.createReturned == (op != "create"))
            expect(s.decodeAttempts == if(op in listOf("decode", "result", "release")) 1 else 0)
            expect(s.resultAttempts == if(op in listOf("result", "release")) 1 else 0)
            expect(s.releaseAttempts == if(op == "create") 0 else 1)
            expect(s.releaseSucceeded == (op != "create" && op != "release"))
            expect(f.creates == stage && r.stages.size == stage)
            if (op == "release") expect(s.releaseErrorClass?.endsWith("Injected") == true)
        }
    }
    for (point in listOf("1.accept", "1.decode", "2.accept", "2.decode")) {
        case("input mutation at $point stops and releases") {
            val f = Rig(mutateAt = point); val r = f.run()
            expect(r.status == "error" && r.errorClass != null && r.rawText == null, r.toString())
            expect(r.stages.last().releaseSucceeded && f.creates == point.take(1).toInt())
            expect(r.stages.last().decodeAttempts == if(point.endsWith("accept")) 0 else 1)
        }
    }
    case("initial persistence failure blocks all native calls") {
        val f = Rig(); f.persistFailure = { true }; val r = f.run()
        expect(r.status == "error" && r.persistErrorClass?.endsWith("Injected") == true)
        expect(f.creates == 0 && r.stages.sumOf { it.decodeAttempts } == 0)
    }
    case("persistence failure after decode preserves attempt and releases") {
        val f = Rig(); f.persistFailure = { it.stages.any { s -> s.decodeAttempts > 0 && !s.releaseSucceeded } }
        val r = f.run()
        expect(r.status == "error" && r.persistErrorClass != null && f.creates == 1)
        expect(r.stages.single().decodeAttempts == 1 && r.stages.single().releaseSucceeded)
    }
    case("terminal persistence failure cannot return completed") {
        val f = Rig(); f.persistFailure = { it.status == "completed" }; val r = f.run()
        expect(r.status == "error" && r.persistErrorClass != null)
        expect(r.stages.size == 2 && r.stages.all { it.releaseSucceeded })
    }
    case("initial gate closed makes no native call") {
        val f = Rig(); f.gate = { false }; val r = f.run()
        expect(r.status == "error" && f.creates == 0 && r.rawText == null)
    }
    case("gate loss after accept blocks decode but releases") {
        val f = Rig(); f.gate = { "1.accept" !in f.events }; val r = f.run()
        expect(r.status == "error" && r.stages.single().decodeAttempts == 0)
        expect(r.stages.single().releaseSucceeded && f.creates == 1)
    }
    case("gate exception is contained") {
        val f = Rig(); f.gate = { throw Injected("gate") }; val r = f.run()
        expect(r.status == "error" && r.errorMessage == "gate" && f.creates == 0)
    }
    case("clock failure before native dispatch blocks dispatch") {
        val f = Rig(); f.clock = { throw Injected("clock") }; val r = f.run()
        expect(r.status == "error" && f.creates == 0)
    }
    case("clock failure after decode still releases and stays error") {
        val f = Rig(); f.clock = { if("1.decode" in f.events) throw Injected("clock") else 42L }
        val r = f.run()
        expect(r.status == "error" && r.errorMessage == "clock" && f.creates == 1, r.toString())
        expect(r.stages.single().decodeAttempts == 1 && r.stages.single().releaseSucceeded)
    }
    case("primary and release failure both survive") {
        val f = Rig(failAt = "1.release")
        f.persistFailure = { it.stages.any { s -> s.decodeAttempts > 0 && s.releaseAttempts == 0 } }
        val r = f.run()
        expect(r.status == "error" && r.errorMessage == "persist" && r.persistErrorClass != null)
        expect(r.stages.single().releaseErrorClass != null && r.stages.single().releaseAttempts == 1)
    }
    for(point in listOf("1.result", "1.release", "2.result", "2.release")) {
        case("gate loss after $point fails after acquired cleanup") {
            val f=Rig(); f.gate={ point !in f.events }; val r=f.run()
            expect(r.status=="error" && r.rawText==null && r.errorClass!=null, r.toString())
            expect(r.stages.last().status=="error" && r.stages.last().errorClass!=null, r.toString())
            expect(r.stages.all { it.releaseSucceeded })
            expect(f.creates==point.take(1).toInt())
        }
    }
    for (locale in listOf("en", "zh_CN", "", "fr-FR")) case("reject unsupported locale '$locale'") {
        val f = Rig(); val r = f.run(locale)
        expect(r.status == "error" && f.creates == 0 && r.rawText == null)
    }
    for ((name, pcm) in listOf("empty" to floatArrayOf(), "NaN" to floatArrayOf(Float.NaN),
        "infinite" to floatArrayOf(Float.POSITIVE_INFINITY), "out-of-range" to floatArrayOf(1.1f))) {
        case("reject $name PCM before native") {
            val f = Rig(); val r = f.run(pcm = pcm)
            expect(r.status == "error" && f.creates == 0 && r.rawText == null)
        }
    }
    println("ASSERTIONS=$assertions FAILURES=${failures.size}")
    failures.forEach { println("FAIL $it") }
    check(failures.isEmpty())
}
