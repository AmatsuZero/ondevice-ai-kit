# F02 Nexus 匿名消费者验收准备

2026-11-07，Copilot 使用现有个人额度完成限定只读审查，47.217 秒自然结束；
实际模型 `gpt-6-luna`，完整 JSON、无 session error 或工具事件，七份输入摘要保持。
原文与父处置：`.omx/artifacts/copilot-f02-remote-consumer-audit-20261107/`。
这是准备方案审查，没有执行 Gradle、取件、上传或重新查询 Nexus。

现有 `.6` 消费者在
`artifacts/verification/f02-nexus-dot6-consumer-20261106/consumer/`；
旧 `command.json` 和冻结计划保持原样。当前结论为 **prepared-not-run**。

| 项目 | 当前证据 | 执行前／执行后的要求 |
| --- | --- | --- |
| 出口和范围 | hosted 为候选；用户本人维护；精确仓库／本次上传范围待答复 | 对齐获确认的仓库、版本和115个路径；不把旧404或待办列表当批准 |
| Skiko 仓库隔离 | `exclusiveContent` 限定 `org.jetbrains.skiko`；版本及 notices 属性均为 `.6` | 保留配置原文；执行后核对实际组件、两 iOS variant、notices classifier 和制品来源 |
| 匿名与缓存 | 旧命令未指定独立 Gradle user home；`authentication:null` 和 `--no-daemon` 不足以证明匿名或冷缓存 | 为本次消费者使用全新 dependency/project cache；排除继承 init scripts、属性及凭据；记录处理方式，不输出秘密；仅冷缓存实际取件成功后才记录远端通过 |
| wrapper 来源 | 复用已保留 sdk-consumer wrapper；它从自己的 `APP_HOME` 加载 wrapper JAR，properties 含 Gradle 9.4.1 分发摘要 | 冻结脚本／JAR／properties 摘要、工作目录、完整参数及实际 Gradle/JDK 版本；外部 wrapper 路径本身不是错误，无需为此重新生成 wrapper |
| 逐项远端字节 | 本地计划／归档一致性已验证；尚无实际 GET 收据 | 获授权发布后匿名 HTTPS 取件；逐项保存请求／最终 URL、响应、实际字节数和 SHA256；拒绝摘要不符及未裁定重定向；不以 HEAD 代替 GET |
| 许可材料保留 | collector 只提取 notices 路径，未在命令中核验内容 | 比较实际取回 notice/source JAR 与计划摘要；对 collector 输出按 `.6` 的74份材料和索引逐文件核验，不能仅看任务成功 |
| 消费者编译 | 已准备双 iOS framework 链接任务，尚未运行远端消费者 | 固定源码／依赖输入，保留真实编译日志、产物及摘要；本地 `.6` 编译结果不能替代此项 |

Copilot 将“出口待定”列为 finding，父处置保留为已知执行门槛；不是新发现的产品缺陷。
其两条缓存／匿名意见合并；外部 wrapper 的 high 分级未采纳，按上表补来源收据即可。
选中组件／variant 与许可摘要的要求采纳。App 最终资源保留和生产许可／资源预算继续分列，
不因本消费者收据或本次静态意见而通过。新准备细节见
`artifacts/verification/f02-nexus-consumer-audit-20261107/preparation.json`。

此清单没有使用发布凭据、不改生产依赖或旧冻结文件，也不重问既有待答复项。
实际发布与远端取件继续依照 [Nexus 交付准备](f02-nexus-delivery.md) 的现有边界。
