# Speech Bridge 与 Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让现有 KMP Demo 真实调用两端系统 TTS、iOS 文件 ASR 与 Android 麦克风 ASR，保留严格后端选择和可裁剪原生模块。

**Architecture:** 在 kmp-binding 添加独立语音 DTO/回调契约，两端 adapter 独占注入的专用语音 Client。共享 ViewModel 管理展示与取消状态，native adapter 管理任务 admission/drain，host 管理权限与文件 lease。iOS 使用忽略目录中的独立生成工程保留用户工程。

**Tech Stack:** Kotlin Multiplatform、Compose、coroutines、Swift concurrency、现有 ODAIKitSpeech/AppleSpeech 与 Android system-tts/system-asr、Ruby xcodeproj；不增加第三方依赖。

**Spec:** [已认可规格](../specs/2026-10-19-speech-demo-bridge-design.md)，提交 `81cb8b2`；用户已认可书面规格并要求编写本计划。本计划待审阅，尚未批准实施。

## Global Constraints

- 系统能力默认；调用参数可以严格指定后端；原生 SDK 模块可以裁剪；宿主负责权限。
- 明确选择未注册/不可用后端绝不回退。后端查询异步、可取消，列表不替代实际 admission。
- 一个 adapter 同时只允许一个音频任务（TTS 或 ASR）；busy 请求明确拒绝，不自动替换。
- cancel 只发起停止；终态回调必须等到原生任务退出、输入释放和取消 drain 完成后发布。
- iOS 本包仅文件 ASR，不申请麦克风权限；Android 仅麦克风 ASR，API26–30 显示 requiresOSVersion，TTS 独立可用。
- 不加入微软 SDK、凭据、云服务、后台录音、模型下载或自动语言资源安装。不把声明的 offline=true 当作已测断网结论。
- `demo/iosApp/OnDeviceAIDemo.xcodeproj/project.pbxproj` 与 `docs/validation/face-identity.md` 不修改、不暂存、不提交；基线 SHA256 分别为 `b35bd984f784d4b13c0724f8c595d28ed96ddbe418e049547b762309fe9fd8f5`、`6f130bd94debd5e8bffaba7b0db560553f7a367818d9cf37b72468e42bb70822`。
- iOS15、Android API26 维持基础下限；仅复用本地签名，不创建账户资产。所有 framework、音频和验证输出放 Git 忽略目录。
- 音频、转写文本和 TTS 文本不写 Git、普通日志或 benchmark 报告。转写仅在当前页面内存展示，不自动保存。
- iOS 麦克风、Android 实际转写、自然语音质量、断网和最低系统验收仍属于 roadmap；缺失时保留未完成，不以 unavailable 占位宣称交付。

## Review Focus

1. 同步回调在句柄赋值之前到达：终态不能被随后返回的句柄重新设为 running；Task 1/5 测试。
2. 取消和成功/后台/close 交错：仅一个终态，先清理再回调，回调内可重入；Task 3/4/5 测试。
3. 文件被替换或选择器晚返回：仅删除已无选择引用和在途 lease 的副本，旧选择不能覆盖新页面；Task 4/6 测试。
4. 后端查询部分不可用、显式 ID 未出现在列表或 Swift 自定义 selection conformer：显示与 admission 分离，未知选择拒绝；Task 1/3/4/5 测试。
5. TTS 初始化/权限回调期间离开页面或销毁 owner：迟到结果不可启动音频，已创建 Client 必须释放；Task 6 测试。

## 文件与执行顺序

Task 1 定义公共 API；Task 2 为 native facade 提供查询；Task 3/4 分别完成 Android/iOS adapter；Task 5 完成共享 VM/UI；Task 6 接入两端 host、权限和独立工程；Task 7 做真机与裁剪验收。每 Task 先 RED 后 GREEN，保存真实失败/通过日志，做任务范围审查后仅提交该 Task 文件；环境失败不计行为 RED。已有 P10 待审查不因本计划而记通过。

### Task 1: Swift 可导出的语音契约

**Files:** Create `kmp-binding/src/commonMain/kotlin/io/ondeviceai/binding/SpeechBridge.kt`、`kmp-binding/src/commonTest/kotlin/io/ondeviceai/binding/SpeechBridgeContractTest.kt`；Modify `examples/bridge-probe/SwiftBridgeProbe.swift`。

