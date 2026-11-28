# F08 Qwen3.5 视频 SFT 36 train / valid18 冻结协议与结果（2026-11-16）

## 当前结论

冻结协议已经按唯一一次正式运行完成。runner、监督器和独立 verifier 均已落地，MLX Metal 进程正常退出；**证据验证通过，但预注册质量门失败**。

证据根：`artifacts/verification/f08-qwen35-video-sft-train36-valid18-20261115/`。selection SHA-256 为 `aa8294ef265aaaede1cd4a3b74e6274b92d52956f703a12ea96ea89c7b816039`。

核心结果：

- train：`18/36 → 34/36`，mean loss `0.506275 → 0.148356`；
- valid18：`9/18 → 8/18`，mean loss `0.656392 → 0.370421`；
- valid 错→对 2 条，均来自允许计数的 `counterfactual` / `abstraction`；
- valid 对→错 3 条，超过预注册上限 1；
- valid 净正确数变化为 `-1`，低于预注册要求 `+2`；
- 峰 MLX `11,174,146,446 B`，最低空闲磁盘 `38,859,603,968 B`；
- supervisor exit `0`、stderr `0 B`，adapter 未保存。

因此该配方证明 36 个训练样本可以明显拟合，也能降低 valid18 completion loss，但没有提高独立 valid18 的严格正确率，反而从 9 降到 8。不能保存 adapter、进入 iPhone、修改 App/SDK 默认或宣称 W6 完成；按冻结协议不在 valid18 上调整 learning rate、epoch、顺序、prompt 或门槛后重跑。

## 冻结数据

- train 来源：`f08-expanded72` 的 72 条官方 train 选择；
- 排除旧 pilot12 的 12 个视频和 pilot24 的 24 个视频；
- 剩余 36 条、36 个不同视频，六类各 6，A/B/C 各 12；
- valid 来源：既有完整 `f08-valid18`，18 条、18 个不同视频，六类各 3，A/B/C 各 6；
- train 与 valid 视频重叠为 0；
- train 媒体 559,561,292 B，valid 媒体 192,862,681 B；
- valid 18/18 MP4 已逐文件 SHA-256 并通过 `ffprobe` 容器/时长读取，不需要下载完整约 70 GB validation 包；
- 选择不读取模型输出。

首冻结算法错误地从“类别边际均衡、答案边际均衡”推断类别×答案交叉桶也均衡，在 0 条桶上触发 `IndexError`。该失败发生在选择写入和模型启动前，已保留。修正后分别按固定 `SHA256(orderSeed|id)` 排序 A/B/C 三个 12 条桶，再按 `ABC` 交错 12 轮；不使用类别交叉分层或模型结果。

## 固定训练合同

- 模型：`Qwen3.5-2B-4bit`，revision `674aaa7240b91e8012fcad5d791b7dfe5ba90207`；
- 仅 MLX Metal，不允许 PyTorch、CPU 模型或 Ollama 推理；
- 8 帧、completion-only；
- LoRA rank 8、alpha 16、dropout 0；
- 默认语言侧 186 个 LoRA targets，vision tower 冻结；
- gradient checkpointing；
- AdamW，learning rate `1e-5`，weight decay 0；
- seed `20261115`，单 epoch、36 次更新；
- 顺序固定为 `ABC` 重复 12 轮；
- 同一进程依次执行 baseline train36、baseline valid18、train36 一次、post train36、post valid18；
- valid18 结果不得用于修改本轮 learning rate、epoch、顺序、prompt、rubric 或门槛；
- 不保存 adapter。

## 预注册门与实际判定

| 条件 | 要求 | 实际 | 判定 |
| --- | --- | --- | --- |
| post train | 至少 34/36 | 34/36 | 通过 |
| train mean loss | 下降 | 0.506275→0.148356 | 通过 |
| valid 正确数 | 至少 +2 | 9→8，delta -1 | **失败** |
| valid mean loss | 下降 | 0.656392→0.370421 | 通过 |
| valid 对→错 | 最多 1 条 | 3 条 | **失败** |
| 非 camera/semantics 错→对 | 至少 2 条 | 2 条 | 通过 |
| 峰 MLX | 不超过 20 GiB | 11,174,146,446 B | 通过 |
| 最低磁盘 | 不低于 30 GiB | 38,859,603,968 B | 通过 |
| adapter | 不保存 | 未保存 | 通过 |

完整质量门为 **failed**。loss 下降和两条有效错转对不能覆盖净正确数下降及三条回归。

## runner 与验证

正式保留：

- `run_train36_valid18.py`：冻结输入校验、baseline/train/post、逐行 telemetry 与结果；
- `run_train36_valid18_supervised.py`：磁盘停止线、RSS、stdout/stderr 和退出收据；
- `verify_result.py`：独立重算九项门并区分 evidence verification 与 quality gate；
- `test_runner_contract.py`：先 RED 后 GREEN 的 runner 合同检查；
- `lora_one_step.py`、`completion_tail_parity.py`：同字节复用已验证依赖，避免传递 import 缺失。

逐行证据保留 loss、choice scores、预测/期望字母、correct、completion/video token 数、sequence length、梯度 finite/nonzero、内存与耗时。独立 verifier 输出：`evidenceVerification=passed`、`experimentStatus=completed`、`qualityGate=failed`。

## 运行环境

正式运行前的刷新预检确认：

- MLX `0.32.4`、mlx-vlm `0.8.0`、NumPy `2.5.4`；
- `Device(gpu, 0)`，Metal 可用；
- Torch/Torchvision 均不存在；
- `model.safetensors` SHA-256 为 `713fe7e5d3c3965f7106b0d0ee17615f7869c23c8d327996df8c1196fbcf07d5`；
- tokenizer SHA-256 为 `87a7830d63fcf43bf241c3c5242e96e62dd3fdc29224ca26fed8ea333db72de4`；
- 无非祖先 F08/MLX 模型进程；
- ComfyUI `queue_running=[]`、`queue_pending=[]`；
- Ollama `/api/ps` 为 `models=[]`；
- 启动前空闲磁盘 `39,194,898,432 B`。

共享服务为空不等于授权使用这些服务；正式路径仅使用 MLX Metal。F08/W6 继续为 open/not-ready。
