# 分阶段交付与恢复入口

## 当前进度口径（2026-10-26 复核）

**基础里程碑 10/12 已完成：P0–P3、P5–P10；P4 和 P11 未完成。** 依据加入 P11 时的原始 12 阶段基线 `cda5ba8`，按各工作包自己的停止点计数。此前反复报告的 5/12 混入了后续 SDK/Demo、第二平台、生产模型和最终验收要求，无法准确反映阶段性成果；详见[计数复核与逐阶段证据](validation/roadmap-progress-audit-20261026.md)。这不是完整项目的完成比例或发布许可。

**完整 SDK/Demo 交付仍在进行。** P4 交互/资源释放、语音实录与 Android ASR、视频 Android/质量、多模态 Android LiteRT-LM 生产推理、身份模型/PAD/数据/校准、资源与最低系统验收及 P11 统一验收均继续推进。P9 的单模型/单平台试验里程碑已完成，当前活动工作是其已批准的 Android 扩展包。

2026-10-27 分批交付：用户已授权将验证通过的benchmark、多模态Kotlin/iOS picker、相机隔离、可选LiteRT-LM基础门禁与合成EXIF验证分别提交，并同步现有foundation分支到origin+jihu。提交与最新复验见[handoff顶部](HANDOFF-2026-10-23-next-session.md)；签名工程、身份草稿和未完成资源取证工具保留本地。提交/同步不关闭P4/P11，也不表示生产后端或发布验收。

2026-10-27 Android ModelStore：修复IO已登记但尚未交付的lease在返回调度前取消时遗失的问题；确定性回归RED→GREEN，ModelStore12/12、LiteRT-LM25/25、lint/consumer/release AAR和仓库Android门禁通过。只回收本次未交付lease，其他owner仍pin；独立review429未取得结论。见[P2补充](validation/p2-model-store-review.md)。未增加设备/模型/native drain验收，基础10/12、P4/P11仍开放。

2026-10-27 P11 一页状态矩阵：新增[状态矩阵](validation/p11-status-matrix-20261027.md)，覆盖9项能力和统一 `open/not-ready` 发布阻断；矩阵来源哈希纳入 readiness 校验，16项来源哈希通过。它不提升局部 pass-bounded 结果，也不关闭P4/P11。

2026-10-27 P9/P11资源补证：固定公开单图的独立VLMProbe单目标Metal trace成功（20.776271秒、PID28389），501条GPU activity、3,227条Metal分配记录，观察峰值1,723,596,800B。Power Profiler因工具SIGTRAP无有效导出，能耗仍未验证；本轮不关闭可比较GPU peak、统一资源预算或P11。详见[移动端VLM](validation/mobile-vlm.md)。

2026-10-27 P11 SDK候选冻结：从双远端已同步的 `7b9f7fc` committed tree 生成干净源码归档，内嵌commit/tree与SHA-256均已固定；受保护的1项tracked dirty和7项untracked继续留在foundation工作树且未进入归档。SDK commit冻结门槛已就绪，模型、最低系统、质量/资源/能耗等P11阻断仍保留，整体仍 `not-ready`。

2026-10-27 Android LiteRT-LM consumer：公开只读availability与独立Kotlin app已完成project/Maven Local消费及TECNO缺资产UI验证；backend JVM25/25、consumer2/2、multimodal48/48，设备backend4/4、UI1/1。默认Demo/base multimodal无vendor runtime，opt-in精确0.18.1；lint无error、warning保留。生产factory/generation/close消费、模型、真实drain/推理、API26及独立review仍开放，Task5仅分段交付；详见[移动端VLM](validation/mobile-vlm.md)。基础10/12、P4/P11未完成不变。

2026-10-27 P4当前源码Compose相机UI：真机控件/前后台/用户停止2/2，两分钟持续UI1/1，合计3/3、0失败/跳过；四窗口新增748/750/749/750，120.457944秒后计数3047，停止5秒不增加。普通Demo已无参数恢复；未访问相册，无图像/视频附件。补齐当前源码三项UI验证，原生GrOp、PHPicker EXIF、最低系统/全范围审查及P11仍开放，基础10/12不变。见[P4记录](validation/p4-camera-stability.md)。
2026-10-27 P4线程标签校准：修正 own-task 快照在 pthread 映射缺失时误报 complete 的缺陷；100轮宿主 send-right 2→2、iOS15 Swift6严格 typecheck和当前 Swift5/Swift6 相机门禁通过。当前源码真机三轮页面退出/重入窗口新增687/661/660，最后页面对象/bridge约32.643秒首次全释放；同PID 301.202178秒 trace 为18×GrOp/2304B live。4097B marker校准导出低32 thread label；8个GrOp标签中7个完整pthread指针对应多个kernel TID，不能据同标签推导同一线程或TLS根因。P4原生残留、完整审查和P11仍开放，见[专项调查](validation/p4-atlas-text-cache.md)。
2026-10-27 P11准备矩阵：已建立只读 `artifacts/verification/p11-readiness-20261027/summary.json`，覆盖9项能力并保留逐项未验证原因；所有9行仍有 gaps，`p11Gate.status=open`，发布结论 `not-ready`。该矩阵仅用于统一验收前的证据盘点，不改变10/12计数。
2026-10-27 P4 ThreadInventory 主机门禁刷新：Swift6 主机100轮快照为 complete、send-right 2→2，缺失 pthread 映射为 partial；iOS15 arm64 typecheck 与资源生成器 Ruby 语法检查通过。证据 `artifacts/verification/p4-thread-inventory-local-20261027/summary.json` 仅覆盖 host ownership/snapshot release，不关闭真机线程/TLS/GrOp/零泄漏门禁。
2026-10-27 P11 candidate freeze 预检：`artifacts/verification/p11-candidate-freeze-20261027/summary.json` 为 `not-ready`；工作树1项 tracked dirty/7项 untracked，只有既有 Apple MLXVLM 合成图观察值可冻结，LiteRT-LM授权模型、Face Identity生产模型与校准、iOS15/API26设备、GPU/energy/峰值资源和统一fixture仍缺失。仅作候选盘点，不冻结发布候选，不改变10/12或P11开放状态。
2026-10-27 P11 readiness 校验门禁：只读 `tools/validate_p11_readiness.py` 强制 `p11Stop`/`requiredBeforeP11` 停止契约，检查证据路径、来源哈希、行计数及非terminal行不得关闭门禁；补齐所有证据必须纳入哈希的门禁。当前快照校验为9行/9 gaps/16证据路径/15哈希，完整工具套件 **156/156**。这只证明快照完整性，P11仍 `open/not-ready`，不增加设备、模型或发布证据。

2026-10-27 Android 合同修正：固定 LiteRT-LM 0.18.1 AAR 否定原 terminal + isAlive=false 的 drain 判据；probe schema 2 不再认证该证明。两项回归 RED→GREEN，JVM9/9、lint/APK/test APK 构建通过；生产 factory/consumer 及真实 native drain 仍未完成，没有模型或设备运行。见[专项证据](validation/android-litertlm-drain-contract.md)。基础10/12不变。

2026-10-27 P4并发包补验：最终Task10限定独立审查APPROVE、0 findings；MLX增强Demo以全部可选开关和固定pins完成generic iOS unsigned构建。补齐下条当时的审查/增强构建缺口，不替代UI/EXIF、原生GrOp或最低系统验收；基础10/12不变。

2026-10-27 P4当前源码并发隔离：capture actor/串行executor、Sendable回调和MainActor controller已实现；Swift5/6严格检查无诊断，simulator8/8、TSan7/7、host470/470及默认/可选Demo构建通过。iPhone16ProMax当前源码11/11、0 skips；两分钟2992次Vision成功，停止/释放及暂停/恢复/FIFO通过，普通Demo已恢复。独立审查服务429，无结论；原生GrOp、真机PHPicker/完整UI与最低系统仍开放，详见[P4记录](validation/p4-camera-stability.md)。基础10/12不变。

2026-10-27 P11报告准备：修复现有benchmark工具重复run将30样本膨胀至60的问题，并拒绝输出别名覆盖原始输入。聚焦9/9、完整Python工具148/148、限定独立审查APPROVE；历史双端30样本及p50/p95重算不变，raw hash保留。见[报告完整性验证](validation/benchmark.md)。P11统一候选/任务/设备/质量与发布结论仍未完成，基础10/12不变。

2026-10-27 P4并发改造前的行为回归：新增真实相机暂停/恢复与连续stop/start通知/回调验证，iPhone16ProMax/iOS27.0.1完整生命周期套件**9/9、0 skips**；暂停保留输入输出，恢复继续出帧，关闭后无新帧且inputs/outputs0。普通Demo已恢复。生产源码未改，Swift6严格检查的4处捕获warnings仍未解决，不据此关闭P4；详见[P4验证](validation/p4-camera-stability.md)。

