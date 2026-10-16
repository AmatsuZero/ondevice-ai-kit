# OnDevice AI Kit 能力与可选后端设计

日期：2026-10-16
状态：对话设计已确认，待书面规格审阅；未授权实现。

本文补充[SDK 总体设计](2026-10-15-ondevice-ai-sdk-design.md)。涉及能力模块、后端接入、默认选择和分发裁剪时，以本文为准；总体设计中的模型生命周期、任务终态、输入坐标、系统下限和实机验收规则继续适用。现有[基础实现计划](../plans/2026-10-15-ondevice-ai-kit-foundation.md)形成于本文之前，其已完成部分保留为历史记录；下一阶段执行前须另写计划，不直接按旧计划扩展后端选择接口。

## 1. 意图与成功条件

SDK 面向多个商用 App，覆盖人脸、语音、超分和多模态等能力。宿主按需接入能力与具体实现；同一能力在 iOS、Android 上可有不同后端。调用方使用稳定的能力接口，并可用参数明确指定已接入的后端。默认使用平台基础实现，不能因引入云端模块而隐式上传输入。

探索阶段先证明接口、选择规则和依赖裁剪成立，不同时建设 CocoaPods、Swift Package、XCFramework 等所有分发形式，也不提前创建尚未实现的能力空模块。商用交付所需的许可证、凭据、隐私和设备验收按后端逐项确认。

成功条件：

1. 只接入一种后端的最小宿主不依赖其他后端及其第三方 SDK；纯原生宿主不依赖 KMP。
2. 接入两种测试后端后，可查询、默认选择和显式选择；请求未接入或不可用的后端返回可区分的原因。
3. 两端对后端选择、结果归属和错误语义使用相同契约夹具；实际输出始终记录后端 ID。
4. 本地默认路径不会自动转向需要联网的实现；后端能力差异不会被静默忽略。

## 2. 方案与边界

采用原生能力接口、独立后端模块、KMP 适配层：

- 每项能力定义自己的类型化请求、结果和会话接口，例如 `FaceDetection`、`FaceVerification`、`FaceIdentification`、`FaceLiveness`、`TTS`、`ASR`、视频超分和多模态。相近领域不强迫共享不相同的结果类型。
- 能力模块仅依赖 Core 契约。后端模块依赖对应能力模块和所需的系统 API、第三方 SDK 或模型运行时；能力模块不反向依赖后端。
- 每个能力模块公开一个面向业务的 Client（例如 `FaceDetectionClient`、`TTSClient`）。Client 持有该能力的后端注册表，提供查询、默认/显式选择和类型化任务方法；后端模块提供符合该能力后端协议的实现或工厂。宿主在组合入口显式注册所接入的后端。首阶段不使用反射、自动扫描、远程代码注册或下载执行代码。
- KMP 共享选择语义、可移植 DTO 和业务状态；平台桥接注入并调用对应的原生 Client，不另建一套 KMP 后端注册表。iOS/Android 原生模块负责权限、缓冲区、推理和资源释放。Swift/Android 原生消费者不必引入 KMP。

当前 `ODAIKitFace`/`ODAIKitAppleVision` 与 Android `face`/`backend-mlkit` 已有物理拆分，但 Demo 分别直接构造 Vision/ML Kit，`FaceDetector.detect` 和 `FaceBridge.detect` 尚无后端选择参数。下一阶段新增 `FaceDetectionClient` 作为带选择参数的上层入口，由现有 `FaceDetector` 实现充当后端并逐步迁移 Demo/原生示例；不要求在每个具体后端的 `detect` 方法上重复增加选择参数。本文定义下一阶段要补的组合与选择契约，不把现状写成已实现。

## 3. 后端描述、注册与选择

每个后端有稳定、带命名空间的 `backendId`。已有的人脸 ID `apple.vision.face` 和 `android.mlkit.face.bundled` 保持不变；后续可用 `apple.avspeech.tts`、`microsoft.azure-speech.tts.cloud` 等 ID。ID 标识实现及服务形态，不用展示名称或包名代替。后端描述至少包含能力 ID、实现版本、平台和最低系统要求、支持的选项、是否需要网络、凭据和模型资产，以及当前可用状态与原因。系统管理的语音资产不冒充 SDK ModelStore 中的自有模型。

构建时的 `packaged` 表示宿主已添加实现依赖；运行时的 `registered` 表示宿主已在组合入口提供实例或工厂；`available` 表示已注册后端在当前设备、系统、权限、资产和配置条件下可运行。运行时只枚举已注册的后端，不尝试探测二进制里是否存在未注册的实现。查询可用性不得触发权限弹窗、下载、凭据交换或模型加载。对会变化的条件，调用时重新校验。请求未注册的 ID、已注册但不可用的 ID，以及不支持的选项，分别返回稳定错误类别。

Client 是唯一负责后端选择和可用性汇总的公开入口。注册时拒绝重复 ID 与能力 ID 不匹配；后端描述由注册项提供，查询不必创建推理会话。Client 对一次调用只分派给选中的后端，不在内部重试另一个后端。Client 持有已注册实现的生命周期：关闭后不接收新任务，取消并等待其拥有的活动任务/会话及后端资源关闭；宿主在 Client 关闭前不单独关闭其已注册实例。后端仍可在测试或高级原生接入中直接构造，但这不提供跨后端选择语义。

两端错误契约至少包括 `backendNotRegistered(capabilityId, backendId)`、`backendUnavailable(capabilityId, backendId, reason)` 和 `unsupportedOption(capabilityId, backendId, optionId)`。`Default` 对应的基础后端未注册或不可用时，分别使用前两类并填入其实际 ID，不再引入特殊默认错误。已有任务错误如 `busy`、`closed`、`cancelled` 保持原语义。Swift/Kotlin 对这些类别和字段使用共享夹具；平台底层错误可作为附加诊断，不替换稳定类别。

