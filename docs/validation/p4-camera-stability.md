# P4 相机闪动与前后台恢复

日期：2026-10-16。此包处理用户实际反馈的两个 Demo 问题，只完成 P4 的相机控件稳定及前后台恢复部分。

## 当前源码 Compose 相机 UI 验收（2026-10-27）

Task10并发改造后的实际Demo在iPhone16ProMax/iOS27.0.1重新完成现有相机UI用例。控件稳定、前后台恢复/显式停止 **2/2、0失败、0跳过**；两分钟持续UI **1/1、0失败、0跳过**。本轮runner正常启动，保留此前初始化失败作为历史证据，不据此声称已定位其原因。

四个30秒窗口新增748/750/749/750次成功结果，窗口实际120.457944秒、计数50→3047；UI断言确认照片/Benchmark按钮可用、无逐帧进度条，停止后5秒无新结果。短测附件为空，持续测试仅四份纯文本计数，无保留图片/视频。未访问相册，未修改产品或测试代码。

普通Demo已无参数恢复PID28004，数据保留；原工程与两个保护hash不变。证据位于`artifacts/verification/p4-current-camera-ui-20261027/summary.json`，含两个xcresult、原始日志及源码/设备KMP框架hash。现有/tmp恢复目录的App已由build-for-testing更新，使用本轮artifact hash，不套用旧恢复包hash。

这只补齐当前源码三项Compose相机UI验证；原生GrOp/渲染资源释放、真机PHPicker→Vision EXIF2/6/7、最低系统和全P4审查仍开放。基础10/12、P4/P11未完成。

## Task10限定审查与MLX增强集成补验（2026-10-27）

最终源码的独立`camera_isolation_review`已返回**APPROVE、0 findings**，覆盖相机并发所有权、MainActor/isolated deinit及Identity测试close-before-reopen屏障；不是全P4批准。新生成MLX增强App以全部可选开关、固定依赖pins完成generic iOS Debug unsigned构建（exit0、minOS18.0）；源码/pins哈希不变，不是新增签名/安装/设备推理。记录在`artifacts/verification/p4-camera-mlx-integration-20261027/summary.json`。下节“review429、MLX未复验”为上一个历史检查点，此处已补齐这两项；其余原生泄漏、交互和最低系统缺口仍保留。

## 相机并发隔离与当前源码真机回归（2026-10-27）

生产CameraSource采用不可变Sendable入口、单AsyncStream控制消费者和专用串行executor上的capture actor，保留start/stop/close顺序和同步有界帧接入。session与terminal状态由actor拥有；只有NSLock保护的callback slot使用窄范围unchecked声明，payload为Sendable，调用和capture析构在锁外。DemoController及Kotlin action conformances隔离至MainActor；各代回调捕获自己的帧队列，detached编码只返回Data。

可选Demo构建发现Published多模态状态不能从nonisolated deinit读取，修为isolated deinit。当前SDK的MainActor析构回溯部署实现与真实对象符号均保留；iOS15 target编译不等于iOS15运行验收。严格Swift5/Swift6 complete checks无诊断，最终simulator生命周期8/8，TSan相机专项7/7且产物有19个插桩符号；默认/语音+视频+多模态Demo构建通过，MLX专属增强构建未复验。Apple门禁通过，完整host470/470；首轮469通过/1项Identity测试busy暴露测试重开存储前未join异步stop排空，已将既有close移至reopen前，未改Identity产品行为。

iPhone16ProMax/iOS27.0.1当前源码hosted XCTest **11/11、0 failures、0 skips**，PID27956。两分钟持续runID `0D4BFED6-DAD0-49B0-9A58-EA9E94A56622`，四个窗口新增747/747/748/747，活动120.054737秒累计2992次Vision成功；停止5秒计数不变、inputs/jobs0，owner/bridge/detector关闭释放通过。同PID暂停/恢复/FIFO用例计数3/6/7/10、关闭后10；start/stop通知各3，inputs/outputs1/1→0/0。所有附件仅状态，不保存图像。

普通Demo从匹配当前源码的签名包恢复安装、无参数启动PID27961，安装/启动路径一致、原容器保留。证据与源hash在`artifacts/verification/p4-camera-isolation-20261027/summary.json`。首轮沙箱缓存、恢复包审批429及沙箱签名信任问题的原件保留，后续授权验证通过。独立code-reviewer及兼容性researcher均429，没有独立审查结论。

P4仍开放：本包不证明Compose/Skia原生GrOp释放，不替代真实PHPicker→Vision EXIF2/6/7、完整UI与最低系统验收。基础10/12、P11与全SDK/Demo范围不变；下节9/9及“生产代码未修改”均指改造前的历史检查点。

## 暂停/恢复与连续 stop/start 真机回归（2026-10-27）

为后续严格并发隔离改造补充行为约束，新增真实CameraSource/session回归：暂停保留同一input/output及callback，delegate排空后250ms无新帧；恢复继续出帧；紧邻stop/start后先等两次真实session通知，再要求新的回调。最后terminal close排空并移除输入输出。错误路径也关闭相机，不保存画面。

当前源码独立签名构建与iPhone16ProMax/iOS27.0.1完整生命周期套件**9/9通过、0 failures、0 skips**。新用例2.126秒，PID27922/runID `B6B0956C-4C7E-4138-8EEE-3E5C08A5B0DD`：暂停3、恢复6、连续stop/start完成后7、最终10次callback，关闭250ms后仍10；start/stop通知各3，inputs/outputs1/1→0/0。普通Demo恢复无参数启动PID27925，原容器保留。原始报告、附件及验证汇总在`artifacts/verification/p4-camera-concurrency-20261027/summary.json`。

生产代码未修改。Swift6/complete/iOS15单文件检查仍有4处CameraSource捕获warning，编译exit0不能当warning-free；SDK API声明不等于iOS15运行证据。并发隔离改造、完整UI/EXIF、原生GrOp残留、最低系统和完整审查继续开放，基础10/12不变。

## 末次 bridge 空闲释放复验（2026-10-27）

当前probe/manifest/status修订已随新签名构建在iPhone16ProMax/iOS27.0.1运行：PID27882/runID `543373D1-52A7-424C-9ED1-FE5922D6E97C`。三轮30秒窗口新增672/694/694次Vision成功，停止5秒无新增且jobs/inputs0；每轮页面退出30秒后owner/Compose controller/Metal layer均释放，残留bridge已关闭接入且无任务/输入。末次bridge在退出实际30.992938秒仍存在、32.086039秒采样消失，并持续到62.350613秒；期间没有重入或强制GC。此范围的自然释放已验证。

