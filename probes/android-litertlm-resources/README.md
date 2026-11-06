# Android LiteRT-LM resource probe

Operator-only test overlay for an already installed, authorized Gemma3n E2B
model. It never imports, downloads, removes or accepts terms for a model, and
never starts the camera or microphone. The fixed red PNG and prompt are public
synthetic test input; result text is represented only by SHA256, length and a
bounded red-word match.

Use a unique frozen source copy and test APK; do not add this runner to default
production sources or replace the ordinary App. The retained run and exact
build/device/restore scripts are under ignored
`artifacts/verification/android-litertlm-resources-20261107/`.

Before installation, verify the original main/test APK digests, original runner,
all protected private files, permissions, default TTS engine, empty model-store
staging, and ordinary LM cache. Constructing ModelStore can clean staging; never
run this probe when another model operation is active. Retain the original test
APK and stage/hash-check its exact rollback before installing the probe.

One warmup is followed by five sequential calls through `LiteRtLm.createBackend`
and `generate`, using the existing ModelStore and explicit CPU_TEXT_GPU_VISION.
Each call creates/closes a native session. Whole generate wall time includes
availability/model integrity checks, snapshot/cache, native initialization,
blocking send and release. Sampler join, post-call assertions, idle and report
writes are excluded. Each RSS value is whole-process VmRSS; nominal sampling
interval20ms is a request, not an exact schedule or true/GPU memory peak.
Retain every actual elapsed timestamp. Five measured calls are a small bounded
characterization, not sustained resource-budget, energy or natural-quality proof.

The runner reserves a unique atomic private report and an exclusively created
`work-<runId>` directory. It removes only its own synthetic image and empty
cache directories; no preexisting input/cache is deleted. Completed rows are
persisted before continuing, and a terminal report precedes Binder finish. On
ADB disconnect, observe the same report/PID/instrumentation; do not reissue a
second run. On native failure, stop and preserve the failed report; no retry.

Always restore the exact original test/runner, recheck preservation, collect the
raw report as exact bytes with device SHA256, then delete only the exact owned
report/staged APK paths and return the ordinary App. Keep failure/restoration
receipts separately; exit0 alone does not prove inference success. Do not reuse
historical mutation scripts on a new device session.

The strict offline reducer is `tools/probes/android_litertlm_resource_report.py`;
P11 groups remain open/not-ready and must bind raw, summary, input and provenance
identities. The whole ordinary App commit is not asserted by a test-build commit.
