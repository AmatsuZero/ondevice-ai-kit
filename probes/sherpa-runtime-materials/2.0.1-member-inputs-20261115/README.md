W3 P1 新增证据目录：记录 AAR 21 个非目录成员、16 个 native SO、21 组大小/哈希及逐成员输入组；此前已接受的 CI 全量 AAR 与 16 SO 一致性不变。

本地 Maven 固定AAR 等同固定版本 AAR；冻结隔离的 main.apk 恰含 4 个 arm64 SO 一致。APK DEX 含全部 124 个 AAR 类名；javap SourceFile 将 124 类映射到固定源码树中 22 条 Kotlin 路径/GitBlob。元数据/名称对应为结构性，非字节码等价或生产采用。

13 个源输入文件共 39,085B GitBlob 已核验；实际 Sherpa version.cc 日期/短 SHA 补丁已记录，前后 GitBlob 前缀匹配两份官方日志。四名 ORT 成员并入既有 release/AAR 字节一致，含精确源 33ca962…、补丁及逐 ABI 配置/依赖输入。配置完整性仍待定。

P1 生产候选绑定与穷尽源/构建输入覆盖仍待定；W3/R4/R5 及 P2-P4 各自独立待定。无新增模型/运行时二进制下载、构建、安装、推理或 mic/AiAi。保留初现 3 次 TLS 握手超时；仅调用的直连恢复补全缺失源材料，未改动全局代理/鉴权。

详见 `member-matrix.json`、`class-source-map.json`、`build-input-groups.json`、`consumer-binding.json`、`source-input-receipts.json` 与 `source-evidence/`。
