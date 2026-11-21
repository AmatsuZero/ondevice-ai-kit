# W9-C：KMP 桥接、录音/导入和变声 Demo 实施 Prompt

你是用户手动启动的 W9-C 实施 Agent。目标是交付能接真实 A/B SDK 的双端 Bridge、共享页面及平台音频 Host 候选。W3/W6 已暂停，不领取旧诊断/模型/训练工作。本任务不启动其他 Agent/helper，不代替主代理做集成、设备操作或最终接受。

## 0. 基线、隔离和必读

- 仓库：`/Users/daubert/Github/ondevice-ai-kit`。
- 主代理当前目录：`.worktrees/foundation`，分支 `feat/w9-ab-integration-20261117`。
- **C 的代码基线：`46c7e4af7a1a34b140899893c2992cd9b34947b1`。** A 原提交 `b12cd53` 对应集成提交 `1d99dfb`；B 原提交 `9629e5c` 对应 `8f0834c`；`46c7e4a` 已接好 Apple Package 与 Android settings。不要重复 cherry-pick A/B。
- 不在 foundation 写代码或切分支；先检查现有 worktree/分支/活动写入。没有本任务目录时，在仓库根用 `rtk proxy git worktree add .worktrees/w9-c-kmp-demo -b w9-c/kmp-demo-candidate 46c7e4af7a1a34b140899893c2992cd9b34947b1`，随后只在该独立 worktree 工作。若已存在，先核身份与 dirty 状态，保留已有工作，不 reset/stash/清理/覆盖。
- shell 命令用 `rtk`（通常 `rtk proxy`）。交付候选文件并只提交本任务拥有的文件；不 push。
- 读根 `AGENTS.md`、foundation 当前 `docs/agent-pitfalls.md`（含未提交最新条目）、`docs/HANDOFF-2026-11-17-weekly-delivery.md`、总计划中 W9-C、`docs/voice-effects/contract-v1.md`、`docs/validation/w9-ab-candidate-review-20261117.md`（后者可从 foundation 只读取得）。文件不在 C 基线中时只读 foundation，不改主树。
- 合同 SHA-256 必须为 `87f890278a07a457d656970a16ca3001ef034cd05e9001b655eb2b84779b86d2`。用原文件核对，不能自行修改合同或跨端名字。

You are not alone in the codebase. Preserve other edits. Do not recursively delegate. Report blockers and scope changes.

Before work and on errors, consult docs/agent-pitfalls.md. Preserve other edits, do not retry a known quota failure, and report concrete blockers upward. 本任务可读文件，必须亲读最新 foundation pitfalls，并在 DELIVERY 引用适用标题。不得为消除错误扩大权限、修改全局配置或换付费模型。

## 1. 当前能力与固定接口

A/B 已在主代理本地验过，不能继承为 C 已验：Apple 完整包 509 XCTest（含变声 25），Android 变声 28 JUnit、lint、Release AAR 通过；iOS 15 目标构建通过不等于设备通过。独立复核仍 unavailable（此前 429，不重试）。

原生构造与使用：

```swift
import ODAIKitVoiceEffects
import ODAIKitAppleVoiceEffects
// Host 先在非 UI 工作上下文创建 SDK 专用输出目录。
let client = try VoiceEffectsClient(backend: AppleVoiceEffectsBackend(), outputDirectory: outputDirectory)
let output = try await client.process(VoiceEffectsRequest(inputFile: canonicalWAV, preset: .high))
await client.drain()
await client.close()
try await client.releaseOutput(id: output.id) // output 跨 close 有效
```

```kotlin
import io.ondeviceai.voiceeffects.*
val client = VoiceEffectsClient(WindowedPitchVoiceEffectsBackend(), outputDirectory)
val output = client.process(VoiceEffectsRequest(canonicalWav, VoiceEffectsPreset.HIGH))
client.drain()
client.close()
client.releaseOutput(output.id)
```

输出字段固定为 id、audioFile、backendId、sampleRate、frameCount、durationSeconds；Swift frameCount=Int64，Kotlin=Long。所有预设 ORIGINAL/HIGH/LOW（Swift original/high/low），±4 半音，原声是归一化输入的独立精确副本。不是自然男女声/说话人克隆，不改成变速播放。

原生只接受 canonical 44-byte RIFF/WAVE PCM1、mono PCM16LE，16/44.1/48kHz，1帧..30秒。Host 负责导入/录音归一化；SDK 保持归一化输入的采样率与帧数。可用系统 API 转码，不新增库/模型。首版 Host 输出统一 16kHz mono PCM16LE 即可；越30秒明确拒绝/录音到上限主动停止，不静默截短导入。原压缩文件与“原声试听”的归一化文件区分清楚。

