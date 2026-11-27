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

真机步骤将权重和三个测试文件放进夹具 App 的 Documents，在 Files 中使用「W6 Fixtures」。导入期间需同时存放来源与 ModelStore 中的模型，两份合计约 6.36 GB。

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

2026-11-27 在 iPhone 16 Pro Max（iOS 27.2.1）上实测，三个阶段分别通过 1/1、1/1、4/4 项，零跳过。从系统「文件」导入模型后，重启仍可选片。同一问题在两段反向色序影片上分别得到 `red, blue` 和 `blue, red`。手动取消或离开页面后都可再次提问，损坏影片显示解码错误并可重新选片。此次验收使用专用已签名夹具。该轮覆盖页面功能，开放问答质量与整机功耗另行验收。

## 从 XCTest 结果包读出页面耗时

在 Mac 上用 Xcode 命令行工具读取上面生成的 `movies.xcresult`。将 `DEVICE_ID` 设为测试目的地的 ID。读取器校验设备、四个影片用例及其通过状态，再输出六个观察窗口。每个窗口从点击「生成」开始，到 XCTest 确认文字后的下一项活动开始为止，包含 UI 自动化与轮询时间。

```sh
python3 demo/iosApp/UITests/Fixtures/WeMM/read-ui.py \
  "$W6_QA_DIR/movies.xcresult" "$DEVICE_ID"
```

在上述真机记录中，两次反向色序短答的页面观察上界分别为 5.619 和 5.680 秒。取消后续问为 5.601 秒，离页后续问为 5.575 秒。

同一真机结果包还保留了三个恢复过程。下面的上界从操作开始，到 XCTest 完成所列状态断言后的下一项活动开始，每项各观测一次。

| 操作 | 确认的界面状态 | 观察上界 |
| --- | --- | ---: |
| 长答中点击取消 | 阶段为 `CANCELLED`，无最终答案 | 1.530 秒 |
| 长答中返回，再进视频理解 | 阶段为 `CANCELLED`，无最终答案，原视频仍选中 | 5.806 秒，含往返导航 |
| 选择截断影片并点击生成 | 显示解码错误，选片按钮可用 | 1.539 秒 |

要复核恢复上界，可在 Xcode 中打开结果包，或用下列命令读取单个用例的活动。取消和离页分别从点击 `multimodal-cancel` 和 `unified-back` 算到添加 `first-visible-text` 附件；坏片从点击 `multimodal-generate` 算到 `Tear Down`。取两项活动的 `startTime` 之差。

```sh
xcrun xcresulttool get test-results activities --path "$W6_QA_DIR/movies.xcresult" \
  --test-id 'W6UnifiedVideoUITests/testCancelAfterVisibleTextAndAskAgain()'
```

## 测量短回答期间的 App 进程内存

2026-11-27 在 iPhone 16 Pro Max（iOS 27.2.1，USB）上复用了通过六项功能验收的 W6 Fixtures App 和已导入的 WeMM 模型。独立的 XCTest 先用红蓝影片预热一次，再用蓝红影片记录一次。两次都校验了答案、模型和后端。测量从点击「生成」开始，到页面出现回答为止，包含 UI 自动化等待。选片、键入问题、核对答案和截图均在测量区间外。

| XCTest 指标 | 第二段短回答（1 个样本） |
| --- | ---: |
| Memory Peak Physical | 394,973.304 kB |
| Absolute Memory Physical | 221,890.808 kB |
| Memory Physical（区间变化） | +36,077.568 kB |
| Clock Monotonic Time | 4.447 s |

内存指标绑定到 `io.ondeviceai.w6.unifiedvideo.fixtures` 进程。峰值约为 394.97 MB（十进制），仅覆盖这次预热后短答。冷启动、长视频、连续问答和整机能耗仍待测量。

复测需要手机有可用屏幕，夹具中已有两段影片及固定版本模型。常规测试默认跳过这项测量。在原构建产物旁另存开启测量的计划，以保留 App 和 Runner 的相对路径。以下命令沿用前文变量，并在新的结果目录中执行一次。

```sh
products="$W6_QA_DIR/build/Build/Products"
plutil -insert SpeechDemoUITests.EnvironmentVariables.W6_MEASURE_MEMORY -string 1 \
  -o "$products/memory.xctestrun" "$products"/SpeechDemo_iphoneos*.xctestrun
xcodebuild test-without-building -xctestrun "$products/memory.xctestrun" \
  -destination "id=$DEVICE_ID" -parallel-testing-enabled NO -collect-test-diagnostics never \
  "-only-testing:$W6_SUITE/testVideoAnswerProcessMemory" -resultBundlePath "$W6_QA_DIR/memory.xcresult"
xcrun xcresulttool get test-results metrics --path "$W6_QA_DIR/memory.xcresult" \
  --test-id 'W6UnifiedVideoUITests/testVideoAnswerProcessMemory()'
```

结果应为 1 项通过、0 项跳过。核对结果中的设备 ID，以及内存指标标识中的夹具 bundle ID。三个内存指标的原始单位都是 kB。

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
