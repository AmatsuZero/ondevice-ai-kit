# F08 weak image lifetime: cross-case objects survive in model path

2026-11-14. **New direct object evidence:** all tracked source and normalized `CIImage` objects remained observable after each model case and cache clear, accumulating16 per case to320 after20 cases. The model-free processor path did not retain prior cases at the next case's observations. This narrows the lifecycle investigation; it does not identify the retaining owner, establish an unreclaimable leak or close W6.

Evidence root: `artifacts/verification/f08-qwen3vl-image-weak20-20261114/`. Plan: [weak-object observation](../superpowers/plans/2026-11-14-qwen3vl-image-weak20.md). Original iPhone16ProMax16/36 and continuous thermal failure remain unchanged. No phone action or quality update.

**Counting clarification:**320 is the number of nonnil weak-registration slots (160 source/160 normalized registrations), not a verified count of320 distinct object identities. The tracker does not record/deduplicate ObjectIdentifier, so different registrations may refer to the same instance. The cross-case-retention finding means prior cases' weak slots remain nonnil; it does not require a unique-instance count. References to objects/counts below use this registration-count meaning.

## Observation rather than memory intervention

The candidate derives from original vmmap20, **not** rejected render-pool20. Original `MediaProcessing.swift` was copied byte-identically: same CI cache settings/clears and render behavior, no new pools. In the consumer, each loaded source image is registered weakly. Qwen3's original normalized-image expression is assigned to a local, registered weakly and returned unchanged. Existing phase writes additionally append scalar weak snapshots. All changes are isolated and reversible through `observation-derivations.json`; no SDK/default/signing/dependency changes.

`ImageLifetimeTracker` stores weak `CIImage?` slots, case/kind labels and locks. Snapshots serialize only counts/labels/PID. Reading a weak reference can briefly keep it alive for the check. It does not give atomic retain/release or VM observations. Neither a nil weak object nor a nonnil one proves backing-surface reclamation or an allocation owner. Intermediate filter objects and unobserved internal caches are outside the two object classes tracked here.

The actual optimized Swift tracker fixture uses real `CIImage` objects. An intentionally strong-slot version fails with `tracker retained image after pool drained`/exit1; the weak version passes explicit-live/dead-after-scope, per-case origin/counts, append without truncation, scalar/phase transport and empty-case rejection/exit0. This demonstrates that the accepted observer itself does not provide the strong reference needed to keep these images alive. The strong test variant is retained separately and is not compiled into either actual processor/model target.

## Two actual paths

| Observation | Model-free processor28 | Model prefix20 |
| --- | --- | --- |
| Process | PID84968, exit0 | PID88658, exit0,14.927s |
| Pixel/protocol matches | 28/28;224 timestamps | 20/20 original inputs |
| Weak registrations | 448 | 320 |
| Phase/weak snapshots | 170/170 | 200/200 |
| Maximum observed live objects | 16 | 320 |
| After-prepare current source/normalized | 8/8 | 8/8 |
| Earlier-case source/normalized at later phase snapshots | 0/0 | Every prior case remains8/8 |
| After-return current source/normalized | 8/(7–8) | 8/8 |
| After-clear live objects | 0–16 current-case only | 16,32,…,320 cumulative |

In the model-free path, some objects remain alive at the immediate function-return/clear sampling boundary, but **no earlier-case object** is alive at later snapshots. Do not describe function return as an instantaneous release guarantee. Its second→last same-arm post-clear physical growth is7,946,240B (baseline7.58MiB)/6,193,152B (detail5.91MiB), with return active0/cache-clear0. The actual runtime sandbox denied weight reads/network, and no model was loaded. `processor-parent-results.json` and `processor-image-results.json` retain all evidence.

In the full model path, all previously observed source **and** normalized images remain alive at all later phase snapshots. Final320 alive consists of160 source and160 normalized image objects, not320 proven unique IOSurfaces. `model-image-results.json` retains per-case/stage current/earlier counts and the final group snapshot. Five altered scalar receipts (wrong observed count/PID, missing earlier group, wrong phase, duplicate group) were rejected without inference.

## Model/vector and VM cross-check

Sole new model process is the same `mlx-community/Qwen3-VL-2B-Instruct-4bit` revision `9c4f5209e57b31f4b9dfba735de3fb983739c9cc`, unchanged weight SHA and pinned MLX Swift Metal runtime. Same original prefix20/order/eight frames/balanced512/online A/B/C→EOS/256MiB cache policy and3.5GiB/thermal2/180s sampled guards; no6111/suffix. Thermal0→0. `parent-results.json` verifies40 full finite151936-value output vectors, input pixels/tokens/grids/positions/delta,28KV and200 phases/1,034 resource samples. All20 answers and40 vectors match original vmmap20 byte-for-byte.

Post-return MLX active is always1,781,949,568B; post-clear cache0. Same-arm second→last clear physical growth is289.22/294.06MiB. Sampled maximum phys3,745,778,616B leaves12,317,768B (11.75MiB) under the diagnostic guard; this is not product resource acceptance.

Four actual, non-retried live vmmap summaries succeeded on PID88658. IOSurface resident6.77/76.7/207.2/271.9MiB, region25/63/133/182; final dirty267.8MiB. All snapshots cross phases. The final before-decode→before-prefill capture includes graphics2.7G and cannot be treated as an idle retained GPU measurement. Rounded VM quantities, MLX counters and physical/RSS measurements remain separate metrics; object counts cannot be converted into a unique byte attribution.

## What this changes next

Pinned `ModelContainer.perform` awaits a closure through `SerialAccessContainer`/`AsyncMutex`. The inspected source stores model context/lock/waiter continuations, not an image history or a saved processing closure. No new owner is proven by that source inspection. The complete model case creates source images and graph objects outside the existing per-token/chunk pools; all-frame processing includes a synchronous region with no `await` in its body. These are concrete boundaries to investigate, without wrapping a pool across an async suspension or changing model math.

Next predeclared control should distinguish source-image creation from normalized graph creation, using the weak counts as the primary release observation and original exact pixels/vectors as correctness gates. A successful boundary change must reduce cross-case surviving objects **and** measure memory effects; object release alone is insufficient. Do not merely enlarge the already failed render-only pool or claim `clearCache()` releases CI images. General short-video content quality and phone continuous thermal behavior remain separate acceptance gates.

## Provenance and limits

Processor build's copied wrapper accidentally listed the prior render-pool plan as a supplemental build input. Actual new plan/selection and exact source/binary hashes were recorded before processing; the original receipts remain untouched. A distinct model build r2 fixes that metadata and freezes the correct plan; no processor rebuild/rerun. `build-metadata-correction.json` records the discrepancy. The model capture r2 additionally requires successful actual weak-count/tracker acceptance before launch. No new shader failure, source changes during builds, model retries or provider calls.

All builds returned0 with the existing Device.setDefault deprecation warning. No downloads/dependencies/global config/signing/default/device/media-capture changes; MLX Metal model only. Context7 is unavailable; the explanation uses inspected pinned source and actual compiler/runtime evidence rather than fabricated documentation. This is primary validation, not independent review or a benchmark.

Final source/process/queue/protected hashes and preservation checks: `final-audit.json`/`final-file-freeze.json`. Remove only exact own inactive rebuildable module cache after lsof; preserve all old raw/weights/media/vectors/candidates/binaries and prior negative results. W6 remains open/not-ready.
