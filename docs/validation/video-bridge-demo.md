# KMP Video Bridge / Demo 验证

更新：2026-10-21。工作树 `.worktrees/foundation` / `feat/sdk-foundation`；本包基线 `ae2becd`。这是本地实现及分层验证记录，P7 整体未完成。

## 实现与提交

| Task | 提交 | 交付 |
| --- | --- | --- |
| 1 | `9bad71d` | 独立 VideoBridge 契约、opaque input/output token、默认 preserve、严格后端选择、明确 unavailable |
| 2 | `e522c9d` | Android adapter，实际 client 的取消/drain、close/query、输入和输出 lease |
| 3 | `fd19f2c` | MainActor iOS adapter、Binding/Demo 两种 Kotlin→Swift ABI fixture |
| 4 | `aa0e837` | VideoViewModel/VideoScreen、独立视频 tab、兼容旧 iOS 入口 |
| 5 | `3b0560c` | iOS 私有输入副本、用户文件选择、独立播放/分享副本；Android unavailable host；独立生成工程 opt-in |
| 6 | 本记录及同次提交 | opt-in 视频 UI 用例、Android unavailable smoke、完整本地门禁、未通过项与恢复入口 |

规格：[Video Bridge design](../superpowers/specs/2026-10-21-video-bridge-demo-design.md)；计划：[实施计划](../superpowers/plans/2026-10-21-video-bridge-demo.md)。原始日志与 ledger：`.superpowers/sdd/2026-10-21-video-bridge-demo/`（忽略目录，保留供续接）。

common 不传路径或 URL。原生输入 lease 持有到 native drain；取消期间保持 busy，晚到输出由 adapter 释放。close 不撤销已交付的输出；显式 release 失败可重试，成功后幂等。离页/后台取消且不自动重启，结果替换/离页显式释放。iOS 播放/分享持有独立临时副本，避免 VM 后台清理结果时破坏读取。

基础下限保持 iOS15 / Android API26。AppleVideo 仅非模拟器 iOS26+、模型 ready、320×180/24fps/1…48帧/BT.709 SDR、4× 输出；默认 preserve 的 AAC 约束仍见[音轨验证](apple-video-audio.md)。Android 没有真实视频后端：GetContent 返回选择后仅生成 opaque token，不读取/保存 URI 或复制媒体，处理明确 unavailable。

## 本地门禁

| 门禁 | 结果与证据 |
| --- | --- |
| 公共契约 | 4/4；binding allTests、simulator framework 成功，Task1 日志 |
| Android adapter/client | adapter10/10、native client7/7；Task2 full video tests/lint/binding/shared278tasks成功 |
| iOS adapter ABI | Binding6/6、Demo6/6；Task3 fixture 日志，不能用早期 package baseline build 代替缺类型 RED |
| 共享状态机 | 8/8；shared allTests/framework 成功，Task4 日志 |
| iOS adapter + host | Demo ABI9/9（adapter6+host3）；Task5 日志 |
| Android host | 3/3，含显式 picker stop/start、取消保留输入、迟到回调拒绝；Task5 日志 |
| 工具 | 83/83，`task-5-tools-ruby34.log`；显式 `RUBY=/Users/daubert/.rvm/wrappers/ruby-3.4.11/ruby`。首次默认系统 Ruby2.6 混用3.4 gem 的3失败/9错误保留在 `task-5-tools-recheck.log`，修正环境后通过 |
| 最终 Android host 改动 | unit/lint/APK492tasks成功（16执行），`task-5-android-recheck.log` |
| iOS 生成 Demo | unsigned simulator、使用已有签名的 device build 成功；`task-5-ios-{simulator,device}-build.log` |
| Apple 总门禁 | `task-6-apple-gate.log` 成功；Swift447/447、KMP测试/framework、分发工具13/13、bridge、相机/照片/方向/ASR probe、原生 consumer/原 Demo 构建成功。Swift 测试完整日志 `artifacts/verification/swift-tests.log`，末尾 Swift Testing 的0tests不代替 XCTest 汇总 |
| Android 总门禁 | `task-6-android-gate.log`，625tasks成功（14执行、611 up-to-date）：JVM tests、lint、APK及native/identity consumer |

本包不修改原生 SDK 源码，不重复生成九 XCFramework；Core+Video 独立消费者/weak VT/本地分发仍使用前包对应的[音轨分发证据](apple-video-audio.md)。新增 KMP 桥接 ABI、宿主和生成工程分别验证，不能把前包设备 SDK suite 当作新 Demo UI 验收。

## 设备证据与明确缺口

