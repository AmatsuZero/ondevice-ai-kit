# P6 语音后端调研与探针

更新时间：2026-10-16。此阶段只确定后端边界和可验证的最小探针，不把第三方服务凭据、网络可用性或商业许可假设成 SDK 已交付。

## 统一接口边界

上层使用能力级选择值：`Default` 或 `Explicit(backendId)`。短任务在调用时选择；流式/长任务在会话创建时固定。显式选择不可用时返回结构化错误，不静默回退到另一个后端。结果和 benchmark 必须记录实际 `backendId`、网络/离线属性和音频格式。

建议的最小能力面：

- `SpeechRecognizer.start(request, backend: BackendSelection)`：事件包含 partial/final、错误、取消和实际后端。
- `SpeechSynthesizer.speak(request, backend: BackendSelection)`：返回可取消的任务句柄；音频输出通过平台音频会话/播放器，不让 SDK 偷改宿主全局音频路由。
- `SpeechCapabilities.list()`：报告 `backendId`、语言、是否联网、是否需要凭据、最低系统和安装模块。

## 候选后端

| 能力 | 后端 | 平台/提供形式 | 离线与凭据 | 选择结论 |
| --- | --- | --- | --- | --- |
| iOS ASR | `apple.speech.recognizer` | Speech framework `SFSpeechRecognizer` | 是否支持本机识别按 locale/设备查询；权限和语音资源不可预设 | 基础后端候选，需 iOS 真机权限与离线探针 |
| iOS 26+ ASR | `apple.speech.transcriber` | Speech framework `SpeechAnalyzer`/`SpeechTranscriber`，仅在新系统条件满足时启用 | 资源可用性、locale 与设备须运行时核验 | 增强模块候选，不抬高基础宿主最低系统 |
| Android ASR | `android.speech.recognizer` | Android framework `SpeechRecognizer`，服务实现由设备提供 | on-device 可用性需按设备/系统查询；不承诺所有机型离线 | 基础候选，需真机权限与取消探针 |
| iOS TTS | `apple.avspeech` | Apple AVFAudio `AVSpeechSynthesizer`，系统 API | 依赖系统语音资源；不需要服务凭据，是否可用由设备 voice 列表决定 | 默认后端；放入 core 能力接口，平台实现放在 Apple 模块 |
| Android TTS | `android.text-to-speech` | Android framework `TextToSpeech`；具体引擎由设备提供 | 语言包和网络行为由引擎决定；不把某个厂商引擎当 SDK 依赖 | 默认后端；初始化、语言检查和 shutdown 必须受任务生命周期管理 |
| Azure Speech TTS/ASR | `microsoft.azure.speech` | Microsoft Speech SDK：iOS/Android 原生包，也可通过 REST；包名/版本在实现任务中锁定 | 云端通常需要订阅 key/endpoint 或 token；网络失败、限流、区域和隐私必须显式暴露 | 可选插件；不进入默认依赖图，安装时单独启用 |
| Web/自有 HTTP | `custom.http` | 宿主提供实现或适配器 | 由宿主负责凭据、网络和数据处理 | 仅保留扩展点，不在 P6 接入 |

Microsoft 官方文档说明 Speech SDK 支持 Android、iOS 和 REST 路线；本项目暂不假设“微软 SDK”一定是离线包。实现前必须分别记录原生包的许可证、包体积、最低系统、初始化线程、音频格式、token 生命周期和网络错误映射：

- <https://learn.microsoft.com/en-us/azure/ai-services/speech-service/speech-sdk>
- <https://learn.microsoft.com/en-us/azure/ai-services/speech-service/speech-to-text>
- <https://learn.microsoft.com/en-us/azure/ai-services/speech-service/text-to-speech>

系统 API 的基线资料（具体可用性必须由目标设备验证）：

- <https://developer.apple.com/documentation/avfaudio/avspeechsynthesizer>
- <https://developer.apple.com/documentation/speech/sfspeechrecognizer>
- <https://developer.apple.com/documentation/speech/speechanalyzer>
- <https://developer.android.com/reference/android/speech/tts/TextToSpeech>
- <https://developer.android.com/reference/android/speech/SpeechRecognizer>

## ASR 可用性证据与状态

官方 API 要求把“支持、已安装、当前可用”分开：

