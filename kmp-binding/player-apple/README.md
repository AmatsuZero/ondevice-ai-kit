# iPhone 实时播放超分控制

配合 UHPlayerSDK 的 `feat/realtime-sr-1080p` 分支使用。Kotlin 的 `RealtimeVideoSR` 只传开关、查询状态。解码帧、Metal 纹理、超时回退和呈现都留在播放器内。

1. 在仓库根目录运行 `./gradlew :kmp-binding:linkReleaseFrameworkIosArm64`。桥接 Pod 使用本地产物，仅针对 arm64 iPhone。
2. 在测试 App 的 Podfile 通过本地路径引用 UHPlayerSDK 和 kmp-binding 下的 ODAIPlaybackControl。CuNNy 着色器须编入宿主的 Metal library（参见 player 仓库 Examples/SuperResolution/iOSDemo）。
3. 在后台调用 `UHPlayerSuperResolution.automatic(device:compatibilityLibrary:width:height:)`，再回到主线程把引擎和实际显示的 UHPlayerMetalView 交给 `UHRealtimeSRHost`。每个视频流按输入尺寸准备；示例支持 BT.709 SDR、full-range NV12、1080p 或 540p 的 2×。

```swift
let sr = RealtimeVideoSR(host: UHRealtimeSRHost(view: view, engine: engine))
sr.setEnabled(enabled: true)
let state = sr.status()
```

`enabled` 记录请求，`preferredBackend` 是当前将尝试的后端；`presentedBackend` 由 drawable 实际呈现更新，尚无帧时为 nil。`fellBackToOriginal` 表示请求增强时最后呈现了原画；主动关闭时为 false。请持有控制器，并在主线程操作。这是分支上的本地 Demo 集成，尚未发布二进制 SDK 或 CocoaPod。

后端状态新增 SPATIAL（单遍 Metal 空间放大，序号 3）；保留原画、系统、CuNNy 的现有序号。Demo 可按后端创建引擎并绑定到同一视图，过滤机型和片源后再让用户选择。KMP 只传控制和状态，不传解码帧。配套播放器功能分支 feat/realtime-sr-1080p。
