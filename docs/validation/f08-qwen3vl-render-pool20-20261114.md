# F08 synchronous render autorelease-pool control: no observed benefit

2026-11-14. **The predeclared render-only hypothesis was not established. Do not adopt this change as a memory fix. W6/F08 remains open/not-ready.** Evidence root: `artifacts/verification/f08-qwen3vl-render-pool20-20261114/`.

## Single-variable control and result

The previous live VM summaries showed IOSurface resident growth while MLX active returned to baseline after each case. This experiment tested whether a pool scoped to the **synchronous bitmap render+existing clearCaches block** releases temporary objects earlier. The candidate copies `MediaProcessing.swift` and adds only that pool. Data allocation/ownership, MLXArray creation, decoder, filter math, frames, model, generation and async boundaries are unchanged. Existing CI cache disabling, cache clears and chunk/Metal pools stay intact. No pool crosses `await`.

The effect criterion was frozen in [the plan](../superpowers/plans/2026-11-14-qwen3vl-render-pool20.md) before candidate execution: at least128MiB less IOSurface growth **and**128MiB lower final IOSurface resident than previous vmmap20, with exact completed20 vectors/answers; both same-arm post-clear physical-growth deltas improving by128MiB as corroboration. This is a diagnostic criterion, not an approved product budget. Comparing the baseline to itself first gave the expected RED result with0 reduction.

| Measurement | Prior vmmap20 | Render-pool candidate |
| --- | --- | --- |
| Actual model inputs completed | 20 | 20 |
| Full output vectors | 40 | 40, byte-identical |
| IOSurface resident at four triggers (MiB, rounded) | 6.77 /76.7 /207.2 /267.8 | 6.77 /76.7 /203.7 /267.8 |
| IOSurface first→last growth | 261.03MiB | 261.03MiB |
| Surface regions | 44 /65 /131 /180 | 25 /63 /132 /182 |
| Same-arm second→last clear phys, baseline | 243.92MiB | 321.69MiB |
| Same-arm second→last clear phys, detail | 221.06MiB | 333.16MiB |

Final IOSurface resident and growth reductions are both0; the physical-growth reduction figures are negative. The candidate therefore failed all three memory-effect criteria. The single-run negative supports stopping this intervention, not a claim that pools can never help or that all image-lifetime interactions are excluded. Do not tune the threshold or broaden the candidate after seeing this result.

## Actual verification

Before any model inference, the new actual **model-free Metal processor28** completed exit0 with28 exact pixel hashes/tokens/grids/positions/delta and224 timestamp transports,170 ordered phase receipts/362 samples. The runtime sandbox actually denied weight reads and network; no model loaded in that process. Each return active0/clear cache0, same-arm warmup→last growth about5.75/5.84MiB, similar to the prior model-free observation. Receipt: `processor-parent-results.json`.

The sole new model process PID57733 completed20/exit0 in15.124s, thermal0→0. Same `mlx-community/Qwen3-VL-2B-Instruct-4bit` revision `9c4f5209e57b31f4b9dfba735de3fb983739c9cc` and original weight SHA, Metal only; same prefix20/order/eight frames/balanced512/online A/B/C→EOS/256MiB cache policy and original3.5GiB/thermal2/180s sampled guards. No6111/suffix or old-case score update. `parent-results.json` checks40 full finite151936-value vectors, original inputs/protocol/28KV and200 ordered phases/1,061 samples.20 answers and40 vectors exactly match prior vmmap20.

All20 post-return active readings equal1,781,949,568B and post-clear cache0. Sampled maximum phys3,744,123,856B, headroom13,972,528B (13.33MiB), not resource acceptance. Sampled cache reached299,958,278B despite the256MiB policy; the policy is not an instantaneous hard ceiling. Timing/peaks from two externally observed processes are not a controlled performance improvement/regression estimate.

Four actual non-retried `/usr/bin/vmmap -summary` calls succeeded on PID57733. All crossed phases; the final snapshot was next-case before-decode→before-prefill and includes graphics2.7G. Neither unchanged completed-row counts nor the after-clear trigger names establish idle boundaries. Rounded category lines, before/after progress and hashes are in `vmmap-analysis.json`; do not add VM/MLX/RSS/footprint metrics or infer a unique owner/leak from IOSurface.

## Failures and boundaries

The processor compiler returned0, then the first wrapper wrongly looked for a single `bin/mlx.metallib` file and asserted before processing/inference. Actual cached resource is `mlx-swift_Cmlx.bundle`; exact link recovery preserved the original wrapper/build receipt without recompilation. A separate r2 build wrapper used the correct bundle for the model. This is a local post-compile packaging assumption, not a model failure or retry. Exact failure/recovery is retained in `shader-link-recovery.json`.

No new helper/provider, dependency/download, global default/signing change, phone action or media capture occurred. Pinned runtime/production code and previous experiments remain unchanged. No CPU/Torch/Ollama model inference. Context7 is unavailable; boundary assertions use inspected pinned source and actual compiler/runtime evidence.

Next justified work is **object-lifetime evidence outside the already tested render block**, especially image acquisition and graph retention through the async consumer. Record retain/release or allocation evidence before another intervention; do not simply enlarge autorelease scopes or repeat cache/resolution sweeps. This negative branch does not address the independent general short-video content errors (phone16/36), continuous-phone thermal failure or formal product integration gates. Current evaluation device remains iPhone16ProMax.

Final preservation/process/queue checks and local parser negatives are recorded in `final-audit.json` and `parser-checks.json`; `final-file-freeze.json` freezes delivered evidence. Cleanup is limited to this branch's inactive rebuildable module cache after no-open-file validation. All raw/model/media/vector/binary/candidate evidence is retained. Primary acceptance is not independent review.
