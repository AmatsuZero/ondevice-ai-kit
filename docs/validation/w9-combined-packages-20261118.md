# W9 双端组合实包核验（2026-11-18）

**发布 NO-GO。** Android 与 iOS 均已构建完整的 unsigned Release 组合，两边候选配置都关闭 W6 视频理解输入，保留独立视频超分。iOS 的旧实包仍带 W6 入口，仅留作历史基线。当前 iOS 组合的隔离开发签名副本已通过真机启动，Android 的隔离副本已通过 TECNO 真机启动、导入合成 WAV 与一次升调；最终签名组合仍待双端功能验收、通知适用性核对和独立复审。W3/W6 后续研发继续暂停，W9 用户确认的双端听感、约 30 秒自动停录、转换和重录无需再验。

源码基线 `70877961f7073841e769973df0c10e61f39ed2b8`，分支 `feat/w9-ab-integration-20261117`。构建与核验在 foundation 的 `.omx/artifacts/w9-combined-candidate-20261118T084303Z/` 隔离目录内完成。以下文件名均相对此目录。`verify-combined-packages.py` 重新读取两个 App、依赖锁、原始通知输入和 Android 测试/检查报告；当前双端摘要与验收证据见 `candidate-pair-acceptance/paired-candidate-index.json`；原 `composition-receipt.json` 保留，其中 Android 当前有效，Apple 段仅是带 W6 入口的历史基线。

| 产物 | SHA-256 | 状态 |
| --- | --- | --- |
| Android unsigned APK | `a0b7627f2a90e521fb12eb9b24489349bdcef0e5c71a367f65590f18aa18dcc6` | `io.ondeviceai.demo`，`0.1.0 (1)`、minSdk 26、targetSdk 36；448,891,769 B |
| Android W9 Release AAR | `75b8f2829de7ef448ad8189280326c710ea66f458a5c4b11cd95a7c17e0f8138` | 与独立 W9 AAR 收据一致 |
| 历史 iOS Release App 文件清单 | `72178206e5fd1c27db9ace7335e9048fa2338ea72b78782d9001058fd0bce09e` | `io.ondeviceai.combined.demo`，unsigned arm64、最低 iOS 18.0；197 个文件。摘要是有序路径/大小/内容摘要清单的 SHA-256 |
| 历史 iOS App 可执行文件 | `f36e16d462ffb36d55277c56773b1b90a6cde6ea9af74079f3343f030423ac97` | Mach-O 平台 IOS、minos 18.0、SDK 27.2 |
| 当前 iOS 单图 Release App 文件清单 | `c263a82418116e8342529de40ca9325a539937ac9831f8d1eecdc21afa750fab` | unsigned arm64、最低 iOS 18.0；197 个文件；版本字段未定 |
| 当前 iOS 单图 App 可执行文件 | `33d2cdfd15e520ec481f7c75bbdeca34e7449ee9e67fc90db188e50ffa140dfb` | 对应当前 App 清单 |
| iOS 实际链接的 Release KMP archive | `43c9b181bc58ff84f52ab872914d5780cbb76e8a831c4f6d57286bb2748bd8dd` | 实际 link map 有 1,091 个该 archive 的对象，链接命令 `-F` 指向 `iosArm64/releaseFramework` |

## Android