**Interfaces:** Produces `SpeechBridgeSelection.Default/Explicit(backendId)`；`SpeechBridgeCapability.TTS/ASR`；`SpeechBridgeInput.Microphone/AudioFile(reference)`；`SpeechBridgeInputKind.MICROPHONE/AUDIO_FILE`。描述 `SpeechBridgeBackend(capability,backendId,availability:BridgeAvailability,supportedInputs:List<SpeechBridgeInputKind>,offline:Boolean)`；请求 `SpeechBridgeTtsRequest(text,locale,voiceId:String?)`、`SpeechBridgeAsrRequest(locale,input)`；结果 `SpeechBridgeTtsResult(backendId,voiceId:String?)`、`SpeechBridgeAsrResult(backendId,transcript,confidence:Float?)`。

`SpeechBridgeFailure(code:SpeechBridgeFailureCode,reason:String?,domain:String?,platformCode:Int?)` 的 code 为 CLOSED/BUSY/INVALID_INPUT/UNSUPPORTED_INPUT/BACKEND_NOT_REGISTERED/BACKEND_UNAVAILABLE/TIMED_OUT/NO_RESULT/PLATFORM。平台诊断只保留 domain/code，不拼接异常 message。三个非泛型 callback：`SpeechBridgeBackendsCallback.onResult(List<SpeechBridgeBackend>)`、`SpeechBridgeTtsCallback.onResult(SpeechBridgeTtsResult)`、`SpeechBridgeAsrCallback.onResult(SpeechBridgeAsrResult)`，各有 `onFailure(SpeechBridgeFailure)`、`onCancelled()`。`SpeechBridgeCloseCallback.onClosed()/onFailure(SpeechBridgeFailure)`。

`SpeechBridge.queryBackends(capability,callback):BridgeTask`、`speak(request,selection,callback):BridgeTask`、`recognize(request,selection,callback):BridgeTask`、`close(callback):Unit`。close 回调不可取消，所有调用 join 同一 close；不复用同步 FaceBridge.close。`UnavailableSpeechBridge` 作为旧 host 默认注入，返回明确 unavailable/closed，不伪造成功。

- [ ] **Step 1 — 写契约 RED。** `explicitBlankIdIsInvalid` 断言空白 ID 拒绝；`fileReferenceIsOpaqueAndNonblank` 拒绝空 token；`unavailableNeverReturnsResult` 断言回调 result=0/failure=1；`closedRejectsEveryMethod` 断言 query/speak/recognize 均 CLOSED，重复 close 均完成。Swift probe 引用每个 DTO/callback 和 Default/Explicit，禁止泛型回调导出。
  ```kotlin
  assertFailsWith<IllegalArgumentException> { SpeechBridgeSelection.Explicit(" ") }
  assertFailsWith<IllegalArgumentException> { SpeechBridgeInput.AudioFile("") }
  ```

- [ ] **Step 2 — 运行 RED。** `rtk proxy ./gradlew :kmp-binding:allTests :kmp-binding:linkDebugFrameworkIosSimulatorArm64 --console=plain`；预期缺少 SpeechBridge 符号。保存 `/tmp/odai-speech-bridge-contract-red.log`。
- [ ] **Step 3 — 实现上述签名。** 使用普通类/枚举/接口，验证 locale/text 非空与 input token 非空；空白文本判 INVALID_INPUT，保留合法文本内容。callback 可同步发生，明确所有回调在 UI/main dispatcher 交付。
- [ ] **Step 4 — GREEN 与 ABI。** 重跑 Step 2，再 `rtk proxy sh scripts/check-swift-bridge.sh`；预期测试零失败且 Swift typecheck 成功，含通过 Swift 导出协议的未知 selection 必须在 adapter 被拒绝的约定。
- [ ] **Step 5 — 审查并提交。** 仅提交上述三个文件，message `feat: define typed speech bridge contracts`。

### Task 2: 专用语音 Client 的后端查询

**Files:** Modify `apple/Sources/ODAIKitSpeech/SpeechClient.swift`、`android/speech/src/main/kotlin/io/ondeviceai/speech/SpeechClient.kt`；Create `apple/Tests/ODAIKitSpeechTests/SpeechBackendQueryTests.swift`、`android/speech/src/test/kotlin/io/ondeviceai/speech/SpeechBackendQueryTest.kt`。

