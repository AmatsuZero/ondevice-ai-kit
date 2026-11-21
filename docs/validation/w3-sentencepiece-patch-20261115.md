# W3 第三方 sentencepiece 补丁与嵌套声明

本轮推进 P2 的实际构建输入边界，发现此前成员矩阵未逐文件记录的一项上游补丁：官方四 ABI 日志均报告 `Patched ssentencepiece for Android: all threading removed`。该补丁发生在下载的第三方源目录，不能从主仓库 `git diff` 只有 version.cc 就推断全部输入没有其他修改。

固定 Sherpa source 为 `af2d8d81dbfa61c4cea4c568da78f3982edca005`；`cmake/simple-sentencepiece.cmake` 的 Git blob 为 `49bdf574c9705a23847cc250de3e50eaafcf5fb0`。本次取得官方 simple-sentencepiece v0.7 归档，355,335 B，SHA-256 `1748a822060a35baa9f6609f84efc8eb54dc0e74b9ece3d82367b7119fdc75af` 与该模块的固定 URL_HASH 一致。全部 35 个普通文件逐项 Git blob/大小核对，与原保存的完整 tree `62dd423df1d51da5ea06f1c3a046fc04f01b4f39` 一致；不从 tag 名单独推断归档身份。

使用现有 Android SDK CMake 3.22.1，只执行固定模块第 5–15 行 helper 和第 96–183 行 Android patch body 的原始字节。没有改写补丁规则，没有执行完整 configure、FetchContent、编译或 CI 重放；wrapper 和选取区间摘要均记录。实际重建结果：

| 文件 | 原始字节数 | 重建修改后字节数 | 修改内容 |
| --- | ---: | ---: | --- |
| threadpool.h | 3,255 | 125 | 上游 Android 空 stub 替换线程池实现 |
| ssentencepiece.h | 3,238 | 2,898 | 移除线程池 include/member，线程数默认值改为 1 |
| ssentencepiece.cc | 8,088 | 7,547 | 三处批量 Encode/Decode 由 future/enqueue 改为同步循环 |

三个文件的原始/重建 Git blob、SHA-256、完整 diff 均保留。四 ABI 的日志分段分别核对下载、补丁消息、ssentencepiece.cc.o 编译、静态 libssentencepiece_core.a 链接和 Built target，补丁消息行号为 924/1726/2484/3279。固定 CMake 的声明链接边为 ssentencepiece_core→sherpa-onnx-core→JNI/C API，再由 C API→C++ API；已连接原 AAR 每 ABI 三个 Sherpa 成员的摘要。该记录说明特定源码修改与构建目标，不断言 LTO/链接裁剪后每个 SO 保留的精确机器码，也没有直接取得历史 CI 补丁后源码。

另外核出两份嵌套源码声明，已从固定归档原注释逐字节保留：

- `darts.h` 的 BSD 2-clause / Susumu Yata 声明。补丁后的 ssentencepiece.h 仍 include 此文件并声明 Darts::DoubleArray；不能只记录 simple-sentencepiece 根 Apache-2.0 文本就代表该 bundled header 声明已收录。原头文件 53,998 B 与固定归档 Git blob 一致；DARTS_VERSION 文本是 0.32，不把来源注释的 master 链接当固定上游 revision。
- 原 threadpool.h 的 Jakob Progsch / Václav Zeman、zlib-style 声明。Android 所选补丁删除原实现；保留原声明用于说明来源与修改边界，不直接断言最终二进制一定包含该原实现，亦不称其为 MIT 许可。

此前有界 `probes/sherpa-runtime-materials` 材料文本检索未收录上述两个署名/组件声明；原 57 份文本与 141 文件材料包保留当时范围，不追改为穷尽许可集合。本次补充文本还没有被生产 SDK/AAR/App 消费或打包，P3 仍缺实际集成与保留验证。

材料见[本轮目录](../../probes/sherpa-runtime-materials/2.0.1-sentencepiece-patch-20261115/README.md)，核心收据为 `patch-record.json` 与 `bundled-notices.json`。原归档、CMake wrapper、工具输出与操作收据留在 `.omx/artifacts/w3-sentencepiece-patch-20261115/`；没有新二进制或模型下载、helper 请求、设备动作、生产实现/依赖变更。原 native 与 CBC hy4free 429 线路继续停线，本轮不构成独立复核或发布接受。

本项验收限定为固定归档/源码、原补丁逻辑重建、四 ABI 构建观察及嵌套声明字节保留。完整递归最终组成、逐输入完整性/可信度、最终生产候选采用、实际通知集成及独立发布复审仍缺，P1–P4/R4/R5/W3 保持 open；设备终态合同和原质量/资源门限不变。

171 个原有保护路径中，167 个保持初始原字节；4 份 F08/共享文档发生恰好一个末尾 LF 的并发删除，均通过“当前字节补一个 LF 后等于初始 SHA”核实。保留当前工作树状态，未回退且未将这些既有差异带入 index；共享文件只暂存本轮前缀加 HEAD。161 文件冻结守卫通过。首个保护检查在暂存前安全失败，精确并发差异及后续限定验证另存，不把它改写为 171 路径原样未变。

完整 git diff --cached --check 真实 exit2：12 处诊断全部来自原样 unified diff 的空上下文/加号行，以及上游补丁产生的 header 两个空白行。已逐个限定路径和行内容，不格式化这些摘要固定的证据；除这两份原始证据文件外，其余本轮文字检查 exit0。未声称完整 diff 检查全绿；没有新的生产编译或功能测试。
