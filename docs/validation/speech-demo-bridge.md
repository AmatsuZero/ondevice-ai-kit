# Speech Demo Bridge 验证记录

日期：2026-10-20。实现基线 `b272b76`（Task 6），本轮 Task 7 增加真实 App UI 测试和审查修复；现代真机闭环及本地回归通过，未推送或发布远端制品。

本包实现 typed bridge、共享状态机与双端原生 host，不代表 P6 全部完成。计划/规格见 [计划](../superpowers/plans/2026-10-19-speech-demo-bridge.md)、[规格](../superpowers/specs/2026-10-19-speech-demo-bridge-design.md)。恢复状态以[交接](../HANDOFF-2026-10-20-speech-demo-bridge.md)和执行 ledger 为准。

## 能力与所有权

| 边界 | Android | iOS |
| --- | --- | --- |
| TTS | `android.text-to-speech`，系统已安装的非网络 voice | `apple.avspeech`；offline 描述不等于独立断网验收 |
| ASR | `android.speech.recognizer`，只创建 on-device recognizer；仅 `MICROPHONE` | `apple.speech.recognizer`，绑定 en-US，强制 on-device；仅 `AUDIO_FILE` |
| 权限 | Host 请求 `RECORD_AUDIO`，拒绝不启动原生识别 | 用户开始文件 ASR 时请求 Speech 授权；本包不请求麦克风 |
| 文件 | 不支持文件输入 | 系统 picker → security-scoped copy → opaque token；最后一份 native lease 在 drain 后释放才删除 |
| 生命周期 | 一个 TTS/ASR 音频槽；后台/离页取消；close 独立于 Activity Job 完成 | MainActor admission；取消等 drain；弱引用 bridge 避免 Host/VM 引用环 |

查询与音频任务独立；Default/Explicit 分能力保留，显式未知/不可用后端不回退。共享层不解析文件 URL，不记录音频、自然转写或朗读内容。错误只保留稳定 domain/code 或结构化 reason。

## 已通过的本地门禁

日志根目录（本机忽略路径，下文简称 `logs/`）：`.superpowers/sdd/2026-10-19-speech-demo-bridge/`。

- Tasks 4–6：Binding/Demo iOS adapter ABI 各 14/14；最新 Demo ABI + Host 20/20；共享 Speech VM 10/10、Face 5/5、Identity 20/20；Android Host 最新 5/5。
- 本轮 RED→GREEN：iOS Host 与 Kotlin VM 的引用环；Android factory 异常误分类；后台取消权限请求后，前台重试被误报拒权。权限 gate 复用一个系统 prompt，只将实际结果交给最新合法重试，不打开第二个 prompt。
- `sh scripts/verify-apple.sh`：Swift 原生 378 项零失败，消费者、原工程、ABI/探针构建与脚本检查通过；见 `logs/task-7-verify-apple-final.log`、`artifacts/verification/swift-tests.log`。
- `sh scripts/verify-android.sh`：569 tasks 构建/测试/lint 门禁成功（包括 up-to-date 任务，不声称全部重执行）；见 `logs/task-7-verify-android-final.log`。
- `./gradlew :kmp-binding:allTests :demo:shared:allTests` 通过；工具 `python3 -m unittest discover -s tools/tests -v` 76/76；见 `task-7-binding-shared-final.log`、`task-7-python-final.log`。
- 独立生成工程 simulator/device build、旧 Demo simulator build 均通过；device 使用本机既有 signing team，没有变更账户资产。生成器拒绝覆盖、缺源码/任一 ABI 时零输出副作用、只有一份 KMP framework、真实 UI test target 的工具回归通过。
- fresh 七产品 XCFramework 位于 `artifacts/verification/speech-bridge-distribution-20261020/`；Face/Speech/Identity 三个 binary consumer build 通过；日志 `task-7-apple-package.log`、`task-7-apple-*-consumer.log`。
- 本地 Maven 发布后，Speech contract/systemTts/systemAsr 消费者 debug/release 构建及依赖图检查通过；原生 contract-only 不引入 KMP/Compose/system backend。见 `task-7-maven-publish-local.log`、`task-7-maven-consumers.log`。

测试 target 最低 iOS 17。构建提示 KMP 内嵌 ICU 的构建系统下限高于 App 声明下限；iOS 15 设备支持仍未验收，不能以现代设备/构建通过替代。

## 实际 App 真机门禁

