# Typed video Demo input / state / host checkpoint

## 2026-10-28 真机 Home 后台取消与前台重入

复用当前 MLX Demo 验证副本和公开 `common-swifts-24fps.mp4`，在 iPhone 16 Pro Max / iOS 27.0.1 上运行独立 XCTest `testNaturalVideoOSBackgroundCancellationAndReentry`。测试从真实系统 Files provider 选择视频，等待共享页面的 `RUNNING` 与非空 update 后按系统 Home，确认 App 状态为后台或后台挂起；回到前台后状态为 `CANCELLED`，随后1秒观察窗口内没有自动重启，再次使用同一输入生成成功，输出 backend=`mlx.smolvlm2-500m`。测试 **1/1、0 failures、0 skips**，耗时 31.659 秒；`build-for-testing` 与 `test-without-building` 均成功。

原始 xcresult、附件和规范化报告见 `artifacts/verification/apple-mlxvlm-natural-video-20261028/os-background-xctest/`。该证据关闭 F08 的 Home 往返取消、1秒不自动重启及前台重入子项；取消 terminal 仅在回前台后观察到，后台中的具体 terminal 时序没有独立取证，Home 前 update 也未单独附带 native request/backend 标识。物理手指触控、自然质量、GPU/能耗和持续资源仍未验证。项目内 background notification 探针本轮返回 `OS lifecycle notifications timeout`，缺少足够事件，不能据此归因；保留于 `demo-background-rerun-20261028/report-current.json`，与这次 XCTest 的系统 Home 结果分别记录。

## 2026-10-28 真实 Files provider 自然短视频真机证据

使用当前 `c978628a7e3cb4f97f580ae307bb7f3218a9e1e1` 源码构建独立共享 Demo 验证工程。验证专用 plist 开启文件共享，将固定公开 `common-swifts-24fps.mp4`（SHA-256 `97682e3f9703e326bab3fbed06aa7a298b506d1965502288f5ed5037c51f2f1f`）和固定模型放入测试 App 沙盒。物理 iPhone 16 Pro Max / iOS 27.0.1 上，XCTest 打开真实系统 Files provider、选择该文件，随后共享页面显示选中文件，生成非空结果，`multimodal-phase=SUCCEEDED` 且 `multimodal-output-backend=实际后端：mlx.smolvlm2-500m`。

`testNaturalMultimodalVideoFromRealFiles` 连续两次真机运行均为 **1 test / 0 failures / 0 skips**，分别耗时 21.792 秒和 21.798 秒。两份 xcresult 路径、测试断言、安装/复制记录见 `artifacts/verification/apple-mlxvlm-natural-video-20261028/real-files/device-report.json` 与同目录测试副本。此项关闭 `systemFileSelection` 子项；物理手指触控、OS 后台、自然质量和资源预算仍未验证。未使用 iPhone 镜像、Photos 或私人媒体。

## 2026-10-28 固定公开自然短视频共享页面真机证据

在已验证的 [Common swifts native 视频输出](apple-mlxvlm-short-video.md)基础上，使用当前 `be75e326673cdcba88a10f3e2809b2c464fc83b9` 源码构建独立 `SpeechDemo` 验证工程。固定模型、Kotlin/Compose shared 框架、iOS `MultimodalHost` 和生产 `IOSMultimodalBridge` 保持原实现；只在 ignored 诊断副本中把验证入口的选择按钮改为 `multimodal-select-video`，并把测试 App Documents 中的输入换为公共领域 2 秒视频。没有使用 iPhone 镜像、相册或私人媒体。

物理 iPhone 16 Pro Max/iOS 27.0.1，`devicectl` 启动 PID `29279` 与报告一致，runId `6FEDC19F-01FA-48B8-B6CA-DF4B8E07E2EB`。KMP shared framework 离线构建和 iOS 签名 `build-for-testing` 均成功；测试 App 沙盒中的输入 SHA-256 `97682e3f9703e326bab3fbed06aa7a298b506d1965502288f5ed5037c51f2f1f`。本地 AX 激活视频选择按钮并向验证用 `UIDocumentPickerViewController` 注入固定公开文件的回调，随后 shared 状态为 `inputKind=VIDEO`、输入名 `common-swifts-24fps.mp4`。

四个同进程 shared 控件场景顺序为：首个 update 后取消→`CANCELLED`；重入→`SUCCEEDED`；首个 update 后离开页面→`CANCELLED`；返回后无自动重启，再重入→`SUCCEEDED`。两次成功均返回 `A group of birds fly in formation against a clear blue sky.`，并通过真实 Compose AX 节点核对文本与 `SUCCEEDED`；11 个既有生命周期 gate 全 true。模型身份为 `mlx.smolvlm2-500m` / `fa57db46815177fbdfd65cc85a2b3416a8332268` / `6e338e5df49424fc0db7c6d58184d622f92ed18a`。原严格 active reporter 使用本次固定素材身份校验通过，诊断额外拒绝4个 inputKind、render、scope 篡改负例。

原始报告 `artifacts/verification/apple-mlxvlm-natural-video-20261028/demo/device-report.json` SHA-256 `7ee24d303635527524a94878859eab59a01858801edc16c0d09137d6ca52b931`；同目录保存源码指纹、构建与设备命令/日志以及 `verification.json`。这证明当前共享页面的 `VIDEO` 路由、SDK生成、主动取消/离开/重入和 AX 输出终态。`systemFileSelection`、真实手指触控、OS后台、自然质量及资源预算仍 `unverified`；验证入口使用 picker 回调注入，不算真实 Files provider 选择。

## 2026-10-26 当前 dirty source iOS 真机 picker 清除回归复验

- 上一轮当前 dirty source 的新鲜真机报告在 Clear 后等待 `multimodal-input=尚未选择输入` 超时。按系统调试取证，产品 `MultimodalHost.clearImageFile()` 与 shared `MultimodalViewModel.selectImage(nil, nil)` 已在 MainActor 上同步更新；失败点是 UI test helper `tapMultimodalClear` 先点击 Compose 的子 `StaticText`，没有激活父 `multimodal-clear-input` Button。该假设由最小验证确认，不修改产品清除逻辑。
- `demo/iosApp/UITests/SpeechDemoUITests.swift` 现优先点击标识为 `multimodal-clear-input` 的父 Button，仅保留静态文本/渲染点作为 fallback。使用当前 dirty source 重新 `rtk proxy xcodebuild ... build-for-testing`，结果 `** TEST BUILD SUCCEEDED **`；验证专用 xctestrun 只在 `/tmp` 副本注入 `ODAI_MULTIMODAL_FILE_UI_TEST=1`。
- 物理 iPhone 16 Pro Max / iOS 27.0.1（UDID `<iPhone-UDID>`）重新执行 `SpeechDemoUITests/SpeechDemoUITests/testMultimodalImageFixturePickerCancellationAndClear`，结果 `/private/tmp/odai-picker-clear-button.xcresult`：**1 test / 0 failures / 0 skips**，耗时 40.178 秒，`** TEST EXECUTE SUCCEEDED **`。真实 Files picker 选择公开 `artifacts/P9/fixture.png`、二次打开取消保留输入、父 Button Clear 后显示“尚未选择输入”均通过。
- fixture SHA-256 为 `60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606`；本轮没有使用 iPhone 镜像、固定坐标、锁屏密码或私人媒体。该结果是当前 dirty source 的真实系统 picker/UI 证据；不扩展为物理手指触摸、OS background、Android 生产 VLM/video backend、自然质量、GPU/energy、minimum OS、binary archive 或全 P9 完成。

Roadmap 仍 **5/12、P9 active**。

## 2026-10-26 iOS OS background external-trigger A/B recheck