- iOS 13+ `SFSpeechRecognizer.supportsOnDeviceRecognition` 只表示该识别器具备离线能力；只有确认它为 true 后，才设置 `SFSpeechRecognitionRequest.requiresOnDeviceRecognition = true`。同时需要语音权限，麦克风输入还需要麦克风权限。
- iOS 26+ `SpeechTranscriber` 需要运行时检查 `isAvailable`、locale 支持和 `AssetInventory` 状态；必要时通过 `AssetInstallationRequest.downloadAndInstall()` 安装语言资产。已支持 locale 不等于资产已安装。
- Android API 31+ 先调用 `SpeechRecognizer.isOnDeviceRecognitionAvailable(context)`，再创建 `createOnDeviceSpeechRecognizer(context)`；创建失败、模型下载和 `RECORD_AUDIO` 权限都必须变成结构化状态。实例结束必须 `destroy()`；普通 `SpeechRecognizer` 可能把音频发送到远端，不能当作离线后端。

后端能力状态建议至少区分：`unavailable`、`requiresPermission`、`requiresAsset`、`ready`。官方资料：

- <https://developer.apple.com/documentation/speech/sfspeechrecognizer/supportsondevicerecognition>
- <https://developer.apple.com/documentation/speech/sfspeechrecognitionrequest/requiresondevicerecognition>
- <https://developer.apple.com/documentation/speech/assetinventory>
- <https://developer.apple.com/documentation/speech/assetinstallationrequest>
- <https://developer.android.com/reference/android/speech/SpeechRecognizer#isOnDeviceRecognitionAvailable(android.content.Context)>
- <https://developer.android.com/reference/android/speech/SpeechRecognizer#createOnDeviceSpeechRecognizer(android.content.Context)>

## 探针结果

`tools/probes/native_tts_probe.sh` 是宿主机探针：使用 macOS 内置 `say` 生成临时 AIFF，再用 `afinfo` 读取音频格式，退出时删除文件。`tools/probes/apple_avspeech_probe.swift` 进一步直接编译 AVFoundation，枚举 180 个系统 voice 并完成 `AVSpeechSynthesizer` 短句合成。两者只验证 host/API 可探测性。`demo/androidApp/src/androidTest/.../TtsProbeInstrumentation.kt` 在 TECNO KM9 / Android 15 上作为 1 个可发现的 instrumentation test 初始化 `com.google.android.tts`，`en-US` 返回 `LANG_AVAILABLE`，467 voices，生成 129404 字节 WAV 后成功关闭并删除缓存文件；同次查询 `SpeechRecognizer.isOnDeviceRecognitionAvailable` 返回 `true`，只证明设备具备 on-device ASR 服务，不代表已授权或已完成转写；`connectedDebugAndroidTest` 报告 1/1 通过。

2026-10-17 增加独立 `probes/ios-speech` 探针。iPhone 16 Pro Max / iOS 27.0.1 已签名构建、安装和启动；原始 JSON 保存在忽略目录 `artifacts/P6IOSSpeech/device-runs/`。设备报告显示 `AVSpeechSynthesizer` 共 193 个 voice，选择 `en-US` 的 Samantha，短句回调 `didFinish`，TTS 状态 `success`。原始 JSON 的 `offline=true` 是旧版探针未经断网验证的推断，不能作为离线证据；当前源码已改为 `networkBehavior=unmeasured`，此版本已通过模拟器构建但尚未重跑真机。探针也未录制或审听音频，因此网络行为和主观质量仍未测。`SFSpeechRecognizer(en-US)` 报告 `supportsOnDeviceRecognition=true`、`isAvailable=true`，但授权状态是 `notDetermined`，`transcription=not-run`；这些字段只证明可用性查询，不证明 ASR 推理通过。

执行：

```sh
sh tools/probes/native_tts_probe.sh Samantha "OnDevice AI Kit native speech probe"
swiftc tools/probes/apple_avspeech_probe.swift -o /tmp/odai-apple-avspeech-probe && /tmp/odai-apple-avspeech-probe
```

P6 仍待完成：iOS 真机 ASR 授权、音频输入和转写验证，iOS 26+ SpeechTranscriber 资源实际状态，以及两端 TTS 的离线/质量验收。Android 与 iOS 系统 TTS 最小探针已通过；统一选择值和错误语义进入后续实现计划。Azure 作为可裁剪插件，待凭据、网络和包分发策略确定后单独实现与 benchmark。

