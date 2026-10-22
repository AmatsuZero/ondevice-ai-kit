# SDD ledger — 2026-10-22 multimodal Demo UI

Base: e0fc825. Solo inline execution; protected files and existing dirty/untracked state are preserved.

Pre-flight: optional shared Compose UI is independent of native host/picker/model work. Default callers remain source-compatible.

Task 1 in progress: expected missing FaceScreen multimodal argument RED in task-1-red.log; screen/routing implemented and offline APK/test APK build passed. Shared simulator suite freshly executed60/60 (task-1-shared.log). Initial UI diagnostics recorded in task-1-device*.log: clickable text child vs parent, stale disabled controls after VM terminal, and scrolling during AX recomposition. Harness now waits on model transitions, visible enabled controls and AX idle, and refreshes cache on API33+. No product workaround added.

First complete physical UI run task-1-device-7.log: opt-out, real unavailable bridge, raw prompt/strict selection/malformed budget, cumulative update, cancel waiting for terminal, output actual identity, typed platform domain/code, leave cancellation/no auto restart all passed. Fixture-only generation, not inference. Added explicit active Speech/Video navigation characterization before final task run.

Entry ABI: original new entry omitted Speech/Video arguments; author review caught that an opt-in caller should retain all legacy tabs. Updated ignored Swift probe first failed extra arguments (task-1-entry-combined-red.log), then full combined entry typechecked successfully (task-1-entry-combined-green.log); original main/speech/video Swift labels unchanged.

Task1 scope author review: no media/picker/native client/lease ownership in shared UI, no fake production success, raw prompt/selection untrimmed, budget validity delegated to VM, disabled active controls and terminal-await cancellation preserved. Optional model gates entry; existing default app calls untouched. DisposableEffect only leavePage, model close remains host-owned. Independent review pending (user prohibits subagents).
Task 1: complete (commits f88b43f..98a3891, tests: rtk proxy python3 scripts/gradle-local.py :demo:shared:iosSimulatorArm64Test :demo:androidApp:assembleDebug :demo:androidApp:assembleDebugAndroidTest -PodaiTestRunner=io.ondeviceai.demo.MultimodalScreenInstrumentation --offline --console=plain → ]9;4;0)

Task2 validation: full tools101/101, actual Demo ABI10/10, generated legacy Speech+Video app simulator unsigned build, combined Swift entry typecheck passed; Android lint0 errors/9 existing warnings. Final full UI with added legacy-navigation check passed in task-1-final-device.log. Device framework link and lint in task-2-final-gate.log. Newer tests do not replace old gates or count caches as fresh execution.

Final review: self-review (user prohibits subagents). Read review package review-e0fc825..98a3891.diff, spec, plan, requesting-code-review template and all five Review Focus items; details in final-review.md. No new Critical/Important or deferred Minor. No Ruling conflicts. Independent review pending. Docs/validation/roadmap/handoff updated and next separate host/picker package selected. Preserve workspace/artifacts and local branch; user overrides cleanup/branch-integration menu.

Task 2: complete — local validation/checkpoint only. task-2-final-gate.log exit0 (device framework linked, lint checked;9 executed/487 up-to-date), task-2-tools.log101/101, task-2-demo-abi.log10/10, task-2-ios-build.log successful, protected hashes match. Final checkpoint includes this ledger/final-review; final HEAD is authoritative.
