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

### 2026-10-29 依赖候选复核

为避免把上游新 tag 当作未经验证的修复，先通过官方只读 refs 确认：`swift-collections` 1.7.4=`844289ac455816e193f1dbbe014f3c4dbe429410`，1.7.5=`f96b909e1a9e469620bc5037c1e24e00153f376f`。官方 compare `1.7.3...1.7.5` 显示 1.7.4 在 `Sources/OrderedCollections` 没有变更；1.7.5 的相关提交 `93b0a28` 只处理 `OrderedCollections`/`InternalCollectionsUtilities` 的文件名歧义，不改变产生错误接口的声明。

随后在 `/tmp` 隔离副本分别对 1.7.4 与 1.7.5 运行同一 Xcode 27.1 / Swift 6.4 / generic iOS / `BUILD_LIBRARY_FOR_DISTRIBUTION=YES` 的 `OrderedCollections` library-evolution build。两者均 exit **65**，失败阶段仍是 `SwiftVerifyEmittedModuleInterface`，并重复出现：

```text
Element is not imported through module OrderedCollections
extraneous argument label 'at:' in call
cannot convert value of type 'Int' to expected argument type 'Key/Element'
```

1.7.5 的 host `swift build --product OrderedCollections -Xswiftc -enable-library-evolution` 可以通过，但这不是 iOS textual-interface verification，也不能替代 archive。两次 iOS probe 生成的 textual interface 已分别保存并绑定 SHA-256（1.7.4=`dfd2f79133fd04ebdb4927e2a08cbce7a4f48e72ccb907ba5d51d7ba8e725cdc`，1.7.5=`277d3e46ae5fd72b49077658fe247b15e95da452c849b4d2cb3a87b996d040a1`），原始候选、命令与结果摘要保存在 `artifacts/verification/f09-swift-collections-20261029/summary.json`。因此没有得到受支持的 dependency-only 修复；生产 `apple-mlx/Package.resolved` 保持 1.7.3 且 SHA-256=`e67e985970770991f1eb2384ca9e9b6267fb8a8f1569051be5cfb2175f0ef858`，不重跑完整 MLXVLM archive、不关闭 interface verification、不修改上游 checkout。

### 2026-10-30 编译器 workaround 与下一道门槛

