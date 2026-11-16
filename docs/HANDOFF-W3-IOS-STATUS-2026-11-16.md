## W3 2026-11-16：当前标点SDK的完整组合App构建通过

[实际App验收](validation/w3-apple-sdk-app-candidate-20261116.md)：当前标点SDK已进入完整Speech/Video/MLX/OpenCV组合App：固定c8038cc的203选定输入、复用已核F02 .6 shared与OpenCV，实际unsigned arm64/minOS18构建76.984s通过；92App文件/30实际SwiftFileList/643唯一Swift输入/74+3通知已核，206原内容保持。首checker仅误拒SwiftPM本地OpenCV artifact，原失败保留、同App检查恢复，无重建。零签名/安装/设备识别/模型下载；当前为本地候选，R4/R5/P1–P4/W3仍open。

## W3 2026-11-16：Apple 标点配置已集成本地 SDK 候选

[集成验收](validation/w3-apple-punctuation-sdk-candidate-20261116.md)：精确应用既有外部候选，Apple FILE 仅增加 guarded addsPunctuation=true；四份AppleSpeech源与实测B臂相同。完整Apple484测试/零跳过失败及iOS15 Release模块构建通过，10真实Swift输入/3对象与3模块已绑定；1条既有Identity警告两处渲染、首Xcode缺scheme失败保留。无新设备识别/发布，shippingApp仍null，R4/R5/P4/W3未关闭。另TECNO注册对象变化后一次只读ADB仍为空，零prepare/解码、合同未消费；不重复恢复。旧准备/批次身份保持，不改hash重放。

## W3 2026-11-16：标点配对真机批次完成

[设备结果](validation/w3-punctuation-paired6-device-20261116.md)：用户明确批准的一次六输入12请求已完成，12非空/12清理/输入后检通过，终态双读同字节；中文CER18/86→16/86、近英WER13/54→10/54，格式编辑7→5及5→2，内容类保持11及8，符合冻结探索条件。首UI授权predicate在零Run/空报告时修正，修正后仅一次Run，无识别重试；45普通App元数据保持、alias卸载、普通App launch accepted。未开麦/下载/AiAi、未改生产代码或重放旧65，未宣布生产采用/P4/R4/R5/W3完成。旧准备授权false为历史快照，本批新授权已消费，不重放。

- [同期标点配对runner准备](validation/w3-punctuation-control-progress-20261116.md)：用户明确一次本地实现例外后，六输入12请求固定AB/BA runner37/506/330检查及unsigned App/driver构建通过，尚未签名/安装/运行，新执行授权未获得。原Apple65不重放，旧单臂65包保留不执行；hy3新429停线、无自动重试。没有独立最终源码/P4或R4/R5/W3通过声明。

- [Apple/Qwen逐项误差分析](validation/w3-paired-error-analysis-20261116.md)：中文45/95和近英36/75为确定编辑路径中的标点格式差异，远英仅11/103，另有68词删除/两typed1110；没有替代评分或新识别。显式addsPunctuation仅为待验证线索，10,879项核验与限定复核不等于R4/P4通过。

- [Apple65设备端文件批次结果](validation/w3-apple65-device-20261116.md)：iPhone17,2单批文件验证已消费，启动授权来源不归因，UI故障与首轮零Run证据保留；r3仅1次Run。无渲染前台、系统Speech真峰值或产品通过证据；限定独立证据复核接受本批记录（非P4），R4/R5和最终生产接受仍open。

# W3 平台状态与下一会话交接 — 2026-11-16

## 当前结论

W3 整包仍为 `open`，但当前缺口不能全部归为 Android：

- **iOS 功能执行链没有活跃硬阻塞**。Apple FILE ASR、显式 locale、既有 iPhone 生命周期及持续观测均已有真机证据；无需等待解锁、签名修复、重新授权或重复采集。
- **iOS 验收仍有余项**。R4 文件质量、R5/W8 生产资源预算，以及固定最终生产候选的采用与独立发布接受尚未关闭。
- **近期 P1–P3 的大量来源、AAR、NDK、JNI、DEX 和通知工作主要属于 Android Sherpa 交付链**，不能反向描述为 iOS 功能阻塞。
- **P4 完整独立发布复审和 W3 整包裁定是跨平台门**。独立 iOS probe/alias 的成功不能代替最终生产 App 的接受记录。

## iOS 已完成并保留的范围

| 范围 | 当前证据 | 边界 |
| --- | --- | --- |
| Apple FILE ASR | iPhone17,2 / iOS 27.2.1 完成 20 fresh cycles、40 次 measured recognition，全部成功 | 不重跑同批；recognition duration 不是完整用户端到端延迟 |
| 持续观测 | 626.211590 s，含 593.928 s paced idle；898 个 measured RSS 样本，采样最大 113082368 B | App PID 采样不含系统 Speech 服务内存，sampled max 不等于 true peak |
| iPhone 生命周期 | 既有取消、重入、后台取消及一次 close/drain 范围证据保留 | 不扩写为所有授权、中断或发布场景均通过 |
| 受控文件路径 | 同一三份公开中文波形已在 Apple 路径完成；三份受控英文噪声文件已运行 | 小样本和受控噪声不等于代表性自然环境质量 |
| 恢复 | probe 构建、签名、安装、终态双读、卸载 alias 和普通 App 恢复已验 | 该 probe 不是固定最终生产候选 |

