# 分阶段交付与恢复入口

更新时间：2026-10-16。GitHub 为主仓，极狐 `home/common/ondevice-ai-kit` 同步显式分支。当前实现位于 `feat/sdk-foundation`，未合入 main。

## 执行规则

用户要求分多步推进，避免一个长任务实现全部工程。**每次只执行一个下表中的工作包**：限定输入、改动范围、验证和停止点；完成后提交并同步，报告结果。后续阶段仍是项目目标，但不在同一轮自动串行实现。原十项 foundation 计划保留为历史细节，不再作为一次执行到底的指令。

- 继续时先读本文件、验收记录和 git 状态，选择当前未完成工作包；不重复已验证工作。
- 工作包发现跨范围缺陷时，记录为后续项；本包无法成立的阻塞才在包内修复。
- 已授权直接使用本地真机做 device-only 验证；仅操作本项目 App，保留设备其他 App/资料。使用已有签名配置，不自行创建或变更证书、描述文件等账户资产。
- 测试/模拟器/真机/最低系统验证分别记录；构建通过不代表真机已通过。
- 不逐项重新请求继续许可。每个包以可复核提交、验证和剩余项结束，后续“继续”推进下一个包。

## 当前范围与后续功能

当前 SDK/Demo 的算法入口只有**人脸检测与关键点**，用于验证原生 SDK → KMP 桥接 → Demo → 真机的完整链路。它尚不提供身份识别、1:1 比对、1:N 搜索或活体认证。已实现的 Core、任务生命周期、最新帧队列、ModelStore 和分发工程是后续能力共用的基础设施；当前人脸后端使用系统/随库模型，尚无自有大模型下载后加载推理的完整链路。

| 后续能力 | SDK 与 Demo 预计交付 | 对应阶段 |
| --- | --- | --- |
| ASR / TTS | 原生识别/合成接口、取消与音频生命周期；KMP Demo 的语音输入/播放与能力状态 | P6 调研和单后端探针，随后按独立工作包完成 SDK/Demo 接入 |
| 视频超分 | 视频处理接口、进度/取消、输出文件；Demo 输入/输出与质量/性能对比 | P7 调研和单链路探针，随后完成选定后端的 SDK/Demo 接入 |
| 图像理解 / 图生文 / 多模态 | 模型会话、生成/取消、结果展示；按模型支持范围增加输入类型 | P8 比较 SmolVLM2、Florence-2、MiniCPM-V 等，P9 先验证单模型/单平台，再分包接入 SDK/Demo 及另一平台 |
| 模型管理与按需加载 | 模型下载/校验/安装/删除、运行时加载/卸载、版本与资源状态；Demo 提供相应流程 | 现有 ModelStore 基础上，随首个自有模型接入补齐真实链路 |
| 后端与性能比较 | 按能力查询可用性、明确实际后端；模型/后端选择和 benchmark 展示 | P5 建立验收基线，各能力接入时补充样本与指标，P11 统一验收 |
| 人脸身份与活体 | 1:1、1:N、活体分别设计接口和数据生命周期，再增加 Demo 流程 | P10 后续 TODO |

这些仍是项目后续目标。P6/P7/P8/P9 的“调研/探针”不是完整功能交付；通过许可证、转换/运行及设备资源验证后，再为选定方案创建具体实现包。Core ML、MLX、LiteRT/TensorFlow Lite、ONNX、llama.cpp 是待按能力选择的后端，不要求所有模型在所有框架/平台重复实现。iOS15 保持基础下限，高版本能力按 availability 或独立增强模块接入。

## 等待设备的 TODO

2026-10-16 用户确认暂时没有 iOS15 真机；随后提供 TECNO KM9（Android 15/API 35）用于现代 Android 验证。以下最低系统验收显式挂起，等对应设备可用后恢复；期间不重复要求设备，也不将这些缺口作为其他可独立工作包的阻塞。

- [ ] **DEVICE-IOS15**：取得 iOS15 真机后，验证基础宿主启动、权限、照片/相机、旋转/镜像、取消、前后台和离线推理；验证可选高版本后端不会破坏基础宿主启动。
- [ ] **DEVICE-ANDROID-API26**：取得 API26 真机后，验证最低系统启动及 ML Kit 推理。当前 API35 设备已完成相机冒烟与流式 UI 修复验证；照片、权限、旋转/镜像、取消、前后台和离线完整行为仍在 P4 范围内。

