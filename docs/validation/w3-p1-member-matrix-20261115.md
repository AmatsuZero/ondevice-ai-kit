# W3 P1 成员输入矩阵及冻结消费者绑定

本轮将此前分散的 CI AAR、16 份 native 库、ORT 来源和消费者信息关联到同一份成员记录。固定 AAR 的 **21/21 个非目录成员**均列出实际尺寸、SHA-256、输入组和覆盖限制；五个目录单独记录。P1、W3、R4/R5 和 P2–P4 仍 open。

| 已核边界 | 本轮实际证据 |
| --- | --- |
| 固定 AAR 到既有隔离消费者 | 本地 Maven 坐标 `com.k2fsa.sherpa.onnx:sherpa-onnx:2.0.1` 对应 AAR 全字节相同；冻结 terminal `main.apk` SHA `88757e37ac741a6bf7b3256f1d7378852b0e6c9bd9464762dd6155be81c7dd13` 精确只含四份 arm64 SO，均与该 AAR 相同。冻结源码声明、repository 配置、既有打包收据与构建父脚本一起绑定。这是隔离探针，尚未成为生产候选。 |
| JVM 成员 | `classes.jar` 含 124 个 class 和一个 Kotlin module 元数据文件；实际 `javap` 的 SourceFile 属性将 124 个 class 映射到固定完整源码树中的 22 个 Kotlin 路径/精确 GitBlob。实际 `apkanalyzer` 在 APK DEX 中找到全部 124 个 class 名称，另有一个生成的 `R`。类名匹配证明结构覆盖，不是 DEX 与 class 原字节相同或全部字节码语义等价证明。 |
| Sherpa 输入与实际补丁 | 固定 source `af2d8d81dbfa61c4cea4c568da78f3982edca005` 的 13 份构建/打包源码共 39,085 B，逐份核 GitBlob。四 ABI 实际 CMake 命令按 ABI 记录。libs/AAR 两 job 均观察到 `new-release.sh` 修改 `version.cc` 日期和短 SHA；按已核源码重建修改前后 blob，匹配日志 `fad4f460..149297a1`，明确不是“无补丁”。不由该 tracked diff 断言所有未观察或生成输入无修改。 |
| 非 native 打包输入 | Manifest 来自 source manifest 与 namespace/minSdk 合并，`R.txt`/`proguard.txt` 实际为空，AAR metadata 为生成输出。固定源码声明 Gradle 8.6、AGP 8.4.0、Kotlin 1.7.20、compileSdk 34、minSdk 21、JVM 1.8、release minify=false；官方 AAR job 另有 Gradle 8.6/Temurin 21 实际记录。 |
| 嵌套 ORT | 四个 ORT 成员逐一连接已有 release ZIP→AAR 字节相同、此次 Sherpa CI→AAR/消费者对应、实际 source `33ca9628233dc8f002435e868d4c2e9f82766ca1`、执行 workflow `bb069649de9edc4689b2c5f9609e5499ae0bee9d` 和四 ABI 日志。实际删除 `cmake/onnxruntime.cmake` 的 SOVERSION/target VERSION 语句；记录 API 27、NNAPI/LTO ON、mimalloc OFF及原完整 CMake 命令、依赖源码/归档收据。ORT job ZIP 摘要转移相等与 raw member 字节相等分开记录；不把上层 Sherpa API 21 当 ORT 配置。 |

源码获取首批三次 `net/http: TLS handshake timeout` 保留；已有成功成员保持，仅对未齐项进行一次调用级直连恢复，最终 13 份源码 GitBlob 全通过，不改全局代理或凭据。没有新模型/运行时二进制下载、构建、安装、解码、麦克风或 AiAi。161 文件冻结守卫通过后一次 `adb devices -l` 仍为空，新 terminal run 的 device-before/run-started 均不存在；未重启 server、切 backend 或重放旧 run。

本轮覆盖的是 **所有 AAR 成员的输入归属记录，以及既有隔离消费者绑定增量**。仍须对选定的实际生产候选完成采用绑定，并复核每个输入组的源码/补丁/构建输入和配置是否完整；SourceFile 元数据与类名映射的证据限制保持显式。不得把矩阵有 21 行等同于完整 source/build-input 可信度已接受。P2 递归最终静态组成、P3 实际通知保留和 P4 独立发布接受分别验收，不用它们替代或否定 P1 增量；也不预设唯一 attestation 或本地可复现构造。

材料见[成员输入目录](../../probes/sherpa-runtime-materials/2.0.1-member-inputs-20261115/)，其中 `member-matrix.json`、`class-source-map.json`、`build-input-groups.json`、`consumer-binding.json` 可交叉读取。验证源码归档为实际执行字节，原始 javap/apkanalyzer 输出、首 TLS 失败和恢复 API 原件留在 `.omx/artifacts/w3-p1-member-matrix-20261115/`。本轮源码分支未推送。

独立 CBC hy4free [复核及父裁定](w3-p1-member-matrix-review-20261115.json)接受本次有界增量，保留两条 LOW 覆盖意见。13 份新收据仅证明这 13 份源码；父端另将七个显式打包/source-target 路径逐份核到固定树 GitBlob，包括此前已有的模块 `build.gradle.kts`，见 `referenced-source-path-bindings.json`。该补充不等于全部编译输入已列全，且不在独立复核供应范围。精简复核投影省略了重复 ORT 补丁行；完整材料中的四份 `patchEvidence` 和原始日志均保留，父端重新核了日志摘要。不声称本轮重新读取原始 per-ABI CI 成员。

原 native 复核返回 429、没有报告，未重试；用户选择 hy4free 后的首个复核自然结束但 JSON 恰在 262,144 B 中途截断，没有完整 result，父端拒收。随后独立精简供应输入、使用 stream-json 的复核自然 365.245 秒完成，完整 result success/is_error=false、零工具调用；旧 prompt/raw/失败均保留。这一实际成功交付不证明原截断内部原因或通用修复。README/helper-delivery/LICENSE 和后加的七路径收据未供应给该事实复核，范围在父裁定中单列。

父端 fresh 验证再次通过 21 成员 Info-ZIP 读取、124 份 javap class 摘要/SourceFile 解析、九输入组解析和 161 文件冻结守卫。另一次离线 SDK metadata 图选择 Sherpa 2.0.1，既有 SDK AAR/POM/module 摘要保持；首命令在缺 Java Runtime 时退出，使用既有 JDK21 的调用级环境后图查询通过。此观察仅留本地运营收据，未纳入本次独立复核，也不是 fresh SDK 构建、runtime artifact 解析字节或生产采用接受。

[本轮机器收据](w3-p1-member-matrix-20261115.json)记录 170 路径保护快照、166 个本次共享编辑以外路径不变及共享文件原有 dirty 内容保留。清理仅删除两份已终态 prompt 的临时重复件，共 303,548 B，字节相同的规范副本保留；未删源码、候选、原始失败、二进制或共享缓存。
