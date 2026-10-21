# Handoff — Speech Demo Bridge

更新时间：2026-10-21（Asia/Shanghai；恢复后追加语言 admission 修复并装机；中文自然输入与取消/重入仍待真机终态）

## 当前恢复检查点（2026-10-21，优先于后面的历史记录）

### 本次接手后的更新（优先于下方上午记录）

- 接手基线 `0526988`；现新增 Demo locale admission 修复，最新提交以 `git log -2 --oneline` 为准。真实 `SpeechRecognitionClient` 回归复现：默认 `en-US` 不可用会在 registry 阶段阻断已就绪的 `zh-CN`。修复移除默认英语实例：无请求 locale 的列表查询只检查共享 Speech 授权/关闭状态，按实际请求惰性创建 canonical locale backend，再由该原生 backend 严格校验本机就绪和权限，不回退语言。
- `task-6-locale-admission-red.log` 记录上述真实 client 断言失败，退出65；GREEN `task-6-locale-admission-green.log` **39/39**，覆盖文件/麦克风、不可用请求不回退、canonical cache/close、拒权和关闭后拒绝启动。native iOS fixture `task-6-resume-native-ios.log` **43/43**。均为测试输入，不是自然麦克风验收。
- 接手后、此 Demo Host 修复前的完整门禁：Swift **414/414**、Apple 全脚本门禁与consumer/原App构建成功；Android **569 tasks（22 executed、547 up-to-date）**；Python **77/77**。新增修复只涉及 Demo Host/Host fixture，不改 SDK/shared/Android；之后完整生成 Host fixture 与 signed device build 已通过。
- 设备已装新普通包：生成工程仍为 `artifacts/verification/microphone-locale-router-20261021/`，最新 build 输出为 `artifacts/verification/microphone-locale-admission-device-20261021/`。build/install 证据 `task-6-locale-admission-device-{build,install}.log`；无诊断/合成源，无 sharing/background audio key。此次未自动开始录音，中文终态仍未收到。
- 独立 `code-reviewer` 与 `architect` 调用均在执行前因模型容量不足失败；路由/1110/locale 与本次 admission 修复均仍无新的独立 APPROVE。作者按独立步骤审读修改与回归，不冒充独立审查。Task6/P6/roadmap 未关闭；两个保护文件hash不变。
- 本次只读查询：origin 分支仍为 `472ca9351eb69c09d863ef7bc267c280b068b846`；极狐查询返回 `SSL_ERROR_SYSCALL`，无法确认当前远端相等。未push。当前七XCFramework `artifacts/P6/microphone-locale-20261021/` 仍对应最新 SDK；本次只改Demo Host，无需重打相同 SDK 产物。

当前待办：先使用用户已回复的中文终态；若没有，等待用户主动在 `zh-CN` 下完成固定句测试，仅报告状态/错误码。真实完成后继续显式取消、同进程重入、TTS往返、后台取消/前台不重启及拒权恢复。独立复审恢复后覆盖三项上午修复和本次 admission delta。不要用“继续”当作设备成功报告，不重复 Tasks1–5 或已通过的整包门禁。

工作树 `.worktrees/foundation` / `feat/sdk-foundation`，语言路由实现提交 `09f5f6e fix: route iOS Demo speech by requested locale`；路由修复 `29b388d`、失败复测记录 `83d5443`、1110→NO_RESULT 修复 `027c7ad` 均已本地提交。恢复时以 `git log -2 --oneline` 核对最新 HEAD。麦克风 Tasks1–5 已完成，不重做；Task6、P6 与整个 roadmap **未完成**。计划 `docs/superpowers/plans/2026-10-20-ios-microphone-asr.md`，ledger `.superpowers/sdd/2026-10-20-ios-microphone-asr/progress.md`。

