# P11 分组 benchmark 快照

2026-11-05 F09追加正式清单、20本地ZIP与第二ZIP消费者两平台编译/真机资源报告证据；当前readiness改为 `artifacts/verification/p11-readiness-20261105-f09-manifest-zip/summary.json`。旧schema1硬pin保持，新schema2记录逐平台生成来源；不增加benchmark样本，F09/package入口/依赖测试及P11发布缺口保留。下段staging快照为历史。

2026-11-05 F09 的真实20模块静态双slice、两平台无源码消费者和真机资源/MLX小算术作为分发侧限定证据加入 readiness；不产生模型推理或性能样本。当前快照为 `artifacts/verification/p11-readiness-20261105-f09-binary-staging/summary.json`，旧schema的Metal固定pin仍未改，正式package门槛仍开放。

2026-11-05 readiness 新增 sherpa 离线 ASR 真实固定样本、用户确认的基本麦克风功能及独立 published AAR 消费证据。仅更新功能状态与来源哈希，不新增 grouped 样本；5项能力/8组、质量/资源及发布缺口保持。当前快照位于 `artifacts/verification/p11-readiness-20261105-offline-asr/summary.json`。

2026-11-04 已把 Android ASR资源入口/授权请求、ESRGAN完整请求/动态重播、LiteRT-LM真机ZIP导入/真实生成及双端Identity/PAD原始分数证据加入新的readiness快照。旧快照因后续文档变化已不能通过当前哈希校验，当前manifest改指重新固定来源的快照。新增的是功能与缺口证据，不是兼容benchmark样本；下面的5项能力/8组、所有既有逐run统计和质量/资源结论保持不变。

2026-11-03 F04 真机麦克风生命周期已单独纳入当前 readiness；`speech-asr-file` 的固定 CAF 分组仍只计文件输入。因[麦克风验证](ios-microphone-asr.md)后来补充本地 finish/cancel 竞态记录，旧 readiness 对该文档的哈希已失效；当前 manifest 改指重新固定文档哈希的快照。分组统计不因此增加样本，P11/发布仍为 `open/not-ready`。

