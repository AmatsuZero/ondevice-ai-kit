# iOS 麦克风 ASR 验证记录

日期：2026-10-21。实现基线 `d60867c`；路由修复已本地提交，真实录音窗口首个成功后仍有1110失败；新的NO_RESULT分类修复已完成本地验证；Task1–5独立审查已通过。P6 和整个 roadmap 保持未收口。

[计划](../superpowers/plans/2026-10-20-ios-microphone-asr.md)、[设计](../superpowers/specs/2026-10-20-ios-microphone-asr-design.md)、[恢复交接](../HANDOFF-2026-10-20-speech-demo-bridge.md)。本机原始日志位于忽略目录 `.superpowers/sdd/2026-10-20-ios-microphone-asr/`，下文简称 `logs/`。

## 实现与调用边界

- 契约 `SpeechMicrophoneRecognitionRequest` / `SpeechMicrophoneRecognitionTask` 为可选扩展；原文件请求和旧 backend conformer 保持兼容。`supportedInputs` 来源于实际后端，不把 unavailable 当作不支持输入。
- iOS 使用显式 `AppleSpeechAudioSessionProvider` / lease。仅注入 provider 的 Apple backend 声明麦克风能力；普通 file-only 构造不创建音频资源。显式后端不支持输入时返回 typed error，不回退。
- SDK 不弹权限。Demo 文件输入只申请 Speech；麦克风先 Speech 后 record。复用在途系统 prompt，cancel/background/close 后的晚到授权不能启动采集。
- `finishInput()` 停止新增输入并等待 final/completed；`cancel()` 丢弃结果。partial 不升级为 final。结束输入后 UI 保持禁止新任务，可继续取消。
- capture 先停止 buffer admission、stop engine/remove tap，再 drain 在途 delivery；native completed 与 lease release 后才发布终态。文件/麦克风共享一个 ASR 槽，旧句柄/回调不能结束下一轮。
- Demo session lease 独占，保存并恢复 category/mode/options；停止 I/O 后 deactivate，使用 notifyOthersOnDeactivation，不猜测恢复原 active 状态。release 错误不能伪报成功。
- 麦克风平台实现仅 iOS；基础编译下限保持 iOS15/macOS12。iOS17 录音权限 API、iOS27 tap API 均有旧系统分支。无新依赖、后台录音、网络 ASR fallback、资源自动下载或录音文件保存。

## 提交与测试证据

| 切片 | 提交 | 已验证内容 |
| --- | --- | --- |
| 可选契约/元数据 | `5be8a76` | locale 校验、旧文件 conformer、unavailable metadata、TTS 输入隔离 |
| native client | `0e43f99` | strict selection、挂起 start/close、cancel/drain、共享终态、旧 handle |
| Apple capture/session lease | `206a601` | 启动回滚、buffer barrier、finish/cancel/timeout/中断/路由/reset、单槽、权限重验 |
| bridge/VM/UI | `98d22a7` | optional finish task、pending intent、同步 terminal 不复活 handle、FINISHING 与取消 |
| 回滚修复 | `780e529` | release 错误传播；失效 native start drain 后保留 cleanup failure |
| host/session/工程 | `d60867c` | 权限合并/晚到授权、弱引用、session 恢复、生成工程隔离 |

所有产品修复有 RED→GREEN 日志。Task3 独立审查发现启动回滚吞 release 错误；新增 `testStartupRollbackSurfacesLeaseReleaseFailure` 与 `testInvalidatedNativeStartSurfacesLeaseReleaseFailureAfterDrain` 先复现断言失败，再修复。Task5 pending permission 的 host retain-cycle 同样先复现后修复。

