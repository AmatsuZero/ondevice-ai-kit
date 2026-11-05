# P11 状态矩阵与发布结论

2026-11-05 F05：可选 CPU `sherpa.onnx.whisper` 已完成普通 Demo 真实麦克风基本功能验收（用户确认基本正确、少量漏错词），以及独立本地 Maven AAR 消费。固定样本英文匹配、静音/纯噪声拒绝；强噪声 SNR0、中文精确匹配仍失败。系统 Google ASR 资源问题单独保留，不再将 Android 全体 ASR 描述成缺真实转写。无新增采音或 grouped benchmark 样本，P11/发布仍 `open/not-ready`。

2026-11-04 功能优先续接：Android LiteRT/ESRGAN 已完成普通系统选择、完整48帧超分与同字节结果动态重播；固定 LiteRT-LM0.18.1/授权 Gemma 已完成 TECNO 系统 ZIP 导入、CPU文本＋硬件GPU视觉真实生成。双端 OpenCV Identity 检测/对齐/embedding/注册删除与精确1:N机制已接入；PAD 仅有独立 iPhone 原始分数试验。下面对应行已更新，不再把这些已实现链路列为缺少后端/权重。ASR 已获授权请求 en-US，资源仍 NOT_REPORTED，最新只读复核仍无默认网络；真实转写未证。新增证据纳入 `artifacts/verification/p11-readiness-20261104-mainflows/summary.json`；不补造延迟/RSS/能耗样本，既有 grouped benchmark 仍为5项能力/8组，九行仍有 gap，P11/发布保持 `open/not-ready`。

