# Apple MLXVLM binary distribution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Produce an inspectable local XCFramework artifact set and a binary-only independent consumer for the optional Apple MLXVLM package, without changing the production dependency lock or claiming release until every closure gate passes.

**Architecture:** Freeze an artifact manifest from a committed source/lock snapshot. Build the exported module closure as library-evolution device and simulator frameworks, combine matching slices into XCFrameworks, and validate an independent consumer that has no apple-mlx or third-party source checkout. Keep model JSON, Metal libraries, C module maps and public Swift interfaces as first-class artifact members.

**Tech Stack:** Swift 6.4/Xcode 27.1, SwiftPM, xcodebuild, XCFramework, Python unittest, Ruby/XcodeGen.

**Spec:** docs/superpowers/specs/2026-10-30-apple-mlxvlm-binary-distribution-design.md

## Global Constraints

- Preserve production apple-mlx/Package.resolved and all protected dirty/untracked files.
- Enhanced binary platform floors remain iOS 18+ and macOS 15+; base SDK floors remain iOS 15/macOS 12.
- Do not download models/dependencies or modify upstream checkouts as a release shortcut.
- Diagnostic compiler/dependency patches remain isolated and are never packaged as production compatibility.
- A source App archive, simulator build or base ten XCFrameworks cannot close F09.
- A remote ZIP, checksum, tag, release or registry publication is outside this plan unless separately authorized.

## Review Focus

- Public Swift interface imports 20 local modules; any omitted transitive module must fail the consumer gate.
- OrderedCollections/EventSource selector settings must remain target-scoped; a global workaround is invalid.
- MLX unknown enum handling must have a supportable revision or explicit vendored source policy before packaging.
- model JSON and Metal resources must be found by runtime lookup from the binary consumer.
- Device and simulator slices must be checked independently; a fat file or App archive object is insufficient.

---

### Task 1: Define and validate the artifact manifest

**Files:**
- Create: `tools/validate_mlxvlm_binary_artifact.py`
- Test: `tools/tests/test_mlxvlm_binary_artifact.py`
- Modify: `docs/validation/apple-mlxvlm-distribution.md`

**Interfaces:**
- `validate_manifest(manifest_path: Path, artifact_root: Path) -> None` raises `ValueError` with a path-specific message.
- Manifest `schemaVersion=1`, `kind=apple-mlxvlm-xcframework-set`, `sourceCommit`, `moduleClosure`, `resources`, `artifacts`, and `toolchain` are required.
- Each artifact entry names an XCFramework path, expected modules, minimum OS, required architectures, SHA-256, and interface hashes.
- Required resources include canonical model JSON and Metal library paths and hashes.
- Reject symlink escapes, missing files, source checkout paths, duplicate module names, missing device/simulator architecture, and hash drift.

**Steps:**
- [ ] Write RED tests for a valid fixture, missing module, hash drift, symlink escape, and source checkout path.
- [ ] Run `python3 -m unittest tools.tests.test_mlxvlm_binary_artifact -v`; confirm failures are due to missing validator behavior.
- [ ] Implement the smallest manifest validator with resolved-path containment and SHA-256 checks.
- [ ] Re-run the focused tests; then run existing manifest/report tests.
- [ ] Add the validator command and schema to the distribution document.

### Task 2: Freeze the production source and dependency compatibility evidence

**Files:**
- Create: `tools/prepare_mlxvlm_binary_snapshot.py`
- Test: `tools/tests/test_mlxvlm_binary_snapshot.py`
- Modify: `docs/validation/apple-mlxvlm-distribution.md`

**Interfaces:**
- `snapshot(root: Path, source_commit: str, output: Path) -> Path` reads tracked bytes from one exact commit and verifies the expected lock hash.
- Output includes tracked package sources/manifests, source inventory, lock hash and diagnostic patch manifest if present.
- It must exclude tracked working-tree edits and untracked package files, and reject lock mismatch before writing.

