# Single-image Demo Input Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans inline. No subagents; RED→GREEN, scope author review and exact local commit per Task.

**Goal:** Supply both native multimodal input providers with private-copy/opaque-token/lease ownership for later host/picker wiring.

**Architecture:** Apple capability wrapper reuses SpeechInputStore; Android native file store implements the existing bridge provider. Selection retirement and native operation leases have separate lifetimes.

**Tech Stack:** Existing Swift/Foundation/XCTest, Kotlin/JVM/Java NIO, offline Gradle/Xcode/Ruby tools.

**Spec:** `docs/superpowers/specs/2026-10-22-multimodal-demo-input-design.md`

## Global Constraints

- No picker/host/model/MLX integration in this package, no new dependencies or downloads.
- Only synthetic local fixture files; no device/private media, recording, signing mutation or push.
- iOS15/macOS12/API26 and optional enhancement boundary unchanged; protected files never modified/staged.
- Preserve existing Speech/Video store sources; only reuse them on Apple.

## Review Focus

- Replacing a selection or closing during native drain must retain bytes until every operation lease releases.
- Duplicate release must not consume another lease or delete a still-selected file.
- Unknown/path-like references must not read/delete outside the owned directory.
- Existing directories/foreign files must not be adopted or recursively removed.
- Import failure/nonregular source must not mutate source or make a usable token.

### Task 1: Apple input provider and generated tests

**Files:** Create `demo/iosApp/MultimodalInputStore.swift`, `probes/ios-multimodal-bridge/MultimodalInputStoreTests.swift`; modify `scripts/generate-multimodal-bridge-tests.rb`, `tools/tests/test_multimodal_bridge_project.py`.

**Interfaces:** Consumes IOSMultimodalInputProvider/Lease plus SpeechInputStore; produces MainActor MultimodalInputStore importImage(from:), acquire(reference:), remove(reference:), close().

- [ ] Add XCTest fixture-file assertions for copy isolation, two leases/retirement/close, duplicate release, closed/unknown admission and foreign/symlink/directory/nonlocal rejection. Add generator expected source files only for Demo ABI.
- [ ] Run generator tests before generator edit: expected actual generated Demo source set mismatch RED; update generator. Generate fresh Demo tests and run Xcode: expect missing store symbols RED.
- [ ] Implement capability wrapper with nonsymlink regular file admission and native lease adapter; do not alter existing stores.
- [ ] Run generated Demo ABI tests (existing10 plus input4), Binding ABI regression and generator tests offline. Inspect actual Xcode outputs, not source grep.
- [ ] Scope author review; exact stage/local commit `feat: add Apple multimodal input ownership`.

### Task 2: Android input provider and checkpoint

**Files:** Create `demo/androidApp/src/main/kotlin/io/ondeviceai/demo/MultimodalInputStore.kt`, `demo/androidApp/src/test/kotlin/io/ondeviceai/demo/MultimodalInputStoreTest.kt`, `docs/validation/multimodal-demo-input.md`; update ROADMAP/HANDOFF and `.superpowers/sdd/2026-10-22-multimodal-demo-input/{progress,final-review}.md`.

**Interfaces:** Consumes AndroidMultimodalInputProvider/Lease; produces synchronized MultimodalInputStore(directory: File), importImage(source: File): String, acquire(reference), remove(reference), close().

- [ ] Write four fixture-file tests for the five Review Focus cases and source copy isolation; run Android app unit tests offline, expect missing store RED.
- [ ] Implement fresh-directory ownership and opaque token registry, NOFOLLOW copy admission, separate selected/lease counts, idempotent lease release and safe nonrecursive cleanup.
- [ ] Run `rtk proxy python3 scripts/gradle-local.py :demo:androidApp:testDebugUnitTest :demo:androidApp:assembleDebug :demo:androidApp:lintDebug --offline --console=plain`; expected full app unit suite green and APK/lint success (existing warnings remain). Run full tools suite with existing Ruby3.4.
- [ ] Separate final author review against spec/plan; record actual evidence, independent-review/host/picker/real-inference gaps, protected hashes and next package. Exact stage/local commit `feat: add Android multimodal input ownership`.
