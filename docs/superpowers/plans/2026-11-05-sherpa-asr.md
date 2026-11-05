# Sherpa offline ASR Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans task by task.

**Goal:** Run Android Demo ASR offline using models downloaded on the computer.

**Architecture:** Optional SDK backend, existing capability selector, host-prepared
model assets and USB-installed APK. Keep system ASR separately selectable.

**Tech Stack:** Kotlin coroutines, Android AudioRecord, sherpa-onnx2.0.1,
Whisper tiny multilingual int8, Python artifact preparation.

**Spec:** `docs/superpowers/specs/2026-11-05-sherpa-asr-design.md`

## Global constraints

minSdk26; CPU; no phone model download/cloud fallback; no persisted microphone
audio; optional dependency only; preserve protected files and production MLX lock.

## Review focus

- Explicit system selection must never execute sherpa, and vice versa.
- Cancellation during synchronous JNI must drain before result/reentry/close.
- Missing/corrupt assets must be unavailable, without starting capture.
- Locale must map explicitly; unsupported locales must not silently become English.
- Default Demo must build without sherpa runtime/model inputs.

## Task1: SDK backend and pinned host preparation

Files: new `android/backend-sherpa-asr/`, `tools/prepare_sherpa_asr.py`;
`settings.gradle.kts` for opt-in local Maven repository/module.

- [x] Write/run failing tests for model validation, locale mapping, session
  cancellation/drain, reentry, close and admission with an injected native decoder.
- [x] Prepare exact AAR/model/license files; compare upstream digests.
- [x] Implement `SherpaAsrBackend`, model-file configuration, in-memory microphone
  collector and serialized/draining decode session; `transcribe(samples,locale)`
  for caller-supplied synthetic samples.
- [x] Run module tests, lint and Release AAR build.

## Task2: Optional ordinary Demo wiring

Files: SpeechHost/MainActivity/build.gradle.kts, enabled/disabled factory source
sets and SpeechHostTest. Existing shared selector needs no redesign.

- [x] Add failing tests for default sherpa selection, explicit system selection
  and close of both backends.
- [x] Register extra ASR backends with an explicit default ID; include assets
  only for `enableSherpaAsr=true`, never introduce runtime classes in disabled sources.
- [x] Run enabled and disabled Demo tests/lint/APK builds.

## Task3: Device execution and handoff

Files: operator-only androidTest synthetic-sample probe, backend README,
validation docs and handoff; ignored raw evidence in artifacts/verification.

- [x] Generate a known fixed waveform on the host, install optional Demo by USB
  after preserving the original APK; verify pinned installed APK/model hashes.
- [x] Execute fixed sample through actual SDK/JNI with status-only output;
  inspect ordinary backend selector and idle UI without microphone capture.
- [x] Restore ordinary App/test package as needed or leave an explicitly
  documented recoverable optional Demo; preserve all existing models/data/permissions.
- [x] Record exact passes/gaps, protect user paths, commit task-owned changes.

Native execution selected by standing autonomy instruction. Independent agents
returned429; do not retry them or manufacture a review approval.

## Task4: Incorrect live recognition / noise correction

- [x] Record both user-reported incorrect microphone attempts as failed
  acceptance, not success inferred from the UI phase. No further capture.
- [x] Reproduce silence/noise hallucinations with fixed synthetic inputs on TECNO.
- [x] Add pinned MIT Silero VAD through existing runtime; require speech and trim
  context before Whisper; test no-speech, short bursts, reset and limits.
- [x] Surface typed NO_SPEECH through SDK/bridge/shared UI; RED→GREEN mapping test.
- [x] Request available Android session noise suppression; preserve resource cleanup.
- [x] Fix first-copy concurrency and missing-default startup crash, with regression
  and cold model-directory device replay.
- [x] Verify exact final App/test/models, restore original test runner/permissions,
  update evidence and commit task-owned paths.
- [x] Newly authorized live retest after these changes; user confirmed basically
  correct with minor omissions/errors, not an exact match. Strong-noise SNR0 and
  Chinese exact-match quality remain open; basic functionality is user-confirmed,
  and accuracy acceptance is incomplete.
