# VoiceEffects v1：实施合同

2026-11-17；**frozen-for-implementation**。主代理在本轮用户授权范围内冻结工程接口；这不是人声听感、产品资源预算、最低系统实体设备或发布验收通过。依据：[W9-0 最小链路结果](../validation/w9-minimal-chain-20261117.md)。后续 A/B/C 使用本文件的同一 SHA，不自行更改字段或效果语义。

## 1. 范围与效果

- 独立离线 audio→audio；录音结束或文件导入后处理。没有 ASR/TTS、联网、模型、实时监听或指定说话人转换。
- `original` / `ORIGINAL`：原文件逐字节独立复制；`high` / `HIGH`：目标 +4 半音；`low` / `LOW`：目标 -4 半音。不是自然男女声转换。
- 输出保持输入采样率和精确 frameCount；durationSeconds = frameCount / sampleRate。效果允许边界滤波与窗化伪影，不能将帧数相同宣传为波形/音色无损。
- Apple 执行面 `apple.time-pitch`：AVAudioUnitTimePitch rate=1、pitch=±400 cents，AVAudioEngine offline manual rendering。
- Android 执行面 `android.windowed-pitch`：本项目原创、80ms 双读头窗化变调与线性插值，输入归一化到 [-1,1]，输出 PCM16 四舍五入并钳位 [-32768,32767]。两端算法不要求逐字节相同。
- 不新增第三方依赖。原型证明的是这两个执行面的可行性，生产版可在合同内修复实现，但不得暗中变为改速播放或增加库/模型。

## 2. 首版文件矩阵

| 字段 | v1 输入/输出要求 |
| --- | --- |
| 输入 | 本地、可读 regular file；RIFF/WAVE，PCM format 1，单声道，16-bit little endian |
| 输入封装 | 首版转换入口接受 canonical 44-byte header：fmt 大小16、data从44开始；RIFF/data/byteRate/blockAlign与真实文件一致。非 canonical 文件由宿主在导入/录音后先归一化，转换器明确拒绝而非猜测 |
| sampleRate | 16000、44100、48000 Hz |
| frameCount | 1..sampleRate×30，正数，整帧；最长30秒，不静默截短 |
| 输出 | 有效 mono PCM16 WAV，同采样率、同帧数；效果输出可包含合法附加 RIFF chunks，播放器/导出须实际支持 |
| 缓冲 | bypass独立副本；转换不修改输入。输入大小与内容边界在分配前/读取时核对，防止并发增长逃逸 |

宿主录音/导入适配器必须提供 canonical 文件；SDK 输出再次导入也走归一化。Apple 原型输出包含附加 chunk，直接喂回原型会被拒绝；这已实测记录，不能把原型包装为任意 WAV 解码器。其他编码、声道、采样率、压缩音频与超限明确拒绝。

工程烟测门（不是产品质量或性能预算）：220/440/880 Hz 合成稳态纯音、三采样率、三个预设，输出帧数精确相同，去除两端200ms后音高相对目标误差≤2%，静音不产生非零PCM、无非有限值或数值越界。此门不能替代人声可懂度、爆音、前后截断与资源验收。

## 3. 原生 SDK 的固定字段/方法

Swift 模块 `ODAIKitVoiceEffects` 与 `ODAIKitAppleVoiceEffects`；Android 包 `io.ondeviceai.voiceeffects`。底层可以使用 actor/协程，公共名字固定如下；实际实现尚未交付。

| 类型/方法 | Swift | Kotlin |
| --- | --- | --- |
| 预设 | `VoiceEffectsPreset`：original/high/low | `VoiceEffectsPreset`：ORIGINAL/HIGH/LOW |
| 请求 | `VoiceEffectsRequest(inputFile: URL, preset: VoiceEffectsPreset)` | `VoiceEffectsRequest(inputFile: File, preset: VoiceEffectsPreset)` |
| 输出 | `VoiceEffectsOutput` | `VoiceEffectsOutput` |
| 输出字段 | id:String、audioFile:URL、backendId:String、sampleRate:Int、frameCount:Int64、durationSeconds:Double | id:String、audioFile:File、backendId:String、sampleRate:Int、frameCount:Long、durationSeconds:Double |
| 转换 | `process(_ request: VoiceEffectsRequest) async throws -> VoiceEffectsOutput` | `suspend fun process(request: VoiceEffectsRequest): VoiceEffectsOutput` |
| 等待当前任务释放输入 | `drain() async` | `suspend fun drain()` |
| 关闭 | `close() async` | `suspend fun close()` |
| 释放已交付输出 | `releaseOutput(id: String) async throws` | `suspend fun releaseOutput(id: String)` |

