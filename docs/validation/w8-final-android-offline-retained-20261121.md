# W8 Android 同签包：无模型分支和语音入口（2026-11-21）

在一次性 Android ARM64 / API 36 AVD 回读安装的 `base.apk` 完整摘要 `69569994cd1da25bbecf03e82d14e8b76a366d1660508368b3d105a4bafc4768`。关闭该实例的 Wi-Fi 和移动数据；检查前后无默认网络。只输入本机合成的色块 PNG 和 1 秒纯色 H.264 视频；未导入模型、请求摄像头或麦克风权限、截图或录制。

| 项目 | 实际验证 | 边界 |
| --- | --- | --- |
| 独立视频超分 | `litert.esrgan.x4` 显示 `MISSING_ASSETS`；用系统选择器导入唯一合成视频，点开始，看到 `BACKEND_UNAVAILABLE MISSING_ASSETS`，无播放/分享结果。 | 证明缺模型时的拒绝，不是超分正向推理或 TECNO 性能。 |
| 图像理解 | 显示「图像理解 · 单图」及图片入口，无视频选择入口；无输入时生成不可点。导入唯一合成 PNG 后实际生成，返回 `BACKEND_UNAVAILABLE · MISSING_ASSETS`、无模型输出，清除后输入为空。 | 不称图像推理通过，不把独立超分的「视频」标签当视频理解入口。 |
| Android ASR 入口 | 系统与 Sherpa 都显示 `PERMISSION_DENIED`，只提供麦克风按钮。直接从最终 APK 读出的 `SpeechHost` 字节码中，文件选择与清除为 no-op，非麦克风请求返回 `UNSUPPORTED_INPUT`；`AndroidSpeechBridge` 对外报告 `MICROPHONE`。 | 不是文件识别失败：该 Demo 不提供文件 ASR。生成的合成 WAV 未被选入或识别，未触发麦克风。SDK 的其它入口和 iOS 不从此推断。正向 ASR 仍须有明确授权的合适方法或缺测裁定。 |

首个 AVD 启动由临时 shell 持有，退出后进程消失；同一隔离 AVD 以持续会话重启，确认 `pm path` 尚无目标包后才安装并回读。首轮视频选择器只暴露 `Video taken…` 缩略项，应用回读名为 `18.mp4`；用 MediaStore 唯一行 `_id=18, _display_name=w8synthetic.mp4, _size=2270, duration=1000` 与文件摘要归属，不把文件名差异当选错。图片 DocumentsUI 的 Downloads 标题行在 UI 树中不标 clickable；使用唯一可见的标题行打开，只在该隔离实例选入已回读的文件。不改 App 或冻结候选。

收据：`.omx/artifacts/w8-final-android-offline-retained-20261121-Wu4XZ2x4/cleanup-and-results.json`，SHA-256 `e69f458fd5d6e9771a822b13193020d8fc5a9bae5ee5ecf6dc215096a0096a90`。同目录保留原错误、逐条 UI 树、两个 DEX 类、合成输入和实例配置；只关闭并移除自己的 AVD 实例，未动其他模拟器。TECNO 恢复枚举，固定新 transport 后仍有两条实时超分进程，即使桌面在前也未获独占窗口；真机未有写操作。
