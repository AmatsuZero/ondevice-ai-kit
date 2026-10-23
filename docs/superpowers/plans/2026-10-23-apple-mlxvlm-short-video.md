# Bounded Apple MLXVLM short-video implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans inline, solo, task-by-task. This is the next implementation package; none of its tasks was executed by the preceding source-feasibility package.

**Goal:** Safely characterize the fixed video processor, then deliver a bounded native Swift video request with honest device evidence.

**Architecture:** Gate public API work on actual bounded decoding/processor compatibility. Add an opt-in video backend protocol and an overload sharing the existing image client's lifecycle. Keep Apple video work within the existing enhanced package/session.

**Tech Stack:** Swift6, XCTest, AVFoundation, cached MLXVLM/MLX, Python unittest, XcodeGen/devicectl.

**Spec:** `docs/superpowers/specs/2026-10-23-apple-mlxvlm-short-video-design.md`

**Execution checkpoint (2026-10-23):** Task1 partially implemented; internal snapshot and host characterization exist, host22/22, tools126/126 and unsigned generic iOS build pass. The required8-frame semantic gate **failed** for actual29FPS input (8→7frames). Default characterization asserts the pinned known failure; this does not pass integration. Do not execute Task2/3 until a separate project-side temporal-adapter design resolves this gate without upstream edits or fabricated timestamps. Task1 external-reference/HDR/offset/short-clip and iOS-device coverage remain open; no Task is marked fully complete.

**Later adapter checkpoint (supersedes the next-step instruction above):** Independent adapter design/plan `2026-10-23-apple-mlxvlm-video-adapter*` executed as `5be1b57`/`c9ef8ce`: project-side direct video preparation preserves all8real29FPSframes and exactCMTime; actualfixedtokenizer2/2(no skips),fullhost30/30,tools126/126,freshgenericiOSbuild passed. Upstream7framebehavior remains unchanged and failedoriginalgate retained. Only internal preparation is validated; Task1 profile/iOSdecode coverage remains open and Task2/3 remain unstarted. Do not implement the old `.frames` runtime route unchanged; eventualruntime must consume the separately designedadapter after its remaininggates. Next independentwork: snapshotprofile/iOSdecode, no video modelinference yet.

**Latest profile/native checkpoint:** `81ea4df`adds4negativeprofiles;host34/34 andtools126/126. HostednativeiOSsnapshot/profile12/12 andadapter6/6 executed onphysicaliPhone16ProMax/iOS27.0.1;adapterusesByteTokenizer,no weights. Actualfixedtokenizer2/2remainhost-only. Originaltool-hosteddeviceexit70 andintermediatehostlink/constraintfailuresretained. Current8-unique-sampleprofile rejects2frameclip; shorterclip supportisstillnotimplemented. Ruling: pureSwiftTask2clientcontractisindependentofsamplercoverageandmaynowproceedafteradaptergate,withoutclaimingalloriginalTask1scopecomplete;no productionbackendconformsorvideo inferenceuntilseparateruntimegates. Task3mustconsumeMLXVLMVideoInputAdapter.Prepared.inputdirectly;theoldUserInput.framesrouteisobsolete.

**Latest Task2 checkpoint:** pureSwift typed video contract/client complete: missingAPI RED exit1, Multimodal22/22, baseApple469/469, enhancedhost34/34(no skips), tools126/126, strictSwift6typecheck exit0, independentunsignedgenericiOS publicAPIcompileprobe BUILD SUCCEEDED exit0. ProductionMLXVLMBackend remainsimage-only; Task3unstarted. Allpreviouscheckpointsarehistorical. Next: refineTask3runtimeplan to consumeadapter.Prepared.inputdirectly; no old.framespath. Task1short-samplesupportremainsopen; independentreviewpending.

## Global constraints

No new dependencies/downloads/upstream edits; exact current model/runtime/Package.resolved. Base iOS15/macOS12 and Android API26 unchanged, enhanced real inference arm64 iOS18+ device only. H.264 SDR, one zero-start video track, local regular nonsymlink input<=32MiB, finite duration0<d<=5s, <=1920 each side and <=2,073,600 pixels, at most8 frames<=512 each side. Prompt<=8192 UTF8 bytes, output1...512, temperature0. No KMP/Android implementation, natural media, remote writes, signing changes or protected-file edits in this package.

## Review focus

- External-reference movie metadata must not cause AVFoundation to read media outside the private snapshot.
- `.frames` re-sampling and first/last timestamp span must be measured, not assumed.
- Cancelled decode/generation must join before temporary input and model lease cleanup.
- Legacy backend availability must not imply video support or trigger image fallback.
- Image and video requests must share busy/close barriers on client and backend.

### Task 1: Bounded native snapshot and processor characterization

**Files:** create `apple-mlx/Sources/ODAIKitAppleMLXVLM/MLXVLMVideoSnapshot.swift` and `apple-mlx/Tests/ODAIKitAppleMLXVLMTests/MLXVLMVideoSnapshotTests.swift`; add fixed synthetic fixture/test utilities under the same Tests directory. Keep processor experiment in `.superpowers/sdd/2026-10-23-apple-mlxvlm-short-video/` until its results justify production integration.

**Interfaces:** `MLXVLMVideoSnapshot.load(_ url: URL) async throws -> MLXVLMVideoSnapshot`; owns private copy, decoded frames, actual/requested timestamps and clipDuration; `close()` removes only owned temporary files after decoding joined. Internal Swift types only; no public product promise in this Task.