Android：TECNO KM9 / API35，serial `15365375C9007986`。`VideoDemoInstrumentation` 使用真实 MainActivity/Compose accessibility：明确 unavailable、实际打开 GetContent 后取消且无输入、后台返回后仍 IDLE/处理按钮禁用。第一次未滚动至 unavailable 状态失败；用例增加滚动定位后 `task-6-android-ui-scroll.log` 返回 `status=passed`、三个检查 passed，`processing=unverified-no-android-backend`。设备结果必须读取 Bundle 字段，不能只看 instrumentation code或进程退出码。最终包含点击成功断言的版本复验也通过：`task-6-android-ui-final.log` 的三个检查 passed、status=passed；对应runner构建/lint为501tasks成功（9执行），`task-6-android-ui-final-build.log`。

这不覆盖 Android 实际选中结果的 ActivityResult 设备往返、真实视频处理、输出播放/分享或处理期间取消；host 单元测试不代替这些设备项。测试不选择或读取设备媒体，不下载模型或资源。

iOS：iPhone16ProMax / iOS27.0.1 (24A446)，UDID `00008140-0008703436D3001C`。新生成 Demo、UI target build-for-testing 和安装成功。固定输入是已有公开 Common swifts 视频加本地440Hz/48kHz AAC，320×180/24fps/48帧/BT.709 SDR/2秒，SHA256 `6a1c2a7bb875c8d9502450e732901fcd21bca5be27da0ef6ceae58c5b64c0d00`；只复制至本项目 App 的 Documents/odai-video-ui-fixture.mp4。

两次 UI 会话都在 runner 初始化阶段失败：`Timed out while enabling automation mode.` 结果 `video-ui-first.xcresult`、`video-ui-retry.xcresult` 位于 `artifacts/verification/video-demo-20261021/`。**0 个视频用例通过；不是产品断言失败，也不能算 UI 验收通过。** 新增两个 opt-in 测试已编译，实际导入/成功/保留AAC、播放/分享/清理、活动取消/drain/同输入重入/后台不重启均仍 unverified。

fixture 在 Files 中可见依靠忽略目录的测试 plist 暂时开启 UIFileSharingEnabled / LSSupportsOpeningDocumentsInPlace，生产 plist/生成器未开启。两次尝试后均恢复普通包；最终 `task-6-final-normal-{build,install}.log` 成功，构建后的 Info.plist 两个 sharing key 均不存在。fixture 保留在本项目 Documents，未删除其他文件。未修改签名资产，未用 allowProvisioningUpdates。

## 复跑与下一入口

```sh
rtk proxy ./gradlew :demo:shared:linkDebugFrameworkIosSimulatorArm64 :demo:shared:linkDebugFrameworkIosArm64
rtk proxy env ODAI_VIDEO_DEMO=1 ODAI_SPEECH_DEMO_DIR=artifacts/VideoDemo-fresh ruby scripts/generate-speech-demo.rb
rtk proxy ./gradlew :demo:androidApp:assembleDebugAndroidTest -PodaiTestRunner=io.ondeviceai.demo.VideoDemoInstrumentation --console=plain
rtk proxy adb shell am instrument -w io.ondeviceai.demo.test/io.ondeviceai.demo.VideoDemoInstrumentation
```

iOS 正常生成工程使用已有 team 可额外传 ODAI_DEVELOPMENT_TEAM。选择一个不存在的目录，生成器拒绝覆盖原输出。先构建/安装测试包，再用 devicectl 将上述固定 fixture 复制到本项目 App 的 Documents；不要读取私人媒体。测试仅在 `TEST_RUNNER_ODAI_VIDEO_FILE_UI_TEST=1` 时启用，使用 `-only-testing:SpeechDemoUITests/SpeechDemoUITests/testVideoFixtureSuccessPlaybackSharingAndCleanup` 与 `.../testVideoFixtureCancellationReentryAndBackground`。测试 plist 只放忽略目录，结束必须普通 build/install 恢复。

当前第一项未完成验收是 iOS Video Demo UI；先解决设备自动化会话初始化，再跑已编译用例。独立最终审查仍 pending：执行子代理多次在启动前429/capacity，未产生实现或审查结论；用户随后要求在当前会话执行，不再派子代理。本包仅作者按单独步骤范围审查，不声明独立 APPROVE。旧 Foundation/AppleVideo 首包/音轨修复复审缺口继续保留。P7 主观质量、SR传递函数、Android真实后端、最低系统和 native export 中途取消仍未验；P6真实麦克风终态、其余 roadmap 独立保留。

两个保护文件 SHA256 未变：受保护 `project.pbxproj` 为 `b35bd984f784d4b13c0724f8c595d28ed96ddbe418e049547b762309fe9fd8f5`；未追踪 `face-identity.md` 为 `6f130bd94debd5e8bffaba7b0db560553f7a367818d9cf37b72468e42bb70822`。两者均未暂存。无 push/发布/下载/录音/签名资产变更。
