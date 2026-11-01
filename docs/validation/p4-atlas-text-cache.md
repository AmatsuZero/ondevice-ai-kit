# P4 AtlasTextOp 分配与线程缓存调查

## 2026-11-01 正常 Skiko iOS publication 已进入独立 consumer

对固定 Skiko `9a5b398...` 的 metadata 编译做仅 AWT flag 不同的对照：关闭 AWT 时 `compileCommonMainKotlinMetadata --rerun-tasks` 报 `@OptionalExpectation`；开启其默认 AWT 目标后同任务 6/6 executed、**BUILD SUCCESSFUL**。成功时 `inputManifest` 仍为 `native_targets=`，不能据此独立解释失败。保留 Skiko/Skia tracked 源码不变，以固定 TLS owner 候选 Skia 输出运行 Skiko 原生 iOS arm64、iOS simulator arm64 与 Kotlin Multiplatform 根模块三个 BuildRepo publication tasks；**BUILD SUCCESSFUL**，没有跳过 metadata。正常发布双 Klib 与前述研究组装仓库对应 Klib 的 SHA-256 完全相同：`340d6912df2f57e1291cd5d8099da414e834ca2fb10a940fd8b5dc994bae6ae6` 和 `5d09908f2987a763edc3fe07d752a26baf52dc5f69c6889fe2f093319c7f063c`。

全新 committed `568aa6d...` source archive consumer 的 762 个未改文件均与提交一致，只在 `settings.gradle.kts` 添加该**正常 BuildRepo**。iOS arm64 依赖图解析 `skiko:0.144.6 -> skiko-iosarm64:0.144.6`，两份 static framework 强制重链成功（14/14 tasks executed），均含 `gpu.AtlasTextOp.o`、Metal layer、`CacheOwner` 析构、TLS init 和系统 `__tlv_atexit` 引用。独立签名 iPhoneOS App 最终 debug dylib UUID=`EDC85B90-54E2-3A1E-BD2F-67331805410D`，真机安装/启动 PID=`35239` 后终止；simulator 最终 App unsigned 构建成功，UUID=`7053A680-5A3E-3B54-ACA4-DADB462705A9`。证据目录为 ignored `artifacts/verification/f02-normal-publication-consumer-20261101/normal-publication-summary.json`，SHA-256=`a362a94d5ccefc08eb6dd449e66466442fb49911b44fc78d7baa4cc6cb738bc3`。

这是隔离 iOS+root publication 和加载 smoke，不是完整 Skiko 全平台分发；root metadata 的 AWT artifact 未在此限定仓库发布。前一十轮候选 App 使用相同 Klib 字节，但最终 App 来源不同，且无逐地址线程退出配对，故不据此宣布 P4 修复或 P11 验收。生产依赖未改，**F02/P4/P11 仍 open/not-ready**。

## 2026-11-01 无 recorder 的 TLS owner 候选真机十轮

固定 Skia/Skiko TLS owner 候选经独立 source-free consumer 链接后，使用已有 Apple Development 身份签名。独立 bundle `io.ondeviceai.f02candidate` 在 iPhone 16 Pro Max / iOS 27.0.1 真机安装、启动并终止（debug dylib UUID=`4E2CD327-B613-3D66-B145-F5140942231F`、PID=`35155`）。随后在 ignored 研究项目里移除全部旧 `skia_atlas_text_op_*` recorder C ABI，保留十轮相机页面驱动，使用相同候选 framework 重新构建签名 App（debug dylib UUID=`919386F5-8B81-321D-9901-2F758CAC404D`、SHA-256=`c5cab192afdbfb1a5b75b9d7ee29f204b63d31802573ce0a836d812b37fb736d`）。生产 Demo 源码、依赖与锁文件未改。

真机 PID=`35178`、runID=`7CC03135-326D-4301-98B5-9F56D6748838` 的 `Leaks` 模板 trace 为 **851.208258 秒**；TOC 的 PID/安装路径与 `devicectl` 回执一致。十轮 30 秒相机活动成功结果增量为 `648/637/668/676/664/647/638/642/638/640`，86 个报告样本无 `failed`。十次页面退出 30 秒时，owner/Compose/Metal 弱引用均为 0，但各有 1 个 bridge 弱引用仍 live；第十轮之后 31/35/60 秒 idle 全部归零，`allObservedPageObjectsReleased=true`。十轮 30 秒 page-absent RSS 从 `144,916,480 B` 到 `153,763,840 B`（+`8,847,360 B`）；该差额没有按分配来源归因。

完整导出的 Allocations List 有 **105,170** 个分配行，最终 `live=true` 且大小为 128 B、栈含 `AtlasTextOp::Make` 的唯一地址有 **6 个**；Leaks 明细 **0 行**。本次实验不保留缓存原始指针，但也没有旧 recorder 提供的析构、`delete-cached`、同 generation 线程退出事件，因此无法判定这 6 个地址在持有线程退出后是否仍 live。它与旧研究 App 的 52 个逐地址确认残留不是严格同口径比较；不能宣称修复率、零泄漏或资源稳定。原始 trace、Allocations/Leaks XML、device 回执、报告和解析脚本均在 ignored `artifacts/verification/f02-candidate-trace-20261101/`，`summary.json` SHA-256=`2df5cadd075af557fde9d7d38980a93d29cbeffa083cd7743527160c9d5c3a85`。研究 PID 已终止，普通无研究参数 Demo 恢复启动 PID=`35196`。

固定 Skiko 的正常 Maven publication 仍未通过 metadata 编译，当前下游使用的是明确标记的研究组装仓库。下一门槛是受支持的正常 publication 与不依赖原始指针 recorder 的线程退出配对；F02/P4/P11 仍为 **open/not-ready**。

## 2026-11-01 F02 候选最终链接与模拟器边界