2026-11-03 新增公开 SDK 的第二自然场景五次真机请求，见[瀑布视频复核](video-sr-backends.md#f11-公开-sdk-第二自然场景五次请求2026-11-03)。它有独立 bundle、输入和运行窗口，作为补充 readiness 证据索引；现有 `video-sr-sdk-resource` 仍只统计首场景的五次请求，不合并两组 p50/p95，也不据此推断场景效应或主观质量。

2026-11-03 新增 20 次同 PID 的公开 SDK Video SR 有界持续资源诊断，见[超分复核](video-sr-backends.md#f11-公开-sdk-路径-20-次有界持续请求2026-11-03)。它与既有五次整请求的测量窗口不同，暂作为独立 readiness 证据，不混入当前 `video-sr-sdk-resource` 五样本分组；本表 5 项能力/8 组和发布门禁不因该诊断升级。

初版日期：2026-10-27；2026-11-02 纳入 iOS TTS 五次逐 run、Video SR 双后端 48 帧系统探针、公开 SDK Video SR 五次整请求及生产 SDK client 文件 ASR 五次识别资源报告。2026-11-03 先因 F02 验证文档新增限定证据刷新 readiness 来源哈希，随后把独立的 20 次 SDK Video SR 有界持续运行及第二自然场景五次请求作为补充证据加入 readiness；没有把它们混入五次整请求分组或改变 P11 门禁。Video SR SDK 整请求与系统逐帧探针分组，文件 ASR SDK client 与直连 Speech framework 探针也分别保留来源。该快照只聚合能够从哈希固定原始 JSON 严格重算的数据；不补造历史字段，也不把不同能力的 timing 或质量指标合成总分。

## 可复现入口

跟踪的输入清单位于 `benchmark/p11/grouped-report-manifest.json`，固定 readiness、七份分组原始设备报告及各附属摘要/媒体/质量文件的路径、SHA-256、source kind 和 P11 能力映射。当前 readiness 另索引20次持续运行、第二场景五次请求、F04麦克风状态及2026-11-04新增功能证据，但不把它们加入 grouped `sources`。该入口依赖本地 ignored `artifacts/verification/` 原始证据；旧的 v1–v4 可搬移包不含本次哈希更新，新 clone 需取得当前 manifest 所列完整原始文件。生成命令：

```sh
python3 tools/p11_grouped_report.py \
  benchmark/p11/grouped-report-manifest.json \
  --repo-root . \
  --output artifacts/verification/p11-grouped-20261105-f09-binary-staging/summary.json
```

工具先验证 readiness 快照及其 `sourceHashes` 指向的全部证据哈希，再验证所有 benchmark 来源哈希和仓库内路径，按来源调用 face/MLXVLM 的既有 reporter 或 TTS/Video SR 的专用校验与重算；全局重复 `runId`、输出覆盖输入、同路径/软链接/现有硬链接别名、变更后的来源或逃逸路径均拒绝。所有校验完成后才写输出。

来源 `kind` 与 P11 能力映射也是固定合同：`face` 只能报告 `face-detection`，`mlxvlm` 只能报告 `multimodal-apple`，`tts-resource` 只能报告 `speech-tts`，`asr-file-sdk-resource` 只能报告 `speech-asr-file`，`video-sr-resource` 与 `video-sr-sdk-resource` 都只能报告 `video-sr`。TTS 同时核对原始报告、既有摘要及两者的哈希关系，重算五次逐 run 最近秩百分位；SDK 文件 ASR 同时核对原始报告、bounded summary、来源清单及固定 CAF 的哈希关系，五次不同成功 run 的后端/归一化文本身份和资源值，再重算最近秩百分位。系统 Video SR 在单次成功报告内核对两路输入/帧数/设备身份，分别从 48 个逐帧耗时重算最近秩百分位，并以 manifest 哈希绑定输入、参考视频、两路输出 MOV 和两份质量 JSON。SDK Video SR 核对五个不同请求 runId、整请求 wall time、采样 RSS、输入和五路输出哈希，以及首末轮质量报告与相应输出、同一参考解码帧的身份。质量始终 `unverified`。工具拒绝把一个来源重标记成另一个能力，也拒绝将系统逐帧与 SDK 整请求的数据合为一个统计组。

## 当前分组结果

分组键为 device、OS、P11 capability、actual backend 和 source kind；最后一项使两种 Video SR 测量对象即使使用同一设备/后端仍保持独立。各来源的 model/fixture/revision/measurement identity 继续随组保留。

| P11 能力 | 设备 / OS | 实际后端 | 主要 p50 / p95 | 质量与资源边界 |
| --- | --- | --- | ---: | --- |
| Face detection | iPhone17,2 / iOS 27.0.1 | `apple.vision.face` | inference `2.432 / 3.065 ms`（30样本） | 原始 quality 为空；peak memory、energy、GPU peak 均未测 |
| Face detection | TECNO KM9 / Android 15 | `android.mlkit.face.bundled` | inference `10.853538 / 18.088231 ms`（30样本） | 原始 quality 为空；peak memory、energy、GPU peak 均未测 |
| Speech TTS | iPhone / iOS 27.0.1 | `apple.avspeech` | duration `2779.726917 / 2779.791875 ms`（5次，先有1次 warm-up） | `en-US` Samantha 固定文本；约10 ms 间隔采样 RSS 峰值 p50/p95=`44,810,240 / 44,810,240 B`，真实峰值、自然音质、断网、GPU/energy 未验 |
| Speech ASR file SDK | iPhone / iOS 27.0.1 | `apple.speech.recognizer` | duration `76.484458 / 93.435792 ms`（5次，先有1次 warm-up） | 固定合成 `en-US` CAF，5/5 归一化全文匹配；约10 ms 间隔采样进程 RSS 峰值 p50/p95=`36,913,152 / 36,929,536 B`；麦克风生命周期另有有界真机证据，自然 CER/WER、断网、真实峰值/GPU/energy 未验 |
| Video SR | iPhone17,2 / iOS 27.0.1 | `apple.videotoolbox.sr` | frame processing `93.619833 / 95.641250 ms`（48帧） | 进程 RSS 采样峰值增量 `87,801,856 B`；单素材编码保真度 PSNR/SSIM=`36.776212/0.979250`，主观质量未验 |
| Video SR baseline | iPhone17,2 / iOS 27.0.1 | `apple.coreimage.bicubic` | frame processing `1.457208 / 1.583791 ms`（48帧） | 顺序执行的进程 RSS 采样峰值增量 `507,904 B`；同素材编码保真度 PSNR/SSIM=`38.872866/0.983506`，不是独立冷启动对照 |
| Video SR SDK 整请求 | iPhone17,2 / iOS 27.0.1 | `apple.videotoolbox.sr` | request wall `5110.416958 / 6533.880333 ms`（5次，首轮含预热） | 约10 ms 采样进程 RSS 峰值相对轮前 p50/p95=`77,824,000 / 259,391,488 B`；首末轮单素材编码保真度 PSNR/SSIM=`36.777730/0.979295`、`36.776212/0.979250`，主观质量未验 |
| Multimodal Apple | iPhone17,2 / iOS 27.0.1 | `mlx.smolvlm2-500m` | total `940.974208 / 971.115375 ms`（30样本） | 固定合成红方块 rubric `30/30`，仅 `synthetic-fixture-only`；RSS 采样峰值 `1,192,296,448 B`、增量 `1,152,172,032 B`；natural quality、Metal GPU peak、energy 未验证 |

该表不用于跨设备或跨能力性能比较。Face 两组使用不同 fixture；TTS 与文件 ASR 的整次请求 duration、Video SR 系统探针的逐帧缩放耗时、SDK 的整请求解码/处理/编码耗时、MLXVLM 的 load/first-update/total 与 face preprocess/inference 不是同一 timing 定义。系统 Video SR 与 TTS 是隔离探针，SDK Video SR 与文件 ASR 是隔离公开 SDK consumer，均不是正式 Demo 整链路性能。文件 ASR 的 SDK client 与直连 Speech framework probe 属于两次独立运行，不能用差值解释 SDK 固有开销。系统 Video SR 两路及 SDK Video SR 五轮都在同一进程顺序执行，RSS 增量不是独立冷启动或 GPU 峰值对照。

## 覆盖与剩余缺口

readiness 的9项能力全部出现在输出 coverage 中；face detection、Speech TTS、Speech ASR file、Video SR 和 multimodal Apple 具有兼容的原始 grouped benchmark，共 **5项能力/8组**。其余4项在 grouped coverage 中保持 `unverified`：Speech ASR Android、Multimodal Android LiteRT-LM、Face identity/liveness、Camera demo resource release。这表示缺少兼容分组报告，不表示这些能力均未实现。当前功能状态以[P11矩阵](p11-status-matrix-20261027.md)为准；readiness校验的路径/哈希计数由工具直接输出，九行仍有gap，分组统计不因功能证据索引而增加样本。

旧 `f11-videosr-20261030` 成功报告缺少设备/系统/run 身份和逐帧原始值，仍不作为本次 grouped 来源；本次仅使用新真机成功报告。五次 SDK 整请求耗时及进程 RSS 样本在 `video-sr-sdk-resource` 独立组，不将约 5 秒的解码/缩放/编码全程与约 94 ms 的逐帧缩放混成同一统计组。所有 Video SR 质量报告和分组 `quality.status` 均保持 `unverified`，不把单素材 PSNR/SSIM 升级为主观质量通过。文件 ASR 的新分组仅使用 `SpeechRecognitionClient` SDK client 的五次资源数据；既有直连 Speech framework 探针仍是独立证据，不合组。身份、相机与 LiteRT-LM 同理保留各自证据和缺口。

两类 Video SR 研究 App 均在已连接 iPhone 上运行，输入/输出留在各自 App Documents 沙盒；本次 SDK consumer 没有下载模型或接受许可证。P11 仍为 `open`，最终发布结论仍为 `not-ready`；后续需逐能力补齐兼容原始报告或保持明确 unavailable/unverified，并完成最低系统、质量、资源、取消和持续运行门禁。
