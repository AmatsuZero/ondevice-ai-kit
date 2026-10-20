# Handoff — Speech Demo Bridge

更新时间：2026-10-20 14:47（Asia/Shanghai；真机验收通过，追加独立审查待恢复）

## 恢复位置

- 仓库：`/Users/daubert/Github/ondevice-ai-kit`
- 工作树：`/Users/daubert/Github/ondevice-ai-kit/.worktrees/foundation`
- 分支：`feat/sdk-foundation`
- Task 6 实现提交：`b272b76 feat: wire speech Demo hosts and isolated iOS project`
- 当前入口：Task 7 真机/本地门禁已通过；追加 UI/Done 独立审查因模型容量不足待恢复，尚不 task-done。不要从 Task 4 重新执行。
- 实施计划：`docs/superpowers/plans/2026-10-19-speech-demo-bridge.md`
- 规格：`docs/superpowers/specs/2026-10-19-speech-demo-bridge-design.md`
- 执行 ledger：`.superpowers/sdd/2026-10-19-speech-demo-bridge/progress.md`

Tasks 1–6 已提交到本地分支。Task 7 验收改动使用提交名 `test: validate speech Demo lifecycle and module isolation`；恢复时用 `git log -3 --oneline` / `git status --short` 查询最新提交和工作树，不依赖旧 HEAD 快照。受保护用户文件继续保留。尚未推送 GitHub 或极狐，远端同步待最终审查门禁。


## 已完成的计划任务

### Task 1 — 公共 Speech Bridge 契约

提交：`6788b1b feat: define typed speech bridge contracts`

已提供 Default/Explicit 后端选择、TTS/ASR capability、Microphone/AudioFile opaque 输入、后端描述、类型化请求/结果/回调、稳定 failure code、close callback 与 `UnavailableSpeechBridge`。Swift 导出 ABI 已通过实际 typecheck。

### Task 2 — 能力级后端查询

提交：`331de92 feat: expose capability-scoped speech backend queries`

Apple 与 Android 的 TTS/ASR facade 均可独立 `listBackends()`；结果按 backend ID 排序，保留 unavailable reason、minOS、offline，并继续由真实 operation admission 决定是否可执行。

### Task 3 — Android Speech adapter

提交：`8719a17 feat: adapt Android speech clients to KMP bridge`

新增 `AndroidSpeechBridge(tts, asr, scope)`，实现以下边界：

- TTS 与 ASR 共用一个音频任务槽；query 不占该槽。
- Explicit 选择严格执行，不回退 Default。
- 缺少单项 client 只影响对应 capability。
- Android ASR 只声明 `MICROPHONE`；`AudioFile` 在进入 native client 前返回 `UNSUPPORTED_INPUT`。
- cancel 等 native `finally`/drain 完成后清槽并发布一次 `cancelled`；drain 期间重入返回 `BUSY`。
- native 忽略取消后产生的迟到 success 不会覆盖 `cancelled`。
- adapter 的 worker Job 与宿主 scope 的 Job 解耦，宿主提前取消不会跳过 drain 或 client close。
- close 立即拒绝 query/TTS/ASR admission，等待所有 query/audio worker，再关闭拥有的 clients；并发 close waiter 共享结果。
- callback 交付在注入 scope 的 dispatcher；未知异常只输出稳定的 `android.speech/1`，不包含文本、转写或文件路径。

集成测试使用真实 `SpeechSynthesisClient` / `SpeechRecognitionClient` 和可控 backend，覆盖计划列出的九个生命周期、选择、输入和隐私用例。

## 最新验证

Tasks 4–6 已完成并本地提交：

- `7605511` iOS adapter/input lease，Binding/Demo ABI 各14/14；lease 等 native drain 后才释放，关闭 join 所有任务。
- `233873e` 共享 SpeechViewModel/页面，取消等待 terminal、分 capability 查询、严格 selection、离页清结果，旧 iOS 四参数入口保留。
- `b272b76` 双端 host/权限/独立 iOS 工程；不修改原受保护项目，实际系统后端仅由 Demo host 引入。

本轮 Task 7 的最新回归：

```sh
rtk proxy sh scripts/verify-apple.sh
rtk proxy sh scripts/verify-android.sh
rtk proxy ./gradlew :kmp-binding:allTests :demo:shared:allTests --console=plain
rtk proxy env RUBY=/Users/daubert/.rvm/wrappers/ruby-3.4.11/ruby python3 -m unittest discover -s tools/tests -v
```

