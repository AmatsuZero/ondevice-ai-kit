# W9 录音拒权、音频中断与交付复审（2026-11-18）

**Android TECNO 真机已通过隔离包的麦克风拒权、拒权后合成 WAV 转换和真实音频焦点抢占；iOS 真机拒权与真实系统中断仍待验。双端模拟器检查见下表。CBC `hy4flash` 审计和独立预审均已完成，当前发布 NO-GO。** [既有真机验收](w9-physical-acceptance-20261118.md)和[资源与活动取消证据](w9-engineering-acceptance-20261118.md)继续有效。W3/W6 暂停。

| 检查 | Android | iOS |
| --- | --- | --- |
| 拒绝麦克风访问 | Android 17 模拟器，隔离 Debug APK 与 TECNO 已验 APK SHA-256 `cbc63255da7e5c8e073d04ed0349d27fc6a1e668f36463551bc75afbe1365192` 相同。系统弹窗点拒绝；权限前后均为 `granted=false`，App 显示 `microphonePermissionDenied`，无录音状态，随后导入合成 WAV 并转换成功。 | iOS 27.2 模拟器，隔离 Speech/W9 Debug 工程。用 `simctl privacy revoke microphone` 预置拒权；无系统弹窗交互。点击录音后显示 `microphonePermissionDenied`、未进入录音，随后系统文件导入和转换成功；XCTest 1 项 0 失败。 |
| 播放中断与恢复 | Android 17 模拟器，测试专用前台服务真实申请 `GAIN_TRANSIENT`，记录 `REQUEST_RESULT=1` 及系统焦点栈。同一 `VoiceEffectsActivity` 保持 top resumed；触发约 1.97 秒后的 UI 样本显示停播、结果保留。释放后不自动续播，手动试听和停止通过。 | iOS 27.2 模拟器，仅在隔离工程副本里遇到已开始的前台播放时注入 `.began`；同一 `VoiceEffectsHost` 通知回调停止播放器、释放播放标识，App 仍前台并保留结果，手动试听和停止通过。XCTest 1 项 0 失败；这是处理回归，不证明 OS 向真机交付真实中断。 |
| 真机剩余 | TECNO Android 15 已完成拒权、拒权后转换和播放焦点抢占；录音过程中的系统打断、Speech/TTS 实际争用仍未覆盖。测试服务的 5 秒定时释放未执行，已仅停止测试服务释放焦点；不把该定时器算通过。 | iPhone 已连接、隔离 App 存在；自动化镜像未就绪，未触碰该 App 的真实权限。待在隔离 App 关闭麦克风权限后验证拒权，并观察由系统触发的 `.began`；普通后台停播只算后台行为。 |

本轮只播放合成音频，未开启麦克风。录音中遇到真实系统打断及 Speech/TTS 实际争用没有覆盖。Android 模拟器离页后无 W9 缓存音频，移除了本轮夹具、UI 文件及焦点测试 App。首错保留：非导出 Activity 直接启动被拒，改走 MainActivity 入口；系统选择器返回名称为「导入 WAV」，以 App 缓存 WAV 与原件 SHA-256 相同及转换终态补足文件身份。Android D8 使用现有 OpenJDK17 修复本轮局部的 Java runtime 路径；iOS 隔离注入首轮误用 iOS 16+ 的 `Task.sleep(for:)`，改用 `Task.sleep(nanoseconds:)` 后通过，产品最低系统未改。iOS 复验仍有一条主线程可能影响响应的运行警告，未据此定位具体调用，也未判为已消除。

验收运行原件：`.omx/artifacts/w9-permission-interruption-20261118T042555Z/`，含 `android-denied-pass.json`、`android-focus-result.json`、焦点栈及 UI 树、`android-cleanup.json`、两份成功 `.xcresult` 与 `ios-injected-source.diff`。隔离测试改动未进入生产源码。