状态继续记为未验证，不以模拟器/编译结果代替真机证据。验收行见 [设备矩阵](validation/device-matrix.md)。

## 当前阶段与独立工作包

| 包 | 交付与边界 | 验证/停止点 | 当前状态 |
| --- | --- | --- | --- |
| P0 当前快照收口 | 阶段表、验收证据、同步现有代码；不新增算法 | 当前检查结果落盘，两端分支 SHA 相等 | 已收口，随本次提交同步 |
| P1 SDK 生命周期审查 | 仅 TaskGate、LatestFrameQueue、Vision/MLKit 取消与 close 的资源所有权；不改 Demo UI/安装器 | 独立审查、必要问题 RED→GREEN、对应测试通过；提交后停止 | 已完成，见 P1 审查记录 |
| P2 模型存储审查 | 仅 manifest、原子安装、下载取消/合并、lease；不接新模型 | 路径/损坏/中断/并发用例和两端测试；提交后停止 | 已完成，见 [P2 审查](validation/p2-model-store-review.md) |
| P3 iOS 真机最小闭环 | 发现设备/已有签名，安装本项目 Demo，合成空白输入→Vision，导出结果；不加入 VLM | 记录设备 OS、后端、成功空集合或真实错误；无可用签名则记录精确阻塞；停止 | 已完成，见 [P3 真机结果](validation/p3-ios-device.md) |
| P4 Demo 交互闭环 | 照片、相机、旋转/镜像、取消、前后台；原生桥接资源释放 | 每项可复现结果；无相应设备时保持缺口；提交后停止 | 部分完成：iOS 相机/前后台、照片选择/取消与 Android API35 相机、选图取消、Orientation 6、权限拒绝/重新授权已验，见 [P4 记录](validation/p4-camera-stability.md)；iOS 旋转/镜像等仍待验收 |
| P5 benchmark 与分发验收 | 固定输入 5 次预热+30 次采样、JSON→报告；Maven/XCFramework 独立消费者 | 真机样本与分组报告可复核；未测项为 null；停止 | 部分完成：工具、Apple/Android host 与分发验证、iOS27 和 Android API35 均以冻结 commit 完成 5+30 样本；最低系统和资源测量仍待补 |
| P6 语音调研与小探针 | 独立对比系统 ASR/TTS、离线资源、iOS26 可选能力 | 先完成 speech-backends.md，再选择一个后端最小探针；独立停止 | 部分完成：TTS/插件边界、macOS/AVSpeech host 探针和 Android API35 系统 TTS 探针已记录；ASR 官方可用性边界已核对，真实 ASR 与 iOS 设备探针待补 |
| P7 视频超分调研与小探针 | iOS26 系统能力、CoreML、Android ONNX/LiteRT；移动端约束 | 先完成 video-sr-backends.md，再做一条固定视频链路；独立停止 | 部分完成：候选比较、固定视频协议和无模型 baseline 预检已完成；模型和单平台链路待选 |
| P8 VLM 调研 | SmolVLM2/huggingsnap、Florence-2、MiniCPM-V及其他指定 checkpoint | mobile-vlm.md 记录许可、平台、体积、内存、证据等级；不同时接入多个模型 | 已完成候选调研与 P9 单路线选择，见 [P8 记录](validation/mobile-vlm.md)；移动端实测归 P9 |
| P9 VLM 单模型试验 | 根据 P8 选择一个 checkpoint、一个平台、一个 runtime | 固定图像→输出→资源/耗时原始证据；随后另包推进第二平台 | 单平台探针已通过：SmolVLM2 500M-Video-Instruct-mlx + iOS18/MLXVLM；固定资产、模拟器 15/15、签名设备构建、iPhone 16 Pro Max 成功/取消/重复会话均有证据；峰值内存未测，Android 第二平台待后续 |
| P10 人脸身份/活体设计 | 1:1、1:N、活体分别设计数据与指标 | [face-identity.md](validation/face-identity.md)；阈值/FAR/FRR、授权删除与特征生命周期；独立停止 | 设计草案待审；实现与真实数据/设备验收待后续独立包 |
| P11 SDK/Demo 最终 benchmark 验收 | 冻结候选 SDK commit、模型 revision、测试输入和设备矩阵；对届时实际交付的每项能力与已接入后端统一运行正确性、质量、延迟、资源、取消和持续运行验证 | 保留逐 run 原始 JSON；生成按设备/OS/能力/后端分组的 p50/p95 与质量报告及一页状态矩阵；逐项标记 pass/fail/unverified/unavailable，列出未达标项和发布结论；停止 | 待各能力实现与分阶段 benchmark 完成后执行 |

