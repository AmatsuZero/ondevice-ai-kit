# P7 视频超分后端调研

更新时间：2026-10-16。P7 只比较后端边界和一条固定输入链路的探针条件，不把单帧放大或桌面运行结果当作移动端视频超分交付。

## 候选路线

| 后端 | 平台 | 优点 | 主要风险 | 当前结论 |
| --- | --- | --- | --- | --- |
| `apple.videotoolbox.sr` | iOS 26+/macOS 26+ 真机 | `VTSuperResolutionScalerConfiguration` 与 `VTFrameProcessor` 提供系统超分处理器，模型资产由系统管理 | 设备、倍率和资源状态需运行时查询；模拟器没有该 API，不能替代真机 | iOS 26+ 首个探针后端，不改变 iOS 15 基础下限 |
| `apple.coreml.sr` | iOS/macOS | 系统模型运行时、可与 Metal 纹理衔接 | 模型转换、内存峰值、设备算力和模型许可必须逐项验证 | iOS 15 路线候选；模型单独锁定 revision |
| `android.litert.sr` | Android | Android 生态和 NNAPI/GPU delegate 选择较多 | delegate 差异、包体积、模型输入布局和 API 级别差异 | Android 探针候选，先用固定短视频和 CPU baseline |
| `onnxruntime.mobile` | iOS/Android | 跨端模型接口和可裁剪 package | 移动端 EP、二进制体积、算子支持和许可证核对 | 作为跨端备选，不与系统路线同时接入 |

官方资料：

- <https://developer.apple.com/documentation/videotoolbox/vtsuperresolutionscalerconfiguration>
- <https://developer.apple.com/documentation/coreml>
- <https://ai.google.dev/edge/litert>
- <https://onnxruntime.ai/docs/tutorials/mobile/>

## 固定探针协议

输入使用 2 秒、24 fps、短边 360 的无音轨视频；记录帧数、时间戳单调性、输出尺寸、解码/模型/编码阶段耗时、峰值内存和取消行为。质量至少保留双三次基线与 SR 输出的 PSNR/SSIM；若模型不适合该输入，记录 `unavailable`，不填代理分数。所有报告按设备、OS、实际后端、模型 revision、delegate 和输入 hash 分组。

P7 当前状态：候选比较和探针协议已完成；`tools/probes/video_sr_fixture.sh` 已生成 48 帧、24 fps、无音轨的 360p 输入、720p reference 和双三次 baseline，并通过帧数/时间戳/尺寸校验。2026-10-17 复核发现 Matroska 每次写入随机标识，旧记录的文件级 SHA 无法用作跨次 fixture 身份；验证器已改为对解码后的 YUV420p 帧序列求 SHA-256。两次独立生成的稳定摘要分别是 reference `5cf853741c9ac22f9da6a23a61f9fedada870fdc7a87591731dfb6dbc3d86bf1`、input `a1cd2682cf78ea244906f631a116d3bda602d077e3734b70aeb182a643b151ed`、bicubic `2a9ec0d0539c7ac253b5016820c56a9df4195b6acfe2fa915e03dfafbb418c95`；均为 1280×720/640×360、48 帧、24 fps、无音轨。此前 baseline 对 reference 的 PSNR=33.404676、SSIM=0.970067，仅作为质量基线，不是 SR 模型结果。尚未选定模型、未引入运行时依赖、未生成移动端实机结果。

2026-10-17 iPhone 16 Pro Max / iOS 27.0.1 签名运行独立 `probes/ios-video-sr` 能力探针。系统报告 `isSupported=true`、revision 1、`supportedScaleFactors=[4]`；原定 640×360→1280×720 的 2× 配置不受支持。改为 320×180→1280×720 的 4× 配置后，返回像素格式 `1380411457`，模型状态 `downloadRequired`、进度 0。原始报告只在被忽略的 `artifacts/P7/device-runs-4x/`，未触发系统模型下载。因此本次证明系统 API 与 4× 配置可查询，不证明模型可运行、输出质量或资源指标。4× fixture 已补齐：输入帧摘要 `9a5470e699904301402aef22d9aff86d358462e80eabee6d095618b70e6fb2bc`，双三次基线摘要 `b18ebdd8b33fb87cd228093ad7b5b320ecad463905964afa2e58c6e9dba23562`；reference 摘要仍为 `5cf853741c9ac22f9da6a23a61f9fedada870fdc7a87591731dfb6dbc3d86bf1`。下一步需由探针的显式按钮下载系统资产，确认 `ready` 后再跑固定 48 帧视频并记录输出、取消、质量和资源；资产不进 Git。
