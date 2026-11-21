# 本周候选交付清单（2026-11-17）

2026-11-19 功能收尾：**W9 本周核心功能已验收；正式发布仍 NO-GO。** [双端真机记录](../validation/w9-physical-acceptance-20261118.md)覆盖文件转换、试听/导出回读及用户确认的录音行为。后续只补发布所需的边界场景和证据；独立复审等最终配置、通知、版本及签名候选冻结后统一安排。下方未勾选项继续保留。

2026-11-19 [Android 依赖通知增量](../validation/w9-android-notice-delta-20261119.md#第二份隔离-release-apk五个发布版本的来源关联)：隔离 unsigned Release APK `0ec07f47…` 保留前包 `765148e0…` 的 734 个成员字节，新增 5 份根许可、2 份归属文件和 1 份索引。前序待核 41 项来源材料齐备，新增 5 个选定版本的入包关联；累计 13/49，剩 36 项待关联。第一份增量的 Kotlin Reflect 来源已复核；第二份独立复审返回 429。完整通知适用性与最终签名验收仍开放，发布 **NO-GO**。

2026-11-19 [Android 组合包真机冒烟](../validation/w9-android-combined-smoke-20261119.md)：TECNO 已通过启动、导入合成 WAV 和一次升调。结果归属隔离开发签名副本；此前双端用户试用已验，无需重做。

2026-11-18 [组合实包核验](../validation/w9-combined-packages-20261118.md)：Android unsigned Release APK 已复核，四个可选后端启用、W6 视频理解输入关闭、独立视频超分保留，80 份新增通知与索引已入包。iOS 单图 unsigned Release App 已通过构建和组成核对，清单 `c263a824…`；初始和准备完成的 Host 均关闭 W6 视频输入，保留单图 MLX 与独立视频超分。实际链接 Release KMP，12 项 SwiftPM 锁定和 184 个通知文件字节一致。iOS 当前组合的隔离开发签名副本通过真机启动；Android 当日 ADB 无设备，已由上方 2026-11-19 记录更新。Android 71 项既有通知与冻结源码逐字节一致，四个已关联选定版本的产物仍需完整适用性核对。当前双端摘要已记录；原生独立复审返回 429，无结论。最终签名组合的双端功能、通知适用性及独立复审仍开放；发布 **NO-GO**。

2026-11-18 历史 [CBC 审计与独立预审](../validation/w9-permission-audio-release-audit-20261118.md)已执行：**NO-GO**。最终组合未冻结，实际入包通知未闭合；iOS notice 目前可选，需核发行配置与实包。双端模拟器拒权通过，Android TECNO 真机隔离包拒权、拒权后转换与真实播放焦点抢占通过；iOS 真机独立包已通过系统弹窗拒权及拒权终态；注入回调仍仅是模拟器证据。真实系统中断与同一最终包复审仍不勾选。

2026-11-18 验收增量：用户双端听感与录音停止/转换/重录通过；[同候选 iOS 已通过活动取消及重试，已取得最大输入资源观测](../validation/w9-engineering-acceptance-20261118.md)。W9 SDK 本地评审 ZIP 已查验；不含 Demo App，通知盘点未闭合正式交付门。Android 同候选最大输入资源观测及活动取消、直接重试已通过验证，另有 TECNO 独立包拒权与播放焦点抢占证据。iOS 真实系统中断、预算裁定、最终组合及独立复审仍开放。**release not-ready**。

2026-11-18最新：**physical-file-core-validated / release not-ready**。[真机报告](../validation/w9-physical-acceptance-20261118.md)固定源码9219cfe与双端隔离安装配置/摘要；Android15与iOS27.2.1文件导入、三预设、播放控制、导出回读及失败/离页路径通过。未开麦/听验；完整设备门、资源、最低系统与最终组合/通知/复审不勾选。下方旧包hash及“未安装”是历史阶段，不代替本次隔离包或最终发行包。

最新候选增量：[双端模拟器验收](../validation/w9-simulator-acceptance-20261117.md)已通过真实系统导入/三预设/播放控制/系统保存回读。Android硬链接发布修复后97测试、lint（0错误/9既有警告）、双APK、新隔离AAR与独立消费通过；Debug APK SHA-256 `5b32fee21b23cd2c9d88db700cdff49061df069e7068bc8e3988cc393bfdffa8`，Release unsigned APK `12173c0fe9fc039cf0a23e45155b649174057736014dbc9867b5a3452723e82c`，Release AAR `75b8f2829de7ef448ad8189280326c710ea66f458a5c4b11cd95a7c17e0f8138`。旧R的Android产物为历史快照。当前 **simulator-core-validated / release not-ready**；下面真机/听感/资源/通知/复审门仍待验。

当前状态：**本地工程候选，release not-ready**。实现基线 `abc67bb1df88baf88090ee140325e6585f8b59ce`；本清单不发布版本、不代表原 roadmap 全部通过。目标仍为11月20日候选、11月22日本周范围验收。实际能力与暂停范围见[已知限制](2026-11-weekly-known-limitations.md)。

## 已具备的工程输入

| 项 | 当前证据 | 交付裁定 |
| --- | --- | --- |
| Apple W9 SDK | 独立 `ODAIKitVoiceEffects`、`ODAIKitAppleVoiceEffects` SwiftPM products；[A/B报告](../validation/w9-ab-integration-20261117.md) | 源码接线及本地测试通过；不等于二进制分发已支持 |
| Android W9 SDK | `android/voice-effects` Release AAR；API26/JVM17；本地版本仍 `0.1.0-SNAPSHOT` | 隔离Maven暂存与独立消费按[R报告](../validation/w9-r-delivery-20261117.md)验，不是远端发布 |
| KMP与双端Demo | [C报告](../validation/w9-c-integration-20261117.md)：shared JVM113、Android67、iOS桥接4/WAV3/UI1、双端构建 | 本轮隔离 App 的双端文件主链路和用户听感已验；同一最终组合仍待验 |
| Apple分发脚本 | `scripts/package-apple.sh`、`scripts/generate-distribution-project.rb` 已补W9，12个XCFramework已真实归档 | 双切片独立二进制编译/链接通过；真机运行与最终通知仍open |
| 组合 Demo App | Android 四项开关启用，已做真机冒烟的基线 `a0b7627…`，另有隔离通知增量 `765148e0…` 和 `0ec07f47…`；iOS 启用 Speech/独立视频超分/单图 MLX/OpenCV/W9，Release 文件清单 `c263a824…` | 当前 iOS 隔离副本已验真机启动，Android 隔离副本已验真机升调主链路；最终签名包双端功能、通知适用性和独立复审未关闭 |

## 同一候选接受门

- [x] W9冻结合同与A/B/C源码及本地测试可定位；不增加效果、格式、依赖或模型。
- [x] 当前候选的APK/AAR、隔离Maven产物和iOS自有构建目录完成摘要/成员盘点，见R报告的本地收据。
- [ ] 固定最终打包配置与源码/依赖清单；同一安装候选包含预期功能，明确是否保留MLX/Sherpa/OpenCV等可选模块。
- [x] W9 Apple双切片二进制打包与独立编译/链接、源码SwiftPM消费分别通过；Android隔离Maven→独立APK构建/JVM运行分别通过。物理运行仍在设备门验收。
- [ ] 每个实际携带组件的来源/适用通知→打包路径→最终包成员相符；零notice命中不能解释为无需通知。
- [ ] iOS与Android目标机完成[设备验收步骤](2026-11-w9-device-acceptance.md)，包括文件链路、录音/拒权、取消/离页/前后台、导出回读、清理及原App恢复。
- [ ] 人声可懂度/失真听验与同候选时延/资源原始结果齐备；资源预算有明确裁定，旧ASR或原型采样不代替W9预算。
- [ ] 具备最低系统设备证据，或对本周交付作明确缺测裁定；不得静默抬高SDK下限或写成已通过。
- [ ] 同候选独立最终复审有结论；作者自检、助手候选和历史文档ACCEPT不代替最终复审。
- [ ] 签名/安装包/版本/交付渠道由本轮实际授权决定；未发布前不写已上线，不移动或复用已发布tag。

## 下一步分工

主代理处理设备预约、隔离消费、构建与最终集成；R材料维护实际配置、通知差异和限制，避免重复W3/W6研究。新供应商请求遵守现有额度边界；无额度时可继续本地只读验收，不能据重置时刻自动重试。独立复审受阻仍记 open。Android 通知工作从第二份 APK 的 36 项队列接续，AndroidX 先核历史版本及模块例外；双端已确认的变声功能无需重复试用。
