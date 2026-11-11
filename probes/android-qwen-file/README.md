# Isolated Android Qwen two-stage FILE ASR

This is an acceptance probe, outside the ordinary Demo and production backend. No microphone, system ASR, AiAi, network permission, new download or production default change is part of this probe.

Core and wrapper sources are supplied by free external CBC and integrated unchanged after parent Kotlin checks (core 42 groups / 264 assertions, wrapper 24 groups / 364 assertions). The actual APK and independent wrapper review passed. The single physical FILE run then stopped at sampled RSS 1.586578 GiB, above the fixed 1.5 GiB limit, before any complete case. It is consumed and must not be replayed. See `docs/validation/w3-qwen-android-file-observed-20261111.md`; current Android quality/resource acceptance remains open.

The fixed pipeline uses full original 16kHz s16LE PCM, auto language admission based on exact empty raw text, then a fresh stream forced to the requested language. It preserves the 65 independent inputs and reference text from the existing Mac confirmation. No VAD, gain, crop or text filtering is applied. Annotation-negative AMI intervals have not been manually listened to.

Parent tools:

- `check_core.py CANDIDATE OUTPUT_DIR`: real Kotlin execution with fault injection; output directories are exclusive.
- `check_inputs.py CANDIDATE OUTPUT_DIR`, `check_activity.py CANDIDATE OUTPUT_DIR` and `check_report.py CANDIDATE OUTPUT_DIR`: supporting-module checks; see `docs/validation/w3-qwen-android-file-modules-20261111.md` for accepted hashes and original failures.
- `check_wrapper.py CORE ACTIVITY INSTRUMENTATION OUTPUT_DIR`: real case core and fixture bytes, host Android/JNI doubles. This does not prove native/device runtime.
- `check_parent.py`: resource boundary and false-acceptance regressions, synthetic reports only.
- `prepare_build.py`: requires accepted candidate hashes; generates an isolated Gradle harness and freezes a distinct run plan.
- `build_acceptance.py build|verify`: offline Gradle/lint, then actual APK class/manifest/permission/native/signing and all 65 packaged asset checks.
- `device_acceptance.py preflight|prepare|run|collect|restore`: sequential one-shot physical acceptance, never replaying the consumed load-only run. Invoke dependent stages only after their prerequisites pass.
- `verify_report.py`: unchanged scorer and prior engineering thresholds, reports coverage/failures independently. Failed or unexecuted rows are not replaced with empty hypotheses.

All shell commands use `rtk proxy`. Evidence roots are `.omx/artifacts/w3-qwen-file-20261111/` and `artifacts/verification/w3-qwen-file-20261111/`. The ordinary Apps/models remain protected by exact manifests and hashes. Source branch push is not part of this probe.

The parent stops the exact new alias if sampled RSS exceeds 1.5GiB, available memory falls below 512MiB, a case/load window exceeds 120 seconds or observation fails. A stopped/in-flight decode has incomplete last-persisted counters; never claim these are exact final dispatch counts. Samples are not true-peak/GPU/energy or sustained-resource proof. Preserve raw reports, failures and source/model/input fingerprints, collect the same run, then restore only the exact temporary aliases/staging.
