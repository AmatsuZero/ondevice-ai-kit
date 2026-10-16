# Foundation 验收记录

更新日期：2026-10-16（保留分包历史证据）。工作分支 `feat/sdk-foundation`；代码提交以 git 历史为准。构建日志与本机产物放在忽略目录，不提交模型权重、照片、设备标识或签名材料。

| 项目 | 证据 | 结论 |
| --- | --- | --- |
| Swift SDK | `scripts/verify-apple.sh` 的 XCTest 和原生消费者日志 | 脚本通过：25 Swift XCTest、原生消费者和 Demo 构建；macOS 测试不替代 iPhone |
| KMP export | Swift bridge typecheck、KMP simulator tests | 跨语言接口编译与注入验证 |
| iOS Demo | iOS27 模拟器安装/启动、合成空白图输入 | 模拟器启动和照片选择器取消 UI 用例已验、Vision Code9；现代 iPhone 合成空白推理、两个相机及一个照片选择器取消 UI 用例通过，见 P3/P4 记录；未生成性能 benchmark |
| Apple 二进制 | 两个平台 archive succeeded；BinaryConsumer 不链接 KMP 的 XCFramework build succeeded | arm64 iOS/simulator 本地编译通过，无发布/签名承诺 |
| Android | JVM tests、lint、APK、本地 Maven consumer、TECNO KM9 真机 | 统一脚本与本地 Maven consumer 通过；API 35 真机已完成 ML Kit 人脸相机、选图取消、EXIF Orientation 6、权限拒绝/重新授权和单图推理；API 26 下限、持续资源/热量和系统设置页恢复仍未验收 |
| Benchmark | Python schema/分组/百分位测试；原生 Demo 采集入口 | 无设备实测结果时不生成虚假样本 |
| 同步 | 两端 refs 新鲜读取并比较 | 仅已提交分支；未提交文件不属于远端交付 |

原始证据目录：`.superpowers/sdd/2026-10-15-ondevice-ai-kit-foundation`（本机任务记录）与 `artifacts/verification`；SDK/APP 产物为 `artifacts/xcframeworks`、`artifacts/iOSDemo`、`artifacts/BinaryBuild`、`demo/androidApp/build/outputs/apk`。

## 尚未验证

iOS15、Android API26 仍需设备单项签收。现代 iPhone 已验启动、合成空白 Vision、连续帧控件、前后台恢复、显式停止和照片选择器取消；照片选择/旋转/镜像、完整权限及离线场景仍待 P4。现代 Android API35 已完成相机 ML Kit、选图取消、Orientation 6、权限拒绝/重新授权和单图推理；持续摄像头背压、峰值内存、热状态和能耗没有实测。iOS27 模拟器 Vision 的错误不被计为成功空脸结果。没有公开或内部制品仓库发布，没有自动镜像 CI。

## 分阶段恢复

执行范围已改为 [ROADMAP](../ROADMAP.md)。P0/P1/P2/P3 已完成；当前 P4 仅完成用户反馈的相机闪动和前后台恢复部分。独立整分支审查因工具用量限制未完成，不宣称审查通过，不合入 main。

本次复核日志：`verify-android-checkpoint.log`（BUILD SUCCESSFUL，290 tasks，其中 278 up-to-date）、`maven-consumer.log`（BUILD SUCCESSFUL，36 tasks，其中 29 up-to-date）；已通过缓存检查与构建，不声称全部测试重新执行。四模块本地 Maven 发布及 lint 的前次成功日志为 `distribution-retry.log`（277 tasks）。

真机使用已由用户授权；实际安装、合成空白推理证据见 [P3](p3-ios-device.md)，相机 UI 验证见 [P4](p4-camera-stability.md)。

P1 生命周期审查与修复已完成，详见 [P1 证据](p1-lifecycle-review.md)。当前通过的是这一范围的审查与回归；安装器、桥接、Demo 交互等仍按后续包推进。P1 后旧 XCFramework/Maven 产物尚未重打包，源码/APK 验证与旧二进制不可混同。

P2 模型存储审查及修复见 [P2 证据](p2-model-store-review.md)。P3/P4 新鲜 Apple/Android 统一检查通过；其中 KMP Gradle 测试任务命中已有成功缓存，不声称每次重跑。旧分发产物仍待 P5 按最终源码重新生成及验收。