2026-10-27最新P4尾部复验：当前probe真机三轮新增672/694/694次成功，页面退出后bridge已closed/无inputs/jobs，在不重入、不强制GC时末次32.086秒采样释放，持续空闲至62.351秒。18×GrOp/2304B仍最终live，trace/binary UUID已匹配；宿主复现支持线程退出导致TLS原始存储残留的机制，但设备线程/TLS归属尚未证明。普通Demo恢复，详见[专项调查](validation/p4-atlas-text-cache.md)；基础10/12、P4/P11仍未完成。

2026-10-27最新P4页面退出/重入：同PID真机三轮真实渲染/相机窗口新增667/690/694次成功，停止与页面移除均执行；每轮30秒后owner/Compose controller/Metal layer释放，最后bridge仍待后续采样。301.107秒Leaks录制留下19×GrOp/2432B，18项33帧AtlasTextOp栈按三周期分配7/6/5，均最终live；不能认定稳定缓存或零泄漏。普通Demo已恢复，完整证据见[专项调查](validation/p4-atlas-text-cache.md)。基础10/12及P4/P11未完成状态不变。

2026-10-26 当前 P4 进展：Android 模型资产待答期间，完成 iPhone 16 Pro Max/iOS27.0.1 的 camera terminal-close 合同 **5/5、0 skips**，默认 simulator 生成模式回归 **4/4**。真实 capture input/output 从1/1变为0/0，关闭后无新增 callback且拒绝重启；普通 Demo 已恢复。两分钟 UI 用例仍因 runner automation 初始化超时未执行；另取当前 Compose 空闲 **91.471143s** Leaks，观察到 **4×GrOp/512B**，责任栈达到限制，完整释放仍未完成。详见 [P4 当前证据](validation/p4-camera-stability.md) 与 handoff 顶部；基础计数仍10/12。

同日后续只读复核：Instruments Inspector 中四个对象的 Allocation Backtrace 也全部达到栈限制，本机公开 recording options 没有 stack-depth 设置；现有 trace 无法继续归因，不据此升级依赖或宣称泄漏已解决。P4 其余交互、资源释放与审查门槛继续推进。

同日 P4 新修复：关闭后重新注册callback会持有新owner的运行时RED已修复；回调读写/terminal门禁同步，旧capture在锁外释放。新simulator生命周期7/7、TSan相机专项6/6、Apple host470/470与默认consumer/Demo构建通过。严格并发capture warnings、独立review429及本次改动的硬件复验仍保留；不升级P4完成状态。

同日后续硬件复验：同步修复的当前源码在iPhone16ProMax/iOS27.0.1 **8/8、0 skips**；input/output1/1→0/0、关闭后无新增callback、拒绝restart。普通Demo已恢复安装/无参数启动。当前源码硬件复验项已关闭，其余P4资源、PHPicker EXIF、最低系统与审查缺口不变，基础计数仍10/12。

同日P4照片方向验证：独立iOS27.1模拟器通过真实PHPicker→生产DemoController，合成EXIF2/6/7共**3/3、0 skips**，尺寸与四角镜像/旋转像素均匹配；未改生产源码。新模拟器自带示例图引起的初始网格count失败已留档，最终按核验过的精确fixture标签唯一匹配。真机PHPicker→Vision、完整Compose交互与其余P4门槛仍未完成。

同日P4持续管线验证：iPhone16ProMax/iOS27.0.1使用真实DemoController→bridge→Vision的device-only用例及既有回归共**9/9、0 skips**；四个30秒窗口新增720/721/720/720次成功，活动120.069秒后累计2885次。停止后5秒计数不变、jobs/inputs清空，owner释放及bridge/detector关闭通过。普通Demo已恢复。该结果不覆盖Compose控件或Leaks/渲染释放，P4/P11仍未完成；原始报告见`artifacts/verification/p4-sustained-controller-20261026/summary.json`。

2026-10-27 P4实际Demo渲染资源验证：独立验证包复用生产SwiftUI/Compose/controller，真机活动121.118秒取得2628次Vision成功，停止30.086秒计数不变且inputs/jobs清空。210.750秒Leaks trace已按runID/PID/安装URL/时间关联，覆盖基线及持续/停止窗口；7×GrOp/896B中5项获得`AtlasTextOp::Make`分配符号，2项仍无完整栈，完整渲染释放与根因未解决。普通Demo已恢复；物理控件/真机PHPicker/最低系统/审查等缺口继续保留，基础10/12不变。详见[P4最新证据](validation/p4-camera-stability.md)。

同日P4分配调查：原7个GrOp地址全部在Allocations List匹配，5个取得33帧文字绘制分配栈、来自5个不同线程。固定源码/当前本地二进制确认AtlasTextOp每线程单槽缓存及GrRecordingContext析构清理路径；缓存解释得到支持，但仍缺运行时TLS/析构、完整页面退出与增长判定。保持7×GrOp/896B资源项开放，后续进行正常页面退出/重入验证，见[专项调查](validation/p4-atlas-text-cache.md)。基础10/12不变。

## 历史检查点

以下保留各次验证与当时的计数；进度口径以上方最新复核为准。

2026-10-26 P9 LiteRT-LM 复审修复与 release package：2 MEDIUM 经 RED→GREEN 处理，lease 覆盖 cache 成功清理，非返回 drain 在 30 秒后 poisoned 并保留资源；完整模块 JVM **23/23**。lint、debug/release AAR、POM/Gradle metadata、probe 与默认 Demo 构建通过（567 tasks），默认 Demo 不解析 LiteRT-LM，生成包仅在 runtime 依赖固定 `0.18.1`，未发布。固定授权模型、真实 callback/native drain、生产 adapter/consumer 与设备推理仍未完成，维持 **5/12、P9 active**；详见 [移动端 VLM](validation/mobile-vlm.md) 和 handoff 顶部。

2026-10-26 P9 LiteRT-LM Task 3 最终本地门禁：cache 删除失败、预算、空结果、availability/close join 的新增回归进入聚焦 Gradle 构建，**BUILD SUCCESSFUL**（548 tasks；模块 JVM **22/22**、probe host **9/9**、Python AAR 工具 **7/7**）。真实 AAR 只读检查通过；新增 cleanup/query 改动的独立复审因 `429` 尚无结论。模型资产、真实 callback/drain、生产 adapter/consumer 与设备推理仍 **unverified**；继续 **5/12、P9 active**。详见 [移动端 VLM](validation/mobile-vlm.md) 与 handoff 顶部。

2026-10-26 P9 LiteRT-LM Task 3 续接：可选 Kotlin backend 新增 cache 删除失败后禁止重入的 RED→GREEN 回归，补齐 512 token / 16 MiB 预算与空输出测试，并用可控 availability 查询验证 close join。当前只证明 host fake 生命周期，真实模型 callback/drain、生产 adapter/consumer 和 Android 设备推理仍未验证；继续 **5/12、P9 active**。最新验证范围见 [移动端 VLM](validation/mobile-vlm.md) 与 handoff 顶部。

2026-10-26 P9 LiteRT-LM fake 生命周期续接：可选 Kotlin 模块补上 image-only 内部 session/backend seam；本地 fake 的正常 cleanup、取消、reentry、poisoned terminal 资源保留回归已通过。TECNO 真机显式无模型 probe 仅返回 `unverified-missing-app-private-assets`；临时 Maven 仓库使用已核验 AAR/POM 后，可选库 AAR 打包、lint、聚焦 JVM/probe build 通过，默认 Demo 依赖图仍无 LiteRT-LM。真实模型 callback/native drain、生产 adapter/consumer、设备生成和视频均未验证，仍 **5/12、P9 active**；详见 [移动端 VLM](validation/mobile-vlm.md) 与 handoff 顶部。

2026-10-26 P9 LiteRT-LM 实施部分检查点：真实 0.18.1 AAR 静态门禁已通过；新增 Kotlin probe 和可选 `android/backend-litertlm` 模块骨架。TECNO KM9/API35 上真实 AAR content mapping instrumentation **1/1 passed**；模块固定 model spec、manifest/路径/版本校验、ModelStore 短 lease availability helper、JVM tests 与 lint 通过。原始日志见 `artifacts/verification/android-litertlm-probe-20261026/`，设计/计划/边界见 [移动端 VLM](validation/mobile-vlm.md)。模型资产未冻结，generation、callback 语义、cancel→native drain、生产 adapter、consumer 与 Android VLM/video 设备推理均 **unverified**；仍 **5/12、P9 active**。

2026-10-26 P9 Android 生产后端路线复核（历史）：依据 LiteRT-LM 官方 Android 文档，下一适配候选固定为 `com.google.ai.edge.litertlm:litertlm-android`；Google Maven 元数据只读查询得到最新 **0.18.1**，POM 为 Apache-2.0 并声明 Gson 2.14.0、Kotlin reflect 2.4.0、coroutines-android 1.11.0。该轮代理无法拉取 AAR 本体，因此当时 `minSdk`/ABI/native library 未验证；后续真实 AAR 门禁与本轮 Kotlin probe 证据见上方及 [移动端 VLM](validation/mobile-vlm.md)。LiteRT-LM 的 Android/Kotlin 流式图像 API 不承诺视频语义，项目暂保持 `supportsVideo=false`。Android 生产 VLM/video backend 与真实设备推理仍 **unverified**，Roadmap 保持 **5/12、P9 active**。

