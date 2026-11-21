# 固定 Sherpa AAR 的 ELF 声明依赖

2026-11-15，主代理读取固定 `sherpa-onnx-2.0.1.aar`（SHA256 `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`）的全部四 ABI/16 份 SO。提取后的每份文件均与 AAR 成员逐字节相同；无新增下载、构建、设备执行或生产实现。

`llvm-readelf` 与 `llvm-readobj` 的 NEEDED 集合、SONAME 对应，机器类型及 ELF 位数与各 ABI 一致。两工具来自同一个已有 LLVM 工具链，不作为两个独立解析器实现。完整声明图有 92 条边：16 条指向同 ABI AAR 成员，76 条指向五个外部名称。四 ABI 的逻辑图完全相同。

| AAR 内的库 | 声明依赖中的 AAR 成员 | 其余声明名称 |
| --- | --- | --- |
| libonnxruntime.so | 无 | libc.so、libdl.so、liblog.so、libm.so |
| libsherpa-onnx-c-api.so | libonnxruntime.so | libandroid.so、libc.so、libdl.so、liblog.so、libm.so |
| libsherpa-onnx-cxx-api.so | libsherpa-onnx-c-api.so、libonnxruntime.so | 同上五名 |
| libsherpa-onnx-jni.so | libonnxruntime.so | 同上五名 |

五个外部名称在本机四个 ABI 对应的 NDK API21 参考桩库目录中全部存在，共 20 份文件，大小/SHA 已记录。本机参考 NDK 是 `30.0.16138531-beta3`，不是历史 job 中 `30.0.16248370` 的同一工具链证明；ORT 的历史工具链另外沿用既有记录。这只核对名称及参考文件，不证明目标真机加载、全部符号可解析或最低 Android API 兼容性。

16 个 ELF 均有 `.dynsym`/`SHT_DYNSYM`，均无 `SHT_SYMTAB`。最初四份 arm64 普通符号读取自然 exit 0、stdout 空，stderr 为 `no symbols`；原件和摘要保留。改读动态符号可获得导出记录，但导出名未出现不能排除静态、内联或隐藏组件。NEEDED 中没有 libc++_shared.so，也不能由此得出没有 C++ 运行时或完整静态组成的结论；`dlopen` 选择的库同样不由此清单穷尽。

入口为 [native-dependency-graph.json](native-dependency-graph.json)、[reference-toolchain.json](reference-toolchain.json)、[symbol-observation-boundaries.json](symbol-observation-boundaries.json)及 [inspection-receipt.json](inspection-receipt.json)。原始 readelf/readobj/section 输出随材料保留；二进制、桩库和初始符号输出保留在本地 `.omx/artifacts/w3-native-linkage-20261115/`，其身份由收据固定，不纳入 Git。

173 个保护路径及 161 文件冻结合同保持。此为 P1/P2 声明动态依赖的父验增量；完整递归静态组成、P3 通知集成、P4 独立发布复审与 R4/R5/W3 均保持 open。