2026-11-03 F04 新增 iPhone17,2 / iOS 27.0.1 真实麦克风有界生命周期：隔离 Demo Host 同 PID 三轮 `finishInput → success`，另有显式取消/重入、Home 后取消/回前台重入成功；公开 `SpeechRecognitionClient` 的单轮插桩 close 探针观察到 engine/tap、buffer delivery、原生任务及 session lease 顺序释放，关闭后请求被拒绝。状态与原始日志哈希见[麦克风验证](ios-microphone-asr.md#2026-11-03-显式-closedrain-真机探针)。这些证据只收窄“真实麦克风生命周期未运行”的缺口；未记录音频/转写，也没有自然 CER/WER、麦克风逐 run 延迟/RSS、完整离线矩阵或长期资源预算。`speech-asr-file` 的既有固定 CAF 分组保持不变，F04/P11 发布仍 `open/not-ready`。

2026-11-03 F11 使用独立公开 SDK consumer 在同一 iPhone17,2 / iOS 27.0.1、PID 36727 上对固定公开视频连续运行 20 次，20/20 输出各经哈希、48 帧时间戳和 HEVC/BT.709 校验。约 107 秒的整请求 p50/p95=`5176.6325/6453.235666 ms`，约 10 ms 采样进程 RSS 峰值相对轮前 p50/p95=`88,768,512/133,595,136 B`；首轮结束到末轮结束 RSS 增加 `1,064,960 B`。这是单素材有界趋势，仍缺长期、跨场景、真实/GPU 峰值、能耗及主观质量；旧研究进程存在，未证明全机负载隔离。原始报告 SHA-256=`7703eff1de1fd868a910f03e2201444eec81ca601d87c4706a674702bbeb71c9`，详见[超分复核](video-sr-backends.md#f11-公开-sdk-路径-20-次有界持续请求2026-11-03)。P11 和发布仍 `open/not-ready`。

2026-11-02 F11 另有公开 SDK 路径五次同 PID 真机请求：`VideoProcessingClient → AppleVideoToolboxBackend`，整请求耗时最近秩 p50/p95=`5110.416958/6533.880333 ms`，约10 ms 采样进程 RSS 峰值相对轮前 p50/p95=`77,824,000/259,391,488 B`。五路 48 帧 HEVC 输出均经哈希、时间戳及元数据校验；首末轮编码保真度报告仍为 `qualityVerdict=unverified`。这是隔离 SDK consumer 的整请求测量，已进入独立 `video-sr-sdk-resource` 组，不与系统探针逐帧耗时合组，也不证明长期资源、主观质量或 GPU/energy。原始证据见[超分复核](video-sr-backends.md#f11-公开-sdk-路径五次真机请求2026-11-02)。

同日将既有生产 SDK client 固定文件 ASR 五次真机资源报告加入独立 `asr-file-sdk-resource` 组；最近秩 duration p50/p95=`76.484458/93.435792 ms`，约10 ms 采样进程 RSS 峰值 p50/p95=`36,913,152/36,929,536 B`。固定合成英文 CAF 的归一化全文匹配不证明自然 CER/WER、真实麦克风或离线网络行为；与直连 Speech framework 探针的两次运行也不用于计算 SDK 固有开销。readiness 仍 **97 evidence paths / 105 source hashes / 9 rows with gaps**，grouped 为 **5项能力/8组**；P11 仍 `open/not-ready`。

2026-11-02 F11 隔离 iOS Video SR probe 新增同一成功报告内的硬件型号/系统/runId 与两路各 48 个逐帧耗时，固定公开输入 SHA-256=`97682e3f...`。P11 按最近秩重算 SR 每帧 p50/p95=`93.619833/95.641250 ms`、Core Image bicubic=`1.457208/1.583791 ms`，进程 RSS 采样峰值增量分别为 `87,801,856/507,904 B`。对应质量报告的单素材编码输出 PSNR/SSIM 分别为 `36.776212/0.979250` 和 `38.872866/0.983506`，`qualityVerdict=unverified`，主观质量及模型传递函数未验。新 grouped coverage **4项能力/6组**，另5项无兼容分组；readiness **76 evidence paths / 84 source hashes / 9 rows with gaps**，P11 仍 `open/not-ready`。原始报告和限制见[超分复核](video-sr-backends.md#f11-可复算真机逐帧报告2026-11-02)与[分组报告](p11-grouped-benchmark.md)。

2026-11-02 F11 将固定来源的 iOS TTS 五次逐 run 原始报告纳入严格 grouped 工具：新增 `speech-tts` / `apple.avspeech` 一组，duration p50/p95=`2779.726917/2779.791875 ms`，约10 ms 采样 RSS 峰值 p50/p95 均为 `44,810,240 B`。报告明确为隔离 SDK TTS probe，voice 为 `en-US` Samantha；自然音质、断网、真实峰值、GPU/energy、最低系统仍未验证。当前 grouped coverage **3项能力/4组**，另6项无兼容分组；readiness **70 evidence paths / 78 source hashes / 9 rows with gaps**。F02 tokenized 同 PID trace 作为新增限定证据，生产归属仍开放；P11 保持 `open/not-ready`。生成入口见[分组报告](p11-grouped-benchmark.md)。

2026-10-31 F11 对两份已固定 SHA-256 的自然视频原始报告做[六行跨场景自由描述核对](f11-cross-scene-video-quality-20261031.md)：飞鸟、瀑布各三个 motion window，场景词分别命中 3/3；飞鸟现有完整 rubric 仅 1/3，瀑布三行含抽查画面不支持的人、动物或鸟细节，完整语义正确率仍未评定。两场景严格 motion/frozen pair 都为 0/3；不同素材和 source commit 不构成配对 benchmark。派生记录 SHA-256=`530c4676f9e81f9a68a76e27aa806f872a4713bfc793361edd8a84267bebe474`；readiness 为 69 evidence paths / 76 source hashes / 9 rows with gaps，既有 grouped benchmark 仍为 3 组。P11 仍 `open/not-ready`。

日期：2026-10-27。矩阵对应 foundation 分支当前证据快照。`pass-bounded` 表示已有范围内的行为证据，未验证项仍是发布阻断；它不等于最终能力通过。

2026-10-28 收尾裁定：用户要求当前问题记录并后续专项推进，本轮执行 `closed-with-followups`。本表原验收状态全部保留，P11仍 `open/not-ready`；后续动作归入 [F01–F12](foundation-followups.md)，不要求本轮继续清零所有发布阻断。见[收尾记录](roadmap-closure-20261028.md)。

2026-10-31 F08 新增第二个公开视频场景 Kawaida 瀑布（Lebu Ayiga，CC BY 4.0）：iPhone 16 Pro Max / iOS 27.0.1 的隔离 native consumer 完成 9/9 请求，三个自由描述均提到瀑布/水，但含未由抽查画面支持的额外细节；motion/frozen 严格标签 pair **0/3**，剥除句号后仍 **0/3**。原始报告 `artifacts/verification/f08-cross-scene-waterfall-20261031/device-report.json` SHA-256=`bcf785bc9637965378f22ac67204e15d68fa63eb654d35fa976946cfdd68b7b`，摘要 SHA-256=`30be9e4895bedb2e547b9fb76fb49b2ce8154ebc2d6c55c8a86fea14da0a7280`；来源、许可、fixture 和限制见[视频验证](multimodal-video-demo.md#2026-10-31-f08-第二自然场景kawaida-瀑布)。这不是全量自然质量或 Demo 验收；新增 readiness 为 67 evidence paths / 74 source hashes / 9 rows with gaps，P11 仍 `open/not-ready`。

2026-10-30 F11 新增一条真实 iOS TTS 原始资源报告：iPhone 16 Pro Max / iOS 27.0.1，`apple.avspeech`、固定 `en-US` Samantha，1 次 warm-up 后 5 次逐 run，5/5 成功；耗时 p50=`2779.727 ms`、p95=`2779.792 ms`，10 ms 左右间隔的 `mach_task_basic_info.resident_size` 采样峰值为 `44,761,088–44,810,240 B`。原始报告与来源清单位于 ignored `artifacts/verification/f11-speech-tts-resources-20261030/`，报告 SHA-256=`d3c9d6caf68d5a45fa10bc9c85f8a23de4c469018d6ec0150c8b26b25d22e7c8`，bounded summary SHA-256=`c8c515d451d9237b3e28120358dc831ef16398f7ed9ab83f37a050fb3a7515e3`。这只补齐 TTS 的逐 run 延迟与采样 RSS；自然语音质量、断网行为、真实峰值、GPU/energy、最低系统仍未验证。该报告不加入既有 grouped benchmark 样本；当前 readiness 总计为 61 evidence paths / 68 source hashes / 9 rows with gaps。

2026-10-30 F11 iOS Video SR 复核使用隔离 probe 和同一公开 48 帧自然素材，SR 与 Core Image bicubic 均成功输出 720p/24 fps；SR 每帧 p50/p95=`93.408/94.587 ms`、采样 RSS 峰值增量=`127,500,288 B`，bicubic 为 `1.467/2.114 ms`、`442,368 B`。两路 48 帧 PSNR/SSIM 分别为 `36.776212/0.979250` 与 `38.872866/0.983506`，色彩元数据匹配；原始路径和哈希见[超分复核](video-sr-backends.md#f11-真实-ios-资源与质量复核2026-10-30)。单一素材的编码输出保真度不等于主观模型质量，`qualityVerdict=unverified`。该数据不加入原分组报告的三份兼容样本；Android 后端、GPU/energy、持续资源和最低系统仍开放，P11 仍 `open/not-ready`。本轮 8 项原始输入/输出与报告已纳入当前 readiness，总计为 61 evidence paths / 68 source hashes / 9 rows with gaps。

2026-10-30 F11 文件 ASR 资源补测在隔离 Speech framework probe 中使用固定 2.473 秒合成 CAF；iPhone 16 Pro Max / iOS 27.0.1，PID `32472` 与启动回执一致，`requiresOnDeviceRecognition=true`，未使用麦克风。warm-up 和随后五次识别均为完整任务且归一化全文匹配；五次耗时 p50/p95=`75.010/76.434 ms`，约 10 ms 间隔采样的进程 RSS 峰值范围 `36,831,232–36,945,920 B`。原始 `artifacts/verification/f11-speech-asr-resources-20261030/device-report-final.json` SHA-256=`81e5312e678bce19cf441933ef430db670f7e5df483a60cf291bec92dad632f2`，bounded summary SHA-256=`71e163cec6634e595c338025d2070a8c0baa63271e936b585c2ddb15ddcca6f3`。这只量到直连 Speech framework 的固定文件探针，不是生产 SDK client 性能、自然语音 CER/WER、断网行为、真实峰值/GPU/能耗或最低系统验收；首轮与正式复跑的 RSS 基线差异较大，不能据此设发布预算。报告不加入既有 grouped 兼容样本，P11 仍 `open/not-ready`。

2026-10-30 同一 CAF 的生产 SDK consumer 路径 `SpeechRecognitionClient.recognize` → `AppleSpeechRecognizerBackend.recognize` 已在同一 iPhone / iOS 27.0.1 上单独测量：预热及 5/5 次成功、backend identity 和全文归一化匹配、client close 返回；p50/p95=`76.484/93.436 ms`，采样进程 RSS 峰值=`36,782,080–36,929,536 B`。原始 `artifacts/verification/f11-speech-asr-sdk-resources-20261030/device-report-final.json` SHA-256=`40d65c13c9ec7065ff639d51063e4508d186a004d38884689f1325074ecf6578`，bounded summary SHA-256=`2b436735c860cc278f28232b1eb2d0e40492c16b8ec4c8c9ca797e0f4a9b4e04`。两轮独立运行，不能用差值归因 SDK 开销；自然 CER/WER、断网、真实峰值/GPU/能耗及最低系统仍未验证。该报告不加入既有 grouped 兼容样本，P11 仍 `open/not-ready`。

| 能力 | 当前状态 | 已有证据 | 仍未验证或阻断 |
| --- | --- | --- | --- |
| Face detection | `pass-bounded` | [benchmark](benchmark.md)、[P11 分组报告](p11-grouped-benchmark.md)、[设备矩阵](device-matrix.md) | iOS 15、Android API26、质量阈值、峰值内存、能耗、长期资源 |
| Speech TTS | `pass-bounded` | [语音后端](speech-backends.md) | 自然语音质量、最低系统、完整离线矩阵 |
| Speech ASR file | `pass-bounded` | [语音后端](speech-backends.md)、[固定文件探针](ios-asr-file-probe.md)、[真机麦克风生命周期](ios-microphone-asr.md#2026-11-03-显式-closedrain-真机探针)（独立证据，不入固定文件分组） | 麦克风自然 CER/WER/逐 run 资源、完整离线矩阵与长期资源、iOS 15 |
| Speech ASR Android | `pass-bounded-offline-basic` | [离线 SDK/Demo 与独立 AAR 消费](android-sherpa-asr.md)、[系统资源入口](speech-backends.md#2026-11-04-tecno-授权资源请求与独立-aar-消费)、[设备矩阵](device-matrix.md) | sherpa 强噪声/中文准确性、真实取消重入/关闭、持续资源/API26；系统 Google 语言资源仍不可用；三次新采音授权均已消耗 |
| Video SR | `pass-bounded` | [Apple超分](video-sr-backends.md)、[Android完整请求及动态重播](android-video-sr-demo.md)、[Video Demo](video-bridge-demo.md)、[原生导出取消](apple-video-audio.md) | 主观/时域质量、传递函数、帧节奏预算、GPU/能耗与持续资源；最低系统设备验收暂缓 |
| Multimodal Apple | `pass-bounded` | [Apple MLXVLM](apple-mlxvlm-backend.md)、[自然短视频 native 输出/8帧时间/取消重入](apple-mlxvlm-short-video.md)、[共享 Demo 自然视频 VIDEO 路由/真实 Files provider/AX 终态/Home 转场 tagged native trace/五次真机语义质量 rubric/同源 motion-control task-set/12轮 RSS 观察](multimodal-video-demo.md)、[P11 分组报告](p11-grouped-benchmark.md)、[Metal 资源观察](mobile-vlm.md#2026-10-27-metal-单目标分配观察固定公开-fixture) | temporal exact-label pair 0/3（`observed-failure`）；新增等时等待/clearCache 对照支持约1 GB残留主要来自MLX可回收缓存；整体泄漏与生产资源策略未定；跨场景/多任务集自然 temporal quality、可比较 GPU peak/能耗、发布资源预算、完整物理触控 |
| Multimodal Android LiteRT-LM | `pass-bounded-sync-image` | [同步factory](android-litertlm-sync-demo.md)、[真机系统ZIP/图片选择与生成](android-litertlm-ui.md)、[异步drain合同](android-litertlm-drain-contract.md) | 异步native drain/streaming、当前候选取消重入、更广自然质量、可比较资源/GPU/能耗；当前不支持视频 |
| Face identity/liveness | `unverified-calibration-pad` | [Android真实Identity](android-opencv-identity-demo.md)、[Apple真实Identity](apple-opencv-identity.md)、[多候选/图库](identity-gallery-demo.md)、[独立PAD原始分数](pad-raw-score-smoke.md) | PAD捕获/crop与SDK/Demo判定、授权评估/校准数据、1:1/1:N阈值与准确率；原始分数不产生LIVE/SPOOF结论 |
| Camera demo resource release | `unverified-native-residuals` | [相机验证](p4-camera-stability.md)（含真机 sandbox EXIF2/6/7→生产 `useImage`→Vision 3/3）、[AtlasTextOp 调查](p4-atlas-text-cache.md) | 18×GrOp/2304B 根因、TLS/destructor 归属、真实 PHPicker provider 路径、最低系统、完整 review |

## 统一结论

当前 P11 gate 为 `open`，最终发布结论为 **`not-ready`**。9 行能力均存在未验证项；这些项不能由局部 JVM、模拟器、现代系统真机、AAR 构建或 synthetic fixture 证据替代。

2026-10-28 F08 使用固定公开 Common swifts 2秒素材，在独立 native consumer 中完成生成、首 update 主动取消、同 PID 重入；三轮均保持8帧 actual CMTime，clip=2秒与sample span=1.75秒分开，7项生命周期 gate 全通过。原始报告 `artifacts/verification/apple-mlxvlm-natural-video-20261028/device-report.json` 固定到 readiness 来源哈希。该单次诊断不加入既有单图 benchmark 的 p50/p95，也不提升自然质量、完整 KMP/Demo 交互或资源结论；仍只有3组/2项能力的兼容 benchmark。

同一公开素材在共享 Demo 的独立真机验证工程中经 `VIDEO` 输入路由完成取消、离开页面、两次重入及 AX 输出终态；原始报告 `artifacts/verification/apple-mlxvlm-natural-video-20261028/demo/device-report.json` 已固定到 readiness 来源哈希。选择路径为验证用 picker 回调注入，`systemFileSelection` 仍未验证；自然质量、OS后台及物理触控不据此升级。

同一公开素材随后通过真实系统 Files provider 进入共享 Demo；iPhone 16 Pro Max / iOS 27.0.1 上 `testNaturalMultimodalVideoFromRealFiles` 连续两次均为 **1/0/0**，真实选择 `common-swifts-24fps.mp4` 后 shared `inputKind=VIDEO`、phase=`SUCCEEDED`、backend=`mlx.smolvlm2-500m` 且输出非空。两份 xcresult 与原始摘要 `artifacts/verification/apple-mlxvlm-natural-video-20261028/real-files/device-report.json` 已固定到 readiness 来源哈希；这关闭 `systemFileSelection` 子项，仍不关闭自然质量、OS 后台或物理触控。

随后同一真机 Home XCTest 用 tagged 单调时钟 trace 保留 request/backend/drain provenance：`firstNativeClientUpdate` 后触发 Home，`willResignActive` 后 41.882 ms 出现 `nativeDrainAndLeaseRelease`，再过 1.185 ms 进入 `bridgeCancelled`，而 `didEnterBackground` 晚 601.106 ms。规范化报告为 `artifacts/verification/apple-mlxvlm-natural-video-20261028/home-timing-xctest/home-timing-device-report.json`。因此 Home 转场取消时序与 native 来源已具备证据，但真正 background 内 terminal timing 仍未验证；该报告也不加入既有 p50/p95 benchmark。

自然质量语义报告 `artifacts/verification/apple-mlxvlm-natural-video-20261028/quality/summary.json` 对同一固定素材/单 prompt 的 5 个 distinct physical-device run 检查 subject/action/scene，三类及 semantic pass 均为 **5/5**；原始报告 `quality/device-report-5.json` 的 SHA-256 为 `64a657dcb3c3ada3762fdd746b2a5b4caebbc81174f6bfdd57ec15e4635aefd0`。它只建立固定单素材 rubric 的 bounded evidence，不加入 grouped benchmark，不关闭多任务集/自然 temporal quality、GPU/energy 或持续资源 gap。

2026-10-29 同一 CC0 source 被固定为三个 2 秒 motion window 与三个 repeated-first-frame control，manifest SHA-256=`666fbfebdc999aefa5b9db944a090024b9478c13107685af621fb842babe1694`；iPhone 16 Pro Max / iOS 27.0.1 的隔离 consumer 同进程完成 9/9 requests。semantic rubric 为 1/3；六个 temporal raw output 均为 `MOVING.`，严格 exact-label 下三个 motion/control pair 均失败，summary verdict=`observed-failure`。原始报告 `device-report-v2.json` SHA-256=`620ce4d2d3a90e83e9385ad5fbb92fe7eb60be728d7be5aa2015863735413ace`，确定性 `summary.json` SHA-256=`787bb647a5d25522da17ef099fcbfa0ee3907a5dcc23cb6ca40a45d254ae0310`，均位于 `artifacts/verification/apple-mlxvlm-natural-video-20261028/temporal/`。该失败证据不加入 grouped benchmark，也不关闭 natural temporal quality、跨场景泛化、GPU/energy 或持续资源 gap，P11 仍 `open/not-ready`。

随后同一固定公开素材在隔离 consumer 的物理 iPhone 同 PID 31130 运行 12 轮：8次成功、4次首 update 后取消，逐轮 drain 和临时快照清理、最终 lease/close 均通过。RSS 首轮 drain 后 136,200,192 B，第二轮取消并 drain 后阶跃至 1,141,293,056 B，第12轮 drain 后 1,152,892,928 B；第二轮之后增加 11,599,872 B。原始报告 `artifacts/verification/mlxvlm-sustained-video-20261029/device-report.json` SHA-256=`0408fb19a30a5c4506fa5c92971453955deb5cb8c0360f19f0f2d53183007012`。这是有界进程 RSS 趋势，增长原因和发布预算仍未验证；GPU 专用内存、能耗和跨素材持续资源仍开放。该报告不加入 grouped benchmark，P11 仍 `open/not-ready`。

首 token 前 Home 诊断进一步补齐后台 terminal：PID `31298` 在 `.videoPrepared` 后、首个文本 update 前触发 Home；`didEnterBackground` 后 106.301 ms 才出现 native drain，107.359 ms 进入 `CANCELLED`，前台重入成功。规范化 raw `artifacts/verification/apple-mlxvlm-natural-video-20261028/pretoken-home-xctest/device-report-pretoken.json` SHA-256=`bdf65e038122c9b63fce6801eb9da33538de4212b33ae14ffe051a4a1a534774`；该窄项关闭，但跨素材/系统和其他资源质量门槛仍开放，不加入 grouped 样本。

静止帧场景语义探针在同一 iPhone 的三个 frozen control 上得到 subject/scene **3/3 pass-bounded**；原始报告 `artifacts/verification/apple-mlxvlm-natural-video-20261028/static-semantic/device-report.json` SHA-256=`31eb195e375c123c39d5133636609cad835af80d0a80408af7912e53fcf43e01`，bounded summary 已纳入 readiness 来源哈希。这只说明固定静止帧的飞鸟/天空语义可读；temporal exact-label pair **0/3** 与跨场景 natural temporal quality gap 保持不变，不加入 grouped benchmark。

后续 cache 归因正式 PID 31212 仍为12/12预期终态、全部cleanup gates通过：close后无干预等待2秒 RSS 1,153,712,128 B、MLX cache 1,086,181,704 B；单次clearCache后等时RSS 137,428,992 B、active 108 B/cache 40 B。约1,016,283,136 B下降支持本固定workload的大部分残留为可回收cache；第一轮cache已存在而RSS较低，不能简单等同两个计数。raw `artifacts/verification/mlxvlm-memory-attribution-20261029/device-report.json` SHA-256=`353ab172cba08aaefd4b8a5d9fe968d79e7d4a60089b0e4c4407f69f4fc98e31`；详见[归因方法与限制](multimodal-video-demo.md#2026-10-29-mlx-cache-归因与等时等待对照)。不改生产缓存策略，不关闭全部泄漏、GPU/energy/预算或跨素材资源gap，不加入grouped样本。

2026-10-28 的原生 mux 专项先在独立诊断 App 中连续 3 轮观察到 iPhone `waiting → exporting → cancelled`；随后使用资源哈希一致的 31,608,443 字节 HEVC fixture 完成非零进度真机 3/3。取消点进度为 5.33%–7.47%，当时已生成 943–1,347 字节部分文件；调用方收到 `CancellationError` 时文件均已删除，输入哈希不变，3 轮同进程 AAC 重入 payload 相同。该子项已关闭；Android 后端、主观质量、GPU/能耗和最低系统仍开放，因此 Video SR 状态与 P11 `open/not-ready` 不变。iOS15/API26运行验收按用户要求暂缓，不视为通过。

2026-10-27 的固定公开单图独立 probe 新增单 PID Metal 分配观察峰值 `1,723,596,800 B`（3,227 条 `currentAllocatedSize` 记录）和 501 条 GPU activity。该值只覆盖单次合成图的 Metal 资源分配，未关闭可比较 GPU peak/能耗或统一资源预算；Power Profiler 的单目标 trace 因 xctrace 工具崩溃无法导出。

2026-10-28 F01 真机 sandbox 子项在 iPhone 16 Pro Max / iOS 27.0.1 上通过 3/3：EXIF2/6/7 均经生产 `DemoController.useImage` 得到预期归一化尺寸/四角像素，并收到 `apple.vision.face`、`faces=0`。验证 App 只读取沙盒 fixture，没有请求 Photos 权限或写入相册；因此真实 PHPicker→Photos provider 路径仍未验证，不改变本行 `unverified-native-residuals` 或 P11 gate。

候选 SDK 已冻结为 committed tree `7b9f7fc75772b61948d211d72ec2e1e221362591` / `f60b65fc117908a9b82c60eb42285f8c855d6f91`，归档 SHA-256 为 `6a2cb8afd895b13fa281d15f55046b3f263a1882aebd8ce31dff563bc3b2b666`。归档不含 foundation 工作树的8项受保护/未完成修改；该冻结只关闭 SDK commit 快照子门槛。

[P11 分组 benchmark](p11-grouped-benchmark.md) 已从三份哈希固定的原始设备 JSON 重算3组：iOS/Android face 与 Apple MLXVLM。9项 readiness 能力全部进入 coverage，但只有2项有本轮兼容报告，另外7项仍为 `unverified`。face 没有质量/资源数据；MLXVLM 的30/30只覆盖合成 fixture，natural quality、Metal GPU peak 与 energy 仍未验证。因此这只推进逐 run/分组报告门槛，不改变发布结论。

发布前仍需完成：

1. 在已冻结 SDK commit 的基础上，冻结剩余模型 revision、输入 fixture 和设备矩阵。
2. 对每项实际交付能力补齐正确性、质量、延迟、资源、取消和持续运行报告。
3. 保留当前8组/5项能力的逐run原始JSON，为其余实际交付能力补齐按设备、系统、能力和后端分组的p50/p95与质量报告，或保持明确unavailable/unverified。
4. 继续 LiteRT-LM 异步 native drain、Android ASR 系统资源就绪、Face Identity/PAD 校准与判定、P4 生产分发/资源，以及 iOS 15/Android API26 设备验收；已完成的固定模型同步图像推理与双端 Identity 注册链路不重复列为未实现。

本矩阵不接受许可证、不下载模型、不改变生产 factory，也不关闭 P4/P11。