固定 Skiko 的正常 Maven publication 在 `compileCommonMainKotlinMetadata` 重编译时报 `@OptionalExpectation` 只能用于 common source；隔离实验的 `-Xmulti-platform` 没有修复，排除该任务又在 nativeJs metadata 编译失败。为验证下游，ignored 研究仓库仅从同 revision 的既有本地元数据替换双 iOS 候选 Klib 及其 `.module` 哈希；23 个文件中严格只有这 4 个变化。它是**研究组装物，不是正常发布物**。独立 committed-snapshot consumer 经 dependency substitution 解析该候选，iOS arm64/simulator arm64 framework 强制重链均成功；最终 unsigned Xcode simulator/iPhoneOS App 构建均成功，debug dylib 分别含 `CacheOwner` 析构、TLS 初始化与 `__tlv_atexit` 系统导入。

隔离 bundle `io.ondeviceai.f02candidate` 在 iOS 27.2 simulator 安装并启动 PID `25744`，截图显示 Demo 主界面，随后研究进程正常终止。这只证明该候选在本次模拟器可链接、加载并显示普通首页；**没有真机线程退出后的原始缓存 live 对照、相机十轮重入、Leaks/Allocations、能耗或生产依赖验收**。设备状态查询的自动审批服务返回 429，调用未执行，不推断设备状态。证据哈希、构建日志和组装约束见 ignored `artifacts/verification/f02-remediation-20261101/candidate-consumer-summary.json`。F02/P4/P11 保持 **open/not-ready**。

## 2026-11-01 F02 TLS owner 候选及 Skiko Klib 集成

固定 Skia revision 的原版宿主夹具在线程退出后留下 1 块缓存原始存储；候选 `thread_local CacheOwner` 的析构释放该存储，8 worker 与跨线程删除场景均为 0，且同线程 1000 次 new/delete 只需 1 次底层分配。无缓存备选同样归零，但需 1000 次底层分配。这些是源码抽取宿主结果，不是真机修复证明。候选只存在 ignored 研究目录 `artifacts/verification/f02-remediation-20261101/`，未改生产 Skiko/Skia 依赖。

从仅有 `AtlasTextOp.cpp` 候选改动的固定 Skia checkout 重新构建并打包 iOS arm64 与 simulator arm64 静态库。两个新 Skia ZIP 的内嵌 `libskia.a` 分别为 SHA-256 `c90798bb2ce3fd7aff7f7de0a1ca9e37c19d837749c05b4010b1742dcccc845f`、`5f4790af17452aaac834a0b80bd9afa2b503ff9a55ce737a9a93eaf1b9dab1ca`；两个新 `0.144.6-SNAPSHOT` Skiko Klib 分别内嵌完全相同的库。`iosArm64Klib iosSimulatorArm64Klib --rerun-tasks` 为 **BUILD SUCCESSFUL / 18 tasks executed**，Klib SHA-256 分别为 `648c9fc00f98b513e309e4a2028c2260550a5b1505336d0a660f5819f2ef7413`、`a4b8fc6963c3ab299e027109106f2920cfa91179f6619c4ea5065e2e35b70bdd`。Klib manifest 的 Kotlin/Native compiler=`2.2.20`、ABI=`2.2.0`、target 对应 iOS arm64/simulator arm64。原目录中的 `0.144.6` Klib 是旧 instrumented 研究库，不能作为候选证据。

完整隔离 Maven publication 两次均在 `compileCommonMainKotlinMetadata` 失败，错误为 `@OptionalExpectation` 只能用于 common source；单独 Klib 目标不依赖该失败任务。这一步只关闭候选进入双 slice Klib 的门槛，没有完成 Maven 分发、Compose consumer 最终链接、`__tlv_atexit` 最终解析或真机线程退出/相机十轮对照。原始 SHA、构建日志和失败边界见 ignored `artifacts/verification/f02-remediation-20261101/klib-integration-summary.json`。F02/P4/P11 保持 **open/not-ready**。

## 2026-11-01 F02 十轮同 PID trace / 逐地址 live 关联

在相同 ignored 十轮研究 App（签名 debug dylib UUID=`1D3DD45D-E704-3B2E-A0EC-2A98BE8B0142`）上另起新 run，iPhone 16 Pro Max / iOS 27.0.1，PID=`34764`、runID=`8425AEE8-5D65-4742-96E1-EBF75BDB4E51`。`Leaks` 模板 trace 从 15:35:03.942 到 15:49:15.159（+08:00），**851.217207 秒**覆盖十轮相机页面协议和最终 idle。每轮活动 Vision 成功增量为 `666/658/686/673/683/686/683/684/685/681`；十次页面移除 30 秒后已观察 owner/Compose/Metal/bridge 弱引用全部为 0。研究 App 的安装 URL 与 trace TOC 的进程路径（`/private/var` / `/var` 别名）及 PID 一致。trace 的 `dyld-library-load` 仅导出 schema、无映像行，因此 **没有独立 trace UUID 回读**；UUID 由本地签名包回读并与安装/进程路径绑定，不把它写成独立 trace 证据。

设备私有 Documents 复制的 **1,387,570 条 / 122,106,160 B** 研究事件序号连续、PID/runId 一致、capture missed=0。逐地址解析将最终 **52 个 cached 地址**全部关联到相同 generation 的析构 entry/exit、`delete-cached` 和持该指针的线程退出观察；完整导出的 Allocations List 读取 **104,727 行**，52/52 地址各唯一匹配 `live=true` 的 128 B malloc 存储。50 个匹配行有 `AtlasTextOp::operator new` 责任栈，2 个为录制前已有、分配栈达到上限。这是研究 App 同 PID 中真实的**析构后线程缓存存储仍 live**证据，不是 52 个仍构造中的 C++ 对象。十轮页面退出 30 秒时采样 RSS 从 `157,368,320 B` 到 `167,247,872 B`（+`9,879,552 B`），不能将全部差额归因于这 52×128 B，也不能据有限十轮推算无界增长率。

