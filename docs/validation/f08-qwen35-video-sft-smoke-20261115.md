# F08 Qwen3.5 视频 SFT smoke 与微型 pilot（2026-11-15）

## 结论

`mlx-community/Qwen3.5-2B-4bit` 的单样本视频 SFT 计算链路在 Mac Metal 上可执行：显式视频占位、8 帧视觉特征、completion-only 标签、尾部词表投影、LoRA backward 和一次 AdamW 更新都已完成。4 步单样本过拟合与一个冻结的 6 train / 6 held-out 微型 pilot 也已完成。该结果否定了“当前模型结构上无法做视频 SFT”的假设。

微型 pilot 的 held-out 从 `2/6` 增至 `3/6`，mean loss 从 `0.551249` 降至 `0.319344`，属于积极但极弱的早期信号。held-out 仍来自官方 train split，样本量只有 6，不能写成 `valid36 = 17/36` 已改善。没有保存 adapter，没有修改 App、SDK 默认模型或项目依赖，W6/F08 继续 `open/not-ready`。

## 固定环境

- 模型：`mlx-community/Qwen3.5-2B-4bit`，本地固定 revision `674aaa7240b91e8012fcad5d791b7dfe5ba90207`；
- MLX：`0.32.4`，`mlx-metal 0.32.4`；
- MLX-VLM：固定提交 `e267ccf740b8b9dfc4529665b5efd8b9e1eff187`，源码归档 SHA-256 `b01c6632d8b01554acd12399a64654710d71e08da6d2103d341370e141fd8212`；
- Python：`3.12.14`；
- 设备：`Device(gpu, 0)`，Apple M5 Pro Metal；
- 隔离环境中不存在 `torch` / `torchvision`；
- Ollama 服务未承载任何已加载模型；
- 30 GiB 可用磁盘停止线，全部训练监督器观测最低 `40,574,844,928 B`，未触发停止。

## 样本和数据合同

样本为 `train_videos.zip` 的 `videos/video_9431.mp4`：

- SHA-256：`c6ee88c3dc3b297e98bb784fa21e38e5c9e0127161edaaad2c25cbfb4f98aef2`；
- 1920×1080、30 fps、35 秒；
- 固定采样 8 帧；
- 问题：`Is the camera moving or static?`；
- 正确答案：`C. moving`。

正例 prompt 显式包含：

```text
<|vision_start|><|video_pad|><|vision_end|>
```

处理结果：

- `video_grid_thw = [[4, 68, 120]]`；
- `pixel_values_videos.shape = [32640, 1536]`；
- merge 后视觉特征数 `8160`；
- prompt 中 video token 数 `8160`，与特征精确匹配；
- 完整输入长度 `8246`；
- completion 为 `C<|im_end|>\n`，共 3 token；
- completion mask 只覆盖 assistant 尾部，不覆盖视频 token 或问题文本；
- 无截断。

负对照使用相同视频但不写视频占位：processor 仍产生视频特征，prompt 中 video token 为 `0`。该行未执行 forward。由此确认训练 row 必须显式插入视频 placeholder，仅提供 `video` 字段不够。

## Metal forward

一次完整 forward：

- logits：`[1, 8246, 248320]`，`bfloat16`；
- 全部有限；
- 用时约 `6.014 s`；
- MLX 峰值 `12,161,695,976 B`。

这一步没有 backward、optimizer 或 adapter。

## completion-only 投影

默认 SFT 路径会先生成全序列 logits，并在 `forward_vlm_logits` 中整体转为 float32。对本样本，真正受监督的目标只位于最后 3 个位置：`[8242, 8243, 8244]`。

使用 Qwen3.5 已有的 `logits_to_keep=3` 后：

- 完整 BF16 logits 理论大小：`4,094,796,800 B`；
- 默认整体 FP32 转换理论大小：`8,189,593,600 B`；
- 3 个尾部 BF16 logits：`1,489,920 B`；
- 词表投影张量缩小约 `2748.33×`。

同一样本分别运行两条路径：

1. 模型内置 `logits_to_keep=3`；
2. `return_hidden=True, skip_logits=True`，选择最后 3 个 hidden 后调用同一个 `lm_head`。

结果逐元素完全一致：

- logits SHA-256 相同：`101da45e23d25819ac296200fa463090216a318c57d566ccebe8fb43c45efe03`（float32 无损快照）；
- `max_abs_logit_diff = 0`；
- loss 均为 `0.21286006271839142`；
- `loss_abs_diff = 0`；
- argmax 均为 `[34, 248046, 198]`。

首轮导出曾因 NumPy 无法直接接收 MLX `bfloat16` PEP 3118 buffer 而失败。失败证据保留；恢复仅把已计算的 BF16 值无损转换为 float32 快照，模型计算和精确比较仍在 MLX 内完成。

## LoRA backward 和一步更新

隔离进程采用：

- LoRA rank `8`、alpha `16`、dropout `0`；
- 默认发现的语言模型 Linear 模块 `186` 个；
- vision tower 冻结；
- trainable 参数 `8,409,600`，约 `33.64 MB`；
- gradient checkpointing 开启；
- MLX memory limit `20 GiB`，cache limit `512 MiB`；
- AdamW，learning rate `1e-5`，weight decay `0`。

一次 backward：

- 用时 `32.748 s`；
- loss `0.2167046219110489`；
- 监督 token 数 `3`；
- 梯度全部有限；
- 非零梯度元素 `4,575,632 / 8,409,600`；
- 梯度 L2 norm `14.362494468688965`；
- MLX 峰值 `11,120,304,267 B`，低于 20 GiB 限制。

一次 optimizer step 后，探针参数 `language_model.model.layers.0.linear_attn.in_proj_qkv.lora_b`：