**Interfaces:** Consumes 既有 registry `listBackends()` 与 c13ed4c close join。Produces 两个 Swift facade 的 `public func listBackends() async throws -> [SpeechBackendDescriptor]`；两个 Android facade 的 `suspend fun listBackends(): List<SpeechBackendDescriptor>`。不另建拥有相同 backend 的 metadata registry；查询只读原 registry，关闭前后检查 admission，既有 API 源兼容。

- [ ] **Step 1 — 写 RED。** `listsOnlyOwnCapabilitySortedById` 检查 TTS/ASR 独立且不调用 speak/recognize；`queryDoesNotChangeDefaultOrFallback` 检查不可用后端仍出现在描述中；`closeWhileAvailabilitySuspendedRejectsQuery` 用可控 barrier 证明 close 后不发布过期列表；`queryCancellationDoesNotCloseClient` 证明取消查询后原 Client 还能调用。
  ```swift
  let descriptors = try await client.listBackends()
  XCTAssertEqual(descriptors.map(\.backendId), ["a.tts", "z.tts"])
  XCTAssertEqual(descriptors.map(\.capability), [.tts, .tts])
  ```

- [ ] **Step 2 — 运行 RED。** `rtk proxy swift test --disable-sandbox --package-path apple --filter SpeechBackendQueryTests` 和 `rtk proxy ./gradlew :android:speech:testDebugUnitTest --tests '*SpeechBackendQueryTest' --console=plain`；预期缺少 facade 方法。
- [ ] **Step 3 — 实现上述查询。** 沿用现有 closed 错误体系和共享 close；不要把 listBackends 结果作为真正操作的选择缓存。保持不可用原因、minOS、offline 原值。
- [ ] **Step 4 — GREEN。** 重跑定向测试，再跑 Apple ODAIKitSpeechTests 与 Android Speech 全测试；c13ed4c 的 close race 用例不得回退。
- [ ] **Step 5 — 审查并提交。** message `feat: expose capability-scoped speech backend queries`，只提交本 Task 四个文件。

### Task 3: Android 语音 adapter

**Files:** Create `kmp-binding/src/androidMain/kotlin/io/ondeviceai/binding/AndroidSpeechBridge.kt`、`android/speech/src/test/kotlin/io/ondeviceai/speech/AndroidSpeechBridgeIntegrationTest.kt`；Modify `kmp-binding/build.gradle.kts`、`android/speech/build.gradle.kts`（沿用 AndroidIdentityBridge 集成测试源码接入方式，避免 Gradle 循环依赖）。

**Interfaces:** Consumes Tasks 1/2。Produces `AndroidSpeechBridge(tts:SpeechSynthesisClient?,asr:SpeechRecognitionClient?,scope:CoroutineScope)` 实现 SpeechBridge。显式声明非空 Client 所有权转交；缺少某项 Client 只使该能力 unavailable，不能阻断另一能力。本包原生 ASR 契约只接 MICROPHONE，查询从该契约生成 supportedInputs，不虚构文件后端支持。adapter 只依赖 speech/core/coroutines，不能依赖 system 后端。自有任务 Job 不因 host scope 提前取消而跳过 finally/close。

- [ ] **Step 1 — 写 native 集成 RED。** 真实 Client + 可控 backend：`explicitUnavailableNeverCallsDefault`；`fileInputRejectedBeforeNativeRecognition`；`ttsAndAsrShareBusySlot`；`missingAsrClientDoesNotBlockTts`；`cancelWaitsForNativeFinallyThenAllowsCallbackReentry`；`lateSuccessCannotReplaceCancelled`；`closeJoinsAllWorkersAndConcurrentWaiters`；`cancelQueryLeavesAudioOwnerOpen`；`backendErrorsRetainReasonWithoutUserText`。断言 main callback、终态恰一次、drain 前重入 BUSY、close 后 CLOSED。
  ```kotlin
  // 测试 callback recorder 在 native finally barrier 释放前/后的快照。
  assertEquals(emptyList(), terminalsBeforeDrain)
  assertEquals(listOf("cancelled"), terminalsAfterDrain)
  assertEquals(0, defaultBackendCallsForExplicitFailure)
  ```

