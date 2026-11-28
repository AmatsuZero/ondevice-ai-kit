# F08 fixed-prefix20 model lifetime attribution

2026-11-14. Continue general short-video understanding/W6, current target iPhone16ProMax. Prior model-free processor28 completed without reproducing the approximately530MiB footprint increase observed at matching MLX counters in the model run. Instrument the actual model consumer before selecting a memory intervention.

## Pre-registered design

- Sole new host Metal model process, new runID/output, not a restart/suffix of the interrupted28 run. Execute exactly the first20 of the existing frozen28 arms, preserving order. This is ten complete pairs, ending875-baseline;6111 and later inputs excluded before inference. No selection based on new outputs.
- Model/revision/weight SHA and pinned runtime unchanged: Qwen3-VL-2B-Instruct4bit9c4f5209, original8 timestamps/frames, baseline/detail576/1008 visual tokens, processor cap262144, balanced512, temperature0, maxTokens32, actual A/B/C→EOS, complete151936 logits and28KV offset evidence. Both arm cacheLimit268435456B. No model/prompt/numerical change.
- Original3.5GiB, thermal2 and180s sampled guards plus new validated stop-telemetry/safe_exit candidate unchanged. Nominal start and empty ComfyUI/Ollama queues. Stop at a guard/contract error; retain incomplete state, no retry/suffix or new quality score.
- Add durable phase observations only: before-perform; before-decode; after-decode; after-prepare; after-input-validation; before-prefill; after-prefill (logits/cache evaluated); after-generation; after-perform-return; after-clear. Record actual ResourceSample values, explicit phase JSONL synchronized before proceeding. Normal row report remains original; clearCache remains after report writing. Do not add pools, clears, synchronization of MLX work or change math merely to improve the result.
- Diagnostic overhead and different process timing prevent causal comparison to prior runs. Within this run, observe the first boundary where growth appears, active/cache on return and after-clear. Phase samples and process memory do not prove allocator ownership, a true leak or an instantaneous peak.

## Acceptance and stop conditions

- [x] Freeze previous completed artifacts/protected files and declared first20 identity before building/running. Review exact derivation diff; sampler matches accepted source, model body unchanged except prefix/run metadata/phase observations.
- [x] Verify phase-log writer/sample transport and failure reporting using a small real Swift fixture with injected memory data; retain positive/negative evidence without loading a model. Build from existing objects only and freeze source during build.
- [x] Sole declared model execution; no re-run to obtain a completed measurement. Persist launch/process/report/rawvectors/phase/stop evidence.
- [x] Independently check exact prefix inputs, complete finite logits/grammar/KV/protocol, phase order/sample membership and stop resource fields if present. Analyze paired post-return/post-clear and prefill deltas without summing physical/RSS/MLX or substituting cross-time maxima.
- [x] Update handoff/W6/report, audit prior raw and2,471 protected files, syntax/links and exact inactive cache cleanup. Keep16/36 quality and iPhone continuous-thermal failure unchanged. W6open/not-ready until actual quality/resources/product gates pass.

No new helper/native/provider, dependency/download, phone action or production/default/signing change. The next intervention, if justified, requires a new frozen experiment and source/behavior verification.

## Execution ledger

Phase writer fixture passed append/sample transport/error checks; cached build0; unique modelPID92478 completed20/exit0.40 full vectors/200 phases/1316 samples verified and five altered receipts rejected with exact copied positive control. Old host20 answers/40 vectors identical. Return active1,781,949,568B and post-clear cache0 for all20; same-arm warmed physical drift268–290MiB, sampled guard headroom7,484,440B. Pinned chunk and Metal pools already present; next new run allocation-region observation before selecting intervention. No phone, no new quality score; W6open/not-ready. Final proof retained in final-audit.json and final-file-freeze.json.