- 物理 iPhone 16 Pro Max / iOS 27.0.1（UDID `<iPhone-UDID>`）已确认解锁：CoreDevice `connected`、`passcodeRequired=false`、`unlockedSinceBoot=true`；本轮没有使用镜像、坐标或锁屏密码。
- 当前 dirty source fresh run `artifacts/verification/multimodal-demo-background-diagnostic-20261026/report-clean.json` 在新 ready marker（PID `27059`、runId `DB766F59-9863-4DDD-8705-96DA77636234`）后，由项目-owned inert companion 触发仍为 `failedGate=OS lifecycle notifications timeout`。
- 为排除 dirty source 回归，安装并运行历史上曾通过 14-gate OS background 的 signed artifact（sourceCommit `d50eff18ed53db61bc46f050f411ab076d118ede`），重新复制同一 fixture/model 并复用相同 `devicectl` launch 顺序；A/B fresh run `artifacts/verification/multimodal-demo-background-diagnostic-20261026/report-old.json`（PID `27089`、runId `0EFFE65D-D797-4B9E-9250-44E40511D65A`）同样为 `failedGate=OS lifecycle notifications timeout`。
- 结论仅定位为当前 CoreDevice/`devicectl` 前台激活链外部门槛；`osBackground=unverified`，不修改或归因于产品生命周期实现。历史通过报告 `artifacts/verification/multimodal-background-20261023/demo-background-integration.json` 仍仅作为旧 checkout 的历史证据。没有提交、推送、合并或发布。

## 2026-10-26 当前 dirty source 的 iOS shared AX active validation

当前 dirty source 重新链接 `:demo:shared:linkDebugFrameworkIosArm64` 与
`:demo:shared:linkDebugFrameworkIosSimulatorArm64`，14 actionable tasks 中 6 executed，exit 0；随后生成独立验证工程并完成签名 `build-for-testing`（`** TEST BUILD SUCCEEDED **`）。验证工程、构建日志和设备报告保存在
`artifacts/verification/multimodal-demo-active-dirty-20261026/`。

- 设备为物理 iPhone 16 Pro Max / iOS 27.0.1，UDID `<iPhone-UDID>`；bundle `io.ondeviceai.multimodalmlxdemo`。当前工作树基准 `HEAD=9288ac5a28dd5e2e55a23f9be9d5ec8108199fc4`，dirty source 指纹为 `560b236527c196187f99055def5d145b61fc1d98`，由 `source-manifest.txt` 和 `source-fingerprint.txt` 保存；该指纹不是 Git ref，不能冒称为已提交 commit。
- 使用现有 Team `4X996UV8LC`、固定本地 Swift/Xcode 缓存和公开 `artifacts/P9/fixture.png`（SHA-256 `60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606`）；14 文件模型从 `artifacts/P9/verified` 复制到该 App 自有 Documents。没有读取私人媒体、使用镜像、坐标或锁屏密码。
- 启动参数为 `--multimodal-mlx-local-model --multimodal-demo-validation --multimodal-demo-active-validation`。真机进程 PID `26996`、runId `F5372494-7EA3-4D79-A0ED-56081586D404`；报告 `demo-active-integration.json` 与 dirty source 指纹一致。
- 严格校验 `python3 -m tools.multimodal_active_report ...` exit 0，`strict-summary.json` 为 `status=passed`。`bootstrap`、`available`、`pageOpened`、`inputAction`、`pickerReply`、`activeCancel`、`reentry`、`activeLeave`、`noAutoRestart`、`returnReentry`、`closed` 全部为 true。首个实际 delivered update 后，`cancel` 和 `leave` 均为 `RUNNING → CANCELLED` 且无 output；两次同进程重入均为 `SUCCEEDED`，backend/model/runtime identity 精确匹配 `mlx.smolvlm2-500m` / `fa57db46815177fbdfd65cc85a2b3416a8332268` / `6e338e5df49424fc0db7c6d58184d622f92ed18a`。
- 这条证据覆盖当前源码的真实共享页面 AX 回调和 SDK 生命周期；`touchAcceptance`、`systemFileSelection`、`osBackground` 仍为 `unverified`。它不替代物理手指触摸、真实系统文件选择或 OS 后台验收，也不升级 Android 生产 VLM/video backend、自然质量、GPU/能耗、minimum OS、binary archive 或全范围独立 review。

Roadmap 仍 **5/12、P9 active**。

## 2026-10-26 Android 真机导入中后台/回前台回归

在 TECNO KM9 / Android 15 / API35（serial `<Android-serial>`）上，当前 dirty source 的 Kotlin-only Demo/test APK 重新安装后运行 `MultimodalPickerInstrumentation`。新增 `background-active-import` 场景使用 test APK 自有公开 slow fixture：导入任务仍 active 且 UI 尚未发布输入时执行 `moveTaskToBack(true)`，再用 `REORDER_TO_FRONT` 回到同一 Activity，断言 `public-slow.png` 输入仍被保留并最终完成。

- `backgroundMidCopy=passed-import-retained-after-background-return`：后台切换没有取消 Activity-owned import，回前台后输入可见。
- 同次真机 bundle 还通过 `actualSelectedSystemFile=passed-documents-provider-real-picker`、system picker cancel、busy reselection、synthetic callback/copy/clear、foreground 和 cleanup；最终 `status=passed`、instrumentation exit `0`。
- 原始日志/退出码：`artifacts/verification/multimodal-android-lifecycle-20261026/device-picker.log` / `.exit`；本地 unit/APK/test APK/lint fresh build：`build-rerun.log` / `.exit`，**BUILD SUCCESSFUL**（570 actionable tasks，38 executed）。
- 设备刷新 artifact `artifacts/verification/multimodal-android-lifecycle-20261026/devices-now.log` 显示 Android `<Android-serial>` 为 ADB `device`，物理 iPhone `<iPhone-UDID>` 为 CoreDevice `connected`；本轮没有使用 iPhone 镜像、坐标或锁屏密码。
- 为复核 test APK runtime 配置而暂时移除 androidTest 全局 assertion flags 后，APK 构建 exit `0`，但真机 runner 卡在 DocumentsUI real-selection 阶段并以 exit `130` 终止；恢复原配置后 `device-picker-restored.log` / `.exit` 再次得到 `status=passed` / exit `0`。这次探索性失败不归因于产品代码，flags 继续保留。
- slow fixture 只来自 test APK 的固定公开字节，未读取私人媒体；`inference=unverified-no-android-backend`。该回归验证 Android Activity 生命周期与导入所有权，不升级 Android 生产 VLM/video backend、自然质量或资源验收。

Roadmap 仍 **5/12、P9 active**。

## 2026-10-26 双真机 picker 证据与临时诊断清理

当前 dirty source 已移除真实 picker 诊断专用的 fixture seed、Documents logger、host/store/picker 日志和 UI test debug print；测试 gate `ODAI_MULTIMODAL_FILE_UI_TEST` 保留，fixture 由验证 artifact 外部复制到 App Documents。

- iOS 真机 bundle 从当前 source 重新签名构建，`artifacts/verification/p9-multimodal-picker-20261026/clean-build.log` 为 `** TEST BUILD SUCCEEDED **`。公开 `artifacts/P9/fixture.png` SHA-256 为 `60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606`。
- 物理 iPhone 16 Pro Max / iOS 27.0.1（UDID `<iPhone-UDID>`）直接运行 `testMultimodalImageFixturePickerCancellationAndClear`，`ios-picker-clean2.xcresult` 为 **1 test / 0 failures / 0 skips**。流程实际选择 Files fixture、二次 picker 取消保留输入、Clear 后变为“尚未选择输入”。这是 XCTest 真机 UI/system picker 证据，不是镜像或坐标点击。
- Android Demo 本地门禁：unit test、APK/test APK、lint **BUILD SUCCESSFUL**（既有完整门禁 570 actionable tasks；本轮 dirty test APK 重建也成功）。仓库级 `scripts/verify-android.sh` 也 fresh exit 0，原始输出见 `artifacts/verification/multimodal-android-cleanup-20261026/full-android-verify-20261026.log`。真机 TECNO KM9 / Android 15 / API35（`<Android-serial>`）的 Kotlin-only `MultimodalPickerInstrumentation` 重跑 bundle 为 `status=passed`；结果见 `artifacts/verification/multimodal-android-cleanup-20261026/device-picker-real-documentsui-rerun2.log`，退出码见同名 `.exit`。
- 真机结果包含 `actualSelectedSystemFile=passed-documents-provider-real-picker`：`OpenDocument` 打开真实 DocumentsUI，测试 APK 自有的 `MultimodalFixtureDocumentsProvider` 显示 `OnDevice AI test fixtures/real-system.png`，随后回到 Demo 并完成异步导入、清除。该证据验证真实系统 picker/provider 选择流程，但 fixture 是公开、固定的测试字节，不是私人媒体，也不代表 Android 生产 VLM/video 推理。
- Android 首次真机尝试因 test APK 独立进程缺 `kotlin.jvm.internal.Intrinsics` 崩溃；随后移除 provider 与 instrumentation 中的 Kotlin `.use {}` 运行时依赖，保留 Kotlin-only provider，并按 logcat 根因保留 androidTest Kotlin 断言编译选项。修复后重新构建、安装和真机流程均通过；`inference=unverified-no-android-backend`。

