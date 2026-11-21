# iOS 边播边超分：Demo 阶段收尾（2026-11-20）

目标是从解码帧实时增强到实际上屏，并让用户在连续播放时切换后端。此阶段的交付物是独立 UHPlayerSDK Demo 与 KMP 控制桥；不要求先集成 UgreenHome 正式 App。

| 所属仓库 | 范围与定位 |
|---|---|
| ondevice-ai-kit | KMP 控制 / 实际呈现状态、iOS 桥接 Pod；功能提交 64b5ab2，入口 kmp-binding/player-apple/README.md。解码帧和纹理不跨 KMP。 |
| UHPlayerSDK（独立仓库） | feat/realtime-sr-1080p，测试版本 7d40dfe；播放器、Metal 着色器、Demo、构建说明与证据位于该仓库 Examples/SuperResolution/iOSDemo。 |

自动优先选设备和片源实际支持的系统 2×，其次 Metal 空间放大，再其次优化版 CuNNy，无可用后端则原画。允许手动选择；截止时间前拿不到结果时，本帧回退。Demo 支持目标从 iOS 15.1 起；旧系统真机运行另验。

- 合并树验证：离线执行 `:kmp-binding:linkReleaseFrameworkIosArm64` 完成；Swift 桥接针对 arm64 iOS 15.1 用新产出的 KMP framework 与配套播放器模块完成 typecheck。
- 配套分支历史真机证据：iPhone 16 Pro Max，iOS 27.2.1。540p 自动用系统、1080p 自动用 Metal；播放中切换、关闭、单帧回退恢复、允许列表和定格对比均有呈现记录。入口：Demo 的 evidence/backend-selection/，可用 check-routing.py 复核。
- 性能边界：默认 Metal 方案的两次 40 秒 1080p 短测超时率为 3.84% / 2.75%，CuNNy 直接纹理读取版为 5.43% / 4.34%。已有 30 分钟证据覆盖系统、旧版 CuNNy 和原画，不能代替这两个新版的长测。
- 上线前再做：低版本和低端真机（先按机型限制开放）、真实摄像头场景画质与持续播放、正式云控、播放器依赖发布及 UgreenHome 接入。Demo 的允许列表为本地注入。此次未发版。
