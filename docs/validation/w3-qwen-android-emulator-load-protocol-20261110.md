# W3 Qwen Android 模拟器单次 load-only 补充合同

用户不在 TECNO 旁，要求以模拟器推进可执行验证。此批独立于未执行的真机 run，
仅验证同一 Android/JNI APK 的一次构造、释放与报告流程；不会关闭真机质量或资源门。

- 新建任务专用 AVD `W3_Qwen_Load_20261110`，固定端口 5576、serial `emulator-5576`。
  使用本机既有 Android 36/default/arm64-v8a revision2 镜像、硬件加速、4GiB guest RAM、2 CPU。
  独立 AVD/user/emulator home，无镜像下载、不修改已有 Medium_Phone AVD。关闭音频、两摄像头和快照。
- 使用与真机准备相同的两份 APK、sourceFingerprint 和六份模型文件，不改 Kotlin 或 native 库。
  另建 evidence root `artifacts/verification/w3-qwen-load-emulator-20261110/`，
  runId `w3-qwen-load-emulator-20261110-01`；真机 run 的独占 reservation 保持未使用。
- 父脚本仅替换 evidence root、目标 serial、预期模拟器 identity、runId 和恢复入口。
  加验 `ro.kernel.qemu=1` 及精确 AVD 名称，防止把本批提交至实体手机。
- 保持原六文件真实 hash 校验、零 stream/decode/麦克风/联网请求、单次构造与显式 release、
  报告独占及双读、普通包/模型清单前后核对。纯净 AVD 没有普通 Demo 时恢复 Launcher。
- 同样记录 guest 内精确 PID/startTicks/RSS/MemAvailable；RSS>1.5GiB、可用内存<512MiB、
  超120s或观测失败即停止本次，不自动重试。不把虚拟机分配的4GiB记为手机预算通过。
- 收集同 run 的终态或保护中止报告，核对实际动作计数和包/模型摘要；只卸本次两 alias、
  清理 manifest 绑定的 staging，关闭本任务模拟器并保留原始日志/报告/配置。

成功只证明所列模拟器配置的加载/释放和观测结果。TECNO/API35、整片两阶段质量、warm RTF、
持续资源、true peak、GPU/能耗和最终发布仍另验；不放宽1.5GiB、不以本批替换真机证据。

官方工具说明已通过 Context7 查阅：[avdmanager](https://developer.android.com/tools/avdmanager)、
[独立环境变量](https://developer.android.com/tools/variables)、
[Emulator 命令行](https://developer.android.com/studio/run/emulator-commandline)。
本机 `-help-environment`、`-help-no-audio`、`-help-no-snapshot`、`-help-port` 与 `-accel-check`
均成功；启动前确认端口空闲，启动后确认已完成 boot、API36、arm64-v8a，再进入准备。
