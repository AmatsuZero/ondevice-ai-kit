# P4 相机闪动与前后台恢复

日期：2026-10-16。此包处理用户实际反馈的两个 Demo 问题，只完成 P4 的相机控件稳定及前后台恢复部分。

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
