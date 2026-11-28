# F08 Qwen3.5 视频 SFT 12/12 pilot（2026-11-15）

## 结论

Qwen3.5-2B-4bit 的视频 LoRA 计算链路可以稳定拟合训练样本，但冻结的 12 train / 12 held-out pilot **未通过预注册质量门**。首轮标签成块顺序运行后，held-out 从 `9/12` 降至 `8/12`，mean loss 从 `0.425835` 升至 `0.521036`。同一选择的确定性交错顺序控制将 train 提升到 `12/12`，但 held-out 仍为 `9/12`，mean loss 进一步升至 `0.556318`，没有任何 held-out row 从错误变为正确。

因此，上一轮 6+6 pilot 的 `2/6 → 3/6` 不能视为可重复的泛化改善。当前证据支持“模型可以训练和记忆”，不支持“这套小样本 LoRA 配方会改善新视频质量”。本轮不保存 adapter、不进入 valid36 前后对照、不修改 App、SDK 默认模型或项目依赖，W6/F08 继续 `open/not-ready`。

## 冻结选择与预注册门

选择 SHA-256：

```text
5f3d349525efeaea521058e1fda49ee623b255ffb3000a36ec180de4b0022483
```

选择约束：

- 来源为已有 `f08-expanded72`，source selection SHA-256 `31711b0484120c19799819e604a516b06ec19697bdb6e986862b09e449bf35ad`；
- 排除上一轮 pilot12 的全部 12 个 `id` 和 `videoId`，prior selection SHA-256 `468b612f09e02ab72cdcf317101445ad1fbd60338a6a5fad4f13ef79ab148e77`；
- train 12 条、held-out 12 条，共 24 个不同视频；
- 每组的 `counterfactual`、`memory`、`abstraction`、`physics`、`semantics`、`camera_motion` 各 2 条；
- 每组答案 A/B/C 各 4 条；
- 所有 row 的 `cutFrame = -1`；
- 选择只使用固定 seed、元数据和 SHA-256 排序，不读取模型预测或 loss；
- 每个媒体绑定大小和 SHA-256，冻结器连续两次输出字节完全相同。

预注册通过条件：

1. held-out accuracy 不下降；
2. held-out mean loss 下降；
3. 至少两个非 `camera_motion`/`semantics` 类别出现错误变正确；
4. 峰 MLX 不超过 20 GiB；
5. 监督器最低磁盘不低于 30 GiB；
6. adapter 不保存。

前三项是质量门，必须全部通过；后三项是资源和副作用边界。

## 首轮：标签成块顺序

首轮使用冻结选择原顺序，训练答案序列实际为：

```text
AAAABBBBCCCC
```

结果：

| 指标 | 训练前 | 12 步后 |
| --- | ---: | ---: |
| Train accuracy | 11/12 | 10/12 |
| Train mean loss | 0.320435 | 0.217015 |
| Held-out accuracy | 9/12 | 8/12 |
| Held-out mean loss | 0.425835 | 0.521036 |

- held-out 没有新增改对；
- `memory` 有一条从正确变为错误；
- 峰 MLX `12,842,383,257 B`；
- 监督器峰 RSS `4,030,054,400 B`；
- 最低磁盘 `40,557,928,448 B`；
- exit `0`、stderr 为空、adapter 未保存。

该轮质量门失败。选择虽然整体答案均衡，但更新顺序按标签成块，因此保留结果后增加单变量顺序控制，不用该混杂直接裁定 LoRA 配方。

## 单变量控制：答案交错顺序

控制臂保持选择 SHA、权重、MLX runtime、random seed `20261115`、LoRA rank/alpha、learning rate、12 步、评估顺序和预注册门不变，只将训练更新顺序改为固定 seed `f08-qwen35-sft-pilot24-interleaved-v1` 生成的：

```text
ABCABCABCABC
```

训练前 24 条 baseline 的稳定字段按 `id` 与首轮逐项完全一致，排除了输入或初始模型状态变化。

结果：

| 指标 | 训练前 | 12 步后 |
| --- | ---: | ---: |
| Train accuracy | 11/12 | 12/12 |
| Train mean loss | 0.320435 | 0.000613 |
| Held-out accuracy | 9/12 | 9/12 |
| Held-out mean loss | 0.425835 | 0.556318 |

- held-out accuracy 未下降，但没有任何错误变正确；
- held-out mean loss 增加 `0.130483`；
- 原三条错误仍是 `counterfactual`、`abstraction`、`semantics`；其中后两条预测字母发生变化，但仍为合法错误答案；
- 峰 MLX `12,842,399,641 B`；
- 监督器峰 RSS `4,111,007,744 B`；
- 最低磁盘 `40,553,074,688 B`；
- exit `0`、stderr 为空、adapter 未保存。

顺序控制修复了 train 拟合退化，却没有产生 held-out 改善，并使错误样本更高置信。质量门仍失败。

## 启动恢复边界

首轮监督器第一次启动在 1.03 秒内因 `lora_one_step.py` 的传递导入 `completion_tail_parity.py` 未复制而 `ModuleNotFoundError`，stdout 为 0，峰 RSS 约 80 MB，权重未加载。原错误和监督器收据已保留。恢复只复制上一轮已验收的同字节源码，并先执行真实 import smoke；随后进行唯一模型运行。该启动错误不是模型、Metal、数据或质量失败。

## 裁定与下一门

- 关闭“Qwen3.5 视频 LoRA 结构上无法训练”的假设；
- 保持“当前小样本、一遍、`1e-5` 语言侧 LoRA 可改善 held-out 视频质量”未成立；
- 不基于当前 12 条 held-out 调 learning rate、顺序、epoch 或样本，避免把验证集变成调参集；
- 不保存临时 adapter，也不运行 valid36 前后对照；
- 若继续 SFT，应先冻结新的训练设计和独立于当前 official-train pilot 的验证集，并使用更大的训练集、正则化/早停或多 seed 方案；这些方案必须在读取新验证结果前确定。

## 证据

首轮证据：`artifacts/verification/f08-qwen35-video-sft-pilot24-20261115/`

- `pilot24_selection.json`、`experiment-plan.json`、`selection-verification.json`；
- `lora_pilot24_result.json`、progress/stdout/stderr、监督器结果；
- 首次启动失败的 stderr 和 supervisor receipt；
- `verify.py`、`parent-verification.json`、`sha256.txt`。

顺序控制证据：`artifacts/verification/f08-qwen35-video-sft-pilot24-order-control-20261115/`

- `order-control-plan.json`、`comparison-to-blocked-order.json`；
- `lora_pilot24_order_control_result.json`、progress/stdout/stderr、监督器结果；
- 首轮结果和父验收的绑定副本；
- `verify.py`、`parent-verification.json`、`sha256.txt`。

CBC 候选原始记录：`.omx/artifacts/cbc-f08-pilot24-candidate-20261115/`。
