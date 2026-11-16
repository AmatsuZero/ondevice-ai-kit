# W6/F08 短视频理解下一会话交接 — 2026-11-17

## 当前结论

W6/F08 仍为 `open/not-ready`。当前阻塞已经不是 Metal、iPhone 连接或模型无法运行，而是**现有候选的通用短视频理解质量不足，且产品任务与资源预算尚未冻结**。

本轮实验目标是短视频内容理解：给定视频、问题和候选答案，判断语义、记忆、抽象、物理、反事实和相机运动等内容。它不是单纯的运动物体检测。既有对照已经证明模型输入会受画面影响，部分相机题也会随像素变化作出正确变化，因此不能归结为“视频本来就无法识别”；更准确的结论是当前 2B 级 4-bit 候选在现有通用合同上不够可靠。

## 已完成且不再重复的结论

### Qwen3.5 运行时修复与 valid36

- 首次崩溃来自 pinned Swift `Qwen35` 两处 RoPE 调用漏传 checkpoint 的 `visionEndTokenId=248054`，误用 Qwen3 默认值后触发视频 position 合同的 `preconditionFailure`。
- 补齐两处参数后，唯一 Mac MLX Metal 进程完成 36/36，72 份完整 logits 验收通过。
- Qwen3.5 严格成绩为 `17/36`、invalid `0`；Qwen3 基线为 `16/36`，只增加 `+1`。
- 预注册晋级门为 `>=20/36` 且相对 Qwen3 净增 `>=+4`，因此未进入 iPhone，也未改 App/SDK 默认。

### Qwen3.5 36/18 SFT

- train：`18/36 → 34/36`，mean loss `0.506275 → 0.148356`。
- valid18：`9/18 → 8/18`，mean loss `0.656392 → 0.370421`。
- valid18 有 2 条错→对、3 条对→错，净正确数 `-1`。
- 独立证据校验为 `evidenceVerification=passed`，质量门为 `qualityGate=failed`。
- adapter 未保存；该配方只能证明拟合训练集和降低 completion loss，不能支持通用短视频理解采用。

### 其他候选与处置

- SmolVLM2 2.2B 4-bit valid36：`14/36`、invalid `1`，低于 Qwen3 的 `16/36`。
- Qwen3/Smol 冻结路由最好为 `19/36`、相对 Qwen3 净增 `+3`；oracle 上限 `21/36` 不能作为可部署路由。
- Qwen2-VL 原始 BF16 tensor payload 为 `4.114 GiB`，已超过现有 `3.5 GiB` 实验保护线；旧质量信息也不足以支持移动候选，故 no-go。该保护线不是获批产品预算。
- Qwen3 显式 LayerNorm 只把最终数值差降低约 `9.15%`，答案仍错；停止把跨运行时数值 parity 当作质量主线。
- 已知本地候选路线已基本耗尽；继续扫描旧 prompt、帧数、输出格式、旧 valid36 或同一 valid18 不会提供足够的新信息。

## 设备与资源边界

- 当前设备范围按用户决定固定为已连接的 **iPhone 16 Pro Max**。
- 既有 Qwen3 graph 候选已在单个新进程连续完成 36 题，采样物理内存峰约 `2.95 GiB`，但这只是实验观测。
- 产品可接受的延迟、内存、加载时间、热状态和最低设备范围尚未批准；不得把实验保护线或单机峰值写成产品预算。
- 新候选先过 Mac Metal 的独立质量门，再考虑 iPhone。质量不过门时不做手机资源实验。

## 下一会话只从以下三条边界选择

### 1. 收窄产品视频任务合同

把“通用短视频理解”拆成可验收的产品能力，例如相机运动、物体状态变化、动作是否发生或有限场景问答；冻结输入时长、采样、问题形式、允许输出、质量门和失败行为。已有结果可以作为历史对照，但不能在旧标签上继续拟合规则。

### 2. 引入有独立质量依据且移动资源可行的新模型

下载或运行前先确认原生视频合同、许可、pinned MLX model+processor 执行路径、静态权重规模和移动资源余量。随后冻结**新的独立验证集**和门槛，只做一次 Mac MLX Metal 质量筛选；通过后才准备 iPhone 隔离 App。不要再用旧 valid18 调参，也不要把旧 valid36 当作可反复试错的筛选集。

### 3. 新训练或蒸馏方案

训练集、验证集和停止条件必须在运行前冻结。验证集必须真正独立于 train36、pilot24、valid18 和已反复审读的 valid36；不能在 valid18 上选择超参数、轮数、prompt、路由或 checkpoint。训练正确率和 loss 下降不替代独立验证正确率。

## 明确停止项

- 不在 valid18 上继续调参、刷分、挑 checkpoint 或改停止轮数。
- 不重复旧 valid36 的 prompt、帧数、输出约束、模型路由或 parity 扫描。
- 不继续 Qwen2 BF16 手机路线，除非出现可验证的 streaming/offload 方案和获批资源合同。
- 不把 Mac Metal 成功、iPhone 能运行、loss 下降、数值更接近或单个视觉反事实成功写成产品质量通过。
- 不改正式 App/SDK 默认，不删除模型、App、证据、失败原件或任何现有 dirty/untracked 文件。

## 权威入口

- [W6 短视频理解路线图](roadmap/W6-video-understanding.md)
- [F08 飞鸟专家交接](HANDOFF-F08-BIRDS-EXPERT-2026-11-08.md)
- [Qwen3.5 valid36 与运行时修复](validation/f08-qwen35-valid36-20261115.md)
- [Qwen3.5 36/18 SFT 结果](validation/f08-qwen35-video-sft-train36-valid18-plan-20261115.md)
- [SmolVLM2 valid36](validation/f08-smolvlm2-valid36-20261115.md)
- [双模型路由处置](validation/f08-valid36-ensemble-disposition-20261115.md)
- [Qwen2 BF16 处置](validation/f08-qwen2vl-bf16-valid36-disposition-20261115.md)
- [Qwen3 显式 LayerNorm 诊断](validation/f08-qwen3vl-explicit-layernorm-20261115.md)

## 新会话可直接使用的提示

> 阅读 `.worktrees/foundation/docs/HANDOFF-W6-F08-NEXT-2026-11-17.md`，在 foundation 工作树继续 W6/F08 短视频理解。当前运行时阻塞已关闭，质量仍 open/not-ready：Qwen3.5 valid36 为 17/36，36/18 SFT 的 valid18 从 9/18 降至 8/18；Smol 为 14/36，冻结双模型路由最好 19/36。不要继续在 valid18 调参、重复旧 valid36、扫描旧 prompt/帧数或做 parity 主线。下一步只从收窄产品合同、引入有独立质量与移动资源依据的新模型、或使用全新独立验证集的新训练/蒸馏方案中选择。新候选先过 Mac Metal 独立质量门，再考虑 iPhone 16 Pro Max。保留全部模型、App、证据及 dirty/untracked 文件。

## Wiki 同步

GitHub/GitLab 的 `短视频内容理解` 已追加唯一 marker `w6-f08-next-session-20261117`，完整页面逐字回读一致；错误的 W3 marker 已从两端 `W3语音验收与后续改进` 删除，其他 W3 内容保留。见 [同步收据](validation/w6-f08-next-session-sync-20261117.json)。源码分支不推送。
