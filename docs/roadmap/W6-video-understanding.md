## 2026-11-27 统一 Demo 视频问答验收

W6 已恢复评估。WeMM-Video-2B 在统一 Demo 中按需启用，iPhone 16 Pro Max 的六项页面测试通过。Mac 开放问答 v1.1 中，WeMM 的可回答题得 20/40，证据不足题得 6/8。独立人工复标、正式质量门槛和产品采用仍待确认。[逐题评分](../validation/w6-video-open-qa-v1.1.md) · [短事件采样实验](../validation/w6-video-temporal-coverage-20261127.md)

一次 USB 真机短答的页面进程内存与计时已交付 [W8 资源表](W8-resources.md)。连续答题、长视频和整机耗电仍待真机测量，资源预算待项目确认。

旧 Qwen2-VL 的 24 题试验已于 11 月 11 日结束。Perception Test 训练划分的 24 段视频在同一 iPhone 进程内全部出答，按原规则严格计分 12/24，含 5 份格式违规。终态报告的两次回读摘要均为 `7b0e1458170fe92c5692010cc870fb0598d97b45ecf1e3c2f504469cfec5adf5`。该候选当时的裁定为 `continue-evaluation`。

## 2026-11-17 暂停记录

用户已明确本周不继续 W6 视频理解，状态 **paused/deferred，原质量/资源门未通过**。停止新模型/下载/训练/SFT/parity、新旧验证集与手机实验；保留代码、模型、普通App、原始结果和失败，不因暂停自动删除或替换生产默认。

本周不承诺通用短视频理解；实际候选相关入口与依赖由主代理统一核对，保留图片等已交付能力。先读[本周新交接](../HANDOFF-2026-11-17-weekly-delivery.md)、[执行计划](../superpowers/plans/2026-11-17-weekly-delivery-voice-effects.md)。下方“下一候选/下一会话继续”均为暂停前历史，不再领取。最新4B立即字母约束的12/36不是自然最终答案能力评分，此边界保持。

---

## 2026-11-17：下一会话继续 W6/F08，质量阻塞未关闭

[W6/F08 下一会话交接](../HANDOFF-W6-F08-NEXT-2026-11-17.md)：Qwen3.5 运行时崩溃已修，但 valid36 仅 `17/36`，相对 Qwen3 `16/36` 只增 `+1`；36/18 SFT 虽使 train `18→34`、valid loss 下降，valid 正确数却 `9→8`，故证据完整而质量门失败，adapter 未保存。Smol 为 `14/36`，冻结双模型路由最好 `19/36`，Qwen2 BF16 资源 no-go，显式 LayerNorm 未改变错误答案。已知本地候选路线基本耗尽；停止在 valid18 调参、重复旧 valid36 和旧 parity 实验。下一步只从收窄产品合同、有独立质量与移动资源依据的新模型、或使用全新独立验证集的训练/蒸馏方案中选择。新候选先过 Mac Metal 独立质量门，再考虑 iPhone 16 Pro Max；W6 保持 `open/not-ready`。