TECNO 真机增量：ADB 重新识别 `15365375C9007986` 后，另建独立包 `io.ondeviceai.demo.w9permissionfocus20261118`（APK SHA-256 `6bc90a9e4d2b90dea5d64176e832e6baeffa610b415af62f9bd8867cff166681`），系统弹窗选择“不允许”；权限保持 `granted=false`，页面报 `microphonePermissionDenied`、无录音状态。导入 48 kHz / 30 秒合成 WAV，App 私有输入与原件 SHA-256 均为 `7aa42a05df9c90367a39c65d9e253517b6d4f99d274a8e35f6b035e91b7563a9`，转换完成。播放中，专用前台服务经直接服务指令取得 `GAIN_TRANSIENT`（`REQUEST_RESULT=1`），同一 `VoiceEffectsActivity` 保持 top resumed；约 2.77 秒后停止试听、结果仍在。精确停止该测试服务后，未自动续播，手动重播/停止通过。原广播夹具被 TECNO 放入受限队列，未取得焦点；改用服务指令。测试服务的 5 秒定时释放没有运行，恢复检查采用 `force-stop` 该专用包，原因未定。新建测试 App、两个焦点夹具和唯一合成 WAV 均已移除，原验收 App 安装及麦克风授权仍在。原验收 App 在首次探查时显示已有 `录音.wav`，其麦克风权限从临时 `revoke` 后变回 `granted=true`；无法归因具体操作，故未用该内容取证或清理。真机原件在 `.omx/artifacts/w9-tecno-permission-focus-20261118T061228Z/`，包括权限/输入摘要、UI 树、焦点栈、首错和清理收据。

## 打包与许可通知审计

CBC 对给定源码/打包/证据材料做了单次 `hy4flash` 审计：80.472 秒、exit 0、`is_error=false`、`modelUsage.hy4flash` 18,286 输入／13,679 输出 tokens、无工具事件。原件 `.omx/artifacts/w9-final-composition-cbc-20261118T034307Z/`；该轮提供复审 brief。随后交给不同的只读 Agent 执行独立预审，结论 **REQUEST CHANGES / NO-GO**：两项高优先级（最终组合未冻结、实际入包/传递依赖通知证据不全），一项中优先级（iOS [`ODAI_THIRD_PARTY_NOTICES_DIR`](../../scripts/generate-speech-demo.rb) 可选，缺发行配置完整性检查）。预审可核现有材料；最终实包生成后仍要复审同一组摘要。

- 当前 Android 隔离包未启用 Sherpa/LiteRT/OpenCV 可选项，iOS 为启用 W9 的 Speech/KMP Debug 组合。两者均不证明最终成员。Android 的「视频超分」入口与 W6 视频理解分开审查；最终工程须证明 W6 未验收的视频理解入口隐藏、图片能力保留。
- 本地 W9 SDK review ZIP 已重新核对 SHA-256 `67783b01bcf498fd9a7c51d2b7f2ea92da09e0bd25cf9ad52821dc293a1f98a5`、50 个成员及 CRC。它不含 Demo App、模型或完整依赖；Kotlin stdlib/coroutines 在消费者侧解析，未放入 ZIP。通知文件名命中情况不能证明适用或完整。

## 下一轮复审输入

1. 先冻结一份组合清单：记录 commit 和实际构建输入摘要、各端 flags、依赖版本、入包模型与资源、最低系统、交付物、渠道和版本。保留 W1/W2/W4/W5 与已接受的 W3 能力及限制、隐藏 W6 未验收的视频理解、保留 W7 未校准拒绝、包含 W9。Android 逐项决定 `enableSherpaAsr` / `enableLiteRtSr` / `enableLiteRtLm` / `enableOpenCvIdentity`；iOS 逐项决定 `ODAI_VOICE_EFFECTS_DEMO` / `ODAI_VIDEO_DEMO` / `ODAI_MULTIMODAL_DEMO` / `ODAI_MULTIMODAL_MLX_DEMO` / `ODAI_OPENCV_IDENTITY_DEMO` / `ODAI_THIRD_PARTY_NOTICES_DIR`。先核影响和实际包，不按试用包默认值推定。
2. 每份候选实包逐组件记录：**名称和版本 → 来源与适用通知 → 原文摘要 → 包内路径与摘要**，分开列出随包、宿主解析、明确排除。iOS 发行候选验证通知目录确实入包；Android 核 APK/AAR 及实际消费者依赖。版本、模型/资源与每个实包 SHA-256 对回同一清单。
3. 补 iOS 目标设备拒权与真实系统中断，以及录音过程中的系统打断和 Speech/TTS 争用；明确最低系统缺测处理与资源预算裁定（当前 **DRAFT/NOT APPROVED**）。把最终候选、通知对账、设备证据和未解问题一并交独立审查者。按[发布检查清单](../release/2026-11-weekly-candidate-checklist.md)逐项给结论，再决定是否允许发布。
