# 本周候选交付清单（2026-11-17）

当前状态：**本地工程候选，release not-ready**。实现基线 `abc67bb1df88baf88090ee140325e6585f8b59ce`；本清单不发布版本、不代表原 roadmap 全部通过。目标仍为11月20日候选、11月22日本周范围验收。实际能力与暂停范围见[已知限制](2026-11-weekly-known-limitations.md)。

## 已具备的工程输入

| 项 | 当前证据 | 交付裁定 |
| --- | --- | --- |
| Apple W9 SDK | 独立 `ODAIKitVoiceEffects`、`ODAIKitAppleVoiceEffects` SwiftPM products；[A/B报告](../validation/w9-ab-integration-20261117.md) | 源码接线及本地测试通过；不等于二进制分发已支持 |
| Android W9 SDK | `android/voice-effects` Release AAR；API26/JVM17；本地版本仍 `0.1.0-SNAPSHOT` | 隔离Maven暂存与独立消费按[R报告](../validation/w9-r-delivery-20261117.md)验，不是远端发布 |
| KMP与双端Demo | [C报告](../validation/w9-c-integration-20261117.md)：shared JVM113、Android67、iOS桥接4/WAV3/UI1、双端构建 | 工程本地通过；物理设备与听感未验 |
| Apple分发脚本 | `scripts/package-apple.sh`、`scripts/generate-distribution-project.rb` 已补W9，12个XCFramework已真实归档 | 双切片独立二进制编译/链接通过；真机运行与最终通知仍open |
| 当前Demo组成 | Android本次默认构建未启用Sherpa/LiteRT/OpenCV可选flag；iOS本次voice opt-in Speech组合 | 不与旧完整组合App混用身份或通知结论；模型/依赖仍以最终配置为准 |

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

主代理处理设备预约、隔离消费、构建与最终集成；R材料维护实际配置、通知差异和限制，避免重复W3/W6研究。新供应商请求遵守现有额度边界；无额度时可继续本地只读验收，不能据重置时刻自动重试。独立复审受阻仍记open。
