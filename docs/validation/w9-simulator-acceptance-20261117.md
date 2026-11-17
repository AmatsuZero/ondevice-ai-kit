# W9 双端模拟器验收（2026-11-17）

结论：**simulator-core-validated / release not-ready**。用户不在机器旁，按授权先验模拟器。真实 Demo 已通过系统文件导入、原声/高音/低音转换、播放控制、系统保存与导出回读；发现并修复 Android App 沙箱内硬链接被拒绝的问题。全程仅用合成 WAV，没有点击录音、采集麦克风、人工听验、安装物理设备或远端发布。W3/W6 继续暂停。

源码起点 `47cd7909893e325ced2bde12ca8cb96f792d402e`，本报告对应随后本地模拟器修复提交。冻结合同 `docs/voice-effects/contract-v1.md` 未改，SHA-256 `87f890278a07a457d656970a16ca3001ef034cd05e9001b655eb2b84779b86d2`。完整原始证据保留在本地 `.omx/artifacts/w9-simulator-20261117T125412Z/`，下文收据名相对此目录；原始工件不整批加入 Git。

## 环境与实际覆盖

| 项目 | iOS | Android |
| --- | --- | --- |
| 执行面 | 自有 iPhone 17 Pro 模拟器，iOS 27.2；隔离 SpeechDemo 工程 | 自有隔离 AVD `ODAI_W9_20261117`，API 37 预览镜像、16KB 页；无窗口/音频输入 |
| App | voice opt-in Speech Demo，真实 KMP/native bridge | 当前源码构建的默认 Debug Demo，真实 Activity/SDK |
| 文件来源 | Files 内合成夹具，经真实 UIDocumentPicker 导入 | Downloads 内合成夹具，经真实 OpenDocument 导入 |
| 基本链路 | 三预设、输入/结果播放开始停止、系统保存 WAV、清理、离页重入 | 三预设、输入/结果播放开始停止、CreateDocument 保存与取消、清理、离页重入 |
| 失败与恢复 | 坏文件/31秒拒绝且保留旧输入；导入取消；Home 后恢复 | 坏文件/31秒拒绝且保留旧输入；导入取消；播放切后台停止，经真实 Recents 恢复 |
| 30秒输入 | 实际导入并完成高音处理、离页重入 | 活动处理取消未成功触发，不能记通过 |

播放检查证明 API/页面状态正常，不证明扬声器听感。两端活动处理完成太快，本轮没有成功点击活动取消；已有 SDK 确定性生命周期测试不能替代此 UI 缺测。录音、拒权、录音30秒自动停止、系统音频中断及 OEM/最低系统实体设备仍待验。

iOS 全链 UI 测试 `ios-ui-full-r3-result.json` exit 0，1 个测试、0 失败（89.240 秒测试体）；30秒输入/离页测试 `ios-long-cancel-leave-result.json` exit 0，1 个测试、0 失败，日志明确记录 `W9_UI_CANCEL_NOT_EXERCISED_RESULT_TOO_FAST`。截图及系统操作保存在对应 xcresult。Android `android-ui-presets-result.json` 与 `android-export-lifecycle-result.json` 记录实际 UI 动作、回读和退出清理，XML/截图及动作日志保留。

## Android 运行时修复

原实现通过 JVM 测试，但实际 App 所有预设报 `PLATFORM`。`android-exact-app-logcat.txt` 记录 App PID 的 SELinux `untrusted_app_34` 对 app-data 文件 `{ link }` 拒绝；同一 App UID 的读取、临时文件写入与同步通过，`Files.createLink` 返回 `AccessDeniedException`。不能从主机测试推定 Android 沙箱可建硬链接。

修复先用原子 `createDirectory` 独占预留输出名，再在该 SDK 私有目录内将已关闭临时文件原子移动为 `audio.wav`。共享命名空间碰撞在预留步骤失败；不删除竞争者，不放宽系统策略、不使用非原子回退。SDK-owned 目录不得被外部写者修改这一既有约束保持。释放按文件→预留目录顺序，失败保留所有权供重试；失败清理按子路径优先重试，close 后调用方持有的输出仍可用。

