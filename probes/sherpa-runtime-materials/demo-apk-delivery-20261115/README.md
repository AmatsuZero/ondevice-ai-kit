# 现有 Sherpa Demo 的 APK 交付观察

2026-11-15，主代理调用仓库已有 Debug 构建任务，未改生产实现、依赖或模型。固定源码 HEAD 为 `de5e7bc2cbbda23bc8d3a1a87783ea9d8f9d8987`；304 个选定构建输入和 47 个 SDK 输入在构建前后摘要相同。它们是明确列出的输入快照，不是完整构建输入闭包。

首次构建在 Gradle 启动前因系统 Java shim 找不到 JRE 而失败。保留原始日志，仅为恢复子进程指定已有 JDK 21.0.7；相同离线参数随后 exit 0，用时 8.834 秒。335 项任务中 25 项执行、310 项 up-to-date；backend Kotlin 编译为 up-to-date，不能声称本次重新编译全部源码。SDK XML 版本警告保留。

新 APK 为 244926013 B，SHA256 `b4036696717aff8ecbd45dc5372f3ec7d96a330fda4cff5cc68d05a0aa9d0f20`。新 backend Debug AAR 为 40726 B，SHA256 `a39766683cab79bcbf8476aafe40b1e6fdc3030f44550159b6c796146e5ddfff`。两个归档分别有 563/4 个非目录成员；Python zipfile 与系统 Info-ZIP 的有序清单和串接解压负载逐字节相同。APK 身份为 `io.ondeviceai.demo`，minSdk 26/targetSdk 36。

APK 中四 ABI 共 16 份 Sherpa/ORT SO 与固定 runtime AAR `a8d09d1d…` 的对应成员逐字节相同。7 份既有资产与输入原字节相同，其中包含 `SHERPA-LICENSE.txt`、`WHISPER-LICENSE.txt`、`SILERO-LICENSE.txt`。124 个 runtime 类名、18 个 backend 类名及 DemoOfflineAsrFactory 均存在于实际 DEX；类名覆盖不是字节码/语义等价证明。

Demo 使用 `project(":android:backend-sherpa-asr")`。这是现有 App 打包边界的增量，不是独立 Maven SDK 最终消费者，也不是产品发布批准。APK 还含 MLKit/Camera/Compose 等其他功能，未在本轮证明整个 APK 的完整组成。backend AAR 仍只有四个原成员，没有新增通知资源；上述三份根/模型许可不能替代全部适用组件、嵌套归属、修改说明和通知索引。P1–P4、R4/R5 和 W3 均保持 open。

本目录收录原始构建参数、失败/成功日志、成员与类名收据及材料清单。完整 APK/AAR、26 MB DEX 原始报告及保护快照保留在本地 `artifacts/verification/w3-demo-apk-delivery-20261115/` 和 `.omx/artifacts/w3-demo-apk-delivery-20261115/`，不纳入 Git。173 个原保护路径、161 文件冻结合同均通过；无设备、麦克风、AiAi、模型执行或下载。
