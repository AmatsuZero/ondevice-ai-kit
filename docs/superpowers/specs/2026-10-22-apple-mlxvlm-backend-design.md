# Optional Apple MLXVLM Backend Design

## Goal

为已经交付的 `ODAIKitMultimodal` 增加一个独立的 Apple MLXVLM 增强包，用固定版本的 SmolVLM2 500M checkpoint 在 iOS 18+ 执行单图描述；基础 Swift package 的 iOS 15/macOS 12 下限和依赖图保持不变。

## Fixed route

- Model: `mlx-community/SmolVLM2-500M-Video-Instruct-mlx`
- Model revision: `fa57db46815177fbdfd65cc85a2b3416a8332268`
- Runtime: `MLXVLM`, `mlx-swift-lm` revision `6e338e5df49424fc0db7c6d58184d622f92ed18a`
- `mlx-swift` 0.32.3 and `swift-transformers` revision `b38443e44d93eca770f2eb68e2a4d0fa100f9aa2`
- Minimum runtime OS: iOS 18.0; no model download or mutable remote configuration.

## Package boundary

`apple-mlx` is a separate Swift package with product `ODAIKitAppleMLXVLM`. It depends on the local `apple` package products `ODAIKitMultimodal` and `ODAIKitModelStore`, and on the exact MLX packages used by the existing probe. The root `apple/Package.swift` does not gain MLX dependencies, so Core, Multimodal, and every existing product remain buildable at their current deployment targets.

`MLXVLMBackend` conforms to `MultimodalGenerationBackend`. Its `backendId` is `mlx.smolvlm2-500m`; availability checks only OS/architecture, the already validated lease identity, and closed state. Availability never loads a model, reads image bytes, installs assets, or runs inference.

## Ownership and lifecycle

The public initializer receives an acquired `ModelLease` and validates it against the bundled canonical `SmolVLM2Model.manifest`. An internal generation session accepts an injected runtime so host tests can exercise lifecycle behavior with tiny real ModelStore leases. The lease remains retained for the entire load, image preprocessing, stream consumption, and generation-task join. `close()` first cancels the active generation, drains the underlying MLX stream/task, then closes the lease exactly once. A returned `MultimodalTextOutput` is a value and remains valid after close. No backend fallback is performed.

The default loader uses `VLMModelFactory.shared.loadContainer(from:using:)` and a local typed `TokenizerLoader` adapter to the pinned Tokenizers API. Each request prepares one structured user message with `GenerateParameters.maxTokens = request.maxOutputTokens`, decodes the bounded immutable image snapshot and passes `.ciImage(...)` with its EXIF orientation, accumulates text snapshots, and retains the low-level `generateTask` handle until actual termination. Each request has fresh KV state; no chat history is retained. Cancellation uses `CancellationError` consistently with the existing native client; an ordinary backend error remains an ordinary error after the drain.

## Model/manifest contract

The adapter accepts only the verified P9 manifest identity and `backendId`; the local `ModelStore` remains responsible for path safety, exact file set, hashes, leases, and compatibility. The model id is mapped to the safe local `ModelRef` used by `ModelStore`; the upstream Hugging Face id/revision stay in the manifest metadata. No probe-only verifier is copied into the SDK.

## Testing and evidence

Unit tests cover availability without loading, manifest mismatch, output-budget propagation, cumulative ordered updates, cancellation/drain/close ordering, repeated close, and lease retention. A compile-only consumer proves the separate package can be imported without importing MLX into the base package. A real-device gate reuses the already verified local model directory and `artifacts/P9/fixture.png`; it records model/runtime/input hashes and terminal status, but does not download, record audio, or read private media. Device success is separate from unit/build evidence, and peak-memory/quality/Android/KMP/Demo gates remain open.

## Explicit non-goals

This package does not add video input, Android inference, KMP/Demo integration, model installation/download, quality scoring, or a new model family. Those remain later roadmap packages.

## Source review and implementation ruling

At the pinned upstream revision, `ChatSession.streamMap` creates an unstructured producer and does not expose its task. `clear()` acquires the cache mutex, but an immediately cancelled consumer can race the producer before it acquires that mutex. Therefore the implementation uses the upstream documented `generateTask` tuple (stream, Task) through `ModelContainer.perform`, explicitly cancels and joins that task, and does not rely on `clear()` as a join. Source: `Libraries/MLXLMCommon/Evaluate.swift` generateTask documentation; `ChatSession.swift` streamMap/onTermination; `Utilities/SerialAccessContainer.swift`.

Image admission is local regular nonsymlink PNG/JPEG only, encoded bytes <=20 MiB, dimensions <=4096 per side and <=16,777,216 pixels; prompt UTF-8 <=8192 bytes, output budget 1...512. Input is copied into bounded Data and decoded from that snapshot, so later caller file mutation cannot change the running request. Inputs with multiple frames are rejected. Native image decoding and Metal inference require a real device acceptance gate.
