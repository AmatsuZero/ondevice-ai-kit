# Android 进程资源观察器（W8）

这是面向一个已运行、已选定的 Android 进程的 Python 标准库 API，不是 CLI。
实现及测试：[android_process_observation.py](android_process_observation.py)、
[test_android_process_observation.py](test_android_process_observation.py)、
[test_android_process_observation_identity.py](test_android_process_observation_identity.py)。

## 测试与调用

在仓库根目录运行现有测试：

```sh
rtk proxy python3 -m unittest discover -s probes/android-process-observation -p 'test*.py' -v
```

API 使用示例（将所有占位值替换为操作者已核实的目标；不得自动发现设备或进程）：

```python
import importlib
import sys

sys.path.insert(0, "probes/android-process-observation")
observe = importlib.import_module("android_process_observation").observe

receipt = observe(
    adb_serial="EXPLICIT_SERIAL",
    package="io.ondeviceai.example",
    pid=12345,
    process_name="exact-kernel-comm",
    duration_s=30.0,
    interval_s=1.0,
    output_path="/absolute/new/observation.json",
)
```

参数必须来自实际目标：明确的 serial、允许范围内的 package、正整数 PID、`/proc/<pid>/stat`
中精确的 kernel `comm`，以及新的输出路径。先由操作者选定并确认目标进程；观察器只读取
`/proc/<pid>/stat` 和 `/proc/<pid>/status`。宿主进程以 `shell=False` 和 argv 列表执行
`adb -s <serial> shell run-as <package> cat /proc/<pid>/<file>`；设备命令使用
`adb shell run-as <package> cat`。不进行 shell 插值或 `eval`。模块没有 `main()`，不要臆造
`--flag` 命令行用法。

## 身份与收据

绑定时记录 `(package, pid, comm, starttime)`。每个 status 样本前后各读一次 stat；
两侧身份都必须匹配。PID 复用、进程退出或字段不可用会产生失败终态；可疑样本不追加，
之前有效样本保留。缺失字段不按 0 处理。

输出以独占方式创建，已存在即拒绝覆盖。首次 proc 读取前写入并 `fsync` `running` 收据；
完成或失败时写入并 `fsync` 终态。采样仅包含 RSS、线程数和原始 CPU ticks。CPU ticks
不换算为时间或能耗；不测真实峰值内存、GPU、能耗或资源预算。

## 证据与限制

针对身份绑定的新增回归曾在旧实现上 4 项 RED，原有控制项通过；修正后现有加新增共
17 项 GREEN。该离线结果不证明任何设备采样已执行；实际设备采样仍未验证。

不启动 workload、安装 App、改权限/设置、触发录音或推理，也不读私有媒体；必须由外部
流程在既有 workload 期间显式调用。失败或完成的样本都只是指定进程、指定时段的采样，
不能据此声称峰值/GPU/能耗/预算通过。