研究 recorder 的全局 ring/generation 表保留原始地址；本轮 Leaks 明细仍为 0 行，**不能作为零泄漏证明**。原始 trace、Allocations/Leaks 导出、报告、事件、逐地址 ledger、解析脚本和设备收据保存在 ignored `artifacts/verification/f02-sustained-trace-20261101/`；汇总 `summary.json` SHA-256=`6f1f8f098baedbcfea941a217bfb3139601fda0204b6cf8e5b9824f0b56f01df`。独立 reviewer 通道返回 429，尚无审查结论。研究 PID 已显式 SIGTERM，普通无研究 C ABI Demo 已重新安装并无研究参数启动（PID=`34802`）；仅为恢复回执，不是 UI 验收。该轮加强了线程退出缓存残留的因果链，**生产依赖的修复/所有权决策及 P4/P11 发布验收仍 open/not-ready**。

## 2026-11-01 F02 十轮持续页面重入（研究 App）

仅在 ignored 研究 consumer 的**独立项目/探针副本**把原三轮协议扩为十轮；原 v4 源码/报告、生产 Demo 与依赖均未覆盖。iPhone 16 Pro Max / iOS 27.0.1 上，签名研究 App 的 debug dylib UUID=`1D3DD45D-E704-3B2E-A0EC-2A98BE8B0142`，runID=`3219BF16-972B-45F9-9E45-5BD6A9C31FB6`，同一 PID=`34735`。十轮各 30 秒相机活动 Vision 成功增量为 `748/754/757/757/757/749/756/751/751/754`；每轮页面移除 30 秒后已观察的 owner/Compose controller/Metal layer/bridge 弱引用均为 0，最终 60 秒 idle 尾部仍为 0。报告终态为 `observations-complete`，不是 `nativeLeakAcceptance` 通过。

私有 Documents 原始事件流为 **1,508,754 条 / 132,770,352 B**，88 B/条；独立解析全流序号连续、PID/runId 一致、地址/generation 匹配，设备报告的 capture missed=0。最终事件状态为 **50 个缓存地址**，其中 **48 个**有持缓存指针的线程退出观察；这是事件流状态，不是本轮 Allocations List live 地址数。研究 App 原始地址也被全局 recorder 持有。本轮**未运行 Instruments Leaks/Allocations trace**，所以不能据此把 50 个地址判定为最终 live、零泄漏、生产修复或持续运行无界增长。十轮页面退出 30 秒时的采样进程 RSS 从 `121,929,728 B` 到 `129,007,616 B`（+`7,077,888 B`），可能混有 recorder/运行时与系统因素，不归因为 AtlasTextOp；也不能称内存稳定。

研究 PID 已显式 SIGTERM；已重新安装且无研究参数启动普通 Demo，PID=`34751`，安装容器=`5B93037F-63E7-4DBF-90E8-42FC97A25CB6`，普通 debug dylib UUID=`6CC6F4AF-8726-375E-BB7C-EA22DCDB07A4` 且无五个研究 C ABI。此恢复仅为安装/启动回执，不是 UI 验收。原始报告、事件、解析器、构建与设备收据均在 ignored `artifacts/verification/f02-sustained-20261101/`；汇总 `summary.json` SHA-256=`9a32100d54dde0cd1e7211596d9ec7ae378da62b248de95a0966f0dc604f5846`。十轮有界重入和缓存累积机制得到进一步观测，但生产修复/所有权裁定、原生最终 live 验证与 P4/P11 发布门槛仍 **open/not-ready**。

## 2026-11-01 F02 同 PID 逐地址析构/TLS 与普通 Demo 恢复

隔离研究 App 在 iPhone 16 Pro Max / iOS 27.0.1 上以 PID `34674`、runID `E226E0C3-D90A-4805-BF5F-2E4494966DD1` 完成三次相机页面创建、退出和重入；各活动窗口 Vision 成功增量为 `660/691/690`，三次页面移除 30 秒后 owner、Compose controller、Metal layer 和 bridge 弱引用均为 0。301 秒同 PID Instruments 录制覆盖三个周期。运行的 `OnDeviceAIDemo.debug.dylib` UUID 为 `D2914E8C-93FA-3E0E-9414-070AE8B24C06`，与该研究包、安装 URL、PID 和事件 runId 绑定；不是生产 Demo 二进制。

事件导出合计 **416,726** 条、88 B/条，序号连续、无导出序号缺口；采集分 184 块完成，ring 在采集中覆盖旧事件 351,190 次，故不能把完整历史事件都视为保留。逐地址 ledger 针对最终 Allocations List 的 **18 个唯一 live 128 B 地址**：18/18 记录到对应 generation 的析构和 `delete-cached`，15/18 还记录到持有该缓存指针的线程退出；其余 3 个不据此推断线程仍存活或已释放。这里的“live”指原始 malloc 存储仍在 Allocations List，不等同于 C++ 对象未析构。旧 trace 中的 18 个地址属于另一 PID，不能直接逐地址配对。

本次研究 recorder 的全局 ring 和 generation 表也保存原始地址，可能使 Instruments Leaks 的可达性判断受到干扰。因此本轮 Leaks 明细 0 行**不是零泄漏、无残留或修复证明**；以逐地址析构/cache/TLS/最终 live 的限定结论为准。研究证据见 ignored `artifacts/verification/f02-consumer-20261101/camera-page-v4-summary.json`（SHA-256 `11115feba01b152145e6676af3c2dc00cedb5cdce1682586a3e2a391e093121d`）、`camera-page-v4-address-ledger.json`（`37a07604269335308a2ab9c879a143e1601eb1adf04564dcc400e70684cd420f`）、原始最终报告（`d2f3e658f37c675662e8db8711716a78d209f269b2bbbbd0838df4f78ac02fb9`）及完整 trace/导出。

