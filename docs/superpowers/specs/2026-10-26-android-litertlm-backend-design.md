# Android LiteRT-LM 多模态后端设计

日期：2026-10-26。本文承接 P9 Android 缺口，在现有 `android:multimodal` 契约和 `android:model-store` 完整性边界之上增加一个可选 LiteRT-LM 后端模块。目标是形成可审查、可取消、可重入的真实 Android 图像后端；视频能力保持显式 unavailable，直到模型和抽帧语义另有独立证据。

## 2026-10-27 合同勘误

原批准设计使用 `terminalCallbackThenDeadConversation`（terminal + `isAlive=false`）作为 drain 证明；固定 0.18.1 AAR 已否定该假设。`isAlive` 在 close 调用 native delete 之前就变 false，不能证明 native 完成。此处撤销该具体判据，保留生命周期目标与生产门禁；没有批准或启用替代关闭协议。完整证据、源码限制与 RED→GREEN 见[合同修正](../../validation/android-litertlm-drain-contract.md)。

## 意图

让 Android 调用方可以显式安装并选择 LiteRT-LM 后端，用本地、哈希验证过的模型目录完成图片加 prompt 生成，同时保持基础 `android:multimodal` 无 LiteRT-LM 依赖、默认 Demo 不自动加载模型、缺少模型时返回稳定 unavailable。该包先建立真实运行时边界，不能用 fixture 输出或 AAR 静态检查代替设备推理。

## 已确定的实现路径

采用独立可选 `android/backend-litertlm` Android library：

- `android:multimodal` 保留纯契约与共享 operation 生命周期；不引入 LiteRT-LM。
- 新模块依赖 `android:multimodal`、`android:model-store` 和固定版本 `com.google.ai.edge.litertlm:litertlm-android:0.18.1`。它不进入 Demo 默认依赖，调用方显式构造并注册 backend。
- 真实引擎通过内部 `LiteRtLmEngine` seam 注入；生产实现包装 `Engine`/`Conversation`，测试实现使用确定性 fake。这样可以在没有模型权重时锁定生命周期，但不会把 fake 结果记为推理证据。
- backend 只实现 `MultimodalGenerationBackend.generate(image)`；不实现 `MultimodalVideoGenerationBackend`，`supportsVideo=false`。视频抽帧与时间戳是后续独立包，不能把多帧循环调用伪装成视频支持。

## 目标

1. 用 `ModelStore.acquire(ModelRef)` 持有经过 manifest、文件集合、大小和 SHA-256 校验的 `ModelLease`，lease 覆盖 engine 初始化、图像读取、Flow/callback 消费和 native 终止。
2. 以固定 model/runtime identity 构造 `MultimodalTextOutput`，严格传播 prompt 与 `maxOutputTokens`，拒绝空输出和超出后端预算的请求。
3. `availability()` 只做能力与资产检查，不初始化模型；缺失资产、API/ABI 不支持、模型不兼容分别映射为稳定 `CapabilityAvailability`/`MultimodalClientError`。
4. 取消顺序固定为 caller cancellation → 停止 Flow/callback 消费 → `Conversation.cancelProcess()` → 等待 native 生成终止（drain）→ 关闭 conversation/engine → 释放 lease。调用方取消不能跳过 drain 或删除正在读取的模型文件。
5. 每次生成有 generation identity；晚到、重复、倒序 callback 不能污染下一次同进程 reentry，也不能在取消/close 后交付 update。
6. `close()` 关闭 admission，取消并 join 当前 operation 和 availability query，再关闭 engine/conversation 一次；重复 close 共享同一完成结果，close 后生成和查询稳定拒绝。

## 非目标与反目标

- 不修改 LiteRT-LM 上游、不下载模型、不在安装时联网、不把模型权重提交 Git/LFS。
- 不把 AAR 的 `minSdk=24` 当作 API26 设备兼容性结论；TECNO 真机运行、模型支持矩阵、GPU/NPU、延迟、内存、能耗和自然质量各自单独验收。
- 不把 `Conversation.sendMessageAsync` 的 Flow 完成当作 native 释放证明；必须显式等待 cancel/drain 后再释放输入和 lease。
- 不自动回退到其他 backend，不在 Demo 中隐式启用 LiteRT-LM，不把 video request 映射为 image request。

