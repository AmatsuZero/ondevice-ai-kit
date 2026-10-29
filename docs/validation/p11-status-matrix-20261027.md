# P11 状态矩阵与发布结论

日期：2026-10-27。矩阵对应 foundation 分支当前证据快照。`pass-bounded` 表示已有范围内的行为证据，未验证项仍是发布阻断；它不等于最终能力通过。

2026-10-28 收尾裁定：用户要求当前问题记录并后续专项推进，本轮执行 `closed-with-followups`。本表原验收状态全部保留，P11仍 `open/not-ready`；后续动作归入 [F01–F12](foundation-followups.md)，不要求本轮继续清零所有发布阻断。见[收尾记录](roadmap-closure-20261028.md)。

| 能力 | 当前状态 | 已有证据 | 仍未验证或阻断 |
| --- | --- | --- | --- |
| Face detection | `pass-bounded` | [benchmark](benchmark.md)、[P11 分组报告](p11-grouped-benchmark.md)、[设备矩阵](device-matrix.md) | iOS 15、Android API26、质量阈值、峰值内存、能耗、长期资源 |
| Speech TTS | `pass-bounded` | [语音后端](speech-backends.md) | 自然语音质量、最低系统、完整离线矩阵 |
| Speech ASR file | `pass-bounded` | [语音后端](speech-backends.md)、[固定文件探针](ios-asr-file-probe.md) | 麦克风录音、自然语音 CER/WER、iOS 15 |
| Speech ASR Android | `unavailable-assets` | [语音后端](speech-backends.md)、[设备矩阵](device-matrix.md) | 已授权离线资源、真实转写、取消/关闭真机运行 |
| Video SR | `pass-bounded` | [超分探针](video-sr-backends.md)、[Video Demo](video-bridge-demo.md)、[原生导出取消](apple-video-audio.md) | Android 后端、主观质量、GPU/能耗；最低系统设备验收暂缓 |
| Multimodal Apple | `pass-bounded` | [Apple MLXVLM](apple-mlxvlm-backend.md)、[自然短视频 native 输出/8帧时间/取消重入](apple-mlxvlm-short-video.md)、[共享 Demo 自然视频 VIDEO 路由/真实 Files provider/AX 终态/Home 转场 tagged native trace/五次真机语义质量 rubric/同源 motion-control task-set/12轮 RSS 观察](multimodal-video-demo.md)、[P11 分组报告](p11-grouped-benchmark.md)、[Metal 资源观察](mobile-vlm.md#2026-10-27-metal-单目标分配观察固定公开-fixture) | temporal exact-label pair 0/3（`observed-failure`）；新增等时等待/clearCache 对照支持约1 GB残留主要来自MLX可回收缓存；整体泄漏与生产资源策略未定；真正 background 内 terminal 时序、跨场景/多任务集自然 temporal quality、可比较 GPU peak/能耗、发布资源预算、完整物理触控 |
| Multimodal Android LiteRT-LM | `unverified-production-gate-closed` | [移动端 VLM](mobile-vlm.md)、[drain 合同](android-litertlm-drain-contract.md) | 授权模型 manifest/weights、production factory、native drain、真实推理、质量、GPU/能耗 |
| Face identity/liveness | `unverified-production-model` | [身份/活体](p10-face-identity.md) | 生产模型、PAD、授权数据、校准、准确率 |
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

后续 cache 归因正式 PID 31212 仍为12/12预期终态、全部cleanup gates通过：close后无干预等待2秒 RSS 1,153,712,128 B、MLX cache 1,086,181,704 B；单次clearCache后等时RSS 137,428,992 B、active 108 B/cache 40 B。约1,016,283,136 B下降支持本固定workload的大部分残留为可回收cache；第一轮cache已存在而RSS较低，不能简单等同两个计数。raw `artifacts/verification/mlxvlm-memory-attribution-20261029/device-report.json` SHA-256=`353ab172cba08aaefd4b8a5d9fe968d79e7d4a60089b0e4c4407f69f4fc98e31`；详见[归因方法与限制](multimodal-video-demo.md#2026-10-29-mlx-cache-归因与等时等待对照)。不改生产缓存策略，不关闭全部泄漏、GPU/energy/预算或跨素材资源gap，不加入grouped样本。

2026-10-28 的原生 mux 专项先在独立诊断 App 中连续 3 轮观察到 iPhone `waiting → exporting → cancelled`；随后使用资源哈希一致的 31,608,443 字节 HEVC fixture 完成非零进度真机 3/3。取消点进度为 5.33%–7.47%，当时已生成 943–1,347 字节部分文件；调用方收到 `CancellationError` 时文件均已删除，输入哈希不变，3 轮同进程 AAC 重入 payload 相同。该子项已关闭；Android 后端、主观质量、GPU/能耗和最低系统仍开放，因此 Video SR 状态与 P11 `open/not-ready` 不变。iOS15/API26运行验收按用户要求暂缓，不视为通过。

2026-10-27 的固定公开单图独立 probe 新增单 PID Metal 分配观察峰值 `1,723,596,800 B`（3,227 条 `currentAllocatedSize` 记录）和 501 条 GPU activity。该值只覆盖单次合成图的 Metal 资源分配，未关闭可比较 GPU peak/能耗或统一资源预算；Power Profiler 的单目标 trace 因 xctrace 工具崩溃无法导出。

2026-10-28 F01 真机 sandbox 子项在 iPhone 16 Pro Max / iOS 27.0.1 上通过 3/3：EXIF2/6/7 均经生产 `DemoController.useImage` 得到预期归一化尺寸/四角像素，并收到 `apple.vision.face`、`faces=0`。验证 App 只读取沙盒 fixture，没有请求 Photos 权限或写入相册；因此真实 PHPicker→Photos provider 路径仍未验证，不改变本行 `unverified-native-residuals` 或 P11 gate。

候选 SDK 已冻结为 committed tree `7b9f7fc75772b61948d211d72ec2e1e221362591` / `f60b65fc117908a9b82c60eb42285f8c855d6f91`，归档 SHA-256 为 `6a2cb8afd895b13fa281d15f55046b3f263a1882aebd8ce31dff563bc3b2b666`。归档不含 foundation 工作树的8项受保护/未完成修改；该冻结只关闭 SDK commit 快照子门槛。

[P11 分组 benchmark](p11-grouped-benchmark.md) 已从三份哈希固定的原始设备 JSON 重算3组：iOS/Android face 与 Apple MLXVLM。9项 readiness 能力全部进入 coverage，但只有2项有本轮兼容报告，另外7项仍为 `unverified`。face 没有质量/资源数据；MLXVLM 的30/30只覆盖合成 fixture，natural quality、Metal GPU peak 与 energy 仍未验证。因此这只推进逐 run/分组报告门槛，不改变发布结论。

发布前仍需完成：

1. 在已冻结 SDK commit 的基础上，冻结剩余模型 revision、输入 fixture 和设备矩阵。
2. 对每项实际交付能力补齐正确性、质量、延迟、资源、取消和持续运行报告。
3. 在现有3组/2项能力首版上继续保留逐 run 原始 JSON，为其余实际交付能力补齐按设备、系统、能力和后端分组的 p50/p95 与质量报告，或保持明确 unavailable/unverified。
4. 解决 LiteRT-LM 授权模型与 native drain、Face Identity 生产模型/PAD/校准、P4 原生残留，以及 iOS 15/Android API26 设备缺口。

本矩阵不接受许可证、不下载模型、不改变生产 factory，也不关闭 P4/P11。
