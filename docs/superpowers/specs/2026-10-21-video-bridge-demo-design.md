# KMP Video Bridge / Demo Design

日期：2026-10-21。基线：`ae2becd`，`feat/sdk-foundation`。

## 目标与范围

将已实现的 Video SDK 接入 KMP 和 Demo，提供后端查询、严格 Default/Explicit 选择、文件输入、逐帧进度、取消、输出持有及释放。共享代码不接触文件路径、URL、原生 AVFoundation 类型。成功标准为公共契约、双端 adapter、共享状态机、宿主和独立生成工程均有可复核证据；设备成功与本地测试分别记录。

保持 iOS 15 / Android API 26 基础下限；AppleVideo 仍只在非模拟器 iOS26+ 且模型 ready 时运行。固定 320×180、24fps、1…48帧、BT.709 SDR、4×、HEVC/MOV 和现有 AAC preserve 限制均不扩大。Android 无真实后端时明确 unavailable。P7 质量与最低系统验收不因此关闭。

## 方案

采用与 Speech 平行的独立 VideoBridge：复用 BridgeTask/BridgeAvailability，避免把视频扩展进语音接口。相比直接在 Compose 调用原生 SDK，此方案保留隔离、可测试生命周期和 Swift ABI；相比统一所有媒体任务的新抽象，接口更小且不会改动已验证 Speech。无需新依赖。

## 公共契约

`VideoBridgeSelection.Default/Explicit(backendId)`，`VideoBridgeAudioPolicy.PRESERVE/DISCARD`。
`VideoBridgeRequest(inputReference, scaleFactor=4, audioPolicy=PRESERVE)`。reference 非空、scale>=2；上限由实际后端 admission 决定。
`VideoBridgeBackend(backendId, availability, supportedScaleFactors, supportedAudioPolicies)`。
`VideoBridgeProgress(completedFrames, totalFrames?)` 与原生非负/有界约束一致。
`VideoBridgeOutput(reference, backendId, width, height, frameCount, durationSeconds, audioTrackCount)`；reference 非空，元数据严格有效。

`VideoBridge.queryBackends(callback): BridgeTask`、`process(request, selection, callback): BridgeTask`、`releaseOutput(reference, callback)`、`close(callback)`。process callback 有 progress/result/failure/cancelled；release/close callback 分别有 released/closed 与 failure。回调均在宿主 UI dispatcher，允许同步 terminal。稳定 failure code：CLOSED、BUSY、INVALID_INPUT、UNSUPPORTED_SCALE_FACTOR、UNSUPPORTED_AUDIO_POLICY、BACKEND_NOT_REGISTERED、BACKEND_UNAVAILABLE、PLATFORM；PLATFORM 仅有稳定 domain/code，无异常文本或路径。UnavailableVideoBridge 永不伪造结果。

## 原生所有权与生命周期

输入 provider 根据 opaque reference acquire 一个原生 lease，持有至 SDK operation/drain 完成；选择变化不使活动输入失效。输出由 adapter 建立独立 opaque token 表，只有终态成功交付后归调用者所有；取消/关闭期间的晚到 output 必须释放。每个 adapter 一个处理槽，取消期间保持 busy，迟到 progress/result 不得改变终态。

close 立即封闭 admission，cancel/join 所有 query/process worker，再关闭拥有的 client；重复 close 共享终态。已交付输出跨 close 有效；releaseOutput 在 close 后仍工作。释放完成前不移除 token，失败可重试；重复成功释放幂等。宿主仅通过原生 resolver 解析输出供播放/分享，路径不进入共享状态或日志。

## Demo

独立 VideoViewModel / VideoScreen，后端列表与显式选择、文件选择、preserve/discard、进度、取消、结果元数据和清理按钮。generation 屏蔽过期 query/progress，cancel 等 terminal；离页/后台取消，不自动重启；结果替换/离页显式 release，close 不隐式撤销交付输出。保留已有 iOS 入口，新增含 VideoBridge/VideoActions 的入口。iOS 使用独立生成工程，不改受保护 pbxproj。picker 只在用户主动选择时读文件；取消保持已有输入。

## 验证与边界

每个 Task 测试先 RED 再 GREEN，范围审查后本地提交。公共 Kotlin tests + 实际 Swift framework ABI；Android 使用真实 VideoProcessingClient 与可控 backend 验证 selection/drain/close/output；iOS fixture 验证 input/output lease 和回调。共享 VM 验证同步终态、取消、迟到回调、输出替换/离页释放。生成器/host 检查与消费者裁剪独立记录；设备使用已有签名和固定公开 fixture，不能以 simulator/unavailable 代替实际超分。

禁止 push、发布、模型/资源下载、自动录音、读取私人媒体、修改签名资产。两个保护文件不覆盖、不暂存、不提交。既有 foundation/AppleVideo/audio 独立审查 pending 保留，不冒称 APPROVE。native export 中途取消仍是未验证项。

执行授权来自 roadmap“逐项实施、复审和提交后自动进入下一项”及本线程 AGENTS 自动推进约定；书面设计/计划自审不声称人工逐字批准。
