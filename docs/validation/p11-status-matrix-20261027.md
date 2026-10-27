# P11 状态矩阵与发布结论

日期：2026-10-27。矩阵对应 foundation 分支当前证据快照。`pass-bounded` 表示已有范围内的行为证据，未验证项仍是发布阻断；它不等于最终能力通过。

| 能力 | 当前状态 | 已有证据 | 仍未验证或阻断 |
| --- | --- | --- | --- |
| Face detection | `pass-bounded` | [benchmark](benchmark.md)、[设备矩阵](device-matrix.md) | iOS 15、Android API26、峰值内存、能耗、长期资源 |
| Speech TTS | `pass-bounded` | [语音后端](speech-backends.md) | 自然语音质量、最低系统、完整离线矩阵 |
| Speech ASR file | `pass-bounded` | [语音后端](speech-backends.md)、[固定文件探针](ios-asr-file-probe.md) | 麦克风录音、自然语音 CER/WER、iOS 15 |
| Speech ASR Android | `unavailable-assets` | [语音后端](speech-backends.md)、[设备矩阵](device-matrix.md) | 已授权离线资源、真实转写、取消/关闭真机运行 |
| Video SR | `pass-bounded` | [超分探针](video-sr-backends.md)、[Video Demo](video-bridge-demo.md) | Android 后端、主观质量、GPU/能耗、最低系统 |
| Multimodal Apple | `pass-bounded` | [Apple MLXVLM](apple-mlxvlm-backend.md)、[多模态视频 Demo](multimodal-video-demo.md)、[Metal 资源观察](mobile-vlm.md#2026-10-27-metal-单目标分配观察固定公开-fixture) | 自然质量、短视频完整验收、可比较 GPU peak/能耗、完整物理触控 |
| Multimodal Android LiteRT-LM | `unverified-production-gate-closed` | [移动端 VLM](mobile-vlm.md)、[drain 合同](android-litertlm-drain-contract.md) | 授权模型 manifest/weights、production factory、native drain、真实推理、质量、GPU/能耗 |
| Face identity/liveness | `unverified-production-model` | [身份/活体](p10-face-identity.md) | 生产模型、PAD、授权数据、校准、准确率 |
| Camera demo resource release | `unverified-native-residuals` | [相机验证](p4-camera-stability.md)、[AtlasTextOp 调查](p4-atlas-text-cache.md) | 18×GrOp/2304B 根因、TLS/destructor 归属、最低系统、完整 review |

## 统一结论

当前 P11 gate 为 `open`，最终发布结论为 **`not-ready`**。9 行能力均存在未验证项；这些项不能由局部 JVM、模拟器、现代系统真机、AAR 构建或 synthetic fixture 证据替代。

2026-10-27 的固定公开单图独立 probe 新增单 PID Metal 分配观察峰值 `1,723,596,800 B`（3,227 条 `currentAllocatedSize` 记录）和 501 条 GPU activity。该值只覆盖单次合成图的 Metal 资源分配，未关闭可比较 GPU peak/能耗或统一资源预算；Power Profiler 的单目标 trace 因 xctrace 工具崩溃无法导出。

候选 SDK 已冻结为 committed tree `7b9f7fc75772b61948d211d72ec2e1e221362591` / `f60b65fc117908a9b82c60eb42285f8c855d6f91`，归档 SHA-256 为 `6a2cb8afd895b13fa281d15f55046b3f263a1882aebd8ce31dff563bc3b2b666`。归档不含 foundation 工作树的8项受保护/未完成修改；该冻结只关闭 SDK commit 快照子门槛。

发布前仍需完成：

1. 在已冻结 SDK commit 的基础上，冻结剩余模型 revision、输入 fixture 和设备矩阵。
2. 对每项实际交付能力补齐正确性、质量、延迟、资源、取消和持续运行报告。
3. 保留逐 run 原始 JSON，并生成按设备、系统、能力和后端分组的 p50/p95 与质量报告。
4. 解决 LiteRT-LM 授权模型与 native drain、Face Identity 生产模型/PAD/校准、P4 原生残留，以及 iOS 15/Android API26 设备缺口。

本矩阵不接受许可证、不下载模型、不改变生产 factory，也不关闭 P4/P11。
