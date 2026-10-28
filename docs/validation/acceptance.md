# Foundation 验收记录

更新日期：2026-10-28（保留分包历史证据）。工作分支 `feat/sdk-foundation`；代码提交以 git 历史为准。构建日志与本机产物放在忽略目录，不提交模型权重、照片、设备标识或签名材料。

## 当前统一口径

用户要求遗留问题后续专项推进、本轮尽快收尾；执行状态为 `closed-with-followups`，见[收尾记录](roadmap-closure-20261028.md)。[F01–F12](foundation-followups.md)覆盖全部9项 readiness 能力及分发/审查缺口。P4/P11 原验收保持未完成，发布仍 `open/not-ready`；下面是已冻结证据，不是新的持续执行指令。

基础里程碑仍为 **10/12**：P4 和 P11 未完成。最新 P4 线程校准在当前源码生成的独立 probe 上完成三轮页面退出/重入和同 PID 301 秒 Leaks 录制：三轮窗口新增 687/661/660 次结果，最后页面的 owner、bridge、Compose controller 和 Metal layer 均释放；trace 最终仍有 18×GrOp/2304B live。4097B marker 校准了导出的低32位 thread label，但7个 GrOp label 对应多个不同 kernel thread ID，尚未证明析构/free/TLS 归属。证据见 [P4 AtlasTextOp 调查](p4-atlas-text-cache.md)；不把页面弱引用释放或严格并发门禁升级为零泄漏、最低系统或 P4 完成。

P4 当前实现/验证包已取得 fresh 全范围 review：0 Critical、2 Important、0 Minor。默认 resource probe 静默吞掉二次报告写入失败的问题已按 compile RED→GREEN 修复，并通过新生成 generic iOS15 unsigned build；受保护 signing 工程被明确排除。上游依赖评估没有找到可支持的 Compose/Skiko/Skia 升级修复，故审查子门槛关闭但 native residual、真机 EXIF 与最低系统继续开放。范围、哈希与验证见 [P4 全范围审查](p4-full-review-20261028.md)。

P11 的 SDK committed-tree 快照已经冻结，但统一候选仍未就绪：剩余模型 revision、跨能力 fixture/设备矩阵，以及所有已接入能力的统一任务/质量/资源/取消报告尚未收口。LiteRT-LM 生产 factory、授权模型、真实 native drain 与 Android 推理仍未验证；iOS15/API26 设备缺口继续按设备矩阵记录。

P4 ThreadInventory 主机门禁已刷新：Swift6 主机100轮快照 complete、send-right 2→2，缺失 pthread 映射正确为 partial；iOS15 arm64 typecheck 与生成器 Ruby 语法检查通过。该证据只覆盖主机快照/释放完整性，不证明真机线程生命周期、TLS/destructor 归属、GrOp 根因或零泄漏。证据见 `artifacts/verification/p4-thread-inventory-local-20261027/summary.json`。

P11 candidate freeze 仍为 `not-ready`，但 SDK 子门槛已关闭：从提交 `7b9f7fc75772b61948d211d72ec2e1e221362591` 的 committed tree 生成了干净归档，内嵌 commit、tree 与 SHA-256 已核对；归档排除当前工作树 1 项 tracked dirty 和 7 项 untracked。LiteRT-LM 授权模型/真实 drain、Face Identity 生产模型与校准、iOS15/API26 设备、可比较 GPU/energy/峰值资源及统一 fixture 仍未满足。因此只冻结 SDK 源码快照，不构成统一发布候选或最终验收。见 `artifacts/verification/p11-candidate-freeze-20261027/summary.json`。

