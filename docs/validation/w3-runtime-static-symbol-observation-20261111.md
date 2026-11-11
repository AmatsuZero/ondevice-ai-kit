# W3 现有AAR静态组成检查的局限

2026-11-11。只读核固定AAR的四份arm64库，使用本机既有NDK `llvm-readelf`检查section、`llvm-nm --defined-only --demangle`和其`--dynamic`变体检查符号。四份实际成员均先核size/SHA，与此前AAR清单一致；没有下载二进制、重建或启动设备。

四库均无`.symtab`，均有`.dynsym`。普通定义符号输出为空（工具exit0，原stderr保留），动态符号输出非空；针对utf8proc/kissfft/fbank/sentencepiece/fst/Eigen/kaldi_decoder/websocketpp/asio/hclust的组件名查询均未提供可核验组成的符号证据。**无匹配不能证明组件不存在，也不能从没有静态符号表推断它曾如何被处理。**

这项不补齐完整静态组成，也不否定[官方job依赖下载/配置观察](w3-runtime-build-dependency-reconciliation-20261111.md)。不继续以相同符号查询尝试关闭该门；精确source-to-prebuilt、嵌套ORT组成、最终生产通知与独立发布复审仍缺。外部heap Kotlin候选和此前免费接续线路选择仍待完成，W3保持open。

[机器收据](w3-runtime-static-symbol-observation-20261111.json)记录四成员摘要和每份工具输出摘要；完整输出保留于本机 `.omx/artifacts/w3-runtime-materials-20261111/static-member-observation/`。原50材料、15补充文件、108冻结文件及普通App/模型保持；0新助手/设备/模型调用。
