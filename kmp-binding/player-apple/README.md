# iPhone 实时播放超分控制

配合 UHPlayerSDK 的 `feat/realtime-sr-1080p` 分支使用。Kotlin 的 `RealtimeVideoSR` 用于发现和选择后端、控制开关及查询呈现状态。解码帧、Metal 纹理、超时回退和呈现都留在播放器内。

1. 在仓库根目录运行 `./gradlew :kmp-binding:linkReleaseFrameworkIosArm64`。桥接 Pod 使用本地产物，仅针对 arm64 iPhone。
2. 在测试 App 的 Podfile 通过本地路径引用 UHPlayerSDK 和 kmp-binding 下的 ODAIPlaybackControl。CuNNy 着色器须编入宿主的 Metal library（参见 player 仓库 Examples/SuperResolution/iOSDemo）。
3. 在后台调用 `UHPlayerSuperResolution.automatic(device:compatibilityLibrary:width:height:)`，再回到主线程把引擎和实际显示的 UHPlayerMetalView 交给 `UHRealtimeSRHost`。每个视频流按输入尺寸准备；示例支持 BT.709 SDR、full-range NV12、1080p 或 540p 的 2×。

已有单引擎接入可以继续使用 `init(view:engine:)` 和 `setEnabled(enabled:)`。若要显示后端选择器，将按当前设备和输入尺寸成功准备好的引擎传入 `explicitEngines`；没有准备好的选项不会出现在列表中。例如：

```swift
// view、autoEngine、可选的 spatialEngine 和 cunnyEngine 已由播放器准备。
var explicit: [UHRealtimeSRBackend: UHPlayerSuperResolution] = [:]
explicit[.spatial] = spatialEngine
explicit[.cunny] = cunnyEngine
let host = UHRealtimeSRHost(view: view, engine: autoEngine, explicitEngines: explicit)
let sr = RealtimeVideoSR(host: host)
let choices = sr.supportedModes()
if choices.contains(.spatial) {
    let accepted = sr.selectMode(mode: .spatial)
    // accepted 为 false 时保留原选择。
}
let lastShown = sr.status().presentedBackend
```

需要手动指定系统后端时，用 `automatic(device:compatibilityLibrary:nil,width:height:)` 单独准备只含系统路径的引擎，仅在其 `preferredBackend == .system` 时加入 `explicit[.system]`。CuNNy、Spatial 分别用 `init?(library:width:height:backend:)` 创建。只有传入自动引擎才会列出自动模式，Kotlin 的 `AUTO` 在当前 Swift 头文件中导出为 `.auto_`。

`selectedMode()` 记录选择，`preferredBackend` 是当前将尝试的后端；`presentedBackend` 由实际呈现的 drawable 更新，尚无确认帧时为 nil。请求增强时最后显示了原画，`fellBackToOriginal` 为 true；主动关闭则为 false。请在主线程操作并持有控制器。此桥接针对配套播放器功能分支，尚未发布二进制 SDK 或 CocoaPod。
