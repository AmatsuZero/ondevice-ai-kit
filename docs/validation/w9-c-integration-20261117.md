# W9-C：KMP bridge 与双端 Demo 集成（2026-11-17）

结论：**implemented / integrated-local-validated；release not-ready**。用户要求在已集成 A/B 上直接执行 C，本轮由主代理实现、接线、验证，没有新 helper 调用、付费请求或已停用额度重试。分支 `feat/w9-ab-integration-20261117`，C 前基线 `a017cb7c4ebd4c9d8453bb73c30731e83ad16ddf`。A/B 源码不改，原验收见 [A/B 报告](w9-ab-integration-20261117.md)。

## 实现与边界

- 公共 KMP 仅传 opaque reference、预设、metadata 与错误。Android/Apple 桥接实际 A/B 客户端；原声独立复制、高音 +4/低音 −4 半音；输出 token 与 native ID/路径独立，close 后仍可释放。
- 取消/close 等待 native drain 和输入 lease 释放；迟到结果进入清理。Android 输入 lease 释放失败保持待恢复状态，重复 cancel/close 可重试；未知 token 不能删除文件。输出释放失败可重试，试听/导出持有输出直到读者结束。
- 共享 ViewModel/页面支持导入、录音/停止、三预设、转换/取消、原声/结果试听、WAV 导出、清理与返回；同步回调、迟到回调、离页与失败释放有状态覆盖。导入取消或失败保留旧输入。
- 原生 Host 负责权限、硬件、音频会话、输入文件租约和导出。录音只在明确 UI 点击后请求权限，16k 单声道 PCM16、最多30秒；不实时监听。Android 使用 ShortArray 采集并显式写 little-endian。iOS 在恢复 Speech 前等待录音停止与播放准备结束；异步播放准备失效时释放当前播放占用。
- Host 在 IO 执行 WAV 读取/归一化，接受附加 RIFF chunk，归一到 canonical44；拒绝坏/空/超30秒、双声道、非 PCM16、非16/44.1/48k，不截断超限文件。SDK 冻结合同不变。
- Android 使用独立、nonexported Activity；iOS 使用 `ODAI_VOICE_EFFECTS_DEMO=1` 的生成工程 opt-in。进入前停止既有 Speech/Video/Camera，并等 Speech 结束。普通 iOS PBX 未修改；生成器原有 Debug/Release framework 选择修改保留，排除本次提交。
- 不新增依赖、模型或 lock/version 更新；不改变 W3/W6 暂停、W7 生产校准/PAD 延期。未签名、未安装物理设备、未开麦/采集私人媒体、未上传/发布/push。

## 实际验证

原始本地证据根：`.omx/artifacts/w9-c-direct-20261117T101447Z/`。该目录是本地工件，未纳入 Git；下表将实际命令、计数和边界分别记录，不能把 CLI exit0 或编译视为未执行的运行测试。

| 验证 | 实际结果 | 证据与边界 |
| --- | --- | --- |
| shared/binding common 状态与合同 | 113测试，0失败 | `all-shared-tests.log`；缓存 Kotlin2.4.0 编译器 + 真实 Compose desktop1.11.2/JUnit JVM harness，覆盖现有 common binding 和非Composable shared 状态；不是 Kotlin/Native 测试执行 |
| 新 shared voice 定向 | 9测试，0失败 | `run-shared.py`；3合同 +6ViewModel；源位于 commonTest |
| Android App unit | 67测试，0失败/错误/跳过，15 suites | `android-tests-presets-result.json`、`android-test-tally.json`；其中 bridge4、WAV3；bridge实际原生后端执行三预设、close后输出、未知token、释放重试及 gated render取消/close/drain |
| Android lint、Debug/Release APK | 全部 exit0 | `android-final-r2-result.json`；lint0错误/9条既有区域警告，没有新voice源警告；未安装OEM设备 |
| KMP iOS device/simulator Debug framework | 构建通过 | `build-r2-result.json`；双端真实framework，未用stub；不是最低系统硬件运行 |
| Swift Host WAV | 3 XCTest，0失败 | `swift-host-tests-final.log`；精确当前 `VoiceEffectsWave.swift` 进入隔离Swift6/macOS12测试包，涵盖chunk/格式/边界/文件读取；不是iOS硬件 |
| iOS bridge | 4 XCTest，0失败 | `ios-bridge-tests-r2.log`/xcresult；真实iOS模拟器、实际Apple后端原声/高/低、输出跨close、未知token、gated活动取消/drain；音频引擎模拟器tuning警告保留 |
| iOS页面UI | 1 XCTest，0失败 | `ios-ui-smoke-final.log`/xcresult；进入/按钮状态/返回，不请求录音、不启动麦克风 |
| iPhoneOS App | 最新源码 unsigned Debug build exit0 | `ios-device-final-r2-result.json`；实际voice opt-in Speech组合App，非MLX/OpenCV等全部组合，不是签名安装或真机接受 |
| 生成器现有回归 | 19测试，0失败 | `generator-regressions-final.log`；使用既有Ruby3.4.11和对应gem环境；不称新增voice独立回归已覆盖全部组合 |

