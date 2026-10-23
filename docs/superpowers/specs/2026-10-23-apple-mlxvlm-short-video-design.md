# Bounded Apple MLXVLM short-video design

2026-10-23。输入为 `515ec07` 分发交接及[短视频可行性](../../validation/apple-mlxvlm-short-video.md)。本轮交付源码/缓存可行性与设计；以下为下一独立实现包，不表示已交付视频能力。按用户继续 roadmap 和安全本地自动推进要求作出下列实现选择；不是独立 reviewer 的 APPROVE。

## Intent and constraints

让原生 Swift consumer 用既有固定 SmolVLM2 500M/MLXVLM 理解单条有界本地合成短视频，同时保持单图 API、基础 iOS15/macOS12 和默认/Android unavailable 语义。增强推理继续只在 arm64 iOS18+ 物理设备运行。

固定 model `fa57db46815177fbdfd65cc85a2b3416a8332268`、runtime `6e338e5df49424fc0db7c6d58184d622f92ed18a`，完整 lock 不变。只用既有缓存与公开合成 fixture；不下载、不变更 upstream、不读取私人媒体、不改签名资产；只本地提交，保留现有保护文件和所有失败日志。

不包括 KMP/Demo/system picker、Android 实际推理、自然质量、binary 分发或统一性能验收。不得把路径为 mp4 的现有 image request 当成视频，也不以逐帧独立图像描述代替视频 processor。

## Approaches and decision

1. 直接 `UserInput(videos: [.url(callerURL)])` 最少代码，但没有稳定输入 snapshot、项目资源预算或可证明的解码取消 join；不采用。
2. 项目先私有复制并有界解码，然后 `.frames` 输入固定 processor；保留时间戳/帧身份证据并验证上游重新采样行为。采用此路线，兼容门槛通过后才接公开视频 API。
3. 升级 runtime、修改上游或换模型会改变已经锁定的范围；本包不采用。若路线2的 processor 时间语义不成立，记录具体门槛，另包设计适配策略。

## Admission, decoding and temporal contract

首个 profile 的数值是本项目保守选择，不是模型或设备的已测上限：本地 regular nonsymlink 文件、encoded bytes `1...32 MiB`；有限正 duration `<=5 s`；单条零起点、无 edit 的 H.264 SDR video track；编码尺寸每边 `<=1920`，总像素 `<=2,073,600`，显示 transform 必须有限、有效。拒绝不支持的容器/reference movie、HDR/其他 codec、多 video track、无帧/坏文件；音轨不送模型，不提供音频理解。

以 `O_NOFOLLOW` 打开并 `fstat` 检查 regular file，按 fd 有界复制到项目独占临时目录，AVFoundation 只读取该 snapshot。验证复制后的大小及所有 AV metadata；容器不能带外部媒体引用。不截断超限视频、不回落单图、不覆盖或删除调用方文件。源文件与复制过程并发写入不能被称为原子的源快照；复制完成后的 bytes 才是本请求输入，并按 hash 绑定证据。

native sampler 固定至多8帧、输出每边<=512，应用 preferred transform；请求时间严格小于 clip duration。记录 requested/actual CMTime、帧 hash、顺序、真实 clip duration。帧解码失败、空集合、非有限/非单调时间戳均拒绝，不能静默通过部分帧。

processor characterization 在正式接 API 前执行：固定8帧网格从0开始，明确 `.targetFrames(8)`；验证上游 `.frames` 的二次采样保留预期数量/顺序和每帧图像身份。小于8帧的合法 clip 使用实际帧数目标，禁止空帧。不得为了通过检查改写 actual timestamps 或伪造终点帧。processor prompt 的 `totalDuration` 是首末 sample span，必须与真实 clip duration分别记录；不能声称完整覆盖两次 sample 之间的所有事件。

解码有 cancellation handler 调用 `cancelAllCGImageGeneration()`，取消后等待 native iteration 完成并释放 AVAsset/frames；在加载前、逐帧、processor 前后检查 Task cancellation。同步 processor 不能抢占时必须等待返回；关闭不能提前释放输入/model lease。取消响应时间无预先 SLA。复用现有 generateTask 的 cancel+await producer 屏障，不依赖 stream 结束。

## Native public interface after characterization

保留 `MultimodalRequest`、`imageFile` 及其 initializer，不改变既有单图调用方。新增 `MultimodalVideoRequest(videoFile: URL, prompt: String, maxOutputTokens: Int = 128)`；值检查与单图一致，不在 initializer 读文件。

新增 `MultimodalVideoGenerationBackend: MultimodalGenerationBackend`，要求 `generateVideo(_:update:)`；只有显式 conform 的 backend 才支持视频。现有 `MultimodalGenerationClient` 新增 `generate(_ request: MultimodalVideoRequest, backend:update:)` overload，与图片共用一个 active operation、query计数、update序列、cancel/drain/close；不得建立互不知情的第二 client/session。选择的 backend 不 conform 时返回 `MultimodalClientError.invalidInput("selected backend does not support video")`，不读输入、不回落其他 backend。现有 availability 继续表示 backend 总体状态，文档明确它不承诺视频支持。

Apple backend 的 image/video 入口共用一个 session/model lease；内部 enum 区分 request，video runtime 将 snapshot 的帧交给 `UserInput(videos: [.frames(...)])`。prompt UTF8<=8192 B，output1...512，temperature0；每请求重新加载，无聊天历史。输入 snapshot 由 runtime 整个 decode/prepare/generation/join 生命周期持有，退出时仅清理自身目录。

Android 对等请求/client 与 KMP 类型化 video input 另包；当前 image bridge 保留。该设计只保证现有源调用兼容，新增基础 API 后必须重建相关分发产物，不宣称旧 XCFramework 已包含新 API。

## Validation and stop condition

每 Task 先真实 RED 再最小 GREEN，分别作者范围复审；当前会话单人执行，不派子代理。native sampler 用小型合成 H.264 fixture 做真实 AVFoundation 解码，包含 malformed、oversize、symlink、无 video、多轨、取消/清理与源变化测试。processor characterization 必须执行固定代码并检查帧/时间身份，源码字符串检查不足。

之后用注入 backend 测公共 client 的 image/video 互斥、legacy拒绝无fallback、取消/drain/close/query及旧单图回归，再做独立 consumer 的固定模型视频成功、首update取消、同PID重入、close/lease清理。只用既有签名/本项目 App；精确记录 source commit、model/runtime/input hash、PID、requested/actual timestamps 与原始结果。unsigned build不等于推理通过；synthetic描述不等于自然时序质量。

只有上述实现和设备 gates 分别有证据，才报告原生短视频已通过本轮范围；不关闭P9或提升5/12。如果 characterization 未通过，则不接 public API，不将 sampler 测试当成视频推理成功，保留具体失败与下一独立设计入口。
