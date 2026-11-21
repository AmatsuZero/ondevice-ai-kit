# Apple MLXVLM Natural Video Temporal Task-Set Design

## Intent

Extend the existing five-run single-fixture semantic evidence into a bounded,
falsifiable temporal task set without changing the production SDK, model,
Demo, signing project, or device input path. The task set reuses the already
verified six-second CC0 Common swifts source and asks whether the current model
can distinguish real motion from a matched repeated-frame control.

This package is evidence generation, not a product feature. It must be able to
record a model limitation as a failed temporal contrast instead of weakening
the rubric until the current output passes.

## Goals

- Verify the existing `source.ogv` against the pinned upstream identity in
  `tools/probes/natural_video_manifest.json` before deriving any input.
- Derive three non-overlapping two-second motion windows (`0–2`, `2–4`, and
  `4–6` seconds) at 320×180, 24 fps, H.264/yuv420p, without audio.
- Derive one matched two-second frozen control per window by repeating that
  window's first decoded frame at 24 fps.
- Freeze every derivative's SHA-256, duration, dimensions, frame rate, frame
  count, source interval, and control relationship in a manifest.
- Run two bounded tasks in the existing ignored standalone consumer:
  1. the existing one-sentence semantic description on each motion window;
  2. a temporal contrast prompt on each motion/control pair that requests an
     exact `MOVING` or `STILL` answer.
- Preserve raw output, unique run IDs, duration, device/OS, backend,
  model/runtime revision, prompt, input hash, and source commit for every run.
- Produce a deterministic validator summary that can report semantic and
  temporal failures without promoting them to passes.

## Non-goals and anti-goals

- Do not download or introduce a second media source in this package.
- Do not change production SDK, KMP/Demo behavior, tokenizer, generation
  defaults, model assets, or public APIs.
- Do not write Photos, open a picker, record media, use private media, or
  require network access during preparation or execution.
- Do not call a frozen repeated-frame control a natural video. It is a matched
  diagnostic control derived from a natural frame.
- Do not infer general temporal understanding, cross-scene generalization,
  aesthetic quality, human preference, GPU/energy efficiency, sustained
  resources, minimum-OS support, or production readiness from this task set.
- Do not add these reports as grouped benchmark timing samples. P11 remains
  `open/not-ready` while its other quality/resource gaps remain.
- Do not treat an answer that contains both labels, neither label, or the wrong
  label as a pass. Do not tune accepted wording after observing device output.

## Approaches considered

### A. Three motion windows with the existing description prompt

This is the smallest expansion and checks semantic consistency across time in
the same source. It does not prove the model used temporal order because a
single sampled frame could support the same description.

### B. Three motion windows plus matched frozen controls — selected

Each natural motion window is paired with a repeated-first-frame control. The
same exact-label prompt is sent to both. This adds a falsifiable temporal
contrast while keeping source identity and scene content as close as possible.
It is still same-source evidence, but it can expose motion hallucination on a
static sequence and is stronger than repeated free-form captions alone.

### C. Add a second licensed natural-video source

This offers better cross-scene coverage but requires new license/source
review, media preparation, reference facts, and a larger device matrix. It is
deferred until the same-source temporal contrast is measured and its failure
modes are known.

The user approved proceeding in the order B first, with the semantic part of A
included, while keeping C as a later extension.

## Fixture preparation contract

The authoritative source is the existing ignored
`artifacts/P7/natural/source.ogv`. Preparation must first verify the byte size,
SHA-1, and SHA-256 already pinned by
`tools/probes/natural_video_manifest.json`. A mismatch stops the package before
any derivative or device run is accepted.

Preparation uses a recorded ffmpeg version and explicit arguments. For each
window, the manifest records:

- stable task ID and role (`motion` or `frozen-control`);
- source interval and matched-pair ID;
- output path and SHA-256;
- codec/pixel format, 320×180 dimensions, 24 fps, 48 frames, and 2-second
  duration;
- audio absence and any explicit BT.709 interpretation inherited from the
  existing fixture process.

