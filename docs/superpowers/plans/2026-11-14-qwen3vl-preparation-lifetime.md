# F08 model-free actual processor lifetime measurement

2026-11-14. The preceding [telemetry branch](../../validation/f08-qwen3vl-stop-telemetry-20261114.md) found matched MLX counters with increasing process footprint, without proving allocation ownership. Measure the actual preparation path before selecting an intervention.

## Frozen design

- Reuse all 28 original detail14 staged inputs in the existing alternating order, original timestamps, questions, expected pixels/tokens/THW/positions/delta. No label-based reselection or answer generation.
- One new host process, MLX Metal only, cacheLimit268435456B, existing pinned runtime/tokenizer and processor. Read only tokenizer/config/frames/manifest; do not load weights or instantiate a model/container. No phone, download, dependency change or provider/helper call.
- Run the actual processor.prepare and eval pixels/tokens/positions; verify every pixel SHA and protocol value against the pre-existing freeze. Return JSON Data only from the per-case async function.
- Observe phase samples before decode, after decode, after prepare, after evaluation/validation, after per-case function returns, and after clearCache. Save each row and phase sample to a durable JSONL file as well as the running report; periodic10ms monitor and independent stop remain active.
- Original3.5GiB/thermal2/180s guards apply; nominal start, empty ComfyUI/Ollama queues, one process, no retry/suffix. Missing or failed case means incomplete. New sampler is the already validated telemetry candidate, unchanged.
- No autorelease intervention in this first measurement. A rise or plateau will guide one later separately frozen control; neither proves the cause of the model-run guard, a true leak, iPhone behavior or quality improvement.

## Acceptance

- [x] Freeze inputs, metadata, runtime source/object/module identities and preceding completed branch; compile against cached objects only.
- [x] Verify a deliberate input-contract tamper is rejected without processing, then restore the exact frozen contract before the sole measurement. Validate expected protocol against the existing independent official checks rather than deriving expected pixels from this new run.
- [x] Execute the single declared process and retain original reports, per-phase receipts, stop/error state and process status.
- [x] Independently validate all produced rows and phases, exact input/protocol preservation, no model loading, physical/RSS/MLX separated; analyze per-case post-return and post-clear series.
- [x] Report the finding and its limits, preserve previous artifacts/protected files, clean only inactive rebuildable caches and leave W6open/not-ready.

## Execution ledger

Cached-object build0; altered-input preflight rejected before processing/output (top-level Swift throw133 retained), exact preflight0; actual runtime weight read denied. Sole new measure process80576 completed28/28 in3.215s, no model, thermal0→0;28 exact pixels/protocol,224 timestamp transport checks,170 phase/424 total samples, five altered-result checks rejected. Post-return active0 and post-clear active/cache0, same-arm warmed physical drift about6–7MiB. Not the original model growth, no causal owner/phone acceptance. Final proof is retained in final-audit.json and final-file-freeze.json.