首版每个 client 只有一个预先配置的后端，不增加跨端后端选择协议。Client 的初始化接收后端和 SDK 自有输出目录；默认工厂由主代理在 A/B 实施前统一接线。初始化签名不进入 C 的公共桥接，不能由各线同时修改 Host/factory。

## 4. KMP 固定公共合同

文件 `kmp-binding/src/commonMain/kotlin/io/ondeviceai/binding/VoiceEffectsBridge.kt`，包 `io.ondeviceai.binding`。遵循现有 VideoBridge 的输出所有权与回调约定。

- `VoiceEffectsBridgePreset { ORIGINAL, HIGH, LOW }`。
- `VoiceEffectsBridgeRequest(inputReference: String, preset: VoiceEffectsBridgePreset)`；reference是宿主提供的非空 opaque token，公共合同不传 File/URL/PCM/AV对象。
- `VoiceEffectsBridgeOutput(reference: String, backendId: String, sampleRate: Int, frameCount: Long, durationSeconds: Double)`；输出字段对应原生结果，reference独立于原生 id。
- `VoiceEffectsBridgeFailureCode { CLOSED, BUSY, INVALID_INPUT, UNSUPPORTED_INPUT, BACKEND_UNAVAILABLE, PLATFORM }`。
- `VoiceEffectsBridgeFailure(code, reason: String?, domain: String? = null, platformCode: Int? = null)`：仅 PLATFORM 填非空domain/平台code且reason=null；其他错误domain/code=null，不外泄路径/异常文本。
- `VoiceEffectsBridgeProcessCallback`：`onResult(output)`、`onFailure(failure)`、`onCancelled()`；首版不承诺数值progress。
- `VoiceEffectsBridgeReleaseCallback`：`onReleased()`、`onFailure(failure)`；`VoiceEffectsBridgeCloseCallback`：`onClosed()`、`onFailure(failure)`。
- `VoiceEffectsBridge.process(request, callback): BridgeTask`、`releaseOutput(reference, callback)`、`close(callback)`。方法/参数类型按上述同名类型，不增 selection 参数。
- 缺模块由 `UnavailableVoiceEffectsBridge` 显式返回 BACKEND_UNAVAILABLE；不返回假成功，不退到TTS/ASR。

## 5. 生命周期与所有权（实施必须满足，原型尚未实现）

1. 每client同时最多一个转换。活动期间新转换返回 BUSY；close后返回 CLOSED。原生错误可用既有 KitError/等效异常映射到固定桥接码，不能发明平台差异语义。
2. 调用方在process正常返回前保持输入不变；取消后仍保持到drain/close返回。SDK不得删除调用方输入或原始导入文件。
3. 取消是请求，不是即时释放证明。工作退出、输入关闭、未交付输出清理后才发送一次取消终态；不能在后台渲染仍运行时放行下一次任务。关闭取消当前任务并等待实际清理，可重复/并发close并等待同一完成点。
4. 结果发布与取消必须有一个明确胜者。若取消先被接受，则不得交付迟到文件；若结果已经交付，close不删除已交付输出。所有异步/异常路径最多一次终态。
5. SDK生成自有唯一输出路径；先写任务自有临时文件，finalize/关闭成功后发布。不能覆盖既有文件，不留下可见半成品；校验/渲染/发布失败须清理自己的临时文件，不清理别人的路径。
6. 输出有效至releaseOutput成功，跨close有效。成功释放可幂等；失败保留所有权与重试能力；未知/他人reference不能删文件。播放器/导出持引用期间不得提前释放。
7. Bridge入场与回调在平台UI/main dispatcher；回调可能同步。昂贵文件/DSP工作离开UI线程；用户取消/离页/close不能冻结UI。每条释放/关闭回调同样一次完成。
8. 前台录音仅由用户明确点击开始；宿主拥有麦克风、播放器、音频会话协调，录完生成上述输入。导入/转换/导出不索取麦克风权限。既有ASR/TTS争用须显式处理，不重开W3研发。

## 6. 并行实施放行和验收边界

- A/B/C 可消费本合同的同一SHA并交付各自候选；共享Package/Gradle/Host/生成器仍由主代理集成。当前只完成原型与合同，不代表这些线已经派发。
- A/B 需补真正的SDK admission/cancel/drain/close/release与测试；不能直接把同步CLI包装为已完成SDK。
- C 需补录音/导入归一化、原声/结果试听、导出/释放、拒权/离页/中断以及真实后端接入。
- 物理iPhone/Android、最低系统设备、录音体验、人声听感、SDK消费者、资源与最终复审均另验。iOS/Android模拟器或ART shell不等于生产App或OEM验收。
- 原型的普通move/rename是隔离单所有者路径验证，不证明生产并发发布保证；不得把原型的检查后rename照搬并宣称满足本节所有权要求。
