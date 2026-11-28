# F08 stop receipt telemetry and lifetime inspection

2026-11-14. Continue W6 general short-video understanding on the current iPhone 16 Pro Max target. This branch fixes diagnostic evidence only; no model inference, phone operation, quality-score change, dependency change or guard relaxation.

## Evidence and hypothesis

The cache256 host run stopped at 3,772,632,064 physical bytes, after 21/28 arms. Its independent stop receipt omits the three MLX fields already present in the triggering ResourceSample. The last persisted main-report sample is earlier and cannot establish the allocation breakdown at the stop. Fix this known information loss before another resource experiment. This is not a claim that missing telemetry caused excessive memory.

The consumer uses a fresh 28-layer KV cache per arm and calls clearCache after each container.perform returns. Inspect the actual preparation and model call boundaries before assuming retained per-case state, a CoreImage leak or dense attention.

## Work and acceptance

- [x] Freeze the completed cache256 branch and the original 2,471 protected files; preserve raw reports and candidates.
- [x] Extend the real Swift sampler receipt/exit fixture with distinct nonzero MLX values, legitimate zeros and explicit unavailable values. Verify RED on the old sampler for missing fields while existing emergency/normal behavior remains correct.
- [x] Create a new task-local sampler candidate. Valid stops serialize active/cache/peak from the triggering ResourceSample; sample failure emits explicit nulls. Keep guard thresholds, readMLX, periodic control flow, atomic writes and Darwin._exit unchanged.
- [x] Verify GREEN, including receipt-write failures, normal atexit, emergency no-atexit, boundaries and null-versus-zero. Mutation checks reject wrong mapping, resampling/defaults and fabricated error values.
- [x] Link/typecheck against the existing pinned MLX modules and iOS SDK without dependency resolution. Use a small real Metal fixture only if required to validate the actual serialization boundary; no model run merely to test receipt fields.
- [x] Inspect actual consumer and processor/model source plus existing time-series evidence. Record verified lifetime boundaries, remaining unknowns and the next bounded resource experiment; no untested lifecycle fix.
- [x] Update validation, handoff and W6 status, check hashes/syntax/links and remove only this branch's inactive rebuildable module cache. W6 remains open/not-ready.

## Semantics

The three MLX counters come from the already collected ResourceSample. Physical/task and MLX readings are sequential operations, not an atomic allocator transaction, and sampling does not capture every instantaneous peak. MLX active/cache and process physical/RSS remain separate metrics. A stop receipt never marks the main suite completed. No new helper or native invocation is needed.

## Execution ledger

Completed diagnostic-only branch: old12-case fixture7 pass/5 expected missing-field failures; new12/12;4 wrong implementations rejected; real pinned MLX Metal normal0/trigger70 and six triggering-sample fields exact; iOS sampler typecheck0. Original1132 samples yielded90 matched active/cache points with physical difference555745424B. Source inspection narrows the next step to actual preparation/lifetime boundaries without claiming a leak. No model or phone run. Final proof is recorded in the branch final-audit.json and final-file-freeze.json.