- Sherpa ASR、LiteRT SR、LiteRT LM、OpenCV 四项构建开关均为 `true`。实际 LiteRT LM factory `imageOnly=true`，`MainActivity` 在该条件下不给多模态 Host 接入视频存储和选择器；独立视频超分 `VideoHost` 保留。包内四 ABI 共 46 个 `.so`（arm64-v8a 12、armeabi-v7a 11、x86 11、x86_64 12）。实际模型资源包括 Sherpa Whisper tiny int8 编解码权重、词表、Silero VAD 与 ML Kit 内置模型资产；没有把宿主加载的外部模型权重计作已入包。
- 组合构建保留测试 XML 与 lint XML：`testDebugUnitTest` 75 项、0 失败、0 错误、0 跳过；`lintRelease` 0 错误、10 警告。首次 `testReleaseUnitTest` 不存在；首次把依赖清单采集并入构建又遇到 Gradle 项目 variant 歧义。成功构建与依赖采集分开留档。测试证据是 Debug JVM，未运行此完整 APK 的真机流程。
- `android-overlay-package-receipt.json`：725 个非目录成员全部复读；原 644 个成员与补入前 APK 的内容一致；新增 80 份原档通知与 1 个索引，索引 82 条来源映射、65 个坐标均对应包内摘要。`android-final-original-byte-bindings.json` 再从 65 个带摘要的本地原始 AAR/JAR 复读 82 条通知，确认原档成员、索引和最终 APK 内容一致；另复核 78 个既有资源输入与 46 个打包后处理的本机库输入。14 个 `assets/models_bundled/` 模型资产均与 `com.google.mlkit:face-detection:16.1.7` 对应 AAR 的成员完全一致。
- `android-component-notice-accounting.json`：195 个解析组件、126 个外部产物。54 个外部产物（51 个不同坐标）未命中通知文件名。`android-unmatched-notice-triage.json` 逐项绑定产物摘要，12 项找到本地 POM、其中 11 项有直接许可声明。这些声明帮助定位发布方原文，不能证明通知适用性。原解析清单绑定补入通知前的 APK；补入前后 3 个 DEX 文件逐字节一致。
- `candidate-pair-acceptance/android-notice-contribution.json`：54 条未匹配解析记录中，appcompat、exifinterface、fragment 各有一组同坐标、文件名、摘要和路径的重复记录，去重后为 **51 个产物**。从当前 APK 的 3 个 DEX 共读到 25,990 条类定义；49 个产物至少有一个类名在本次解析产物清单中唯一匹配，其中 Graphics Path、OpenCV 和 Sherpa 另有原始资源或本机库字节命中。Kotlin JDK7/JDK8 `1.8.20` 未找到正向证据；不能据此排除贡献或豁免通知。类名命中也不等同于编译前后代码字节一致。
- `candidate-pair-acceptance/android-existing-notice-byte-bindings.json`：Sherpa 68 项和 OpenCV Identity 3 项均与冻结源码逐字节一致，Sherpa 4 个索引的 63 项还能绑定留存原件。已有通知与 Annotations `23.0.0`、Kotlin stdlib `2.4.0`、Sherpa `2.0.1`、OpenCV `4.12.0` 的选定原文建立关联；其他 45 个有类证据的产物本轮未建立同类关联。Coroutines `1.10.2` 文本不能证明 `1.11.0` 两个坐标已覆盖；Kotlin stdlib 的证据不能直接替代同版 `kotlin-reflect`。Sherpa 的嵌套原生索引仍标为不完整。根索引的修改说明摘要仅匹配前 4,317 B；实际 9,709 B 文件与既有 ORT 追加后的收据及 APK 一致。

## iOS

- `ODAI_VIDEO_DEMO`、`ODAI_VOICE_EFFECTS_DEMO`、`ODAI_MULTIMODAL_DEMO`、`ODAI_MULTIMODAL_MLX_DEMO`、`ODAI_OPENCV_IDENTITY_DEMO` 均为 `1`，实际传入 `ODAI_THIRD_PARTY_NOTICES_DIR`。本地 SwiftPM 输入是 `apple`、`apple-mlx`、`apple-opencv`；`Package.resolved` SHA-256 `e67e985970770991f1eb2384ca9e9b6267fb8a8f1569051be5cfb2175f0ef858`，12 项远程 pin 均与解析状态和本地 checkout HEAD 一致（10 个版本 pin，MLX Swift LM 和 Swift Transformers 为 revision pin）。包内有 SmolVLM2 manifest、Metal 库和 tokenizer 配置，无 SmolVLM2 权重。`CFBundleShortVersionString` 和 `CFBundleVersion` 缺失，版本、签名和分发渠道待定；完整 App 下限 18.0 与 W9 SDK 下限 15.0 分列，最低系统实机尚待裁定。
- `apple-release-app-notice-accounting.json`：同一 Release App 重新比对 180 个索引列出的原文、索引本身及 OpenCV/SFace/YuNet 3 份许可，184 个文件的来源与包内字节一致。索引仍记录文件/头部个别例外与历史 Unicode 术语适用性等开放问题。文件留存核对完成，完整归因与发布审查仍开放。
- `apple-overlay-not-applied.json` 保留首次 `git apply` 返回 0 但目标源码摘要未变的原始异常。首次 Xcode Release 链接的是 Kotlin Debug，仅保留作首轮通知基线。随后在隔离副本用 `patch -p1` 应用已有生成器改动，再核源文件摘要并生成工程；第二次核对误把 linker 对象路径当成构建日志文本，现以 link map 对象表和实际 `-F` 参数完成核实。未改普通工程和现有生成器的在途改动。

