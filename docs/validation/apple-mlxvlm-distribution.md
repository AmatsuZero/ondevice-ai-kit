# Apple MLXVLM 分发验证

2026-10-23。可搬移 **source consumer 已通过本地离线编译**；增强包 **binary 分发未通过**。设计/计划为 `docs/superpowers/{specs,plans}/2026-10-23-apple-mlxvlm-source-distribution*`；设计提交 `cf342a1`，实现 `b8acaa3286431460b1de455a5d7a83ce66f770ed`。本包不重跑推理，不提升 roadmap 5/12，也不关闭 P9。

## Source consumer

生成器默认仍引用当前仓库的本地 package。新增显式模式：

```sh
rtk proxy python3 tools/generate_mlxvlm_consumer.py --portable-source artifacts/MLXVLMSourceConsumer
```

输出含独立 App 源码、测量 helper、XcodeGen spec/工程、`packages/apple`、`packages/apple-mlx` 和 `source-bundle.json`。两个 package 只复制 Git-tracked manifests、Sources、Tests 及固定 lock；复制当前工作树字节（不是承诺任意 dirty 源码等于 HEAD）。不复制 untracked 文件、模型权重、依赖 checkout、`.build` 或签名资产。基础 package 的 Tests 随 manifest 保留，但本包只编译 consumer，不把它称为这些 SDK 测试的新一轮运行。

两处工程 package 引用均为相对路径，增强 package 的 `../apple` 依赖仍有效。每个复制的 package 文件有 SHA256 inventory，kind=`local-source`；该 inventory 不是 binary 或推理成功凭据。输出非空或本身为 symlink 时拒绝写入；生成期间不解析依赖、不下载。

### 本轮实际证据

先生成再搬移到：

`artifacts/verification/apple-mlxvlm-source-distribution-20261023/relocated source consumer/`

用下列固定缓存完成 Debug unsigned generic iOS build：

```sh
rtk proxy xcodebuild \
  -project "artifacts/verification/apple-mlxvlm-source-distribution-20261023/relocated source consumer/MLXVLMConsumer.xcodeproj" \
  -scheme MLXVLMConsumer -destination generic/platform=iOS \
  -derivedDataPath artifacts/verification/apple-mlxvlm-source-distribution-20261023/source-build \
  -clonedSourcePackagesDirPath /Users/daubert/Library/Developer/Xcode/DerivedData/VLMProbe-gsskvhiuepshlpbhywyqjxgoktbb/SourcePackages \
  -disableAutomaticPackageResolution -skipPackageUpdates -onlyUsePackageVersionsFromResolvedFile \
  CODE_SIGNING_ALLOWED=NO build
```

- Xcode 27.1 (`27A9275`)，Apple Swift 6.4 (`swiftlang-6.4.0.34.1`)；结果 **BUILD SUCCEEDED**。现有 orientation/AppIntents/上游编译 warnings 保留。
- 开始前 12 个缓存 checkout 的 HEAD 与 lock revision 全匹配，tracked dirty 均为空。没有下载或更新依赖。
- 搬移后的 122 个 package 文件 hash 全匹配；生成工程只引用 `packages/apple` 和 `packages/apple-mlx`。真实 build graph 指向搬移目录；`ODAIKitAppleMLXVLM.SwiftFileList` 中六个 backend source 全来自该目录，不回落到原始仓库源码。
- snapshot lock 与工程 workspace lock 构建后仍逐字节匹配原 `apple-mlx/Package.resolved`。实际 App 内 `OnDeviceAIKitAppleMLX_ODAIKitAppleMLXVLM.bundle/smolvlm2-model.json` 与 canonical 资源逐字节一致。
- source inventory SHA256：`479bdeb51f178c2b791a9d9228217b21eda90a3c3859aaa543c572ac6bbc2033`。
- lock SHA256：`e67e985970770991f1eb2384ca9e9b6267fb8a8f1569051be5cfb2175f0ef858`；canonical JSON：`c074e693cc0fb9cfb19d6e7a393ffb113108e88d1489a387e8cb06d309602d54`。
- 可复核摘要 `artifacts/verification/apple-mlxvlm-source-distribution-20261023/source-summary.json`；原始 build log 在本包 `.superpowers/sdd/` 的 `task-2-source-build.log`。构建产物和所有日志保留。

