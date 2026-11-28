# W8 最终候选能力与验收范围（2026-11-20）

已按当前实物对齐最终验收范围，避免只验 W9 变声就关闭整个 W8，也不重开 W3/W6 专项。Android `365fd3fd…` 与待审 `b158711e…` 的 Manifest 和 3 份 DEX 相同；iOS `4e5bdfa8…`（199 文件，执行件 `70a58191…`）与实际 Release 编译条件绑定；配套变声 ZIP `67783b01…` 摘要不变。Android 待审副本未采纳，以下只证明静态组成和实际接线，不证明运行。

| 能力 | 最终输入中的配置 | 同签包至少要验或明确裁定的范围 |
| --- | --- | --- |
| W9 文件效果变声 | 双端宿主和后端在包；变声独立 SDK 沿用同 ZIP 消费证据 | 合成文件三预设、试听、导出回读、拒绝/取消/重入/清理、前后台及真实中断；48 kHz/30 秒资源轮另按前置预算。历史录音/听感保留原摘要；任何新录音需用户明确触发。 |
| Face / Camera | Android ML Kit（14 个供应商内置模型）与相机接线；iOS Vision 导入和相机宿主符号 | 用固定合成或授权样本验导入、生命周期与拒权；相机另需本次明确范围。不要用程序可链接或旧样本时延代表新签包资源。 |
| W3 既有语音 | Android 系统 ASR/TTS 接线和 Sherpa Whisper，实际 `onCreate` 的默认 ASR ID 为 `sherpa.onnx.whisper`；APK 有 Whisper tiny 编码/解码、词表与 Silero。iOS AppleSpeech/AVSpeech 宿主在包 | 既有路径回归，分别辨别系统资源缺失、音频资源释放和可重入；禁用 W3 的是研发/模型替换，不是回归。文件优先，任何麦克风测试不自动启动。 |
| 独立视频超分 | Android LiteRT SR 后端已接入，查到模型导入位置但 APK 未发现独立 `ESRGAN.tflite`；iOS VideoToolbox SR 的宿主在包，后端要求 26+；实时播放超分不在冻结输入 | Android 无模型时的可用性和正确拒绝；模型实跑只用另行批准、已验来源的独立输入。iOS 15–25 验不可用分支，不宣传低系统可跑；26+ 功能和生命周期另验。不安装或替换普通模型。 |
| 开放式 Identity / PAD 边界 | 双端 OpenCV Identity 宿主在包；冻结宿主使用外部 YuNet/SFace 导入，不把带模型名的 LICENSE 当成权重 | 验无模型/无校准 fail-closed，已批准独立输入才能跑推理；多人校准/PAD 仍按原 W7 裁定，不扩大范围或宣称生产准确率。 |
| 图像理解 / W6 | Android LiteRT LM Host 工厂 `imageOnly=true`，实际 DEX 传入空的 video store / picker；仅有 ARM64 和 x86_64 的 LiteRT LM JNI，查了具类型后缀的资产，未发现独立 LM 权重。iOS 无多模态/MLX 编译开关或所查宿主符号 | Android 验图像入口的可用性／模型缺失行为及**没有视频理解入口**；获批外部模型前不报推理通过。iOS 记录此候选未纳入该能力；不重开视频理解实验。 |

2026-11-21 同签包增量：Android Demo 语音只接麦克风 ASR（两个提供器）；**文件优先不代表 Android 有文件入口**。最终 DEX 的文件选择方法为空、非麦克风请求被拒绝；不把未消费的合成 WAV 写作识别成功或失败。iOS 文件路径仍可按实际配置单独验。见[Android 同签包实测](w8-final-android-offline-retained-20261121.md)。

适用的 retained 能力都进入最终验收表，不把上表的最低回归动作当成资源预算或未覆盖项目的豁免。两份 Intel ABI 仍随包核通知；运行后置。ARM 在 TECNO 上验，iOS 15.0 实跑、能耗与预算仍须明确裁定。双端最终签包、证据完整性及 Copilot 同摘要验收未执行。

本地证据：`.omx/artifacts/w8-capability-scope-20261120-1xDB4a/`（实际 APK DEX 读回、Mach-O 符号、只读 `verify.py`，回执 `a122ff639b3db248c56dd4fdb2b4ac72b97a7d506741e18a9e5bd560eb9eff95`；重复核验同字节）。错 imageOnly、错视频接入、意外 iOS MLX 编译、意外 APK 模型载荷四项对照拒收。保留两次首错：四个 ML Kit .tflite 不可误归 LiteRT SR，以及最终的 `onCreate` 为 protected，默认 Sherpa ID 已常量内联。未读私人样本、未导出模型、未改签装或 Git index。
