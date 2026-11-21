package io.ondeviceai.acceptance.qwenfile

import org.json.JSONArray

private var assertions = 0
private val failures = mutableListOf<String>()
private fun expect(value: Boolean, message: String = "assertion") { check(value) { message }; assertions++ }
private fun test(name: String, action: () -> Unit) {
    try { action(); println("PASS $name") } catch (e: Throwable) { failures += "$name: $e" }
}
private fun thrown(action: () -> Unit): Throwable {
    try { action() } catch (e: Throwable) { return e }
    error("expected actual failure")
}

fun main() {
    test("normal boundaries preserve stage counters at capture time and detached snapshots") {
        var clock = 10L; var reads = 0
        val recorder = HeapBoundaryRecorder({ clock++ }, { reads++; 1000L }, { reads++; 600L }, { reads++; 400L })
        val stages = mutableListOf(StageState("", createAttempts = 1))
        recorder.capture("before", 0, CaseState(phase = "auto", stages = stages))
        stages[0] = stages[0].copy(decodeAttempts = 1, releaseAttempts = 1, releaseSucceeded = true)
        recorder.capture("after", 0, CaseState(phase = "auto", stages = stages))
        val events = recorder.eventsSnapshot()
        expect(events.length() == 2 && reads == 6)
        val first = events.getJSONObject(0); val second = events.getJSONObject(1)
        expect(first.has("errorClass") && first.isNull("errorClass"), "successful event must explicitly preserve null errorClass")
        expect(first.getInt("seq") == 0 && second.getInt("seq") == 1)
        expect(first.getString("label") == "before" && second.getString("label") == "after")
        expect(first.getLong("elapsedRealtimeNanos") == 10L && second.getLong("elapsedRealtimeNanos") == 11L)
        expect(first.getJSONArray("stages").getJSONObject(0).getInt("decodeAttempts") == 0)
        expect(second.getJSONArray("stages").getJSONObject(0).getInt("decodeAttempts") == 1)
        expect(first.getString("casePhase") == "auto" && first.getInt("activeCaseIndex") == 0)
        first.put("label", "tampered"); first.getJSONArray("stages").getJSONObject(0).put("decodeAttempts", 99)
        expect(recorder.eventsSnapshot().getJSONObject(0).getString("label") == "before")
        expect(recorder.eventsSnapshot().getJSONObject(0).getJSONArray("stages").getJSONObject(0).getInt("decodeAttempts") == 0)
        recorder.requireHealthy(); expect(recorder.errorClass == null && reads == 6)
    }
    test("zero heap and absent case are valid") {
        val recorder = HeapBoundaryRecorder({ 0 }, { 0 }, { 0 }, { 0 })
        recorder.capture("initial", null, null); val event = recorder.eventsSnapshot().getJSONObject(0)
        for (field in listOf("elapsedRealtimeNanos", "nativeHeapSizeBytes", "nativeHeapAllocatedBytes", "nativeHeapFreeBytes")) expect(event.getLong(field) == 0L)
        for (field in listOf("activeCaseIndex", "casePhase", "errorClass")) expect(event.has(field) && event.isNull(field), "explicit null key $field missing")
        expect(event.getJSONArray("stages").length() == 0 && recorder.errorClass == null)
    }
    for (badIndex in 0..3) test("callback failure $badIndex retains real exception and readable prefix") {
        var reads = 0; val fault = UnsupportedOperationException("fault-$badIndex")
        val callbacks = (0..3).map { index -> { reads++; if (index == badIndex) throw fault else (100L + index) } }
        val recorder = HeapBoundaryRecorder(callbacks[0], callbacks[1], callbacks[2], callbacks[3])
        expect(thrown { recorder.capture("fault", 0, CaseState()) } === fault)
        expect(recorder.errorClass == fault.javaClass.name)
        val event = recorder.eventsSnapshot().getJSONObject(0)
        val fields = listOf("elapsedRealtimeNanos", "nativeHeapSizeBytes", "nativeHeapAllocatedBytes", "nativeHeapFreeBytes")
        for ((index, field) in fields.withIndex()) {
            expect(event.has(field), "numeric key $field missing after callback failure")
            if (index < badIndex) expect(event.getLong(field) == 100L + index) else expect(event.isNull(field))
        }
        expect(event.getString("errorClass") == fault.javaClass.name)
        val before = reads; recorder.eventsSnapshot(); expect(thrown { recorder.requireHealthy() } === fault)
        expect(thrown { recorder.capture("blocked", null, null) } === fault)
        expect(reads == before && recorder.eventsSnapshot().length() == 1)
    }
    for (badIndex in 0..3) test("negative measurement $badIndex never becomes false zero") {
        val callbacks = (0..3).map { index -> { if (index == badIndex) -1L else 100L } }
        val recorder = HeapBoundaryRecorder(callbacks[0], callbacks[1], callbacks[2], callbacks[3])
        val fault = thrown { recorder.capture("negative", null, null) }
        expect(fault is IllegalStateException || fault is IllegalArgumentException)
        val event = recorder.eventsSnapshot().getJSONObject(0)
        val field = listOf("elapsedRealtimeNanos", "nativeHeapSizeBytes", "nativeHeapAllocatedBytes", "nativeHeapFreeBytes")[badIndex]
        expect(event.has(field) && event.isNull(field) && event.getString("errorClass") == fault.javaClass.name)
        expect(recorder.errorClass == fault.javaClass.name)
    }
    test("backward monotonic clock fails closed") {
        var clock = 10L
        val recorder = HeapBoundaryRecorder({ clock }, { 1 }, { 1 }, { 1 })
        recorder.capture("first", null, null); clock = 9
        val fault = thrown { recorder.capture("backward", null, null) }
        expect(fault is IllegalStateException || fault is IllegalArgumentException)
        expect(recorder.errorClass == fault.javaClass.name)
        expect(recorder.eventsSnapshot().getJSONObject(1).has("elapsedRealtimeNanos") && recorder.eventsSnapshot().getJSONObject(1).isNull("elapsedRealtimeNanos"))
    }
    test("cleanup captures later failure without replacing first error and can later record success") {
        var fail = true; var reads = 0
        val first = IllegalArgumentException("first"); val later = UnsupportedOperationException("later")
        val recorder = HeapBoundaryRecorder({ 1 }, { reads++; if (fail) throw if (reads == 1) first else later; 0 }, { 0 }, { 0 })
        expect(thrown { recorder.capture("normal", null, null) } === first)
        recorder.capture("cleanup", null, null, cleanup = true)
        expect(recorder.errorClass == first.javaClass.name && reads == 2)
        expect(recorder.eventsSnapshot().getJSONObject(1).getString("errorClass") == later.javaClass.name)
        fail = false; recorder.capture("cleanup-ok", null, null, cleanup = true)
        expect(recorder.eventsSnapshot().getJSONObject(2).isNull("errorClass"))
        expect(thrown { recorder.requireHealthy() } === first && recorder.errorClass == first.javaClass.name)
    }
    test("128 event boundary fails rather than truncating healthy and cleanup remains bounded") {
        var reads = 0
        val recorder = HeapBoundaryRecorder({ reads++; 1 }, { reads++; 0 }, { reads++; 0 }, { reads++; 0 })
        repeat(128) { recorder.capture("e$it", null, null) }
        expect(recorder.eventsSnapshot().length() == 128 && recorder.errorClass == null && reads == 512)
        val fault = thrown { recorder.capture("overflow", null, null) }
        expect(fault is IllegalStateException && recorder.errorClass == fault.javaClass.name)
        expect(thrown { recorder.capture("still-blocked", null, null) } === fault, "unhealthy normal capture must rethrow original first fault")
        recorder.capture("cleanup", null, null, cleanup = true)
        expect(reads == 512 && recorder.eventsSnapshot().length() == 128)
        expect(thrown { recorder.requireHealthy() } === fault)
    }
    println("ASSERTIONS=$assertions FAILURES=${failures.size}")
    failures.forEach { println("FAIL $it") }; check(failures.isEmpty())
}
