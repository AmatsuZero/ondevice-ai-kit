# KMP Multimodal host/VM device implementation plan

> Required: executing-plans inline/no subagents. Explicit user instruction supplies solo execution and no routine confirmation. Each Task behavior first RED→GREEN, scope author review, exact local commit.

**Goal:** real physical-device KMP VM/host lifecycle with fixed MLXVLM and auditable JSON.
**Architecture:** independent validation App reuses production host/input/bridge/client/backend; test-only forwarding observation records actual callbacks without replacing results.
**Tech Stack:** Swift/KMP, existing XcodeGen/cache/ModelStore, Python unittest/report validator.
**Spec:** docs/superpowers/specs/2026-10-22-multimodal-demo-device-design.md

## Global constraints

Only fixed14file model and synthetic fixturehash60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606; exact apple-mlx lock. Authorized iPhone00008140-0008703436D3001C, existing team4X996UV8LC. Fresh independent bundle io.ondeviceai.multimodallifecycle; no product/default/KMP export change. No downloads/private media/recording/signing changes/push/merge/publish, protected files untouched/unstaged, all logs retained. UI rendering/touch/picker, minimumOS and independent review remain unverified; roadmap5/12/P9active.

## Review focus

False pass on missing callback or wrong sequence; release-before-terminal evidence; cancellation swallowed into success; background automatic restart; active close/model lease ownership and after-close rejection. Pin with trace validation mutation tests and actual real-device gates.

### Task1: strict lifecycle report gate

Files: create tools/multimodal_lifecycle_report.py and tools/tests/test_multimodal_lifecycle_report.py.
Interface: validate(report: dict, expected_commit: str) -> dict; CLI takes report and --source-commit and prints compact validated summary, exit nonzero on any failure. Constants exact model/backend/runtime/fixture; gates availability, success, cancel, reentry, background, close, modelLeaseProtected, modelLeaseReleased, inputsReleased, closedRejected.

- [ ] Write tests using a complete valid synthetic report and mutations: absent/false/type-confused gate, wrong source/hash/identity/simulator, empty trace, missing update/cancel/request or release/terminal order. Run unittest RED missing module.
- [ ] Implement strict validation of all gates and ordered actual trace (request, update, cancel/background/close, inputReleased, terminal), duplicate/malformed events rejected. Summary says programmatic KMP host/VM; UI unverified.
- [ ] Full tools suite passes; scope author review and exact local commit test: validate real KMP multimodal lifecycle evidence.

### Task2: real KMP host/VM device suite

Files: probes/ios-multimodal-device/{MultimodalDeviceApp.swift,MultimodalDeviceSuite.swift,project.yml}, tools/generate_multimodal_device.py and generator unittest. Validation/roadmap/handoff/ledger/final-review.
Interfaces: MainActor MultimodalDeviceSuite.run(documents: URL) async -> [String:Any]; output device-result.json atomic. Generator generate(output: Path) fresh directory only; copy test sources, absolute existing product sources/framework and exact lock; sourceCommit from git HEAD baked into Info.plist.

- [ ] App test entry references missing suite; generate/build with existing cache offline, verify RED missing suite symbol. Generator tests prove fresh output/source set/lock/bundle/platform and refusal before overwriting/missing inputs.
- [ ] Implement actual install/acquire/backend/client/observing input provider/forwarding bridge/host/VM; deterministic first delivered update actions, bounded waits, real release-before-terminal and drain evidence. No fake runtime outputs; active close and idempotent rejection tested. Source fixture hash in device report checked before inference.
- [ ] Full tools, strict new Swift lint; unsigned/signed physical build with -disableAutomaticPackageResolution -skipPackageUpdates -onlyUsePackageVersionsFromResolvedFile and existing team. Check connection/hash14 files before installing/copying only own synthetic/model assets. Launch, retrieve own report, validate exact sourceCommit/hash/PID/identity/ordered gates; keep failure logs if external blocker.
- [ ] Separate author final review, document precise pass/fail/unverified, update roadmap/handoff/ledger/final-review and exact local Task2 commit. No UI/minimumOS/quality overclaim or independent APPROVE.