- 原 Task1–5、旧 Task7 独立审查已 APPROVE；原分发/Android/KMP/host门禁见[验证记录](validation/ios-microphone-asr.md)。路由及新的 1110 修复独立审查仍因模型容量不足未执行，没有新的 APPROVE；不推送远端。
- 首次真机 `FAILED PLATFORM` 两次均为 categoryChange(reason3)被无条件判失效。`29b388d` 仅放过与启动健康快照完全一致的 categoryChange，其余仍失效。新普通包安装后用户回报一次 `SUCCEEDED ASR 完成`。
- 随后的“取消/重入”用户回报都 FAILED。成功接入诊断后，实际两轮日志均为 `finishInput`、没有 `cancel`，错误为 `kAFAssistantErrorDomain/1110`。先一轮0.77秒/24000frames，后一轮4.63秒/211200frames（4.4秒PCM），均有非零float样本。**不能把较长一轮失败仅归于录音过短，也不能将非零样本等同可识别语音。** 录音/转写未保存或导出。
- Apple SDK `SFSpeechRecognitionTask.h` 的官方错误表明确1110为“Failed to recognize any speech”；新的窄修复映射既有 `.noResult`，不伪报成功。显式取消保持取消、其他domain/code保持平台错误。新增2测试先断言RED再GREEN；Swift全套414项0失败，iOS native fixture43/43。日志 `task-6-no-speech-{red,green,swift-full-final,native}.log`。
- 自动合成对照不使用麦克风：同一固定CAF，文件识别成功；最初复用可变buffer的临时探针1110（探针实现问题），修正为独立buffer后，整段/实时、不保留数组、iOS27 readonly-copy和48kHz均成功。日志 `task-6-{synthetic-comparison,buffer-variants,buffer-copy}-events.log`。**不把原探针失败解释为系统buffer识别不支持。**
- 进一步在忽略目录的 SDK副本仅替换 Engine 为合成PCM源，保留生产backend/delegate/session/finish/drain/release：首次完成、取消、同进程重入和再次完成成功。日志 `task-6-native-synthetic{-lifecycle,}-events.log`。这是真实系统识别+合成源，不是实际麦克风或UI取消验收。
- 诊断工程 `artifacts/verification/microphone-reentry-diagnostic-20261021/` 内含 bridge/session副本、`apple-diagnostic/`和合成探针，均不得提交。最初工程引用替换未命中相对路径导致诊断未生效，已用xcodeproj真实引用修正并确认 `ODAI_MIC_DIAGNOSTIC ready`；可靠现场日志为 `task-6-reentry-buffer-events.log`。
- 结束自动对照后曾恢复普通生成工程 `artifacts/verification/microphone-live-fix-20261021/`。目前设备上改为新普通 locale-router 包 `artifacts/verification/microphone-locale-router-20261021/`，生产源码、无合成输入或诊断源码；构建/安装见 `task-6-locale-device-{build,install}.log`。原七XCFramework `artifacts/P6/microphone-20261021/` 已被新鲜分发 `artifacts/P6/microphone-locale-20261021/` 取代；新包两个 archive、七个 XCFramework 与 Speech binary consumer 均成功，见 `task-6-locale-{xcframeworks,speech-consumer}.log`。没有发布或上传。
- 两个受保护文件及hash见下方，未覆盖/暂存/提交。没有push、force-push、语言资源安装、签名账户资产/网络配置变更。iPhone UDID `00008140-0008703436D3001C`，沿用team `4X996UV8LC`，bundle `io.ondeviceai.ondeviceaidemo`。
- 用户确认失败轮次使用中文或中英混合；此前 Demo Host 却将 Apple 识别器固定为 `en-US`。现已加入 Demo 内部 `SpeechLocaleRoutingBackend`：保持稳定 `apple.speech.recognizer` ID，按请求 locale 缓存 Apple backend，共用同一个 `DemoSpeechAudioSessionProvider`，不回退语言；共享 UI 文案改为按请求语言。新增 locale 路由回归后生成桥接/Host/Session 测试 **36/36** 通过（RED 日志 `task-6-locale-router-red.log`，GREEN `task-6-locale-router-green-final.log`），shared framework/allTests 成功（`task-6-locale-shared.log`），真机包构建成功并安装（`task-6-locale-device-build.log` / `task-6-locale-device-install.log`）。

