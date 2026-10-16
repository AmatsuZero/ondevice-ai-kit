# OnDevice AI Kit SDK 设计

日期：2026-10-15

状态：书面规格已由用户确认；本文不代表后端已实现或实机验收通过。

后续确认的[能力与可选后端设计](2026-10-16-capability-backends-design.md)补充并更新本文的后端接入、默认选择和分发裁剪规则；下一阶段实施时以该补充规格为准。

## 1. 目标与边界

OnDevice AI Kit 是供多个公司 App 复用的端侧智能 SDK。首版以主流 iOS/Android 手机为验收基线，先交付人脸检测与分析，再交付 ASR/TTS、视频超分和图像/视频理解。SDK 需要支持能力按模块接入、模型按需安装、运行时加载与释放、统一进度/取消/错误事件，以及 KMP Demo 验证。

首版人脸范围是检测与分析：人脸框、关键点、姿态和可用的质量指标。身份验证、1:N 搜索和活体认证列入后续路线，作为独立能力和独立隐私/误识率验收，不改变首版检测接口。所有识别结果保留坐标系、置信度和指标可用性，不用零值表示“不可用”。

首版不训练模型，不把任意 Hugging Face 模型自动转换成 Core ML/LiteRT，不将所有能力强制统一到一个推理框架，也不把模型权重提交到 Git。

## 2. 兼容策略

SDK Core、KMP 公共 API 和人脸模块的最低 iOS 版本为 iOS 15。高系统版本能力以独立后端发布，并通过能力查询报告不可用原因，不能抬高整个 SDK 的最低部署版本。

| 能力或后端 | 首版目标 | 最低系统/设备策略 |
| --- | --- | --- |
| Core、模型清单、任务事件、错误协议 | 必选 | iOS 15；Android API 26 作为 Demo 基线 |
| iOS Vision 人脸检测/关键点 | 必选 | iOS 15 |
| iOS Speech / `SFSpeechRecognizer` | 必选后端 | iOS 15；运行时检查 `supportsOnDeviceRecognition`，语言包和设备能力不足时返回状态 |
| iOS `AVSpeechSynthesizer` | 必选后端 | iOS 15；语音由系统提供 |
| Android ML Kit Face Detection | 必选后端 | API 26 起；模型安装状态显式暴露 |
| Android `SpeechRecognizer` on-device | 可选系统后端 | API 31+ 且 `isOnDeviceRecognitionAvailable` 为真 |
| Android `TextToSpeech` | 必选后端 | API 26 起；具体语音由系统引擎提供 |
| Core ML 通用模型 | 可选后端 | iOS 15 起，按模型输入/算子单独验证 |
| VideoToolbox `VTSuperResolutionScalerConfiguration` | 可选高版本后端 | iOS/iPadOS 26+；不能作为 iOS 15 默认实现 |
| Real-ESRGAN Core ML | 可选后端 | 独立验证 iOS 15；移动端不得依赖 macOS 的 `Process`、`ditto` 或 `/usr/bin/unzip` |
| MLX/MLX Swift LM | 可选高版本后端 | 当前 `mlx-swift` 和 `mlx-swift-lm` 声明 iOS 17；按模块隔离 |
| SmolVLM2 Swift/MLX | 可选模型 | 500M 为首选，256M 为低端档，2.2B 为高端档；HuggingSnap Demo 当前要求 iOS 18 |
| MiniCPM-V / llama.cpp | 后续候选 | 从官方 1B GGUF/llama.cpp-omni 路径验证；35B-A3B 需要独立的存储、专家分片和内存验收 |

Compose Multiplatform 的当前文档支持 iOS 14，因此 KMP Demo 的 UI 层不会阻止 iOS 15；实际可用版本仍由选定的 Kotlin、Compose、Xcode 组合锁定。

Android 高版本后端不作为基础 API 的编译前提。Apple 端区分两种门槛：