- [ ] **Step 2 — 运行 RED。** `rtk proxy ./gradlew :android:speech:testDebugUnitTest --tests '*AndroidSpeechBridgeIntegrationTest' --console=plain`；预期缺 adapter。测试使用既有可控调度/线程边界，不增加 mock 库。
- [ ] **Step 3 — 实现 adapter。** 主线程认领任务；worker 调用 native Client，取消后在 NonCancellable 清理区等 native finally 完成，再清槽/发终态。query 不占音频槽，close 拒绝新请求并 join 查询和音频任务后关闭两个专用 Client；关闭失败映射 PLATFORM，所有 close waiter 收到同一结果。未知异常保留稳定 domain/code，不 catch fatal Error 当成功。
- [ ] **Step 4 — GREEN。** 定向测试及 `rtk proxy ./gradlew :android:speech:testDebugUnitTest :android:speech:lintDebug :kmp-binding:compileAndroidMain --console=plain`，预期零失败和 BUILD SUCCESSFUL；native 依赖不得反向引用 KMP。
- [ ] **Step 5 — 审查并提交。** message `feat: adapt Android speech clients to KMP bridge`。

### Task 4: iOS adapter 与输入 lease

**Files:** Create `kmp-binding/apple/IOSSpeechBridge.swift`、`kmp-binding/apple/IOSSpeechAudioInput.swift`、`demo/iosApp/SpeechInputStore.swift`、`probes/ios-speech-bridge/IOSSpeechBridgeTests.swift`、`scripts/generate-speech-bridge-tests.rb`、`tools/tests/test_speech_bridge_project.py`。

**Interfaces:** Consumes Tasks 1/2。Produces `@MainActor IOSSpeechBridge(tts:SpeechSynthesisClient?,asr:SpeechRecognitionClient?,inputs:any IOSSpeechAudioInputProvider)`，按 `ODAI_DEMO_BINDING` 选择单个 Kotlin module；缺少某 Client 只影响该能力。`IOSSpeechAudioInput.swift` 定义 MainActor 协议 `IOSSpeechAudioInputProvider.acquire(reference:String) throws -> any IOSSpeechAudioInputLease`、lease 的 `url:URL`/幂等 `release()`，adapter 不依赖 Demo 类型。本包原生 ASR 契约仅支持 AUDIO_FILE。Demo 的 `SpeechInputStore` 实现 provider，额外提供 `importAudio(from:URL) throws -> String`、`remove(reference:String)`、`close()`；只有 native 触碰 URL，共享层仅 token。

生成器 env `ODAI_SPEECH_BRIDGE_TEST_DIR`、`ODAI_SPEECH_BRIDGE_KMP_MODULE=ODAIKitBinding|ODAIKitDemo`；产出 `SpeechBridgeTests.xcodeproj`/scheme `SpeechBridgeTests`，本地 Swift products 只链接 Speech/Core，编译上述 adapter/store 与测试。框架/源码缺失和已有输出在创建目录前拒绝。

- [ ] **Step 1 — 写 RED。** `cancelledRecognitionRetainsLeaseUntilDrain` 用真实 SpeechRecognitionClient + 可控 backend；`replacedInputDeletedOnlyAfterLastLease`；`unknownTokenAndMicrophoneAreRejected`；`missingTtsClientDoesNotBlockAsr`；`closeJoinsNativeDrainAndConcurrentWaiters`；`unknownSwiftSelectionDoesNotBecomeDefault`；`synchronousFailureReentryHasNoOccupiedSlot`；`cancelledQueryDoesNotCloseClients`；`metadataAndPlatformFailureMapWithoutTranscript`。Python 测生成器两个 ABI、缺输入/拒绝覆盖与无重复 Kotlin framework。
  ```swift
  // 从测试 store 取得私有副本 URL；移除选择后 lease 仍然在途。
  XCTAssertTrue(FileManager.default.fileExists(atPath: privateCopy.path))
  XCTAssertEqual(terminalCountBeforeDrain, 0)
  // 释放 native drain barrier 并等终态后检查。
  XCTAssertFalse(FileManager.default.fileExists(atPath: privateCopy.path))
  XCTAssertEqual(terminalCountAfterDrain, 1)
  ```

