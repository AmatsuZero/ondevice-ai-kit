# simple-sentencepiece Android 上游补丁与嵌套声明

本目录补充固定 Sherpa `af2d8d81dbfa61c4cea4c568da78f3982edca005` 构建中的第三方源码输入。官方四 ABI job 均报告对 simple-sentencepiece v0.7 删除线程实现，随后实际编译 ssentencepiece.cc.o 并链接 libssentencepiece_core.a。

355,335 B 原始归档的 SHA-256 为 `1748a822060a35baa9f6609f84efc8eb54dc0e74b9ece3d82367b7119fdc75af`，匹配固定 CMake 模块声明；全部 35 个普通文件与已保留 Git tree `62dd423df1d51da5ea06f1c3a046fc04f01b4f39` 的 Git blob 一致。使用现有 CMake 3.22.1，仅执行固定模块原字节的 helper 与 Android 补丁 body，重建三个文件的修改，没有 FetchContent、完整 configure、编译或 CI 重放。

`original/` 与 `patched/` 保留实际前后字节，`android-threading-removal.diff` 保留差异；修改后头文件的空白行也是上游补丁原始产物，不格式化。`patch-record.json` 关联来源、归档、原补丁行区间/摘要、四 ABI 编译与静态库日志，以及声明的 Sherpa 链接路径。没有直接取得历史 CI 的已补丁源码或证明最终保留的机器码。

`original/ssentencepiece/csrc/darts.h` 为上述固定归档中的原始 bundled header，含 BSD 2-clause 声明；补丁后的 ssentencepiece.h 仍 include 该头文件并声明 Darts::DoubleArray。`notices/darts-clone/NOTICE.txt` 保留其原注释完整字节。原 ThreadPool 的 zlib-style 声明也单独保留；其实现被上游 Android 补丁换成空 stub，不据此断言最终整个进程没有线程或其他 ThreadPool 来源。`bundled-notices.json` 记录提取区间，根 Apache-2.0 LICENSE 原文另存。

本项补强 P2 的特定组件/修改/声明材料，不是穷尽递归最终组成或实际生产通知集成。P1–P4、R4/R5、W3 均保持 open；旧材料快照不改写。