2026-10-26 P9 Android 导入中后台/回前台回归：TECNO KM9 / Android 15 / API35（`<Android-serial>`）上的 Kotlin-only `MultimodalPickerInstrumentation` 新增 slow fixture 生命周期场景；在 Activity-owned import active 且输入尚未发布时执行 `moveTaskToBack(true)`，回前台后 `backgroundMidCopy=passed-import-retained-after-background-return`，同次 bundle `status=passed`、exit 0。原始证据见 `artifacts/verification/multimodal-android-lifecycle-20261026/device-picker.log` / `.exit`；本地 unit/APK/test APK/lint `BUILD SUCCESSFUL`，见 `build-rerun.log` / `.exit`。该证据只验证导入生命周期，不代表 Android 生产 VLM/video backend、自然质量或资源验收。Roadmap 保持 **5/12、P9 active**。

2026-10-26 P9 Android DocumentsUI 真机选择收口：当前 dirty source 的 Kotlin-only test APK 以 `OpenDocument` 打开真实 DocumentsUI，TECNO KM9 / Android 15 / API35（`<Android-serial>`）通过 test APK 自有 `MultimodalFixtureDocumentsProvider` 选择公开固定 `real-system.png`，随后回到 Demo 完成异步导入与清除；instrumentation `status=passed`，`actualSelectedSystemFile=passed-documents-provider-real-picker`，证据为 `artifacts/verification/multimodal-android-cleanup-20261026/device-picker-real-documentsui-rerun2.log` / `.exit`。该证据不读取私人媒体，也不代表 Android 生产 VLM/video backend、自然质量或资源验收；`inference=unverified-no-android-backend`。Roadmap 保持 **5/12、P9 active**。

2026-10-26 P9 双真机 picker 收口与临时诊断清理：当前 dirty source 已移除 iOS 真实 picker 诊断专用 fixture seed、Documents logger 和 UI debug print；fixture 改由验证 artifact 外部复制。物理 iPhone `<iPhone-UDID>` 直接执行当前 source 的 Files picker 测试 **1/0/0**，Android TECNO KM9/API35 的 Kotlin-only instrumentation 最终 `status=passed`，本地 Android unit/APK/testAPK/lint 也 **BUILD SUCCESSFUL**。Android 首次真机崩溃已定位为 test APK 独立进程缺 Kotlin `Intrinsics`，补 `androidTest` `-Xno-param-assertions` 后复跑通过；真实 DocumentsUI/test-provider 选择的后续证据见上方最新条目、[Demo 验证](validation/multimodal-video-demo.md) 和 handoff。以上只收口 picker/UI 与生命周期门禁，Android 生产 backend、自然质量/GPU/energy、minimum OS、binary archive、独立全范围 review 及其他 roadmap 缺口仍未完成。Roadmap 保持 **5/12、P9 active**。

2026-10-26 CoreDevice 外部门槛复核：物理 iPhone `<iPhone-UDID>` 未进行解锁、镜像或 UI 操作；`xcrun devicectl list devices` 当前直接返回 `Timed out waiting for CoreDeviceService to fully initialize`。不启动真机 XCTest，也不把该外部失败归因于源码；用户本人解锁且 CoreDevice 恢复后继续真实 picker `1/0/0`。Roadmap 保持 **5/12、P9 active**。

2026-10-25 P9 Android 模拟器 Kotlin picker/in-flight 回归：API 37 `Medium_Phone`（`emulator-5554`）上，当前 dirty source 的 app/test APK 与 Kotlin-only `MultimodalPickerInstrumentation` 直接运行退出码 `0`、bundle `status=passed`；系统 picker 取消、Activity-owned mid-copy、busy reselection、合成回调/clear、前后台及目录清理均通过。原始证据见 [Demo 验证](validation/multimodal-video-demo.md) 与 `artifacts/verification/multimodal-android-emulator-20261025/`。`actualSelectedSystemFile` 仍为 `unverified-synthetic-result-only`，`inference` 仍为 `unverified-no-android-backend`，不改变 P9 Android 生产 backend/真实文件选择/质量缺口；Roadmap 保持 **5/12、P9 active**。

2026-10-25 P9 fixture seed 诊断收口：`MultimodalHost` 的 UI-test fixture 写入改为显式 `do/catch`，失败只写受控 DEBUG 诊断；当前 dirty source 重新生成的 iOS bridge/store/factory 工程在 iOS 27.1 模拟器全套 **75/75 passed、0 failures、0 skips**。普通沙箱首轮仅受 CoreSimulator/SwiftPM 缓存权限阻断，固定缓存 escalated 重跑通过。物理 iPhone picker 仍需用户本人解锁取得 `1/0/0`，Roadmap 保持 **5/12、P9 active**。

2026-10-25 P9 模拟器 Files picker 回归：当前 dirty source 的生成 Demo 在 iOS 27.1 `iPhone Duo` 上，使用 artifact-only 文件共享 plist 和公开 `fixture.png`，真实系统 picker/UI 流程 **1/1 passed、0 failures、0 skips**；覆盖进入“我的 iPhone”、点击真实 fixture Cell、异步导入、二次取消保留输入和清除。证据见 [Demo 验证](validation/multimodal-video-demo.md) 与 handoff。该结果只覆盖模拟器，不替代物理触摸/真机系统文件选择；真机 `1/0/0` 仍未验，Roadmap 仍 **5/12、P9 active**。

2026-10-25 P9 picker退场竞态续接：当前 dirty source 的 `MultimodalFilePicker.finish`/host 后台竞态已完成 RED→GREEN 回归；从修复后源码重新签名 `build-for-testing` 成功。物理 iPhone `<iPhone-UDID>` 恢复到 `available (paired)` 后，新包 `test-without-building` 仍 exit70；lock-state 直接返回 `CoreDeviceError 10003` / `RemotePairingError 1016`（设备近期未解锁）。全程未使用镜像。新包与原始阻断证据见 [视频 Demo 验证](validation/multimodal-video-demo.md) 和 handoff；设备解锁后继续真实 picker `1/0/0` 验收，P9 状态暂不升级。

2026-10-25 P9 自然图真机 smoke evidence 续接：固定 `mlx.smolvlm2-500m` 在已授权物理 iPhone 16 Pro Max / iOS27.0.1 上对公开自然视频的 `frame-0`、`frame-1`、`frame-18` 三帧均 `status=success`；原始报告、输入 hash、3 样本首 token/total/RSS 汇总见 [移动端 VLM](validation/mobile-vlm.md)。这补齐自然输入的真机样本与输出证据，但没有 ground truth/独立人工质量裁定，`naturalImageQuality` 继续 `unverified`，P9/P11 不收口。

2026-10-25 P9/P5 资源观测续接：授权 iPhone 16 Pro Max / iOS27.0.1 上，固定公开 fixture 的 MLXVLM probe 成功（`status=success`、首 token 490.557542ms、总计 997.558208ms、进程常驻峰值 1,170,046,976B）。同步录制 15.747300s `Metal System Trace`，筛得 VLMProbe PID23718 的 405 条 GPU interval（399 Compute、6 CoreImage），设备 A18 Pro；原始 trace 和筛选副本见 [mobile-vlm](validation/mobile-vlm.md)。这只证明真实 GPU 执行活动被捕获，不填 Metal GPU peak/energy，也不升级自然质量或完整 P9/P5 通过；导出有 dylib-overlap timeline warning，需保留原始 trace并按部分证据处理。

2026-10-24 P10 scoped review checkpoint：冻结范围 `ecdfd528..bb895161` 已完成独立 code-reviewer 的 98 文件只读审查和 architect scoped architecture review，CRITICAL/HIGH/MEDIUM/LOW 功能或安全 finding 均为 0；Apple/Android identity tests（313/313、319/319）及 distribution/tool tests（14/14）均通过。Android review 仍因 `429 Too Many Requests` 不可用，结论为 `SCOPED_CODE_AND_ARCHITECTURE_REVIEW_COMPLETE_PRODUCT_GATES_WATCH`，不能计为最终全 roadmap APPROVE。Task 9/10 生产模型/PAD、授权数据/校准和 P9/P11 缺口仍未完成。

