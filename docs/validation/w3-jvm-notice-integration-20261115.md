# W3 JVM 通知集成与独立最终 APK 保留

2026-11-15，用户指定免费 CBC hy3 后已实际推进生产通知资产。新增10份已核原许可/归属63392B及索引，共11资产72718B；原Kotlin、Gradle、依赖、版本/坐标/锁、模型、runtime AAR及原Release四成员不变。详细来源、选择/排除表、候选、夹具及验收见[材料目录](../../probes/sherpa-runtime-materials/jvm-notice-integration-20261115/README.md)。

- 首hy3调用5.676s虽CLI0/result success，但只有伪工具文字，保留并拒收。独立缩小任务28.537s交付JSON、独立APK任务68.832s交付三份实际候选文本；均无已观察的quota或真实工具事件，不将其当权限保证或未来配额保证。hy4free原429停线不变，无付费/自动fallback。
- 离线Debug/Release AAR、lint和隔离本地Maven发布通过：12.290s，435任务/29执行，其他up-to-date。Release AAR68052B/SHA `098bb261b5ef0da3c8a39b5fa3b61add2dc52e9a4b41c17675c9c9204c3f11ef`。
- 独立`com.android.application`经Maven坐标消费新SDK，实际解析无project dependency；10.904s、35任务全执行。最终APK127887599B/SHA `9409ebd8c4d722bf33cc02a2a4ba41b16dd89a3167b0d90cddd1665fbc11959c`。46非目录成员；11通知经Python/Info-ZIP逐字节核对，16SO与固定runtime四ABI相同。构建中的既有SDK XML和原库不可strip警告保留，不声称无警告。
- 原APK复制件的“删除一份通知”和“同长改单字节”均被校验器拒收，原APK保持；173原保护路径及161冻结文件通过。未安装/执行设备、开麦克风/AiAi或下载模型。

这只证明已知JVM子集的技术交付与保留。native通知、完整递归组成与所需全部修改说明、最终生产App采用及P4独立发布接受未关闭；P1–P4/R4/R5/W3仍open。无project依赖的测试APK也不等于真实生产App或设备验收，独立helper实例不自动成为完整独立发布审查。

原始提示、事件与本地构建日志：`.omx/artifacts/w3-p3-jvm-hy3-20261115T111653Z/`、`w3-p3-jvm-hy3-selection-20261115T111916Z/`、`w3-p3-apk-hy3-20261115T112302Z/`。二进制及负例留在`artifacts/verification/w3-p3-jvm-hy3-20261115/`，未向远端发布SDK或推送源码分支。
