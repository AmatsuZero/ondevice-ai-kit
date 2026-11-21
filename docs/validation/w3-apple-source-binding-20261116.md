# W3 Apple Speech 有界源绑定候选说明 — 2026-11-16

状态：`accepted-bounded-apple-speech-source-and-historical-build-binding`。本文依据主代理离线核验与 hy3 文稿整理；不代表独立设备验证或发布批准。[绑定收据](w3-apple-source-binding-20261116.json)记录 264 checks passed，其中 177 protected content checks；本轮构建、设备执行、下载、麦克风启动和生产源码修改均为 0。收据标记 currentWholeAppEquivalenceProved、finalProductionCandidateAccepted、R4、R5、P4、W3 均为 false。

## 新增已接受结果

- 当前检视 HEAD88643d2（88643d26a8221684d7af4d7fe0dd949521760e7f）不同于历史 sourceCommit636a6eb（636a6eb8854db5dff74244c42c3ce0e82bd2be9f）。10 个当前 Core/Speech/AppleSpeech production Swift 文件匹配 accepted sustained probe source manifest（sha256:4bcf5c6764ed9ffd9ebf21d6d2898fe2a72a47dd28bf48368519c776c434cc35）与 Nov7 committed source；16 个 selected inputs 全量匹配 Nov7 snapshot 和 committed source。
- 14 个 compiler Swift 文件绑定到冻结 compiled-input entries 与原始 SwiftCompile log。`apple/Package.swift` 是 manifest，不是 SwiftCompile 输入。
- 18 文件历史 App 未变；unsigned-device bundle `io.ondeviceai.combined.demo`、Debug/generic iOS/arm64、minOS 18.0、旧 sourceCommit/revision/config 与 debug dylib SHA 566d52831e574f1775877d17665e7c1385a24d73b3c48bf67be08ecf6130669d 保留。历史整包含无关 MLX/OpenCV/shared 依赖，不是 Apple Speech-only 发布裁定。
- 四份原始中间 SwiftFileList raw 文件当前不保留；保留 hashed inspection、compiled-input records 和 original SwiftCompile logs 仅构成有界历史证据。不重建 raw filelists，不称 freshly checked，不推断缺失原因，不以重建修补观察。

## 陷阱与失败保留

按 docs/agent-pitfalls.md，三项 checker 假设被拒：Package.swift 作为 compiled-input、raw intermediates 仍保留、SwiftCompile 被写成 swift-frontend full commands。实际 Xcode 日志使用 SwiftCompile records；保留被拒脚本与失败记录，证据范围收窄。

## Apple lane matrix

| Lane | 状态 | 边界/下一动作 |
| --- | --- | --- |
| 功能、生命周期、持续观测 | 保留：Apple FILE ASR、explicit locale、取消/重入/后台取消/close-drain；[持续观测](w8-apple-asr-sustained-20261110.md)含 20 cycles、40 measured requests、626.211590 s、898 RSS 样本、sampled max 113082368 B | 不重跑 40-call 批次；probe runtime 与历史 unsigned combined App 分离；本轮未新增生产 App ASR 执行 |
| 选中子系统到历史构建绑定 | 新接受：上述 10/16/14 文件级绑定，仅覆盖所选 Core/Speech/AppleSpeech 与四个 Demo Swift 输入 | 不是 fresh current-HEAD App build，不是 whole-App equivalence，不是 final candidate acceptance |
| R4 文件质量 | open：[质量裁定](w3-quality-disposition-ready-20261110.md)保留 FLEURS 中文 CER 16/92；AMI Apple 2/3 recognized、一次平台 1110；Android 1/3 与两次 no-speech；FLEURS 1906 前缀 152 ms 仅改局部输出、无 crop reference/CER 改善 | 原失败不得改写为通过；代表性真实噪声、更广中英文样本、独立评测集与产品要求仍缺 |
| R5/W8 Apple 预算 | open：路径为系统 Speech recognition，不是实验性 Qwen 两阶段解码；持续观测只覆盖 App PID 采样，不含系统 Speech 服务内存、true peak、GPU、energy、thermal | Apple 预算模板不得写入两阶段 RTF。仅在未来实际可测量时，分别记录 Apple recognition duration/audio duration 与 whole request wall/audio duration，并标注缺测；当前已记录的 recognition duration 仅代表识别段，不是完整 end-to-end。不从旧观察或其他端草案产出数值阈值、不做 retroactive pass；未测量、未批准，不授权新运行 |
| 最终生产候选/P4 | open：需固定源码 revision、构建配置、bundle、App/Framework 摘要和采用边界；[iOS 交接](../HANDOFF-W3-IOS-STATUS-2026-11-16.md)所述 P4 独立发布接受未完成；W3 remains open | 固定 final Apple candidate adoption/identity 与 applicable review inputs 仍待补齐；不为普通本地准备新增 user approval gate |
| 跨平台范围保护 | Android Qwen 全量 65 项仅 24 项、第 25 项触固定内存停止线、英文 4 项、AMI/挑战未执行；Sherpa NDK overlay 79 正文/55 缺正文等旧失败继续单列 | Android AAR/NDK/JNI/DEX 要求不复制到 Apple；不为普通可逆准备新增审批请求；既有已授权 Android 单文件诊断不新增审批门 |

## 向上报告与下一动作

W3 完整范围不变；R4、R5/W8、最终生产候选/P4 仍未关闭。后续交付物为：固定 Apple 最终候选的采用记录、身份与适用复审材料，以及预注册的 Apple 质量验收合同和 Apple 专属资源测量/预算合同。上述本地准备可按既有授权独立推进；本轮未启动新测试批次、未关闭这些验收项，也未扩大设备执行授权。既有 Android 单文件诊断仍按原授权执行。不再做等价静态证明，也不重复历史 40-call 批次。