2026-10-24 10:10 P9 Demo最新检查点：shared typed video输入/分派/UI、双端独立store/host/picker及32MiB复制已本地实现、未提交。shared64/64、Android Demo29/29、Swift DemoABI59/59；基础iOS simulator与已有签名真机build通过，iosArm64框架已刷新，iPhone16ProMax/iOS27.0.1安装启动成功（仅启动，不是视频推理）。TECNO KM9/Android15 API35实际shared UI instrumentation通过，新增视频选择/错provider隔离/unavailable/清除/清理；初次旧文案断言失败保留。详见[Demo验证](validation/multimodal-video-demo.md)。下一优先异步host staging及导入取消、增强MLX编译，再固定视频KMP权重真机链路；仍5/12/P9active，iOS15/API26/生产Androidbackend/独立review等缺口不变。

2026-10-24 P9 KMP视频bridge完成：optional typed video DTO/interface与双平台adapter，独立videoInputs默认拒绝、共用native client/lease清理。common24/24，Android模块48/48、完整Android725tasks/465JUnit，SwiftBinding23/23、Demo55/55及旧VideoABI6/6均0fail/skip，tools133/133。仅契约/注入client生命周期，不是KMP视频权重设备验收，Demo视频host/state/UI另包；详见[视频bridge验证](validation/multimodal-video-bridge.md)。原同步05c67d2现已非强推同步两远端、fresh SHA相等；新Android/KMP提交仍本地。仍5/12/P9active、独立reviewpending及全部旧缺口保留。下一独立sharedvideo state/UI与hoststager设计。

2026-10-24 P9 Androidvideo契约对等完成：typed request/可选video backend/client共享Operation已实现，新增13+原22项=35/35；offline模块lint/KMPAndroidcompile通过，完整Android725tasks/452JUnit及tools133/133。新local Maven AAR/sourceJar与独立Kotlin2.4 consumer实际编译/assemble/lint45tasks通过；初次2.2metadata失败与callback测试修正均留档。Android仍无生产推理backend，KMP/Demo视频接线另包；仍5/12/P9active、独立reviewpending及旧验收缺口保留，见[Android视频契约验证](validation/android-multimodal-video-contract.md)。用户请求的旧HEAD05c67d2双远端同步因jihu TLS/reset预检未完成、未push，后续新包仅本地。下一独立KMPtypedvideo设计。

2026-10-23 P9固定合成短视频native链路通过：`c9ea298/eebc670/30e5e8e` typed API/shared runtime/opt-in consumer后，首次设备因ModelStore大文件校验临时缓冲保留被per-process-limit终止；独立实际RED→GREEN修复`5e39cb8`保持全hash校验。真实iPhone17,2/iOS27.0.1/PID22072固定29FPS8帧权重success、首update取消/drain、同PID重入、close/lease清理及严格validator通过；clip2s与span25600/14848分列。完整Apple470/470、增强host41/41无skip、tools133/133。仅合成集成生命周期，不证明自然视频质量/Metal/energy/minOS；短实际样本仍拒绝、上游8→7失败/binary archive exit65/旧验收与独立reviewpending保留，仍5/12/P9active。下一独立Androidvideo契约对等，再另包KMP/Demo；详见[短视频验证](validation/apple-mlxvlm-short-video.md)顶部。以下为历史检查点。

2026-10-23 P9 runtime库续接：MLXVLMBackend已显式支持video入口，沿同session/lease直接使用adapter LMInput并join后清理。新增session6/metadata1项RED→GREEN，增强host41/41无skip、tools126/126、fresh generic iOS build通过；仅库host/build，视频权重设备运行尚未执行，原Task3仍未完成。下一opt-in consumer/严格validator及固定合成device生命周期；仍5/12/P9active、独立reviewpending及旧缺口保留，见[短视频验证](validation/apple-mlxvlm-short-video.md)顶部。

2026-10-23 P9 Task2续接：纯Swift typed video request/可选backend协议/client共享生命周期已实现，API RED后Multimodal22/22、完整Apple469/469、增强host34/34无skip、tools126/126、Swift6严格typecheck与新独立generic iOS公共API compile probe通过。MLXVLMBackend仍image-only，Task3 runtime未开始；下一先细化adapter直入LMInput的runtime计划。Task1短样本仍拒绝，上游8→7失败/二进制/自然质量/资源/Android真实后端/最低系统/独立review缺口保留，仍5/12/P9active。见[短视频验证](validation/apple-mlxvlm-short-video.md)顶部；以下是历史检查点。

更新时间：2026-10-23（P9真实 OS background lifecycle 14 gates通过；Demo/bridge42/42、tools123/123；物理触摸/systempicker与独立review仍待补）。GitHub为主仓，feat/sdk-foundation未合入main；本次按用户显式授权同步GitHub/GitLab功能分支，未合入main；后续恢复默认本地提交。

## 执行规则

2026-10-23 P9 profile/设备续接：`81ea4df`真实PQ/offset/2frame/reference负向4/4，HDRguard mutation RED后原源码恢复，完整host34/34、tools126/126。空App unit-test host沿用既有签名，物理iPhone16ProMax/iOS27.0.1执行snapshot/profile12/12和adapter6/6；ByteTokenizer输入准备，不是视频权重/质量/资源验收。原Package tool-hosted exit70及中间host链接/约束失败均保留；固定lock/checkout不改。短于8实际样本仍拒绝、不泛化profile。下一可独立推进纯Swift typed video contract/client，runtime视频推理仍未开始；见[短视频验证](validation/apple-mlxvlm-short-video.md)顶部。独立reviewpending、仍5/12/P9active。

2026-10-23 P9短视频内部适配续接：`5be1b57`逐帧保留29FPS全部8帧真实pixels/顺序/CMTime，不调用上游二次采样；`c9ef8ce`真实固定tokenizer2/2，无权重加载。clip2s与sample span约1.724138s分别记录；24FPS完整tokens/pixels/mask对照上游一致。最新host30/30、tools126/126、fresh generic iOS编译通过。上游`.frames`仍8→7，原失败保留；公共API/runtime/设备视频推理未开始，snapshot额外profile/iOSdecode与旧验收门槛仍待补。下一入口补snapshot profile及iOSdecode/cancel，见[短视频验证](validation/apple-mlxvlm-short-video.md)顶部和独立适配设计/计划。只本地提交、独立reviewpending，仍5/12、P9active。

2026-10-23 P9短视频Task1：内部有界snapshot及实际pinned processor characterization已实施，host22/22、tools126/126、独立consumer generic iOS编译通过。24FPS的8帧保留并完成真实prepare；29FPS的8帧在固定上游二次采样后变7帧，原始8帧门槛实际失败。因此Task2/3暂停，不接公共video API、不改上游/伪造时间戳。下一入口先设计保留真实帧/时间语义的项目侧适配，见[短视频验证](validation/apple-mlxvlm-short-video.md)；设备推理/质量/资源与旧缺口不变，仍5/12、P9active。

2026-10-23 P9短视频续接：固定模型14文件 hash/size 与12个缓存 checkout HEAD/clean 重新离线复核通过。上游有单视频 processor；`fps=1/max_frames=64` 是配置值，短片默认采样率会动态变化。项目仍无视频 API/推理证据，已完成[可行性与设计](validation/apple-mlxvlm-short-video.md)。下一 Task 先执行有界 snapshot 与固定 processor 帧/时间语义 characterization；通过后才接 typed API、Apple backend 与独立consumer。仍5/12、P9 active，binary与旧验收缺口保留。

2026-10-23 P9分发续接：`b8acaa3` 新增可搬移source consumer（122个tracked package文件、相对引用、固定lock/资源hash）；搬移后generic iOS离线编译通过，tools126/126。增强Release/library-evolution archive 在本机Swift6.4验证固定swift-collections1.7.3的OrderedCollections.swiftinterface失败（exit65），binary未通过，不复用基础十XCFramework证据。见[分发验证](validation/apple-mlxvlm-distribution.md)。独立review及既有缺口保留，仍5/12、P9active；本包仅本地提交。

用户最新要求是**继续推进，直到 roadmap 完成**。按下表工作包限定输入、改动范围与验证，逐项实施、复审和提交后自动进入下一项。设备、凭据、商用模型许可与校准缺口分别保留，不能把局部测试通过视为完整交付。原十项 foundation 计划保留为历史细节。

2026-10-21 接手更新：P6 Demo 语言路由补齐请求级 admission，避免默认英语不可用阻断已就绪的中文。真实 client 回归先失败再通过，Host/bridge/session39/39、native iOS fixture43/43及普通真机包构建/装机通过；完整恢复门禁Swift414/414、工具77/77和Android569tasks通过。尚无中文实录终态与真实取消/重入完成证据，独立复审仍因模型容量不足不可用，P6/roadmap保持未完成，未同步远端。恢复入口见[交接检查点](HANDOFF-2026-10-20-speech-demo-bridge.md)。