同PID301.116972秒trace完整覆盖尾部，18×GrOp/2304B仍最终live；17项33帧AtlasTextOp路径。trace/local运行binary UUID、安装路径、源/框架hash均吻合。固定源码宿主实验支持TLS槽随线程退出丢失原始存储的可能性，但实际线程生命周期/TLS地址未证实，详见[专项调查](p4-atlas-text-cache.md)。普通Demo恢复PID27888、无参数、保留容器；P4原生残留、UI/EXIF/minOS/严格并发/完整审查缺口不变。原始证据`artifacts/verification/p4-page-tail-20261027/summary.json`。

## 同 PID 页面退出/重入与原生残留（2026-10-27）

新增生成page-cycle模式及弱引用probe，复用生产SwiftUI/Compose内容与StateObject，独立root真实移除/重建页面；不改变生产导航或依赖。真机PID27863、runID `7BB10334-CBE9-4CC8-839A-BC6DD3F38C20`三轮30秒相机窗口新增667/690/694次Vision成功，stop后5秒无新增且inputs/jobs清空。每轮页面退出30秒后owner/Compose controller/Metal layer弱引用均为空，bridge仍1；前两轮bridge在下一次warmup已释放，最后bridge后续释放未验，因此原报告`incomplete-release`。

同PID301.106594秒Leaks录制覆盖全部周期。19×GrOp/2432B全部在最终live allocation表匹配，其中18项有33帧AtlasTextOp文字绘制栈，来自8个分配线程，按分配周期为7/6/5；另1项录制前已有。三轮均新增最终残留对象，不能因页面弱引用清空而宣布原生资源释放或稳定缓存。详细数值、解释边界与后续析构/线程验证见[AtlasTextOp专项调查](p4-atlas-text-cache.md)。

`artifacts/verification/p4-page-release-20261027/summary.json`保留trace/PID/URL/时间校验、完整分配表流式读取统计、实际运行源及App/framework hashes。普通Demo已恢复无参数启动PID27875，保护hash和生产源hash不变。独立probe审查的诊断/manifest/status建议已处理，双模式生成检查及修订后build通过；后续修订未重跑设备，原运行版本单独冻结。范围不包括物理UI、真机PHPicker→Vision、最低系统、严格并发或完整P4审查，P4仍未完成。

## AtlasTextOp 完整分配栈与线程缓存（2026-10-27）

对下方PID27593既有trace的后续只读导出已匹配全部7个地址。5个AtlasTextOp对象各有33帧完整分配栈，来自5个不同allocation threads，均经过Compose `SurfaceMetalRedrawer`的文字绘制路径；另2个录制前已有对象仍无栈。固定上游cpp与当前本地二进制确认每线程单槽缓存，且`GrRecordingContext`基类析构调用`ClearCache`清理调用线程槽。这支持缓存假设，但没有证明每个地址的最终TLS/析构状态、跨线程释放或长期稳定性，**原7×GrOp/896B仍保留为未关闭资源项**。

完整证据、当前二进制与trace关联边界、工具失败和下一页面退出/重入实验见[专项调查](p4-atlas-text-cache.md)。本轮未改生产源码或依赖，未新操作真机；独立产品review与P4其余验收缺口不变。

## 实际 Demo 渲染的持续/停止资源采样（2026-10-27）

`generate-camera-resource-app.rb`在独立目录生成device-only验证包，App源码是生产`OnDeviceAIDemoApp.swift`加唯一`onAppear`验证调用，其余controller/相机/Compose路径保持当前源码。显式UUID启动后等待本轮app-private触发文件，录制已附加准确PID后才触发相机。全过程无截图或图像文件；只导出状态、类型、几何与资源数据。

最终iPhone16ProMax/iOS27.0.1报告runID `DE42C583-3089-416C-92A0-94B38266F918`、PID27593为`passed`：预热3次，活动121.117918秒，四个30秒窗口分别新增672/642/670/641次，累计2628次真实Vision成功。停止后30.085894秒结果计数不变、bridge inputs/jobs均0。各采样点前台/thermal为raw0，`ComposeHostingViewController`和可见440×380 `CMPMetalLayer`存在。它验证真实页面渲染时的controller驱动运行，不替代物理UI控件操作。

Leaks trace为210.750103秒（00:19:03.834–00:22:34.584 +08:00），与安装URL/PID及报告逐阶段时间一致，完整覆盖基线、持续和停止后窗口。明细有**7个GrOp/896B**：5项`responsible-frame`现在可见`skgpu::ganesh::AtlasTextOp::Make(...)`，责任library为`OnDeviceAIDemo.debug.dylib`；另2项仍达到栈限制。这是新的分配位置线索，**不是完整所有权栈、泄漏根因或依赖升级依据**，也不能与先前不同状态的空闲样本直接计算增长率。

| 观测点 | Vision成功累计 | RSS快照（B） |
| --- | --- | --- |
| 活动30秒 | 675 | 471859200 |
| 活动60秒 | 1317 | 472678400 |
| 活动90秒 | 1987 | 473088000 |
| 活动120秒 | 2628 | 473841664 |
| 停止30秒后 | 2628 | 464404480 |

Allocations的`All Heap & Anonymous VM` persistent为351357200B，其中`VM: IOSurface`317390848B。它们是采样分配统计；RSS也是离散观测，均非峰值、physical footprint、GPU或能耗。录制会影响资源/时间，正常stop仍保留最后图像和Compose页面；本轮没有整个页面owner teardown或零泄漏证明。

验证入口的首次启动inactive已修正为限时前台就绪等待；初次只检测CAMetalLayer的误判，经本地固定Compose1.11.1官方source JAR与实际图层几何确认后补识别CMPMetalLayer。旧Swift几何API编译失败、一次未采用新构建的诊断启动、所有失败报告与早期未启动相机的trace均保留在`diagnostics.json`，不计产品RED或持续相机通过。

最终构建、生成源等价检查、源/保护hash、报告时序与trace关联通过；`summary.json`位于`artifacts/verification/p4-rendered-resource-20261027/`，同目录保留raw trace（约4GB，本地忽略）、导出XML、报告及source manifest。普通Demo已恢复安装/无参数启动（PID27598），共享override缺席，原应用数据与保护文件保留。生产源码未变，未重复运行未改动的470项Apple测试；新增范围独立review仍未取得。

**P4仍未完成。** 下一步从新trace中的AtlasTextOp符号调查对象生命周期与完整分配栈；完整渲染释放、真机PHPicker EXIF2/6/7、物理UI控件、最低系统和并发/review门槛继续保留。

## 真机两分钟 controller/bridge/Vision 管线（2026-10-26）

新增opt-in device-only XCTest，通过生产`DemoController.startCamera()`持续输入真实相机帧，计数仅在native Vision请求成功后增加。未改变生产代码，没有保存画面。iPhone16ProMax/iOS27.0.1完整套件**9 passed/0 failed/0 skipped**；持续用例runID `A13B90FD-12DB-4283-928C-832A205AF891`、PID27564，预热4次后活动120.069秒。

