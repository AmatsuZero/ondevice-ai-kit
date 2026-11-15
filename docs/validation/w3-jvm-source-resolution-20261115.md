# W3 JVM 源码链接归属修正

本轮修正来源证据中的一个边界：旧 [class/source 映射](../../probes/sherpa-runtime-materials/2.0.1-member-inputs-20261115/class-source-map.json) 的 `gitBlobSHA1` 对应 AAR 模块下的 Git 符号链接文本。22 个条目的 mode 全为 `120000`，共 1,568 B，不能称作 22 份 Kotlin 源码字节已保留。旧限定复核与原快照保留，不追改历史为完整源码通过。

本次在固定提交 `af2d8d81dbfa61c4cea4c568da78f3982edca005` 的完整 tree（`truncated:false`，SHA-256 `c1e7beafad4f8f5d001b3dfbb8709aef6cd610f3f0896c33d91f68b1a04a81b7`）中逐条读取实际 link blob，再规范化相对目标，要求目标属于同一固定 tree 且为 mode `100644` 的普通 Kotlin blob。没有按同名猜目标，也没有在本地创建真实 symlink。

| 核验边界 | 结果与限制 |
| --- | --- |
| 链接与真实源码字节 | 22/22 链接、22/22 普通源码 Git blob、大小与 SHA-256 均通过；源码共 141,269 B。实际执行 43 次固定官方 GitBlob API 读取，复用一份已有且 Git blob 相同的 OfflineRecognizer.kt；原 API 响应与 stderr 留存于任务证据。 |
| 声明源码集合 | 固定模块 `src/main/` 中的 Kotlin/Java 源条目精确为上述 22 条，`src/release/` 无此类条目；各链接目标唯一。此结果限定为固定 tree 的声明源集合，不是全部生成输入或环境闭包。 |
| 第 23 个共享 API 文件 | `sherpa-onnx/kotlin-api/OfflineDiacritization.kt`（blob `0fc20bd47af2c70927df851462a749cb2f7f166f`）无对应 AAR 模块链接或实际 class SourceFile，故排除于该集合，未下载并混入编译输入。 |
| 实际 class 对应 | 固定 AAR SHA-256 `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7` 的 classes.jar 为 245,045 B、SHA-256 `0ee4991c56c19ef5a8a573b01891193d7298ffe7ebcc68f42b944361d8869d93`。全部 124 class 的 fresh javap SourceFile 与旧值一致，并覆盖全部 22 源；Python 与独立 Info-ZIP 读取的 JAR 字节及 class 名集合一致。元数据对应不证明重建 class 或 DEX 语义等价。 |
| 构建记录 | 固定 workflow、模块 build.gradle.kts 和 new-release.sh 均重新对照同 tree 的 blob。实际官方日志固定相同 checkout、执行 compileReleaseKotlin、Java NO-SOURCE 和 assembleRelease；Tts.kt 第 299 行参数对应编译 warning。原 version.cc 日期/SHA 补丁仍保留，不由 Kotlin 未被脚本选中推出全构建无补丁。 |
| 历史与新矩阵 | 新记录保留原链接路径/blob 并明确类型，增加 resolved path/blob/真实源码摘要；旧映射 38,122 B、SHA-256 `4dde5dd98f0239cc88501931dbe077a9edb62829c0c95c6786e9a9ec1b9b726f` 不变。新矩阵仅补 JVM 解析，仍为 21 非目录成员、九个原输入组。 |

最新来源入口为[本轮材料](../../probes/sherpa-runtime-materials/2.0.1-jvm-inputs-20261115/README.md)：`source-link-resolution.json`、`class-source-map.json`、`jvm-input-group.json`、`member-matrix.json` 和 `parent-verification.json`。上游 Kotlin 源原字节及 Apache-2.0 LICENSE 一并保留，未作实现修改。当前 SDK 的实际消费链仍见[上一轮绑定](w3-sdk-consumer-binding-20261115.md)。

首个验收脚本使用系统 javap shim，真实报 `Unable to locate a Java Runtime`，尚未形成 class 结果；原脚本与 stderr 保留。仅改用上一轮已使用的 JDK 21.0.7 的绝对 javap 路径后，本次完整限定验收通过；没有安装 JDK 或修改全局 Java 配置。本轮未重新执行 Gradle、lint、产品测试或推理。

170 个既有 dirty/untracked 路径在文档更新前均保持原摘要；四份共享文档只加本轮明确前缀，原余下字节受保护。161 文件冻结守卫再次通过。fresh ADB devices 清单为空；新 run `w3-qwen-terminal-20261114-01` 的 device-before.json/run-started.json 仍不存在，零 prepare、安装、模型传输或解码；不开麦克风、AiAi，不重试 USB/server 恢复。

独立复核目前没有可接受的新报告：原 native429 不重试；用户所选 CBC hy4free 在上一轮实际返回 HTTP429/code6004、零 token，服务端重置时间为 2026-11-16 10:15:32 UTC+8。该时间不是可用性证明，仍按原停线裁定等待用户线路选择；本轮未再调用任何 helper。

本次 **PASS** 仅覆盖实际源码字节、符号链接闭包、固定声明集合与实际 class 元数据对应。上游重建 class/DEX 等价、全部生成/原生构建输入、最终生产 App 采用、递归组成与通知保留、独立发布复审仍未验证；P1–P4、R4/R5、W3 保持 open。源码分支本轮不 push。
