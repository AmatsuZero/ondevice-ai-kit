# 本周阶段交付与 W9 变声 Implementation Plan

执行接续：已完成[双端模拟器核心链路](../../validation/w9-simulator-acceptance-20261117.md)，并修复Android实际App的硬链接发布拒绝；SDK30＋Demo67测试、lint/双APK、新隔离AAR独立消费通过。当前 **simulator-core-validated / release not-ready**。接下来依次固定最终组合/通知与资源测量方案，用户在场后补真机录音/听感/活动取消/中断及同候选预算，再作独立最终复审。不重复派发A/B/C，不重开W3/W6；没有新云端helper或额度重试。

当前增量：A/B/C 已集成本地验证，见 [C报告](../../validation/w9-c-integration-20261117.md) 与 [A/B报告](../../validation/w9-ab-integration-20261117.md)。用户要求主代理直接执行 C，覆盖本任务旧外部实现分工；没有新 helper/额度重试。Android67测试/lint/双APK、shared JVM113、Swift WAV3、iOS真实桥接4/UI1与unsigned App通过；独立审核/真机/听感/资源/完整组成和消费仍未接受。C Prompt仅保留作合同参考，不再等启动；下方最初派工条目按本段和状态表覆盖。


> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans within the repository's existing allocation. External helpers supply implementation candidates; the primary integrates and validates; at most one native child reviews code and acceptance evidence read-only. The user's explicit parallel method is already selected; no new execution-method confirmation is required.

**Goal:** 暂停 W3/W6 后续研发，新增双端离线效果变声，争取 2026-11-20 候选、2026-11-22 完成本周范围验收。

**Architecture:** 独立 audio→audio SDK 能力，沿用已有文件租约与生命周期模式；原生后端处理音频，KMP 暴露稳定 reference 和状态。合同冻结后 Apple、Android 和 bridge/Demo 独立交付候选；发布证据线并行，主代理统一集成与真机验证。

**Tech Stack:** 现有 Swift/SwiftPM、Kotlin/Android/KMP、Compose 与原生音频执行面。变声算法/渲染方案先验证，当前不新增第三方依赖。

**Spec:** [本周范围与产品合同](../specs/2026-11-17-weekly-delivery-voice-effects-design.md)。

## Global Constraints

- 工作目录 `.worktrees/foundation`；命令使用 `rtk`。保留所有 dirty/untracked、普通 App、模型、失败/原始证据；不重置或混入其他提交。
- W3/W6 `paused/deferred`，不重放识别/视频实验、不新增模型/训练/专属诊断；交付包含的依赖仍须必要通知与消费验收。
- W7 生产校准/PAD 延期；已有功能和未校准拒绝保留。W8 继续实际交付范围的必要验证，未测与预算不可自定通过。
- 基础库下限沿用 Apple iOS 15/macOS 12、Android API 26；高版本执行面要 availability/unavailable，不为变声静默抬高全库下限。最低系统实体设备延期仍明确记录。
- W9 产品范围是录音后/文件效果变声、SDK + Demo、试听/导出。工程格式/时长/半音等数值在 W9-0 实测后冻结；新依赖另行授权。
- 外部实现候选；主代理规划、接线和测试/构建/设备验收；native ≤1，仅只读代码与验收证据审查。准备提示词不是已派发，产出候选不是已接受。
- CBC 新 429 停该线路，不自动重试/换模/付费；reset 不是恢复证明。付费逐次确认、新 Copilot 暂停保持；不把并行请求理解为解除这些限制。
- 无自动开麦、私人媒体外传、全局配置/凭据/签名资产修改或自动发布。共享设备逐项预约；构建使用各自输出目录，集成构建串行。

## Requirements Summary / Evidence

