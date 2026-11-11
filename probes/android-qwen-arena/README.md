# Isolated Qwen CPU arena policy

Preparation for one allocator hypothesis after the default FILE run exceeded the unchanged1.5GiB gate. It does not retry that run or establish native memory savings.

External freehy3 supplied `QwenArenaPolicy.kt` and a separate bounded-read correction; the primary integrated SHA`c86b96b68fb617f4920736bfa257ecf90280d9d1565ad9010725a5dbd56812e0` byte-for-byte. `prepare(existingPrivateRoot)` exclusively reserves `cpu-arena-off/cpu.config`, writes/syncs exact20B `EnableCpuMemArena=0\n`, and returns the canonical provider/path/size/SHA receipt. `verify` rebinds and rechecks without writing, with at most21B read. Symlink ancestry, collisions, tampering and mismatched receipts fail without bare-CPU fallback. The exclusive alias assumption and filesystem TOCTOU limitation are explicit in source.

Run parent checks with a new output directory:

```sh
rtk proxy python3 probes/android-qwen-arena/check_policy.py probes/android-qwen-arena/QwenArenaPolicy.kt /private/tmp/odai-arena-policy-check-output
```

Real Kotlin/JDK filesystem checks passed13groups41assertions; initial absent-candidate and oversized-file failures remain retained. Android API34classpath compilation with `-no-jdk/-Werror` passed3classes. No new dependency, Android/JNI runtime, recognizer/model, microphone, AiAi or device operation occurred. The old FILE sources/APKs/report remain unchanged and consumed. This directory has no new run/build/install command; wrapper integration, package/review and a separately frozen diagnostic contract are pending.

Evidence: `docs/validation/w3-qwen-arena-{feasibility,policy}-20261111.*` and `.omx/artifacts/w3-qwen-file-20261111/memory-investigation/`. W3 remains open.
