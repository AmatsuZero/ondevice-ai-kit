# 默认 iOS CMP Demo 文件超分接入修复 — 2026-11-22

用户在已运行的 Demo 中看到“视频文件超分：当前构建未接入”。基线为 `453e5974ee51316ee1f11ab7a572297206661513`。

## 原因与修改

“播放时增强”绑定的是 `PlaybackEnhancementHost`，并不提供文件导出。默认工程缺少 `ODAI_VIDEO_DEMO`、`ODAIKitVideo` / `ODAIKitAppleVideo` 产品，以及已有 `VideoHost`、`VideoInputStore`、`VideoFilePicker`、`IOSVideoBridge`、`IOSVideoInput` 五个源文件。因此 `UnifiedDemoIOSBindings.videoModel` / `videoActions` 编译为 nil，页面正确地显示了未包含视频模块。

已在默认 Xcode 生成器中补齐上述闭包并重新生成已提交工程，复用现有 CMP `VideoScreen`、Host 与 SDK 文件处理后端；未新增导出引擎、依赖、独立原生控制页或生产测试后门。新增默认 App 的真实 UI 回归，避免独立生成工程成功掩盖默认工程漏接。

## 验证

- RED：`ui-red.log/.xcresult`，1 项真实失败。页面实际显示“当前构建未接入 · 当前构建未包含视频模块”，找不到 `video-select`。
- GREEN：`ui-green.log/.xcresult`，1 项、0 失败；测试体 21.309 秒，外层 30.122 秒。实际打开统一视频分类中的文件超分页，查询 `apple.videotoolbox.sr`，验证选择按钮可用、空输入时处理禁用、系统选片器能打开并取消、回页输入仍为空、可以正常离页。
- 专用模拟器为 `ODAI Unified CMP 20261121` / `1BF8DC23-08A1-435E-90AE-A091A90E3DC1`。后端实际报告 `unsupportedDevice`，没有伪造可用状态或输出。
- 工具完整套件 725 项通过，59.400 秒；`tools-tests.log`。未改共享 Kotlin 或 SDK/Host 的运行逻辑。
- 默认根 workspace 真机架构签名构建通过，9.964 秒；`device-signed-build.log`。实包严格验签通过，TeamIdentifier 为 UGREEN `4X996UV8LC`，没有命令行团队/签名方式覆盖或 provisioning 更新参数。
- 生成器再次执行的工程和 scheme 字节一致，旧对象 UUID 全部保留；`generator-verification.json`。NativeConsumer 与原有 dirty SDKDistribution scheme 字节保持，私人 IDE 配置未作为本次修复提交。
- 原生只读审核结论为 ACCEPT，无本次接线修复的正确性阻塞；报告为 `/private/tmp/odai-ios-file-sr-20261122/native-review.md`。审核不替代真机成功导出验收。

## 后端边界

当前文件导出复用现有 Apple 系统超分后端：支持该能力且资源就绪的 iOS 26+ 真机、320×180、24 fps、1–48 帧、BT.709 SDR 输入，4× 输出 1280×720。支持的音频策略和拒绝条件仍见[既有文件链路](video-bridge-demo.md)。不代表任意尺寸、长视频或实时后端的文件导出已经完成。

本轮没有安装/启动用户正在使用的真机 App，也未执行新的真机文件转换、输出画质/性能验收。模拟器的选片取消和后端不可用路径不替代真机成功导出；历史后端设备证据保持原有范围。

原始日志、xcresult、签名及生成器收据位于 `/private/tmp/odai-ios-file-sr-20261122/`。
