# Next session — foundation continuation

恢复入口；以最上方最新检查点为准，下方各节保留历史，不是整个项目完成结论。

当前状态：**原路线图执行 closed-with-followups；用户随后要求继续可执行专项，goal active；基础里程碑10/12，P4/P11原验收未完成；发布 open/not-ready**。下方5/12为历史口径。已批准范围保留为[F01–F12后续专项](validation/foundation-followups.md)，不把延期视为通过。

## 最新续接 — 2026-10-31 F09 诊断 framework archive 与双远端同步

- 用户恢复的 `gh` keyring 凭据在授权环境核对有效；一次性 HTTPS askpass 非强制快进将 `f553b69` 同步到 GitHub `origin/feat/sdk-foundation`，GitLab `jihu/feat/sdk-foundation` 此前已是该提交。没有更改全局 Git 配置、强推、merge、tag 或 release。此后新提交需再按新鲜远端 SHA 快进同步。
- 使用 ignored `f09-target-scoped-20261030/SourcePackages` 诊断副本和 `f09-framework-target-20261031/snapshot` 的 XcodeGen wrapper target，generic iOS unsigned Release archive **exit 0 / ARCHIVE SUCCEEDED**。原始 `archive-diagnostic.log` SHA-256=`8b768fcba17351e0875da032551d6c2314148b2b05df7615cea1ffc4a85b6681`。这只证明隔离 workaround 下 framework target 可归档：产物仅 `MLXVLMFrameworkProbe.framework`，公开 interface 仍 `@_exported import ODAIKitAppleMLXVLM`；archive 有 24 个 `.o`、2 个 wrapper interfaces、0 资源 bundle，没有 20 模块闭包或 simulator slice。详见[分发验证](validation/apple-mlxvlm-distribution.md)。
- 生产固定 lock 的同一 target 仍在 `OrderedCollections` Swift 6.4.2 interface 校验失败；ignored selector flag/MLXLM switch 补丁不可作为生产兼容通过。下一步需先确定受支持的依赖配置或明确 vendored policy，再构建完整 device/simulator XCFramework 及独立 binary-only consumer。F09 和发布仍 `open/not-ready`；保护性未提交修改保留。

## 最新续接 — 2026-10-30 F09 binary artifact gates

- F09 计划 Task 1/2 已完成，Task 3 仅完成布局检查器。提交 `73f70c4` 包含 artifact manifest validator、committed-tree snapshot、XCFramework/Mach-O 布局检查器、定向测试和[分发证据](validation/apple-mlxvlm-distribution.md)。独立审查发现的 canonical 资源哈希、真实 Mach-O、畸形 manifest 与 symlink 问题已修复并由测试复核；未取得修复后的新一轮独立 APPROVE。完整 `tools/tests` 在可写 SwiftPM 环境 **221/221**，`compileall`/`git diff --check` 通过。
- 忽略目录 `artifacts/verification/f09-binary-snapshot-20261030-v2/` 保存 `79536bd715146eb69906c2123d907e2406df47e2` 的 147 个已提交 package 文件；lock SHA-256=`e67e985970770991f1eb2384ca9e9b6267fb8a8f1569051be5cfb2175f0ef858`，tree SHA-256=`8d3b9e075e165ca1ce35d71bd1735c9dd7d0c1536aa9fcbf82b0526260eb2a68`，diagnostic patch manifest 明确为 `null`。保护性 dirty/untracked 文件哈希前后未变。
- Xcode 27.2 拒绝 `xcodebuild -packagePath`，尚无可归档的增强 framework target；当前 App archive 仍只含对象文件。Task 3 下一步需从 committed snapshot 建立真实 device/simulator framework 构建项目，并先证明 OrderedCollections/EventSource 和 MLXLM switch 的**生产可支持**依赖配置。没有增强 XCFramework、binary-only consumer 或资源运行时结果，F09 继续 `open/not-ready`。
- `73f70c4` 已快进推送并新鲜核对到 GitLab `jihu/feat/sdk-foundation`。GitHub `origin` 使用 SSH 时返回 `Permission denied (publickey)`；本机 `gh auth status` 显示默认 token 无效，本轮无法新鲜读取 GitHub SHA；上次核对为 `79536bd`，可能已变化。没有强推、tag、release 或 merge。保留本地提交，恢复 GitHub 凭据后按 `scripts/sync_remotes.py` 的快进检查补同步。

## 最新续接 — 2026-10-31 F09 framework-target 工具链门槛

- 在 ignored `artifacts/verification/f09-framework-target-20261031/` 从 `79536bd` committed snapshot 生成隔离 XcodeGen framework target；workspace 复制生产 `Package.resolved`、使用固定依赖缓存及 offline 解析 flags。XcodeGen exit 0；Xcode 27.2 generic iOS Release unsigned archive exit **65**，首个可归因失败为 `mlx-swift_Cmlx` 无 Metal Toolchain，尚未产出 archive。原始 log SHA-256=`ca600eb0c8cdd542bc8a43562c36bf17c431c2579392c5206e7868ad0bb363c5`，完整 argv/spec/summary 均在同目录；详见[分发验证](validation/apple-mlxvlm-distribution.md)。
- 初次临时探针忘记在生成的 workspace 放 lock，虽使用 offline flags 仍尝试 GitHub clone，传输失败 exit 74；此轮不计源码或依赖兼容性失败。之后 pinned rerun 越过解析；既有缓存 checkout 的 tracked 状态为 clean，生产 lock 与保护文件哈希未变。没有安装 Metal Toolchain、下载模型、修改生产依赖、签名或发布。
- 下一步先取得可用 Metal Toolchain 或另一个已安装的兼容 Xcode，再用同一 pinned project 复核第一真实编译错误；继续验证 device/simulator slices、20 模块闭包、资源和 binary-only consumer。F09 仍 `open/not-ready`，不要把本探针或旧 App archive 计为增强二进制通过。

## 最新续接 — 2026-10-31 Metal Toolchain 安装后真实首个编译门槛

- 用户批准后安装 Xcode Metal Toolchain 27B47（`com.apple.dt.toolchain.Metal.32023.922.4`）。同一 committed snapshot/framework target、固定 lock、固定 cache、offline flags 的第二次 unsigned generic iOS archive 已越过 Metal 编译，随后 exit **65** 在生产锁定 `OrderedCollections` 1.7.3 的 Swift 6.4.2 textual-interface verification；首个错误为 `Element is not imported through module OrderedCollections`，随后为 `remove(at:)` label/type 错误。没有 archive 或 framework 产物。
- 原始日志 `artifacts/verification/f09-framework-target-20261031/archive-v2.log` SHA-256=`650a143ac9d6a24ca5c404a228d57e0623dfefc4f30210f1b574f6a9e624c330`；同目录保留 pinned project、Package.resolved、argv、derived 诊断和 summary。保护文件、生产 lock、固定 cache checkout tracked 状态未变。
- 结论：Metal Toolchain 前置条件已排除，F09 当前真实阻断收敛为生产依赖/Swift textual-interface 兼容性；不能把 ignored target-scoped selector workaround 或五处 MLXLM switch 补丁当作生产修复。下一步需获得上游/依赖支持的 selector 配置或明确 vendored policy，再重跑 device/simulator framework 闭包；F09 仍 `open/not-ready`。

## 最新续接 — 2026-10-30 F09 binary distribution 设计与实施顺序

- 在现有 F09 当前图像/视频 source App archive 证据上，新增 [binary distribution design](superpowers/specs/2026-10-30-apple-mlxvlm-binary-distribution-design.md) 与 [implementation plan](superpowers/plans/2026-10-30-apple-mlxvlm-binary-distribution.md)，并在[分发验证](validation/apple-mlxvlm-distribution.md)中加入入口。设计要求完整 20 个本地 Swift interface 模块的闭包、C module maps、模型 JSON/Metal 资源、device/simulator 双 slice，以及不带增强源码 checkout 的独立 binary consumer。
- 计划从 committed tree/固定 lock 的 artifact manifest 和兼容配置证明开始，再做 XCFramework 与搬移 consumer；当前 App archive 与 isolated target-scoped flag/五处 enum switch 诊断补丁不升级为生产二进制通过。F09 仍 `open/not-ready`，P11 与发布结论不变。
- 本轮只写文档和验证顺序，未执行新增 binary build、没有下载依赖/模型、没有修改生产 lock 或受保护 dirty/untracked 文件。下一次从计划 Task 1 的 manifest validator RED→GREEN 开始；若兼容配置仍不具生产可支持性，记录具体失败并停止发布包构建。

## 最新续接 — 2026-10-30 双端项目介绍材料与 Wiki 发布

- 新增领导/产品介绍材料 `docs/reports/2026-10-project-introduction.md`，按参考调研报告补齐背景、适用场景、业务数据流、SDK 分层、双端能力矩阵、Demo 说明、性能/资源对比、发布边界和后续计划。正文明确当前 `10/12`、`open/not-ready`，不把阶段性证据写成发布承诺。
- 新增并裁剪 4 张公开 Demo 截图：`docs/reports/assets/android-demo-home-20261030.png`（TECNO KM9 / Android 15 真机）、`ios-speech-demo-20261020.png`、`ios-asr-cancel-demo-20261020.png`、`ios-video-demo-20261025.png`。图片已视觉检查；ASR 图按实际内容标为运行/取消状态，不误称为权限弹窗。
- 仓库提交 `f04bfb0a861fc883f3d2d0e09f29f1775740d384` 已推送到 GitHub `origin/feat/sdk-foundation` 与 GitLab `jihu/feat/sdk-foundation`。现有 `project.pbxproj` dirty、身份草稿和相机 probes 等受保护 dirty/untracked 文件未暂存、覆盖或清理。
- GitHub Wiki 已更新首页与介绍页，Wiki HEAD=`b9d296fa495232cfa1490248394f5406d56b3d00`；介绍页 HTTP `200`，页面引用 4 个图片 URL，Android 图片原始 URL HTTP `200 image/png`。GitLab Wiki 已更新首页与介绍页，Wiki HEAD=`9bf0f8286001fd4936a6ee4ba778fa17123d8053`；Git push 成功，未登录 HTTP 请求会重定向到 `/users/sign_in`，页面读取需已有 GitLab 登录会话。
- 后续更新约定：先修改仓库中的介绍材料/月报，再复制到 `/tmp/ondevice-ai-kit-wiki-20261030` 与 `/tmp/ondevice-ai-kit-gitlab-wiki-20261030` 的 Wiki Git 仓库，提交并推送；图片优先使用已审核、无私人媒体的验证附件。

## 最新续接 — 2026-10-30 F09 当前图像/视频源码 archive 与示例兼容修复

- 复核发现下节成功 archive 使用 10 月 23 日的 image-only 搬移快照。此次从当前 `a98aa51` 重新生成并搬移 source consumer，147 个 package 文件逐项匹配当前 HEAD；生产 lock 与受保护 dirty/untracked 文件保留。
- 当前 consumer 的首次 archive 真实 exit 65：`VideoFixtureSuite.swift:191` 对公共 `MLXVLMGenerationEvent` 缺少未知 case。示例源码增加一行 `@unknown default: append("unknownGenerationEvent")`，保留已知五类事件处理；重新生成后同一 unsigned Release generic iOS / library-evolution archive **exit 0 / ARCHIVE SUCCEEDED**。依赖继续使用下节 ignored target-scoped flag 与五处 MLXLM switch 诊断改动，不能视为生产依赖已修复。
- 当前 archive 原始日志 `artifacts/verification/f09-current-source-20261030/archive-fixed.log` SHA-256=`ed582152bb95e8cd6e4a2122ebdfff1d1d3ff5c2253b0ab187a05cfe3f6851d2`；接口明确包含 `MultimodalVideoGenerationBackend`、视频 metadata 和事件，模型 JSON 与 canonical 相同，Metal bundle 存在。摘要 `summary.json` SHA-256=`3a8a4986fbbc53cb44184f1e42e1c5d25f1b0747514605a1a8c063b79354b590`。接口导入图有 20 个本地 Swift 模块，C modules 与资源还需单独处理；增强 XCFramework/device+simulator slices/binary consumer 未验，F09 仍 `open/not-ready`。
- 相关 portable-source/consumer 回归在正常缓存环境 6/6；受限沙盒的两项 Swift 测试因用户缓存不可写失败，不计产品失败。独立 code-reviewer 通道返回 429，未取得 APPROVE；作者完成范围/证据自审，未发现 Critical/Important，但审查缺口继续保留。

## 最新续接 — 2026-10-30 F09 隔离 App archive 越过依赖编译门槛

- 同一 active goal 继续。原有受保护的 `project.pbxproj`、身份草稿和相机 probes 未覆盖或暂存；生产 `apple-mlx/Package.resolved` SHA-256 仍为 `e67e985970770991f1eb2384ca9e9b6267fb8a8f1569051be5cfb2175f0ef858`。仅在 ignored `artifacts/verification/f09-target-scoped-20261030/SourcePackages/checkouts/mlx-swift-lm` 副本给五处 `MLXFast.ScaledDotProductAttentionMaskMode` switch 增加显式 `@unknown default`，未知值立即 `fatalError`；生产依赖没有修补。
- 用搬移 source consumer 的 `MLXVLMConsumer` App scheme、Release、generic iOS、`BUILD_LIBRARY_FOR_DISTRIBUTION=YES`、unsigned、锁定依赖及 target-scoped OrderedCollections flag，真实 archive **exit 0 / ARCHIVE SUCCEEDED**。`MLXLMCommon`、`MLXVLM`、`ODAIKitAppleMLXVLM` 的编译和 interface 验证越过先前错误，App 包内包含 `smolvlm2-model.json` 与 `default.metallib`。原始日志 `artifacts/verification/f09-target-scoped-20261030/archive-v5-app-escalated.log` SHA-256=`97bece006f118bf9b05d9f70c3653827606f04558680c4c57fef6f3a41e3ae82`。
- 这只是 **App archive**：SDK 产物在 archive 的 `Products/Users/daubert/Objects/*.o`，尚无可分发的增强 XCFramework、device/simulator slices 或独立 binary consumer。F09 与发布继续 `open/not-ready`。原 SwiftPM package workspace scheme 当前无法从搬移目录直接恢复；前两次错误工作目录/scheme 尝试和一次沙盒 SwiftPM 缓存失败均未进入编译，不能计产品失败。一次自动审批因 429 未执行；随后获准的实际 archive 已完成。

## 最新续接 — 2026-10-30 F09 target-scoped archive 新阻断

- 同一 active goal 继续；原有 `project.pbxproj`、身份草稿和相机 probes 等 protected dirty/untracked 文件未覆盖。使用 ignored `SourcePackages` 副本，只给 `swift-collections` 的 `OrderedCollections` target 注入 `-Xfrontend -disable-module-selectors-in-module-interface`；EventSource 保持默认 selectors。生产 `Package.resolved`、生产 checkout 和签名产物未修改。
- 真实搬移 source consumer 的 unsigned Release generic iOS archive 已通过 `OrderedCollections` 和 `EventSource` 的 `SwiftVerifyEmittedModuleInterface`，随后 exit=`65` 在 `mlx-swift-lm` 首次失败。诊断为 Swift 6.4 对 `MLXFast.ScaledDotProductAttentionMaskMode` 的未知枚举值检查，位置为 `KVCache.swift:2627/2803`、`TurboQuantKVCache.swift:1345`、`Gemma4.swift:105`。
- archive log SHA-256=`5a4e6d769b0a92712c0e3764366b964c91552f3557767dfe4cf4f015485937c2`；隔离摘要 `artifacts/verification/f09-target-scoped-20261030/summary.json` SHA-256=`ed5f997486bdecc8fd73301160b9e69a60f8824b5954eb60072a042f5424d9d8`。这证明 target-scoped 方向越过前两个门槛，但 F09 仍 `open/not-ready`，尚未得到 archive 或 binary consumer 通过。

## 最新续接 — 2026-10-30 F09 EventSource textual-interface 依赖设置冲突

- 同一 active goal 继续；现有 `project.pbxproj`、身份草稿和相机 probes 等 protected dirty/untracked 文件未覆盖。未修改生产 `Package.resolved`、上游 checkout、签名或发布产物。
- 本地 EventSource 1.5.1 checkout（revision=`86b5096ac59ab46e66bd1f6377c604bc1dab0bc2`）在 `arm64-apple-ios15.0` 下用默认 module selectors 生成并独立回读 textual interface 成功；接口 SHA-256=`2992a275dade76028a002c4afcbd305b5fd5b8d7c48347285ca386bad6bfb188`。
- 为 OrderedCollections 采用的 `-Xfrontend -disable-module-selectors-in-module-interface` 若全局传入 EventSource，编译期验证和独立回读均失败，错误为 `EventSource` 不是 actor `EventSource.EventSource` 的成员类型；接口 SHA-256=`92c827cb0b5dacb047cf7c7fb83ae1668619807b71b217cce7f7ae1f9adcd60a`。当前编译器已将 `-module-interface-preserve-types-as-written` 视为默认 selectors 的淘汰选项。
- 上游缓存 `main`=`bdaaba6` 相对 1.5.1 只改 Swift 6.4 CI 矩阵，官方 tags 止于 1.5.1；因此没有已验证的 dependency-only 修复。F09 现在精确为 **OrderedCollections 需要关闭 selectors、EventSource 需要 selectors 的依赖级编译设置冲突**。隔离摘要 `artifacts/verification/f09-eventsource-interface-20261030/summary-corrected.json` SHA-256=`913ad55586c86fb28942b9279d6e64655f3e5296470706689ecc628697a90334`；F09 仍 `open/not-ready`。

## 最新续接 — 2026-10-30 F11 生产 SDK 文件 ASR 资源补测

- 同一 active goal 继续，无需替换；工作树 `feat/sdk-foundation`，原有 `project.pbxproj`、身份草稿和相机 probes 等受保护 dirty/untracked 文件未覆盖。隔离 consumer 由当前 `ODAIKitSpeech.SpeechRecognitionClient.recognize` 调用 `ODAIKitAppleSpeech.AppleSpeechRecognizerBackend.recognize`，使用相同固定合成 CAF；未修改生产 SDK/Demo，未使用麦克风或 Photos。
- iPhone 16 Pro Max / iOS 27.0.1，PID=`32487`；输入 SHA-256=`ea1e97d99f859f4bb1eafb75f6090bb9fe98f89ab86bd903f7c325013f3dd98e`。unsigned generic 和签名真机构建均 `BUILD SUCCEEDED`，安装、启动、Documents 回收成功。预热及 5/5 次 SDK 识别成功，实际 backend 均为 `apple.speech.recognizer`、归一化全文匹配，`client.close()` 返回。
- 原始 `artifacts/verification/f11-speech-asr-sdk-resources-20261030/device-report-final.json` SHA-256=`40d65c13c9ec7065ff639d51063e4508d186a004d38884689f1325074ecf6578`；五次耗时 p50/p95=`76.484/93.436 ms`，约 10 ms 采样进程 RSS 峰值=`36,782,080–36,929,536 B`。bounded summary SHA-256=`2b436735c860cc278f28232b1eb2d0e40492c16b8ec4c8c9ca797e0f4a9b4e04`，source manifest SHA-256=`8ae79f1efcc8f150fc58934739d2004054c86df29ecb3ecd8448b820278b469a`。
- 直连 Speech framework 与 SDK 路径为独立运行，不能把 p50 差值归因为 SDK 固有开销；自然 CER/WER、断网、真实峰值/GPU/能耗及最低系统仍未验证。P11 维持 `open/not-ready`，goal 维持 active。
- readiness 已刷新为 **61 evidence paths / 68 source hashes / 9 rows with gaps**；快照 `artifacts/verification/p11-readiness-20261030-f11-asr-index/summary.json` SHA-256=`e6ac109c818a1650b2b1df6d129eecff6f2518c9a2abd3be70def604f24bb88c8`。grouped 报告确定性重算为 3 groups / 9 coverage，输出 `artifacts/verification/p11-grouped-benchmark-20261030-f11-sdk-asr/summary.json` SHA-256=`dfbfcb8d9bc6b0e3667e82f630df1388b6a832323f73275087181ac9d586b86f`；SDK ASR 不加入兼容 benchmark sample。

## 最新续接 — 2026-10-30 F11 iOS 文件 ASR 资源补测

- 同一 active goal 继续；在 ignored `artifacts/verification/f11-speech-asr-resources-20261030/` 建立隔离 Speech framework probe，固定合成 CAF、一次 warm-up、五次顺序本机识别、逐 run 单调耗时和约 10 ms 采样进程 RSS。无麦克风、Photos 或私人媒体，生产 SDK/Demo 与受保护 dirty/untracked 文件未改。
- 泛型 iOS 构建 `BUILD SUCCEEDED`；首次签名构建在受限环境因 CoreSimulator 服务和 profile 读取失败，授权环境复用现有 `io.ondeviceai.probe.f11speech` profile 后真机构建、安装和启动成功。CoreDeviceService 两次 Documents 读取超时后恢复；这不是 ASR 运行失败。
- 正式原始报告 `device-report-final.json` SHA-256=`81e5312e678bce19cf441933ef430db670f7e5df483a60cf291bec92dad632f2`，PID=`32472` 与 `launch-final.json` 一致，bundle=`io.ondeviceai.probe.f11speech`，固定 CAF SHA-256=`ea1e97d99f859f4bb1eafb75f6090bb9fe98f89ab86bd903f7c325013f3dd98e`。warm-up 与 5/5 run 均 success、task completed、归一化全文匹配；耗时 p50/p95=`75.010/76.434 ms`，采样 RSS 峰值范围=`36,831,232–36,945,920 B`。bounded `summary.json` SHA-256=`71e163cec6634e595c338025d2070a8c0baa63271e936b585c2ddb15ddcca6f3`，来源清单保留源文件、构建/安装/启动/复制回执哈希。
- 该 probe 直连 Speech framework，仅补固定文件原始延迟/RSS；生产 SDK client 性能、自然 CER/WER、断网、真实峰值/GPU/能耗、最低系统仍未验证。首轮和正式复跑的 RSS 基线明显不同，不能推算发布预算。P11 `open/not-ready`，goal active。
- readiness 索引已加入 ASR 原始报告、summary、source manifest 和设计边界；最新快照 `artifacts/verification/p11-readiness-20261030-f11-asr-index/summary.json` 为 **57 evidence paths / 64 source hashes / 9 rows with gaps**，SHA-256=`e5497430c8e789ea1035c6adeec17af1673058400d9cd1d85f930162a2d4bf85`。grouped 复算仍为 3 groups / 9 coverage，结果 SHA-256=`f9d9ee46b3128c1c3f41cf5c3e25bf2bfcbff28967ac5426c9764736eb2dddf1`，重复生成逐字节一致；ASR 与 Video SR 均不硬映射为兼容 grouped sample。来源清单 10 项哈希回读无漂移，聚焦 readiness/grouped 回归 **21/21**。

## 最新续接 — 2026-10-30 F11 iOS Video SR 原始质量/资源复核

- 同一 active goal 继续，无需替换；工作树仍为 `feat/sdk-foundation`，现有 `project.pbxproj`、身份草稿和相机 resource/thread probes 等受保护 dirty/untracked 文件未 restore、覆盖、stage 或提交。
- 隔离 probe `io.ondeviceai.probe.f11videosr` 在 iPhone 16 Pro Max / iOS 27.0.1 上使用已审计的 Common swifts BT.709 实验约定素材处理 48 帧。仓库与设备输入副本 SHA-256 均为 `97682e3f9703e326bab3fbed06aa7a298b506d1965502288f5ed5037c51f2f1f`；SR 和 Core Image bicubic 两路均 `success`，各输出 1280×720/48 帧/24 fps/无音轨，设备 Documents 列表未出现 Photos 或共享相册路径。
- 原始报告 `artifacts/verification/f11-videosr-20261030/device-documents/ios-video-sr-probe-2076985E-4089-4826-8893-4E6C4051E3D7.json` SHA-256=`73aacec0f3b5b78b203825304f52dc668761ae3fe504a93dd383a9d476a353b0`。SR 每帧 p50/p95=`93.408/94.587 ms`，采样 RSS 峰值增量=`127,500,288 B`；bicubic 为 `1.467/2.114 ms` 与 `442,368 B`。两路首帧/编码色彩元数据可比，仍只是 probe 进程 `resident_size` 采样，不是全机/GPU/energy 峰值。
- 独立 `video_sr_validate.py --scale=4` 通过 48 帧/24 fps/无音轨/时序校验；`video_sr_quality.py` 两路均完成 48 帧配对，`colorComparability=matched-metadata`，SR PSNR/SSIM=`36.776212/0.979250`，bicubic=`38.872866/0.983506`。质量 JSON 为 ignored 证据，`qualityVerdict` 保持 `unverified`；不把单一自然素材的编码输出分数升级为模型主观质量或发布通过。
- 该轮只补齐 F11 的真实 iOS Video SR 原始延迟、采样 RSS、输出完整性与色彩/质量边界；Android 后端、主观/跨场景质量、GPU/energy、持续资源和最低系统仍开放。P11 仍 `open/not-ready`，goal 继续 active。
- 将本轮真机可用性、原始报告、两份质量 JSON、输入/参考和两份输出视频共 8 项加入 Video SR readiness 来源后，P11 readiness 为 **9 rows / 53 evidence paths / 51 source hashes / 9 rows with gaps**；grouped 报告仍为 **3 groups / 9 coverage**，聚焦 readiness/grouped 回归保持 **21/21**。SR 数据仍不硬映射成兼容 grouped sample。
- 该快照随后被 ASR 续接快照替代；当前以顶部 `p11-readiness-20261030-f11-asr-index/summary.json` 和 `benchmark/p11/grouped-report-manifest.json` 为准。

## 最新续接 — 2026-10-30 F11 iOS TTS 原始资源测量

- 同一 active goal 继续；设备入口通过 `devicectl` 恢复，物理 `iPhone 16 Pro Max` / iOS 27.0.1（UDID `00008140-0008703436D3001C`，CoreDevice `1D0EA54B-8379-5147-84DF-A86534DADE1C`）已解锁。生产 Demo、SDK 和受保护 dirty/untracked 文件未修改。
- 隔离 probe `io.ondeviceai.probe.f11speech` 使用当前 Apple Speech backend、固定 `en-US` Samantha 和固定短句；1 次 warm-up 后连续 5 次生成，5/5 `success`，同一 PID=`32416`。原始 `device-report.json` SHA-256=`d3c9d6caf68d5a45fa10bc9c85f8a23de4c469018d6ec0150c8b26b25d22e7c8`；bounded `summary.json` SHA-256=`c8c515d451d9237b3e28120358dc831ef16398f7ed9ab83f37a050fb3a7515e3`。duration p50/p95=`2779.727/2779.792 ms`；采样 RSS 峰值范围=`44,761,088–44,810,240 B`，方法为约 10 ms 间隔的 `mach_task_basic_info.resident_size`，不是真实峰值/physical footprint/GPU/energy。
- 新 readiness 快照 `artifacts/verification/p11-readiness-20261030-f11-tts/summary.json` 已通过 **45 evidence paths / 43 source hashes / 9 rows with gaps**；grouped 报告确定性重算为 3 groups / 9 coverage，TTS 原始资源数据未硬映射为兼容 grouped sample。TTS 自然质量、断网行为、最低系统仍保持 `unverified/deferred`，P11 仍 `open/not-ready`。

## 最新续接 — 2026-10-30 F09 OrderedCollections workaround 后的 EventSource 门槛