2026-10-21 P7 续接：已提交 Swift 视频契约/client（`17a330e`、`fd2e537`）和 Android 对等模块（`112634d`），本地分发已验证，独立审查因服务容量失败保留pending。Apple VideoToolbox 后端现已完成本地实施、九XCFramework及固定fixture设备验证，有界AAC音轨preserve已完成并通过设备验证，KMP Video bridge/Demo Task1–5 已提交、本地门禁通过，Task6 iOS UI 因自动化初始化超时待补；Android unavailable/picker取消/空闲前后台 smoke通过。当前验收入口为[视频 Demo 验证](validation/video-bridge-demo.md)；Video SDK Foundation/AppleVideo/bridge 独立审查待恢复；P6 受控录音等待真实终态期间继续可独立推进的 P7。基础契约本身不代表实际超分，新增固定profile后端也不代表 P7 完成，见[音轨验证](validation/apple-video-audio.md)、[AppleVideo验证](validation/apple-video-backend.md)、[基础验证](validation/video-sdk-foundation.md)与[实施计划](superpowers/plans/2026-10-21-video-sdk-foundation.md)。

2026-10-23 P9当前入口：增强Demo真实 OS background lifecycle 14 gates通过，source d50eff1、PID21398；项目-owned companion 触发真实四通知，RUNNING update→CANCELLED无output，恢复后不自动重启，同PID重入成功。见[OS后台验证](validation/multimodal-demo-os-background.md)。物理touch/真实systempicker/独立review仍未验，不关闭P9或提升5/12。

2026-10-22 P9程序化host历史入口：独立consumer中程序化KMP host/VM→真实MLXVLM设备生命周期已通过，source2868bee，iPhone16ProMax/iOS27.0.1同PID20852，10 gates/5真实请求；成功/首update取消/drain/重入/host后台不重启/close/input与modellease/关闭拒绝。工具112/112，signeddevicebuild与严格rawvalidator通过。见[真机host/VM验证](validation/multimodal-demo-device.md)。下一独立包为共享Compose页面与增强Demo集成验收；实际UI/OS后台/systempicker仍未验，不提升5/12或关闭P9。

2026-10-22 P9可选host历史入口：Apple固定模型/ModelStore可选Demo host包本地完成（design30e691b、factory5afb045及后续Task2）；Demo33/33、工具104/104、默认simulator与增强iOS18 generic device unsigned构建通过，default/Android仍unavailable。见[可选MLX host验证](validation/multimodal-demo-mlx-host.md)。下一独立包为KMP页面→真实MLXVLM设备成功/update取消/drain/重入/后台/close/lease闭环，iOS实际触摸与system picker仍未验；不关闭P9或提升5/12计数。

2026-10-22 P9 host历史入口：双端native MultimodalHost生命周期包本地完成；设计cc99556、Android854d256、Apple与本检查点同次提交。新host各7/7、Android全Demo21/21、Apple Demo21/21/Binding10/10、工具101/101及APK/lint通过。见[host验证](validation/multimodal-demo-host.md)。下一独立包为system picker/shared action控件/平台opt-in及session接线；Apple固定模型与真实KMP设备闭环继续另包，Android默认unavailable。iOS实际UI/minimumOS/独立review仍待补，P9不关闭。

2026-10-22 P9输入历史入口：双端opaque input/lease输入所有权本地包已完成，设计0b0f851、Apple caab202、Android与验证本检查点同次提交。私有副本、opaque UUID、remove/close封闭admission、独立幂等lease及foreign-file保护通过实际文件测试；新测试暴露并修复旧SpeechInputStore递归删除目录缺陷，Speech39/Video9回归通过。见[输入验证](validation/multimodal-demo-input.md)。下一独立包为两端host/picker与页面生命周期；Apple固定模型/ModelStore与KMP真实设备闭环随后另包，Android保持明确unavailable。不关闭P9，不提升5/12计数。

2026-10-22 P9 UI历史入口：shared单图Screen/UI与opt-in入口本地实现98a3891；默认App不自动出现新页，提供model才可见。Android实际shared页面全部具名场景通过（含unavailable、原始字段、取消/update/身份及旧Speech/Video切页），shared60/60、Swift旧/新入口typecheck、含原host的独立iOS Demo build、更新Demo ABI10/10、工具101/101及Android lint通过。见[UI验证](validation/multimodal-demo-ui.md)。下一独立包为双端host/picker与opaque input/lease生命周期，Apple固定模型/ModelStore与KMP设备闭环随后分包。iOS UI、真实推理和独立review仍未验；不关闭P9或提升5/12阶段计数。

2026-10-22 P9状态机历史续接：bridge三Task（2e96a26/b1e6873/1291bd2）之后，单图Demo共享状态机已提交5db0083，默认UnavailableBridge，UI/导航/host未接入。新VM12/12、全shared60/60、更新后Demo ABI bridge10/10、Android Demo APK/lint（0 error/9条既有warning）、工具101/101通过；[状态机验证](validation/multimodal-demo-state.md)、[设计](superpowers/specs/2026-10-22-multimodal-demo-state-design.md)、[计划](superpowers/plans/2026-10-22-multimodal-demo-state.md)。下一独立包为shared单图Screen/UI与opt-in入口，host/固定模型/picker/设备随后另包；短视频、Android真实推理仍分包。独立审查pending，整个P9未关闭。

2026-10-22 bridge历史证据：common21/21、Android bridge10/native12、Video17/Speech25回归、Apple Binding/Demo各10/10、工具101/101；真实native client+注入fixture，不是KMP→MLXVLM真机推理。专属入口前缀避免旧ABI selector重编号，旧Video6/6通过。见[bridge验证](validation/multimodal-bridge.md)。

2026-10-22 P9 benchmark历史证据：源提交f9512a0，iPhone16ProMax/iOS27 Debug、1预热+5测量：加载p50/p95=311.09/323.69ms、首交付update=763.80/774.15ms、总耗时=924.39/934.32ms；process RSS采样peak=2,437,054,464B（非Metal/GPU）。合成red+square rubric5/5，额外“flag”语义保留，不能算自然质量通过。原始证据见[后端验证](validation/apple-mlxvlm-backend.md)。本bridge包不重跑或提升这些旧证据等级。

2026-10-22 P9 Foundation历史续接：Multimodal SDK Foundation本地四Task完成：cf3a0e6 Swift、8476dd8 Android、e1ff006分发。双端纯契约/client、十XCFramework、Core+Multimodal-only iOS15 device/simulator编译链接及Android Maven真实消费通过；[验证](validation/multimodal-sdk-foundation.md)、[设计](superpowers/specs/2026-10-22-multimodal-sdk-foundation-design.md)、[计划](superpowers/plans/2026-10-22-multimodal-sdk-foundation.md)。下一独立入口为可选Apple MLXVLM后端，先复核固定revision、已有资产与加载/lease/取消边界，不下载、不将probe直接当SDK。P9/roadmap未关闭，Video UI/独立审查及P6实录缺口保留。

- 继续时先读本文件、验收记录和 git 状态，选择当前未完成工作包；不重复已验证工作。
- 工作包发现跨范围缺陷时，记录为后续项；本包无法成立的阻塞才在包内修复。
- 已授权直接使用本地真机做 device-only 验证；仅操作本项目 App，保留设备其他 App/资料。使用已有签名配置，不自行创建或变更证书、描述文件等账户资产。
- 测试/模拟器/真机/最低系统验证分别记录；构建通过不代表真机已通过。
- 不逐项重新请求继续许可。每个包记录可复核提交、验证和剩余项，再推进下一个可执行包。

## 当前范围与后续功能

当前 KMP Demo 已有**人脸检测与关键点**入口，以及独立的身份/活体控件与宿主配置入口；原生 SDK 已接入系统 TTS、文件 ASR 与 iOS 麦克风 ASR；新麦克风路径已实现并通过本地 fixture，实际录音未验。语音工作包已完成公共契约、能力级后端查询、双端 adapter、iOS 文件 lease、共享状态机/语音页面，以及两端宿主接线、权限与独立 iOS 工程；Task 7 的现代真机闭环已通过，追加 UI/Done 独立审查已 APPROVE；不能视为 P6 已完整收口。VLM 与视频超分的独立探针状态见下表。当前尚不提供生产身份识别、1:1 比对、1:N 搜索或活体认证。已实现的 Core、任务生命周期、最新帧队列、ModelStore 和分发工程是后续能力共用的基础设施；P10 已实现独立身份存储、授权生命周期、类型化 bridge 与可裁剪分发；生产算法门槛仍待通过。

