## 2026-11-27 W6 视频问答页面资源

W8 复用 W6 在 iPhone 16 Pro Max（iOS 27.2.1，USB）上的 XCTest 结果。W6 Fixtures 已导入 WeMM-Video-2B，先用红蓝片预热一次，再用蓝红片正式测一次。以下四项来自同一次点击「生成」至页面出现回答的窗口，计时包含 UI 自动化等待。[原始指标摘录](../validation/w6-wemm-iphone-short-answer-memory-20261127.json) · [夹具与复测步骤](../../demo/iosApp/UITests/Fixtures/WeMM/README.md#测量短回答期间的-app-进程内存)

| 短答窗口 | XCTest 指标 | 1 个样本 |
| --- | --- | ---: |
| 进程物理内存峰值 | `physical_peak` | 394,973.304 kB |
| 进程物理内存绝对值 | `physical_absolute` | 221,890.808 kB |
| 进程物理内存变化 | `physical` | +36,077.568 kB |
| 页面出答时间 | `Clock.time.monotonic` | 4.447155897 s |

本次窗口仅覆盖预热后的第二次出答。App 全程峰值、页面 RSS、连续六次短答、长视频、持续热状态与电池供电下的整机耗电仍待测量，正式预算待项目确认。

## 2026-11-17 当周候选资源与交付范围

W3/W6后续研发暂停、W7生产校准/PAD阶段外延期，新增[W9变声](W9-voice-effects.md)。W8状态仍 **open / active-planned**：按[本周范围](../superpowers/specs/2026-11-17-weekly-delivery-voice-effects-design.md)验证实际交付候选的稳定性、资源/预算或明确缺测裁定、通知/消费和最终复审；不等待W3/W6模型改进，也不替它们另跑实验。

历史完整矩阵与未通过事实保留；不能以范围调整把采样RSS/旧包/无效能耗值改成新候选预算通过。复用已验TTS/Sherpa等证据只在来源/适用性绑定后成立，不重复同批凑表。W9新增路径须先冻结可测预算/设备范围和停止条件，再验真实输出、取消/关闭/持续使用与资源恢复。

发布材料R可与W9并行，主代理统一候选/构建/设备时段；实际包仍含的依赖须必要通知和消费验证，不因功能暂停自动豁免。具体[并行计划](../superpowers/plans/2026-11-17-weekly-delivery-voice-effects.md)、[当前状态](../validation/weekly-delivery-status-20261117.md)。完整历史矩阵继续保留在本地工作区，尚未纳入本次提交；不作整包通过声明。