| 观测点 | 累计Vision成功 | 本窗口新增 | RSS快照（B） |
| --- | --- | --- | --- |
| 启动前 | 0 | — | 148439040 |
| 预热完成 | 4 | — | 180322304 |
| 活动30秒 | 724 | 720 | 490962944 |
| 活动60秒 | 1445 | 721 | 492339200 |
| 活动90秒 | 2165 | 720 | 491012096 |
| 活动120秒 | 2885 | 720 | 491012096 |
| 停止后5秒 | 2885 | 0 | 490864640 |
| owner释放 | 2885 | 0 | 450330624 |

每个30秒窗口均超过既定至少10次成功要求；停止后等待bridge jobs结束，再观察5秒，计数不变且inputs/jobs为空。owner weak引用为空、retained bridge与detector closed通过。8个观测点的applicationState/thermalState均raw0，仅为采样值。RSS来自`mach_task_basic_info.resident_size`，不等于峰值、physical footprint、GPU或能耗；上述数据不证明无泄漏。

`ODAI_CAMERA_LIFECYCLE_SUSTAINED=1`要求device模式，错误simulator组合实际拒绝且未创建输出；默认simulator项目生成成功且不含持续用例。证据在`artifacts/verification/p4-sustained-controller-20261026/summary.json`，含xcresult summary、8份原始文本附件与日志的交叉核对、源/保护hash和恢复来源。普通Demo预构建包与上轮hash一致，已重新安装并无参数启动（PID27570），安装URL/启动路径吻合，无文件共享override，原容器保留。

**范围限于native controller/bridge/Vision持续运行、停止与owner释放。** 完整Compose相机UI、持续及停止后的Leaks、渲染释放、真机PHPicker→Vision EXIF2/6/7、严格并发warnings、独立review和最低系统仍未完成；不将先前UI runner初始化失败转写为通过，P4基础里程碑继续未完成。

## 真实 PHPicker 的合成 EXIF 模拟器验证（2026-10-26）

独立SwiftUI验证host直接使用生产`DemoController.selectPhoto()`及其PHPicker delegate/normalize路径，以`controller.image`的尺寸和四角像素作为观测。没有复制生产decoder、注入选择回调或修改生产源码；Vision结果不在断言内。三个新建iOS27.1模拟器各导入一张公开合成80x40 JPEG，元数据确认EXIF2/6/7，库内original与fixture的SHA256唯一吻合。

| EXIF | 真实picker→controller输出（左上/右上/左下/右下） | XCTest |
| --- | --- | --- |
| 2 | 80x40，白/黑/白/黑 | 1 passed/0 failed/0 skipped |
| 6 | 40x80，黑/黑/白/白 | 1 passed/0 failed/0 skipped |
| 7 | 40x80，白/白/黑/黑 | 1 passed/0 failed/0 skipped |

新模拟器自带6张Apple示例图，首次总网格count=1断言因此失败，诊断重跑确认标签；两次都在选图前结束。最终测试要求外部核验的精确fixture标签且唯一匹配，无配置或匹配不唯一就停止，不使用位置fallback。失败与最终3/3结果均保留在`artifacts/verification/p4-picker-exif-20261026/`，`summary.json`关联原始xcresult、文本像素附件、fixture manifest、库内hash清单和源码hash。只清理了本轮新建的三个模拟器，原模拟器状态与受保护文件不变。

该结果补齐系统picker链路的模拟器证据，**不替代真机PHPicker→Vision、完整Compose Demo交互或最低系统验收**。Photos可能转换provider导出的编码表示，本包只验证已知EXIF输入的最终方向，不声称临时文件保留原EXIF。独立审查未取得；P4其余资源门槛继续开放。复跑说明见`probes/ios-photo-picker/README.md`。

## 回调同步修复后的真机复验（2026-10-26）

- 当前源码hash与`callback-summary.json`一致，iPhone16ProMax/iOS27.0.1 hosted XCTest **8/8 passed、0 failures、0 skips**，覆盖真实硬件关闭、owner/bridge释放与6项callback/session生命周期回归。使用既有签名，无provisioning updates。
- 文本附件记录callback3次，input/output1/1→0/0，关闭后250ms仍3次，restart后session未运行；没有保存相机画面。测试后恢复当前普通Demo安装并无参数启动，保留App容器，Info.plist没有文件共享override。
- 证据为`artifacts/verification/p4-owner-release-20261026/callback-physical-summary.json`、`callback-physical-tests.xcresult`及`callback-physical-attachments/`。这关闭新同步修复的硬件复验项；PHPicker EXIF2/6/7、持续相机/停止资源采样、完整渲染释放、最低系统、并发warnings与独立review仍保留，**P4未完成**。

## 回调注册与关闭同步（2026-10-26）

- `CameraSource.onFrame` 的注册、读取与清理原来没有同步，terminal close后仍可重新持有callback owner。`testClosedCameraRejectsCallbackRegistrationAndReleasesItsCapture` 在旧代码上实际失败两条断言（callback非nil、捕获对象未释放），完成exit65；最初沙箱访问失败不计RED。
- 现以NSLock保护回调槽与terminal admission；封住注册后停止/拆卸session、排空帧队列。回调调用及替换/关闭释放的旧捕获对象都在锁外，不引入析构重入死锁。没有通过新增unchecked Sendable隐藏警告。
- simulator完整生命周期7/7通过；带Thread Sanitizer的相机专项6/6通过，没有race/lock-order report，测试binary含19项`__tsan_`符号引用。另覆盖并发setter/getter/close及旧callback捕获对象析构时读取相机状态。它只证明该测试范围，不能替代持续摄像头或完整渲染资源验收。
- Apple完整门禁exit0：host XCTest470/470、distribution tools18/18、生命周期/照片/方向/ASR checks及native consumer/default Demo build通过；KMP部分UP-TO-DATE，增强binary consumer未运行。单文件Swift6 strict typecheck exit0但保留4项capture warnings，严格并发门槛未收口。新独立review因429不可用；本轮未做真机复验，旧硬件5/5不自动扩展到当前修改。
- 原始RED/GREEN、TSan、strict诊断与冻结后的Apple日志及源hash位于`artifacts/verification/p4-owner-release-20261026/callback-summary.json`及同目录。保护文件hash保持不变，**P4仍未完成**。

## 既有空闲 trace 的分配栈复核（2026-10-26）

