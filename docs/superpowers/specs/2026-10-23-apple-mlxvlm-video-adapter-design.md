# Fixed SmolVLM2 sampled-video adapter

2026-10-23，基线 `eb1287d`。用户要求先独立设计真实帧/时间语义适配，再按结果单人逐 Task 实施；明确自动继续本地安全步骤。本设计为作者选择和自审，不代表独立 APPROVE。

## Intent and boundary

解决内部 snapshot 的真实非均匀时间戳在固定 MLXVLM `.frames` 路径被二次采样丢帧的问题。只增加实验性内部输入准备器，输出同一固定模型使用的 `LMInput`；不接公共 API/session/Demo，不开始视频权重推理。原 Task1 仍有 profile/iOS decode 缺口，Task2/3 继续暂停。

固定 model `fa57db46815177fbdfd65cc85a2b3416a8332268`、runtime `6e338e5df49424fc0db7c6d58184d622f92ed18a` 和 lock SHA256 `e67e985970770991f1eb2384ca9e9b6267fb8a8f1569051be5cfb2175f0ef858`。不增加依赖、下载、修改上游 checkout、timestamps、假帧、模型或签名；保护两原始脏文件；只本地提交，所有失败日志保留。

## Evidence and approaches

固定上游 `Libraries/MLXVLM/MediaProcessing.swift:584–652` 从首末帧推导 sample span，以网格循环只向前消费帧。29FPS 实测丢 index6，原断言/exit65保留于前包。`Models/SmolVLM2.swift:371–455` 的视频分支在采样后，逐帧执行公开的 CIImage sRGB→Lanczos512→normalize，再拼接 NHWC pixels、THW 和视频 prompt。

1. 继续调 `.targetFrames`/FPS 无法对任意非均匀实际帧保证一一对应；不采用。
2. **采用项目内部 sampled-video adapter**：只复用公开的固定图像运算与消息生成器，在项目侧完成这段有界视频输入装配，不调用 `asProcessedSequence`。保留固定视频格式与每帧64个 image token；维护成本由固定配置校验、逐帧 pixel 对照和真实 tokenizer 对照约束。
3. 修改上游、换 runtime、改时间戳/加终点帧违反本包边界；不采用。

选择由作者在用户授权的本地适配范围内作出。它不是上游修复；原 processor 仍丢帧，既有 characterization 不改为“通过”。

## Internal interface and semantics

`MLXVLMVideoInputAdapter.prepare(frames: [MLXVLMVideoSnapshot.Frame], clipDuration: CMTime, prompt: String, configuration: SmolVLMProcessorConfiguration, tokenizer: any MLXLMCommon.Tokenizer) async throws -> Prepared`。

`Prepared` 含 `input: LMInput`、原样的 `timestamps: [CMTime]`、`clipDuration` 和 `sampledSpan`。frames 必须1...8，每边1...512；requested/actual time numeric、有限、epoch0、非负且小于 clip duration，actual严格递增。clip duration有限且0<d<=5s。单帧sampledSpan为0，第一帧可有实际非零时间；绝不平移/伪造时间戳。顺序不排序、不丢弃、不复制帧。

只接受已固定配置的mean/std=[0.5,0.5,0.5]、size2048、maxImageSize512、imageSequenceLength64、videoSampling fps1/maxFrames64；错配fail closed而非猜测。prompt非空且<=8192 UTF8 bytes；拒绝用户注入三个媒体控制标签，保证 frame/token 一一对应。

视频 block 与固定 processor 的文字、ceil秒级H:MM:SS和媒体标签一致；duration使用**真实clip duration**，而非采样首末span。原始CMTime精度由结果元数据保留；不声称模型prompt有亚秒精度或采样覆盖完整事件。先把block+用户prompt放入 `SmolVLM2MessageGenerator` 的单视频text，再用同一固定tokenizer/template编码；避免在rendered assistant文本后附加内容。默认system文字沿用固定版本。视频帧只走全局512视频路径，不走单图tiles。

逐帧使用公开 `toSRGB()/resampled(.lanczos)/normalized/asMLXArray`；concat后NHWC，frame index对应THW。取消在入口、template之后、逐帧前后及返回前检查；同步CI/MLX操作不可抢占，不承诺取消SLA或GPU完成屏障。没有stream/后台producer；本包不释放模型lease，不接generation。

## Tasks and evidence gates

1. ByteTokenizer隔离tokenization，真实snapshot24/29FPS与真实CI/MLX处理；先缺失接口RED，再逐帧规范化pixels对照、全部timestamps/order/count、THW、duration/span、1帧/非法时间/预算/错配config/取消GREEN。已知上游7帧测试保持。
2. 将既有LocalTokenizerLoader编译范围扩到host（不改iOS语义），显式环境变量指向既有hash验证缓存，测试真实固定tokenizer/template：24FPS完整token/pixels对照上游；29FPS适配器保留8帧/512 image tokens、上游仍7帧；不加载权重。未提供缓存时该项明确skip，不以skip称完成。
3. 完整增强host、tools（显式Ruby3.4.11）、新独立consumer unsigned generic iOS编译；固定缓存和全部3项离线flags；验证lock、checkout和保护文件。作者复审、更新validation/roadmap/handoff与本地提交。source build不等于binary、真机或质量证据。

## Remaining gates

reference-container/HDR/offset/短于8样本等snapshot profile、iOS实际decode/cancel、实际模型视频生成/取消/close、自然质量、GPU/energy、最低系统、Android真后端、物理触摸/systempicker、旧Video UI/P6实录和独立review全部继续保留。binary archive既有exit65没有条件变化，不重跑。
