package io.ondeviceai.demo
fun main() {
    val rows = (0 until 20).map { SustainedCycleEvidence(it + 1, it * 30_000_000_000L, (it + 1) * 30_000_000_000L, 2, true, true) }
    fun valid(r: List<SustainedCycleEvidence> = rows, warm: Int = 1, attempts: Int = 40, success: Int = 40, total: Long = 600_000_000_000L, sampler: Boolean = true, lifecycle: Boolean = true, teardown: Boolean = true, persistence: Boolean = true) = sustainedRunComplete(r, warm, attempts, success, total, sampler, lifecycle, teardown, persistence)
    var count = 0
    fun expect(value: Boolean) { check(value); count++ }
    expect(valid())
    expect(!valid(rows.dropLast(1)))
    expect(!valid(rows + rows.last()))
    expect(!valid(rows.mapIndexed { i, c -> if (i == 3) c.copy(index = 3) else c }))
    expect(!valid(rows.mapIndexed { i, c -> if (i == 2) c.copy(endedNs = c.startedNs + 29_999_999_999L) else c }))
    expect(!valid(rows.mapIndexed { i, c -> if (i == 1) c.copy(startedNs = 29_000_000_000L) else c }))
    expect(!valid(rows.mapIndexed { i, c -> if (i == 4) c.copy(measuredSuccesses = 1) else c }))
    expect(!valid(rows.mapIndexed { i, c -> if (i == 5) c.copy(closeSucceeded = false) else c }))
    expect(!valid(rows.mapIndexed { i, c -> if (i == 6) c.copy(inputUnchanged = false) else c }))
    expect(!valid(warm = 0)); expect(!valid(warm = 2))
    expect(!valid(attempts = 39)); expect(!valid(attempts = 41)); expect(!valid(success = 39))
    expect(!valid(total = 599_999_999_999L)); expect(!valid(sampler = false))
    expect(!valid(lifecycle = false)); expect(!valid(teardown = false)); expect(!valid(persistence = false))
    println("$count sustained acceptance checks passed")
}
