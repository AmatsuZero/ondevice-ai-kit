# 分阶段交付与恢复入口

更新时间：2026-10-20（本次进度快照截至 `233873e`）。GitHub 为主仓，极狐 `home/common/ondevice-ai-kit` 同步显式分支。当前实现位于 `feat/sdk-foundation`，未合入 main；远端同步结果以 GitHub / 极狐该分支的新鲜 SHA 核对为准。

## 执行规则

用户最新要求是**继续推进，直到 roadmap 完成**。按下表工作包限定输入、改动范围与验证，逐项实施、复审和提交后自动进入下一项。设备、凭据、商用模型许可与校准缺口分别保留，不能把局部测试通过视为完整交付。原十项 foundation 计划保留为历史细节。

- 继续时先读本文件、验收记录和 git 状态，选择当前未完成工作包；不重复已验证工作。
- 工作包发现跨范围缺陷时，记录为后续项；本包无法成立的阻塞才在包内修复。
- 已授权直接使用本地真机做 device-only 验证；仅操作本项目 App，保留设备其他 App/资料。使用已有签名配置，不自行创建或变更证书、描述文件等账户资产。
- 测试/模拟器/真机/最低系统验证分别记录；构建通过不代表真机已通过。
- 不逐项重新请求继续许可。每个包记录可复核提交、验证和剩余项，再推进下一个可执行包。

## 当前范围与后续功能

当前 KMP Demo 已有**人脸检测与关键点**入口，以及独立的身份/活体控件与宿主配置入口；原生 SDK 已接入系统 TTS 与文件 ASR。语音工作包已完成公共契约、能力级后端查询、双端 adapter、iOS 文件 lease、共享状态机和语音页面；两端宿主接线、权限与 Demo 真机验收尚未完成，不能视为语音 Demo 已形成真实调用闭环。VLM 与视频超分的独立探针状态见下表。当前尚不提供生产身份识别、1:1 比对、1:N 搜索或活体认证。已实现的 Core、任务生命周期、最新帧队列、ModelStore 和分发工程是后续能力共用的基础设施；P10 已实现独立身份存储、授权生命周期、类型化 bridge 与可裁剪分发；生产算法门槛仍待通过。

| 后续能力 | SDK 与 Demo 预计交付 | 对应阶段 |
| --- | --- | --- |
| ASR / TTS | 原生识别/合成接口、取消与音频生命周期；KMP Demo 的语音输入/播放与能力状态 | P6 调研和单后端探针，随后按独立工作包完成 SDK/Demo 接入 |
| 视频超分 | 视频处理接口、进度/取消、输出文件；Demo 输入/输出与质量/性能对比 | P7 调研和单链路探针，随后完成选定后端的 SDK/Demo 接入 |
| 图像理解 / 图生文 / 多模态 | 模型会话、生成/取消、结果展示；按模型支持范围增加输入类型 | P8 比较 SmolVLM2、Florence-2、MiniCPM-V 等，P9 先验证单模型/单平台，再分包接入 SDK/Demo 及另一平台 |
| 模型管理与按需加载 | 模型下载/校验/安装/删除、运行时加载/卸载、版本与资源状态；Demo 提供相应流程 | 现有 ModelStore 基础上，随首个自有模型接入补齐真实链路 |
| 后端与性能比较 | 按能力查询可用性、明确实际后端；模型/后端选择和 benchmark 展示 | P5 建立验收基线，各能力接入时补充样本与指标，P11 统一验收 |
| 人脸身份与活体 | 1:1、1:N、活体分别设计接口和数据生命周期，再增加 Demo 流程 | P10 已完成原生契约/存储/引擎、bridge/Demo 与独立本地分发，最终独立审查待完成；生产模型/PAD/校准待定 |

这些仍是项目后续目标。P6/P7/P8/P9 的“调研/探针”不是完整功能交付；通过许可证、转换/运行及设备资源验证后，再为选定方案创建具体实现包。Core ML、MLX、LiteRT/TensorFlow Lite、ONNX、llama.cpp 是待按能力选择的后端，不要求所有模型在所有框架/平台重复实现。iOS15 保持基础下限，高版本能力按 availability 或独立增强模块接入。

