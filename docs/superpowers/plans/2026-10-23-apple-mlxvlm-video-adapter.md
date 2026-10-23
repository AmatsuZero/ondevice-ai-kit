# Fixed sampled-video adapter implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline, solo. User explicitly requires no subagents and automatic local execution; preserve logs/workspaces and report independent review pending.

**Goal:** Preserve every real sampled frame and exact temporal metadata without pinned upstream resampling.

**Architecture:** Internal adapter uses the fixed public video image transforms and message generator to assemble LMInput directly. It does not enter public request/generation paths.

**Tech Stack:** Swift6, XCTest, CoreImage/CoreMedia, fixed cached MLXVLM/MLX/Tokenizers, Python tools, Xcode.

**Spec:** `docs/superpowers/specs/2026-10-23-apple-mlxvlm-video-adapter-design.md`

## Global constraints

Model fa57db46815177fbdfd65cc85a2b3416a8332268; runtime6e338e5df49424fc0db7c6d58184d622f92ed18a; lock unchanged. No downloads/dependencies/upstream edits/timestamp rewriting/false endpoint frames/public API/device inference/signing changes/remote writes. Existing foundation only; original two protected files unchanged. Frames1...8, dimensions1...512, duration0<d<=5s; numeric finite epoch0 requested/actual times in[0,d), actual strictly increasing. Exact config512/2048/64/fps1/max64/mean+std0.5; prompt1...8192UTF8bytes, no media tags. Fixed ceil-second prompt formatting; exact times retained separately.

## Review focus

- Nonuniform timestamp boundaries must not skip or duplicate real pixels (Task1).
- A single frame/nonzero first time must retain time and report span0 (Task1).
- Invalid CMTime/config/oversized frame must fail before tensor assembly (Task1).
- Cancellation inside synchronous template must prevent a prepared result (Task1).
- Real template special tokens must align with all frame tensors (Task2).

### Task 1: Internal already-sampled video preparation

**Files:** create `apple-mlx/Sources/ODAIKitAppleMLXVLM/MLXVLMVideoInputAdapter.swift` and `apple-mlx/Tests/ODAIKitAppleMLXVLMTests/MLXVLMVideoInputAdapterTests.swift`.

**Interfaces:** consumes snapshot.Frame, exact config, MLXLMCommon.Tokenizer. Produces `MLXVLMVideoInputAdapter.prepare(frames:clipDuration:prompt:configuration:tokenizer:) async throws -> Prepared` with input/timestamps/clipDuration/sampledSpan. Throws internal invalidInput on invalid budgets/time/config; CancellationError preserves priority.

- [x] Write tests: snapshot29FPS gives8 pixels/THW/timestamps includingindex6; each output slice equals independent public CI normalization of corresponding input; snapshot24FPS parity with pinned processor; prompt has8 frame blocks/512 image labels, clip2s and sample span~1.724s differ. Single frame actual1/4s with clip1/2s gives exact timestamp and span0. Reject empty/>8/nonmonotonic/out-of-clip/invalid/epoch/oversized frame/config mismatch/oversized ormedia-tag prompt. Pre-cancel and template-triggered cancel return CancellationError.
- [x] Run focused cached host, preserve missing-symbol RED.
- [x] Implement interface in dedicated source, no resampling selection or image tiling; use fixed video formatting before template and actual clip duration. Guard exact config before tuple access. Check cancellation around synchronous phases.
- [x] Run focused then enhancedhost GREEN, author scope review; exact-stage local Task commit with spec/plan.

### Task 2: Real fixed tokenizer compatibility

**Files:** modify `apple-mlx/Sources/ODAIKitAppleMLXVLM/LocalTokenizerLoader.swift`; create `apple-mlx/Tests/ODAIKitAppleMLXVLMTests/MLXVLMVideoTokenizerTests.swift`.

**Interfaces:** unchanged LocalTokenizerLoader.load(from:) now available to macOS tests. Consumes Task1 adapter. Opt-in `ODAI_VIDEO_MODEL_DIRECTORY` supplies local verified assets; absent means explicit XCTSkip, not passing model evidence.

- [ ] Write tests and run RED for missing host loader. Load only local tokenizer; no model/container/weights. 24FPS adapter and upstream tokens/pixels exactly match (same rounded duration); 29FPS adapter has8frames and512 actual image IDs versus upstream7/448; prompt retains user question and assistant suffix. Assert exact metadata retained.
- [ ] Remove iOS-only compilation guard without changing loader implementation. Verify required tokenizer files against manifest before run; run focused actual-tokenizer tests with local env, no skips.
- [ ] Run complete enhancedhost and tools with explicitRuby, generic iOS unsigned consumer build from fresh output, all3 fixed dependency flags. Verify no lock/checkout drift. Author scope review and exact-stage local Task commit.

### Task 3: Durable acceptance boundary

**Files:** update short-video validation, ROADMAP, next-session handoff, old short-video plan checkpoint and this plan; local `.superpowers/sdd/2026-10-23-apple-mlxvlm-video-adapter/` ledger/review/checks/logs.

**Interfaces:** consumes Task1/2 evidence only; original upstream red remains failed.

- [ ] Document exact RED/GREEN/count/build/tokenizer scope and remaining profile/device/API/review/binary gates. Record configuration/protected hashes, cache status and unchangedlock.
- [ ] Verify links/diff/index/protected state; separate author final review; precise local docs commit. No P9 completion/count promotion or remote sync.

## Execution checkpoint

Design and plan author-reviewed against user constraints; tasks pending. No independent APPROVE. Automatic inline execution is explicitly requested, so routine skill approval prompts and subagent/reviewer/cleanup instructions yield to the user's instructions.
