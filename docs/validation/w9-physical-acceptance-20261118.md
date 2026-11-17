# W9 双端真机文件链路验收（2026-11-18）

当前 **physical-file-core-validated / release not-ready**。本轮只运行新的合成文件，未点击录音、未采集麦克风、未进行人声听验；W3/W6 保持暂停。文件链路通过不关闭 W9 完整设备门或本周发布门。

## 候选与设备

- 源码基线：`9219cfec7c39d1e90b6217e76bf25a87170a5468`，分支 `feat/w9-ab-integration-20261117`；本轮无生产源码改动。沿用 C 集成与 Android 原子发布修复。
- Android：TECNO KM9 / POVA Slim 5G，Android 15 / API 35 / arm64；默认构建未启用 Sherpa/LiteRT/OpenCV 可选 flag。隔离 applicationId `io.ondeviceai.demo.w9acceptance20261118`，APK SHA-256 `cbc63255da7e5c8e073d04ed0349d27fc6a1e668f36463551bc75afbe1365192`。离线 Gradle 构建 exit 0 / 20.302s，设备端 APK 摘要核对后安装成功。
- iOS：iPhone 16 Pro Max，iOS 27.2.1 / arm64；voice opt-in Speech/KMP 组合，隔离 bundleId `io.ondeviceai.w9acceptance20261118`。使用本机既有开发证书和匹配 profile，仅签名任务目录内的 App/runner 副本；没有下载、修改签名资产或开启自动 provisioning。unsigned App 构建、严格签名检查与隔离安装通过；不是分发签名或最终完整 MLX/OpenCV 组合。
- 两端显示名均为 **W9 验收 1118**。普通 App 不覆盖、不卸载、不清数据；只访问隔离容器与本轮四个合成文件。构建配置、实际安装包/签名副本成员摘要保留在本地收据。

本地证据根：`.omx/artifacts/w9-physical-20261117T230454Z/`。原始设备标识、签名选择、profile 与测试结果包留本机，不纳入本次 Git 提交。该目录不是发行包。

## 实际结果

| 检查 | Android | iOS |
| --- | --- | --- |
| 系统文件选择器导入合规 WAV | 通过；未授予麦克风权限 | 通过；进入隔离 App 的 W9 Synthetic 目录 |
| 原声 / 升调 / 降调 | 三次实际转换，各 48,000 帧 | 三次实际转换，各 48,000 帧 |
| 归一化原声与三结果播放控制 | 开始、停止状态均观察到 | 开始、停止状态均观察到 |
| 系统保存并独立回读 | LOW 与已读取结果逐字节相同 | LOW 回读可解析，实际保存名 `w9-physical-low.wav.wav` |
| 导出取消、清理 | 取消保留结果，清理删除所属结果 | 取消保留结果，清理移除结果 |
| 坏文件 / 31 秒文件 | 明确失败；原归一化输入字节保持 | 明确失败；原输入显示保持为 tone.wav |
| 导入 picker 取消后再次转换 | 通过 | 通过 |
| 30 秒合规文件 | 实际转换 480,000 帧 | 实际完成转换；没有成功触发活动取消 |
| 后台返回 | 30 秒结果播放中 Home→Recents→返回共 4.909s，停止试听已消失，480,000 帧结果保留 | Home→activate 后页面可用；未覆盖播放中的后台切换 |
| 离页再进入 / 清理 | 空输入；最终离页后 cache 无 voice-input / voice-output 文件 | 空输入；最终离页后 tmp 列表仅空目录，无音频文件 |

iOS 测试体 `testPhysicalFileChain` 为 **1 test / 0 failures / 107.032s**。外层 xcodebuild 随后自然退出 **exit 0 / 709.560s / TEST EXECUTE SUCCEEDED**，结果包已落盘；额外耗时包含设备诊断收集等待600秒超时，日志明确 `Failure collecting diagnostics from devices`。这是诊断附件收集失败，不是测试失败或完整性能测量；保留告警与进程采样，不重跑测试、不发送终止信号。