Android 命令（既有 `JAVA_HOME=/opt/homebrew/opt/openjdk@17`）：

```sh
rtk proxy ./gradlew --offline --console=plain :demo:androidApp:testDebugUnitTest :demo:androidApp:lintDebug :demo:androidApp:assembleDebug :demo:androidApp:assembleRelease
```

iOS Demo：先生成/构建实际 KMP Debug frameworks，使用既有 Ruby/gem 环境，将 `ODAI_VOICE_EFFECTS_DEMO=1`、`ODAI_SPEECH_DEMO_DIR` 指向新的隔离输出目录，再运行 `scripts/generate-speech-demo.rb`。生成的 `SpeechDemo` scheme 可 unsigned build；选择 `SpeechDemoUITests/VoiceEffectsDemoUITests` 运行页面烟测。实际构建参数及耗时见对应 `*-result.json`；iOS桥接与Swift WAV的隔离测试工程/包也保存在证据根。不要覆盖普通 App 工程或运行全部 Speech UI 测试导致未授权采集。

## 首错与纠正

- shared/WAV 最初缺符号编译失败记录保留。重复 cancel 的真实行为回归曾期望2次却只有1次，修正后 GREEN；不能把先实现后运行的 bridge 测试称完整 TDD。
- bridge 初次测试2失败：线程名断言被 coroutine debug 后缀影响，改为实际 Thread identity；输入 lease release 抛错则是真缺陷，修为保持未释放状态、可恢复，不吞为已清理。
- 首次系统Java shim无runtime，使用已安装OpenJDK17；系统Ruby2.6继承Ruby3.4 gem而失败，使用已有Ruby3.4.11。没有安装依赖或改全局环境。
- Swift 首次构建的显式self/AVFileType导入问题已修复；以真实生成 KMP header 的 `close(callback__:)`、`releaseOutput(reference:callback_:)` 和 callback标签为准。原错误日志保留。
- 模拟器仅本次独立创建 `A2624842-6D3F-4AC5-A7D3-202D5CE58793`（iPhone17Pro/iOS27.2）；收尾 shutdown 返回405/current state Shutdown，随后只读确认本模拟器已关闭；未操作其他设备/App。

## 保持与剩余验收

冻结 [contract-v1](../voice-effects/contract-v1.md) SHA256 仍为 `87f890278a07a457d656970a16ca3001ef034cd05e9001b655eb2b84779b86d2`。C 前1253个受保护dirty/untracked逐项hash复核；仅生成器和ROADMAP有本次明确增量，原有内容保留，生成器原Release两处修改不混入提交；其余原内容不变。实际收据见本地 `protected-final.json` 与 `staging-verification.json`。

仍缺：物理iOS/Android导入→转换→试听→导出回读、明确点击的录音/拒权/30秒停止、前后台/中断与硬件会话恢复、人声可懂度/失真听验、同候选时延/采样资源及批准预算、最低系统设备、完整交付组合/通知/独立消费者、独立最终复审。iOS会话恢复采用best-effort系统调用；关闭/释放失败有桥接状态，但硬件异常恢复和离页失败可见性仍须设备验收。当前无独立APPROVE；原429停线，不自动重试。

下一步 DEVICE 与 R 分工并行，优先固定同一安装候选和输入；失败定位后只修实际阻断，不加效果/格式/模型，不恢复W3/W6研究。11/20候选、11/22验收目标保持，不能因日期或工程测试通过宣布项目发布完成。