以上通过：Swift378项、工具76项、Android验证569tasks；iOS adapter/Host20项、Android Host5项。fresh七个XCFramework和Face/Speech/Identity binary消费者、Speech Maven消费者构建/裁剪检查通过。含 up-to-date 命中，不声称全部重执行。日志在 ledger 同目录的 `task-7-*-final.log` 和 `task-7-apple-*.log`；完整边界见[语音 Demo 验证](validation/speech-demo-bridge.md)。

独立 reviewer 覆盖 `d6af05a..b272b76` 及 Task7代码，提出三项 Host 问题：iOS Host/VM引用环、Android权限等待中的重试误报拒绝、factory异常被当缺模块。均已锁定回归并修复，定向复审 CLEAR。受保护 pbxproj 不属于本轮 diff，未采纳恢复该文件的建议。

## 受保护的用户文件

以下改动属于用户，本包任务没有暂存或提交，最新 hash 复查仍一致：

- `demo/iosApp/OnDeviceAIDemo.xcodeproj/project.pbxproj`
  - 状态：modified
  - SHA-256：`b35bd984f784d4b13c0724f8c595d28ed96ddbe418e049547b762309fe9fd8f5`
- `docs/validation/face-identity.md`
  - 状态：untracked
  - SHA-256：`6f130bd94debd5e8bffaba7b0db560553f7a367818d9cf37b72468e42bb70822`

后续不得覆盖、暂存或提交这两个文件。Task 4–7 的生成工程、framework、APK、音频、模型和真机报告继续放入已忽略的 `artifacts/`。不要把超过 1 GB 的文件交给 Git 或 Git LFS；需要模型或大型资产时使用下载脚本。

## Task 7 最新真机证据与下一步

Task7 已 task-start，base `b272b76e8428eeb78340bc623960691cbf08b224`，尚未 task-done；文件 ASR 与最终全套 UI 已通过，追加独立审查未产生结果前保留最后门禁。

1. **iOS TTS 已通过完整真实页面闭环**：原多行输入打开键盘、输入、Done 收起、完成、活动取消、终态后重入、后台取消/前台不自动重启。证据：`task-7-ios-slow-drag.log`、`artifacts/verification/speech-demo-ui-20261020/tts-slow-drag.xcresult`，1/1 passed，73.656 秒；输入单测 `tts-rendered-stable-retry.xcresult` 也通过。
2. **焦点旧结论已纠正**：Compose 的离屏 AX 节点保留陈旧坐标，滚动惯性还会使截图后的位置继续变化；历史 `isHittable`/完整 AX frame 不足以证明实屏位置。当前 UI helper 用本地 Vision OCR 定位固定标签、等待 0.6 秒稳定和慢速边缘拖动。临时焦点/触摸计数及单行实验均已移除。唯一新增产品修改为多行 TTS 字段提供 `ImeAction.Done`/clearFocus；缺 Done 的 RED 为 `task-7-ios-done-red.log`。
3. **iOS 文件 ASR 完整闭环已通过**：真实系统 picker 导入、重开/取消保留输入、首次识别、活动取消、同一输入重入完成、后台取消/前台不重启，`task-7-ios-asr-file-ui-fast-cancel.log` / `asr-file-ui-fast-cancel.xcresult` 1/1 passed，40.535秒。最终完整5项UI套件 `task-7-ios-asr-file-ui-full-suite.log` / `asr-file-ui-full-suite.xcresult` **5/5 passed，0 skipped，141.919秒**；已用 xcresulttool summary核对。
4. 文件测试 opt-in 为 `TEST_RUNNER_ODAI_SPEECH_FILE_UI_TEST=1`。为真实 picker 暴露固定 fixture，只在忽略路径 `artifacts/verification/speech-ui-fixture-info.plist` 中覆盖 `UIFileSharingEnabled`/`LSSupportsOpeningDocumentsInPlace`；未修改生产 plist 或生成器分享设置。设备 Demo Documents 中唯一测试文件为 `odai-speech-ui-fixture.caf`，不选择私人文件。系统显示“我的iPhone”；Speech 授权弹窗归属 SpringBoard，不能只查询 App alerts。最终已恢复无 plist 覆盖的普通构建并安装：`task-7-ios-restore-normal-build.log` / `task-7-ios-restore-normal-install.log`；成包两项 sharing key 均不存在。Documents 的固定合成 fixture 保留用于复跑，未删除其他内容。
5. **Android TTS 已通过**：完成/取消/重入/后台取消与前台不启动，`task-7-android-ui-final.log`。ASR 仍 `BACKEND_UNAVAILABLE MISSING_ASSETS`，未采集自然语音。此前英语资源下载为单次显式授权，没有确认就绪，普通“继续”不能重用它安装资源。
6. Done 修改后的新鲜本地回归：shared allTests、Android unit/lint/APK、simulator framework 共468tasks成功；Python76/76；device framework重建成功。日志 `task-7-ui-shared-regression.log`、`task-7-ui-python-regression.log`、`task-7-done-framework.log`。先前全包本地分发/裁剪/Host审查证据继续有效，不无故重跑。
7. 独立 reviewer 曾对 Task1–6及Task7 Host修复给出定向 CLEAR；本次新 UI/Done 追加审查因模型容量不足，未产生新审查结论。作者已独立过读最终源码及断言，但不冒充独立 CLEAR。