| 事实或决策 | 依据 |
| --- | --- |
| 用户本轮要求暂停 W3/W6、增加变声、本周完成并行推进 | 2026-11-17 当前会话；本计划仅将本周阶段范围执行化 |
| 现有能力是 ASR/TTS，不能视为已有变声 | `apple/Sources/ODAIKitSpeech/SpeechClient.swift:4`；`kmp-binding/src/commonMain/kotlin/io/ondeviceai/binding/SpeechBridge.kt:4` |
| 现有麦克风合同不提供通用 PCM 输出 | `apple/Sources/ODAIKitSpeech/SpeechMicrophone.swift:16`；`kmp-binding/apple/IOSSpeechAudioInput.swift:3` |
| Apple/Android 模块配置及下限应保持 | `apple/Package.swift:3`；`android/speech/build.gradle.kts:2`；`settings.gradle.kts:27` |
| Whisper 已集成、Qwen 是另一实验轨道 | `docs/validation/w3-integrated-release-review-20261116.md:3` |
| W6 最新机械分数不代表自然最终答案质量 | `docs/validation/f08-qwen35-4b-fresh36-20261117.md:7` |
| W7 生产门依赖数据/协议 | `docs/roadmap/W7-identity-pad.md` 历史目标与当前延期裁定 |
| W8 测量不等于预算通过，W1/W2/W4/W5不重开 | `docs/roadmap/W8-resources.md`；`docs/validation/p11-readiness-reconciliation-20261109.md:30` |
| 助手成功、编译、host 夹具不能替代真机接受 | `docs/agent-pitfalls.md` §1、§11、§12；W3 ACK OEM 边界条目 |

## Review Focus

1. 输入格式合法但时长/帧数/声道不一致：真实解析拒绝越界，错误不留输出。
2. 渲染期间取消或 close 与回调竞态：只一次终态，等待释放，不交付迟到文件。
3. 麦克风/播放器/变声与既有 TTS/ASR 争用：显式 admission，中断后无悬挂会话。
4. 导出失败/重选/离页：原输入和已接受输出租约正确，不误删用户文件。
5. 默认/完整组合构建配置漂移：Package/Gradle/generator 只由主代理接线，同一候选来源及通知可核。

## Acceptance Criteria

验收勾选以[W9 检查表](../../roadmap/W9-voice-effects.md)和[本周状态表](../../validation/weekly-delivery-status-20261117.md)为准。每个检查须有实际命令/原始结果与候选身份；ready、built、device-passed、reviewed、delivered 分开记录。阻断项未消除时保持 not-ready，不因到期自动降级标准。

## Task 0：本周范围与合同入口（主代理）

**Files:** 本计划、spec、`docs/ROADMAP.md`、W3/W6/W7/W8/W9 roadmap、当前 handoff、状态表和 dispatch 入口。

- [x] 固定暂停/延期/本周必做范围、时间目标、分工和状态字段；历史正文只前置新裁定。
- [x] 本地链接/历史正文及原dirty保护已执行检查；初次三处EOF定位失败已修正、原收据保留，独立只读范围复核ACCEPT；最终机械结果见状态表/收据。

## Task W9-0：合同与可行性先行（外部候选，主代理冻结）

**Owned candidate files:** `docs/voice-effects/contract-v1.md`、`probes/voice-effects-feasibility/apple/*`、`probes/voice-effects-feasibility/android/*`；只在隔离 task 目录交付候选。

**Consumes:** spec、现有 Speech/Video 的 request/result/lease/busy/close 模式、Apple/Android 现有依赖清单。先读官方文档（Context7 可用时先用它），不靠模型记忆选平台 API。

**Produces:** 冻结的效果语义、格式/上限、跨端类型字段、错误和输出所有权合同，以及两端真实文件渲染验证结果。候选 public API 命名空间为 `VoiceEffects`，禁止各线独立更名；产物经主代理接受后提供确定的签名和摘要给后续任务。

- [x] 先交最小可执行候选和正/反例：bypass、非 bypass 实际渲染、空/坏/超限输入，不能只返回设计或 mock。
- [x] 主代理先跑合成音频验证频率/时长、可播放 WAV、输入不变和失败清理；先证明 Android 无新依赖路径，不能默认已有引擎。
- [x] 已证明无新依赖路径，未改为变速；不是产品听感接受。
- [x] 主代理接受原型并冻结 [合同](../../voice-effects/contract-v1.md)，SHA `87f890278a07a457d656970a16ca3001ef034cd05e9001b655eb2b84779b86d2`；[四环境结果](../../validation/w9-minimal-chain-20261117.md)。A/B/C ready但尚未派发，SDK/Demo待实现；最终独立复核429无结论。