- [ ] **Step 2 — 运行 RED。** `rtk proxy env RUBY=/Users/daubert/.rvm/wrappers/ruby-3.4.11/ruby python3 -m unittest discover -s tools/tests -p test_speech_bridge_project.py -v`；预期缺生成器。随后用 fresh 输出生成并运行 XCTest，观察缺少 adapter 的 RED。
- [ ] **Step 3 — 实现 adapter/store/生成器。** 主线程 admission；native Task 取消后仍 await `asr.drain()`，释放 lease、清槽后发一次终态；查询取消独立，close join 所有任务再 close clients。安全访问范围内复制至私有临时目录，以随机 token 管理引用；复制失败清理自己的部分输出，不删除源文件。未选中的遗留私有临时副本可在新 owner 启动时清理，不越出本 store 目录。
- [ ] **Step 4 — GREEN 与两个 ABI。** 先链接 `:kmp-binding:linkDebugFrameworkIosSimulatorArm64`、`:demo:shared:linkDebugFrameworkIosSimulatorArm64`；分别用上述 env 生成到两个 fresh artifacts 子目录。`rtk proxy xcodebuild test -project <fresh>/SpeechBridgeTests.xcodeproj -scheme SpeechBridgeTests -destination 'platform=iOS Simulator,id=DF2DC7EA-257B-4E5F-9AFD-EC4BD2ED6D7F' CODE_SIGNING_ALLOWED=NO` 两次均零失败；Python 生成器测试通过。
- [ ] **Step 5 — 审查并提交。** message `feat: bridge iOS speech with drained file leases`；不提交生成工程或音频。

### Task 5: 共享语音状态与独立页面

**Files:** Create `demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/SpeechViewModel.kt`、`SpeechScreen.kt`（同目录）、`demo/shared/src/commonTest/kotlin/io/ondeviceai/demo/SpeechViewModelTest.kt`；Modify `demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/FaceScreen.kt`、`demo/shared/src/iosMain/kotlin/io/ondeviceai/demo/MainViewController.kt`。

**Interfaces:** Consumes Task 1。Produces `SpeechViewModel(bridge:SpeechBridge)`；方法 `refreshBackends(capability)`、`speak(request,selection)`、`recognize(request,selection)`、`cancel()`、`leavePage()`、`background()`、`close()`。`SpeechState.phase:SpeechPhase` 枚举为 IDLE/RUNNING/CANCELLING/SUCCEEDED/FAILED/CANCELLED，另有按能力的描述/查询错误、TTS/ASR独立结果、failure；取消查询与音频各自 generation。

`SpeechDemoActions.selectAudioFile()/clearAudioFile()/openSpeechSettings()`；`SpeechViewModel.selectAudio(reference:String?,displayName:String?)` 保存进程内选择。`FaceScreen` 追加可选 speech/speechActions 参数；iOS 保留原四参数 mainViewController 入口，新增 `speechMainViewController(model,actions,identity,identityActions,speech,speechActions)`，避免改变旧 host 调用签名。

- [ ] **Step 1 — 写 RED。** `synchronousTerminalDoesNotResurrectHandle`；`switchSelectionWhileRunningWaitsForDrain`；`backgroundAndLeaveDoNotAutorestart`；`lateQueryCannotOverwriteNewQuery`；`explicitMissingFromListStillReachesNativeAdmission`；`ttsAndAsrResultsNeverOverlap`；`closedRejectsUiAdmission`；`unsupportedInputIsExplainedWithoutCallingNative`。假 bridge 可延迟/同步/重复回调；断言取消期间 controls 禁用、无伪造成功、下一请求仅在终态后进入。
  ```kotlin
  // cancel 后保持 cancelling；可控 bridge 的 onCancelled 到达后才变 cancelled。
  assertEquals(SpeechPhase.CANCELLING, stateBeforeTerminal.phase)
  assertEquals(SpeechPhase.CANCELLED, stateAfterTerminal.phase)
  assertEquals(1, nativeAdmissionCountBeforeTerminal)
  ```

- [ ] **Step 2 — 运行 RED。** `rtk proxy ./gradlew :demo:shared:allTests --console=plain`，预期缺 SpeechViewModel。
- [ ] **Step 3 — 实现 VM/UI。** 语音 tab 与人脸/身份互斥；进入语音停止相机、取消已有检测/身份任务，语音 UI 不显示人脸状态。两组 Default/Explicit 选择、locale、TTS text/voiceId、ASR输入和文件名、刷新/播放/开始/取消、结果/失败分开。输入/后端改变发 cancel 并等 terminal，不靠列表 preflight 拒绝 Explicit。离开页面清转写显示，保持 adapter 可复用；close 才永久关闭。语音入口和取消终态分别提供稳定测试标记 `speech-tab`、`speech-cancelled`，两端验证真实 accessibility 导出。
- [ ] **Step 4 — GREEN。** Task 5 测试、binding tests、Android APK 与共享模拟器 framework 编译通过；Face/Identity 现有测试不回退。
- [ ] **Step 5 — 审查并提交。** message `feat: add shared speech controls and cancellation state`。