| 设备 / 后端 | 结果 | 证据与限制 |
| --- | --- | --- |
| TECNO KM9 / API35 / 系统 TTS | 已通过完成、活动取消、取消后重入、后台取消/前台不自动开始 | instrumentation 从实际 Activity 的 `speech-tab` 进入；`logs/task-7-android-ui-final.log` 各项 passed；固定测试文本，不评价自然语音质量 |
| TECNO KM9 / API35 / on-device ASR en-US | unavailable；真实转写未通过 | 实际 Demo 返回 `BACKEND_UNAVAILABLE MISSING_ASSETS`；不计作转写或 ASR 生命周期通过 |
| iPhone 16 Pro Max / iOS27.0.1 / 实际语音页 | 完整5项UI通过，0跳过 | 多行输入/Done、TTS完成/取消/重入/后台取消；真实picker导入/取消保留、文件ASR完成/活动取消/同输入重入/后台取消，前台均不重启；`asr-file-ui-full-suite.xcresult` 5/5，141.919秒 |

Android 最初 RED 为 Compose tag 未导出为资源 ID；添加根节点 `testTagsAsResourceId`。辅助功能滚动未推进，真实边缘触摸滚动可达控件；instrumentation 改用真实触摸后 TTS 测试通过。

iOS 历史输入失败已定位为测试自动化问题：Compose 离屏 AX 节点保留陈旧坐标，滚动惯性又会使截图后位置变化。`isHittable` 或完整 AX frame 均不足以证明实屏可点击。当前 helper 用本地 Vision OCR 定位固定 UI 标签，位置稳定0.6秒再点击，并用慢速边缘拖动控制滚动；不输出 OCR 文本。临时 focus/touch 诊断和单行实验均已移除。原多行输入单测通过 `tts-rendered-stable-retry.xcresult`；缺 Done 动作的真实 RED 为 `task-7-ios-done-red.log`，补 `ImeAction.Done`/clearFocus 后，完整 TTS 用例 `tts-slow-drag.xcresult` 1/1 passed（73.656秒）。

Done 修改后本地回归：`task-7-ui-shared-regression.log` 的 shared allTests、Android unit/lint/APK、simulator framework 共468tasks成功；`task-7-ui-python-regression.log` Python76/76；`task-7-done-framework.log` 重建device framework成功。包含 up-to-date 命中。

文件 ASR opt-in 用例通过实际系统 picker，未注入 bridge 输入。`task-7-ios-asr-file-ui-system-alert.log` 已证明固定 CAF 导入、重开/取消保持选择、首次真实 on-device ASR 完成；整轮因2.47秒短音频在取消观测前结束而失败。随后用相同合成音频重复20次（约49.47秒）；即时画面定位取消按钮后，`task-7-ios-asr-file-ui-fast-cancel.log` 完整生命周期1/1通过（40.535秒）。最后 `task-7-ios-asr-file-ui-full-suite.log` / `asr-file-ui-full-suite.xcresult` **5/5通过，零失败/跳过**，xcresulttool summary已核对。系统授权弹窗属于 SpringBoard；仅查询 App alerts 会漏掉它，历史 RUNNING 等待不是 native ASR 卡死证据。

固定测试文件通过 `devicectl` 复制至本 Demo 的 Documents。为实际 Files picker 暴露它，仅在忽略目录生成测试用 plist，覆盖 `UIFileSharingEnabled=true` 和 `LSSupportsOpeningDocumentsInPlace=true`；生产 plist/生成器未开启分享。验收结束已恢复普通包build/install，并核对两个sharing key均不存在，日志`task-7-ios-restore-normal-{build,install}.log`。固定合成fixture继续留在Demo Documents供复跑，不删除其他文件。新UI/Done追加独立review因模型容量不足未产生结论；先前Host修复的定向CLEAR仍保留。

### Android 英语离线资源尝试

本轮用户明确允许尝试下载英语离线资源，限定不登录、不启用联网识别。仅这一显式授权下执行 `mode=asr-download`；普通 App/测试不会下载。

