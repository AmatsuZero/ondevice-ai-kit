# Qwen3-VL fixed14 decoding-detail diagnostic

> Execute inline with `superpowers:executing-plans`; preserve raw evidence. The existing user-authorized primary implementation and no-new-helper restriction apply.

**Goal:** Test whether increasing decoding detail changes answers on a fixed diagnostic set while retaining the existing model, temporal sampling and safety guards.

**Architecture:** Reuse the accepted AVFoundation exporter and Swift processor/model consumer. Change the exporter's maximum decoded dimension from 512 to 1280 only. The processor remains minPixels4096/maxPixels262144: measure its actual grid, pixels and visual-token counts rather than calling the decoded PNG size the model resolution. Baseline uses the exact original PNGs. Both arms use identical original requested/actual times, eight frames, questions, options, labels, timestamp protocol, grammar and generation settings.

**Tech Stack:** Existing Swift/MLX Metal runtime, AVFoundation, cached Python preprocessing/NumPy/tokenizers. No new dependencies, weights or production changes.

**Spec:** The single-hypothesis follow-up to `docs/validation/f08-qwen3vl-error-visibility14-20261114.md`; this is known-answer diagnostic evidence, not an independent benchmark.

## Constraints and review focus

- Use all six fine-detail-uncertain and all five visible-sufficient-cue cases from the frozen visibility audit. Add three original-correct controls, ranked by SHA256 of `detail14-control-v1:` plus case ID. Freeze all 14 and order before export or inference; no case replacement/output-driven tuning.
- Exclude video251/9676 and do not rerun the original36 benchmark. Report the paired diagnostic separately; original16/36 stays immutable.
- Require source SHA, requested times and eight actual times exact, baseline phone pixel SHA exact, official expanded text/positions correct, and increased actual spatial grid/visual tokens. A larger decoder alone is insufficient evidence.
- Single new host model process, all28 arms once, interleave paired baseline/detail in fixed order and alternate which arm is first. It is host feasibility/causal diagnosis, never phone resource or quality acceptance.
- Keep 3,758,096,384-byte footprint, serious-thermal and180s guards unchanged. On any stop, retain partial/stop evidence; do not relaunch or silently finish the suffix.
- Serialize GPU; check ComfyUI and Ollama empty immediately before processor/model launches. Model inference on MLX Metal only; CPU decoding/validation is permitted.
- Preserve prior2471 protected files and the preceding visibility audit snapshot; classify later living-doc changes explicitly. No new helper/native/provider invocations.
- No claimed internal root cause, independent labeling, generalized improvement, or budget approval from these known cases. A regression is as reportable as an improvement.

## Tasks

- [x] Freeze selection, protection snapshot and input policy before export; verify the original scores and selection provenance.
- [x] Export fixed original times with existing1280 exporter; prepare paired inputs with unchanged processor cap and validate exact times/baseline pixels, official positions and increased actual detail. Run tampered-time/question/label/frame/order/input checks before model launch.
- [x] Derive a task-local consumer changing only run identity, exact28 count and claim wording; freeze/build from cached packages. Run one Metal process with immutable input snapshot and unchanged guards.
- [x] Independently audit complete logits, grammar, EOS,28KV offsets, deltas, scores, resources and all original snapshots. If stopped, explicitly retain incomplete status and validate only completed rows.
- [x] Write report and update three living docs. Verify syntax/whitespace/links/hashes and remove only exact inactive task-owned rebuild caches after recording inventory; preserve unique binaries and evidence. Evidence finalization is checked by `final_audit.py`; this does not mark the stopped experiment complete.

## Execution ledger

- Plan frozen before decoding/inference. User continuation/direct-execution authorization applies; no permission handoff. New independent review remains unavailable under the current no-new-invocation restriction; final author audit must say so.
- Input preparation passed28/28,112 actual times exact,14 baseline phone pixel SHA exact and10 negative cases rejected. Actual THW increases18x32→24x42; visual tokens576→1008.
- Build89.228s passed. First wrapper failed before build because build-command.json already exists; same frozen command executed under distinct compile receipt, no source/model retry.
- Unique PID52977: one baseline complete, first detail prefill stops at3,791,358,976B/4474.441ms/thermal0; final139 SIGSEGV. Zero completed pairs, no detail answer, no relaunch.
- Ruling: Preserve frozen exit70-only checker; add a distinct crash-bound partial verifier for exact139/PID/stop/stack, without weakening completed28 requirement. Failure-evidence audit passes2 vectors; experiment remains incomplete. Single crash supports concurrent-exit risk, not unique causal proof.
