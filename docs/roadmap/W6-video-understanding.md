## 2026-11-17：下一会话继续 W6/F08，质量阻塞未关闭

[W6/F08 下一会话交接](../HANDOFF-W6-F08-NEXT-2026-11-17.md)：Qwen3.5 运行时崩溃已修，但 valid36 仅 `17/36`，相对 Qwen3 `16/36` 只增 `+1`；36/18 SFT 虽使 train `18→34`、valid loss 下降，valid 正确数却 `9→8`，故证据完整而质量门失败，adapter 未保存。Smol 为 `14/36`，冻结双模型路由最好 `19/36`，Qwen2 BF16 资源 no-go，显式 LayerNorm 未改变错误答案。已知本地候选路线基本耗尽；停止在 valid18 调参、重复旧 valid36 和旧 parity 实验。下一步只从收窄产品合同、有独立质量与移动资源依据的新模型、或使用全新独立验证集的训练/蒸馏方案中选择。新候选先过 Mac Metal 独立质量门，再考虑 iPhone 16 Pro Max；W6 保持 `open/not-ready`。
