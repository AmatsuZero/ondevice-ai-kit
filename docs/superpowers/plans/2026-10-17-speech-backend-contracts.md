# Speech Backend Contracts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add capability-scoped ASR/TTS backend registries with default or explicit backend IDs, strict selection errors, and optional registration without adding a provider dependency.

**Architecture:** Each platform exposes a small capability client with registrations supplied by the host. The registry resolves exactly one backend when the caller selects an ID and never silently falls back from an explicit ID. Backend implementations and task/audio protocols remain in later optional Apple/Android/plugin modules; this package only defines registration, availability, and lifecycle.

**Tech Stack:** Swift 5.9 actors and Sendable values; Kotlin/JVM coroutines and sealed errors; existing ODAIKitCore availability types.

**Spec:** `docs/superpowers/specs/2026-10-15-ondevice-ai-sdk-design.md`, section 7.2.

## Global Constraints

- iOS 15 remains the base platform; newer speech backends are optional and availability-gated.
- System backends are defaults when registered; explicit selection is strict and does not fall back.
- Azure/Microsoft Speech remains an optional plugin and is not added to the default dependency graph.
- Audio session/routing is owned by the host; the SDK contracts do not mutate global audio state.
- No audio samples, credentials, or model assets are committed.

## Review Focus

- Explicit unknown backend IDs return a structured not-registered error.
- An unavailable default backend is reported as unavailable rather than silently replaced.
- Closed clients reject new work and close registrations exactly once.
- Backend descriptors expose current availability, minimum OS, and offline metadata.
- The registry does not pretend to perform ASR/TTS before task and audio protocols are designed.

### Task 1: Apple speech contracts

**Files:** Create `apple/Sources/ODAIKitSpeech/SpeechClient.swift`; create `apple/Tests/ODAIKitSpeechTests/SpeechClientTests.swift`; modify `apple/Package.swift`.

- [x] Write failing registration/selection/lifecycle tests.
- [x] Implement minimal actor client and protocols.
- [x] Run `swift test --package-path apple --filter ODAIKitSpeechTests`.

### Task 2: Android speech contracts

**Files:** Create `android/speech/build.gradle.kts`; create `android/speech/src/main/kotlin/io/ondeviceai/speech/SpeechClient.kt`; create unit tests; modify `settings.gradle.kts`.

- [x] Write failing registration/selection/lifecycle tests.
- [x] Implement minimal client and value types.
- [x] Run the targeted offline Android unit test.

### Task 3: Documentation and verification

- [x] Add the package boundary and IDs to `docs/validation/speech-backends.md`.
- [x] Run Apple and Android targeted suites plus `git diff --check`.
- [x] Commit only package code/docs; leave artifacts and user files untouched.
