> 2026-11-16 execution: the one authorized `w3-apple65-file-20261116-01` batch is consumed. Do not replay. See [device result](../../docs/validation/w3-apple65-device-20261116.md). The preparation-stage specification below is retained as history.

# W3 Apple 65 FILE probe candidate

This is an isolated, manually triggered SwiftUI iOS FILE-only evaluation alias for fixed run `w3-apple65-file-20261116-01`. It is preparation only: no device recognition, no physical 65 batch, no production acceptance, no scoring, and no budget or W3 completion claim is authorized or reported by this candidate.

## Scope and pins

- Bundle identifier: `io.ondeviceai.probe.w3apple65file20261116`
- Backend: `apple.speech.recognizer`
- Manifest SHA256: `99584dec071c0e8a3b47f510443e002d80058482cb48d580122a0af88c86a1fb`
- Input binding SHA256: `b3838b23136aa1be8c760915b565a2f192976ae3299631fcd3470f8df5334e88`
- Source base commit: `8537933d2340a2bf1e3c1f3aa97780bca5973780` (base only; final overlay is separate)
- Version/build in config: 1.0 / 1; the app reports actual bundle id, version, build, and OS from the installed bundle and process.
- The app does not hardcode the digest of `source-identity-manifest.json`. It reports the actual SHA256, parsed identities, selected-source count, boundary, and raw JSON text.

## Prerequisites and integration boundary

- XcodeGen and an iOS 15.0+ SDK with Swift 5.
- The existing local package at `../../apple` with ODAIKitCore, ODAIKitSpeech, and ODAIKitAppleSpeech products only.
- Accepted `Apple65Core.swift` compiled explicitly with this app file. `Apple65CoreChecks.swift` is intentionally not in the target because it contains host-check code.
- Frozen AudioFixtures folder at `../../.omx/artifacts/w3-apple65-probe-preparation-20261116/AudioFixtures`, containing `manifest.json` and `audio/<id>.wav` payloads.
- Parent-generated `source-identity-manifest.json` with exactly schemaVersion 1, runId, backendId, manifestSHA256, inputBindingSHA256, sourceBaseCommit, selectedSources with path/sha256 entries, and distributionBoundary `isolated-unsigned-probe-not-production`. This candidate intentionally does not author that file.
- Speech authorization must already be authorized on the device before a run. Preparation does not perform device authorization.
- No microphone usage key, microphone API, AVAudioSession, AiAi, download path, fallback, VAD, gain, trim, two-pass, scoring, or budget decision is included.

## Manual controls

- The app start only initializes UI state and displays status.
- `Speech authorization setup` is a separate explicit button. It may request Speech authorization only and never starts recognition after authorization completes.
- `Run fixed FILE alias` is the only run trigger. It refuses to start unless the scene is active and Speech authorization is already authorized.
- `Cancel` requests cancellation while cleanup remains awaited. Scene inactive/background also cancels an active task.
- A report for the fixed run already existing, even partial or failed, refuses replay. The app does not overwrite, delete, or retry that report after refusal.

## Reports

The report path is the app Documents directory under `W3Apple65FileReports/w3-apple65-file-20261116-01.json`. Initial, pre-request, post-request, and final writes are atomic. All 65 rows are initialized as `not-run`; before a request the row is transitional `in-progress` with `terminal=false`; terminal per-case statuses are `recognized-nonempty`, `completed-empty`, `typed-error`, `timeout-or-cancel`, and `not-run`.

Preflight validates the manifest digest, pins, paths, and every WAV before any backend/client creation. Each request revalidates its bundled WAV, and the same input is revalidated only after drain/close cleanup. A final full manifest plus 65-WAV verification runs before normal completion. Missing or changed input, report I/O failure, scene cancellation, and fatal setup state stop the batch with remaining rows left `not-run` where applicable. Ordinary typed ASR, platform, noResult, availability, and setup errors continue to the next case. A typed timeout remains typed and stops only when the task or scene is actually cancelling.

If the OS kills the app, there is no guarantee of a final report: persisted in-progress rows may remain incomplete, and replay is still refused. This is not resilience or termination recovery.

## Measurement boundaries

All 65 cases are measured sequentially with a fresh file-only `AppleSpeechRecognizerBackend(locale:clip.locale,timeout:30)` and a fresh recognition client. There is no warmup and no paced idle. Request admission uses monotonic uptime before input load and setup; whole-request wall time ends at recognition terminal or preparation error and excludes drain/close. Recognition duration brackets only the client recognize await, and is null when no recognition call was made.

Drain and close are always awaited after any backend/client creation, including setup failures and cancellation, with separate drainSeconds and closeSeconds and a cleanupCompleted flag. App PID RSS is sampled by an independent detached task at a nominal 100 ms interval from admission through cleanup, then cancelled and joined before the case becomes terminal. The report preserves timestamped optional-byte samples where nil means missing, sample count, total attempts, missing count, optional min/max bytes, window start/end, duration, interval, gaps, threshold, and leading/trailing gaps. There is no claim about systemSpeech memory, true peak, GPU, energy, thermal state, recognition quality, budget approval, or W3 completion.

## Input and dataset limitations

The fixed public dataset has 65 ordered inputs with 20 Mandarin, 20 English near, 12 English far, 9 annotation-negative, and 4 silence cases. There are 63 distinct PCM digests. The 9 annotation-negative cases were not listened in an audit. Qwen outputs were already exposed, so this is not a new blind heldout. Wave-module round trips and header-only WAV additions were handled by the input preparation lane; this adapter does not rederive waveform quality or dataset completeness.

## Build disposition

This candidate makes no build, install, signing, device, or recognition execution claim. Parent integration must generate the source identity file, run XcodeGen, build unsigned generic iOS with isolated DerivedData, inspect built resources and hashes, and decide separately whether any physical execution is authorized.
