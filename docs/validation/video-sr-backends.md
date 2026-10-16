# P7 视频超分后端调研

更新时间：2026-10-16。P7 只比较后端边界和一条固定输入链路的探针条件，不把单帧放大或桌面运行结果当作移动端视频超分交付。

## 候选路线

| 后端 | 平台 | 优点 | 主要风险 | 当前结论 |
| --- | --- | --- | --- | --- |
| `apple.videotoolbox` | iOS/macOS | 系统框架，硬件路径和编解码集成好 | VideoToolbox 本身不是通用超分模型；需要 Core ML/Metal 模型和帧级资源控制 | 作为解码/编码与硬件管线候选，不单独宣称 SR |
| `apple.coreml.sr` | iOS/macOS | 系统模型运行时、可与 Metal 纹理衔接 | 模型转换、内存峰值、设备算力和模型许可必须逐项验证 | iOS 优先探针候选；模型单独锁定 revision |
| `android.litert.sr` | Android | Android 生态和 NNAPI/GPU delegate 选择较多 | delegate 差异、包体积、模型输入布局和 API 级别差异 | Android 探针候选，先用固定短视频和 CPU baseline |
| `onnxruntime.mobile` | iOS/Android | 跨端模型接口和可裁剪 package | 移动端 EP、二进制体积、算子支持和许可证核对 | 作为跨端备选，不与系统路线同时接入 |

官方资料：

- <https://developer.apple.com/documentation/videotoolbox>
- <https://developer.apple.com/documentation/coreml>
- <https://ai.google.dev/edge/litert>
- <https://onnxruntime.ai/docs/tutorials/mobile/>

## 固定探针协议

输入使用 2 秒、24 fps、短边 360 的无音轨视频；记录帧数、时间戳单调性、输出尺寸、解码/模型/编码阶段耗时、峰值内存和取消行为。质量至少保留双三次基线与 SR 输出的 PSNR/SSIM；若模型不适合该输入，记录 `unavailable`，不填代理分数。所有报告按设备、OS、实际后端、模型 revision、delegate 和输入 hash 分组。

P7 当前状态：候选比较和探针协议已完成；`tools/probes/video_sr_fixture.sh` 已生成 48 帧、24 fps、无音轨的 360p 输入、720p reference 和双三次 baseline，并通过帧数/时间戳/尺寸校验。2026-10-17 复核 `video_sr_validate.py`：reference `sha256=8b26c4454568b58502b5d9382212f20af08ecafb6eb7fafba7ace4cd79f2764b`、input `sha256=4f38a27313f72b4af2a6b37ceb0d5da70d57bb37223e72b9f53e3abc1f86cd76`、bicubic `sha256=476f0727fc59eb5e5ebb13be47bedd6cdca339449db86e029ec7ac0f3abc09d2`；均为 1280×720/640×360、48 帧、24 fps、无音轨。baseline 对 reference 的 PSNR=33.404676、SSIM=0.970067，仅作为质量基线，不是 SR 模型结果。尚未选定模型、未引入运行时依赖、未生成移动端实机结果。