- 系统 API 门槛：`SpeechTranscriber` 和系统超分使用 availability 标注与运行时检查，公共 API 不暴露这些高版本类型；在低版本系统上验证 App 能启动并返回不可用状态。
- 第三方依赖门槛：MLX 的 SwiftPM 平台声明不能靠 `#available` 消除。iOS 15 基础 package 不依赖 MLX；增强后端使用独立 package manifest/构建入口，按其实际最低版本接入。仅在同一个高最低版本 package 下拆分多个 product，不视为已解决隔离。

基础 Demo target 以 iOS 15 部署并验证全部基础流程；后续 MLX 增强 Demo target 暂以 iOS 18 部署，复用同一 KMP UI 与业务模块。基础 Demo 在高版本设备上可启用已通过链接和实机验证的系统增强 API。若业务要求一个 iOS 15 宿主二进制在新系统上启用 MLX，必须另做编译、链接与低系统启动探针；本文不把这个组合视为已支持，也不通过下载执行代码解决版本限制。

## 3. 方案选择

采用“原生 SDK + KMP 适配层”。

1. 原生层利用平台生命周期、音视频缓冲区、权限和系统模型能力。
2. 公共层只定义能力协议、模型清单、任务事件和错误码，不暴露 Core ML、MLX、LiteRT 或 llama.cpp 类型。
3. KMP 共享 DTO、会话适配、业务流程、模型管理状态和 Demo UI。Android 直接调用 Kotlin SDK；iOS 由 Swift 实现 KMP 导出的桥接协议并在入口注入。Kotlin 不直接 import 纯 Swift 的 MLX/Core ML 实现。
4. Android 和 iOS 可以为同一能力选择不同模型和后端，但输出契约、错误语义和验收样本保持一致。

相比单一 C/C++ 推理核心，这个方案更适合系统人脸、系统语音和系统 TTS；相比 KMP 作为核心，它不会让 Kotlin 直接承担 MLX、Core ML 和音频会话的桥接细节。

## 4. 模块结构

```text
ondevice-ai-kit/
├── sdk-core/                 # 能力描述、事件、错误、任务取消、资源预算
├── model-store/              # manifest、下载、SHA-256、安装、回滚、缓存
├── capability-face/          # FaceDetector 与统一 FaceObservation
├── capability-speech/        # ASR/TTS 公共模型与会话接口
├── capability-super-resolution/
├── capability-multimodal/
├── backend-apple-system/     # Vision、Speech、AVSpeechSynthesizer
├── backend-android-system/   # ML Kit、SpeechRecognizer、TextToSpeech
├── backend-coreml/           # iOS 15+ Core ML 模型适配
├── backend-mlx/              # iOS 17+ MLX/MLXLMCommon/MLXVLM
├── backend-litert/           # Android LiteRT/LiteRT-LM，按支持模型接入
├── backend-onnx/             # ONNX Runtime，传统模型或明确支持的模型
├── backend-llama/            # GGUF、多模态 llama.cpp 后端
├── kmp-binding/              # commonMain API 与平台注入
└── demo/                     # KMP Demo 与设备/模型性能记录
```

这张目录表表示逻辑边界，不要求首阶段创建所有目录或发布空模块。公共协议在 Swift/Kotlin SDK 内各自表达，KMP 映射为同语义 DTO，以共享夹具验证一致性；原生业务 App 不需要引入 KMP 或 Compose。首阶段不建立通用插件发现、反射或远程代码注册系统。

基础模块不依赖高版本后端。能力查询返回支持状态、原因、模型资产状态、离线属性和具体限制，不进行下载、权限弹窗或模型加载。系统模型由系统资产管理 API 管理，不交由 SDK ModelStore 冒充可控版本的自有模型。SDK 自有下载模型和 MLX/GGUF 模型才进入 ModelStore 生命周期。

## 5. 公共 API 与数据流

