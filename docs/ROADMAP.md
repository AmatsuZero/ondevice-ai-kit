# 分阶段交付与恢复入口

2026-10-23 P9固定合成短视频native链路通过：`c9ea298/eebc670/30e5e8e` typed API/shared runtime/opt-in consumer后，首次设备因ModelStore大文件校验临时缓冲保留被per-process-limit终止；独立实际RED→GREEN修复`5e39cb8`保持全hash校验。真实iPhone17,2/iOS27.0.1/PID22072固定29FPS8帧权重success、首update取消/drain、同PID重入、close/lease清理及严格validator通过；clip2s与span25600/14848分列。完整Apple470/470、增强host41/41无skip、tools133/133。仅合成集成生命周期，不证明自然视频质量/Metal/energy/minOS；短实际样本仍拒绝、上游8→7失败/binary archive exit65/旧验收与独立reviewpending保留，仍5/12/P9active。下一独立Androidvideo契约对等，再另包KMP/Demo；详见[短视频验证](validation/apple-mlxvlm-short-video.md)顶部。以下为历史检查点。

2026-10-23 P9 runtime库续接：MLXVLMBackend已显式支持video入口，沿同session/lease直接使用adapter LMInput并join后清理。新增session6/metadata1项RED→GREEN，增强host41/41无skip、tools126/126、fresh generic iOS build通过；仅库host/build，视频权重设备运行尚未执行，原Task3仍未完成。下一opt-in consumer/严格validator及固定合成device生命周期；仍5/12/P9active、独立reviewpending及旧缺口保留，见[短视频验证](validation/apple-mlxvlm-short-video.md)顶部。

2026-10-23 P9 Task2续接：纯Swift typed video request/可选backend协议/client共享生命周期已实现，API RED后Multimodal22/22、完整Apple469/469、增强host34/34无skip、tools126/126、Swift6严格typecheck与新独立generic iOS公共API compile probe通过。MLXVLMBackend仍image-only，Task3 runtime未开始；下一先细化adapter直入LMInput的runtime计划。Task1短样本仍拒绝，上游8→7失败/二进制/自然质量/资源/Android真实后端/最低系统/独立review缺口保留，仍5/12/P9active。见[短视频验证](validation/apple-mlxvlm-short-video.md)顶部；以下是历史检查点。

更新时间：2026-10-23（P9真实 OS background lifecycle 14 gates通过；Demo/bridge42/42、tools123/123；物理触摸/systempicker与独立review仍待补）。GitHub为主仓，feat/sdk-foundation未合入main；本次按用户显式授权同步GitHub/GitLab功能分支，未合入main；后续恢复默认本地提交。

## 执行规则

2026-10-23 P9 profile/设备续接：`81ea4df`真实PQ/offset/2frame/reference负向4/4，HDRguard mutation RED后原源码恢复，完整host34/34、tools126/126。空App unit-test host沿用既有签名，物理iPhone16ProMax/iOS27.0.1执行snapshot/profile12/12和adapter6/6；ByteTokenizer输入准备，不是视频权重/质量/资源验收。原Package tool-hosted exit70及中间host链接/约束失败均保留；固定lock/checkout不改。短于8实际样本仍拒绝、不泛化profile。下一可独立推进纯Swift typed video contract/client，runtime视频推理仍未开始；见[短视频验证](validation/apple-mlxvlm-short-video.md)顶部。独立reviewpending、仍5/12/P9active。

2026-10-23 P9短视频内部适配续接：`5be1b57`逐帧保留29FPS全部8帧真实pixels/顺序/CMTime，不调用上游二次采样；`c9ef8ce`真实固定tokenizer2/2，无权重加载。clip2s与sample span约1.724138s分别记录；24FPS完整tokens/pixels/mask对照上游一致。最新host30/30、tools126/126、fresh generic iOS编译通过。上游`.frames`仍8→7，原失败保留；公共API/runtime/设备视频推理未开始，snapshot额外profile/iOSdecode与旧验收门槛仍待补。下一入口补snapshot profile及iOSdecode/cancel，见[短视频验证](validation/apple-mlxvlm-short-video.md)顶部和独立适配设计/计划。只本地提交、独立reviewpending，仍5/12、P9active。