P11 readiness 快照现有只读校验门禁：当前 9 行均有未验证 gap；非 terminal 行仍强制 `open/not-ready`，且快照必须声明 `p11Stop` 与 `requiredBeforeP11`。修复前未纳入哈希的证据内容可变更而仍通过，回归先 RED 后 GREEN；完整工具套件在可写 Swift 编译缓存的环境中 **156/156**。该结果只证明报告完整性，不改变 P11 的未完成状态。新证据见 `artifacts/verification/p11-evidence-hashes-20261027/summary.json`；原始 154 项历史验证留在 `artifacts/verification/p11-readiness-validator-20261027/summary.json`。

P11 分组 benchmark 首版已从哈希固定的原始 JSON 重算出3组：iOS/Android face 与 Apple MLXVLM，按 device、OS、P11 capability、actual backend 分组。readiness 的9项能力全部进入 coverage，但仅2项有兼容分组报告，另外7项保持 `unverified`；face quality/peak memory/energy/GPU 与 MLXVLM natural quality/Metal GPU peak/energy 没有被补成通过。详见 [P11 分组 benchmark](p11-grouped-benchmark.md)。

| 项目 | 证据 | 结论 |
| --- | --- | --- |
| Swift SDK | `scripts/verify-apple.sh` 的 XCTest 和原生消费者日志 | 本次 P5 脚本通过：33 Swift XCTest、原生消费者和 Demo 构建；macOS 测试不替代 iPhone |
| KMP export | Swift bridge typecheck、KMP simulator tests | 跨语言接口编译与注入验证 |
| iOS Demo | iOS27 模拟器安装/启动、合成空白图输入 | 模拟器启动和照片选择器取消 UI 用例已验、Vision Code9；现代 iPhone 已验合成空白推理，Photo Picker 引导卡修复后常规交互 5/5 通过，两分钟持续相机另有 1/1 通过，见 P3/P4 记录；冻结 `927b7749` 的 iPhone 16 Pro Max benchmark 已生成 |
| Apple 二进制 | P5 人脸四模块 + P6 六模块 archive/XCFramework；speech/face 独立消费者构建和依赖隔离日志 | arm64 iOS/simulator 本地编译通过；语音与人脸依赖可裁剪，无发布/签名承诺 |
| Android | JVM tests、lint、APK、本地 Maven consumer、TECNO KM9 真机 | 统一脚本与本地 Maven consumer 通过；API 35 真机已完成 ML Kit 人脸相机、选图取消、EXIF Orientation 6、权限拒绝/重新授权和单图推理；另有两分钟相机 PSS/热状态抽样，见 P4；API 26 下限、长期资源/能耗和系统设置页恢复仍未验收 |
| Benchmark | Python schema/分组/百分位测试；原生 Demo 采集入口 | iOS 真机和 Android API35 均以 `927b7749` 完成 5+30 样本；P11 首版从三份原始 JSON 重算3组并明确7项能力 gap；当前四模块 Apple/Android 消费者构建通过；API26/iOS15、统一质量/峰值资源/能耗仍待完成 |
| 同步 | 两端 refs 新鲜读取并比较 | 仅已提交分支；未提交文件不属于远端交付 |

原始证据目录：`.superpowers/sdd/2026-10-15-ondevice-ai-kit-foundation`（本机任务记录）与 `artifacts/verification`；SDK/APP 产物为 `artifacts/xcframeworks`、`artifacts/iOSDemo`、`artifacts/BinaryBuild`、`demo/androidApp/build/outputs/apk`。

## 尚未验证

iOS15、Android API26 仍需设备单项签收。现代 iPhone 已验启动、合成空白 Vision、连续帧控件、前后台恢复、显式停止、照片选择/取消和两分钟持续相机 UI；真实相机权限往返由用户手动确认，随后相机/Vision 自动化复核 1/1 通过，不计为 Settings 自动化全流程通过。旋转/镜像及离线场景仍待 P4。现代 Android API35 已完成相机 ML Kit、选图取消、Orientation 6、权限拒绝/重新授权和单图推理；两端持续相机的资源/热状态数据均为有限抽样，持续背压、峰值内存、GPU、能耗和完整资源释放仍未实测。iOS27 模拟器 Vision 的错误不被计为成功空脸结果。没有公开或内部制品仓库发布，没有自动镜像 CI。