## 等待设备的 TODO

2026-10-16 用户确认暂时没有 iOS15 真机；随后提供 TECNO KM9（Android 15/API 35）用于现代 Android 验证。以下最低系统验收显式挂起，等对应设备可用后恢复；期间不重复要求设备，也不将这些缺口作为其他可独立工作包的阻塞。

- [ ] **DEVICE-IOS15**：取得 iOS15 真机后，验证基础宿主启动、权限、照片/相机、旋转/镜像、取消、前后台和离线推理；验证可选高版本后端不会破坏基础宿主启动。
- [ ] **DEVICE-ANDROID-API26**：取得 API26 真机后，验证最低系统启动及 ML Kit 推理。当前 API35 设备已完成相机冒烟与流式 UI 修复验证；照片、权限、旋转/镜像、取消、前后台和离线完整行为仍在 P4 范围内。

状态继续记为未验证，不以模拟器/编译结果代替真机证据。验收行见 [设备矩阵](validation/device-matrix.md)。

## 当前阶段与独立工作包

截至本次快照，12 个阶段中 **5 个已完成（P0–P3、P8），7 个尚未收口（P4–P7、P9–P11）**。阶段大小不同，此计数不代表工时完成比例。主要剩余工作为：P4 交互/资源释放与最低系统验收，P5 资源指标，P6 语音宿主与后续麦克风/转写/质量验证，P7 超分 SDK/Demo 与 Android，P9 多模态 SDK/Demo、模型管理与 Android，P10 生产模型/PAD/授权数据/校准及独立审查，以及 P11 统一验收和发布结论。最低系统设备缺口独立挂起，不阻断可独立推进的实现。

