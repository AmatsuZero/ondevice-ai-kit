# F08 weak image-object lifetime observation

2026-11-14. Previous turn was progress: render-only pool effect was tested and rejected with exact pixels/vectors. Do not adopt that pool or broaden it. Continue ordinary solo primary-authorized diagnostics; no helper/native/provider invocation.

## Question

Does image-graph object retention visible to weak references persist beyond actual processor/model return, while IOSurface regions grow? Observe **source `CIImage` objects and the final normalized `CIImage` graph objects** in original code. A weak CIImage becoming nil does not prove its backing surfaces/driver resources are reclaimed; a non-nil image does not identify the owner or a true leak. No intervention is made.

## Frozen scope

- New isolated candidate from accepted original vmmap20 (not rejected render-pool20). Keep original MediaProcessing render/cache/pools. In ModelSuite and no-model consumer, register each source image via weak-only tracker. In Qwen3VL.preprocess(image:resizedSize:), return exactly the original expression value after registering it weakly. No pixel/math/frame/cache/guard changes.
- Record counts grouped by originating case and source/normalized stage at existing phase points. Tracker stores only weak references plus scalar labels; no image, array, closure capturing an image, or strong pointer is serialized. Lock protects diagnostic records. Reading a weak reference briefly retains it during a count; observations can perturb timing and are not atomic VM compositions.
- First actual Swift weak-lifetime/file-append tests: assert live while explicit strong refs remain, dead after an enclosing autoreleasepool drains and refs end; demonstrate append without truncation and per-case counts. A deliberate strong-tracker mutant must fail the dead check. These prove diagnostic behavior, not real CI owner attribution.
- Next no-model Metal actual processor28 with exact old pixels/tokens/positions/grids/delta/224 timestamps and170 phase logs, sandbox denies weights/network. Require8 source and8 normalized registrations per case; allow live count observations to be either outcome, never demand a convenient answer.
- Only after processor parity: at most one original model prefix20 with same model/revision/SHA/eight frames/order/balanced512/grammar/cache256/original3.5GiB thermal2 elapsed180s guards, same phase points and four non-retried progress-triggered live VM summaries. No6111/suffix/phone/quality update. Before launch verify nominal start/empty queues/no other target process.
- Accept output20/40 full vectors/28KV/200phases and compare exact prior answer/vector identities; any mismatch preserved, no claim of equivalent generation. Analyze weak counts at after-prepare, after-perform-return and after-clear, including cumulative surviving objects and whether earlier cases remain. Retain full scalar snapshots and actual VM phase context; no arbitrary lifetime success threshold, causal claim or added cleanup.
- Final audit original2,471 protected plus previous render-pool final snapshot, unmodified original MediaProcessing, minimal derivations and final exact inputs/binaries/output evidence. Update report/handoff/W6/pitfalls only with verified findings. Preserve old16/36, phone thermal failure, old139/70 reports. Remove only own inactive rebuildable cache after lsof.

No new dependency/download/default/signing/global setting/media capture. MLX Metal model only, CPU analysis permitted. Context7 unavailable, use actual pinned source/compiler behavior with documented limits. Stop this branch after this one observation or a verified failure; choose subsequent action from actual counts, not a preselected pool fix.

## Acceptance

- [x] Freeze/reversible observation-only source derivation and real Swift tracker tests/mutant.
- [x] Actual no-model processor28 exact parity and lifetime count transport.
- [x] Sole model prefix20/full-vector parity and actual four live summaries.
- [x] Lifetime interpretation/report/final audit/cleanup, W6open/not-ready preserved.

## Execution ledger

Actual CIImage tracker strong mutation fails exit1/dead-after-scope, weak implementation exit0; scalar append/phase/per-origin behavior passed. Actual no-model28/448 registrations/170 snapshots, all old-case alive counts0; model PID88658 completed20/exit0/40vectors identical/200 snapshots, all16 current plus earlier-case images survive cumulatively to320. Five altered observation receipts rejected. Four actual live summaries succeeded; no same-process restart or device action.

Source/default/math/guards unchanged, original MediaProcessing exact. New evidence supports source-versus-normalized-graph synchronous lifetime control; no owner/leak proof or fix claimed. Report `docs/validation/f08-qwen3vl-image-weak20-20261114.md`, final preservation checks/cleanup in branch final-audit.json/cleanup.json. Original16/36 and phone thermal failure/W6open remain.