## 分阶段恢复

执行范围已改为 [ROADMAP](../ROADMAP.md)。P0/P1/P2/P3 已完成；P4 已完成的交互/资源抽样见设备矩阵，剩余 EXIF、资源释放和最低系统项保持待验收。P5 人脸分发与 benchmark、P6 语音分发的当前证据见上表和对应记录。P4 当前范围的 fresh review 已完成并处理 findings；它不替代整个 foundation 分支、P11 或最终发布审查，也不授权合入 main。

本次复核日志：`verify-android-checkpoint.log`（BUILD SUCCESSFUL，290 tasks，其中 278 up-to-date）、`maven-consumer.log`（BUILD SUCCESSFUL，36 tasks，其中 29 up-to-date）；已通过缓存检查与构建，不声称全部测试重新执行。四模块本地 Maven 发布及 lint 的前次成功日志为 `distribution-retry.log`（277 tasks）。

真机使用已由用户授权；实际安装、合成空白推理证据见 [P3](p3-ios-device.md)，相机 UI 验证见 [P4](p4-camera-stability.md)。

P1 生命周期审查与修复已完成，详见 [P1 证据](p1-lifecycle-review.md)。当前通过的是这一范围的审查与回归；安装器、桥接、Demo 交互等仍按后续包推进。P1 后旧 XCFramework/Maven 产物尚未重打包，源码/APK 验证与旧二进制不可混同。

P2 模型存储审查及修复见 [P2 证据](p2-model-store-review.md)。P3/P4 新鲜 Apple/Android 统一检查通过；其中 KMP Gradle 测试任务命中已有成功缓存，不声称每次重跑。旧分发产物仍待 P5 按最终源码重新生成及验收。

2026-10-17 P5 复核已更新上表：两端真机 benchmark 冻结 `927b7749`；人脸四模块分发已重新构建，修复 Apple target 遗漏 Client 源文件并验证显式选择消费者。前述 P1/P2 的“旧产物待重打包”是历史状态，本轮已补人脸分发证据；P6 又补齐两端语音模块的本地分发、published consumer、依赖隔离和 lint/JVM 回归；Android ASR 资源缺失、iOS15/API26、iOS 麦克风 ASR、断网与质量验收仍保持未完成。详细路径与未测项见 [benchmark](benchmark.md) 与 [语音后端记录](speech-backends.md)。

## 2026-10-17 Apple 文件 ASR SDK 收口

公共契约、可选 Apple 后端和分发三个 Task 已提交。最新完整 Swift XCTest 58/58、语音 31/31、Python 工具测试 44/44（含分发生成器 3/3）通过；全新六模块 XCFramework、unsigned SDK/二进制消费者构建、Core+Speech 独立 typecheck 和 12 个框架的动态依赖检查通过。日志归档在忽略目录 `artifacts/P6/apple-file-asr/verification/`，不把末尾 Swift Testing 的零测试 sibling summary 当成 XCTest 总数。

实际 SDK `5fd0b2d` 在 iPhone 16 Pro Max / iOS 27.0.1 通过固定合成文件转写、活动任务取消、drain、输入保持不变、同进程重入及关闭后拒绝。报告及 hash 见 [语音后端记录](speech-backends.md#2026-10-17-apple-文件-asr-sdk-后端)。既有语音授权被复用，麦克风/音频 session、iOS26 资源、iOS15、自然语音质量和网络行为仍未验收。

该工作包最终独立架构审查 CLEAR，无 Critical/Important；一个 LOW 文件 metadata 错误映射延后处理。此前两次服务 429 与作者自审是过程记录，不作为最终独立审查证据；此结论仅覆盖 Apple 文件 ASR 工作包，不替代整条 roadmap 的验收。
