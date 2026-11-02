# P11 分组 benchmark 快照

初版日期：2026-10-27；2026-11-02 纳入 iOS TTS 五次逐 run 原始资源报告。该快照只聚合能够从哈希固定原始 JSON 严格重算的数据；不补造历史字段，也不把不同能力的 timing 或质量指标合成总分。

## 可复现入口

跟踪的输入清单位于 `benchmark/p11/grouped-report-manifest.json`，固定 readiness、四份原始设备报告及 TTS 既有摘要的路径、SHA-256、source kind 和 P11 能力映射。生成命令：

```sh
python3 tools/p11_grouped_report.py \
  benchmark/p11/grouped-report-manifest.json \
  --repo-root . \
  --output artifacts/verification/p11-grouped-benchmark-20261102-tts/summary.json
```

工具先验证 readiness 快照及其 `sourceHashes` 指向的全部证据哈希，再验证所有 benchmark 来源哈希和仓库内路径，最后分别调用 `benchmark_report.py` 与 `mlxvlm_benchmark_report.py` 重新计算；全局重复 `runId`、输出覆盖输入、同路径/软链接/现有硬链接别名、变更后的来源或逃逸路径均拒绝。所有校验完成后才写输出。

来源 `kind` 与 P11 能力映射也是固定合同：`face` 只能报告 `face-detection`，`mlxvlm` 只能报告 `multimodal-apple`，`tts-resource` 只能报告 `speech-tts`。TTS 同时核对原始报告、既有摘要及两者的哈希关系，重算五次逐 run 最近秩百分位，保留独立 probe 范围。工具拒绝把一个来源重标记成另一个能力，以免覆盖统计看似增加却失去语义身份。

## 当前分组结果

分组键为 device、OS、P11 capability 和 actual backend；各来源的 model/fixture/revision/measurement identity 继续随组保留。

| P11 能力 | 设备 / OS | 实际后端 | 主要 p50 / p95 | 质量与资源边界 |
| --- | --- | --- | ---: | --- |
| Face detection | iPhone17,2 / iOS 27.0.1 | `apple.vision.face` | inference `2.432 / 3.065 ms`（30样本） | 原始 quality 为空；peak memory、energy、GPU peak 均未测 |
| Face detection | TECNO KM9 / Android 15 | `android.mlkit.face.bundled` | inference `10.853538 / 18.088231 ms`（30样本） | 原始 quality 为空；peak memory、energy、GPU peak 均未测 |
| Speech TTS | iPhone / iOS 27.0.1 | `apple.avspeech` | duration `2779.726917 / 2779.791875 ms`（5次，先有1次 warm-up） | `en-US` Samantha 固定文本；约10 ms 间隔采样 RSS 峰值 p50/p95=`44,810,240 / 44,810,240 B`，真实峰值、自然音质、断网、GPU/energy 未验 |
| Multimodal Apple | iPhone17,2 / iOS 27.0.1 | `mlx.smolvlm2-500m` | total `940.974208 / 971.115375 ms`（30样本） | 固定合成红方块 rubric `30/30`，仅 `synthetic-fixture-only`；RSS 采样峰值 `1,192,296,448 B`、增量 `1,152,172,032 B`；natural quality、Metal GPU peak、energy 未验证 |

该表不用于跨设备或跨能力性能比较。Face 两组使用不同 fixture；TTS 的整次请求 duration、MLXVLM 的 load/first-update/total 与 face preprocess/inference 也不是同一 timing 定义。TTS 是隔离 SDK 后端 probe，不是正式 App 的整链路性能。

## 覆盖与剩余缺口

readiness 的9项能力全部出现在输出 coverage 中；face detection、Speech TTS 和 multimodal Apple 具有兼容的原始 grouped benchmark，共 **3项能力/4组**。其余6项保持 `unverified`：Speech ASR file、Speech ASR Android、Video SR、Multimodal Android LiteRT-LM、Face identity/liveness、Camera demo resource release。新 readiness 快照校验了70个 evidence paths、78个 source hashes，9行仍有 gap。

其中 Video SR 已有独立 per-frame p50/p95、PSNR/SSIM 和边界文档。复核 `f11-videosr-20261030` 的哈希固定成功报告后，确认它只保存计算后的 p50/p95，没有逐帧耗时、device、OS 或 runId；同目录另一个含 device/OS/runId 的报告只记录系统模型能力查询，属于不同运行，不能拼接为成功 benchmark。两份质量报告均明确 `qualityVerdict=unverified`。因此继续保持 Video SR 的 grouped `unverified`，下一次采样须在同一成功报告中保留设备/系统/run 身份及逐帧原始耗时，再核验质量报告的输入/输出哈希关系；单素材 PSNR/SSIM 不升级为主观质量通过。文件 ASR 的现有五次资源数据来自直连 Speech framework 的隔离 probe，不能冒充 SDK client 分组；身份、相机与 LiteRT-LM 同理保留各自证据和缺口。

本包不运行设备、不下载模型、不接受许可证。P11 仍为 `open`，最终发布结论仍为 `not-ready`；后续需逐能力补齐兼容原始报告或保持明确 unavailable/unverified，并完成最低系统、质量、资源、取消和持续运行门禁。