### Task 6: 两端 host、权限与安全生成工程

**Files:** Create `demo/androidApp/src/main/kotlin/io/ondeviceai/demo/SpeechHost.kt`、`demo/iosApp/SpeechHost.swift`、`demo/iosApp/SpeechFilePicker.swift`、`scripts/generate-speech-demo.rb`、`tools/tests/test_speech_demo_project.py`、`probes/ios-speech-bridge/SpeechHostTests.swift`、`demo/androidApp/src/test/kotlin/io/ondeviceai/demo/SpeechHostTest.kt`；Modify Android `MainActivity.kt`、`build.gradle.kts`、`src/main/AndroidManifest.xml`；Modify iOS `DemoController.swift`、`OnDeviceAIDemoApp.swift`；Task 4 测试生成器加入 host 测试 sources。

**Interfaces:** Android `SpeechHost` 持有初始化任务与 adapter，`onForeground()/onBackground()/close()`；Activity 注册 RECORD_AUDIO request、带 generation 的 pending ASR action；无授权不调用 recognizer。TTS异步创建完成才转交 Client，失败显示 unavailable，销毁时回收尚未转交的 backend。system-tts/asr 改为 host implementation 依赖，binding 仍只有 speech 契约。

iOS `SpeechHost` 持有 inputs、专用 clients/adapter、SpeechViewModel 和权限/选择器；`selectAudioFile()/clearAudioFile()/openSpeechSettings()/background()/close()`。Apple ASR backend 初始化 locale 为 `en-US`，页面明确当前后端语言约束；其他请求语言沿用 native 校验失败，不悄悄改 locale。支持新语言/动态 backend 重建不属于本包。宿主只在用户开始 ASR 时申请 Speech 授权，拒绝后给设置入口，授权晚到且 generation 已失效时不启动识别。

独立生成器 env `ODAI_SPEECH_DEMO_DIR` 指向 fresh artifacts 目录、`ODAI_DEVELOPMENT_TEAM` 可选传入已有本地 team；项目/scheme `SpeechDemo`，bundle ID 复用 `io.ondeviceai.ondeviceaidemo`。在创建输出前验证 simulator/device 两份 ODAIKitDemo framework 和所有 sources。条件 `ODAI_SPEECH_DEMO` 启用新 host，旧工程走 UnavailableSpeechBridge 默认；只新工程包含新增 Swift source。沿用 Core/Face/AppleVision，加 Speech/AppleSpeech；不引入第二份 Kotlin framework。语音 usage description 仅生成工程包含，不增加麦克风用途声明。

- [ ] **Step 1 — 写 RED。** `permissionReplyAfterBackgroundDoesNotStartRecognition`、`ttsInitializationAfterOwnerCloseDisposesBackend`、`pickerReplyAfterCloseDisposesCopiedInput`、`pickerCancelPreservesCurrentReference`；Python 测输出拒绝覆盖、缺任一 ABI 无副作用、platform条件正确、无改写原工程、无隐式账户操作；基线 hash 精确相等。
  ```kotlin
  // 测试宿主边界注入可控 permission reply 和 backend factory，回复先挂起再 close。
  assertEquals(0, recognitionStartsAfterLatePermissionReply)
  assertEquals(1, lateUntransferredBackendCloseCount)
  ```

- [ ] **Step 2 — 运行 RED。** 定向 host 测试与 `rtk proxy env RUBY=/Users/daubert/.rvm/wrappers/ruby-3.4.11/ruby python3 -m unittest discover -s tools/tests -p test_speech_demo_project.py -v`；预期 host/生成器缺失。
- [ ] **Step 3 — 实现 host/生成器。** iOS 以 UTType.audio 单文件 importer 和安全访问复制接 Task 4 store，文件名只展示 basename；后台、tab离开和 App owner 关闭转入既有取消/drain。Android onStop 取消、onDestroy 发 close，不能提前取消负责清理的 scope。复用已有 CameraLifecycle，不新增并行相机管理器。生成器不调用旧 generate-xcode-projects.rb。
- [ ] **Step 4 — GREEN/构建。** host 测试、Python 两个生成器 suites、Android lint/APK；重建 `:demo:shared:linkDebugFrameworkIosSimulatorArm64 :demo:shared:linkDebugFrameworkIosArm64` 后 fresh 生成，原受保护工程和 SpeechDemo 分别 `xcodebuild ... -sdk iphonesimulator CODE_SIGNING_ALLOWED=NO build`；再用已有 team 签名构建 SpeechDemo device target，不改账户资产。检查原工程/用户草稿 hash 未变化。
- [ ] **Step 5 — 审查并提交。** message `feat: wire speech Demo hosts and isolated iOS project`；逐路径 stage，受保护文件不在列表。