| 包 | 交付与边界 | 验证/停止点 | 当前状态 |
| --- | --- | --- | --- |
| P0 当前快照收口 | 阶段表、验收证据、同步现有代码；不新增算法 | 当前检查结果落盘，两端分支 SHA 相等 | 已收口，随本次提交同步 |
| P1 SDK 生命周期审查 | 仅 TaskGate、LatestFrameQueue、Vision/MLKit 取消与 close 的资源所有权；不改 Demo UI/安装器 | 独立审查、必要问题 RED→GREEN、对应测试通过；提交后停止 | 已完成，见 P1 审查记录 |
| P2 模型存储审查 | 仅 manifest、原子安装、下载取消/合并、lease；不接新模型 | 路径/损坏/中断/并发用例和两端测试；提交后停止 | 已完成，见 [P2 审查](validation/p2-model-store-review.md) |
| P3 iOS 真机最小闭环 | 发现设备/已有签名，安装本项目 Demo，合成空白输入→Vision，导出结果；不加入 VLM | 记录设备 OS、后端、成功空集合或真实错误；无可用签名则记录精确阻塞；停止 | 已完成，见 [P3 真机结果](validation/p3-ios-device.md) |
| P4 Demo 交互闭环 | 照片、相机、旋转/镜像、取消、前后台；原生桥接资源释放 | 每项可复现结果；无相应设备时保持缺口；提交后停止 | 部分完成：iOS 相机/前后台、照片选择/取消、两分钟持续相机 UI 与 Activity Monitor/thermal 抽样、Settings 恢复入口及用户手动权限往返（恢复后相机/Vision 自动化 1/1），以及 Android API35 相机、选图取消、Orientation 6、权限拒绝/重新授权和两分钟相机 PSS/热状态抽样已验；新增 iOS Debug Orientation 6 合成图共用 ImageIO 解码→Vision，真机结果 80×160，见 [P4 记录](validation/p4-camera-stability.md)。完整 PHPicker EXIF 2/6/7、资源释放及最低系统仍待验收；Leaks detail 已导出：相机路径复现3个GrOp/384B，静态Compose对照1个/128B，纯原生consumer采样0行；渲染责任边界与完整释放仍未验 |
| P5 benchmark 与分发验收 | 固定输入 5 次预热+30 次采样、JSON→报告；Maven/XCFramework 独立消费者 | 真机样本与分组报告可复核；未测项为 null；停止 | 部分完成：冻结提交 `927b7749` 后，iOS27 和 Android API35 均重新完成 5+30 样本；四个 Apple XCFramework（补全现有 Client 的 target 源文件）、四个 Android 人脸 Maven artifact 和显式后端独立消费者均通过；最低系统和峰值内存/能耗测量仍待补。语音 artifact 分发已在 P6 独立验证 |
| P6 语音调研与小探针 | 独立对比系统 ASR/TTS、离线资源、iOS26 可选能力；已有后端按独立工作包接入 SDK/Demo | 原生探针与 Demo 验收分别记录；未测项继续保留 | 部分完成：两端系统 TTS SDK 已完成现代真机成功/取消/关闭验证；Android on-device ASR 模块与严格选择已实现，TECNO KM9 的 en-US 返回 MISSING_ASSETS，真实转写和设备生命周期仍未通过。Apple 文件 ASR SDK `5fd0b2d` 已通过强制 on-device 固定文件转写、活动取消、drain、输入保持、重入及关闭后拒绝，独立架构审查 CLEAR（一个 LOW 延后）；Apple/Android 原生语音分发及消费者依赖裁剪已有通过证据。2026-10-20 语音 bridge/Demo 工作包 Task 1–5 已完成，最新提交 `233873e`：公共契约、能力级查询、Android/iOS adapter、文件 lease 与共享 VM/UI 已实现；iOS 两种 ABI 各 14/14、生成器 5/5、共享 VM 10/10，Android APK 与共享模拟器 framework 构建通过。Task 6 两端 host/权限/独立工程、Task 7 Demo 真机/裁剪/整包审查待完成。iOS 麦克风与音频 session、iOS26 资源、Android ASR 真实转写、两端断网/自然语音质量及最低系统验收继续保留 |
| P7 视频超分调研与小探针 | iOS26 系统能力、CoreML、Android ONNX/LiteRT；移动端约束 | 先完成 video-sr-backends.md，再做一条固定视频链路；独立停止 | 部分完成：iOS 27 真机确认 VideoToolbox 系统 SR 支持 4×并完成 48 帧固定视频链路；已记录内存采样、统一色彩/HEVC 编码的 SR/双三次对照和自然视频真机质量配对（两路均 48/48 帧、BT.709 元数据一致，质量仍为 unverified）；双路流程在 SR 阶段和基线阶段均验证取消无残留输出，重启后两路再处理成功，随后两次取消后同进程重入也已通过（PID 不变、两路各 48 帧）。SR 模型传递函数、逐帧主观质量、Android/最低系统及 SDK/Demo 接入仍待补，见 [P7 记录](validation/video-sr-backends.md) |
| P8 VLM 调研 | SmolVLM2/huggingsnap、Florence-2、MiniCPM-V及其他指定 checkpoint | mobile-vlm.md 记录许可、平台、体积、内存、证据等级；不同时接入多个模型 | 已完成候选调研与 P9 单路线选择，见 [P8 记录](validation/mobile-vlm.md)；移动端实测归 P9 |
| P9 VLM 单模型试验 | 根据 P8 选择一个 checkpoint、一个平台、一个 runtime | 固定图像→输出→资源/耗时原始证据；随后另包推进第二平台 | 单平台探针已通过：SmolVLM2 500M-Video-Instruct-mlx + iOS18/MLXVLM；固定资产、模拟器 16/16、签名设备构建、iPhone 16 Pro Max 成功/取消/重复会话和进程常驻内存采样均有证据；质量基准与 Android 第二平台待后续 |
| P10 人脸身份/活体设计 | 1:1、1:N、活体分别设计数据与指标 | [身份与活体规格](superpowers/specs/2026-10-17-face-identity-liveness-design.md)；阈值、授权删除与特征生命周期；独立停止 | 书面规格已获用户认可；含 12 个工作包的[实施计划](superpowers/plans/2026-10-17-face-identity-liveness.md)已确认实施。实施前核对已修正 selection 命名冲突、Task 4 backend/policy 先于存储的依赖顺序和 codec/授权边界；[模型许可预研](validation/p10-identity-model-selection.md)已有固定上游 revision 的证据，未选模型、未下载权重。Task 1–8 契约、加密存储/删除、gallery、1:1/1:N、活体会话和模型 lease 已实现；Task 11 双端 native bridge、独立 Demo 控件与宿主配置已完成，含 Android API35 UI 冒烟及两种 iOS KMP ABI 测试；Task 12 已提交 bb89516，七个 Apple XCFramework、Identity 独立二进制 consumer 与 Maven consumer 已通过本地验收；最终独立分支审查因模型容量不足仍待完成。最新 Apple371/371、Android Identity319/319、分发与工具测试见[P10 进展](validation/p10-face-identity.md)。Task 9/10 生产模型/PAD、授权数据、校准和最低系统设备仍是未通过门槛 |
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

