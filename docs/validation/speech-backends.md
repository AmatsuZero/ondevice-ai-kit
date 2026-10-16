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

## 探针结果

`tools/probes/native_tts_probe.sh` 是宿主机探针：使用 macOS 内置 `say` 生成临时 AIFF，再用 `afinfo` 读取音频格式，退出时删除文件。`tools/probes/apple_avspeech_probe.swift` 进一步直接编译 AVFoundation，枚举 180 个系统 voice 并完成 `AVSpeechSynthesizer` 短句合成。两者都验证 host/API 可探测性，不代表 iOS 真机、Android 引擎或 Azure 网络调用已通过。`demo/androidApp/src/androidTest/.../TtsProbeInstrumentation.kt` 在 TECNO KM9 / Android 15 上初始化 `com.google.android.tts`，`en-US` 返回 `LANG_AVAILABLE`，467 voices，生成 129404 字节 WAV 后成功关闭并删除缓存文件。

执行：

```sh
sh tools/probes/native_tts_probe.sh Samantha "OnDevice AI Kit native speech probe"
swiftc tools/probes/apple_avspeech_probe.swift -o /tmp/odai-apple-avspeech-probe && /tmp/odai-apple-avspeech-probe
```

P6 仍待完成：核对 ASR 和 iOS 26+ API 的官方可用性、权限、离线资源，并在 iOS 真机上补同等原生 TTS/ASR 探针。Android 系统 TTS 最小探针已通过；本机 host 探针不能替代移动端验收。统一选择值和错误语义进入后续实现计划；Azure 作为可裁剪插件，待凭据、网络和包分发策略确定后单独实现与 benchmark。