2026-10-23 P9短视频Task1：内部有界snapshot及实际pinned processor characterization已实施，host22/22、tools126/126、独立consumer generic iOS编译通过。24FPS的8帧保留并完成真实prepare；29FPS的8帧在固定上游二次采样后变7帧，原始8帧门槛实际失败。因此Task2/3暂停，不接公共video API、不改上游/伪造时间戳。下一入口先设计保留真实帧/时间语义的项目侧适配，见[短视频验证](validation/apple-mlxvlm-short-video.md)；设备推理/质量/资源与旧缺口不变，仍5/12、P9active。

2026-10-23 P9短视频续接：固定模型14文件 hash/size 与12个缓存 checkout HEAD/clean 重新离线复核通过。上游有单视频 processor；`fps=1/max_frames=64` 是配置值，短片默认采样率会动态变化。项目仍无视频 API/推理证据，已完成[可行性与设计](validation/apple-mlxvlm-short-video.md)。下一 Task 先执行有界 snapshot 与固定 processor 帧/时间语义 characterization；通过后才接 typed API、Apple backend 与独立consumer。仍5/12、P9 active，binary与旧验收缺口保留。

2026-10-23 P9分发续接：`b8acaa3` 新增可搬移source consumer（122个tracked package文件、相对引用、固定lock/资源hash）；搬移后generic iOS离线编译通过，tools126/126。增强Release/library-evolution archive 在本机Swift6.4验证固定swift-collections1.7.3的OrderedCollections.swiftinterface失败（exit65），binary未通过，不复用基础十XCFramework证据。见[分发验证](validation/apple-mlxvlm-distribution.md)。独立review及既有缺口保留，仍5/12、P9active；本包仅本地提交。

用户最新要求是**继续推进，直到 roadmap 完成**。按下表工作包限定输入、改动范围与验证，逐项实施、复审和提交后自动进入下一项。设备、凭据、商用模型许可与校准缺口分别保留，不能把局部测试通过视为完整交付。原十项 foundation 计划保留为历史细节。

2026-10-21 接手更新：P6 Demo 语言路由补齐请求级 admission，避免默认英语不可用阻断已就绪的中文。真实 client 回归先失败再通过，Host/bridge/session39/39、native iOS fixture43/43及普通真机包构建/装机通过；完整恢复门禁Swift414/414、工具77/77和Android569tasks通过。尚无中文实录终态与真实取消/重入完成证据，独立复审仍因模型容量不足不可用，P6/roadmap保持未完成，未同步远端。恢复入口见[交接检查点](HANDOFF-2026-10-20-speech-demo-bridge.md)。

2026-10-21 P7 续接：已提交 Swift 视频契约/client（`17a330e`、`fd2e537`）和 Android 对等模块（`112634d`），本地分发已验证，独立审查因服务容量失败保留pending。Apple VideoToolbox 后端现已完成本地实施、九XCFramework及固定fixture设备验证，有界AAC音轨preserve已完成并通过设备验证，KMP Video bridge/Demo Task1–5 已提交、本地门禁通过，Task6 iOS UI 因自动化初始化超时待补；Android unavailable/picker取消/空闲前后台 smoke通过。当前验收入口为[视频 Demo 验证](validation/video-bridge-demo.md)；Video SDK Foundation/AppleVideo/bridge 独立审查待恢复；P6 受控录音等待真实终态期间继续可独立推进的 P7。基础契约本身不代表实际超分，新增固定profile后端也不代表 P7 完成，见[音轨验证](validation/apple-video-audio.md)、[AppleVideo验证](validation/apple-video-backend.md)、[基础验证](validation/video-sdk-foundation.md)与[实施计划](superpowers/plans/2026-10-21-video-sdk-foundation.md)。

2026-10-23 P9当前入口：增强Demo真实 OS background lifecycle 14 gates通过，source d50eff1、PID21398；项目-owned companion 触发真实四通知，RUNNING update→CANCELLED无output，恢复后不自动重启，同PID重入成功。见[OS后台验证](validation/multimodal-demo-os-background.md)。物理touch/真实systempicker/独立review仍未验，不关闭P9或提升5/12。

2026-10-22 P9程序化host历史入口：独立consumer中程序化KMP host/VM→真实MLXVLM设备生命周期已通过，source2868bee，iPhone16ProMax/iOS27.0.1同PID20852，10 gates/5真实请求；成功/首update取消/drain/重入/host后台不重启/close/input与modellease/关闭拒绝。工具112/112，signeddevicebuild与严格rawvalidator通过。见[真机host/VM验证](validation/multimodal-demo-device.md)。下一独立包为共享Compose页面与增强Demo集成验收；实际UI/OS后台/systempicker仍未验，不提升5/12或关闭P9。