Roadmap 仍 **5/12、P9 active**。本检查点不升级 Android 生产 inference、自然质量、GPU/能耗、minimum OS、binary archive 或全范围独立 review。

## 2026-10-25 Android 模拟器 Kotlin picker/in-flight 回归（真机与生产推理仍未验）

在用户不在 iPhone 真机旁的约束下，本轮只使用本地 Android 模拟器 `Medium_Phone`（`emulator-5554`，API 37），没有操作已连接的物理 Android 或 iPhone。当前 dirty source 构建出的 app/test APK 安装到模拟器后，直接运行 `io.ondeviceai.demo.test/io.ondeviceai.demo.MultimodalPickerInstrumentation`，退出码为 `0`，结果 bundle 为 `status=passed`。

- `systemPicker=passed-open-cancel-without-media-read`：真实 Android picker 打开后取消，不读取媒体。
- `activityMidCopy=passed-activity-owned-import-active`：Activity 自有导入任务在二次选择前仍处于 active。
- `busyReselection=passed-first-import-retained-second-picker-rejected`：忙态拒绝重选且保留已接受的导入。
- `syntheticResult=passed-real-callback-copy-host-clear-fixture-only`、`foreground=passed-no-input-no-auto-generation`、`cleanup=passed-new-owned-directories-released`：真实 Activity 回调、异步 staging、clear、前后台和目录清理通过。

原始输出、退出码、设备/API 与 dirty source 摘要保存在 `artifacts/verification/multimodal-android-emulator-20261025/`。该包仍明确标记 `actualSelectedSystemFile=unverified-synthetic-result-only`、`inference=unverified-no-android-backend`；合成 provider 不证明真实系统文件选择、视频解码、Android VLM/video 推理或质量。Roadmap 仍 **5/12、P9 active**。

## 2026-10-25 fixture seed 错误诊断收口（真机 picker 仍未验）

上一轮 review 的 LOW 建议已收口：`MultimodalHost` 的 UI-test fixture 写入不再静默吞错，写入成功/失败均通过现有 DEBUG + `ODAI_MULTIMODAL_FILE_UI_TEST=1` 诊断日志记录。当前 dirty source 重新生成的 bridge/store/factory 工程在 iOS 27.1 模拟器上全套 **75/75 passed、0 failures、0 skips**；普通沙箱首轮仅因 CoreSimulator/SwiftPM 缓存权限失败，固定缓存的 escalated 重跑通过。xcresult：`/tmp/odai-multimodal-bridge-seed-fix2/build/Logs/Test/Test-MultimodalBridgeTests-2026.10.25_23-07-10-+0800.xcresult`。

这只关闭 fixture seed 的诊断建议，不改变真机门槛：物理 iPhone Files picker 仍需用户本人解锁后取得 `1/0/0`；不使用锁屏密码或 iPhone 镜像代替真实设备证据。Roadmap 仍 **5/12、P9 active**。

## 2026-10-25 当前 dirty source 本地门禁复核（真机 picker 仍未验）

在保留当前 dirty source、未使用 iPhone 镜像或锁屏密码的前提下，重新执行了本地门禁：

- iOS 生成工程 `artifacts/verification/multimodal-ios-demo-dirty-20261025/MultimodalBridgeTests.xcodeproj` 在 iOS 27.1 `iPhone Duo`（`DF2DC7EA-257B-4E5F-9AFD-EC4BD2ED6D7F`）通过 **75/75，0 failures，0 skips**。最新 xcresult：`/tmp/odai-multimodal-bridge-current/Logs/Test/Test-MultimodalBridgeTests-2026.10.25_22-40-16-+0800.xcresult`。QoS priority-inversion 为已有 runtime warning，不是测试失败。
- Android `:demo:androidApp:testDebugUnitTest :demo:androidApp:assembleDebug :demo:androidApp:assembleDebugAndroidTest :demo:androidApp:lintDebug --offline` **BUILD SUCCESSFUL**，570 tasks（3 executed、567 up-to-date），lint 无 error。
- 仓库 `rtk proxy ./scripts/verify-android.sh` 随后也 **BUILD SUCCESSFUL**，725 actionable tasks（44 executed、681 up-to-date）；末尾确认 Android JVM tests、lint 和 APK builds passed。该脚本不包含 Android 生产 VLM/video inference。
- benchmark/consumer 定向本地测试 **12/12 passed**；当前 validator 对修复后的 30-sample raw 报告以 `--expected-samples 30` 通过，要求并确认 `sampleProfile=1-warmup-30-samples`。SwiftPM manifest 检查仍受本机 `sandbox_apply: Operation not permitted` 缓存限制，不能当作源码失败或通过。

这些结果只收口当前本地代码门禁；物理 iPhone 的 Files picker 仍等待用户亲自解锁，必须直接运行既有真机 xctestrun 取得 `1/0/0` 后再更新结论。

## 2026-10-25 模拟器真实 Files picker 回归通过（真机仍未验）

本轮遵守“用户不在真机旁、不使用 iPhone 镜像”的约束，只在 iOS 27.1 模拟器 `iPhone Duo`（`DF2DC7EA-257B-4E5F-9AFD-EC4BD2ED6D7F`）运行当前 dirty source。首次重跑发现系统 picker 的 `浏览` 查询只查宿主 `app.buttons`，随后发现点击 `fixture` 静态文本只选中而不会回调 delegate；两处均在 UI test helper 中修正：系统 picker 控件使用跨层级 `descendants(.any)`，公开 fixture 使用真实 `Cell` 点击。

- 验证 artifact：`artifacts/verification/p9-multimodal-picker-20261025/simulator-ui-ocr-rerun4/`；`build5.log` 为 `** TEST BUILD SUCCEEDED **`。
- artifact-only `ui-test.plist` 临时开启 `UIFileSharingEnabled` / `LSSupportsOpeningDocumentsInPlace`，只复制公开 `artifacts/P9/fixture.png` 到模拟器 App Documents；fixture SHA-256 为 `60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606`。tracked 生产 plist 未改动。
- `test2.xcresult` / `test2.log` 使用完整 selector `SpeechDemoUITests/SpeechDemoUITests/testMultimodalImageFixturePickerCancellationAndClear`，实际执行 **1 test / 0 failures / 0 skips**。日志显示真实 Files picker 打开、进入“我的 iPhone”、点击 `fixture.png` Cell 后异步导入成功；第二次 picker 取消保留选择，Clear 后释放输入。
- 这是模拟器系统 picker/UI 证据，不是物理触摸或真实 iPhone 验收；真机 picker `1/0/0` 仍为 `unverified`，等待用户亲自解锁设备后直接复用真机 xctestrun。临时 Documents logger、fixture seed 和 UI debug `print` 仍等待真实设备日志回读后清理。

Roadmap 仍 **5/12、P9 active**；本轮 `git diff --check` 通过，所有既有 dirty/untracked 修改保留，未 commit/push/merge/release。

## 2026-10-25 无真机时的模拟器回归与 Kotlin fixture 收口

用户暂时不在物理设备旁，本轮没有使用 iPhone 镜像，也没有继续等待解锁的真机 runner；已终止 pending session `28306`（exit 130）。真机 picker 的 `1/0/0` 证据保持未完成，待设备解锁后直接复用已生成的 xctestrun。

