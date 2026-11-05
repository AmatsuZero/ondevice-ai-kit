# Private Android TTS probe reports

Test-only overlay for the exact `e0438c030a987ceeff9f9207c2475684749e4d8b`
candidate. The ordinary APK and the original probe remain unchanged. No new
dependencies, media capture or model downloads are needed.

`generate.py` checks the original probe's SHA-256, gives the copy a distinct
`PersistentTtsProbeInstrumentation` class name, and inserts `Recorder.kt.inc`.
It preserves the existing SDK normal playback, in-flight cancellation, followup
and closed-client assertions. Additional checkpoints record close entry/return
and the successful closed-client assertion.

Use a new isolated source/build directory, with all original committed input
hashes checked before and after building. Generate into that copy's androidTest
source set, then build only `:demo:androidApp:assembleDebugAndroidTest` with the
existing offline build arguments and:

```sh
rtk proxy python3 probes/android-tts-report/generate.py \
  <isolated-source>/demo/androidApp/src/androidTest/kotlin/io/ondeviceai/demo/TtsProbeInstrumentation.kt \
  <isolated-source>/demo/androidApp/src/androidTest/kotlin/io/ondeviceai/demo/PersistentTtsProbeInstrumentation.kt
# Additional Gradle argument:
# -PodaiTestRunner=io.ondeviceai.demo.PersistentTtsProbeInstrumentation
```

Pass a fresh alphanumeric/hyphen `runId` (1–80 characters) and `reportMode` of
`recorder-pass`, `recorder-fail`, or `tts` as instrumentation arguments. Reports
live at `files/odai-tts-reports/<runId>.json` inside the target App. Existing
report IDs are rejected. `AtomicFile` commits each stage and the final report
before `Instrumentation.finish` sends the result over Binder. An interrupted
run leaves a nonterminal stage, which must never count as a pass.

Validate actual `recorder-pass` and intentional `recorder-fail` reports first;
neither is a TTS success. Run the fixed TTS probe once, collect using `run-as`,
and compare host/device report SHA-256 before cleanup. ADB transport exit code
does not establish the test verdict. If observation fails, inspect the same
report, PID and active instrumentation; do not start another request.

```sh
rtk proxy python3 tools/probes/validate_android_tts_report.py <report.json> \
  --run-id <runId> --sdk-commit e0438c030a987ceeff9f9207c2475684749e4d8b
rtk proxy python3 -m unittest tools.tests.test_android_tts_report
```

The validator requires the complete ordered lifecycle, `terminal=true`, the
matching run/source identity, nonempty voice/engine, nonempty WAV output, the
exact backend and the original cancellation/close outcomes. The installed
APK/test hashes, device identity and source/build receipts must also be checked
separately; a report by itself is not APK provenance or a release gate.

Before replacing APKs, preserve/verify the original main/test APKs, runner,
model hashes, permissions and TTS settings. Fully push and hash rollback APKs
on device before installation. Restore with local `pm install -r`, without
clearing App data. Only after collecting terminal evidence and confirming no
active instrumentation, restore and recheck those invariants, then remove
only this run's verified report/APK paths. Retain raw host evidence.