## 2. 你拥有的候选文件

仅新增/修改下列路径中的本任务文件：

- `kmp-binding/src/commonMain/kotlin/io/ondeviceai/binding/VoiceEffectsBridge.kt`
- `kmp-binding/src/commonTest/kotlin/io/ondeviceai/binding/VoiceEffectsBridgeContractTest.kt`
- `kmp-binding/src/androidMain/kotlin/io/ondeviceai/binding/AndroidVoiceEffectsBridge.kt`
- `kmp-binding/src/androidHostTest/kotlin/io/ondeviceai/binding/AndroidVoiceEffectsBridgeTest.kt`（先核项目现有测试 sourceSet；若不适用只提出明确替代位置，不改 Gradle）
- `kmp-binding/apple/IOSVoiceEffectsBridge.swift`、`kmp-binding/apple/Tests/IOSVoiceEffectsBridgeTests.swift`
- `demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/VoiceEffects*.kt`
- `demo/shared/src/commonTest/kotlin/io/ondeviceai/demo/VoiceEffects*.kt`
- `demo/shared/src/iosMain/kotlin/io/ondeviceai/demo/VoiceEffects*.kt`（新的 UIViewController 入口，不能改已有 MainViewController.kt）
- `demo/iosApp/VoiceEffects*.swift`
- `demo/androidApp/src/main/kotlin/io/ondeviceai/demo/VoiceEffects*.kt`
- `demo/androidApp/src/test/kotlin/io/ondeviceai/demo/VoiceEffects*.kt`
- `docs/agent-tasks/weekly-20261117/C-delivery/DELIVERY.md`、同目录 `INTEGRATION.md` 与 `suggestions/*.patch`。

其他文件为主代理所有：A/B 全部源码与测试、合同、现有 Speech/Video/Face/Identity/Multimodal Host、DemoController/MainActivity/App 入口、现有 MainViewController、全部 build.gradle/settings/Package/版本目录/manifest/Info.plist/PBX、`scripts/generate-speech-demo.rb` 等生成器及 roadmap/状态表。共享接线需要改动时，交独立精确 patch 建议和调用签名，由主代理应用；不能直接修改、顺手重构或提交。不要改现有 ASR/TTS 行为。

## 3. 实施顺序与行为

1. 先写公共合同、生命周期/状态测试，再实现 Bridge 与 ViewModel；最后交平台 Host 和页面。按小批文件落盘，实际交付完整可审查文件，不只给说明/mock/占位 TODO。未运行测试就记录未运行，不能伪造 observed RED/GREEN。
2. 公共类型/方法严格取 contract-v1 第4节：VoiceEffectsBridgePreset/Request/Output/FailureCode/Failure，Process/Release/CloseCallback，`process(request, callback): BridgeTask`、`releaseOutput(reference, callback)`、`close(callback)`，以及 UnavailableVoiceEffectsBridge。不加 selection、progress 或公共 File/URL/PCM；reference 是非空 opaque token，独立于原生 id/path。
3. 复用已有 VideoBridge 的租约/一次终态思路，但逐项验证，不能机械复制。平台 adapter 自管输入 acquire/release 和输出 token→原生 id/file 的映射；resolver 仅供 native Host，公共状态不暴露路径。输入 lease 必须等真实 drain 完成后释放。unknown/其他 bridge token 拒绝且不触碰文件；成功释放可幂等，失败保留所有权可重试，输出跨 close 有效。
4. 取消/关闭与结果竞争只有一个胜者；同步回调、回调重入、取消早于 worker 开始、迟到成功、重复/并发 close/release 都处理。UI/main admission 和回调，DSP/解码/文件 IO 离开 UI。close 与取消都不能因宿主 scope 已取消而跳过清理。
5. **Android 特别注意：** B 在取消清理失败时会保持 process 等待、保持 BUSY，直到 drain/close 真实重试清理。adapter 不能先无限 join process、再调用 drain/close，形成互相等待；取消后应安排 native drain/close 与任务等待的正确顺序。失败必须如实给出可重试恢复，不擅自超时宣称释放或修改 B 来绕过。Apple 非抛错 drain/close 对永久删除拒绝会继续等待；UI 不能阻塞等待，不虚报 onClosed。
6. 统一稳定错误码。PLATFORM 仅 domain/code 且 reason=null；其他错误 domain/code=null，reason 若有取稳定枚举，不外泄路径、NSError userInfo、异常文本/cause/suppressed。取消走 onCancelled，未链接模块走 BACKEND_UNAVAILABLE。Swift 对 Kotlin 导出方法标签以实际 header/现有适配器为准，不猜 `failure_`/`close` 名。
7. UI 只做首版核心链路：选文件/明确点击开始录音→停止并归一化→选择原声/升调/降调→转换/取消→原声与结果试听→导出 WAV。清楚显示30秒限制、处理中/取消中/失败状态。拒麦克风权限后仍可导入；导入/处理/导出不索麦克风权限。没有自动开麦/实时监听。
8. 重选或 picker取消保留当前有效输入；新导入成功后才替换。每次请求用 generation/operation identity 防止迟到回调改新页面。转换、录音、播放、导出及离页有明确互斥/持有关系：先停止播放器、等待读取结束再 release；导出完成/失败/取消前保留输出，失败允许重试。取消/离页/后台/中断都要结束硬件资源与未交付文件生命周期，不删用户原文件。
9. 平台 Host 使用系统文件选择、前台录音、播放与分享/文档导出 API，音频解码/归一化有实际读取/时长/帧数边界和清理；不能只改 WAV header、把压缩文件当 PCM、或以无限读到 EOF 代替上限。安全作用域 URL/Content URI 先在有效授权期内复制为 Host 自有输入，lease 结束再清理。
10. 与既有 Speech 录音/ASR/TTS、播放器争用通过 Host 明确 admission/协调接缝处理；需要修改既有 Host 时在 INTEGRATION.md 交主代理 exact patch，不重开 W3。iOS AVAudioSession 属 Host，Bridge/SDK 不动它；Android 录音/播放器生命周期亦由 Host 持有。App 路由/权限/生成器接线建议应明确创建、进入、离开、close 和释放时序。

