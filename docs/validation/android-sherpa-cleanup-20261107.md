# Sherpa primary failure preservation

2026-11-07, source base `beb87c61c4a7e5fc68d410f039b6a0c687fc0483`.

`SherpaSession` previously closed its decoder in a plain `finally`. If decoding failed or cancellation was observed, and decoder release also threw, the release exception replaced the original failure. Two regressions reproduced both cases against the unchanged production source: expected `IllegalArgumentException` / `CancellationException`, actually received `IllegalStateException: decoder close failed`.

The internal decoder now implements `AutoCloseable`, and the session uses the existing Kotlin standard-library `use` utility. It keeps the primary error and attaches a close failure as suppressed. This removes the manual decoder `try/finally`; it adds no dependency or public API. PCM clearing, single-task admission and synchronous JNI drain remain in their existing enclosing `finally` blocks.

## Validation and scope

Evidence: `artifacts/verification/sherpa-cleanup-20261107/` (local ignored material).

- `red-complete.json`: eight session tests executed against the original implementation; exactly the two new regressions failed for exception replacement.
- `green.json`: the whole Sherpa module **14 tests passed**, and the Demo configured with `enableSherpaAsr=true` **56 tests passed**. These 70 tests actually executed. Speech's 29 passing tests were up-to-date; do not describe them as a new execution.
- Sherpa lint passed with **0 errors / 1 `GradleDependency` warning**. Kotlin compilation, existing cancellation/drain/reentry and waveform clearing tests passed.
- The first launcher omitted `sherpaMavenRepo`; offline dependency resolution failed before tests ran. That attempt is retained as `red.json` and is not a product RED. Recovery used the existing frozen local Maven AAR/POM and seven model/notice inputs, all nine byte lengths and hashes checked against the original candidate receipt. No download or global repository change.

The two added tests verify primary error semantics, suppressed cleanup failure, one decoder release, PCM clearing, and admission reuse after failed decode. Cancellation test errors are synthetic; the existing blocking decoder test covers actual coroutine cancellation and drain. This is host source-level evidence, without new JNI/device inference, recording, quality or resource samples.

Local Ollama `qwen3-coder:30b` reviewed the original two-file slice over loopback in 14.429 seconds. Its exception-masking finding was confirmed by the parent. Its other suggestion—release admission immediately while a cancelled synchronous decode is still running—was rejected because it permits overlap and violates the tested drain contract. Its assumption about exception identity across coroutine boundaries was also rejected. Exact prompt, raw response, usage and dispositions: `.omx/artifacts/ollama-sherpa-session-20261107/`; advisor index `.omx/artifacts/ask-ollama-sherpa-session-20261107.md`.

The local opinion covers pre-fix source and is not a final independent native review. Previously failed quota lanes were not restarted. The parent owns the minimal implementation and all execution evidence. Official Kotlin resource-management documentation was checked through Context7; the actual project tests verify the resulting suppression behavior.

Read-only verification: `rtk proxy python3 artifacts/verification/sherpa-cleanup-20261107/freeze.py --verify`.

Nine protected paths, production Apple lock, ordinary phone Apps/models and existing frozen candidates remain unchanged. No upload, source publication, dependency update, capture or new model download. Roadmap remains **10/12**; P4/P11 and release gates remain open. Pending `.6` hosted upload scope and external-review provider choices are not answered by this correction.