## 模块边界与主要类型

新模块建议提供：

- `LiteRtLmModel`：固定 backend id、model/runtime identity、manifest 校验和后端预算。
- `LiteRtLmBackend`：实现 `MultimodalGenerationBackend`，拥有一个 `LiteRtLmGenerationSession`。
- `LiteRtLmGenerationSession`：以 `Mutex`/单 operation 状态串行化 availability、生成、drain、close；持有 `ModelLease` 直到 engine 与 conversation 已终止。
- `LiteRtLmEngineFactory`（internal）：生产实现只在这里引用 LiteRT-LM 类；`suspend fun open(modelPath: File, config: LiteRtLmEngineConfig): LiteRtLmSession`。`LiteRtLmSession` 是唯一所有权对象，同时持有一个 per-generation `Engine` 和 `Conversation`，并提供 `suspend fun generateImage(image: File, prompt: String, maxOutputTokens: Int, onSnapshot: (String) -> Unit): String`、`fun cancelProcess()`、`suspend fun awaitTerminated()`、`suspend fun close()`；健康 session 的 `close()` 固定按 conversation → engine 顺序执行且幂等，poisoned session 按下文 fail-closed 规则返回 `DrainFailure`。所有 blocking/native 调用运行在 `Dispatchers.IO`；fake 记录调用顺序、generation token 和 callback。
- `LiteRtLmError`：公开稳定 sealed error，仅含 `InvalidModel`、`RuntimeFailure`、`NoResult`、`DrainFailure`、`RuntimeUnavailable`；不携带 prompt、路径或原生异常文本。drain probe 未通过时 `LiteRtLmBackend` 的生产 factory 不返回实例，只返回 `RuntimeUnavailable` 并让 `availability()` 固定为 `UNAVAILABLE/UNSUPPORTED_DEVICE`。

生产 engine 的 `open` 在 IO dispatcher 构造 `EngineConfig(modelPath = resolvedModelPath, backend = CPU, visionBackend = CPU, maxNumTokens = 512, maxNumImages = 1, cacheDir = lease-private cache)` 并调用 `Engine.initialize()`；每个 `generateImage` 创建新的 `ConversationConfig(maxOutputToken = request budget)` 和新的 conversation/KV state。图像与 prompt 映射固定为 `Contents.Companion.of(Content.ImageFile(image.absolutePath), Content.Text(prompt))` 后交给 `Message.Companion.user(contents)`。`MessageCallback.onMessage` 只读取 `Message.contents.contents` 中的 `Content.Text`，多个 text 按顺序拼接成一个累计 snapshot；空 snapshot 不发布，重复 snapshot 丢弃。若上游探针证明 callback 是 delta 而不是累计值，生产 adapter 必须在 seam 内累加后再交付 snapshot，不能把语义留给调用方。

模型路径由 `LiteRtLmModelSpec.modelPathRelative` 明确解析为 lease 目录内的单个模型文件或目录；禁止拼接外部 prompt/URI。spec 的固定字段为 `backendId="litertlm.android"`、`capability="multimodal.image"`、`formatVersion="litertlm-model-v1"`、`processing.inputSignature="image-file+text-prompt-v1"`、`runtimeRevision="litertlm-android:0.18.1"`、`maxOutputTokens=512`、`maxImageBytes=16*1024*1024`，以及 `backendPolicy=CPU_ONLY`。CPU_ONLY 不自动回退或改选 GPU/NPU；GPU/NPU 需要单独的模型/资源包。cacheRoot 由构造方显式传入 app-private cache 目录，实际路径为 `cacheRoot/litertlm/<modelId>/<revision>/<generationUUID>`，session close 后递归删除，删除失败保持稳定 cleanup error 并禁止再次生成。实际模型未固定前，spec 只允许 fake/lifecycle 测试，生产 `availability` 返回 `REQUIRES_ASSETS/MISSING_ASSETS`。

lease 仅在 private cache 成功清理后释放；删除失败时返回稳定 `RuntimeFailure`、关闭 backend admission，并保留 lease/cache 到进程结束。此时 native conversation/engine 已完成正常 close，不能将 cleanup 失败标成 native drain 证明失败；模型移除仍会被所持 lease 阻止。

实现者按以下 Kotlin 形状落地，不再重新解释这些字段：