- 在 Instruments 的 Leaks 明细展开 4 个 GrOp 对象，逐一选择后查看 Inspector `Allocation Backtrace`，地址分别为 `0x1112ddd80`、`0x1112de480`、`0x1112df200`、`0x1112de680`；每项均为128B，栈均只有 `<Call stack limit reached>` / `<unknown>`。UI 未提供比已导出的 Responsible Frame 更完整的来源；View Options 只列出 Invert Stack / Collapse Stack。
- 本机官方 `xctrace … --show-recording-options` 输出包含 Allocations 的记录/丢弃/引用计数/类型筛选及 Leaks snapshot interval，没有公开 stack-depth 参数。只读上游代理返回429，无调查结论。这些证据不足以定位根因或选择依赖修复；没有新采样、代码或依赖修改。
- 证据：`artifacts/verification/p4-owner-release-20261026/leaks-recording-options.json`、`idle-backtrace-inspection.json` 和追加后的 `physical-summary.json`。结束对同一 trace 的重复检查；完整资源释放、持续相机/停止窗口与其余 P4 门槛继续未完成。

## 真机关闭合同与当前空闲 Leaks（2026-10-26）

- 使用现有签名资产创建独立 hosted XCTest 工程，App/test bundle 标识沿用 Demo；没有 provisioning updates、工程或证书修改。iPhone 16 Pro Max / iOS 27.0.1 的 `physical-tests.xcresult` 为 **5/5 passed、0 failures、0 skips**。新硬件测试真实取得 **3** 次 camera callback，关闭前 input/output 为 **1/1**，关闭后 **0/0**，关闭后 250ms 的 callback 仍 **3**；再次 start 后 session 未重新运行、input/output 仍空。
- 另四项 owner 释放、face bridge/native detector 关闭、video output/delegate/callback 释放、frame queue drain 和重复 close 同机通过。测试仅输出计数/状态，未保存 capture image。新 device 模式以外的默认 simulator 模式再次 **4/4 passed**，证明生成器未混用 KMP ABI；硬件测试不在 simulator 上运行。
- 临时 native host 随后由普通 Demo 的当前源码 signed device build 和安装替换，保留原 App 容器；结束采样后已恢复无参数正常启动。普通 App 没有 `UIFileSharingEnabled` / `LSSupportsOpeningDocumentsInPlace`；保护文件 hash 保持不变。
- 两分钟相机 UI 用例两次均未能初始化 runner，精确错误为 `Timed out while enabling automation mode.`；xcresult 将 runner 初始化错误记作一条 failure，业务用例体未运行。曾核对并终止本项目旧安装路径的进程，重试仍失败，不把它当产品断言失败或通过。artifact-only xctestrun 按本机官方 man page 设置 `SystemAttachmentLifetime=keepNever`、`UserAttachmentLifetime=keepAlways`，禁止自动截图，保留自建文本附件。
- 另对当前普通 Compose Demo 的合成空白图启动后空闲进程采样 Leaks。实际 PID **27475**、安装路径和 TOC 均吻合，窗口 **91.471143 秒**。导出 **4 个 GrOp、总 512 B**；四项责任栈均为 `<Call stack limit reached>`。它证明当前该空闲路径仍可观察到泄漏对象，不能报告零泄漏，不能仅凭对象名认定相机桥责任；与历史静态/相机样本状态不同，不推导增长率。
- 同 trace 的 Allocations `All Heap & Anonymous VM` persistent bytes 为 **19,206,032**，只作为分配统计保留，不等同 physical footprint 或峰值。app-owned `face-smoke.json` 回读为 synthetic-white-64x64/Vision success，但该启动没有显式预设 run ID，其逐请求与 trace 的关联/新鲜性不作为验收依据。
- 证据位于 `artifacts/verification/p4-owner-release-20261026/physical-summary.json`、`physical-tests.xcresult`、`physical-attachments/`、`idle-trace-toc.xml`、`idle-leaks-detail.xml`、`idle-allocations-statistics.xml` 和 raw logs；大体积 trace 只留 Git 忽略目录。新增真机证据关闭 camera terminal-close 合同的硬件缺口；持续相机及停止窗口、PHPicker EXIF 2/6/7、完整渲染释放、最低系统和独立审查继续保留，**P4 尚未完成**。

## Demo owner 释放回归（2026-10-26）

- 新增真实 `DemoController` 的模拟器回归，先观察 owner 已销毁而 bridge 仍 available、持有 pending image、native detector 未 closed 的运行时失败。现 deinit 取消 camera-start/benchmark/liveness tasks、关闭 frame queue，并在 main actor 取消 photo submission、关闭 face bridge 和相机。
- `CameraSource.stop()` 继续是可恢复暂停；新增 asynchronous terminal `close()`，在既有 session serial queue 上停止 capture、移除全部 inputs/outputs、清空 video delegate，然后排空 frame queue、释放 callback 与 CIContext cache。重复 close 等待同一串行关闭边界；后续 start 不再重新打开 capture。
- `scripts/generate-camera-lifecycle-tests.rb` 创建独立测试工程，保留受保护的 Demo 工程。`probes/ios-camera-lifecycle/` 使用真实 AVCaptureSession/video output 和真实控制器验证 owner 释放、output/delegate/callback 清理、frame queue drain、并发重复 close：iOS 27.1 模拟器 **4/4 通过、0 failures**。此 simulator 没有配置真实 camera input，不证明硬件关闭或真实 sample-buffer 的完整释放。
- 新鲜 Apple 门禁 exit0：host XCTest **470/470**、分发工具 **18/18**、既有 camera lifecycle（7 scenarios）/photo submission/ImageIO orientation/ASR checks、native consumer 与默认 Demo simulator build 通过。KMP 部分任务为 UP-TO-DATE；增强 binary consumer 未运行。初轮 Ruby 2.6 与 Ruby 3.4 gem 混用导致 5 failures/2 errors，显式使用现有 Ruby 3.4.11 后同一门禁通过，未改安装或项目配置。
- 证据：`artifacts/verification/p4-owner-release-20261026/summary.json`、`camera-green.xcresult`、`bridge-green.xcresult` 与 frozen logs。运行时 RED 的实际断言已记录，runner 后续诊断整理被中断（exit130），没有把该 red xcresult 记成完整运行。camera API 的 RED 为缺少 initializer/terminal close 合同的编译失败（exit65）。
- 独立审查未取得结论（`429 Too Many Requests`）。默认 Demo build 新增 `CameraSource.close` non-Sendable self capture warning；Swift 5 构建通过，严格并发审查仍待完成。本改动不建立对历史 `GrOp` 的因果结论；新同设备 Leaks/Allocations、PHPicker EXIF 2/6/7 与最低系统缺口继续保留，**P4 尚未完成**。

## 现象 → 根因 → 修复 → 防护

