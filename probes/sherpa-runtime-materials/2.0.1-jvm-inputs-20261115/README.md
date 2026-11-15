# Sherpa 2.0.1 JVM 源码链接解析

固定源码提交 `af2d8d81dbfa61c4cea4c568da78f3982edca005` 的 AAR 模块有 22 个 Kotlin 链接条目，Git mode 均为 `120000`。旧成员输入记录的 `gitBlobSHA1` 固定的是 1,568 B 链接文本，并非 Kotlin 源码内容。本目录保留原字段并增加真实目标的路径、普通文件 mode、Git blob、字节数和 SHA-256；旧快照不改写。

`source-link-resolution.json` 记录 22 个真实相对目标；`link-blobs/` 保存普通文本副本，`source-evidence/` 保存 22 份未改动上游源码，共 141,269 B。没有创建文件系统符号链接。21 份源码与 22 份链接由固定官方 GitBlob API 取回，一份源码复用已有同 blob 文件，全部重算 Git blob 和 SHA-256。

`class-source-map.json` 通过实际固定 AAR 的 124 个 class 的 fresh javap SourceFile 核对；两种 ZIP 读取方式得到相同 classes.jar。`jvm-input-group.json` 记录固定 main/release 源条目集合、构建脚本和已存在官方日志。共享 kotlin-api 目录另有 OfflineDiacritization.kt，但无 AAR 模块链接或 class SourceFile 对应，因此未纳入该模块声明源集合。它的排除不证明不存在其他生成输入。

`member-matrix.json` 仅更新旧 21 成员矩阵的 JVM 来源解析，其他输入组仍引用原材料。`parent-verification.json` 为限定父验收据，`verification-source.py.txt` 保存验收脚本，`LICENSE` 为上游 Apache-2.0 原文。

源码链接闭包及声明集合已验；未重编译上游 AAR，不声称 class/DEX 语义等价、构建输入穷尽、最终 App 采用或独立发布接受。独立复核仍不可用；P1–P4、R4/R5 和 W3 保持 open。
