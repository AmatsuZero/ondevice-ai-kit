# F02 Nexus GET 收据与留存字节核对

2026-11-07，按用户要求增加 Nexus/Copilot 分工。Copilot 使用现有个人额度，
`auto` 实际选择 `gpt-6-luna`，84.31 秒自然完成两个文件候选，完整结果、0 session error、
0 工具请求/执行事件；cbc hy3 的独立12项边界矩阵65.496秒完整返回、0工具块。
两条线路本次真实可用，不保证未来额度；没有监督者任务超时或重复请求。

新增 [离线核对器](../../tools/nexus_get_receipts.py) 比较既有发布计划、未来 GET 收据与实际
留存文件：精确条目覆盖、请求/最终URL、GET/200、声明无鉴权/无重定向、真实大小与流式SHA256。
使用现有计划校验，不修改冻结 `.6` 计划或归档。逐层 `dir_fd`/nofollow 打开目录；拒绝链接、
FIFO/特殊文件、缺失平台能力、读取中修改及JSON重复键/非有限数值；输入只读，错误不回显内容。

```sh
rtk proxy python3 -m tools.nexus_get_receipts PLAN.json RECEIPTS.json BODIES_ROOT
```

`RECEIPTS.json` 的根字段严格为 `schemaVersion:1`、`kind:"nexus-get-receipts"`、
与计划一致的 `version`、`fileCount` 和 `entries`。每条严格包含 `path`、`requestUrl`、
`finalUrl`、`method:"GET"`、`status:200`、`authenticated:false`、`redirects:[]`、
`bytes`、`sha256`；文件存放在 `BODIES_ROOT/<计划path>`。整数不接受bool或等值float。
检查成功仅为 `pass-bounded`：`networkPerformedByThisTool`、`anonymousExecutionVerified`、
`remoteConsumerValidated`、`uploaded` 均为false。声明与字节一致不能独立证明真实匿名请求来源。
未来实际传输需另留调用/配置/HTTP收据，Gradle variant、来源、编译和74份许可材料仍分别验收。

## 验证与父处置

主代理先写11项验收测试，真实RED为核对器缺失，再集成候选。
候选测试中的变更场景向只读验证fd调用 `pwrite`，泛化ValueError误将EBADF当作检测成功；
补“实际字节已改变”断言后真实RED。现以独立可写句柄改同一inode，断言原读取内容、
实际修改内容和拒绝结果，确保覆盖fd签名检查；原候选与两份失败日志保留。

最终新增20项测试，与原9项计划/归档测试合计29项聚焦通过；完整tools **530/530**，
49.150秒，所有tools源码前后摘要一致，9个保护路径和生产Apple lock保持。三文件py_compile通过。
cbc矩阵作为边界检查输入；它的“匿名”描述未提升成实际传输证明。无Nexus请求、凭据使用、
上传、模型下载或生产切换，未重复旧404/归档核验。

本地原始证据：`artifacts/verification/f02-nexus-get-receipts-20261107/`。
助手原文与父处置：`.omx/artifacts/copilot-f02-get-receipts-candidate-20261107/`、
`.omx/artifacts/cbc-f02-get-receipts-cases-20261107/`；另各有 `ask-*-f02-get-receipts-20261107.md`。
这些ignored证据不随普通Git clone分发；源码与测试随本分支提交。

这关闭离线收据核对工具子项，F02/P4/P11、精确hosted/本次发布范围、远端消费、
生产资源及许可适用性门槛继续保持原状态。见[消费者清单](f02-nexus-consumer-checklist.md)。