- SHA-256 从 `3381de4c...` 变为 `3f71247c...`；
- 改变元素 `49,040`；
- 最大绝对变化 `3.162269058520906e-05`。

进程 exit `0`，stderr 为空。adapter 未保存，进程退出后 Metal 内存释放。

## 4 步单样本过拟合检查

保持相同模型、样本、LoRA 配置、`1e-5` learning rate、checkpointing 和 completion-tail loss，重新从基础权重启动独立 4 步训练。每步 loss：

1. `0.2167046219110489`；
2. `0.16425873339176178`；
3. `0.019871508702635765`；
4. `0.0011091551277786493`。

四步均为有限、非零梯度，loss 单调下降。第四次更新后的独立评估 loss 为 `0.0001793862320482731`，相对第一步下降 `99.9172%`，预注册的单样本过拟合门通过。总训练与最终评估约 `117.95 s`，监督器总时长 `121.22 s`；峰 MLX `11,187,581,067 B`，最低可用磁盘 `40,624,431,104 B`，未触发资源停止线。

这是刻意检查模型能否记住一个训练样本。它证明 LoRA 更新会沿目标方向降低 loss，但同样不能说明新视频或 held-out 问题会改善。

## 6 train / 6 held-out 微型 pilot

从已有 `f08-expanded72` 的 Perception Test 官方 train subset 中，以不读取模型结果的固定规则选择 12 个不同视频：

- train 6 条、held-out 6 条；
- 每组各覆盖 `counterfactual`、`memory`、`abstraction`、`physics`、`semantics`、`camera_motion` 六类；
- 每组答案均衡为 A/B/C 各 2 条；
- 每个视频只取 1 个问题；
- selection SHA-256：`468b612f09e02ab72cdcf317101445ad1fbd60338a6a5fad4f13ef79ab148e77`。

在独立重新加载的基础权重上，使用相同 LoRA 配置依次训练 6 条 train row，各 1 步。结果：

| 指标 | 训练前 | 6 步后 |
| --- | ---: | ---: |
| Train accuracy | 4/6 | 6/6 |
| Train mean loss | 0.438405 | 0.109160 |
| Held-out accuracy | 2/6 | 3/6 |
| Held-out mean loss | 0.551249 | 0.319344 |

held-out 新增答对的唯一类别是 `semantics`；`memory`、`abstraction`、`physics` 仍然答错。训练 6 步用时 `117.83 s`，监督器总时长 `227.87 s`；峰 MLX `11,061,767,389 B`，监督器峰 RSS `4,191,027,200 B`，最低可用磁盘 `40,574,844,928 B`。进程 exit `0`、stderr 为空，adapter 未保存。

这组结果说明小规模 LoRA 更新后，未参与本轮训练的 6 条 row 没有整体退化，并出现 `+1` 条改对与 mean loss 下降。但这些 row 来自官方 train split，且单条变化足以让准确率移动 16.7 个百分点，因此只能记录为 `accuracy_improved` 的早期信号，不能代表 official validation、valid36、产品质量或移动端验收。

## 裁定

当前阻塞不是“Qwen3.5 无法接受视频 SFT”、Metal 无法训练 LoRA，或 LoRA loss 完全不响应更新。数据合同、前向、反向、参数更新和单样本记忆均成立。

后续[12/12 pilot](f08-qwen35-video-sft-pilot24-20261115.md)已完成：标签成块首轮 held-out `9/12 → 8/12`；只改为交错顺序的控制臂 held-out 保持 `9/12`，但 mean loss 从 `0.425835` 升至 `0.556318`，零新增改对。该结果使本报告中的 6 条 held-out `2/6 → 3/6` 降级为不可重复的小样本波动。当前配方不保存 adapter、不进入 valid36 前后对照；若继续 SFT，须先冻结更大训练设计和独立验证集，不能在当前 held-out 上调参。

## 证据

证据根：`artifacts/verification/f08-qwen35-video-sft-smoke-20261115/`

- 初始数据合同与完整 forward：`result.json`、`verification.json`、`progress.jsonl`、`smoke.py`；
- 尾部投影 parity：`completion_tail_parity_result.json`、`completion_tail_parity_progress.jsonl`、`completion_tail_parity.py`；
- 保留失败：`completion_tail_parity_failure1.json`、`completion_tail_parity_progress1.jsonl`；
- LoRA 单步：`lora_one_step_result.json`、`lora_one_step_progress.jsonl`、`lora_one_step_stdout.jsonl`、`lora_one_step.py`；
- 监督器：`lora_supervisor_result.json`、`run_lora_supervised.py`；
- 4 步过拟合：`lora_overfit4_result.json`、`lora_overfit4_progress.jsonl`、`lora_overfit4_stdout.jsonl`、`lora_overfit4.py`；
- 过拟合监督器：`lora_overfit4_supervisor_result.json`、`run_overfit4_supervised.py`；
- 6+6 pilot 固定选择：`pilot12_selection.json`、`freeze_pilot12.py`；
- 6+6 pilot：`lora_pilot12_result.json`、`lora_pilot12_progress.jsonl`、`lora_pilot12_stdout.jsonl`、`lora_pilot12.py`；
- pilot 监督器：`lora_pilot12_supervisor_result.json`、`lora_pilot12_stderr.txt`、`run_pilot12_supervised.py`；
- 环境/来源：`environment-packages.json`、`receipt.json`、`source-provenance.json`、`source-archive.sha256`；
- 固定源码摘录：`mlx-vlm-cross-entropy-runtime.py`、`mlx-vlm-models-base.py`、`mlx-vlm-qwen35-language.py`；
- 独立离线验收：`verify.py`、`parent-verification.json`、`sha256.txt`。