正式 API 按能力定义，而不是让业务方调用一个暴露框架类型的泛型函数。以下为 KMP 概念签名，用于说明职责；泛型会话不是公开 ABI，Swift 使用 async/AsyncSequence、Android 使用 suspend/Flow，各能力会话提供类型化 API，最终绑定方式在首阶段编译探针中固定：

```kotlin
interface CapabilityDescriptor {
    val id: String
    val platform: Platform
    val backend: BackendId
    val minOsVersion: OsVersion?
}

interface ModelStore {
    suspend fun status(model: ModelRef): ModelStatus
    suspend fun install(model: ModelRef, observer: ProgressObserver): InstalledModel
    suspend fun remove(model: ModelRef)
}

interface FaceDetector {
    suspend fun availability(): CapabilityAvailability
    suspend fun detect(input: ImageInput, options: FaceOptions = FaceOptions()): FaceResult
}

interface InferenceSession<I, O> {
    suspend fun start(): SessionResult
    fun run(input: I, options: RunOptions = RunOptions()): Flow<InferenceEvent<O>>
    suspend fun cancel()
    suspend fun close()
}
```

ASR 使用 `AudioChunk`/`AudioFile` 输入和 `TranscriptEvent` 输出；TTS 使用文本和输出模式（直接播放或导出音频，按后端声明支持）；超分使用文件或平台视频句柄，不让逐帧大数组穿过 KMP；多模态使用图片、视频文件/帧引用和文本消息。文件引用须明确由谁持有和何时释放，平台缓冲区必须保留到该次推理完成。

每个成功接受的任务恰有一个终态：`completed`、`failed` 或 `cancelled`，终态之后不再发事件。每个会话同一时间最多一个活跃任务，重复执行返回 `busy`；取消幂等，完成与取消竞争时以先提交的终态为准。`close` 幂等，停止接收任务并等待底层释放，不能在 GPU 尚使用缓冲区时释放内存；`unload` 不代表删除磁盘模型。

相机流在平台端只保留一个处理中帧和一个最新待处理帧，覆盖旧待处理帧并报告丢帧数，避免无界排队。音频按采样率、声道、格式和时间戳传递，缓冲队列有明确长度，溢出时返回背压状态，不能静默丢掉转写音频。

数据流为：能力查询 → 模型状态检查 → 按需安装 → 创建会话 → 预处理 → 后端推理 → 增量/进度事件 → 输出校验 → 关闭会话。取消在下载、模型加载、帧循环和生成回调处检查；失败时保留既有有效模型和输出，不发布半成品。

## 6. 模型清单和动态加载

每个模型由版本化 manifest 描述，发布清单必须具备以下字段；本设计不提供可下载的占位模型包，也不把未测量的帧数或内存设为生产默认值。

| 字段组 | 规则 |
| --- | --- |
| 身份 | schemaVersion、modelId、不可变 revision、上游仓库和固定提交/资产版本 |
| 能力与兼容 | capability、backendId、backendVersionRange、minOS、架构、模型格式版本 |
| 文件 | 全量相对路径、受信任来源 URL、字节数、SHA-256；含词表、processor、视觉 projector 和专家分片 |
| 处理协议 | 输入输出名称/形状/类型、预处理版本、tokenizer/提示模板版本、上下文和输入上限 |
| 资源 | 下载总量、安装临时空间、解压上限；内存估计注明测量设备和输入条件，不能当作保证 |
| 授权 | 模型/代码/转换产物的许可证标识、全文或链接、归属和再分发条件 |

模型下载写入沙盒临时目录，逐文件校验并校验整套文件关系，成功后原子移动为 `installed/<id>/<revision>`。同版本并发安装合并为一个任务，取消一个观察者不取消其他调用方仍需要的安装。解压拒绝绝对路径、路径穿越、越界符号链接和超出声明大小的内容。失败清理自己拥有的临时文件，不破坏有效缓存。

