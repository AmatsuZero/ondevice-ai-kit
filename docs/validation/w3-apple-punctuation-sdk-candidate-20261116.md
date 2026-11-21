# W3 Apple FILE 标点：本地 SDK 候选集成 — 2026-11-16

已将真机配对实验支持的同一配置精确集成到本地 SDK 的 `AppleSpeechRecognizerBackend.swift`：FILE 请求在 iOS 16/macOS 13 及以上显式设置 `addsPunctuation = true`，低版本继续原路径。完整 Apple 测试 484/484 通过、0 跳过、0 失败；iOS 15 最低目标的 Release 模块构建通过。当前是本地源码候选，没有发布、安装或新增识别；最终 shipping App 仍未固定，R4/R5/P4/W3 未关闭。

## 实现来源及效果边界

本次不是主代理新增实现：精确应用先前外部候选 `probes/ios-file-asr-punctuation-control/candidate-replacement.json` 的唯一替换。该次启动器请求 hy3，结果 `modelUsage` 报告 hy4flash-free，两者分开保留。替换记录 SHA-256 为 `1e8d60d7cd42e192c453fb0717e9c6fc6b48b9d1dc30f1957fa70fa74a4aadd4`；原源码 `46fc138edd8b23286a6e7ab38561a7c4bfb342ef6c2083205e95a94e26a2e9db`，集成后 `e202d3d0d0efa80410f22d3e9d475ea51cdeb810acba2930a86d23afd626d952`。反向移除该块恢复原字节。

四份 AppleSpeech 源码均与已执行的 PunctuationArmB 源码逐字节相同；模块名、配置与实际二进制身份分别记录，不宣称 SDK Release 对象与实验动态 framework 相同。现有 [12 请求同期实验](w3-punctuation-paired6-device-20261116.md)中，中文 CER 18/86→16/86、近英 WER 13/54→10/54，格式编辑减少而内容类不增加，构成采用该本地候选的有界依据。没有新设备测试，不外推修复远场、内容错误、历史1110或负例非空。

改动仅为既有 FILE 构造位置的 availability 保护赋值；on-device、partial results、delegate、timeout、drain/close、公共接口、麦克风实现、依赖和默认后端保持原代码。没有新 helper 调用或配额重试；主代理负责精确集成和实际验收。

## 新鲜验证

| 检查 | 实际结果 |
| --- | --- |
| 完整 `swift test` | 19.351 s，8 个 XCTest 产品共 484 项通过；Speech 产品 76 项，0 跳过/失败。另有 Testing Library 的 0-test 尾部，不重复计数 |
| 编译警告 | IdentityTemplateLeaseTests.swift:906:38 的 async 中 DispatchSemaphore.wait 产生 **1 条独立警告、2 处渲染**；该测试文件未改。先前进展消息的“两条”仅指渲染行，实际不计两项 |
| 初 Xcode 调用 | exit65，现有 OnDeviceAIKit.xcodeproj 没有 ODAIKitAppleSpeech scheme，发生于编译前；原命令/两输出保留，不归因 SDK 源码失败 |
| iOS Release | 改用安装 CLI 支持的 SwiftPM `--target ODAIKitAppleSpeech --triple arm64-apple-ios15.0 --sdk … --configuration release`，5.700 s，exit0、stderr0 |
| 编译输入 | 3 份实际 SwiftFileList 绑定 10 份真实 Swift 源；Package.swift 另记配置输入，不计作编译源 |
| 输出身份 | Core/Speech/AppleSpeech 的 3 个 Release 组合对象与 3 个 swiftmodule 分别核摘要；vtool 回读三个对象均为 IOS/minos15.0。测试构建 AppleSpeech 对象为 MACOS/minos12.0 |

Release 输出是模块对象和 Swift modules，不是 App、签名分发包或设备接受。SwiftPM 当前使用 SwiftBuild 后端，实际证据在 `out/Intermediates.noindex/.../*.SwiftFileList` 和 `out/Products`；没有假定存在 description.json。全部命令、构建/测试输出、原失败、真实 FileLists、对象/模块、来源收据保留在 `.omx/artifacts/w3-apple-punctuation-sdk-candidate-20261116/`；[机器收据](w3-apple-punctuation-sdk-candidate-20261116.json)及 `probes/apple-speech-sdk-punctuation-20261116/` 固定可携带身份。

## Android 新连接观察与后续边界

只读 IORegistry 显示 TECNO USB 注册对象由旧记录 `0x1007539b2` 变为 `0x10097ec5f`，满足旧合同的连接变化后 fresh 检查条件。一次 `adb devices -l` exit0 仍为空；未重启 ADB、切 USB 后端或重复恢复，未 prepare、安装、传模型、instrumentation 或 native decode。`w3-qwen-terminal-20261114-01` 仍未消费，锁屏和资源当前未验证。新注册不等于 ADB 恢复，更不证明物理原因；原始私有观察在 `.omx/artifacts/w3-android-fresh-connection-20261116/`。

旧实验和准备收据保持历史身份，不改写成使用本次 SDK 源码；旧 probe 的 prepare 守卫应继续拒绝不同生产源，不能更新其 hash 来重放。后续任何完整质量/资源设备批次须固定新的 SDK/App 候选和合同。当前缺口仍包括 Apple 远场及负例质量、Android 41 项缺失/单片诊断、生产预算、同候选发布组成与独立接受；此次源码候选集成不缩减完整 W3 目标。

## 提交与同步

源码及验收提交为 `5b582f43b176a2f94dcda4d6b5ed45e0880eaa32`，源码分支未推送。GitHub Issue 15 评论 `6914885523`、GitLab 项目 461 Issue 15 备注 `45450` 均已 POST 一次并 GET 回读；GitHub 正文逐字相等，GitLab 仅由服务器移除一个末尾 LF。GitHub Wiki 提交 `9eb77eaf16da28222d3cbb902f31d903f4c6b192` 已推送，远端引用及完整页面回读一致，原页面内容保留；页面 SHA-256 为 `e437062698f2a42455597bb034b914de8fc6a188d029ef8e305735680c116d97`。本节是 Issue/Wiki 记录同步，不是软件发布；GitLab Wiki 旧 EOF 通道继续 pending，本轮零新 GET/PUT。见[同步收据](w3-apple-punctuation-sdk-candidate-sync-20261116.json)。

续接时两个检查器假设被实际检查拦住：测试日志没有跨八个 XCTest 产品的单行 484 汇总，改按各产品通过段合计；Git 默认将中文路径输出为带引号的转义文本，Wiki 写入后的路径守卫因此停止于暂存前，改用 `--name-only -z` 核对真实路径后才唯一提交和推送。两次原失败及修正检查保留在本地 `continuation-checker-assumptions.json`；没有重跑测试、重复 POST 或新增设备识别。
