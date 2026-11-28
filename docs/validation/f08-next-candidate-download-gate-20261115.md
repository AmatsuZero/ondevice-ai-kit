# F08 下一移动视频候选下载门

状态：**下载前审计通过；仅 `mlx-community/Qwen3.5-2B-4bit` 可以进入固定 revision 下载与 Mac MLX Metal `valid36` 筛选准备。尚未下载、运行模型或操作 iPhone，F08/W6 仍为 open/not-ready。**

## 裁定

已知本地 Qwen3-VL 2B 与 SmolVLM2 2.2B 在 `valid36` 分别只有 **16/36** 和 **14/36**，冻结双模型规则最高 **19/36**。下一候选必须同时满足：官方有原生视频依据、产品许可可用、当前 MLX Metal 执行面存在、静态权重仍有合理资源余量，并有独立质量动机。

| 候选 | 下载前结论 | 主要依据 |
| --- | --- | --- |
| Qwen3.5-2B 4-bit | **go：仅进入下载/协议冻结** | Apache-2.0；官方原生视频；当前官方表相对 Qwen3-VL-2B 的 VideoMME 无字幕、VideoMMMU、MLVU、MVBench 分别为 69.0/62.1/76.2/64.9 对 62.1/54.1/69.2/64.5；1.604 GiB 权重；pinned Swift 已注册 `qwen3_5`、`Qwen35`、`Qwen3VLProcessor` 与 fallback resolver |
| LFM3-VL-2B 4-bit | **blocked** | 原生视频和 1.482 GiB 权重有吸引力，但 LFM Open License 对年收入达到 1000 万美元的商业实体不授权；没有产品法律放行。pinned Swift 无 LFM3，PR #781 仍 open 且带 `needs-ci`/`needs-changes` |
| Gemma 4 E2B it 4-bit | **no-go** | 官方支持视频，但 3,550,670,554 B 权重已占现有 3.5 GiB 诊断保护线约 94.5%，只留约 198 MiB，低于保守 512 MiB 静态余量筛选，也远小于已测更小四位模型的运行开销 |
| LFM2.5-VL-1.6B | **排除** | 官方卡描述图像、多图、OCR 与逐帧视频流 caption demo，没有原生视频输入/时序训练合同；不能把连续送图演示当通用视频理解证据 |
| MiniCPM-V 4.7 1B | **当前范围排除** | 官方有视频能力，但 pinned MLXVLM 没有 MiniCPM-V model type；现有移动路线是 GGUF/llama.cpp，超出本轮“只用 MLX Metal”范围 |

## Qwen3.5 为什么值得下一步

官方 Qwen3.5-2B 模型卡提供了独立于本项目 `valid36` 的视频质量动机。MLX 候选冻结到 revision `674aaa7240b91e8012fcad5d791b7dfe5ba90207`：仓库 1,749,081,927 B，单一 `model.safetensors` 1,722,271,785 B，LFS SHA-256 `713fe7e5d3c3965f7106b0d0ee17615f7869c23c8d327996df8c1196fbcf07d5`。

pinned `mlx-swift-lm` revision `6e338e5df49424fc0db7c6d58184d622f92ed18a` 的本地源码确认：

- `model_type = qwen3_5` 由 `Qwen35` 加载；
- `Qwen3VLProcessor` 与 Qwen3.5 processor aliases 已注册；
- `Qwen35ProcessorLoadingResolver` 能从 vision config 重建处理器配置；
- 共享 processor 已有视频采样、patchify、THW 和 `<|video_pad|>` 替换路径。

这些只能证明存在执行路径，不能证明协议正确或模型会达到产品门。

## 仍需先解决的协议边界

先前 Qwen3 对照已经证明，当前 Swift 共享 processor 的单一视频占位替换与官方按时间分组的 temporal wrappers 不同。Qwen3.5 还必须用真实 checkpoint 再验证：

1. tokenizer/BPE 与图像、视频 special token IDs；
2. 官方带时间戳的视频 prompt 展开；
3. `THW`、temporal grouping 与视觉 token 数；
4. 三轴 positions、rope delta 和 prefill 分段；
5. checkpoint 加载、首步完整 logits 与输出格式。

因此本门只允许进入“冻结下载 + 协议适配 + Mac 筛选计划”。官方 benchmark 不能替代 `valid36`。

## 后续门

下载后只在 Mac MLX Metal 串行运行一次冻结 `valid36`：

- 严格正确至少 **20/36**；
- 相对当前 Qwen3 基线 16/36 净增至少 **+4**；
- tokenizer、视频 prompt、THW、positions、rope delta、完整 logits 与输入 SHA 全部通过父核验；
- 任何协议或模型失败都停止该候选，不自动改 prompt、帧数、分辨率、量化或重试刷分。

只有 Mac 双门通过后才允许进入 iPhone 16 Pro Max 资源/热/延迟评估。3.5 GiB 仍只是既有诊断保护线，不是已批准产品预算。

## 证据与边界

证据根：`artifacts/verification/f08-next-candidate-download-gate-20261115/`。

- `source-metadata.json` 绑定官方模型/许可证/权重/PR 元数据、本地 pinned revision 与源码 SHA；
- `candidate-gate.json` 冻结候选规则、唯一 eligible 候选、停止条件和 20/+4 后续门；
- `verify_candidate_gate.py` 离线重算尺寸、资源余量、候选唯一性，并核本地 Package.resolved 与四份 Swift 源码；
- 本报告纠正了准备阶段摘要中的一处 benchmark 漂移：当前官方 Qwen3.5 表的 MVBench 是 **64.9 对 64.5**，不是旧摘要中的 71.8 对 72.1；不影响“进入下一筛选”结论；
- 远端元数据是 2026-11-15 快照；后续下载仍须按固定 revision 和文件 SHA 验收；
- 本门没有下载权重、模型推理、iPhone 操作、PyTorch、CPU 模型、Ollama 或生产依赖/默认改动。
