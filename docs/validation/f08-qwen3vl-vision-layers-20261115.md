# F08 Qwen3-VL 视觉层边界定位

证据根：`artifacts/verification/f08-qwen3vl-vision-layers-20261115/`。本轮只观察已经冻结的 `pt-valid36-video_6500-q1-baseline`：期望 `A`，此前 Swift 与 Python 都输出 `C`。没有修改正式模型、依赖、权重、默认配置、手机状态或原 16/36 成绩。

## 执行合同

- 输入沿用上一轮 Swift 打包后的同一 pixels、tokens、显式 M-RoPE positions 和 THW `[4,18,32]`。
- 唯一 Swift Metal 进程与唯一 Python MLX Metal 进程顺序执行；Python 进程内先调用未修改的原生 vision，再按已安装源码逐层重放。
- 禁用网络，未导入 PyTorch，未运行 CPU 模型；cache 保持 256 MiB。
- 守卫保持物理内存 `3,758,096,384 B`、thermal raw `2`、elapsed `180000 ms`；无 stop、无重试。
- 推理前冻结 69 个源、模型、输入、二进制、Metal bundle、Python package 源及旧答案向量。

## 观察器有效性

- Python 原生 vision 与手工逐层重放的 final merger 和三组 deepstack **逐字节一致**。
- Swift 新增的 final/deepstack 观测与原有 `vision-final`、`deepstack-0..2` **逐字节一致**。
- Swift 本题两条答案 logits 与上一轮 exact-boundaries **逐字节一致**，答案仍为 `C`。
- `acceptance.json` 与 `audit.json` 均通过；34 个跨运行时边界全部重算 shape、SHA、有限值、NRMSE、最大误差和 cosine。

## 定位结果

| 边界 | NRMSE | 结论 |
| --- | ---: | --- |
| patch weight / position weight | 0 | 权重逐字节一致 |
| patch / position / patch+position | 0 | 输入与视觉前处理边界逐字节一致 |
| rotary | 0.000000009478 | 极小数值差，未接近 0.01 |
| block 0 | 0.000235022 | 首个明显但很小的 block 差异 |
| block 5 | 0.009294929 | 仍低于阈值 |
| deepstack layer 5 | 0.007727245 | merger 后误差下降 |
| **block 6** | **0.010502929** | **首个 `NRMSE > 0.01`** |
| block 10 / 17 / 23 | 0.027073546 / 0.026846182 / 0.063511960 | 后续继续累积 |
| final merger | 0.085135892 | 与上一轮本题 final 结果一致 |

结论是：差异不来自权重、Swift packed pixels、patch embedding 或绝对位置插值。数值差从第一个 vision block 开始逐层累积，block 6 只是首次跨过预注册 1% 阈值，不能据此称 block 6 存在独有实现错误。deepstack layer 5 的 NRMSE 反而低于 block 5，也不支持“某一层突然损坏”的解释。

这仍不能解释内容错误：Swift 与 Python 在该题都选择 `C`，期望 `A`。运行时数值差真实存在，但当前证据不支持它是主要语义失败原因。

## 资源与状态

- Swift 峰值物理内存 `2,776,205,952 B`，thermal 0。
- Python 峰值物理内存 `3,019,360,752 B`，thermal 0。
- 新证据目录约 703 MiB；未触发 3.5 GiB 守卫。
- iPhone 16 Pro Max 原质量仍为 **16/36**；W6 仍 **open/not-ready**，产品加载/内存/延迟预算和正式采用/集成门仍未通过。

## 下一单变量假设

下一轮只定位 block 0 内部：假设首个材料级差异出现在 attention 分支（norm1/qkv/rotary/SDPA/proj/residual）而不是后续 MLP。使用同一冻结输入和权重导出 block 0 子边界；若 attention residual 已产生差异，继续定位其最早算子；若 attention residual 仍一致，则假设被否定并转向 norm2/MLP。不得把 block 6 的阈值越界直接当根因，也不重跑旧质量集。