2026-10-22 P9可选host历史入口：Apple固定模型/ModelStore可选Demo host包本地完成（design30e691b、factory5afb045及后续Task2）；Demo33/33、工具104/104、默认simulator与增强iOS18 generic device unsigned构建通过，default/Android仍unavailable。见[可选MLX host验证](validation/multimodal-demo-mlx-host.md)。下一独立包为KMP页面→真实MLXVLM设备成功/update取消/drain/重入/后台/close/lease闭环，iOS实际触摸与system picker仍未验；不关闭P9或提升5/12计数。

2026-10-22 P9 host历史入口：双端native MultimodalHost生命周期包本地完成；设计cc99556、Android854d256、Apple与本检查点同次提交。新host各7/7、Android全Demo21/21、Apple Demo21/21/Binding10/10、工具101/101及APK/lint通过。见[host验证](validation/multimodal-demo-host.md)。下一独立包为system picker/shared action控件/平台opt-in及session接线；Apple固定模型与真实KMP设备闭环继续另包，Android默认unavailable。iOS实际UI/minimumOS/独立review仍待补，P9不关闭。

2026-10-22 P9输入历史入口：双端opaque input/lease输入所有权本地包已完成，设计0b0f851、Apple caab202、Android与验证本检查点同次提交。私有副本、opaque UUID、remove/close封闭admission、独立幂等lease及foreign-file保护通过实际文件测试；新测试暴露并修复旧SpeechInputStore递归删除目录缺陷，Speech39/Video9回归通过。见[输入验证](validation/multimodal-demo-input.md)。下一独立包为两端host/picker与页面生命周期；Apple固定模型/ModelStore与KMP真实设备闭环随后另包，Android保持明确unavailable。不关闭P9，不提升5/12计数。

2026-10-22 P9 UI历史入口：shared单图Screen/UI与opt-in入口本地实现98a3891；默认App不自动出现新页，提供model才可见。Android实际shared页面全部具名场景通过（含unavailable、原始字段、取消/update/身份及旧Speech/Video切页），shared60/60、Swift旧/新入口typecheck、含原host的独立iOS Demo build、更新Demo ABI10/10、工具101/101及Android lint通过。见[UI验证](validation/multimodal-demo-ui.md)。下一独立包为双端host/picker与opaque input/lease生命周期，Apple固定模型/ModelStore与KMP设备闭环随后分包。iOS UI、真实推理和独立review仍未验；不关闭P9或提升5/12阶段计数。

2026-10-22 P9状态机历史续接：bridge三Task（2e96a26/b1e6873/1291bd2）之后，单图Demo共享状态机已提交5db0083，默认UnavailableBridge，UI/导航/host未接入。新VM12/12、全shared60/60、更新后Demo ABI bridge10/10、Android Demo APK/lint（0 error/9条既有warning）、工具101/101通过；[状态机验证](validation/multimodal-demo-state.md)、[设计](superpowers/specs/2026-10-22-multimodal-demo-state-design.md)、[计划](superpowers/plans/2026-10-22-multimodal-demo-state.md)。下一独立包为shared单图Screen/UI与opt-in入口，host/固定模型/picker/设备随后另包；短视频、Android真实推理仍分包。独立审查pending，整个P9未关闭。

2026-10-22 bridge历史证据：common21/21、Android bridge10/native12、Video17/Speech25回归、Apple Binding/Demo各10/10、工具101/101；真实native client+注入fixture，不是KMP→MLXVLM真机推理。专属入口前缀避免旧ABI selector重编号，旧Video6/6通过。见[bridge验证](validation/multimodal-bridge.md)。

2026-10-22 P9 benchmark历史证据：源提交f9512a0，iPhone16ProMax/iOS27 Debug、1预热+5测量：加载p50/p95=311.09/323.69ms、首交付update=763.80/774.15ms、总耗时=924.39/934.32ms；process RSS采样peak=2,437,054,464B（非Metal/GPU）。合成red+square rubric5/5，额外“flag”语义保留，不能算自然质量通过。原始证据见[后端验证](validation/apple-mlxvlm-backend.md)。本bridge包不重跑或提升这些旧证据等级。

