# KMP Video Bridge / Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 让 KMP Demo 通过独立 VideoBridge 使用 Video SDK，明确文件/输出所有权与取消边界。

**Architecture:** 平行 Speech 的 callback bridge，原生 adapter 持有 opaque input/output lease；共享 VM 不依赖平台文件类型。宿主才链接可选 AppleVideo。

**Tech Stack:** Kotlin Multiplatform / Compose、Swift concurrency、现有 Kotlin coroutines / Video SDK，无新依赖。

**Spec:** `docs/superpowers/specs/2026-10-21-video-bridge-demo-design.md`

## Global Constraints

- 保持 iOS 15 / Android API 26 基础下限。
- 禁止 push、发布、模型/资源下载、自动录音、读取私人媒体、修改签名资产。
- 两个保护文件不覆盖、不暂存、不提交。
- native export 中途取消仍是未验证项。
- UI dispatcher admission/callback；cancel 等 drain；close 不撤销已交付输出。

## Review Focus

- 同步 terminal 不能让 VM 留下活动 handle（Task4）。
- cancel 后 native 晚到 output 必须释放且不能成功回调（Tasks2/3）。
- close 等待 query 和 process，宿主 scope 取消不能跳过 drain（Tasks2/3）。
- 输出 release 失败保留 token、成功后重复 release 幂等（Tasks2/3）。
- picker 取消/输入替换不能撤销活动输入 lease，离页迟到 output 应释放（Tasks3/4/5）。

### Task 1: Common contract and Swift ABI

Files: create `kmp-binding/src/commonMain/kotlin/io/ondeviceai/binding/VideoBridge.kt`, `kmp-binding/src/commonTest/kotlin/io/ondeviceai/binding/VideoBridgeContractTest.kt`.
Produces: spec 中精确命名的 DTO、VideoBridge 与 callback、UnavailableVideoBridge，供所有后续任务使用。

- [ ] RED：测试 blank selection/input/output、非法 progress/metadata、unavailable 不成功、closed 优先与重复 close/release、platform failure 隐私；未定义类型预期编译失败。
- [ ] GREEN：实现契约，`rtk proxy ./gradlew :kmp-binding:allTests :kmp-binding:linkDebugFrameworkIosSimulatorArm64 --console=plain` 成功。
- [ ] 范围审查 Kotlin→Swift 导出、缺模块/close 行为，记录日志，精确暂存并提交。

### Task 2: Android adapter

Files: create `kmp-binding/src/androidMain/kotlin/io/ondeviceai/binding/AndroidVideoBridge.kt`, `android/video/src/test/kotlin/io/ondeviceai/video/AndroidVideoBridgeTest.kt`; modify `kmp-binding/build.gradle.kts` 和现有测试 fixture 的依赖接线。
Consumes: Task1 VideoBridge；native VideoProcessingClient。Produces: `AndroidVideoBridge(client, inputs, scope)`、AndroidVideoInputProvider/Lease、原生 outputFile(reference) resolver。

- [ ] RED：真实 client + 可控 backend：strict selection、progress、cancel/drain busy、晚到成功释放、query/close join、缺模块、输入 lease drain 后释放、close 后 output release、释放失败重试/幂等、platform 文本不泄漏。
- [ ] GREEN：实现单槽任务/独立 worker job、输出 token 表和 native 错误映射。
- [ ] Run Android fixture tests、binding allTests、lint；范围审查后提交。

### Task 3: iOS adapter

Files: create `kmp-binding/apple/IOSVideoBridge.swift`, `kmp-binding/apple/IOSVideoInput.swift`, `probes/ios-video-bridge/` fixture 与 `scripts/check-ios-video-bridge.sh`。
Consumes: Task1 Swift ABI、native VideoProcessingClient。Produces: `IOSVideoBridge`、IOSVideoInputProvider/Lease、原生 outputURL(reference) resolver。

- [ ] RED：真实 Swift client fixture 验证 Task2 同等边界，以及未知 Swift selection conformer 拒绝。
- [ ] GREEN：MainActor adapter，input lease 跨 drain，output lease 交付/释放。
- [ ] Run 新 fixture/device+simulator ABI 编译；范围审查后提交。

### Task 4: Shared Demo state and screen

Files: create `demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/VideoViewModel.kt`, `VideoScreen.kt`, `demo/shared/src/commonTest/kotlin/io/ondeviceai/demo/VideoViewModelTest.kt`；modify existing entry points。
Consumes: Task1 VideoBridge。Produces: `VideoViewModel` 与 `VideoActions` 的 opaque 输入/结果操作；旧入口兼容。

- [ ] RED：同步 callback、cancel 等 terminal、generation、离页/后台取消、晚到结果释放、替换结果释放、closed 不重启。
- [ ] GREEN：状态机与 Compose 控件，固定profile提示如实呈现。
- [ ] Run shared allTests + simulator framework；范围审查后提交。

### Task 5: Native hosts / generated project

Files: create `demo/iosApp/VideoHost.swift`, `VideoInputStore.swift`, `VideoFilePicker.swift`, Android `VideoHost.kt`；modify `scripts/generate-speech-demo.rb` 或独立 video 生成器、`DemoController.swift`、`MainActivity.kt`，新增 tools generator / host tests。
Consumes: Tasks2–4。Produces: 用户主动文件选择、结果播放/分享/释放、后台取消、仅 host 链接 AppleVideo 的独立工程。

- [ ] RED：缺模块、factory 错误、picker 取消/替换、后台生命周期、生成工程 optional video/source membership 回归。
- [ ] GREEN：双端宿主接线；Android 无 backend 保持明确 unavailable。
- [ ] Run tools tests / Android unit+lint+APK / signed device+unsigned simulator builds；不修改受保护工程，范围审查后提交。

### Task 6: Distribution and device validation

Files: create `docs/validation/video-bridge-demo.md`；update README、ROADMAP、handoff、ledger。

- [ ] 固定公开 fixture 的完整 Demo 选择/成功/取消/重入/后台/输出 release 验证；不能运行的设备项明确 unverified。
- [ ] Apple/Android 本地总门禁、裁剪 consumer 按实际 diff 运行；不重复不相关已通过验证。
- [ ] 一次独立最终审查；发现问题 RED→GREEN 修复。服务失败记 pending，不冒称 APPROVE。
- [ ] 核对保护 hash、diff check、精确暂存提交；不 push。P7/roadmap 总体保持未完成，入口指向首个未完成任务。