会话固定模型 revision；激活新 revision 不更换运行中会话的权重。Core ML 运行时编译产物与源模型分开缓存，编译缓存失效时可以由完整源包重建。旧版本没有活跃引用才可删除；使用中的删除请求返回 `inUse`。App 重启清理过期临时安装并检查完整性。安装、加载、卸载和删除是四个独立动作；“已安装”不表示“已加载”。

首版信任根是随 SDK 发布的固定清单，URL 使用 HTTPS 并固定资产版本；SHA-256 只验证内容完整性，不把远端同一响应给出的 hash 当作来源证明。远程目录更新若在后续引入，必须使用 SDK 内置信任公钥验证签名，且不得改变能力协议或加载执行代码。各业务 App 默认使用各自沙盒、配额和数据生命周期，不假设跨 App 共享缓存。

重型任务由后端配置并发上限，默认每个推理后端最多一个活跃重任务。加载前检查磁盘和估算内存，内存压力时停止接受新任务并释放空闲模型；该检查无法保证避免系统终止，因此验收包含持续运行和前后台切换。后端降级须符合调用方策略，并在结果中报告实际后端，不静默切换云服务。

## 7. 首版后端策略

### 7.1 人脸

iOS 使用 Vision 的人脸矩形和关键点请求；Android 首版使用 bundled ML Kit Face Detection，使检测不依赖 Google Play Services 的模型下载。以后采用 unbundled 后端时，未下载不能误报“无人脸”。系统/内置模型无需业务方手动安装 SDK 模型包。

输出基于完成方向归一化、尚未做预览镜像的图像：原点左上、x 向右、y 向下、坐标范围 0–1；输出带 frameId、时间戳、图像尺寸及预览变换。关键点按语义区域映射并保留点集类型，不假设两端关键点数量或序号相同；姿态按轴逐项声明可用性。trackingId 仅为会话内跟踪，不代表身份，也不跨会话保存。表情/质量不做跨后端统一分数承诺，缺失字段显式为空。

后续人脸路线拆为三个独立模块：`face-verification`（1:1 特征比对）、`face-identification`（本地 1:N 索引搜索）和 `face-liveness`（活体检测）。每个模块单独定义特征存储、阈值、误接受/误拒绝指标、数据删除和用户授权；首版不预留“检测结果自动升级为身份结果”的隐式行为。

### 7.2 ASR/TTS

iOS 15 首先使用 `SFSpeechRecognizer` 和 `AVSpeechSynthesizer`；ASR 检查支持情况并设置 `requiresOnDeviceRecognition = true`，不可用时返回明确状态，不能退回默认联网识别。iOS 26 的 `SpeechTranscriber` 作为可选增强后端。Android API 31+ 且可用时通过 `createOnDeviceSpeechRecognizer` 创建本地识别服务；API 26–30 或没有服务的设备返回不可用，后续由离线模型后端补齐覆盖。

Android TTS 选择不要求网络的已安装 Voice；iOS TTS 核对已安装语音并以断网测试验证。不能把存在系统 API 等同于支持所有语言的离线推理。下载语言资源作为显式安装动作，和推理隔离。离线神经 ASR/TTS（Whisper、sherpa-onnx 或 ONNX 模型）另做后端，不改变公共会话协议；SDK 不接管业务 App 的音频会话配置，允许调用方注入音频管理策略。

### 7.3 视频超分

先实现离线视频任务和完整性校验：输出尺寸、帧数、时间戳、音轨、取消和可解码性。iOS 26 系统超分作为可选后端；iOS 15 路径评估 Core ML Real-ESRGAN。Android 评估 LiteRT GPU/NPU 与 ONNX Runtime，并以同一套短视频样本比较质量、耗时和峰值内存。实时摄像头超分不列入首个实现切片。

CrawlerBot 已探明的 iOS 26+ 能力保留为 `available` 后端，不抬高 SDK Core 的最低版本。SDK 在能力查询中报告 `requiresOSVersion`，在支持设备上允许显式选择该后端；不支持时保持可用的低版本后端或返回不可用。系统超分、SpeechTranscriber 等能力都进入同一 benchmark 矩阵。benchmark 固定输入集和模型 revision，在同一设备/系统上比较可用后端；跨系统或设备的结果分组展示，不把硬件差异归因于系统 API。