The validator reads media metadata independently and rejects a manifest whose
declared identity or pair relationship does not match the files. Preparation
outputs remain ignored artifacts; only the reproducible contract, validator,
tests, and bounded documentation are tracked.

## Task and scoring contract

### Semantic task

Each of the three motion windows uses the existing prompt:

`Describe what happens in this video in one brief sentence.`

Scoring reuses `natural-video-semantic-rubric-v1`: plural subject
`birds|swifts`, action `fly|flying|flight|in flight|soar|soaring`, and scene
requiring `sky` plus `blue|cloud|clouds`. Results are reported per window; a
failure remains a failure. Frozen controls are not scored with this rubric.

### Temporal contrast task

Each motion window and its frozen control uses the same prompt, fixed before
device execution:

`Are the birds moving across the sky or staying still? Answer exactly MOVING or STILL.`

The normalized answer is `strip().upper()`. A valid answer must equal exactly
one of `MOVING` or `STILL`; extra prose, both labels, neither label, or the
opposite label fails. Motion windows expect `MOVING`; frozen controls expect
`STILL`. The pair passes only when both members pass. The suite-level temporal
contrast passes only when all three pairs pass.

This exact-label rubric intentionally measures instruction following together
with temporal discrimination. The report must state that coupling; it cannot
be described as a pure perception metric.

## Device runner and lifecycle

The ignored standalone consumer installs the fixed model once, acquires one
lease, and executes nine requests in one process: three semantic motion runs
and six temporal contrast runs. Every request receives a fresh UUID. The
client drains between requests, closes once after the suite, removes the model
store entry, and rechecks all input hashes. Any setup, generation, identity,
drain, cleanup, or report-write failure makes the suite state `failed` while
retaining all completed raw rows.

The runner must not use picker, Photos, recording, network, or private media.
The host copies only the six prepared inputs and existing model directory into
the isolated App's Documents container.

## Validator and evidence contract

The tracked Python validator is pure and deterministic. It binds every row to
the task manifest and authoritative raw device report path/SHA-256, rejects
duplicate run IDs and missing/unknown task IDs, verifies exact run cardinality,
and writes sorted output only after all structural checks pass.

The summary contains:

- fixture preparation identity and manifest hash;
- backend/model/runtime/prompt identities;
- three semantic results and category counts;
- six temporal results, three pair verdicts, and suite verdict;
- observed durations without converting them into a benchmark sample;
- explicit limitations: same source/scene, exact-label instruction coupling,
  no human preference, no GPU/energy, no sustained resources, and no general
  temporal-quality claim.

If the device output fails any contrast, the summary records the exact raw
answer and expected label. The validator must never rewrite raw reports or
substitute a more permissive label set.

## P11 and documentation integration

A valid raw report and deterministic summary may be added as non-benchmark
evidence for `multimodal-apple`. Readiness source hashes and the grouped
manifest readiness fingerprint are refreshed, but the grouped benchmark
source list remains unchanged. Documentation distinguishes:

- `pass-bounded`: all three same-source motion/frozen pairs pass;
- `observed-failure`: at least one validly executed pair fails;
- `unverified`: execution or evidence identity is incomplete.

None of these states closes complete natural temporal quality, GPU/energy,
sustained resources, physical touch, in-background terminal timing, F08, or
P11 by itself.

## Verification and stop condition

Stop this package after:

1. preparation and validator negative tests cover source/hash drift, metadata
   drift, pair mismatch, duplicate/missing task IDs, wrong or ambiguous labels,
   and deterministic ordering;
2. the isolated consumer build succeeds and exactly nine physical-device runs
   are retained with raw identity and lifecycle evidence;
3. the validator summary is regenerated byte-for-byte;
4. focused tests, readiness validation, grouped deterministic regeneration,
   `git diff --check`, and protected-file hashes pass;
5. documentation reports the measured pass or failure without broadening the
   claim, and only tracked validator/tests/docs/manifest changes are committed.

If the device, model lease, fixture identity, or raw report cannot be verified,
record the blocker and leave the temporal task set `unverified` rather than
falling back to host-only output.