- 最新 `verify-apple.sh` 通过：Swift 全套 **412 项，0 failures**（Speech72、ModelStore13、Identity313、Face9、Core5），binding/shared simulator tests/frameworks、分发11项、bridge/相机/照片/ASR脚本、native consumer 与原 App build；`logs/task-6-verify-apple-final.log` 和 `artifacts/verification/swift-tests.log`。
- 首次 Apple gate 因系统 Ruby2.6 加载 RVM3.4 gems 失败；通过 `RUBY=/Users/daubert/.rvm/wrappers/ruby-3.4.11/ruby` 重跑解决，无产品变更。
- `verify-android.sh` **569 tasks 成功，11 executed**，包含 unit/lint/APK/consumer；`logs/task-6-verify-android.log`。Gradle 包含 up-to-date，不声称所有任务重新执行。
- binding/shared allTests、双 simulator framework、Android unit/lint 通过435tasks；APK另251tasks；`logs/task-4-full-gradle.log`、`task-4-apk.log`。Speech VM15/15，Binding ABI20/20。
- 新生成 Demo ABI XCTest **35/35**：bridge20、host12、session3；`logs/task-5-generated-host-tests.log`。Python tools **77/77**；`logs/task-5-python-final-green.log`。
- 独立生成 simulator App、unsigned device App、Demo device framework、原受保护 App simulator build 均通过；`logs/task-5-{simulator-app-build,device-app-build,device-framework,original-app-build}.log`。生成器只在新工程加录音描述，无后台音频/文件分享键；原项目未纳入提交。
- iOS capture fixture 最新 **39/39、0 failures**，含 capture3、microphone19、file backend17；`logs/task-6-native-simulator.log` / `artifacts/verification/microphone-task6-rollback-final.xcresult`。此 fixture 使用合成 PCM/fake engine/session，不能证明实际录音或自然语音质量。

路由修复追加验证：`task-6-route-green.log` native simulator **41/41**（新增两项路由测试）；`task-6-route-swift-full.log` Swift全套 **412项、0失败**；`task-6-route-device-build.log` signed device build成功。两次独立复审调用均因模型容量不足未启动，路由修复复审仍待补。

## 新鲜本地分发

以下分发产物早于本次路由修复，尚未重打包。输出 `artifacts/P6/microphone-20261021/`，device/simulator archive 与七个 XCFramework 均成功：Core、ModelStore、Face、AppleVision、Speech、AppleSpeech、Identity；`logs/task-6-xcframeworks.log`。

Speech binary consumer unsigned simulator App **BUILD SUCCEEDED**，只嵌入 Core/Speech/AppleSpeech；`logs/task-6-speech-consumer-build.log`。两份 slice 的 contract-only 消费者（原文件 API 和新麦克风 request/start/finish/cancel）只提供 Core/Speech 搜索路径完成 iOS15 target typecheck。14个框架的依赖及接口 imports 检查确认契约未引入 AppleSpeech/AVFAudio/KMP/Face，只有 AppleSpeech 直接依赖 Speech.framework；`logs/task-6-speech-isolation.log`。这只证明编译/裁剪，不等于设备录音通过。

## 独立审查与未验收项

Task4 独立 code-reviewer APPROVE、0问题；Task5 与 Task3 回滚修复独立 code-reviewer APPROVE、10files、0问题。Task1–3 capture/client核心整包独立审查同样 APPROVE、11files、0问题，并独立跑相关34tests通过；五条 Review Focus 均已覆盖，真实录音/iOS15/网络边界除外。旧 Speech Demo Task7 UI/Done 追加审查在首次429后已恢复独立 APPROVE，旧包本地门禁收口；两者审查范围分别记录。最终门禁前不提前同步远端。

2026-10-21 受控真机窗口（iPhone 16 Pro Max，UDID `00008140-0008703436D3001C`）已使用新生成包 `artifacts/verification/microphone-live-fix-20261021/`。用户主动开始并结束录音后，回报页面显示 **`SUCCEEDED ASR`**，记录本次真实麦克风 ASR 的用户复测成功；未独立核对识别文本或断网表现；不记录或导出自然转写文本。此前诊断包的 `categoryChange` 误报已由 route snapshot 修复覆盖。随后用户在取消/重入测试回报两次均 `FAILED`，该生命周期门禁当前失败，尚无本轮底层错误码；首轮成功不代表完整修复。诊断工程与日志见交接。以下仍未验证：

- 实际录音授权/拒权恢复、取消/drain、同进程重入、TTS 往返、后台取消/前台不重启。
- iOS15 真机、两端自然语音 CER/WER/听感、断网与资源指标。`requiresOnDeviceRecognition=true` 是实现约束，不是独立网络验收。
- Android en-US ASR 仍 MISSING_ASSETS；不重新下载语言资源，不切网络识别。

