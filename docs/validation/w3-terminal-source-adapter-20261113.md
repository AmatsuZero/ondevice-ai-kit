# W3 文件驱动源码适配验证（2026-11-13）

免费 CBC hy3 的 `terminal_source_adapter.adapt_source` 已原样集成。输入必须是与既有不可变 FILE 驱动 SHA 一致的精确 str；它只改新 alias/run/report/OUT/runner、单样本两次解码断言和逐调用冻结回调，并以 dispatcher 替换旧 prepare/run/collect/restore 分支、移除模块启动入口。传输、模型摘要、普通App状态和身份等辅助函数的 AST 保持一致。导入、适配以及 `__name__='__main__'` 下求值均不启动设备操作。

父端8方法先取得真实缺实现 RED，再在隔离候选与实际 fake-ADB 子进程上通过：4次身份读取各自先调用守卫；守卫原错误阻止首进程；旧65样本计划在任何传输前失败；生成身份在外层覆盖前已是新身份；类型/源漂移拒绝及纯确定性适配通过。

父端另复现完整合同可同时从 files 与两份复核摘要中遗漏新模块。免费 hy3 单组精确替换把该依赖加入 `DRIVERS`，使 `REQUIRED` 和 `REVIEWED` 同时强制包含。真实 RED→14守卫方法GREEN，不能仅把依赖作为 files 的可选条目。独立 supplied-source 复核 accept、无finding；未运行测试的限制和实际输入摘要保留。

集成后新鲜检查：观察层 **201 / 28.651s**、守卫 **14 / 1.627s**、终态层 **59 / 25.352s**，共 **274项通过**；43个Python文件AST通过。旧120冻结文件、原失败报告/裁定、95条其他dirty路径、81份准备源和两APK无漂移。机器摘要见[同名JSON](w3-terminal-source-adapter-20261113.json)；原始请求/候选、8RED、必需依赖RED、隔离和仓库检查在 `.omx/artifacts/w3-terminal-observation-20261112/source-adapter-*`。

`_terminal_before_transport` 和 `_terminal_dispatch` 仍由未来完整 `device_acceptance.py` 提供。这一纯源码片段不实现 durable 所有权日志、prepare/collect/policy 或部分prepare恢复；原传输函数也不能替代这些门槛。完整源码/父端复核、新冻结和同run真机仍缺。当前ADB为空，本次零新安装/推理/麦克风/AiAi/下载，旧consumed run不重放，源码分支未push；W3/R4/R5仍open、goal保持active。