## 下一会话最小恢复步骤

1. 读本文件/计划/ledger，核对 foundation 工作树和受保护 hash；不重做 Tasks1–6，不丢未提交 Task7 文件。
2. 优先恢复追加独立 review，只检查最终 UI/Done delta；先前整包 reviewer 的三项 Host 修复已定向 CLEAR，无需重做实现。测试 helper 不使用 AX 点击回退：固定标签 OCR，普通控件等待画面稳定；活动取消只取即时画面，避免等待期间任务已完成。OCR把“任务”读作“仟务”，因此匹配唯一片段“取消语音”。
3. review提出问题才按RED→GREEN修复并复跑相关门禁；否则不无故重复5项真机套件。原生/分发/Android及shared/Python门禁见上述日志；最终targeted Gradle427tasks（5执行）与生成器4/4也通过。
4. 审查通过后补Task7完成记录并串行核对/同步双方远端分支；此前用户已请求同步，本轮尚未执行，不force push。P6后续入口为独立iOS麦克风ASR设计，Android缺资源/自然质量/最低系统继续保留。

Task 7 文件分组（以恢复时 `git status` / 提交 diff 为准）：

- Host 修复和回归：Android `MainActivity.kt`、`SpeechHost.kt`、`SpeechHostTest.kt`；iOS `SpeechHost.swift`、`probes/ios-speech-bridge/SpeechHostTests.swift`；共享 `SpeechScreen.kt` 的 Done 动作。
- UI 验证/工程：新增 `demo/androidApp/src/androidTest/kotlin/io/ondeviceai/demo/SpeechDemoInstrumentation.kt`、`demo/iosApp/UITests/SpeechDemoUITests.swift`；修改 `scripts/generate-speech-demo.rb`、`tools/tests/test_speech_demo_project.py`。
- 文档：README、本 handoff、ROADMAP、`docs/validation/speech-backends.md`、新增 `docs/validation/speech-demo-bridge.md`。不要把受保护的 pbxproj/face-identity.md 混入本任务暂存列表。

固定合成英语音频在 `artifacts/P6IOSASR/fixture/asr-fixture.caf`；仅供后续实际文件选择/识别验证，不选择用户私人文件，不把自然录音、转写或 TTS 文本写入日志。此前原生探针成功不等于本 Demo 已验收。

## 仍需保留的 roadmap 缺口

这些项目不能因 Tasks 1–6 与本地分发回归通过而标为完成：

- iOS 15 真机、Android API 26 真机。
- Android ASR 真实转写；当前 TECNO KM9 / API 35 的 en-US 返回 `MISSING_ASSETS`。
- iOS 麦克风 ASR。
- 两端自然语音质量与断网验证。
- P7 Android 视频超分与完整 SDK/Demo 接入。
- P9 Android 第二平台与质量基准。
- P10 生产人脸模型、PAD、授权数据、阈值校准与最终独立审查。
- P11 统一 benchmark 与发布结论。

可用现代 Android 真机：TECNO KM9，API 35，ADB serial `15365375C9007986`。iPhone 16 Pro Max / iOS 27 已用于现有 iOS 验证；连接状态需在新会话重新检查。
