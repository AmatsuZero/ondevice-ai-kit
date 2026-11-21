# SmolVLM2 iOS Probe Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在独立的 iOS 18+ 增强入口中，用固定 checkpoint 的 SmolVLM2 500M MLX 模型完成一条图片输入→文本输出探针，并记录可复核资源证据。

**Architecture:** 探针放在独立的 iOS 增强 target/package，不修改 iOS 15 基础 SDK，不把 MLX/MLXVLM 依赖放入 `ODAIKitCore`、`ODAIKitFace` 或默认 Demo。模型加载、会话取消和资源释放由探针持有；输出 JSON 记录模型 revision、runtime revision、输入 hash、首 token/完整输出耗时、峰值内存和状态。

**Tech Stack:** Swift 6.4、iOS 18+、MLX/MLXLMCommon/MLXVLM、SmolVLM2 500M MLX checkpoint；现有 benchmark schema v1 作为结果格式参考。

**Spec:** `docs/validation/mobile-vlm.md`、`docs/superpowers/specs/2026-10-15-ondevice-ai-sdk-design.md` §7/§11。

## Global Constraints

- 基础 SDK 最低系统继续为 iOS 15；VLM 探针只能声明 iOS 18+。
- checkpoint 使用 `mlx-community/SmolVLM2-500M-Video-Instruct-mlx`，下载前重新读取并校验 observed SHA `fa57db46815177fbdfd65cc85a2b3416a8332268`。
- MLXVLM/MLXLMCommon 使用不可变 commit；禁止依赖 `main` 分支或浮动模型配置。
- 不提交模型权重、用户图片、音频、截图或包含个人信息的输出。
- 不在 P9 同时接入 256M、Florence-2 或 MiniCPM-V。
- 模型未下载、构建失败、设备内存不足、权限/资产缺失必须记录为 `unavailable`，不能伪造输出。

## Review Focus

- 模型 revision 与实际下载文件 hash 不一致时必须 fail closed；测试覆盖 manifest 校验。
- iOS 15 基础 target 的依赖图不能出现 MLX；测试检查 package/product 链接边界。
- 图片取消、会话取消和第二次会话必须释放模型 lease、任务和 Metal/MLX 资源；测试覆盖连续两次会话。
- 首 token 成功但完整生成取消时，JSON 必须记录 cancelled 终态，不得标记 pass。
- 峰值内存无法可靠采集时写 `null/unmeasured`，不得用常驻内存瞬时值冒充峰值。

### Task 1: Pin the model and runtime manifest

**Files:**
- Create: `docs/validation/p9-smolvlm-manifest.json`
- Create: `tools/validate_vlm_manifest.py`
- Test: `tools/tests/test_vlm_manifest.py`

**Interfaces:**
- Manifest fields: `modelId`, `modelRevision`, `runtime`, `runtimeRevision`, `files`, `license`, `platformMin`, `status`.
- Validator rejects floating revisions, non-iOS18 platform, and unknown runtime; `status=verified` additionally requires SHA256 for every file. A `candidate` manifest must not be used for device inference.

- [ ] Write failing tests for a valid pinned manifest and each rejection case.
- [ ] Run `python3 -m unittest tools.tests.test_vlm_manifest -v` and confirm failures are from the missing validator.
- [ ] Implement the validator with standard library JSON/hash checks only; do not download weights.
- [ ] Run the focused tests and record the observed checkpoint SHA separately from the manifest until the download is verified.
- [ ] Commit manifest and validator independently.

### Task 2: Create an isolated iOS 18+ probe target

**Files:**
- Create: `probes/ios-vlm/VLMProbe.xcodeproj/project.pbxproj`
- Create: `probes/ios-vlm/VLMProbeApp.swift`
- Create: `probes/ios-vlm/ProbeRunner.swift`
- Test: `probes/ios-vlm/Tests/ProbeRunnerTests.swift`
- Modify: no base SDK target files

**Interfaces:**
- `VLMProbe.run(input: URL, manifest: VLMManifest) async throws -> VLMRunResult`.
- `VLMRunResult` includes `status`, `actualBackend`, `modelRevision`, `runtimeRevision`, `fixtureHash`, `firstTokenMs`, `totalMs`, `peakMemory`, `cancelled`, and structured text output.

- [ ] Write tests for input hash, missing manifest, cancellation terminal state, and no-output on unavailable runtime.
- [ ] Run tests before adding MLX dependencies; expected compile failure only for the unimplemented probe type.
- [ ] Add MLX/MLXLMCommon/MLXVLM as exact revision dependencies in the separate Xcode project, with deployment target iOS 18.0; avoid new dependencies in the base package.
- [ ] Implement single-image generation, cancellation, and deterministic cleanup; never expose third-party types from base SDK modules.
- [ ] Run the probe tests on an available iOS 18+ simulator without claiming device inference.
- [ ] Commit the isolated target.

### Task 3: Download and verify the fixed model asset

**Files:**
- Modify: `docs/validation/p9-smolvlm-manifest.json`
- Create: `tools/fetch_vlm_asset.py`
- Test: `tools/tests/test_vlm_asset.py`

**Interfaces:**
- `fetch_vlm_asset(manifest, destination) -> VerifiedAsset` verifies every listed file SHA256 before making the asset visible to the probe.

- [ ] Write tests for partial download, wrong hash, and atomic publish.
- [ ] Run tests and confirm the wrong-hash path fails.
- [ ] Fetch only the pinned checkpoint revision into ignored `artifacts/P9`; never write weights into source control.
- [ ] Recompute and record actual file hashes and total bytes in the manifest.
- [ ] Run validator and asset tests.
- [ ] Commit only the manifest/tool/tests, not `artifacts/P9`.

### Task 4: Run the iOS physical-device probe

**Files:**
- Create: `artifacts/P9/ios-vlm-run.json` (ignored)
- Create: `artifacts/P9/ios-vlm-report.json` (ignored)
- Modify: `docs/validation/mobile-vlm.md`
- Modify: `docs/ROADMAP.md`

**Interfaces:**
- Device run uses a fixed non-personal fixture; report stores hashes and structured output only.

- [ ] Build the probe for a connected iOS 18+ device or record `unavailable` with exact build/device reason.
- [ ] Run one successful image generation, one cancellation, and one repeated session.
- [ ] Verify output is non-empty, cancellation reaches one terminal state, and resources are released between sessions.
- [ ] Generate report grouped by device/OS/model/runtime/input hash.
- [ ] Update P9 status only from the raw JSON evidence; do not infer memory or quality from build success.
- [ ] Commit docs and report metadata; keep raw weights and personal fixtures ignored.

## Verification Commands

```sh
python3 -m unittest tools.tests.test_vlm_manifest tools.tests.test_vlm_asset -v
xcodebuild -project probes/ios-vlm/VLMProbe.xcodeproj -scheme VLMProbe -destination 'platform=iOS Simulator,name=<available-simulator>' test
xcodebuild -project probes/ios-vlm/VLMProbe.xcodeproj -scheme VLMProbe -destination 'id=<connected-device>' build
python3 tools/benchmark_report.py artifacts/P9/ios-vlm-run.json --output artifacts/P9/ios-vlm-report.json
```

A P9 pass requires all four tasks, a real structured output on an iOS 18+ device, and raw evidence whose model/runtime/input identifiers match the pinned manifest. A build-only or host-only result remains `unavailable`.