- 当前 dirty source 生成工程在 iOS 27.1 模拟器 `iPhone Duo`（`DF2DC7EA-257B-4E5F-9AFD-EC4BD2ED6D7F`）完整回归通过：**75 tests / 0 failures / 0 skips**。xcresult：`artifacts/verification/multimodal-ios-demo-dirty-20261025/build-final/Logs/Test/Test-MultimodalBridgeTests-2026.10.25_21-10-28-+0800.xcresult`。
- 原先集中在 `testLateSuccessfulImportIsRetiredAfterClearLeaveBackgroundCloseAndOwnerRelease` 的 3 个失败断言是测试预期落后于 picker 退场修复：picker reply 后的 `background()` 必须保留正在复制的输入。测试已改为 `testLateSuccessfulImportIsRetiredAfterClearLeaveCloseAndOwnerRelease`，background 保留行为由 `testPickerDismissalBackgroundAfterReplyDoesNotCancelImport` 单独覆盖；没有扩大生产改动。
- Android Demo 仍为 Kotlin/Compose；fixture provider 仅存在于 `demo/androidApp/src/androidTest/kotlin/io/ondeviceai/demo/MultimodalFixtureProvider.kt`，旧 Java 文件已删除。`testDebugUnitTest`、`assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 通过（570 tasks，3 executed、567 up-to-date），lint 无 error。
- `git diff --check` 通过，两个保护文件 hash 未变化，所有既有 dirty/untracked 修改保留。临时 Documents logger/fixture seed 和 UI debug print 等待真实设备日志取得后再移除；本地模拟器结果不升级为真实 Files picker 验收。

## 2026-10-25 current dirty-source真机包重建与设备门槛

在上一节 picker 退场竞态修复后，已从当前 dirty source 重新生成签名 iOS 真机 UI 包；`xcodebuild build-for-testing` 输出 `** TEST BUILD SUCCEEDED **`。新产品二进制位于 `artifacts/verification/p9-multimodal-picker-20261025/device-derived-after-host-fix/`，`SpeechDemo.app/SpeechDemo` SHA-256 为 `7cdbf08fff372fd1848daf242a2a57881e905873fe6886f0b5b2de276129164c`，`SpeechDemoUITests.xctest` SHA-256 为 `8ca23ad8eb0bd6917068cc5b08d56c3bb12616a65097bd20a423613746b7c072`。验证专用 xctestrun 只在 artifact 内注入 `ODAI_MULTIMODAL_FILE_UI_TEST=1`，没有修改产品工程。

随后对物理 iPhone 16 Pro Max / iOS 27.0.1（UDID `<iPhone-UDID>`）直接发起 `test-without-building`，没有使用 iPhone 镜像；`xcodebuild` exit **70**，设备条目明确返回 `The device needs to be unlocked to continue`。原始证据为 `artifacts/verification/p9-multimodal-picker-20261025/host-fix-device-attempt.{log,exit,xcresult}`。解锁后必须继续使用该新包运行 `testMultimodalImageFixturePickerCancellationAndClear`，取得真实设备 `1/0/0` 前不改变 P9 picker 结论。

同一时段的 `xcrun xctrace list devices` 将该 UDID 列入 **Devices Offline**，与 `devicectl` 的 `Timed out waiting for CoreDeviceService to fully initialize` 一致；这只是设备/CoreDevice 外部门槛，不把镜像画面或 AX 结果升级为真机证据。

## 2026-10-28 Home 转场取消时序与 native provenance

在前述真实 Files provider 与 Home 往返 XCTest 基础上，独立 ignored 验证副本把 UIKit lifecycle、共享页面 phase、native request/backend 和 drain/lease release 写入同一条单调时钟 trace。生产源码、普通 Demo、签名工程和媒体库均未修改。

- iPhone 16 Pro Max / iOS 27.0.1（UDID `00008140-0008703436D3001C`）执行 `testNaturalVideoOSBackgroundCancellationAndReentry`：**1 passed / 0 failed / 0 skipped**，xcodebuild 用时 39.377 秒。固定公开 `common-swifts-24fps.mp4` SHA-256 为 `97682e3f9703e326bab3fbed06aa7a298b506d1965502288f5ed5037c51f2f1f`，从真实系统 Files provider 进入共享页面。
- 请求 `7511710F-23D4-458A-854A-FF9F34035326` 在 Home 前记录 `nativeRequestAdmitted` 和 `firstNativeClientUpdate`，backend=`mlx.smolvlm2-500m`。`willResignActive` 后 41.882 ms 记录 `nativeDrainAndLeaseRelease`，再过 1.185 ms 记录 `bridgeCancelled`；`didEnterBackground` 在取消终态后 601.106 ms 才出现。因此可确认取消发生在 inactive 转场期间，不能写成进入 background 后才出现 terminal。
- 前台恢复后，同一输入以新 request ID `6A411334-E9AD-4FEC-8D62-5DF4D6AD1440` 再次生成，记录新的 native update 和 drain/lease release。规范化报告为 `artifacts/verification/apple-mlxvlm-natural-video-20261028/home-timing-xctest/home-timing-device-report.json`，trace SHA-256 为 `07c99f6d13f6d44266bb9a81e8d244db58c9c0a09f92808806020580bbbfd699`。

本证据关闭 Home 转场取消时序和 tagged native request/backend/drain provenance 两个窄缺口；`inBackgroundTerminalTiming` 仍为 `unverified`。自然质量、可比较 GPU/能耗、持续资源和完整物理触控也仍未验证。

## 2026-10-28 自然视频语义质量首个有界报告

对已保留的真实 iPhone native run 建立了独立的 `natural-video-semantic-rubric-v1` 报告。固定身份为 fixture SHA-256 `97682e3f9703e326bab3fbed06aa7a298b506d1965502288f5ed5037c51f2f1f`、backend=`mlx.smolvlm2-500m`、model/runtime revision 与原始报告一致，prompt 仍为“一句简短描述视频发生了什么”。

- 评分只检查三类可审计语义：subject=`birds/swifts`（参考帧明确为复数）、action=`fly/flying/flight/in flight/soar/soaring`、scene=`sky` 且包含 `blue/cloud/clouds`。首个保留 run 的输出 `A group of birds fly in formation against a clear blue sky.` 三类均命中，`semanticPass=1/1`。
- 规范化输入、期望身份、评分器和汇总分别位于 `artifacts/verification/apple-mlxvlm-natural-video-20261028/quality/{raw-runs.json,expected-identity.json,summary.json}`；同一输入重复生成汇总逐字节一致。原始设备报告仍是权威来源，未改写。
- 这是单个既有 run 的 bounded semantic evidence，不是完整自然质量结论，也不是 benchmark 样本。`naturalTemporalQuality`、`gpuEnergy`、`sustainedResources` 明确保持 `unverified`；还需要额外 distinct physical-device runs 与更完整任务集才能裁定质量。

CoreDevice 恢复到 `available (paired)` 后，fresh `test-without-building` 仍 exit **70**；直接查询 lock state 返回 `CoreDeviceError 10003` / `RemotePairingError 1016`（`The device has not been unlocked recently`）。这确认当前只差一次设备解锁，不能归因于 host/picker 代码。

## 2026-10-25 P9 picker 退场竞态修复与本地回归

当前物理 iPhone 仍因 `CoreDeviceService` 初始化超时无法重跑；本节只记录代码级根因和本地证据，不升级 P9 真机 picker 结论。

- 根因位于 `MultimodalFilePicker.finish`：它在调用选择回调前就清除了 `isPresenting`。系统 Files picker 随后退场触发 Demo 的 background 通知时，host 看见 picker 已不在 presenting 状态，取消了刚创建的异步私有复制，因此真实流程回到 Demo 后仍显示“尚未选择输入”。
- 新增 `testSelectionKeepsPresentingDuringReplyToPreserveImportAcrossDismissal`，先在旧实现上得到预期 RED；最小修复增加 `finishing` reentrancy guard，并把状态清理延后到回调返回后。
- 修复后 Picker 专项 XCTest **5/5** 通过；当前生成的 `MultimodalBridgeTests.xcodeproj` 全套 bridge/store/factory XCTest **73/73、0 failures、0 skips**。RED/专项/全套结果分别保存在 `artifacts/verification/multimodal-ios-demo-dirty-20261025/tdd-red-escalated/`、`tdd-green-escalated/` 和 `full-after-picker-fix/`。
- 修复后的真机 UI bundle 已重新 `build-for-testing` 成功；待运行的 `SpeechDemo_picker_after_fix_iphoneos27.1-arm64.xctestrun` 位于 `artifacts/verification/p9-multimodal-picker-20261025/`，SHA-256 为 `a4fad0364c6027853a5d051830ef9a078cad7e0def7737bbd1fb137672206b48`，两个测试环境均注入 `ODAI_MULTIMODAL_FILE_UI_TEST=1`。由于 CoreDevice 仍超时，尚未安装或运行。
- 这证明 host/picker 状态竞态已在本地被锁定和修复，不替代真实物理 Files picker。下一次 CoreDevice 恢复后仍须执行真实 iPhone 测试、读取 `Documents/odai-multimodal-debug.log`，确认后再移除临时诊断代码。

## 2026-10-25 dirty source 本地门禁复核

当前 dirty 多模态视频 Demo scope 的本地验证已重新执行，但不构成独立 review：

- Android `:demo:androidApp:testDebugUnitTest --offline --rerun-tasks` 在 escalated 本地缓存路径 **BUILD SUCCESSFUL**，245 tasks executed；`assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 组合命令 **BUILD SUCCESSFUL**，565 tasks（68 executed/497 up-to-date），lint 无 error。
- iOS 生成工程 `artifacts/verification/multimodal-ios-demo-dirty-20261025/MultimodalBridgeTests.xcodeproj` 使用当前本地 `apple` package 和 iOS 27.1 simulator，`** TEST SUCCEEDED **`，72 tests、0 failures、0 skips；结果见 `artifacts/verification/multimodal-ios-demo-dirty-20261025/build/Logs/Test/Test-MultimodalBridgeTests-2026.10.25_09-27-44-+0800.xcresult`。
- 独立 `code-reviewer` / `architect` lane 本轮均因 `429 Too Many Requests` 未返回证据，不能写成 APPROVE、CLEAR 或 merge-ready；QoS priority-inversion 仍是已知 LOW watchlist。