| 现象 | 根因 | 修复 | 防护 |
| --- | --- | --- | --- |
| 开相机后界面快速闪动 | 每帧沿用单图路径，清空结果并切换进度条、按钮与结果行 | 新增 `detectFrame` 流式状态，保留上次结果，连续帧期间保持输入控件可用；单图仍显示进度 | 共享 ViewModel 两个新增回归用例；真机连续帧 UI 用例 |
| 前后台切换后相机不再输出 | 失活时停止捕获，未保留恢复意图和前台入口 | 区分暂时暂停和用户停止，令牌使过期权限回调失效；恢复等待取消中的桥接推理真正结束，再重建帧队列；会话操作与帧回调使用独立串行队列 | 生命周期检查 7 个场景；真机两轮 Home→返回、停止后返回及手动重启 |

## 验证结果

- 真机：iPhone 16 Pro Max，iOS 27.0.1。`CameraLifecycleUITests` **2/2 通过，0 失败，约 26.46 秒**。帧计数只在实际原生推理成功时增长；测试验证帧持续推进、结果行存在、无逐帧进度条、照片/benchmark 按钮可用。
- 首次 UI runner 在执行测试前因 “Timed out while enabling automation mode” 失败，属于自动化启动阻塞，不能作为行为 RED。停止本次遗留诊断/控制台进程后，以单 runner 重试通过。
- 共享 ViewModel iOS 模拟器测试 **4/4**，其中两例新增流式状态回归。新增 API 先经历缺失编译 RED，再实现为 GREEN。
- `sh scripts/check-camera-lifecycle.sh`：实际 Swift 状态代码 **7 个场景通过**，覆盖暂停恢复、显式停止、过期权限令牌和快速切换；已加入 `verify-apple.sh`。
- Apple 主机 XCTest **25/25**，桥接 typecheck、原生消费者和 KMP Demo 模拟器构建通过；设备 KMP framework 与签名 App 构建通过。
- Android 统一检查通过：JVM 测试、lint、Demo/原生消费者 APK 构建，290 个任务；此结果不代表 Android 设备相机验收。
- `git diff --check` 通过。真机结果包和控制台日志保存在忽略的本地 artifacts/ledger 内，不提交相机照片或设备标识。

## Android 真机补充（2026-10-16）

- 设备：TECNO KM9，Android 15，API 35，`arm64-v8a`；ADB 序列号不写入仓库。
- 修复前截图确认相机每帧走单图 `detect`：Compose 反复显示进度条并暂时禁用“选择图片”。根因是相机分析器未使用已有的 `detectFrame` 流式状态入口。
- 修复：`MainActivity` 相机分析器改为 `model.detectFrame(...)`，保留上一帧结果并保持输入控件稳定；照片路径仍使用单图 `detect`。
- 验证：`./gradlew :demo:shared:allTests :demo:androidApp:assembleDebug` 通过；安装 `io.ondeviceai.demo` 后，真机相机完成人脸检测，结果显示 `人脸 1`，UI dump 显示“选择图片”和 Benchmark 均为 enabled；3 秒 `logcat` 无 `FATAL EXCEPTION`/`AndroidRuntime`。
- 该设备覆盖现代 Android API 35，不替代 API 26 下限验收；未提交截图或用户照片。

独立 reviewer 返回部分意见后因模型容量限制中断，未形成完整审查结论。其提出的 benchmark-smoke 导出问题经对照 HEAD 核实：该路径在基线中已存在，输出指标和 fixtureHash、不输出图像像素，也不标记为合成空白证据；因此不作为本次新增的隐私回归。该路径及 benchmark→相机切换的取消等待由 P5 另行审查。此次执行了本地 diff 自查、项目配置重复执行、Ruby/plist 检查与上述构建/测试，不能等同完整独立审查通过。

## Android API 35 交互补充（2026-10-16）

- 设备：TECNO KM9，Android 15，API 35；使用 ADB 操作已安装 Demo，未保存设备标识或截图。
- 选图取消：打开系统 Photo Picker 后点击“取消”，返回 Demo，原有控件和状态保持可用。
- 选图推理：选择一张现有图片后，UI 显示 `人脸 1`，按钮保持可用；3 秒日志未发现 `FATAL EXCEPTION` 或应用 `AndroidRuntime` 崩溃。
- EXIF：使用本机生成的 Orientation 6 合成 JPEG（原始 160×80），经 Photo Picker 选择后结果显示为 `80×160`，证明 Android 读取 EXIF 并旋转后再送入推理。该临时文件已从设备删除。
- 权限：撤销 CAMERA 后重新启动，系统权限对话框出现；点击“不允许”后回到 Demo，再授予 CAMERA 并重新启动相机，成功得到 `人脸 1`。没有改动系统其他权限。
- 本次验证没有覆盖持续摄像头内存/热量、API 26、系统设置页恢复按钮或 iOS 照片选择器；这些仍保持未验证，不以本次结果替代最低版本验收。
- 尝试在已连接的 iPhone 16 Pro Max 上重跑 UI 测试时，`xcodebuild test` 在构建阶段因 Demo 与 UI Test runner 均无匹配 provisioning profile 退出（exit 65）。已确认本机有 Apple Development 身份，但未使用 `-allowProvisioningUpdates` 创建账户资产；此次未产生新的 iOS 行为结论，此前相机 UI 结果仍以 P4CameraFinal 记录为准。
- iPhone 18 Pro / iOS 27 模拟器新增 `testPhotoPickerCancellationReturnsToDemo`：打开 PHPicker、点取消后，Demo 的选图和相机按钮仍可用。`xcresulttool` 确认 1/1 通过、0 失败。首次测试筛选写错而执行 0 个用例，该次不计为通过；模拟器结果不替代真机。
- 用户完成两个 target 的 Xcode Automatic 签名设置后，在已连接的 iPhone 16 Pro Max / iOS 27.0.1 上重跑同一 `testPhotoPickerCancellationReturnsToDemo`。`xcodebuild test` exit 0，xcresult 确认 **1/1 通过、0 失败**。此前缺 profile 的构建阻塞已解除；该用例仅覆盖取消选择，未读取或保存用户照片。Xcode 自动写入的签名/工程格式改动仍留在本地工作区，未并入本次验证提交。
- 同一签名配置下重新执行 `testCameraKeepsControlsStableAcrossFrames` 与 `testCameraResumesAfterBackgroundAndHonorsUserStop`，真机 xcresult 确认 **2/2 通过、0 失败**。
- 首次自动化选图测试错误地查询 `CollectionView.Cell`，xcresult 的 UI hierarchy 显示 iOS 27 Photo Picker 网格将照片暴露为 `PXGGridLayout-Info` 标识的 `Image`，此前据失败文案推断“设备缺少照片”不成立。改为查询网格 Image 后，在同一 iPhone 上选中图片、返回 Demo 并得到人脸结果；`xcodebuild test` exit 0，xcresult 确认 **1/1 通过、0 失败**。测试未将照片或截图加入仓库，真机结果附件仅留在忽略的本地 artifacts。

## 剩余 P4 范围