## Task W9-A：Apple SDK 后端（外部 Agent A）

**Owned candidate files:** `apple/Sources/ODAIKitVoiceEffects/*`、`apple/Sources/ODAIKitAppleVoiceEffects/*`、`apple/Tests/ODAIKitVoiceEffectsTests/*`。

**Consumes:** 已冻结合同及 Swift 原生接口；**Produces:** 文件处理客户端、Apple 后端、输出 lease 与定向测试。`apple/Package.swift` 变更仅提出接线补丁，由主代理统一集成。

- [ ] 先交失败测试，覆盖 Review Focus 1/2/5、空/超限、活动取消/close/重入；预设数值只取冻结合同。
- [ ] 交实际渲染实现候选，不调用 ASR/TTS 或联网模型。
- [ ] 主代理集成后执行 `rtk proxy swift test --package-path apple --filter ODAIKitVoiceEffectsTests`（名称以实际冻结 test target 为准），再验证 iPhoneOS 构建/真实输出。
- [ ] 候选源码、测试、实际执行结果和未验证项分别留收据。

## Task W9-B：Android SDK 后端（外部 Agent B）

**Owned candidate files:** `android/voice-effects/build.gradle.kts`、`android/voice-effects/src/*`。

**Consumes:** 同一冻结合同和已验 Android 处理方案；**Produces:** 客户端、离线渲染/输出 lease、定向测试。根 settings/版本目录只向主代理提出补丁。

- [ ] 测试先覆盖格式/数值边界、流式读写、活动取消/close、busy 与所有权，至少一条证明真实音频效果而非仅选择预设。
- [ ] 实现采用已批准执行面，不新增依赖/模型、不触发 Google 资源请求。
- [ ] 主代理接线后执行 `rtk proxy ./gradlew :android:voice-effects:testDebugUnitTest :android:voice-effects:lintDebug :android:voice-effects:assembleRelease`。
- [ ] 真实设备音频转换/播放/导出验证另列，JVM 通过不写为设备通过。

## Task W9-C：KMP、录音与 Demo（用户改为主代理直接执行）

**Owned candidate files:** `kmp-binding/src/commonMain/kotlin/io/ondeviceai/binding/VoiceEffectsBridge.kt`、`kmp-binding/src/commonTest/kotlin/io/ondeviceai/binding/VoiceEffectsBridgeContractTest.kt`、`kmp-binding/src/androidMain/kotlin/io/ondeviceai/binding/AndroidVoiceEffectsBridge.kt`、`kmp-binding/apple/IOSVoiceEffectsBridge.swift`、`demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/VoiceEffects*.kt`、`demo/shared/src/commonTest/kotlin/io/ondeviceai/demo/VoiceEffects*.kt`、`demo/iosApp/VoiceEffects*.swift`、`demo/androidApp/src/main/kotlin/io/ondeviceai/demo/VoiceEffects*.kt`。

**Consumes:** 同一合同；初期使用后端 fake 只验状态，最终必须接实际 A/B 产物。**Produces:** 原生适配器、录音/导入/试听/导出动作、共享状态与页面；公共桥接只传稳定 reference。

- [ ] 测试先覆盖拒权、重选/取消保留、处理时离页、迟到回调与导出失败（Review Focus 2/3/4）。
- [x] 录音开始需明确 UI 动作；录完走同一文件转换入口，不做实时监听。源码及UI未开麦烟测已验，实际录音体验待真机。
- [x] 主代理统一接线 Host、路由、factory、Gradle与生成器；保留普通PBX及生成器原有Release修正。
- [x] 主代理完成 shared/binding 定向验证、Android和iOS opt-in组合构建；真实录音需后续用户在场动作，合成夹具不替代录音体验。
- [x] shared状态覆盖同步/迟到回调、取消重试、导入取消/失败保留、close与输出读引用；双端真实桥接验证三预设、活动取消/drain与输出所有权。拒权/录音/导出失败等硬件和系统交互仍待设备证据，不勾选首项完整覆盖。

