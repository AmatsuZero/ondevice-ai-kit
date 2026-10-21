# iOS 麦克风 ASR Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为原生 SDK 与 KMP Demo 增加可主动结束输入、可取消且强制 on-device 的 iOS 麦克风 ASR。

**Architecture:** 保留文件 API，添加可选麦克风 task/backend 接口；Apple capture 通过宿主提供的 session lease 获得录音资源，复用当前识别终态/drain 机制。bridge/VM 使用可选 finishInput 句柄，host 依输入类型处理权限。

**Tech Stack:** Swift 5.9、Speech、AVFAudio、Kotlin Multiplatform/Compose、XCTest/JVM tests、既有 Ruby 生成器。

**Spec:** `docs/superpowers/specs/2026-10-20-ios-microphone-asr-design.md`

## Global Constraints

- iOS15/macOS12 基础编译下限不变；麦克风系统实现仅在 iOS 启用。
- 原有文件 `SpeechRecognitionRequest(locale:audioFile:)` 和第三方文件 backend 源兼容。
- 不增加依赖、后台录音、网络 fallback、语言资源自动安装或自然语音/转写日志。
- 保护 `demo/iosApp/OnDeviceAIDemo.xcodeproj/project.pbxproj`、`docs/validation/face-identity.md`，逐路径 stage。
- `finishInput` 正常结束输入，`cancel` 丢弃结果；partial 不能成为 final；任务/输入清理后才能回调和重入。
- 不把本计划的本地门禁作为旧 Task7 UI/Done 独立审查通过；远端同步仍受旧门禁约束。
- 由当前 roadmap 自动推进约定授权本地可逆工作；用户未逐字审阅本设计/计划，不能伪称已审阅。用户反馈随时修订后续切片。

## Review Focus

1. start 或 session acquire 挂起时 close/cancel，晚到资源必须回收，不能发布新任务；Task2/3。
2. finish/cancel 在 native task 返回之前到达，cancel 优先，且旧句柄不能结束新一轮；Task3/4。
3. buffer callback 与 removeTap/endAudio/release 并发，先停止 admission 再 join delivery；Task3。
4. file 与 mic 权限/能力不同，mic 拒权不阻断文件，Explicit 不支持 mic 不 fallback；Task1/2/5。
5. finish 后 interruption/background、清理异常与迟到 final 交错，至多一个终态且前台不自动录音；Task3/4/5/6。

### Task 1: 可选麦克风契约与能力元数据

**Files:** Create `apple/Sources/ODAIKitSpeech/SpeechMicrophone.swift`、`apple/Tests/ODAIKitSpeechTests/SpeechMicrophoneContractTests.swift`；Modify `apple/Sources/ODAIKitSpeech/SpeechClient.swift`。

**Interfaces:** Produce `SpeechRecognitionInputKind: String, Sendable, Equatable`（`audioFile`、`microphone`）；`SpeechMicrophoneRecognitionRequest(locale:) throws`（canonical locale）；`SpeechRecognitionInputError.unsupportedInput`；`SpeechMicrophoneRecognitionTask: Sendable`（`result() async throws -> SpeechRecognitionResult`、`finishInput() async`、`cancel() async`）；`SpeechMicrophoneRecognitionBackend: SpeechRecognitionBackend`（`startMicrophone(_ request: SpeechMicrophoneRecognitionRequest) async throws -> any SpeechMicrophoneRecognitionTask`）。`SpeechRecognitionBackend.supportedInputs` 默认 `[.audioFile]`；`SpeechBackendDescriptor.supportedInputs` 和 list/select 都使用真实 backend 元数据，TTS 为 `[]`。