benchmark 记录设备/系统、实际后端、模型与预处理版本、冷启动、模型加载、预热和纯推理时间，报告 p50/p95 延迟、吞吐、峰值内存及可重复的任务质量指标。能耗/热状态仅在有可靠测量手段时记录，否则标为未测，不用代理值冒充功耗。视频超分使用固定视频样本和 PSNR/SSIM/VMAF 等适用指标，并同时检查帧数、时间戳、音轨与完整解码；ASR 使用 CER/WER，VLM 使用固定任务集和人工可复核的质量记录。不同能力不合并成一个总分。

### 7.4 多模态

SmolVLM2 500M 作为首个 iOS MLX 验证模型，256M 作为低内存档，2.2B 作为高端档。Android 先验证对应 GGUF/llama.cpp 路径；LiteRT-LM 只接入其模型导入和运行时明确支持的模型。Florence-2 作为结构化图像任务模型（caption、OCR、grounding、检测）单独评估，不把它包装成聊天模型。MiniCPM-V 4.7 先验证 1B，35B-A3B 需要额外的专家分片、存储和内存验收。

## 8. KMP Demo 与分发

Demo 使用 Compose Multiplatform，展示能力状态、模型安装、权限、推理进度、取消、结果和性能摘要。平台侧只注入原生能力实现；相机、麦克风、音频会话和系统权限留在平台层。

基础与增强 Demo target 共享 KMP 展示流程；未链接的后端显示原因和升级条件，不能暗示通过下载模型就能获得缺失的执行代码。首阶段只展示实际完成人脸检测与 SDK 基座的页面，后续能力在研究文档中跟踪，不交付不可用的假按钮。

iOS SDK 以 Swift Package/XCFramework products 分发，按模块选择依赖；Android SDK 以 Maven AAR 分发。模型资产不进入 AAR 或 XCFramework，使用 manifest 和受控下载器按需安装。Demo 可以加载远端公开模型或开发者指定的本地模型路径，但发布 SDK 不携带大模型权重。

## 9. 验收和测试

每个能力同时有纯逻辑测试、后端集成测试和实机验收。测试报告必须记录设备型号、系统版本、SDK/模型 revision、量化方式、冷启动、预热、纯推理、峰值内存和实际执行后端。

首阶段验收：

1. iOS 15 真机与 Android API 26+ 设备完成图片/相机人脸检测；另测较新 iOS 和 Android，各平台覆盖可取得的一台中端与一台较新设备。报告列出真实设备和系统版本，没有最低系统真机时该项保持未验证，不能用新系统或模拟器代替。
2. 旋转、镜像、裁剪和坐标变换有固定夹具回归测试。
3. 模型下载中断、SHA-256 失败、磁盘不足、版本不兼容和取消路径可复现。
4. iOS 15 最小接入宿主不链接 MLX，原生 Swift/Kotlin 最小消费者不引入 KMP；KMP Demo 能显示能力状态，不把高版本后端缺失显示成崩溃或空结果。编译、最低系统启动、断网推理分别记录证据。
5. ASR/TTS、超分和多模态先建立基线，不在没有设备证据时承诺统一实时性指标。

后续阶段分别验证：ASR 中文/英文文件与流式样本；TTS 队列、取消和音频中断；超分输出完整性和质量对比；多模态图片描述、短视频摘要、首字延迟、峰值内存和持续运行稳定性。每个后端先进入 benchmark，再决定是否进入稳定 SDK 产品面；benchmark 结果不能直接当作跨设备承诺。

## 10. 仓库与同步

项目目录为 `/Users/daubert/Github/ondevice-ai-kit`。两端仓库初始设为 private，远端目标固定为：

