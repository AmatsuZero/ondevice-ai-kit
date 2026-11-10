# W3 Qwen Android 模拟器 load-only 已验收

用户不在 TECNO 旁，要求模拟器替代推进；同一份隔离 Android/JNI 探针在新建 ARM64 模拟器上完成了
唯一一次模型构造与显式释放。**这只完成模拟器加载子项，TECNO/API35 的质量与1.5GiB资源门仍未验收。**

| 项目 | 实际观测 |
| --- | --- |
| 环境 | 既有 Android36/default/arm64-v8a revision2 镜像；独立临时 AVD，4GiB guest RAM、2 CPU、硬件加速；音频/摄像头/快照关闭，无镜像下载 |
| 源/包 | 与真机准备相同 APK、原生库、三 Kotlin 文件和 sourceFingerprint `605181e50ea9357c292be93e2bf634742c40566a13984a0ff0b83fdb8b9c0ecd` |
| 单次结果 | run `w3-qwen-load-emulator-20261110-01`，PID3397/startTicks51740，terminal/completed；构造尝试1/返回成功，release尝试1/成功 |
| 时间 | 设备构造2.419058418s、释放0.080721376s；父观测窗口4.711034625s。不是推理RTF或手机性能 |
| 采样 | 17个guest进程RSS样本，最大1293668352B（1.204823GiB）；guest MemAvailable最小1784631296B；没有触发1.5GiB/512MiB/120s停止门 |
| 真实采样间隔 | min0.173200s、median0.257056s、max0.328127s；不是连续观测或true peak |
| 零动作 | streamsCreated/nativeDecodeCalls/microphoneStarts/networkRequests均0；没有识别、录音或AiAi调用 |
| 报告 | 同run双读逐字一致，SHA `7585e920dcac279914a6b689b446d907455daff3ee1e159a10ee691ea48024e2`；六模型SHA前后匹配，teardown/destroyed ack存在 |
| 恢复 | 只卸本次两个alias、清manifest匹配staging、回Launcher；精确AVD关闭、host PID退出。实体TECNO及其run reservation未使用 |

父验收28项通过。进程退出时，最后一次 `/proc/PID/status` 读取已无该PID，且instrumentation已经结束；
原`terminalObservationGap`完整保留。随后取得completed双读报告与release ack，因此不将这次退出后缺页当作加载中采样失败，
也不声称覆盖进程最后一瞬、测得真实峰值或证明原生内存全部归还。

父脚本首preflight因AVD控制台返回CRLF而失败，发生于零安装/构造时。保存原始响应与脚本后仅改为逐行校验
精确AVD名和OK，三种错名/错误状态/多余行仍拒绝。模型/Kotlin/APK和停止阈值没有改变。

免费hy3对父脚本的supplied-context复核160.161s完成，无Critical/Important。父裁定纠正三处边界：
助手收到模拟器脚本实际字节但未独立核hash；排他文件不禁止只读preflight；`/proc/self`可读性不等于目标PID已实测。
实际运行随后取得同PID/startTicks的17个样本。原文与父裁定均保留，不冒充设备独立复验。

关闭后只删除七份本任务闲置虚拟磁盘/initrd，共3811372312名义B；522份保留文件逐SHA复核，
普通AVD未被操作。未保存其运行前配置SHA，故只记录当前SHA，不声称普通AVD逐字前后证明。
全部配置、源码/APK、原始双读/采样/错误、helper与清理收据仍在本机证据目录。

依据：[事前合同](w3-qwen-android-emulator-load-protocol-20261110.md)、
[原始设备报告](w3-qwen-emulator-load-report-20261110.json)、
[完整父验收收据](w3-qwen-emulator-load-accepted-20261110.json)、
[复核原文](w3-qwen-emulator-load-review-raw-20261110.md)、
[父复核裁定](w3-qwen-emulator-load-review-20261110.json)。
本机原件：`artifacts/verification/w3-qwen-load-emulator-20261110/`。

下一项可继续设计 Android 两阶段文件识别与资源测量；本批没有创建stream，不能记为该项通过。
TECNO实体结果、真实噪声质量、完整两阶段成本、持续资源、GPU/能耗和最终发布仍分别保留原门槛，W3保持open。
