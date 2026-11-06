# Android SR 文件释放失败修复（2026-11-07）

真实 `LiteRtVideoSuperResolutionBackend` 的输出租约及失败处理原先忽略两次 `File.delete()` 的返回值。
当输出或其专属目录仍存在且无法删除时，释放仍返回成功；`AndroidVideoBridge.releaseOutput` 会据此移除句柄，无法按原有失败重试协议再次释放。

## 复现与修复

两个临时JVM探针直接构造原编译产物中的匿名输出租约，不执行Android codec或模型。
分别用非空输出目录、含其他条目的专属目录触发真实删除失败；原实现两项均真实RED：预期IOException却正常返回。
该反射探针只用于固定旧源码的诊断，已从正式测试树移走并冻结在证据目录；不以编译器生成类名作为长期测试协议。

现在两条路径共用一个小型内部函数 `deleteSrOutput`：

- 使用 `Files.deleteIfExists`，缺失条目可重复释放，存在但删除失败时抛出I/O错误。
- 分别尝试两个精确拥有的条目，保留后续错误为suppressed，不递归删除其他条目或整个outputRoot。
- 失败处理中的清理错误保持主错误，并附上原处理错误；符合既有“清理失败可以覆盖取消，避免静默丢失所有权”的约定。
- 已交付输出仍可在client关闭后释放；原bridge在收到释放错误时保留句柄，解除失败原因后可重试，无须修改bridge。

公开API、codec/模型/JNI、准入与同步drain保持。没有把取消时仍运行的native任务提前放行。

## 实际验证

最终SR模块10项JVM测试实际执行通过，其中6个新永久测试覆盖删除/幂等、缺失输出、双错误/重试、其他条目保留、第二路径仍尝试及处理错误保留。
`-PenableLiteRtSr=true` 的Demo56项实际执行通过。video18为缓存通过；SR10在消费者轮也缓存，不重复计入执行数。
默认消费者轮亦为缓存通过，不能把它当作启用SR的证明。
SR lint0error、2项既有warning：`InlinedApi`（原codec的API29常量）和`NewerVersionAvailable`。

cbc hy3于106.301秒返回完整候选，主代理修正了第二错误仅进消息、测试目录未清理等问题，未原样接受。
本机Ollama qwen3-coder:30b对最终三个文件9.108秒给出零finding；摘要匹配，但回复理由很简短，只计限定静态意见，不计整分支或发布批准。

Context7成功解析官方Java文档库，但正文请求fetch failed，未作为内容证据且未循环重试。
改核对本机官方JDK21源码Javadoc及Android36 SDK API版本元数据：`Files`/`deleteIfExists`可用于本模块minSdk26；非空目录、缺失和符号链接行为按原文记录。
这不替代API26真机验收。

## 证据与边界

验证目录：`artifacts/verification/sr-output-cleanup-20261107/`，只读 `freeze.py --verify`。
助手原文/处置：`.omx/artifacts/cbc-sr-output-cleanup-20261107/`、`.omx/artifacts/ollama-sr-cleanup-final-20261107/`；各自ask索引保留。
9保护路径和生产Apple lock保持，无设备、模型下载、采集、上传、签名或依赖变化。
当前新源码尚无真机重测结论；既有固定APK设备结果仍只对应其历史候选。
本轮修复一处错误释放成功的工程缺陷，未关闭F06/F11原质量/资源门槛，10/12、P4/P11/release not-ready保持。