### Task 7: 真机闭环、裁剪回归与记录

**Files:** Create `docs/validation/speech-demo-bridge.md`、`demo/iosApp/UITests/SpeechDemoUITests.swift`、`demo/androidApp/src/androidTest/kotlin/io/ondeviceai/demo/SpeechDemoInstrumentation.kt`；Modify Task 6 生成器以包含独立 UI test target、`README.md`、`docs/ROADMAP.md`、`docs/validation/speech-backends.md`；必要时更新现有分发 consumer/check 脚本，仅覆盖新增 facade ABI。

**Interfaces:** Consumes Tasks 1–6。保留每次验证 SDK commit/设备/OS/backend/状态与日志路径；不记录自然转写文本或音频。UI用例通过实际 App 语音入口，不把旧直接探针当 Demo 验收。自动化固定文本/合成 fixture 不替代自然语音质量。

- [ ] **Step 1 — 写交互 RED。** 两端用例断言语音页入口、独立后端列表、TTS 完成/活动取消/重入；iOS 文件选择取消与已导入输入保留、文件 ASR 转写/取消/再运行；Android mic拒权不启动、授权后真实转写或明确 MISSING_ASSETS，不能把 unavailable 当转写通过。增加后台取消后回前台不自动开始的可复核步骤。
  ```swift
  // 生成工程的 UI 测试通过稳定 accessibility ID 观测状态，不读取转写内容。
  XCTAssertTrue(app.buttons["speech-tab"].waitForExistence(timeout: 10))
  XCTAssertTrue(app.staticTexts["speech-cancelled"].waitForExistence(timeout: 30))
  ```

- [ ] **Step 2 — 运行并保存初始结果。** iPhone `00008140-0008703436D3001C`、Android `15365375C9007986` 先检查在线状态，再用生成项目 UI test/APK instrumentation；需要真人权限点击或录音输入时只询问该动作，同时继续本地 gate。缺资源/设备记录 unverified/unavailable。
- [ ] **Step 3 — 修复真实失败。** 只修本包实际问题，每项保留可复现 RED→GREEN；不自动装资源、不打开网络识别、不调整保护工程。host/adapter/VM问题回到所属 Task 测试锁定。
- [ ] **Step 4 — 最终本地与裁剪回归。** `rtk proxy sh scripts/verify-apple.sh`、`rtk proxy sh scripts/verify-android.sh`、`rtk proxy ./gradlew :kmp-binding:allTests :demo:shared:allTests --console=plain`、`rtk proxy env RUBY=/Users/daubert/.rvm/wrappers/ruby-3.4.11/ruby python3 -m unittest discover -s tools/tests -v`；fresh Apple七产品 package、原生 Identity/Face/Speech消费者及 Maven published消费者依赖检查沿用现有脚本。检查无新增 KMP/Compose/system backend 进入原生 contract-only 产品；测试输出逐份读 XML/xcresult，hash 与 git diff --check 通过。
- [ ] **Step 5 — 独立整包审查、提交与下一入口。** 审查 Task 1 起点到最终实现 HEAD，处理 Critical/Important、保留 Minor。文档记录通过与缺口及生成/打开命令，提交 `test: validate speech Demo lifecycle and module isolation`。P6仍不标完成，下一工作包为 iOS 麦克风 ASR 的独立设计，同时继续 Android 资源/真实转写与质量验证；P10/其他 roadmap 项不因本包完成而关闭。

## 计划自审

规格全部节已映射：契约/严格选择→1/2；native所有权与单任务→3/4；文件与权限→4/6；共享页面与后台→5/6；隔离工程与双 ABI→4/6；真机/依赖裁剪/未测项→7。五条 Review Focus 均有归属测试。类型名称统一，close 使用可等待回调，不复用 Face 的同步关闭；没有把泛型 Kotlin callback 留给 Swift 猜测。计划批准前不开始实现。
