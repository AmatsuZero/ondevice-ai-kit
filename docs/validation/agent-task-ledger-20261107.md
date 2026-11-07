# AI助手任务台账 — 2026-11-07

本台账由主代理维护。状态取自真实进程/完整结果/验收收据，历史任务不因本表刷新而重发。
所有助手先看`docs/agent-pitfalls.md`；禁工具任务由父提供适用摘录。供应商输出不等于验收通过。

| 任务 | 助手及能力匹配 | 当前状态 | 范围/句柄/证据 | 下一步 |
| --- | --- | --- | --- | --- |
| F10经验曲线 | cbc hy4free，复杂统计纯函数候选 | terminal-unusable；1647.306秒自然结束，344064B JSON截断 | unified session52594 / PID19468；`.omx/artifacts/cbc-f10-empirical-curves-20261107/`；仅`tools/identity_calibration_curves.py`及测试候选，无工具/数据下载/阈值选择 | 无完整assistant/result/候选；父接手原实现，不修补JSON、不重发，不再轮询session52594 |
| Nexus74材料核对器 | Copilot auto，个人额度的小工具 | candidate已由父修正并集成，32聚焦及545全tools通过 | `.omx/artifacts/copilot-nexus-notice-materials-20261107/`；40.77秒完整响应；实际`.6`73索引材料+index通过，保留父schema错误RED与修正 | 最终源码独立复核待下行任务；远端消费/许可仍未通过 |
| Roadmap当前表交叉审计 | cbc hy3，短一致性审查 | completed，父已处置 | `.omx/artifacts/cbc-roadmap-status-crosscheck-host-20261107/`；119.69秒/0工具；原受限启动失败在不带host的同名目录，未计模型失败 | 2条采纳、1条部分采纳；父额外发现已改当前表，不再运行此任务 |
| Nexus材料核对器最终审查 | Copilot auto，独立限定源码复核 | completed，25.75秒/0工具/0finding；父核对源码摘要保持 | `.omx/artifacts/copilot-nexus-notice-final-review-20261107/`；供应4份最终源码/测试及真实schema边界，只读结果 | 最终限定审查已记录，不再轮询session27684；不是整分支批准 |
| .6真实variant/消费者核查 | cbc hy3，短metadata/config审计 | completed，84.803秒/0工具；父逐项处置 | `.omx/artifacts/cbc-dot6-variant-audit-20261107/`；3份实际module原字节已对冻结计划摘要，含当前未运行消费者配置 | 12个variant逐字段对原metadata、3notices路径及既有property核实；期望清单已存，否决新缺失推断；不再轮询session37246 |

已知停止线路：原生审查429、Qoder403、dsh429、OpenCode401/free429不自动重试。
agy上次明确授权网络修复后的独立任务成功，可留作后续合适工作；本轮未重新调用。
Ollama已有本机模型可承担小型候选，但本轮未新发任务。免费优先；不因耗时切换有限付费模型。

延期/外部依赖不派发伪完成任务：最低系统缺设备、F10缺授权校准/攻击数据、Nexus精确出口/本次
上传范围既有问题仍待答复。已完成的GitLab上传、本地SDK链接、普通导入/注册/清除与设备推理不重复。