## 4. 验收用例与边界

至少覆盖：public request/failure 约束、Unavailable、busy/closed、同步与迟到回调、真实 native 取消后 drain 再 input.release、取消后清理失败恢复、重复/并发 close/release、unknown/其他 token、输出跨close、释放失败重试；拒权仍能导入、picker取消/导入失败保留旧输入、重选与离页竞态、导出失败可重试、播放/导出持有期间不提前删文件、录音/试听/既有语音争用。

状态测试可以用 fake，最终生产路径必须接真实 AppleVoiceEffectsBackend / WindowedPitchVoiceEffectsBackend。真实 SDK 集成验证使用合成 canonical WAV；麦克风、用户媒体和设备验收由主代理另安排，不自行录音/启动模拟器/操作真机。不要用 fake 的成功推断已完成音频转换。

按现有分工，主代理执行正式 Gradle/Swift/消费者/App/设备验收；你交测试源码、准确建议命令和先决条件，未执行就标 unverified。可执行不涉及设备/网络的本地文件检查（diff --check、hash、编译前静态核对）。若工具已执行，保留真实 exit/status/原始日志，不把 NO-SOURCE、CLI0、空结果或旧 XML 当通过。

使用陌生平台 API 前先查官方文档（Context7 可用则先 resolve/query，否则官方原文），保留下限/行为依据。保持 iOS15/macOS12、Android API26；不能抬高全库下限。不给现有 model/dependency/lock、凭据、签名或全局配置做附带更改；不下载模型/媒体、不上传/发布，不绕过 quota/权限，不开启付费 fallback。

## 5. 交付与停止条件

- 提交候选源码/测试以及 DELIVERY/INTEGRATION；只 stage 拥有的文件，不 `git add .`；给分支、完整commit、文件列表与 SHA-256。
- DELIVERY 分开列：实际实现、实际运行检查、失败/恢复、未运行、需主代理接线、设备/听感/资源/独立复核缺口，并引用亲读的 pitfalls 标题。
- INTEGRATION 给准确类型/构造方法/回调/cleanup时序，以及 kmp-binding Android dependency、Android App dependency、Swift module、桥接导出、Host/路由/权限/生成器的最小建议 patch。patch 放 suggestions，不应用共享文件；不能让主代理猜该怎么接。
- 若某个需求必须超出拥有范围或合同，报告具体文件、原因和最小补丁建议，继续其他独立部分；不自行扩大权限/合同，不递归派工。
- 停在“完整候选已提交，等待主代理接线验收”。不要宣称 C 已完成产品验收/真机成功/独立批准，也不要只交规划文档就标完成。
