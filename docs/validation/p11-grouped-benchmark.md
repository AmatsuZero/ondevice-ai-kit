# P11 分组 benchmark 快照

初版日期：2026-10-27；2026-11-02 纳入 iOS TTS 五次逐 run 及 Video SR 双后端 48 帧原始资源报告。同日另取得公开 SDK 路径的五次 Video SR 整请求报告，因测量对象与现有逐帧系统探针不同，先纳入 readiness 证据，不混入本表六组。该快照只聚合能够从哈希固定原始 JSON 严格重算的数据；不补造历史字段，也不把不同能力的 timing 或质量指标合成总分。

## 可复现入口

跟踪的输入清单位于 `benchmark/p11/grouped-report-manifest.json`，固定 readiness、五份原始设备报告及 TTS 既有摘要的路径、SHA-256、source kind 和 P11 能力映射。SDK 五次整请求样本只进入 readiness 的哈希证据集，不改这五份 grouped 来源。生成命令：

```sh
python3 tools/p11_grouped_report.py \
  benchmark/p11/grouped-report-manifest.json \
  --repo-root . \
  --output artifacts/verification/p11-grouped-20261102-sdk-videosr/summary.json
```

工具先验证 readiness 快照及其 `sourceHashes` 指向的全部证据哈希，再验证所有 benchmark 来源哈希和仓库内路径，按来源调用 face/MLXVLM 的既有 reporter 或 TTS/Video SR 的专用校验与重算；全局重复 `runId`、输出覆盖输入、同路径/软链接/现有硬链接别名、变更后的来源或逃逸路径均拒绝。所有校验完成后才写输出。

来源 `kind` 与 P11 能力映射也是固定合同：`face` 只能报告 `face-detection`，`mlxvlm` 只能报告 `multimodal-apple`，`tts-resource` 只能报告 `speech-tts`，`video-sr-resource` 只能报告 `video-sr`。TTS 同时核对原始报告、既有摘要及两者的哈希关系，重算五次逐 run 最近秩百分位；Video SR 在单次成功报告内核对两路输入/帧数/设备身份，分别从 48 个逐帧耗时重算最近秩百分位，并以 manifest 哈希绑定输入、参考视频、两路输出 MOV 和两份质量 JSON。质量报告必须各自指向相应输出、共用同一参考解码帧身份、均配对 48 帧且保持 `qualityVerdict=unverified`；两个后端不能指向同一输出。两者都保留独立 probe 范围。工具拒绝把一个来源重标记成另一个能力，以免覆盖统计看似增加却失去语义身份。

## 当前分组结果

分组键为 device、OS、P11 capability 和 actual backend；各来源的 model/fixture/revision/measurement identity 继续随组保留。

| P11 能力 | 设备 / OS | 实际后端 | 主要 p50 / p95 | 质量与资源边界 |
| --- | --- | --- | ---: | --- |
| Face detection | iPhone17,2 / iOS 27.0.1 | `apple.vision.face` | inference `2.432 / 3.065 ms`（30样本） | 原始 quality 为空；peak memory、energy、GPU peak 均未测 |
| Face detection | TECNO KM9 / Android 15 | `android.mlkit.face.bundled` | inference `10.853538 / 18.088231 ms`（30样本） | 原始 quality 为空；peak memory、energy、GPU peak 均未测 |
| Speech TTS | iPhone / iOS 27.0.1 | `apple.avspeech` | duration `2779.726917 / 2779.791875 ms`（5次，先有1次 warm-up） | `en-US` Samantha 固定文本；约10 ms 间隔采样 RSS 峰值 p50/p95=`44,810,240 / 44,810,240 B`，真实峰值、自然音质、断网、GPU/energy 未验 |
| Video SR | iPhone17,2 / iOS 27.0.1 | `apple.videotoolbox.sr` | frame processing `93.619833 / 95.641250 ms`（48帧） | 进程 RSS 采样峰值增量 `87,801,856 B`；单素材编码保真度 PSNR/SSIM=`36.776212/0.979250`，主观质量未验 |
| Video SR baseline | iPhone17,2 / iOS 27.0.1 | `apple.coreimage.bicubic` | frame processing `1.457208 / 1.583791 ms`（48帧） | 顺序执行的进程 RSS 采样峰值增量 `507,904 B`；同素材编码保真度 PSNR/SSIM=`38.872866/0.983506`，不是独立冷启动对照 |
| Multimodal Apple | iPhone17,2 / iOS 27.0.1 | `mlx.smolvlm2-500m` | total `940.974208 / 971.115375 ms`（30样本） | 固定合成红方块 rubric `30/30`，仅 `synthetic-fixture-only`；RSS 采样峰值 `1,192,296,448 B`、增量 `1,152,172,032 B`；natural quality、Metal GPU peak、energy 未验证 |

该表不用于跨设备或跨能力性能比较。Face 两组使用不同 fixture；TTS 的整次请求 duration、Video SR 的逐帧缩放耗时、MLXVLM 的 load/first-update/total 与 face preprocess/inference 不是同一 timing 定义。Video SR 是隔离系统探针，TTS 是隔离 SDK 后端探针，均不是正式 App 的整链路性能。Video SR 两路在同一进程顺序执行，RSS 增量不是独立冷启动或 GPU 峰值对照。

## 覆盖与剩余缺口

readiness 的9项能力全部出现在输出 coverage 中；face detection、Speech TTS、Video SR 和 multimodal Apple 具有兼容的原始 grouped benchmark，共 **4项能力/6组**。其余5项保持 `unverified`：Speech ASR file、Speech ASR Android、Multimodal Android LiteRT-LM、Face identity/liveness、Camera demo resource release。新增 SDK 样本后的 readiness 快照校验 **97 evidence paths / 105 source hashes / 9行有 gap**。

旧 `f11-videosr-20261030` 成功报告缺少设备/系统/run 身份和逐帧原始值，仍不作为本次 grouped 来源；本次仅使用新真机成功报告。新增的五次 SDK 整请求耗时及进程 RSS 样本保留为单独范围，不将约 5 秒的解码/缩放/编码全程与约 94 ms 的逐帧缩放混成同一统计组；待新增独立合同与复审后才考虑对应 SDK 分组。Video SR 的两份质量报告保持 `qualityVerdict=unverified`，分组 JSON 的 `quality.status` 也仍是 `unverified`，不把单素材 PSNR/SSIM 升级为主观质量通过。文件 ASR 的现有五次资源数据来自直连 Speech framework 的隔离 probe，不能冒充 SDK client 分组；身份、相机与 LiteRT-LM 同理保留各自证据和缺口。

两类 Video SR 研究 App 均在已连接 iPhone 上运行，输入/输出留在各自 App Documents 沙盒；本次 SDK consumer 没有下载模型或接受许可证。P11 仍为 `open`，最终发布结论仍为 `not-ready`；后续需逐能力补齐兼容原始报告或保持明确 unavailable/unverified，并完成最低系统、质量、资源、取消和持续运行门禁。
