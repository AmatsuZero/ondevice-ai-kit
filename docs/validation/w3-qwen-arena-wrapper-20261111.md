# W3 独立 CPU arena-off 外壳与实包验收

外部免费 hy4free 交付的新外壳已原样集成并通过本地 host、离线构建和实包检查；独立免费 hy3 复核未发现阻断准备工作的 Critical/Important 代码问题。尚未安装或执行新变体，不能据此声称 native 消费配置、省内存或 Android 质量通过；W3 保持 open。

| 项目 | 证据 |
| --- | --- |
| 外部交付 | 768.671s 自然结束，实际文件 21937B，SHA `bdcc9a5ef409c0f0533d902ec12298e8d50a40936b93613cbae4b4568a136cb3`；36 Read/1 Write，19 个缺失路径错误保留，11 份最终上下文摘要保持 |
| 原样集成 | [QwenArenaInstrumentation.kt](../../probes/android-qwen-arena/QwenArenaInstrumentation.kt) 与候选逐字相同；主代理没有代写该实现 |
| wrapper host | 真实政策、case core、65 PCM，Android/native doubles；30 组、4113 断言全部通过，真实 native/设备调用为零 |
| 政策重验 | 13 组、41 断言；真实 Kotlin/JDK 文件系统检查，仍不证明 JNI/ORT 行为 |
| 离线构建 | assembleDebug、assembleDebugAndroidTest、lintDebug 成功，27.240454667s；lint 0 error、7 warning |
| 实际包 | 独立 app/test ID `io.ondeviceai.acceptance.qwenarena[.test]`，runner `io.ondeviceai.acceptance.qwenfile.QwenArenaInstrumentation`；16 目标类、65 PCM、四 arm64 库摘要、零权限和同一 debug 证书已核 |
| main.apk | 32464746B，SHA `8e2d54922f613dccab409de537b348b77ec1aa6363966bec1f6f99bfdff9c4f6` |
| test.apk | 7897668B，SHA `b8faa014904a352c02d78af1469b7f7dff1de18183f20fa9c233ae0b72f298ef` |
| sourceFingerprint | `d3d1a844ebd7e0c9cfe1347c194770e04bc60e11b7300bbee254cd9eb51ec9c1` |
| 独立复核 | [父裁定](w3-qwen-arena-wrapper-review-20261111.md)／[原文](w3-qwen-arena-wrapper-review-original-20261111.md)；独立设备合同及其父端检查另行准备 |

变体只改变 CPU allocator 请求：独占写入并同步精确 20B `EnableCpuMemArena=0\n`，持久化 receipt 后重验，再检查前台，随后才计数并调用唯一构造。每份报告明确 `nativePolicyEffectVerified=false`，receipt/provider 在准备前为空，之后记录实际构造参数对应的路径、size/SHA；其他模型、输入、两阶段解码和资源门保持。

父 host 新增了目录碰撞、政策快照落盘失败、落盘后篡改、落盘后锁屏，以及所有快照元数据检查；原 24 组错误、清理、完整 PCM 与计数检查保留。新的父端工具正在按独立合同准备，未赋予本次准备任何设备运行结果。

SDK XML 版本、无法剥离四个 native 库及七项 lint warning 均保留，没有声称无警告。旧 FILE 源码、APK、部分报告和已消耗 run 不变，1.5GiB/512MiB/120s 门不放宽。不开麦克风、AiAi、新模型下载或生产改动。

本机证据：`.omx/artifacts/w3-qwen-arena-wrapper-20261111/` 与 `artifacts/verification/w3-qwen-arena-wrapper-20261111/`。这些目录不保证随 Git 分发；[机器收据](w3-qwen-arena-wrapper-20261111.json)保留关键摘要。实际 AAR 及其 classes.jar 的文件名盘点未发现 license/notice/copying 条目，此有限盘点不等于法律结论；生产通知保留、运行时传递性许可和发布复审仍未通过。
