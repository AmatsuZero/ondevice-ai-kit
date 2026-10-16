# 分阶段交付与恢复入口

更新时间：2026-10-16。GitHub 为主仓，极狐 `home/common/ondevice-ai-kit` 同步显式分支。当前实现位于 `feat/sdk-foundation`，未合入 main。

## 执行规则

用户要求分多步推进，避免一个长任务实现全部工程。**每次只执行一个下表中的工作包**：限定输入、改动范围、验证和停止点；完成后提交并同步，报告结果。后续阶段仍是项目目标，但不在同一轮自动串行实现。原十项 foundation 计划保留为历史细节，不再作为一次执行到底的指令。

- 继续时先读本文件、验收记录和 git 状态，选择当前未完成工作包；不重复已验证工作。
- 工作包发现跨范围缺陷时，记录为后续项；本包无法成立的阻塞才在包内修复。
- 已授权直接使用本地真机做 device-only 验证；仅操作本项目 App，保留设备其他 App/资料。使用已有签名配置，不自行创建或变更证书、描述文件等账户资产。
- 测试/模拟器/真机/最低系统验证分别记录；构建通过不代表真机已通过。
- 不逐项重新请求继续许可。每个包以可复核提交、验证和剩余项结束，后续“继续”推进下一个包。

## 当前阶段与独立工作包

| 包 | 交付与边界 | 验证/停止点 | 当前状态 |
| --- | --- | --- | --- |
| P0 当前快照收口 | 阶段表、验收证据、同步现有代码；不新增算法 | 当前检查结果落盘，两端分支 SHA 相等 | 已收口，随本次提交同步 |
| P1 SDK 生命周期审查 | 仅 TaskGate、LatestFrameQueue、Vision/MLKit 取消与 close 的资源所有权；不改 Demo UI/安装器 | 独立审查、必要问题 RED→GREEN、对应测试通过；提交后停止 | 已完成，见 P1 审查记录 |
| P2 模型存储审查 | 仅 manifest、原子安装、下载取消/合并、lease；不接新模型 | 路径/损坏/中断/并发用例和两端测试；提交后停止 | 已完成，见 [P2 审查](validation/p2-model-store-review.md) |
| P3 iOS 真机最小闭环 | 发现设备/已有签名，安装本项目 Demo，合成空白输入→Vision，导出结果；不加入 VLM | 记录设备 OS、后端、成功空集合或真实错误；无可用签名则记录精确阻塞；停止 | 已完成，见 [P3 真机结果](validation/p3-ios-device.md) |
| P4 Demo 交互闭环 | 照片、相机、旋转/镜像、取消、前后台；原生桥接资源释放 | 每项可复现结果；Android 无设备时保持缺口；提交后停止 | 部分完成：相机闪动/前后台修复已验，见 [P4 记录](validation/p4-camera-stability.md) |
| P5 benchmark 与分发验收 | 固定输入 5 次预热+30 次采样、JSON→报告；Maven/XCFramework 独立消费者 | 真机样本与分组报告可复核；未测项为 null；停止 | 工具已实现，真机数据待补 |
| P6 语音调研与小探针 | 独立对比系统 ASR/TTS、离线资源、iOS26 可选能力 | 先完成 speech-backends.md，再选择一个后端最小探针；独立停止 | 未开始 |
| P7 视频超分调研与小探针 | iOS26 系统能力、CoreML、Android ONNX/LiteRT；移动端约束 | 先完成 video-sr-backends.md，再做一条固定视频链路；独立停止 | 未开始 |
| P8 VLM 调研 | SmolVLM2/huggingsnap、Florence-2、MiniCPM-V及其他指定 checkpoint | mobile-vlm.md 记录许可、平台、体积、内存、证据等级；不同时接入多个模型 | 未开始 |
| P9 VLM 单模型试验 | 根据 P8 选择一个 checkpoint、一个平台、一个 runtime | 固定图像→输出→资源/耗时原始证据；随后另包推进第二平台 | 未开始 |
| P10 人脸身份/活体设计 | 1:1、1:N、活体分别设计数据与指标 | face-identity.md；阈值/FAR/FRR、授权删除与特征生命周期；独立停止 | 后续 TODO |

P1/P2 是此前未完成独立审查的拆分；二者已完成，但基础 SDK 仍需后续 P3/P4/P5 验收，不能据此声称完整工程可发布。P3 不要求先解决模拟器 Vision Code9；真机结果另行记录。iOS15 保持基础下限，高版本能力通过 availability/独立 backend 接入。

## 已有实现与证据

截至代码快照 `c03003f`：原生 Core、ModelStore、Face、Vision/MLKit，KMP Demo，benchmark 报告工具和本地分发工程已提交。这是开发快照，**不是完整工程验收完成或可发布版本**。

- Apple 验证脚本已通过：19 个 Swift XCTest、3 个 KMP 测试的已有报告、桥接 typecheck、原生消费者与 Demo 构建。两平台 archive 和四个 XCFramework 已生成；二进制消费者构建通过。
- Android 已有 11 个 JVM 测试报告通过；Demo/原生消费者 APK 离线构建通过。后续重试完成四模块 Maven 本地发布和 lint；当前统一检查与 Maven 消费者复核结果见 acceptance.md。
- Python benchmark/sync 测试 7/7 通过。
- iOS27 模拟器 Demo 启动成功，但 Vision 实际推理报 Code9，未生成有效 benchmark；不算成功空脸。iPhone 16 Pro Max / iOS27.0.1 的合成空白 Vision 真机闭环已通过，见 P3；iOS15 和 Android 设备仍未验证。
- 原整分支 reviewer 未返回审查结果：工具报告用量限制，不能视为审查通过。后续分 P1/P2/P4/P5 各包完成必要审查。

## 恢复时的第一步

P0、P1、P2、P3 已完成。P1 见 [生命周期审查](validation/p1-lifecycle-review.md)，P2 见 [模型存储审查](validation/p2-model-store-review.md)，P3 见 [真机结果](validation/p3-ios-device.md)。用户反馈使本包额外完成 P4 的相机闪动及前后台恢复修复，见 [P4 记录](validation/p4-camera-stability.md)。下一次仅推进 P4 剩余照片/取消、旋转/镜像与资源释放验收；不要同时开始 P5 或新算法研究。

详细设计：[SDK 设计](superpowers/specs/2026-10-15-ondevice-ai-sdk-design.md)。历史计划：[foundation](superpowers/plans/2026-10-15-ondevice-ai-kit-foundation.md)。证据与缺口：[验收](validation/acceptance.md)、[设备矩阵](validation/device-matrix.md)。
