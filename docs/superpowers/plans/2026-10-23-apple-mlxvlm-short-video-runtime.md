# Apple MLXVLM video runtime continuation implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans inline solo, test-first and author review; user explicitly excludes subagents and routine approval handoffs.

**Goal:** Wire bounded video through the existing session and prove fixed synthetic device lifecycle separately.

**Architecture:** One image/video session task/model lease, private request enum. Runtime prepares video with MLXVLMVideoInputAdapter and directly generates from its LMInput, retaining owned snapshot until producer join.

**Tech Stack:** existing Swift6/XCTest/AVFoundation/MLX/XcodeGen/devicectl/Python unittest, no new dependencies.

**Spec:** `docs/superpowers/specs/2026-10-23-apple-mlxvlm-short-video-design.md`, Runtime continuation decision; adapter design is binding for temporal/input semantics.

## Global constraints

Same fixed model fa57db46815177fbdfd65cc85a2b3416a8332268 and runtime6e338e5df49424fc0db7c6d58184d622f92ed18a; locke67e9859… unchanged. Current regular/nonsymlink<=32MiB H.264 BT709 one zero-start video-track profile, 0<duration<=5s, sides<=1920/pixels<=2073600, eight distinct frames<=512; short two-frame clips explicitly rejected. Prompt<=8192UTF8 and output1...512, temperature0. Enhanced inference arm64physicaliOS18+ only. No upstream edits/download/private media/recording/signing changes/push; retain protected files/artifacts/failures. Independentreviewpending and5/12/P9active.

## Review focus

- Image/video share busy and close barrier even during callback cancellation and late errors.
- Snapshot survives model prepare/generation until producer join; failure and cancellation clean owned copy.
- Actual rational times and copied-byte hash bind metadata; clip duration and sample span remain distinct.
- Direct prepared LMInput is consumed without upstream frame reselection or image fallback.
- Normal/old flags keep opt-in behavior; strict report rejects inferred device/quality claims.

### Task 1: Shared backend/runtime lifecycle (original Task3a)

**Files:** existing MLXVLMBackend.swift, MLXVLMGenerationSession.swift, LocalMLXVLMRuntime.swift; new MLXVLMVideoMetadata.swift and MLXVLMVideoLifecycleTests.swift; existing test-only runtimes updated for video requirement.

**Interfaces:** internal MLXVLMRuntime.generateVideo(directory:request:observer:update:) async throws -> String; session.generateVideo(_:update:) -> MultimodalTextOutput. Public backend conforms MultimodalVideoGenerationBackend. Metadata.Time(value:Int64,timescale:Int32), snapshotSHA256, requestedTimes, actualTimes, clipDuration, sampledSpan, widths/heights. Observer videoDecoded(metadata), videoPreparationStarted, videoPrepared(durationMs:Double,frameCount:Int), videoSnapshotClosed(removed:Bool).

- [x] Write new fake-runtime tests for exact video request and model identity/ordered updates; bidirectional image/video busy; cancellation gated in decode/prepare/generate phases joins runtime and holds lease; close joins and rejects both; late success/error cancellation precedence and reentry; budget/media-token input rejection without load; empty/error outcomes release slot. Existing image tests unchanged semantically. Missing session video API is expected RED.
- [x] Run profile run-host.py with filter MLXVLMVideoLifecycleTests and preserve RED. Add video runtime requirement, test-only defaults for old image fixtures, session enum dispatch/common task admission, backend conformance and same unsupported-device guard.
- [x] Add real snapshot metadata test checking exact29FPS times, byte hash, duration/span and dimensions; missing metadata is expected RED. Implement immutable metadata using owned copy bytes only.
- [x] Implement Local video route: snapshot/defer close+actual absence event, model/config load, adapter prepare in model.perform, direct Prepared.input TokenIterator; extract existing stream consumer into private shared helper preserving explicit producer await. Cancellation checks at decode/load/prepare/iterator boundaries; map profile/adapter errors to invalidInput and runtime errors to runtimeFailure.
- [x] Run focused GREEN then full enhanced host (real tokenizer environment enabled), full tools with explicitRuby; fresh unsigned generic iOS consumer build using fixed cache/all3offline flags. Build proves iOS route compiles, not model execution. Author review exact staged local commit, update validation/roadmap/handoff and original Task3 checkpoint.

### Task 2: Opt-in video suite and strict evidence (original Task3b)

**Files:** new examples/ios-mlxvlm-native/VideoFixtureSuite.swift, existing App/project.yml/generator copy list; tools/mlxvlm_video_report.py and tests/test_mlxvlm_video_report.py.

**Interfaces:** VideoFixtureSuite.run() async writes sdk-mlxvlm-video.json, explicit --sdk-mlxvlm-video-suite only. CLI rawJSON [--output path]; strict schemaVersion1/runId/sourceCommit/device/OS/PID/identity/hashes + three ordered runs success/cancelled/reentry, plus closed rejection/lease/remove/source integrity. Each run records copied hash/exact times/clip/span/decode+prepare+model-load/update/generation-return/cleanup evidence. SinglePID, requested grid and all8actual times, nonempty success/reentry, firstupdate cancel then drain, cleanup after return required. Quality/resource/minOS explicitly unverified.

- [ ] Write validator positive fixture + mutations (missing/invalid identities, hash, rational time count/order/grid/span, empty output, PID change, missing cancel/drain/cleanup/close/lease, inferred quality), run missing-module RED. Implement strict parser and summary; no inferred values. Extend generator tests to require copying video suite before implementation, preserve RED then GREEN.
- [ ] Implement suite and flag using hash-verified29FPS fixture/model, shared client and observer with locked metadata collector; cancel from first update, await task and drain before samePID reentry, awaitclose before store.remove. Persist failed report with stable error type. No natural media/automatic normal launch.
- [ ] Run full tools and Python compile/diff-check; fresh independent generic iOS build. Commit source before device execution so sourceCommit is exact.
- [ ] Recheck connected authorized device; signed build using existing team/profile only, install project App, provision verified synthetic fixture+fixedmodel, launch explicit flag/sourceCommit, retrieve rawJSON. Strict validator must pass actual raw; preserve all failures and device gap if it cannot. No host/build substitution.
- [ ] Author review and exact local evidence/docs commit; mark original Task3 only if device gates pass. Original Task1 short-samples remains partial. Next Android contract parity only after native gates.

### Task 3: Recovery documentation (original Task4)

- [ ] Update validation/roadmap/handoff/current plans with actual commits/counts/paths/pass-fail-unverified; preserve all prior gates and independentreviewpending.
- [ ] Verify protected2hash/index, fixedlock/checkouts, canonicalmodelresource, doc links and diff-check; exact local commit, no push.
