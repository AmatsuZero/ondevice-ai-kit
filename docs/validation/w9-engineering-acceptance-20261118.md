# W9 工程验收增量（2026-11-18）

**状态：双端真机核心链路与用户录音试用已通过；iOS 活动转换取消与重入已通过；release not-ready。** Android 和 iOS 的用户听感、约 30 秒自动停止、停止后转换、再次录音沿用[真机报告](w9-physical-acceptance-20261118.md)，本轮无自动开麦。W3/W6 保持暂停。

本轮两次 iOS 运行使用 iPhone 16 Pro Max / iOS 27.2.1、源码基线 `9219cfe`，沿用隔离 Debug 验收 App。逐文件核对与前次签名 App 的 7 个文件完全一致，只重建及签署隔离测试 runner；26 个绑定的 W9 源文件摘要保持一致。合成夹具 `max48.wav` 为单声道 PCM16、48 kHz、30 秒、1,440,000 帧，SHA-256 `7aa42a05df9c90367a39c65d9e253517b6d4f99d274a8e35f6b035e91b7563a9`。

## iOS 活动取消和再次转换

- 首轮两项 XCTest 中资源用例通过，取消用例在「无输出」断言失败（外层 exit 65，45.719 秒）。原活动记录与录屏 OCR 保留：显示从「转换中」进入「转换完成」、出现完整 1,440,000 帧结果。首轮的取消按钮曾出现，但不能证明点击在转换提交前由 App 接收，也未观察到「已取消」。不能把这个结果单独定性为产品缺陷或取消通过。
- 只改隔离 runner，去掉点击前额外的 `isHittable` 查询，改为明确记录终态。若先出现完整结果则标为未触发取消。复验实际捕捉到「已取消」且 `output=false`，随后同一输入再次转换得到 1,440,000 帧；正常离页。1 项、0 失败、测试体 22.442 秒、外层 exit 0。未改生产实现。Apple SwiftPM 变声回归 25 项、0 失败（macOS，覆盖取消、迟到结果、close 等）。
- 隔离 App 的 `tmp` 元数据核查：复验与资源用例的所属目录无音频文件；首轮断言中断未正常离页，留下两条与该轮时间、大小和路径相符的 WAV。未读取或删除旧会话音频。跨进程残留清理仍待验。

## iOS 最大输入资源观测

升调完成 4 次（第 1 次热身，3 次计入采样），每次 1,440,000 帧。资源测试体 23.660 秒，独立用例通过。以下是同一台高端 iPhone、隔离 Debug App 的测试观测，预算仍 **DRAFT/NOT APPROVED**。

| 指标 | 三次实测 |
| --- | --- |
| 点击转换至 XCTest 观察到完成 | 1.574 / 1.556 / 1.564 秒 |
| XCTest Clock（含 UI 自动化；前两次额外包含清理） | 1.953 / 1.936 / 1.539 秒 |
| XCTest App CPU time | 0.608 / 0.611 / 0.512 秒 |
| XCTest App peak physical memory | 183,994 / 183,945 / 180,963 kB |

UI 耗时不是纯 DSP 耗时，内存是 XCTest 报告值；没有据此反推通过阈值，也没有验证发行配置、能耗或最低系统。Android 本轮 ADB 枚举无设备，未新增真机取消或资源数据。

## 本地 SDK 评审包与通知盘点

评审 ZIP `w9-sdk-local-review.zip`，265,603 字节、50 个成员，SHA-256 `67783b01bcf498fd9a7c51d2b7f2ea92da09e0bd25cf9ad52821dc293a1f98a5`，全部成员逐项回读核对。Apple 收录已独立链接过的 `ODAIKitVoiceEffects` 和 `ODAIKitAppleVoiceEffects`（iOS arm64 和 arm64 simulator）；Android 收录修复后的 `voice-effects` / `sdk-core` `0.1.0-SNAPSHOT` 本地 Maven 产物并核 AAR 与 Gradle metadata 摘要。包含项目许可证、依赖盘点和逐文件摘要。Kotlin stdlib 2.4.0、coroutines 1.10.2 需要消费者解析，未纳入此 ZIP。此前消费者证据见[R 报告](w9-r-delivery-20261117.md)及[修复后 Android 报告](w9-simulator-acceptance-20261117.md)；本次只验证封装回读，未声称重新运行消费者。

隔离真机 Android APK 摘要匹配，有 5 个名称命中通知扫描的成员、16 个 `.so`；隔离 iOS App 的 7 个文件无名称命中。扫描与 POM 声明不能证明通知齐全；ZIP 不含 Demo App、模型或完整交付组合，不用于正式分发。

下一步：Android 真机活动取消与同候选资源；双端录音拒权、系统音频中断/竞争；固定保留能力的最终组合并验证构建、实包通知/消费者及独立复审。最低系统实机按既定延期记录。原测试结果、录屏复核、资源原值、SDK 回归、App 绑定、通知清单和 ZIP 收据保存在 `.omx/artifacts/w9-engineering-20261118T004441Z/`。无推送、上传、发布或 tag。
