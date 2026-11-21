# W3 JNI 表面对应性子项验证（2026-11-16）

父端 fresh verification 已接受一个新增有界子项：将已验证 DEX/JVM 侧 native 声明，与固定 AAR 四 ABI JNI 导出名、pinned Sherpa 源码 JNI 定义做跨层名称和显式类型类别对应。文稿由 hy3 依据供应上下文起草并做独立文本复核，主代理另对实际材料验收；提交与双端同步另留收据，W3 目标不变。

证据：[parent-acceptance](../../probes/sherpa-runtime-materials/jni-surface-correspondence-20261116/parent-acceptance.json)、[declaration-source-type-comparison](../../probes/sherpa-runtime-materials/jni-surface-correspondence-20261116/declaration-source-type-comparison.json)。材料目录 probes/sherpa-runtime-materials/jni-surface-correspondence-20261116/ 现有 25 个源码文本、Root Sherpa LICENSE Apache 2.0 现有文本、收据、映射与 raw targeted exports；LICENSE 随源码原样复制，无新增生产资产。原始提示、完整官方 HTML、探索性快照仍在 .omx/artifacts/w3-jni-surface-20261116T0800/；完整 HTML 只留在本地忽略目录，不纳入 Git。

边界：prior 3cf41c6 DEX proof 的 4 个 APK 哈希与 496 个 class disassembly 哈希已按父端 fresh verification 重查；每 APK 有 120 个 native 声明、4 个 JNI lib 成员，与固定 AAR 形成 16 个 JNI 条目的打包字节比较。本轮无新增 Kotlin 编译、DEX 转换、APK/native 构建、设备/ASR/麦克风/AiAi 执行或模型运行时二进制下载。原 AOSP 来源站429与旧 native/hy4free 配额失败停线未重试。固定 JNI blob 的 Git SHA1、size、SHA256 从 pinned Sherpa af2d8d81 校验；只是源码检索与声明检查，不等于源码到二进制正文等价或历史完整输入闭包。

结果：20 个类 120 个 native 声明（2 个 static）在四 ABI 全覆盖，共 480 个声明名-ABI 覆盖对；每 ABI 134 个 defined/global code Java 导出名，四个 134 名称集合相同。全部 120 名命中，另有 14 个额外导出名；134 个导出名映射到 134 个固定函数定义，位于 CMake 所列 23 个 C++ 翻译单元中的 20 个；common.cc、jni.cc、speech-denoiser.cc 没有这些 Java_ 导出定义。固定源码文件 25 个：23 个 .cc、CMakeLists.txt、sherpa-onnx-symbols.lds；23 个新增 GitHub 文本下载、2 个复用现有来源。实际仅新增 3 次 LLVM-nm 调用读取另外三个 JNI ABI 库，arm64 复用旧符号输出；无整库静态符号重扫。

类型：显式参数 JNI 类型类别 120/120、返回类别 120/120 匹配。接收者名义类型 114/120 匹配，保留 6 处差异：VersionInfo$Companion 4 个方法、WaveReader$Companion 2 个方法为实例方法，Java 侧期望 jobject，固定 C++ 声明第二参数却是 jclass；这些接收者参数无名且未使用。不得静默归一化为完全相等，不声称原型逐字相等、缺陷已修或运行时 ABI 已证，也不给生产补丁或声称会导致崩溃；可执行文件行为未运行。jobject 返回具体类、C++ 函数体语义、真实 native 调用与 loader binding 均未证明。

14 个额外导出均有固定源码定义，但不是本 AAR native 声明：4 个 OfflineDiacritization（AAR 无对应类）、4 个 VersionInfo 外层兼容函数、1 个 WaveReader 外层、1 个 WaveWriter（AAR 无对应类），以及 OfflineRecognizer.decodeStreams、OnlineRecognizer.decodeStreams、OfflineStream.hasOption、OnlineStream.hasOption 各 1 个。只说明定义存在，不等于不可达，也不要求删除绑定或组件。

负例控制均是对验收输入的内存内变异：缺名、同数量改名导出、未定义符号、重复源定义、显式参数类型改动、返回类型改动、接收者差异变为具名、同长度源码字节改动；8 例均以 AssertionError 拒绝，恢复原始输入后通过。未修改实际 ELF/APK/DEX，未调用 JNI，不能称为实际二进制负例。

公开源码文本下载 23 份，另 Oracle Java 21 JNI design HTML 下载 1 份（status 200），2 份来源复用，不是零下载；官方规范仅作规则参照，不是实际 JNI 执行证明。Context7 工具不可用，未声称 Context7 或 web tool 返回可用正文。161 个冻结契约与 176 个预集成受保护路径当前内容未变。P1/P2/P3/P4/R4/R5/W3 仍未关闭：R4 维持 Android 24/65 memory-stop，R5 预算草案待定，native 全量输入与组成、生产采用和独立发布验收未解决。本子项仅新增有界证据，目标未缩减。提交、双端 Issue 与 Wiki 同步另行记录；源分支未推送。
