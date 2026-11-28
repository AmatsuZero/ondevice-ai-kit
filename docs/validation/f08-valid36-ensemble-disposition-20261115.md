# F08 valid36 双模型互补性与本地候选处置

状态：**离线审计完成；没有非 oracle 规则达到晋级门，不进入双模型 iPhone 资源评估。已知本地 F08 候选路线耗尽，F08/W6 仍为 open/not-ready。**

## 结果

本轮没有运行模型，只读取既有 Qwen3-VL 2B 4-bit 与 SmolVLM2 2.2B 4-bit 的 valid36 首步完整 logits。输入仍是同一冻结 selection，Qwen3 基线 **16/36**，Smol 基线 **14/36**。两者配对为：

- both correct：9
- Smol-only：5
- Qwen3-only：7
- both wrong：15

预先冻结的规则结果：

| 规则 | 严格正确 | 相对 Qwen3 | 晋级 |
| --- | ---: | ---: | --- |
| Qwen3 基线 | 16/36 | 0 | 否 |
| Smol 基线 | 14/36 | -2 | 否 |
| 原始 margin 更高者 | 18/36 | +2 | 否 |
| 归一化 margin 更高者 | **19/36** | **+3** | 否 |
| entropy 更低者 | 15/36 | -1 | 否 |
| 三标签 softmax 等权平均 | 17/36 | +1 | 否 |
| perfect oracle | 21/36 | +5 | 仅理论上限 |

晋级门是至少 **20/36** 且相对 Qwen3 净增至少 **+4**。最好通用规则只有19/36，没有规则通过。

## 为什么不继续双模型手机实验

15题两模型都会错，路由无法修复；剩余12个分歧题中，达到20/36要求至少选对 **11/12**。最好冻结规则选对 **10/12**，perfect oracle才是12/12。继续在这36题上拟合阈值或组合规则会直接使用答案过拟合，计划明确禁止。

即使 oracle 上限达到21/36，双模型权重文件合计也有 **3,240,146,464 B（3.018 GiB）**，还未计算双加载、切换、缓存、延迟和热。由于质量门已失败，本轮不启动资源实验，也不把文件大小冒充真机峰值。

## 本地候选处置

`local-candidate-inventory.json` 汇总了已知本地 F08 候选：Smol500M、Qwen2 4-bit、Smol2.2B 4-bit、Qwen3 4-bit、Qwen2 原始权重以及本次双模型路由。当前没有一个既有本地候选同时具备独立质量晋级证据和移动资源可行性：

- Qwen3 与 Smol2.2B 分别在 valid36 为16/36和14/36。
- Qwen2 原始权重已因移动运行形态和资源下限 no-go。
- 双模型冻结规则仍只有19/36。
- 更小/更旧候选已有更弱的固定集证据。

因此，继续重跑这些权重、调 prompt、resize、帧数、格式或事后路由阈值的信息增益不足。下一实际决策边界是：**收窄产品任务合同，或引入有独立质量依据且资源可控的新模型/训练、蒸馏方案。**

## 验证与边界

证据根：`artifacts/verification/f08-valid36-ensemble-disposition-20261115/`。

- `analysis-plan.json` 在读取规则结果前冻结四种非 oracle 规则、oracle 上限和20/+4门。
- `analysis-result.json` 保存36题三标签分数、路由选择和结果。
- `verify_analysis.py` 独立重读72个首步 logits，验证文件SHA/词表尺寸/输出token映射并重算全部规则。
- `verification.json` 状态 `passed`：Qwen 36与Smol 36身份一致、所有logits SHA通过、分数与门结论精确重算。
- 跨模型 logits 未校准，规则仍是在同一个 valid36 上评估；19/36不能当held-out路由泛化成绩。失败足以停止本分支，成功则本来也仍需新的独立集。
- 无模型推理、下载、iPhone操作、生产改动、PyTorch、CPU模型或Ollama推理。