研究 App 已显式 SIGTERM；随后从 committed source 构建、安装并无研究 C ABI 参数启动普通 Demo，启动回执 PID `34719`、安装 URL 为 `54F2120E-2511-4E32-A330-46054A62937E` 容器，`launch.json` SHA-256=`9001d260faf7af7fb359897f9566209038e09df0b1714e0f20258c899a38c1be`。该恢复只证明普通 App 安装/启动回执，不是新一轮 UI 验收。生产依赖与受保护未提交文件未改。F02 机制归因已有直接证据，但持续重入边界、生产修复/裁定及 P4/P11 发布验收仍 **open/not-ready**。

## 2026-11-01 F02 真机单页 ABI smoke

在 iPhone 16 Pro Max 真机安装并启动隔离研究 App；PID `34529` 的 `device-console.log` SHA-256=`f45b61a787fb5be8bfa76e5fae3a2f45a2407bb6d033db70ba448eccca5f49f6`。初始 `eventCount=0`，2 秒后 `delayedCount=120 copied=120 dropped=0 correctRun=true`；new-allocated/new-reused=8/22，destructor entry/exit=30/30，delete-cached/delete-freed=24/6。签名 device debug dylib UUID=`CADD1856-3A9A-31E5-911B-8317551BA3E6` 且含五个 C ABI。此证据覆盖真机最终 App 链接与 C ABI 调用，但只是单页研究 smoke；未运行相机三页/Leaks 协议，未完成逐地址 ledger，旧 18 个地址仍不可跨 PID 直接匹配。P4/F02 保持 `open/not-ready`。

## 2026-11-01 F02 iPhoneOS 最终 App 链接

同一 ignored research consumer 的 generic `iphoneos` unsigned 与使用既有身份/profile 的 signed App 构建均 **BUILD SUCCEEDED**；签名构建日志为 `artifacts/verification/f02-consumer-20261101/xcode-app-device-signed.log`（SHA-256=`8e4f83bd0b228d292b19f9d8127824e54a846d96ba4382574ecf7f6ae847f76d`）。最终 device debug dylib UUID=`CADD1856-3A9A-31E5-911B-8317551BA3E6`，包含五个研究 C ABI；App stub UUID=`2EEFC256-A5F0-3D06-A279-4D59476AE98A`。授权环境有签名身份，之前沙箱内 0 身份是凭据视图差异。设备安装授权调用因审批服务 429 未执行；沙箱尝试因 CoreDeviceService 初始化超时失败。**无真机 PID/事件/三页协议**，不能从链接证据推出旧 18 个地址的析构/释放路径。P4/F02 仍 `open/not-ready`。

## 2026-11-01 F02 最终 App 链接与 simulator 运行时事件

普通 unsigned generic simulator App 链接成功后，未引用研究 C ABI 的最终 debug dylib 被 `dead_strip`，因此不能只看 static framework archive 的符号。ignored consumer 加入显式 research probe 后，`xcode-app-sim-probe6.log` **BUILD SUCCEEDED**；最终 debug dylib UUID=`A93B4B79-57D6-32DE-B4F0-5DB2B983AF56`，包含五个 instrumentation C ABI，App stub UUID=`AFD5B670-07B7-3718-8C4E-07035B2018E0`。这是研究 consumer 的最终链接证据，不是生产 Demo 改动。

在 iPhone 18 Pro / iOS 27.2 simulator（UDID=`C8D20AFB-8CDF-4968-92E5-069F732C241E`）安装启动后，PID=`70773` 的 unified log 回读 `eventCount=0`，2 秒后 `delayedCount=120 copied=120 dropped=0 correctRun=true`，事件 kind 计数为 new-allocated 9、new-reused 21、destructor entry/exit 30/30、delete-cached 24、delete-freed 6。说明最终 app 中 C ABI 调用、88-byte event 结构复制、runId 和零丢失计数均可工作，且普通 Compose 页面产生了 AtlasTextOp 生命周期事件。该 run 没有触发 `ClearCache` 或 thread-exit kind，也没有做真机三页协议；P4/F02 仍 `open/not-ready`。

## 2026-11-01 F02 双 slice framework link 回读

隔离 committed-snapshot consumer 已分别完成 `:demo:shared:linkDebugFrameworkIosSimulatorArm64` 与 `:demo:shared:linkDebugFrameworkIosArm64`，两者均 `BUILD SUCCESSFUL`。生成的静态 framework archive 均含 `gpu.AtlasTextOp.o`、`CMPMetalLayer.o` 以及五个 instrumentation C ABI 符号；simulator archive SHA-256=`c85dcb91718af355cd9215517ee349ff0d813eaaf5562b4848a1306b4bda9077`，device archive SHA-256=`5c7b92eaae51b34bdd0ce1aef8f3f1b4237c67ba83b681117c875a4e00e0824e`。完整符号/平台回读见 ignored `artifacts/verification/f02-consumer-20261101/framework-evidence.json`（SHA-256=`c9de48b8376ab1f9797b3adb5ec9415938cadfcac02c1c09e86e28a34240fcae`）。

两个输出是 static archive，不能取得 `LC_UUID`；最终 app executable 尚未链接，因此当前只证明 **Kotlin/Native framework archive link 与 instrumented object/C ABI 包含**，不证明 Compose renderer/Metal layer 的运行时身份、C ABI 运行时调用或真机 P4 三页协议。F02/P4 仍 `open/not-ready`。

## 2026-11-01 F02 隔离研究包与 host 事件验证

官方依赖同步及固定 Skia 的 iOS arm64、simulatorArm64 原版和 instrumented archive 已在 ignored `artifacts/verification/f02-instrumented-skia-20261101/` 完成。iOS-only Skiko publication 产出 `0.144.6` Klib；两个 Klib 内嵌 `libskia.a` 的 SHA-256 分别为 `1f5804d9c31d42870b324215b0f495bcca1b4060cefa02492486f8bab75b0cdc`、`7d24eeec9a2ca4ff3dca53162a16e20e78fd293697abe2096d75eb8bb5846a7d`，与对应 instrumented archive 内的文件逐字节相同。ABI manifest 为 compiler `2.2.20`、ABI `2.2.0`、iOS 两目标；五个研究 C ABI 符号在 archive 中可见。来源及 publication 校验见 `instrumented-artifact-manifest.json`。