```kotlin
internal enum class LiteRtLmBackendPolicy { CPU_ONLY }
internal data class LiteRtLmEngineConfig(
    val backendPolicy: LiteRtLmBackendPolicy,
    val maxNumTokens: Int = 512,
    val maxNumImages: Int = 1,
    val cacheDir: File,
)
internal data class LiteRtLmModelSpec(
    val modelPathRelative: String,
    val metadataPathsRelative: Set<String>,
    val backendId: String = "litertlm.android",
    val capability: String = "multimodal.image",
    val formatVersion: String = "litertlm-model-v1",
    val inputSignature: String = "image-file+text-prompt-v1",
    val runtimeRevision: String = "litertlm-android:0.18.1",
    val maxOutputTokens: Int = 512,
    val maxImageBytes: Long = 16L * 1024 * 1024,
)
```

## 生命周期与并发合同

backend 构造时只保存 `ModelStore`、`ModelRef`、`LiteRtLmModelSpec` 和 engine factory，不 acquire lease、不初始化 engine。`availability()` 检查 closed、API/ABI policy 和 `ModelStore.status`; 通过后短暂 acquire/validate/close lease 以确认完整 manifest/hash，但不调用 LiteRT-LM。每次 generation 独占一组 `ModelLease → Engine → Conversation`，生成返回或失败后由同一 session 在 `NonCancellable` 中唯一负责 cleanup；下一次 generation 重新 acquire/initialize，绝不复用 conversation/KV state。availability query 与 close 共享 query join，close 不会打断正在进行的 hash/manifest 校验。

生成开始前检查 closed、busy、caller cancellation、lease manifest 和后端 availability。生成开始后保存 caller Job 与 generation id；callback adapter 以锁串行化来自任意线程的 `onMessage/onDone/onError`，按到达顺序分配本地 sequence，重复 terminal 只接受第一次；sequence 表示 adapter 交付顺序，不宣称上游 token 顺序。任何正常或可证明的取消路径进入 `NonCancellable` cleanup，先停止 callback，再调用 `cancelProcess()`（仅在生成已启动或 caller 已取消时），等待具有独立 native 证据的 `awaitTerminated()` barrier，随后按 conversation → engine 顺序关闭、清理 cache，最后释放 lease。当前仅实现 fake barrier；0.18.1 尚无满足此要求的生产实现。

前置 probe 不再接受 terminal callback 与 `isAlive=false` 组合作为 drain 证明。schema 2 把这些字段保留为原始观察，`drainProven=false`；满足单 terminal/累计 callback 也只返回 `unverified-native-drain-contract`。生产 `LiteRtLmBackend` 仍不可构造，资产齐备但合同未证实时 availability 保持 `UNAVAILABLE/UNSUPPORTED_DEVICE`。`awaitTerminated()` 是待证实的抽象合同；30 秒期限只验证可取消 fake 的失败门禁，不能中断或证明阻塞 JNI 的结束。官方源码的析构等待忽略错误且默认 10 分钟，不能直接替代此 barrier。

若终止证明缺失、失败或 barrier 违反，session 进入不可重试的 **poisoned terminal**：停止 update、关闭 admission、禁止下一次 acquire，`drain()` 与 `close()` 以不含原生文本的 `LiteRtLmError.DrainFailure` 完成，并故意不调用 conversation/engine close、不释放 ModelStore lease、不删除 cache；进程终止是此失败路径的回收边界。正常 cleanup 只允许在真实 barrier 得到验证后实现；不能通过 timeout 强行释放 native 仍可能读取的资源。

`close()` 与 caller cancellation 都不能中断 cleanup。`drain()` 完成后才允许下一次生成；下一次生成必须创建新的 conversation/KV state。关闭后的 immutable `MultimodalTextOutput` 可以被调用方继续读取，但不再允许任何 backend action。

`MultimodalGenerationClient.run()` 在 backend.generate 返回/抛错后仍会调用 backend.drain；因此 backend.drain 必须幂等：若本次 session 已完成 cleanup 则立即返回，若 cleanup 尚在进行则等待同一个 deferred。conversation/engine 只由 backend session 关闭一次，client 不直接关闭它。callback 终态、`isAlive()==false` 和 Flow collector 完成都不构成 native drain 证明；组合观察也不能消除此缺口。

### 状态与错误映射