| 后续能力 | SDK 与 Demo 预计交付 | 对应阶段 |
| --- | --- | --- |
| ASR / TTS | 原生识别/合成接口、取消与音频生命周期；KMP Demo 的语音输入/播放与能力状态 | P6 调研和单后端探针，随后按独立工作包完成 SDK/Demo 接入 |
| 视频超分 | 视频处理接口、进度/取消、输出文件；Demo 输入/输出与质量/性能对比 | P7 调研和单链路探针，随后完成选定后端的 SDK/Demo 接入 |
| 图像理解 / 图生文 / 多模态 | 模型会话、生成/取消、结果展示；按模型支持范围增加输入类型 | P8 比较 SmolVLM2、Florence-2、MiniCPM-V 等，P9 先验证单模型/单平台，再分包接入 SDK/Demo 及另一平台 |
| 模型管理与按需加载 | 模型下载/校验/安装/删除、运行时加载/卸载、版本与资源状态；Demo 提供相应流程 | 现有 ModelStore 基础上，随首个自有模型接入补齐真实链路 |
| 后端与性能比较 | 按能力查询可用性、明确实际后端；模型/后端选择和 benchmark 展示 | P5 建立验收基线，各能力接入时补充样本与指标，P11 统一验收 |
| 人脸身份与活体 | 1:1、1:N、活体分别设计接口和数据生命周期，再增加 Demo 流程 | P10 已完成原生契约/存储/引擎、bridge/Demo 与独立本地分发；冻结范围 code/architecture scoped review 已完成且无 finding，Android 专项 review 待恢复；生产模型/PAD/校准待定 |

这些仍是项目后续目标。P6/P7/P8/P9 的“调研/探针”不是完整功能交付；通过许可证、转换/运行及设备资源验证后，再为选定方案创建具体实现包。Core ML、MLX、LiteRT/TensorFlow Lite、ONNX、llama.cpp 是待按能力选择的后端，不要求所有模型在所有框架/平台重复实现。iOS15 保持基础下限，高版本能力按 availability 或独立增强模块接入。

## 等待设备的 TODO

2026-10-16 用户确认暂时没有 iOS15 真机；随后提供 TECNO KM9（Android 15/API 35）用于现代 Android 验证。以下最低系统验收显式挂起，等对应设备可用后恢复；期间不重复要求设备，也不将这些缺口作为其他可独立工作包的阻塞。

- [ ] **DEVICE-IOS15**：取得 iOS15 真机后，验证基础宿主启动、权限、照片/相机、旋转/镜像、取消、前后台和离线推理；验证可选高版本后端不会破坏基础宿主启动。
- [ ] **DEVICE-ANDROID-API26**：取得 API26 真机后，验证最低系统启动及 ML Kit 推理。当前 API35 设备已完成相机冒烟与流式 UI 修复验证；照片、权限、旋转/镜像、取消、前后台和离线完整行为仍在 P4 范围内。

状态继续记为未验证，不以模拟器/编译结果代替真机证据。验收行见 [设备矩阵](validation/device-matrix.md)。

## 当前阶段与独立工作包

按原始停止点，12 个基础里程碑中 **10 个已完成（P0–P3、P5–P10），2 个尚未完成（P4、P11）**。下表状态同时保留后续扩展交付的未完成项；完成调研、探针或设计不等于对应生产能力已交付。主要剩余工作为：P4 交互/资源释放与最低系统验收，P5 后续资源指标，P6 麦克风实录/Android 转写/离线与质量验证，P7 超分 Android/质量及相应审查，P9 多模态 Android 生产后端、模型资产与设备验收，P10 生产模型/PAD/授权数据/校准及独立审查，以及 P11 统一验收和发布结论。最低系统设备缺口独立挂起，不阻断可独立推进的实现。

