# Apple MLXVLM Fixed-Image Benchmark Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce bounded, reproducible fixed-image quality, latency, and process-RSS evidence for the real Apple MLXVLM SDK backend.

**Architecture:** Extend the standalone MLXVLM consumer with opt-in repeated measurement and a narrow runtime load-timing observer. Validate and summarize the raw device JSON with a repository tool that rejects identity drift and metric overclaiming.

**Tech Stack:** Swift 6, XCTest, iOS 18+, MLXVLM, `mach_task_basic_info`, Python `unittest`, JSON.

**Spec:** `docs/superpowers/specs/2026-10-22-apple-mlxvlm-fixed-image-benchmark-design.md`

## Global Constraints

- Use only the existing fixed local model and synthetic fixture; no downloads, recording, private media, or signing-asset changes.
- Keep root `apple` at iOS 15/macOS 12 and keep MLX isolated in `apple-mlx`.
- Report process RSS only as process RSS; Metal/GPU peak and energy remain `unverified`.
- Synthetic-rubric success must not be described as natural-image quality acceptance.
- Do not modify or stage the two protected files named in the handoff.

## Review Focus

- A first update delivered before load completion, or after terminal completion, must make the run invalid.
- Fewer or more than five measured iterations must not produce a comparable summary.
- Identity or fixture drift must be rejected before statistics are emitted.
- Resource fields must not relabel process RSS as Metal/GPU memory.
- Synthetic output without both rubric concepts must remain a quality failure without hiding the raw output.

---

### Task 1: Strict MLXVLM benchmark validator and report

**Files:**
- Create: `tools/mlxvlm_benchmark_report.py`
- Create: `tools/tests/test_mlxvlm_benchmark_report.py`

**Interfaces:**
- Consumes: raw schema version 1 MLXVLM benchmark JSON from Task 2.
- Produces: `validate(run)` and `summarize(run)` with nearest-rank p50/p95 and explicit unverified fields.

- [ ] **Step 1: Write failing tests** for the fixed identity, exactly five ordered samples, process-RSS method, synthetic rubric, and rejection of Metal/GPU or natural-quality overclaims.
- [ ] **Step 2: Run** `rtk proxy python3 -m unittest tools.tests.test_mlxvlm_benchmark_report -v`; Expected: FAIL because the report module does not exist.
- [ ] **Step 3: Implement** strict validation and summary generation without external dependencies.
- [ ] **Step 4: Run** the focused tests and the full tool suite; Expected: PASS.
- [ ] **Step 5: Commit** `feat: validate fixed MLXVLM benchmark evidence`.

### Task 2: SDK load timing and standalone consumer measurement

**Files:**
- Modify: `apple-mlx/Sources/ODAIKitAppleMLXVLM/MLXVLMBackend.swift`
- Modify: `apple-mlx/Sources/ODAIKitAppleMLXVLM/MLXVLMGenerationSession.swift`
- Modify: the real runtime source under `apple-mlx/Sources/ODAIKitAppleMLXVLM/`
- Modify: `apple-mlx/Tests/ODAIKitAppleMLXVLMTests/MLXVLMBackendTests.swift`
- Modify: `examples/ios-mlxvlm-native/MLXVLMConsumerApp.swift`
- Modify: `tools/tests/test_apple_mlx_consumer.py`
- Create: `examples/ios-mlxvlm-native/BenchmarkMeasurements.swift` (executable host-tested measurement helper)
- Modify: `examples/ios-mlxvlm-native/project.yml` and `tools/generate_mlxvlm_consumer.py` (include helper)
- Modify: `tools/mlxvlm_benchmark_report.py` and its tests (reject missing source/failed runs; preserve unavailable RSS)

**Interfaces:**
- Consumes: existing backend/client generation and fixed consumer provisioning.
- Produces: public opt-in benchmark observer events for model-load completion plus `sdk-mlxvlm-benchmark.json` containing one warm-up and five measured iterations.

- [ ] **Step 1: Write failing Swift and generator tests** for ordered load timing, five samples, 10 ms process-RSS labeling, frozen prompt/budget/identity, and explicit unverified GPU/energy/natural-quality fields.
- [ ] **Step 2: Run focused tests**; Expected: FAIL because benchmark observation/output is absent.
- [ ] **Step 3: Implement** the minimal observer, runtime load measurement, repeated consumer loop, rubric, and process-RSS sampler.
- [ ] **Step 4: Run** apple-mlx host tests, consumer generator tests, and the full tool suite; Expected: PASS.
- [ ] **Step 5: Commit** `feat: measure fixed MLXVLM device runs`.

### Task 3: Real-device evidence and roadmap checkpoint

**Files:**
- Create: raw and summarized files under `artifacts/verification/apple-mlxvlm-fixed-image-20261022/`
- Modify: `docs/validation/apple-mlxvlm-backend.md`
- Modify: `docs/ROADMAP.md`
- Modify: `docs/HANDOFF-2026-10-20-speech-demo-bridge.md`
- Create: `.superpowers/sdd/2026-10-22-apple-mlxvlm-fixed-image-benchmark/progress.md`
- Create: `.superpowers/sdd/2026-10-22-apple-mlxvlm-fixed-image-benchmark/final-review.md`

**Interfaces:**
- Consumes: Task 2 consumer plus existing authorized device, signing setup, local model, and fixture.
- Produces: device raw JSON, validated summary, exact pass/fail/unverified record, roadmap/handoff/ledger checkpoint.

- [ ] **Step 1: Check device connection and re-verify model/fixture hashes** without changing device data outside this app container.
- [ ] **Step 2: Generate/build/install/run** the opt-in benchmark consumer using fixed package resolution and existing caches; Expected: one warm-up plus five measured successes or an exact device blocker.
- [ ] **Step 3: Pull and validate raw JSON** with Task 1; Expected: summary generated only if the strict contract passes.
- [ ] **Step 4: Update documentation** with measured values only from validated artifacts and preserve all unrelated acceptance gaps.
- [ ] **Step 5: Run targeted/full verification, self-review the exact task range, verify protected hashes/staging, and commit** `docs: checkpoint fixed MLXVLM benchmark evidence`.
