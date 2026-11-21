# W3 Qwen KV288 Implementation Plan

> **For agentic workers:** Execute task-by-task using the established external CBC candidate workflow; the primary agent owns integration, acceptance and device operations. Native review is paused after its prior 429.

**Goal:** Measure whether constructor capacity288 preserves all65 fixed-file outputs and reduces sampled memory in a new isolated Android emulator run.

**Architecture:** Copy the accepted arena-off wrapper under a distinct class/package alias/report path, changing only constructor capacity512→288 and truthful snapshot metadata. Reuse the original models, core, assets, build chain, scorer and device guards. Never mutate the frozen512 experiments.

**Tech Stack:** Kotlin, Android Instrumentation, Sherpa2.0.1, existing Android36 ARM64 emulator, Python acceptance tools.

**Spec:** `docs/validation/w3-qwen-kv-capacity-preflight-20261111.md` and its machine/review receipts.

## Global Constraints

- Empty hotwords,16k mono,≤160000samples,65 original inputs in unchanged order. CPU2/maxNewTokens128/seed42/temperature1e-6/topP0.8/two-stage/arena-off unchanged.
- Original RSS1.5GiB / available512MiB / case120s / batch7920s stop gates; no automatic retry. No microphones/AiAi/downloads/production/default changes.
- New wrapper `QwenKv288Instrumentation`, alias `io.ondeviceai.acceptance.qwenkv288`, report child `w3-qwen-kv288`, variant `cpu-arena-off-kv288-v1`.
- New run `w3-qwen-kv288-emulator-20261111-01`, AVD `W3_Qwen_Kv288_20261111`, serial `emulator-5582`, RAM4096MiB/CPU2/API36/default/arm64-v8a revision2. Protect ordinary Medium_Phone and all physical contracts.
- Source/AAR mapping, phone acceptance, truepeak/GPU/energy and production release remain outside what emulator data can prove. W3/R4/R5 stay open unless their actual remaining gates are met.

## Review Focus

- Constructor and every snapshot must both say288; early and failure reports must never inherit512 from the shared serializer.
- Failure after allocation-policy persistence must still block native construction; preserve first errors and cleanup semantics.
- New emulator identity and report path must fail closed, including CRLF console responses and quoted remote shell arguments.
- Frozen essential inputs must be nonempty and include new wrapper/source/package/parent tools/review; old contracts remain intact.
- Collection must bind same run/two identical reports/six model hashes/current APKs/inodes and bounded21B policy reads; no inference replay on collection failure.

## Task 1: Isolated wrapper and actual package

Files: create `probes/android-qwen-kv288/QwenKv288Instrumentation.kt`, `check_wrapper.py`, `prepare_build.py`, `build_acceptance.py`; reuse original arena/file source without edits.

- [ ] Write parent host acceptance requiring constructor288 and config288 in initial/running/failed/terminal snapshots, retaining all prior lifecycle faults. Run a test-only512 baseline; expect actual failing assertions/native configuration rejection.
- [ ] Free hy3 supplies a bounded tool-free edit candidate against the original wrapper. Review and apply exactly; host checks must pass without weakened expectations.
- [ ] Prepare an independent source tree, build offline, run lint and verify actual DEX/class/alias/runner/65assets/four originalarm64libs/signature/no permissions. Verify original source checksums unchanged.
- [ ] Independent free hy3 source/package review; resolve findings before accepting and committing exact task files.

Interface: candidate receipt binds every source file and fingerprint; package receipt binds both APKs and the original four runtime libraries.

## Task 2: Frozen parent contract and unique emulator run

Files: create `probes/android-qwen-kv288/device_acceptance.py`, `check_parent.py`, `verify_report.py`; evidence under `artifacts/verification/w3-qwen-kv288-emulator-20261111/`.

- [ ] Adapt the proven FILE/emulator driver to new identity/alias/report/run only; new verifier requires288 without changing quality rules. Pin the known policy collector literal count2 and shlex.join behavior before freezing.
- [ ] Verify wrong serial/AVD/API/ABI/user/report/capacity/fingerprint/empty hashes fail; retain original resource boundary checks and all65 inputs.
- [ ] Free hy3 reviews parent/contract. Freeze required files plus original108/120/327 guards, source/package/plan/reviews and prior512report before any device mutation.
- [ ] Fresh preflight, create exact isolatedAVD using the existing image, prepare/one run/collect/score/restore. Stop at any original gate, preserve partial evidence, no automatic re-run.

Interface: immutable same-run raw report, host RSS observations, collected policy and restoration receipts. Policy effect remains unverified.

## Task 3: Compare and deliver

- [ ] Compare all65ID/PCM/sample/locale/reference and raw stage/result strings against512; apply unchanged scorer and report exact counts, errors, RTF and sampledRSS. No claims of controlled phone savings or truepeak.
- [ ] Stop exact task emulator, preserve logs/source/reports and ordinaryAVD; inspect task disk artifacts before any exact cleanup.
- [ ] Record result and unresolved boundaries, free hy3 evidence-limited review, verify protected files, commit only task increments and sync new marker to bothissues/wiki with exact readback. Keep goal active while required phone/production evidence remains unavailable.
