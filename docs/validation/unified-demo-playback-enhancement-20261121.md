# Unified Demo 播放时超分接入记录

2026-11-21

统一 CMP 页面现在提供“播放时增强”入口。Android 复用仓库已有 `examples/android-realtime-sr` 的解码、音频时钟、GLES renderer 和 `RealtimeVideoSR` 控制链路；iOS 使用 Demo 专用的 `AVPlayer` + `AVPlayerItemVideoOutput` Host。两个平台共用能力页、入口和状态说明；iOS 已将控制项收回 CMP，Android 现阶段仍由既有播放器 Host 页面提供控制，避免在尚未抽出 Surface/时钟控制器前放入无效按钮。

边界保持清晰：CMP 页面负责能力说明和入口，播放器负责解码帧、逐帧增强、回退以及实际呈现回报。播放器未产生确认帧时不向 CMP 伪造 `presentedBackend`。`EglTiming` 在 Demo APK 没有 `srtiming` native 库时安全降级为无呈现时间统计，增强渲染仍可运行；没有 GLES/模型/输入能力时由播放器回退原画并显示原因。

iOS 不修改或依赖外部 UHPlayerSDK；当前 Demo 通过 `AVPlayerItemVideoOutput` 逐帧取图，在原画和 Core Image `CISharpenLuminance` 增强预览之间切换。增强预览明确标注为 Demo 示意，不代表生产 AI 超分后端。`PlaybackEnhancementHost.swift` 不再维护独立的原生标题、按钮或模式控件，iOS 交互由 CMP 统一呈现。Android 原生播放器控件抽取为嵌入式 CMP Host 仍是后续收口项。

本轮完成静态差异检查。验证时发现系统未配置 `JAVA_HOME`；改用本机 JDK 17 后，Gradle Wrapper 需要下载 9.4.1，但当前网络无法完成 `services.gradle.org` 下载，因此 shared framework 未能重新生成。直接运行 Xcode 时读取到旧 framework，报错是旧产物缺少新增 CMP 接口，不能作为当前 Swift 源码的最终编译结论。Android 真机播放、逐帧画质、帧时序和 iOS 真机体验仍待后续独立验收。