2026-10-22 P9 Foundation历史续接：Multimodal SDK Foundation本地四Task完成：cf3a0e6 Swift、8476dd8 Android、e1ff006分发。双端纯契约/client、十XCFramework、Core+Multimodal-only iOS15 device/simulator编译链接及Android Maven真实消费通过；[验证](validation/multimodal-sdk-foundation.md)、[设计](superpowers/specs/2026-10-22-multimodal-sdk-foundation-design.md)、[计划](superpowers/plans/2026-10-22-multimodal-sdk-foundation.md)。下一独立入口为可选Apple MLXVLM后端，先复核固定revision、已有资产与加载/lease/取消边界，不下载、不将probe直接当SDK。P9/roadmap未关闭，Video UI/独立审查及P6实录缺口保留。

- 继续时先读本文件、验收记录和 git 状态，选择当前未完成工作包；不重复已验证工作。
- 工作包发现跨范围缺陷时，记录为后续项；本包无法成立的阻塞才在包内修复。
- 已授权直接使用本地真机做 device-only 验证；仅操作本项目 App，保留设备其他 App/资料。使用已有签名配置，不自行创建或变更证书、描述文件等账户资产。
- 测试/模拟器/真机/最低系统验证分别记录；构建通过不代表真机已通过。
- 不逐项重新请求继续许可。每个包记录可复核提交、验证和剩余项，再推进下一个可执行包。

## 当前范围与后续功能

