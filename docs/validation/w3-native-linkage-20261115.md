# W3 四 ABI ELF 声明依赖核对

2026-11-15：固定 AAR 的 16 个 ELF、92 条声明依赖已核对。四 ABI 逻辑图相同；内部成员与本机参考桩库名称覆盖通过。该结果补充 P1/P2 的实际二进制观察，W3 及 P1–P4、R4/R5 仍 open。

材料：[说明](../../probes/sherpa-runtime-materials/2.0.1-native-linkage-20261115/README.md)、[完整依赖图](../../probes/sherpa-runtime-materials/2.0.1-native-linkage-20261115/native-dependency-graph.json)、[工具链与参考桩库](../../probes/sherpa-runtime-materials/2.0.1-native-linkage-20261115/reference-toolchain.json)、[符号读取限制](../../probes/sherpa-runtime-materials/2.0.1-native-linkage-20261115/symbol-observation-boundaries.json)。

| 验证项 | 实际结果 | 接受范围 |
| --- | --- | --- |
| 成员身份 | 全部 16 SO 与固定 a8d09d1d AAR 原成员逐字节同 | 沿用已接受的 CI/AAR 与 Demo APK 成员绑定 |
| ABI、SONAME | 四 ABI 的机器类型/位数一致；16 SONAME 同成员 basename | ELF 元数据观察 |
| 声明图 | 92 条边，16 指向同 ABI AAR 成员，76 指向五个外部名称；四 ABI 逻辑图同 | DT_NEEDED 范围 |
| 外部名称参考 | libandroid.so/libc.so/libdl.so/liblog.so/libm.so；本机四 ABI API21 目录的 20 个桩库存在，记录大小/SHA | 不证明真机符号解析或最低 API 兼容 |
| 两种工具输出 | readelf/readobj 的依赖集合及 SONAME 对应 | 同一 LLVM 工具链的两个前端，不冒充独立解析实现 |
| 符号表边界 | 16 均有 SHT_DYNSYM，无 SHT_SYMTAB；四 arm64 初读 no symbols，动态读取有输出 | 不凭导出名阴性排除隐藏/内联/静态组件 |
| 不变量 | 173 原保护路径、161 文件冻结合同通过 | 零设备、下载、构建、helper 或生产实现变更 |

本机参考 NDK 30.0.16138531-beta3 与原 Sherpa job 的 30.0.16248370 不同；本轮未重建历史工具链。NEEDED 未列 libc++_shared.so 不能推导为没有 C++ 运行时。静态依赖、运行时 dlopen 分支、完整嵌入组成及适用通知仍须分别核对；这份清单不关闭 P2/P3，也没有替代独立发布复审。原 arm64 普通符号的 exit 0/空 stdout/no symbols 记录保留，没有当成完整组成接受。

完整暂存 diff-check 的 32 处诊断均来自原样 readelf 输出的行尾空格；每份原始输出与本地执行结果逐字节相同，不为格式检查改写。其他本轮新增文字检查通过。

原 native/CBC 429 停线继续有效，已提出的新免费线路选择待答。本轮未重发旧复核；下一生产通知候选及独立最终 APK 验收仍按原分工推进，真机质量/资源余项仍等待实体连接条件改变。