- 同一 active goal 继续，无需替换；工作树 `feat/sdk-foundation`，`project.pbxproj` 和 8 项身份/相机受保护 dirty/untracked 文件未 restore、覆盖、stage 或提交。
- Swift 上游 issue [#91540](https://github.com/swiftlang/swift/issues/91540) 与 PR [#92814](https://github.com/swiftlang/swift/pull/92814) 对应当前 `OrderedCollections` textual-interface 错误。严格 generic iOS interface 回读中，1.7.5 无 workaround 仍失败；加 `-Xfrontend -disable-module-selectors-in-module-interface` 后 1.7.5 和生产锁定 1.7.3 均退出 0。证据和接口哈希已记录在[分发验证](validation/apple-mlxvlm-distribution.md)的 2026-10-30 节。
- 在搬移 source consumer 上用同一 workaround 运行 unsigned Release generic iOS archive，`exit=65 / ARCHIVE FAILED`；`OrderedCollections` 不再是首个失败目标，新的首个失败是 `EventSource` 1.5.1 的 `SwiftVerifyEmittedModuleInterface`（`EventSource.EventSource.Event` 与 `AsyncSequence` conform 错误）。原始日志在 ignored `artifacts/verification/f09-interface-workaround-20261030/archive.log`，SHA-256=`0a39797e059c6cb423ec08454d15e291d76199c35ac8051de92cc4653f4800b3`。
- 结论仍为 F09 `open/not-ready`；没有修改生产 `Package.resolved`、上游 checkout、签名或发布产物。下一步只核对 EventSource 的受支持修复/隔离方案，不重复无参数 archive。

## 最新续接 — 2026-10-30 P11 committed-tree candidate freeze 刷新

- 最终 GitLab 连接恢复，`feat/sdk-foundation` 在 `origin` 与 `jihu` 均回读为 `9f066081bbd425846c9bb0cd2aaf2e258cdf9467`；下面 F08 节保留首次 TLS/connection reset 的历史尝试，不再是当前同步阻断。
- 从当前 `HEAD=9f066081bbd425846c9bb0cd2aaf2e258cdf9467` 重建 committed-tree archive：tree=`58f27c795a7a5a90a83eab30a95a1dbc801b18e5`，tar=`artifacts/verification/p11-candidate-freeze-20261030/sdk-foundation-9f066081bbd.tar`，SHA-256=`c81cdf13b01abdfd7b2404648f949d1107d3fabd2ccf9e7b3ff77749cb658ae2`；`git get-tar-commit-id`、tree 与重复生成逐字节一致。
- 归档只取 committed tree，八项受保护 untracked 文件均 absent；一项 tracked dirty 的 `project.pbxproj` 只保留提交中的旧版本，不包含工作树修改。SDK/Demo 源码目录相对上一冻结点没有 committed diff。候选摘要留在 ignored `artifacts/verification/p11-candidate-freeze-20261030/summary.json`，状态仍 `not-ready`。
- 该刷新只关闭候选 SDK 快照的新鲜性检查，不满足 LiteRT-LM 授权模型/真实 drain、Face Identity 生产模型/PAD、iOS15/API26、统一质量/资源报告等阻断项；P11 仍 `open/not-ready`。

## 最新续接 — 2026-10-30 F08 跨场景素材搜索边界

- 为继续 F08 跨场景 temporal quality，检查了当前仓库和既有验证目录；唯一有明确来源/许可/哈希的自然素材仍是 `tools/probes/natural_video_manifest.json` 中的 Common swifts，不能作为第二个场景重复使用。其他本地视频没有可审计授权，不纳入设备任务。
- 对 Wikimedia Commons API 的代理路径和 `--noproxy "*"` 直连路径各尝试一次：前者因本机 `127.0.0.1:7897` 代理不可用退出 7，后者因 `commons.wikimedia.org` DNS 失败退出 6。完整尝试、策略和停止点记录在 ignored `artifacts/verification/f08-cross-scene-source-search-20261030/summary.json`；这不是 F08 通过证据。
- 因此本轮不生成第二场景、不重复已有 temporal run；待有可审计来源和网络后恢复。F08 temporal pair 仍 0/3，P11 仍 `open/not-ready`，F03 最低系统按用户要求暂缓。
- 本节 handoff 的首次提交 `daf1ff4` 先只推送到 GitHub；GitLab 曾在预检和单独重试中返回 TLS `SSL_ERROR_SYSCALL` 与 connection reset，随后在 F12 提交同步时恢复并已追平到 `5007e3f`。ignored `sync-summary.json` 保留失败尝试，当前不再阻断同步。

## 最新续接 — 2026-10-30 P11 分组报告的 readiness 来源校验

- 沿用同一 active goal，不替换；开始时 `feat/sdk-foundation`、本地 `origin`、`jihu` 均在 `5f8158261d6ca05907481a7b4ab5f0e207c633ca`。现有 1 项 tracked dirty 与 8 项 untracked 受保护文件的哈希保持不变，未覆盖或暂存。
- 发现分组报告生成器只核对 readiness JSON 本身的 manifest 哈希，没有复核 readiness 内 `sourceHashes` 引用的底层证据。新增回归先观察到预期 RED（底层证据更改后仍生成报告），随后在生成器中调用现有 `validate_p11_readiness.validate_summary`；来源哈希漂移现在会在输出写入前被拒绝。
- 独立审查又发现输出路径未覆盖 readiness `sourceHashes` 文件；已先观察到直接路径、软链接和硬链接 **3 个 RED**，再补齐 alias 拒绝。当前聚焦 P11 **21/21**、完整 `tools/tests` 在 Ruby 3.4 与可写 clang/Swift cache 的正常授权环境 **198/198**；受限沙箱首次运行因 SwiftPM `sandbox_apply` 失败，不计产品失败。当前 manifest 生成 3 组、9 项覆盖、9 行 gap，P11 仍 `open/not-ready`；新报告留在 ignored 的 `artifacts/verification/p11-grouped-benchmark-20261030-integrity/summary.json`。`py_compile`、`git diff --check` 通过。
- F08 temporal pair 仍 0/3，F11 跨能力质量/资源原始测量仍缺，F03 最低系统按用户要求暂缓；此修复只防止陈旧证据产生新快照，不提升验收结论。

## 最新续接 — 2026-10-29 P11 committed-tree candidate freeze 重建

- 从当时 `HEAD=6ddc13a23cc8f7052b77a9486a527223b7c2d872` 重新生成 committed-tree archive：tree=`825c54d0af8ff5f1ad08a7d08fed28b434b30b0e`，tar=`artifacts/verification/p11-candidate-freeze-20261029/sdk-foundation-6ddc13a23cc8.tar`，SHA-256=`b6daeec000479665656ab87249ab28d20e7e51d46b37c598470b4df86426685a`；`git get-tar-commit-id` 与源提交一致，重复生成逐字节一致。
- 归档只取 committed tree，排除当前 1 项 tracked dirty 和 8 项 untracked 的受保护文件；archive 内没有这些 untracked paths。候选摘要 `artifacts/verification/p11-candidate-freeze-20261029/summary.json` 状态仍 `not-ready`：Android LiteRT-LM 授权模型/真实 drain、Face Identity 生产模型/PAD、iOS15/API26、统一跨能力资源/质量报告仍是阻断项。
- `docs/validation/acceptance.md` 已更新本次 archive 信息，readiness source hash 同步后通过 **9 rows / 42 evidence paths / 40 source hashes / 9 rows with gaps**；`benchmark/p11/grouped-report-manifest.json` 的 readiness SHA-256=`58126552d6fee4af1e58acf649b5281931c80a98df92b22189553524319ed6e4`。该重建只关闭 SDK committed-tree snapshot 的新鲜性，不冻结统一发布候选，也不改变基础里程碑 **10/12** 或 P11 `open/not-ready`。

## 最新续接 — 2026-10-29 F08 静态语义证据校验加固

- 本地审查发现静态语义 validator 原先只要求 launch receipt 的 source commit，未强制核对静态探针入口和 fixture manifest，也可能把输出路径指向 launch/install receipt。已先写出并观察到 **2 个 RED**，随后加固为要求 `--sdk-mlxvlm-static-semantic`、source commit、manifest 参数，并拒绝覆盖 source/launch/install 三类输入证据。
- 专项 **4/4**、完整 `tools/tests`（Ruby 3.4 + 临时可写 Swift cache）**196/196**；真实设备 raw report SHA-256=`31eb195e375c123c39d5133636609cad835af80d0a80408af7912e53fcf43e01` 重新校验为 **3/3 pass-bounded**。该修复只提高证据完整性，不改变 temporal pair 0/3、F08 `observed-failure` 或 P11 `open/not-ready`。
- 独立 reviewer 通道本轮仍返回 429，无 APPROVE；保留该审查缺口。下一步继续 F08 跨场景/标签概率诊断或 F11 资源专项，不重复同一 raw generation。

## 最新续接 — 2026-10-29 F08 静止帧语义真机探针

- 同一 active goal 直接 resume，无需替换；工作树 `feat/sdk-foundation`，设备探针源提交 `835221e11e06dae4db2e47d75e08ef64db855c32`。隔离签名 App `io.ondeviceai.staticsemantic` 已安装并在 iPhone 16 Pro Max / iOS 27.0.1 启动，launch receipt 的 PID=`31720`；模型、三个 frozen fixture 只存在 App Documents 沙盒，没有写入 Photos。
- 原始报告 `artifacts/verification/apple-mlxvlm-natural-video-20261028/static-semantic/device-report.json` SHA-256=`31eb195e375c123c39d5133636609cad835af80d0a80408af7912e53fcf43e01`。三行均绑定 fixture hash、bundle、PID、backend=`mlx.smolvlm2-500m`、model revision=`fa57db46815177fbdfd65cc85a2b3416a8332268`、runtime revision=`6e338e5df49424fc0db7c6d58184d622f92ed18a`；prompt 为 `Describe the visible scene in one brief sentence.`。
- 静态语义 validator 输出 `artifacts/verification/apple-mlxvlm-natural-video-20261028/static-semantic/summary.json`，`pass-bounded`、subject/scene **3/3**；三个输出都描述飞鸟和天空。该证据只关闭 frozen control 的基础场景语义可读性疑点，不改变原 temporal exact-label pair **0/3**、F08 `observed-failure`、P11 `open/not-ready` 或基础里程碑 **10/12**；temporal quality、GPU/energy、持续资源仍 `unverified`。
- 本轮专项测试 **4/4**，完整 `tools/tests` 在 Ruby 3.4 与临时可写 Swift 缓存下 **196/196**，`py_compile` 与 `git diff --check` 通过。CoreDeviceService 首次读取报告发生初始化超时，使用授权的 `devicectl device copy from` 重试后成功；不要把此前超时当作 App 失败。提交 `71bb8bb` 已同步 `origin` 与 `jihu`，原始报告、summary、install/launch/copy receipts 均为 ignored 证据；生产 SDK、普通 Demo、九项受保护 dirty/untracked 文件未修改。
- 下一步：若继续 F08，扩展不同来源/场景或完整标签概率/视觉特征观测；不要重复 Snapshot、adapter 或同一 temporal 生成任务。P11 仍保持 `open/not-ready`。

## 最新续接 — 2026-10-29 P11 readiness 证据索引刷新

- 静态语义 raw report 与 bounded summary 已加入 `multimodal-apple` 的 P11 evidence/source-hash 索引；不加入 grouped benchmark 样本，也不改变任何 unverified gap。`tools/validate_p11_readiness.py` 当前校验 **9 rows / 42 evidence paths / 40 source hashes / 9 rows with gaps**，`p11Gate=open`、release=`not-ready`。
- `tools.tests.test_p11_readiness` 与 `tools.tests.test_p11_grouped_report` **19/19** 通过；状态矩阵已记录静态语义边界，`benchmark/p11/grouped-report-manifest.json` 已更新 readiness SHA-256=`64c1dfcd4718df968c600d4f0ba6718265dd2f14287505468c62d6a8c8805e42`。受保护 dirty/untracked 文件仍未暂存或修改。

## 最新续接 — 2026-10-29 F08 选定标签前缀 margin 派生诊断

- 同一 active goal 继续，未替换 goal。当前工作树 `feat/sdk-foundation`；本轮开始时本地、`origin` 与 `jihu` 的该分支已通过普通 fast-forward push/回读核对为 `a45b3f718351fa3c018956d0fd131e24df51fdc5`。九项受保护 dirty/untracked 文件哈希仍 **9/9** 匹配，未 restore、覆盖或 stage。
- 沿用已验证的真实七行首 token logits raw report，不重新运行文字生成或设备 probe。新增 `tools/mlxvlm_label_margin.py` 与 `tools/tests/test_mlxvlm_label_margin.py`，从固定 tokenizer 的 `ĠMO`=`15720`、`ĠST`=`5022` 提取 selected prefix logits、margin 与仅在两 prefix token 上的归一化值；tokenizer hash 拒绝测试先观察到预期失败再修复，专项 **6/6**，拒绝缺失 token、raw/tokenizer hash 变更及覆盖输入。
- 派生结果 `artifacts/verification/apple-mlxvlm-natural-video-20261028/label-margin/summary.json` SHA-256=`5c801baceb0072f571920c4bd58f05c3a04264f8bc25be07e832dd4019dd4a8b`，raw report SHA-256=`d20d2a4ca2c15480b0ed1d62bf36e00f42443da567fcb49f50b13cc2195f99ec`，tokenizer SHA-256=`5ece781dc8d2b2f3e2f289ca0ae50b17cfc27dd27bfe7971bb8241e0b964331a`。三组 motion/frozen margin 为 `1.625/1.000`、`1.625/1.375`、`1.750/1.250`；motion 均更高，但 frozen 仍偏向 `ĠMO`。这支持首 token 偏置诊断，不证明完整标签概率、视觉语义或 temporal quality；F08/P11 `observed-failure`、gate `open`、release `not-ready` 与基础10/12不变。
- 下一步优先级不变：若继续 F08，需静止帧语义或真实完整标签分布/视觉特征观测；不要重复已有六次生成、Snapshot、adapter 或同一 raw report。生产 SDK、普通 Demo 与九项受保护修改未改动。
- 完整 `tools/tests` 首轮受系统 Ruby 2.6/RVM gem 不兼容及 Swift ModuleCache 写权限影响失败；以已安装 Ruby 3.4 和临时可写 cache 在正常授权环境复跑 **192/192**。`python3 -m py_compile` 与 `git diff --check` 通过。
- 修改验证文档后已刷新只读 P11 快照来源哈希：`tools/validate_p11_readiness.py` 输出 **9 rows / 40 evidence paths / 38 source hashes / 9 rows with gaps**，`open/not-ready`；`benchmark/p11/grouped-report-manifest.json` 已同步该快照哈希，未把 label-margin 派生诊断加入 benchmark sample。
- 本轮新增提交 `3704c60`（派生诊断工具/测试/文档）、`bcb7818`（grouped manifest readiness hash）均已普通推送，且 `origin`/`jihu` 回读与本地一致；本段 handoff 更新另形成后续提交。当前无额外暂存的生产改动。

## 最新续接 — 2026-10-29 F08 iPhone 首 token logits 边界

- 同一 active goal 直接 resume，不替换；工作树 `feat/sdk-foundation`，HEAD=`608b22ac838432979c6217d374468c47dbe98535`。隔离 `io.ondeviceai.logitdebug` 探针在 iPhone 16 Pro Max / iOS 27.0.1 已解锁设备运行，PID `31609` 与 launch receipt 一致。固定本地模型权重及六段公开 fixture 仅放该 App 沙盒；不使用镜像、Photos、私人媒体或文字生成。unsigned generic 与 signed device build 均 exit0/`BUILD SUCCEEDED`；首次无冻结 package cache 的受限构建卡在 SwiftPM 远程 clone，已终止并用现有 `SourcePackages` 成功重建。
- 正式原始报告 `artifacts/verification/apple-mlxvlm-natural-video-20261028/logit-boundary/device-report.json` SHA-256=`d20d2a4ca2c15480b0ed1d62bf36e00f42443da567fcb49f50b13cc2195f99ec`。七行含六个固定 motion/frozen 任务及一个 `motion-0-2` 重复 run；同输入两次完整 logits hash 一致，三组 motion/frozen hash 均不同，shape 全为 `[1,1,49280]`。六行 top-5 token ID 排序均为 `15720,5022,26332,372,625`，首位 tokenizer token `ĠMO`；各 pair 数值有差异但排序不变。同目录 `validate.py` 为 `pass-bounded`，含 PID/manifest/source/model hash 与篡改负例。
- 这关闭固定输入在首 token logits 边界“完全相同”的疑点，但不证明 vision encoder 的正确运动语义或 control 答案。原正式六次 `MOVING.`、pair 0/3、`observed-failure`、F08/P11 `open/not-ready` 和基础10/12不变。下一步可用静止帧语义任务或标签概率/视觉特征区分模型能力与任务设计，不重复同一生成。九项受保护 dirty 文件继续保留；goal active。

## 最新续接 — 2026-10-29 F08 iPhone prepared tensor 边界

- 沿生产调用链 `MLXVLMVideoSnapshot.frames → MLXVLMVideoInputAdapter.prepare → LMInput.image.pixels` 构建独立签名 `io.ondeviceai.tensordebug` App；adapter 与 tokenizer loader 副本分别和生产源 SHA-256 `5f15491e843973f473f04abcd83ced284c64513a4daa232a4f5b849e075c7751`、`2c8f0958d7764b09cba527d123d13744de18b8478cd68fd5812143fcdf520084` 一致。设备只 provision 4.6MB tokenizer/config 和六个固定 fixture，没有复制或加载 968MB 模型权重，也没有执行生成。
- iPhone 16 Pro Max / iOS 27.0.1，正式复跑 PID `31574` 与 launch receipt 一致。六行 prepared pixel shape 均为 `[8,512,512,3]`：三个 motion 各 8 个唯一 tensor-frame hash，三个 frozen 各 1 个；三对首帧 tensor hash 一致。每行文本输入均为 720 tokens、512 个 `<image>` tokens，token hash 六行相同；实际时间仍为 `0,0.25,…,1.75` 秒。原始报告 `artifacts/verification/temporal-iphone-tensor-20261029/device-report-rerun.json` SHA-256=`fa1244768cfd208fe6c4af0423187798f7677c378df2ab406d267057378a1052`；独立 validator 输出 `pass-bounded`，`verification.json` SHA-256=`2bed03a8286d1f8d4f0ed2bc8e5c5d5f8213d6edd747961f0d00c48074ca8262`；最新签名 build `** BUILD SUCCEEDED **`。
- 该证据关闭固定 fixture 的 Snapshot 后、模型前预处理边界疑点：motion/frozen 差异仍存在于生产 adapter 交给模型的 pixel tensor 中，且六行文本 prompt/token 一致。它不证明 vision encoder/模型能感知差异，也不把旧 `MOVING.` 六次、temporal pair 0/3 改写为通过；下一处有区分力的边界是 vision encoder/模型内部，不再重复 Snapshot、adapter 或同一生成任务。P11 仍 `open/not-ready`，goal active。

## 最新续接 — 2026-10-29 F08 iPhone 原生视频采样边界

- 在已解锁的物理 iPhone 16 Pro Max / iOS 27.0.1 上构建并运行独立签名 `io.ondeviceai.temporalframes` App；隔离 consumer 引入与生产文件 SHA-256 一致的 `MLXVLMVideoSnapshot.swift`（`33db01c3ce3470b2d78f7ff6b2f7c87a9197dc41198295dd483c053424eb5f01`）。六个固定公开视频 fixture 仅复制到该 App 的 Documents 沙盒；未加载模型、未生成文字、未写 Photos。
- 设备 PID `31522` 与 `devicectl` launch receipt 一致。六段均成功解码，每段 8 帧、实际时间 `0,0.25,…,1.75` 秒；三个 motion 各 8 个唯一像素哈希，三个 frozen 各 1 个，三对首帧哈希相同；snapshot 副本 SHA 与原始素材一致，close 后目录删除且原文件未变。原始报告 `artifacts/verification/temporal-iphone-native-frames-20261029/device-report.json` SHA-256=`9413fbc62497239d486f4a8cfb39bb470521d41ace3ae17e9b8b09065bb31529`；同目录独立 `validate.py` 输出 `pass-bounded`，校验结果 SHA-256=`b57b8a2919f1f1255fce07809cfabc93726e5c4bd512678d9b3d065168082d8a`。
- iPhone/macOS 使用同一 RGBA 哈希渲染参数，但 48/48 对应帧的字节哈希不同，因此只认各平台内部 motion/frozen 关系，不声称跨平台像素相等。该结果关闭固定 fixture 的**物理 iPhone 生产 Snapshot 采样边界**疑点；MLX prepared tensors、模型感知和自然 temporal quality 仍未验证。旧正式模型输出 `MOVING.` 六次、temporal pair 0/3、`observed-failure` 与 P11 `open/not-ready` 不变；下一步若继续定位，应观察下游预处理/tensor，勿重复同一生成任务。
- 首次受限构建因 ModuleCache/SPM 写权限在依赖解析阶段失败；授权环境同一命令 `** BUILD SUCCEEDED **`。受保护 dirty 文件未 stage/恢复/覆盖，goal 仍 active。

## 最新续接 — 2026-10-29 F08 原生视频采样边界

- 用生产 `MLXVLMVideoSnapshot.swift` 原文件构建隔离 macOS 27.2 探针，对六个 temporal fixture 完成 AVFoundation 解码：motion 每段8个唯一采样帧，frozen 每段1个唯一帧；三对首帧 RGBA 哈希一致；实际采样时间均为 `0,0.25,…,1.75` 秒；snapshot 临时目录全部清理且源文件未变。
- 报告 `artifacts/verification/temporal-native-frames-20261029/report.json` SHA-256=`53fd6203ee5d71e194d99193eb66bace7a7009ddeeb066497785b32d34032f2b`。首次受限沙箱运行返回 `invalidInput`/系统查询拒绝，正常授权环境同一二进制复跑完成6/6；没有修改生产源码或fixture。
- 只证明本机原生采样保留 motion/frozen 差异，不证明物理 iPhone 解码、MLX tensor 或模型感知；不能据此归因真机0/3。下一步若继续此调查，需要有区分力的下游输入边界证据，不重复相同 raw 输出；正式 temporal `observed-failure`、P11 `open/not-ready` 和 goal active 不变。

## 最新续接 — 2026-10-29 F08 temporal 失败模式分解

- 对固定 raw `device-report-v2.json` 的六个 temporal 输出逐行复核：全部为 `MOVING.`；正式 exact-label 为 0/6。仅为诊断去掉末尾标点后，motion 3/3 为 `MOVING`，frozen control 0/3 为期望 `STILL`；诊断报告位于 `artifacts/verification/apple-mlxvlm-natural-video-20261028/temporal/failure-mode-diagnosis.json`，SHA-256=`370a392590f2ab3e10a5b63cdd01d82e5875cac693d779e4c9256ac703a296ca`。
- 该结果把输出格式问题与 motion/frozen control 区分失败分开记录，但仍是固定 prompt/同源场景的诊断，不能宣称纯运动感知或自然 temporal quality。正式 summary、P11 gate 和 `observed-failure` 结论不变；不重跑同一真机任务集。


## 最新续接 — 2026-10-29 F08 temporal 原始行绑定修复

- 根因复现：保持真实 raw-report 不变，只篡改 copied evidence 的六个输出为期望标签，旧 temporal validator 会从 `observed-failure` 错判为 `pass-bounded`。现在对成功完整 nine-row raw report 逐行核对内容，并禁止输出覆盖任何输入及 symlink/hardlink；严格 MOVING/STILL rubric、模型/生产SDK/Demo 均未改动。
- 新回归先 RED（12 个子用例失败，无 errors）后 GREEN；聚焦38/38、完整 tools **186/186**。完整套件首次两个 Swift 测试因用户 ModuleCache 写权限失败，正常授权环境复跑后全部通过；不隐瞒首轮环境失败。
- 真实 temporal evidence 经新工具重建与原 summary 逐字节一致，semantic1/3、temporal pair0/3、`observed-failure` 保持。未重跑相同真机输入，未篡改 raw/summary，不提升 F08 或 P11。独立 code-reviewer 返回 `Selected model is at capacity`，没有审查结论；保留 F12 缺口。
- 下一步继续区分 exact-label instruction following 与 motion perception，或推进资源预算/缓存所有权设计；现有失败不放宽评分，不新增发布门槛。受保护dirty文件继续保留，goal仍active。


## 最新续接 — 2026-10-29 F12 候选范围与证据重建审计

- 同一 goal 继续保持 `active`，无需替换或新建 goal。对冻结 SDK 提交 `7b9f7fc75772b61948d211d72ec2e1e221362591` 到当前 `HEAD=cc438de8e32eec3f01d02d64bf7e6585f44ec2a3` 做只读范围核对：49 个提交路径全部位于文档、工具、探针、脚本或 benchmark；生产 SDK、KMP binding、Demo 和构建配置没有提交差异。既有 committed-tree archive 的 SHA-256=`6a2cb8afd895b13fa281d15f55046b3f263a1882aebd8ce31dff563bc3b2b666`，内嵌 tar commit 与冻结提交一致。
- 当前 readiness validator 重新读取真实文件通过：9 rows、38 evidence paths、36 source hashes、9 rows with gaps；P11 gate 仍为 `open`，release 仍为 `not-ready`。`tools.tests.test_p11_readiness` 与 `tools.tests.test_p11_grouped_report` 为 **19/19**。
- 两个最新正式诊断均可确定性重建：MLX cache attribution `summary.json` 与 validator 输出逐字节一致；首 token 前 Home report 与 validator 重建结果一致，报告中 10/10 source/evidence hashes 匹配。受保护 dirty/untracked 文件的 9/9 哈希保持不变。
- 该审计只关闭候选范围/报告可重建性子项，不关闭统一 release candidate、独立 reviewer、P4/P11、最低系统、Android 授权模型/native drain、Face Identity 生产模型/PAD、自然 temporal quality、GPU/energy 或发布资源预算。独立 reviewer 若无返回仍按未验证处理。

## 最新续接 — 2026-10-29 F08 首 token 前 Home 后台 terminal

- 在 ignored 隔离 Home timing 副本中，以真实 Files provider 固定公开视频，在 `.videoPrepared` 后、首个文本 update 前触发系统 Home；保持生产 `willResignActive` 取消策略，不延迟 native。iPhone 16 Pro Max / iOS 27.0.1 PID `31298`，XCTest **1/1、0 failures、0 skips**。
- `didEnterBackground` 时 phase=`CANCELLING`；106.301 ms 后 `nativeDrainAndLeaseRelease`，107.359 ms 后 `bridgeCancelled`；取消请求没有首个文本 update，前台 phase 保持 `CANCELLED` 且同输入重入成功。该窄证据关闭固定 fixture/首 token 前阶段的 `inBackgroundTerminalTiming`，不外推跨素材或跨系统。
- 规范化 report `artifacts/verification/apple-mlxvlm-natural-video-20261028/pretoken-home-xctest/device-report-pretoken.json` SHA-256=`bdf65e038122c9b63fce6801eb9da33538de4212b33ae14ffe051a4a1a534774`；trace=`c727a0843ff58ca44270016e127d536ede339531c7986e9f7e4d3dbeef1935fa`，xcresult summary=`1ae5ea82406b8456743c8b08f19b8933008b701b98281c6de9a4469292add9d7`。validator 真实报告和6个篡改负例通过；生产SDK、普通Demo、受保护dirty文件未修改。
- readiness 现为 **9 rows / 38 evidence paths / 36 source hashes / 9 rows with gaps**；P11 继续 `open/not-ready`。自然 temporal quality、物理触控、GPU/energy、持续资源、发布预算仍开放；该报告不加入 grouped benchmark。

## 最新续接 — 2026-10-29 F08/F11 MLX cache 归因

- 同一 goal active，工作树 `feat/sdk-foundation`，生产源码和9项保护dirty文件保持不变。正式设备 PID `31212`，固定公开视频/模型 12轮（8 success、4 cancelled）、drain/close/lease/临时清理全部通过。
- 隔离 consumer 新增 MLX snapshot 和关闭后单次clearCache。close后RSS `1,153,695,744 B`、MLX active `148 B`/cache `1,086,181,704 B`；无干预等待2秒RSS `1,153,712,128 B`，MLX计数不变；clearCache后立即RSS `137,347,072 B`/cache0，再2秒RSS `137,428,992 B`、active108/cache40。相对无干预对照RSS下降 `1,016,283,136 B`，支持本workload约1GB主要残留是可回收cache。第一轮cache已约1.086GB但RSS较低，不把两者等同，也不声称所有泄漏/资源预算通过。
- 正式 raw `artifacts/verification/mlxvlm-memory-attribution-20261029/device-report.json` SHA-256=`353ab172cba08aaefd4b8a5d9fe968d79e7d4a60089b0e4c4407f69f4fc98e31`；同目录 `summary.json`、`source-manifest.json`、`EXPERIMENT.md`、validator及8项合同测试。初版generic unsigned和signed build通过，加入等待对照后的signed build通过；最终PID与launch receipt匹配。两份pilot独立保留，正式结论只用等待对照版本。raw/source/build仍ignored，不纳入源码提交。
- 新鲜 readiness 校验为 **9 rows / 35 evidence paths / 33 source hashes / 9 rows with gaps**；P11 focused tests **19/19**，grouped报告重算逐字节一致。研究子代理本轮429无结果，不计独立review；已按实际源码与报告完成作者复核。
- 不修改生产SDK的全局缓存策略。下一步先确定资源预算和缓存所有权/多client影响，再决定是否提供显式维护策略；不重复相同诊断。其余F08自然temporal quality仍有失败、GPU/energy/跨素材资源开放，F11/P11 `open/not-ready`，基础里程碑仍10/12。F04受控朗读窗口尚无，最低系统设备仍暂缓。

## 最新续接 — 2026-10-29 F08/F11 固定自然视频持续 RSS 观察

- 当前 Codex goal 仍为同一 active 目标，继续 `goal resume`，无需替换；工作树为 `feat/sdk-foundation`。原有 `project.pbxproj`、身份草稿与相机 resource/thread probes 等受保护 dirty 文件未 restore、覆盖、stage 或提交。
- 在 ignored 隔离 consumer `artifacts/verification/mlxvlm-sustained-video-20261029/consumer` 中新增显式 `--sdk-mlxvlm-sustained-video` 入口。无签名 generic iOS 与使用既有开发团队签名的 physical iOS build 均 `** BUILD SUCCEEDED **`；生产 SDK、普通 Demo、`apple-mlx/Package.resolved` 和 Photos 未改动。
- iPhone 16 Pro Max / iOS 27.0.1，PID `31130`，固定公开 `common-swifts-24fps.mp4`（SHA-256=`97682e3f9703e326bab3fbed06aa7a298b506d1965502288f5ed5037c51f2f1f`）在同一进程连续执行 12 轮，顺序为四组 `success → 首 update 后 cancelled → reentry`：8 次生成成功、4 次取消成功；每轮 `drain` 后临时 `odai-video-*` 快照均为空，lease/close/remove 与 closed-client gates 全部为 true。
- 原始设备报告 `artifacts/verification/mlxvlm-sustained-video-20261029/device-report.json` 的 SHA-256=`0408fb19a30a5c4506fa5c92971453955deb5cb8c0360f19f0f2d53183007012`；同目录 `validate.py` 生成的 `summary.json` 记录 RSS 观察：第一轮前 `40,681,472 B`，首轮 drain 后 `136,200,192 B`，第二轮取消并 drain 后阶跃至 `1,141,293,056 B`，第12轮 drain 后 `1,152,892,928 B`，close 后 `1,152,909,312 B`。首轮至末轮增加 `1,016,692,736 B`，但第二轮之后只增加 `11,599,872 B`。采样方法为 `mach_task_basic_info` 进程 RSS，不能证明 GPU 专用内存、能耗、泄漏根因、跨素材资源或发布预算；这个阶跃和后续小幅增长已记录为后续专项问题，不提升 P11 为 ready。
- F11 readiness 已纳入上述 raw report 和 bounded summary；新鲜校验为 **9 rows / 33 evidence paths / 31 source hashes / 9 rows with gaps**, gate=`open`, release=`not-ready`。分组 benchmark 仍只接受既有兼容 raw sources，不将持续 RSS 报告加入 grouped samples；重算 `artifacts/verification/mlxvlm-sustained-video-20261029/grouped.json` 与 repeat 逐字节一致，SHA-256=`608c5ae640a4744d6318e1569a248720eaa6528fa8d03b09e792b235f98af61e`。

## 最新续接 — 2026-10-29 F09 swift-collections 候选复核

- 当前 Codex goal 仍为同一 active 目标，继续 `goal resume`，无需替换；工作树为 `feat/sdk-foundation`。`project.pbxproj`、身份草稿与相机 resource/thread probes 等 8 项受保护 dirty 文件未 restore、覆盖、stage 或提交。
- 官方只读 refs：`swift-collections` 1.7.4=`844289ac455816e193f1dbbe014f3c4dbe429410`，1.7.5=`f96b909e1a9e469620bc5037c1e24e00153f376f`。1.7.4 的 `OrderedCollections` 无变更；1.7.5 仅有 `93b0a28` 文件名歧义修复，未触及失败 API/interface。
- 在 `/tmp/odai-f09-swift-collections.vVEqIL` 隔离副本中，Xcode 27.1 / Swift 6.4 / iPhoneOS27.1 / generic iOS / `BUILD_LIBRARY_FOR_DISTRIBUTION=YES` 的 `OrderedCollections` build：1.7.4 与 1.7.5 均 **exit 65**，同样停在 `SwiftVerifyEmittedModuleInterface`，错误为 `Element is not imported through module OrderedCollections`、`extraneous argument label 'at:'`、`Int` 到 `Key/Element` 类型错配。1.7.5 host `swift build` 成功不构成 iOS interface 或 binary 证据。
- 证据摘要：`artifacts/verification/f09-swift-collections-20261029/summary.json`；生产 `apple-mlx/Package.resolved` 未变，SHA-256=`e67e985970770991f1eb2384ca9e9b6267fb8a8f1569051be5cfb2175f0ef858`。F09 仍 `open/not-ready`，不重跑完整 archive、不关闭 interface verification、不改上游 checkout；下一次只在出现明确上游/toolchain 修复时恢复。

## 最新续接 — 2026-10-29 F08 temporal motion/control task-set 收尾

- 当前 Codex goal 仍为同一 active 目标，直接继续 `goal resume`，无需替换；工作树仍为 `feat/sdk-foundation`，受保护的 `project.pbxproj`、身份草稿与相机 resource/thread probes 均保留，未 restore、覆盖或 stage。
- F08 temporal task-set 已完成：复用 pinned CC0 Common swifts source，三个 2 秒 motion window 与三个 repeated-first-frame frozen control 共 6/6 fixture；metadata 为 320×180、24fps、48帧、H.264/yuv420p、无音频、`hasBFrames=0`。fixture manifest SHA-256=`666fbfebdc999aefa5b9db944a090024b9478c13107685af621fb842babe1694`，真实 validate 通过。
- iPhone 16 Pro Max / iOS 27.0.1 的隔离 consumer 同进程完成 3 semantic + 6 temporal，共 9/9 rows；raw `artifacts/verification/apple-mlxvlm-natural-video-20261028/temporal/device-report-v2.json` SHA-256=`620ce4d2d3a90e83e9385ad5fbb92fe7eb60be728d7be5aa2015863735413ace`。semantic rubric 1/3；六个 exact-label 输出均为 `MOVING.`，motion/frozen pair 0/3，summary `observed-failure`，summary SHA-256=`787bb647a5d25522da17ef099fcbfa0ee3907a5dcc23cb6ca40a45d254ae0310`。这保留模型限制，不改写为通过。
- P11 readiness 已刷新并通过只读校验：9 rows、31 evidence paths、29 source hashes、9 rows with gaps，gate=`open`、release=`not-ready`；grouped 报告重算为 3 groups/9 coverage，temporal 证据未加入 benchmark sample，重复输出逐字节一致。完整 tools suite **183/183**，专项 focused **29/29**，fixture/summary deterministic、`py_compile`、`git diff --check` 和保护哈希均通过。
- 独立 code-reviewer 通道本轮 429，未取得 APPROVE；作者按 review focus 完成 self-review，未发现 Critical/Important，但不能将 429 视作独立审查通过。忽略目录 raw/build/summary 不提交；受保护 dirty 文件继续保留。
- 仍保持未验证/开放：完整 natural temporal quality、跨场景泛化、GPU/energy/持续资源、background 内 terminal timing、真实 physical touch、minimum OS、Android LiteRT-LM production/native drain/模型、Face Identity production model/PAD，以及 P4 native residual root cause。发布继续 `open/not-ready`，基础里程碑仍 10/12。

## 最新续接 — 2026-10-29 F08 固定自然视频五次真机语义质量

- 当前 Codex goal 仍为同一 active 目标，直接继续，无需替换 goal；本工作树仍为 `feat/sdk-foundation`，保护 dirty 文件未 restore、覆盖、stage 或提交。
- 修正 isolated consumer runner 的素材路径：设备复制步骤提供 `common-swifts-24fps.mp4`（SHA-256=`97682e3f9703e326bab3fbed06aa7a298b506d1965502288f5ed5037c51f2f1f`），runner 已与其对齐；生产 SDK、普通 Demo、签名工程和 Photos 均未改动。
- iPhone 16 Pro Max / iOS 27.0.1 使用签名 isolated App，在同一进程按固定 prompt 连续生成 5 次；App 沙盒只放公开 fixture/model。原始报告 `artifacts/verification/apple-mlxvlm-natural-video-20261028/quality/device-report-5.json`：`state=success`、5 个 distinct UUID、backend=`mlx.smolvlm2-500m`、model/runtime revision 与冻结身份一致，耗时 2119.134–2362.877 ms；SHA-256=`64a657dcb3c3ada3762fdd746b2a5b4caebbc81174f6bfdd57ec15e4635aefd0`。
- `quality/raw-runs.json`、`expected-identity.json` 已切换 `expectedRuns=5` 并逐行绑定原始报告/hash；validator 汇总 `summary.json` 为 subject/action/scene/semantic pass **5/5**，重复生成逐字节一致。`naturalTemporalQuality`、`gpuEnergy`、`sustainedResources` 仍明确 `unverified`，不把五次单素材语义命中写成完整自然质量。
- P11 readiness 已纳入五次原始报告：`29` evidence paths、`27` source hashes、`9` rows with gaps，validator 通过；新 grouped 快照为 `artifacts/verification/p11-grouped-benchmark-20261029-f08-quality-5/summary.json`，仍 3 groups / 9 coverage、gate=`open`、release=`not-ready`，自然质量报告不加入 benchmark sample。
- 本轮已通过 build、quality validator、P11 readiness、grouped deterministic regeneration、25/25 focused tests、`git diff --check` 和保护哈希复核；文档/manifest 提交为 `8df9c0c427e6de42a0d428cab716b3cdff439faf`，已按既有授权同步并核对 local/origin/jihu 三端一致。隔离 consumer 源码与原始 5-run JSON 仍为 ignored 证据，不纳入生产 SDK 或普通 Demo。

## 最新续接 — 2026-10-28 F08 自然质量 rubric 首包

- 按用户“继续”从 F08 可执行队列进入自然质量首包；设计与计划为 `docs/superpowers/{specs,plans}/2026-10-28-apple-mlxvlm-natural-quality-*`。当前只改 validator/测试/文档和 ignored 证据，不改生产 SDK、Demo、签名工程或保护 dirty 文件。
- `tools/mlxvlm_natural_quality.py` 及 6 项 focused unittest 已 RED→GREEN；评分器固定 rubric=`natural-video-semantic-rubric-v1`，要求复数 subject 并检查 action/scene 三类词义覆盖，且绑定原始 device report 路径与 SHA-256。对保留的 iPhone native run，1/1 semantic pass，重复汇总逐字节一致。
- 证据：`artifacts/verification/apple-mlxvlm-natural-video-20261028/quality/{expected-identity.json,raw-runs.json,summary.json}`。readiness 为 **28 evidence paths / 26 source hashes / 9 rows with gaps**，P11 仍 `open/not-ready`。这是单个既有 run 的 bounded semantic evidence，不是完整自然质量结论；`naturalTemporalQuality`、`gpuEnergy`、`sustainedResources` 和额外 distinct runs 仍未完成。

## 最新续接 — 2026-10-28 F08 Home 转场取消时序与 native provenance

- 当前 goal 保持 active，继续 `goal resume`，不需要替换。工作树为 `feat/sdk-foundation`；受保护工程、身份草稿和相机 probes 保持本地，不 restore、不覆盖、不 stage。
- iPhone 16 Pro Max / iOS 27.0.1 的独立 ignored 验证副本执行 `testNaturalVideoOSBackgroundCancellationAndReentry`：**1 passed / 0 failed / 0 skipped**，39.377 秒。真实 Files provider 选择公开 fixture；规范化报告为 `artifacts/verification/apple-mlxvlm-natural-video-20261028/home-timing-xctest/home-timing-device-report.json`，trace SHA-256=`07c99f6d13f6d44266bb9a81e8d244db58c9c0a09f92808806020580bbbfd699`。
- 同一 trace 保留 `nativeRequestAdmitted`、`firstNativeClientUpdate`、`willResignActive`、`nativeDrainAndLeaseRelease`、`bridgeCancelled`、`didEnterBackground` 及前台恢复/重入。取消 drain 在 `willResignActive` 后 41.882 ms，bridge terminal 再晚 1.185 ms；`didEnterBackground` 在 terminal 后 601.106 ms。因此只关闭 Home inactive 转场取消时序和 tagged request/backend/drain provenance；真正 background 内 terminal timing 仍为 `unverified`。
- P11 readiness 已加入新报告并刷新相关文档/source hashes：validator 为 **27 evidence paths / 25 source hashes / 9 rows with gaps**，仍 `open/not-ready`；新 grouped 快照为 `artifacts/verification/p11-grouped-benchmark-20261028-f08-home-timing/summary.json`，聚焦回归 **19/19**。GitHub/GitLab F08 #8 描述已更新并经 API 复核为 `OPEN` / `opened`，GitLab milestone=`active`；未评论、未关闭，receipts 在同一 ignored 证据目录。本轮文档/manifest 已提交为 `96576a59d7f1ccea851d767974cd67da1a468ccf`，并已同步、核对 `origin` 与 `jihu`。

## 最新续接 — 2026-10-28 F08 后台 observer 时序诊断增量

- 隔离 validation observer 为每条 UIKit notification 新增真实单调时间与 `applicationState`，保留 host phase/update snapshot，并禁止 snapshot 覆盖 observer-owned 字段。新增回归先因字段缺失 RED，再通过完整生成 Demo ABI XCTest **75/75、0 failures、0 skips**；原始日志和 xcresult 在 `artifacts/verification/f08-observation-timing-20261028/`，详见[验证记录](validation/f08-observation-timing.md)。
- 本包未重新运行真机，也没有关闭后台 terminal timing/native provenance 或其他 F08 gate；P11 readiness/benchmark 样本不变。下一次真实 Home 运行需保留含失败路径的 lifecycle snapshots，事件采样只能界定终态所在区间；native request/backend provenance 仍需独立 trace。独立 reviewer 本轮 429，未计 APPROVE。
- 当前 goal 仍 active，同一范围继续 `goal resume` 即可；仅在任务目标改变时另设目标。受保护工程、身份草稿与相机 probes 均保留。

## 最新续接 — 2026-10-28 F08 真机 Home 后台取消与前台重入

- 复用隔离 MLX Demo 验证副本和公开 `common-swifts-24fps.mp4`，在 iPhone 16 Pro Max / iOS 27.0.1 执行 `testNaturalVideoOSBackgroundCancellationAndReentry`。真实 Files provider 选择后等待共享页面 `RUNNING` 和非空 update，XCTest 按系统 Home 进入后台；回前台状态为 `CANCELLED`，随后1秒内无自动重启，同一输入再次生成成功，输出 backend=`mlx.smolvlm2-500m`。
- 真机 XCTest **1/1、0 failures、0 skips**，31.659 秒；build-for-testing 与 test-without-building 均成功。报告为 `artifacts/verification/apple-mlxvlm-natural-video-20261028/os-background-xctest/device-report.json`，原始 xcresult 与两个文本附件同目录。
- 该证据关闭 F08 的 Home往返取消/1秒不自动重启/前台重入子项；后台中 terminal 时序、自然质量、GPU/能耗、持续资源和完整物理触控仍未验证。项目内 notification 探针本轮仅返回 `OS lifecycle notifications timeout`，缺少足够事件，保留为失败对照且不据此归因。
- P11 readiness 已纳入新报告：**26 个 evidence paths / 24 个 source hashes**，9行仍有gap；grouped 快照为 `artifacts/verification/p11-grouped-benchmark-20261028-f08-background/summary.json`，仍3组/2项能力、`open/not-ready`。聚焦回归19/19通过。F01–F12 milestone 与 parent issue 已在两端创建，映射见[跟踪索引](validation/foundation-tracking.md)；F08 issue #8 仍应保持 open。前一 checkpoint 的 F08 Files 文档与 tracker 索引已分别提交为 `e5fd746`、`59f99a6` 并同步两端。

## 最新续接 — 2026-10-28 F08 真实 Files provider 共享 Demo 真机验证

- 基于 `c978628a7e3cb4f97f580ae307bb7f3218a9e1e1` 构建独立验证工程；固定公开 `common-swifts-24fps.mp4`（SHA-256 `97682e3f9703e326bab3fbed06aa7a298b506d1965502288f5ed5037c51f2f1f`）和模型仅放测试 App 沙盒。XCTest 从真实 Files provider 选择视频，未使用 iPhone 镜像、Photos 或私人媒体。
- iPhone 16 Pro Max / iOS 27.0.1（UDID `00008140-0008703436D3001C`）执行 `testNaturalMultimodalVideoFromRealFiles` 连续两次，均为 **1 test / 0 failures / 0 skips**，分别耗时 21.792、21.798 秒。共享页面显示选中文件；生成结果非空，phase=`SUCCEEDED`，实际 backend=`mlx.smolvlm2-500m`。规范化报告和两份原始 xcresult 路径在 `artifacts/verification/apple-mlxvlm-natural-video-20261028/real-files/device-report.json`。
- F08 `systemFileSelection` 子项已完成；真实手指触控、OS 后台、自然质量、GPU/能耗和持续资源仍未验证。P11 继续 `open/not-ready`。P11 readiness/grouped 校验见下文；双远端同步需网络恢复后执行。后续优先 OS 后台和自然质量，不重复本次 Files 路径。

### F08 Files 证据纳入 P11 readiness（2026-10-28）

- 新增真实 Files provider 原始摘要到 `multimodal-apple` readiness evidence 与来源哈希；readiness validator 检查 **9 行 / 25 个 evidence paths / 23 个 source hashes**，仍9行有缺口，gate=`open`、结论=`not-ready`。
- grouped manifest 仅刷新 readiness 摘要哈希，不把 UI acceptance 当作 benchmark 样本；新快照 `artifacts/verification/p11-grouped-benchmark-20261028-f08-files/summary.json` 仍3组、覆盖2项能力。
- 聚焦 P11 回归 **19/19**、Python `py_compile`、`git diff --check` 通过；保护文件哈希保持 `project.pbxproj=b35bd984…9fd8f5`、`face-identity.md=6f130bd9…70822`。远端连接目前 DNS 解析失败，尚未同步此批。

## 最新续接 — 2026-10-28 F08 自然视频共享 Demo 真机验证

- 在前包公开自然视频 native SDK 输出上，使用 `be75e326673cdcba88a10f3e2809b2c464fc83b9` 的生产 KMP/Swift 实现，生成 ignored 目录的共享 Demo 验证副本。物理 iPhone16ProMax/iOS27.0.1，PID29279、runId `6FEDC19F-01FA-48B8-B6CA-DF4B8E07E2EB`。验证入口只选 `multimodal-select-video` 并回调注入公开沙盒视频，实际共享状态 `inputKind=VIDEO`；没有改生产 SDK、普通 Demo、保护工程或媒体库。
- 首update后取消 `CANCELLED`、同进程重入 `SUCCEEDED`、首update后离开 `CANCELLED`、返回不自启后再次重入 `SUCCEEDED`。两次输出均为 `A group of birds fly in formation against a clear blue sky.` 且 Compose AX 文本/phase对应，11项gate全true。raw为 `artifacts/verification/apple-mlxvlm-natural-video-20261028/demo/device-report.json`，SHA-256 `7ee24d303635527524a94878859eab59a01858801edc16c0d09137d6ca52b931`；KMP离线构建及iOS签名build-for-testing均成功。自然质量、真实Files provider选择、物理触控、OS后台、GPU/能耗仍unverified。
- 本包将原始报告加入P11 readiness来源并重算grouped：24条证据路径/22个来源哈希、9行有gap，快照为 `artifacts/verification/p11-grouped-benchmark-20261028-demo-natural/summary.json`，仍3组/2项能力、open/not-ready。完整证据目录为 `artifacts/verification/apple-mlxvlm-natural-video-20261028/demo/`。独立review服务429未恢复，不计APPROVE；F08剩余是有区分力的真实系统选择/后台与自然质量裁定，避免重复本次注入式AX流程。

## 最新续接 — 2026-10-28 F08 公开自然短视频 native 真机证据

- 当前源码基点 `6b6f4a26c29a5c3e1ea1fec083cc040c539cd951`，只在 ignored 目录生成消费者/validator 的固定自然素材诊断副本，没有修改生产 SDK、普通 Demo、原合成门禁或受保护文件。沿用公共领域 Common swifts 的320×180/24fps/2秒H.264副本，hash `97682e3f9703e326bab3fbed06aa7a298b506d1965502288f5ed5037c51f2f1f`；素材和14文件固定模型只放独立App沙盒，不写Photos。
- iPhone16ProMax/iOS27.0.1，PID29258、runId `78F2A44B-8050-4A56-A404-6F882E1690FD`：success与reentry均输出 `A group of birds fly in formation against a clear blue sky.`；cancel在首update后终止。三轮8帧actual times=`[0,3072,6144,9216,12288,15360,18432,21504]/12288`；clip=2秒、sample span=1.75秒，7项lease/close/输入/清理gate全true。自然质量、时序理解和KMP/Demo交互不据此宣称通过。
- 证据目录 `artifacts/verification/apple-mlxvlm-natural-video-20261028/`，raw `device-report.json` SHA-256 `049550af32129447a0edf622c7758e1d6dbe9a05de539531ba49b0a4dacfdcec`。专用严格validator通过且拒绝6个篡改负例，原合成报告器测试7/7；签名构建exit0，launch/raw PID与source identity一致。独立verifier仍429，无review结论。
- **缓存路径更新**：旧 `~/Library/Developer/Xcode/DerivedData/VLMProbe-…/SourcePackages` checkout 已缺失。历史 `artifacts/verification/multimodal-mlx-full-app-9288ac5-20261024/SourcePackages` objects完整但alternate引用失效；复制到本包 `SourcePackages` 后仅修复11个副本路径，12个固定revision/clean前后均通过，锁文件不变。下一构建复用本包缓存，不能继续假设旧DerivedData可用；`apple-mlx/.build/checkouts/mlx-swift` 另有既有dirty submodule，未修改或使用。
- P11矩阵新增自然视频原始来源，readiness校验23条证据路径/21项来源哈希通过，聚焦回归19/19，当前grouped快照为 `artifacts/verification/p11-grouped-benchmark-20261028-f08/summary.json`；仍9行有gap、3组/2项能力benchmark、open/not-ready，本包不是benchmark样本。下一有区分力的F08工作是复用现有素材验证KMP/Demo短视频输入与UI终态；自然质量另建参考/评分依据。F04等待受控真人朗读窗口，最低系统仍暂缓，不重复既有F06或本次native套件。

## 最新续接 — 2026-10-28 F06 Apple 非零导出进度取消

- 独立 `ExportCancellationProbe` 使用当前生产 `AppleVideoAudioMuxer` 的诊断复制，在 host 先以 31,608,443 字节、1280×720、24 fps、2 秒 HEVC fixture 完成 3/3；随后清理独立 DerivedData 重新签名构建。包内 manifest、AAC、视频 SHA-256 与诊断目录逐项一致，未修改生产 mux、普通 Demo 或受保护工程。
- iPhone 16 Pro Max / iOS 27.0.1 当前报告 `runId=8163CFEA-3940-4272-A5C4-267C858BE2DF`，三轮均观察 `waiting → exporting → cancelled`，在进度 `0.0746666640`、`0.0533333346`、`0.0533333346` 时取消；当时部分输出为 1,347、943、943 字节。调用返回前输出均已删除，调用方均收到 `CancellationError`，source/processed video 哈希不变，三轮同进程 AAC 重入均为 1 轨且 payload 相同。
- 当前原始报告为 `artifacts/verification/video-export-nonzero-20261028/device-report-current.json`，SHA-256 `a4f323525b592b7a92945a80ecf9821c63b6fe27f94c870ef0d2e22e14cc0b6a`；内嵌 manifest SHA-256 `eb9115eaa0e4e8477d77a24a194d720d82af9e0d79f9e95aef60e81143f7e17c`。此前 7,464 字节 fixture 的 0% 与非零进度先行结果保留为历史对照，不作为当前证据身份。
- F06 的 Apple 非零进度部分文件清理子项已完成；Android 生产后端、自然质量/传递函数、GPU/能耗、持续资源和最低系统仍开放。P11 Video SR 移除此单项 gap，但该行仍 `pass-bounded`，整体 9 行仍有 gap，发布继续 `open/not-ready`。F11 证据矩阵、readiness 与 grouped manifest 已刷新：校验 21 条证据路径/19 个来源哈希通过，聚焦回归 18/18；新报告为 `artifacts/verification/p11-grouped-benchmark-20261028-f06/summary.json`，重复生成逐字节一致。F04 真实麦克风仍等待受控真人朗读窗口，F08 已有固定合成 fixture，不重复同构运行。

## 最新续接 — 2026-10-28 P11 reporter 映射门禁

- 对 `tools/p11_grouped_report.py` 做了一个有界完整性修复：`face` source 现在只能映射 `face-detection`，`mlxvlm` 只能映射 `multimodal-apple`；此前仅检查“能力名存在”，可能让错误 reporter 覆盖成另一行。新增真实 `write_report` 回归先 RED 后 GREEN，错误映射在任何输出写入前拒绝。
- 聚焦 P11 readiness/grouped 回归 **19/19** 通过；现有真实 manifest 重算仍成功，F06 新 grouped 快照两次输出逐字节一致。独立 code-reviewer/architect 审查通道本轮均因服务 429 无法返回证据，未把本轮标为 APPROVE；后续如服务恢复再做独立审查。

## 最新续接 — 2026-10-28 F11 矩阵同步

- F01 的真机 sandbox 证据现已写入 [P11 一页矩阵](validation/p11-status-matrix-20261027.md) 的 camera 行，真实 PHPicker provider 路径显式保留为未验证。相应 readiness source hash 与 grouped manifest 已刷新；`validate_p11_readiness.py` 为9行、21 evidence paths、19 source hashes、9行仍有gap，`open/not-ready`；分组报告在 `artifacts/verification/p11-grouped-benchmark-20261028-matrix/summary.json`，仍只有既有3组/2项能力。聚焦18/18测试及 `git diff --check` 通过，两个保护哈希未变。
- F04现有Task1–5实现/本地门禁已有证据，Task6真实麦克风取消/重入仍缺受控真人朗读与终态；未自动启动录音。F08固定合成视频的真机success/cancel/reentry/close已有证据，重复相同fixture无区分力。CoreDevice `device info apps/processes` 仍因服务初始化超时不可用，普通 Demo 状态未重新确认。下一步优先使用有区分力的新输入或恢复真机受控窗口；不把旧synthetic与静音对照写成自然质量通过。

## 最新续接 — 2026-10-28 F01 真机 sandbox 子项

- 用户要求继续推进 F01，但明确合成图片只放验证 App 沙盒，不写入真机 Photos。新增隔离 `PhotoSandboxDeviceHost`/UI-test 将三张 EXIF2/6/7 fixture 交给生产 `DemoController.useImage`，再观察生产 Vision bridge；没有调用 `selectPhoto()`、请求 Photos 权限或读取/写入照片库。
- iPhone 16 Pro Max / iOS 27.0.1（UDID `00008140-0008703436D3001C`）的 `build-for-testing` 与 `test-without-building` 成功，XCTest **3/3、0 failures、0 skips**。EXIF2=`80x40:white,black,white,black`，EXIF6=`40x80:black,black,white,white`，EXIF7=`40x80:white,white,black,black`；三例均返回 `apple.vision.face`、`faces=0`。
- 证据目录为 `artifacts/verification/p4-picker-sandbox-device/`，汇总为 `summary.json`；fixture SHA-256 和保护哈希均记录在汇总中。该包只完成 F01 的 sandbox 方向/镜像归一化与 Vision 子项，**真实 PHPicker→Photos provider→controller 仍未验证**，也不证明 EXIF 标签经 Photos 导出后保留；最低系统设备仍排除。
- 新增源码/脚本为 `probes/ios-photo-picker/PhotoSandboxDeviceHost.swift`、`PhotoSandboxDeviceUITests.swift` 和 `scripts/generate-photo-sandbox-device-tests.rb`。保留既有 tracked dirty 工程、身份草稿及 camera resource/thread probes；不恢复、不覆盖、不 stage 受保护文件。
- 验证 host 使用独立 bundle ID，没有替换普通 Demo。当前 CoreDevice 的 `device info apps/processes` 只读查询均因服务初始化超时失败，因此普通 Demo 当前安装/运行状态尚未重新确认；不要把此前历史恢复记录当成本轮确认。
- `artifacts/verification/p11-readiness-20261027/summary.json` 已按新的 P4 记录刷新来源哈希；readiness 校验为 9 行、21 evidence paths、19 source hashes，9 行仍有 gap，`open/not-ready`。`benchmark/p11/grouped-report-manifest.json` 同步更新哈希，新报告在 `artifacts/verification/p11-grouped-benchmark-20261028-f01/summary.json`；该报告为矩阵更新前的中间快照，以上方 `p11-grouped-benchmark-20261028-matrix` 为当前快照。

## 最新收尾 — 2026-10-28 用户要求结束本轮持续执行

- 用户明确要求“存在的问题记录下来，后续专项推进，尽快结尾”。据此停止新增Photos/native取证/模型实施；最终结果见[收尾记录](validation/roadmap-closure-20261028.md)和[专项清单](validation/foundation-followups.md)。每个专项保留问题、证据、恢复输入和退出标准；P4/P11不改成pass，发布门禁不放宽。
- 交付源码基点为`90d528c60726b4fd3f3ce23690dbb6125816fc91`。收尾包只选择文档与benchmark来源指纹；protected signing工程、身份草稿、相机resource/thread probe共9项本地文件保留，不stage、不commit。原picker计划Task2仍未执行，新增收尾注记记录延期。
- 接续中仅确认iPhone connected、passcodeRequired=false；未安装/启动测试App、未请求Photos权限、未写入/读取照片。device harness patch因格式验证失败未产生文件，随收尾决定不再重试。不能把可连接/解锁当成EXIF验收。
- 最终复核只检查文档链接/问题覆盖、readiness来源哈希与grouped报告重算、受保护文件和选择提交范围；不把这些检查称为新产品/设备/完整分支审查。同步沿用已授权的origin+jihu现有分支，不merge、tag或发布。后续按专项编号恢复，勿重新开始历史循环。

## 最新续接 — 2026-10-28 Video native export cancellation

- 以独立诊断 App 复制当前 `AppleVideoAudioMuxer`，保留 production source/fixtures/Demo/signing 不变；host mux 8/8、Ruby3.4 distribution 16/16，真机 iPhone16 Pro Max/iOS27.0.1 报告 `E5DC6A49-E240-4A04-A6B6-BAC7AAF40DE8`。
- 3/3 运行观察原生 `AVAssetExportSession` `waiting → exporting → cancelled`，调用方 `CancellationError`，输出在返回时不存在，source/processed video 哈希不变；3/3 同进程 AAC 重入 payload 相同。主机负向对照移除取消调用后以 native completed=3 被拒绝。
- 取消点进度均为0%，因此关闭“完全未观察到 native export 阶段取消”这一项，但不声称非零进度的部分文件清理已验证；P7/P11其余质量、资源、最低系统与Android门槛不变。证据见 `docs/validation/apple-video-audio.md` 与 `artifacts/verification/video-export-cancellation-20261028/`。

## 最新续接 — 2026-10-28 P6 当前源码分发复核

- 以当前 HEAD `c245d12` 生成 `artifacts/P6/microphone-locale-admission-20261028/`；iphoneos 与 iphonesimulator archive 均成功，产物实际为 **10 个 XCFramework**：Core、ModelStore、Face、AppleVision、Speech、AppleSpeech、Identity、Video、AppleVideo、Multimodal。逐包确认 `ios-arm64` 与 `ios-arm64-simulator` 存在，两个 slice 均为 arm64。
- 当前 `SpeechBinaryConsumer` simulator App 已构建成功，只链接/嵌入 Core、Speech、AppleSpeech。20 个 framework slice 的动态依赖检查确认只有 AppleSpeech 直接链接 `Speech.framework`；Speech 的两个 slice interface 只导入 Foundation/Core 与 Swift 运行时，没有跨入 AppleSpeech、AVFAudio、Video、Face、KMP 或 Demo。
- 这是当前源码的分发与依赖裁剪证据，不提升真机自然录音、中文质量、取消/重入、后台生命周期或最低系统状态。P6 Task6 仍开放；最低系统设备验收暂缓。后续按既有授权精确提交验证文档，保护工程、身份草稿和相机资源/线程 probe 保持本地。

## 最新续接 — 2026-10-28 P4 全范围审查与上游依赖结论

- fresh code-reviewer 只读覆盖 `e7c1484` camera isolation、`33dd8c4` synthetic PHPicker EXIF harness、当前 resource/page/thread probe、计划/ledger及冻结 Task12 证据；结果 **0 Critical、2 Important、0 Minor**。camera/controller/FIFO/terminal close、simulator picker边界和ThreadInventory资源释放未发现其他阻断。
- Important 1：默认 `CameraResourceValidation` 的失败报告若再次写入失败会被 `try?` 静默吞掉。新增主机回归先因 reporter 不存在得到compile RED，再加入有界 `NSLog` fallback后GREEN；当前生产App生成的全新resource probe在generic iOS15 unsigned build中 **BUILD SUCCEEDED**。首次build仅因受限环境SwiftPM/Xcode cache权限失败，同一生成项目在批准环境复跑通过；没有设备安装/启动。
- Important 2：受保护pbxproj的automatic signing/team diff不属于P4。按用户边界不restore、不覆盖、不stage、不commit；Face Identity草稿同样留在P10。排除本轮待提交文档后，审查涉及的本地保护/未完成子集为 **1 tracked dirty + 8 untracked（共9项）**；这不是完整 `git status` 计数，详情与hash见 `docs/validation/p4-full-review-20261028.md`。
- dependency-expert与官方release/source核验得到 `no-supported-upgrade-conclusion`：Compose1.12.2→Skiko0.150.2及Skia m153仍保留相同AtlasTextOp TLS单槽/调用线程ClearCache机制，不能把版本升级当作18×GrOp/2304B修复。当前不升级、不私调ClearCache、不重标Leaks；下一条有区分力的方向是单独设计非生产instrumented Skiko/Skia research build，不重复旧LLDB或同构301秒trace。
- Task12 ledger已用新鲜主机100轮、iOS15 typecheck、Swift5/6相机门禁、runtime/UUID/hash及17/17分配邻接脚本闭合；复用已完成的301.202178秒真机录制，不重复设备运行。P4仍缺native destructor/free/TLS证据、物理PHPicker→Vision EXIF和最低系统；P11仍`open/not-ready`，goal active。

## 最新续接 — 2026-10-27 P11 分组 benchmark 快照

- 新增 `benchmark/p11/grouped-report-manifest.json`、`tools/p11_grouped_report.py` 及回归测试；工具先验证 readiness/原始 JSON SHA-256、仓库内路径和全局 `runId` 唯一性，再分别委托既有 face/MLXVLM reporter 重算，输出覆盖前拒绝同路径、软链接和现有硬链接别名。
- 真实 manifest 生成 `artifacts/verification/p11-grouped-benchmark-20261027/summary.json`：3 个组（iOS face、Android face、Apple MLXVLM），2/9 项 readiness 能力有兼容 grouped report，7 项显式 `unverified`；face quality/peak memory/energy/GPU 未测，MLXVLM 30/30 仅 synthetic rubric，natural quality/Metal GPU peak/energy 未验证。gate 仍 `open/not-ready`。
- `docs/validation/acceptance.md` 与 `docs/validation/p11-status-matrix-20261027.md` 已链接首版报告并修正候选快照旧描述；详细合同见 `docs/validation/p11-grouped-benchmark.md`。readiness 校验为 19 evidence paths / 17 source hashes / 9 open rows。
- 原始输入 SHA-256 未变：iOS face `e4005b5d…927e7`、Android face `13d6e256…5131`、MLXVLM `186a2077…73d1`；readiness `933e10bf…37fa`。继续保留 8 项本地修改和保护哈希，不把忽略目录 artifacts 当作提交内容。
- 聚焦回归10/10、完整Python tools 166/166、`py_compile`、`git diff --check`及真实快照确定性重生成通过。独立code-reviewer因429重试耗尽没有结论；作者按review focus完成一次fix pass，不宣称独立审查通过。

## 最新续接 — 2026-10-27 P11 SDK 候选快照冻结

- 当前已在 linked `foundation` worktree，按 worktree 隔离规则不创建嵌套 worktree。直接从已同步双远端的提交 `7b9f7fc75772b61948d211d72ec2e1e221362591` 生成 committed-tree archive；内嵌 commit ID 精确匹配，tree `f60b65fc117908a9b82c60eb42285f8c855d6f91`，tar SHA-256 `6a2cb8afd895b13fa281d15f55046b3f263a1882aebd8ce31dff563bc3b2b666`。
- 候选源码归档位于 `artifacts/verification/p11-candidate-freeze-20261027/sdk-foundation-7b9f7fc.tar`，只含 committed tree，因此排除受保护工程、身份草稿和未完成线程/资源probe；原 `foundation` 工作树仍保持1项tracked dirty+7项untracked，保护hash不变。
- P11 SDK commit 冻结门槛由未就绪改为已就绪；整体候选仍 `not-ready`，Android LiteRT-LM授权模型/native drain、Face Identity生产模型/PAD/数据/校准、最低系统设备、统一质量/资源/能耗报告仍阻断。P11仍open，基础10/12与goal active不变。

## 最新续接 — 2026-10-27 Metal 单目标资源观察

- 解决受限沙箱无法初始化 InstrumentsCLI 用户缓存的工具入口问题；受控提权后模板列表可用。物理 iPhone16ProMax/iOS27.0.1 上，以已安装独立 VLMProbe、固定 manifest/model/公开 fixture 录制单目标 `Metal System Trace`：20.776271秒、PID28389、进程exit0；新报告runId `D8FEFBDC-94F8-425B-931C-474925C09DFC` status success，fixture/model/runtime identity不变。
- 新导出501条该PID GPU interval（495Compute/6CoreImage）、3,227条 `MTLDevice.currentAllocatedSize` 记录，观察峰值 **1,723,596,800B**，资源分配事件1,436条。原始trace/XML/report、带schema/引用/PID断言的汇总脚本和摘要见 `artifacts/verification/p9-metal-20261027/`；这是单次合成图独立probe的Metal分配观察，不能当作物理GPU驻留、RSS可加值、当前SDK完整验收或统一预算。
- Power Profiler All Processes被Location Energy Model拒绝，改为单目标后xctrace在 `NetworkConnectionStatsModeling.receive(row:writer:context:)` 触发SIGTRAP，trace缺模板无法导出；相关原始报告/诊断记录在 `artifacts/verification/p9-power-20261027/`。不重跑相同失败组合；能耗仍unverified。普通Demo已无参数启动恢复，见Metal证据目录restore-demo-launch.json。
- 更新移动端VLM记录、P11矩阵及readiness/candidate来源哈希，9行仍有gap，`open/not-ready` 与基础10/12、P4/P11、goal active保持。原8项本地修改及保护hash保留；本包按既有授权精确提交文档并同步origin+jihu，实际SHA以新鲜远端检查为准。下一独立项需提升真实资源/质量/最低系统或production门禁，避免重复仅有GPU activity的录制。

## 最新续接 — 2026-10-27 P11 一页状态矩阵

- 新增 `docs/validation/p11-status-matrix-20261027.md`，把 readiness 快照的 9 项能力、bounded 状态、证据链接、未验证项和 `not-ready` 发布阻断整理为可审阅矩阵；来源哈希已纳入 `artifacts/verification/p11-readiness-20261027/summary.json`，校验结果为 16 条来源哈希、9 行未达 terminal pass。
- 矩阵不提升任何能力状态，不接受许可证或下载模型；P4 原生残留、LiteRT-LM 模型/drain、Face Identity 生产模型、最低系统与统一质量/资源报告仍是发布阻断。提交 `00b11a1d255473f91d1a3c02d5e3c1fe60f48baa` 已同步 origin+jihu，goal active 保持。

## 最新续接 — 2026-10-27 ModelStore published SDK consumer 复验

- 以提交 `1102978bd9f66b56a09d77371a734f38152027cc` 重新发布 `sdk-core`、`model-store`、`multimodal` 和 `backend-litertlm` release AAR 到本地 Maven，并使用 `-PusePublishedSdk=true` 构建 `examples:android-litertlm-native`；**147 tasks / 22 executed，BUILD SUCCESSFUL**。摘要和完整日志见 `artifacts/verification/android-lease-cancellation-20261027/published-consumer-current.json` / `.log`。
- 该结果确认分发 consumer 使用当前发布依赖链；它不增加模型资产、生产 factory、native drain、设备推理或 P11 状态。原8项本地修改与保护hash继续保留，goal active。

## 最新续接 — 2026-10-27 Android ModelStore 取消交付 lease 回收

- 独立 LiteRT-LM consumer review 再次429无结论后，转向共享资源门禁，发现 `ModelStore.acquire()` 在 IO 已登记 lease、调用方尚未接收时取消，会丢弃返回值并永久 pin 模型。手动排队 dispatcher 的真实 store 回归得到 **1 test/1 failure**（取消后 remove 抛 ModelInUseException）；修复在取消退出时 NonCancellable 回收尚未交付的本次 lease，不改正常返回后的调用方所有权。
- 最终 ModelStore JVM **12/12**，含取消后可删与另一 owner 仍 pin 的两个回归，release AAR构建通过；LiteRT-LM JVM **25/25**、两模块 lint/consumer 构建通过（388tasks/51executed）。仓库 `verify-android.sh` 通过（725tasks/55executed/670up-to-date）；最后测试失败清理保护更新后，ModelStore全套与release重新通过（62tasks/8executed）。没有新编译warning或lint error；既有lint提示保留。
- 新修复的限定独立review仍429无结论，不计APPROVE。证据冻结于 `artifacts/verification/android-lease-cancellation-20261027/summary.json`，详见 `docs/validation/p2-model-store-review.md`。本轮无设备操作/媒体/模型下载；不把JVM取消交付证据提升为native drain或真机推理。
- 按既有授权仅提交本次源码、回归与记录，再同步origin+jihu；实际结果见该证据目录sync-summary.json及新鲜远端SHA。原8项未提交修改保留，保护hash不变；基础10/12、P4/P11和goal active继续保持。下一步仍需解决受支持native drain、授权模型和P4原生残留/EXIF等实际门禁，不重跑旧LLDB变体。

## 最新续接 — 2026-10-27 LiteRT-LM 可选 Kotlin consumer

- 继续 Task 5，新增公开只读 `LiteRtLm.availability` 和独立 Kotlin `examples/android-litertlm-native`；真实 `ModelStore`、API/ABI 加固定 `UNPROVEN`，缺资产显示 `REQUIRES_ASSETS/MISSING_ASSETS`、已安装资产仍为 `UNAVAILABLE/UNSUPPORTED_DEVICE`。不构造 production backend、不下载模型、不生成 fixture 输出；Task 4 及 Task 5 generation/close 消费仍开放，裁定已写入设计/计划与本地 ledger。
- backend JVM **25/25**、consumer **2/2**、multimodal **48/48**；project consumer/default Demo/AAR/test APK/lint 构建通过，594 tasks。lint 无 error，backend 2 条既有版本提示，consumer 3 条 API/icon/i18n warning 保留。四 SDK 模块本地 Maven 发布后，consumer `-PusePublishedSdk=true` 构建通过，37 tasks；runtime 精确0.18.1，默认 Demo 与 base multimodal 新鲜图均不含 LiteRT-LM。AAR 工具7/7及固定vendor artifact校验通过。
- TECNO KM9/API35 backend **4/4**、consumer Activity UI **1/1**，均无 failures/errors/skips。公开查询用例使用真实 Build 与已安装合成文件，确认仍 unavailable、无 generation cache 且可正常 remove；UI 读取真实 TextView 缺资产状态。无模型推理、native drain 或最低系统验收。设备命令首次自动权限审核超时未执行，提示允许的一次重试后成功；受限沙箱 ADB 失败不是断连证据。
- 独立审查首选模型容量不足、备用模型429，未取得结论，不计为通过。证据冻结于 `artifacts/verification/android-litertlm-consumer-20261027/summary.json`；新包按用户授权提交并通过 `scripts/sync-remotes.sh feat/sdk-foundation` 同步，实际结果以本地 `sync-summary.json` 与新鲜远端 SHA 为准。原8项受保护/未完成修改保留；模型、production factory、真实drain/推理、API26及完整review仍开放，基础10/12、P4/P11、goal active 不变。

## 最新续接 — 2026-10-27 P11 证据哈希覆盖门禁

- 发现旧 readiness 校验只核对 16 条证据路径存在，却仅固定 5 项来源哈希；13 个独立证据文件中 10 个可以在内容变更后仍通过校验。真实测试目录复现了该误判。先新增回归得到 **8 tests/1 failure**，再让校验器拒绝任何未纳入 `sourceHashes` 的证据；修复后聚焦 **8/8**，旧快照如预期列出全部10项遗漏。
- 忽略目录快照现纳入 15 项来源哈希（包含全部13个独立证据文件）。当前校验仍为 9 行/9 gaps/16条证据路径，`open/not-ready` 不变；Python 工具完整套件在可写Swift编译缓存的环境中 **156/156**。受限沙箱首次完整运行的2项 Swift 错误为模块缓存 `Operation not permitted`，同一源码随后完整复验通过。此包只提升 P11 证据完整性，不增加产品/设备/发布验收。
- 前一检查点的远端网络判断仅适用于受限沙箱。已授权提权运行 `scripts/sync-remotes.sh feat/sdk-foundation`，`0c6559d` 成功同步至 `origin` 与 `jihu`，脚本完成新鲜远端SHA相等检查；本包后续提交仍需单独同步。8项受保护/未完成本地修改继续保留。

## 最新续接 — 2026-10-27 P11 快照来源漂移与同步通道

- 巡检起点 `feat/sdk-foundation` 为 `ec8308d`；既有 P11 readiness 快照的 `mobile-vlm.md`、`p4-atlas-text-cache.md` 两项来源哈希因后续已提交证据变化而失配。重新按当前文件刷新忽略目录中的 readiness/candidate 盘点并运行只读校验；九项能力仍全有 gap，`open/not-ready` 不变。快照刷新只证明来源完整性，不增加模型、真机推理、质量、资源、最低系统或发布证据。
- 原有 1 项 tracked dirty 与 7 项 untracked 保留；两个保护文件哈希仍为 `b35bd984…fe9fd8f5`、`6f130bd9…bb70822`。Android `adb devices -l` 当前因 USB plugin/smartsocket `Operation not permitted` 失败；本机没有 iOS15 simulator runtime 或 Android API26 AVD，物理 iPhone 列表中的 connected 状态未作为新的验收。
- 本次在受限沙箱内重新读取远端遇到 `github.com` DNS 失败，`jihu` 直连 DNS 失败且现有 `127.0.0.1:7897` 代理未监听。当时本地 remote-tracking refs 均为 `ec8308d`；随后受控提权同步成功，结果见上方最新续接。

## 最新续接 — 2026-10-27 P4 分配时间与线程快照邻接复核

- 对既有同 PID 301.202178 秒 trace 的完整 Allocations List、Leaks 明细和 236 个 own-task 快照完成只读时间对齐：18 个最终 live `GrOp` 中 1 个录制前已有，trace 内其余 **17/17** 个分配在估计时刻前后约1.1秒的快照中都观察到同一个 kernel thread ID。7 个导出低32位标签在不同分配窗口对应多个不同 kernel TID，结果与逐行邻接时间保存在 `artifacts/verification/p4-thread-calibration-20261027/allocation-snapshot-correlation.json`，可复核脚本为同目录 `correlate_allocations.py`。
- 这只增加分配时刻的观察映射，不能证明析构/free、TLS槽值、精确线程退出或长期增长；快照非原子，marker 到 trace 原点有调度误差。没有新设备操作、没有依赖或生产代码变更，原始18×GrOp/2304B结论和 P4/P11 开放状态保持。

## 最新续接 — 2026-10-27 LiteRT-LM unavailable 真机合同覆盖

- 新增计划 Task 5 的 Kotlin instrumentation 与测试 runner 配置；真实 `ModelStore` 在 UUID 私有目录安装带 SHA-256 的合成文件，覆盖缺资产、已安装但 native drain 未证明、重复关闭三种状态。检查具体 availability/generate 拒绝、factory/输出次数为零、无 generation cache、remove 无残留 lease及精确目录清理。生产源码与 factory 边界未改。
- TECNO KM9/API35 初次 **3/3**；临时将测试 setup 的 `UNPROVEN` 替换为 `PROVEN_CUMULATIVE` 作负向对照，得到预期 **3 tests/1 failure**（UNAVAILABLE→AVAILABLE）；恢复后正式设备 XML **3/3、0 failures、0 errors、0 skips**。强制重跑 JVM **23/23**，最终 lint 0 errors/2 既有版本提示、AAR/test APK 构建通过；默认 Demo 依赖图排除 LiteRT-LM，backend runtime 精确0.18.1且无runner/JUnit。证据冻结于 `artifacts/verification/android-litertlm-unavailable-20261027/summary.json`，不把合成合同覆盖记为模型推理或 native drain。
- 新限定独立审查返回429无结论，未重派；一次额外runner注册查询也因自动审批服务429未执行，实际runner执行由设备XML证明。生产factory/consumer、授权模型、真实drain与API26真机仍开放，基础10/12、P4/P11及完整roadmap goal保持active。
- 补齐上一段工作留下的iOS恢复记录：页面resource probe曾构建并安装但未启动，未重复301秒录制；普通Demo随后已恢复并无参数启动 **PID28142**。本轮读取 `/tmp/odai-camera-resource-page-20261027/restore-launch.json` 确认arguments为空，恢复包executable SHA-256仍为 `5852045750eae0f390b89de2b0f0f1b3b84ea7bdbfe0e1a7546ba3a077eea96d`。这不是新的iOS设备验收；本轮设备测试只操作Android独立test package。
- 本批按既有授权提交Kotlin验收代码和验证文档，再以 `scripts/sync-remotes.sh feat/sdk-foundation` 同步origin+jihu；实际远端结果以本证据目录的 `sync-summary.json` 和新鲜ls-remote为准。保护工程、身份草稿与未完成线程/资源probe共8项继续留本地，原始证据/签名产物保持ignored。

## 最新续接 — 2026-10-27 P4 ThreadInventory 主机门禁刷新

- 新鲜本地主机验证编译 `probes/ios-camera-lifecycle/ThreadInventory.swift` 与 `ThreadInventoryChecks.swift`（Swift 6）并执行100轮快照；正常状态为 `complete-snapshot`，send-right 引用 **2→2**，注入缺失 pthread 映射时正确降级为 `partial-snapshot`（1 次 lookup failure），4097B marker 保持。
- 同一源码通过 iOS15 arm64 typecheck，`scripts/generate-camera-resource-app.rb` 通过 Ruby 3.4.11 语法检查。证据：`artifacts/verification/p4-thread-inventory-local-20261027/summary.json`。
- 这是 host ownership/snapshot release 与生成器边界证据；不证明真机线程生命周期、TLS/destructor 归属、GrOp 根因或零泄漏。当前 candidate/readiness 快照在本检查点刷新，P4/P11 仍开放。

## 最新续接 — 2026-10-27 P11 candidate freeze 预检

- `artifacts/verification/p11-candidate-freeze-20261027/summary.json` 的状态为 `not-ready`，仅作候选冻结前盘点，不冻结发布候选、不接受许可证、不下载模型，也不提升任何未验证行。
- 当前 SDK 工作树仍有 1 个 tracked dirty、7 个 untracked；受保护工程、身份草稿和线程/资源 probe 按边界保留，因此没有单一 clean candidate snapshot。
- 仅 Apple MLXVLM 的既有合成图观察值可冻结（model `fa57db46815177fbdfd65cc85a2b3416a8332268`、runtime `6e338e5df49424fc0db7c6d58184d622f92ed18a`、fixture SHA-256 `60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606`）。LiteRT-LM 授权 manifest/weights、Face Identity 生产模型/PAD/数据/校准、iOS15/Android API26 设备、GPU/energy/峰值资源以及统一跨能力 fixture 仍缺失。
- 该预检不改变基础 **10/12**、P4/P11 开放状态或 goal active；下一步仍须先完成 `requiredBeforeP11` 的候选冻结、逐能力矩阵和发布结论。

## 最新续接 — 2026-10-27 P11 readiness 快照校验门禁

- 新增只读 `tools/validate_p11_readiness.py` 与 `tools/tests/test_p11_readiness.py`。校验器强制 `p11Stop`/`requiredBeforeP11` 停止契约，并拒绝缺失/越界证据、来源哈希漂移、门禁计数不一致，以及仍有非 terminal 行时的 `closed/ready` 结论；不会修改快照或工作树。
- 当前快照新鲜校验通过：9 行、9 个未验证 gap、16 条证据路径、5 个来源哈希；`p11Gate.status=open`、`finalReleaseConclusion=not-ready` 保持不变。证据与原始日志见 `artifacts/verification/p11-readiness-validator-20261027/summary.json`。
- 使用显式 Ruby 3.4.11、临时 SwiftPM `--disable-sandbox` wrapper（只作用于测试子进程）重跑完整 Python 工具套件 **154/154、0 failures、0 errors**；`git diff --check` exit0。此前未设置 wrapper 的单项失败是当前受限环境的 SwiftPM sandbox 错误，不是产品/校验器失败。
- 本轮只推进 P11 证据完整性，未新增设备、模型、质量、资源、最低系统或发布证据；基础10/12、P4/P11、goal active保持不变。

## 最新续接 — 2026-10-27 CoreDevice 通道恢复但 EXIF 仍受 harness 边界限制

- 新鲜 `devicectl list devices` 与 `device info lockState` 均 exit0；物理 iPhone 16 Pro Max 当前 `connected`、`passcodeRequired=false`、`unlockedSinceBoot=true`。本轮没有安装、启动或访问媒体。
- Android ADB 仍因 USB plugin/smartsocket `Operation not permitted` exit1。iOS EXIF 计划仍明确为 simulator-only，生成器固定 `SUPPORTED_PLATFORMS=iphonesimulator`；不通过向物理 Photos 写入合成资产来绕过该边界。P4 真机 PHPicker→Vision EXIF2/6/7 继续 `unverified`。
- 最新通道/harness 状态（不含设备标识）更新于 `artifacts/verification/p4-device-gate-status-20261027/summary.json`；这只证明通道可用，不关闭 P4、最低系统或 P11 门禁。

## 最新续接 — 2026-10-27 P4 设备通道与 EXIF harness 边界刷新

- 本轮首次 `devicectl list devices` 曾列出物理 iPhone 16 Pro Max 为 `connected`，立即重查却因 CoreDeviceService timeout 退出；没有安装、启动或测试。Android `adb devices -l` 因 USB plugin/smartsocket `Operation not permitted` 退出。该瞬时连接状态不计为设备验收。
- 现有 `scripts/generate-photo-picker-tests.rb` 明确只生成 `SUPPORTED_PLATFORMS=iphonesimulator`、iOS15 的 host；没有把物理设备或 Photos 写入作为隐式 fallback。当前没有安全、可复核的真机合成 EXIF fixture 注入路径，因此 P4 真机 PHPicker→Vision EXIF2/6/7 继续 `unverified`。
- 设备通道与 harness 证据（不含设备标识）见 `artifacts/verification/p4-device-gate-status-20261027/summary.json`。本轮没有重复页面 trace、没有访问私人媒体、没有修改签名或保护工程。

## 最新续接 — 2026-10-27 P4 线程标签校准与原生残留复验

- 延续 Task12 的 own-task 取证，先发现并修正完整性缺陷：`pthread_from_mach_thread_np` 缺失映射不能再报告 `complete-snapshot`。新增 lookup seam 后先得到稳定 runtime RED，再修正为 `partial-snapshot` 并记录 `pthreadLookupFailures`；100轮 macOS 宿主快照、JSON 序列化和 send-right **2→2**，缺映射回归通过，iOS15 Swift6 warnings-as-errors typecheck exit0。签名设备 probe build exit0；没有改保护工程或安装资产。
- 在已解锁 iPhone 上使用显式 `--camera-thread-inventory` 完成当前源码生成 probe、三轮 page exit/reentry 与同 PID **301.202178s** Leaks 录制；运行 ID、PID、安装路径、trace 时间和实际 binary UUID 已严格关联。三轮 active 窗口新增 **687/661/660**，停止5秒无新增；页面退出后最后 owner、bridge、Compose controller、Metal layer 均在约 **32.643s** 首次全释放，约61.986s仍为0。普通 Demo 已从当前核验 restore 包恢复并无参数启动，容器保留。
- 新鲜 trace 的最终 Leaks 为 **18×GrOp/2304B**，18项全部在完整 Allocations List 中为 live；导出读取 **108263 rows / 85579214 bytes，exit0**。4097B marker 实际分类为5120B，分配栈命中 `ThreadInventoryMarker`，其导出 thread label 与记录的 pthread low32 candidate 一致，完成了标签格式校准。8个 GrOp allocation labels 均能匹配快照候选值；其中7个完整 pthread 指针各对应多个不同 kernel thread ID，证明相同导出标签不能直接代表同一持续存活 OS 线程。
- 取证仍有限：快照为非原子 own-task 列举，236个快照中66 complete/170 partial；partial 原因仅为 pthread 查询缺失，未见 thread_info、send-right release 或 VM array release 错误。没有观测析构/free 线程、TLS 槽地址或线程退出事件，因此不能关闭 GrOp、宣称泄漏是误报、或推导长期增长/平台修复。新限定审查仍因429无结论，不写成 APPROVE。
- 线程校准结束后重新运行当前生产相机源码门禁：`scripts/check-camera-concurrency.sh` exit0，Swift5/Swift6 transfer checks 均通过，目标 iOS15；日志与 exit 文件在同一证据目录。该源码门禁不替代 iOS15 真机运行、原生释放或全范围 P4 review。
- 为 P11 建立只读统一准备矩阵 `artifacts/verification/p11-readiness-20261027/summary.json`：9 项能力均保留至少一个未验证项，`p11Gate.status=open`、`rowsNotTerminalPass=9`，发布结论明确为 `not-ready`。它只汇总当前证据和阻断项，不把分包 pass-bounded 结果升级为最终验收。
- 证据冻结在 `artifacts/verification/p4-thread-calibration-20261027/summary.json`、`snapshot-summary.json`、`candidate-thread-mapping.json`、`verify_runtime.py`、trace/导出及恢复日志。线程取证源码、生成器、资源 probe、保护工程和身份草稿继续留在本地 dirty/untracked；本轮未提交或再次同步。基础10/12、P4/P11、LiteRT-LM生产factory/模型/真实drain及完整交付继续开放。

## 最新续接 — 2026-10-27 已验证工作包分批提交与双远端同步

- 用户明确授权“分批提交已验证改动，并同步 origin + jihu”。此前各计划/历史检查点的 no commit/push 限制已被本次授权覆盖；只同步现有 `feat/sdk-foundation` 分支，不涉及合并、tag 或发布。
- 提交前远端 origin 为 `9288ac5`，本地共有98项未提交路径；有可独立交付的成果，长期没有提交/同步是交付收尾遗漏，不是没有代码。已完成六笔代码/验证工具提交：`2f30b2a` benchmark输入完整性、`aab883c` MLXVLM 30样本校验、`8378c9a` 多模态picker与Kotlin fixture、`e7c1484` 相机并发/关闭合同、`96f03bb` 可选LiteRT-LM基础门禁、`33dd8c4` 合成PHPicker EXIF验证。本检查点与其余验证文档组成最后一笔文档提交。
- 本次新鲜复验：聚焦Python29/29；Android Demo38/38、LiteRT-LM23/23、probe9/9均强制重跑，lint/AAR/APK/test APK构建exit0（693tasks、24executed）。Apple当前picker/bridge/store/factory模拟器套件75/75、0failures、xcodebuild exit0；既有测试QoS warning仍在。相机此前同源码host470/470、simulator8/8、TSan7/7、真机native11/11及Compose UI3/3证据保留；EXIF harness hash与历史模拟器3/3一致，不将历史EXIF结果升级为当前controller改造或真机验收。
- 始终按精确路径暂存，每批验证空index/实际提交文件/父提交；保护工程与身份草稿hash不变。另将整个未完成资源取证工具组留本地：`CameraResourceValidation.swift`、`CameraPageResourceValidation.swift`、`ThreadInventory.swift`、`ThreadInventoryChecks.swift`、probe README和`generate-camera-resource-app.rb`。共8项保留本地，不纳入远端；原始报告/trace/签名产物仍在ignored目录。
- 同步目标是包含本节的foundation HEAD，使用`scripts/sync-remotes.sh feat/sdk-foundation`，仅允许快进；先origin再jihu，完成后新鲜ls-remote核对两者与本地SHA相等。冻结清单、每笔commit、保护hash、验证日志、同步输出和最终结果保存在`artifacts/verification/batched-sync-20261027/`；恢复时须读取`sync-summary.json`并刷新远端，不能仅凭本节推断同步成功。真实设备标识已从本轮改动文档替换为占位符。
- P4线程校准准备已完成own-task Mach/pthread快照、已知4097B标记分配及显式opt-in接线；100轮宿主快照/send-right计数2→2、iOS15 Swift6 warnings-as-errors typecheck及签名device build通过。libproc handle不等于pthread指针，保留为独立原始字段；尚未安装或录制真机，review因429无结论，不能证明trace线程/TLS归属或关闭GrOp项。下一步先核查快照完整性与标记相关性，再做有界设备取证；不得重复已失败的五种LLDB变体。证据`artifacts/verification/p4-thread-inventory-20261027/`。
- 普通Demo最近恢复仍为下方当前Compose UI检查点：PID28004，恢复产物SHA256 `5852045750eae0f390b89de2b0f0f1b3b84ea7bdbfe0e1a7546ba3a077eea96d`；同步阶段没有操作真机。基础10/12、P4/P11及完整交付继续开放。LiteRT-LM生产factory、授权模型、真实native drain与推理未完成，既有模型问题待答，不重复询问或代为接受许可；goal保持active。

## 最新续接 — 2026-10-27 当前源码 Compose 相机 UI 验收

- 延续P4 Task10后的真实UI缺口；本次没有修改产品或测试源码。iPhone16ProMax/iOS27.0.1在线、passcodeRequired=false；确认无并行构建/测试后，以受保护原工程及现有签名build-for-testing成功，无provisioning更新。源码/设备KMP framework/工程hash冻结且结束时未变。
- 原有控件稳定、两轮前后台恢复/用户停止/手动重启用例 **2/2、0failures、0skips**（27.409秒）。UI runner本次正常进入测试体，未重试旧失败；两分钟持续UI用例 **1/1、0failures、0skips**（131.868秒），四窗口新增748/750/749/750，实际窗口120.457944秒，起始50、最终3047；停止后5秒UI无新增结果。补齐当前源码三项Compose camera UI门槛，不取代native资源释放。
- artifact-only xctestrun关闭系统附件保留、保留用户文本；完整导出后短测0附件，持续测试只有4份计数txt，无图像/视频。未访问相册或保存相机画面。PHPicker合成方案仍仅适用独立模拟器，真机缺安全的合成资产选择路径，因此EXIF2/6/7未验，未改写为通过。
- 同一普通Demo已签名核验并无参数恢复 **PID28004**，executable URL为`F264C05D-AF8C-42E3-99C2-A5529CB2A26E/OnDeviceAIDemo.app/OnDeviceAIDemo`。本轮build-for-testing更新了原/tmp恢复目录产物；新的executable SHA256为`5852045750eae0f390b89de2b0f0f1b3b84ea7bdbfe0e1a7546ba3a077eea96d`，旧115635ec hash仅为历史，后续不得套用。未卸载/清数据，sharing overrides缺席。
- 命令均结束；xcresult、原始日志、四份计数、manifest及`summary.json`在`artifacts/verification/p4-current-camera-ui-20261027/`。两个保护hash不变，既有dirty/untracked保留，无commit/push。Task11只关闭三项当前UI验证；P4原生GrOp、真机PHPicker→Vision、最低系统、全范围review及P11仍开放，基础10/12、goal active。

## 最新续接 — 2026-10-27 Android drain 假设撤销与进度记录同步

- 用户再次询问5/12；当前README/ROADMAP/阶段审计为基础 **10/12，P4/P11未完成**。修正mobile-vlm仍像当前状态的一条旧5/12，保留历史数字与完整扩展范围，不把计数转为整体完成率。既有dirty/untracked及两个保护hash保留。
- 固定LiteRT-LM0.18.1 AAR字节码证实：`isAlive`只读AtomicBoolean，cancel不改它，close在nativeDeleteConversation返回前已置false。原`terminalCallbackThenDeadConversation`不可作为drain证明；设计/计划已撤销该判据，未批准或启用替代协议。
- 官方tag `v0.18.1`为`bda624851d74be34d4e1f7fac1fc4155939539c7`；JNI terminal之后仍清理callback引用，SessionAdvanced析构忽略WaitUntilDone错误，默认timeout为10分钟。源码与AAR内.so的构建对应及真实安全barrier均未证明，不能用withTimeout(30s)宣称阻塞JNI有界。
- probe两项误判回归先9tests/2failures，修正后JVM **9/9**，lint/APK/test APK构建 **exit0、80tasks/16executed**。schema2增加`drainEvidence=unsupported-conversation-lifetime-flag`，drainProven固定false，累计callback也只报`unverified-native-drain-contract`；移除错误证明后的close分支，send后保留native资源。旧30秒轮询仅作原始观察，不是新协议；旧设备schema1报告不重写。证据和局限见`docs/validation/android-litertlm-drain-contract.md`及`artifacts/verification/android-litertlm-drain-contract-20261027/`。
- 本轮无模型下载/运行、无设备写操作，生产factory/consumer保持未提供，授权模型仍待答，不重复询问。本次限定独立审查因429无结论，不重复派发；证据摘要/source manifest已冻结，diff check及保护hash复核通过。下一步须先建立真实native barrier，不能以fake测试替代。其余P4/P11工作仍可继续，goal保持active。

## 最新续接 — 2026-10-27 相机独立审查通过与MLX增强构建

- 上轮实际源码/真机11项证据完成后，对最终isolated deinit与Identity测试排空屏障修订做了新的独立审查。`camera_isolation_review`返回**APPROVE、0 findings**，范围仅为CameraSource/Controller并发所有权、相关合同/测试/脚本和Task10，不是全P4或原生零泄漏审查。此前429日志继续保留，当前限定审查缺口已补齐。
- 新生成工程启用Speech/Video/Multimodal/MLX全部开关，固定`apple-mlx/Package.resolved`、禁用自动解析/更新，generic iOS Debug unsigned构建**exit0**。源码manifest及pins未变，增强App minOS18.0符合既有可选后端边界；未安装、未下载模型、无新增MLX推理验收。证据`artifacts/verification/p4-camera-mlx-integration-20261027/summary.json`与`independent-review.md`；没有重跑上一轮已通过相机持续/关闭测试。
- 正转向已批准Android LiteRT-LM Task4的实际adapter边界。授权模型仍缺席，不能启动生产推理；先核对固定AAR的`Conversation.isAlive`及terminal/drain语义，避免将未经证实的runtime假设写入生产wrapper。未修改Android生产行为或对外启用backend。模型待答问题不重复询问。
- 所有相机编译命令已结束，普通真机Demo仍以上轮恢复PID27961记录为最近写操作。两个保护hash/所有dirty保留，无commit/push。P4原生GrOp、真实picker/UI/minOS及全范围P11继续开放，基础10/12、goal active。

## 最新续接 — 2026-10-27 相机并发隔离、11项真机回归与普通Demo恢复

- 已进入批准的相机计划Task10，生产`CameraSource`改为不可变Sendable入口、单AsyncStream命令消费者和专用串行executor上的capture actor；session/terminal状态由actor独占，start/stop/close保留FIFO。回调为Sendable，只有NSLock保护的私有callback slot保留窄范围unchecked声明；回调调用/旧capture析构均在锁外，close排空frame队列。`DemoController`及Kotlin action conformances隔离至MainActor，每代callback捕获自身队列，保留同步有界帧接入。
- 真实Sendable contract先编译失败；生产源修复后Swift5/Swift6 complete concurrency、iOS15 deployment均exit0且无诊断。新增`scripts/check-camera-concurrency.sh`并接入Apple门禁。新模拟器完整生命周期**8/8、0 failures**，含facade释放后worker自动关闭的新用例；首轮exit74是CoreSimulator/Swift缓存沙箱限制，授权重跑通过，失败原件保留。证据目录`artifacts/verification/p4-camera-isolation-20261027/`。
- 当前本地证据：完整simulator生命周期8/8（isolated deinit修复后再验8/8），TSan相机专项7/7并确认19个插桩符号；Apple门禁、默认Demo与语音/视频/多模态可选Demo构建通过。可选首次构建暴露Published状态的析构隔离错误，已改为isolated deinit；保留SDK回溯部署实现及实际对象符号证据。全量host首次469通过/1项Identity测试busy，定位为测试未等待异步stop排空；仅把既有close屏障移到reopen前，修正后完整470/470。所有失败日志保留，不修改Identity产品逻辑。
- 普通Demo当前源码恢复包已在`/tmp/odai-p4-camera-isolation-restore-20261027/Build/Products/Debug-iphoneos/OnDeviceAIDemo.app`构建/签名校验通过，executable hash `115635ecf9db39285a159ab4731d71fbd17051e6a9727f4b836535c7b430ec35`。第一次审批服务429未执行，后续正常审批重试成功；沙箱内codesign信任错误也经授权只读校验消除。新真机测试包已通过**11/11、0 failures、0 skips**，包括当前源码两分钟controller→bridge→Vision、暂停/恢复/FIFO及关闭/释放；原9/9仅保留为改造前历史。`camera_isolation_review`及兼容性researcher均429，无独立结论；不重复派发。
- 真机iPhone16ProMax/iOS27.0.1、PID27956，持续runID `0D4BFED6-DAD0-49B0-9A58-EA9E94A56622`：四个30秒窗口新增747/747/748/747，活动120.054737秒、总2992次成功；停止5秒不增加、inputs/jobs0，owner/bridge/detector关闭释放通过。暂停/恢复runID `274E9FCC-E934-44AA-8373-14EC8F7B13EC`计数3/6/7/10，关闭后仍10，start/stop通知各3，输入输出1/1→0/0。没有保存画面；该证据不覆盖Compose渲染或零泄漏。
- 已从当前恢复包重新安装普通Demo并无参数启动**PID27961**，安装/启动URL `DC8CFDBE-50C2-46C1-B0D2-933985B057EF/OnDeviceAIDemo.app/`一致，sharing override缺席、原数据保留。所有本轮命令均结束；源manifest、xcresult、10份纯文本附件、全部失败/重试日志、恢复校验和交叉验证汇总在`artifacts/verification/p4-camera-isolation-20261027/summary.json`。下一步处理P4独立审查/剩余交互或原生残留，不重复已完成并发/持续测试；MLX专属增强构建本轮未复验。
- 基础里程碑仍**10/12**；README/ROADMAP/计数审计已一致，历史5/12不代表当前进度。本轮没有新增关闭阶段。原生GrOp、真机PHPicker→Vision、最低系统、完整审查、Android授权模型和全范围P11仍开放；全部dirty/untracked与两个保护文件保留，无commit/push，goal active。

## 最新续接 — 2026-10-27 benchmark 重复计数与原始输入保护修复

- 继续P11报告准备时发现真实缺陷：现有iPhone P5原始JSON重复传入，`benchmark_report.py`将30个样本计成60个。新增全局`runId`唯一性校验，跨device/backend/memory方法或不同测量值的同ID输入也拒绝；不同ID的真实独立run保留。CLI另补输出路径保护，拒绝同路径/软链接/现有硬链接别名覆盖原始输入；有效独立报告仍可重建。分组和最近秩算法不变。
- 工具/CLI回归先RED（9失败/子用例），后聚焦**9/9**通过。完整`python3 -m unittest discover -s tools/tests -v`首轮148tests、13failures/8errors，根因包括系统Ruby2.6加载3.4gems与Swift缓存权限；保留全部日志。使用显式`RUBY=/Users/daubert/.rvm/rubies/ruby-3.4.11/bin/ruby`及授权缓存权限后，完整**148/148、exit0**。未通过修改产品代码规避环境失败。
- 对原`927b7749`双端报告分别/合并重算，输出与原工具对有效输入的结果完全相等，各30样本。iOS p50/p95=2.432/3.065ms，Android=10.853538/18.088231ms；两个raw hash不变，重复真实输入现在exit非0且不生成报告。本轮没有新设备run，也没有提升旧报告的质量/资源或当前源码验收等级。
- 独立`benchmark_integrity_review`对工具、测试和计划只读审查**APPROVE、0 findings**；审查员另跑聚焦9/9、py_compile与diff check通过。该结论仅限本修复包。原始证据、旧工具副本、实报告重算、全部失败日志及总结在`artifacts/verification/benchmark-report-integrity-20261027/`；计划与ledger为`2026-10-27-benchmark-report-integrity`。schema仅新增runId说明，未改schema版本或结构。
- 全部原dirty/untracked保留、保护hash不变，无commit/push/设备操作。P4严格并发与GrOp、Android授权模型等工作继续开放；P11实际候选冻结、全能力统一任务/阈值/设备运行和发布结论仍未完成。基础10/12、总goal active。下一步继续可独立交付包；独立agent服务本轮已恢复一次审查，但不能由此假定后续可用。

## 最新续接 — 2026-10-27 CameraSource 暂停/恢复与 FIFO 真机回归

- 本轮推进了新的行为验证，没有修复或抑制严格并发警告。新增`CameraHardwareReleaseTests.testPhysicalPauseResumePreservesSessionAndStopStartOrder`，复用真实session/callback；暂停后排空delegate队列并观察250ms无新帧，恢复时要求同一input/output，再紧邻调用stop/start并等待真实AVCaptureSession stop/start通知与新的回调。timeout/error也执行terminal close，不保存画面。
- 独立新工程build-for-testing和真机全生命周期套件均exit0。iPhone16ProMax/iOS27.0.1 **9/9、0 failures、0 skips**；新用例2.126秒。PID27922/runID `B6B0956C-4C7E-4138-8EEE-3E5C08A5B0DD`：暂停计数3、恢复6、连续stop/start通知后7，之后继续至10；关闭后仍10，start/stop通知各3，inputs/outputs从1/1变为0/0。原关闭/并发注册/owner释放回归均通过。
- 当前CameraSource单文件Swift6、`-strict-concurrency=complete -warnings-as-errors`、iOS15 target检查仍有**4处Sendable捕获warning**，即使编译exit0也不算通过。日志名称`strict-red.log`是原计划命名，实际不是编译失败，原exit0已保存。本机SDK接口确认SerialExecutor/UnownedJob基础API声明可回溯iOS13，`checkIsolated`则iOS18起；这不能证明旧系统AVCapture delegate调用`assumeIsolated`安全。研究agent429无结论；不重复同请求，不用unchecked包装、警告抑制或每帧Task替代所有权设计。生产并发迁移保持未完成。
- 原始xcresult/两份纯文本状态附件/构建日志/当前源码hash/SDK声明证据与summary在`artifacts/verification/p4-camera-concurrency-20261027/`。初次xcresult导出因Xcode TestReport缓存权限exit64，授权重跑成功；不是产品失败。常规测试构建的既有warnings、XCTest最低版本链接提示和Xcode版本诊断保留，不宣称warning-free或iOS15运行通过。
- 已恢复核验过的普通Demo并无参数启动**PID27925**，安装/启动URL一致，容器保留；两个保护hash与生产CameraSource/DemoController hash不变。只新增验证与文档，全部既有dirty/untracked保留，无commit/push。Task9仅行为证据子项完成；严格并发所有权设计、native GrOp残留、UI/EXIF/minOS、完整review及Android模型等仍开放。总goal active、基础10/12。

## 最新续接 — 2026-10-27 LLDB 取证受阻与进度口径复核

- 复核 README、ROADMAP、逐阶段计数审计和本 handoff，当前均为**基础10/12**；旧5/12只累计P0–P3/P8，遗漏已达到原定停止点的P5/P6/P7/P9/P10。此次核对没有新增完成阶段，也没有减少SDK/Demo、Android、模型、质量或统一验收范围；总goal仍active。
- 上轮5次有界LLDB诊断原件保存在`artifacts/verification/p4-atlas-device-debug-20261027/`。`device process attach`后target为`<none>`/`state=connected`、无executable images；同步模式及显式`remote-ios`也未取得模块，`process interrupt`报`Process must be launched`，后续`process attach`报`no such process`。因此没有实际读取TLS、析构/释放事件或线程生命周期；这是调试通道阻断，不是产品失败或新的根因证据。没有新支持路径时不再重复同类attach。
- 本轮新鲜`devicectl device info processes`成功，原已核验普通Demo安装URL `EB090307-6674-4E16-948B-421B04C463EC/OnDeviceAIDemo.app/OnDeviceAIDemo`当前进程为**PID27906**，不同于上轮无参数启动记录PID27901。本轮只读观察，未启动该PID，未重装/终止应用，也未操作另一个旧安装URL的PID27473；进程存在不等于前台UI验收。原始结果`processes-after-debug.json`保留。
- 两个保护文件hash仍为`b35bd984…9fd8f5`和`6f130bd9…70822`；本轮没有生产源码修改、提交或远端写入。末次bridge自然释放已经取得证据，不重复该实验；native GrOp残留、真机交互/EXIF、minimum OS、strict-concurrency warnings与完整review仍开放。下一独立代码分支是CameraSource严格并发所有权设计与回归，不以`@unchecked Sendable`包装或诊断抑制代替修复。

## 最新续接 — 2026-10-27 P4 末次 bridge 自然释放、TLS 线程退出候选机制

- 上一轮有真实页面/分配证据进展；本轮补31/35/60秒idle尾部和同步弱bridge状态，不重建第四页、不强制GC、不跨await保留bridge。新包构建成功，当前probe/生产App/框架manifest哈希全部匹配；上轮审查后的报告诊断/status/来源修订本轮也实际跑到真机。所有本轮build/record/export/download句柄均已正常结束，无需重启。
- iPhone16ProMax/iOS27.0.1、PID27882/runID `543373D1-52A7-424C-9ED1-FE5922D6E97C`，三轮30秒相机窗口新增**672/694/694**次成功；stop5秒无新增且inputs/jobs0。每轮名义30秒采样owner/Compose controller/Metal layer均为空，bridge仍存在但**admissionAvailable=false、inputs/jobs0**。末次实际30.992938秒bridge存在，**32.086039秒采样已释放**，持续空闲至62.350613秒均为0。结果`observations-complete`/`weakReferenceOutcome=released`，不把它写成原生零泄漏。
- trace**301.116972秒**，覆盖全部周期与尾部；**18×GrOp/2304B全部最终live**，其中17项33帧AtlasTextOp路径、1项录制前已有。完整allocation导出99401行/73387236bytes、exit0。额外保存binary定义，trace中`OnDeviceAIDemo.debug.dylib`UUID **00708A06-D253-3EEB-9B8C-0AF13A8BF460**与实际运行build的dwarfdump匹配，PID/安装URL/时序/source hashes均严格验证。证据`artifacts/verification/p4-page-tail-20261027/summary.json`、`verify_tail.py`；运行源码/产物在同目录manifest及`/tmp/odai-p4-page-tail-20261027-build`。
- 官方固定Skia revision的AtlasTextOp.h、GrOp.h/cpp、GrRecordingContext.cpp已直接获取，确认virtual析构、自定义delete和context同线程ClearCache路径。researcher429未提供结论，不重复派发。独立C++host从固定cpp68–89行提取allocator块，仅new/delete重定向计数，三场景通过：同线程复用/清理；3线程析构后退出仍留3块且主线程清理无效；存活worker槽须该worker清理。未链接Skia、未调用设备私有API，不是生产修复或真机根因证明。
- 宿主3线程pthread地址重复但kernel TID不同。原PID27863 thread-info导出35个kernel TID，与Allocations thread-id标签无交集、metadata时间全0；未建立线程生命周期映射。因此先前“同分配线程”应限定为相同导出标签，不能推导同一个持续存活OS线程有多槽。候选机制为线程退出使raw TLS槽丢失已析构存储引用，尚需设备实际TLS/析构/释放线程证据。固定源/host原始结果/线程metadata/最近5条默认分支文件历史在`artifacts/verification/p4-atlas-reentry-upstream-20261027/summary.json`；没有取得已证实可应用的上游修复依据。
- 普通Demo已从原核验restore包恢复安装/无参数启动**PID27888**，URL匹配、容器保留。保护hash和生产源hash不变，全部dirty/untracked保留，无commit/push。后续先围绕native TLS/线程生命周期/实际delete事件设计最小取证，不能继续以相同导出标签直接排除缓存机制；本范围bridge释放不必再重跑。原生残留、物理UI、真机PHPicker→Vision、minOS、strict warnings、完整review及Android模型等roadmap缺口继续保留，goal active、基础10/12。

## 最新续接 — 2026-10-27 P4 同 PID 页面退出/重入与跨周期原生残留

- 上轮进度问答仅复核数字，没有推进实现；本轮已新增实际证据。`generate-camera-resource-app.rb`新增显式page-cycle模式，生产App内容/StateObject原样成为page View，独立root控制存在与identity；新增`CameraPageResourceValidation.swift`只用弱引用追踪owner/bridge/Compose controller/Metal layer。未改生产导航、相机、controller或依赖。双模式生成源码逆变换严格等于原生产源码；现有stop模式保留。
- 真机iPhone16ProMax/iOS27.0.1、PID27863、runID `7BB10334-CBE9-4CC8-839A-BC6DD3F38C20`：三次新owner/真实渲染，相机30秒窗口新增**667/690/694**次成功；stop5秒计数不变、inputs/jobs0。页面移除30秒后每轮owner/Compose controller/Metal layer均已释放，bridge仍1；前两轮bridge下一次warmup归零，最后bridge未来生命周期未验，原报告保持`incomplete-release`。
- Leaks trace**301.106594秒**，覆盖全部周期，设备/PID/安装URL/时间已严格关联。**19×GrOp/2432B全部最终live**；流式导出**104655行/80807499bytes、exit0**。18项有33帧AtlasTextOp/SurfaceMetalRedrawer完整栈，来自8个分配线程；按分配周期7/6/5（不是三次即时Leaks计数），另1项录制前已有。同分配线程可跨周期各留1项，简单“每分配线程一槽”解释不足；delete/析构线程和TLS仍未观测，不宣称长期无界增长或缓存误报。另3个live AtlasTextOp未列入Leaks，不能自行升级为泄漏。
- 原始报告/trace/源码/运行App/framework hashes、分配栈、生成检查与严格汇总脚本位于`artifacts/verification/p4-page-release-20261027/`。`summary.json`和[专项文档](validation/p4-atlas-text-cache.md)记录边界。xctrace初次沙箱导出因Instruments缓存权限exit134；授权后导出成功，不是产品错误。当前所有本轮build/record/export会话均结束，不需重启。
- 独立probe reviewer提出报告I/O失败诊断、framework/Info来源、状态语义和模式检查4项：前三项已在源码补强并重新签名build成功，第四项已有真实双模式逆变换/manifest检查。真机运行的是补强前probe，`run-source/`、`project/`和`/tmp/odai-p4-page-release-20261027-build`完整冻结；修订后`project-final/`与`/tmp/odai-p4-page-release-final-20261027-build`仅编译验证，不混称设备已验。没有全P4独立review结论。
- 普通Demo从已核验`/tmp/odai-p4-restored-demo-20261026/Build/Products/Debug-iphoneos/OnDeviceAIDemo.app`恢复安装/无参数启动**PID27875**，URL匹配、数据容器保留。保护两文件hash及生产CameraSource/DemoController/App hash不变，全部dirty/untracked保留，无commit/push。
- 下一步：依据新19地址调查真实析构/释放线程与最终TLS/原生owner，补最后页面退出更晚的bridge采样；三轮已经证明跨周期新增最终残留，不重复旧trace“可能缓存”结论。保持依赖，不强制GC/调用私有ClearCache/压制Leaks。物理UI、真机PHPicker→Vision、最低系统、strict warnings、完整review及Android授权模型等全部roadmap缺口保留，总goal active、基础10/12。

## 最新续接 — 2026-10-27 P4 AtlasTextOp 完整分配栈与缓存机制

- 本轮只读分析上一包PID27593 trace，无新设备操作或生产/依赖修改。流式完整读取Allocations List **118470行/77838589 bytes**，七个Leaks地址全部匹配。**5个AtlasTextOp地址各取得33帧完整分配栈，分别来自5个不同线程**；路径明确为Compose `SurfaceMetalRedrawer.startRenderLoop/renderAndPresentFrame → Canvas.drawPicture → Skia文字绘制 → AtlasTextOp::Make`。另2个在trace时间0已存在，仍只有栈限制。
- 固定Skia `22f58c9fd43d55bde818821c04b48fda5d7ec939`的cpp确认每线程一个`thread_local void* gCache`，new复用、delete缓存已析构存储、ClearCache清理调用线程槽。当前验证包二进制的TLS/new/delete路径吻合，并发现**GrRecordingContext基类析构实际调用ClearCache**；不能从派生类源码未见调用推导不清理。5对象/5线程支持缓存假设，但尚未读取运行时TLS值、析构/释放线程、页面退出与重复绘制增长，**不把7×GrOp/896B改为误报或零泄漏**。
- 详情和固定来源链接在`docs/validation/p4-atlas-text-cache.md`；原始分配记录/完整栈、cpp、当前二进制hash/UUID/反汇编及汇总在`artifacts/verification/p4-atlas-cache-20261027/summary.json`。attach trace的dyld-load导出无事件，映像UUID未由trace独立复核。原trace和summary保留不变；上轮普通Demo恢复记录仍是最近设备操作，本轮未重新安装/启动。
- row XPath为空、64MiB初轮partial均保留，512MiB限额的后续完整导出才用于结论。桌面UI未到达目标trace，无UI审查结论；不要再走这条不稳定入口。上游首次research有结果，补查429；补取header/版本文件的自动审批服务也429、两命令未执行，不是安全拒绝，未绕过审批。现有cpp与二进制证据可继续推进。
- 下一验证：保持依赖，覆盖实际页面退出/重入、owner/renderer释放及同PID资源采样，区分线程缓存和持续增长；正常stop保留页面不能代替完整teardown。不盲升依赖或添加私有ClearCache调用。保护hash/生产源hash未变，全部dirty/untracked保留，无commit/push；基础10/12、总goal active及Android授权模型等所有缺口保留。

## 最新续接 — 2026-10-27 P4 实际 Demo 渲染与持续/停止资源采样

- 新增独立生成器`generate-camera-resource-app.rb`及`CameraResourceValidation.swift`，复用真实SwiftUI/Compose页面、controller和当前KMP framework；仅在生成的App副本插入一个验证调用，生产源码/保护工程未改。使用显式UUID启动和app-private触发文件，在准确PID的Leaks录制存活后才启动相机；不保存画面或截图。复跑说明在`probes/ios-camera-lifecycle/README.md`，计划Task6与原ledger已更新。
- iPhone16ProMax/iOS27.0.1，runID `DE42C583-3089-416C-92A0-94B38266F918`、PID27593：真实ComposeHostingViewController与可见CMPMetalLayer各采样点存在。预热3次，四个30秒窗口新增**672/642/670/641**次成功，活动**121.117918秒**，共**2628**次Vision成功；停止后**30.085894秒**计数不变且inputs/jobs均0，报告`passed`。这是controller驱动、实际页面渲染的运行验证，不是物理UI控件触摸验收。
- `rendered-camera-pid27593.trace`完整**210.750103秒**，PID/安装URL、时间范围与报告逐阶段核对，覆盖触发后基线、全部活动与停止窗口。Leaks明细**7×GrOp/896B**：**5项责任分配符号为`skgpu::ganesh::AtlasTextOp::Make(...)`，2项仍`<Call stack limit reached>`**。新符号缩小调查范围，但不能仅凭它认定Skiko/Compose所有权缺陷或修复方案；旧空闲trace不可归因结论不变。下一步针对这份新trace调查完整分配栈/对象生命周期，不重复旧trace或盲升依赖。
- 活动RSS快照471859200/472678400/473088000/473841664B，停止30秒后464404480B；采样点thermal raw0。Allocations aggregate persistent351357200B，其中IOSurface317390848B，均不是峰值/footprint/GPU/能耗或无泄漏结论；正常stop保留最后一张预览和Compose页面，尚未覆盖整个页面owner teardown。原始trace约4GB，仅保留本地忽略目录。
- 初次冷启动inactive、CAMetalLayer-only检测误判、诊断旧Swift API编译失败均留档，属于harness问题，非产品RED。固定Compose1.11.1源码确认SurfaceMetalView使用CMPMetalLayer，实际440×380图层证据支持修正检测。一次诊断编译失败后启动到旧产物的记录明确排除，未触发相机。所有失败、source manifest和最终交叉核验汇总见`artifacts/verification/p4-rendered-resource-20261027/summary.json`、`diagnostics.json`。
- 普通Demo已恢复安装并无参数启动（PID27598），安装/启动路径一致、共享override缺席，保留应用数据；保护hash和生产相机/控制器hash未变，全部dirty/untracked保留，无commit/push。基础**10/12**与总goal active不变；P4仍缺真实UI控件、真机PHPicker→Vision EXIF2/6/7、完整渲染释放/泄漏根因、最低系统、并发warnings与独立review。Android授权模型问题仍待答，不重复询问。

## 最新续接 — 2026-10-26 P4 真机两分钟 controller/Vision 管线

- 新增device-only opt-in `CameraControllerSustainedTests`，使用生产`DemoController.startCamera/stopCamera`与真实bridge/Vision；生成器以`ODAI_CAMERA_LIFECYCLE_SUSTAINED=1`启用，拒绝simulator模式，默认快速套件不包含此用例。未修改生产相机代码，未保存画面。
- iPhone16ProMax/iOS27.0.1完整hosted XCTest **9/9 passed、0 failures、0 skips**。runID `A13B90FD-12DB-4283-928C-832A205AF891`、PID27564：预热4次成功，四个30秒窗口分别新增720/721/720/720次，活动120.069秒后共2885次真实Vision成功结果。停止后等待bridge jobs，再观察5秒，计数仍2885、inputs/jobs为0；owner释放、bridge/detector关闭均通过。
- RSS仅为`mach_task_basic_info.resident_size`快照：启动前148439040B，四个活动窗口490962944/492339200/491012096/491012096B，停止后490864640B，owner释放后450330624B。采样点前台状态/热状态均raw0；不是连续热状态、峰值、footprint、GPU、能耗或零泄漏结论，不设临时内存阈值。
- raw xcresult、8个阶段文本附件、build/test logs、generator checks及源/保护hash汇总位于`artifacts/verification/p4-sustained-controller-20261026/summary.json`。普通Demo复用上轮已核验且hash相同的当前源码构建包，已恢复安装并无参数启动（PID27570），安装URL与启动路径一致，共享override缺席；未卸载或清理容器。两个保护hash不变，全部dirty/untracked保留，未提交/推送。
- 此结果补齐原生controller持续运行与停止/owner释放合同，**不替代完整Compose相机UI、持续/停止后Leaks、渲染资源释放、真机PHPicker→Vision EXIF2/6/7、最低系统、并发warnings或独立review**。旧UI runner初始化超时和不可归因的GrOp trace继续保留，不盲目重跑。基础10/12与总goal active不变；Android授权模型仍待答，不重复询问。README及本入口已明确5/12为历史口径。

## 最新续接 — 2026-10-26 P4 真实 PHPicker 合成 EXIF 模拟器验证

- 新增独立simulator-only验证host/UI tests与生成器，直接编译生产`DemoController`，经其`selectPhoto()`打开真实PHPicker；观察实际`controller.image`，不复制decoder或注入picker callback。本轮未改生产代码、保护工程或真机照片库。计划与恢复说明为`docs/superpowers/plans/2026-10-26-ios-picker-exif.md`、`probes/ios-photo-picker/README.md`。
- 三个新建独立iOS27.1模拟器分别导入已核验EXIF2/6/7的公开合成JPEG，原始DCIM文件与fixture SHA256唯一匹配。真实picker用例各**1/0/0，合计3/3 passed、0 failures、0 skips**。生产controller输出分别为`80x40:white,black,white,black`、`40x80:black,black,white,white`、`40x80:white,white,black,black`（按左上/右上/左下/右下），同时验证尺寸与镜像/旋转像素位置。
- 首次“网格只能有1项”的保护断言失败，未点击图片；诊断证实新模拟器预置6张Apple示例图。随后要求`ODAI_PICKER_FIXTURE_LABEL`指定经核验的精确标签且唯一命中，无位置fallback。最初失败与诊断保留为harness/environment证据，不是产品方向处理RED。最终raw xcresult、文本附件、fixture manifest、库内hash清单、源hash与汇总在`artifacts/verification/p4-picker-exif-20261026/summary.json`。
- 已仅删除本轮创建的3个临时模拟器，原iPhone Duo模拟器仍Booted；fixture与所有证据保留。`git diff --check`、Ruby语法与独立host/UI test构建通过，两个保护hash不变；此前dirty/untracked全部保留，未提交/推送。无需重跑未改动的Apple470项来冒充新证据。
- **P4真机PHPicker→controller→Vision的EXIF2/6/7仍未验**；本包未断言Vision推理成功或Photos provider临时文件保留原EXIF，亦不覆盖完整Compose Demo交互。持续相机/停止资源窗口、渲染释放、最低系统、严格并发warnings与独立review继续保留。基础10/12，完整roadmap仍active；Android授权模型问题继续待答，不重复询问。

## 最新续接 — 2026-10-26 P4 回调同步当前源码真机复验

- 上一轮完成回调注册/terminal close同步修复及本地验证；本轮没有新生产代码修改。先核对源hash与设备lockState（无需密码），预先构建普通Demo恢复包，再使用既有hosted XCTest工程/签名测试iPhone16ProMax/iOS27.0.1。**当前源码8/8 passed、0 failures、0 skips**，包含真实硬件关闭、owner/bridge释放及全部回调同步回归。
- 硬件文本附件确认capture callback **3** 次，input/output **1/1→0/0**，关闭后250ms计数仍3，拒绝restart；没有保存相机图像。普通Demo已用当前源码重新安装、无参数启动，sharing overrides缺席，未删除App容器或更新签名资产。结果为`artifacts/verification/p4-owner-release-20261026/callback-physical-summary.json`，`callback-summary.json`已追加新真机结论；旧`physical-summary.json`继续保留上一源码历史。
- 两个保护文件hash与本轮前的source hashes均未变，全部dirty/untracked保留，未提交/推送。新源码硬件复验缺口已关闭；**P4仍需PHPicker EXIF2/6/7、持续相机/停止资源窗口、完整渲染释放/GrOp归因、最低系统与独立审查**。严格并发capture warnings仍在；review服务429不算审查通过。不要重复检查已无法归因的旧trace或无依据重跑失败UI runner。基础里程碑10/12，完整roadmap保持active。

## 最新续接 — 2026-10-26 P4 回调注册与关闭同步（局部完成）

- 本轮从并发 warning 排查到实际生命周期漏洞：`CameraSource.onFrame` 跨线程读写未同步，且 terminal close 后仍能重新注册并持有 callback owner。新真实 XCTest 先完成运行时 RED（1 test / 2 assertion failures，exit65），不是构建或 runner 错误；初轮沙箱缓存/模拟器权限失败单独保留为环境失败。
- 回调存储、替换与关闭门禁现由 NSLock 保护；关闭先封住注册入口，再停止/拆卸 session 并排空帧队列。旧回调捕获对象在锁外释放，避免析构重入死锁。没有新增 Sendable 注解，没有改变 start/stop 可恢复暂停合同。新增关闭后注册/捕获释放、并发读写与 close、捕获对象析构重入回归。
- 完整 simulator lifecycle **7/7、0 failures**；Thread Sanitizer 相机专项 **6/6**，日志无 race/lock-order report，测试二进制实际含 `__tsan_read/write` 等插桩符号。完整 `verify-apple.sh` exit0：Apple XCTest **470/470**、分发工具 **18/18**、既有生命周期/照片/方向/ASR checks、原生 consumer/default Demo simulator build通过；KMP 26 tasks up-to-date，增强 binary consumer未指定未运行。新证据与 frozen logs见 `artifacts/verification/p4-owner-release-20261026/callback-summary.json`。
- 单文件 `-swift-version 6 -strict-concurrency=complete` typecheck exit0但仍有4个 non-Sendable capture warnings，涉及 start/stop/close/帧队列闭包；不能报告无警告或 Swift6迁移完成。新 bounded reviewer 返回429，无审查结论，不继续重派。同机旧5/5硬件结果属于上一源码，本轮未操作真机；当前修改的硬件复验仍待完成。
- 两个保护文件 hash未变，既有dirty/untracked全部保留，未提交/推送。基础里程碑仍10/12；继续 P4严格并发/独立审查、当前源码硬件复验、PHPicker EXIF2/6/7和持续相机/停止资源窗口。现有GrOp trace不可归因结论不变，不重复检查同一trace。完整roadmap与Android模型/生产推理等扩展目标保持active。

## 最新续接 — 2026-10-26 P4 现有 trace 归因复核

- 当前基础里程碑为 **10/12，P4/P11 未完成**；下方历史 `5/12` 不再用于当前汇报。完整交付的 Android 后端、生产模型、质量、最低系统和统一验收缺口全部保留，不将 10/12 换算为整体完成百分比。
- 已用 Instruments 打开既有 `idle-current-pid27475.trace`，逐一选择四个 GrOp 对象并查看 Inspector 的 `Allocation Backtrace`：四项均只有 `<Call stack limit reached>` / `<unknown>`。这不只是 CLI 的 Responsible Frame 列省略；当前保存的 trace 在这两个入口均未提供可归因的分配栈。View Options 仅见 Invert Stack / Collapse Stack。
- 本机 `xctrace record --template Leaks --device … --show-recording-options` 成功输出的公开选项没有 stack-depth 设置。上游只读调查代理返回 `429 Too Many Requests`，没有调查结论。没有新录制、源码或依赖修改，也没有将 GrOp 归因于 CameraSource、Skiko 或 MetalRedrawer TODO。调查记录为 `artifacts/verification/p4-owner-release-20261026/idle-backtrace-inspection.json`，汇总已追加到 `physical-summary.json`。
- 同一 trace 的无效重复检查到此结束；P4 继续保留 PHPicker EXIF2/6/7、持续相机/停止窗口、渲染释放、严格并发与独立审查。不要无依据重跑已两次初始化失败的 UI runner，或盲目升级依赖。上一检查点的真机5/5、模拟器4/4为既有验收，本轮没有重跑。保护文件 hash 未变，原有 dirty/untracked 修改保留；goal 仍 active。

## 最新续接 — 2026-10-26 P4 真机关闭合同与当前空闲 Leaks（部分完成）

- 前一轮为有效进展：owner teardown 修复、模拟器测试和 Apple 门禁已落地。本轮只读 lock-state 确认 iPhone 无需密码后，直接使用真机，没有镜像或解锁操作；原有 dirty/untracked 文件全部保留。基础里程碑仍 **10/12，P4/P11 未完成**。
- `generate-camera-lifecycle-tests.rb` 新增 isolated hosted device 模式，复用现有 Demo/test bundle 标识和签名，不执行 provisioning updates。iPhone 16 Pro Max / iOS 27.0.1 上 **5/5 passed、0 failures、0 skips**；新增硬件测试实际收到 **3** 次 capture callback，关闭前 input/output **1/1**，关闭后 **0/0**，250ms 后回调仍 **3**、再次 start 后 session 仍 stopped。另四项 owner/bridge/output/delegate/drain/repeated-close 测试同机通过；没有保存相机画面。更新后的默认 simulator 生成模式也新鲜 **4/4 passed**。
- 普通 Demo 的当前源码签名 device `build-for-testing`、安装、无参数恢复启动均成功；App 私有容器未删除，sharing overrides 不存在，两个保护 hash 未变。真机原始结果和状态附件在 `physical-tests.xcresult` / `physical-attachments/`，汇总见 `artifacts/verification/p4-owner-release-20261026/physical-summary.json`。
- 既有两分钟相机 UI 用例两次均在 runner 初始化时报 `Timed out while enabling automation mode.`，业务测试体没有执行；xcresult 把 runner 错误记录为 failure，不计为相机行为 RED/通过。中途核实并结束了本项目旧安装路径的进程，但重试仍失败。本轮没有完成持续相机/停止窗口的新 Leaks。
- 另取当前普通 Compose Demo 的 `--smoke-blank` 启动后空闲采样：PID **27475** 与当前安装 URL 和 trace TOC 一致，Leaks 模板窗口 **91.471143 秒**。明细 **4×GrOp / 512 B**，均 `Call stack limit reached`，责任边界仍未确认；与历史静态/相机样本状态不同，不能推导泄漏增长率或认定关闭修复无效。Allocations aggregate persistent bytes **19,206,032**，不是 physical footprint/峰值。TOC、明细和统计均已导出；原始 trace 仅留本地忽略目录。
- 继续项：PHPicker EXIF 2/6/7、持续相机及停止后的完整释放/渲染责任、最低系统、独立审查和 Swift 5 non-Sendable warning。相机硬件 terminal close 这一合同现有真机证据，不能扩展为 P4 全部资源或零泄漏。LiteRT-LM 模型资产问题仍待用户答复，不重复询问或代为接受许可；总 goal 保持 active。

## 最新续接 — 2026-10-26 P4 Demo owner 释放回归（局部完成）

- 延续 Roadmap 口径复核后的 **基础里程碑 10/12**；P4、P11 未完成。LiteRT-LM 模型资产问题仍待用户答复，不重复询问、不下载或接受许可。转向独立可推进的 P4 相机/bridge 所有权缺口；全部先前 dirty/untracked 修改保留。
- 真实控制器 XCTest 先得到运行时 RED：控制器已释放，但保留的 bridge 仍 available、待处理图像未清空、native detector 未 closed。`DemoController.deinit` 现取消 owner tasks、关闭 frame queue，并在 main actor 取消 photo submission、关闭 face bridge 和相机。相机新增幂等 terminal `close()`，在既有 capture serial queue 停止并移除 inputs/outputs、清空视频 delegate，再排空 frame queue、释放 callback/CIContext cache；`stop()` 保持可恢复暂停。
- 新鲜模拟器 XCTest **4/4、0 failures**；`verify-apple.sh` exit0：Apple host XCTest **470/470**、分发工具 **18/18**、既有生命周期/照片提交/方向/ASR 检查、原生 consumer 与默认 Demo simulator build 通过。KMP 部分任务 UP-TO-DATE，不称全部重跑；增强 multimodal binary consumer 未指定、未运行。初轮 Ruby 2.6/3.4 gem 混用失败后，以 `RUBY=/Users/daubert/.rvm/rubies/ruby-3.4.11/bin/ruby` 重跑通过。
- 证据冻结在 `artifacts/verification/p4-owner-release-20261026/summary.json`、`camera-green.xcresult` 和对应 raw logs；计划/ledger 为 `docs/superpowers/plans/2026-10-26-ios-camera-release.md` / `.superpowers/sdd/2026-10-26-ios-camera-release/progress.md`。bridge RED 的 runner 在实际断言失败后整理诊断时被中断（exit130），不把未完成的 red xcresult 当成完整执行包；camera API 为缺少 initializer/close 合同的编译 RED。
- 独立审查服务再次 `429 Too Many Requests`，没有审查结论。默认 Demo 的新 `CameraSource.close` 有 Swift 5 non-Sendable self capture warning，严格并发审查仍待完成；未用 unchecked Sendable 隐藏。真实 capture input/hardware 释放、新同设备 Leaks/Allocations、PHPicker EXIF 2/6/7 均未验，不能据模拟器测试关闭 P4 或宣称历史 GrOp 泄漏已解决。保护文件 hash 未变；`git diff --check` 通过；未 commit/push/merge/release。

## 最新续接 — 2026-10-26 Roadmap 进度口径复核

- 用户追问为何长期 5/12 后，对照 `cb7c13b` 原始阶段表与加入 P11 的 `cda5ba8` 基线，确认原阶段停止点与后续完整交付被混算。现按原始停止点记录 **基础里程碑 10/12（P0–P3、P5–P10）**，P4、P11 未完成；这不是整体完成百分比或发布结论。逐阶段依据、原始报告复核与扩展任务见 `docs/validation/roadmap-progress-audit-20261026.md`。
- 新鲜只读复核：P5 两个 `927b7749` 真机 5+30 JSON 均由 `benchmark_report.py` 重验成功，Apple/Android 独立消费者历史构建日志成功标记仍在；P6 真机 TTS success/cancelled/closed、P7 固定视频 success/48→48 原始报告可读；P9 `20261025-fix2/device-report.json` 通过 `--expected-samples 30` validator。新汇总在 `artifacts/verification/roadmap-scope-audit-20261026/`；没有重跑设备或推导未测指标。
- 已批准的后续 SDK/Demo/第二平台/生产模型/质量/最低系统与审查任务全部保留。当前活动工作仍是 LiteRT-LM Android 扩展包；它的模型资产、生产 adapter/consumer 和真实设备推理未完成。下方 5/12 为历史报告口径，续接时不再直接沿用。全部现有未提交修改保留。

## 最新续接 — 2026-10-26 LiteRT-LM 复审修复与 release 打包边界（部分完成）

- 在原有 dirty worktree 上完成独立复审；该轮发现的 2 MEDIUM 均经 RED→GREEN 修复。private cache 成功删除后才释放 lease；删除失败时保留 lease/cache、关闭 admission 并返回稳定 `RuntimeFailure`（模型移除持续被 lease 阻止直到进程结束）。`cancelProcess → awaitTerminated` 由 session owner 强制 30 秒截止时间；非返回 fake drain 在旧代码中触发外层 35 秒保护超时，修复后约 30 秒返回 `DrainFailure`，不 close native、不释放 lease/cache，并拒绝 drain/close/reentry。修复由回归验证，未声称独立复审已重审修复后的 diff。
- 新鲜完整模块 JVM **23/23**（`review-fixes-green.log`）；后续 lint、debug/release AAR、POM/Gradle metadata、probe host/APK/test APK、`android:multimodal` JVM 与默认 Demo APK 门禁 **BUILD SUCCESSFUL**（567 tasks，40 executed、527 up-to-date，`review-package-final.log`）。probe host **9/9**、Python AAR 工具 **7/7**；`git diff --check` 通过。日志均在 `.superpowers/sdd/2026-10-26-android-litertlm-backend/`。
- 生成包和依赖图只读断言通过：默认 Demo runtime 无 LiteRT-LM；可选后端精确解析 `0.18.1`；POM 仅以 runtime scope、Gradle metadata 仅在 runtime variant 声明 LiteRT-LM；release AAR 没有内嵌 vendor classes/JNI，AAR 与 source JAR 的 hash 匹配 metadata。冻结副本、raw logs 和 `summary.json` 位于 `artifacts/verification/android-litertlm-package-20261026/`；release AAR SHA-256 `b14f143f…d7c0e08`。只生成本地产物，未发布。
- Task 1/2/3 及 Task 5 仍 **partial**，Task 4 生产构造未启用。固定授权模型仍缺失；真实 callback 模式、native drain、模型兼容性、设备图像生成均 **unverified**，不得以 host 超时测试或打包结果替代。保护文件 hash 仍为 `project.pbxproj=b35bd984…9fd8f5`、`face-identity.md=6f130bd9…70822`；全部原有未提交修改保留。Roadmap 仍 **5/12、P9 active**。
- 模型候选只读 metadata 已记录在 `docs/validation/android-litertlm-model-assets.md`：Google Gemma 3n E2B 固定 revision 通用 `.litertlm` 为 `3,655,827,456` bytes，manual gate/Gemma 许可，SHA-256、当前文件 CPU/image/TECNO 兼容性未证实；没有下载或接受条款。已异步询问用户是否有授权本地资产，不能将候选记录当作真实 manifest。独立尝试恢复 P10 Android 专项 review 再次 `429`、无结论，P10 review 状态保持待审。

## 最新续接 — 2026-10-26 LiteRT-LM Task 3 完整本地门禁复核（部分完成）

- 延续原有 dirty `feat/sdk-foundation` worktree；上一检查点所述 cache 删除失败、预算、空结果及 availability/close join 回归已纳入最终聚焦构建。`.superpowers/sdd/2026-10-26-android-litertlm-backend/focused-final2.log` 记录 **BUILD SUCCESSFUL**（548 tasks，16 executed、532 up-to-date）；模块 JVM XML 合计 **22/22**（0 failure/error/skip），probe host **9/9**，Python AAR 工具 **7/7**，`git diff --check` 通过。此前“完整模块 lint/assemble 和默认 Demo 构建仍需重跑”只属于下方历史检查点。
- 本次重新只读检查真实 AAR：`tools/inspect_android_vlm_artifact.py --json` 返回 `status=passed`、`errors=[]`，`minSdk=24`、target SDK 未声明、JNI `arm64-v8a/x86_64`；AAR/POM SHA-256 仍为 `05c6bdc8…699f3012b` / `3d6ea70f…699f5`。两个保护文件 hash 仍为 `project.pbxproj=b35bd984…9fd8f5`、`face-identity.md=6f130bd9…70822`。
- Task 1/2/3 仍为 **partial**：没有固定授权的 LiteRT-LM 模型，真实 callback 模式、cancel→native drain、设备图像生成和模型兼容性均 **unverified**；生产 factory/consumer 未启用。本次新增代码的独立复审仍无结论（审查服务 `429 Too Many Requests`）。Roadmap 保持 **5/12、P9 active**；保留全部未提交修改，未 reset/clean/stash/commit/push/merge/release。

## 最新续接 — 2026-10-26 LiteRT-LM Task 3 预算、查询 join 与 cache 失败门禁（部分完成）

- 在原有 dirty `feat/sdk-foundation` worktree 继续可选 Kotlin 模块，不改变既有未提交文件的归属；保护文件 `project.pbxproj=b35bd984…9fd8f5`、`face-identity.md=6f130bd9…70822` 在本轮开头复核未变。
- 新增可确定性复现的 private cache 删除失败测试，先因缺清理边界 RED，再引入内部 `LiteRtLmCacheCleaner` 与稳定 `RuntimeFailure` 映射，关闭 backend admission，保留失败 cache，禁止下一次 native open；该测试 GREEN。lease 已在 native 正常 close 后释放，不把清理失败误报为 drain 证明失败。
- 补充 `512` token 上限、`513` 拒绝、`16 MiB + 1` 图像拒绝和空输出清理覆盖；新增可控 availability source，验证 `close()` 等待已进入的查询结束，随后查询返回 `CLOSED`。新增测试及当前模块 JVM 定向测试通过；完整模块 lint/assemble 和默认 Demo 构建仍需本轮最终重跑。
- 生产 LiteRT-LM factory/consumer 仍未启用：没有固定授权模型资产，真实 callback 模式、cancel→native drain、设备生成与模型兼容性全部 **unverified**。独立复审本次新改动的 lane 遇到 `429 Too Many Requests`，未产生复审结论；上一轮已复审的范围不得自动扩展到这些新改动。Roadmap 仍 **5/12、P9 active**。

## 最新续接 — 2026-10-26 LiteRT-LM fake 生命周期与 Maven 打包检查（部分完成）

- 本检查点后独立审查最初给出 2 HIGH / 1 MEDIUM。HIGH native dispatcher 已由 `Dispatchers.IO` 包住完整 session 并以专用 caller 线程测试 RED→GREEN；复审确认 **resolved**。callback seam 现要求 `CUMULATIVE` 或 `DELTA` 并与 probe state 匹配，fake delta 被累积为有序累计 update；真实 probe 的 `delta-or-reordered` 含糊结果现以 RED→GREEN 测试改为 `unverified-callback-mode`，只有明确 cumulative + drain proof 才写 `candidate-probe`，复审确认原 HIGH 证据标签问题 **resolved**。MEDIUM 的 probe 资源归属已用 `ProbeNativeOwner` 处理：send 前失败按 conversation → engine 关闭，send 尝试后没有 drain 证明则保留；host 三项所有权回归先 RED 后 GREEN，复审确认原 MEDIUM 已解决。真实模型运行尚未测试，生产 gate 继续关闭。
- 新增 caller 取消后、native drain 前的 late callback RED→GREEN 隔离回归，以及第二次并发 generation 只能 `Busy` 的测试。修复后最终聚焦门禁 `:android:backend-litertlm:testDebugUnitTest :lintDebug :assembleDebug :probes:android-litertlm:testDebugUnitTest :assembleDebug :assembleDebugAndroidTest :android:multimodal:testDebugUnitTest :demo:androidApp:assembleDebug` **BUILD SUCCESSFUL**（548 tasks，6 executed、542 up-to-date），完整日志为 `.superpowers/sdd/2026-10-26-android-litertlm-backend/focused-final.log`。模块 JVM **18/18**、probe host **9/9**；Python AAR 工具 **7/7**、`git diff --check` 通过。该本地构建不替代真实模型 probe。
- 当前仍在 `/Users/daubert/Github/ondevice-ai-kit/.worktrees/foundation`、`feat/sdk-foundation`，所有原有 dirty/untracked 文件保留，未 reset/clean/stash/commit/push/merge/release。Roadmap 仍 **5/12、P9 active**。
- 已修复 probe 最新编辑的 Kotlin 多余大括号；TECNO KM9/API35 安装并显式启动无模型 probe 后，App 私有报告精确为 `{"status":"unverified-missing-app-private-assets","schemaVersion":1}`，副本在 `artifacts/verification/android-litertlm-probe-20261026/missing-assets-device-report.json`。这只证明缺资产安全退出，没有运行模型或证明 callback/drain。
- `android/backend-litertlm` 新增内部 fake engine/session seam 与 image-only `LiteRtLmBackend`。RED→GREEN 回归覆盖两次独立 generation、`cancel → awaitTerminated → native close → lease/cache release`、drain/open 失败后 poisoned terminal 保留 lease/cache、close 取消 active generation 与晚到 callback 隔离。availability 的缺资产优先级也做了 RED→GREEN 修复：缺模型显示 `REQUIRES_ASSETS/MISSING_ASSETS`；已安装但 probe 未证明则显示 `UNAVAILABLE/UNSUPPORTED_DEVICE`。这些都是 fake/host 合同，不能代表 native drain 已证明。
- AGP 拒绝可选库直接依赖本地 `.aar`（`bundleDebugAar` 报 `Direct local .aar file dependencies are not supported`）；已将 AAR/POM 的已核验原件复制到被忽略的 `.superpowers/sdd/2026-10-26-android-litertlm-backend/maven/`，由 `-PlitertlmMavenRepo=.superpowers/sdd/2026-10-26-android-litertlm-backend/maven` 提供 Maven 坐标离线解析。模块和 probe 始终声明固定 `com.google.ai.edge.litertlm:litertlm-android:0.18.1`；依赖图为 Gson 2.14.0、reflect 2.4.0、coroutines-android 1.11.0，默认 Demo `debugRuntimeClasspath` 无 `litertlm`。默认无属性时仍走正常 Google Maven；本地构建不证明当前网络下 Google Maven 可达。保护文件 hash 仍为 `project.pbxproj=b35bd984…9fd8f5`、`face-identity.md=6f130bd9…70822`。
- Task 1/2/3 仍仅 **partial**。真实模型资产、generation callback 累计/增量实测、cancel→native drain、生产 adapter/公开 opt-in consumer、Android VLM 推理与视频路径均未完成。继续补 close-query/预算及 native probe 双路径；生产 factory 必须继续不可构造。

## 最新续接 — 2026-10-26 LiteRT-LM Kotlin probe 与可选模块合同（部分完成）

- 用户已批准 `docs/superpowers/specs/2026-10-26-android-litertlm-backend-design.md`；实施计划见 `docs/superpowers/plans/2026-10-26-android-litertlm-backend.md`。本轮在现有 `feat/sdk-foundation` dirty worktree 开始 Native 执行，未 reset/clean/stash/commit/push/merge/release；所有既有未提交修改保持。
- 新增 `probes/android-litertlm` Kotlin probe：使用真实 `litertlm-android:0.18.1` AAR 离线编译并生成 main/test APK。因当前代理 `127.0.0.1:7897` 不可连接，本地门禁显式传入 `-PlitertlmAar=artifacts/verification/android-litertlm-aar-20261026/litertlm-android-0.18.1.aar`；无该参数时 Gradle 仍使用固定 Google Maven 坐标。独立 review 发现首次本地模式错误地使用旧版 Gson/reflect；已修正为 AAR POM 的精确传递版本，离线 Gradle runtime graph 为 Gson **2.14.0**、Kotlin reflect **2.4.0**、coroutines-android **1.11.0**。AAR classes 的 Java class major 为 65，主机 JVM 17 无法执行 `Contents`，故 JVM 测试只跑无 AAR 类加载的规则，真实 content mapping 放在 Android instrumentation。
- TECNO KM9 / API35 / `arm64-v8a`（serial `<Android-serial>`）上，修正版本的 probe APK/test APK 重新安装成功，`LiteRtLmProbeInstrumentationTest.inputMappingUsesRealLiteRtLmContentTypes` **1/1 passed**，新鲜原始日志 `artifacts/verification/android-litertlm-probe-20261026/message-user-exact-device.log` / `.exit`。它证明真实 AAR `Message.user(Contents.of(ImageFile, Text))` 的角色/顺序映射和 Android 类加载；设备 generation、callback delta/cumulative、`cancelProcess()` 后 native drain **未验证**，因为没有冻结/授权的 LiteRT-LM 模型资产。probe 目前尚无可执行的模型运行入口，不能将编译测试视为 Task 1 完成。
- 新增可选 `android/backend-litertlm` 模块骨架，不进入 `android:multimodal` 或默认 Demo 依赖：固定 model spec/预算、manifest 版本与路径校验、`ModelStore.status → acquire → manifest → release` availability helper 和稳定错误类型。测试先观察缺类型 RED；发现原预算测试因空 metadata 假阳性后修正为有效 metadata，观察 `128` 预算预期 RED，再固定 spec ceiling 为 **512**。当前 `:android:backend-litertlm:testDebugUnitTest :android:backend-litertlm:lintDebug` **BUILD SUCCESSFUL**，但 backend generation/session、native adapter、consumer 均未实现，Task 2 仍部分完成。
- 执行进度记录在被忽略的 `.superpowers/sdd/2026-10-26-android-litertlm-backend/progress.md`；其中 Task 1/2 均标为 **partial**，不得依据先前脚本自动产生的完成行升级任务状态。下一步先补可运行的真实模型 probe 入口和安全的正常/取消双路径，若模型不可得则继续 fake 生命周期/poisoned terminal 测试但保持生产 factory unavailable；随后完成模块/consumer/设备门禁与独立 review。
- `git diff --check` 通过；两个保护文件继续未被本包编辑。Roadmap 保持 **5/12、P9 active**，Android 生产 VLM/video 推理、自然质量、GPU/NPU/energy、minimum OS 和 P9 完成继续 `unverified`。

## 最新续接 — 2026-10-26 LiteRT-LM Android AAR 真实 artifact 门禁（未接入生产）

- 通过公开 Google Maven 取得 `com.google.ai.edge.litertlm:litertlm-android:0.18.1` AAR/POM；完整 AAR ZIP 校验通过。AAR 大小 `20,905,360` bytes，SHA-256 `05c6bdc8acda834efcca12c0ad5d06d2cd8c83e60770f056d8d222f699f3012b`；POM SHA-256 `3d6ea70f41bc6305c5985a52c5f5d00da97402d6531c9a395f3145cb5c1a89a5`。所有原始副本与清单在 `artifacts/verification/android-litertlm-aar-20261026/`（被忽略目录）。
- 真实 AAR 的 ZIP 路径/symlink 安全、`AndroidManifest.xml`、`classes.jar` 和 JNI ABI 门禁通过。manifest 是文本 XML，声明 `android:minSdkVersion="24"`，没有 `targetSdkVersion`，所以 SDK 状态为 `partial`：只确认 minSdk=24，不填 target SDK 代理值。
- JNI 只有 `arm64-v8a` 与 `x86_64`；`file` 确认为 AArch64/x86-64 ELF 64-bit shared object。许可证在根目录 `LICENSE`，第三方声明在根目录 `THIRD_PARTY_NOTICE.txt`，POM 为 Apache-2.0；工具已覆盖根目录与 `META-INF` inventory。
- `classes.jar` 的实际 API 包含 `Engine.initialize()/close()`、`Conversation.sendMessageAsync()` Flow/callback、`Conversation.cancelProcess()` 以及 text/image/audio 内容类型，没有视频内容类型。项目继续保持 `supportsVideo=false`；未修改 Gradle、未下载模型、未运行 LiteRT-LM 推理，Android 生产 VLM/video backend、模型兼容性、自然质量、GPU/NPU/energy 和 cancel 后 native drain 仍 `unverified`。
- `tools/inspect_android_vlm_artifact.py` 已补齐文本 manifest 与根目录 license/notice 解析；标准库回归 **7/7 passed**：`python3 -m unittest tools.tests.test_android_vlm_artifact -v`。旧“代理不可用/未验证 AAR”段落保留为历史，不覆盖本节的新鲜证据。
- 本轮设备通道刷新：`rtk proxy adb devices -l` 显示 TECNO KM9 `<Android-serial> device`（另有 `emulator-5554`），`rtk proxy xcrun devicectl list devices` 显示目标 iPhone `<iPhone-UDID> connected`。这些只证明通道可用，不升级为 LiteRT-LM 运行时或 P9 完成。Roadmap 仍 **5/12、P9 active**；全部 dirty/staged/untracked 修改继续保留，未 reset/clean/stash/commit/push/merge/release。

## 最新续接 — 2026-10-26 当前 dirty source iOS 真机 picker 清除回归复验

- 上一轮当前 dirty source 的新鲜真机报告在 Clear 后等待 `multimodal-input=尚未选择输入` 超时。系统调试确认产品 `MultimodalHost.clearImageFile()` 与 shared `MultimodalViewModel.selectImage(nil, nil)` 已在 MainActor 上同步更新；根因是 UI test helper `tapMultimodalClear` 先点击 Compose 子 `StaticText`，没有激活父 `multimodal-clear-input` Button。最小验证只调整 test helper，不修改产品清除逻辑。
- `demo/iosApp/UITests/SpeechDemoUITests.swift` 现在优先点击带 `multimodal-clear-input` 标识的父 Button，静态文本/渲染点仅作 fallback。使用当前 dirty source 通过 `rtk proxy xcodebuild` 重新签名 `build-for-testing`，结果 `** TEST BUILD SUCCEEDED **`；xctestrun 副本只在 `/tmp` 注入 `ODAI_MULTIMODAL_FILE_UI_TEST=1`。
- 物理 iPhone 16 Pro Max / iOS 27.0.1（UDID `<iPhone-UDID>`）重新执行 `SpeechDemoUITests/SpeechDemoUITests/testMultimodalImageFixturePickerCancellationAndClear`，证据 `/private/tmp/odai-picker-clear-button.xcresult`：**1 test / 0 failures / 0 skips**，40.178 秒，`** TEST EXECUTE SUCCEEDED **`。真实 Files picker 选择公开 fixture、二次打开取消保留输入、父 Button Clear 后显示“尚未选择输入”均通过。
- fixture `artifacts/P9/fixture.png` SHA-256 为 `60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606`；本轮未使用 iPhone 镜像、坐标、锁屏密码或私人媒体。该结果收口当前 dirty source 的真实系统 picker 选择/取消/清除证据，但不升级为物理手指触摸、OS background、Android 生产 VLM/video backend、自然质量/GPU/energy、minimum OS、binary archive 或全 P9 完成。
- 随后只读刷新设备通道时，`rtk proxy adb devices -l` 因 macOS USB plugin / ADB smartsocket `Operation not permitted` 失败，`rtk proxy xcrun devicectl list devices` 因 `Timed out waiting for CoreDeviceService to fully initialize` 失败；这是测试后的外部工具状态，不推翻刚才已完成的 iPhone XCTest，也不归因于产品代码。下一次真机操作前必须重新刷新通道。
- 继续保留所有 dirty/staged/untracked 修改，未 reset/clean/stash/commit/push/merge/release；Roadmap 仍 **5/12、P9 active**。

## 最新续接 — 2026-10-26 Android 生产 VLM backend 路线复核

- 只读复核官方 Android 运行时资料后，下一候选固定为 LiteRT-LM Android/Kotlin API（官方页：<https://developers.google.com/edge/litert-lm/android>）。Google Maven 元数据当前返回 `com.google.ai.edge.litertlm:litertlm-android` **0.18.1**；POM 声明 Apache-2.0，依赖 Gson 2.14.0、Kotlin reflect 2.4.0、coroutines-android 1.11.0。本轮没有新增依赖、下载模型、修改生产实现或把设备连接状态当作推理证据。
- LiteRT-LM 提供离线模型路径、流式图像消息和 GPU/NPU 方向，但官方 API 没有视频语义/帧序列承诺；项目必须先保持 `supportsVideo=false`，视频仅可由显式 `MediaExtractor`/`MediaCodec` 抽帧适配层接入。`Engine.initialize()` 的耗时和 Flow/callback 终止都不能替代现有 native drain/close 契约；取消时仍需等待底层终止后释放输入和 engine。
- MediaPipe LLM Inference 已标 maintenance-only，ExecuTorch Android 多模态入口仍 experimental，MLC Android 文档不提供当前可复用的 VLM/video API；它们不作为本包新生产路线。下一包先做 LiteRT-LM AAR `minSdk`/ABI/许可证/离线模型 manifest 的独立门禁，再实现 ModelStore 哈希、抽帧时间戳、cancel→drain→close 和 late-callback 过滤。没有实测 AAR/模型/真机推理前，Android 生产 VLM/video backend、自然质量、GPU/energy 继续 `unverified`。
- 代理无法拉取 LiteRT-LM AAR 本体（127.0.0.1:7897 不可用），所以 `minSdk`、ABI 和 native library 清单仍未验证；Maven 坐标存在不等于 API 26/TECNO 可运行。由于本会话的 `xcrun devicectl` 仍返回 `Timed out waiting for CoreDeviceService to fully initialize`、ADB daemon 仍被 macOS USB/权限层拒绝，本轮不重试真机安装或 UI 操作；这些属于外部工具通道证据，不归因于产品代码。Roadmap 仍 **5/12、P9 active**，所有 dirty/staged/untracked 修改继续保留。
- 本轮文档验证：`git diff --check` exit 0；标准库 `python3 -m unittest tools.tests.test_mlxvlm_benchmark_report -v` 为 **10/10 passed**。`pytest` 当前未安装（`No module named pytest`），不把它写成产品失败；两个保护文件 SHA-256 仍为 `project.pbxproj=b35bd984f784d4b13c0724f8c595d28ed96ddbe418e049547b762309fe9fd8f5`、`docs/validation/face-identity.md=6f130bd94debd5e8bffaba7b0db560553f7a367818d9cf37b72468e42bb70822`。
- 为下一次拿到 AAR 后的独立门禁，新增 `tools/inspect_android_vlm_artifact.py` 与 `tools/tests/test_android_vlm_artifact.py`（仅 Python 标准库）：覆盖 SHA-256/size、ZIP traversal/symlink、manifest/classes.jar、JNI ABI、license/notice、可选 `aapt2` min/target SDK 解析；当前合成回归 **6/6 passed**，本地 binary-AXML wrapper 也实际解析出 `minSdk=24`/`targetSdk=35`。工具不接入生产 Demo、不下载模型，也不把 synthetic artifact 当作 LiteRT-LM 证据。

## 最新续接 — 2026-10-26 iOS OS background 外部激活链 A/B 复核

- 物理 iPhone `<iPhone-UDID>` 已由 CoreDevice 确认为 `connected`，`passcodeRequired=false`、`unlockedSinceBoot=true`，屏幕背光为 `activeOn`；本轮没有使用镜像、坐标或锁屏密码。此前重复运行当前 dirty source 的新鲜报告 `artifacts/verification/multimodal-demo-background-diagnostic-20261026/report-clean.json` 为 `failedGate=OS lifecycle notifications timeout`，ready marker 为新 PID `27059` / runId `DB766F59-9863-4DDD-8705-96DA77636234`，不是旧报告复用。
- 为排除当前源码回归，随后仅安装并运行之前在 2026-10-23 取得 14-gate 通过的旧 signed Demo/companion artifact（sourceCommit `d50eff18ed53db61bc46f050f411ab076d118ede`），重新复制同一公开 fixture 与固定模型，严格复用 `devicectl device process launch` 的前台 companion 触发方式。A/B fresh ready marker 为 PID `27089` / runId `0EFFE65D-D797-4B9E-9250-44E40511D65A`；结果 `artifacts/verification/multimodal-demo-background-diagnostic-20261026/report-old.json` 同样为 `failedGate=OS lifecycle notifications timeout`。
- 当前证据把失败层定位为本机 CoreDevice/`devicectl` 激活链：旧通过 artifact 与当前 dirty source 均无法在本机触发四个 UIKit lifecycle notifications。没有修改生产 lifecycle 代码，也没有把该结果写成通过；`osBackground=unverified` 继续保留。此前旧 artifact 的 2026-10-23 通过报告仍保留为历史、checkout-specific 证据，不能替代当前 dirty source。
- 本轮只在被忽略的诊断 artifact 中加入 companion 自证日志尝试；该 artifact generic rebuild 因无匹配 provisioning profile 失败，未安装、未进入产品源码。工作树原有 dirty/staged/untracked 修改均保留，未 reset/clean/stash/commit/push/merge/release。下一步记录外部门槛后继续可独立推进的 Android 生产 backend、P9/P10/P11 其他缺口；Roadmap 仍 **5/12、P9 active**。

## 最新续接 — 2026-10-26 当前 dirty source iOS shared AX active validation

- 工作树仍为 `/Users/daubert/Github/ondevice-ai-kit/.worktrees/foundation` / `feat/sdk-foundation`；本包只写入被忽略的 `artifacts/verification/multimodal-demo-active-dirty-20261026/`，保留全部原有 dirty/staged/untracked 修改，未 reset/clean/stash/commit/push/merge/release。基准 `HEAD=9288ac5a28dd5e2e55a23f9be9d5ec8108199fc4`；dirty source 指纹 `560b236527c196187f99055def5d145b61fc1d98` 及 manifest 保存在该 artifact 目录。指纹是当前工作树快照，不是 Git ref。
- 当前 dirty source 的 `:demo:shared:linkDebugFrameworkIosArm64` 与 `:demo:shared:linkDebugFrameworkIosSimulatorArm64` exit `0`（14 actionable tasks，6 executed）；独立验证工程签名 `build-for-testing` 输出 `** TEST BUILD SUCCEEDED **`。使用既有 Team `4X996UV8LC`、固定 Swift/Xcode 缓存、公开 `artifacts/P9/fixture.png` 和 `artifacts/P9/verified` 14 文件模型，没有读取私人媒体、使用 iPhone 镜像/坐标或锁屏密码。
- 物理 iPhone 16 Pro Max / iOS 27.0.1（UDID `<iPhone-UDID>`）安装并启动 `io.ondeviceai.multimodalmlxdemo`，参数为 `--multimodal-mlx-local-model --multimodal-demo-validation --multimodal-demo-active-validation`；PID `26996`、runId `F5372494-7EA3-4D79-A0ED-56081586D404`。原始报告 `artifacts/verification/multimodal-demo-active-dirty-20261026/demo-active-integration.json`，严格摘要 `strict-summary.json`，validator exit `0`。
- 当前源码的共享页面 active 包 **11/11 gates true**：bootstrap、available、pageOpened、inputAction、pickerReply、activeCancel、reentry、activeLeave、noAutoRestart、returnReentry、closed。cancel/leave 在真实 delivered update 后由共享 AX 动作进入 `CANCELLED` 且无 output；两次同进程 reentry 为 `SUCCEEDED`，backend/model/runtime identity 精确匹配固定 `SmolVLM2`。`touchAcceptance=unverified`、`systemFileSelection=unverified`、`osBackground=unverified`，因此不扩展为物理触摸、真实系统 picker、OS 后台或全 P9/P11 完成。
- 下一步继续保留 Android 生产 VLM/video backend、自然质量/GPU/energy、minimum OS、binary archive、独立全范围 review 及 P10/P11 其他缺口；Roadmap 仍 **5/12、P9 active**。

## 最新续接 — 2026-10-26 Android 导入中后台/回前台真机回归

- 工作树仍为 `/Users/daubert/Github/ondevice-ai-kit/.worktrees/foundation` / `feat/sdk-foundation`；保留全部 dirty/staged/untracked 修改，未 reset/clean/stash/commit/push/merge/release。两个保护文件 hash 未变。
- 在 TECNO KM9 / Android 15 / API35（serial `<Android-serial>`）安装当前 dirty source 的 Kotlin-only Demo/test APK，运行 `io.ondeviceai.demo.test/io.ondeviceai.demo.MultimodalPickerInstrumentation`。新增 `background-active-import` 使用 test APK 自有公开 slow fixture：确认 Activity-owned import active 且 UI 尚未发布输入，执行 `moveTaskToBack(true)`，再以 `REORDER_TO_FRONT` 回到同一 Activity；`backgroundMidCopy=passed-import-retained-after-background-return`，输入最终保留并完成。
- 同次真机 bundle 全部通过：`actualSelectedSystemFile=passed-documents-provider-real-picker`、system picker cancel、Activity-owned mid-copy、busy reselection、synthetic callback/copy/clear、foreground、cleanup；`status=passed`，instrumentation exit `0`。原始证据：`artifacts/verification/multimodal-android-lifecycle-20261026/device-picker.log` / `.exit`。
- 设备刷新 artifact `artifacts/verification/multimodal-android-lifecycle-20261026/devices-now.log` 显示 Android `<Android-serial>` 为 ADB `device`，物理 iPhone `<iPhone-UDID>` 为 CoreDevice `connected`；本轮没有使用 iPhone 镜像、坐标或锁屏密码。
- 当前 dirty source 的 Android unit/APK/test APK/lint fresh build 为 **BUILD SUCCESSFUL**（570 actionable tasks，38 executed），原始证据：`artifacts/verification/multimodal-android-lifecycle-20261026/build-rerun.log` / `.exit`。本次不需要修改生产 lifecycle 逻辑；现有 `onStop` 在 Activity-owned picker/import active 时传递 `preservePicker=true`，由真机回归锁定该行为。
- 对独立 review 的 assertion-flags LOW 建议做了可逆验证：移除 androidTest 全局 flags 后 APK 构建仍 exit `0`，但真机 runner 在 DocumentsUI real-selection 阶段无终态，已以 exit `130` 终止；日志为 `build-no-assertions.log` / `device-picker-no-assertions.log`。恢复原配置后重新构建并安装，`device-picker-restored.log` / `.exit` 再次 `status=passed` / exit `0`，因此本轮保留现有 flags，不把该探索性失败归因于产品代码。
- slow fixture 为 test APK 自有公开固定字节，不读取私人媒体；`inference=unverified-no-android-backend`。仍未收口 Android 生产 VLM/video backend、自然质量/GPU/energy、minimum OS、binary archive、独立全范围 review 及其他 roadmap 缺口；Roadmap 仍 **5/12、P9 active**。

## 最新续接 — 2026-10-26 双真机 picker 收口与临时诊断清理

- 工作树仍为 `/Users/daubert/Github/ondevice-ai-kit/.worktrees/foundation` / `feat/sdk-foundation`；保留全部 dirty/staged/untracked 修改，未 reset/clean/stash/commit/push/merge/release。两个保护文件 hash 未变。
- 已移除上一轮真实 picker 诊断专用代码：`MultimodalHost.seedUITestFixtureIfRequested()`、`MultimodalInputStore` 的 Documents logger 及所有 host/store/picker 日志调用、UI test 的失败时 debug print。`ODAI_MULTIMODAL_FILE_UI_TEST` 仍只作为测试 gate；fixture 改由验证 artifact 通过 `devicectl device copy to` 放入 App Documents，不写入生产 host。
- 当前 dirty source 重新签名构建 iOS 真机 UI bundle：`artifacts/verification/p9-multimodal-picker-20261026/clean-build.log` 为 `** TEST BUILD SUCCEEDED **`；仅在 artifact xctestrun 注入 `ODAI_MULTIMODAL_FILE_UI_TEST=1`。公开 `artifacts/P9/fixture.png`（170 bytes，SHA-256 `60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606`）已复制到物理 iPhone App Documents。
- 物理 iPhone 16 Pro Max / iOS 27.0.1（UDID `<iPhone-UDID>`）直接执行 `SpeechDemoUITests/SpeechDemoUITests/testMultimodalImageFixturePickerCancellationAndClear`，结果 `artifacts/verification/p9-multimodal-picker-20261026/ios-picker-clean2.xcresult`：**1 test / 0 failures / 0 skips**。真实流程覆盖 Files picker 选择 fixture、二次打开后取消保留输入、Clear 释放输入；未使用镜像、坐标或锁屏密码。
- Android 当前 dirty source 的本地门禁仍为 **BUILD SUCCESSFUL**（Demo unit test、APK、test APK、lint；既有完整门禁 570 actionable tasks，44 executed；本轮 test APK 重建也成功）。首个真机 instrumentation 发现 test APK 的 Kotlin provider 在独立进程缺少 `kotlin.jvm.internal.Intrinsics`，随后移除 provider/instrumentation 中的 Kotlin `.use {}` 运行时依赖，并收窄 DocumentsUI root 行匹配以避免工具栏标题误选。TECNO KM9 / Android 15 / API35（serial `<Android-serial>`）重跑结果 `artifacts/verification/multimodal-android-cleanup-20261026/device-picker-real-documentsui-rerun2.log` / `.exit`：`status=passed`，包含 `actualSelectedSystemFile=passed-documents-provider-real-picker`、system picker cancel、Activity-owned mid-copy、busy reselection、synthetic callback/copy/clear、foreground 和 cleanup。
- 这项 `actualSelectedSystemFile` 证据来自真实 DocumentsUI 与 test APK 自有 `MultimodalFixtureDocumentsProvider` 的公开固定字节，不读取私人媒体；仍不升级为 Android 生产 VLM/video backend、自然质量或资源验收。`inference=unverified-no-android-backend`。设备刷新：`xcrun devicectl list devices` 显示目标 iPhone `connected`，`adb devices -l` 显示 `<Android-serial> device`；完整输出见 `artifacts/verification/multimodal-android-cleanup-20261026/devices-rerun-20261026.log`；Roadmap 仍 **5/12、P9 active**。
- 清理后门禁：iOS bridge/store/factory 模拟器 **75/75 passed、0 failures、0 skips**（`/tmp/odai-multimodal-cleanup-20261026c/tests2.xcresult`）；仓库级 `rtk proxy ./scripts/verify-android.sh` fresh exit 0（725 actionable tasks；`artifacts/verification/multimodal-android-cleanup-20261026/full-android-verify-20261026.log`）；`git diff --check` 通过。后续保留 P9 的 Android 生产 backend、自然质量/GPU/energy、minimum OS、binary archive、独立全范围 review 及其他 roadmap 缺口，不把本检查点写成全 P9 完成。

## 最新续接 — 2026-10-25 Android 模拟器 Kotlin picker/in-flight 回归（iPhone 真机仍需用户解锁）

- 用户不在 iPhone 真机旁，本轮没有使用 iPhone 镜像、镜像坐标、锁屏密码，也没有操作物理 Android；只使用本地 Android 模拟器 `Medium_Phone`（`emulator-5554`，API 37）。
- 当前 dirty source 构建出的 app/test APK 安装后，直接运行 `io.ondeviceai.demo.test/io.ondeviceai.demo.MultimodalPickerInstrumentation`；退出码 `0`，bundle `status=passed`，并通过 `optOut`/`optIn`、真实 Android picker 打开/取消、Activity-owned mid-copy、busy reselection、synthetic Activity result/copy/clear、前后台和 owned-directory cleanup。
- 关键结果：`systemPicker=passed-open-cancel-without-media-read`、`activityMidCopy=passed-activity-owned-import-active`、`busyReselection=passed-first-import-retained-second-picker-rejected`、`syntheticResult=passed-real-callback-copy-host-clear-fixture-only`、`foreground=passed-no-input-no-auto-generation`、`cleanup=passed-new-owned-directories-released`。
- 原始日志、退出码、API/设备身份和 source provenance：`artifacts/verification/multimodal-android-emulator-20261025/`。结果继续明确 `actualSelectedSystemFile=unverified-synthetic-result-only`、`inference=unverified-no-android-backend`；不升级为 Android 生产推理、真实文件选择或自然质量证据。Roadmap 仍 **5/12、P9 active**。

## 最新续接 — 2026-10-26 CoreDevice 外部门槛复核（真机仍需用户解锁）

- 当前物理 iPhone `<iPhone-UDID>` 未执行任何解锁、镜像或 UI 操作；刷新 `xcrun devicectl list devices` 直接在 CoreDevice 初始化阶段失败：`Timed out waiting for CoreDeviceService to fully initialize`（`com.apple.coredevice.devicectl error 1`）。因此本轮没有启动真机 XCTest，也没有把该外部失败归因于源码。
- 已有本地 dirty source 证据仍有效：fixture seed 显式错误诊断修复后的 iOS 模拟器 bridge/store/factory **75/75 passed**；`git diff --check` 通过，保护文件 hash 未变。设备恢复且由用户本人解锁后，继续直接复用 `artifacts/verification/p9-multimodal-picker-20261025/SpeechDemo_picker_after_fix_iphoneos27.1-arm64.xctestrun`，目标真实 picker `1/0/0`。
- README 的多模态状态说明已同步到当前证据：Apple 单图/短视频 native 与 KMP/Demo 生命周期已记录；Android 仅类型化 contract/client 与明确 unavailable 路径，仍没有生产 VLM/video backend；自然质量、GPU/energy、最低系统、增强 binary archive 和独立全范围审查继续明确为未收口。仅更新文档，没有改变实现或验收等级。

## 最新续接 — 2026-10-25 fixture seed 错误诊断收口（真机仍需用户解锁）

- 针对上一轮独立 review 保留的 LOW 建议，`demo/iosApp/MultimodalHost.swift` 的 UI-test fixture seed 已从静默 `try?` 改为显式 `do/catch`；成功和失败都只通过现有 DEBUG + `ODAI_MULTIMODAL_FILE_UI_TEST=1` 受控日志记录，不改变生产路径或 fixture 内容。
- 从当前 dirty source 重新生成 `/tmp/odai-multimodal-bridge-seed-fix2/MultimodalBridgeTests.xcodeproj`，使用本地 `apple` package、iOS 27.1 模拟器 `iPhone Duo`（UDID `DF2DC7EA-257B-4E5F-9AFD-EC4BD2ED6D7F`）执行全套 bridge/store/factory XCTest；首个普通沙箱命令只在 CoreSimulator/SwiftPM 缓存权限阶段失败，使用同一固定缓存的受控 escalated 路径后结果为 **75 tests / 0 failures / 0 skips，`** TEST SUCCEEDED **`**。xcresult：`/tmp/odai-multimodal-bridge-seed-fix2/build/Logs/Test/Test-MultimodalBridgeTests-2026.10.25_23-07-10-+0800.xcresult`；仅有既有 QoS priority-inversion 和 Xcode build-number parse warnings。
- `git diff --check` 通过；保护文件 hash 未变；未 reset/clean/stash/commit/push/merge/release。此前 fixture seed LOW 建议现已关闭；独立 review 仍不是全 roadmap APPROVE。物理 iPhone picker 仍保持 `1/0/0` 未验，必须等用户本人解锁后直接复用 `artifacts/verification/p9-multimodal-picker-20261025/SpeechDemo_picker_after_fix_iphoneos27.1-arm64.xctestrun`。

## 最新续接 — 2026-10-25 当前 dirty source 本地门禁复核（真机仍需用户解锁）

- 在保留全部 dirty/untracked 修改、未使用 iPhone 镜像/镜像坐标/锁屏密码的前提下，当前生成的 `MultimodalBridgeTests.xcodeproj` 在 iOS 27.1 模拟器 `iPhone Duo`（UDID `DF2DC7EA-257B-4E5F-9AFD-EC4BD2ED6D7F`）重新执行；xcresult `/tmp/odai-multimodal-bridge-current/Logs/Test/Test-MultimodalBridgeTests-2026.10.25_22-40-16-+0800.xcresult` 明确为 **75 passed / 0 failed / 0 skipped，`** TEST SUCCEEDED **`**。现有 QoS priority-inversion runtime warnings 仍是测试等待告警，不是失败。
- 当前 Android Demo 以受控本机缓存重跑 `:demo:androidApp:testDebugUnitTest :demo:androidApp:assembleDebug :demo:androidApp:assembleDebugAndroidTest :demo:androidApp:lintDebug --offline`，结果 **BUILD SUCCESSFUL**，570 actionable tasks（3 executed、567 up-to-date），lint 无 error。
- 随后在同一受控环境重跑仓库 `rtk proxy ./scripts/verify-android.sh`，结果 **BUILD SUCCESSFUL**，725 actionable tasks（44 executed、681 up-to-date）；末尾明确报告 Android JVM tests、lint 和 APK builds passed。该门禁仍不包含 Android 生产 VLM/video inference。
- 当前 benchmark/consumer 本地定向验证为 **12/12 passed**：`MLXVLMBenchmarkReportTests`、Swift `BenchmarkMeasurements` 行为测试和独立 consumer 生成测试；修复后的 validator 对 `artifacts/verification/apple-mlxvlm-benchmark-30-20261025-fix2/device-report.json` 使用 `--expected-samples 30` 通过，确认 `sampleProfile=1-warmup-30-samples`、30/30 synthetic rubric。`swift package dump-package` 仍因受限缓存触发 `sandbox_apply: Operation not permitted`，这是环境级未验项，不改写源码结论。
- 旧 code-review 的 presenter HIGH、Android broad-catch MEDIUM 与 fixture seed 写入错误 LOW 建议均已在当前 dirty source 关闭并有回归/诊断；真实物理 iPhone picker 仍未取得 `1/0/0`；用户解锁后必须直接复用 `artifacts/verification/p9-multimodal-picker-20261025/SpeechDemo_picker_after_fix_iphoneos27.1-arm64.xctestrun`，不能用模拟器结果替代。
- `git diff --check` 通过；保护文件 hash 仍为 `project.pbxproj=b35bd984f784d4b13c0724f8c595d28ed96ddbe418e049547b762309fe9fd8f5`、`docs/validation/face-identity.md=6f130bd94debd5e8bffaba7b0db560553f7a367818d9cf37b72468e42bb70822`。未 reset/clean/stash/commit/push/merge/release；Roadmap 仍 **5/12、P9 active**。

## 最新续接 — 2026-10-25 模拟器真实 Files picker 回归通过（真机仍未验）

- 在用户暂时不在真机旁的约束下，本轮没有使用 iPhone 镜像、镜像坐标或锁屏密码；只使用 iOS 27.1 模拟器 `iPhone Duo`（UDID `DF2DC7EA-257B-4E5F-9AFD-EC4BD2ED6D7F`）和当前 dirty source。模拟器 CoreSimulatorService 先因沙箱缓存权限失效，随后在允许本机 Xcode/CoreSimulator 缓存的验证环境恢复；这不是源码失败。
- 为真实系统 Files picker 提供可见容器，验证 artifact `artifacts/verification/p9-multimodal-picker-20261025/simulator-ui-ocr-rerun4/` 使用 artifact-only `ui-test.plist`（`UIFileSharingEnabled=true`、`LSSupportsOpeningDocumentsInPlace=true`），并只复制公开合成 `artifacts/P9/fixture.png` 到该模拟器 App Documents。fixture SHA-256 为 `60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606`；tracked 生产 `demo/iosApp/Info.plist` 未改动。
- 修正 `demo/iosApp/UITests/SpeechDemoUITests.swift` 的系统 picker helper：`浏览`/取消改为跨 picker 层级查找；fixture 改为点击真实 `Cell(label: "fixture, png, …")`，避免点击静态文本只选中而不触发 delegate。当前 artifact `build5.log` 为 `** TEST BUILD SUCCEEDED **`。
- 最终执行命令使用完整 selector `SpeechDemoUITests/SpeechDemoUITests/testMultimodalImageFixturePickerCancellationAndClear`，结果 `artifacts/verification/p9-multimodal-picker-20261025/simulator-ui-ocr-rerun4/test2.xcresult`，日志 `test2.log`：**1 test / 0 failures / 0 skips，`** TEST EXECUTE SUCCEEDED **`**。流程实际打开 Files picker、进入“我的 iPhone”、显示并点击 `fixture.png`、异步导入成功；第二次 picker 取消保留输入，随后 Clear 释放输入。
- 这只是模拟器系统 picker/UI 证据，不替代物理触摸或真实 iPhone 文件选择；真机 picker 仍保持 `1/0/0` 未验，必须等用户亲自解锁设备后直接复用真机 xctestrun。临时 Documents logger、fixture seed 和 UI debug `print` 仍保留到真实设备日志回读后再清理。Roadmap 仍 **5/12、P9 active**。
- 本轮 `git diff --check` 通过；保护文件当前 hash 仍为 `project.pbxproj=b35bd984f784d4b13c0724f8c595d28ed96ddbe418e049547b762309fe9fd8f5`、`docs/validation/face-identity.md=6f130bd94debd5e8bffaba7b0db560553f7a367818d9cf37b72468e42bb70822`。未 reset/clean/stash/commit/push/merge/release。

## 最新续接 — 2026-10-25 无真机时的模拟器回归与 Kotlin fixture 收口

- 用户暂时不在设备旁，本轮已终止等待解锁的真机 XCTest 会话 `28306`（exit 130），没有继续使用 iPhone 镜像、镜像坐标或真机 UI 操作。真机 picker 的 `1/0/0` 仍等待物理设备解锁后再跑。
- 在 iOS 27.1 模拟器 `iPhone Duo`（UDID `DF2DC7EA-257B-4E5F-9AFD-EC4BD2ED6D7F`）重跑当前 dirty source 生成工程。完整 `MultimodalBridgeTests` 为 **75 tests / 0 failures / 0 skips**，结果：`artifacts/verification/multimodal-ios-demo-dirty-20261025/build-final/Logs/Test/Test-MultimodalBridgeTests-2026.10.25_21-10-28-+0800.xcresult`。
- 复现并定位了原先记录的 3 个失败断言：`background()` 在 picker 回调后按新 `preserveImportOnNextBackground` 竞态修复保留异步导入，旧测试却仍要求该场景回收 token。测试已改名为 `testLateSuccessfulImportIsRetiredAfterClearLeaveCloseAndOwnerRelease`，仅保留 clear/leave/close/owner-release 的迟到回调回收覆盖；picker 退场后的 background 保留由 `testPickerDismissalBackgroundAfterReplyDoesNotCancelImport` 覆盖。生产逻辑未因该修复再改动。定向测试和完整 75 项均通过。
- Android Demo 仍是 Kotlin/Compose：`demo/androidApp/src/androidTest/kotlin/io/ondeviceai/demo/MultimodalFixtureProvider.kt` 是唯一 fixture provider；临时 Java provider 已删除。当前 `testDebugUnitTest`、`assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 组合命令 **BUILD SUCCESSFUL**，570 tasks（3 executed、567 up-to-date），lint 无 error。
- `git diff --check` 通过；受保护文件 hash 仍为 `project.pbxproj=b35bd984…fd8f5`、`docs/validation/face-identity.md=6f130bd9…70822`。所有现有 dirty/untracked 修改继续保留，未 reset/clean/stash/commit/push/merge/release。临时 Documents logger、fixture seed 和 UI `print` 仍保留到下一次真实设备日志回读后清理。

## 最新续接 — 2026-10-25 picker 退场时序 host 回归（非镜像）

- 在上一检查点之后补齐了 picker 回调、退场通知与 host 后台切换的完整竞态回归：先以当前实现得到 RED，再用 `MultimodalFilePicker` 的 `finishing` reentrancy guard 和 host 的 `preserveImportOnNextBackground` 保留回调期间的导入，得到 GREEN。覆盖场景是文件已选中、picker 退场回调仍在执行、随后收到 background 通知时不能取消刚启动的异步导入。
- 新增回归位于 `probes/ios-multimodal-bridge/MultimodalFilePickerTests.swift` 与 `MultimodalHostTests.swift`；Picker 专项 **5/5**、独立 bridge/store/factory 全套 **73/73** 通过。现有临时 `print`/Documents logger/fixture seed 仍只为下一次真机日志诊断保留，拿到日志后必须移除。
- 已从当前 dirty source 重新执行签名 iOS 真机 `build-for-testing`，`** TEST BUILD SUCCEEDED **`。新产品二进制 SHA-256 为 `SpeechDemo.app/SpeechDemo=7cdbf08fff372fd1848daf242a2a57881e905873fe6886f0b5b2de276129164c`、`SpeechDemoUITests.xctest=8ca23ad8eb0bd6917068cc5b08d56c3bb12616a65097bd20a423613746b7c072`；验证 artifact 为 `artifacts/verification/p9-multimodal-picker-20261025/device-derived-after-host-fix/`。生成后的 xctestrun 仅在 artifact 内补入 `ODAI_MULTIMODAL_FILE_UI_TEST=1`，SHA-256 `a4fad0364c6027853a5d051830ef9a078cad7e0def7737bbd1fb137672206b48`。设备恢复后再对物理 iPhone `<iPhone-UDID>` 直接执行 `test-without-building`。
- 已对同一物理 UDID 发起真实 `test-without-building`，没有调用镜像；`xcodebuild` exit **70**，兼容设备条目明确返回 `The device needs to be unlocked to continue`。结果保存在 `artifacts/verification/p9-multimodal-picker-20261025/host-fix-device-attempt.{log,exit,xcresult}`。解锁后继续使用同一 xctestrun 和 `testMultimodalImageFixturePickerCancellationAndClear`，不复用旧包。
- 随后用可写本机 Instruments 缓存执行 `xcrun xctrace list devices`，目标 `<iPhone-UDID>` 被列在 **Devices Offline**；同机另一台 iPhone 也为 offline。该结果与 `devicectl` 的 CoreDeviceService 初始化超时相互印证，当前阻断是设备/CoreDevice 外部状态，不是镜像或源码失败。
- CoreDevice 恢复到 `available (paired)` 后，对新包再次执行真实 `test-without-building` 仍 exit **70**；`xcrun devicectl device info lockState --device <iPhone-UDID> --json-output -` 返回 `CoreDeviceError 10003` / `RemotePairingError 1016`，明确为 `The device has not been unlocked recently`。结果保存在 `artifacts/verification/p9-multimodal-picker-20261025/host-fix-device-rerun.{log,exit,xcresult}`；解锁一次后继续同一 xctestrun。
- 本续接不使用 iPhone 镜像、镜像画面或镜像坐标；CoreDevice/XCTest 的外部门槛只能如实记录，不能用镜像结果替代真实设备 picker 验收。现有 dirty/untracked 修改、保护文件和未提交状态均保留，未 reset/clean/stash/commit/push/merge/release。

## 最新续接 — 2026-10-25 真机 picker 续接与 AutomationMode 外部门槛（非镜像）

- 本轮纠正了之前误用 iPhone 镜像的路径。验收目标仍是物理 iPhone 16 Pro Max / iOS 27.0.1，UDID `<iPhone-UDID>`；没有使用镜像画面、镜像坐标或私人媒体。现有 dirty/untracked 修改和两个保护文件均保留，未 reset/clean/stash/commit/push/merge/release。
- 已对最新 `SpeechDemo_iphoneos27.1-arm64.xctestrun` 仅补入测试环境变量 `ODAI_MULTIMODAL_FILE_UI_TEST=1`，没有修改产品工程。真机 `debug-log15`、`debug-log16`、`debug-log17` 均在 XCTest 初始化阶段因 `Timed out while enabling automation mode` 失败，未进入 picker/import 断言；因此不能改变 P9 picker 的通过结论。
- 为排除项目进程残留，已只终止真机上两个旧 `SpeechDemo` 进程；随后做了一次可逆 userspace reboot。设备已重新启动并显示 `available (paired)`，但 CoreDevice 当前返回 `device was still locked`，需要解锁一次后才能继续 XCTest。没有修改系统设置、签名资产或其他 App。
- 上一份真正进入 Files picker 的 `multimodal-picker-debug-log12.xcresult` 仍是 `passedTests=0/failedTests=1/skippedTests=0`，失败位于异步导入完成等待；当前临时 `print`/Documents logger/fixture seed 只用于下一次真机诊断，取得日志后必须移除。只有真实物理设备拿到 `1/0/0` 才能更新 picker 验收结论。
- 设备锁定期间，从当前 dirty source 重新生成独立 `SpeechDemo` 工程并执行完整 iOS Simulator App/UITest bundle 编译，`** BUILD SUCCEEDED **`；artifact 为 `artifacts/verification/p9-multimodal-picker-20261025/dirty-generated-app2/`。该结果只证明生成工程可编译，不替代物理真机 picker 验收。
- 本次续接再次确认不使用 iPhone 镜像：没有把镜像画面、镜像坐标或镜像结果纳入验收。物理设备先返回 `device was still locked`，随后 `xcrun devicectl` 多次返回 `Timed out waiting for CoreDeviceService to fully initialize`；该外部门槛不能通过镜像绕过。设备解锁且 CoreDeviceService 恢复后，直接重跑同一物理 UDID 的 `test-without-building`，不要改走镜像路径。
- 在无法访问真机日志期间完成了一次有证据的竞态修复：`MultimodalFilePicker.finish` 原先在回调前清除 `isPresenting`，picker 退场通知因此会把刚启动的异步导入误取消。新增回归测试先以当前实现 RED，再用 `finishing` reentrancy guard 保留回调期间的 presenting 状态；Picker 专项 **5/5**、独立 bridge/store/factory 全套 **73/73** 通过。真实设备 `1/0/0` 仍未取得，临时 logger/fixture seed 必须保留到下一次真机日志回读后再移除。
- 已从修复后的 dirty source 完成新的签名真机 `build-for-testing`（`** TEST BUILD SUCCEEDED **`）；待运行 xctestrun 为 `artifacts/verification/p9-multimodal-picker-20261025/SpeechDemo_picker_after_fix_iphoneos27.1-arm64.xctestrun`，SHA-256 `a4fad0364c6027853a5d051830ef9a078cad7e0def7737bbd1fb137672206b48`，两个测试环境均含 `ODAI_MULTIMODAL_FILE_UI_TEST=1`。当前 CoreDevice 仍超时，尚未安装或运行此 artifact。

## 最新续接 — 2026-10-25 P9 自然图 3 帧真机 smoke evidence（非镜像）

- 在物理 iPhone 16 Pro Max / iOS 27.0.1（`iPhone17,2`，UDID `<iPhone-UDID>`）上，使用已安装 `com.ondeviceaikit.probe.vlm`、现有固定模型/manifest 和公开自然视频抽帧 `frame-0`、`frame-1`、`frame-18`，只通过 `xcrun devicectl` 的 App Documents 与进程启动完成三次真实推理；没有使用镜像、坐标点击或私人媒体。
- 三份报告 `artifacts/verification/p9-natural-20261025/reports/frame-{0,1,18}.json` 均 `status=success`，汇总 `artifacts/verification/p9-natural-20261025/summary.json`。backend/model/runtime、device/OS identity 全部匹配；首 token p50/p95 `1314.134208/1321.358042 ms`，total p50/p95 `1800.751500/1884.726460 ms`，进程 RSS 峰值 p50/p95 `1,196,851,200/1,198,440,448 B`。
- 输出文本已保存；该包仅证明公开自然帧的真机推理/延迟/RSS smoke evidence，不含 ground truth 或独立人工质量裁定，`naturalImageQuality` 继续 `unverified`，不升级 P9/P11。现有工作树 dirty/untracked 修改全部保留，未 reset/clean/stash/commit/push/merge/release。

## 最新续接 — 2026-10-25 benchmark review 修复后真机重跑（非镜像）

- 针对独立 code review 的两个 MEDIUM，本轮已修复并验证：`--expected-samples 30` 现在要求 raw `sampleProfile=1-warmup-30-samples`，而默认 5 样本继续兼容缺失 profile 的旧报告；benchmark 运行异常、JSON 序列化或写盘失败不再显示为 `Finished`，会返回显式失败状态。新增 Swift helper/validator 回归后，`AppleMLXConsumerTests` + `MLXVLMBenchmarkReportTests` 为 **13/13**。
- 从修复后的当前源码重新生成并签名构建独立 consumer，安装到物理 iPhone 16 Pro Max / iOS 27.0.1（`iPhone17,2`，UDID `<iPhone-UDID>`），只使用 `xcodebuild`、`xcrun devicectl` 和 App Documents；没有使用镜像、坐标点击或私人媒体。固定模型设备清单仍为 14/14。
- 最新原始报告：`artifacts/verification/apple-mlxvlm-benchmark-30-20261025-fix2/device-report.json`，SHA-256 `186a2077bf966dfb69cd89034d940c79a09b91a6d09d74b05172dc862c3c73d1`；汇总：同目录 `summary.json`，SHA-256 `4cf8f2430d896addd2f2d821d815b795647924c83f09b9d825ed5987a6fa61d6`。`runId=ACC626B8-725C-4820-9E13-57A758B8314C`、`sampleProfile=1-warmup-30-samples`、warm-up 成功、测量 30/30、30/30 synthetic rubric，固定模型/runtime/fixture identity 全部匹配。
- nearest-rank 结果：load p50/p95 `337.240167/364.418917 ms`；首 update `779.335084/809.109292 ms`；total `940.974208/971.115375 ms`。RSS baseline `40,124,416 B`、peak `1,192,296,448 B`、delta `1,152,172,032 B`，方法仍为 `process-resident-mach-task-basic-info-10ms`。
- 当前 benchmark source diff（含 helper、strict validator、tests、spec/plan）SHA-256 为 `58afa193a03d8b4df1117cd63ff87d940690bc6a9847431aaea964728e4ab9e5`；full tools suite 仍有既有环境失败（135 tests，13 failures/6 errors），本包定向测试和真实 artifact validator 已通过。架构 reviewer lane 仍因 429 unavailable；不能写成独立 ARCHITECTURE CLEAR 或全 roadmap APPROVE。

## 最新续接 — 2026-10-25 固定单图 benchmark 1 warm-up + 30 samples（非镜像）

- 为补齐 P9 固定单图的规模化统计，本轮从当前源码生成独立 `MLXVLMConsumer` artifact，使用固定本地 Swift/Xcode 缓存和既有 Team `4X996UV8LC` 完成签名 iOS 真机构建；未修改产品工程、签名资产或两个保护文件。新增显式启动参数 `--sdk-mlxvlm-benchmark-30`，旧 `--sdk-mlxvlm-benchmark` 的 1 warm-up + 5 行为保持不变；validator 新增 `--expected-samples 30` 严格门禁。
- 目标物理设备为 iPhone 16 Pro Max / iOS 27.0.1（`iPhone17,2`，UDID `<iPhone-UDID>`）；全程只用 `xcodebuild`、`xcrun devicectl` 和 App Documents 文件服务，没有使用 iPhone 镜像、镜像画布、坐标点击或私人媒体。公开 `artifacts/P9/fixture.png` 与已校验的 14 文件模型复制后，设备端模型清单为 14/14。
- 原始设备报告：`artifacts/verification/apple-mlxvlm-benchmark-30-20261025/device-report.json`，SHA-256 `dd75f54cb84b821261e06639d032af2bba539fbdf1706023691479eb2e7c9f66`；汇总：`.../summary.json`，SHA-256 `3d970842aec57e0e79519f6588a49beb0b7ff3f9bdd776611aa3648d57fb7b83`。报告 `runId=D824ACA7-42E8-492D-B9A5-6FE3868551F5`、`sampleProfile=1-warmup-30-samples`、`warmup=success`、测量项 30/30，`mlx.smolvlm2-500m`、模型 revision `fa57db...2268`、runtime revision `6e338e...18a`、fixture hash `60e528...a606` 全部匹配。
- nearest-rank 结果：load p50/p95 `334.141875/372.181459 ms`；首 update `782.097334/819.266917 ms`；total `943.978709/981.462959 ms`。30 次输出均为 `A flag with a red square in the center.`，synthetic rubric `30/30`；进程 RSS baseline `34,127,872 B`、peak `1,186,676,736 B`、delta `1,152,548,864 B`，方法为 `process-resident-mach-task-basic-info-10ms`。
- 该证据只补齐固定合成图片的 30 样本延迟/RSS 统计，不是自然图片质量、Metal GPU peak、energy、冷启动、跨设备预算或 P11 统一验收；`metalGpuPeak`、`energy`、`naturalImageQuality` 继续 `unverified`。构建与 validator 测试为 12/12，通过 `git diff --check`；未 commit、stage、push、merge 或 release，roadmap 仍 5/12、P9 active。
- 全量 `tools/tests` 回归原始日志保存在 `.../tools-tests-full.log`：135 tests 中 13 failures/6 errors，失败集中于既有 distribution project 生成断言和系统 Ruby 2.6 加载 Ruby 3.4 `json` gem 的环境不兼容；本包的 `AppleMLXConsumerTests` 与 `MLXVLMBenchmarkReportTests` 均逐项通过，不能把全量环境失败写成 benchmark 通过或产品回归。

## 最新续接 — 2026-10-25 自然视频质量链路真机复核（非镜像）

- 针对用户指出“已有真机连接，不应使用 iPhone 镜像”，本轮已停止镜像路径。工作树仍为 `/Users/daubert/Github/ondevice-ai-kit/.worktrees/foundation`、分支 `feat/sdk-foundation`、HEAD `9288ac5a28dd5e2e55a23f9be9d5ec8108199fc4`；全部既有 dirty/untracked 修改保留，未 reset/clean/stash/commit/push/merge/release。
- 仅使用固定签名的 `IOSVideoSRProbe.app`、`xcodebuild` 和 `xcrun devicectl` 真机操作：安装到 iPhone 16 Pro Max / iOS 27.0.1、复制仓库公开 fixture `artifacts/P7/natural/prepared/input-180p-4x.mp4` 到 App Documents，并以 `--process-video` 启动；没有使用 iPhone 镜像、镜像画布或坐标点击。
- 最新设备报告：`artifacts/verification/p7-natural-device-20261025/device-documents/ios-video-sr-probe-777236F5-1760-4698-A44E-8EABAFAD81A5.json`。SR 与 bicubic 均 `state=success`，48/48 帧、1280×720、PTS 单调、HEVC/MOV、BT.709 元数据一致；设备副本 input SHA-256 与仓库 fixture 同为 `97682e3f9703e326bab3fbed06aa7a298b506d1965502288f5ed5037c51f2f1f`。
- `video_sr_quality.py` 对 48 对解码帧报告：SR `PSNR 36.776212 dB / SSIM 0.979250`，bicubic `38.872866 dB / SSIM 0.983506`，`colorComparability=matched-metadata`，但两路 `qualityVerdict` 继续为 `unverified`。这补充了自然视频统一色彩/编码的真机证据，不构成主观模型质量结论，也不升级 P7/P9/P11、GPU/能耗、最低系统或全 roadmap。
- 证据已追加到 `docs/validation/video-sr-backends.md` 顶部。下一步仍按独立门槛推进；禁止恢复镜像路径，除非用户明确改变验收目标。

## 最新续接 — 2026-10-25 全仓 Android verifier 重跑／review service 仍 unavailable

- 在上一节 dirty 多模态门禁之后，使用当前工作树和本地缓存重跑 `rtk proxy ./scripts/verify-android.sh`，exit **0**；`725 actionable tasks`，其中 `44 executed / 681 up-to-date`，末尾明确报告 Android JVM tests、lint 和 APK builds passed。该证据仍只覆盖 Android 本地门禁，不代表生产视频/VLM inference。
- 为当前 dirty scope 重试既有 `code-reviewer` 与 `architect` 两条独立 lane；两者再次因 `429 Too Many Requests` 超出重试限制退出，未产生审查内容。当前没有活动 review 进程；因此仍不能写成独立 APPROVE、ARCHITECTURE CLEAR 或 merge-ready。服务恢复后需重新启动两条 lane，而不是复用本地测试代替。
- 本轮没有改源码、保护文件、签名或远端；所有既有 dirty/untracked 内容继续保留。Roadmap 仍 **5/12、P9 active**。

## 最新续接 — 2026-10-25 dirty 多模态门禁复核／独立 review unavailable

- 在不修改两个保护文件、保留全部现有 dirty/untracked 内容的前提下，对当前多模态视频 Demo scope 做了本地门禁复核。`rtk proxy ./gradlew :demo:androidApp:testDebugUnitTest --offline --console=plain --rerun-tasks` 首次仅因 sandbox 无法创建 `~/.gradle` lock 失败；使用相同本地缓存的 escalated 路径后 **BUILD SUCCESSFUL**，245 tasks executed。随后 `rtk proxy python3 scripts/gradle-local.py :demo:androidApp:assembleDebug :demo:androidApp:assembleDebugAndroidTest :demo:androidApp:lintDebug --offline --console=plain` **BUILD SUCCESSFUL**，565 tasks（68 executed/497 up-to-date），无 lint error；仅保留既有 warning。
- 当前 dirty source 生成的 iOS bridge/store/factory artifact 工程 `artifacts/verification/multimodal-ios-demo-dirty-20261025/MultimodalBridgeTests.xcodeproj` 使用本地 `apple` package、iOS 27.1 simulator、`-collect-test-diagnostics never` 重跑，`** TEST SUCCEEDED **`，`72 tests / 0 failures / 0 skips`；xcresult 为 `artifacts/verification/multimodal-ios-demo-dirty-20261025/build/Logs/Test/Test-MultimodalBridgeTests-2026.10.25_09-27-44-+0800.xcresult`。XCTest QoS priority-inversion 与 Xcode build-number parse warning 仍是既有观察项，不是失败。
- 按 `code-review` 门禁尝试启动独立 `code-reviewer` 与 `architect` 两条 lane，但两者均因服务 `429 Too Many Requests` 超出重试限制而未返回审查证据。因此本轮**不**给当前 dirty scope 独立 APPROVE、ARCHITECTURE CLEAR 或 merge-ready 结论；需服务恢复后重新获取独立 review，不能用作者本地测试代替。
- 以上只是当前实现的本地验证，不升级为 P9/P11、Android 生产 inference、真实系统文件选择、物理触摸、自然质量、GPU/energy、minimum OS、binary archive 或全 roadmap 完成。Roadmap 仍 **5/12、P9 active**；未 commit、stage、push、merge 或 release。

## 最新续接 — 2026-10-25 iPhone 真机 Video Demo UI 成功／取消闭环

- 本轮明确停止使用 iPhone 镜像；目标设备始终是物理 iPhone 16 Pro Max / iOS 27.0.1，UDID `<iPhone-UDID>`，`xcrun devicectl list devices` 状态为 `connected`。所有动作均走 `devicectl`、`xcodebuild` 和 XCTest 真机 runner；没有用镜像画布或 AX 镜像点击替代真机测试。
- 保留当前 `/Users/daubert/Github/ondevice-ai-kit/.worktrees/foundation`、`feat/sdk-foundation`、HEAD `9288ac5a28dd5e2e55a23f9be9d5ec8108199fc4` 及全部既有 dirty/untracked 修改；未 reset/clean/stash/commit/push/merge/release。受保护 `demo/iosApp/OnDeviceAIDemo.xcodeproj/project.pbxproj` 与 `docs/validation/face-identity.md` 未被本轮修改。
- 在忽略的验证 artifact `artifacts/verification/roadmap-p7-video-ui-20261025-ruby34/SpeechDemo.xcodeproj` 上，用固定 Ruby `/Users/daubert/.rvm/rubies/ruby-3.4.11/bin/ruby`、`ODAI_VIDEO_DEMO=1`、Team `4X996UV8LC` 重新执行真机 `build-for-testing`，exit 0（`TEST BUILD SUCCEEDED`）。测试专用 `ui-test.plist` 仍只存在于 artifact，生产工程未开启文件共享 key。
- 固定公开 fixture 为 `artifacts/verification/apple-video-audio-20261021/DeviceHarness/input-audio.mp4`，SHA256 `6a1c2a7bb875c8d9502450e732901fcd21bca5be27da0ef6ceae58c5b64c0d00`。每次真机 UI runner 安装新 app container 后，先以 `xcrun devicectl device copy to --device <iPhone-UDID> --source .../input-audio.mp4 --destination Documents/odai-video-ui-fixture.mp4 --domain-type appDataContainer --domain-identifier io.ondeviceai.ondeviceaidemo` 重新放入项目 App Documents；没有读取私人媒体。
- `video-success7.xcresult`：`testVideoFixtureSuccessPlaybackSharingAndCleanup` 在真机通过。真实 XCTest 流程打开系统 Files picker，在“我的 iPhone/SpeechDemo”选择 fixture；再次打开 picker 后取消并保留输入；处理得到 `1280×720 · 48 帧 · 2.0 秒 · 音轨 1`；系统分享面板通过真实 `PopoverDismissRegion` 关闭；播放面板关闭；清理结果后保留输入。`xcrun xcresulttool get test-results summary` 报告该物理设备 `passedTests=1/failedTests=0`。
- `video-cancel1.xcresult`：`testVideoFixtureCancellationReentryAndBackground` 在同一物理设备通过。覆盖处理期间取消、`CANCELLED` 终态、同一输入重新处理成功、再次运行后 Home/回前台不自动重启且终态仍 `CANCELLED`；summary 报告 `passedTests=1/failedTests=0`。
- 失败包 `video-success5.xcresult` 仅暴露 iOS 27 分享面板没有“关闭/Close”按钮，已按真机 accessibility hierarchy 改为 `PopoverDismissRegion`；`video-success6.xcresult` 仅因 app 重装后的新容器未重新复制 fixture 失败，已通过每次 runner 前 `devicectl device copy to` 修正。测试命令必须保留 `-collect-test-diagnostics never`，否则 Xcode 可能启动额外 `devicectl diagnose` 并挂住收尾。
- 这补齐的是 **P7 iOS Video Demo UI 的 XCTest 真机证据**，不是人工物理手指触摸验收，也不代表 P9/P11、Android 生产视频 backend、自然视频质量、GPU/能耗、最低系统矩阵、binary archive/Swift interface 或全 roadmap 完成。Roadmap 仍 **5/12、P9 active**；后续继续保留这些独立边界。

## 最新续接 — 2026-10-25 Android Activity-owned mid-copy instrumentation 补强

- 在保留当前全部 dirty/staged/untracked 内容的前提下，补强 `MultimodalPickerInstrumentation`：二次点击前先通过 Activity 自有 `multimodalImport` job 的 active 状态确认真实复制仍在进行，再断言第二个 picker 没有启动；这不是只依赖 host 单测或立即连续点击。
- 使用固定缓存编译 `rtk proxy python3 scripts/gradle-local.py :demo:androidApp:assembleDebug :demo:androidApp:assembleDebugAndroidTest -PodaiTestRunner=io.ondeviceai.demo.MultimodalPickerInstrumentation --offline --console=plain`，**BUILD SUCCESSFUL**；仅有既有 Activity `!!` warning，无新增 error。
- 已授权 TECNO KM9 / Android 15 / API35 真机重新安装本项目 app/test APK 并运行 instrumentation，命令 exit **0**、bundle `status=passed`。新增字段 `activityMidCopy=passed-activity-owned-import-active`；同一 bundle 的 `busyReselection=passed-first-import-retained-second-picker-rejected`、`systemPicker=passed-open-cancel-without-media-read`、`syntheticResult=passed-real-callback-copy-host-clear-fixture-only`、`foreground=passed-no-input-no-auto-generation`、`cleanup=passed-new-owned-directories-released` 均通过。
- 原始输出：`artifacts/verification/multimodal-android-activity-midcopy-20261025/device-picker.log`；这补齐此前 scoped review 提出的 Activity busy-state/mid-copy coverage gap，但仍不等同真实系统文件选择、Android 生产推理或 P9 完成。未读取私人媒体、未下载依赖、未修改签名/保护文件。

## 最新续接 — 2026-10-25 iPhone 镜像画面可见但真实点击仍不可用

- 本轮先核对工作树，仍为 `/Users/daubert/Github/ondevice-ai-kit/.worktrees/foundation` / `feat/sdk-foundation`，HEAD `9288ac5a28dd5e2e55a23f9be9d5ec8108199fc4`；全部既有未提交修改保留，未 reset/clean/stash/commit/push/merge/release，保护文件不变。
- 授权 iPhone `<iPhone-UDID>` 仍可由 `xcrun devicectl` 识别。已确认安装包的真实 bundle identifier 是 `com.ugreen.ondeviceai.demo`；`rtk proxy xcrun devicectl device process launch --device <iPhone-UDID> --terminate-existing com.ugreen.ondeviceai.demo` 返回成功，进程列表出现 `OnDeviceAIDemo`（PID `23713`）。此前错误尝试的 `io.ondeviceai.demo` 只是未安装的 bundle，不代表产品失败。
- 本机 `com.apple.ScreenContinuity` 现在能显示 iPhone 画面（此前为连接/超时状态），但窗口 AX 树仍只有镜像容器；对镜像画布的真实坐标点击 `[42,138]` 返回 `windowNotFoundAtPosition((36.0, 215.0))`。因此当前仍没有可复核的物理触摸或真实系统文件选择证据，不能把 `devicectl` 启动成功或镜像截图升级为 P9 通过。
- 本轮没有读取私人媒体、没有安装新包、没有修改签名/保护文件；`touchAcceptance` 与 `systemFileSelection` 继续标记 **unverified**。下一步若镜像窗口获得可点击输入，再进行一次真实 picker；否则保留该外部门槛并推进其他可独立验证的 roadmap 缺口。

## 最新续接 — 2026-10-25 P9/P5 Metal System Trace 资源观测

- 沿用已安装 `com.ondeviceaikit.probe.vlm`、项目已有固定模型/manifest 和公开 `fixture.png`，在授权 iPhone 16 Pro Max / iPhone17,2 / iOS 27.0.1 上以 `--p9-auto-run` 执行一次真实 MLXVLM 图像生成；report `artifacts/verification/p9-metal-20261025/p9-run-39CEE4F5-7F1E-4645-A621-7892811267B0.json` 为 `status=success`，`runId=74E7A0E7-01DB-47A5-9E7F-B07B7C251519`，首 token `490.557542ms`、总计 `997.558208ms`、进程常驻峰值 `1,170,046,976B`，fixture/model/runtime hash 与 manifest 一致。
- 同步录制 15.747300 秒 `Metal System Trace`：`artifacts/verification/p9-metal-20261025/vlm-metal-all.trace`；TOC 显示 A18 Pro，筛出的 VLMProbe PID `23718` 有 **405** 条 GPU interval（**399 Compute**、6 CoreImage），原始导出 `all-metal-gpu-intervals.xml` / `vlmprobe-gpu-rows.xml`。这证明真实 GPU 执行活动被捕获，但 `metal-current-allocated-size` 只出现一次 80KiB 记录，GPU counter set 为 null、shader timeline disabled，导出还有 dylib-overlap timeline warning；不填写 Metal GPU peak 或 energy，不升级完整 P9/P5 通过。
- 本次只使用固定公开 fixture，没有读取私人媒体、下载/更新依赖或改签名/保护文件。下一步仍优先真实镜像点击（若窗口可输入），否则推进其他独立缺口；自然质量、能耗、最低系统、Android 生产 backend、P10 Task 9/10 和 P11 继续未完成。

## 最新续接 — 2026-10-25 Kotlin-only Android fixture revalidation / iPhone mirror foreground gap

- Demo 生产源码仍为 Kotlin/Compose。测试专用 `MultimodalFixtureProvider` 已从临时 `demo/androidApp/src/androidTest/java/.../MultimodalFixtureProvider.java` 恢复为 `demo/androidApp/src/androidTest/kotlin/io/ondeviceai/demo/MultimodalFixtureProvider.kt`；Java 版本只是隔离 test APK Kotlin runtime/classpath 的临时尝试，不能作为 Demo 语言方向。当前 Android-test-only 配置显式加入 Kotlin stdlib，并限定断言编译选项，不改生产源码语言。
- 强制离线重跑 `rtk proxy ./gradlew :demo:androidApp:testDebugUnitTest --offline --console=plain --rerun-tasks` **exit0**：XML 为 **38/38，0 failures/errors/skips**；`assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 强制重跑也 **exit0**。证据在 `artifacts/verification/multimodal-android-kotlin-fixture-20261024/{unit-rerun,build-lint-rerun}.{log,exit}`；lint 为既有 14 warnings，无 error。`git diff --check` 通过，provider 源码 inventory 仅有 `.kt`。
- 在已授权 TECNO KM9/API35 上重新安装这版 Kotlin-only app/test APK 后，直接 `am instrument` 返回 bundle `status=passed`；`busyReselection=passed-first-import-retained-second-picker-rejected`、`systemPicker=passed-open-cancel-without-media-read`、`syntheticResult=passed-real-callback-copy-host-clear-fixture-only`、`foreground=passed-no-input-no-auto-generation`、`cleanup=passed-new-owned-directories-released`。证据：`artifacts/verification/multimodal-android-kotlin-fixture-20261025/device-picker.log`，build/device exit 均为 0。实际系统文件选择仍 unverified，推理仍 `unverified-no-android-backend`。
- 仓库级 `rtk proxy ./scripts/verify-android.sh` 也 fresh exit0：725 actionable tasks（44 executed/681 up-to-date），JVM tests、lint 与 APK build 均通过。证据：`artifacts/verification/multimodal-android-kotlin-fixture-20261025/full-android-verify.log` / `.exit`；这不升级为 Android 生产推理通过。
- 授权 iPhone `<iPhone-UDID>` fresh 状态为 `connected`；`com.apple.ScreenContinuity` 已能显示真实主屏。用 `xcrun devicectl device process launch` 启动已安装 Demo 成功，但镜像窗口没有把 Demo 带到可见前台，故物理触摸与真实系统 picker 仍是 **unverified**。没有读取私人媒体、修改签名或保护文件。

Roadmap 仍 **5/12、P9 active**。下一步继续从可独立验证门槛推进；不把 Kotlin 本地门禁或 AX/注入式 picker 证据升级为真实 UI/P9 完成，不自动提交、推送、合并或发布。

## 最新续接 — 2026-10-24 iPhone 镜像重试仍超时

- 使用本机 `com.apple.ScreenContinuity` 启动 iPhone 镜像并重试“连接”；窗口先显示“正在连接 iPhone 17 Pro Max”，随后显示“已超时 iPhone 镜像连接超时”。当前没有可操作镜像窗口，真实物理触摸与系统 picker 继续 `unverified`。
- 该结果是新的外部 UI 门槛证据；没有用 AX 激活、注入 picker callback、`devicectl` connected 状态或合成 fixture 替代真实触摸/真实文件选择。未安装/修改产品 App、未读取私人媒体、未改变签名。

## 最新续接 — 2026-10-24 P10 scoped final review complete（product gates watch）

- 当前工作树仍为 `/Users/daubert/Github/ondevice-ai-kit/.worktrees/foundation` / `feat/sdk-foundation`，HEAD `9288ac5a28dd5e2e55a23f9be9d5ec8108199fc4`。全部既有未提交修改保留；未 reset/clean/stash/commit/push/merge/release。两个保护文件仍未修改或暂存，hash 不变。
- 冻结的 P10 review 范围为 `ecdfd528..bb895161`，覆盖 `apple/Sources/ODAIKitIdentity/**`、`android/identity/**`、`kmp-binding` identity bridge、identity Demo wiring 和 distribution/reporting tools；排除当前 dirty P9 diff、`demo/iosApp/OnDeviceAIDemo.xcodeproj/project.pbxproj` 与 `docs/validation/face-identity.md`。
- 独立 code-reviewer 已完成冻结范围 98 文件只读审查，architect 也完成 scoped architecture review；两者 CRITICAL/HIGH/MEDIUM/LOW 功能或安全 finding 均为 0。Android 专项 follow-up 仍因 `429 Too Many Requests` 不可用；记录在 `.superpowers/sdd/2026-10-24-p10-final-review/{review.md,architecture.md,ledger.md}`，结论为 `SCOPED_CODE_AND_ARCHITECTURE_REVIEW_COMPLETE_PRODUCT_GATES_WATCH`，不能写成全 roadmap APPROVE。
- 新鲜验证：Apple `rtk proxy swift test --package-path apple --filter ODAIKitIdentityTests` exit0，313 tests/0 failures；Android `rtk proxy ./gradlew :android:identity:testDebugUnitTest --console=plain` exit0，XML 319 tests/0 skipped/0 failures/0 errors；固定 Ruby 3.4.11 的 distribution/tool unittest exit0，14 tests OK。`git diff --check`（当前工作树）通过；范围 diff 的历史 trailing blank lines 仅为 style observation。
- 之后补跑全量门禁：`rtk proxy swift test --package-path apple` exit0（Identity313、Speech31、ModelStore13、Face9、Core5，共371，0 failures）；`rtk proxy ./scripts/verify-android.sh` exit0（725 actionable tasks，JVM tests/lint/APK passed）。这些是本地验证，不升级为生产模型、真实 UI 或全 roadmap 验收。
- 仍未完成：P10 Task 9/10 生产模型、PAD、授权数据、阈值/校准、最低系统/设备推理；以及 P9 物理触摸/真实 system picker、Android 生产 inference backend、自然质量、GPU/energy、binary archive 等。Roadmap 仍 **5/12、P9 active**。下一步继续选择有独立证据的 roadmap 缺口；不自动提交、推送、合并或发布。

## 最新续接 — 2026-10-24 物理触摸/真实系统 picker 可行性探针

- `rtk proxy xcrun devicectl list devices` 与 `xctrace list devices` 均确认授权 iPhone `<iPhone-UDID>` 当前 `connected`；未安装新 bundle、未读取私人媒体、未改变签名或源码。
- 本机 `cua_repl` 的 app inventory 能看到 `com.apple.ScreenContinuity`，但绑定 iPhone Mirroring 返回 `cgWindowNotFound`，当前没有可操作的镜像窗口。因此本轮没有物理触摸或真实系统文件选择证据；既有 AX/injected picker 报告保持 `touchAcceptance=unverified`、`systemFileSelection=unverified`。
- 该外部 UI 入口阻断已精确记录，不能用程序化 AX 激活、注入 picker callback 或 `devicectl` 连接状态代替这两项验收。Roadmap 仍 **5/12、P9 active**；fresh branch-wide review 仅有已知 XCTest QoS LOW watchlist。

## 最新续接 — 2026-10-24 dirty-source picker/UI 真机复验通过

- 产品工作树仍为 `.worktrees/foundation` / `feat/sdk-foundation`，HEAD 仍 `9288ac5a28dd5e2e55a23f9be9d5ec8108199fc4`。本轮保留所有未提交修改，未暂存、未 commit/push/merge/release；两个保护文件未改动。
- 当前真机 `<iPhone-UDID>`（iPhone16ProMax / iPhone17,2 / iOS 27.0.1）已重新连接。沿用 Team `4X996UV8LC`、固定缓存、项目-owned fixed model 和公开 `fixture.png`，当前 dirty source signed generic device build **exit0**，新证据：`artifacts/verification/multimodal-mlx-full-app-9288ac5-20261024/resume3-20261024/signed-device.xcresult`。fixture SHA256 `60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606`。
- 精确来源为 HEAD `9288ac5a28dd5e2e55a23f9be9d5ec8108199fc4` 加当前未提交 product/probe source diff SHA256 `a71a6a758e349b4d48d71bff7163380251a928fedbaeea31cfd38b879c8597f9`；报告中的 `sourceCommit` 启动参数是 HEAD 标签，不能单独代表 dirty source。
- 真机 active report：`artifacts/verification/multimodal-mlx-full-app-9288ac5-20261024/resume3-20261024/demo-active-integration.json`，`status=passed`。`bootstrap/available/pageOpened/inputAction/pickerReply/activeCancel/activeLeave/noAutoRestart/reentry/returnReentry/closed` 全部 true；cancel/leave 为 `CANCELLED`，两次 reentry 为 `SUCCEEDED`，backend `mlx.smolvlm2-500m`。本轮将 picker reply 后检查改为等待异步 host/store 状态，已验证 delayed callback gate。
- branch-review 的 MEDIUM 已修复：shared UI 仅在 `supportsVideoInput` 且实际 video store+picker 均配置时显示视频入口；Android/iOS image-only capability 回归已加。LOW factory failure 测试已改为断言阻塞文件构造的 `NSCocoaErrorDomain/fileWriteFileExists`，同时验证 image store 清理、foreign sentinel 保留与 model lease 可 remove。
- 当前 iOS bridge/store/factory suite **72/72，0 failures/skips**：`artifacts/verification/multimodal-ios-store-resume-20261024/resume6-20261024/tests.xcresult`；完整 Android Demo unit suite **38/38，0 failures/errors/skips**，`demo:shared:allTests` 也通过。修复后的 fresh branch-wide review 已完成：CRITICAL/HIGH/MEDIUM 为 0，仅保留已知 XCTest QoS priority-inversion LOW watchlist；这仍不是全产品或 P9 APPROVE。
- 报告仍明确 `touchAcceptance=unverified`、`systemFileSelection=unverified`、`osBackground=unverified`；这是 app-local AX/injected picker host/VM lifecycle 验证，不是物理触摸、真实系统文件选择或 OS background。Android 生产视频 backend、自然视频质量、Metal/GPU/energy、minimum OS、binary archive/interface、原上游 8→7、P4/P6/P7/P10/P11 和全分支最终 review 继续保留。

Roadmap 仍 **5/12、P9 active**。下一步转向尚未完成且可独立验证的 P9 门槛（物理触摸/真实系统 picker 能力若工具条件允许，或记录外部门槛后推进其他独立缺口）；不自动提交、推送、合并或发布。

## 最新续接 — 2026-10-24 KMP bridge 固定视频真机闭环通过

- 已从本节上方检查点继续，保留全部 dirty/staged/untracked 内容；产品工作树仍为 `.worktrees/foundation` / `feat/sdk-foundation`，HEAD 仍 `9288ac5a28dd5e2e55a23f9be9d5ec8108199fc4`。本轮未提交、未暂存、未 push/merge/release；两个保护文件未改动。
- 临时 KMP consumer 初次失败的根因已定位为过期 framework 产物：`IOSMultimodalBridge.swift` 需要 `MultimodalBridge*`，而旧 `ODAIKitBinding.framework` 头文件仍为 `VideoBridge*`。沿用固定缓存离线执行 `rtk proxy ./gradlew :kmp-binding:linkDebugFrameworkIosArm64 --offline --console=plain` 成功，重生成 header 已导出 `MultimodalBridge*`。原失败日志与 retry1–3 均保留在 `artifacts/verification/multimodal-kmp-video-device-20261024/`。
- artifact-only `KMPVideoConsumer` 在 retry5 signed generic iOS build **exit0**，使用既有 Team `4X996UV8LC`、当前 `IOSMultimodalBridge.swift`/`IOSMultimodalInput.swift`、重生成 binding framework 和固定 Swift package cache；证据：`retry5-20261024/generic-build.exit`、`generic.xcresult`。临时工程不改变 tracked 产品源码。
- 真机 `<iPhone-UDID>`（iPhone17,2 / iOS 27.0.1 build 24A446）已安装并运行该 consumer。仅复制项目-owned 固定模型（从先前 native consumer 的 appDataContainer）及公开 fixture `apple-mlx/Tests/ODAIKitAppleMLXVLMTests/Fixtures/video-29fps.mp4`，没有读取私人媒体。启动带 `--sdk-commit=9288ac5a28dd5e2e55a23f9be9d5ec8108199fc4`。
- KMP bridge raw report：`artifacts/verification/multimodal-kmp-video-device-20261024/sdk-kmp-mlxvlm-video.json`；严格副本 `strict-kmp-summary.json`。`rtk proxy python3 tools/mlxvlm_video_report.py ...` **exit0**。`state=success`，success / 首次 update cancel+drain / same-PID reentry 均为预期状态；8 帧实际时间 `[0,3584,7168,10752,14848,18432,22016,25600]/14848`，`snapshotRemoved=true`；close 后 image/video 拒绝；取消前后 lease 保护、close 后 lease 释放、source unchanged、no owned snapshots 均 true。`runId=13B17E44-6145-4568-8C62-6A4F670CA64E`，`pid=22856`，backend `mlx.smolvlm2-500m`。
- 该证据只证明当前 KMP `IOSMultimodalBridge` 到固定 Apple MLXVLM runtime/native lifecycle；**不**扩展为 Demo 真实 picker/物理触摸、Android 真后端、自然视频质量、Metal/GPU/能耗、minOS、binary archive 或全分支/P9 完成。Roadmap 仍 **5/12、P9 active**，独立 review 与其余历史门槛保留。详细记录已追加到 `docs/validation/multimodal-video-demo.md` 与 `.superpowers/sdd/2026-10-24-multimodal-video-demo/resume-20261024.md`。

## 最新续接 — 2026-10-24 增强构建通过／原真机不可用

- 工作树仍为 `.worktrees/foundation` / `feat/sdk-foundation`，HEAD `9288ac5a28dd5e2e55a23f9be9d5ec8108199fc4`。原全部未提交修改保留；本轮新增 iOS store 父目录兼容修复、store/factory 回归与 Android IO→UI 取消窗口测试，也未提交/暂存/推送。两个原保护文件 SHA256 保持下方原表值；不修改/暂存它们。
- 当前最小 `sandbox-exec` 探针 exit0。使用原独立增强工程、固定复制缓存、不解析/更新依赖重新构建完整 MLX App **exit0 / BUILD SUCCEEDED**，加入 store 修复后再次 exit0。未关闭宏/接口校验、未改上游/签名或下载。原沙箱错误精确外部策略原因未知，本环境未复现；原 exit65 保留。logs/xcresults：`artifacts/verification/multimodal-mlx-full-app-9288ac5-20261024/resume-20261024/`。
- 12/12 固定 checkout HEAD/clean tracked 状态匹配，package/生成工程 lock hash 均仍 e67e9859…。源码是 HEAD+dirty，生成 validation label 仍是旧 HEAD，**后续设备报告必须重新冻结/生成精确来源，不可沿用该 label 假称精确源码**。snapshot/设备/格式证据在 `artifacts/verification/multimodal-resume-final-20261024/`。
- iOS 只递归创建父目录，最终目录仍独占创建，拒绝既有外来目录。真实 RED70项/2缺父目录失败→GREEN70/70；补 factory video-store 失败后 image 目录与 model lease 清理。随后补实际 factory→host/VM→独立 video provider→typed native backend 的合成请求/输出/关闭测试；测试初版编译错误和URL目录末尾slash断言失败留档，生产路由未修改。最终 fresh 完整 **71/71，无失败/跳过，exit0**，xcresult summary确认；`multimodal-ios-store-resume-20261024/verified-final.{log,exit,xcresult}`。前一绿色测试卡在测试后simctl诊断，采样与日志保留；核对PID后仅终止本轮diagnose子进程，随后用文档支持的 `-collect-test-diagnostics never` 重跑，测试/编译校验保持启用；旧semaphore测试QoS warnings保留。
- Binding ABI 新独立工程 **23/23**；Android Demo 最终 **37/37**，另APK/testAPK/lint 离线通过；Android新测试精确暂停成功IO copy后UI发布前，再clear回收，并以真实PickerCopy+host重建取消caller时NonCancellable join，证明source存活到worker/join退出后再清理；不冒充Activity中途复制实操。Android重选修复独立review APPROVE（LOW实际Activity忙状态/中途复制覆盖缺口）；iOS父目录/factory及Android取消测试切片独立review APPROVE，无finding；不扩大为全分支最终APPROVE。
- 原授权 iPhone16ProMax `<iPhone-UDID>` fresh查询 **unavailable**；另一台physical设备无DDI，未使用。没有安装/修改签名/读私人媒体/执行权重推理。当前阻断转为设备可用性，**KMP固定视频权重success/首update cancel/drain/同PID reentry/close/lease与当前iOS真实picker/UI仍未验**，不复用旧native证据。

下一：先核对本节与validation/dirty状态；原设备恢复后再冻结dirty源码与精确hash/生成validation工程，沿用已授权既有签名/固定缓存/公开合成profile视频，建立独立KMP视频权重严格validator证据；同一真机串行操作。不自动推送/merge/release。仍5/12/P9active，旧minOS/质量/资源/binary/短样本/Androidbackend/P4/P6/P7/P10/P11及全分支最终review缺口保留。

## 最新续接 — 2026-10-24 14:20 真机输入通过／增强编译阻断

### 工作树、提交与远端

- 实际产品工作树：`/Users/daubert/Github/ondevice-ai-kit/.worktrees/foundation`，分支 `feat/sdk-foundation`。会话目录 `/Users/daubert/WorkBuddy/Worktrees/ondevice-ai-kit/main-263aa2ba` 不是产品源码工作树，不要在main上重做或迁移源码。
- HEAD：`9288ac5a28dd5e2e55a23f9be9d5ec8108199fc4`，提交说明 `feat(demo): add typed video inputs and cancellable async staging`，26文件。用户明确授权后已普通推送GitHub origin和GitLab jihu，并独立ls-remote核对双方完整SHA一致。未强推、未合main、未建PR/MR。本次交接没有重新查询远端；上述是13:39左右的已验证结果。
- HEAD之后仍有未提交修复：`demo/androidApp/src/main/kotlin/io/ondeviceai/demo/{MultimodalHost.kt,MainActivity.kt}`、`demo/androidApp/src/test/kotlin/io/ondeviceai/demo/MultimodalHostTest.kt`、`demo/androidApp/src/androidTest/kotlin/io/ondeviceai/demo/MultimodalPickerInstrumentation.kt`、`docs/validation/multimodal-video-demo.md`，以及本交接文档。不要称这些已同步远端。
- 两个原保护文件保持未提交：tracked dirty `demo/iosApp/OnDeviceAIDemo.xcodeproj/project.pbxproj` 和 untracked `docs/validation/face-identity.md`；禁止修改/暂存，原hash见下方保护表。不要reset/clean/stash、删除构建日志或覆盖现有修改。

### 已完成及证据

1. `9288ac5`包含shared typed video/UI、双端独立store/picker、异步有界私有copy、取消/晚到回收。Android生产Activity使用lifecycleScope/IO，临时picker source通过NonCancellable join保活；iOS detached worker复制、MainActor registry、安全域与inflight目录到worker退出才释放。保留同步兼容API，不可声称所有调用均异步。
2. 该提交前已实际验证Android34/34、APK/lint；主会话完整iOS Demo ABI67/67。日志 `artifacts/verification/multimodal-ios-async-20261024/author-full.log`。不要重复实现已完成链路。
3. 后续独立只读review发现major Android竞争：导入A时再选B，host先取消A，Activity又因busy拒B。已先编译RED再新增`canSelectInput`前置admission、接入Activity busy状态，拒绝重选时保留已接受导入；新确定性队列测试通过。修复真实picker runner三处旧文案“尚未选择图片”→“尚未选择输入”。当前Demo **35/35**、APK/testAPK/lint离线通过，diff-check通过。
4. 连接TECNO KM9（serial `<Android-serial>`，Android15/API35）运行真实`MultimodalPickerInstrumentation`：bundle **status=passed**，系统picker真实打开/取消、公开合成provider经真实Activity回调/异步copy/host/clear、前后台不自动生成、自有目录清理通过。**实际系统选择文件未验，合成字节不是视频，Android无推理backend；重选中途暂停竞争仅单测覆盖。** 本轮未读取私人媒体。
5. Android新证据均在 `artifacts/verification/multimodal-video-demo-red-20261024/`：`busy-selection-red.log`、`busy-selection-green-build.log`、`async-device-picker.log`。读bundle字段判断通过，不能只看adb退出码。

### 当前阻断与未收口评审

- 增强MLX完整App已生成新独立工程，使用既有复制缓存与固定lock，unsigned generic iOS构建 **exit65**。原始日志：`artifacts/verification/multimodal-mlx-full-app-9288ac5-20261024/unsigned-generic-ios-build.log`；同目录有generate/cache logs。
- 根错误：`sandbox-exec: sandbox_apply: Operation not permitted`，`SwiftMacros.TaskLocalMacro`无法从`swift-plugin-server`载入，继发MLXRandom错误。**不是通过，不是确认产品API错误。** 未改上游、下载、修改签名或跳过宏校验；普通manifest sandbox参数不能被假定已解决compiler宏插件沙箱。
- 独立review未APPROVE：major Android项已修但尚未复审；minor iOS store重写丢失自动创建父目录兼容行为仍待TDD处理；factory视频真实typed生成/provider链路、视频store创建失败后的图片目录/model lease清理测试仍待加强。Android临时source中途取消/join和IO完成→UI发布取消窗口还需精确覆盖。
- 当前增强App没构建成功，**KMP→固定视频权重真机success/首update cancel/drain/同PID reentry/close/lease清理仍未执行**，不复用旧native视频或旧单图证据冒充。

### 新对话直接继续的顺序

1. 先核对本文件、validation/multimodal-video-demo.md、git状态与两个保护hash；保留全部未提交修复。
2. 优先诊断宏插件sandbox执行条件。若确需外层沙箱权限，按环境权限流程申请；不得关闭代码/宏校验、改固定依赖或偷偷下载来绕过。没有条件变化不机械重跑相同失败命令。
3. 可并行TDD补iOS父目录兼容及factory视频生成/失败清理测试；独立review复核Android修复。用户已明确允许并行，覆盖下方历史solo限制；同文件不并发写，同一真机串行操作。
4. 增强编译通过后，核对iPhone连接，仅操作项目App，沿用既有签名与公开合成固定profile视频，独立建立KMP视频权重运行与严格validator证据，再验证当前iOS真实picker/UI。旧iPhone UDID `<iPhone-UDID>`，恢复时重新查询，不假定仍连接。
5. 每个验证包准确记录失败/未验项。后续提交精确排除保护文件；这次已有同步授权对应9288ac5，不自动扩大为以后每轮push/merge/release授权。

仍 **5/12，P9 active**；iOS15/API26、自然质量、Metal/GPU/energy、Android生产backend、短样本拒绝、原上游8→7、binary archive exit65、P4/P6/P7/P10/P11及独立最终评审缺口继续保留。

## 最新续接 — 2026-10-24 双端异步输入提交检查点

双端生产host已接异步最终复制及取消/晚到回收；Android Activity临时source经join保活，iOS detached worker/MainActor registry持有inflight目录与安全域直到worker退出。Android34/34、生产接线APK/lint通过；主会话完整iOS Demo ABI67/67。详见validation/multimodal-video-demo.md新增节。仍缺当前版本真机picker/UI、增强MLX整App、KMP视频权重闭环及独立review，5/12、P9active。用户本次明确授权将当前变更提交到feat/sdk-foundation并推送GitHub/GitLab；两个保护文件排除。提交与远端同步精确结果以后续git证据为准，下方未提交描述为历史状态。

## 最新续接 — 2026-10-24 可取消 store 前置检查点

后续已先解决Android store整段复制持锁问题：in-flight目录所有权、锁外逐块copy/checkpoint、close后拒绝publish、部分副本清理。2新测试真实缺checkpoint API编译RED→GREEN，增强旧lease+blocked worker断言后offline Demo31/31、assembleDebug/lintDebug成功。验证与原始GREEN log见multimodal-video-demo.md新增节。尚未改双端host异步调用；下一接Android staging任务与picker临时source存活/join，再Apple worker/MainActor分离。当前HEAD仍8d2cd9c，全部未提交、无新push/设备操作；保护hash不变。不是P9收口，仍5/12、P9active。

## 最新续接 — 2026-10-24 视频 Demo 输入检查点

用户已确认独立双store/共享状态机方案。新增shared媒体类型与视频分派、可选video actions/UI、双端host/picker独立store、32MiB有界私有复制及Apple factory videoInputs接线。当前均未提交/未push。shared64/64、Android Demo29/29及assemble/lint通过；Swift Demo ABI59/59、独立基础Demo simulator build通过。tools标准命令受清单sandbox阻断，进程内仅对dump-package加--disable-sandbox后133/133；完整说明见[本轮验证](validation/multimodal-video-demo.md)。旧图片测试异步序列断言[2]偶发失败，改验合法单调序列及terminal后不更新，产品bridge未改。两保护hash/index不变。

不称本包完全收口：host最终复制仍UI同步有界，异步stager/显式导入中取消待补；增强MLX整App、Binding-only ABI、全Android modules、本轮UI实操及KMP到固定权重设备视频未验。下一先作者差异复核/独立review与增强编译及stager，再另包设备链路；5/12、P9active、原质量/资源/minOS/binary/picker等缺口保留。

## 最新续接 — 2026-10-24 KMP 视频 bridge

优先于下方历史下一步：设计e5a91a7、common447a7d5、Android0a84af6、Apple/generator2a1757d。optionalMultimodalVideoBridge/typedvideoDTO，原imageprotocol不加requirement；两端单独可选videoInputs默认nil/null，不借图片provider解析，image/video共用既有任务/nativeclient/drain/release/close。common24/24；Android模块48/48、完整725tasks/465JUnit；SwiftBinding23/23、Demo55/55、旧VideoABI6/6，xcresult0fail/skip；tools133/133、swiftformatstrict/Pythoncompile/diffcheck通过。中间common/JUnit/generatorfixture失败及所有logs留档，详见[视频bridge验证](validation/multimodal-video-bridge.md)、同名spec/plan/ledger。作者自审，独立reviewpending。

下一独立包为sharedtypedvideo input/state/UI与两端hoststager设计实施，再单独KMP→固定模型设备视频链路。当前Demohost/UI仍image-only；本包只验证注入真实client生命周期，不是设备视频推理。原Apple固定native证据及所有Android真backend/自然质量/资源/minOS/binary/短样本/原上游丢帧/真实picker/旧UI/P6/独立review缺口保留，仍5/12/P9active。HEAD以git为准，原2保护文件hash/state/index保持。

本次用户授权的原检查点05c67d2在jihu网络恢复后已非强推同步origin与jihu，独立freshls-remote双方均05c67d2a85c050d70ad51e255d0ed8deabc1d833；此前SSL/reset失败保留，未改网络/凭据/签名，无mainmerge/force/publish。新Android/KMP包仅本地提交；不自动继续push。同步JSON/成功push与失败logs在KMP视频bridge及前两个ledger，详见validation。下方“同步pending”为当时历史状态。

## 最新续接 — 2026-10-24 Android 视频契约对等

优先于下方全部历史下一步：设计333e414、typed API/client bd52496、callback测试修正f2f7bd1、consumer2164ebd。本包35/35（video13+旧client12+bridge10），完整Android725tasks/452JUnit、tools133/133；模块lint/KMPAndroidcompile通过，新localMavenAAR/sourceJar/javap及fresh独立Kotlin2.4 consumer45tasks assemble/lint通过。callback异常引用assertSame失败后误commit不是通过证据，f2f7bd1修正语义后全GREEN；独立consumer起初2.2metadata失败保留，对齐既有2.4后通过，未关闭检查/下载。Android仍无真实后端，未读媒体/运行设备/改KMPbridge或Demo。详见[Android视频契约验证](validation/android-multimodal-video-contract.md)和同名spec/plan/ledger。

下一独立包为KMPtypedvideo/opaqueinput桥接设计实施，之后再Demo/picker；不要重复已完成Swift/Android native包。原Apple设备链路与所有质量/资源/binary/minOS/短样本/旧UI/P6/独立reviewpending缺口不变，仍5/12/P9active。HEAD以git为准；仅本地提交、2保护文件hash/state/index未变。

用户本次“同步代码，然后继续”授权冻结同步05c67d2。sync-remotes两次因jihu SSL_ERROR_SYSCALL/reset预检失败，独立重查/HTTP1.1也失败；无任何push。GitHubfresh仍eb1287d，jihu fresh未知，双远端同步pending。初始runtimeledger remote-sync-20261024日志与Androidledger sync-jihu-recheck.log保留，未改网络/签名/凭据。继续实现后的新Android包默认本地，不自动追push。

## 最新续接 — 可搬移 source 分发与 binary 门槛

- 已从下述b988742基线续接；设计/计划cf342a1、source generator实现b8acaa3，最后精确HEAD以git为准。新增[分发验证](validation/apple-mlxvlm-distribution.md)和对应spec/plan/本地ledger；**不重做本包**。
- `tools/generate_mlxvlm_consumer.py --portable-source <fresh-output>` 复制122个tracked package文件、固定lock和资源，以相对package引用生成独立consumer。已搬移到含空格目录，并在既有12个精确匹配/无tracked修改的依赖缓存下完成unsigned generic iOS build；snapshot/project lock及built canonical JSON完全一致，六个backend源文件确实从搬移目录编译。工具126/126，最终定向6/6、py_compile/diff-check通过。
- source raw/summary `artifacts/verification/apple-mlxvlm-source-distribution-20261023/`；完整logs与progress/final-review在`.superpowers/sdd/2026-10-23-apple-mlxvlm-source-distribution/`。inventorySHA256 `479bdeb51f178c2b791a9d9228217b21eda90a3c3859aaa543c572ac6bbc2033`；lock仍e67e9859…。
- 增强package真实Release archive（OnDeviceAIKitAppleMLX scheme、library evolution、固定缓存）**exit65 / ARCHIVE FAILED**：本机Xcode27.1/Swift6.4验证固定swift-collections1.7.3的OrderedCollections.swiftinterface失败，`'Element' is not imported through module 'OrderedCollections'`。binary分发未通过；未改上游/关闭校验/下载，不反复重跑同一门槛。source构建不替代binary、设备推理或最低系统验收。
- 全程当前会话单人、作者自审，独立reviewpending；只本地提交，无远端同步。原有两个保护文件hash/state/index不变。旧UI/模型/质量/资源/最低系统门槛与roadmap5/12/P9active保留。
- 下一独立包先核对**既有固定模型/runtime是否能支持有界短视频输入**，先读相关P8/P9/probe与backend契约，仅本地源码/缓存可行性和设计；若不满足依赖或设备条件，准确记录门槛后选择其他独立项。不与binary路线混做，不下载新模型/依赖，不预先承诺短视频可用。

## 基线与远端

- 工作树 `/Users/daubert/Github/ondevice-ai-kit/.worktrees/foundation`，分支 `feat/sdk-foundation`。
- 本轮实现/验证基线 `9b6c8880244df10550edcee20b33ac18f1199037`；用户明确授权后，GitHub origin 与 GitLab jihu 的功能分支已通过仓库 sync-remotes 脚本快进同步到该提交（此前两端626b27b，本地领先49，无分叉）。本恢复文档随后本地提交并再次同步；最终精确HEAD以git/两端新鲜ls-remote核对为准。没有合入main、强推或发布。
- 下一会话恢复默认**仅本地提交**；本次同步授权不作为以后自动push/merge/publish授权。
- 工作树只保留以下原始脏状态；暂存区应为空。恢复前复核HEAD/branch/worktree/index与保护哈希；有并发变化只调查，不恢复覆盖。

| 文件 | 状态 | SHA256 |
| --- | --- | --- |
| demo/iosApp/OnDeviceAIDemo.xcodeproj/project.pbxproj | tracked dirty，禁止修改/暂存 | b35bd984f784d4b13c0724f8c595d28ed96ddbe418e049547b762309fe9fd8f5 |
| docs/validation/face-identity.md | untracked，禁止修改/暂存 | 6f130bd94debd5e8bffaba7b0db560553f7a367818d9cf37b72468e42bb70822 |

## 已完成，不重做

SDK Foundation、独立apple-mlx后端/真实SDK固定单图生命周期与benchmark、KMP双平台bridge、sharedVM/UI/opt-in、两端input lease/host/picker接线、Apple可选ModelStore/MLX host均已本地实现。默认/Android无生产多模态后端时明确unavailable。

- 共享App-local AX成功/取消/活动离页/同PID重入：真实device source2915725、PID21348、11 gates；见 `docs/validation/multimodal-demo-active-ui.md`。
- 真实OS前后台：source `d50eff18ed53db61bc46f050f411ab076d118ede`、iPhone16ProMax/iOS27.0.1、PID21398/runId16D64305-AD03-404E-ACB1-A509F9A79DA3；项目-owned inert companion触发真实UIKit四通知，ready update0、willResignActive时RUNNING/update23、后台CANCELLED无output/failure、同PID恢复300ms不自动重启、显式重入成功与close；14 gates严格通过。
- raw `artifacts/verification/multimodal-background-20261023/demo-background-integration.json`，SHA256 `bc5ec5fbcb17f93ff3a3f034a8bab27a82fe0f48646236dd0a944bc7755b96e5`；详细见 `docs/validation/multimodal-demo-os-background.md`。
- 最新工具123/123、Apple Demo42/42、strict新Swift0、default simulator/enhanced signed device/companion构建成功；之后仅交接文档变化，没有新App行为。CLI修复39660fc支持direct/module。
- 当前设备通过devicectl可用；mobile MCP只执行了foreground检查，报agent未安装。未执行元素读取、未安装agent/profile。agent缺失继续限制触摸工具，**不阻断已通过的OS前后台证据**。

## 最新续接 — 固定 MLXVLM 短视频可行性

**最新native设备完成检查点（优先于下方全部历史下一步）：** Task2typedclient`c9ea298`、共享session/runtime`eebc670`、显式consumer/严格validator`30e5e8e`完成。首次设备PID22015 per-process-limit无report；阶段定位到ModelStore.acquire前，独立64MiB真实RED→GREEN定位Foundation逐块read临时对象保留，`5e39cb8`在read+SHA256.update内逐块autoreleasepool，不跳过校验、不动模型/runtime/MLXglobals/帧数。完整Apple470/470、增强host41/41无skip、tools133/133，实际签名build成功。真实iPhone16ProMax/iOS27.0.1源`5e39cb8b06a1dc3862e0f0a034ec6397951eac48`/PID22072/runId2B747E73-3D02-4585-8F13-969685C84892，固定29FPS8frame实际权重生成、首update取消+drain、同PID重入、close/lease/remove/清理均通过，严格validatorexit0。clip2s与span25600/14848分列，14modelhash/12checkout/lock均核对；raw与失败Jetsam/阶段诊断全保留，见[短视频验证](validation/apple-mlxvlm-short-video.md)顶部和runtime/hash-memory两份ledger。原Task2/3本轮固定native范围完成；Task1短样本仍拒绝，不泛化<=5s。下一Androidtypedvideo契约/client对等独立设计实施，再另包KMP/Demo；不要重做adapter/profile/client/runtime。原上游8→7失败、binary archiveexit65、自然质量/Metal/energy/Android真backend/minOS/旧UI/P6实录/独立reviewpending缺口不变，5/12/P9active。仅本地提交，无新push授权。HEAD以git为准；2保护文件原hash/state/index不变。

**最新runtime库检查点（优先于下方）：** MLXVLMBackend已conform video协议，image/video共用session/lease，视频直接adapter.Prepared.input，producerjoin后snapshot清理。session6项、实际29FPSmetadata1项均RED→GREEN，全增强host41/41无skip、tools126/126、新独立unsigned generic iOS build通过。原Task3仍缺consumer/严格validator及设备视频权重success/cancel/reentry/close，不以本包host/build替代。下一按`2026-10-23-apple-mlxvlm-short-video-runtime.md` Task2继续；日志/checks/作者自审在同名ledger目录。原Task1短样本仍拒绝，独立reviewpending、5/12/P9active、所有旧门槛不变，仅本地提交。

**最新Task2检查点（优先于下方历史状态）：** 纯Swift `MultimodalVideoRequest`、可选video backend协议与client overload完成，image/video共用admission、update过滤、取消/drain及close/query。API RED后Multimodal22/22、完整Apple469/469、增强host34/34无skip、tools126/126、Swift6严格typecheck和新独立unsigned generic iOS compile probe通过。当前MLXVLMBackend仍image-only，Task3 runtime尚未开始；下一先细化runtime设计/验证，直接用adapter.Prepared.input，禁止原.frames路线。Task1短于8实际样本仍拒绝；上游失败、binary archive exit65与旧验收缺口保留。Task2日志/checks/作者自审在原short-video ledger目录，独立reviewpending、5/12/P9active、仅本地提交，不沿用旧push授权。详见[短视频验证](validation/apple-mlxvlm-short-video.md)顶部。

**最新profile/设备检查点（优先于下方）：** `81ea4df`新增真实PQ/offset/2frame/reference-dref负向profile4/4；原代码baselineGREEN、HDRguard临时mutation真实RED并字节精确恢复，完整host34/34无skip、tools126/126。Package直接真机测试exit70（tool-hosted不支持device），改本项目现有bundleID/team/wildcard profile的空App testhost后，iPhone16ProMax/iOS27.0.1实际snapshot/profile12/12及adapter6/6执行通过；源码/fixtures16hash匹配。后者ByteTokenizer，仅输入准备，不是视频模型/真实tokenizer/质量/资源验收。中间host链接exit65/约束exit74保留；修正为原MLX exactVersion0.32.3后lock不变，无下载/签名资产变更。raw/summary/argv在`.superpowers/sdd/2026-10-23-apple-mlxvlm-video-profile/`和同名artifacts；详见[短视频验证](validation/apple-mlxvlm-short-video.md)最新节。短于8实际样本当前明确拒绝，尚未提供支持；原Task1不泛化任意<=5s。下一可独立推进原Task2纯Swift typed request/client生命周期，仍无公共API/runtime实现或视频权重推理；原Task3需采用新adapter而非原`.frames`。独立reviewpending、5/12/P9active、只本地提交。

**新增最新检查点（优先于下方历史状态）：** 内部项目侧`MLXVLMVideoInputAdapter`（`5be1b57`）直接逐帧使用固定视频图像变换与模板装配，不再调用上游二次帧选择；29FPS实际8帧的pixels/顺序/原始CMTime全部保留，clip2s和sample span约1.724138s分列。`c9ef8ce`真实固定tokenizer2/2无skip，24FPS完整tokens/pixels/mask与上游一致；29FPS为8帧/512imageIDs，上游仍7帧/448imageIDs。全增强host30/30、tools126/126、fresh独立unsigned generic iOS编译通过；14modelassets和12checkout/lock均核对匹配。设计/计划为`2026-10-23-apple-mlxvlm-video-adapter*`；新ledger与rawlogs保留于同名`.superpowers/sdd/`目录。**没有公共video API、runtime接线或新视频权重/设备推理；原Task1 profile/iOSdecode缺口仍在，原Task2/3未开始。** 下一先补snapshot profile和iOS decode/cancel，再独立评估runtime集成门槛。单人作者自审，独立reviewpending；仅本地提交，没有新远端写入。详见[短视频验证](validation/apple-mlxvlm-short-video.md)顶部，不按下方历史“先设计适配”重复执行已完成包。

**历史eb1287d检查点（已由上方适配包续接）**：52fc766之后已实施内部`MLXVLMVideoSnapshot`和真实pinned processor测试；增强host22/22、tools126/126、独立consumer unsigned generic iOS编译通过。24FPS/2s fixture8帧保留，真实`SmolVLMProcessor.prepare` pixels为`[8,512,512,3]`；29FPS/2s原8帧count/timestamps门槛实际失败（上游二次采样变7帧，原index6丢失）。原失败log与断言副本保留；默认characterization测试断言固定版本已知7帧行为，不把GREEN称为8帧集成通过。**不要直接执行原Task2/3**。下一独立设计先解决真实帧/时间语义适配，不能改写timestamps或修改上游来绕过；原生snapshot额外profile测试和iOS实际decode/cancel仍待补。详见[短视频验证](validation/apple-mlxvlm-short-video.md)顶部。

- 已完成源码/缓存核对并新增[短视频可行性](validation/apple-mlxvlm-short-video.md)、[设计](superpowers/specs/2026-10-23-apple-mlxvlm-short-video-design.md)和[计划](superpowers/plans/2026-10-23-apple-mlxvlm-short-video.md)。固定14个模型文件 hash/size 全匹配，12个 lock checkout HEAD/clean 全匹配；摘要 `artifacts/verification/apple-mlxvlm-short-video-20261023/preflight.json`，SHA256 `135c87c076307df7fbf5c1e02bfb75016bef0cb7b0e417efa7596b0362343186`。
- 固定 runtime 的 `SmolVLMProcessor` 有单视频分支；配置 `fps=1/max_frames=64/max_image_size=512`，实际默认FPS会按时长变化。`UserInput.Video` 支持 `.url/.avAsset/.frames`，但项目公共请求和 backend 仍只支持单图。URL sampling包含duration端点并跳过失败帧；`.frames`重新计算sample span并二次采样，必须先做实际characterization。
- 下一 Task1 为有界 H.264 SDR 本地 snapshot、AVFoundation 解码取消/join、帧身份/时间戳及 pinned processor characterization。只有通过后才执行 Task2 typed video contract、Task3 Apple runtime/consumer/device。若失败保留精确诊断，不改上游或伪造timestamps绕过。
- 初次52fc766仅文档/preflight；后续内部snapshot/host/build结果见本节最新段，无新视频设备推理或设备写入。仍5/12、P9active。独立只读代理因429未启动，后续遵守当前会话单人执行。两个保护文件hash/state/index不变；无远端写入。

## 下一独立包建议

原建议的可选Apple MLXVLM分发包已按顶部最新续接执行，不再从头探测。source/binary结果分别见[分发验证](validation/apple-mlxvlm-distribution.md)；后续入口以顶部为准，binary路线必须有工具链/依赖条件变化或独立策略设计才重新启动。

物理触摸/命中、真实system picker选文件、自然图片质量、可靠Metal/GPU/能耗/最低系统、短视频、Android真后端和独立review各自仍待验；不以AX/合成fixture/processRSS替代。短视频或Android新后端不与分发包混做。作者自审不称独立APPROVE。

## 执行约束与必读

先读生效AGENTS.md（无磁盘文件则会话注入版）和 `/Users/daubert/.codex/RTK.md`，再读本文件、`docs/HANDOFF-2026-10-20-speech-demo-bridge.md`顶部最新检查点、`docs/ROADMAP.md`、本包validation/spec/plan及`.superpowers/sdd/2026-10-23-multimodal-demo-os-background/{progress.md,final-review.md}`。保留全部本地`.superpowers/`/artifacts/日志；不clean/reset/stash，不覆盖用户改动。

单人当前会话执行，不派子代理；按适用技能设计/逐Task计划、行为修改RED→GREEN、范围作者自审、精确暂存与本地提交；清晰可逆下一步不例行询问。所有shell命令以rtk开头，普通命令rtk proxy；Ruby测试显式RUBY=/Users/daubert/.rvm/rubies/ruby-3.4.11/bin/ruby。只既有固定模型/依赖缓存/公开合成fixture，不下载、录音、读取私人媒体或变更签名资产。允许使用已授权本项目真机和既有签名，先核对连接，只操作本项目App。

保留旧Video UI runner初始化超时、P6实录、Android ASR MISSING_ASSETS、iOS15/API26最低系统和独立review缺口；全roadmap仍5/12完成、7阶段未收口，P9active。只因真实破坏性操作/并发冲突/必要权限缺失或无安全恢复路径停下。