- GitHub：`AmatsuZero/ondevice-ai-kit`
- 极狐个人空间：`https://clients-gitlab.ugreeniot.com/Daubert/ondevice-ai-kit`

极狐已只读验证 `full_path=Daubert`、`kind=user`、namespace ID 为 572；创建前重新核对，不在组织根空间创建。GitHub 作为主仓，极狐作为镜像副本。初始同步 main，后续仅同步显式列出的工作分支与发布 tag；不使用 `git push --mirror`。

使用一个串行写入者：本地提交 → GitHub fast-forward 推送 → 冻结已核实的 GitHub SHA → 极狐 fast-forward 推送 → 重新读取两端目标引用并比较 SHA。极狐失败标记为“同步待重试”，重试前重新对账；存在分叉停止该引用写入，保留两边提交。tag 发布后不可覆盖。CI 自动同步仅在最小权限凭据和仓库能力验证后启用，凭据置于 Secret，首版不假设极狐支持某付费镜像功能。

首个实现计划包含 Core 契约、ModelStore 的基础生命周期与本地小夹具验证、人脸检测模块、iOS 15 Vision 后端、Android bundled ML Kit 后端、KMP Demo 和 benchmark 基础设施。人脸路径没有自定义权重，不用伪造下载模型来证明人脸功能。benchmark 首先支持可取得的 iOS 15/高版本 iPhone 与 Android 设备，比较人脸后端；视频超分阶段加入 iOS 26 VideoToolbox、经移动端验证的 Core ML/ONNX/LiteRT 后端及共同视频样本。1:1 比对、1:N 搜索、活体认证，以及 ASR/TTS、超分、多模态各模型实现，补充模型、许可证和实机证据后进入独立设计/计划/实现周期；iOS 26+ 系统能力可先以 benchmark 任务进入，不要求等待低版本后端完成。

## 11. 主要风险与处理

- iOS 15 与高版本后端：区分系统 API availability 与第三方 package 编译门槛，基础和增强依赖分别构建、验证；高版本能力统一进入 benchmark 和 `requiresOSVersion` 状态。
- 系统语音的离线能力因设备和语言变化：公开 `supportsOnDevice` 与实际失败原因。
- 模型权重过大或更新失败：分片清单、临时目录、SHA-256 和原子发布。
- 不同后端输出质量不一致：统一输入预处理、样本集和报告格式，不宣称跨后端等价。
- Android 硬件加速差异：记录实际 delegate/backend，仅在调用方策略允许时回退本地 CPU，否则报告不可用。
- Apple App Store 代码加载限制：运行时只下载模型/资源，不下载或执行改变 App 功能的代码。
- 输入隐私：默认不保存相机、照片、音频或提示正文，诊断只记录必要元数据；样本持久化仅由 Demo 显式导出触发。

## 12. CrawlerBot 复用边界与研究证据

以下为源码静态检查结果，不表示新 SDK 已编译或已在手机上运行。现有项目保持原样，本设计不直接复制其 macOS 构建环境：

| 本地源码路径（相对 `/Users/daubert/Github/CrawlerBot`） | 可借鉴内容 | 迁移限制 |
| --- | --- | --- |
| `speech_subtitle/swift-tool/Sources/SpeechSubtitleLib/SpeechModelAssets.swift` | 系统语音资产安装与进度 | 当前 macOS 26；iOS 15 使用独立的旧版 Speech 后端 |
| `speech_subtitle/swift-tool/Sources/SpeechSubtitleLib/Live/LiveTranscriptionSession.swift` | PCM 校验、会话状态和增量事件 | 验证移动端音频会话、取消与背压，不搬运 Node/进程桥接 |
| `speech_subtitle/swift-tool/Sources/SpeechSubtitleLib/PostProcess/MLXProvider.swift` | 本地模型容器加载与复用 | 当前为文本后处理，不是 VLM；缓存需增加预算与显式释放 |
| `video_enhancer/swift-tool/Sources/VideoEnhancerLib/RealESRGANModel.swift` | manifest、SHA-256、原子安装、编译缓存 | 替换 Process/ditto/unzip 和 macOS 缓存路径 |
| `video_enhancer/swift-tool/Sources/VideoEnhancerLib/RealESRGANProcessor.swift` | 分块推理、边缘处理与融合 | 重新测试手机内存、像素格式和视频输出 |
| `InvoiceOCR/Sources/InvoiceOCRLib/VisionOCR.swift` | Vision 请求与坐标处理 | 当前使用 AppKit/NSImage；人脸检测另用对应 Vision 请求 |

