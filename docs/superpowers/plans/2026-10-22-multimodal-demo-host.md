# Single-image Demo native host lifecycle plan

> REQUIRED: superpowers:executing-plans inline, no subagents. RED→GREEN, scope author review, exact local commit per Task.

**Goal:** Native owners compose the existing VM/bridge/input stores with injectable local-file selection and deterministic lifecycle.
**Spec:** docs/superpowers/specs/2026-10-22-multimodal-demo-host-design.md
**Constraints:** No actual system picker/URI/UI/navigation/model integration, new dependencies/downloads/private media/recording/signing or remote writes. Protected files untouched; keep artifacts/logs. iOS15/macOS12/API26.
**Review Focus:** latest/once-only picker tickets; nil/error preservation; retired token lease retention; background no restart; close once while drain pending; default unavailable; no source/reference path leakage.

### Task 1: Android native host

**Files:** create demo/androidApp/src/main/kotlin/io/ondeviceai/demo/MultimodalHost.kt and src/test/kotlin/io/ondeviceai/demo/MultimodalHostTest.kt.
**Interfaces:** existing MultimodalInputStore + MultimodalBridge + VM → MultimodalHost(store, bridge=UnavailableMultimodalBridge(), selectFile: ((File?) -> Unit) -> Unit), model, selectImageFile/clearImageFile/leavePage/background(preservePicker=false)/onForeground/close.

- [ ] Write actual-file tests for success/replacement/lease retention, nil/duplicate/older tickets, invalid input/throwing picker, clear/leave/background/close late replies, preservePicker-before-foreground, default unavailable, controlled bridge cancel/drain/close once; run app unit tests offline. Expected: missing host symbol RED.
- [ ] Implement only native ownership composition and consume ticket before importing. No shared VM rewrite or native backend.
- [ ] Run app14 existing plus new host tests, APK/lint offline; inspect actual XML counts and cached task distinction. Expected: no new failures/lint errors (9 existing warnings).
- [ ] Scope author review and exact local commit feat: add Android multimodal host lifecycle.

### Task 2: Apple native host and checkpoint

**Files:** create demo/iosApp/MultimodalHost.swift and probes/ios-multimodal-bridge/MultimodalHostTests.swift; update scripts/generate-multimodal-bridge-tests.rb, tools/tests/test_multimodal_bridge_project.py; create docs/validation/multimodal-demo-host.md; update ROADMAP/HANDOFF/ledger/final-review.
**Interfaces:** existing store/typed bridge/VM → MainActor MultimodalHost(store:, bridge: defaultUnavailable, picker: (@escaping(URL?)->Void)->Void) with same lifecycle methods.

- [ ] Add source-set expectations and native actual-file tests mirroring Task1. Run generator tests before generator edit. Expected: actual generated Demo source mismatch RED.
- [ ] Update Demo-only generator sources; generate fresh Demo XCTest project; run before host implementation. Expected: missing host symbols RED.
- [ ] Implement thin MainActor host, once-only tickets, input/selection ownership and VM lifecycle; keep Binding ABI and old exports unchanged.
- [ ] Run generated Demo/Binding ABI suites, full tools with existing Ruby3.4 and strict Swift lint, offline. Expected: new tests + existing14 pass; Binding10 pass, tools101 pass, format exit0. No device inference/UI claim.
- [ ] Separate whole-package author final review, update validation/roadmap/handoff/ledger and all gaps/protected hashes; exact local commit feat: add Apple multimodal host lifecycle. Next package is actual system pickers/shared actions/platform opt-in integration, then optional model and real KMP device loop separately.