- [x] RED：`testMicrophoneRequestCanonicalizesAndRejectsBlankLocale`；`testLegacyFileRequestAndBackendRemainSourceCompatible`；`testListAndSelectPreserveInputSupportEvenWhenUnavailable`；`testTtsDescriptorsDoNotAdvertiseRecognitionInputs`。旧 conformer 不实现新属性，仍成功注册并调用文件 recognize；unavailable 描述仍保留其真实支持输入，select 仍失败不 fallback。
- [x] Run `rtk proxy swift test --disable-sandbox --package-path apple --filter SpeechMicrophoneContractTests`，预期缺类型/属性而失败，保存原始 RED。
- [x] 实现 additive 类型、默认 protocol extension 与 registry metadata；不创建 engine、请求权限或把 AVFAudio 引入契约模块。
- [x] GREEN：重跑定向测试，再 `rtk proxy swift test --disable-sandbox --package-path apple`；预期零失败。检查文件 API 与已有测试不变。
- [x] 任务范围审读并逐路径提交 `feat: add optional microphone speech contracts`；独立审查若服务不可用，明确 pending，不宣称 CLEAR。

### Task 2: native client 麦克风 admission 与生命周期

**Files:** Modify `SpeechClient.swift`；Create `apple/Sources/ODAIKitSpeech/SpeechMicrophoneClientTask.swift`、`apple/Tests/ODAIKitSpeechTests/SpeechMicrophoneClientTests.swift`。

**Interfaces:** Consume Task1。Produce `SpeechRecognitionClient.startMicrophone(_ request: SpeechMicrophoneRecognitionRequest, backend: SpeechBackendSelection = .default) async throws -> any SpeechMicrophoneRecognitionTask`；backend task retain 到结果/drain，late start 清理不得绕过 close。所有 task result waiter 共享一次终态。

- [x] RED：Explicit missing/unavailable/file-only 不 fallback；在 registry availability 与 native start 各自挂起时 close；caller cancellation 后 native 忽略取消再返回；finish/cancel 幂等；多个 result waiter；旧 handle 不影响下一轮；metadata query 不启动音频。使用真实 client 与可控 backend/barrier。
- [x] 定向 `swift test ... --filter SpeechMicrophoneClientTests`，观察行为或缺 API RED。
- [x] 实现 start/drain/close join 与 managed task，沿用 registry selection；unsupported 返回独立 typed input error；release/结果处理不包含用户文本日志。
- [x] 同上定向及 Swift 全套 GREEN；close/drain race 旧测试保持通过。
- [x] 逐路径提交 `feat: admit and drain microphone speech tasks`，记录 review 与测试。

### Task 3: Apple capture、session lease 和统一 backend 终态

**Files:** Create `apple/Sources/ODAIKitAppleSpeech/AppleSpeechMicrophone.swift`、`AppleSpeechAudioSession.swift`、`apple/Tests/ODAIKitSpeechTests/AppleSpeechMicrophoneTests.swift`；Modify `AppleSpeechRecognizerBackend.swift`，必要时从中提取共同 operation/inbox 文件，保护现有行为。

**Interfaces:** Consume Task1/2。Produce iOS `AppleSpeechAudioSessionProvider.acquire() async throws -> any AppleSpeechAudioSessionLease`；lease `release() async throws` 幂等；Apple backend 新增显式 provider 构造方式、支持输入描述与 `startMicrophone`。内部 capture task 管理 engine/tap/request/observers，复用现有 final/completed/drain 规则。

- [x] RED：session失败、无有效format、engine启动失败逐步回滚；buffer barrier 在途不释放 lease；finish 正常endAudio一次并等待final/completed；partial-only为NO_RESULT；cancel/timeout/interruption/route/reset丢弃迟到final；start挂起close；文件与mic互斥；旧handle/oldcallback不影响新任务；session清理失败不伪成功。
- [x] 定向 Swift 测试观测 RED，iOS-only capture 通过 simulator XCTest fixture 验证系统类型调用；macOS fake测试不应跳过核心生命周期断言。
- [x] 实现 iOS capture 与 lease注入，on-device gate、有限timeout、无录音文件；新增路径不修改普通file-only构造行为。停止buffer admission→stop engine/remove tap→drain delivery→endAudio/cancel→native completed→lease release。
- [x] Swift全套、iOS simulator与device编译通过；现有AppleSpeechRecognizerBackendTests不得回退，API15 availability编译核对。
- [x] 独立资源边界审查；容量不可用保留pending；逐路径提交 `feat: capture on-device microphone speech with leased audio`。

### Task 4: iOS bridge 和共享 finish-input 状态

