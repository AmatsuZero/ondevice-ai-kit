# W8 TTS Sustained Resource Probe — RUNBOOK

Isolated standalone candidate. Helper writes only the three owned files
(`W8TTSSustainedApp.swift`, `RUNBOOK.md`, `DELIVERY.md`) in the candidate cwd.
**The helper does NOT build, sign, run, or claim any test run.** Integration,
device run, signing, acceptance and result collection are owned by Main/Parent.
No run is requested or triggered by the helper.

## Purpose (W8 stable-capability distinct sustained characterization)

A *distinct* sustained create/play/close TTS resource probe. It does **not**
overwrite, pool, or rerun the existing fixed 5-run benchmark
(`f11-speech-tts-resource.json` / `ExistingTTSProbe.swift`). It uses a new
protocol, a new report file, and a new button.

## Protocol freeze (declared BEFORE measurement)

- **20 cycles**, each **>= 30 s monotonic elapsed** (measured end-to-end) ⇒
  **>= 600 s total paced run** (not 10 minutes of continuous active speech;
  speech is brief, the rest is idle).
- **Fresh REAL `AppleAVSpeechBackend` + `SpeechSynthesisClient` per cycle**;
  both are closed on every path.
- **One initial warm-up, excluded only on the first cycle** (not counted in the
  40 measured requests).
- **TWO sequential measured fixed-text speaks per cycle** using the same explicit
  en-US voice chosen **once** at run start.
  - Fixed text: `"OnDevice AI Kit iOS speech probe"` (public, fixed; no audio
    persistence, no mic/ASR/Photos).
  - Total **40 measured requests** (20 × 2). Successful requests are counted
    **separately** (`successfulRequests`); failed rows are preserved, not dropped.
- **en-US requires exact availability**; if no `language == "en-US"` voice exists,
  the run fails closed. **No arbitrary locale fallback.**
- **Idle pacing is derived from the remaining duration after work/close, not a
  fixed 30 s.** `requestedIdleMs` = `max(0, 30 - workElapsed)` where `workElapsed`
  is the measured wall time of the cycle's speaks + close-RSS capture, taken from
  `systemUptime`. `actualIdleMs` is **measured** with `systemUptime` sampled
  *immediately before* and *immediately after* the `Task.sleep` await — the
  computed remaining duration is **never** written as the measured idle.
- The **full cycle duration** (`fullCycleMs`, end-to-end from cycle start through
  the idle wait) is recorded **after** the wait. `completedCycles` is incremented
  **only after the entire cycle (including the idle wait) has finished**. Each
  completed cycle row must prove `fullCycleMs >= 30000`; a cycle below 30 000 ms
  fails the run. Completion is asserted only when **exactly 20 cycles / 40
  successful requests**, **every full cycle >= 30000 ms**, and **total >= 600 000 ms**.
- No p95 / budget pass is invented. `budgets: false`, `productionReady: false`.

## Sampling (reused unchanged f11 sampler)

- `mach_task_basic_info.resident_size` sampled ~every 10 ms via a detached `Task`
  **per request**.
- Per request records: `sampleCount`, `rssStartBytes`, `rssEndBytes`,
  `rssSampleMaxBytes`, `memoryMethod`.
- **Sampling nil (start/end nil or zero samples) ⇒ fail-closed, measurement
  incomplete, run never marked completed.**
- The sampler `Task` is `cancel()` + `await value` on **every** path.
- No GPU or energy measurement is implemented; sampled RSS is explicitly *not*
  a true peak.

## Foreground / lifecycle (running batch)

- `UIApplication.shared.applicationState == .active` is required **before and
  after each speak** and **at each cycle boundary**.
- **Lifecycle observation for the running batch:** `NotificationCenter` observers
  for `UIApplication.willResignActiveNotification` and
  `UIApplication.didEnterBackgroundNotification` are installed at run start and
  **removed on every exit** (success, failure, early return) via `defer`. Either
  event sets a **sticky** `lifecycleEventOccurred` flag.
- **Idle timer held during the measured batch (auto-lock guard):** at run start
  the prior `UIApplication.shared.isIdleTimerDisabled` value is saved, then set
  to `true` so the device does **not** auto-lock during this one user-triggered
  ~600s batch. The **exact** prior value is restored on **every** exit (success,
  failure, early no-voice / report-write failure) in the same `defer` that removes
  the observers. No global auto-lock permission or setting is changed — only this
  measured run holds the timer disabled, and no playback or run is started
  automatically. Manual lifecycle changes (`willResignActive` / `didEnterBackground`)
  still set the sticky `lifecycleEventOccurred` flag independently, so a real
  lock/background event during the batch still fails the run rather than passing.
- The sticky flag is checked **after every await**, including after each speak,
  after the per-cycle idle wait, after `client.close()`, after the per-close +1 s
  RSS wait, and after the final process-RSS +1 s wait. Once the flag is set during
  the batch, the report **must not** become `completed` even if the app returns to
  the foreground — it is reported as a terminal `failed` with
  `failReason: "lifecycle-event-during-batch"`.
