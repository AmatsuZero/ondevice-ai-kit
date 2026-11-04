# Android ASR Resources Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline. Steps use checkbox syntax.

**Goal:** Add locale-specific system ASR asset query and explicit download recovery.
**Architecture:** A tested coroutine resource session owns an injected engine;
Android supplies version-gated SpeechRecognizer calls. A Demo resource host exposes
state through an optional common UI contract, without changing recognition clients.
**Tech Stack:** Existing Kotlin/coroutines, Android system SpeechRecognizer, Compose.
**Spec:** ../specs/2026-11-04-android-asr-resources-design.md

## Global Constraints

- Keep minSdk26, query API33+, observed download API34+; no dependency changes.
- No recording or system download during validation; no cloud fallback.
- Preserve nine protected paths and production Apple lock; keep F05/release open.

## Review Focus

- Exact locale match, including underscores/case, must not infer a parent language.
- Pending, unreported and query-error results must not become installed.
- Download can happen only after explicit confirmation for the checked locale.
- Background/exit/close detach monitoring and release its own engine.
- Resource operations and microphone admission cannot overlap in Demo.

### Task 1: SDK resources and platform adapter

Files: `android/backend-system-asr/src/{main,test}/kotlin/io/ondeviceai/systemasr/AsrResources*.kt`.
Produces `AndroidAsrResources.check(locale)` and `download(locale, onProgress)`;
results distinguish installed/pending/downloadable/not-reported and platform errors.

- [x] Write tests for resource-list classification, scheduled/success/error,
  cancellation/cleanup and locale validation; observe RED.
- [x] Implement pure session and version-gated on-device engine; observe GREEN.

### Task 2: Demo recovery UI and host

Files: new Android `AsrResourceHost.kt` and tests, common `SpeechAsrResources.kt`,
`SpeechScreen.kt`, Android `SpeechHost.kt`/`MainActivity.kt`.
Consumes Task1; exposes optional resource controls through `SpeechDemoActions`.

- [x] Write RED host tests for locale binding, explicit download, progress/pending,
  background cancellation and microphone exclusion.
- [x] Implement check/confirmation/download, cancel/cleanup and native wiring.
- [x] Run backend/Demo tests, common compile, APK/test APK, lint and diff checks.
- [x] Review bounded diff, record fresh evidence and remaining runtime gaps,
  update handoff, exact-stage and commit locally without push.

## Execution evidence

`artifacts/verification/android-asr-resources-20261104/` contains original RED
outputs, the subsequent locale-exception/assertion/lint failures, final GREEN,
frozen source/APK/JUnit, ordinary emulator UI and the287-check receipt.
SDK13, combined Demo62, default54 and shared80 tests passed. Initial shared
tests/arm64 compile ran in the first full build, whose later lint step failed;
the final successful full build reused those unchanged shared outputs.

SDK-query and host test files first failed compilation because their APIs did
not exist; the separate immediate-close regression failed its actual assertion
before UNDISPATCHED admission was applied. No download/recording was performed.
Actual service-supported download/install and transcription remain unverified.

Final bounded review: native independent request failed HTTP429, no verdict.
Author separately reviewed the scoped diff, Main dispatch/cleanup, confirmation
and mutual exclusion; no additional urgent finding. Annotation/version lint
warnings remain. This is not independent approval or a whole-branch review.