**Steps:**
- [ ] Write RED tests for committed-byte snapshot despite unrelated dirty files, untracked exclusion and lock mismatch.
- [ ] Run the focused test and confirm RED.
- [ ] Implement a read-only Git object snapshot using existing source-bundle patterns.
- [ ] Verify protected dirty/untracked files are absent from the snapshot and unchanged in the worktree.
- [ ] Record the exact commit and snapshot hash separately from the current dirty checkout.

### Task 3: Build closure slices and create XCFrameworks

**Files:**
- Create: `scripts/package-apple-mlxvlm.sh`
- Create: `tools/inspect_mlxvlm_xcframework.py`
- Test: `tools/tests/test_mlxvlm_xcframework_layout.py`
- Modify: `docs/validation/apple-mlxvlm-distribution.md`

**Interfaces:**
- Package script accepts a fresh output directory and fixed SourcePackages cache; it refuses existing output.
- It builds generic iOS and iOS Simulator archives with library evolution, then runs `xcodebuild -create-xcframework` for every exported closure module.
- It emits one manifest consumed by Task 1.
- The inspector checks module names, `ios-arm64`, `ios-arm64-simulator`, minimum OS, `.swiftinterface`, C module maps, and bundle paths.

**Steps:**
- [ ] Add layout tests against synthetic XCFramework fixtures, including missing simulator slice and missing interface.
- [ ] Run RED.
- [ ] Implement inspector and package command using existing `scripts/package-apple.sh` conventions, without touching that base script.
- [ ] Run the package command only after Task 2 has a valid snapshot and supported dependency configuration.
- [ ] Keep incomplete output ignored and record exact first failure.

### Task 4: Generate and validate the binary-only consumer

**Files:**
- Create: `scripts/generate-mlxvlm-binary-consumer.rb`
- Test: `tools/tests/test_mlxvlm_binary_consumer.py`
- Modify: `docs/validation/apple-mlxvlm-distribution.md`

**Interfaces:**
- Consumer accepts only the Task 3 XCFramework set and a fresh output directory.
- It imports `ODAIKitAppleMLXVLM`, exercises image and video request types, and has no local Swift package references.
- Build graph must contain no apple-mlx, MLX, swift-transformers or dependency checkout source path.
- Runtime smoke verifies `Bundle.module`/framework bundle lookup for model JSON and Metal bundle.

**Steps:**
- [ ] Write RED project inspection tests for missing artifacts, local package reference, image/video imports and existing-output preservation.
- [ ] Run RED.
- [ ] Implement the smallest generator with source-free framework references.
- [ ] Build generic iOS and iOS Simulator consumer with library-evolution interfaces.
- [ ] Run a runtime resource smoke on an available supported device; record unavailable if no supported MLXVLM device/model is available.

### Task 5: Local binary package gate and handoff

**Files:**
- Create: `tools/validate_mlxvlm_binary_package.py`
- Test: `tools/tests/test_mlxvlm_binary_package.py`
- Modify: `docs/validation/apple-mlxvlm-distribution.md`
- Modify: `docs/HANDOFF-2026-10-23-next-session.md`

**Steps:**
- [ ] Test root-layout ZIP, `swift package compute-checksum`, local `binaryTarget` resolution and source-free second consumer.
- [ ] Run RED and implement only the local package gate.
- [ ] Run the focused tests, existing distribution tests, Python compile and `git diff --check`.
- [ ] Update F09 evidence with separate statuses for slice, local consumer, resource runtime and remote publication.
- [ ] Do not publish a URL, release, tag or registry artifact without a separate authorization.

## Validation commands

```sh
python3 -m unittest tools.tests.test_mlxvlm_binary_artifact -v
python3 -m unittest tools.tests.test_mlxvlm_binary_snapshot -v
python3 -m unittest tools.tests.test_mlxvlm_xcframework_layout -v
python3 -m unittest tools.tests.test_mlxvlm_binary_consumer -v
python3 -m unittest tools.tests.test_mlxvlm_binary_package -v
python3 -m unittest tools.tests.test_mlxvlm_source_bundle tools.tests.test_multimodal_distribution -v
python3 -m compileall -q tools
git diff --check
```

Current stop condition: build only from an exact committed tree while preserving protected dirty files. If dependency interface errors recur, record them and keep F09 open rather than packaging the diagnostic copy.