剩余 P4 范围为 iOS 真机 EXIF 旋转/镜像，以及完整的原生桥接/相机资源释放验证。iOS 真机照片选择和取消已通过；权限设置页往返已有用户手动验收和恢复后的相机自动化复核，详见下节。Android 现代 API 35 的相机和照片交互已完成；API 26 下限仍等待设备。两端持续资源/热量只保留有边界的抽样证据，不能替代完整资源释放、P5 benchmark 或最低系统验收。

## iOS 相机权限恢复入口（2026-10-17）

- Demo 在相机授权拒绝后显示“打开设置”，点击后调用系统 App Settings URL；从系统设置回到 Demo 时重新查询相机授权，若已授权则清除恢复提示。调试构建提供仅用于测试的模拟拒绝启动参数，不改变真实设备权限。
- 当前 KMP iOS framework 重建后，iPhone 16 Pro Max / iOS 27.0.1 的 `testCameraPermissionDeniedOffersSettingsRecovery` 在 xcresult 中 1/1 通过、0 失败：按钮出现并打开系统 Settings。iPhone 17 / iOS 27 模拟器同一用例也为 1/1 通过。原始 xcresult 保存在忽略目录 `artifacts/P4PermissionDeviceFinal.xcresult` 与 `artifacts/P4PermissionSimulator.xcresult`。
- 此用例没有真实关闭或重新开启相机权限，单独不能证明 Settings 往返后的授权恢复或再次启动相机；随后补充的手动验收与恢复后复核见下方。此前照片选择 UI 重跑第一次因过期 KMP framework 缺少 `BridgeBackendSelection` 未编译；重建后第二次 runner/CoreDevice 卡住，未形成新的照片选择结论。EXIF 2/6/7 的 PHPicker→Demo→Vision 真机端到端仍未验证。

### 真实权限往返：用户手动验收与恢复后复核

- 用户按本次提供的四步操作完成 Settings 关闭本项目相机权限 → Demo 点击相机并确认“打开设置” → Settings 重新开启权限 → Demo 再次启动相机，并回复“已完成”。这部分证据来自用户手动真机验收，不是自动化全流程通过。
- 随后复跑无模拟拒绝参数的 `testCameraKeepsControlsStableAcrossFrames`，同一 iPhone 16 Pro Max / iOS 27.0.1 的 `P4-post-manual-permission-20261017.xcresult` 为 **1/1 通过、0 失败，10.156 秒**：真实 Vision 成功回调计数持续推进，控件可用并能显式停止。此结果确认手动恢复权限后的推理正常，不单独证明此前开关变化过程。
- 先前新增的 Settings 自动化草稿在点击开关后仍读到 `1`，未进入真实拒绝状态；该次为失败，不能算权限往返通过。失败后 CoreDevice 诊断收集未完成，保留本地日志 `artifacts/verification/p4-real-permission.log`。已确认本次测试/诊断进程退出，并撤销未通过的测试草稿，不将其纳入常规用例。

## EXIF 方向路径补充（2026-10-17）

- 新增合成 TIFF 的 ImageIO 主机检查，使用与 iOS Photo Picker 相同的 `CGImageSourceCreateThumbnailAtIndex` 参数。EXIF 1/2/6/7 的宽高、左右镜像及旋转方向均通过；合成图仅有黑白半幅，不含个人数据。`sh scripts/check-ios-photo-orientation.sh` 输出 `iOS photo ImageIO orientation checks passed (1, 2, 6, 7)`，并已加入 Apple 验证脚本。
- 此检查验证解码/方向变换，不替代 PHPicker→Demo→Vision 的真机端到端证据。iPhone 16 Pro Max 上重新运行三个现有 UI 用例：相机连续帧、前后台恢复通过；照片选择用例在系统 Photo Picker 的 `PXGGridLayout-Info` 图片可点击性判定失败，未进入 Demo 推理。尝试仅改变测试点击坐标后，设备 runner 启动超过两分钟未返回用例结果，已停止该 runner 并撤销未验证的测试改动。此前已通过的选图用例证据仍保留，未把本次失败定性为 App 回归。
- 真机 EXIF 2/6/7 及完整资源释放仍为未验证；权限设置页恢复见前述独立手动验收。本次 EXIF 检查不覆盖持续资源/热量，独立有限抽样见下一节。本次未改动 SDK 图像处理逻辑或设备相册内容。

## iOS 两分钟持续相机与资源抽样（2026-10-17）

- iPhone 16 Pro Max / iOS 27.0.1 上新增 `testCameraKeepsDeliveringFramesForTwoMinutesAndStops`，初版真机 xcresult 为 **1/1 通过、0 失败**。四个 30 秒窗口分别在 30.24、60.34、90.46、120.57 秒记录成功推理回调计数 802、1551、2301、3051；初版每窗口只要求至少 3 个新增成功回调。审查后改为每窗口至少 10 个新增成功回调，显式断言总时长至少 120 秒，避免重复停止，并明确停止后观察 5 秒只检查 UI 不再发布结果。
- 修订后的门槛在同一 iPhone 上重新执行，xcresult `P4-ios-sustained-reviewed-20261017.xcresult` 为 **1/1 通过、0 失败，134.187 秒**；这才是当前测试代码的有效验证证据。
- 这里的计数是桥接推理成功回调，不是底层捕获帧数；停止断言只证明停止后 Demo 不再向 UI 发布成功结果，不能单独证明 `AVCaptureSession`、Vision 任务、队列或图像内存已经释放。
- Activity Monitor trace `artifacts/P4-ios-sustained-activity-20261017.trace` 覆盖 96.378 秒、94 个样本。进程 physical footprint 采样范围约 79.6–145.3 MB，首样本约 143.3 MB、末样本约 81.9 MB；同一 trace 的 `device-thermal-state-intervals` 表显示热状态为 `Nominal`。
- Trace 在相机启动后才附着，只覆盖 120.565 秒 UI 窗口中的约 96 秒；该数据只代表进程 physical footprint 和采样时的设备热状态，不代表 GPU、全机峰值、能耗或长期资源释放。热节流 trace 未成功采集，仍记为未测。
- 收口前复核：Apple 主机 XCTest **33/33**，相机生命周期检查 **7 个场景**，ImageIO 方向检查 **1/2/6/7** 通过；Demo 与 UI runner 的 unsigned simulator `build-for-testing` 通过。独立审查指出的低回调门槛、重复停止和停止语义问题已修正；完整资源释放继续列为缺口。原始 xcresult、附件、trace/XML/JSON 和编译日志均留在忽略的 `artifacts/`，不提交设备照片或 trace 大文件。
- 同日先前完整 UI suite 为 **4/5 通过、1 失败**（`P4-ios-all-20261017.xcresult`），失败发生于系统 Photo Picker 图片 activation point。改为中心坐标点击后 Picker 返回，但等待人脸结果失败（`P4-ios-photo-center-20261017.xcresult`）；坐标改动已撤销。这些失败保留为待排查项，不据此声称本轮照片选择或 EXIF 端到端通过，也不覆盖此前独立选图成功记录。