当前 KMP Demo 已有**人脸检测与关键点**入口，以及独立的身份/活体控件与宿主配置入口；原生 SDK 已接入系统 TTS、文件 ASR 与 iOS 麦克风 ASR；新麦克风路径已实现并通过本地 fixture，实际录音未验。语音工作包已完成公共契约、能力级后端查询、双端 adapter、iOS 文件 lease、共享状态机/语音页面，以及两端宿主接线、权限与独立 iOS 工程；Task 7 的现代真机闭环已通过，追加 UI/Done 独立审查已 APPROVE；不能视为 P6 已完整收口。VLM 与视频超分的独立探针状态见下表。当前尚不提供生产身份识别、1:1 比对、1:N 搜索或活体认证。已实现的 Core、任务生命周期、最新帧队列、ModelStore 和分发工程是后续能力共用的基础设施；P10 已实现独立身份存储、授权生命周期、类型化 bridge 与可裁剪分发；生产算法门槛仍待通过。

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
| P6 语音调研与小探针 | 独立对比系统 ASR/TTS、离线资源、iOS26 可选能力；已有后端按独立工作包接入 SDK/Demo | 原生探针与 Demo 验收分别记录；未测项继续保留 | 部分完成：两端系统 TTS SDK 已完成现代真机成功/取消/关闭验证；Android on-device ASR 模块与严格选择已实现，TECNO KM9 的 en-US 返回 MISSING_ASSETS，真实转写和设备生命周期仍未通过。Apple 文件 ASR SDK `5fd0b2d` 已通过强制 on-device 固定文件转写、活动取消、drain、输入保持、重入及关闭后拒绝，独立架构审查 CLEAR（一个 LOW 延后）；Apple/Android 原生语音分发及消费者依赖裁剪已有通过证据。2026-10-20 语音 bridge/Demo 工作包 Task 1–6 已完成，Task 6 提交 `b272b76`：公共契约、能力级查询、Android/iOS adapter、文件 lease、共享 VM/UI、双端 host/权限与独立 iOS 工程均已实现；Task 7 的 Demo 真机/裁剪门禁已通过；追加 UI/Done 独立审查已于2026-10-21 APPROVE。iOS 两种 ABI 各 14/14、最新 iOS Host20/20、Android Host5/5、工具76/76，Android APK 与独立生成工程构建通过；Task7本地验收/独立审查现已收口；P6其他缺口保留。iOS 麦克风契约/client/capture/bridge/host/session 已于 `5be8a76..d60867c` 本地提交；Swift412项、生成桥接/host/session35项、工具77项、Apple/Android门禁、fresh七XCFramework和Speech裁剪通过，Task1–5独立APPROVE、native simulator39/39。受控真机录音仍待验；iOS26资源、Android ASR真实转写、两端断网/自然语音质量及最低系统继续保留，见[麦克风记录](validation/ios-microphone-asr.md) |
| P7 视频超分调研与小探针 | iOS26 系统能力、CoreML、Android ONNX/LiteRT；移动端约束 | 先完成 video-sr-backends.md，再做一条固定视频链路；独立停止 | 部分完成：iOS 27 真机确认 VideoToolbox 系统 SR 支持 4×并完成 48 帧固定视频链路；已记录内存采样、统一色彩/HEVC 编码的 SR/双三次对照和自然视频真机质量配对（两路均 48/48 帧、BT.709 元数据一致，质量仍为 unverified）；双路流程在 SR 阶段和基线阶段均验证取消无残留输出，重启后两路再处理成功，随后两次取消后同进程重入也已通过（PID 不变、两路各 48 帧）。双端 Video SDK Foundation 已实现纯契约、严格后端选择、取消/drain、输出 lease 与 close/query join，Swift12项、Android7项及本地八XCFramework/Core+Video消费者验证通过，独立审查因服务容量失败待恢复；可选 Apple VideoToolbox SDK 后端已接入（`e996d5d`，取消竞态修复 `51d98bf`），九XCFramework、Core+Video独立消费者和设备48帧成功/取消无残留/同进程重入通过，仍只支持固定SDR profile，现支持默认preserve（无音轨或单条零起点等长AAC）与discard，设备音频payload相同、无音轨默认请求、取消/重入通过；KMP bridge、共享 VM/UI 和双端 host 已实现（`9bad71d..3b0560c`），本地 Apple/Android 门禁通过；Android unavailable/选择器取消/空闲前后台 smoke通过；iOS Video Demo UI runner两次初始化超时，尚无新Demo成功/取消证据。新 Demo UI、独立审查、Android模型、SR模型传递函数、主观质量与最低系统仍待补，见[视频 Demo 验证](validation/video-bridge-demo.md)、[音轨验证](validation/apple-video-audio.md)、[AppleVideo验证](validation/apple-video-backend.md)、[基础验证](validation/video-sdk-foundation.md)和 [P7 记录](validation/video-sr-backends.md) |
| P8 VLM 调研 | SmolVLM2/huggingsnap、Florence-2、MiniCPM-V及其他指定 checkpoint | mobile-vlm.md 记录许可、平台、体积、内存、证据等级；不同时接入多个模型 | 已完成候选调研与 P9 单路线选择，见 [P8 记录](validation/mobile-vlm.md)；移动端实测归 P9 |
| P9 VLM 单模型试验 | 根据 P8 选择一个 checkpoint、一个平台、一个 runtime | 固定图像→输出→资源/耗时原始证据；随后另包推进第二平台 | 单平台探针、双端Multimodal契约/client及十XCFramework/Maven裁剪分发已有证据。独立Apple MLXVLM SDK后端、ModelStore lease及固定单图设备生成/取消/重入/close已通过；固定SDK/模型/runtime/input的1预热+5测量、load/首update/total p50/p95和进程RSS采样已记录。单图KMP common/双端adapter通过注入native client测试、两种Apple ABI各10/10；shared Demo状态机新12/12、全shared60/60已通过，shared单图UI/opt-in及Android fixture页面交互已验证，双端opaque input/lease基础已完成并验证（Apple Demo14/14、Android新4/4），native host所有者与注入选择生命周期通过双端各7/7；shared选择/清除/离页动作及Android opt-in系统picker/IO staging与独立生成iOS picker/host接线已完成：Android API35系统取消及合成content回调/清理通过，全Demo26/26，Apple Demo24/24、工具102/102、完整生成App编译通过；iOS物理触摸/真实系统选文件仍未验；程序化host/VM10gates与增强Demo bootstrap/共享AX真实生成9gates现均通过（source2868bee/51d3b72），共享活动取消/实际OS后台仍待补。仅合成rubric5/5，不证明自然质量；Apple固定模型host可选接线已完成（Demo33/33、tools104/104、增强离线编译），短视频、共享页面活动取消/真实系统交互、Metal/GPU资源、Android真推理、最低系统与独立审查待补，见[增强Demo集成验证](validation/multimodal-demo-integration.md)、[真机host/VM验证](validation/multimodal-demo-device.md)、[可选MLX host验证](validation/multimodal-demo-mlx-host.md)、[picker验证](validation/multimodal-demo-picker.md)、[状态机验证](validation/multimodal-demo-state.md)、[bridge验证](validation/multimodal-bridge.md)、[后端验证](validation/apple-mlxvlm-backend.md) |
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