Swift 上游 [#91540](https://github.com/swiftlang/swift/issues/91540) 记录了与上述 `OrderedCollections` 诊断吻合的接口打印问题；[#92814](https://github.com/swiftlang/swift/pull/92814) 已合并编译器修复，但当前 Xcode 27.1/Swift 6.4.0 是否包含它没有证据。上游给出的 `-disable-module-selectors-in-module-interface` workaround 在 `/tmp` 隔离副本做了真正的 iOS textual-interface 回读：1.7.5 无参数 `swift-frontend -typecheck-module-from-interface` 失败，仍报 `Element`、`at:`、`Int`；同版加 `-Xfrontend -disable-module-selectors-in-module-interface` 回读退出 0；**生产锁定的 1.7.3** 加同一参数也回读退出 0。生成的三份接口 SHA-256 分别为 `3f7a56daf19a91c0a762500ba4483e6bb4eb38cbf7a66a6cc78145a3d0df7b2d`、`245721868eeda0522fbd1afb3006a8eb8279e623e62eafb64c33b953b538a4cc`、`f03a28f32ab0424800f3a39ee97ca5ccecabb7d49174da93d016f8a02484bc7d`。最初只运行 `swift build` 的尝试未做接口回读，不计通过。

随后用原搬移 source consumer、固定依赖缓存/解析 flags 和同一 workaround 运行 unsigned Release generic iOS archive。结果仍为 **exit 65 / ARCHIVE FAILED**，新的首个失败目标为固定 `EventSource` 1.5.1 的 `SwiftVerifyEmittedModuleInterface`：生成的 `EventSource.swiftinterface` 包含 `EventSource.EventSource.Event`，回读报 `EventSource` 不是 actor `EventSource.EventSource` 的成员类型，并派生 `AsyncSequence` conform 错误。原始日志 SHA-256=`0a39797e059c6cb423ec08454d15e291d76199c35ac8051de92cc4653f4800b3`，保留在 ignored `artifacts/verification/f09-interface-workaround-20261030/archive.log`。这证明单一 OrderedCollections workaround 尚不足以完成增强包 archive；没有改生产 lock、上游 checkout、签名或发布结论。下一步先核对 EventSource 的受支持修复或隔离方案，再继续 binary package/consumer 门槛。

### 2026-10-30 EventSource 最小接口复现

隔离的 `EventSource` 1.5.1 checkout（revision=`86b5096ac59ab46e66bd1f6377c604bc1dab0bc2`）在相同 Xcode 27.1 / Swift 6.4 / `arm64-apple-ios15.0` 目标下单独生成 textual interface。默认 module selectors 生成并独立 `swift-frontend -typecheck-module-from-interface` 回读成功；生成接口 SHA-256=`2992a275dade76028a002c4afcbd305b5fd5b8d7c48347285ca386bad6bfb188`。

把为 `OrderedCollections` 使用的 `-Xfrontend -disable-module-selectors-in-module-interface` 全局传入 EventSource 后，编译期接口验证和独立回读均失败，首个错误仍是 `EventSource` 不是 actor `EventSource.EventSource` 的成员类型；接口 SHA-256=`92c827cb0b5dacb047cf7c7fb83ae1668619807b71b217cce7f7ae1f9adcd60a`。`-module-interface-preserve-types-as-written` 在当前编译器已被 module selectors 取代，实际等价于默认设置，不能作为独立修复。

上游缓存的 `main` revision=`bdaaba6` 相对 1.5.1 只修改 CI 矩阵以测试 Swift 6.4（没有 EventSource 源码或 API 修复），且官方 tags 目前止于 1.5.1。因此当前没有已验证的 EventSource dependency-only 修复；问题是 **OrderedCollections 需要关闭 selectors，而 EventSource 需要 selectors** 的依赖级编译设置冲突。隔离摘要保留在 `artifacts/verification/f09-eventsource-interface-20261030/summary-corrected.json`（SHA-256=`913ad55586c86fb28942b9279d6e64655f3e5296470706689ecc628697a90334`）。生产 lock、上游 checkout、archive 选项和发布产物均未修改。

## Tests、审查与恢复

新 black-box generator 测试初次真实 RED 为缺少 `--portable-source`（2 failures；symlink 拒绝测试当时由 parser 拒绝而通过，不冒称该项独立 RED）。实现后新3/3；含默认 consumer 与基础分发的定向11/11；完整 tools **126/126**；补强 lock/resource 精确比较后的定向6/6、Python py_compile、`git diff --check` 通过。完整 tools 显式使用 `RUBY=/Users/daubert/.rvm/rubies/ruby-3.4.11/bin/ruby`；一次漏设 Ruby 的两个既有分发测试失败已由正确环境通过，不是产品修改。

每 Task 与最终为作者自审，独立 review pending，无 APPROVE。两个保护文件的 hash/state/index 保留；无 push/merge/publish、下载、录音、私人媒体读取、签名资产变更。物理 touch、真实 system picker、短视频、Android 真 backend、质量、Metal/GPU/energy、最低系统，以及旧 Video/P6/ASR 缺口仍独立保留。

下一步不反复重跑同一 archive。若仍固定本机工具链/依赖，本路线门槛记录在此；可另包推进缓存可支持的短视频输入设计/可行性核对，或在工具链/依赖升级获得明确范围后单独设计 binary 策略。先读取最新交接和当前状态，不下载新模型/依赖。

2026-10-23 已完成短视频源码/缓存核对与设计，并继续内部snapshot/host characterization：host22/22、tools126/126、generic iOS编译通过；29FPS输入的原8帧门槛真实失败（上游重新采样得到7帧），后续公共API暂停，见[短视频验证](apple-mlxvlm-short-video.md)。本source/binary证据不变，未新增公共视频API或视频设备推理结果。
