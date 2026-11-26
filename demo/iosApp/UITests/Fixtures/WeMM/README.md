# W6 iOS 页面验收

这套 XCTest 从统一 Demo 的 Files 选择器导入本地模型和视频。测试宿主需要 iOS 17 或更高版本。独立夹具 App 的标识为 `io.ondeviceai.w6.unifiedvideo.fixtures`，Files 中显示为「W6 Fixtures」。三段测试素材共 5,195 字节。模型由测试人员提供。

| 文件 | 验收内容 |
| --- | --- |
| `w6-yuv-clip-01.mp4` | 先红后蓝，4 秒 32 帧，影片时基为 1，覆盖整数步长为零的回归 |
| `w6-yuv-clip-02.mp4` | 先蓝后红，问题相同，回答顺序也要反转 |
| `w6-clip-01.mp4` | 只保留首片的 32 字节 `ftyp` 容器头，选择后生成应显示解码失败并允许重新选片 |

## 构建

从仓库根目录运行。先按 [W6 原生库构建说明](../../../../../README.md) 准备 `w6_build` 中的静态 XCFramework、NOTICE 和两套 KMP framework。设置 `DEVICE_ID` 为目标 iPhone 的 Xcode destination ID，`ODAI_DEVELOPMENT_TEAM` 为已有签名团队，`WEMM_WEIGHTS` 为仅含固定版本 `language.gguf` 和 `mmproj.gguf` 的本地目录。示例用新的输出目录，保留每次测试结果。

```sh
W6_QA_DIR="$(mktemp -d "${TMPDIR:-/tmp}/w6-ui.XXXXXXXX")"
ODAI_VIDEO_DEMO=1 ODAI_MULTIMODAL_DEMO=1 ODAI_WEMM_VIDEO_DEMO=1 \
  ODAI_WEMM_UI_FIXTURES=1 ODAI_DEVELOPMENT_TEAM="$ODAI_DEVELOPMENT_TEAM" \
  ODAI_WEMM_NATIVE_XCFRAMEWORK="$w6_build/ODAIWeMMNative.xcframework" \
  ODAI_WEMM_NATIVE_NOTICE="$w6_build/iphoneos/NOTICE" \
  ODAI_SPEECH_DEMO_DIR="$W6_QA_DIR/project" ruby scripts/generate-speech-demo.rb

xcodebuild build-for-testing -project "$W6_QA_DIR/project/SpeechDemo.xcodeproj" \
  -scheme SpeechDemo -configuration Debug -destination 'generic/platform=iOS' \
  -derivedDataPath "$W6_QA_DIR/build"
```

## 按模型状态运行

从空的专用夹具 App 开始。第一阶段验证打开并取消模型选择器，第二阶段导入模型并重启恢复，最后四项验证问答、两种取消方式和损坏影片提示。每个阶段通过后再执行下一阶段。手机需解锁并可供 XCTest 使用屏幕。

```sh
W6_APP=io.ondeviceai.w6.unifiedvideo.fixtures
W6_SUITE=SpeechDemoUITests/W6UnifiedVideoUITests
W6_MEDIA=demo/iosApp/UITests/Fixtures/WeMM

run_w6_tests() {
  phase="$1"
  shift
  xcodebuild test-without-building \
    -xctestrun "$W6_QA_DIR"/build/Build/Products/SpeechDemo_iphoneos*.xctestrun \
    -destination "id=$DEVICE_ID" -parallel-testing-enabled NO \
    -resultBundlePath "$W6_QA_DIR/$phase.xcresult" "$@"
}

run_w6_tests empty "-only-testing:$W6_SUITE/testMissingModelAndPickerCancellation"
```

真机步骤将权重和三个测试文件放进夹具 App 的 Documents，在 Files 中使用「W6 Fixtures」。当前真机页面验收待执行。导入期间需同时存放来源与 ModelStore 中的模型，两份合计约 6.36 GB。

```sh
stage_w6_inputs() {
  xcrun devicectl device copy to --device "$DEVICE_ID" \
    --domain-type appDataContainer --domain-identifier "$W6_APP" \
    --source "$WEMM_WEIGHTS" --destination "Documents/W6 Model Import" || return 1
  for clip in w6-yuv-clip-01.mp4 w6-yuv-clip-02.mp4 w6-clip-01.mp4; do
    xcrun devicectl device copy to --device "$DEVICE_ID" \
      --domain-type appDataContainer --domain-identifier "$W6_APP" \
      --source "$W6_MEDIA/$clip" --destination "Documents/$clip" || return 1
  done
}

stage_w6_inputs && run_w6_tests import "-only-testing:$W6_SUITE/testImportModelFolderAndRestore"
```

```sh
run_w6_tests movies \
  "-only-testing:$W6_SUITE/testRealMovieAnswer" \
  "-only-testing:$W6_SUITE/testCancelAfterVisibleTextAndAskAgain" \
  "-only-testing:$W6_SUITE/testLeavingDuringGenerationCancelsAndRestoresInput" \
  "-only-testing:$W6_SUITE/testUndecodableMovieReportsInputFailure"

for phase in empty import movies; do
  xcrun xcresulttool get test-results summary --path "$W6_QA_DIR/$phase.xcresult"
done
```

三个结果应分别执行 1、1、4 项，通过数等于执行数且跳过数为零。

## 在专用模拟器运行

将构建 destination 改为 `generic/platform=iOS Simulator`，运行时选取同一构建目录的 `SpeechDemo_iphonesimulator*.xctestrun`。`DEVICE_ID` 应取自本轮新建并启动的专用模拟器。先运行空模型测试，再把两份权重和三段素材放入 Files 的「我的 iPhone / W6 Test Files」。iOS 27.2 模拟器实测使用以下来源目录。请在同一个 shell 中设置前述变量并运行。

```sh
stage_w6_simulator_inputs() {
  w6_groups="$(xcrun simctl get_app_container "$DEVICE_ID" com.apple.DocumentsApp groups)" || return 1
  w6_group="$(printf '%s\n' "$w6_groups" | awk -F '\t' '$1 == "group.com.apple.FileProvider.LocalStorage" { print $2 }')"
  [ -n "$w6_group" ] && [ -d "$w6_group/File Provider Storage" ] || return 1
  w6_source="$w6_group/File Provider Storage/W6 Test Files"
  mkdir "$w6_source" && mkdir "$w6_source/W6 Model Import" || return 1
  for weight in language.gguf mmproj.gguf; do
    cp -c "$WEMM_WEIGHTS/$weight" "$w6_source/W6 Model Import/$weight" || return 1
  done
  for clip in w6-yuv-clip-01.mp4 w6-yuv-clip-02.mp4 w6-clip-01.mp4; do
    cp "$W6_MEDIA/$clip" "$w6_source/$clip" || return 1
  done
}
```

运行 `stage_w6_simulator_inputs` 后依次执行上面的 import、movies 阶段。导入测试通过系统选择器选中 `W6 Model Import`，检查就绪状态，重新启动 App，再次确认可以选片。若要在宿主核对导入后的权重，等 XCTest 完成，再用 `xcrun simctl get_app_container "$DEVICE_ID" "$W6_APP" data` 读取此时的容器路径，模型在其 `Documents/wemm-model-store/installed` 下。
