# Apple MLXVLM Fixed-Image Benchmark Design

## Goal

为已经通过固定设备生命周期套件的 `ODAIKitAppleMLXVLM` 增加一组有界、可复核的固定单图质量、性能和资源证据。证据固定 SDK commit、模型/runtime revision、输入 hash、设备/系统和测量方法；不把合成红色方块的结果推广为自然图片质量结论。

## Scope and identity

- Backend: `mlx.smolvlm2-500m`
- Model: `mlx-community/SmolVLM2-500M-Video-Instruct-mlx`
- Model revision: `fa57db46815177fbdfd65cc85a2b3416a8332268`
- Runtime revision: `6e338e5df49424fc0db7c6d58184d622f92ed18a`
- Input: existing synthetic `artifacts/P9/fixture.png`, SHA256 `60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606`
- Prompt: `Describe this image in one brief sentence.`
- Output budget: 32 tokens

The benchmark runs only in the standalone MLXVLM consumer on an arm64 iOS 18+ physical device. It reuses the existing local model and fixture, performs no download, recording, file picking, or private-media access, and does not change the base iOS 15/macOS 12 package.

## Measurement contract

Each measured iteration creates a fresh backend/client so the backend's per-request model load remains visible and no chat state is shared. The runtime records the interval around `VLMModelFactory.loadContainer`; the consumer records time from `generate` admission to the first delivered `MultimodalTextUpdate` and to terminal success. A run is valid only if load, first-update, and total durations are finite, non-negative, and ordered within the total interval.

The suite performs one untabulated warm-up followed by five measured iterations. Five samples are deliberately a bounded development checkpoint, not the final P11 5+30 acceptance protocol. Raw per-iteration values remain in JSON; the report computes nearest-rank p50/p95 without merging different identity or measurement methods.

Process resident memory is sampled every 10 ms with `mach_task_basic_info.resident_size` from immediately before warm-up until the final measured iteration finishes. The report records baseline, peak, and delta with the exact method string `process-resident-mach-task-basic-info-10ms`. It is process RSS only: it is not Metal/GPU allocation, device-wide peak, energy, or a stable resource budget. Metal/GPU peak and energy remain `unverified` rather than receiving proxy values.

## Quality contract

The synthetic fixture has one inspectable property: a red square on a near-white background. Each output is preserved in the local artifact and evaluated by a narrow deterministic rubric requiring case-insensitive mentions of both `red` and `square`. Passing this rubric proves only that the fixed synthetic fixture produced a minimally relevant description on these runs. Natural-image description quality, OCR, counting, spatial reasoning, hallucination rate, and multilingual behavior remain `unverified`.

## Artifacts and validation

The device writes one raw JSON document containing frozen identity, environment, measurement methods, warm-up status, five measured iterations, RSS observation, explicit `unverified` fields, and the synthetic rubric. A repository tool validates this document and produces a summary JSON with nearest-rank p50/p95 for load, first update, total, and RSS delta. Invalid identity, missing samples, non-finite or misordered timings, a misleading GPU/Metal memory label, or an overclaimed natural-image status is rejected.

Host tests cover the validator/report rules. Swift host tests cover timing-event ordering with an injected runtime before the real runtime is changed. The device run is reported separately from host correctness. Independent review remains pending; author self-review cannot be labeled APPROVE.

## Non-goals

This package does not add natural images, short video, Android, KMP/Demo integration, binary distribution, model download, minimum-OS execution, Metal/GPU instrumentation, energy instrumentation, or P11-scale sampling. It does not close P9 or replace the earlier cancellation/reentry/lease/close suite.