从 instrumented `AtlasTextOp.cpp` 精确抽取 allocator/recorder block，在 macOS 用 `clang++ -std=c++17 -O0 -pthread` 编译并运行 stress fixture。5/5 场景通过：同线程复用及 generation 更新；跨线程 delete 后 owner 与 non-owner `ClearCache` 差异；worker exit 时占用 TLS 的事件；8 线程 16016 个事件的连续 sequence/非零 generation/零丢失；65540 个事件的 ring 覆盖与 4 个丢失计数。这验证研究记录器的宿主语义，不证明 iOS app 中符号存活、Compose renderer/Metal layer 一致，也不是旧 18 个真机地址的析构证据。

ignored `artifacts/verification/f02-consumer-20261101/` 从 committed HEAD `568aa6d` 生成；Compose `1.11.1` / Kotlin `2.4.0` 的 simulator compile dependency graph 成功解析到 Skiko `0.144.6` 两个坐标。该检查点曾因自动审批服务 429 暂停 framework link；上方最新章节已记录审批恢复后的 simulator/device 双 slice link 和符号回读。UUID、runtime render-path 和设备三页协议仍未验证，P4/F02 仍 `open/not-ready`。

## 2026-11-01 F02 隔离 instrumented Skia 研究构建路线

对固定依赖做了只读上游核对：Skiko `0.144.6` 对应 commit
`9a5b398bb2044fff7e7a84fbfd6f4b803e4427c0`，其 Skia 依赖为
`m144-22f58c9fd4`。Skiko 官方的 `BuildLocalSkiaTask` 支持从包含
`tools/skia_release/` 的 Skia checkout 构建本地库：

```sh
./gradlew :prepareLocalSkiaBuild \
  -Pskia.repo.dir=/abs/JetBrains-skia -Pskia.target=ios
./gradlew :skiko:publishToMavenLocal \
  -Pskia.dir=/abs/JetBrains-skia \
  -Pskiko.native.ios.arm64.enabled=true \
  -Pskiko.native.ios.simulatorArm64.enabled=true
```