2026-10-22 **单图UI/input/host/picker及Apple固定模型可选接线本地包已完成**：[可选MLX host验证](validation/multimodal-demo-mlx-host.md)。程序化KMP host/VM→MLXVLM设备生命周期现已通过，见[真机host/VM验证](validation/multimodal-demo-device.md)。增强Demo bootstrap与共享AX真实生成已通过，见[集成验证](validation/multimodal-demo-integration.md)。下一本地入口为共享AX首update取消/活动离页/重入，先独立设计/逐Task计划；保留default/Android unavailable。短视频/Android真backend/自然质量/Metal GPU/energy另包。旧Video自动化初始化超时、P6实录/ASR资源、最低系统与独立review缺口保留，不下载、不自动录音。

[iOS 麦克风设计](superpowers/specs/2026-10-20-ios-microphone-asr-design.md)的文件兼容、optional finish、session lease、按输入权限和drain边界均已落地。Task1–3核心、Task4桥接、Task5及回滚修复均独立APPROVE，0问题；最新native simulator39/39。旧语音包 Task7 UI/Done 独立审查已恢复APPROVE；旧包本地门禁收口。当前新麦克风Task6未验，远端最终同步门禁维持。

| 语音工作包任务 | 当前状态与证据 |
| --- | --- |
| Task 1 公共 Speech Bridge 契约 | 已完成，`6788b1b`；类型化选择、输入、结果、失败与回调，Swift ABI typecheck 通过 |
| Task 2 能力级后端查询 | 已完成，`331de92`；独立 TTS/ASR 查询、严格选择与 close 竞态测试通过 |
| Task 3 Android adapter | 已完成，`8719a17`；单音频槽、取消/drain、并发 close、输入与隐私测试，Speech 单元测试/lint/binding 编译通过 |
| Task 4 iOS adapter 与输入 lease | 已完成，`7605511`；Binding/Demo 两种 ABI XCTest 各 14/14，生成器 5/5；受保护文件 hash 未变化 |
| Task 5 共享状态机与语音页面 | 已完成，`233873e`；Speech VM 10/10、Face 5/5、Identity 20/20，binding tests、Android APK 与共享模拟器 framework 构建通过；Android 真实 tags/滚动已在 Task 7 验证 |
| Task 6 两端 host/权限/独立工程 | 已完成，`b272b76`；iOS picker/Speech 授权、Android 麦克风权限/TTS 初始化、双端生命周期与独立工程构建；Task 7 补引用环、权限重试与 factory 分类回归 |
| Task 7 真机与裁剪验收 | 已完成本包本地验收；Android TTS 完成/取消/重入/后台门禁通过，ASR 仍 MISSING_ASSETS；Apple/Android 本地验证、76 项工具测试、七个 XCFramework、三个 Apple consumer 与 Maven 消费者通过；iOS TTS与文件ASR完整生命周期通过，全套UI5/5、零跳过（141.919秒）；普通无分享配置已恢复；历史焦点失败为陈旧AX坐标/滚动惯性。新UI/Done追加独立审查已APPROVE/0issues |

旧包七个任务的实现、本地验收和独立审查均已收口；三项Host问题修复后定向CLEAR，最终UI/Done追加审查也已独立APPROVE。Android MISSING_ASSETS明确不计转写通过。P6的麦克风真机录音、Android真实转写、离线/质量和最低系统缺口仍不能标通过，路线图继续active。