## Photo Picker 引导卡修复后的交互复测（2026-10-17）

- 失败层级显示 iOS 27 Photo Picker 的“私密访问照片”引导卡覆盖在 `PXGGridLayout-Info` 网格图片上，导致图片存在但不可点击。UI 测试现在只在 3 秒内发现系统 `关闭` 按钮时关闭该引导卡，随后仍必须点击网格图片、返回 Demo 并等待 `人脸 …` 检测结果；没有绕过真实 Picker 或放宽结果断言。
- 同一 iPhone 16 Pro Max / iOS 27.0.1 上复跑五个非持续交互用例，`P4-ios-interactions-20261017.xcresult` 为 **5/5 通过、0 失败，52.539 秒**：Photo Picker 取消、Photo Picker 选图→Vision、相机控件稳定、前后台恢复/用户停止、权限拒绝后的 Settings 入口。
- 这次修复证明该设备和 iOS 27.0.1 的选图→Demo→Vision 闭环；不扩展为 EXIF 2/6/7 真机验证，也不证明 Settings 中实际关闭/重新开启权限后的恢复或完整资源释放。

## Android API 35 短时持续相机补充（2026-10-17）

- 设备：TECNO KM9，Android 15/API 35。重新安装当前 Debug Demo，授权本项目 CAMERA；使用前置相机，画面中有人脸，未保存摄像头图像或设备标识。UI 显示实际后端 `android.mlkit.face.bundled · offline`。
- 启动前 `dumpsys meminfo io.ondeviceai.demo` 的 TOTAL PSS 为 150689 KiB；相机开始推理后为 254968 KiB。随后每 30 秒采样一次，共 4 次，PSS 分别为 260815、256966、238050、231722 KiB，UI 每次均显示 `人脸 1` 及本次结果耗时（159、155、141、145 ms），`dumpsys thermalservice` 热状态均为 0。显式停止相机后 UI 不再显示人脸结果，PSS 为 198266 KiB，热状态 0。
- 原始 meminfo、thermalservice 与 UI hierarchy 保存在被忽略的 `artifacts/P4AndroidSustained/`。这只证明该设备本次约两分钟窗口内没有观察到持续 PSS 增长或热节流；它不是峰值内存、能耗、长期稳定性或 API 26 的验收，单次 UI 结果耗时也不是 P5 benchmark。
- 同次运行的语音 instrumentation 已改为向 Android 测试平台报告 1 个可发现的测试；`connectedDebugAndroidTest` 在 TECNO KM9 上显示 **1/1 通过、0 失败**，probe status 为 pass，`en-US` 可用并生成 129404 字节音频；API 35 的 on-device ASR 能力查询返回 `true`。该测试不代表 ASR 已授权、完成转写或 TTS/ASR 的离线网络行为。

## iOS Debug Orientation 6 合成探针（2026-10-17）

- 用户批准增加仅 Debug 的 `--smoke-orientation-6` 启动参数。App 在内存生成无个人数据的双色 JPEG：原始像素 `160×80`、EXIF Orientation `6`、renderer scale `1`；不写相册、不依赖用户图片，也不生成仓库内媒体文件。普通启动不运行该探针，Release 不包含该入口或状态 UI。
- Photo Picker 与探针共用 `decodePhoto`：`CGImageSourceCreateThumbnailAtIndex` 使用 `CreateThumbnailFromImageAlways=true`、`CreateThumbnailWithTransform=true` 和最大边 `2048`。Picker 从临时 URL 创建 image source，探针从内存数据创建 source；之后复用 `useImage` → KMP ViewModel → Swift bridge → 系统 Vision。Debug 成功状态及尺寸仅在真实原生成功回调中发布，尺寸取自 Vision `FaceResult`。
- TDD RED：实现前，`P4-ios-orientation6-red-device-20261017.xcresult` 真实执行 1 个用例并失败，原因是缺少探针标识及成功结果。首次沙箱内调用因 Xcode 缓存权限在构建前失败，不计为行为 RED。
- GREEN：同一 iPhone 16 Pro Max / iOS 27.0.1 的 `P4-ios-orientation6-green-20261017.xcresult` 为 **1/1 通过、0 失败，5.426 秒**。UI 用例等待成功回调并断言实际结果为 `80×160`，没有仅检查生成器的预期常量。
- 完整真机 UI suite `P4-ios-orientation6-suite-20261017.xcresult` 为 **7/7 通过、0 失败，190.997 秒**，包含新探针、Photo Picker 选择/取消、相机控件、两分钟持续运行、前后台恢复与 Settings 入口。首次请求因自动审批服务 429 未执行，重试获批后才运行，未将未执行请求计为验证结果。
- `sh scripts/verify-apple.sh` 通过：Apple 主机 XCTest **33/33**、KMP/共享 ViewModel 模拟器任务、桥接 typecheck、相机生命周期/照片提交/方向/ASR 检查、原生消费者和 Demo 模拟器构建。KMP 测试任务本次为 `UP-TO-DATE`，未声称重新运行。额外的 unsigned `build-for-testing` 和 Release 模拟器构建通过。
- Release App 二进制的 strings 检查未发现 `--smoke-orientation-6`、`orientation-probe-*` 或 `runOrientationProbe`。本次构建仍有既有的界面方向/AppIntents 警告与 Xcode SDK build-number 诊断，没有新增编译错误。
- 独立只读 code review 覆盖控制器、Debug UI 与新增用例，结论 APPROVE，未发现阻断或重要问题。用户既有 signing 工程和未追踪的人脸身份草稿保持原哈希，未纳入提交。
- 这只覆盖合成 Orientation 6 的共享解码→Demo→Vision 尺寸链路，没有通过 PHPicker 选择该图，也没有在真机检查像素旋转方向或 EXIF 2/7 镜像；完整 PHPicker EXIF 2/6/7 验收继续保留。主机方向检查另行覆盖 1/2/6/7 的像素方向。

## iOS Leaks 采集补充（2026-10-17）

- 同一设备再次运行两分钟相机用例，`P4-ios-resource-leaks-20261017.xcresult` 为 **1/1 通过、0 失败，137.211 秒**，停止后仍观察 5 秒没有新成功结果。
- 只附着本项目 Demo 的 Leaks 模板采集完成，`P4-ios-resource-leaks-20261017.trace` 记录 **96.019181 秒**。`xctrace export --toc` 确认存在 Allocations 和 Leaks tracks；可导出的 data tables 没有泄漏/分配统计 schema，因此本次尚未取得可核验的泄漏摘要，不能据此报告零泄漏或完整资源释放通过。
- trace 约 3.4 GB，仅留在 Git 忽略的 `artifacts/`，不加入 Git/LFS 或上传；xcresult、日志和导出 XML 也仅保存在该目录。完整相机/桥接资源释放、GPU/系统服务资源与最低系统验收仍未完成。

