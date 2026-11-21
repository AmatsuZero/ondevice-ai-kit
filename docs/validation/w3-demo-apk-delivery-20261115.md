# W3 现有 Sherpa Demo 的真实 APK 保留验证

2026-11-15：现有 Sherpa Demo 离线构建及限定产物核对通过。本轮补充实际 App 打包边界，未修改生产实现；W3 和 P1–P4、R4/R5 尚未通过。

证据入口为 [材料说明](../../probes/sherpa-runtime-materials/demo-apk-delivery-20261115/README.md)及 [产物验收](../../probes/sherpa-runtime-materials/demo-apk-delivery-20261115/artifact-verification.json)。本轮主代理负责已有任务调用与验收，没有派发新 helper；原 native/CBC 配额失败及待选线路维持原状态。

| 核对 | 本次结果 | 边界 |
| --- | --- | --- |
| Gradle | 首次 exit 1；仅子进程设置已有 JDK 后 exit 0，8.834 秒，335/25 执行/310 up-to-date | 离线增量构建；backend Kotlin 编译 up-to-date；非全量重建 |
| APK 身份 | io.ondeviceai.demo，244926013 B，SHA256 b4036696717aff8ecbd45dc5372f3ec7d96a330fda4cff5cc68d05a0aa9d0f20 | Debug Demo，本轮未安装或运行 |
| 新 backend AAR | 40726 B，SHA256 a39766683cab79bcbf8476aafe40b1e6fdc3030f44550159b6c796146e5ddfff | project 依赖；不能替代既有独立 Maven SDK 消费证据 |
| 归档交叉核验 | APK/AAR 563/4 非目录成员，Python/Info-ZIP 有序列表与串接解压负载逐字节同 | 结构与字节核对，不证明完整组成 |
| 原生运行库 | 四 ABI 的全部 16 SO 与固定 a8d09d1d runtime AAR 对应成员逐字节同 | 未证明全部递归输入或机器码重建 |
| 类名覆盖 | 实际 DEX 含 124 runtime、18 backend 类及 DemoOfflineAsrFactory | 类名存在，不是语义/字节码等价 |
| 模型与许可 | 7 资产原字节保留，含 3 份现有根/模型许可 | 不是全部适用通知集合；未新增通知资源 |
| 保护 | 304 选定构建输入、47 SDK 输入、7 资产、173 原保护路径不变；161 文件冻结合同通过 | 304 输入非完整构建输入闭包 |

原 Java 启动错误、恢复参数、SDK XML 版本警告均保留。原默认 Demo APK 在构建前已另存，未将旧 APK 或隔离探针当作这次新产物；本轮也未清理共享缓存、改全局 Java 配置、下载工具或修改签名资产。

完整暂存 diff-check 的唯一诊断是原始 `build.stderr` 第 3 行的末尾空行；原失败日志不为格式检查改写。其余本轮新增文字检查通过。

这次确认三份既有许可可从 Demo 的 assets 输入保留至真实 APK。当前 backend AAR 仍无独立通知资源，不能将许可保留通过写成 P3 通知完整；后续仍须按已备 P3 brief 交付适用通知选择/index/修改说明候选，再验证新发布 SDK 的独立最终 APK、篡改/缺失负例及独立发布复审。R4 真机完整英文/AMI/挑战覆盖和 R5 资源预算也没有由此次构建解决。
