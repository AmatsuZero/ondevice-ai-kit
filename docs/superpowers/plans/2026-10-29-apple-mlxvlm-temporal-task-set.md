# Apple MLXVLM Temporal Task-Set Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a reproducible, hash-bound same-source motion-vs-frozen-control evidence package for Apple MLXVLM without changing production SDK or Demo behavior.

**Architecture:** A tracked Python preparation/validation module verifies the pinned six-second source, derives six two-second fixtures, validates media metadata and pair relationships, and scores immutable device rows. An ignored standalone Swift consumer performs three semantic and six exact-label temporal requests in one process. A tracked report validator emits deterministic evidence summaries; documentation records `pass-bounded`, `observed-failure`, or `unverified` without changing the P11 gate.

**Tech Stack:** Python 3 standard library, ffmpeg/ffprobe, JSON/SHA-256, existing Swift 6 MLXVLM consumer, `xcrun devicectl`, existing model and fixture manifests.

**Spec:** `docs/superpowers/specs/2026-10-29-apple-mlxvlm-temporal-task-set-design.md`

## Global Constraints

- Reuse `artifacts/P7/natural/source.ogv`; verify its pinned byte size, SHA-1, and SHA-256 before deriving anything.
- Derivatives are 320×180, 24 fps, H.264/yuv420p, 48 frames, two seconds, no audio; motion windows are `0–2`, `2–4`, and `4–6` seconds.
- Frozen controls repeat each matched motion window's first decoded frame for all 48 frames.
- Use the exact prompts and rubrics in the spec; exact `MOVING`/`STILL` answers only, with no permissive normalization.
- Keep raw reports immutable and retain unique run IDs, device/OS, backend, model/runtime revisions, prompt, input hash, duration, and source commit.
- Do not modify production SDK, KMP/Demo behavior, tokenizer, generation defaults, model assets, public APIs, protected dirty files, or grouped benchmark samples.
- Do not use Photos, picker, recording, private media, or network during preparation or execution.
- A failed or incomplete device run remains `observed-failure` or `unverified`; it is never promoted to a pass.

## Review Focus

- Source or derivative hash drift: preparation and validation must stop before publishing or accepting a report; covered by source/manifest drift tests.
- Metadata drift (frame count, duration, dimensions, fps, codec, pixel format, audio): validator rejects the derivative; covered by ffprobe metadata-negative tests.
- Motion/control pair mismatch: task IDs and pair IDs must bind the expected source interval and control relationship; covered by pair-manifest tests.
- Ambiguous or verbose model output: only exact normalized `MOVING` or `STILL` is valid; covered by wrong-label, both-label, neither-label, and extra-text tests.
- Partial or duplicated device evidence: exact nine-row cardinality, unique run IDs, known task IDs, and source-report SHA-256 are required; covered by report-structure-negative tests.

### Task 1: Add deterministic temporal fixture preparation and manifest contract

**Files:**
- Create: `tools/probes/natural_video_temporal_fixture.py`
- Create: `tools/probes/natural_video_temporal_manifest.json`
- Create: `tools/tests/test_natural_video_temporal_fixture.py`

**Interfaces:**
- `file_identity(path: Path) -> dict[str, int | str]`
- `prepare_temporal_fixture(source: Path, destination: Path, manifest: dict) -> dict`
- `validate_temporal_manifest(manifest_path: Path, fixture_root: Path) -> dict`
- CLI: `python3 tools/probes/natural_video_temporal_fixture.py prepare <source> <destination> --manifest <manifest>`
- CLI: `python3 tools/probes/natural_video_temporal_fixture.py validate <manifest> --root <fixture-root>`

- [ ] Write RED tests for pinned source mismatch, output-directory collision, three exact motion intervals, repeated-first-frame controls, 48 frames/24 fps/320×180/no audio, deterministic manifest ordering, and metadata/pair drift rejection.
- [ ] Run `python3 -m unittest tools.tests.test_natural_video_temporal_fixture`; confirm failure against the absent module.
- [ ] Implement atomic staging/publication using explicit ffmpeg arguments, recording ffmpeg/ffprobe versions, source identity, derivative hashes, decoded metadata, intervals, role, pair ID, and control relationship.
- [ ] Run the focused fixture tests and a local `validate` against the existing source; require all six derivatives and exact manifest identity before proceeding.

### Task 2: Add pure temporal scoring and report validator

**Files:**
- Create: `tools/mlxvlm_temporal_task_set.py`
- Create: `tools/tests/test_mlxvlm_temporal_task_set.py`

**Interfaces:**
- `normalize_temporal_answer(output: str) -> str`
- `score_temporal_row(raw: dict, *, task: dict) -> dict`
- `score_semantic_row(raw: dict, *, task: dict) -> dict`
- `build_temporal_summary(rows: list[dict], *, manifest: dict, source_report: Path) -> dict`
- CLI: `python3 tools/mlxvlm_temporal_task_set.py validate <report.json> --manifest <manifest.json> --source-report <raw-report.json> --output <summary.json>`

