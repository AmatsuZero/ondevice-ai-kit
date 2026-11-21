# Multimodal SDK Foundation Implementation Plan

> Execute inline in current session with superpowers:executing-plans, per latest user instruction. No subagents.

**Goal:** 将已选 P9 单模型路线所需的纯 SDK 契约/协调器独立分发；不在 Foundation 中加载 MLX/权重。
**Spec:** `docs/superpowers/specs/2026-10-22-multimodal-sdk-foundation-design.md`
**Baseline:** `ae76aa3` / `feat/sdk-foundation`。
**Stack:** Swift concurrency、Kotlin coroutines（既有版本）、SwiftPM/Xcodeproj/Maven local；无新第三方依赖。

## Global Constraints

- iOS15/macOS12、Android API26 基础下限保持。
- 不派子代理、不push/远端发布/下载模型资源/录音/读取私人媒体/修改签名资产。
- 保护 `demo/iosApp/OnDeviceAIDemo.xcodeproj/project.pbxproj` 与未追踪 `docs/validation/face-identity.md` 不修改/暂存。
- 单图片+prompt SDK 首包不等于 P9 完整交付；MLX/短视频/KMP/Demo/质量/最低系统设备继续列为后续项。
- 每Task RED→GREEN、范围自审、精确文件提交；用户已明确当前会话执行，独立review保持pending，不冒称APPROVE。

## Review Focus

- 取消后 backend 忽略取消并抛普通错误，调用方仍取消终态（Tasks1/2）。
- listBackends 在 availability 内挂起时 close 必须 join，再关闭 backend（Tasks1/2）。
- 累计文本更新序号倒序/重复/旧请求晚到、取消和close后不能污染回调（Tasks1/2）。
- Kotlin close 等待者被取消，内部cleanup不能中断；某backend意外close失败也要关闭余下backend并保留共享终态（Task2）。
- Foundation二进制消费者仅Core+Multimodal，无MLX/其他能力传递依赖，deployment target保持15（Task3）。

### Task 1: Swift contracts and generation client

Create:
- `apple/Sources/ODAIKitMultimodal/MultimodalContracts.swift`
- `apple/Sources/ODAIKitMultimodal/MultimodalGenerationClient.swift`
- `apple/Tests/ODAIKitMultimodalTests/MultimodalContractsTests.swift`
- `apple/Tests/ODAIKitMultimodalTests/MultimodalGenerationClientTests.swift`
Modify: `apple/Package.swift`。

Produces spec精确命名的 request/model/update/output/result/selection/descriptor/error、`MultimodalGenerationBackend`、`MultimodalGenerationClient`。Foundation只依赖Core。

- [ ] 测试scaffold先接test target与仅import的空实现文件，写契约 RED：非file URL/空白prompt/max<=0、空identity/结果/backendId/负sequence、默认128且不读取不存在的图片；运行 `rtk proxy swift test --package-path apple --filter MultimodalContractsTests`，Expected缺失symbol编译失败。
- [ ] 实现spec契约；同命令 GREEN，Expected全部契约测试通过。
- [ ] 写client RED：缺默认/重复注册、strict Explicit unknown/unavailable不回退、按ID排序查询/关闭后拒绝；成功返回实际backend/model/text并保留原输入；生成错误仍drain；挂起availability占槽busy；cancel/drain前busy、晚到success/error取消胜出；乱序/重复/旧update屏蔽；query/close join、并发close各backend一次。用actor gate/continuation控制。
- [ ] 运行targeted测试观察缺client RED，再实现actor client与单槽operation、mandatory drain、query join/共享close。
- [ ] `rtk proxy swift test --package-path apple`，Expected已有+新增XCTest全部通过，无失败summary。按契约/竞态逐项范围自审、记录count/日志、精确暂存提交。

### Task 2: Android equivalent module

Create:
- `android/multimodal/build.gradle.kts`
- `android/multimodal/src/main/AndroidManifest.xml`
- `android/multimodal/src/main/kotlin/io/ondeviceai/multimodal/MultimodalContracts.kt`
- `android/multimodal/src/main/kotlin/io/ondeviceai/multimodal/MultimodalGenerationClient.kt`
- `android/multimodal/src/test/kotlin/io/ondeviceai/multimodal/MultimodalGenerationClientTest.kt`
Modify: `settings.gradle.kts`。

