# W3 终态观测启动与运行桥接（2026-11-13）

`terminal_device_lifecycle.py` 已按免费 CBC hy3 候选及精确补丁原样集成。它在 Popen 前建立共享240秒时钟及不可覆盖的消耗标记，以20秒预算取得真实 PID/startTicks、资源样本和首报告，随后把真实 phase/index/原始起点接入现有轮询。启动失败也不能重放同一 run。

终态 ACK 后只等待 instrumentation 至多15秒并读取本地证据；必须取得真实 Bundle 的两个 ACK true、终态原文 SHA 和 completed/-1 或 failed/0。`terminal-observed.json` 保存原始终态报告，`terminal-receipt.json` 另存绑定及样本。首报告可能仍在 running，因此不能以启动报告哈希替代终态哈希。failed 终态可被正确观察，但不构成识别质量通过。

错误路径保留原异常和发布不确定性，精确停止回调之后再终止 host 子进程、关闭日志并写失败收据。`KeyboardInterrupt`、`SystemExit` 及恢复中的第二个中断均不掩盖原错误。每次恢复 wait 最多5秒、至多两次；总等待预算最多10秒，另有回调开销，不宣称整个恢复只需5秒。

主代理先取得首稿、严格 presence 类型和中断恢复的真实 RED，按 hy3 补丁修正。集成后新鲜检查：观察层 **201 tests / 28.042s**，冻结守卫 **13 / 1.537s**，新增生命周期 **51 / 24.202s**，合计 **265 / 全部通过**；41个Python文件AST通过。新测试使用实际本地 fake-ADB 子进程与等待实际 ACK 文件的 instrumentation 替身，覆盖前置拒绝、身份/时钟/资源/报告/发布失败、启动消耗、退出码、完成字段、日志/恢复异常及首报告与终态哈希不同的路径。

独立 supplied-source 首审误将 observed 文件理解为 receipt，提出错误哈希替换。父端提供实际协调器、Kotlin finalization 和真实不同哈希测试后，hy3补充复核 **accept**，明确撤回原意见；只余非行为性的重复 import minor，本次不扩大修改。补充复核输入为50方法时的收据；随后父端追加 SystemExit/secondary KeyboardInterrupt 的真实 RED→GREEN，并在集成套件内取得51方法通过。复核未运行测试，其限制与原始 revise 均保留。

保护核验：旧120份冻结文件、原失败报告/裁定、95条其他 dirty 路径、81份准备源及两APK均无漂移。机器摘要见[同名JSON](w3-terminal-lifecycle-20261113.json)。原始候选、失败、精确替换、两轮复核、测试迁移收据及执行日志保留在 `.omx/artifacts/w3-terminal-observation-20261112/`。

这是 host 行为验收。完整 `device_acceptance.py`、每次ADB前的冻结/当前身份检查、prepare/collect/collect-policy、部分准备失败的所有权日志与精确恢复仍未实现；完整源码/父端复核、新冻结和同run真机继续。ADB当前为空，零新安装、推理、麦克风、AiAi或模型下载；旧consumed run不重放。源码分支未push，W3/R4/R5仍open，goal保持active。