权威证据：

- [W3 语音路线图](roadmap/W3-speech.md)
- [W3 当前验收范围](validation/w3-closure-scope-20261115.md)
- [Apple FILE ASR 持续观测](validation/w8-apple-asr-sustained-20261110.md)
- [W3 剩余场景矩阵](validation/w3-remaining-acceptance-cases-20261108.md)
- [W3 质量裁定材料](validation/w3-quality-disposition-ready-20261110.md)

## iOS 尚未关闭的项目

### 1. R4 文件质量

- 固定三片 FLEURS 中文：Apple CER `16/92`，只是有界观察，没有获批的产品质量阈值。
- AMI 远场三片：Apple `2/3` 返回识别文本，另一次为平台错误 `1110`；原失败不能改写成通过。
- 代表性真实噪声、更广中文/英文样本和产品裁定仍缺。
- 当前无新的录音、数据采集或重放授权；下一会话先做证据/产品裁定，不自动重跑已消费批次。

### 2. R5/W8 资源预算

Apple 持续观测子项已经验收，但以下仍未测或未批准：

- 系统 Speech 服务内存；
- true peak、GPU、能耗和 thermal；
- 更广设备、负载和持续高负载范围；
- 按 Apple 能力/设备/输入批准的生产数值预算。

2026-11-16 提出的 `RTF <= 1`、App sampled RSS `<= 1 GiB` 是未来 TECNO Qwen 顺序 FILE 批次的待批准草案，**不得直接套用为 Apple 预算**。

### 3. 最终生产候选与发布接受

- 当前 Apple 证据来自独立 probe/alias，不是最终生产 App 候选。
- 下一步需要固定 iOS 生产候选身份，包括源码 revision、构建配置、bundle、App/Framework 摘要和实际采用边界。
- Android Sherpa AAR/NDK 的 P1–P3 细节不应复制为 Apple 系统 Speech 依赖要求；Apple 仍需完成与其实际交付边界相适配的生产候选核验。
- P4 必须基于同一固定候选、材料和未决问题形成独立发布接受记录。

## Android 专属或主要缺口

- Qwen 真机完整 65 项质量/成本批次只完成 24 项，第 25 项触及固定内存停止线；英文仅 4 项，AMI/挑战未执行。
- Sherpa AAR/JVM/DEX/JNI/NDK 的完整输入、native 来源、递归最终组成、通知和实际 APK 交付链仍主要是 Android P1–P3 工作。
- 当前 NDK overlay 为 79 条有正文、55 条仍缺正文；这不是 Apple Speech 功能阻塞。

## 下一会话执行入口

工作目录：`/Users/daubert/Github/ondevice-ai-kit/.worktrees/foundation`

恢复顺序：

1. 先读本文件，再读 `docs/roadmap/W3-speech.md` 顶部和 `docs/validation/w3-closure-scope-20261115.md`。
2. 先形成 **iOS lane 独立关闭清单**：把已完成的功能/生命周期/持续观测与仍缺的 R4、R5、生产候选接受逐项分开。
3. 优先处理无需新设备运行的工作：固定 Apple 生产候选边界、整理现有质量裁定、提出 Apple 专属资源预算选项及缺测处置。
4. 仅当新的固定问题能区分结论且已有授权时才运行真机；不要重跑 Apple 20-cycle/40-call 批次，不重复录音、授权或已验生命周期。
5. Android 继续按 R4 完整真机质量、P1–P3 完整组成/通知链推进；不要把 Android 余项写成 iOS 阻塞。
6. P4 在同一固定生产候选和 P1–P3 适用材料齐备后进行独立发布复审；在此之前 W3 保持 `open`。

## 新会话可直接使用的提示

> 阅读 `.worktrees/foundation/docs/HANDOFF-W3-IOS-STATUS-2026-11-16.md`，在 foundation 工作树继续 W3。iOS 功能链和 Apple FILE ASR 持续观测已完成，不重跑 20 周期/40 请求、既有生命周期、录音或授权。先把 iOS 剩余 R4 质量、R5/W8 Apple 专属预算和固定生产候选/P4 接受拆成可关闭清单；Android Qwen 全量质量及 Sherpa P1–P3 继续单列。保留所有 dirty/untracked、原 App/模型和既有失败证据。

## Wiki 同步

本状态已追加到 GitHub/GitLab 的 `W3语音验收与后续改进` 页面并逐字回读；两端完整页面 SHA-256 相同。见 [同步收据](validation/w3-ios-platform-status-sync-20261116.json)。源码分支未因此推送，W3 仍为 `open`。