| 包 | 交付与边界 | 验证/停止点 | 当前状态 |
| --- | --- | --- | --- |
| P0 当前快照收口 | 阶段表、验收证据、同步现有代码；不新增算法 | 当前检查结果落盘，两端分支 SHA 相等 | 已收口，随本次提交同步 |
| P1 SDK 生命周期审查 | 仅 TaskGate、LatestFrameQueue、Vision/MLKit 取消与 close 的资源所有权；不改 Demo UI/安装器 | 独立审查、必要问题 RED→GREEN、对应测试通过；提交后停止 | 已完成，见 P1 审查记录 |
| P2 模型存储审查 | 仅 manifest、原子安装、下载取消/合并、lease；不接新模型 | 路径/损坏/中断/并发用例和两端测试；提交后停止 | 已完成，见 [P2 审查](validation/p2-model-store-review.md) |
| P3 iOS 真机最小闭环 | 发现设备/已有签名，安装本项目 Demo，合成空白输入→Vision，导出结果；不加入 VLM | 记录设备 OS、后端、成功空集合或真实错误；无可用签名则记录精确阻塞；停止 | 已完成，见 [P3 真机结果](validation/p3-ios-device.md) |
| P4 Demo 交互闭环 | 照片、相机、旋转/镜像、取消、前后台；原生桥接资源释放 | 每项可复现结果；无相应设备时保持缺口；提交后停止 | 部分完成：iOS 相机/前后台、照片选择/取消、两分钟持续相机 UI 与 Activity Monitor/thermal 抽样、Settings 恢复入口及用户手动权限往返（恢复后相机/Vision 自动化 1/1），以及 Android API35 相机、选图取消、Orientation 6、权限拒绝/重新授权和两分钟相机 PSS/热状态抽样已验；新增 iOS Debug Orientation 6 合成图共用 ImageIO 解码→Vision，真机结果 80×160，见 [P4 记录](validation/p4-camera-stability.md)。完整 PHPicker EXIF 2/6/7、资源释放及最低系统仍待验收；Leaks detail 已导出：相机路径复现3个GrOp/384B，静态Compose对照1个/128B，纯原生consumer采样0行；渲染责任边界与完整释放仍未验 |
| P5 benchmark 与分发验收 | 固定输入 5 次预热+30 次采样、JSON→报告；Maven/XCFramework 独立消费者 | 真机样本与分组报告可复核；未测项为 null；停止 | 原始 benchmark/分发里程碑已完成；扩展资源与最低系统验收未完成：冻结提交 `927b7749` 后，iOS27 和 Android API35 均重新完成 5+30 样本；四个 Apple XCFramework（补全现有 Client 的 target 源文件）、四个 Android 人脸 Maven artifact 和显式后端独立消费者均通过；最低系统和峰值内存/能耗测量仍待补。语音 artifact 分发已在 P6 独立验证 |
| P6 语音调研与小探针 | 独立对比系统 ASR/TTS、离线资源、iOS26 可选能力；已有后端按独立工作包接入 SDK/Demo | 原生探针与 Demo 验收分别记录；未测项继续保留 | 原始调研/最小探针里程碑已完成；扩展 SDK/Demo 验收仍在进行：两端系统 TTS SDK 已完成现代真机成功/取消/关闭验证；Android on-device ASR 模块与严格选择已实现，TECNO KM9 的 en-US 返回 MISSING_ASSETS，真实转写和设备生命周期仍未通过。Apple 文件 ASR SDK `5fd0b2d` 已通过强制 on-device 固定文件转写、活动取消、drain、输入保持、重入及关闭后拒绝，独立架构审查 CLEAR（一个 LOW 延后）；Apple/Android 原生语音分发及消费者依赖裁剪已有通过证据。2026-10-20 语音 bridge/Demo 工作包 Task 1–6 已完成，Task 6 提交 `b272b76`：公共契约、能力级查询、Android/iOS adapter、文件 lease、共享 VM/UI、双端 host/权限与独立 iOS 工程均已实现；Task 7 的 Demo 真机/裁剪门禁已通过；追加 UI/Done 独立审查已于2026-10-21 APPROVE。iOS 两种 ABI 各 14/14、最新 iOS Host20/20、Android Host5/5、工具76/76，Android APK 与独立生成工程构建通过；Task7本地验收/独立审查现已收口；P6其他缺口保留。iOS 麦克风契约/client/capture/bridge/host/session 已于 `5be8a76..d60867c` 本地提交；Swift412项、生成桥接/host/session35项、工具77项、Apple/Android门禁、fresh七XCFramework和Speech裁剪通过，Task1–5独立APPROVE、native simulator39/39。受控真机录音仍待验；iOS26资源、Android ASR真实转写、两端断网/自然语音质量及最低系统继续保留，见[麦克风记录](validation/ios-microphone-asr.md) |
| P7 视频超分调研与小探针 | iOS26 系统能力、CoreML、Android ONNX/LiteRT；移动端约束 | 先完成 video-sr-backends.md，再做一条固定视频链路；独立停止 | 原始调研/固定链路里程碑已完成；扩展 SDK/Demo/Android/质量验收仍在进行：iOS 27 真机确认 VideoToolbox 系统 SR 支持 4×并完成 48 帧固定视频链路；已记录内存采样、统一色彩/HEVC 编码的 SR/双三次对照和自然视频真机质量配对（两路均 48/48 帧、BT.709 元数据一致，质量仍为 unverified）；双路流程在 SR 阶段和基线阶段均验证取消无残留输出，重启后两路再处理成功，随后两次取消后同进程重入也已通过（PID 不变、两路各 48 帧）。双端 Video SDK Foundation 已实现纯契约、严格后端选择、取消/drain、输出 lease 与 close/query join，Swift12项、Android7项及本地八XCFramework/Core+Video消费者验证通过，独立审查因服务容量失败待恢复；可选 Apple VideoToolbox SDK 后端已接入（`e996d5d`，取消竞态修复 `51d98bf`），九XCFramework、Core+Video独立消费者和设备48帧成功/取消无残留/同进程重入通过，仍只支持固定SDR profile，现支持默认preserve（无音轨或单条零起点等长AAC）与discard，设备音频payload相同、无音轨默认请求、取消/重入通过；KMP bridge、共享 VM/UI 和双端 host 已实现（`9bad71d..3b0560c`），本地 Apple/Android 门禁通过；Android unavailable/选择器取消/空闲前后台 smoke通过；iOS Video Demo UI 已在物理 iPhone 上通过 XCTest 成功/取消两条 opt-in 用例（`video-success7.xcresult`、`video-cancel1.xcresult`，均 1/1 passed），覆盖真实 Files picker、48 帧带单音轨输出、分享/播放/清理和取消/重入/回前台不自动重启。人工物理触摸、独立审查、Android模型、SR模型传递函数、主观质量与最低系统仍待补，见[视频 Demo 验证](validation/video-bridge-demo.md)、[音轨验证](validation/apple-video-audio.md)、[AppleVideo验证](validation/apple-video-backend.md)、[基础验证](validation/video-sdk-foundation.md)和 [P7 记录](validation/video-sr-backends.md) |
| P8 VLM 调研 | SmolVLM2/huggingsnap、Florence-2、MiniCPM-V及其他指定 checkpoint | mobile-vlm.md 记录许可、平台、体积、内存、证据等级；不同时接入多个模型 | 已完成候选调研与 P9 单路线选择，见 [P8 记录](validation/mobile-vlm.md)；移动端实测归 P9 |
| P9 VLM 单模型试验 | 根据 P8 选择一个 checkpoint、一个平台、一个 runtime | 固定图像→输出→资源/耗时原始证据；随后另包推进第二平台 | 原始单模型/单平台试验里程碑已完成；以下 SDK/Demo/第二平台及产品验收继续跟踪。单平台探针、双端Multimodal契约/client及十XCFramework/Maven裁剪分发已有证据。独立Apple MLXVLM SDK后端、ModelStore lease及固定单图设备生成/取消/重入/close已通过；固定单图新增真实 iPhone 1 warm-up + 30 samples（load/首update/total p50/p95、进程RSS、30/30合成rubric）并保留旧1+5兼容入口，原始 JSON/汇总见 `mobile-vlm.md`；单图KMP common/双端adapter通过注入native client测试、两种Apple ABI各10/10；shared Demo状态机新12/12、全shared60/60已通过，shared单图UI/opt-in及Android fixture页面交互已验证，双端opaque input/lease基础已完成并验证（Apple Demo14/14、Android新4/4），native host所有者与注入选择生命周期通过双端各7/7；shared选择/清除/离页动作及Android opt-in系统picker/IO staging与独立生成iOS picker/host接线已完成：Android API35系统取消及合成content回调/清理通过，全Demo26/26，Apple Demo24/24、工具102/102、完整生成App编译通过；iOS物理触摸仍未验；iOS 真机 XCTest Files picker 已取得 1/0/0，Android API35 真实 DocumentsUI/test-provider 选择也已通过；程序化host/VM10gates与增强Demo bootstrap/共享AX真实生成9gates现均通过（source2868bee/51d3b72），Android Activity-owned 导入中后台/回前台已验，共享活动取消及其他 OS/后台场景仍待补。仅合成rubric5/5，不证明自然质量；Apple固定模型host可选接线已完成（Demo33/33、tools104/104、增强离线编译），短视频、共享页面活动取消/真实系统交互、Metal/GPU资源、Android真推理、最低系统与独立审查待补，见[增强Demo集成验证](validation/multimodal-demo-integration.md)、[真机host/VM验证](validation/multimodal-demo-device.md)、[可选MLX host验证](validation/multimodal-demo-mlx-host.md)、[picker验证](validation/multimodal-demo-picker.md)、[状态机验证](validation/multimodal-demo-state.md)、[bridge验证](validation/multimodal-bridge.md)、[后端验证](validation/apple-mlxvlm-backend.md) |
| P10 人脸身份/活体设计 | 1:1、1:N、活体分别设计数据与指标 | [身份与活体规格](superpowers/specs/2026-10-17-face-identity-liveness-design.md)；阈值、授权删除与特征生命周期；独立停止 | 原始设计里程碑已完成；后续实施与产品验收仍在进行。书面规格已获用户认可；含 12 个工作包的[实施计划](superpowers/plans/2026-10-17-face-identity-liveness.md)已确认实施。实施前核对已修正 selection 命名冲突、Task 4 backend/policy 先于存储的依赖顺序和 codec/授权边界；[模型许可预研](validation/p10-identity-model-selection.md)已有固定上游 revision 的证据，未选模型、未下载权重。Task 1–8 契约、加密存储/删除、gallery、1:1/1:N、活体会话和模型 lease 已实现；Task 11 双端 native bridge、独立 Demo 控件与宿主配置已完成，含 Android API35 UI 冒烟及两种 iOS KMP ABI 测试；Task 12 已提交 bb89516，七个 Apple XCFramework、Identity 独立二进制 consumer 与 Maven consumer 已通过本地验收；冻结范围 code/architecture scoped review 已完成且无功能/安全 finding，Android 专项 review 因容量限制待恢复。最新 Apple371/371、Android Identity319/319、分发与工具测试见[P10 进展](validation/p10-face-identity.md)。Task 9/10 生产模型/PAD、授权数据、校准和最低系统设备仍是未通过门槛 |
| P11 SDK/Demo 最终 benchmark 验收 | 冻结候选 SDK commit、模型 revision、测试输入和设备矩阵；对届时实际交付的每项能力与已接入后端统一运行正确性、质量、延迟、资源、取消和持续运行验证 | 保留逐 run 原始 JSON；生成按设备/OS/能力/后端分组的 p50/p95 与质量报告及一页状态矩阵；逐项标记 pass/fail/unverified/unavailable，列出未达标项和发布结论；停止 | 待各能力实现与分阶段 benchmark 完成后执行 |

P1/P2 是此前未完成独立审查的拆分；二者已完成，但基础 SDK 仍需后续 P3/P4/P5 验收，不能据此声称完整工程可发布。P3 不要求先解决模拟器 Vision Code9；真机结果另行记录。iOS15 保持基础下限，高版本能力通过 availability/独立 backend 接入。

P11 是最终统一验收包，不替代 P5 或各能力接入时的基准测试。执行前为每项能力预先固定样本、质量指标和合格阈值：人脸检查检测结果与坐标；ASR 使用 CER/WER、TTS 检查语言/输出与可复核质量；视频超分检查帧数、时间戳、音轨、解码和适用画质指标；多模态使用固定任务集和可复核输出。各项记录冷启动、加载、预热、推理、峰值内存和测量方法；不能可靠测量的资源指标记为未测，不填代理值。系统与第三方后端、离线与联网路径分组展示，不合并成跨能力总分。

2026-10-17：已完成现有人脸检测 Client 的 Default/Explicit 后端注册与错误契约验证，见 [后端选择验证](validation/backend-selection.md)。这只验证统一选择入口，不代表 TTS、ASR、身份、超分或 VLM 的后端已全部实现。
同日已将选择值传递到 KMP Bridge 与两端 Demo 的人脸检测调用；目前各平台只注册一个基础后端，显式选择第二个生产后端、UI 选择器和真机验证仍待后续能力包。

设备矩阵至少区分 iOS 15 基线、较新 iOS、Android API 26 基线和较新 Android；同一能力/后端仅在实际支持的设备上执行。缺设备、权限、凭据或模型资产时标记 unverified/unavailable 并写明原因，不能以构建或模拟器结果替代真机。未实现的研究候选不进入通过率分母；已计划交付但尚未实现的能力不得标记 pass。汇总页使用可读的状态矩阵和指标摘要支持 SDK/Demo 评估，发布结论需列明阻断项及证据链接。

## 已有实现与证据

截至代码快照 `c03003f`：原生 Core、ModelStore、Face、Vision/MLKit，KMP Demo，benchmark 报告工具和本地分发工程已提交。这是开发快照，**不是完整工程验收完成或可发布版本**。

- Apple 验证脚本已通过：19 个 Swift XCTest、3 个 KMP 测试的已有报告、桥接 typecheck、原生消费者与 Demo 构建。两平台 archive 和四个 XCFramework 已生成；二进制消费者构建通过。
- Android 已有 11 个 JVM 测试报告通过；Demo/原生消费者 APK 离线构建通过。后续重试完成四模块 Maven 本地发布和 lint；当前统一检查与 Maven 消费者复核结果见 acceptance.md。
- Python benchmark/sync 测试 7/7 通过。
- iOS27 模拟器 Demo 启动成功，但 Vision 实际推理报 Code9，未生成有效 benchmark；不算成功空脸。iPhone 16 Pro Max / iOS27.0.1 的合成空白 Vision 真机闭环已通过，见 P3；iOS15 和 Android 设备仍未验证。
- 原整分支 reviewer 未返回审查结果：工具报告用量限制，不能视为审查通过。后续分 P1/P2/P4/P5 各包完成必要审查。

