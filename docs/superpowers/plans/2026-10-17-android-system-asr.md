# Android System ASR Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add an optional Android on-device `SpeechRecognizer` backend with strict capability selection, cancellation, and close ownership.

**Architecture:** The existing `android:speech` contract gains typed ASR request/result/backend interfaces. `android:backend-system-asr` owns Android framework recognition and is never added to the default app dependency graph; it reports API, permission, and on-device availability without prompting or falling back to network recognition.

**Tech Stack:** Kotlin coroutines, Android `SpeechRecognizer`, API 31 on-device factory, JVM gate tests, connected instrumentation on TECNO KM9.

**Spec:** `docs/superpowers/specs/2026-10-16-capability-backends-design.md`.

## Global Constraints

- Android minSdk remains 26; on-device ASR is available only from API 31 and is availability-gated.
- Explicit backend selection never falls back to another backend or remote recognition.
- The SDK never requests permission or mutates host audio routing.
- Cancellation and close destroy recognizer instances and isolate late callbacks by task ID.
- No audio samples, credentials, or transcripts are committed.

## Review Focus

- API 30 reports `REQUIRES_OS_VERSION`; it must not call the API 31 factory.
- Missing `RECORD_AUDIO` reports `PERMISSION_DENIED` without a permission prompt.
- A cancelled task cannot complete a later task through a late callback.
- `close` is idempotent and rejects new recognition.
- Recognition errors and no-result callbacks do not leave the backend busy.

## Tasks

1. Add typed ASR request/result/backend contracts and tests in `android:speech`.
2. Add `android:backend-system-asr` with a main-thread recognizer owner and gate tests.
3. Add a cancellable Android instrumentation probe and manifest permission only for the demo probe.
4. Run JVM, assemble, connected device probe, update P6 evidence, review and sync both remotes.
