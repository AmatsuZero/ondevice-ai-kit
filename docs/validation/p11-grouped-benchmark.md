# P11 分组 benchmark 快照

日期：2026-10-27。该快照只聚合现有原始 JSON 中能够按既有严格 reporter 重新计算的数据；不补造历史字段，也不把不同能力的 timing 或质量指标合成总分。

## 可复现入口

跟踪的输入清单位于 `benchmark/p11/grouped-report-manifest.json`，固定 readiness 与三份原始设备报告的路径、SHA-256、source kind 和 P11 能力映射。生成命令：

```sh
python3 tools/p11_grouped_report.py \
  benchmark/p11/grouped-report-manifest.json \
  --repo-root . \
  --output artifacts/verification/p11-grouped-benchmark-20261027/summary.json
```

工具先验证所有来源哈希和仓库内路径，再分别调用 `benchmark_report.py` 与 `mlxvlm_benchmark_report.py` 重新计算；全局重复 `runId`、输出覆盖输入、同路径/软链接/现有硬链接别名、变更后的来源或逃逸路径均拒绝。所有校验完成后才写输出。

来源 `kind` 与 P11 能力映射也是固定合同：`face` 只能报告 `face-detection`，`mlxvlm` 只能报告 `multimodal-apple`。工具拒绝把一个 reporter 的原始 JSON 重标记成另一个能力，以免覆盖统计看似增加却失去语义身份。

## 当前分组结果

分组键为 device、OS、P11 capability 和 actual backend；各来源的 model/fixture/revision/measurement identity 继续随组保留。

| P11 能力 | 设备 / OS | 实际后端 | 主要 p50 / p95 | 质量与资源边界 |
| --- | --- | --- | ---: | --- |
| Face detection | iPhone17,2 / iOS 27.0.1 | `apple.vision.face` | inference `2.432 / 3.065 ms`（30样本） | 原始 quality 为空；peak memory、energy、GPU peak 均未测 |
| Face detection | TECNO KM9 / Android 15 | `android.mlkit.face.bundled` | inference `10.853538 / 18.088231 ms`（30样本） | 原始 quality 为空；peak memory、energy、GPU peak 均未测 |
| Multimodal Apple | iPhone17,2 / iOS 27.0.1 | `mlx.smolvlm2-500m` | total `940.974208 / 971.115375 ms`（30样本） | 固定合成红方块 rubric `30/30`，仅 `synthetic-fixture-only`；RSS 采样峰值 `1,192,296,448 B`、增量 `1,152,172,032 B`；natural quality、Metal GPU peak、energy 未验证 |

该表不用于跨设备或跨能力性能比较。Face 两组使用不同 fixture；MLXVLM 的 load/first-update/total 与 face preprocess/inference 也不是同一 timing 定义。

## 覆盖与剩余缺口

readiness 的9项能力全部出现在输出 coverage 中，但只有 face detection 和 multimodal Apple 具有本快照兼容的原始 grouped benchmark，形成3个组。其余7项保持 `unverified`：Speech TTS、Speech ASR file、Speech ASR Android、Video SR、Multimodal Android LiteRT-LM、Face identity/liveness、Camera demo resource release。

其中 Video SR 已有独立 per-frame p50/p95、PSNR/SSIM 和边界文档，但旧报告没有与本工具相同的 device/OS/run identity 合同，且 `qualityVerdict` 明确为 `unverified`，因此没有被硬映射。语音、身份、相机与 LiteRT-LM 同理保留各自证据和缺口。

本包不运行设备、不下载模型、不接受许可证。P11 仍为 `open`，最终发布结论仍为 `not-ready`；后续需逐能力补齐兼容原始报告或保持明确 unavailable/unverified，并完成最低系统、质量、资源、取消和持续运行门禁。