这些是本地实现门禁，不是 Android 生产推理、真实系统 picker/物理触摸、自然质量、资源能耗、最低系统或全 P9/P11 验收。Roadmap 仍 **5/12、P9 active**。

## 2026-10-25 iPhone 真机 Video Demo UI 成功／取消闭环

本节是当前 iOS Demo UI 的最新证据；下方镜像段落保留为历史外部门槛记录，不再作为本轮真机 UI 结论。

- 目标物理设备：iPhone 16 Pro Max / iOS 27.0.1，UDID `<iPhone-UDID>`，`xcrun devicectl list devices` 为 `connected`。本轮完全不使用 iPhone 镜像，使用 `devicectl` + `xcodebuild` XCTest 真机 runner。
- 验证工程：`artifacts/verification/roadmap-p7-video-ui-20261025-ruby34/SpeechDemo.xcodeproj`；固定 Ruby 3.4.11、`ODAI_VIDEO_DEMO=1`、Team `4X996UV8LC`；`build-for-testing` 成功。测试专用 `ui-test.plist` 只在忽略 artifact 中存在。
- 固定公开 fixture `input-audio.mp4` 的 SHA256 为 `6a1c2a7bb875c8d9502450e732901fcd21bca5be27da0ef6ceae58c5b64c0d00`；每次 runner 新容器安装后，用 `devicectl device copy to` 放到 `Documents/odai-video-ui-fixture.mp4`，没有读取私人媒体。
- `video-success7.xcresult` 通过 `testVideoFixtureSuccessPlaybackSharingAndCleanup`：真实系统 Files picker 选取 fixture、二次 picker 取消并保留输入、真机处理输出 `1280×720 · 48 帧 · 2.0 秒 · 音轨 1`、系统分享面板 `PopoverDismissRegion` 关闭、播放面板关闭、清理输出并保留输入。
- `video-cancel1.xcresult` 通过 `testVideoFixtureCancellationReentryAndBackground`：取消到 `CANCELLED`、同输入重入成功、Home/回前台不自动重启。两个 summary 均为物理设备 `passedTests=1`、`failedTests=0`。

这是 **真机 XCTest UI/系统 Files picker** 证据，不是人工物理手指触摸验收，也不代表 Android 生产 backend、自然视频质量、GPU/能耗、最低系统、binary archive 或 P9/P11/全 roadmap 完成。完整命令与中间失败包见 [video-bridge-demo](video-bridge-demo.md) 与 handoff。

## 2026-10-25 iPhone 镜像画面可见但真实点击仍不可用

本轮复核当前 dirty 工作树后，授权 iPhone `<iPhone-UDID>` 仍由 `devicectl` 识别。设备上已安装 Demo 的真实 bundle identifier 为 `com.ugreen.ondeviceai.demo`（此前尝试的 `io.ondeviceai.demo` 不存在）；使用 `--terminate-existing` 启动该 bundle 返回成功，设备进程列表出现 `OnDeviceAIDemo` PID `23713`。

本机 `com.apple.ScreenContinuity` 现在能显示镜像画面，但镜像窗口的 AX 树只有容器，真实坐标点击 `[42,138]` 返回 `windowNotFoundAtPosition((36.0, 215.0))`。所以物理触摸与真实系统文件选择仍为 **unverified**；镜像可见、`devicectl` 启动成功、AX/injected picker 或合成 callback 都不替代真实 UI 门禁。本轮没有读取私人媒体、没有安装新包、没有改签名或保护文件。

## 2026-10-25 Kotlin-only Android test fixture revalidation

The Android Demo remains Kotlin/Compose. The test-only `MultimodalFixtureProvider` is now kept at `demo/androidApp/src/androidTest/kotlin/io/ondeviceai/demo/MultimodalFixtureProvider.kt`; the temporary `src/androidTest/java/.../MultimodalFixtureProvider.java` implementation has been removed. The Java version was only a short-lived workaround for the test APK's separate Kotlin runtime/classpath; the final fixture is Kotlin with explicit `androidTestImplementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.0")` and Android-test-only assertion flags so provider startup does not require target-APK runtime assumptions.

- Forced local revalidation with the fixed dependency cache: `rtk proxy ./gradlew :demo:androidApp:testDebugUnitTest --offline --console=plain --rerun-tasks` exits **0**; the current XML totals are **38 tests, 0 failures, 0 errors, 0 skipped**. Raw log/exit: `artifacts/verification/multimodal-android-kotlin-fixture-20261024/unit-rerun.log` and `unit-rerun.exit`.
- Forced `:demo:androidApp:lintDebug :demo:androidApp:assembleDebug :demo:androidApp:assembleDebugAndroidTest` exits **0**. Lint reports the existing 14 warning baseline and no error; raw log/exit: `artifacts/verification/multimodal-android-kotlin-fixture-20261024/build-lint-rerun.log` and `build-lint-rerun.exit`.
- Repository Android verifier `rtk proxy ./scripts/verify-android.sh` exits **0** with **725 actionable tasks** (44 executed, 681 up-to-date); its final line confirms Android JVM tests, lint, and APK builds passed. Raw log/exit: `artifacts/verification/multimodal-android-kotlin-fixture-20261025/full-android-verify.log` and `full-android-verify.exit`. This remains local Android evidence, not production inference acceptance.
- Source inventory confirms the provider exists only as `.kt`; `git diff --check` exits **0**. A fresh API35 run after installing the Kotlin-only fixture also reports `status=passed`, `busyReselection=passed-first-import-retained-second-picker-rejected`, `systemPicker=passed-open-cancel-without-media-read`, `syntheticResult=passed-real-callback-copy-host-clear-fixture-only`, `foreground=passed-no-input-no-auto-generation`, and `cleanup=passed-new-owned-directories-released`. Raw bundle and exit: `artifacts/verification/multimodal-android-kotlin-fixture-20261025/device-picker.log` and `device-picker.exit`. Actual system-file selection remains unverified; inference remains `unverified-no-android-backend`.
- Follow-up instrumentation now records `activityMidCopy=passed-activity-owned-import-active` before the second click, then confirms the same `busyReselection` result. The APK/test-APK rebuild and API35 run both exit **0**; raw output: `artifacts/verification/multimodal-android-activity-midcopy-20261025/device-picker.log`. This supplies the previously missing Activity-owned mid-copy evidence, while real system-file selection and Android inference remain unverified.

