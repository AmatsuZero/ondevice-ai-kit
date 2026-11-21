# Multimodal SDK Foundation

日期：2026-10-22。基线 `ae76aa3`，`feat/sdk-foundation`。属于 P9 由单平台探针走向 SDK 的首个独立工作包，不关闭 P9/roadmap。

## 意图、范围与决策

目标是给多个原生 App 提供可裁剪的图文生成能力接口，随后接入 P8 已选 SmolVLM2 500M/MLXVLM。已有探针仅证明固定图片→描述和设备取消，不包含可复用 SDK 的后端注册、并发查询/关闭或模型所有权契约。

首包只实现 Swift `ODAIKitMultimodal` 与 Android `android/multimodal` 的纯契约/client、本地分发与只依赖 Core+Multimodal 的消费者。每次请求是一张图片和提示词，适用于描述与问答；对话历史、视频/帧序列、结构化 OCR/grounding、真实 MLX/Android 模型、ModelStore adapter、KMP bridge/Demo、质量/性能验收分别作为后续工作包，不把本包标成完整多模态交付。后续仍需接入图片与短视频能力、模型状态/加载/释放、KMP/Demo及另一平台，完整目标不缩减。

选择独立能力模块，复用 Core 的 availability。把探针直接并入基础 SDK 会引入 MLX/iOS18 下限和模型加载副作用；把图文塞入 VideoBridge 会混淆文本生成与视频文件结果。本包不采用这两种方式。不添加第三方依赖；Android 复用已用协程库。基础下限 iOS15/macOS12、Android API26 不变。

用户已要求按 roadmap 自主推进、在当前会话执行并逐阶段更新 handoff。这里保留设计/计划、自审和测试证据，不额外请求常规继续许可，也不声称用户已逐行审阅新文档。禁止子代理、push/发布远端、模型或资源下载、自动录音、私人媒体读取及签名资产修改。两个既有保护文件保持不变且不暂存。

## 公共类型与接口

命名在两端一一对应，Swift 使用 lowerCamel case，Kotlin enum 使用 UPPER_SNAKE_CASE。

- `MultimodalRequest(imageFile, prompt, maxOutputTokens=128)`：Swift URL 必须为 file URL；prompt 去空白后非空；maxOutputTokens>0。图片文件/解码/格式和后端预算由实际 backend admission 校验，构造请求不做文件 IO，不修改原 prompt。Android 用 File。
- `MultimodalModelIdentity(modelId, modelRevision, runtimeRevision)`：三个值去空白后非空，保存原值。revision 是后端绑定的精确版本标识，不强制为 Git SHA；后端包另负责资产校验，不能仅以这些字符串宣称模型已验证。
- `MultimodalTextUpdate(sequence, text)`：sequence>=0；text 是当前累计生成文本，可为空；sequence 代表更新序号，不是 token 数量。不推断 tokenizer/词数，也不将 chunk 个数伪装成 token 数。
- `MultimodalTextOutput(text, model)`：text 去空白后非空，否则拒绝为 invalidInput；model 是上述 identity。输出是不可变的文本值，无文件输出 lease；client.close 不清空已返回值。
- `MultimodalGenerationResult(backendId, output)`：由 client 写入实际选择的 backendId；ID 非空。
- `MultimodalBackendSelection.default/explicit(String)`：Default 只用注册默认 ID，Explicit 未注册/不可用立即报错，永不切到其他后端。
- `MultimodalGenerationBackend`：backendId、`availability()`、`generate(request, update?) -> MultimodalTextOutput`、`drain()`、`close()`；后端单次生成需遵守请求 maxOutputTokens，实际实现接入时验证，不在 Foundation 伪造完成结果。
- `MultimodalBackendDescriptor(backendId, availability)`：本包不声称所有后端支持任意图片大小/语言或视频；后续 backend 的请求 admission 提供真实限制。
- `MultimodalClientError`：invalidInput、invalidRegistration、backendNotRegistered、backendUnavailable(id, reason)、busy、closed、cancelled（Kotlin coroutine 原生 CancellationException 对应取消）。未知平台异常本包原生 API 保留原异常，不把私有文本写入日志；未来 KMP adapter 必须稳定映射。
- `MultimodalGenerationClient(defaultBackendId, backends)`：空白/重复 ID 或缺默认注册失败；空 backend 列表不构造假 client。`listBackends()` 返回按 ID 排序的描述；`generate(request, backend=.default, update?)`、`drain()`、`close()`。

## 生命周期与所有权

client 拥有注册的 backend。创建 client 不加载模型、不下载资源。每个 client 只允许一个生成槽，availability 等待期间也占槽；cancel 后直到真实 backend.generate 和 drain 完成前仍 busy。请求及输入引用保持至终态；宿主不得在 drain 前删除/修改输入。

client 在本次 generate 开始后建立唯一 operation identity。backend 更新通过 client 串行化；只接受严格递增 sequence，取消、关闭、旧 operation 的更新不交付；回调不保证特定 UI dispatcher，宿主负责切换。更新是累计文本，即使并发后端更新的旧序号晚到，也不需要重拼已丢弃 delta。终态前后台队列中的旧更新也不能泄漏到下一请求。

取消向底层传播，但不可跳过 drain。底层忽略取消并返回输出、或取消后晚到普通错误时，调用者取消胜出（抛 CancellationError/CancellationException）；不交付晚到输出。正常 backend 错误保持原样。Swift 用独立取消状态的任务，Kotlin 用 NonCancellable 等待 drain；无 release 文件清理路径。drain 等待活动生成完成，不主动取消。

close 立即封闭新查询/生成 admission，取消并 join 活动生成，同时 join 所有已接纳 availability 查询，随后逐个关闭各 backend 一次。并发/重复 close 共享终态；Kotlin 外部等待者取消不能取消内部 close/跳过 backend cleanup。backend.close 不抛错（Swift async非throws，Kotlin实现遵守接口不抛异常的约定；若意外抛错，client仍尝试关闭其他backend，重复close传播一致终态）。已交付输出不撤销；关闭后查询/生成均拒绝。backend 列表不能隐式安装资产或触发模型推理。

## 验证与停止边界

Swift/Android 均用实际 client 和可控 backend，不依赖模型或真实图片：契约无文件IO、严格选择无回退、availability/并发槽、成功/失败/cancel的drain、底层忽略取消/晚到错误、过期/乱序更新屏蔽、query/close join、重复close与取消close等待者。用明确可控 latch/continuation，不用长 sleep 证明竞态。每项先观察 RED，再实现 GREEN；任务完成单独自审并本地提交。

分发增加第十个 Apple XCFramework 和 Android Maven local artifact。Core+Multimodal-only 设备/模拟器 consumer 编译链接，确认不依赖 MLX/Speech/Video/Identity，iOS15 target不变；Android consumer同样只依赖Core+Multimodal，检查Maven POM与真实消费。生成器前置校验/拒绝覆盖回归；不改受保护 Demo project。既有原生组件保持全套回归。

这些测试只证明契约/协调/裁剪，不证明模型推理、图像理解质量或 iOS15/API26设备执行。MLX真实后端、已有资产适配、device与KMP/Demo属于后续包。Video Task6 UI/独立审查、P6实录及旧审查缺口独立保留，不重试已确认的外部 blocker。
