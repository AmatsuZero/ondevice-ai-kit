# Optional Apple MLXVLM Demo host implementation plan

> Required: executing-plans inline/no subagents. Each Task RED→GREEN, scope author review, exact local commit. No routine permission gate per explicit user instruction.

**Goal:** optional fixed local Apple model behind generated enhanced Demo and explicit runtime argument.
**Architecture:** ModelStore→lease→MLXVLMBackend→native client→existing IOS bridge/host/VM; no new coordinator.
**Tech Stack:** existing Swift/KMP, ModelStore, isolated apple-mlx, Ruby generator/Python tests.
**Spec:** docs/superpowers/specs/2026-10-22-multimodal-demo-mlx-host-design.md

## Global constraints

Preserve base iOS15/macOS12/API26; enhanced iOS18 only. Exact existing14file canonical model/lock/caches; no dependency/model downloads or private media. Protected files untouched/unstaged; no push/merge/publish; all artifacts retained. Default and Android unavailable; no actual-device/quality/resource overclaim. Solo execution, independent review pending.

## Review focus

Cancellation during preparation, failures after ownership transfer, default graph/flags isolation, owner released before async preparation completes, inactive late host/foreground no restart. Tests below pin these boundaries; actual UI/device remains explicit next package.

### Task1: lease-safe native host composition

Files: create demo/iosApp/MultimodalModelHostFactory.swift, probes/ios-multimodal-bridge/MultimodalModelHostFactoryTests.swift; extend Demo-only test generator and source/product inspection tests.
Interfaces: MainActor static make(modelStore: ModelStore, manifest: ModelManifest, source: URL, inputDirectory: URL, picker: @escaping (@escaping (URL?)->Void)throws->Void, backend: (ModelLease)throws->any MultimodalGenerationBackend) async throws -> MultimodalHost.

- [x] Write tiny real ModelStore tests: successful transfer protects remove until host.close; builder throw closes lease; invalid registration closes backend; cancelled before install invokes no builder; cancelled during builder closes backend/lease; corrupt source invokes no builder. Real generated test RED missing factory symbol.
- [x] Implement composition using Task.checkCancellation at ownership boundaries, explicit async cleanup on failure, bridge/store/host reuse.
- [x] Run fresh complete Demo ABI tests, actual generator tests and strict new Swift format; expected all pass with no skipped. Scope author review; exact commit feat: compose lease-safe multimodal model host.

### Task2: enhanced fixed MLX Demo opt-in

Files: create demo/iosApp/MultimodalMLXHostFactory.swift, MultimodalHostPreparation.swift and preparation XCTest; modify DemoController.swift, OnDeviceAIDemoApp.swift, scripts/generate-speech-demo.rb, tools/tests/test_speech_demo_project.py; validation/roadmap/handoff/ledger/final-review.
Interfaces: MainActor MultimodalMLXHostFactory.make(documents: URL, picker: MultimodalFilePicker) async throws -> MultimodalHost; compile flag ODAI_MULTIMODAL_MLX_DEMO and explicit --multimodal-mlx-local-model runtime opt-in. Only enhanced generator adds source/product/local apple-mlx and copies fixed lock; enhanced deployment18/bundle distinct.

- [x] Add actual generation tests for flag prerequisites, default graph unchanged, enhanced18/source/products/lock/bundle, refuse missing lock before output; run RED.
- [x] Implement canonical factory and async once-only owner wiring: unavailable retained on failure, weak completion/close on cancelled or released owner, published host and loading gate before controls, inactive completion backgrounded. No auto generation/restart.
- [x] Run full tools and Demo native tests as changed; generate fresh enhanced project and offline unsigned generic iOS device compile with fixed existing SourcePackages/lock and -disableAutomaticPackageResolution -skipPackageUpdates -onlyUsePackageVersionsFromResolvedFile. Expected success or exact external cache/tool blocker, never download.
- [x] Separate author final review; document real pass/fail/unverified and next independent device suite. Verify protected hashes/index, exact local commit feat: add opt-in fixed MLXVLM Demo host. Roadmap5/12/P9active unchanged.