| 条件 | availability | generate/error |
| --- | --- | --- |
| backend closed | `UNAVAILABLE/CLOSED` | `MultimodalClientError.Closed` |
| API < 26 | `UNAVAILABLE/REQUIRES_OS_VERSION` | `BackendUnavailable(backendId, REQUIRES_OS_VERSION)` |
| ABI 无 `arm64-v8a`/`x86_64` | `UNAVAILABLE/UNSUPPORTED_DEVICE` | `BackendUnavailable(backendId, UNSUPPORTED_DEVICE)` |
| ModelStore ABSENT/INVALID、缺文件或坏 hash | `REQUIRES_ASSETS/MISSING_ASSETS` | `BackendUnavailable(backendId, MISSING_ASSETS)` |
| manifest backend/capability/format/input signature 不匹配 | `REQUIRES_ASSETS/MISSING_ASSETS` | `LiteRtLmError.InvalidModel` |
| prompt/image/budget 不合法 | 不改变 availability | `InvalidInput` |
| caller cancellation | 不改变 availability | `CancellationException`，cleanup 仍完成 |
| native callback/runtime failure | 不改变 availability | `LiteRtLmError.RuntimeFailure`，不带路径/prompt/原生文本 |
| drain proof 失败 | `UNAVAILABLE/UNSUPPORTED_DEVICE`（后续查询） | `LiteRtLmError.DrainFailure`；进入 poisoned terminal，不 close native、不释放未证明的 lease |

`MultimodalModelIdentity` 使用 `modelId=manifest.modelId`、`modelRevision=manifest.upstreamRevision`、`runtimeRevision="litertlm-android:0.18.1"`。`LiteRtLmModelSpec` 必须精确匹配上述常量、manifest `backendVersion` 覆盖 `0.18.1`、许可证/归属字段，并要求 files 包含 spec 指定的模型文件、LiteRT-LM metadata、tokenizer/config 辅助文件；实际文件路径和 hash 由 manifest 约束。`EngineConfig.maxNumTokens` 固定为 512，request `maxOutputTokens` 必须在 `1..512`，并同时写入 `ConversationConfig.maxOutputToken`；不做静默 min/max 修正。`ModelInfo.from(modelPath)` 的 modalities 必须包含 vision、`maxVisionTokenBudget>0` 且 `minRuntimeVersion <= 0.18.1`，在 generation open 后再次核对，不能在未初始化的 availability 中伪造。

## 验收证据

- 静态：AAR ZIP、manifest、ABI、ELF、license/notice、POM 和依赖清单，见 `artifacts/verification/android-litertlm-aar-20261026/`。
- 前置探针：以真实 0.18.1 AAR 编译 probe，确认 `Contents.of`/`Message.user` 映射与 callback 模式；lifetime flag 只作原始观察。必须另行建立有 native 证据支持的终止 barrier，保存 API/设备原始日志；当前 schema 2 一律不认证 drain，backend 不接入生产。
- 本地：新模块 JVM/fake-engine 测试覆盖 manifest mismatch、image input、budget、availability 无初始化、callback snapshot/sequence、cancel→drain→close、late callback、reentry、close/query join、错误隐私，以及 drain proof 失败后 poisoned terminal 的 `drain/close` 错误与 lease/cache 保留；Gradle assemble/lint/consumer 编译分别记录。fake 结果只证明生命周期，不计设备推理。
- 设备：在 TECNO KM9/API35 上使用固定公开 fixture 和已授权模型资产，记录 model/runtime/input hash、backend、首 update/total、取消 drain、同进程重入、close 和清理；没有模型或运行失败时明确写 `unverified`，不以 AAR 或 fake 测试替代。
- 视频：保持 `supportsVideo=false`，直到独立抽帧、时间戳、模型输入语义和设备质量包全部通过。

## 仍需实现者完成的决定

- 可证明 native 工作及 callback 已结束的真实 barrier，以及等待失败/阻塞 close 的资源边界；当前没有有效替代协议。

- LiteRT-LM 具体模型及固定 manifest（模型格式、文件 hash、许可、设备支持矩阵）；在该资产冻结前只允许 fake/lifecycle 包。
- Android API26 基线与 TECNO 实机的最低运行条件；AAR 声明的 target SDK 缺失必须保留为未验证。
