# 播放时选择视频超分后端

接入方决定向用户提供哪些模式、是否保存用户偏好。每个播放器视图持有一个 `RealtimeVideoSR`，默认原画。在 UI 线程查询、切换、读状态；解码帧和纹理留在播放器里。

```kotlin
import io.ondeviceai.binding.RealtimeSRMode
import io.ondeviceai.binding.RealtimeVideoSR
import io.ondeviceai.realtimesr.AndroidRealtimeSRHost

// host 已绑定到当前播放器；保留返回的控制器，供 UI 和呈现回调共用。
fun bindPicker(
    host: AndroidRealtimeSRHost,
    showChoices: (List<RealtimeSRMode>, (RealtimeSRMode) -> Unit) -> Unit,
    onSelection: (RealtimeSRMode?, Boolean) -> Unit,
): RealtimeVideoSR {
    val sr = RealtimeVideoSR(host)
    showChoices(sr.supportedModes()) { choice ->
        val accepted = sr.selectMode(choice)
        onSelection(sr.selectedMode(), accepted)
    }
    return sr
}
```

- `supportedModes()` 返回当前宿主接好的选项。可用性可能改变：即使刚展示过选项，仍应处理 `selectMode()` 返回 `false`；失败时保留先前选择和呈现状态。
- `selectedMode()` 是用户的选择；`status().preferredBackend` 是播放器打算尝试的后端。实际显示以 `status().presentedBackend` 为准：切换后的首个确认前为 `null`；该帧未能增强而显示原画时，可读 `fellBackToOriginal`。重复选择同一模式不要求重新初始化引擎。
- 保留原来的 `setEnabled()`。仅实现旧 `RealtimeSRHost` 的宿主仍能开关；它只支持显式选择原画，开启时无法反推所选模式，`selectedMode()` 返回 `null`。

**Android 接入。** `AndroidRealtimeSRHost` 随 `:kmp-binding` 的 AAR 提供；传入 `RendererSwitch` 将模式和代次交给播放器，需要按设备或配置隐藏某个模式时设置 `isModeAvailable`。同代次实际显示后，向 `onPresented(generation, ordinal, backend, enhancementRequested)` 回报；旧代次或乱序帧不会覆盖当前状态。未传渲染回调时只提供原画。[播放器示例](../examples/android-realtime-sr/src/main/kotlin/io/ondeviceai/realtimesr/PlayerActivity.kt) 展示如何连接解码器、GLES 渲染器和显示回调；仅创建控制器不会处理视频像素。

| 普通 Android 播放器选项 | 行为 |
|---|---|
| `ORIGINAL` | 关闭增强，显示原画；默认。 |
| `AUTO` | 播放器按帧尝试 CuNNy，按时拿不到结果时显示原画，可在连续超时后暂时停止尝试。Auto 本身不会作为呈现后端返回。 |
| `CUNNY`、`SPATIAL` | 显式选择 GLES 渲染路径。片源、GPU 或帧预算不满足时仍可显示原画。 |

普通 Android 示例最低 API 26；这不保证每台设备都有可用的 GLES 3.1 或能按帧完成增强。[系统版本、片源与真机实测](../docs/validation/android-realtime-sr-playback.md)。`SYSTEM` 和 `LITERT` 仅供实现了相应实时渲染的自定义 `SelectableRealtimeSRHost` 声明。常规 Android 播放器不提供这两项；既有 [LiteRT 文件导出](../docs/validation/android-video-sr-demo.md) 是独立能力。

iOS 同一控制器的接入示例见 [Apple 播放器桥接](player-apple/README.md)。