Produces Task1同名语义API，包 `io.ondeviceai.multimodal`；`File`替代URL；`MultimodalBackendSelection.Default/Explicit`、原生CancellationException。

- [ ] RED先写注册/API/契约及真实client+受控backend测试；Expected缺类型/缺实现失败。仅复用Core与项目既有coroutines/test版本，不引入新库。
- [ ] 测试覆盖Task1全部生命周期/选择边界，以及关闭等待者取消/意外backend.close失败仍关闭其他项/重复close一致终态。用CompletableDeferred/CountDownLatch控制，不用长sleep。
- [ ] GREEN实现串行注册状态、独立generation工作与cancel propagation、NonCancellable drain/共享close，更新严格序号过滤。
- [ ] `rtk proxy ./gradlew :android:multimodal:testDebugUnitTest :android:multimodal:lintDebug --console=plain` Expected成功并检查XML测试数；然后现有 `scripts/verify-android.sh`回归。范围自审后精确提交。

### Task 3: Distribution and trimmed consumers

Modify:
- `scripts/generate-distribution-project.rb`
- `scripts/package-apple.sh`
- `scripts/generate-binary-consumer.rb`
- `tools/tests/test_distribution_project.py`；新增 `tools/tests/test_multimodal_distribution.py`
- `apple/OnDeviceAIKit.xcodeproj/` 的SDK分发工程/共享scheme（不是受保护Demo工程）
- `scripts/verify-apple.sh` / `scripts/verify-android.sh` 增加新模块/独立consumer门禁。
Create: `examples/android-multimodal-native/{build.gradle.kts,src/main/AndroidManifest.xml,src/main/kotlin/io/ondeviceai/example/multimodal/MainActivity.kt}`；`settings.gradle.kts`include。

Consumes Tasks1/2 public APIs；Produces第十个Apple XCFramework及`io.ondeviceai:multimodal:0.1.0-SNAPSHOT` Maven local、Core+Multimodal-only consumers。

- [ ] RED生成器fixture：新target全部source membership、只依赖Core、SDKDistribution包含target；binaryconsumer `--multimodal`仅Core+Multimodal且source引用实际类型；缺artifact/现有输出拒绝覆盖；package脚本列出新product。Expected新增检查失败。
- [ ] GREEN更新generators/package脚本并生成SDK工程，保持现有products/source/optionalframework；工具全套 Expected通过。
- [ ] fresh `rtk proxy env ODAI_ARTIFACTS_DIR=artifacts/verification/multimodal-foundation-20261022/distribution sh scripts/package-apple.sh`，Expected10个含device/simulator slices的XCFramework。
- [ ] `generate-binary-consumer.rb --multimodal`生成到fresh目录；device+simulator/iOS15target build；读取二进制依赖/Swiftinterface，Expected不含MLX/其他能力。
- [ ] Maven local发布Core+Multimodal，原生consumer project与`-PusePublishedSdk=true`真实构建，读取POM依赖Expected仅Core与既有coroutines，不能把项目依赖build当已验证Maven消费。
- [ ] 范围自审/准确日志/精确提交，保护hash不变。

### Task 4: Full gates and handoff

Create `docs/validation/multimodal-sdk-foundation.md`；update README、ROADMAP、handoff与本包ledger。

- [ ] `scripts/verify-apple.sh`、`scripts/verify-android.sh`、工具全套（指定既有Ruby3.4wrapper）通过；已通过且源码未再变化的分发不重复重打。
- [ ] 作者单独步骤final review，按Review Focus逐项审读；问题先RED→GREEN修复。记录自审不能替代独立审查；用户禁止子代理下不调reviewer。
- [ ] 文档分别报告contract/client/分发/consumer证据和未接真实backend/device/质量，不用旧probe替代SDK验收；更新当前handoff首个未完成入口为MLX后端/后续视频与语音设备缺口。
- [ ] local links/diffcheck/保护hash、精确暂存提交。保留scratch ledger/日志，不push。

## Preflight / coverage

Task1契约供Task2镜像、Task3消费者；均统一imageFile/prompt/maxOutputTokens、cumulative sequence/text、identity/result/selection命名。Task1/2 client输出为纯值，不复用VideoOutputLease；Task3不引入MLX，也不提升基础下限；Task4不把contract通过称作P9完成。每Task覆盖spec相应section；新runtime与backend研究/下载不在本包。