`NativeTasksConfiguration` 显示 iOS native 目标最低为 iOS 12，并链接
Metal/CoreGraphics/CoreText/UIKit，将 Skia 静态库以 `-include-binary` 纳入
Kotlin/Native。上游参考为 [BuildLocalSkiaTask](https://github.com/JetBrains/skiko/blob/9a5b398bb2044fff7e7a84fbfd6f4b803e4427c0/skiko/buildSrc/src/main/kotlin/BuildLocalSkiaTask.kt)、[properties](https://github.com/JetBrains/skiko/blob/9a5b398bb2044fff7e7a84fbfd6f4b803e4427c0/skiko/buildSrc/src/main/kotlin/properties.kt) 和 [NativeTasksConfiguration](https://github.com/JetBrains/skiko/blob/9a5b398bb2044fff7e7a84fbfd6f4b803e4427c0/skiko/buildSrc/src/main/kotlin/tasks/configuration/NativeTasksConfiguration.kt)。

该路线属于 Skiko 开发/研究用途；没有官方证据证明本地库可以直接替换
Compose Multiplatform 1.11.1 的传递 Skiko。后续若执行，只能在 ignored 隔离 checkout
中做 Gradle dependency substitution，并对 Kotlin/Native ABI、当前 Compose renderer、
Metal layer 和 `AtlasTextOp` 构造/析构/cache/TLS 事件做对照。未经这些对照，不修改生产
依赖、不覆盖缓存、不调用私有 `ClearCache()`，也不把研究包当作发布 artifact。当前
F02 仍 `open/not-ready`，这条路线是下一步可执行输入而非根因修复证据。

## 2026-10-28 全范围审查与依赖结论

fresh P4 code-review 覆盖 camera isolation、synthetic PHPicker harness 和当前
resource/page/thread probe，结果为0 Critical、2 Important、0 Minor。默认 resource probe
静默吞掉二次报告写入失败的问题已按 RED→GREEN 增加有界 fallback，并通过 fresh generic
iOS 15 unsigned build；受保护 signing 工程继续留在独立 workstream，禁止进入 P4 选择。
完整范围、哈希和验证见[全范围审查记录](p4-full-review-20261028.md)。

上游只读核验同时得到 `no-supported-upgrade-conclusion`：已发布的 Compose 1.12.2 /
Skiko 0.150.2 及更后 Skia m153 源码仍保留相同 AtlasTextOp TLS 单槽、调用线程
`ClearCache()` 和 `GrRecordingContext` 析构清理机制，没有证据表明升级会处理本次18个
residual。当前不升级、不强制覆盖 Skiko、不调用私有清理，也不重标 Leaks；P4 继续开放。

## 2026-10-27 分配时间与线程快照邻接复核

对既有 `p4-thread-calibration-20261027` 的完整 Allocations List、Leaks 明细和 236 个 own-task 快照做了只读时间对齐。18 个最终 live `GrOp` 中，1 个在 trace 开始前已有；其余 17 个分配的 trace 相对时间均能由 4097B marker 校准到分配时刻前后各约 1.1 秒内的快照，并且前后快照都观察到同一个 kernel thread ID。7 个导出低 32 位标签在不同分配窗口分别对应多个不同 kernel thread ID，具体映射与每行邻接时间见 `artifacts/verification/p4-thread-calibration-20261027/allocation-snapshot-correlation.json`，复核脚本为同目录 `correlate_allocations.py`。

这把“相同导出标签”与“同一实际 kernel thread”区分开：本次 17/17 的时间邻接样本支持分配时刻的观察映射，同时 7 个标签的跨周期 ID 复用仍然存在。快照是非原子的，marker 到 trace 时间原点有调度误差；结果没有读取析构/free、TLS 槽值或精确线程退出，不能关闭 GrOp、证明缓存释放或推导长期增长。原始 Leaks 判定和 P4/P11 状态保持不变。

## 最新：marker 校准与实际 thread label 复验（2026-10-27）

Task12 的取证完整性修正先以 lookup seam 稳定复现缺失 pthread 映射的 runtime RED，再要求 `partial-snapshot`、失败数和 unchanged send-right；修正后 macOS 宿主100轮快照通过，引用计数2→2，iOS15 Swift6 warnings-as-errors typecheck通过。设备 probe 仅在显式 `--camera-thread-inventory` 下启用，普通模式不改变。

本轮在当前源码生成的签名 probe 上完成三轮同进程页面退出/重入和精确 PID Leaks 录制。三轮活动窗口新增 **687/661/660** 次，停止5秒无新增；最终页面 owner、Compose controller、Metal layer 与 bridge 均释放，首次全弱引用释放的实际页面缺失时长约32.643秒，最终约61.986秒仍为0。trace 时长301.202178秒，导出 Allocations List 读取108263行/85579214 bytes、exit0；最终 **18×GrOp/2304B** 全部仍为 live，不能据此关闭原生残留。

4097B marker 实际由 malloc 分类为5120B，分配栈命中 `ThreadInventoryMarker`。其记录的 pthread low32 candidate 与导出的 thread label一致，证明此导出器使用相同低32格式。8个 GrOp allocation labels 都能在 own-task 快照中找到；其中7个完整 pthread 指针分别对应多个不同 kernel thread ID，说明相同 label 不能直接代表同一持续存活 OS 线程。236个快照中66个 complete、170个 partial，partial 仅因 pthread 查询缺失；未见 thread_info、port release 或 VM array release 错误。

这仍不是析构/free/TLS 证据：快照边界非原子，缺失不是精确线程退出时间；没有跨线程内存读取、TLS 读取或私有清理调用。marker 只校准一个已知主线程分配，其他 GrOp 仍缺少 generation/析构归属。新限定 reviewer 继续因429无结论。证据：`artifacts/verification/p4-thread-calibration-20261027/summary.json`、`candidate-thread-mapping.json`、`snapshot-summary.json`及同目录原始报告/trace/导出。P4/P11保持开放。

## 最新：设备 LLDB 取证边界（2026-10-27）

使用本机`devicectl`帮助中记录的`device select`、`device process attach -p`路径，5次有界诊断均未取得可检查的映像。target保持`<none>`/`state=connected`；显式`remote-ios`仍报`no modules found`，`process interrupt`报`Process must be launched`，补充`process attach`报`no such process`。这些是调试工具结果，不能推导产品错误、析构已经发生或TLS槽归属，也不关闭原生残留项。没有新的受支持路径时不重复这些变体。

原始日志与后续只读设备进程快照位于`artifacts/verification/p4-atlas-device-debug-20261027/`。普通Demo原恢复安装URL的新鲜进程为PID27906；上次明确无参数启动为PID27901。本次未重装、重启、终止或操作另一旧安装的进程，未取得新的设备UI或native生命周期验收。已有页面退出和bridge自然释放证据保持有效，基础进度仍10/12、P4/P11未完成。

## 最新：末次 bridge 延迟释放与线程退出机制（2026-10-27）

在保留上一轮全部证据的基础上，当前probe增加最后页面退出后的31/35/60秒名义采样，并记录实际monotonic时间及同步弱bridge状态；不重建页面、不强制GC，也不跨await持有bridge。当前源码/生成器/framework哈希均与本轮实际构建匹配，上一轮审查后的诊断/manifest/status修订也已随新包实际运行。

iPhone16ProMax/iOS27.0.1，PID27882/runID `543373D1-52A7-424C-9ED1-FE5922D6E97C`。三轮30秒窗口新增672/694/694次Vision成功，stop后5秒无新增且inputs/jobs0。每轮名义30秒采样时owner/Compose controller/Metal layer均为空，bridge仍存在但**admissionAvailable=false、pendingInputs/jobs=0**。最后退出实际30.992938秒bridge仍存在，32.086039秒采样已消失，直到62.350613秒仍全部weak对象为0；没有第四次页面或相机重启。原始结果为`observations-complete`、`weakReferenceOutcome=released`，nativeLeakAcceptance继续显式unverified。

301.116972秒Leaks录制（07:14:56.428–07:19:57.545 +08:00）覆盖全部周期与idle尾部。**18×GrOp/2304B仍列入Leaks且全部在最终live allocation表匹配**：17项有33帧AtlasTextOp路径，1项录制前已有。完整导出99401行/73387236bytes、exit0。此次导出额外保留binary定义，trace中`OnDeviceAIDemo.debug.dylib`的UUID **00708A06-D253-3EEB-9B8C-0AF13A8BF460**与实际运行构建的dwarfdump完全一致，PID/安装URL/时序也吻合。末次RSS快照147816448B，不据此宣称零泄漏。

这关闭本实验范围内“最后bridge是否必须重入才释放”的疑问：已关闭的bridge在持续空闲中自然释放。**原生残留仍未解决**，也不能把本轮18与上轮19跨运行对比为增长率或改善。当前普通Demo已恢复无参数启动PID27888，原应用容器、保护文件及生产源码保持原样。证据与严格验证脚本：`artifacts/verification/p4-page-tail-20261027/summary.json` / `verify_tail.py`。

### 固定源码与宿主机制复现

直接取得同一固定Skia revision的[AtlasTextOp.h](https://github.com/JetBrains/skia/blob/22f58c9fd43d55bde818821c04b48fda5d7ec939/src/gpu/ganesh/ops/AtlasTextOp.h#L68)、[GrOp.h](https://github.com/JetBrains/skia/blob/22f58c9fd43d55bde818821c04b48fda5d7ec939/src/gpu/ganesh/ops/GrOp.h#L81)、GrOp.cpp及[GrRecordingContext.cpp](https://github.com/JetBrains/skia/blob/22f58c9fd43d55bde818821c04b48fda5d7ec939/src/gpu/ganesh/GrRecordingContext.cpp#L63)，文件hash和下载URL已保存。它们确认virtual GrOp析构、AtlasTextOp自定义delete及context析构ClearCache调用；不是从类名猜测删除路径。

从已固定cpp的68–89行提取allocator块，仅将全局new/delete调用映射到计数器，用简化120B虚析构类和`unique_ptr<GrOp>`运行独立C++宿主实验（没有链接Skia或修改设备TLS）。`clang++ -std=c++17 -Wall -Wextra -Werror -O0`与运行均exit0：

| 宿主场景 | 观察结果 |
| --- | --- |
| 同一线程三次new/delete | 三次析构，仅一次分配；同线程ClearCache后原始存储归零 |
| 三个线程分别new/delete后退出 | 三次析构、三次分配；join及主线程ClearCache后仍有三块原始存储 |
| 存活worker缓存、主线程清理 | 主线程清理仍留一块；由worker清理才归零 |

宿主三次线程的pthread指针完全相同，kernel thread IDs却不同。原始`thread_local void*`槽没有指向对象的自动清理行为，线程退出路径可丢失这块已析构存储的引用。实验完成后仅回收实验自己的已退出线程存储，不调用实际Skia或设备私有清理API。这说明**线程退出/地址复用可以解释多个周期的残留**，尚不证明真机地址实际经过此路径。

另导出上一PID27863的thread-info：35个kernel TID与Allocations List的thread-id标签没有交集，所有metadata时间为0，未建立生命周期映射。因此，之前“同分配线程有多个地址”应严格解释为**相同导出thread-id标签**，不能据此断言同一持续存活OS线程有多个TLS槽，也不能据此排除单槽缓存机制。需要继续观测实际thread lifetime、delete/析构、TLS槽指针与对象地址的关系。

源码/宿主实验/thread metadata见`artifacts/verification/p4-atlas-reentry-upstream-20261027/summary.json`。researcher本轮429没有意见；作者直接官方取证成功。最近五条默认分支AtlasTextOp.cpp历史未取得可适用修复依据，这不是完整上游修复检索或依赖升级结论。当前不改依赖、不压制Leaks，P4/P11与完整交付继续开放。

## 前次：同 PID 三次页面退出/重入（2026-10-27）

新真机实验已补上真实页面退出：生成器把生产App内容和StateObject原样移入page View，由独立root控制移除/新建；未修改生产导航、相机、controller或依赖。新probe只用弱引用观察page owner、bridge、Compose controller及Metal layer，不强制GC、清理TLS或保存画面。逐项生成源码反向还原与生产源码完全相等；这是生成root驱动的页面实验，不是物理UI控件验收。

iPhone16ProMax/iOS27.0.1、PID27863、runID `7BB10334-CBE9-4CC8-839A-BC6DD3F38C20`。三轮均创建新owner，实际相机活动30秒后stop/drain并等待5秒，再移除整个页面并采样5/15/30秒。每轮最后采样的owner/Compose controller/Metal layer均已释放，bridge仍为1；下一轮warmup时上一轮bridge已消失。最后一轮bridge在最终采样仍为1，所以原始probe结果保留`incomplete-release`，未证明其后释放时间。

| 周期 | 活动秒数 | 窗口新增Vision成功 | 每轮成功累计 | 活动RSS(B) | 页面退出30秒RSS(B) | 本周期分配且最终仍存活的Leaks地址 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 30.137306 | 667 | 672 | 468615168 | 144605184 | 7 |
| 2 | 30.049058 | 690 | 694 | 472645632 | 149618688 | 6 |
| 3 | 30.189110 | 694 | 697 | 474431488 | 150798336 | 5 |

同PID Leaks录制301.106594秒，06:56:02.474–07:01:03.580 +08:00，已验证设备/PID/安装URL和全部周期的时间覆盖。**Leaks19×GrOp/2432B，全部地址在最终live Allocations List中匹配**。完整流式导出104655行/80807499bytes、exit0；18项均有33帧AtlasTextOp/SurfaceMetalRedrawer文字绘制栈，另1项在录制前已存在。18项来自8个allocation thread IDs，其中4个线程各有3个最终存活地址，分属不同周期。

表中的7/6/5是**最终存活地址按分配周期分组**，不是三个时点的Leaks全量计数或全分配事件序列。它证明三轮均新增跨页面退出仍存活的原生分配；当前“每个分配线程恰好一槽”的简化解释不足以关闭此项。实际delete/析构可能在其他线程，TLS槽和释放线程仍未直接观测，也没有证明长期无界增长。Allocations另外含3个live AtlasTextOp地址未列入Leaks，不将其擅自判成泄漏。原7×GrOp旧trace保持原结论，不能跨不同运行计算增长率。

最终Allocations persistent为35672864B、其中VM:IOSurface3080192B；RSS明显回落说明大块资源释放，但不是零泄漏、峰值、footprint、GPU或能耗验收。下一步聚焦AtlasTextOp实际析构/释放与线程归属，并在最后一次页面退出后补更晚的bridge采样。保持当前依赖，不调用私有ClearCache或压制Leaks。

证据：`artifacts/verification/p4-page-release-20261027/summary.json`，包含每轮样本、地址/线程/分配时间、完整栈、原始trace、生成源码/实际App/framework哈希与restore记录。新普通Demo已恢复无参数启动PID27875，保留容器。独立probe审查的3项诊断/元数据建议已处理：失败报告写入错误记录有界NSLog、manifest补framework/Info哈希、将观察完成与原生泄漏验收分字段；第4项生成模式验证由真实双模式源码还原/manifest检查满足。修订后签名构建再次成功，但未再运行真机；真机使用的修订前probe源码和binary保存在独立目录，结论仅对应它。未声称全P4独立审查通过。

## 历史：单次持续/停止 trace 的只读调查

2026-10-27早先针对 `rendered-camera-pid27593.trace` 的只读调查没有新真机运行、生产源码或依赖修改。当时结论是**五个对象的分配路径已明确，线程缓存假设得到支持，完整资源释放仍未验收**；后续跨周期证据以上方最新实验为准。

## 本轮取得的运行证据

原始 runID 为 `DE42C583-3089-416C-92A0-94B38266F918`，PID27593；其两分钟真实相机/Compose运行和停止后30秒验证见[P4记录](p4-camera-stability.md)。本轮按原 Leaks 明细的七个地址筛选 Allocations List，完成导出读取118470行、77838589 bytes，保留目标记录并解析共享frame引用。七个地址、live状态及128B大小全部与原明细吻合。

| 地址 | trace内分配时间 | allocation thread-id | 可用分配栈 |
| --- | --- | --- | --- |
| 0x10240a600 | 00:00.000.000 | 0x6efff000 | 栈限制；录制前已有 |
| 0x10240a780 | 00:00.000.000 | 0x6efff000 | 栈限制；录制前已有 |
| 0x117fb2a80 | 02:12.450.161 | 0x6efff000 | 33帧 |
| 0x11c4c0f80 | 02:31.993.493 | 0x6f1a3000 | 33帧 |
| 0x11c4c0380 | 02:32.176.569 | 0x6f22f000 | 33帧 |
| 0x11c4c3200 | 02:32.285.241 | 0x6ef73000 | 33帧 |
| 0x11c4c0880 | 02:32.352.232 | 0x6f117000 | 33帧 |

五个符号化对象分别来自五个不同分配线程，均有如下完整路径中的关键帧：

`SurfaceMetalRedrawer.startRenderLoop → renderAndPresentFrame → Canvas.drawPicture → SkCanvas.drawTextBlob → SurfaceDrawContext.drawGlyphRunList → AtlasTextOp::Make → operator new → malloc`

栈底为GCD serial lane/worker thread。这明确了这五次分配来自Compose/Skia文字绘制。它记录的是**分配线程与分配栈**，没有记录这些地址的析构执行、最终TLS槽指针或释放线程；不能直接将分配路径等同于泄漏所有权根因。另两个对象仍无可用归因。报告中的wallTime只有秒精度，也不据此断言最后几次分配发生于stop调用之后。

## 固定版本机制与当前二进制

上游只读研究核对的版本链为Skiko `0.144.6` / commit `9a5b398bb2044fff7e7a84fbfd6f4b803e4427c0` → Skia tag `m144-22f58c9fd4` / commit `22f58c9fd43d55bde818821c04b48fda5d7ec939`。

[固定 AtlasTextOp.cpp](https://github.com/JetBrains/skia/blob/22f58c9fd43d55bde818821c04b48fda5d7ec939/src/gpu/ganesh/ops/AtlasTextOp.cpp#L68-L89)已直接获取并保存，其68–89行明确：

- `gCache`是`thread_local void*`单槽。
- `operator new`优先取出缓存并清空槽，没有缓存时才分配。
- `operator delete`在槽为空时保留这块已析构对象的原始存储；槽非空则释放本次存储。
- `ClearCache()`释放并清空调用线程的槽。

本地最终验证包的Mach-O也含相同路径：TLS符号`__ZN5skgpu6ganeshL6gCacheE`位于`0x21c3550`；Make的分配慢路径请求120B，与trace中128B malloc分类一致。删除析构函数先释放几何引用链/其他成员，再选择缓存或`operator delete`。导入符号表核对了new/delete跳转目标，避免依赖反汇编中不准确的邻近符号标注。

还取得了补充证据：当前二进制的`GrRecordingContext::~GrRecordingContext()`在`0x486374`直接调用`AtlasTextOp::ClearCache()`。因此，不能从仅查看`GrDirectContext`派生类析构函数推导“context销毁不清理缓存”。但是该清理仍限于调用线程，不会自动遍历其他线程的TLS槽。

被分析本地产物SHA256为`a54698340b106d1482cec680c68c39015910e556294983981b5f6821516f2d3f`，UUID为`5DD32633-C5A3-3B08-9104-9B915963FAAB`。此次attach trace的`dyld-library-load`导出仅有schema、无事件行；没有据此宣称trace映像UUID已独立复核，也没有读取运行时TLS值。

## 结论与下一验证

“五个文字绘制分配分别来自五个线程”与“每线程缓存一个已析构对象的存储”相容，支持线程缓存解释；它尚不能证明所有七个对象都是缓存、存储已析构、线程退出时被释放，或长期运行没有增长。当前仍保留Leaks原始判定，不标记为误报或零泄漏。

下一验证应保持现有依赖，通过真实页面退出/重入及同PID采样，区分稳定缓存、跨线程清理边界与持续增长，同时观测Compose owner/renderer正常释放。正常停止相机仍保留页面和最后一张预览，不能代替整个页面的teardown。未取得适用于本版本的已证实上游修复，不据此升级或增加私有清理调用。

证据在`artifacts/verification/p4-atlas-cache-20261027/summary.json`，包括七个分配记录、五份33帧栈、源码hash、当前二进制符号/反汇编/导入目标与保护hash；完整栈另存`allocation-stacks.txt`。原录制包与summary保留不变。

## 工具与证据边界

- row-address XPath导出为空；首个64MiB上限仅覆盖部分记录，明确保留为partial。扩至512MiB上限后于约77.8MB完整结束，匹配全部七个地址，不把早先空输出解释成对象不存在。
- 桌面UI路径未到达目标trace，且出现窗口不可用/状态变更；没有Instruments UI审查结论。实际调用栈来自CLI导出。
- 上游首次研究有结果，后续补查遇429，没有追加结论或独立产品审查通过。
- 直接补取`AtlasTextOp.h`与Skiko `gradle.properties`的两次只读下载未执行：自动审批服务429，审核未完成，并非操作被判定不安全。未绕过审批；关键缓存路径以已获取cpp及本地二进制交叉核对。

P4/P11仍未完成，基础里程碑保持10/12；全部原范围、未提交修改与保护文件保留。