The authorized iPhone `<iPhone-UDID>` is currently connected and the iPhone Mirroring app can display the physical Home screen. Launching the installed Demo with `devicectl` succeeds, but the mirror did not bring the Demo to a visible foreground window during this attempt; physical touch and real system-file selection therefore remain **unverified**. No private media was read and no protected file or signing asset was changed.

## Latest resume — 2026-10-24 physical-touch/system-picker feasibility probe

`devicectl` and `xctrace` both report the authorized iPhone `<iPhone-UDID>` as connected. The local `cua_repl` app inventory exposes `com.apple.ScreenContinuity`, but binding the iPhone Mirroring app returns `cgWindowNotFound`; no mirror window is available for a physical-touch or real-system-picker action. No new app bundle, signing state, source, or private media was touched. The existing active report therefore remains explicitly `touchAcceptance=unverified` and `systemFileSelection=unverified`; AX/injected picker callbacks and device connectivity do not substitute for those gates. Roadmap remains **5/12, P9 active**.

## Latest resume — 2026-10-24 dirty-source picker and image-only capability revalidation

The authorized physical iPhone `<iPhone-UDID>` is connected again. The current dirty source was rebuilt and installed with the existing Team `4X996UV8LC`, fixed cached packages, the project-owned fixed model, and the public `fixture.png`; no protected file, signing asset, private media, or remote ref was changed.

- Current source provenance is HEAD `9288ac5a28dd5e2e55a23f9be9d5ec8108199fc4` plus the uncommitted product/probe source diff SHA256 `a71a6a758e349b4d48d71bff7163380251a928fedbaeea31cfd38b879c8597f9`. The launch argument retains `--sdk-commit=9288ac5a28dd5e2e55a23f9be9d5ec8108199fc4`; the dirty diff hash is the exact additional provenance for this run.
- The signed generic device build succeeds: `artifacts/verification/multimodal-mlx-full-app-9288ac5-20261024/resume3-20261024/signed-device.xcresult`. The app was installed as `io.ondeviceai.multimodalmlxdemo`, and the fixed fixture hash is `60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606`.
- Raw active report: `artifacts/verification/multimodal-mlx-full-app-9288ac5-20261024/resume3-20261024/demo-active-integration.json`. It is `status=passed` with `bootstrap`, `available`, `pageOpened`, `inputAction`, delayed `pickerReply`, `activeCancel`, `activeLeave`, `noAutoRestart`, `reentry`, `returnReentry`, and `closed` all true. Cancel and leave terminate as `CANCELLED`; both reentry scenarios terminate as `SUCCEEDED` through `mlx.smolvlm2-500m`, model `mlx-community/SmolVLM2-500M-Video-Instruct-mlx`, revision `fa57db46815177fbdfd65cc85a2b3416a8332268`, runtime `6e338e5df49424fc0db7c6d58184d622f92ed18a`.
- This revalidation also includes the branch-review fix for optional video UI: the shared screen checks an explicit `supportsVideoInput` capability backed by both video store and picker, and Android/iOS image-only host regressions are covered. The iOS bridge/store/factory suite is **72/72, zero failures/skips** in `multimodal-ios-store-resume-20261024/resume6-20261024/tests.xcresult`; the full Android Demo unit suite is **38/38, zero failures/errors/skips**, and shared `allTests` is green.
- `touchAcceptance=unverified`, `systemFileSelection=unverified`, and `osBackground=unverified` remain explicit. The report validates app-local accessibility/injected picker callbacks and the host/model lifecycle; it is not physical touch, real system file selection, OS background, natural video quality, Android inference, resource/energy, minimum-OS, binary archive, or whole-branch/P9 acceptance.

Roadmap remains **5/12, P9 active**. No commit, stage, push, merge, or release was performed.

## Latest resume — 2026-10-24 KMP bridge fixed-video device closure

The authorized physical iPhone `<iPhone-UDID>` is connected again. The current KMP bridge device evidence is now complete in an artifact-only consumer; no product source, signing asset, protected file, or remote ref was changed.

- The first temporary consumer build failed because its cached `ODAIKitBinding.framework` still exported the old `VideoBridge*` symbols while the current `IOSMultimodalBridge.swift` requires `MultimodalBridge*`. Rebuilding the existing framework offline with `rtk proxy ./gradlew :kmp-binding:linkDebugFrameworkIosArm64 --offline --console=plain` succeeded and the regenerated header contains `MultimodalBridge*`. The original failed consumer log remains under `artifacts/verification/multimodal-kmp-video-device-20261024/generic-build.log`; retry logs are retained.
- The artifact-only `KMPVideoConsumer` was then signed with the existing team `4X996UV8LC`; retry5 generic iOS build passed (`generic-build.exit=0`, `retry5-20261024/generic.xcresult`). It links the current `IOSMultimodalBridge.swift`/`IOSMultimodalInput.swift`, the regenerated `ODAIKitBinding.framework`, and the fixed cached Apple packages. The temporary source has no tracked product counterpart.
- Only the project-owned fixed model was copied from the prior native consumer app container and the public fixture `apple-mlx/Tests/ODAIKitAppleMLXVLMTests/Fixtures/video-29fps.mp4` was copied into the KMP consumer Documents. No private media was read. The app was installed and launched on iPhone17,2 / iOS 27.0.1 build 24A446 with `--sdk-commit=9288ac5a28dd5e2e55a23f9be9d5ec8108199fc4`.
- Raw bridge report: `artifacts/verification/multimodal-kmp-video-device-20261024/sdk-kmp-mlxvlm-video.json`; strict copy: `strict-kmp-summary.json`. `rtk proxy python3 tools/mlxvlm_video_report.py ...` exits **0**. It records success, first-update cancellation with drain, same-PID reentry, close rejection for image/video, lease protection before/after cancellation, lease release after close, eight preserved frames with exact CMTime values, unchanged source hash, and zero owned snapshots remaining. `runId=13B17E44-6145-4568-8C62-6A4F670CA64E`, `pid=22856`, backend `mlx.smolvlm2-500m`, fixture hash `9786524a69579ffd69d75c49048a566f31eaec090b1487b69aa4ce6d55e1ee51`.
- This is current KMP `IOSMultimodalBridge` to fixed Apple MLXVLM runtime/native lifecycle evidence. It does not claim Demo real picker/physical touch acceptance, Android inference, natural-video quality, Metal/GPU/energy, minimum-OS, binary archive, or broad P9 completion. Roadmap remains **5/12, P9 active**; the prior native-direct consumer evidence remains separately labeled.

2026-10-24. Implemented in foundation feat/sdk-foundation from 8d2cd9c. User approved independent image/video stores and one shared state machine. Changes are local uncommitted; no push/merge/release. Fresh branch-wide review after the capability/factory fixes found no CRITICAL/HIGH/MEDIUM issues; only the known XCTest QoS priority-inversion LOW watchlist remains. This scoped review is not a whole-product/P9 approval. Still 5/12, P9 active.

## Resume — 2026-10-24 local compilation and regression

This section supersedes earlier current-state statements about the macro sandbox blocker and unresolved store/factory tests. HEAD remains `9288ac5a28dd5e2e55a23f9be9d5ec8108199fc4`; the existing dirty Android fixes and protected files were retained. New changes are also uncommitted, with no push/merge/release or device installation.

