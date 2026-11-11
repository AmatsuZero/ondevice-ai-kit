# Isolated Qwen CPU arena policy

Preparation for one allocator hypothesis after the default FILE run exceeded the unchanged1.5GiB gate. It does not retry that run or establish native memory savings.

External freehy3 supplied `QwenArenaPolicy.kt` and a separate bounded-read correction; the primary integrated SHA`c86b96b68fb617f4920736bfa257ecf90280d9d1565ad9010725a5dbd56812e0` byte-for-byte. `prepare(existingPrivateRoot)` exclusively reserves `cpu-arena-off/cpu.config`, writes/syncs exact20B `EnableCpuMemArena=0\n`, and returns the canonical provider/path/size/SHA receipt. `verify` rebinds and rechecks without writing, with at most21B read. Symlink ancestry, collisions, tampering and mismatched receipts fail without bare-CPU fallback. The exclusive alias assumption and filesystem TOCTOU limitation are explicit in source.

Run parent checks with a new output directory:

```sh
rtk proxy python3 probes/android-qwen-arena/check_policy.py probes/android-qwen-arena/QwenArenaPolicy.kt /private/tmp/odai-arena-policy-check-output
```

Real Kotlin/JDK filesystem checks passed13groups41assertions; initial absent-candidate and oversized-file failures remain retained. Android API34classpath compilation with `-no-jdk/-Werror` passed3classes. No new dependency, Android/JNI runtime, recognizer/model, microphone, AiAi or device operation occurred. The old FILE sources/APKs/report remain unchanged and consumed.

The distinct external `QwenArenaInstrumentation.kt` is now integrated unchanged (SHA`bdcc9a5ef409c0f0533d902ec12298e8d50a40936b93613cbae4b4568a136cb3`). Its real-policy/core/65PCM host checks passed30groups4113assertions with Android/native doubles. Offline build/lint passed27.24s,0errors/7warnings; actual APKs verified16classes/65PCM/fourarm64library hashes/zero permissions/same debug certificate. The app/test IDs are `io.ondeviceai.acceptance.qwenarena[.test]`; the Kotlin runner remains in `io.ondeviceai.acceptance.qwenfile`. Independent source/package review found no Critical/Important code defect; evidence does not prove device behavior or memory savings.

Parent preparation commands (`prepare_build.py`, `build_acceptance.py`) have already produced the frozen local source/APKs; do not rerun preparation into the same directory. Parent-only `device_acceptance.py` and `verify_report.py` prepare a separately gated diagnostic. They refuse device access without the complete `diagnostic-frozen.json` and both independent review receipts. The new contract now binds108artifact hashes; parent22checks/baseline8/metrics31passed after the independent review fix pass. Read-only physical preflight is locked/asleep, with no new alias installed. No new model run has occurred. Preserve the consumed old run; no replay, fallback, parameter sweep or larger memory gate.

See `docs/validation/w3-qwen-arena-wrapper-20261111.md` for package evidence and `docs/validation/w3-qwen-arena-diagnostic-protocol-20261111.md` for the proposed single-variable diagnostic. The protocol document alone is not execution evidence.

Evidence: `docs/validation/w3-qwen-arena-{feasibility,policy}-20261111.*` and `.omx/artifacts/w3-qwen-file-20261111/memory-investigation/`. W3 remains open.