下一会话从这里继续：先核对 `git status`、HEAD、受保护哈希及设备连接，不重做已通过的 Tasks1–5。设备已安装并启动 locale-router 生产包；让用户在语音页把“语言 / locale”设为 `zh-CN`，主动开始麦克风 ASR、朗读固定句“今天我们测试手机上的语音识别功能”，结束录音后只报告状态/错误码，不发送转写文本。若本会话末尾的异步提问已有答复，先使用答复，避免重复要求朗读。忽略目录中文诊断包仅确认 `zh-CN` recognizer `available=true onDevice=true`（`task-6-zh-cn-events.log`），尚无现场中文朗读终态。随后以真实完成结果验证取消、同进程重入、后台取消与前台不自动重启；诊断须只记状态/标量，不保存音频/自然转写。当前路由/1110/locale 修复复审调用均因模型容量不足未执行，**没有新的独立 APPROVE**；可用时做一次针对 `29b388d`、`027c7ad`、`09f5f6e` 的独立复审。iOS15、Android API26/ASR MISSING_ASSETS、自然质量与断网、P7/P9/P10/P11缺口保留。无远端 push。

以下为旧 Speech Demo 包的历史交接，旧“当前入口/HEAD”不覆盖本检查点；旧Task7追加审查和原始证据仍有效。

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

### Roadmap 后续设计已启动

在 Task 7 追加独立审查仍不可用期间，已完成下一包 [iOS 麦克风 ASR 设计](superpowers/specs/2026-10-20-ios-microphone-asr-design.md)。它保留当前文件 API，提出独立 microphone task、finish/cancel 分离、显式 session lease、权限与 drain 门禁，并记录 Apple DocC/SDK 依据。按用户“逐项实施、自动进入下一项”的 roadmap 约定推进本地契约测试与实现，不把这当成人工逐字批准或验收通过。新包的录音、真机与独立审查分别记录；旧 Task 7 的审查与双远端同步门禁仍保留。恢复时读新包计划/ledger，避免重做已完成切片。

### 15:09 恢复检查点

- 实现 HEAD 仍为 `476d027`；本轮未修改产品或 UI 测试源码，Task 7 未 task-done。
- 新鲜回归：生成器 `test_speech_demo_project.py` **4/4**；`./gradlew :demo:shared:allTests :demo:androidApp:testDebugUnitTest :demo:androidApp:lintDebug --console=plain` **BUILD SUCCESSFUL，427 tasks（5 executed、422 up-to-date）**。日志：ledger 同目录 `task-7-resume-gradle.log`。未重跑此前完整五项真机套件。
- native `code-reviewer`、`critic`、`architect`、默认继承模型和显式 `gpt-6.1-sol`、`gpt-6-astra`、`gpt-6-luna` 独立子代理均在执行前返回 `Selected model is at capacity`，没有审查发现或 CLEAR。替代的 `omx ask claude` 退出 1：`Not logged in`，未修改登录状态。失败原始记录在 `.omx/artifacts/claude-read-only-independent-code-review-repository-worktree-users--2026-10-20T06-58-08-790Z.md`。恢复后只需一次可用的独立 UI/Done 审查，不重复已通过的 Host 整包审查。
- 两个远端 `feat/sdk-foundation` 实时查询均仍为 `472ca9351eb69c09d863ef7bc267c280b068b846`，是当前本地分支的祖先。没有推送；最终审查门禁保留，既有串行同步授权继续有效。
- 两个受保护文件的 SHA-256 与本文件固定值一致，`git diff --check` 通过。后续不得将它们纳入提交。

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
- iOS 麦克风 ASR 的受控真机录音与最终验收（本地实现现已提交，见顶部检查点）。
- 两端自然语音质量与断网验证。
- P7 Android 视频超分与完整 SDK/Demo 接入。
- P9 Android 第二平台与质量基准。
- P10 生产人脸模型、PAD、授权数据、阈值校准与最终独立审查。
- P11 统一 benchmark 与发布结论。

可用现代 Android 真机：TECNO KM9，API 35，ADB serial `15365375C9007986`。iPhone 16 Pro Max / iOS 27 已用于现有 iOS 验证；连接状态需在新会话重新检查。
