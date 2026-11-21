# Apple System TTS Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans task by task.

**Goal:** Provide a usable Apple TTS SDK method with strict backend selection and a default `AVSpeechSynthesizer` backend.

**Architecture:** `SpeechSynthesisClient` owns a `SpeechClient` registry and dispatches `speak` to a typed registered backend. `AppleAVSpeechBackend` owns one `AVSpeechSynthesizer` on the main actor, checks installed voices for the requested locale, and maps finish/cancel callbacks to one async terminal result. Cancellation and close stop the active utterance and settle its continuation once.

**Tech Stack:** Swift 5.9, AVFAudio, XCTest. iOS 15 minimum; no cloud SDK or audio session mutation.

**Spec:** `docs/superpowers/specs/2026-10-15-ondevice-ai-sdk-design.md` §7.2 and `docs/validation/speech-backends.md`.

## Task 1: Typed SDK dispatch
- [x] Add failing tests for default and explicit `speak`, strict missing/unavailable backend, and result backend ID.
- [x] Implement `SpeechSynthesisClient`, `SpeechSynthesisBackend`, request/result values.
- [x] Run targeted XCTest.

## Task 2: Apple AVSpeech backend
- [x] Add host tests for empty input and close rejection; verify cancellation terminal behavior on device.
- [x] Implement AVSpeech adapter in a separate optional SwiftPM product.
- [x] Run targeted tests, host build, and iOS simulator compile.

## Task 3: Device and documentation
- [x] Run on connected iPhone using existing signing without modifying account assets; record success/cancel evidence only if actually observed.
- [x] Update P6 validation and roadmap with exact evidence and gaps.
- [x] Commit and fast-forward sync GitHub and JiHu; preserve user files.