**Files:** Modify `kmp-binding/src/commonMain/kotlin/io/ondeviceai/binding/SpeechBridge.kt`、`kmp-binding/apple/IOSSpeechBridge.swift`、`demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/SpeechViewModel.kt`、`SpeechScreen.kt`、`SpeechViewModelTest.kt`、`probes/ios-speech-bridge/IOSSpeechBridgeTests.swift`。

**Interfaces:** Produce `SpeechBridgeMicrophoneTask: BridgeTask { fun finishInput() }`，VM `finishInput()` 与只在当前句柄支持时可见的finish能力；既有BridgeTask/Android task无需改签名。原文件 lease 路径保持。

- [x] RED：finish/cancel before native handle、同步失败/同步terminal、不复活旧句柄、单音频槽、file lease不走mic、finish后controls保持禁用、cancel覆盖finish、close join和terminal callback重入。
- [x] shared测试与双ABI bridge XCTest定向观察RED。
- [x] 实现动态native输入metadata、mic分支、可选finish句柄与UI动作；仍只在native drain后发terminal。
- [x] `rtk proxy ./gradlew :kmp-binding:allTests :demo:shared:allTests :demo:androidApp:assembleDebug :demo:shared:linkDebugFrameworkIosSimulatorArm64 --console=plain`，双ABI bridge XCTest；预期全部通过。
- [x] review并提交 `feat: bridge microphone finish and cancellation state`。

### Task 5: iOS host 权限/session provider 与工程

**Files:** Modify `demo/iosApp/SpeechHost.swift`、`probes/ios-speech-bridge/SpeechHostTests.swift`、`scripts/generate-speech-demo.rb`、`scripts/generate-speech-bridge-tests.rb`、`tools/tests/test_speech_demo_project.py`；Create `demo/iosApp/SpeechAudioSession.swift`。

**Interfaces:** Host按输入申请权限：file仅Speech，mic Speech后record；Demo实现独占session lease并恢复category/mode/options，不猜测旧active状态；generated Demo有mic usage description，原工程无修改。

- [x] RED：拒绝mic仍可file；授权晚到/background/close不启动；重试不重复prompt；capture结束恢复session配置且先停I/O；生成器缺新source零输出，只有新工程含mic说明、无background/audio sharing键。
- [x] Host XCTest、Python suites观察RED。
- [x] 实现带generation权限gate，iOS17新API和iOS15–16fallback；MainActor交付；注入session provider，stage仅task文件。
- [x] Swift/Host/工具测试，双framework和独立simulator/device/旧App构建通过；保护hash一致。
- [x] review并提交 `feat: wire iOS microphone permissions and audio ownership`。

### Task 6: 真机、分发、独立审查与记录

**Files:** Modify iOS `SpeechDemoUITests.swift`、`README.md`、`docs/ROADMAP.md`、当前handoff；Create `docs/validation/ios-microphone-asr.md`；必要时新增受控native capture probe，不记录音频/转写。

**Interfaces:** 所有实现任务的可审查证据，区分native readiness、UI RUNNING和实际转写；只有安排好受控朗读才开始录音。

- [ ] 设备实时发现；本项目App中验证录音授权/拒权恢复、结束取得final、取消/drain/同进程重入、TTS往返、后台取消/前台不重启；未安排朗读时保持自然语音未验，不采旁人声音。
- [ ] 必要缺陷回所属Task测试锁定RED→GREEN；原有文件UI门禁仅在新改动影响它时复跑。
- [ ] 完整verify-apple/verify-android、binding/shared/Python门禁；fresh七XCFramework和Speech contract/backend原生消费者裁剪检查。
- [ ] 本包独立审查及旧Task7审查分别记录；Critical/Important修复后相关门禁重跑。缺设备/模型容量如实保留，不能标包完成或提前同步。
- [ ] commit验证记录并更新roadmap，继续下一可独立工作包。P6其他缺口和P11不随本包关闭。

## 自审与执行次序

类型/metadata→client lifecycle→Apple capture→bridge/VM→host→验收顺序连续；Task1的纯契约不依赖后续平台实现。5条Review Focus均有明确测试归属。所有产品实现以RED为先；没有把“模型不可用”升级为阻止全部roadmap工作的规则。独立review/设备/远端边界维持原约定。
