> 2026-11-15 后续说明：本文“当前”指当时 `a18292ac` / `6de95f56…` 的历史本地候选，不代表最新 HEAD。免费 hy3 已补做限定供应文本复核并接受该历史增量；[复核及处置](w3-sdk-binding-hy3-review-20261115.md)保留未执行范围及旧 hy4free 429。最新 native 通知候选为 `51b705c` / `5ae065d2…`，见[本轮验收](w3-native-notice-integration-20261115.md)。原材料和旧失败收据不改，完整 P1–P4/R4/R5/W3 仍 open。

# W3 当前 SDK 发布候选与独立消费者绑定

本轮补充实际 SDK 消费证据：在当前干净的相关源码上，复用已有 Gradle 发布和独立消费者任务，将五个 SDK 模块发布到新的工作树内 Maven 仓库，再由 `examples/android-speech` 真实解析、编译。没有改生产源码、依赖、模型或全局 Maven 仓库，也未进行远端 SDK 发布或设备执行。源提交为 `a18292ac56e7c694fd2e7efb2550864a0b3ef21e`。

| 边界 | 实际结果 |
| --- | --- |
| SDK 发布 | 五模块发布至 `artifacts/verification/w3-sdk-consumer-binding-20261115/m2`。137 个任务中 30 实际执行、107 up-to-date；包含 `backend-sherpa-asr:compileReleaseKotlin`。不是全新缓存的完整重编译。 |
| 当前 Sherpa 后端 | AAR 38,862 B，SHA-256 `6de95f567932747fb98ffc7b15ee66e0cca75a6674f63c87e40e8bc9b2852028`；与对应当前本地产出逐字节相同。旧 `02ee869e…` AAR 和既有 35 项发布材料保持原摘要。 |
| 源码与元数据 | 五份 sources JAR 共 14 个 Kotlin 文件与当前源码逐字节相同，Python 与独立 Info-ZIP 均验证；15 个 Gradle metadata variant-file 条目的尺寸及所声明摘要算法通过。47 份选定源码均干净且前后未变，不声称穷尽构建输入或二进制语义等价。 |
| 实际独立消费 | 使用既有 `resolve.gradle`，三个 Debug runtime 配置实际解析制品；SDK 路径全部指向新本地仓库，无 SDK project component。contract/systemAsr 不含 Sherpa 后端或 runtime；Sherpa 配置选中当前 SDK AAR 与 `com.k2fsa.sherpa.onnx:sherpa-onnx:2.0.1`。 |
| 固定 runtime 绑定 | 实际解析的 runtime AAR 为 49,441,341 B、SHA-256 `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`，与此前已验固定 AAR 全字节一致，可连接既有 CI 整包/16 SO 及 21 成员输入矩阵。 |
| 编译范围 | 独立消费者 Release 27 任务、3 执行，Debug 23 任务、2 执行，两次实际 Kotlin 编译均通过。解析报告仅是 Debug runtime 配置；不声称已收集 Release runtime 解析图。 |
| 打包边界 | 五份 SDK AAR 和两个消费者库 AAR 都不内嵌 SO；backend POM 为 runtime 作用域声明 Sherpa 2.0.1。这证明传递消费绑定，不能当最终 App 的原生库打包、安装采用或通知保留证据。 |

两次构建保留既有 SDK XML-version warning，没有运行或宣称新 lint/功能测试。全局依赖缓存复用且未清理；新的本地 Maven 仓库独立，不等于全部构建环境隔离。170 个原有 dirty/untracked 路径和 161 文件终态冻结守卫在父验收时通过，无新 prepare/解码/麦克风/AiAi。

材料见[当前 SDK 消费目录](../../probes/sherpa-runtime-materials/sdk-consumer-20261115/)。`candidate-receipt.json` 固定当前候选、47 份源码和实际命令日志身份；`published-sdk-artifacts.json`、`source-jar-bindings.json`、`resolved-artifact-binding.json` 交叉关联实际 AAR/POM/module/source JAR/消费者。`metadata/` 为实际生成的五 POM、五 module 原字节副本，不是生产通知包。

独立 CBC hy4free 复核返回 `error_during_execution/is_error=true`、HTTP429/code6004/categoryquota，零 token、没有报告；CLI exit0 不计通过。服务端给出的重置时间为 **2026-11-16 10:15:32 UTC+8**，已停线、不自动重试或换模型，等待用户所选线路。本轮父端构建/绑定事实保留，独立复核未完成。

[复核未交付收据](w3-sdk-consumer-binding-review-20261115.json)保留精确错误及父停线裁定。任务本地新 Maven 制品、消费者 AAR、原始日志和规范 prompt 全部保留；仅删除 69,506 B 的已终态临时 prompt 重复件，字节相同的规范副本仍在。

P1 的当前 SDK 本地候选与实际库消费者绑定已有父验收证据；最终生产 App 采用、逐输入组完整性/可信度和独立接受仍未完成。P2 递归最终组成、P3 实际交付通知、P4 独立发布复审及 R4/R5/W3 仍 open，本次不修改这些门限或批准状态。