2026-10-20 **当前执行入口：语音 bridge/Demo Task 6**。按[实施计划](superpowers/plans/2026-10-19-speech-demo-bridge.md)继续两端 host、权限、文件选择器与独立 iOS 工程；Task 1–5 已完成，不重复执行。当前实现提交 `233873e`，Task 6/7 在本次快照时未完成。原[交接文档](HANDOFF-2026-10-20-speech-demo-bridge.md)记录的是 Task 1–3 checkpoint，恢复时结合最新 Git 与该计划的执行 ledger（`.superpowers/sdd/2026-10-19-speech-demo-bridge/progress.md`）核对，不能将旧交接中的 Task 4 入口当作当前进度。

| 语音工作包任务 | 当前状态与证据 |
| --- | --- |
| Task 1 公共 Speech Bridge 契约 | 已完成，`6788b1b`；类型化选择、输入、结果、失败与回调，Swift ABI typecheck 通过 |
| Task 2 能力级后端查询 | 已完成，`331de92`；独立 TTS/ASR 查询、严格选择与 close 竞态测试通过 |
| Task 3 Android adapter | 已完成，`8719a17`；单音频槽、取消/drain、并发 close、输入与隐私测试，Speech 单元测试/lint/binding 编译通过 |
| Task 4 iOS adapter 与输入 lease | 已完成，`7605511`；Binding/Demo 两种 ABI XCTest 各 14/14，生成器 5/5；受保护文件 hash 未变化 |
| Task 5 共享状态机与语音页面 | 已完成，`233873e`；Speech VM 10/10、Face 5/5、Identity 20/20，binding tests、Android APK 与共享模拟器 framework 构建通过；尚未验证真实 accessibility 导出与宿主调用闭环 |
| Task 6 两端 host/权限/独立工程 | 待完成；iOS 文件选择与 Speech 权限、Android RECORD_AUDIO 与异步 TTS 初始化、生命周期清理、拒绝覆盖的独立工程及双端构建 |
| Task 7 真机与裁剪验收 | 待完成；通过真实 Demo 页面验证 TTS/ASR/取消/重入/后台行为，完成分发裁剪回归、独立整包审查与证据记录 |

以上测试结果取自对应任务的已保存输出；本次仅同步文档，未重跑测试。当前仍有 2/7 个语音任务未完成；即使这两项完成，P6 的 iOS 麦克风、Android 真实转写、离线/质量和最低系统缺口仍不能标为通过。