- When the sticky flag fires in the **`.success`** branch immediately after
  `runCycle` (an otherwise-successful cycle row that must be appended as failed),
  the **actual attempted and successful request counts** for that row are recorded
  before breaking, exactly like the `.failure` branch — so the partial row is not
  undercounted. The normal-path counting is skipped on this branch (it breaks out
  of the loop), so counts are **never duplicated** on the normal path.
  `completedCycles` is unchanged (only whole >=30s cycles that fully finish count).
- Observers are dispatched on `.main` and mutate state via `MainActor.assumeIsolated`
  (MainActor-safe; no `UIApplication` sampling off-main).
- `client.close()` is supplied as `async` returning `Void` (no throwing
  wrapper, no fabricated error). It is awaited on all success/failure paths, and
  the sampler is cancelled+awaited on all paths.
- Partial honest failed report is persisted; a report-write failure is never
  recorded as success.

## Close RSS (captured immediately after each close)

- For **each actual `client.close()`**, `closeRSS` is captured **immediately
  after** the close and again **+1 s later**, with a monotonic offset
  (`closeOffsetMs` = uptime-at-close − run `startUptime`, in ms). These live in
  each cycle row as `cycles[].closeRSS` (`immediateBytes`, `after1sBytes`,
  `closeOffsetMs`).
- Nil `immediateBytes`/`after1sBytes` ⇒ `close-rss-incomplete` ⇒ terminal failed
  (incomplete, never relabeled as success). No new fabricated throwing close API.
- The **final process RSS** (`finalProcessRSS`, `immediateBytes`/`after1sBytes`)
  is sampled once after all cycles (immediate + +1 s). It is reported under a
  **distinct** key and is **not** relabeled as per-close RSS. Old measurements are
  not renamed to imply a different capture time.
- **Nil final process RSS ⇒ failed, never completed.** If either the immediate or
  +1s `finalProcessRSS` sample is `nil`, the run is marked terminal `failed` with
  `failReason: "final-process-rss-incomplete"` **before** the completion decision
  (any earlier failure reason is preserved). The raw `nil` fields remain `NSNull`
  in the report — no `0` is invented. Per-cycle `closeRSS` nil already failed via
  `close-rss-incomplete`; this closes the gap where a nil final process RSS could
  otherwise pass the completion gate.

## Report schema (pretty JSON, atomic)

Saved to `Documents/w8-tts-sustained-report.json` on **start, each cycle
partial, each cycle completion, failure, and end**.

Fields: `schemaVersion`, `kind`, `runId` (single batch UUID), `PID`,
`actualOS`, `device`, `backendId`, `selectedVoice`, `language`, `textSHA256`,
`sourceCommit` = `"PARENT_ATTESTED_NOT_EMBEDDED"`, `lifecycleEventOccurred`
(bool), `elapsed` (`startUptime`, `totalMonotonicMs`, `globalWallWindowMs`,
`requestedCycleIdleMsTotal`, `actualCycleIdleMsTotal`), `status`
(`running`/`failed`/`completed`), `terminal` (bool), `completedCycles`,
`requests` (total observed rows), `successfulRequests` (actual successes),
`cycles` (20 rows; each has `cycle`, `warmupExcluded`, `requestedIdleMs`,
`actualIdleMs`, `fullCycleMs`, `closeRSS`, `requests` (2 ⇒ 40 unique
`requestId`s), and `status`/`failReason` on failure), `finalProcessRSS`
(`immediateBytes`, `after1sBytes`), `microphoneStarts: 0`, `memoryMethod`,
`budgets: false`, `productionReady: false`, `failReason`.
No per-cycle private data is emitted.

Failed measured request rows are **preserved** in the report together with the
partial cycle; successful requests are counted separately. A report-write failure
at **any** checkpoint sets `status: "failed"` and is never later overwritten to
`completed`. Failed warm-up / no available voice / JSON write failure / short
sampling / failed voice / lifecycle event / cancelled close RSS can never be
recorded as success.

## Modules & build prerequisites

- Allowed imports: `Foundation`, `CryptoKit`, `Darwin`, `AVFoundation`,
  `SwiftUI`, `UIKit`, `ODAIKitSpeech`, `ODAIKitAppleSpeech` (ODAIKitCore is
  available transitively for the real APIs used).
- Real APIs: `AppleAVSpeechBackend` (`@MainActor`, `backendId =
  "apple.avspeech"`, `speak`, `close`), `SpeechSynthesisClient` (`actor`,
  `speak`, `close`), `SpeechSynthesisRequest(text:locale:voiceId:)`.
- Backend `minOS` is 15.0; device/simulator target must satisfy AVFoundation
  TTS. Build, sign, and run are performed by Main on a real device.
- The f11 `mach_task_basic_info` sampler requires `Darwin` (host process).

## UI / trigger

- One accessible `Button("Run TTS sustained")` with stable
  `accessibilityIdentifier = "w8-tts-run"`.
- **No `onAppear` auto-playback.** The button is disabled while busy (one-run
  guard); UI exposes `status` and the report filename.
- Parent binds source/artifact hashes externally; `sourceCommit` is the sentinel
  `PARENT_ATTESTED_NOT_EMBEDDED`.

## Out of scope (preserved / untouched)

- Existing 5-run benchmark and any other repository files are **not** accessed,
  overwritten, pooled, or rerun by this helper.
- W6 models/devices are not touched. No mic/ASR/Photos. No new production source
  or general observer/scorer. No recursive delegation.
