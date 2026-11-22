# CMP capability details implementation plan

> **For agentic workers:** Use superpowers:executing-plans task by task. External helpers deliver implementation candidates; the primary integrates and verifies. One native child reviews read-only at the end.

**Goal:** Implement the approved compact, unified capability details and remove duplicate safe-area spacing.

**Architecture:** Shared CMP owns navigation, cards, actions and observable state. Native hosts retain media selection, decoding and rendering. SwiftUI remains the launch shell with safe-area handling coordinated with CMP.

**Tech Stack:** Existing Kotlin Multiplatform, Compose Multiplatform, SwiftUI shell and XCTest; no new dependencies.

**Spec:** `docs/superpowers/prototypes/2026-11-22-capability-details/README.md`, approved by the user on 2026-11-22 (“设计稿没问题”).

## Global Constraints

- Preserve input limits, strict backend selection, supportedModes and actual presentedMode; do not expand SDK capability.
- Preserve cancellation drain, explicit cleanup, recovery, leave/background lifecycle and all existing test tags.
- Save records only when an existing result permits it; records remain in-process summaries without media leases.
- One safe-area owner; background extends behind system areas, controls remain safe. Navigation grows for larger text.
- Preserve unrelated dirty and untracked files, signing settings and ordinary device Apps/models.
- External implementation candidates only; primary owns integration and tests. At most one native read-only reviewer.

## Review Focus

- Empty input and picker cancellation: controls remain reachable and processing disabled.
- Missing backend/model and failures: visible explanation and recovery, even with advanced details collapsed.
- Cancellation and leaving: controls remain blocked until real terminal cleanup.
- Playback before presentation/fallback: selection never impersonates actual enhancement.
- Narrow/landscape/large-text layouts: no clipped navigation or inaccessible actions.

### Task 1: Lock layout and existing interaction behavior

**Files:** `demo/iosApp/UITests/VideoFileSuperResolutionUITests.swift`, existing playback UI tests.
**Interface:** Existing accessibility tags; add coverage for compact top navigation and initially collapsed advanced backend controls.

- [x] Obtain a bounded helper test candidate preserving the current picker and unsupported-device assertions.
- [x] Run against the unchanged default App; retain a failure caused by current navigation/advanced layout.

### Task 2: Implement approved shared details and safe-area ownership

**Files:** `UnifiedDemoScreen.kt`, `VideoScreen.kt`, `UnifiedDemoTheme.kt`, iOS `MainViewController.kt`, Android `MainActivity.kt`, SwiftUI `OnDeviceAIDemoApp.swift`.
**Interfaces:** Existing VideoViewModel and PlaybackDemoState/actions; optional result-save callback for VideoScreen with source-compatible default values.

- [x] Obtain an isolated helper candidate after RED; review exact changes before integration.
- [x] Replace oversized header spacer, coordinate root insets, consume scaffold padding, apply consistent neutral container colors.
- [x] Group file input/settings/run/result and advanced controls. Keep input requirements/errors and cleanup visible.
- [x] Group playback preview/input/modes/actual status; modes from supportedModes; render both hosts at shared 16:9.
- [x] Re-run targeted UI checks, existing shared lifecycle/state tests and Android/iOS builds.

### Task 3: Verify and deliver

**Files:** prototype README, this plan, validation report, execution ledger.

- [x] Check default App layout coordinates/screenshots, picker cancellation, playback modes/background/EOS and existing tests.
- [x] Exercise available narrow/large-text/landscape checks, explicitly report gaps.
- [x] One native read-only final code/evidence review; fix concrete blockers and verify affected checks.
- [x] Commit exact task files, integrate main, ordinary push to authorized remotes and read back SHAs. Preserve protected files.

验收与边界：见 `docs/validation/cmp-capability-details-20261122.md`。最后一项提交/推送在交付终态收据核实；运行态键盘/无Home条及真机质量未验，不由适配检查替代。
