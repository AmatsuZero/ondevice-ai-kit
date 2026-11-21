# Android System TTS Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans task by task.

**Goal:** Add an Android system TTS SDK backend with the same `speak(request, backend)` selection model as Apple.

**Architecture:** `SpeechSynthesisClient` dispatches via the existing capability-scoped registry. An optional `android:backend-system-tts` module owns `TextToSpeech`, selects installed non-network voices, and maps utterance callbacks to one cancellable coroutine result. The backend owns `shutdown()` and never changes the host audio routing.

**Tech Stack:** Kotlin 2.4, Android API 26+, Kotlin coroutines, Android `TextToSpeech`, JVM unit tests and API 35 device instrumentation.

**Spec:** `docs/superpowers/specs/2026-10-15-ondevice-ai-sdk-design.md` §7.2 and `docs/validation/speech-backends.md`.

## Task 1: Typed SDK dispatch
- [x] Write failing tests for default/explicit selection and no fallback.
- [x] Add TTS request/result types, backend protocol, and `SpeechSynthesisClient`.
- [x] Run `:android:speech:testDebugUnitTest` offline.

## Task 2: Optional Android backend
- [x] Add `android:backend-system-tts` without adding it to Demo's default runtime dependency.
- [x] Implement init, locale/voice selection, callback/cancellation, and shutdown.
- [x] Build release AAR and run host tests.

## Task 3: Device evidence and sync
- [x] Run SDK backend on TECNO KM9 API 35; record success and close without retaining audio; cancellation remains explicitly unmeasured.
- [x] Update P6 docs and roadmap with exact evidence and gaps.
- [ ] Commit and fast-forward sync GitHub and JiHu; preserve user files.