## 2026-10-17 统一 SDK 语音选择契约

P6 增加了可裁剪的语音能力契约，但尚未接入具体系统/云端实现：

- Apple：`ODAIKitSpeech.SpeechClient`，通过 `SpeechCapability.tts` 或 `.asr` 创建能力注册表；注册 `SpeechBackend` 后可用默认后端或 `SpeechBackendSelection.explicit(id)` 查询所选描述符。显式 ID 不存在或不可用时返回结构化错误，不会静默切换。
- Android：`io.ondeviceai.speech.SpeechClient`，对应 `SpeechCapability.TTS` / `ASR` 与 `SpeechBackendSelection.Explicit(id)`，目前仅查询所选描述符；模块 `android:speech` 可独立依赖，未加入 Demo 默认依赖。
- 描述符公开现有 `CapabilityAvailability` 的可用性、`offline` 与最低系统字段；尚未定义凭据/网络错误或音频任务接口。SDK 不修改宿主音频会话或路由。
- Apple 契约测试 2/2 通过；Android `:android:speech:testDebugUnitTest --tests io.ondeviceai.speech.SpeechClientTest` 通过。当前 Fake 后端只验证选择和生命周期，不能替代系统 TTS/ASR 真机行为。

后续系统后端应分别放入可选模块：`apple.avspeech`、`apple.speech.recognizer`、`android.text-to-speech` 和 `android.speech.recognizer`；Azure/Microsoft 适配器保持独立插件，不进入默认依赖图。

## 2026-10-17 Apple 系统 TTS SDK 后端

- 新增可选 SwiftPM 产品 `ODAIKitAppleSpeech`，实现 `AppleAVSpeechBackend`；默认依赖仍不链接 AVFoundation 语音实现。`SpeechSynthesisClient` 通过 `.default` / `.explicit(id)` 选择后端，显式选择不回退。
- `AppleAVSpeechBackend` 查询已安装 voice、按 locale/voiceId 选择语音；单任务执行期间拒绝并发，取消调用 `stopSpeaking(.immediate)`，`close` 停止当前 utterance 并拒绝后续选择。
- 主机与 iOS 通用设备构建通过。iPhone 16 Pro Max / iOS 27.0.1 实机通过 SDK 探针：`apple.avspeech`、193 voices、`en-US` Samantha、`status=success`；长文本取消报告 `cancellationStatus=cancelled`，关闭后调用报告 `afterCloseStatus=closed`。
- 真实设备报告保存在被忽略的 `artifacts/verification/ios-speech-device-cancel-report.json`，不提交音频或设备用户数据。`networkBehavior=unmeasured`，因此不能据此宣称系统 voice 的离线网络行为。

## 2026-10-17 Android 系统 TTS SDK 后端

- 新增可选 Android 模块 `android:backend-system-tts`，实现 `AndroidTextToSpeechBackend`；模块不进入 Demo 默认运行依赖，Demo instrumentation 仅为验证显式加入。
- 后端初始化 `TextToSpeech`，只选择已安装且 `isNetworkConnectionRequired=false` 的 voice；按 `locale`/可选 `voiceId` 选择，超过系统输入长度或缺少 voice 时返回结构化错误。`close` 调用 `stop()` + `shutdown()`，注册表关闭后拒绝新任务。
- TECNO KM9 / Android 15(API 35) instrumentation **1/1 通过**：系统引擎 `com.google.android.tts`，`en-US` 可用，467 voices，原始文件 129404 bytes；通过 SDK 后端的 `sdkBackend=android.text-to-speech`、`sdkVoice=en-US-language`、`sdkStatus=success`，关闭后 `sdkAfterCloseStatus=closed`。
- 后端新增主线程约束的 `TtsTaskGate`：取消在 `speak` 的 `finally` 中同步执行 `stop()` 并释放 admission，迟到的 `onDone/onStop/onError` 只能匹配原 utterance ID；`close` 对活动 utterance 只调用一次 `stop()`。JVM `TtsTaskGateTest` 覆盖取消后重新进入、迟到回调隔离、并发拒绝、重复取消、正常完成不调用 `stop()` 以及平台 `stop()` 异常时仍执行清理。
- 取消设备验收已通过：TECNO KM9 / Android 15(API 35) 在取消长句后立即发起短句，阶段日志确认取消协程完成、后续请求成功，instrumentation **1/1 通过**；根因为取消后重复读取 `TextToSpeech.voices` 会阻塞，后端现于初始化时缓存 voice 列表。原先长句尝试的崩溃记录仍不计为通过。
- Android typed facade 单元测试和 `android:backend-system-tts:assembleRelease` 通过。该模块没有引入 Azure/Microsoft SDK。

