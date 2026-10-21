# Video SDK Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在双端提供可裁剪的视频超分契约、严格选择与取消/drain/输出 lease 生命周期。

**Architecture:** 独立 Swift/Android video 模块只依赖 Core；真实 client 协调可插拔 backend 和输出 lease，后续系统处理器与 KMP/Demo 分包接入。

**Tech Stack:** Swift5.9、Kotlin/JVM、已有 kotlinx-coroutines、XCTest/JUnit、Ruby分发生成器。

**Spec:** `docs/superpowers/specs/2026-10-21-video-sdk-foundation-design.md`

## Global Constraints

- iOS15/macOS12、Android API26；无新第三方依赖，不引用 AVFoundation/KMP/Face/Identity/Speech。
- 音轨默认 preserve，Explicit不回退，不自动下载/录音/写相册，结果由调用方 release。
- 保护原Demo pbxproj与face-identity.md；生成物/报告只写忽略目录。独立审查/真机/远端门禁分开。
- 按roadmap自动实施本地可逆切片，不伪称人工逐字审阅；先测试RED再产品实现。

## Review Focus

1. availability或process忽略取消时，close必须等待排空，不能发布晚到结果；Task2/3 barrier测试。
2. 取消后的晚到output lease必须释放且释放失败不能吞；Task2/3清理失败测试。
3. 旧进度/close不能污染下一轮，已交付lease不能被close删除；Task2/3 generation/ownership测试。
4. metadata不可用不等于不支持倍率/音轨；Explicit不静默改选；Task1/2/3严格选择测试。
5. 分发只引入Core和video，不把未来系统/模型运行时带进契约；Task4真实依赖/消费者编译验证。

### Task 1: Apple 契约

**Files:** Create `apple/Sources/ODAIKitVideo/VideoContracts.swift`、`apple/Tests/ODAIKitVideoTests/VideoContractTests.swift`；Modify `apple/Package.swift`。

**Interfaces:** Produce VideoAudioPolicy、VideoProcessingRequest(inputFile:scaleFactor:audioPolicy:) throws、VideoOutputInfo(width:height:frameCount:durationSeconds:audioTrackCount:) throws、VideoProcessingProgress(completedFrames:totalFrames:) throws、VideoOutputLease(file,info,release async throws)、VideoSuperResolutionBackend(backendId,supportedScaleFactors,supportedAudioPolicies,availability/process/drain/close)。Produce VideoBackendSelection(default/explicit)、VideoBackendDescriptor、VideoClientError。

- [ ] RED：请求拒绝http/倍率1；默认preserve；Info拒绝零尺寸/帧/NaN/Infinity/负音轨；progress拒绝负数/超总数。运行 `rtk proxy swift test --disable-sandbox --package-path apple --filter VideoContractTests`，Expected missing模块或类型失败。
- [ ] 实现纯契约，backend process使用 async throws返回lease，progress为Sendable回调；列表支持元数据不可变。
- [ ] GREEN：定向测试和Swift全套0失败，提交 `feat: define optional video processing contracts`。

### Task 2: Apple client admission/drain

**Files:** Create `apple/Sources/ODAIKitVideo/VideoProcessingClient.swift`、`apple/Tests/ODAIKitVideoTests/VideoProcessingClientTests.swift`。

**Interfaces:** Consume Task1；Produce actor VideoProcessingClient.init(defaultBackendId:backends:) throws、listBackends async throws、process(_:backend:progress:) async throws -> VideoProcessingResult、drain async、close async。

- [ ] RED：真实client注册校验、排序与unavailable metadata、Explicit missing/unavailable/unsupported无fallback、挂起availability/process/drain的cancel/close、并发close、late lease release/cleanup failure、进度generation与结果跨close保留。Expected缺client或断言失败，保存日志。
- [ ] 实现单槽worker、父任务取消传递、排空后终态、输出所有权转移、query join和共享close；不做编解码/文件发布。
- [ ] GREEN：定向及Swift全套0失败，范围审读并提交 `feat: coordinate video backend selection and draining`。

### Task 3: Android 对等契约/client

**Files:** Create `android/video/build.gradle.kts`、`android/video/src/main/kotlin/io/ondeviceai/video/VideoContracts.kt`、`VideoProcessingClient.kt`、`android/video/src/test/kotlin/io/ondeviceai/video/VideoProcessingClientTest.kt`；Modify `settings.gradle.kts`。

**Interfaces:** Consume语义Task1/2；File输入，suspend process/drain/close、输出lease.release。保留同名选择/元数据/错误，不引入Swift或KMP类型。

- [ ] RED：与Task1/2相同关键行为，kotlin真实client+受控backend。`rtk proxy ./gradlew :android:video:testDebugUnitTest --console=plain` Expected missing类型或断言失败。
- [ ] 实现已有coroutines版本，worker独立于调用方取消，NonCancellable drain与lease cleanup；取消期间拒绝新任务直到drain结束。
- [ ] GREEN：video tests/lint和已有Android总门禁，提交 `feat: add Android video processing facade`。

### Task 4: 本地分发/审查/恢复入口

**Files:** Modify `scripts/generate-distribution-project.rb`、`scripts/package-apple.sh`、`tools/tests/test_distribution_project.py`、`scripts/verify-android.sh`、README、ROADMAP、当前handoff；生成SDK project；Create `docs/validation/video-sdk-foundation.md`。

**Interfaces:** Consume native模块；Produce独立ODAIKitVideo XCFramework及Android video本地Maven；旧产品隔离不变。下一入口AppleVideoToolbox backend。

- [ ] RED：真实生成工程检查新video target只有Core依赖、新源完整注册且重复生成稳定。Expected缺video target失败。
- [ ] 接入产品列表/依赖，verify-android加入video，fresh archive/8XCFramework和Core+Video consumer实际编译；Android本地发布/POM不引入其他能力。
- [ ] GREEN：工具全套、Apple/Android门禁；独立review可用则执行，否则pending，禁止假称APPROVE。写清仅foundation完成、P7未完、下一包入口；逐路径提交。
