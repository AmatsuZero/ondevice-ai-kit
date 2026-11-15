W3 当前 SDK 本地发布与独立消费者绑定材料。源提交为 `a18292ac56e7c694fd2e7efb2550864a0b3ef21e`；使用已有发布任务及既有 `verifyPublishedSpeechArtifacts` 校验器，未改生产源码、依赖或模型。

五个 SDK 模块实际发布到新任务本地 Maven 仓库，Sherpa 后端 Release Kotlin 实际重新编译，AAR 为 38,862 B、SHA-256 `6de95f567932747fb98ffc7b15ee66e0cca75a6674f63c87e40e8bc9b2852028`。十四份 sources JAR Kotlin 文件与当前源码逐字节相同；十五个 Gradle metadata variant-file 条目尺寸/摘要通过。AAR 本身不内嵌 SO，Sherpa 2.0.1 通过 runtime 依赖提供。

独立消费者 Debug/Release 实际编译通过；既有校验器的实际制品报告仅覆盖三个 Debug runtime 配置。选中的 SDK 来自新仓库、没有 SDK project component；Sherpa runtime AAR 字节与固定 `a8d09d1d…` AAR 相同。contract/systemAsr 两配置未选 Sherpa 后端或 runtime。Release 编译单独记录，不能把 Debug 报告写成 Release runtime 配置已收集。

构建复用既有全局依赖缓存，137 个发布任务中 30 执行、107 up-to-date；不是全新缓存的完整重编译。SDK XML-version warning 保留，不称零告警 lint。47 份选定源码身份不代表穷尽构建输入。

`candidate-receipt.json`、`published-sdk-artifacts.json`、`source-jar-bindings.json`、`resolved-artifact-binding.json` 和 `metadata/` 提供对应记录；`verification-source.py.txt` 是实际运营核验源码归档，不是新的公共命令入口。原始构建/解析日志和二进制保存在本地验证目录。

本次仅证明当前本地 SDK 发布候选及独立库消费者的构建/解析绑定，不证明最终 App 打包、安装采用、实际通知保留、真机质量/资源或生产发布接受。P1 完整输入/可信度与 P2–P4、R4/R5、W3 仍 open；独立复核当前因 hy4free quota429 未交付，等待用户选择线路。