- The current minimal `sandbox-exec` probe exits 0. Replaying the prior full enhanced MLX App build with the same generated project, fixed copied dependency cache, resolution disabled and existing manifest setting succeeds, exit 0. A second post-store-fix build also succeeds. No compiler macro/interface verification was disabled, no dependency/upstream/signing changes were made. The precise external policy behind the prior sandbox failure remains unknown; it is not reproduced in the current environment. Keep the original exit65 log.
- Build evidence: `artifacts/verification/multimodal-mlx-full-app-9288ac5-20261024/resume-20261024/{unsigned-generic-ios,post-store-fix}.{log,exit}` and both xcresults. All 12 cached checkout revisions and clean tracked states match the prior inventory; generated and package lock SHA256 remain `e67e985970770991f1eb2384ca9e9b6267fb8a8f1569051be5cfb2175f0ef858`.
- The build consumes the current dirty source. The generated validation label still names HEAD `9288ac5`; it must not be used as exact source provenance for a later device inference report. Regenerate/freeze source and hash provenance before that run. `artifacts/verification/multimodal-resume-final-20261024/snapshot.json` records dirty-source hashes, locks, checkout identities, empty index and protected hashes.
- iOS store compatibility restored by creating only missing parents recursively, then retaining exclusive final-directory creation. RED: 70 tests, two expected missing-parent failures. An over-broad recursive final-directory attempt exposed the existing foreign-sentinel regression and was replaced. GREEN: 70/70. Added video-store-construction failure coverage proves image-store removal, preservation of the blocking foreign file and model-lease release via successful ModelStore removal. Logs: `artifacts/verification/multimodal-ios-store-resume-20261024/{red,green,final}.log`.
- Factory typed-video coverage exercises the actual generated Demo VM, asynchronous private video store, video-only provider, native typed backend, raw prompt, output, retained model lease and close cleanup, with synthetic bytes and an injected backend. It is not a decoder or weight inference test. The first attempt had test-code compile errors; the next 71-test run failed only on URL directory trailing-slash equality. Both logs are retained (`video-provider.log`, `video-provider-final.log`); the assertion compares parent paths without changing production routing. The first all-green test process stalled in post-test `simctl diagnose`; its exact diagnostic child was verified and terminated, then xcodebuild returned 0. A fresh final run uses the documented `-collect-test-diagnostics never` (test assertions and compiler checks remain enabled): **71/71, zero failures/skips, exit0**, confirmed from xcresult summary. Final evidence: `verified-final.log`, `verified-final.exit`, `verified-final.xcresult`; command/help/sample and earlier failed/diagnostic logs are retained. Existing semaphore-test QoS runtime warnings remain; they are not failures.
- Fresh Binding-only generated ABI project: 23/23, no failures; `artifacts/verification/multimodal-binding-resume-20261024/{test.log,binding.xcresult}`. Android deterministic two-dispatcher test pauses after successful IO copy but before UI publication, then clears selection and checks token and directory reclamation. Offline full Demo unit/APK/test-APK/lint passes with 36 tests (`artifacts/verification/multimodal-resume-final-20261024/android.log`). A subsequent added source-owner regression cancels the caller during `NonCancellable { awaitImports() }`, uses real `MultimodalPickerCopy` and host with queued IO/UI, proves source survival until join then staging cleanup. Final full Demo unit suite **37/37, zero failures/errors/skips**, independently counted from XML; raw `artifacts/verification/multimodal-android-cancel-resume-20261024/demo-unit-full.log`. This reconstructs owner/join behavior and is distinct from Activity-owned mid-copy picker instrumentation.
- Independent read-only review approves the Android busy-selection repair with one LOW remaining Activity busy-state/mid-copy coverage gap. A second bounded independent review approves the iOS parent/factory and Android two-queue test slice, with no findings. These are scoped approvals, not whole-branch/P9 approval. Review initially encountered 429; later review results were returned and code/logs inspected by the main session.
- Fresh device inventory: the previously authorized iPhone16ProMax `<iPhone-UDID>` is **unavailable**; another physical device is connected without DDI and was not used. No new installation, signing change, private media access or weight inference. Raw `artifacts/verification/multimodal-resume-final-20261024/devices.log`. KMP-to-fixed-video-weight success/cancel/drain/reentry/close and current iOS real picker/UI remain unverified until the authorized device is available.

Still 5/12, P9 active. Original upstream 8->7, short-sample rejection, enhanced binary archive exit65, natural temporal quality, GPU/energy, minOS, Android production backend, old Video UI and P6 gates remain open. Earlier implementation and failure records below are historical and are not erased by these local passes.

## Implementation

