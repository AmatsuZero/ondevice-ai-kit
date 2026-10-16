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
| iOS TTS | `apple.avspeech` | Apple AVFAudio `AVSpeechSynthesizer`，系统 API | 依赖系统语音资源；不需要服务凭据，是否可用由设备 voice 列表决定 | 默认后端；放入 core 能力接口，平台实现放在 Apple 模块 |
| Android TTS | `android.text-to-speech` | Android framework `TextToSpeech`；具体引擎由设备提供 | 语言包和网络行为由引擎决定；不把某个厂商引擎当 SDK 依赖 | 默认后端；初始化、语言检查和 shutdown 必须受任务生命周期管理 |
| Azure Speech TTS/ASR | `microsoft.azure.speech` | Microsoft Speech SDK：iOS/Android 原生包，也可通过 REST；包名/版本在实现任务中锁定 | 云端通常需要订阅 key/endpoint 或 token；网络失败、限流、区域和隐私必须显式暴露 | 可选插件；不进入默认依赖图，安装时单独启用 |
| Web/自有 HTTP | `custom.http` | 宿主提供实现或适配器 | 由宿主负责凭据、网络和数据处理 | 仅保留扩展点，不在 P6 接入 |

Microsoft 官方文档说明 Speech SDK 支持 Android、iOS 和 REST 路线；本项目暂不假设“微软 SDK”一定是离线包。实现前必须分别记录原生包的许可证、包体积、最低系统、初始化线程、音频格式、token 生命周期和网络错误映射：

- <https://learn.microsoft.com/en-us/azure/ai-services/speech-service/speech-sdk>
- <https://learn.microsoft.com/en-us/azure/ai-services/speech-service/speech-to-text>
- <https://learn.microsoft.com/en-us/azure/ai-services/speech-service/text-to-speech>

系统 API 的基线资料：

- <https://developer.apple.com/documentation/avfaudio/avspeechsynthesizer>
- <https://developer.android.com/reference/android/speech/tts/TextToSpeech>

## 探针结果

`tools/probes/native_tts_probe.sh` 是宿主机探针：使用 macOS 内置 `say` 生成临时 AIFF，再用 `afinfo` 读取音频格式，退出时删除文件。它验证系统语音链路和测试环境，不代表 iOS 真机、Android 引擎或 Azure 网络调用已通过。

执行：

```sh
sh tools/probes/native_tts_probe.sh Samantha "OnDevice AI Kit native speech probe"
```

P6 停止条件：统一选择值和错误语义进入实现计划；默认系统后端保持零第三方依赖；Azure 作为可裁剪插件，待凭据/网络和包分发策略确定后单独实现与 benchmark。真实 iOS/Android TTS/ASR 设备样本留给语音接入工作包，不以本机 host 探针替代。