输入为 3 秒 220Hz 单声道 PCM16 / 16kHz WAV（48,000 帧），另有 30 秒、31 秒与 malformed 合成文件。Android 原声 SHA-256 `cab16e27631da5a8fd0aea3ca422facc5136cb862a79b49ef0195bf3f5b8f278` 与原合成输入相同；升调 `b82a9008b9d0bd33d1f876b2cf14e4b3ee461237852364f4d178bcf2795d2135`，降调及导出 `47f7d2622891fa0799dfc88312da9c052070cb8e70e66e0183f10c57e140db34`。iOS LOW 导出 `b6fdea0a84a0adeb111ad7167762c11891e8cae0ce52bd20d3de59ed5bb2a5d4`。四个回读文件均为单声道 PCM16 / 16kHz / 48,000 帧；粗略过零估计分别 220 / 278.5 / 173.5 / 174.5Hz，仅说明变化方向，不代替精密音高误差或听感验收。

## 首错、恢复与限制

- Android 四个文件在磁盘上存在且 hash 正确，但 Downloads 初次显示为空；只对这四条所属合成路径发送媒体扫描后可见。没有扫描私人媒体或清空媒体库。
- Android 导出 picker 的 Back 会先返回父目录；按实际 UI 有界返回到 App 后验证取消与结果保持。初次 Home/Recents 连发未进入概览；桌面图标额外压入 MainActivity，原 VoiceEffectsActivity 仍在任务栈。退出新空页面及新增 Main 后找回原 30 秒结果，再以实际 Recents 返回完成有界后台检查。未修改产品 launchMode 或导出非公开 Activity。
- iOS 初次假定产品名为 OnDeviceAIDemo.app，在签名之前断言停止；按实际 SpeechDemo.app 恢复。任务工程 build setting 含 UIFileSharingEnabled，但 built Info.plist 没有该 Boolean；改为任务专用 Info.plist 明确布尔值、重建并检查实包后签名。生产 PBX/Info 不改。
- iOS 原生保存框自动附加扩展名，测试输入又带 `.wav`，实际双扩展名保留；不隐藏或重跑修饰结果。后续人工保存应填写不带扩展名的名称。
- iOS 播放日志出现音频 session 活跃时主线程调用可能导致 UI 无响应的 warning；本次操作完成，尚未定位具体 API/量化卡顿，不能据测试通过消除性能风险。
- 双端活动转换取消均没有成功触发，不记通过。录音拒权/成功录音/30 秒自动停止、人声可懂度/失真、系统音频中断与 Speech/TTS 竞争、最低系统、时延/内存及预算裁定、最终组合通知/消费/独立复审仍 open。

主要收据：`android-physical-presets-result.json`、`android-export-readback.json`、`android-export-cancel-clear-result.json`、`android-lifecycle-partial.json`、`android-background-result.json`、`android-lifecycle-final.json`、`ios-physical-full.log`、`ios-physical-full-result.json`、`ios-physical-full.xcresult`、`ios-physical-full-source.swift`、`ios-export-copy-result.json`、`ios-after-leave-list.json`、`physical-wave-readback.json`、`ordinary-before.json`、`ordinary-after.json`、`w9-source-bindings.json`、`protection-final.json`。26份选定W9源码当前字节与HEAD相同；1,252个其他保护文件摘要不变，pitfalls只前置本轮说明，原1份正文完整保持，合计1,253份原内容保持。

## 接续

用户在 **W9 验收 1118 → 离线变声** 明确点击录音后，补齐录音/权限/上限停止与人声三预设试听、保存。主代理继续同候选资源及交付组合验证；本轮无 push、上传、版本发布或 tag。普通 Android APK 字节与录音 appop 保持；普通 iOS App 安装记录保持，未操作其私有容器。保护文件逐项核验另留本地收据。