- Shared MultimodalInputKind IMAGE/VIDEO appended to state; selectVideo preserves raw prompt, opaque token, budget and strict selection. Same-token media switch cancels the pending request and waits for terminal. Video dispatch uses only optional MultimodalVideoBridge; legacy bridge rejects BACKEND_UNAVAILABLE, no image fallback. Existing close/cancel/update filtering retained.
- Optional MultimodalVideoDemoActions preserves old image-only conformers. UI adds video selection, experimental heading and profile disclaimer; old tags remain.
- Both hosts accept optional independent video store/picker. One picker generation and selected token across media; previous store retirement keeps independent operation leases alive. Old image call sites remain valid. Android Activity shares the guarded GetContent launcher using image/* or video/*, bounded initial IO staging and default unavailable backend.
- Apple picker uses .movie for video; default Demo and optional MLX factory configure distinct stores. Model host factory injects videoInputs and cleans image store if video-store creation fails.
- Android private copy is bounded to 32MiB and rejects empty input; Apple MultimodalInputStore now owns its registry directly with O_NOFOLLOW descriptor/fstat regular-file checks, exclusive private destination, 64KiB streaming, 32MiB cap, cancellation checkpoints, partial-copy retry ownership and idempotent leases. Neither store interprets token as path.

## Fresh verification

| Gate | Result |
| --- | --- |
| Shared new tests | Missing selectVideo/inputKind compile RED, then 64 tests / 5 suites, zero failures/errors/skips; 4 new VM tests |
| KMP Demo framework | iosSimulatorArm64 debug link passed |
| Android host/store | Missing optional host and maxBytes APIs compile RED; final Demo 29 tests / 6 suites, zero failures/errors/skips; 3 new tests |
| Android build/lint | offline testDebugUnitTest + assembleDebug + lintDebug successful, 539 tasks (24 executed, 515 up-to-date) |
| Swift Demo ABI | Missing host/store/picker/factory APIs each real compile RED; final 59 XCTest, zero failures; 4 new tests |
| Full base iOS Demo | Fresh generated SpeechDemo, multimodal/video enabled, generic simulator unsigned build successful |
| Tools | Standard launch first had PYTHONPATH/import errors; corrected path gave 133 tests with one manifest sandbox error. Process-local wrapper adds --disable-sandbox only to swift package dump-package: 133/133 passed. Repository tests unchanged for this workaround. Not an unqualified standard-command pass |
| Checks | changed core Swift strict format and git diff --check passed; index empty; both protected SHA256 unchanged |

Xcode initially failed sandbox-exec sandbox_apply Operation not permitted, including outside-sandbox retry. -IDEPackageSupportDisableManifestSandbox=YES allowed local base-package compilation, with automatic resolution disabled and skipPackageUpdates. No dependency download. Raw xcresults retain compile RED and an intermediate 59-test run with one old image test failure. That old test expected only [2], but asynchronous MainActor hops can legitimately deliver [1,2]. Test now asserts strictly increasing sequence ending at 2 and unchanged updates after terminal; no bridge product logic was changed. Final 59/59 passed.

Evidence: artifacts/verification/multimodal-video-demo-red-20261024/{build/Logs/Test,final-xcode-test.log,tools-no-manifest-sandbox.log,base-package.json}; full base app at multimodal-video-demo-app-20261024/build.log. Gradle XML: demo/shared/build/test-results/iosSimulatorArm64Test and demo/androidApp/build/test-results/testDebugUnitTest. RED Gradle outputs are session execution evidence, not separately archived raw log files.

Protected files remain original: project.pbxproj b35bd984f784d4b13c0724f8c595d28ed96ddbe418e049547b762309fe9fd8f5; face-identity.md 6f130bd94debd5e8bffaba7b0db560553f7a367818d9cf37b72468e42bb70822. Both un-staged.

## 10:10 connected-device follow-up

User confirmed both devices available. Fresh devicectl/adb confirmed physical iPhone16ProMax iOS27.0.1 and TECNO KM9 Android15/API35. No minimum-system conclusion.

- Android existing MultimodalScreenInstrumentation initially returned status=failed at unavailable-input-name because old test expected 尚未选择图片 but current UI says 尚未选择输入. Updated that assertion and extended the actual shared page test with video action, independent store token rejection, default BACKEND_UNAVAILABLE, clear and owned-file cleanup. Tiny [1,2,3] synthetic bytes are not a decodable video. Real API35 execution returned status=passed and videoInputActions=passed-video-select-isolation-unavailable-clear-cleanup-synthetic-bytes-only; existing opt-out, raw edits/cancel/drain, fixture output identity, failures, cross-page and image input checks passed. This is accessibility-action UI instrumentation, not physical touch or real system document selection. Raw: android-device-ui.log in this checkpoint artifact directory. Only project app/test APKs installed.
- Refreshed :demo:shared:linkDebugFrameworkIosArm64 offline. Fresh basic generated iOS device build succeeded with existing DEVELOPMENT_TEAM=4X996UV8LC, without provisioning-update flags or signing asset changes. Installed and launched io.ondeviceai.ondeviceaidemo on the connected iPhone. device-build.log is under multimodal-video-demo-app-20261024. No screenshot/private media/recording/model inference performed. Launch success is not UI/video validation.
- All code remains uncommitted and no remote writes. Next order remains staging responsiveness/cancellation -> enhanced fixed-cache build -> separately evidenced KMP video weight lifecycle. Remaining P4/P6/P7/P10/P11 gates do not disappear because modern devices are connected.

## Follow-up: Android cancellable store prerequisite

Fresh continuation from unchanged HEAD 8d2cd9c; all changes still local and uncommitted. Android import previously held its registry monitor across the entire bounded copy, so simply dispatching the call to IO could still block UI-side close/acquire/remove. MultimodalInputStore now reserves an in-flight import under its lock, copies outside that lock, checks cancellation/closed state at chunk boundaries, and publishes only under the lock while open. In-flight ownership keeps the private directory alive until cleanup completes. Failure retains owned partial files for deletion retry; operation leases are unchanged.

Two new deterministic tests cover cancellation after one written chunk with no partial file, and a latch-paused copy while close returns, an older operation lease remains valid/releasable, the directory stays alive for the worker, then the worker rejects publication and reclaims the final directory. Real compile RED: the old importImage API rejected the new checkpoint argument at both call sites. RED output is retained in session execution, not a separately archived raw file. GREEN and strengthened lease assertions: offline testDebugUnitTest + assembleDebug + lintDebug exit 0, JUnit 31 tests / 6 suites / 0 failures/errors/skips. Raw GREEN: artifacts/verification/multimodal-video-demo-red-20261024/android-store-cancellable-green.log.

This is not asynchronous host completion: both hosts still invoke their final copies synchronously. Android staging task and picker-temp-file lifetime/join, and Apple worker/MainActor registry split remain next. No new device test, iOS build, model inference, remote write, or independent review in this increment. Protected hashes rechecked unchanged.

## Follow-up: asynchronous host wiring and regression

User explicitly authorized parallel work. Initial iOS implementation dispatch hit 429; a later isolated iOS worker completed changes, subsequently inspected and rerun by the main session. Neither its claimed results nor its notification summaries were accepted without filesystem/log checks.

Android host now optionally accepts importScope/importDispatcher; the actual Activity supplies lifecycleScope and IO staging. The no-scope path remains synchronous for source-compatible embedded/test hosts. Imports have cancellation, generation-gated UI publication, stale-token retirement, and cleanup if withContext cancels after successful copy before delivery. clear/leave/non-preserved background/close invalidate imports. awaitImports joins worker completion; Activity holds the picker-owned temporary copy through a NonCancellable join before closing it. Tests cover queued cancellation/lifecycle boundaries and real IO success. Missing scope/dispatcher/awaitImports produced compile RED; final Demo JUnit 34/34, zero failure/error/skip. APK/lint passed after production wiring; the last additional lifecycle-boundary test also passed. Logs: multimodal-video-demo-red-20261024/android-host-async-{green,final}.log.

Apple store adds startImport with detached POSIX copy and MainActor registry completion, explicit worker cancellation forwarding, in-flight directory ownership, owned-destination tracking, and security-scoped source access held until worker termination. Synchronous importImage remains for compatibility; host defaults to asynchronous startImport. Host invalidates imports on lifecycle/selection changes and retires late successful tokens; stale failures cannot overwrite current state. Existing host/factory tests now await actual input tasks rather than assuming synchronous picker callback completion. Main session ran the original complete generated Demo ABI project: 67 tests, zero failures. Raw author-full.log and xcresult under artifacts/verification/multimodal-ios-async-20261024. Xcode emits SDK build-version diagnostic warnings; these are not test failures.

This closes the implementation/unit-regression increment, not P9 overall. Current asynchronous UI has not been rerun on physical devices/system pickers; enhanced MLX app build and KMP-to-fixed-weight video lifecycle remain pending. Shared 64 and previous device results are historical, not fresh tests of these host changes. No new commit/push; protected hashes unchanged; independent review remains pending.

## Post-9288ac5 review and physical Activity follow-up

Independent read-only review found a major Android interaction race: host cancelled the accepted import before Activity's busy picker guard rejected a replacement. Fixed with a UI-serialized canSelectInput admission check before invalidation, wired to the Activity picker/import busy state. Missing admission API produced real compile RED (busy-selection-red.log); new deterministic queued-import test verifies rejected selection preserves the accepted import. Updated three stale input-label expectations in the separate real-picker instrumentation. Offline Demo unit/APK/test-APK/lint command succeeded; Fresh XML totals verified: 35 tests, zero failures/errors/skips.

On connected TECNO KM9, installed only project app/test APKs and ran MultimodalPickerInstrumentation. Raw async-device-picker.log reports status=passed: real system picker open/cancel without reading media; synthetic content-provider Activity result through real callback, asynchronous copy, host selection/unavailable/clear; foreground no automatic generation; new owned directories removed. actualSelectedSystemFile remains explicitly unverified, inference unverified-no-android-backend. This is not actual system file selection, video decode or mid-copy physical cancellation evidence.

Enhanced MLX unsigned full-App build used a fresh generated project and existing copied fixed caches, but failed exit65: sandbox-exec sandbox_apply Operation not permitted; SwiftMacros.TaskLocalMacro could not be loaded from swift-plugin-server, followed by derived MLXRandom errors. Raw: artifacts/verification/multimodal-mlx-full-app-9288ac5-20261024/unsigned-generic-ios-build.log. No dependency, macro verification, or signing changes were used to bypass it. KMP video weights/device closure remains blocked on enhanced build. Review also noted iOS missing-parent-directory compatibility and factory video-generation/failure-path coverage gaps; these are unresolved, not APPROVE. Follow-up changes are not yet committed/pushed.

## Remaining gates / next package

Current local compile/store/factory/ABI checkpoint is verified above; it is not end-to-end video acceptance. No model weights were run in this resume. No current iOS physical picker/UI or KMP-to-fixed-video-weight lifecycle acceptance; the authorized iPhone is unavailable. Full all-module Android regression was not rerun (Demo units/APK/test-APK/lint were). Android Activity-owned mid-copy/reselection instrumentation remains the LOW review gap. Scoped independent approvals do not replace final whole-branch review.

Next: recover the already-authorized iPhone, recheck worktree/protected hashes and exact source provenance, generate a fresh validation App from frozen sources with existing signing and fixed caches, then separately verify KMP->fixed-weight video success/cancel/drain/reentry/close with the public synthetic profile fixture and strict report. Preserve Android production-backend, original upstream 8->7, short-sample rejection, binary archive exit65, natural temporal quality, GPU/energy, minOS, real picker, old Video UI and P6 gates.
## 2026-10-24 iPhone 镜像重试（仍为外部门槛）

本轮从 `com.apple.ScreenContinuity` 启动 iPhone 镜像并点击“连接”重试；窗口先显示正在连接授权 iPhone，随后显示“已超时 iPhone 镜像连接超时”。因此没有可执行的物理触摸或真实系统 picker 操作，`touchAcceptance` 与 `systemFileSelection` 继续为 `unverified`。本轮未安装/修改产品 App、未读取私人媒体、未变更签名；AX/injected picker 证据不升级为真实 UI 验收。
