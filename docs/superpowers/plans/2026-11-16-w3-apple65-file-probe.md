# W3 Apple 65 FILE Probe Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans task by task. CBC hy3 supplies candidates; the primary integrates and executes acceptance. Native children remain read-only review only and no failed quota lane is retried.

**Goal:** Prepare and unsigned-build a separate Apple FILE probe for the already fixed 65 public inputs. No device recognition or capture is authorized by this preparation.

**Architecture:** Keep the frozen three-file quality and forty-call sustained probes untouched. A new SwiftUI alias consumes a hash-pinned manifest and WAV payloads, calls the existing production AppleSpeechRecognizerBackend/SpeechRecognitionClient sequentially by each case locale, and writes per-case terminal status and costs. A Foundation/CryptoKit core validates inputs and is tested independently of Speech.

**Tech Stack:** Existing Swift 5/iOS 15+ local Apple package, Foundation/CryptoKit/SwiftUI/Speech, existing Mach RSS sampler, XcodeGen; no new dependencies.

**Spec:** docs/validation/w3-ios-closure-contracts-20261116.md and docs/validation/w3-apple-65-input-binding-20261116.json.

## Global Constraints

- Full R4/R5/P1–P4/W3 remains open; preparation/build is not physical or production acceptance.
- Fixed runId w3-apple65-file-20261116-01; backend apple.speech.recognizer; 65 ordered inputs/20 Mandarin/20 English near/12 English far/9 annotation-negative/4 zero. 441.102 seconds, 63 distinct PCM digests. Qwen outputs already exposed; not new blind heldout.
- Frozen WAV manifest SHA256 99584dec071c0e8a3b47f510443e002d80058482cb48d580122a0af88c86a1fb; resources .omx/artifacts/w3-apple65-probe-preparation-20261116/AudioFixtures. Only WAV headers added; all raw PCM/ref/locale/sample counts unchanged; 65 wave-module round trips passed.
- Do not edit production Swift, packages/locks, old probes, normal Apps/models, signing/auth/global configuration. No network downloads/microphone/AiAi/device enumeration, installation or recognition. GitLab Wiki EOF lane stays stopped until an actual route/server change.
- Manual explicit action only; refuse replay if this fixed run's report already exists, no onAppear recognition. Separate explicit Speech authorization setup may exist, never auto-invoked or used to start recognition. No microphone APIs or usage key.
- Validate full manifest and every WAV before any recognition. Safe single-component ASCII id paths audio/<id>.wav. Input SHA/bytes/PCM data checked again after drain/close.
- Fresh file-only backend/client for each case and its explicit locale; availability must report available/offline. Timeout 30 seconds. No fallback/VAD/gain/trimming/two-pass.
- Recognized-nonempty/completed-empty/typed-error/timeout-or-cancel/not-run are execution statuses independent of CER/WER. Preserve public transcript only on success; typed1110 remains typed error with no synthetic empty hypothesis. Continue ordinary ASR typed errors to collect all rows; stop fatal input/report-write/background/cancellation failure and retain remaining not-run. Do not award a quality/budget pass.
- Use monotonic recognition duration and request wall (before input load/setup to terminal). Drain/close timed separately and always awaited. App PID RSS count/min/max/missing sampled during each request and cleanup; report sampling window/nominal interval/gaps. No system Speech/true peak/GPU/energy/thermal claim or new approved budget.
- Preserve run/source/input identity, per-row progress and final report atomically. Post-close input verification required; sampler must cancel AND join. Scene deactivation cancels work and cleanup remains awaited.

## Review Focus

Wrong/corrupt manifest or WAV must prevent Speech calls; mixed locales never use the device locale; typed errors never become zero-error hypotheses; an existing report prevents repeat; background cancellation and failure must retain cleanup evidence and not-run rows.

### Task 1: Validate fixed inputs (host-testable)

**Files:** probes/ios-apple65-file/Apple65Core.swift; probes/ios-apple65-file/Apple65CoreChecks.swift (standalone host executable excluded from App target).

**Interfaces:** Apple65Clip Codable with id/cohort/locale/reference/samples/pcmSHA256/wavPath/wavBytes/wavSHA256. Apple65Manifest Codable with schemaVersion/runId/backendId/inputBindingSHA256/sampleRate/channels/sampleWidth/cases. Apple65Core.sha256(Data)->String; loadManifest(Data, expectedSHA256:String) throws -> Apple65Manifest; validateWave(Data, clip:Apple65Clip) throws.

- [x] Get a helper test-only candidate plus a deliberately unimplemented interface stub kept only in the artifact directory. Test valid real65 manifest/WAV, wrong whole-manifest digest, duplicate ids, wrong locale/cohort counts, reference rules, bad path, wrong sample count, changed WAV PCM/header, missing bytes. Do not synthesize recognition outcomes.
- [x] Compile and run tests against the stub; retain expected failing assertions for valid inputs (not accidental syntax errors). Then request implementation candidate. No implementation before observed RED.
- [x] Review source, run same tests against implementation; independently round-trip all65 files. Keep all old inputs/protected content unchanged.

### Task 2: App execution adapter and unsigned build

**Files:** probes/ios-apple65-file/W3Apple65App.swift, project.yml, README.md, source-identity-manifest.json.

- [x] Request bounded helper App/config/docs candidate after Task1 interfaces are accepted, supplying exact production APIs and existing sampler. No invented SDK API.
- [x] Review fatal/ordinary errors, replay/scene/authorization guards, all await cleanup and sampler join; preserve typed platform codes. Host test only deterministic core, do not claim it proves physical lifecycle.
- [x] XcodeGen then generic iOS unsigned build using existing local package, no model/runtime downloads, no signing or installation.
- [x] Inspect actual App Info.plist/resources/manifests/65 WAV payloads and source hashes. New identity required; old probe identity is not reused.
- [x] Commit only this isolated probe, plan and evidence receipt; preserve unrelated dirty files via exact staging. Raw prompts/errors/candidates and accepted App retained; remove only exact dispensable task-owned build intermediates after checking writers/references.

### Task 3: Execution handoff

- [x] Publish a concrete prepared batch/candidate identity and unresolved product/physical gates. No new Apple batch, authorization prompt or device operation is started by this plan. A genuinely new 65 FILE run is a separate execution decision under the current iOS handoff; existing Android single-file authorization is unchanged.
