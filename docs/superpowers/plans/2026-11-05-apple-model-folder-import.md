# Fixed Apple model folder import implementation plan

> Execute inline with `superpowers:executing-plans`; retain the existing solo lane.

**Goal:** import the fixed local MLX model from Files and retry failed preparation.
**Architecture:** existing folder picker and ModelStore factory; no generation coordinator.
**Tech stack:** UIKit, SwiftUI, existing Swift/KMP SDK.
**Spec:** `docs/superpowers/specs/2026-11-05-apple-model-folder-import-design.md`.

## Global constraints

Canonical 14-file SmolVLM2 only; no downloads/dependencies/lock/protected-file changes.
No inference from setup, no installed-model replacement, no failed UI-tool retries.
Review focus: failure→retry, concurrent starts, picker cancellation/late callbacks,
owner release during preparation, scope release after success/failure.

## Task 1: retriable preparation and folder selection

- [x] Add `testFailureAllowsExplicitRetryButSuccessfulPreparationStaysOnceOnly` to
  `probes/ios-multimodal-bridge/MultimodalHostPreparationTests.swift`; observe RED.
- [x] Reset the existing preparation admission after failure without retaining its owner
  across `await`; retain late-host cleanup and duplicate-start rejection. Observe GREEN.
- [x] Add folder selection/cancellation/once-only callback coverage in
  `MultimodalFilePickerTests.swift`; observe missing-method RED, add
  `MultimodalFilePicker.presentModelFolder(reply:)`, then observe GREEN.

## Task 2: enhanced Demo setup flow

- [x] Add optional `source: URL?` to `MultimodalMLXHostFactory.make`; scope selected local
  folder access through the existing canonical ModelStore factory, releasing with defer.
- [x] In `DemoController.swift`, reuse one preparation owner for bootstrap and explicit
  import retry; guard busy/ready selection and clear an old failure message on success.
- [x] In `OnDeviceAIDemoApp.swift`, expose one MLX-only fixed-folder import button and
  ready status. Preserve the existing shared host/VM and default dependency isolation.
- [x] Run the full multimodal Swift suite and tools/generator suite; typecheck the actual
  combined Swift source set against existing dependency artifacts. Review ownership and
  failure paths separately, commit only owned files and record device/UI limitations.