真实录音只在确认受控环境和固定朗读窗口后启动。记录是否有 final、取消/重入状态和 backend，不导出自然转写或保存录音。UI RUNNING、metadata readiness、fixture passed 都不足以作为真实采集证据。其余 P7/P9/P10/P11 按 roadmap 独立推进，不以本包关闭。

## 1110 后续定位与修复

语言对照更新：四次实际输入中，iOS27 tap 和旧 tap 各出现成功与 `NO_RESULT`，两种采集 API 均能交付有限非零 PCM，故不据此替换 tap。用户确认失败轮次说中文或中英混合，而 Demo 的原生 Apple recognizer 固定 `en-US`，页面 locale 可编辑却无法改变原生 recognizer；改填 `zh-CN` 会被旧 backend 的 locale 校验拒绝。隔离诊断包在这台设备读到 `zh-CN available=true onDevice=true`，没有自动启动录音，也没有取得中文实录终态。现有 1110 样本只能确认系统未识别出语音，语言错配是明确配置缺陷；不能仅凭这些样本断定每次 1110 都由语言造成。

Demo 内部的语言路由现在按请求 locale 构造并缓存 Apple backend，沿用单一 `apple.speech.recognizer` 后端 ID、单音频槽与共享会话 provider；共享 UI 显示请求语言。新增回归先因缺少路由编译失败，修复后生成桥接/Host/Session 测试36/36；shared framework/allTests 与真机包构建成功，包已安装并启动。证据 `logs/task-6-locale-router-{red,green-final}.log`、`task-6-locale-shared.log`、`task-6-locale-device-{build,install}.log`、`task-6-zh-cn-events.log`。后端列表查询仍只报告系统 backend 总体元数据，不宣称每种 locale 都已就绪；具体请求会再由对应 Apple backend 校验。中文自然输入、取消/重入及后台生命周期需继续真机验证。

实际日志 `logs/task-6-reentry-buffer-events.log` 记录的是结束输入而非显式取消：较长一轮约4.63秒后结束，PCM211200frames/48kHz（4.4秒），44buffer均有非零样本，仍1110。不能仅以“过早结束”解释，也不能从非零样本推定可识别语音。Apple官方SDK `SFSpeechRecognitionTask.h` 错误表及[公开入口](https://developer.apple.com/documentation/speech/sfspeechrecognitiontask)将1110定义为“Failed to recognize any speech”；没有文档将它唯一绑定静音或某个更细原因。

新的映射使用已有 `SpeechRecognitionError.noResult`，显式取消仍优先，其他domain/code仍为platform；不制造空转写或成功。两项回归的准确RED断言和GREEN见 `task-6-no-speech-{red,green}.log`；完整Swift414/414，native iOS43/43，日志 `task-6-no-speech-{swift-full-final,native}.log`。真实麦克风仍未收口。

无需录音的合成对照：修正临时探针重复使用可变buffer后，独立buffer整段/实时、readonly-copy、48kHz均成功。不得把原探针错误扩大为系统buffer能力结论。`task-6-buffer-{variants,copy}-events.log`。保留生产backend/delegate/session/finish/drain，仅用合成Engine替换真实Engine的对照中，取消、同进程重入、再次完成均成功（`task-6-native-synthetic-lifecycle-events.log`）。该对照没有采集现场声音，不能替代真实麦克风或UI生命周期验收。忽略目录的诊断SDK副本不纳入产品/提交。

## 本地复跑

```sh
rtk proxy env RUBY=/Users/daubert/.rvm/wrappers/ruby-3.4.11/ruby sh scripts/verify-apple.sh
rtk proxy sh scripts/verify-android.sh
rtk proxy env RUBY=/Users/daubert/.rvm/wrappers/ruby-3.4.11/ruby python3 -m unittest discover -s tools/tests -v
rtk proxy env ODAI_ARTIFACTS_DIR=artifacts/P6/microphone-next sh scripts/package-apple.sh
rtk proxy env ODAI_ARTIFACTS_DIR=artifacts/P6/microphone-next /Users/daubert/.rvm/wrappers/ruby-3.4.11/ruby scripts/generate-binary-consumer.rb --speech
```

分发/生成目录必须不存在，不覆盖既有产物。保护的原 Demo pbxproj 与 face-identity.md 的 hash 见交接，禁止暂存/覆盖。验证只操作本项目资料，不变更签名账户、设备其他 App 或系统网络配置。
