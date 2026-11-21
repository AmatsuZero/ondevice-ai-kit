# Unified Demo 播放时超分接入记录

2026-11-21

统一 CMP 页面现在提供“播放时增强”入口。Android 复用仓库已有 `examples/android-realtime-sr` 的解码、音频时钟、GLES renderer 和 `RealtimeVideoSR` 控制链路，并将播放器 Activity 注册到统一 Demo APK；用户从能力页点击“打开实时播放器”后进入原生播放器页，在同一 APK 内选择视频并切换原画、自动、CuNNy、Spatial。

边界保持清晰：CMP 页面负责能力说明和入口，播放器负责解码帧、逐帧增强、回退以及实际呈现回报。播放器未产生确认帧时不向 CMP 伪造 `presentedBackend`。`EglTiming` 在 Demo APK 没有 `srtiming` native 库时安全降级为无呈现时间统计，增强渲染仍可运行；没有 GLES/模型/输入能力时由播放器回退原画并显示原因。

iOS 统一页面暂未接入 UHPlayerSDK 的 `UHPlayerMetalView` 宿主，因此仍显示“当前构建未接入播放器”。接入 iOS 时应把实际播放器 View 和 `UHRealtimeSRHost` 作为 `playbackContent` / `RealtimeSRDemoActions` 传入，不应只填充模式状态。

本轮完成静态差异检查。验证时发现系统未配置 `JAVA_HOME`；改用本机 JDK 17 后，Gradle Wrapper 需要下载 9.4.1，但当前网络无法解析 `services.gradle.org`，因此编译未能启动。Android 真机播放、逐帧画质、帧时序和 iOS UHPlayerSDK 接入仍待后续独立验收。
