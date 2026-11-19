# 本周阶段交付交接（2026-11-17）

2026-11-19 功能收尾：**W9 本周核心功能已验收，可内部演示和交接。** [W9 roadmap](roadmap/W9-voice-effects.md)与[双端真机记录](validation/w9-physical-acceptance-20261118.md)保留范围与证据。已验项目不再重复跑；下方音频中断、通知、预算及同一最终包复审属于[正式发布待办](release/2026-11-weekly-candidate-checklist.md)。停止对未冻结的临时包逐轮复审，待最终候选冻结后统一复审。W3/W6 继续暂停。

[当前接续：拒权、音频中断与打包复审](validation/w9-permission-audio-release-audit-20261118.md)。双端模拟器拒权后仍可转换；Android TECNO 真机已通过隔离包拒权、拒权后转换及真实播放焦点抢占，iOS 真机隔离包系统弹窗拒权已通过，真实系统中断待验。CBC `hy4flash` 审计已完成，独立预审 NO-GO；接下来补 iOS 真实系统中断证据与冻结最终组合、实包通知，最后按候选摘要复审。W3/W6 保持暂停。

2026-11-19 发布补项：[Android 第二份通知增量](validation/w9-android-notice-delta-20261119.md#第二份隔离-release-apk五个发布版本的来源关联)已构建为隔离 unsigned Release APK `0ec07f47…`。前包 `765148e0…` 的 734 个成员字节不变，仅新增 5 份根许可、2 份归属文件和 1 份索引。前序待核 41 项发布材料均已取得，新增 5 个选定版本的入包关联，累计 13/49；剩 36 项（32 个 AndroidX、2 个 Firebase、`listenablefuture`、`javax.inject`）。第一份增量的 Kotlin Reflect 正式版来源已复核关闭；第二份增量的独立复审返回 429，仍开放。iOS 组合未变，实际版本字段仍缺。原设备记录按原摘要保留，最终发布仍 **NO-GO**。

## 双端组合包接续（2026-11-18 至 2026-11-19）

用户指定的一位原生助手已交付 iOS 单图补丁，主代理隔离构建和核验后已集成到 foundation。初始和准备完成后的 Host 都关闭 W6 视频输入；单图 MLX、独立视频超分、Speech、OpenCV 和 W9 保留。生成候选要求有效通知输入。21 项生成器回归通过，完整 unsigned Release App 清单 SHA-256 `c263a82418116e8342529de40ca9325a539937ac9831f8d1eecdc21afa750fab`，Release KMP 1,091 对象、12 项 SwiftPM pin、184 份通知字节均已复核。普通工程未改。详见[组合实包核验](validation/w9-combined-packages-20261118.md)；下节带 W6 入口的旧 iOS 包是历史基线。

当前组合的摘要及验收证据已写入 `candidate-pair-acceptance/paired-candidate-index.json`（路径见组合实包报告）。当前 iOS 单图组合的隔离开发签名副本已在 iPhone 16 Pro Max 真机安装并启动，6 秒后进程仍在，原组合 App 保留；未做本组合的功能流或真实音频中断。Android ADB 在 2026-11-18 曾无设备；2026-11-19 TECNO 重连后，[隔离组合副本](validation/w9-android-combined-smoke-20261119.md)通过启动、导入合成 WAV 与一次升调，普通 Demo 及上轮验收包均保留。APK 71 项既有通知与冻结源码一致，其中 63 项能复核索引原件。54 条未匹配解析记录对应 51 个产物，49 个有 DEX 类证据，4 个已关联选定版本的包内通知；通知适用性仍开放。原生独立复审 429，无意见。

下一步按第二份 APK 的 `review-input.json` 补齐 AndroidX 历史来源和模块例外及另外 4 个依赖的关联，继续核对嵌套通知。双端已通过的变声功能无需重复验收；设备补项限于最终签名组合的冒烟、iOS 真实系统播放中断及尚未覆盖的双端录音中断/Speech/TTS 争用。资源预算仍为 `DRAFT/NOT APPROVED`，最低系统缺测、通知适用性、版本/渠道/签名与独立复审未闭合。发布仍 **NO-GO**，W3/W6 保持暂停。

## 历史组合包基线（2026-11-18）

[双端实包核验](validation/w9-combined-packages-20261118.md)已补：Android 四个可选后端已启用，W6 入口按 LiteRT LM 单图配置关闭，独立视频超分保留；80 份通知和索引已入包，原有 644 个成员内容不变。iOS 第二份 App 确认实际链接 Release KMP（1,091 个对象）、12 项 SwiftPM pin 一致，184 个通知相关文件与原件一致；仍暴露 W6 入口，只作打包基线。两个 SHA-256 及依赖、版本、资源和通知缺项见上述报告。**未冻结最终双端候选，发布仍 NO-GO。**

下一步先补 iOS 单图 Host 与必填通知输入，两项本地合同回归已真实失败。已准备 `cbc-ios-image-only/prompt.txt`；CBC `hy3` 返回 429、无补丁，服务端提示 2026-11-19 14:03:12（UTC+8）重置，补丁路线等待用户指定可用实现 Agent。最终通知适用性、真实系统中断/音频争用、预算/最低系统、签名/渠道和同摘要独立复审仍开放。用户已确认的两台听感、自动停止、停录后转换和重新录音均继续有效。

## 工程验收接续（2026-11-18）

[本轮报告](validation/w9-engineering-acceptance-20261118.md)：同一隔离 iOS App 真机 48 kHz / 30 秒升调资源观测、活动取消无输出及立即重新转换通过；Apple SDK 25 项回归通过。本地 W9 SDK 评审 ZIP 已生成并逐成员回读。首轮取消断言失败及遗留文件证据保留。TECNO 已补齐同候选 48 kHz / 30 秒资源观测（热身 1 次、正式 3 次），活动取消无输出、同页直接重试和本轮夹具清理均通过；另见顶部 Android 拒权/焦点抢占增量。iOS 真实系统中断、预算裁定、最终组合通知与独立复审仍开放。**release not-ready**。下方两台用户确认已生效，无须重复试用。

## 用户试用效果已确认（2026-11-18）

用户已确认Android/iOS两台均试用效果OK，并明确两台**录音约30秒自动停止、停止后转换、再次录音均正常**；四项记为用户手动验收通过，见[真机报告](validation/w9-physical-acceptance-20261118.md)。不再要求重复这些步骤。下一步补拒权/活动转换取消/系统音频中断、同候选资源预算与最终组合通知/独立复审，release仍not-ready。下方“无人声听验/待用户试用/30秒停止待验”等是此前历史状态，不覆盖本次手动确认；未取得精确录音帧数或逐预设评分。

## 最新接续：双端真机文件核心链路通过（2026-11-18）

当前 **physical-file-core-validated / release not-ready**。在保留普通 App 的隔离 **W9 验收 1118** 上，TECNO Android15 与 iPhone16ProMax/iOS27.2.1 已运行实际系统导入、三预设转换、播放控制、系统保存与回读；坏/超限拒绝、picker取消、离页再进入和所属音频清理通过。Android播放中后台返回4.909s，停止播放且结果保留；iOS后台返回只验证页面可用。见[真机报告](validation/w9-physical-acceptance-20261118.md)：iOS测试1项0失败，外层exit0/709.560s，另含设备诊断附件收集600秒超时告警，结果包已落盘。

源码基线9219cfe；本轮无生产代码改动、无开麦、无人工听验。录音/拒权/30秒自动停止、活动转换取消、系统音频中断/竞争、资源预算、最低系统及最终组合通知/独立复审仍open。两端验收App已安装，下一步由用户明确点击录音补齐人声验收；不重放已完成文件流程或W3/W6实验。以下模拟器/C/R内容为历史快照。

## 最新接续：双端模拟器核心链路通过

用户离机期间已执行真实系统文件导入→三预设转换→播放控制→系统保存→导出回读；无麦克风采集。Android 实际 App 的硬链接发布被 SELinux 拒绝，已改为独占目录预留＋目录内原子移动，新增回归 RED→GREEN；SDK30＋Demo67共97测试、lint（0错误/9既有警告）、双APK构建通过。修复后重新隔离暂存 AAR，独立APK构建及AAR/JVM三预设消费通过，旧R工件保持。见[模拟器验收](validation/w9-simulator-acceptance-20261117.md)。

当前 **simulator-core-validated / release not-ready**。双端坏/超限输入、picker取消与离页恢复有证据；活动处理取消未成功触发，iOS先前测试进程结束留下的合成文件不冒称已清空。下一步是真机录音/拒权/30秒停止、听感、活动取消/系统音频中断与同候选资源，以及最终组合通知和独立复审。下方C/R段为历史阶段快照，不据旧“ADB为空/未安装”回退本轮模拟器增量。

## 最新接续：A/B/C 已集成，本地验证通过，下一步设备验收与 R

用户已明确要求直接执行 C。当前 foundation 分支 `feat/w9-ab-integration-20261117` 在 A/B 基线 `a017cb7` 上完成 KMP bridge、共享 Demo 和双端录音/导入/试听/导出 Host。C 由主代理直接实现；这是本轮用户对旧候选分工的覆盖，没有新 helper 调用或额度重试。见 [C 集成报告](validation/w9-c-integration-20261117.md)。Android 67 测试、lint 与 Debug/Release APK；共享层 JVM 113 测试；iOS 真实桥接 4 测试、WAV 3 测试、页面 UI 冒烟和 unsigned iPhoneOS App 构建通过。A/B 原 [集成报告](validation/w9-ab-integration-20261117.md) 保留。

**当前 engineering-local-validated / release not-ready。** 下一步只推进同一候选的真机核心流程/人声听感/资源测量，以及 R 的实际交付清单、通知差异、独立消费者和最终复审。未录音、未安装物理设备、未签名/上传/发布，不将模拟器或 JVM 验证写成设备接受。旧 C Prompt 仅作任务合同参考，不再派发同一实现。

R已有本轮本地增量：Apple分发脚本补W9，隔离完整12个XCFramework归档与双切片独立链接通过；Android隔离Maven→独立APK及AAR/JVM公开API运行、Apple独立SwiftPM三预设运行通过。26生成器回归通过，见[R报告](validation/w9-r-delivery-20261117.md)。[交付清单](release/2026-11-weekly-candidate-checklist.md)、[限制](release/2026-11-weekly-known-limitations.md)、[设备步骤](release/2026-11-w9-device-acceptance.md)已形成。实际最终组合通知/真机/资源/独立复审未关闭；一次ADB只读清单为空，没有恢复循环或安装。

当前主线：**暂停 W3/W6 后续研发，新增 W9 离线效果变声，11/20 候选、11/22 本周范围验收目标。** 这是用户本轮明确调整，不按旧“先 W3→W6→W8→W7、单包串行”继续。

## 接手顺序

1. 读[本周范围](superpowers/specs/2026-11-17-weekly-delivery-voice-effects-design.md)、[执行计划](superpowers/plans/2026-11-17-weekly-delivery-voice-effects.md)、[W9](roadmap/W9-voice-effects.md)与[任务状态](validation/weekly-delivery-status-20261117.md)。
2. 读根 `AGENTS.md` 与 `docs/agent-pitfalls.md`，核 foundation git 状态、候选实际终态和活跃写入；保留所有 dirty/untracked/普通 App/模型。
3. W9-0 冻结合同与双端可行性后，A Apple / B Android / C KMP-Demo 并行；R 发布材料先行。只读 native reviewer 至多一位，外部提供实现候选，主代理集成/验证。
4. 按真实派发收据继续同一任务；提示词已准备不算在运行，候选交付不算验收。CBC 已知 429 不自动重试/换模/付费，旧单次付费授权不续用。

## 暂停与延期

- W3 暂停识别质量提升、Qwen 替换、内存/ACK 专属实验。保留 Whisper 基础功能与 iOS 语音能力，真实质量失败与资源余项不改绿。最新 ACK 有本地修复接受增量也不构成 OEM/完整 W3 通过。
- W6 暂停视频理解模型评估、训练、parity、旧/新验证集和手机实验；不删除模型/证据、不自动改默认。实际本周入口不承诺未验通用视频理解。
- W7 多人生产校准、数值协议与 PAD 阶段外延期；既有未校准拒绝和数据保护继续。原目标仍未通过。
- W8 继续本次交付候选必要的稳定性/资源/通知/消费/复审。历史完整矩阵缺口另记，不能把采样或旧包指标当本次预算通过。
- 实际包仍包含的 Sherpa/MLX/OpenCV/KMP 等依赖，必要通知与消费链不能因暂停功能而豁免。

## 下一可领取任务

- DEVICE：同一候选先在 iOS/Android 目标机验文件导入→三预设转换→原声/结果试听→导出回读，再由用户明确点击测试录音/拒权/30秒停止、取消/离页/前后台与音频恢复。使用新的合成或授权音频，不重放 W3/W6 实验。
- R：清单/限制/通知差异和有界双端独立消费已验；下一步固定最终功能组合并补齐实包通知与完整消费接受。可与设备验收分工并行，所有权沿用 [Agent 任务入口](agent-tasks/weekly-20261117/README.md)。提示准备不等于已启动；不自动调用已停用供应商。
- Q：独立代码/证据复核仍缺；原 429 保留并停线，无本轮 APPROVE。

## 本轮完成边界

W9-0 与 A/B/C 工程实现及有界本地验证完成；物理设备、人声听感、同候选预算、完整组成/通知/消费者和独立最终接受尚缺，本周范围交付仍 not-ready。11/20 候选与 11/22 验收是目标，不是保证。旧 P11 JSON、benchmark manifest、普通 App/模型及所有无关 dirty/untracked 保留。只精确本地提交 C 与本轮文档，不 push、上传或打 tag。
