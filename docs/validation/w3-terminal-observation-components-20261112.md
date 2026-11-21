# W3 terminal observation components, 2026-11-12

The future terminal-observation byte contract and bounded wait are implemented
and tested. They address the proven observation race without changing the old
run or lowering its acceptance gate. **IO integration, Android packaging and new
device execution are not complete. W3/R4/R5 remain open.**

The consumed physical heap run remains diagnostic-rejected (19/20): its final
observed report was still running when `/proc/PID/status` disappeared. The later
completed report cannot fill that observation gap. Its original report SHA256
`e80a4678468e736fdf20049fa2f5e4cee49a5a32c437da83b6fcb1db570b8a0e`,
false verdict SHA256
`3575b70f0531560aeecc94935cf41f4415c15e1a5bef7e494b8f57c2cb31681d`
and all 120 frozen files remain unchanged.

The Kotlin component waits at most 30000 monotonic milliseconds after a future
wrapper completes native release, Activity teardown and terminal persistence.
Clock regression, callback failure, wrong bytes and ACK at the exact deadline
fail. The host validator produces ACK bytes only after two identical strict JSON
terminal reports, matching PID/startTicks, and ordered report reads enclosed by
the same-process resource sample within the existing inclusive RSS/available
limits. ACK confirms observation and can acknowledge a failed terminal report;
it does not accept schema counters, quality or inference success.

| Fresh check | Result |
| --- | --- |
| Existing Kotlin compiler/JDK, `-Werror` | Pass |
| Kotlin contract checks | 45 pass |
| Cross-iteration regressing clock | Original RED retained, final GREEN |
| Python unittest methods | 22 pass, including boundary subtests |
| Kotlin/Python exact ACK bytes | Four completed/failed and boundary-identity cases match |
| Python syntax | Pass |
| Old frozen materials and failed verdict | 120/120 and original report/verdict hashes unchanged |

Free CBC hy3 supplied implementation and test candidates. The parent reviewed,
integrated exact candidate bytes and ran the checks. Original test-wrapper
failure (1 failure/1 error), exact-type failures (17), non-finite exponent
failures (2), and JSON whitespace failure (3 failures/1 error) are retained.
Separate narrow candidates repaired each defect; strict tests were preserved.
A parent delta-check first assumed a different equivalent expression; that
assertion failure and the corrected exact delta check are recorded separately.

An independent supplied-context hy3 source review completed in 193.938s and
reported two low-priority findings. The proposed Arabic-digit reproduction was
factually wrong: the ASCII first-character guard rejects all-Arabic/fullwidth
strings, and mixed strings could only raise `UnicodeEncodeError` (a `ValueError`
subclass), never return an ACK. Actual observations are retained. The requested
explicit ASCII predicate was nevertheless supplied as a narrow hy3 candidate,
and the missing zero/non-ASCII rejection tests were added. The parent verified
that final source changed only the predicate and final newline, reran 22 tests
and all four Kotlin/Python comparisons. There was no separate full final-source
review after this narrow change; none of these reviews is release approval.

Local raw evidence: `.omx/artifacts/w3-terminal-observation-20261112/`, including
`final-components-acceptance.json`, `integrated-kotlin-check/`,
`integrated-host-check/`, `source-review/`, and the original helper outputs.
The [portable receipt](w3-terminal-observation-components-20261112.json) records
source hashes, boundaries and evidence references. Local raw artifacts are
ignored by Git and are not promised by a remote clone.

TECNO USB recovery is separately observed: after the user's physical reconnect,
ADB returned `device`; preflight read KM9/API35/arm64, Awake, keyguard
`showing=false`, MemAvailable 3023384 kB, and the ordinary Demo APK path. This
turn made no inference, microphone, AiAi or download request and installed no
package. This proves transport/preflight at that time, not future unlock state
or Android quality. Recovery records are in
`artifacts/verification/w3-android-recovered-20261112-0936/`; the earlier
`e00002ed` failure remains in `w3-android-retry-20261112-092504/`.

Next work is the independently named IO wrapper/driver with bounded exclusive
ACK persistence and actual fault/race checks, Android build/lint/package review,
and a new frozen contract. Do not execute the consumed FILE or heap drivers.
Complete phone quality/resource, source-to-exact-AAR, recursive binary
composition, production notices/integration and independent release review
remain unmet; this milestone does not replace them.
