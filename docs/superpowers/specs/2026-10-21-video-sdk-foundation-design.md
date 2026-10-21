# 视频超分 SDK 基础契约与生命周期

日期：2026-10-21。基线 `36567f2`，foundation 工作树。根据用户持续推进 roadmap 的授权形成设计，用户可随时修订；不声称用户已逐字审阅本文件。

## 目标与拆包

将 P7 已验证的单独探针接入可裁剪 SDK。先建立 Apple/Android 一致的能力级多后端入口和输出所有权，再另包接入 Apple VideoToolbox 视频处理器、KMP/Demo，最后补 Android 选定模型与设备质量。当前包交付可用的后端扩展/选择与生命周期库，不提供或假装提供视频推理算法，不关闭 P7。

沿用 `superpowers:brainstorming` 的架构设计路径。已有 roadmap 明确要求逐包自动实施，不重复请求本地可逆工作的继续许可；此授权不代替独立审查、模型许可、资源下载或真实质量验收。

选择独立 `ODAIKitVideo` / `android:video`，仅依赖 Core 和已有协程库。另一方案是在 Core 中加入视频/AVFoundation 类型，会污染所有消费者；直接把探针搬入 Demo 则无法供 SDK 调用方复用，均不采用。Apple VideoToolbox 后端是下一包，届时引用 SDK/官方资料并保留 iOS26 availability，基础模块不引用它。

## 请求、结果与输出所有权

Swift `VideoProcessingRequest(inputFile: URL, scaleFactor: Int, audioPolicy: VideoAudioPolicy = .preserve) throws`；Android 对应 `File`。只接受本地输入与至少2倍的整数倍率；文件实际可读性、编解码、色彩、音轨和尺寸由具体后端在 admission 检查。宿主持有输入到 `process` 返回或取消后的 drain 完成，不改写或删除输入。

`VideoAudioPolicy` 为 preserve/discard；默认 preserve。后端不支持请求的音轨策略必须明确拒绝，禁止默默丢音轨或改用其他后端。静态 `supportedScaleFactors`、`supportedAudioPolicies` 与带 reason/minOS/offline 的 availability 独立保留；unavailable 不丢失结构元数据。

`VideoOutputInfo(width,height,frameCount,durationSeconds,audioTrackCount)` 验证正尺寸/帧数、有限正时长和非负音轨数。`VideoOutputLease` 提供 `file: URL`、`info`、异步幂等 `release()`。后端在自己拥有的唯一临时工作目录生成 MOV，不能覆盖输入或既有输出；成功结果转交调用方持有 lease，调用方不再使用后显式释放。client.close 不删除已经交付的结果。后续 Demo host 负责输入 lease、结果展示/清理，显式导出另行设计。

选择 output lease 而不是让每个后端直接写用户指定的最终路径，使取消后的晚到成功有明确清理责任，并避免在本包发明跨平台覆盖/原子替换语义。API 不隐式写相册或共享目录。后端持有的处理中间文件必须在 process/drain 完成前清理。

`VideoProcessingProgress(completedFrames,totalFrames?)` 限定非负、已知总数为正且完成数不超过总数。进度不等于成功；回调在 client 协调上下文交付，不保证每帧都交付，不携带文件路径。终态/取消/关闭后的迟到进度不再交付给调用方。

## 多后端与生命周期

`VideoSuperResolutionBackend` 提供 backendId、静态支持元数据、availability、process(request,progress) -> VideoOutputLease、drain、close。`VideoProcessingClient` 注册一个默认 backend ID，提供 default/explicit、listBackends、process、drain、close。注册拒绝空/重复 ID 和未注册默认 ID；列表按 ID 排序。Explicit missing/unavailable/不支持倍率或音轨策略一律不 fallback；查询不启动处理或占用处理槽。

同一个 client 最多一个进行中/等待可用性/排空的任务；查询独立。取消请求立即传递给处理 worker，但终态必须等待底层 process 返回与 drain。忽略取消而晚到的成功结果必须 release 后才能抛 CancellationError，不能泄漏 output lease；释放错误作为明确 cleanup failure，不伪造成功。关闭立即拒绝新 query/process，取消并等待 active worker，join 已启动查询，最后只关闭各 backend 一次；并发 close 等待同一个完成结果。旧进度和旧任务不得影响下一轮。

客户端不添加模型下载、网络回退、系统权限请求或日志；具体后端输出自然路径/帧内容不进入测试日志。新模块不依赖 Face/Identity/Speech/KMP/AVFoundation；Apple 基础下限 iOS15/macOS12、Android API26 不变。无新第三方依赖。

## 验证与后续入口

每项先 RED 再实现：类型校验、不可用 metadata、严格选择、native start/availability 挂起时 cancel/close、drain 中 BUSY、晚到 lease 清理/释放失败、并发 close、进度 generation 隔离与已交付结果跨 close 保留。可控 backend 只替代未来外部编解码/模型操作，真实 facade 协调逻辑参与测试。

Apple Swift 全套和 Android video/JVM 测试、依赖隔离与本地分发/消费者验证分别记录。独立 reviewer 服务不可用时记录 pending，不冒称 APPROVE、不 push。本包不代表真实视频处理、音轨保留、质量/内存/离线、Android 模型、最低系统或 SDK/Demo 完整接入已验收。完成后自动进入 Apple VideoToolbox 后端包，继续沿用 P7 的固定无私人内容 fixture。