系统服务为 Google Speech Services。`checkRecognitionSupport` 的 installed/supported/pending 列表均空；API34 带 listener 的下载返回15（不支持下载事件），随后尝试 API33 单参数接口，只能确认请求已发起。实际 Demo 重试仍为 `MISSING_ASSETS`。未确认下载成功，未登录/切换网络 recognizer，也未采集真人语音。官方接口边界见 [SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer)、[RecognitionSupport](https://developer.android.com/reference/android/speech/RecognitionSupport)。

## 复跑入口

使用已安装 Ruby/xcodeproj。输出目录必须是 fresh；本机 Ruby 可用 `/Users/daubert/.rvm/wrappers/ruby-3.4.11/ruby`。真实设备 identifier 每轮先重新查询。

```sh
rtk proxy ./gradlew :demo:shared:linkDebugFrameworkIosSimulatorArm64 :demo:shared:linkDebugFrameworkIosArm64
rtk proxy env ODAI_SPEECH_DEMO_DIR=artifacts/SpeechDemo-next ruby scripts/generate-speech-demo.rb
rtk proxy xcodebuild test -project artifacts/SpeechDemo-next/SpeechDemo.xcodeproj -scheme SpeechDemo \
  -destination 'id=<connected-iPhone>' -resultBundlePath artifacts/SpeechDemo-next/device.xcresult
rtk proxy ./gradlew :demo:androidApp:assembleDebug :demo:androidApp:assembleDebugAndroidTest \
  -PodaiTestRunner=io.ondeviceai.demo.SpeechDemoInstrumentation --console=plain
rtk proxy adb install -r demo/androidApp/build/outputs/apk/debug/androidApp-debug.apk
rtk proxy adb install -r demo/androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk
rtk proxy adb shell am instrument -w io.ondeviceai.demo.test/io.ondeviceai.demo.SpeechDemoInstrumentation
```

文件 ASR UI 用例默认跳过，因为普通设备没有测试音频；这不算验收通过。本机验收使用同一个合成 fixture 的20次重复（`ffmpeg -stream_loop 19 -i artifacts/P6IOSASR/fixture/asr-fixture.caf -c:a pcm_s16le -ar 16000 -ac 1 <ignored-dir>/odai-speech-ui-fixture.caf`），约49.47秒，增加活动取消的可观测窗口。先构建/安装生成Demo，再用 `xcrun devicectl device copy to --device <id> --source <fixture> --destination Documents/odai-speech-ui-fixture.caf --domain-type appDataContainer --domain-identifier io.ondeviceai.ondeviceaidemo` provision 固定输入。只选择 Demo 文件夹中的该文件。

测试用 plist 从 `demo/iosApp/Info.plist` 复制到忽略路径，再增加两个布尔键 `UIFileSharingEnabled`、`LSSupportsOpeningDocumentsInPlace`；使用下列覆盖进行文件完整验收。该设置只负责 fixture 在系统picker中可见，识别仍走真实导入/host/bridge/系统后端。完成后用不带覆盖的普通build/install恢复。

```sh
rtk proxy env TEST_RUNNER_ODAI_SPEECH_FILE_UI_TEST=1 xcodebuild test \
  -collect-test-diagnostics never \
  -project artifacts/SpeechDemo-next/SpeechDemo.xcodeproj -scheme SpeechDemo \
  -destination 'id=<connected-iPhone>' -resultBundlePath <fresh-dir>/device.xcresult \
  INFOPLIST_FILE=<absolute-path-to-ignored-test-plist>
```

Android `am instrument` 的进程退出码不等于门禁通过；必须读取结果 Bundle 的 `status` 与每项结果。正常测试只覆盖 TTS/UI 生命周期。真实录音使用单独 `-e mode asr-live`，需安排真人朗读并检查系统 mic readiness；不得将“请求已提交”当作平台已开始录音。资源下载模式有设备外部副作用，必须获得新的明确授权，不属于常规复跑步骤。

## 未完成项 / 下一入口

iOS TTS/文件ASR完整闭环及全套5项UI已通过；恢复新UI/Done追加独立审查后关闭Task7。作者审读不替代独立结论。Android 需英语离线资源就绪后补真人转写和 ASR 取消/重入；目前 unavailable 单独保留。iOS 麦克风属于后续独立设计；两端最低系统、断网/自然质量、第三方语音和 P7/P9/P10/P11 不因本包本地门禁通过而关闭。

最终targeted回归：生成器4/4通过（`task-7-generator-final.log`）；shared allTests、Android unit/lint427tasks成功（5执行，`task-7-final-gradle-targeted.log`）。受保护pbxproj及face-identity.md的SHA256与交接固定值一致；git diff --check通过。合成CAF SHA256：`1f001801f3bcc12be1c22c038827f7fa436b73a3c462467c82f6541ef4304d10`。Xcode结果另有一项QoS优先级反转runtime warning，未造成用例失败，未在此轮扩展排查。
