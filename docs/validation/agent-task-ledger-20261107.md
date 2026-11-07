# AI助手任务台账 — 2026-11-07

本台账由主代理维护。状态取自真实进程/完整结果/验收收据，历史任务不因本表刷新而重发。
所有助手先看`docs/agent-pitfalls.md`；禁工具任务由父提供适用摘录。供应商输出不等于验收通过。

| 任务 | 助手及能力匹配 | 当前状态 | 范围/句柄/证据 | 下一步 |
| --- | --- | --- | --- | --- |
| F10经验曲线旧正文请求 | cbc hy4free，复杂统计纯函数候选 | terminal-unusable；1647.306秒自然结束，344064B JSON截断 | unified session52594 / PID19468；`.omx/artifacts/cbc-f10-empirical-curves-20261107/`；无工具/数据下载/阈值选择 | 旧请求不修JSON/不重发/不再轮询；用户后续明确改为直接文件交付，见下行，主代理不代写实现 |
| F10经验曲线直接文件交付 | cbc hy4free，用户新指定由Agent写文件 | completed；467.413秒自然终态，模块/测试原样集成；18定向、3110穷举核对及完整563tools通过 | `.omx/artifacts/cbc-f10-file-delivery-trace-fix-20261107/`；56完整JSONL事件/最终success，原RED/GREEN与父验收保留 | 不再轮询session98174；原DELIVERY“无权限失败”与实际一条Bash拒绝/缺文件Read不符，已另存父更正；文档与限定静态复核见下行 |
| F10 PAD接入梳理初稿 | cbc hy3，独立源码/接口关系梳理并直接写文档 | terminal-needs-corrections；106.841秒写出6675B文档，源快照未变 | `.omx/artifacts/cbc-pad-integration-map-20261107/`；初稿与父处置保留 | 已有复制/缺配置测试不能再当新缺口；raw score不能凭空包装成必选outcome的LivenessDecision；修正任务见下行，不轮询session2004 |
| F10文档定稿与限定静态复核 | cbc hy3，模块使用文档/PAD图修正/源码测试限定意见 | terminal-rejected；420.615秒，实际API/并列阈值/PAD路径有误，三文档未集成 | `.omx/artifacts/cbc-f10-docs-final-20261107/parent-assessment.json`；原文件另存rejected-candidate，进程成功不等于内容正确 | 不再轮询10691，不重复广泛文档任务；保留可用实现及563测试事实 |
| Android持续重开探针 | cbc hy4free，复杂有界Kotlin实现 | terminal-provider-error；931.375秒自然终态、empty_model_response，无文件 | `.omx/artifacts/cbc-identity-reopen-probe-20261107/`；独立新探针；20周期/至少10分钟，无自动任务超时 | 原始上游finish_reason=error保留，无父超时/已观察quota；不再轮询44550；用户选定agy免费接手，见下行 |
| Android持续重开探针接手 | agy gemini-3.8-flash-medium，用户明确选择免费路线 | running；PID19582/unified66400；任务目录写入正负检查通过，尚无交付 | `.omx/artifacts/agy-identity-reopen-probe-20261107/`；accept-edits/sandbox，无耗时超时；CLI自身会话目录写入被隔离拒绝已观察 | 保留当前同次运行/错误，不重发或放行整个用户目录；主代理待实际文件后编译/真机/恢复 |
| Android持续重开汇总 | cbc hy3，严格纯函数与负向测试 | terminal-needs-corrections；351.851秒交付，父独立复现两个缺陷，未集成 | `.omx/artifacts/cbc-identity-reopen-reducer-20261107/parent-red.json`；后段1秒周期误接受，warmup峰值遗漏/输出schema错 | 原件保持，不再轮询47486；定向修正交回助手，见下行 |
| Android持续重开汇总定向修正 | cbc hy3，已复现两处错误的最小修正/回归 | completed；161.615秒原样集成；父15测试/221变异、完整578tools通过 | `.omx/artifacts/cbc-identity-reopen-reducer-fix-20261107/`；共享协议保持，不允许为迎合错误修改协议 | 新回归对原候选真实4fail，修正15GREEN；助手自行加cd使测试命令被拒已保留，实际测试由父执行；完整578/53.677秒、源码/九保护/lock保持 |
| F09远端消费者 | 主代理，既有工具的实际网络/构建/运行验收 | bounded-pass；新GET/344文件→全新双平台consumer编译→模拟器资源运行→精确卸载 | `artifacts/verification/f09-remote-consumer-20261107/summary.json`；787文件摘要、两App各24材料、九保护保持 | 实机资源/推理与其他生产门槛保持；同一物理Mac，不称另一机器通过 |
| cbc长输出诊断与后续交付规则 | 主代理，本地原字节/启动器核查，不调用模型 | completed；原启动器已重定向文件且无超时；2MiB合成链路逐字节通过，内部根因未确定 | `.omx/artifacts/cbc-output-diagnosis-20261107/diagnosis.json`；更新根AGENTS和踩坑记录 | 后续新复杂任务优先经真实沙盒限制的候选文件交付/短收据；尚未实测此方案，不算F10实现完成，不重发旧请求 |
| Nexus74材料核对器 | Copilot auto，个人额度的小工具 | candidate已由父修正并集成，32聚焦及545全tools通过 | `.omx/artifacts/copilot-nexus-notice-materials-20261107/`；40.77秒完整响应；实际`.6`73索引材料+index通过，保留父schema错误RED与修正 | 最终源码独立复核待下行任务；远端消费/许可仍未通过 |
| Roadmap当前表交叉审计 | cbc hy3，短一致性审查 | completed，父已处置 | `.omx/artifacts/cbc-roadmap-status-crosscheck-host-20261107/`；119.69秒/0工具；原受限启动失败在不带host的同名目录，未计模型失败 | 2条采纳、1条部分采纳；父额外发现已改当前表，不再运行此任务 |
| Nexus材料核对器最终审查 | Copilot auto，独立限定源码复核 | completed，25.75秒/0工具/0finding；父核对源码摘要保持 | `.omx/artifacts/copilot-nexus-notice-final-review-20261107/`；供应4份最终源码/测试及真实schema边界，只读结果 | 最终限定审查已记录，不再轮询session27684；不是整分支批准 |
| .6真实variant/消费者核查 | cbc hy3，短metadata/config审计 | completed，84.803秒/0工具；父逐项处置 | `.omx/artifacts/cbc-dot6-variant-audit-20261107/`；3份实际module原字节已对冻结计划摘要，含当前未运行消费者配置 | 12个variant逐字段对原metadata、3notices路径及既有property核实；期望清单已存，否决新缺失推断；不再轮询session37246 |

已知停止线路：原生审查429、Qoder403、dsh429、OpenCode401/free429不自动重试。
agy上次明确授权网络修复后的独立任务成功，可留作后续合适工作；本轮未重新调用。
Ollama已有本机模型可承担小型候选，但本轮未新发任务。免费优先；用户最新覆盖：每次付费cbc调用前先确认具体任务/模型/范围，不因耗时自动切换。

唯一获准延期项为低系统版本设备实机验证（当前F03，旧对话编号不一致）；F10授权校准/攻击数据、Nexus精确出口/本次上传范围属于本周待解决输入，不是新增豁免。完整拆分见 `.omx/plans/roadmap-critical-path-20261107.md`。持续执行期间每小时汇报真实增量；未配置跨会话后台推送。已完成的GitLab上传、本地SDK链接、普通导入/注册/清除不重复。
