# W3 NDK 与 C++ runtime 来源材料

本目录固定两条历史工具链的日志、源码声明、两枚 arm64 产物 note 和公开通知候选；完整 W3 待办范围保持。见 [验证报告](../../../docs/validation/w3-ndk-runtime-provenance-20261115.md)。

`source-retention-map.json`将69条原始公开源路径绑定至61种唯一字节；重复字节只保留一个canonical文件。`official-receipts/`保留精确请求URL及摘要，原完整catalog/HTTP响应位于本轮`.omx/artifacts/w3-ndk-runtime-20261115T130823Z/`；没有获取完整NDK包或runtime二进制。tree/blob相等用于核对当前官方响应的字节与对象关系，不是签名验证或可信构建证明。

`parent-acceptance.json`、`git-tree-verification.json`、`graph-acceptance.json`为父端机械核验；`elf-notes/`保留readelf与上游Python解码输出，JNI缺`.comment`的警告保留。定位器仍使用同一参考LLVM，不能称两个完全独立的ELF解析器。

`helper-deliveries.json`区分一次限定guard复核、两个拒收文档稿和最终接受稿。raw/prompt/错误原件均保留；guard复核讨论被禁止的推断，并非现有生产实现含所有所列HIGH缺陷。未执行新构建、设备或生产改动；既有63通知资产保持原字节。

Whole-toolchain NOTICE及四组基础LLVM许可证只作为候选输入材料；未将其无选择地集成到App，不证明完整适用通知、历史patch全部应用或最终静态代码保留。R4/R5/P1-P4/W3保持open。
