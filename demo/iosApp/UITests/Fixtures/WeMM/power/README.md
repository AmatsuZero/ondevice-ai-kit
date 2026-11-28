# 测量 W6 短答期间的整机耗电

在电池供电、无线调试可用的 iPhone 上，Power Profiler 可以和已有的四项影片页面测试同时运行。这里对齐的是 `testRealMovieAnswer` 中两次点击「生成」到页面确认回答的窗口，包含 UI 自动化等待。记录当次实际供电方式。USB 或 MagSafe 充电期间的读数不能用于评估电池耗电。

## 随影片用例录制

沿用[页面验收](../README.md#按模型状态运行)里的 `DEVICE_ID`、`W6_QA_DIR`、`W6_SUITE` 和 `run_w6_tests`。确认夹具里已有模型和三段短片。为这次测试创建新的结果目录，保留已有签包。下面两个终端在同一仓库根目录运行，使用同一组变量。

第一个终端运行采集器。`POWER_RUN` 是本次新目录，将它的绝对路径传给第二个终端。看到 `watching` 表示采集器已开始等待目标测试。

```sh
POWER_RUN="$(mktemp -d "$W6_QA_DIR/power-XXXXXXXX")"
printf '%s\n' "$POWER_RUN"
touch "$POWER_RUN/movies.log"
python3 demo/iosApp/UITests/Fixtures/WeMM/power/capture.py \
  "$DEVICE_ID" "$POWER_RUN/movies.log" "$POWER_RUN/capture"
```

在第二个终端，将 `POWER_RUN` 设为刚才输出的目录。根据本次要测的范围，选择下面一组命令。采集器从目标测试的启动日志定位 W6 Fixtures 并向其当前 PID 附加。若开始附加时首个「生成」已经发生，采集器会记录错误并退出。

若出现 `Cannot find process for provided pid`，保留本轮的 `binding.json` 和退出日志，用 XCTest 会话日志核对该 PID 在附加时是否仍在前台。普通 Demo 和 W6 夹具都可能使用可执行名 `SpeechDemo`，仅凭名称无法区分同时运行的实例。[iPhone 无线附加记录](../../../../../../docs/roadmap/W8-resources.md#拔线后的两次短答)

在已复现的无线失败中，CoreDevice 可查询到空闲夹具的实际 PID，Power Profiler 与 Time Profiler 仍返回退出码 21。无线的 Activity Monitor 加 Power Profiler instrument 全进程录制则在 2 秒内断开。USB 对照中，按 PID 附加也返回 21。改用 `xcrun xctrace record --device "$DEVICE_ID" --template "Power Profiler" --time-limit 30s --output "$POWER_RUN/idle.trace" --launch -- io.ondeviceai.w6.unifiedvideo.fixtures` 后，已取得夹具的进程记录。采集新版 App 时先显式安装签包，让测试等待 Instruments 启动的夹具进入前台。从 trace 核对硬件 UDID、PID、安装路径，以及主可执行文件和 debug dylib 的 UUID。[USB 实测](../../../../../../docs/roadmap/W8-resources.md#usb-下的夹具进程记录)。[无线空闲对照结果](../../../../../../docs/roadmap/W8-resources.md#无线空闲附加诊断)

### 四项影片页面用例

```sh
phase="$(basename "$POWER_RUN")-movies"
run_w6_tests "$phase" \
  "-only-testing:$W6_SUITE/testRealMovieAnswer" \
  "-only-testing:$W6_SUITE/testCancelAfterVisibleTextAndAskAgain" \
  "-only-testing:$W6_SUITE/testLeavingDuringGenerationCancelsAndRestoresInput" \
  "-only-testing:$W6_SUITE/testUndecodableMovieReportsInputFailure" \
  > "$POWER_RUN/movies.log" 2>&1 &&
  python3 demo/iosApp/UITests/Fixtures/WeMM/read-ui.py \
    "$W6_QA_DIR/$phase.xcresult" "$DEVICE_ID" > "$POWER_RUN/ui-observations.json"
```

### 单项色序短答

此范围只执行 `testRealMovieAnswer`。读取器核对唯一通过的用例、目标设备及两次出答的完整活动序列，然后输出两个观察窗口。

```sh
phase="$(basename "$POWER_RUN")-short-answers"
run_w6_tests "$phase" "-only-testing:$W6_SUITE/testRealMovieAnswer" \
  > "$POWER_RUN/movies.log" 2>&1 &&
  python3 demo/iosApp/UITests/Fixtures/WeMM/read-ui.py --short-answers-only \
    "$W6_QA_DIR/$phase.xcresult" "$DEVICE_ID" > "$POWER_RUN/ui-observations.json"
```

## 对齐出答和采集窗口

所选测试的观察窗口已生成、第一终端的采集器成功退出后，在第二终端执行。下面的脚本从同一份 trace 导出四张表，并核对设备、App 路径、PID 和两次出答的起止时间。

```sh
(
  set -eu
  fixture=demo/iosApp/UITests/Fixtures/WeMM
  cap="$POWER_RUN/capture"
  xcrun xctrace export --input "$cap/workload.trace" --toc --output "$cap/toc.xml"
  for schema in SystemPowerLevel DeviceChargingState ProcessSubsystemPowerImpact device-thermal-state-intervals; do
    xcrun xctrace export --input "$cap/workload.trace" \
      --xpath "/trace-toc/run[@number='1']/data/table[@schema='$schema']" \
      --output "$cap/$schema.xml"
  done
  python3 "$fixture/power/bind_trace.py" "$cap" "$POWER_RUN/ui-observations.json" \
    "$cap/question-windows.json"
)
```

`question-windows.json` 的两个窗口以相对于 trace 起点的纳秒数表示。分别取各窗口和 `SystemPowerLevel` 的交集，保留系统耗电率原始值和单位 `%/hr`、覆盖时长及数据间隙。用 `DeviceChargingState` 检查充电区间是否重叠。窗口只有零值、缺数据或与充电重叠时，注明本次未取得可解释的电池耗电率。热状态和目标进程各子系统的相对影响另记。页面功能、窗口对齐和整机耗电率各自记录实测结果。
