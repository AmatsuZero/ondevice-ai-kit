# F08 Qwen3-VL block 0 norm1 定位

证据根：`artifacts/verification/f08-qwen3vl-norm1-boundaries-20261115/`。本轮只观察冻结病例 `pt-valid36-video_6500-q1-baseline`，期望 `A`，Swift 与 Python 仍都选择 `C`。没有修改正式模型、依赖、权重、默认配置、手机状态或原 iPhone 16 Pro Max 的 16/36 成绩。

## 执行合同

- 唯一 Swift Metal 进程与唯一 Python MLX Metal 进程顺序完成；无 stop、无模型重试。
- 使用相同模型 revision、同一 Swift-packed pixels、tokens、positions 与 THW `[4,18,32]`。
- Python 明确运行在 `Device(gpu, 0)`，未导入 PyTorch；两端 cache 均为 256 MiB。
- 守卫保持物理内存 `3,758,096,384 B`、thermal raw `2`、elapsed `180000 ms`。
- 推理前冻结 69 个源、模型、输入、二进制、Metal bundle、Python package 源和旧答案向量。

## 定位结果

| 边界 | 跨运行时结果 | NRMSE | max abs |
| --- | --- | ---: | ---: |
| norm1 weight | 逐字节一致 | 0 | 0 |
| norm1 bias | 逐字节一致 | 0 | 0 |
| norm1 input | 逐字节一致 | 0 | 0 |
| **native LayerNorm** | **首个非精确边界** | **0.00000599324** | **0.001953125** |
| 显式 BF16 mean / variance / invstd / normalized / affine | 全部逐字节一致 | 0 | 0 |
| 显式 FP32 mean / variance / invstd / normalized / affine | 全部逐字节一致 | 0 | 0 |

实际加载的参数与输入完全相同；普通 MLX 归约、`rsqrt`、归一化和 affine 在 BF16 与 FP32 两组路径上也完全相同。差异只出现在两个独立构建运行时的 native fused LayerNorm 输出，因此本轮把**跨运行时首差异定位到 fused LayerNorm 路径**，而不是参数加载、epsilon 运输、普通归约、qkv、rotary 或 SDPA。

同运行时离线比较显示，native 输出与显式公式本来就不会逐字节等价：Swift/Python 的 native 对显式 BF16 NRMSE 都约 `0.00626459`，对显式 FP32 都约 `0.00289367`。这与 fused kernel 使用不同数值路径相符；本轮证据没有定位到 MLX C++/Metal 内部的具体指令、归约树或编译选项，不能进一步宣称某一端实现错误。

## 语义裁定

Swift 两条 logits 与上一轮 exact-boundaries 逐字节一致，答案仍为 `C`；Python 原生路径也仍为同一错误选择。fused LayerNorm 差异是真实的运行时复现差异，但当前病例中两端语义结果相同，因此**它尚未证明是 16/36 内容错误的主要原因**。修复跨运行时数值一致性与提升视频理解质量是两个独立门。

下一项有区分力的诊断应在隔离副本中，把 vision block 的 LayerNorm 统一替换为同一显式公式，再检查最终 vision、logits 与答案：若最终跨运行时差异消失但仍答 `C`，可排除该数值漂移为本题语义主因；若答案或最终边界发生材料变化，再继续做因果定位。不能从本轮直接修改生产 LayerNorm。

## 资源与验收

- Swift 峰值物理内存 `3,063,630,656 B`，thermal 0→0，旧两条 logits 精确保持。
- Python 峰值物理内存 `2,100,529,216 B`，thermal 最大值 0，14 个目标边界完整。
- `acceptance.json` 与 `audit.json` 通过；冻结文件、shape、SHA、有限值、NRMSE、最大误差、cosine 和三个畸形数组负例均核验。
- 首个 Swift 预验脚本错误地读取不存在的 per-sample `thermalStateRaw`，原失败保留为 `swift-preaccept-check1-failure.json`；r2 改读报告顶层 `thermalStateStart/End`，没有重跑模型。
- iPhone 16 Pro Max 质量仍为 **16/36**；产品质量、资源预算、正式采用/集成和 W6 仍为 **open/not-ready**。
