# NDK runtime patch selection material - 2026-11-15

本目录保存 W3 NDK runtime patch 选择的有界材料与分类输入。验证文档见 [验证文档](../../../docs/validation/w3-ndk-runtime-patch-selection-20261115.md)。

结论：父级获取并校验官方源码文本，hy3 编写可集成的报告与分类修正候选文本；源码文本下载确实发生，运行时二进制下载未发生。本轮没有新增构建/CMake 求值、ELF 扫描、设备操作、模型或运行时二进制下载，也没有编辑生产 63 notice 资产。

范围：

- 17 份可保留 patch body 的分类仅依据 source paths 与 patch text；source-path scope only，58 份缺失 body 全部未分类。
- 134 条声明与 r27d 75 / r30 59 metadata 精确匹配是 declaration/metadata agreement，不证明最终归档保留。
- r27d writer 只序列化 applied-success；r30 generator code 未保留，二者生成语义不能等同。
- 四个组件根 LICENSE.TXT future index 状态是 FUTURE CANDIDATE ONLY；本轮无集成验收，生产 63 assets 不变。

限制：

- source-host HTTP 429 后停止新请求；该 429 与 hy3 配额无关。receipt 记录 76/134 body（r27d 59、r30 17）、58 missing（56 HTTP 429、2 bad URL），以及独立 r30 builder 丢失 5 份成功 body 和 3 次 429；并非 fail-fast。
- 本目录不证明最终归档、最终 APK 保留、STL/NDK 选择、sanitizer/architecture inclusion 或 final binary retention。
- R4/R5/P1-P4/W3 仍开放；离线 notice 集成与真实 APK 留存核验可沿用现有输入继续推进。

相关：[前置来源](../../../docs/validation/w3-ndk-runtime-provenance-20261115.md) | [链接规则](../../../docs/validation/w3-cxx-linkage-rules-20261115.md) | [验收范围](../../../docs/validation/w3-closure-scope-20261115.md)
