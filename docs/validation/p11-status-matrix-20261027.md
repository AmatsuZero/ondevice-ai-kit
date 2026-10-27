# P11 状态矩阵与发布结论

日期：2026-10-27。矩阵对应 foundation 分支当前证据快照。`pass-bounded` 表示已有范围内的行为证据，未验证项仍是发布阻断；它不等于最终能力通过。

| 能力 | 当前状态 | 已有证据 | 仍未验证或阻断 |
| --- | --- | --- | --- |
| Face detection | `pass-bounded` | iOS 27.0.1、Android 15/API35 benchmark 与设备矩阵 | iOS 15、Android API26、峰值内存、能耗、长期资源 |
| Speech TTS | `pass-bounded` | iOS 27.0.1 与 Android 15/API35 后端/关闭路径 | 自然语音质量、最低系统、完整离线矩阵 |
| Speech ASR file | `pass-bounded` | iOS 真机固定文件识别、取消和重入报告 | 麦克风录音、自然语音 CER/WER、iOS 15 |
| Speech ASR Android | `unavailable-assets` | Android 后端与设备矩阵记录 | 已授权离线资源、真实转写、取消/关闭真机运行 |
| Video SR | `pass-bounded` | iOS 真机 48 帧链路、取消、音轨和质量测量记录 | Android 后端、主观质量、GPU/能耗、最低系统 |
| Multimodal Apple | `pass-bounded` | Apple MLXVLM 图像与视频 Demo/设备报告 | 自然质量、短视频完整验收、GPU/能耗、完整物理触控 |
| Multimodal Android LiteRT-LM | `unverified-production-gate-closed` | LiteRT-LM 0.18.1 AAR 与 drain 合同取证、Kotlin opt-in consumer | 授权模型 manifest/weights、production factory、native drain、真实推理、质量、GPU/能耗 |
| Face identity/liveness | `unverified-production-model` | 双端 API、存储、生命周期和分发记录 | 生产模型、PAD、授权数据、校准、准确率 |
| Camera demo resource release | `unverified-native-residuals` | iOS 27.0.1 相机/页面退出、AtlasTextOp 线程调查 | 18×GrOp/2304B 根因、TLS/destructor 归属、最低系统、完整 review |

## 统一结论

当前 P11 gate 为 `open`，最终发布结论为 **`not-ready`**。9 行能力均存在未验证项；这些项不能由局部 JVM、模拟器、现代系统真机、AAR 构建或 synthetic fixture 证据替代。

发布前仍需完成：

1. 冻结候选 SDK、模型 revision、输入 fixture 和设备矩阵。
2. 对每项实际交付能力补齐正确性、质量、延迟、资源、取消和持续运行报告。
3. 保留逐 run 原始 JSON，并生成按设备、系统、能力和后端分组的 p50/p95 与质量报告。
4. 解决 LiteRT-LM 授权模型与 native drain、Face Identity 生产模型/PAD/校准、P4 原生残留，以及 iOS 15/Android API26 设备缺口。

本矩阵不接受许可证、不下载模型、不改变生产 factory，也不关闭 P4/P11。
