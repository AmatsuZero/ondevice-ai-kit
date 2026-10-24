# Next session — foundation continuation

2026-10-23。建议在新会话开始下一独立包，本文件是恢复入口，不是整个项目完成结论。 最新状态见下方“固定 MLXVLM 短视频可行性”的native设备完成检查点；上面的分发段保留历史。

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
