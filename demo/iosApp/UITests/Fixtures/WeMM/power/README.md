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

在第二个终端，将 `POWER_RUN` 设为刚才输出的目录，再执行影片阶段。使用新的结果包名称。采集器从目标测试的启动日志定位 W6 Fixtures，只向其当前 PID 附加；若开始附加时首个「生成」已经发生，采集器会记录错误并退出。

```sh
phase="$(basename "$POWER_RUN")-movies"
run_w6_tests "$phase" \
  "-only-testing:$W6_SUITE/testRealMovieAnswer" \
  "-only-testing:$W6_SUITE/testCancelAfterVisibleTextAndAskAgain" \
  "-only-testing:$W6_SUITE/testLeavingDuringGenerationCancelsAndRestoresInput" \
  "-only-testing:$W6_SUITE/testUndecodableMovieReportsInputFailure" \
  > "$POWER_RUN/movies.log" 2>&1
```

## 对齐出答和采集窗口

四项页面测试通过且第一个终端的采集器成功退出后，在第二个终端执行。下面的脚本从同一份 trace 导出四张表，并核对设备、App 路径、PID 和两次出答的起止时间。

```sh
(
  set -eu
  fixture=demo/iosApp/UITests/Fixtures/WeMM
  cap="$POWER_RUN/capture"
  python3 "$fixture/read-ui.py" "$W6_QA_DIR/$phase.xcresult" "$DEVICE_ID" \
    > "$POWER_RUN/ui-observations.json"
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
