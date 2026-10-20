# iOS 麦克风 ASR 与音频会话设计草案

日期：2026-10-20。基线：`3d4ab52`，工作树 `foundation`。状态：**按既有 roadmap 自动推进授权形成的设计决策，可随用户反馈修订；未声称人工逐项审阅**。先执行本地、可逆的契约测试与实现，独立审查/真机/远端门禁另行保留；本文件不表示语音 Demo Task 7 或 P6 已通过。

## 目标与已知约束

按 roadmap 的下一入口补齐 iOS 麦克风 ASR：使用者从现有语音页开始录音，主动结束输入并获得最终转写，或者取消；后台、离页和关闭均停止采集且前台不自动重启。原生 SDK 与 KMP Demo 都提供可复用的入口。保持 on-device、严格 Default/Explicit 后端选择和取消/drain 后再允许重入。

保留 iOS 15 基础下限和 macOS 12 Swift Package 编译；本包新增的系统麦克风实现只在 iOS 提供。原有文件识别 API、文件 lease、TTS、Android 现有路径和七产品裁剪继续工作。不自动下载语言资源，不回退网络识别，不采集后台录音，不保存录音或自然转写，不加入第三方依赖。新权限描述只由独立 SpeechDemo 工程生成器写入，保护原 `pbxproj` 和用户 `face-identity.md`。

当前代码事实：

- `SpeechRecognitionRequest(locale:audioFile:)` 公开持有非可空 URL；改变其类型会破坏文件消费者。
- `SpeechRecognitionClient` 通过 registry 严格选后端；现有 `drain()` 等 backend 完成后才释放输入。
- `AppleSpeechRecognizerBackend` 固定 locale，使用 MainActor operation、generation、事件 inbox 和 completed-state polling；partial 不作为最终成功。
- `IOSSpeechBridge` 硬编码 ASR 支持 `AUDIO_FILE`；共享契约已有 `Microphone`，无需向共享层暴露 PCM 或 AVFoundation 对象。
- `SpeechHost` 现只申请 Speech 权限；旧 `AppleAVSpeechBackend` 没有 App 级音频会话协调器。

## 方案与取舍

**建议 A：增加独立麦克风任务 API，保留文件 API。** 契约模块定义类型化 request/task 和可选 backend 协议；Apple backend 复用当前 final/cancel/drain 引擎，增加系统 capture 实现；host 明确提供音频会话租约。文件消费者不需要迁移。

B：将 `SpeechRecognitionRequest.audioFile` 改为输入 enum。统一入口方便，但当前非可空 URL 使用方和 conformer 都受影响，本包不采用。

C：由 Demo host 采集 PCM，再把 buffer 送给识别 backend。会让 SDK 调用方重复实现采集/清理边界，还把平台对象带进宿主，本包不采用。

方案 A 的代价是新增可选任务接口。需要这个接口的原因是 **finish input 与 cancel 不同**：前者调用 `endAudio()` 后等最终结果，后者停止并丢弃本轮结果。用一个取消按钮表达两者会破坏现有取消语义。

## 原生公共契约

`ODAIKitSpeech` 保持只依赖 Core/Foundation：

- 保留 `SpeechRecognitionRequest(locale:audioFile:)`、`SpeechRecognitionBackend.recognize` 和结果结构。
- 新增 `SpeechMicrophoneRecognitionRequest(locale:)`，沿用现有 locale canonicalization。
- 新增 `SpeechMicrophoneRecognitionTask`：`result() async throws -> SpeechRecognitionResult`、`finishInput() async`、`cancel() async`。finish/cancel 幂等，任务句柄只作用于本次 generation；过期任务不得停止下一轮。多个 result waiter 取得同一终态，一个 waiter 被取消不释放仍在使用的原生资源。
- 新增可选 `SpeechMicrophoneRecognitionBackend: SpeechRecognitionBackend`，提供 `startMicrophone(_:) async throws -> any SpeechMicrophoneRecognitionTask`。已有文件 backend 不必实现该协议。
- `SpeechRecognitionClient.startMicrophone(_:backend:)` 继续使用 registry 严格选择；不支持的选定 backend 返回明确 unsupported input，不换成其他后端。开始过程被取消或 client close 后晚到的 task 必须先取消、join/drain，不能泄漏。
- backend 描述增加类型化输入能力，现有文件 backend 默认 `AUDIO_FILE`，TTS 输入集合为空；实际支持麦克风的 backend 才声明 `MICROPHONE`。描述表达结构能力，权限是否允许仍由 admission 检查，不能因麦克风拒权隐藏可用文件识别。

Apple backend 同一实例的文件与麦克风共用已有单音频槽；关闭立即拒绝 admission，join 所有在途任务后才返回。普通文件构造方式保持 file-only；新增显式 microphone session provider 的构造方式才启用该实例的麦克风能力。backend ID 保持 `apple.speech.recognizer`，locale 继续绑定 en-US，其他 locale 明确拒绝，不静默改写。