- [ ] Write RED tests for exact valid labels, extra prose, both labels, neither label, opposite labels, semantic rubric reuse, duplicate run IDs, unknown/missing task IDs, wrong row count, stale source-report hash, and byte-stable sorted output.
- [ ] Run the focused validator tests; confirm RED.
- [ ] Implement strict task binding for exactly nine rows: three `semantic-motion` rows and six `temporal-motion`/`temporal-frozen-control` rows, with three pair verdicts and a suite verdict.
- [ ] Preserve raw answers and durations, include backend/model/runtime/prompt identities, and emit explicit limitations from the spec; never rewrite raw reports or broaden claims.
- [ ] Run focused tests, `py_compile`, and two validator generations compared byte-for-byte.

### Task 3: Extend the ignored Swift consumer for the nine-request suite

**Files:**
- Modify: `artifacts/verification/apple-mlxvlm-natural-video-20261028/consumer/MLXVLMConsumerApp.swift`
- Create: `artifacts/verification/apple-mlxvlm-natural-video-20261028/consumer/TemporalTaskSetSuite.swift`
- Modify: `artifacts/verification/apple-mlxvlm-natural-video-20261028/consumer/project.yml`
- Create: ignored `artifacts/verification/apple-mlxvlm-natural-video-20261028/temporal/` build, copy, launch, raw-report, and log outputs

**Interfaces:**
- Add explicit launch argument `--sdk-mlxvlm-temporal-task-set`.
- `TemporalTaskSetSuite.run() async` writes `sdk-mlxvlm-temporal-task-set.json` to the App Documents container.

- [ ] Add a host-side negative fixture/argument smoke test proving the new suite is opt-in and does not alter existing natural-quality or benchmark dispatch.
- [ ] Implement one model install, one lease, one client, three semantic motion requests followed by six temporal requests, fresh UUID per row, `drain()` between requests, close/remove cleanup, final input re-hash, and failure-state retention of completed rows.
- [ ] Build the ignored consumer with the existing cached packages and verify the app embeds the new source without touching the production project.

### Task 4: Prepare fixtures, execute nine physical-device requests, and validate raw evidence

**Files:**
- Create: ignored `artifacts/verification/apple-mlxvlm-natural-video-20261028/temporal/fixture-manifest.json`
- Create: ignored `artifacts/verification/apple-mlxvlm-natural-video-20261028/temporal/device-report.json`
- Create: ignored `artifacts/verification/apple-mlxvlm-natural-video-20261028/temporal/summary.json`
- Create: ignored `artifacts/verification/apple-mlxvlm-natural-video-20261028/temporal/README.md`

**Interfaces:**
- Consume the tracked temporal manifest and six prepared inputs.
- Produce one immutable raw report with nine rows and the exact source-report hash required by Task 2.

- [ ] Verify source identity, six derivative hashes/metadata, model inventory, current source commit, connected iPhone identity, and protected-file hashes before device launch.
- [ ] Copy only six inputs and the existing model directory into the isolated App Documents container; run the explicit argument on one physical iPhone in one process.
- [ ] Retain all nine rows even if a later request fails; mark execution `failed` and do not synthesize missing rows.
- [ ] Run the tracked validator and inspect per-task, per-pair, and suite verdicts; preserve exact raw outputs for any observed failure.

### Task 5: Integrate bounded evidence into P11 documentation and close verification

**Files:**
- Modify: `docs/validation/multimodal-video-demo.md`
- Modify: `docs/validation/foundation-followups.md`
- Modify: `docs/validation/p11-status-matrix-20261027.md`
- Modify: `docs/superpowers/specs/2026-10-29-apple-mlxvlm-temporal-task-set-design.md` only for builder-call decisions discovered during implementation

- [ ] Add the raw-report and summary hashes/paths, measured semantic and temporal verdicts, and explicit coupling of exact-label instruction following with temporal discrimination.
- [ ] Keep natural temporal quality, broad task-set generalization, GPU/energy, sustained resources, minimum OS, physical touch, background terminal timing, F08, and P11 open/unverified as required by the design.
- [ ] Run focused Python tests, `py_compile`, readiness validation, grouped-report deterministic regeneration, `git diff --check`, and protected-file hash checks.
- [ ] Review `git diff` and stage only tracked validator/tests/manifest/docs/plan changes; leave all protected dirty files and ignored evidence untouched.
- [ ] Commit the tracked package in small commits only after all gates pass.

## Stop Condition

Stop this package only when the six derivatives and manifest validate, the isolated consumer build succeeds, exactly nine physical-device rows are retained or an explicit `unverified` blocker is recorded, the validator regenerates byte-for-byte, all focused/readiness/grouped/diff/protected-hash checks pass, and documentation reports the measured bounded result without claiming complete temporal quality or roadmap completion.
