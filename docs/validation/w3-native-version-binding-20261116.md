# W3 四 ABI native 版本字段静态对应

已接受限定 P1 增量：固定 Sherpa AAR 的四份 C-API 库中，三个导出函数的实际指令分别指向版本、Git SHA 和日期字面量，12 项均与既有 `version.cc` 修改及两份 CI 日志一致。新增检查读取原始 ELF/指令字节，不依靠字符串搜索推断返回值，也未执行 native 代码。P1–P4、R4/R5 和 W3 仍 open。

## 实际核对

固定 AAR SHA256 为 `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`。四份 `jni/<ABI>/libsherpa-onnx-c-api.so` 均与该 AAR 的对应成员逐字节相同。

| ABI | 静态指令形式 | 三个字面量 |
| --- | --- | --- |
| arm64-v8a | ADRP + ADD + RET | 全部匹配 |
| armeabi-v7a | Thumb LDR literal + ADD PC + POP | 全部匹配 |
| x86 | CALL-next/POP + ADD + LEA + RET | 全部匹配 |
| x86_64 | RIP-relative LEA + RET | 全部匹配 |

`SherpaOnnxGetVersionStr` 指向 `2.0.1`，`SherpaOnnxGetGitSha1` 指向 `af2d8d81`，`SherpaOnnxGetGitDate` 指向 `Mon Oct 26 14:57:12 2026`。该日期是上游写入的字段，不是本次检查时间。原 source commit 为 `af2d8d81dbfa61c4cea4c568da78f3982edca005`；`version.cc` 的原 GitBlob `fad4f460…` 经已记录的两处修改后，完整 GitBlob 为 `149297a10a8ba54619e57615d844bbf5c7b21b1b`，SHA256 为 `c4addca669aa59aa7aef9d8862acc761d94d3a36eb26ab0d0ea60eb5a41e106d`。父端重新核对两份已留存 CI 日志的原始 38 行，没有重新获取网络材料。

父端先按 LLVM 反汇编的操作数手工计算地址，再以标准库读取 ELF `PT_LOAD` 映射与原始字符串。免费 hy3 随后交付独立的原始字节解码器；它自行发现动态符号，检查窄指令模式和文件映射，得到相同的 12 个地址/值。LLVM 工具之间属于同一实现家族；这里新增的交叉核对是标准库解码路径，不能把多个 LLVM 前端称为完全独立实现。NDK r30-beta3 工具仅用作检查参考，不冒充历史构建工具链。

## 候选、失败与验收

新增工具与测试位于 [sherpa-native-version-check](../../probes/sherpa-native-version-check/README.md)，源码按 hy3 交付及精确替换原字节集成，主代理没有代写解码实现。四个免费 hy3 请求均完整终态，观察到的工具调用事件为零：首稿 196.174s、主修正 84.088s、测试小修正 7.087s、独立复核 56.300s。没有 provider 错误重试、付费回退或 native 子代理调用。

首候选被真实测试拒收：7 项方法出现 6 个错误，四个真实 ABI 仅 x86_64 通过。AArch64 ADD 条件自相矛盾且源寄存器位域错位、Thumb 三返回值解包错误、i386 CALL 偏移错误均保留原件。hy3 七处精确替换后，四个真实库/12 字面量及八个损坏对照通过；另一个新增测试夹具误写 ELF64 `p_paddr`、未扩展 `p_memsz`，校验器正确拒收。独立测试小修正只改 `p_filesz/p_memsz` 偏移和 ADD shift 测试位，解码器源码不再变化。

最终实际集成的 **12 项测试全部通过**。对四份真实库构造的八个同长、仅内存负例分两种：四处 SHA 字面量改单字节后，解码器返回变更值，由父端固定源码期望比较拒收；四处 RET 指令改变则由解码器拒收。不能混写为八项都由解析器拒收，也不是修改签名 APK 或设备执行。合成 ELF 测试另外覆盖缺失/重复符号、错误机器类型/表边界、非文件映射、错误指令和字符串终止/编码。

独立 hy3 复核结论为 `accept_bounded_increment`，无 finding，仅审供应源码和收据；当时两处测试小修正仍明确在途。最终测试修正与全量 12 项通过由父端另验，不倒写成复核者已执行。161 文件冻结合同和共享更新前的 176 个既有工作文件摘要核对通过；生产代码、通知、模型、设备状态均未改。仅删除本任务无 open 的 Python 缓存，精确字节数见清理收据；候选、全部原始错误、工具输出、源码与二进制保留。

## 覆盖边界与材料

版本字符串可以被伪造。本结果证明的是四个固定文件中三段受支持函数的静态指令到字面量的对应及其与既有源码/日志的相符关系，不能单独证明所有 native 源码、补丁或构建输入相同，也不证明运行时符号解析、动态执行、完整静态组成、JNI/CXX/ORT 覆盖、生产采用或发布接受。工具不是通用 ELF 校验器、反汇编器或模拟器；不支持的指令应拒收。完整关闭要求仍见 [W3 验收范围](w3-closure-scope-20261115.md)。

本轮零构建、零设备/AiAi/麦克风调用、零模型或 ASR runtime 下载；只执行本地文件读取、校验和免费文本 helper 请求。源站、hy4free、native 的旧 429 未重试，未重放 USB、FILE 或 heap。

已纳入 Git 的文本证据见 [材料目录](../../probes/sherpa-runtime-materials/native-version-binding-20261116/README.md)。原始失败 unittest stderr 的第1/10行各有尾空格，全量 `git diff --check` 如实返回2；该文件与原 stderr 摘要相同，未格式化。仅排除此精确原始日志后，源码、文档及其余材料检查返回0，见 `diff-check-disposition.json`。完整 prompt、stream-json、stderr、首失败及验收过程留在本地 `.omx/artifacts/w3-native-version-20261116T002000/`；原 ELF/AAR 留在既有 artifact，不新增二进制入 Git。