## 采集、会话与清理

`ODAIKitAppleSpeech` 增加 iOS 专用 capture 边界，内部系统实现使用 `AVAudioEngine`、input-node tap 和 `SFSpeechAudioBufferRecognitionRequest`。fake capture/recognizer 可分别测试采集与识别的竞态。

音频会话由宿主显式提供 `AppleSpeechAudioSessionProvider`；其 `acquire()` 返回本次独占 lease。backend 不自行读取一份全局状态后宣称能恢复整个 App 的音频：系统没有可供本包可靠查询并恢复的“之前 active”布尔状态。

Demo 的 provider 在语音页没有相机/其他语音任务时获得会话所有权，保存 category、mode、categoryOptions，配置 `.record` / `.measurement`，在启动 engine 前激活。清理时先停止 I/O，再以 `.notifyOthersOnDeactivation` deactivate，最后恢复保存的配置；不重新激活未知的先前 owner。其他宿主可提供自己的协调器/lease 策略。未获得 lease 时不安装 tap、不激活、不录音。

任务生命周期：

1. 检查 backend/client 未关闭、无在途文件/麦克风任务、locale 一致、Speech 与录音均获授权、recognizer 可用并支持 on-device。
2. 获得 session lease，建立 buffer request，设置 `requiresOnDeviceRecognition=true`，建立识别 delegate 和 generation。
3. 检查输入格式 channelCount/sampleRate 有效后安装唯一 tap，再 prepare/start engine。tap 只把 PCM 交给本 request，不发日志、不写文件；失败逐项回滚已获得资源。
4. “结束录音”关闭 buffer admission，停止 engine、移除本任务的 tap，join 已进入的 buffer delivery 后 `endAudio()` 恰一次。继续等 final 与 native `.completed`，成功结果只在所有资源释放后发布。
5. cancel、后台、离页、route change、interruption begin、media-services reset 或 timeout：立即停止采集并 endAudio/cancel，禁止迟到 final 覆盖取消。保持音频槽到 native completed、buffer delivery drain 与 lease 释放均完成。中断结束/回前台不自动录音。
6. 清理失败不得伪装成功；保留稳定 domain/code，仍尽最大努力完成其他清理项。lease release 幂等，不能把旧任务的 tap/observer/lease 用于新任务。

finish 不把 partial 晋升为 final；无 final 使用 `NO_RESULT`。超时沿用当前 30 秒默认值和 1–300 秒构造限制；包含录音与等待最终结果，超时不返回 partial。设备录音无效格式返回结构化不可用/平台失败，不进入可能导致 Objective-C 异常的 tap 安装。

## Bridge 与共享页面

继续使用 `SpeechBridgeInput.Microphone`。新增可选 `SpeechBridgeMicrophoneTask : BridgeTask`，仅增加 `finishInput()`；不改变 `BridgeTask.cancel()` 的含义。现有 fake、Android task 和文件任务不需要实现新接口。

`IOSSpeechBridge` 从真实 native 描述生成输入能力。文件请求继续 acquire/drain/release 文件 lease；麦克风请求调用新 native client task。UI 句柄即使在异步 native start 返回前收到 finish/cancel，也要记住意图并在 task 返回后执行；cancel 优先于 finish，不漏清理。两条路径共用同一音频槽、close worker 和 main callback 交付规则。

`SpeechViewModel` 增加本轮 microphone task 的可选结束能力和 `finishInput()`；完成输入后保持 controls disabled、显示“正在结束录音，等待识别”，直到真正终态。同步失败/同步终态不得被随后返回的句柄复活；旧句柄的 finish 不影响新轮次。

语音页根据实际输入能力显示“开始麦克风 ASR”。仅当当前句柄支持 finish 时显示“结束录音并识别”，取消按钮继续可用；Android 仍保留已有系统 recognizer 的结束方式，不伪造 finish 支持。开始前显示录音提示，transcript 仅在当前页面内存展示，离页清理。不以 RUNNING 宣称硬件已经开始采集；测试若需要 readiness，要由内部 native capture 边界提供可核查证据。

## Host 权限和独立工程

- 文件只申请 Speech；麦克风按 Speech → record 顺序申请两项授权，拒绝/受限立即返回对应稳定原因并提供 Settings 入口。权限检查与 backend 查询均不主动录音。
- iOS 17+ 使用 `AVAudioApplication.requestRecordPermission`，iOS 15–16 使用 `AVAudioSession.requestRecordPermission`。回调显式 hop main，不假设平台回调线程。
- prompt 等待期间离页、后台、取消或关闭使本次 generation 失效。晚到授权不启动音频；重试由最新合法请求接管，不能产生多个并行系统 prompt。
- generator 只为独立 SpeechDemo 添加 `NSMicrophoneUsageDescription`，不添加 background-audio mode、文件分享键或账户操作；旧原工程继续通过默认 unavailable 路径编译。

## 验证与完成门禁

