# W3 ADB 主机 USB 后端一次恢复尝试

一次主机 libusb 后端尝试未恢复 TECNO 连接；已恢复 NATIVE 后端。新 terminal run 没有 prepare、安装、模型传输或解码，W3/R4/R5 仍 open。

本机 ADB 37.0.1/build15733141 的 `server-status` 记录 NATIVE 后端；Mac IORegistry 连续三次仍识别同一 TECNO USB 序列号和同一注册对象，但 ADB 清单为空，主机日志持续 `usb_osx.cpp usb_read/usb_write failed with status: e00002ed`。USB 枚举不等于 ADB 可用；这些观察不足以确定是线材、手机、系统驱动或授权状态导致。

[AOSP 官方手册](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/docs/user/adb.1.md)说明 `ADB_LIBUSB=1` 选择 libusb、`0` 选择内建 USB 后端。本机二进制亦包含该变量及 libusb 实现。执行前核实5037端口只有一个 ADB server监听者，按 PID 的 `lsof` 证明实际二进制是现有 platform-tools/adb，没有其他 ADB 客户端。没有改凭据、保存配置、手机调试模式、普通 App或模型。

顺序执行一次停止 NATIVE、以仅该进程的环境变量启动 libusb；`server-status` 明确回读 `usb_backend: LIBUSB`。三次有界清单与型号查询均失败：清单为空、指定 TECNO `device not found`。随后停止该后端，以仅该进程的 `ADB_LIBUSB=0` 恢复，回读 `usb_backend: NATIVE`。恢复后清单仍空，现有 `adb mdns services` 也为空；没有扫描网络、启用手机 TCP/IP 调试或重新配对。

执行前首个纯主机 guard错误地要求 `ps` 使用二进制绝对路径，实际 server argv以 `adb` basename 开头，导致断言停止，零服务/设备变更。原失败保留；之后以5037监听 PID、精确 argv和 `lsof -d txt` 实际二进制路径核验身份，才执行上述单次尝试。没有因 guard失败重复设备任务。

原始证据：`.omx/artifacts/w3-adb-libusb-20261114/`，包含官方手册原文/摘要、实际后端切换与恢复结果、失败 guard纠正和恢复后状态。USB枚举、NATIVE原日志摘录保留于 `.omx/artifacts/w3-ort-all-abi-20261114/`。没有新构建或待轮询进程，未消费当前冻结 run；不反复重启 server或重放旧推理。下一步只在外部连接状态改变后重新执行完整前置检查。