2026-10-19 P10 验收补充：iPhone16ProMax/iOS27 已完成注册持久化后 SIGKILL→新进程实际解密，以及持有 lease 的删除提交后 SIGKILL→新进程 tombstone/稳定回执/payload/key 清理；最终精确 cleanup=true，原语音探针已恢复。见 [进程恢复证据](validation/p10-ios-store-device-probe.md#两次-sigkill-后的持久恢复2026-10-19)。

2026-10-20 P10 验收补充：TECNO KM9/API35 已完成 Android create→SIGKILL、delete→SIGKILL、recover、cleanup 四阶段跨进程链；runner 在每个 stage 前对精确测试包 force-stop 并确认无旧 PID，controller 与校验器确认四个不同 PID/launch nonce、报告绑定、双 kill -9、tombstone/稳定回执、payload/key purge 与 run-scoped cleanup，runner 检测到的失败路径也会 best-effort 清理并留下 cleanup outcome。见 [Android 进程恢复证据](validation/p10-android-store-device-probe.md#两次-sigkill-后的跨进程持久恢复2026-10-20)。两端证据仍不证明 API26/iOS15、提交中断或文件系统故障；P10/roadmap 继续未完成。最终分支独立审查仍待取得结论。

2026-10-19 P6 历史背景（当前进度以上述 2026-10-20 入口为准）：语音 Client 的 availability/close 竞态与并发 close 等待已修复并提交 `c13ed4c`，Apple 全包374/374、Android Speech12/12、TTS3/3、ASR7/7及消费者构建通过，见[语音记录](validation/speech-backends.md)。用户已选择“先将现有后端接 Demo，再独立补齐 iOS 麦克风 ASR”；[书面规格](superpowers/specs/2026-10-19-speech-demo-bridge-design.md)已认可，[七任务计划](superpowers/plans/2026-10-19-speech-demo-bridge.md)现已执行至 Task 5 完成。麦克风、Android ASR 实际转写、离线/质量和最低系统验证继续保留。P10 最终独立审查的再次重试仍因模型容量不足退出，未产生审查结论；固定 revision 的 SFace LFS 元数据与运行时可行性研究已补入[模型预研](validation/p10-identity-model-selection.md)，未下载模型或选定运行时。

2026-10-19 当前入口：P10 Task 1–8、11、12 已实现并提交，Task12 为 `bb89516`；最终独立审查包覆盖 `ecdfd52..bb89516`，三个审查实例均因模型容量不足未启动，没有审查结论。Task9/10 生产模型/PAD、数据授权与校准继续保留。iOS Demo 真机 KMP framework 已刷新，新增身份 UI 后照片取消、方向探针、相机前后台回归3/3通过，详见[P4补充](validation/p4-camera-stability.md#身份-ui-集成后的-ios-回归2026-10-19)。下文10月17日段落为历史入口，P10当前状态以上表和[P10记录](validation/p10-face-identity.md)为准。

2026-10-17 P6 后续包：用户已批准的[Apple 文件 ASR 实施计划](superpowers/plans/2026-10-17-apple-system-asr-file.md)已实现公共契约、Apple backend 生命周期与可裁剪分发三个 Task。Swift 58/58、工具测试 44/44、Xcode 模拟器构建、全新六模块 XCFramework、binary consumer 和 Core+Speech 独立 typecheck 均通过。公共请求不绑定离线策略，Apple 系统后端单独强制 on-device；取消/超时后先 drain 再更改输入，lease 释放以平台 task completed 为边界。实际 SDK `5fd0b2d` 已在 iPhone 16 Pro Max / iOS 27.0.1 通过固定合成文件转写、busy 证明的活动任务取消、drain、输入保持、同进程重入和 close 后拒绝。最终独立架构审查 CLEAR，一个 LOW 文件 metadata 错误映射延后处理。报告/hash 见[语音后端记录](validation/speech-backends.md#2026-10-17-apple-文件-asr-sdk-后端)；既有授权被复用，麦克风、音频 session、资源安装、自然语音质量与网络行为仍未测。

恢复时以阶段表、2026-10-20 当前入口和最新 Git 为准。P1–P3 证据分别见[生命周期审查](validation/p1-lifecycle-review.md)、[模型存储审查](validation/p2-model-store-review.md)与[真机结果](validation/p3-ios-device.md)；P4 的完整 PHPicker EXIF 2/6/7、资源释放和最低系统缺口见[P4 记录](validation/p4-camera-stability.md)。P6 已有原生文件探针与 SDK 真机证据，尚不能代替本次 Demo 验收。P10 Task 1–8、11、12 和两端 SIGKILL 后存储恢复已有证据，生产模型/PAD、授权数据、校准、提交中断/文件系统故障、最低系统及最终独立审查仍未通过；本地原始草稿保持未追踪。P7 取消/重入探针结果见[P7 记录](validation/video-sr-backends.md)，完整 SDK/Demo 与 Android 接入仍待完成。按已批准计划推进，缺设备项独立保留。

详细设计：[SDK 设计](superpowers/specs/2026-10-15-ondevice-ai-sdk-design.md)。历史计划：[foundation](superpowers/plans/2026-10-15-ondevice-ai-kit-foundation.md)。证据与缺口：[验收](validation/acceptance.md)、[设备矩阵](validation/device-matrix.md)。
