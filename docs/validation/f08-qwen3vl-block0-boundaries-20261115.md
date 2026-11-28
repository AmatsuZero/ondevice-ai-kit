# F08 Qwen3-VL block 0 子边界定位

证据根：`artifacts/verification/f08-qwen3vl-block0-boundaries-20261115/`。本轮承接已通过的24层trace，只观察同一冻结case `pt-valid36-video_6500-q1-baseline` 的vision block 0。正式模型、依赖、权重、默认配置、手机和原16/36成绩均未修改。

## 执行与自校验

- 唯一Swift Metal进程和唯一Python MLX Metal进程顺序完成，无stop、重试、PyTorch或CPU模型。
- 两runtime都先运行未修改的native block 0，再从同一输入独立重放norm/attention/MLP子边界。
- Swift native/trace逐字节一致，Python native/trace逐字节一致；Swift两条旧答案logits与exact-boundaries逐字节一致，答案仍为`C`（期望`A`）。
- 推理前冻结69个源、模型、输入、二进制、Python package和旧向量；`acceptance.json`及`audit.json`通过。

## 子边界曲线

| 边界 | NRMSE | max abs | 结论 |
| --- | ---: | ---: | --- |
| block0 input | 0 | 0 | 输入逐字节一致 |
| **norm1** | **0.000005993** | 0.001953125 | **首个非精确边界** |
| qkv | 0.000011680 | 0.0078125 | 继续放大 |
| q/k pre-rotary | 0.000010280 / 0.000014793 | 0.0078125 | rotary前已不同 |
| q/k post-rotary | 0.000014607 / 0.000016112 | 0.015625 / 0.0078125 | rotary不是首因 |
| attention pre-proj | 0.000037364 | 0.00390625 | attention累计 |
| attention residual | 0.000041566 | 0.015625 | MLP前已不同 |
| norm2 | 0.000162483 | 0.0293579 | 第二次LayerNorm显著放大 |
| MLP activation | 0.000345626 | 0.015625 | MLP内部再放大 |
| native/traced block output | 0.000235022 | 0.03125 | 与24层trace的block0精确对应 |

原假设“attention分支在MLP前已非精确”成立，但更精确的定位是：差异在`norm1`已经出现，早于qkv、rotary和SDPA；因此不能把SDPA称为最早来源。`norm2`与MLP会放大已有差异。

## 离线验收修正

首版`accept.py`在模型完成后因post-rotary观察器shape拒绝：Swift导出`[1,2304,16,64]`，Python导出`[2304,16,64]`，其余15个shape相同。原checker和失败保留；没有重跑模型。新`accept_r2.py`只对这两个已证明的leading singleton做squeeze，仍严格检查元素数量、SHA、有限值、其余shape和全部数值；`audit_r2.py`记录该恢复并通过。不能据此全局忽略单例维或放宽任意shape。

## 资源与裁定

- Swift峰值物理内存`3,218,410,304 B`，Python`2,387,544,344 B`，thermal均0。
- 本证据根约584MiB；无3.5GiB/thermal/180s保护触发。
- 两runtime仍同答错误选项，数值差尚未证明为语义失败主因。原iPhone16ProMax **16/36**、产品资源预算、正式采用/集成和W6 **open/not-ready**保持。

## 下一单变量假设

下一步只检查block0 norm1：先比较实际加载的LayerNorm weight/bias，再用同一exact input分别导出mean、variance、rsqrt、normalized和affine结果。假设参数逐字节一致，而差异来自Swift/Python LayerNorm算子或其dtype/归约顺序；若参数不同，先停在加载/映射边界，不继续算子归因。