实施计划按以下可独立验证的切片分 Task，逐项 RED→GREEN、review、commit；实现授权来自用户的 roadmap 指令和自动推进约定，不来自本文件自身。

1. **契约/客户端**：旧文件消费者源兼容；严格显式选择；file-only backend 的 microphone 被拒绝；task finish/cancel 幂等与 generation 隔离；start/close/cancel race join 资源；描述与 admission 分离。
2. **Apple capture/backend**：假 session/engine/recognizer 验证获得/启动/结束/释放顺序、所有启动失败回滚、buffer 在途 drain、正常 final、无 final、cancel/timeout、interruption/route/reset、close 并发等待、迟到 callback 不污染下一轮。macOS Package 测试继续编译，iOS 专用源码用 simulator/device build 验证。
3. **Bridge/VM/host**：Binding/Demo 两种 ABI 编译；文件 lease 回归；单音频槽；finish-before-start/cancel-before-start；permission 晚到/拒绝/后台/重试；结束输入后等待终态；新旧页面与 Android 测试不回退。
4. **生成/分发**：生成器预检新源码、拒绝覆盖；仅新工程有录音说明且无背景录音/分享键；旧项目 hash 不变；Speech contract-only consumer 不包含 AVFoundation/KMP/Compose；七产品与原生消费者验证。
5. **真实设备**：只有安排好受控朗读后才开始麦克风采集，避免录入旁人声音。验证主动结束取得 final、活动取消、同进程重入、TTS↔麦克风、后台取消/前台不启动、拒权恢复；单元/fixture 不替代自然语音。结果记录 transcript 是否存在，不导出内容/录音。无输入/未安排朗读时只记录未验，不自动开始录音。
6. **独立审查**：审查本包 API、lease、回调及权限边界；模型服务仍不可用时保留待审。旧 Task 7 的 UI/Done 审查仍单独待完成，不能由本设计或新包测试替代。远端同步继续等既定最终门禁，不 force push。

iOS15 真机、Android 真转写、自然语音质量/断网、第三方后端、iOS26 可选 SpeechAnalyzer、P7/P9/P10/P11 均保持 roadmap 原有要求；本包完成不等于 P6 或整个 roadmap 完成。

## Apple 官方依据与本地证据

已在 2026-10-20 读取官方 DocC JSON 和 Xcode iPhoneOS27.1 SDK header；原始 JSON 存在忽略目录 `artifacts/verification/ios-microphone-design-20261020/apple-*.json`。

- [SFSpeechAudioBufferRecognitionRequest](https://developer.apple.com/documentation/speech/sfspeechaudiobufferrecognitionrequest)：输入从空开始，通过 append 加入 PCM；必须显式 endAudio。SDK `Speech.framework/Headers/SFSpeechRecognitionRequest.h:137–182`。
- [requiresOnDeviceRecognition](https://developer.apple.com/documentation/speech/sfspeechrecognitionrequest/requiresondevicerecognition)：禁止发送音频到网络；须先检查 supportsOnDeviceRecognition，不能设置 flag 后就假定受支持。SDK 同文件 `:53–65`。
- [endAudio()](https://developer.apple.com/documentation/speech/sfspeechaudiobufferrecognitionrequest/endaudio())：标记不再有输入，不等于取消或已取得最终结果。
- [AVAudioSession](https://developer.apple.com/documentation/avfaudio/avaudiosession)：App 级 singleton；配置与激活分别执行。SDK `AVFAudio.framework/Headers/AVAudioSession.h:45–57,250–265`，停止 I/O 后再 deactivate。
- [requestRecordPermission](https://developer.apple.com/documentation/avfaudio/avaudioapplication/requestrecordpermission(completionhandler:))：录音需明确授权，拒绝时只能取得静音；回调线程不能假定 main。SDK `AVAudioApplication.h:107–119` 与 `AVAudioSession.h:121–133` 证明 iOS17 新旧 API 边界。
- SDK `AVAudioNode.h:81–168`：每条 bus 只能有一个 tap；iOS27 新增 throwing 安装变体，iOS15 基线不能无 availability 分支直接使用它。
- [Recognizing speech in live audio](https://developer.apple.com/documentation/speech/recognizing-speech-in-live-audio) 的当前版本已使用 iOS26/27 SpeechAnalyzer/CaptureInputSequenceProvider，并自动安装资源；只用来识别版本差异，**不照搬到 iOS15 on-device/no-download 实现**。

清理顺序、lease 协调、取消不自动恢复和测试门禁是本包提出的工程契约；官方文档没有证明本实现已无竞态，仍须通过上述测试和独立审查。

## 草案自检

已检查：保持文件非可空 URL API；finish/cancel 分离；权限按输入隔离；麦克风与文件不共享输入 lease；AVAudioSession 无隐式全局所有权；iOS/macOS 条件编译；generation、startup rollback、buffer drain、无 final、晚到授权均有验证归属。用户可在推进期间纠正方案；新增录音与远端操作仍受各自边界约束。
