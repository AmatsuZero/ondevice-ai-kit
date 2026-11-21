# Single-image Demo picker and shared action plan

> REQUIRED: executing-plans inline/no subagents; each Task RED→GREEN, scope author review, exact local commit. Preserve protected files, artifacts/logs and all old gaps. No push/download/model/recording/private-media/signing mutations.

**Spec:** docs/superpowers/specs/2026-10-22-multimodal-demo-picker-design.md
**Review Focus:** optional defaults; unique export selectors; page leave invalidates picker; staging cleanup/error/cap; external-picker Activity lifecycle; source byte ownership; default unavailable; no auto restart; generated iOS source list only.

### Task 1: shared action controls

Files: create demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/MultimodalDemoActions.kt; modify MultimodalScreen.kt, FaceScreen.kt, iosMain/MainViewController.kt; extend Android MultimodalScreenInstrumentation; native hosts implement interface with dedicated leaveMultimodalPage method.
Interfaces: optional MultimodalDemoActions(selectImageFile/clearImageFile/leaveMultimodalPage), default null. New dedicated multimodalHostMainViewController preserves all existing entry labels and adds actions without collisions.

- [x] Add actual UI action assertions/host callback fixture; build test APK. Expected: missing interface/parameter RED.
- [x] Implement optional select/clear controls and page leave/Dispose callback, hosts conform without changing existing lifecycle logic; new iOS entry dedicated unique name.
- [x] Run actual Android shared UI instrumentation on connected authorized API35 device, shared simulator60/60 and two framework links; compile old/new Swift entry calls against actual header. Expected: all named UI fields passed, no export collision. Scope author review; commit feat: expose multimodal input actions in shared UI.

### Task 2: Android system picker integration

Files: MainActivity.kt; new MultimodalPickerCopy.kt and unit tests; new picker instrumentation/test fixture provider if needed.
Interfaces: host local File callback; Intent extra odai.multimodal.enabled defaults false; GetContent image/png/jpeg, stream cap32MiB and per-result staging cleanup.

- [x] Write real stream/file tests for copy/cap/read failure/cleanup; run missing-helper RED.
- [x] Implement staging helper and Activity opt-in host/picker lifecycle: onStart foreground, onStop preserve only in-flight picker/import, onDestroy closes and clears callbacks. IO staging, finally cleanup, no URI as reference.
- [x] Run full app unit/APK/testAPK/lint offline and actual device opt-in/default/unavailable/system-cancel/background-no-restart; use public/synthetic fixture for any selected path and distinguish actual GetContent from injected test result. Expected: new tests/full suite green, lint0errors9existing warnings. Scope author review; commit feat: wire Android multimodal picker lifecycle.

### Task 3: iOS generated opt-in and checkpoint

Files: new MultimodalFilePicker.swift; DemoController.swift, OnDeviceAIDemoApp.swift; generate-speech-demo.rb and test_speech_demo_project.py; Demo-only bridge test generator source list/picker delegate tests; validation/roadmap/handoff/ledger/final-review.
Interfaces: MainActor UIDocumentPicker PNG/JPEG single file, existing host; compile opt-in ODAI_MULTIMODAL_DEMO requires independent Speech+Video Demo generation, new dedicated shared action entry.

- [x] Add actual generator source/product/flag expectations; run RED before generator edit. Add picker delegate cancellation/once-only URL tests and compile missing-picker RED.
- [x] Implement picker and optional host/session wiring in independent generator only; protected project untouched. MainActor factory with default unavailable, background/foreground/close forwarding, no model/product dependency additions.
- [x] Run fresh Demo ABI native tests, full tools, strict Swift lint and generated full app unsigned simulator build with all old hosts. Expected: tests pass, old/new header entry compile, generated app build succeeds. Actual iOS touch UI remains unverified unless run; keep minimumOS warnings.
- [x] Separate author final review, exact evidence/docs/roadmap/handoff/ledger/protection hashes. Commit feat: wire generated iOS multimodal picker demo. No independent APPROVE. Next package optional existing Apple model/ModelStore, then real KMP lifecycle devices; Android stays unavailable.