## iOS 单图候选增量

- 一位原生助手交付三文件补丁 `native-ios-image-only/candidate.patch`（SHA-256 `570c1d0658233da473aa34683f42d38f1bc90e6930f0b8170646374c5a3b553d`）。主代理比对补丁前后字节、在隔离源码生成和构建，并将三个文件的已验增量合入工作树。保留生成器既有 Release KMP 修正；普通 `OnDeviceAIDemo.xcodeproj` 没有改动。固化的生成器回归 **21 项通过**，覆盖 Debug/Release 单图编译条件、缺通知拒绝生成、MLX 前置条件、与验证模式互斥、普通配置不启用单图。首轮 RED 与原生助手 Ruby 版本首错均留档。
- `apple-image-only/package-receipt.json`：App 清单 SHA-256 `c263a82418116e8342529de40ca9325a539937ac9831f8d1eecdc21afa750fab`（197 个文件）；可执行文件 SHA-256 `33d2cdfd15e520ec481f7c75bbdeca34e7449ee9e67fc90db188e50ffa140dfb`。实编译包含 `ODAI_MULTIMODAL_IMAGE_ONLY`、MLX、独立视频超分、OpenCV、Speech 和 W9。两个 Host 的单图分支均不接视频存储和选择器；MLX 准备后不接视频输入 provider。旧可执行文件含多模态视频输入目录标记，新可执行文件没有；功能运行仍待真机。
- 实际链接 Release KMP 的 1,091 个对象，KMP archive 摘要与历史 Release 基线一致；12 项 SwiftPM pin 与锁、解析状态和 checkout HEAD 一致；184 个通知文件逐一和原件比对一致。iOS 设备目标为 arm64、minOS 18.0，当前 App 未签名，版本字段仍待定。Android 冻结组合沿用原 `a0b7627f2a90e521fb12eb9b24489349bdcef0e5c71a367f65590f18aa18dcc6`，iOS 单图构建阶段未重建 Android。这两份摘要供后续验收，不替代最终签名安装包的身份。

2026-11-19 后续：[Android 选定版本通知增量](w9-android-notice-delta-20261119.md)已构建并核验，另有 APK 摘要 `765148e0…`，只新增 8 份通知原文和 1 份索引。下节真机证据仍对应原始组合的隔离副本。

## 当前组合真机和复审

- iPhone 16 Pro Max：以当前 unsigned App 的完整 197 文件清单为输入，复制并开发签名为 `io.ondeviceai.w9combinedsmoke20261118`，通过真机 CoreDevice 安装和启动，6 秒后同 PID 仍在。原组合 App 仍在；184 份通知字节一致，两份 Mach-O 的 `__text` 在签名前后相同。首次 launch exit 64 的原因是选项放在 Bundle ID 之后，调整顺序后仅重试启动。见 `candidate-pair-acceptance/ios-launch-smoke.json` 和 `ios-launch-resume.json`。这是隔离开发签名副本的启动证据，尚无本组合的 UI 功能、真实录音或系统中断证据。
- Android TECNO：2026-11-18 曾为 `(no serial number) offline`，随后 ADB 清单为空。2026-11-19 重连后，[隔离组合副本真机冒烟](w9-android-combined-smoke-20261119.md)通过：启动、打开离线变声、导入 1 秒合成 WAV、一次升调，结果 16,000 帧。早期拒权与播放焦点抢占证据仍归属早期包。
- 同摘要原生 `code-reviewer` 返回 `429 Too Many Requests`，没有复审意见，未重试。已有 CBC `hy4flash` 审计和独立 `REQUEST CHANGES / NO-GO` 均保留为历史证据。

## 下一道门

1. 补齐仍缺的 Android 对应版本通知和 iOS 历史 Unicode、个别文件及头部原文；现有文件一致性不代表适用性结论。
2. 在同一最终签名组合上完成双端功能冒烟，补真实 iOS 系统播放中断、双端录音中的系统打断与 Speech/TTS 争用。既有证据见[权限和中断验收](w9-permission-audio-release-audit-20261118.md)。
3. 裁定资源预算 `DRAFT/NOT APPROVED` 和最低系统缺测，明确发行版本、签名和渠道，再按最终摘要独立复审。未生成正式签名发行包，未上传或发布。