Client 的能力方法接受一个统一的选择值，概念上为 `Default` 或 `Explicit(backendId)`；具体 Swift/Kotlin ABI 在实现计划中以编译探针固定。短任务在调用时选择；流式或长任务在创建会话时选择，并在会话生命期内固定后端。显式选择严格使用指定实现，不回退其他后端。结果、事件及 benchmark 记录实际后端 ID；取消和关闭沿用总体设计的单终态与资源释放约束。

概念调用形式为 `tts.synthesize(request, backend = Default)`、`tts.synthesize(request, backend = Explicit("microsoft.azure-speech.tts.cloud"))` 或 `asr.openSession(config, backend = Explicit(id))`。这是选择语义示例，不承诺当前代码已有这些方法或固定各语言的最终签名。

`Default` 是每个平台、每项能力预先指定的一个**基础后端**，不表示任意后端自动排序。有适用的系统自带实现时将其指定为基础后端；没有时可指定一个随 SDK 接入的本地实现，例如当前 Android 人脸检测的 bundled ML Kit。这个映射不因设备临时不可用而改变。基础后端未注册或暂不可用时报告原因，不自动选择云端或另一后端。宿主需要云端实现时必须显式指定。后续若需要可配置的自动回退策略，应另立设计并由调用方明确开启。

## 4. 能力差异与联网实现

公共能力接口仅承诺可跨后端定义和验证的核心语义。可选能力通过后端描述查询；后端特有参数采用命名空间明确的扩展选项，不把第三方 SDK 类型放进公共 DTO。不支持的参数必须拒绝，不能忽略或假装有同等语义。不同后端的质量分数、声音、语言覆盖、延迟和资源开销不宣称等价。

微软 Speech SDK 是 TTS 可选后端的实例，不是 TTS 接口的依赖。`microsoft.azure-speech.tts.cloud` 明确标记联网和凭据要求；宿主注入短期凭据获取方式，SDK 不内置服务主密钥。隐私说明需披露输入可能发送至云端，默认本地调用不能隐式切换至此后端。微软嵌入式 TTS 若进入范围，使用独立 ID 与模块，并在明确版本、授权、模型资产、目标设备验证后发布；不把云端与嵌入式包混成同一个后端。

模型资产仍由对应后端声明，并按总体设计的 ModelStore 规则安装和租用。安装、已链接、可用、已加载是不同状态。

## 5. 模块和分发

首阶段沿用现有 iOS Swift Package products 与 Android Maven 模块。新增能力按“契约 product/artifact + 每个后端独立 product/artifact”交付；后端才引入第三方 SDK。例如 TTS 可对应 `ODAIKitTTS`、`ODAIKitTTSApple`、`ODAIKitTTSAzureSpeech`，Android 使用相同逻辑边界的独立 Maven 坐标。实际创建模块以该后端进入实现阶段为前提。

CocoaPods subspec、独立 Framework/XCFramework 可以在商用分发阶段映射相同边界，例如 `TTS/Apple` 和 `TTS/AzureSpeech`；本文不要求探索阶段实现这些渠道，也不假设第三方 SDK 本身提供同名 subspec。一个后端的第三方依赖、最低系统版本或许可证不得抬高未接入该后端的基础能力包要求。Swift Package 的平台下限作用于整个 package；如果第三方后端要求更高下限，须使用独立 package manifest/构建入口，不能仅拆分同一 package 中的 product。以最小宿主的编译、链接和低系统启动探针确认隔离后再宣称支持。

## 6. 验证切片与阶段边界

下一实现阶段先利用现有人脸检测能力及测试后端，验证注册、默认选择、显式选择、错误分类、KMP 桥接和依赖裁剪。测试后端只用于契约验证，不作为用户可安装的生产功能。iOS Vision 与 Android bundled ML Kit 继续作为各自平台的基础实现。TTS、身份验证及其他能力逐项沿用这套结构，每项另定输入输出、可选特性和实机样本；不为其提前发布空 API 或假按钮。

验证至少包括：两端共享选择夹具；未注册、不可用和不支持选项的错误；显式选择不回退；默认路径断网可运行或明确不可用；最小宿主依赖图；会话期间后端固定；结果及 benchmark 标记实际 ID。真实第三方后端还需独立核对 SDK 版本、许可证、网络、凭据、最低系统、质量和设备证据。构建通过不等于实机或商用验收通过。

## 7. 设计取舍和参考

将 KMP 作为整个 SDK 核心会让 Swift 原生接入、系统 API 与可选二进制依赖受桥接约束；把所有后端打进单体包则无法物理裁剪第三方依赖。现有原生接口与 KMP 适配结构更适合逐项扩展，代价是 Swift/Kotlin 需要用共享夹具保持语义一致。

以下资料于 2026-10-16 查阅；实际接入时重新锁定版本并验证：

- [KMP common interface 与平台实现](https://kotlinlang.org/docs/multiplatform/multiplatform-connect-to-apis.html#interfaces-in-common-code)
- [Microsoft Speech SDK 安装方式](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/quickstarts/setup-platform)及[iOS Swift Package](https://github.com/microsoft/speech-sdk-spm)
- [Microsoft 嵌入式 Speech](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/embedded-speech)及[版本记录](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/releasenotes)：iOS 嵌入式支持范围的页面表述不同，接入前核对具体版本和授权
- [CocoaPods subspec 语法](https://guides.cocoapods.org/syntax/podspec.html#subspecs)