### Leaks 明细复核与当前版本重现（2026-10-19）

- 对历史 trace 直接导出 Leaks detail 后发现 **3 个 `GrOp`、共 384 字节**；其中两项责任栈指向 `OnDeviceAIDemo.debug.dylib` 的 Skia `AtlasTextOp::Make`，另一个栈达到 Instruments 的 call-stack limit。历史记录不能证明当前源码仍有相同问题，但也不能报告零泄漏。
- 用当前提交 `98da9a9` 的同一相机 UI 用例在 iPhone 16 Pro Max / iOS 27.0.1 重跑，xcresult `P4-ios-leaks-overlap-20261019.xcresult` 为 **1/1 通过、0 失败，133.547 秒**。通过核对安装 bundle 与 PID `15804`，Leaks trace `P4-ios-leaks-current-pid15804-20261019.trace` 与相机进程重叠 **37.753672 秒**；导出的 detail 仍为 **3 个 `GrOp`、384 字节**。
- 该有效窗口为 21:40:51.055–21:41:28.809，覆盖持续相机用例后段、用户停止后的观察及目标退出；不覆盖整个两分钟窗口。最初 `P4-ios-leaks-repro-20261019.trace` 附着旧 PID `13348` 且在第一轮用例结束后才开始，其空表不能作为本轮零泄漏证据；随后按名称自动附着又因多个同名进程而 exit20，未产生有效 trace。当前证据只采用与安装 bundle 路径一致的 PID `15804`。
- 当前复现只建立了“在该设备、该 UI/渲染路径和采样窗口中观察到的泄漏对象”事实，尚未证明根因来自 SDK 相机桥、Compose/Skia 文本渲染或系统服务，也未证明进程终止后的可回收状态。原始 trace 和导出 XML 保留在忽略目录；摘要哈希见 `artifacts/P4-leaks-export-20261019/exports.json`。下一步需用最小 UI/渲染路径和同一 trace 模板隔离责任边界，再决定是否属于本项目可修复缺陷。

### 静态页面对照（2026-10-19）

- 用同一已安装 bundle `io.ondeviceai.ondeviceaidemo` 启动不运行相机的静态 Compose 页面，PID `15819`；Leaks trace `P4-ios-leaks-idle-pid15819-20261019.trace` 采集 **96.079477 秒**。导出的 detail 仍有 **1 个 `GrOp`、128 字节**。
- 该对照说明 `GrOp` 不是相机启动后才首次出现，不能把三项/384 字节全部归因于相机桥或 Vision。相机路径相对静态页面多出的对象数量仍需最小化渲染/文本状态对照；P4 资源释放状态继续为 **unverified**。

### 纯原生 consumer 对照（2026-10-19）

- 使用仓库已有 `examples/ios-native/NativeConsumerApp.swift` 和工程，在命令行传入已有 development team 构建，未改写工程或签名资产；签名 device build、安装与启动均成功。该宿主只链接原生 Core/Face/AppleVision 与系统框架，不链接 KMP/Compose；源码中有一次合成空白 Vision 请求及 Client close。
- `P4-ios-leaks-native-pid15846-20261019.trace` 附着启动返回的 PID `15846`，采集 **95.903346 秒**，Leaks detail 为 **0 行**。这只是该采样窗口未观察到泄漏，不证明初始 Vision 请求成功，也未覆盖其完整调用/释放窗口或相机资源。
- 同设备对照为：纯原生 consumer **0 行**；静态 Compose Demo **1× GrOp / 128 B**；相机 Compose Demo **3× GrOp / 384 B**。下一调查重点是 Demo 的 Compose/Skia 渲染路径；当前没有依据将这些对象直接认定为原生 SDK 相机泄漏，或通过修改 SDK 释放逻辑修复它们。
- 本地 Gradle `:demo:shared:dependencyInsight --configuration iosArm64CompileKlibraries --dependency skiko` 确认 Demo 解析到 `org.jetbrains.skiko:skiko:0.144.6`，由 Compose Multiplatform `1.11.1` 引入（`gradle/libs.versions.toml:4,10`、`demo/shared/build.gradle.kts:4,12`）。对应缓存 sources 的 `uikitMain/.../MetalRedrawer.uikit.kt` 中，`dispose()` 在 180–192 行关闭 display link、flush 和 `DirectContext`；每帧 `surface`/`renderTarget` 在 263–264 行关闭，但 265 行仍保留 `TODO manually release metalDrawable when K/N API arrives`，空 surface 分支 245 行也有同一 TODO。该实现证据解释了为什么应继续检查 Compose/Skiko 的 drawable 生命周期，但不是当前仓库可直接修改的 SDK 源码。

可重复导出方式（无需依赖 `data/table` 的 schema 列表）：

```sh
xcrun xctrace export --input <trace> --xpath '/trace-toc/run[@number="1"]/tracks/track[@name="Leaks"]/details/detail[@name="Leaks"]' --output <leaks.xml>
xcrun xctrace export --input <trace> --xpath '/trace-toc/run[@number="1"]/tracks/track[@name="Allocations"]/details/detail[@name="Statistics"]' --output <allocations.xml>
```

Allocations 的 `persistent-bytes` 是分配统计，不等同进程 physical footprint/峰值；本次不据此改写既有内存测量。

## 身份 UI 集成后的 iOS 回归（2026-10-19）

- 在提交 `bb89516` 的 iPhone 16 Pro Max 上复测三个已有用例：Photo Picker 取消、Debug Orientation 6 和相机前后台/用户停止。`P4-ios-post-identity-refreshed-20261019.xcresult` **3/3 通过、0 失败，29.795 秒**；日志 `/tmp/odai-p4-post-identity-ui-refreshed.log`，xcodebuild exit0。
- 首次 `P4-ios-post-identity-20261019.xcresult` 在构建阶段失败：本地 `iosArm64` Demo framework 仍为 10月17日旧导出，缺少新增 Identity 类型，模拟器产物则为 10月19日。按 README 的真机构建前置步骤运行 `./gradlew :demo:shared:linkDebugFrameworkIosArm64 --console=plain` 后重试通过；没有修改 Swift 代码、工程或签名。Gradle 日志 `/tmp/odai-p10-device-framework-refresh.log`。
- 本次为新增身份控件后的检测流程回归，不覆盖身份生产推理、完整 PHPicker EXIF 2/6/7、资源释放或最低系统验收。两份受保护用户文件 SHA256 保持不变。