## 恢复时的第一步

2026-10-22 **单图UI/input/host/picker及Apple固定模型可选接线本地包已完成**：[可选MLX host验证](validation/multimodal-demo-mlx-host.md)。程序化KMP host/VM→MLXVLM设备生命周期现已通过，见[真机host/VM验证](validation/multimodal-demo-device.md)。增强Demo bootstrap与共享AX真实生成已通过，见[集成验证](validation/multimodal-demo-integration.md)。下一本地入口为共享AX首update取消/活动离页/重入，先独立设计/逐Task计划；保留default/Android unavailable。短视频/Android真backend/自然质量/Metal GPU/energy另包。旧Video自动化初始化超时、P6实录/ASR资源、最低系统与独立review缺口保留，不下载、不自动录音。

[iOS 麦克风设计](superpowers/specs/2026-10-20-ios-microphone-asr-design.md)的文件兼容、optional finish、session lease、按输入权限和drain边界均已落地。Task1–3核心、Task4桥接、Task5及回滚修复均独立APPROVE，0问题；最新native simulator39/39。旧语音包 Task7 UI/Done 独立审查已恢复APPROVE；旧包本地门禁收口。当前新麦克风Task6未验，远端最终同步门禁维持。

| 语音工作包任务 | 当前状态与证据 |
| --- | --- |
| Task 1 公共 Speech Bridge 契约 | 已完成，`6788b1b`；类型化选择、输入、结果、失败与回调，Swift ABI typecheck 通过 |
| Task 2 能力级后端查询 | 已完成，`331de92`；独立 TTS/ASR 查询、严格选择与 close 竞态测试通过 |
| Task 3 Android adapter | 已完成，`8719a17`；单音频槽、取消/drain、并发 close、输入与隐私测试，Speech 单元测试/lint/binding 编译通过 |
| Task 4 iOS adapter 与输入 lease | 已完成，`7605511`；Binding/Demo 两种 ABI XCTest 各 14/14，生成器 5/5；受保护文件 hash 未变化 |
| Task 5 共享状态机与语音页面 | 已完成，`233873e`；Speech VM 10/10、Face 5/5、Identity 20/20，binding tests、Android APK 与共享模拟器 framework 构建通过；Android 真实 tags/滚动已在 Task 7 验证 |
| Task 6 两端 host/权限/独立工程 | 已完成，`b272b76`；iOS picker/Speech 授权、Android 麦克风权限/TTS 初始化、双端生命周期与独立工程构建；Task 7 补引用环、权限重试与 factory 分类回归 |
| Task 7 真机与裁剪验收 | 已完成本包本地验收；Android TTS 完成/取消/重入/后台门禁通过，ASR 仍 MISSING_ASSETS；Apple/Android 本地验证、76 项工具测试、七个 XCFramework、三个 Apple consumer 与 Maven 消费者通过；iOS TTS与文件ASR完整生命周期通过，全套UI5/5、零跳过（141.919秒）；普通无分享配置已恢复；历史焦点失败为陈旧AX坐标/滚动惯性。新UI/Done追加独立审查已APPROVE/0issues |

旧包七个任务的实现、本地验收和独立审查均已收口；三项Host问题修复后定向CLEAR，最终UI/Done追加审查也已独立APPROVE。Android MISSING_ASSETS明确不计转写通过。P6的麦克风真机录音、Android真实转写、离线/质量和最低系统缺口仍不能标通过，路线图继续active。

2026-10-19 P10 验收补充：iPhone16ProMax/iOS27 已完成注册持久化后 SIGKILL→新进程实际解密，以及持有 lease 的删除提交后 SIGKILL→新进程 tombstone/稳定回执/payload/key 清理；最终精确 cleanup=true，原语音探针已恢复。见 [进程恢复证据](validation/p10-ios-store-device-probe.md#两次-sigkill-后的持久恢复2026-10-19)。

2026-10-20 P10 验收补充：TECNO KM9/API35 已完成 Android create→SIGKILL、delete→SIGKILL、recover、cleanup 四阶段跨进程链；runner 在每个 stage 前对精确测试包 force-stop 并确认无旧 PID，controller 与校验器确认四个不同 PID/launch nonce、报告绑定、双 kill -9、tombstone/稳定回执、payload/key purge 与 run-scoped cleanup，runner 检测到的失败路径也会 best-effort 清理并留下 cleanup outcome。见 [Android 进程恢复证据](validation/p10-android-store-device-probe.md#两次-sigkill-后的跨进程持久恢复2026-10-20)。两端证据仍不证明 API26/iOS15、提交中断或文件系统故障；P10/roadmap 继续未完成。最终分支独立审查仍待取得结论。

2026-10-19 P6 历史背景（当前进度以上述 2026-10-21 入口为准）：语音 Client 的 availability/close 竞态与并发 close 等待已修复并提交 `c13ed4c`，Apple 全包374/374、Android Speech12/12、TTS3/3、ASR7/7及消费者构建通过，见[语音记录](validation/speech-backends.md)。用户已选择“先将现有后端接 Demo，再独立补齐 iOS 麦克风 ASR”；[书面规格](superpowers/specs/2026-10-19-speech-demo-bridge-design.md)已认可，[七任务计划](superpowers/plans/2026-10-19-speech-demo-bridge.md)现已执行至 Task 5 完成。麦克风、Android ASR 实际转写、离线/质量和最低系统验证继续保留。P10 最终独立审查的再次重试仍因模型容量不足退出，未产生审查结论；固定 revision 的 SFace LFS 元数据与运行时可行性研究已补入[模型预研](validation/p10-identity-model-selection.md)，未下载模型或选定运行时。

2026-10-19 当前入口：P10 Task 1–8、11、12 已实现并提交，Task12 为 `bb89516`；最终独立审查包覆盖 `ecdfd52..bb89516`，三个审查实例均因模型容量不足未启动，没有审查结论。Task9/10 生产模型/PAD、数据授权与校准继续保留。iOS Demo 真机 KMP framework 已刷新，新增身份 UI 后照片取消、方向探针、相机前后台回归3/3通过，详见[P4补充](validation/p4-camera-stability.md#身份-ui-集成后的-ios-回归2026-10-19)。下文10月17日段落为历史入口，P10当前状态以上表和[P10记录](validation/p10-face-identity.md)为准。

2026-10-17 P6 后续包：用户已批准的[Apple 文件 ASR 实施计划](superpowers/plans/2026-10-17-apple-system-asr-file.md)已实现公共契约、Apple backend 生命周期与可裁剪分发三个 Task。Swift 58/58、工具测试 44/44、Xcode 模拟器构建、全新六模块 XCFramework、binary consumer 和 Core+Speech 独立 typecheck 均通过。公共请求不绑定离线策略，Apple 系统后端单独强制 on-device；取消/超时后先 drain 再更改输入，lease 释放以平台 task completed 为边界。实际 SDK `5fd0b2d` 已在 iPhone 16 Pro Max / iOS 27.0.1 通过固定合成文件转写、busy 证明的活动任务取消、drain、输入保持、同进程重入和 close 后拒绝。最终独立架构审查 CLEAR，一个 LOW 文件 metadata 错误映射延后处理。报告/hash 见[语音后端记录](validation/speech-backends.md#2026-10-17-apple-文件-asr-sdk-后端)；既有授权被复用，麦克风、音频 session、资源安装、自然语音质量与网络行为仍未测。

恢复时以阶段表、2026-10-21 当前入口和最新 Git 为准。P1–P3 证据分别见[生命周期审查](validation/p1-lifecycle-review.md)、[模型存储审查](validation/p2-model-store-review.md)与[真机结果](validation/p3-ios-device.md)；P4 的完整 PHPicker EXIF 2/6/7、资源释放和最低系统缺口见[P4 记录](validation/p4-camera-stability.md)。P6 已有原生文件探针与 SDK 真机证据，尚不能代替本次 Demo 验收。P10 Task 1–8、11、12 和两端 SIGKILL 后存储恢复已有证据，生产模型/PAD、授权数据、校准、提交中断/文件系统故障、最低系统及最终独立审查仍未通过；本地原始草稿保持未追踪。P7 取消/重入探针结果见[P7 记录](validation/video-sr-backends.md)，Video SDK/KMP/Demo 实现已有本地证据，新的 iOS Demo UI/独立审查、Android 真实后端和质量验收仍待完成。按已批准计划推进，缺设备项独立保留。

详细设计：[SDK 设计](superpowers/specs/2026-10-15-ondevice-ai-sdk-design.md)。历史计划：[foundation](superpowers/plans/2026-10-15-ondevice-ai-kit-foundation.md)。证据与缺口：[验收](validation/acceptance.md)、[设备矩阵](validation/device-matrix.md)。