## Task R：候选交付与限制材料（主代理验收，本地Ollama限域包装候选）

**Owned candidate files:** `docs/release/2026-11-weekly-candidate-checklist.md`、`docs/release/2026-11-weekly-known-limitations.md`、`examples/voice-effects-consumer/*`（后者等合同后）。

**Consumes:** 已有分发/通知/消费者证据、当前暂停范围；**Produces:** 实际仍交付组件的通知/来源差异表、消费者检查候选、能力限制清单。禁止重复做 W3 native 归因、W6 模型评估或已通过的 Nexus 上传消费。

- [x] 已按本周真实候选列清单、限制与设备步骤；W9源码、二进制与物理运行分开，见[R报告](../../validation/w9-r-delivery-20261117.md)。
- [x] 已标注Android Whisper/系统ASR、iOS ASR、W6暂停、Identity未校准/PAD延期和最低系统设备缺口。
- [x] 已记录当前C包成员/通知差异及最终组合适用性待验；不将零notice命中作为无需通知。
- [ ] 双端有界独立消费和Apple分发归档已通过；最终组合的完整通知与消费者接受仍待验，不自动发布、上传、打tag。

## Task Q：独立代码/验收证据审查（唯一 native reviewer）

**Owned output:** `docs/validation/weekly-delivery-review-20261117.md` 或原生结果消息，由主代理保存。只读代码与证据，不规划新任务、不实现、不构建、不操作设备；出现 429 不自动重试。

- [x] 审查范围更新是否把暂停写成通过、把工程提议写成批准预算，及事实是否与最新证据冲突；本轮唯一native返回ACCEPT，仅文档/证据边界。
- [ ] 逐候选检查源/测试/实际验收的对应和边界；作者自检与独立接受分开。
- [ ] 最终复审只对同一候选和本周范围给结论，完整原 roadmap 仍保留未关闭项。

## 日程与关键路径

| 日期（Asia/Shanghai） | 交付点 | 未达时动作 |
| --- | --- | --- |
| 11/17 周二 | 范围文档、派工提示、W9-0 双端最小链路/合同 | 实现仍未启动则明确记风险；有新依赖需求先具体决策 |
| 11/18 周三 | A/B SDK 候选；C 状态/UI、R 通知与限制并行 | 仅返设计/mock不能晋级；不切回W3/W6填空 |
| 11/19 周四 | 主代理集成双端、独立消费和定向测试/组合构建 | 阻断优先修复，不追加新效果/格式/模型 |
| 11/20 周五 | 可安装候选、目标设备核心流程与资源报告 | 候选未通过继续标 not-ready，并列具体剩余 |
| 11/21–22 | 真机问题修复、必要资源/通知、同候选复审与交付材料 | 未通过不能用日期或文件数宣布完成 |

## 调度、验证与停止条件

最多三条不冲突外部候选线并行，唯一 native reviewer 可并行；这是任务容量，不是额度已恢复或任务已启动。主代理处理全局配置与共享 Host，设备和最终集成构建串行。每次实际派发保存 prompt/raw/终态与文件 SHA，登记 ready/dispatched/delivered/accepted/blocked。新提示必须带 `docs/agent-pitfalls.md` 要求；无工具提示注入相关原文。

当前 CBC hy3/hy4flash-free 有已知 429 记录；不按 reset 自动重试，不换其他同供应商模型绕限，不复用旧付费授权。没有可用实现线路时，主代理继续完成规划/本地验收准备，状态表明确候选未派发；不以原生实现违反既有分工。

最初文档重排阶段验证本地链接、历史正文与受保护内容、范围/日期/所有权一致性，并收独立只读复核。实现开始后按各任务跑 targeted test→平台 lint/build→消费者→目标设备→同候选复审；实际任务名在合同和接线后核对，不能照抄不存在的 target 当通过。

停止条件：本周范围同一候选通过并形成可复核交付，或剩余明确阻断需要数据/资源/范围决定。完成计划不等于完成 W9、W8 或发布；不创建/修改旧 OMX 运行态、旧 goal 或历史 P11 机器快照。