## Android on-device ASR 可选后端

本包沿用同一注册表，新增 `SpeechRecognitionRequest(locale)`、`SpeechRecognitionResult(backendId, transcript, confidence?)`、`SpeechRecognitionBackend` 与 `SpeechRecognitionClient`，与 TTS 请求/结果分别定义。首版为一次短句的麦克风输入，不承诺持续听写、partial 流、音频文件注入或语音模型安装。`android:backend-system-asr` 使用 `android.speech.recognizer` ID，保持 minSdk26，API31 以下只报告不可用；已注册但不可用或显式选择未知 ID 均不回退到其他引擎。Demo 只在 androidTest 依赖后端；麦克风权限仅加入 debug manifest，默认 release 不包含 ASR 实现或麦克风权限。

官方边界已按本机 Android API34 `api-versions.xml`、API37 public sources 及 [AOSP Android 13 固定源码](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-13.0.0_r1/core/java/android/speech/SpeechRecognizer.java) 核对：

- `createOnDeviceSpeechRecognizer` 与 `isOnDeviceRecognitionAvailable` 自 API31 提供。仅调用 on-device factory；不将普通 `createSpeechRecognizer` 加 `EXTRA_PREFER_OFFLINE` 的 hint 当作同等保证。
- 全部 framework 方法在应用主线程调用，listener 先于 start 设置，取消/结束后必须 destroy。一个实例对应一次短句，晚到的旧 callback 不能完成下一次请求。
- `RECORD_AUDIO` 由宿主声明并授权；查询不弹权限、不下载资源。服务存在不等于某语言模型已安装或真实转写成功。
- API33 的 [EXTRA_AUDIO_SOURCE](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-13.0.0_r1/core/java/android/speech/RecognizerIntent.java) 可提供 PFD/PCM，调用方负责关闭，但不支持的服务可改用麦克风。`checkRecognitionSupport` 的语言列表不证明音频注入支持，因此本包不把该路径加入 SDK 公共 DTO。

2026-10-17 本地验证：`android:speech` 8 项 JVM 测试、ASR session 7 项测试通过，ASR Release AAR、`lintDebug`、Demo debug/androidTest APK 编译通过；现有 Android 验证脚本所列 JVM/lint/APK 任务及语音模块测试通过。lint 无 error，保留项目固定 coroutines 版本更新提示和私有 API31 实现类 `TargetApi` 建议两条 warning。独立审查修正了外部 deadline 被误转为 ASR 超时、选择后运行时不可用的错误分类，以及清理异常静默丢失；外部超时和 destroy 失败回归均已观察 RED→GREEN，busy 请求不会销毁当前任务。

TECNO KM9 / Android 15（API35），debug 宿主已授权 `RECORD_AUDIO`。服务查询 `AVAILABLE`，实际 `en-US` 短句启动返回错误码 13（`ERROR_LANGUAGE_UNAVAILABLE`：语言支持但资源当前不可用）。Client 返回 `BackendUnavailable(reason=MISSING_ASSETS)`，探针报告 `probeStatus=unavailable`、`transcription=not-run`，取消/重入/活跃关闭未完成设备验收，不能报告 1/1 ASR 推理或生命周期通过。未触发下载、未调用网络 recognizer。网络行为未独立测量。

Gradle connected 测试会卸载测试宿主；第二轮曾因麦克风授权丢失而跳过。最终以重新安装后 `pm grant` + `am instrument -w` 的直接探针为准，原始证据位于忽略目录 `artifacts/P6AndroidSpeech/asr-20261017/`。探针只将明确的运行时不可用分类标为 unavailable；其他异常仍为 fail。待语言资源可用后重跑取消、重入、close 和真实转写；API26–30 实机门禁与离线/质量验收仍待补。
