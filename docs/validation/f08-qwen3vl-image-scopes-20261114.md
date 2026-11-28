# F08 image creation scopes: normalized graph boundary reduces host retention

2026-11-14. **A useful local fix boundary is now demonstrated:** scoping synchronous normalized-image creation releases both tracked image categories after each model case and reduces observed IOSurface growth. Source-image initialization alone did not help. Select the smaller graph-only candidate for subsequent iPhone verification; this is not production adoption or W6 completion.

Evidence: `artifacts/verification/f08-qwen3vl-image-scopes-20261114/`; [predeclared plan](../superpowers/plans/2026-11-14-qwen3vl-image-scopes.md). The original phone16/36, continuous thermal failure and formal adoption/integration/resource-budget gates remain unchanged. Current device scope remains iPhone16ProMax.

## Frozen three-arm control

The previous weak observation found320 nonnil registration slots after model20. They are **registrations, not deduplicated unique object identities or surfaces**. This experiment predeclared three modes before inference: source-only, graph-only, both. One shared build uses an invocation-local `F08_IMAGE_SCOPE_MODE`, retained with each command. Source mode wraps only `CIImage(contentsOf:)`; graph mode wraps the existing normalized-image expression and weak observation in a synchronous autoreleasepool. Both is the predetermined factorial arm. No pool spans await and no raw Objective-C pool push/pop is used. The original `MediaProcessing.swift`/render/cache/chunk pool logic remains byte-identical to the original weak branch; rejected render-only pool was not reused.

Every arm first completed a distinct actual no-model Metal processor28 run, with exact pixels/tokens/grid/positions/delta/224 timestamps,170 phase/weak snapshots and448 registrations. Weight reads/network were actually denied in each runtime sandbox. Only after all three passed were the three model processes executed serially, once each, in source→graph→both order. There were no model/processor reruns.

Actual Objective-C autoreleased-token fixtures independently verified each helper's effective pool boundary, value/error transport and cleanup. A distinct mutant that omits both pools fails both lifetime checks. These characterize the helper, not CoreImage ownership. The original weak tracker/strong-mutation evidence remains preserved and its source is unchanged.

## Actual results

| Model arm | Source-only | Graph-only | Both |
| --- | --- | --- | --- |
| Model PID | 24717 | 28108 | 28513 |
| Exit / completed | 0 /20 | 0 /20 | 0 /20 |
| Process time | 11.850s | 11.118s | 11.196s |
| Full vectors identical to original vmmap20 | 40/40 | 40/40 | 40/40 |
| Final source / normalized nonnil weak slots | 160 /160 | 0 /0 | 0 /0 |
| Earlier-case slots nonnil after return/clear | present | none | none |
| IOSurface resident at four triggers (MiB, rounded) | 6.77 /76.7 /207.2 /267.8 | 6.77 /44.1 /44.1 /6.77 | 6.77 /44.1 /44.1 /10.7 |
| Same-arm second→last clear phys, baseline | 299.92MiB | 90.78MiB | 55.56MiB |
| Same-arm second→last clear phys, detail | 278.34MiB | 28.80MiB | 16.14MiB |
| Sampled phys maximum (bytes) | 3,744,812,008 | 3,432,074,168 | 3,442,903,896 |

All three kept original inputs/protocol,40 full finite151936-value vectors,28 fresh KV offsets/grammar,200 ordered phases/weak snapshots. All60 answers/120 vectors match original vmmap20 byte-for-byte. Every post-return MLX active reading equals1,781,949,568B and every post-clear cache reading is0. Model/weight/revision remains `mlx-community/Qwen3-VL-2B-Instruct-4bit`, `9c4f5209e57b31f4b9dfba735de3fb983739c9cc`; unchanged eight frames/order/balanced512/online A/B/C→EOS/cache256 and3.5GiB/thermal2/180s sampled guards. Thermal starts/ends nominal. No6111/suffix or quality-score update.

The predeclared criteria required no prior source/normalized slots alive at any after-return/clear point, at least128MiB reduction in final IOSurface resident and its growth versus original vmmap20, plus at least128MiB less growth in both same-arm physical deltas and exact20 vectors/answers. **Graph and both met all criteria; source failed the lifecycle/memory criteria.** Graph reduced final surface/growth nominally261.03MiB and physical-growth deltas153.14/192.27MiB versus the reference. Both adds a boundary not needed for the observed release; smaller graph-only is the next validation candidate. Exact criteria/results: `scope-effect-results.json`.

## What is and is not explained

The effective boundary is graph creation, not source initialization or the already-tested inner bitmap render block. This supports accumulated temporary references associated with synchronous graph creation in this model consumer. Scoping creation can release source images too because a graph references its input. It does not identify the exact internal retaining object/framework defect, prove an unreclaimable leak, or map each registration to a distinct IOSurface. Some residual physical-growth deltas remain, and memory peaks still contain prefill/driver/framework effects.

All twelve live VM summaries actually succeeded, four per exact target PID, without retries. They remain progress-triggered snapshots that may cross stages and perturb execution; the first source-only snapshot already contains graphics2.6G and the final captures may contain next-case prefill. Do not call them idle composition measurements, add VM/MLX/RSS/footprint quantities, or use the process times as a speed comparison. This is one serial process per mode, not randomized statistical evidence.

`selection-for-phone-validation.json` chooses graph-only. The actual tested form is the shared helper with mode=graph. `selected-graph-only.diff` is a review artifact showing the direct boundary on the weak-instrumented Qwen3 source; **that direct form has not been compiled/run yet**. Before phone work, derive/freeze the exact isolated candidate and verify it independently; do not present a diff as device evidence or touch the ordinary App/models.

## Retained local failures

The first source processor wrapper opened an outer stdout file under the arm before execution freeze; the launcher froze its then-empty bytes and wrote its terminal JSON later. Strict offline acceptance correctly rejected this changed **non-input wrapper stdout**. Original freeze/raw/error are retained; a distinct r2 checker verifies that one exact final JSON and original empty digest while checking all other frozen files unchanged. No processor rerun. Later outer wrappers capture output in memory and write terminal receipts only after the child ends. Recovery: `freeze-live-log-correction.json`.

The first source model offline checker still expected arm-local selection, while the new shared-binary layout stores it at shared root. It failed with FileNotFoundError after the actual model completed. A distinct r2 checker reads the same already-frozen shared selection, keeping original checker/failure unchanged; no model rerun or input change. `checker-shared-selection-correction.json` records this layout correction. These are evidence-packaging errors, not model/quality failures.

Final preservation/process/queue/source/AST/link checks and exact own inactive module-cache cleanup are retained in `final-audit.json`, `final-file-freeze.json` and `cleanup.json`. No helpers/providers, dependency/download, global defaults/signing, phone action or media capture; model inference only MLX Metal. Context7 unavailable; reasoning uses pinned source/compiler/runtime evidence. Primary acceptance is not independent review.

**Next:** isolated iPhone graph-only verification of image release, resource behavior and continuous operation with unchanged guards; formal resource/quality acceptance remains separate. Original general short-video content16/36 cannot improve from a host output-preserving memory boundary alone. W6 remains open/not-ready.
