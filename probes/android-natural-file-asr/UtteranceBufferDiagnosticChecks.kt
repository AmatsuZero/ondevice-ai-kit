package io.ondeviceai.sherpaasr

private var assertions = 0
private fun same(actual: Int, expected: Int) { check(actual == expected) { "expected $expected but was $actual" }; assertions++ }
@Suppress("UNCHECKED_CAST")
private fun snapshot(buffer: UtteranceBuffer): Map<String, Int> {
    val method = buffer.javaClass.methods.singleOrNull { it.name == "diagnosticSnapshot" && it.parameterCount == 0 }
    checkNotNull(method) { "diagnosticSnapshot is missing: true partial-chunk consumption is not observable" }
    return method.invoke(buffer) as Map<String, Int>
}
fun main(args: Array<String>) {
    val diagnostics = args.contains("diagnostics")
    var calls = 0
    val buffer = UtteranceBuffer { calls++; calls in 5..20 }
    val input = FloatArray(64000) { (it + 1) / 100000f }
    var finished = false
    var offeredEnd = 0
    while (offeredEnd < input.size && !finished) {
        val end = minOf(offeredEnd + 1600, input.size)
        finished = buffer.append(input.copyOfRange(offeredEnd, end))
        offeredEnd = end
    }
    check(finished); assertions++
    same(offeredEnd, 27200)
    same(calls, 51)
    val output = buffer.samples()
    same(output.size, 13440)
    check(output.contentEquals(input.copyOfRange(0, 13440))); assertions++
    if (diagnostics) {
        val before = snapshot(buffer)
        same(before.getValue("consumedSamples"), 26240)
        same(before.getValue("speechSamples"), 8192)
        same(before.getValue("firstSpeechSample"), 2048)
        same(before.getValue("lastSpeechEndSample"), 10240)
        same(before.getValue("preparedStartSample"), 0)
        same(before.getValue("preparedEndSample"), 13440)
        same(before.getValue("preparedSamples"), output.size)
        same(calls, 51)
        check(snapshot(buffer) == before); assertions++
        check(buffer.samples().contentEquals(output)); assertions++
        same(calls, 51)
    }
    buffer.clear()
    check(runCatching { buffer.samples() }.exceptionOrNull() is io.ondeviceai.speech.SpeechNotDetectedException); assertions++
    if (diagnostics) {
        val empty = snapshot(buffer)
        same(empty.getValue("consumedSamples"), 0)
        same(empty.getValue("speechSamples"), 0)
        same(empty.getValue("firstSpeechSample"), -1)
        same(empty.getValue("lastSpeechEndSample"), 0)
        same(empty.getValue("preparedStartSample"), -1)
        same(empty.getValue("preparedEndSample"), -1)
        same(empty.getValue("preparedSamples"), 0)
    }
    val full = UtteranceBuffer { true }
    val fullInput = FloatArray(160000) { it / 200000f }
    check(full.append(fullInput)); assertions++
    check(full.samples().contentEquals(fullInput)); assertions++
    if (diagnostics) { same(snapshot(full).getValue("consumedSamples"), 160000); same(snapshot(full).getValue("preparedEndSample"), 160000) }
    val leading = UtteranceBuffer { it.first() > 0f }
    val shortInput = ShortArray(16000) { if (it < 5120) 0 else 16384 }
    check(!leading.append(shortInput, shortInput.size)); assertions++
    val cropped = leading.samples()
    same(cropped.size, 14080)
    check(cropped.all { it.isFinite() }); assertions++
    if (diagnostics) { val s=snapshot(leading); same(s.getValue("preparedStartSample"),1920); same(s.getValue("preparedEndSample"),16000); same(s.getValue("preparedSamples"),14080) }
    val silent = UtteranceBuffer { false }
    check(!silent.append(FloatArray(10000))); assertions++
    check(runCatching { silent.samples() }.exceptionOrNull() is io.ondeviceai.speech.SpeechNotDetectedException); assertions++
    if (diagnostics) same(snapshot(silent).getValue("preparedSamples"),0)
    println("PASS assertions=$assertions diagnostics=$diagnostics")
}