这是依赖既有缓存的 source 编译/链接证据。未做 App 安装/运行、模拟器/最低 iOS18 设备验收、自然质量或资源测量。全新机器仍须另行提供匹配缓存，bundle 不内含 MLX 依赖源码。

## Binary archive 实验

在搬移后的 `packages/apple-mlx` 目录，使用实际 SwiftPM workspace scheme `OnDeviceAIKitAppleMLX`（不是不存在的产品同名 scheme）、Release、generic iOS、同一缓存及三项固定解析 flags，运行 `archive`，额外设置：

```text
BUILD_LIBRARY_FOR_DISTRIBUTION=YES SKIP_INSTALL=NO CODE_SIGNING_ALLOWED=NO
```

完整 argv/原始诊断：`.superpowers/sdd/2026-10-23-apple-mlxvlm-source-distribution/task-2-archive.log`；exit 文件记录 **65**，结果 **ARCHIVE FAILED**。失败命令为 `SwiftVerifyEmittedModuleInterface`，目标 `OrderedCollections`（固定 `swift-collections` 1.7.3 / revision `35a1edcb8ebd2bfe251d587f0b69e62af28f508c`）。首个具体诊断：

```text
OrderedCollections.private.swiftinterface:2876:127:
error: 'Element' is not imported through module 'OrderedCollections'
```

public `OrderedCollections.swiftinterface` 也有同类错误，并有 `extraneous argument label 'at:' in call` / `cannot convert value of type 'Int'`。这表明**当前固定依赖与本机工具链的这条 library-evolution archive 路线未通过**，不等价于所有工具链/所有 binary 策略都不可能。没有修改上游 checkout、关闭 interface 验证、改变固定依赖或把普通 App build 充当 archive。

未得到可验收的增强 XCFramework。除当前 interface 门槛，自动/static library 的最终 framework 布局、传递模块依赖、canonical JSON/Metal runtime bundle 的打包、device/simulator slices，以及只依赖分发产物的独立 binary consumer 均需另包设计验证。根基础十个 XCFramework 不计入增强包通过证据。

## Tests、审查与恢复

新 black-box generator 测试初次真实 RED 为缺少 `--portable-source`（2 failures；symlink 拒绝测试当时由 parser 拒绝而通过，不冒称该项独立 RED）。实现后新3/3；含默认 consumer 与基础分发的定向11/11；完整 tools **126/126**；补强 lock/resource 精确比较后的定向6/6、Python py_compile、`git diff --check` 通过。完整 tools 显式使用 `RUBY=/Users/daubert/.rvm/rubies/ruby-3.4.11/bin/ruby`；一次漏设 Ruby 的两个既有分发测试失败已由正确环境通过，不是产品修改。

每 Task 与最终为作者自审，独立 review pending，无 APPROVE。两个保护文件的 hash/state/index 保留；无 push/merge/publish、下载、录音、私人媒体读取、签名资产变更。物理 touch、真实 system picker、短视频、Android 真 backend、质量、Metal/GPU/energy、最低系统，以及旧 Video/P6/ASR 缺口仍独立保留。

下一步不反复重跑同一 archive。若仍固定本机工具链/依赖，本路线门槛记录在此；可另包推进缓存可支持的短视频输入设计/可行性核对，或在工具链/依赖升级获得明确范围后单独设计 binary 策略。先读取最新交接和当前状态，不下载新模型/依赖。

2026-10-23 已完成上述短视频源码/缓存核对与设计，见[短视频可行性](apple-mlxvlm-short-video.md)。下一门槛为有界native sampler和固定processor的实际帧/时间语义characterization；本source/binary证据不变，尚未新增视频API或视频推理结果。