候选来自本地 Ollama `qwen3-coder:30b`，主代理审查并集成选定片段；有缺陷的首稿/订正稿和原始响应全部保留，见 `integration-receipt.json`。没有新增依赖、修改公共 API 或冻结合同。新增两项回归先在原实现 RED，再在修复后 GREEN；碰撞用例保留竞争者断言并移至新的共享预留边界。

| 验证 | 结果与收据 |
| --- | --- |
| 新增回归 RED→GREEN | 原实现2失败，修复后2通过；`publication-{red,green}-result.json` |
| SDK 与 Demo JVM 测试 | SDK30 + Demo67 = **97**，0失败/错误/跳过；`persisted-regression-result.json`、`android-regression-tally.json` |
| lint / Debug与Release APK | exit 0；lint 0错误、9个既有警告；`android-regression-build-result.json`、`lint-counts.json` |
| 修复后独立消费 | 新隔离 Maven/AAR→独立 APK 与 AAR/JVM 三预设验证，以 `android-final-artifact-*-result.json` 为准；不覆盖旧 R 工件 |

本轮仅修复 Android 文件发布/释放及其回归；iOS 产品源码未改。C 阶段共享层113、Swift WAV3、桥接4等历史验证仍见 [C报告](w9-c-integration-20261117.md)，不计入本轮97个 JVM 测试。

## 导出与文件所有权证据

合成输入为220Hz、16kHz、单声道PCM16、48000帧（3秒），SHA-256 `cab16e27631da5a8fd0aea3ca422facc5136cb862a79b49ef0195bf3f5b8f278`。两端保持采样率和帧数；Android 原声与输入字节相同。Android 高/低音估计278.462/173.846Hz，±4半音目标277.183/174.614Hz，误差均小于0.5%；iOS 导出低音估计174.615Hz。这是合成信号检查，不是人声听感接受。

Android 系统保存文件回读与所选低音输出完全相同，SHA-256 `47f7d2622891fa0799dfc88312da9c052070cb8e70e66e0183f10c57e140db34`；最终离页的 owned input/output 文件清单为空。iOS 系统保存 `w9-ios-low.wav` 回读为16000Hz/48000帧/单声道PCM16，SHA-256 `62a7130d9063c0e2df2f74828090d08e9d3fdcbebd30d0c91edbc0afb0a9fe93`。收据为 `android-wave-readback.json`、`android-export-lifecycle-result.json`、`ios-export-readback.json`。

iOS 正常离页测试清理了本次会话文件；此前两个检查型 XCTest 在页面未离开时被测试进程结束，仍有4个合成输入/输出文件，记录于 `ios-after-leave-files.json`。没有删去这些残留来制造空目录结论，进程被杀后的跨会话清理尚未接受。

## 首错、边界与下一步

第一次 iOS picker 检查等待不足，保留失败并改为等待系统 picker 实际出现后重测通过。Android 恢复驱动曾尝试外部启动 non-exported Activity 被拒，随后经真实 Recents 完成恢复；没有修改产品导出属性。FileProbe 的中断/137退出不计入验收。

旧 [R报告](w9-r-delivery-20261117.md) 的 Android Maven/AAR 属修复前快照；本轮使用新的隔离暂存，不混用旧包身份。最终 APK/AAR 摘要见 `final-artifact-hashes.json`。模拟器恢复/关闭记录、文档链接检查和原1253项保护检查见本轮最终收据；保留普通 App、模型和无关修改。

接下来按 [设备验收步骤](../release/2026-11-w9-device-acceptance.md) 补真机文件链路、用户点击录音/拒权/自动停止、活动取消/音频中断、人声听感和同候选资源预算；R 补实际最终组合与通知，Q 补独立最终复核。最低系统、实际设备与发布门均未关闭。未 push、签名分发、上传或打 tag，不能据模拟器结果宣布本周项目已全部完成。
