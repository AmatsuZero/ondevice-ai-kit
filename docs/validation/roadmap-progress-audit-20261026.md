# Roadmap 阶段计数复核

2026-10-26。针对“为何仍是 5/12”复核最初阶段定义、当前记录和已保存的原始证据。此次只校正进度统计，没有缩减已批准的功能、实现或验收范围，没有重跑设备，也没有将项目标记为完成。

## 统计依据与结论

最初阶段表由 `cb7c13b` 引入；加入 P11 后的 12 阶段基线为 `cda5ba8:docs/ROADMAP.md`。该基线明确把调研/探针、后续 SDK/Demo/第二平台接入和 P11 最终验收分开，并允许不能可靠测量的资源项记为 null/unmeasured。

依据这些工作包各自的停止点，基础里程碑为 **10/12**：P0–P3、P5–P10 已达到原定停止点，P4 和 P11 尚未完成。阶段大小不同，该计数不表示工时比例，也不表示完整 SDK/Demo 的验收或发布完成。

此前的 **5/12** 只累计了 P0–P3、P8。后续实现、第二平台、质量、模型资产、最低系统和最终审查要求不断被挂回已有阶段，导致阶段停止点随扩展工作漂移。那些工作仍然需要完成，但不应抹去已经取得的阶段性成果。历史检查点保留其当时数字；当前进度采用本次核对后的定义。

## 逐阶段复核

| 阶段 | 原定停止点 | 证据与本次核对 | 基础里程碑 |
| --- | --- | --- | --- |
| P0 | 当前快照、验收记录与两端同步 | 阶段表已有闭环记录；此次不将历史同步视作当前远端状态 | 完成 |
| P1 | SDK 生命周期独立审查、问题修复和测试 | [P1 审查](p1-lifecycle-review.md)；原范围已关闭 | 完成 |
| P2 | ModelStore 路径、损坏、中断、并发与 lease 审查 | [P2 审查](p2-model-store-review.md)；原范围已关闭 | 完成 |
| P3 | 一台真实 iPhone 的最小 Vision 输入/结果闭环 | [P3 真机记录](p3-ios-device.md) | 完成 |
| P4 | 照片、相机、旋转/镜像、取消、前后台与桥接资源释放 | [P4 记录](p4-camera-stability.md)仍有 EXIF/完整资源释放缺口；不因已有部分交互成功而关闭 | 未完成 |
| P5 | 固定输入 5 次预热+30 次测量、分组报告、Maven/XCFramework 独立消费者；未测项为 null | [benchmark](benchmark.md)；本次对两个 `927b7749` 原始 JSON 重新运行 `benchmark_report.py`，均成功；核对独立 Apple/Android consumer 日志成功标记 | 完成 |
| P6 | ASR/TTS 后端与资源调研，再完成一个最小探针 | [语音调研](speech-backends.md)；真实 `ios-speech-device-cancel-report.json` 的 TTS 为 success，cancelled/closed；网络行为仍是 unmeasured | 完成 |
| P7 | 后端调研，再完成一条固定视频链路 | [视频调研](video-sr-backends.md)；真实 `ios-video-sr-probe-777236F5-1760-4698-A44E-8EABAFAD81A5.json` 为 success、48→48 帧；不将质量 unverified 改成 pass | 完成 |
| P8 | 记录 VLM 候选许可、平台、体积、内存和证据等级，选一条路线 | [移动端 VLM](mobile-vlm.md) | 完成 |
| P9 | 一个 checkpoint、平台、runtime 的固定图像→输出→资源/耗时原始证据；第二平台另包 | [Apple VLM](apple-mlxvlm-backend.md)；本次用 `--expected-samples 30` 重新验证 `20261025-fix2/device-report.json`，成功；只采纳已测进程 RSS/延迟，不推导 GPU、能耗或自然质量 | 完成 |
| P10 | 分开设计 1:1、1:N、活体接口、指标、阈值、授权删除和特征生命周期 | [已批准规格](../superpowers/specs/2026-10-17-face-identity-liveness-design.md)第 1、3、4 节；不以设计完成宣称生产模型或校准完成 | 完成 |
| P11 | 冻结届时实际交付范围，统一正确性、质量、延迟、资源、取消、持续运行验证与发布结论 | 尚无完整统一验收矩阵和发布结论；单能力报告不替代 | 未完成 |

重新生成的 P5/P9 汇总与本次读取的证据摘要位于被忽略的 `artifacts/verification/roadmap-scope-audit-20261026/`。这些是对既有报告的核对，设备、源码与产物的当前运行状态仍须在对应执行包中验证。

## 必须继续的扩展交付与验收

| 工作 | 当前状态与剩余项 |
| --- | --- |
| P4 交互/资源释放 | EXIF、桥接/渲染完整释放，以及单列的 iOS15/API26 设备验收待完成 |
| 资源与分发 | 峰值/GPU/能耗、最低系统等未测项继续显式保留；Apple MLX 增强 binary archive 仍未通过 |
| 语音 SDK/Demo | 已有 TTS、文件 ASR、bridge/Demo；麦克风实录、Android ASR 资产与实际转写、离线/质量验收仍待完成 |
| 视频 SDK/Demo | 已有 Apple 固定 profile 后端和 Demo 设备证据；Android 生产后端、主观质量、传递函数及相应审查/系统门槛仍待完成 |
| 多模态 SDK/Demo 与 Android | Apple 固定图像及有界视频链路已有证据；LiteRT-LM 模型资产、生产 adapter/consumer、真实 callback/drain/设备推理仍待完成 |
| 身份/活体实现 | Task 1–8、11、12 及部分设备存储恢复已有证据；Task 9/10 生产模型/PAD、授权数据、校准与对应审查/设备验收仍待完成 |
| P11 统一验收 | 全部交付范围冻结、统一任务集/阈值、逐 run 报告和最终发布结论待执行 |

这些扩展项保留在各自已批准的计划中，继续受原有授权、模型、设备、隐私和验证约束。项目总目标仍处于 active，完整交付未完成；不把基础里程碑的 10/12 换算为整体完成百分比。
