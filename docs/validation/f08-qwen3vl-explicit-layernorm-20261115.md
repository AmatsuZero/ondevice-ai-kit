# F08 Qwen3-VL 显式 LayerNorm 因果对照

证据根：`artifacts/verification/f08-qwen3vl-explicit-layernorm-20261115/`。本轮承接已定位的 fused LayerNorm 首差异，只在隔离 Swift/Python 副本中把视觉塔全部 LayerNorm 统一为同一显式 FP32 公式，检查最终视觉差、logits 和 video6500 答案。正式模型、依赖、权重、默认配置、手机和原 16/36 成绩均未修改。

## 单变量合同

- 24 个 vision block 的 norm1/norm2、三个 deepstack merger norm 和最终 merger norm，共 **52 次**。
- 公式固定为 FP32 mean → variance → `rsqrt(variance + 1e-6)` → affine，随后 cast 回输入 dtype 并立即 `eval`。
- 输入保持同一 `pt-valid36-video_6500-q1-baseline` pixels、tokens、positions、THW `[4,18,32]`、balanced512 和选择规则。
- 唯一 Swift Metal 进程与唯一 Python MLX Metal 进程顺序完成；无 Torch、CPU 模型、手机、下载、stop 或模型重试。
- cache 256 MiB；守卫仍为物理内存 `3,758,096,384 B`、thermal raw `2`、elapsed `180000 ms`。

## 结果

| 边界 | 原 native 跨运行时 NRMSE | 全显式 LayerNorm NRMSE |
| --- | ---: | ---: |
| 首个非精确边界 | norm1 `0.000005993` | rotary `0.000000009478` |
| block 0 | `0.000235022` | `0.000153944` |
| block 5 | `0.009294929` | `0.008935823` |
| block 6 | `0.010502929` | `0.010097696` |
| block 23 | `0.063511960` | `0.066315357` |
| final merger | `0.085135892` | `0.077347711` |

显式替换确实消除了最早的 native LayerNorm 差异，并把首差异后移到已知极小 rotary 边界。block0 和 final 的跨运行时差异有所下降，但 final 只下降约 **9.15%**（新/旧比 `0.90852`），后续仍逐层累积；block23 甚至略高于旧值。因而 fused LayerNorm 是早期 parity 来源之一，但不是最终视觉差异的主要单一来源。

## 语义因果裁定

| 路径 | 选择 | A/B/C logits | margin |
| --- | --- | --- | ---: |
| 旧 Swift native | C | 26.25 / 24.625 / 26.5 | 0.25 |
| 旧 Python native | C | 26.125 / 24.375 / 26.375 | 0.25 |
| 新 Swift 显式 | C | 26.125 / 24.5 / 26.25 | 0.125 |
| 新 Python 显式 | C | 26.125 / 24.5 / 26.375 | 0.25 |

期望仍为 `A`，四条路径全部选择 `C`。显式归一化实际改变了最终视觉张量和 logits，但没有修复答案，因此**fused LayerNorm 数值差不是本题错误的充分原因**。这也进一步支持：当前 16/36 的主要产品阻塞是模型/任务内容能力与完整视觉计算链的可靠性，而不是一个可通过替换 LayerNorm 单独修复的移动端算子故障。

该结论仍限定单一冻结病例，不能证明所有错误都与运行时无关。若继续做跨运行时 parity，下一非精确边界是 rotary，之后 attention/MLP 等计算继续累积；但这条 parity 工作不应再冒充质量修复。面向 W6 的高价值下一步应回到候选模型/任务适用性和独立质量门，而不是继续为同一错题逐算子追求逐字节一致。

## 资源与验收

- Swift 52 次调用，峰值物理内存 `2,764,524,136 B`，thermal 0→0，答案 `C`。
- Python 52 次调用，峰值物理内存 `2,805,057,960 B`，thermal 最大 0，答案 `C`。
- `acceptance.json` 与 `audit.json` 通过；35 个主边界、五个最终输入边界、两步 logits、旧 native 双臂和资源守卫均离线复算。
- 首次无模型 preflight 把本题 684 tokens 错套成另例 674-token balanced 边界，原 `python-preflight` exit1 与旧 `execution-freeze.json` 保留；r2 只修输入合同、生成 `execution-freeze-r2.json` 并通过，没有重编译或模型重试。
- iPhone 16 Pro Max 质量仍为 **16/36**；W6 的质量、产品预算、正式采用/集成仍为 **open/not-ready**。
