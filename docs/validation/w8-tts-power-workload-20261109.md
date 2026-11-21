# W8 TTS 实际负载 Power Profiler — 2026-11-09

当前：**实际负载、trace时窗和恢复已完成限定验收，29项父核验通过**。不是焦耳／瓦特／生产能耗预算通过，W8仍open。

隔离候选由免费hy3直接交付，真实墙钟订正由另一免费hy3交付；父核对唯一可逆替换、实际unsignedbuild11.787s与签名/MachO段不变。新别名`io.ondeviceai.probe.w8ttspower20261109`，固定公开文本，一次预热、两次测量speak、一次close、单cycle至少30秒含pacingidle；未重跑已验收20cycles资源批次，无麦克风/照片/音频落盘。

首次点击ref `e3`格式被mobile工具拒绝，未启动负载；45s空闲trace完整保留。随后CoreDevice仍列7001但xctrace两次exit21 Cannot find process；精确重启本次新别名后PID7006才附加成功，不归因设备App崩溃或模型429。成功录制后只发一次`@e3`，UI真实Completed。最终trace窗口12:30:16.518–12:31:47.758 +08，91.240580秒；TOC绑定PID7006和本次App真实安装路径。

179行ProcessSubsystemPowerImpact与91行SystemPowerLevel离线导出成功。raw表CPUimpact最大14、display849；含-1e-9占位，系统powerusage91行全0。它们是原始模型化指标，不能换算焦耳/瓦特或当完整可用能耗。GPUimpact的0/占位也不是GPU预算通过。

报告`Documents/w8ttspower/w8-tts-power-report.json`回读命令在执行前被**自动审批服务429**拦截；不是provider额度、设备失败或真实负载失败。已请用户确认仅这一次稍后重试，无自动重试/替代通道绕过。当时保留新别名及设备报告，避免删掉唯一终态；该等待状态已被下节的成功回读与恢复取代。

证据：`artifacts/verification/w8-tts-power-workload-20261109-1214/`的source/build/signature/协议/one-successful-trigger/trace/toc/raw-table-description/collect-approval-failure/pending-report-disposition文件。验收要求包括两次SHA一致、真墙钟与单调时钟差≤250ms、全负载在trace内，并在只删新别名后核44原App记录、返回Combined；这些恢复步骤已实际完成，测不到的指标继续明确保留。

## 用户授权重试后的实际验收

用户明确“允许都重试”后，唯一一次报告回读重试成功；后续独立第二回读逐字节相同，2942B/SHA`e8d676362d4c21647dd77076fd38dd4b7c2d67bb2b2ba5f4b4bd5ede1aaac3a6`。前段pending描述保留为历史失败过程，以本节为准。

runUUID373FC900-B5DB-4159-95BB-116212A0CD4D/PID7006，1cycle/2success，实际31.875210667s含20.521s idle；两次测量speak共5.539403542s，另有一次排除预热。497RSS样本/max112705536B。真墙钟负载12:30:31.521606–12:31:03.396482 +08，完整包含在12:30:16.518–12:31:47.758 trace内；墙钟减单调差-0.334644341ms，低于预冻结250ms拒绝线。PID/安装路径/语音/locale/公开固定文本/两回读/最终close/RSS及生产保护29checks通过。

179/91行原始导出带上述缺值局限，未包装成完整有效能耗值或GPU/预算通过。只卸本次新alias，44原App records完全相同并已返回Combined，mobile重新观察前台确认。三处精确inactiveXcodecache清133251389名义B，3011保留文件SHA保持，所有trace/源码/签名App/失败原件保持。实际并发free+133623808B，不作排他归因。