P1/P2 是此前未完成独立审查的拆分；二者已完成，但基础 SDK 仍需后续 P3/P4/P5 验收，不能据此声称完整工程可发布。P3 不要求先解决模拟器 Vision Code9；真机结果另行记录。iOS15 保持基础下限，高版本能力通过 availability/独立 backend 接入。

P11 是最终统一验收包，不替代 P5 或各能力接入时的基准测试。执行前为每项能力预先固定样本、质量指标和合格阈值：人脸检查检测结果与坐标；ASR 使用 CER/WER、TTS 检查语言/输出与可复核质量；视频超分检查帧数、时间戳、音轨、解码和适用画质指标；多模态使用固定任务集和可复核输出。各项记录冷启动、加载、预热、推理、峰值内存和测量方法；不能可靠测量的资源指标记为未测，不填代理值。系统与第三方后端、离线与联网路径分组展示，不合并成跨能力总分。

2026-10-17：已完成现有人脸检测 Client 的 Default/Explicit 后端注册与错误契约验证，见 [后端选择验证](validation/backend-selection.md)。这只验证统一选择入口，不代表 TTS、ASR、身份、超分或 VLM 的后端已全部实现。
同日已将选择值传递到 KMP Bridge 与两端 Demo 的人脸检测调用；目前各平台只注册一个基础后端，显式选择第二个生产后端、UI 选择器和真机验证仍待后续能力包。

设备矩阵至少区分 iOS 15 基线、较新 iOS、Android API 26 基线和较新 Android；同一能力/后端仅在实际支持的设备上执行。缺设备、权限、凭据或模型资产时标记 unverified/unavailable 并写明原因，不能以构建或模拟器结果替代真机。未实现的研究候选不进入通过率分母；已计划交付但尚未实现的能力不得标记 pass。汇总页使用可读的状态矩阵和指标摘要支持 SDK/Demo 评估，发布结论需列明阻断项及证据链接。

## 已有实现与证据

截至代码快照 `c03003f`：原生 Core、ModelStore、Face、Vision/MLKit，KMP Demo，benchmark 报告工具和本地分发工程已提交。这是开发快照，**不是完整工程验收完成或可发布版本**。

- Apple 验证脚本已通过：19 个 Swift XCTest、3 个 KMP 测试的已有报告、桥接 typecheck、原生消费者与 Demo 构建。两平台 archive 和四个 XCFramework 已生成；二进制消费者构建通过。
- Android 已有 11 个 JVM 测试报告通过；Demo/原生消费者 APK 离线构建通过。后续重试完成四模块 Maven 本地发布和 lint；当前统一检查与 Maven 消费者复核结果见 acceptance.md。
- Python benchmark/sync 测试 7/7 通过。
- iOS27 模拟器 Demo 启动成功，但 Vision 实际推理报 Code9，未生成有效 benchmark；不算成功空脸。iPhone 16 Pro Max / iOS27.0.1 的合成空白 Vision 真机闭环已通过，见 P3；iOS15 和 Android 设备仍未验证。
- 原整分支 reviewer 未返回审查结果：工具报告用量限制，不能视为审查通过。后续分 P1/P2/P4/P5 各包完成必要审查。

## 恢复时的第一步

P0、P1、P2、P3 已完成。P1 见 [生命周期审查](validation/p1-lifecycle-review.md)，P2 见 [模型存储审查](validation/p2-model-store-review.md)，P3 见 [真机结果](validation/p3-ios-device.md)。P4 的 iOS 相机/前后台、真机照片选择/取消与 Android API35 相机、照片和部分权限交互已有结果，见 [P4 记录](validation/p4-camera-stability.md)。下一次仅推进 P4 剩余 iOS 真机旋转/镜像与资源释放验收；本地签名已可运行 UI runner。iOS15/API26 最低系统项继续等待设备，不以其他验证代替。仍按每轮一个工作包推进。

详细设计：[SDK 设计](superpowers/specs/2026-10-15-ondevice-ai-sdk-design.md)。历史计划：[foundation](superpowers/plans/2026-10-15-ondevice-ai-kit-foundation.md)。证据与缺口：[验收](validation/acceptance.md)、[设备矩阵](validation/device-matrix.md)。