2026-10-19 P10 验收补充：iPhone16ProMax/iOS27 已完成注册持久化后 SIGKILL→新进程实际解密，以及持有 lease 的删除提交后 SIGKILL→新进程 tombstone/稳定回执/payload/key 清理；最终精确 cleanup=true，原语音探针已恢复。见 [进程恢复证据](validation/p10-ios-store-device-probe.md#两次-sigkill-后的持久恢复2026-10-19)。

2026-10-20 P10 验收补充：TECNO KM9/API35 已完成 Android create→SIGKILL、delete→SIGKILL、recover、cleanup 四阶段跨进程链；runner 在每个 stage 前对精确测试包 force-stop 并确认无旧 PID，controller 与校验器确认四个不同 PID/launch nonce、报告绑定、双 kill -9、tombstone/稳定回执、payload/key purge 与 run-scoped cleanup，runner 检测到的失败路径也会 best-effort 清理并留下 cleanup outcome。见 [Android 进程恢复证据](validation/p10-android-store-device-probe.md#两次-sigkill-后的跨进程持久恢复2026-10-20)。两端证据仍不证明 API26/iOS15、提交中断或文件系统故障；P10/roadmap 继续未完成。最终分支独立审查仍待取得结论。

2026-10-19 P6 历史背景（当前进度以上述 2026-10-21 入口为准）：语音 Client 的 availability/close 竞态与并发 close 等待已修复并提交 `c13ed4c`，Apple 全包374/374、Android Speech12/12、TTS3/3、ASR7/7及消费者构建通过，见[语音记录](validation/speech-backends.md)。用户已选择“先将现有后端接 Demo，再独立补齐 iOS 麦克风 ASR”；[书面规格](superpowers/specs/2026-10-19-speech-demo-bridge-design.md)已认可，[七任务计划](superpowers/plans/2026-10-19-speech-demo-bridge.md)现已执行至 Task 5 完成。麦克风、Android ASR 实际转写、离线/质量和最低系统验证继续保留。P10 最终独立审查的再次重试仍因模型容量不足退出，未产生审查结论；固定 revision 的 SFace LFS 元数据与运行时可行性研究已补入[模型预研](validation/p10-identity-model-selection.md)，未下载模型或选定运行时。

2026-10-19 当前入口：P10 Task 1–8、11、12 已实现并提交，Task12 为 `bb89516`；最终独立审查包覆盖 `ecdfd52..bb89516`，三个审查实例均因模型容量不足未启动，没有审查结论。Task9/10 生产模型/PAD、数据授权与校准继续保留。iOS Demo 真机 KMP framework 已刷新，新增身份 UI 后照片取消、方向探针、相机前后台回归3/3通过，详见[P4补充](validation/p4-camera-stability.md#身份-ui-集成后的-ios-回归2026-10-19)。下文10月17日段落为历史入口，P10当前状态以上表和[P10记录](validation/p10-face-identity.md)为准。

2026-10-17 P6 后续包：用户已批准的[Apple 文件 ASR 实施计划](superpowers/plans/2026-10-17-apple-system-asr-file.md)已实现公共契约、Apple backend 生命周期与可裁剪分发三个 Task。Swift 58/58、工具测试 44/44、Xcode 模拟器构建、全新六模块 XCFramework、binary consumer 和 Core+Speech 独立 typecheck 均通过。公共请求不绑定离线策略，Apple 系统后端单独强制 on-device；取消/超时后先 drain 再更改输入，lease 释放以平台 task completed 为边界。实际 SDK `5fd0b2d` 已在 iPhone 16 Pro Max / iOS 27.0.1 通过固定合成文件转写、busy 证明的活动任务取消、drain、输入保持、同进程重入和 close 后拒绝。最终独立架构审查 CLEAR，一个 LOW 文件 metadata 错误映射延后处理。报告/hash 见[语音后端记录](validation/speech-backends.md#2026-10-17-apple-文件-asr-sdk-后端)；既有授权被复用，麦克风、音频 session、资源安装、自然语音质量与网络行为仍未测。

恢复时以阶段表、2026-10-21 当前入口和最新 Git 为准。P1–P3 证据分别见[生命周期审查](validation/p1-lifecycle-review.md)、[模型存储审查](validation/p2-model-store-review.md)与[真机结果](validation/p3-ios-device.md)；P4 的完整 PHPicker EXIF 2/6/7、资源释放和最低系统缺口见[P4 记录](validation/p4-camera-stability.md)。P6 已有原生文件探针与 SDK 真机证据，尚不能代替本次 Demo 验收。P10 Task 1–8、11、12 和两端 SIGKILL 后存储恢复已有证据，生产模型/PAD、授权数据、校准、提交中断/文件系统故障、最低系统及最终独立审查仍未通过；本地原始草稿保持未追踪。P7 取消/重入探针结果见[P7 记录](validation/video-sr-backends.md)，Video SDK/KMP/Demo 实现已有本地证据，新的 iOS Demo UI/独立审查、Android 真实后端和质量验收仍待完成。按已批准计划推进，缺设备项独立保留。

详细设计：[SDK 设计](superpowers/specs/2026-10-15-ondevice-ai-sdk-design.md)。历史计划：[foundation](superpowers/plans/2026-10-15-ondevice-ai-kit-foundation.md)。证据与缺口：[验收](validation/acceptance.md)、[设备矩阵](validation/device-matrix.md)。
