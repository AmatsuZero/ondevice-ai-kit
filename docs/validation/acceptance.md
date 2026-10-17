# Foundation 验收记录

更新日期：2026-10-17（保留分包历史证据）。工作分支 `feat/sdk-foundation`；代码提交以 git 历史为准。构建日志与本机产物放在忽略目录，不提交模型权重、照片、设备标识或签名材料。

| 项目 | 证据 | 结论 |
| --- | --- | --- |
| Swift SDK | `scripts/verify-apple.sh` 的 XCTest 和原生消费者日志 | 本次 P5 脚本通过：33 Swift XCTest、原生消费者和 Demo 构建；macOS 测试不替代 iPhone |
| KMP export | Swift bridge typecheck、KMP simulator tests | 跨语言接口编译与注入验证 |
| iOS Demo | iOS27 模拟器安装/启动、合成空白图输入 | 模拟器启动和照片选择器取消 UI 用例已验、Vision Code9；现代 iPhone 已验合成空白推理，Photo Picker 引导卡修复后常规交互 5/5 通过，两分钟持续相机另有 1/1 通过，见 P3/P4 记录；冻结 `927b7749` 的 iPhone 16 Pro Max benchmark 已生成 |
| Apple 二进制 | 源码基线 `927b7749` + 本次导出配置修复的两个 archive、四个 XCFramework 和显式后端 BinaryConsumer build succeeded | arm64 iOS/simulator 本地编译通过；`FaceDetectionClient` 已纳入 `ODAIKitFace` 分发 target，无发布/签名承诺 |
| Android | JVM tests、lint、APK、本地 Maven consumer、TECNO KM9 真机 | 统一脚本与本地 Maven consumer 通过；API 35 真机已完成 ML Kit 人脸相机、选图取消、EXIF Orientation 6、权限拒绝/重新授权和单图推理；另有两分钟相机 PSS/热状态抽样，见 P4；API 26 下限、长期资源/能耗和系统设置页恢复仍未验收 |
| Benchmark | Python schema/分组/百分位测试；原生 Demo 采集入口 | 工具测试 4/4、iOS 真机和 Android API35 均以 `927b7749` 完成 5+30 样本及分组报告；当前四模块 Apple/Android 消费者构建通过；API26/iOS15、峰值内存/能耗仍待完成 |
| 同步 | 两端 refs 新鲜读取并比较 | 仅已提交分支；未提交文件不属于远端交付 |

原始证据目录：`.superpowers/sdd/2026-10-15-ondevice-ai-kit-foundation`（本机任务记录）与 `artifacts/verification`；SDK/APP 产物为 `artifacts/xcframeworks`、`artifacts/iOSDemo`、`artifacts/BinaryBuild`、`demo/androidApp/build/outputs/apk`。

## 尚未验证

iOS15、Android API26 仍需设备单项签收。现代 iPhone 已验启动、合成空白 Vision、连续帧控件、前后台恢复、显式停止、照片选择/取消和两分钟持续相机 UI；真实相机权限往返由用户手动确认，随后相机/Vision 自动化复核 1/1 通过，不计为 Settings 自动化全流程通过。旋转/镜像及离线场景仍待 P4。现代 Android API35 已完成相机 ML Kit、选图取消、Orientation 6、权限拒绝/重新授权和单图推理；两端持续相机的资源/热状态数据均为有限抽样，持续背压、峰值内存、GPU、能耗和完整资源释放仍未实测。iOS27 模拟器 Vision 的错误不被计为成功空脸结果。没有公开或内部制品仓库发布，没有自动镜像 CI。

## 分阶段恢复

执行范围已改为 [ROADMAP](../ROADMAP.md)。P0/P1/P2/P3 已完成；当前 P4 仅完成用户反馈的相机闪动和前后台恢复部分。独立整分支审查因工具用量限制未完成，不宣称审查通过，不合入 main。

本次复核日志：`verify-android-checkpoint.log`（BUILD SUCCESSFUL，290 tasks，其中 278 up-to-date）、`maven-consumer.log`（BUILD SUCCESSFUL，36 tasks，其中 29 up-to-date）；已通过缓存检查与构建，不声称全部测试重新执行。四模块本地 Maven 发布及 lint 的前次成功日志为 `distribution-retry.log`（277 tasks）。

真机使用已由用户授权；实际安装、合成空白推理证据见 [P3](p3-ios-device.md)，相机 UI 验证见 [P4](p4-camera-stability.md)。

P1 生命周期审查与修复已完成，详见 [P1 证据](p1-lifecycle-review.md)。当前通过的是这一范围的审查与回归；安装器、桥接、Demo 交互等仍按后续包推进。P1 后旧 XCFramework/Maven 产物尚未重打包，源码/APK 验证与旧二进制不可混同。

P2 模型存储审查及修复见 [P2 证据](p2-model-store-review.md)。P3/P4 新鲜 Apple/Android 统一检查通过；其中 KMP Gradle 测试任务命中已有成功缓存，不声称每次重跑。旧分发产物仍待 P5 按最终源码重新生成及验收。

2026-10-17 P5 复核已更新上表：两端真机 benchmark 冻结 `927b7749`；人脸四模块分发已重新构建，修复 Apple target 遗漏 Client 源文件并验证显式选择消费者。前述 P1/P2 的“旧产物待重打包”是历史状态，本轮已补人脸分发证据；语音产物及消费者仍待 P6。详细路径与未测项见 [benchmark](benchmark.md)。
