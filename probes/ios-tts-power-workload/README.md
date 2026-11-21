# Single TTS Power Profiler workload

Uses the real public `AppleAVSpeechBackend` and `SpeechSynthesisClient`, fixed public text, one excluded warmup, two measured speaks, one close, one >=30s paced cycle. No microphone, photos or persisted audio. This is separate from the accepted20cycle sustained resource run.

Generate with XcodeGen from this directory; build unsigned with the existing local Apple package. Only the primary coordinates signing and device use. Newalias `io.ondeviceai.probe.w8ttspower20261109`; never replace the ordinary App. Obtain current PID immediately before attaching PowerProfiler; the recorder may reject an old PID even if CoreDevice still lists it. Preserve all failed attempts. Do not retrigger a completed workload.

Start a90second exactPID PowerProfiler trace, observe `Starting recording`, then click `Run TTS power workload` once. Mobile element refs require the leading `@` (e.g. `@e3`), otherwise the tool rejects the tap before execution. No auto-start. Trace window must contain the report's true `wallClock.wallStartUnixSeconds..wallEndUnixSeconds`; compare wall delta with `elapsed.totalMonotonicMs` and reject absolute drift>250ms. Monotonic uptime is not an absolute wall clock. All original fail-closed lifecycle, close/RSS, report-write and idleTimer restoration guards remain.

Collect the same terminal report twice from `Documents/w8ttspower/w8-tts-power-report.json`, compare exact bytes and source/PID/trace paths, and retain rawtrace/table exports. Remove only the newalias, compare all original installedApprecords and return Combined.

Parent acceptance2026-11-09: actual unsignedbuild11.787s, signature/MachO checks; PID7006,1cycle/2success,31.875s including20.521sidle; wall drift-0.3346ms;29checks.179processimpact/91systempowerrows exported. Modelled impacts, negative sentinel values and all-zero systemusage do not prove joules/watts, GPU allocation or a productionbudget. Evidence: `artifacts/verification/w8-tts-power-workload-20261109-1214/`; scoped disposition: `docs/validation/w8-tts-power-workload-20261109.md`.
