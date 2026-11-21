# W3 完整终态文件驱动：本地验收

本地 `device_acceptance.py` 已接通 preflight、prepare、run、collect、collect-policy、restore；源码来自免费 CBC hy3 两个完整片段与六块精确订正，主代理审查后原字节集成。源 SHA-256 为 `7b0511326856ec0680853ba9a34a2dda55cfda4282e7407b3f953edcf231aee5`。

## 当前证据

- 现行完整 Python 验收为 **376 方法、62 文件 AST**：201 observation、18 current contract、157 terminal；旧冻结 120 文件、原失败报告/verdict、95 保护文件、81 准备源与两 APK 的摘要保持。
- 新增 launch 8、dispatcher 13、真实本地 fake-ADB 全链路 2，原四项 driver 路由检查也通过。六 action 共享同一 run，实际子进程等待 ACK、验证 finish、双读收取报告及 20 字节请求配置，最后恢复精确新 alias/stage 并回读普通 Demo。
- 资源超限故障真实触发 lifecycle 停止与 drain，尝试同 run 有界收取和独立恢复；原主异常及次生 notes 保留。lifecycle 与 restore 各自执行一次经所有权校验的 APP force-stop，facade 没有另加停止。
- 新 run 的全部固定输出和任意 `poll-sample-NNNNNN.json` 陈旧项在 transport 前拒绝；prepared/claims、精确整数预算、int64 时钟、安装 APK、普通 App/model、初始 2 GiB、用户 0、锁屏及精确 runner 检查保持。
- cleanup 为零参数且单次，`allRestored is True` 才确认恢复；false、异常或不确定性不重试。启动前未消费新 marker 时不做破坏性恢复。

完整结果见 `.omx/artifacts/w3-terminal-observation-20261112/driver-repository-acceptance/result.json`。两份独立 hy3 文本格式复核均已完整返回 `accept`，覆盖现行 16 个 Python driver/observation 模块；静态复核不代替真机证据。源码复核原件的 reader SHA 漏首字符，独立 parent 收据与实际输入均正确；原件保留，另一次 21.231 秒的窄修仅确认转录订正和原完整复核继续 accept，没有修改源码。

新 `diagnostic-frozen.json` 已实际建立并由 `verify_frozen_contract(ROOT, OUT)` 读回验证，覆盖 **140 文件**、81 准备源、16 现行复核模块、两 APK、生产保护、当前验收结果/父测试。freeze SHA-256 为 `f778ea664bd407007845e4035428e06262fe0531033735d9fefe961fd4afc4c0`。`run-plan.json` 和 `production-before.json` 均为新终态目录独立记录，未写 `device-before.json` 或 `run-started.json`，未消费 run。收据为 `driver-parent/freeze-verified.json`。

## 原始失败及父端订正

两份实现的初始自然终态分别为 300.327 秒和 217.714 秒；完整可解析 result 中提取候选。原 launch6 通过后，新增故障实际发现 `ctx['adb']` 漏传、消费计数 bool 等值/时钟越界、marker 读取中断覆盖主异常、preflight 本地绑定/输入和陈旧收取输出漏查。免费 hy3 独立窄修在 217.868 秒完整交付六块替换，每块旧串恰好匹配一次。

父夹具另补 `/data` 祖先映射、临时候选的不可变 FILE 路径和 `exec-in`，这些是夹具能力订正。完整回归首命令误选旧 artifact13 合同夹具，缺新增必需模块导致 2ERROR；原件保存在 `contract.stderr` 和 `initial-run-result.json`，现行 `probes/android-qwen-terminal/check_device_contract.py` 独立 18GREEN 后替代该旧入口，未修改 guard。最终 376 项由当前正确入口的实际输出组成。

两份原完整源码复核均自然退出0，但 raw262144B 在回显输入源码内截断，零完整 event、无 verdict，不能计通过。保留于 `driver-full-{source,parent}-truncated/`；各自在终态后独立改用同免费 hy3、禁用工具、CLI 文档支持的文本输出。文本交付实际分别在 377.573 秒和 130.534 秒完整返回 5576/4632 字节可解析 JSON，两份均 accept。该结果验证本次交付方式，不定位历史截断的内部原因。

复核中的低优先级意见保留并由主代理裁定：重复 import/缺少内部 ctx 时的错误消息不影响此实际接线；嵌套时钟保留外层 240 秒约束，不采用可能丢失该约束的 raw callback 建议；Demo 前台回读不成功仍须将恢复判为不完整；marker 34 字节有界读/33 字节精确匹配无需改动。不为非阻断建议重写已验逻辑。

## 验收边界

这些是本地 fake-ADB/源码证据，不是 TECNO、模型质量、原生 allocator policy effect 或 true peak 证明。固定 run `w3-qwen-terminal-20261112-01`、单文件 `fleurs-en_us-1776`、最多一 constructor/两 stream/两 decode、1.5 GiB RSS 停止门、512 MiB 可用内存门、120/240 秒预算不变；不重放旧 FILE/heap run，不开麦克风、AiAi 或下载。

最后一次实时 `adb devices -l` 为空，未做新真机操作。W3/R4/R5 仍 open；终态同 run 真机证据、完整 Android 质量/资源门及生产发布/再分发复审仍待完成。源码分支尚未 push。