- [ ] Write tests for valid real H.264 decoding and expected uniform samples, symlink/directory/invalid/reference-container rejection, encoded bytes/duration/dimensions/codec/track rejection, later source mutation independence, decode cancellation and owned cleanup. Build fixture locally, no download.
- [ ] Run focused tests using the existing cached host harness (consult backend ledger) and record real missing-implementation RED.
- [ ] Implement fd-based bounded private copy, strict profile, native generator sampling strictly before duration, cancellation handler plus join, finite timestamp/transform checks. Assert returned sampled-frame identities; throw on missing/duplicate/nonmonotonic frames.
- [ ] Run focused tests GREEN. Execute a fixed-cache processor characterization on a supported native target: feed actual `.frames`/`.targetFrames(n)` to the pinned `SmolVLMProcessor`, assert processed frame count/order/identity and reported sample span. Preserve exact argv/raw results. A build-only or source-text assertion does not pass this step.
- [ ] If re-sampling/time semantics fail, record the failure in validation and stop downstream tasks; no upstream edit, timestamp rewriting or unapproved alternative strategy.
- [ ] Author scope review, verify protected hashes/index, exact-stage local Task commit.

### Task 2: Typed video contract and shared client lifecycle

**Files:** modify `apple/Sources/ODAIKitMultimodal/MultimodalContracts.swift`, `MultimodalGenerationClient.swift` and their existing tests.

**Interfaces:** `MultimodalVideoRequest(videoFile: URL, prompt: String, maxOutputTokens: Int = 128)`; `MultimodalVideoGenerationBackend: MultimodalGenerationBackend` with `generateVideo(_:update:)`; `MultimodalGenerationClient.generate(_ request: MultimodalVideoRequest, backend:update:) -> MultimodalGenerationResult`. Existing image interface is unchanged. These are the exact types Task3 consumes.

- [x] Write real fake-backend tests: invalid video request values; image/video share busy; cancelled video awaits drain; close awaits query and video; out-of-order/late update suppressed; legacy backend rejects video with `invalidInput("selected backend does not support video")` without invocation or fallback; explicit backend selection honored.
- [x] Run `rtk proxy swift test --package-path apple --filter Multimodal`; record missing API RED.
- [x] Implement the new value/protocol and overload, factoring admission/run only as necessary to share one operation. Preserve all image code semantics and closed/cancelled precedence.
- [x] Run focused and full base Apple tests GREEN; author scope review and exact-stage local Task commit. Do not reuse old binary consumer evidence as new API distribution evidence.

### Task 3: Enhanced runtime and independent video consumer

**Files:** modify existing `apple-mlx` backend/session/runtime, add video lifecycle tests; extend `examples/ios-mlxvlm-native/` with opt-in video suite and its spec/generator copy list; add `tools/mlxvlm_video_report.py` and `tools/tests/test_mlxvlm_video_report.py` for strict device evidence.

**Interfaces:** Task2 video request/protocol; Task1 snapshot; existing ModelStore lease/LocalTokenizerLoader/generateTask. `MLXVLMBackend.generateVideo(_:update:)` shares session with generate(image). Explicit consumer launch flag `--sdk-mlxvlm-video-suite`; normal launch and existing flags keep behavior. Report CLI takes raw JSON path and optional `--output` summary path.

- [ ] Write RED tests for backend image/video mutual exclusion, cancellation during decoding/prepare/update, producer join before lease release, reentry and close; write validator mutations that reject missing identity/hash/timestamps, empty output, different PID, absent cancellation/drain/cleanup gates and inferred quality.
- [ ] Implement video session routing with same model lease and active task, input ownership through final join; explicit targetFrames, fixed model processor, existing temperature/output budget and update relay. Preserve image errors/availability and host unsupported-device behavior.
- [ ] Implement consumer synthetic fixture lifecycle and strict raw validator. Include runId, sourceCommit, PID/device/OS, model/runtime/fixture hashes, actual/requested timestamps, clip duration vs sampled span, decode/prepare/generation phases, success/update-cancel/samePID reentry/close/lease results; natural quality/Metal/energy/minOS remain unverified.
- [ ] Run targeted host and full tools tests with `RUBY=/Users/daubert/.rvm/rubies/ruby-3.4.11/bin/ruby`, Python compile, diff-check. Generate independent consumer and build generic iOS unsigned with matching SourcePackages and `-disableAutomaticPackageResolution -skipPackageUpdates -onlyUsePackageVersionsFromResolvedFile`.
- [ ] Recheck the existing authorized device, install/run only the project consumer with existing signing; provision only hash-verified model/synthetic fixture; collect raw results and run strict validator. Preserve failed attempt logs. Device failure is a gap, never substitute host tests.
- [ ] Author scope review, exact-stage local Task commit.

### Task 4: Recovery checkpoint

**Files:** update `docs/validation/apple-mlxvlm-short-video.md`, ROADMAP and next-session handoff, this plan and local progress/final-review.

- [ ] Record actual counts, commands, hashes, commits, characterization and host/device outcomes separately. Leave Android/KMP, natural quality, binary, minimumOS and independent review explicit.
- [ ] Recheck fixed lock/canonical resource/protected hashes and index, docs links and diff-check; separate author final review and local docs commit.
- [ ] Next independent package is Android video contract parity and then KMP/Demo video input, only after native gates pass. Do not close P9 or promote the 5/12 count from native progress alone.
