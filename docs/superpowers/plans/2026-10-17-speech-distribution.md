# Speech Distribution Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (inline execution is already selected by the ongoing continuation).

**Goal:** Make the already implemented system speech modules independently distributable and verifiable without adding them to the face/core default dependency graph.

**Architecture:** Extend the existing Apple distribution generator and package script with `ODAIKitSpeech` and `ODAIKitAppleSpeech`, preserving their separate Swift Package targets. Add small generated/compiled consumers for Apple speech and Android speech/TTS/ASR artifacts. Keep Microsoft/Azure providers out of the dependency graph.

**Tech Stack:** Swift Package/Xcode archive/XCFramework, Kotlin Android libraries, Maven Local, existing native consumer build tooling.

**Spec:** `docs/superpowers/specs/2026-10-16-capability-backends-design.md`.

## Global Constraints

- Speech modules remain optional; face/core consumers must not link them.
- Android minSdk remains 26; on-device ASR availability remains runtime-gated at API 31.
- No credentials, transcripts, audio files, model weights, or device identifiers enter Git.
- No Microsoft/Azure SDK is added in this package.
- Existing user signature configuration and untracked face identity design remain untouched.

## Tasks

1. Make Apple distribution generation idempotently include the two speech targets and package script outputs; add a speech consumer generator and compile it against the fresh XCFrameworks.
2. Add an Android speech native consumer module that resolves `speech`, `backend-system-tts`, and `backend-system-asr` from Maven Local; compile it without changing the face consumer dependency graph.
3. Run the targeted Apple/Android distribution checks and existing speech unit/build checks; record exact artifacts, current SHA, and remaining device/quality gaps in P6 docs.
4. Run scoped review, commit, and synchronize both remotes while preserving unrelated worktree state.

## Execution record

- [x] Task 1: six XCFrameworks built; speech and face binary consumers compiled; Core/Speech-only contract typecheck passed; generator repeat hashes equal; existing package outputs refused without mutation.
- [x] Task 2: Android contract/systemTts/systemAsr consumers compile with project dependencies and published Maven Local coordinates; all debug/release flavors passed; dependency graphs isolate each provider.
- [x] Task 3: Swift XCTest 33/33 (speech 6), existing Android speech/TTS/ASR test reports 18/18, module and consumer lint with no errors. Android unit tasks reused up-to-date outputs. Evidence and remaining gaps recorded in speech-backends.md.
- [x] Task 4: independent review APPROVE (0 findings); implementation commit `58cc5a61ce1d4507efaf0ac3dcc4e2a9566eb8e3` synchronized to GitHub and JiHu with fresh remote SHA equality checks.

Scope decision: this package distributes existing APIs only. It does not add iOS ASR, KMP speech UI, or cloud providers. Android ASR MISSING_ASSETS and minimum-version devices remain explicit gaps.
