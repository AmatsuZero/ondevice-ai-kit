# W8 ASR resource-window source review — 2026-11-08

## Code Review Summary

**Scope:** static review of the opt-in Android natural-ASR resource-observation
candidate against the accepted source harness. No build, test, device operation,
audio capture, model operation, or resource observation was performed. No
private data was supplied or read.

**Files Reviewed:** 3
**Total Issues:** 1

### By Severity

- CRITICAL: 0 (must fix)
- HIGH: 0 (should fix)
- MEDIUM: 1 (consider fixing)
- LOW: 0 (optional)

### Confirmed scope compliance

- `resourceWindow` is disabled when absent, accepts only the literal `true`, and
  adds no report fields or holds on the default path
  (`.omx/artifacts/cbc-hy3-w8-asr-observer-window-20261108-2335/candidate/NaturalAsrInstrumentation.kt:89-96,135-137`).
- With the opt-in enabled, the runner publishes a nonterminal bind receipt with
  its actual process PID, then holds the instrumentation worker for a separately
  recorded requested/actual five seconds before model work
  (`NaturalAsrInstrumentation.kt:127-137,199-221`). It uses the existing model
  verification/open path and does not install or repair a model
  (`NaturalAsrInstrumentation.kt:223-228`).
- The same four successful transcription requests are retained. Each post-success
  ten-second hold is separate from the segment `startedElapsedNs`,
  `endedElapsedNs`, and `durationNs`; a failed request reaches the original one
  close path without a new hold (`NaturalAsrInstrumentation.kt:249-297,325-361`).
  The candidate neither creates an observer nor fabricates RSS/thread/CPU
  metrics.
- The supplied DELIVERY document correctly limits this to an unrun observation
  window, rather than a claim about continuous inference, GPU, energy, or budget
  acceptance (`.omx/artifacts/cbc-hy3-w8-asr-observer-window-20261108-2335/candidate/DELIVERY.md:1-99`).

### Issues

[MEDIUM] Opt-in row-level `durationNs` silently includes an idle resource hold.

File: `.omx/artifacts/cbc-hy3-w8-asr-observer-window-20261108-2335/candidate/NaturalAsrInstrumentation.kt:260-310`

Issue: the candidate correctly captures each segment’s end timestamp before the
post-success hold. However, for the third sample’s two segments, `rowStarted`
is captured before segment 0 and `rowEnded` after segment 1; the ten-second hold
between them is therefore included in the pre-existing row `durationNs`.
Nothing in the emitted row or top-level report explicitly says that the
row-level duration is wall-clock time containing resource-observation idle
time. The separate per-segment hold fields do not make that legacy aggregate
unambiguous to an ordinary row consumer.

Impact: a later reader can mistake an opt-in row duration for active ASR latency
or continuous inference, defeating the requested boundary between an observation
window and performance/resource-budget evidence.

Fix: when `resourceWindow` is enabled, add an explicit row-level disclosure such
as `durationIncludesResourceHold: true` (or equivalently rename/add a clearly
labelled wall-clock field). Preserve the existing `durationNs` and default-path
schema; do not fit a resource metric, alter the four requests, or change the
segment inference timing.

### Validation limits

The candidate naturally completed in the helper lane but is explicitly **UNRUN**
as source. The parent must still compile and run it with the existing external
observer. Any resulting observations include bind/loading, inference, and idle
holds; they cannot establish pure continuous inference, GPU behavior, energy
use, or any budget approval.

### Recommendation

**REQUEST CHANGES** — add the narrow row-level wall-clock/hold disclosure above,
then proceed with the planned parent build and physical execution. No production
source, observer rewrite, or resource-threshold change is required.

---

## Narrow source re-review — 2026-11-08

**Files Reviewed:** 1
**Total Issues:** 0

### By Severity

- CRITICAL: 0 (must fix)
- HIGH: 0 (should fix)
- MEDIUM: 0 (consider fixing)
- LOW: 0 (optional)

The prior MEDIUM issue is fixed in
`.omx/artifacts/cbc-hy3-w8-duration-disclosure-20261108-2355/candidate/NaturalAsrInstrumentation.kt:314-318`.
When `resourceWindow` is enabled, the row now explicitly records
`durationIncludesResourceHold: true`, immediately before it is appended. The
comment accurately limits the statement to row wall-clock time; the existing
per-segment `durationNs` remains delimited around `transcribe` and therefore
excludes post-success holds (`NaturalAsrInstrumentation.kt:249-293`).

The disclosure is conditional, preserving the default schema and behavior when
the flag is absent. It adds no request, hold, observer, resource metric, model
operation, or production-source change.

### Recommendation

**APPROVE_SLICE** — source-only correction accepted. It remains unbuilt and
unrun in this review and does not establish continuous inference, GPU/energy
behavior, or resource-budget approval.