研究证据分为“官方文档/源码支持”“本地已有实现”“新 SDK 实机已验证”三档。目前仅前两档具备部分证据。LiteRT/ONNX 的转换成功不能替代目标设备推理成功；图像超分成功不能替代视频时间轴与音轨验收。

其他端侧模型候选包括 Qwen3.5-0.8B/2B 与 Gemma 系列。它们进入对照研究池，不列入首阶段依赖；选定具体 checkpoint 后才评估许可证、视觉能力、量化产物、目标运行时与中文样本。Florence-2 的 ONNX/Core ML 转换和移动端解码仍是待验证路线，不宣称开箱即用。

MiniCPM-V 4.7 官方手机 Demo 使用专门的 llama.cpp-omni 分支。35B-A3B 的专家分片方案总下载约 16.6GB，官方低内存解码展示依赖特定设备与调度方案；不能按 3GB 内存展示值估算下载体积。SmolVLM2 的 HuggingSnap 使用 MLX 和可更新的运行配置，SDK 复用部署经验但不继承无版本锁定的模型配置。

## 13. 参考资料

资料查阅日期为 2026-10-15。main 分支和文档可能变化，实施时锁定依赖 tag/commit，并保存实际构建和设备证据。

- Apple Vision：<https://developer.apple.com/documentation/vision>
- Apple Speech `supportsOnDeviceRecognition`：<https://developer.apple.com/documentation/speech/sfspeechrecognizer/supportsondevicerecognition>
- Apple `SpeechTranscriber`：<https://developer.apple.com/documentation/speech/speechtranscriber>
- Apple VideoToolbox Super Resolution：<https://developer.apple.com/documentation/videotoolbox/vtsuperresolutionscalerconfiguration>
- Apple App Review 2.5.2：<https://developer.apple.com/app-store/review/guidelines/>
- Google ML Kit Face Detection：<https://developers.google.com/ml-kit/vision/face-detection>
- Android `SpeechRecognizer`：<https://developer.android.com/reference/android/speech/SpeechRecognizer>
- LiteRT-LM：<https://github.com/google-ai-edge/LiteRT-LM>
- ONNX Runtime mobile：<https://onnxruntime.ai/docs/tutorials/mobile/>
- Compose 兼容矩阵：<https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compatibility-and-versioning.html>
- MLX Swift 平台声明：<https://github.com/ml-explore/mlx-swift/blob/main/Package.swift>
- MLX Swift LM 平台声明：<https://github.com/ml-explore/mlx-swift-lm/blob/main/Package.swift>
- SmolVLM2：<https://huggingface.co/blog/smolvlm2>
- HuggingSnap：<https://github.com/huggingface/huggingsnap>
- Florence-2：<https://huggingface.co/microsoft/Florence-2-base>
- MiniCPM-V 4.7：<https://github.com/OpenBMB/MiniCPM-V/blob/main/docs/minicpm_v4dot7_en.md>
- MiniCPM-V mobile demos：<https://github.com/OpenBMB/MiniCPM-V-Apps>
- Qwen3.5-0.8B：<https://huggingface.co/Qwen/Qwen3.5-0.8B>
- Qwen3.5-2B：<https://huggingface.co/Qwen/Qwen3.5-2B>
- Gemma 移动端部署：<https://ai.google.dev/gemma/docs/integrations/mobile>

