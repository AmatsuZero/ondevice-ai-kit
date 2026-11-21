# Android 播放时视频超分辨率

2026-11-20 · 用户已确认路线 A 及首轮验收标准。见[首轮实现计划](../plans/2026-11-20-android-realtime-video-sr.md)。

用户打开视频后即可播放，在播放中切换原画与 2× 增强。第一档用 Android 手机播放有声 1080p/30 SDR 视频，复用 iOS 已测的 CuNNy 模型与同一片源。目标是查明哪些系统、GPU 和输入能持续按时显示增强帧，以及观看者在运动中能看见什么变化。

## 路线取舍

| 路线 | 能优先回答的问题 | 代价与决定 |
|---|---|---|
| A．应用内 OpenGL ES 先行，随后对照 Google | 无 GMS 的手机能否增强，哪些机型能持续播放。同一版 CuNNy 还可与 iOS 对像素 | 需维护 shader、颜色转换和逐帧呈现。用户选定 A |
| B．Google Play services 增强先行，随后补应用内 GPU | 支持设备上，官方模型的观感和速度如何 | Beta 服务、按需模型和硬件支持限制了首轮覆盖。保留为 A 的第二阶段对照 |

OpenGL ES 和 Vulkan 都能承载自有算法。第一阶段用 GLES 3.1 计算着色器跑 CuNNy 1×8 BOX，2× 单帧亮度超分。将来若某组 GPU 的耗时或驱动问题可复现，再评估 Vulkan 能否改善。性能或能力不足时，用户看到的是正常播放的原画。现有 LiteRT 1.4.2 / ESRGAN 4× 继续提供离线文件参考；已测 TECNO KM9/API 35 和 API 36 模拟器，API 26 是该模块声明的下限。飞鸟、瀑布两组同机对照的平均 PSNR/SSIM 均低于双线性。离线 4× 与播放 2× 分开列，不互推覆盖率或效果。

手机上的官方对照是 [Google Play services Media Enhancement 的 Surface 视频模式](https://developer.android.com/media/ai-enhancement/surface-mode-lifecycle)。[官方接入示例](https://developer.android.com/media/ai-enhancement/get-started?hl=en) 使用 16.0.0-beta09；[官方适用条件](https://developer.android.google.cn/media/ai-enhancement/bitmap-mode-lifecycle?hl=en) 列出 Android 11/API 30 起。仍须分别检查设备支持、所选输入能否启用 Upscale，以及按需模块是否就绪。Android 16/API 36 也有[面向 Android TV 的画质框架](https://developer.android.com/training/tv/mqf)，包含[自动超分参数](https://developer.android.com/reference/android/media/quality/MediaQualityContract.PictureQuality#PARAMETER_AUTO_SUPER_RESOLUTION_ENABLED)。这类电视显示设置不能作为手机播放器的统一后端。

## 用户体验和播放器接入

- 本仓库增加可独立运行的 Android 播放 Demo，对齐 iOS 独立播放器 Demo 的交付形式。首版在 Demo 使用 MediaExtractor 读取本地轨道和 PTS，两个 MediaCodec 分别解码 H.264 和 AAC。AudioTrack 播放 PCM，使用音频已播放的位置和对应媒体时间建立主时钟，按该时钟安排视频显示。原生渲染层持有视频解码 Surface、纹理和最终 EGL 窗口。暂停时冻结时钟，seek 时刷新解码器、音频队列和时钟锚点，从同步帧解码并丢弃目标 PTS 之前的输出。这个选择让测试直接关联解码帧、显示帧和声音进度。现有 Demo 的 MP4 导出及外部播放入口服务于离线任务。
- 用户可在同一次播放中选自动、CuNNy 或原画，并能暂停后对同一媒体 PTS 切换原画和增强。自动模式只在片源和本机能力满足时尝试 CuNNy。显式模式展示可用状态及具体不可用原因；运行中的单帧超时仍回退原画。保持播放位置和声音连续。
- 复用 commonMain 的 `RealtimeVideoSR` 和 `RealtimeSRHost`。KMP 管开关和状态，模式选择绑定在 Android 播放器。UI 分别显示用户选择和最近一次确认上屏的 `ORIGINAL` / `CUNNY`；未来 Google 走 `SYSTEM` 并标清提供方。切流、seek、开关或替换 Surface 后用新的播放代次，先显示“等待呈现”。延迟回来的旧帧和旧呈现回报不能覆盖新状态。主动关闭后继续播放原画；只有确认显示原画且该帧请求过增强，才将 `fellBackToOriginal` 置为 true。新代次开始时 `presentedBackend=null`、`fellBackToOriginal=false`。同代有结果异步待查时保留最近确认的状态，按显示顺序更新；只有提交证据时在 Demo 单列。
- 首版涵盖本地、单路、有声 H.264、BT.709 SDR、固定 30 fps 和 2×。HDR、DRM、直播、4K 输入、正式 App 接入与发布另行评估。遇到片源超出此档，沿用播放器原有的可播放能力并给出增强不可用原因。

## 从解码到屏幕

1. 解码帧进入原生 SurfaceTexture。以媒体 PTS 关联解码输出与实际取得的帧；记录纹理时间戳、裁剪、方向和颜色范围。先通过片元着色器把当前外部 OES 纹理采样到应用持有的普通二维纹理，保留原画副本，供计算 shader 和呈现独立读取。OES 采样可能已经转为 RGB，在这次转换中用色阶/色条校准 BT.709 全幅与限幅，提取亮度和色度，避免再次做范围扩展。解码提供的时间戳若无法可靠绑定媒体 PTS，该设备只记录可验证的阶段。
2. 保留当前源帧，CuNNy 运行三段 shader，1080p 输入生成 3840×2160 亮度纹理，合成源色度，按显示区比例输出。实际屏幕尺寸另记。按 EGL 上下文实测 GLES 版本、纹理尺寸、所用格式及写入能力，完成编译、试分配及已知像素测试。申请失败或上下文丢失时走原画。CuNNy 的移植保留源文件头、原许可证、权重来源、变更说明，首轮交付范围为仓内实验源码与本地设备上安装的 Demo；使用实验源文件头指明的 LGPL-3.0-or-later，将原许可、固定修订和移植改动与 shader 一起留存。若后续向外分发 APK/AAR 或纳入生产，单独确认对应源码的提供方式和适用的分发条件。
3. 原画呈现可独立于 SR 进行。提交 SR 前保留稳定的当前帧纹理与 PTS，最多一个增强作业在途。同步对象隔离读写与纹理复用，呈现线程按音频/媒体时钟在显示截止前查询结果；只取帧标识完全一致的就绪结果。过时则用同一帧原画，回收迟到结果。预算由实测刷新节奏预留合色和提交时间，队列繁忙时跳过本帧增强。若共用 GPU 导致原画也错过时限，记录真实漏显并暂停增强，让后续帧恢复正常播放。
4. 无合适计算能力时仍须能用播放器原生输出原画。前后台切换、seek、切片源、Surface 销毁/重建及关闭都要使旧任务失效，释放属于该代的缓冲和同步对象。恢复时重新检查尺寸、颜色与能力。

## 画质、性能和覆盖如何验收

**片源和算法。** iOS 播放器 `7d40dfe` 的 1080p `fixture.mp4` SHA-256 为 `df677798a8604a7a6289fcf50a0fef3844a047631e0041ae7d5563c55f3746ec`，该 30 秒片段有 900 个视频帧、带 AAC。若原素材无法取得，另列新素材，双端使用相同文件重新建立对照。记录实际 CuNNy 权重/着色器修订，参考 iOS 固定的上游 `906031bb00c15dd6a6bbbaa21c0eb0b724ca8437`。独立探针和 UHPlayerSDK Demo 各有自己的取帧起点及统计分母，对照时注明测试入口。

本机实物位于 `~/Downloads/UHPlayer-SR-Compare-20261120/实时长测片源/fixture.mp4`，已重算 SHA-256，与 iOS 对照包记录完全相同。该位置在两个仓库之外；其他机器按摘要指定本地输入。容器轨道为 1920×1080 H.264、TV range、BT.709、30/1 fps、900 帧和 AAC，时长 30 秒。可直接将这一文件作为 Android 的输入；iOS 解码侧记录的 full range 描述的是解码输出，Android 仍按自己实际取到的纹理校准。

**算法正确性与观看效果。** 对已知亮度图、边界像素和同一解码帧的亮度裁剪做独立 CPU 对照，使用 Android 实际送进模型的样本。首版提议用 8-bit 输出最大差 ≤1 灰阶校验数值，并检查非平坦样本确实区别于双线性。在最终不透明输出上核验 RGB 有限、alpha 为 1，并用色阶与色条分辨范围错误、黑帧和模型误差。在画质场次单独读回缓冲，做有参考的降采样重建和原画/双线性/CuNNy 同位置对照；实播再用可复用的自然素材看运动纹理、压缩边缘、细字、暗部和闪烁。保存可回放的匿名 A/B、素材摘要及场景评价。盲看可选 A、B、无明显差异；效果按场景报告，未做观看评价时标为待评。

**实际呈现。** 预先按片源的显示顺序给每个源帧编号（包含解码器可能未输出的帧），用「播放代次、循环序号、源帧序号」唯一标识一次应有呈现；媒体 PTS 用于调度和校验，重复 PTS 的不同帧保留各自序号。让解码释放、纹理、增强结果、回退决定与最终窗口缓冲都携带这个标识。在支持的 EGL 窗口上启用 `EGL_ANDROID_get_frame_timestamps`，查询是否支持 `EGL_DISPLAY_PRESENT_TIME_ANDROID`，把 swap 前取得的 frame ID 与稍后返回的有效显示时间配对。记录单调时钟域及各段起止。PENDING 异步回收，收尾时仍未返回、无效或丢失的另计。没有可用呈现时间时可显示“已提交／呈现未验证”，该设备本次播放的公共 `presentedBackend` 保持空，端到端结果记为未知。这类设备可完成播放、画质、GPU 计时和长时功能观察；表中的 30 分钟实时增强验收列固定为“未验证”，不得计为达标机型。真正的 GPU 计时用可用的计时查询并排除 disjoint 样本，缺失时仅报告已测的 CPU 提交至 fence 完成时长。保存具名的计时起点，使数据可复算。

将 MediaCodec 输出的 BufferInfo.presentationTimeUs 和本次释放记录关联；向解码 Surface 逐帧交付时仅保持一个待取得的输出。渲染层取得纹理后确认代次、时间戳和序列，才释放下一个待取得输出，遇到合并、跳帧或重复回调明确记录。最终 EGL 窗口按音频时钟调度，独立于解码纹理的取得。有多种可能对应关系时将受影响的帧列为未确认，不凭相同 PTS 猜测。用跳帧和重复 PTS 的控制场景验证该绑定。每轮保留原始帧记录、缺失记录及恒等式（按时增强 + 按时原画 + 迟到显示 + 未确认显示 = 应有帧数）。GPU 时长缺测时列覆盖率，无法证明该门槛的行标“未验证”。

**验收门槛（本稿建议）。** 每台设备先测原画基线和 30 秒增强短测，交换顺序观察波动。短测通过再用同一片和音轨连续播放 30 分钟，按循环序号和源帧序号逐项统计，保留冷启动和接缝，按全程和每分钟给出结果。计算增强交付率时，主动关闭、seek、生命周期和故障注入用各自的独立场次。正常增强场用全部应显示源帧作分母。“按时”须同时满足取帧至实际显示 ≤33⅓ ms，且赶上该帧的播放时隙：按音频主时钟换算的目标显示刷新点，到下一源帧的目标刷新点前截止。这样，因解码落后而很晚才取得的帧不会因处理耗时较短被误记为按时。

| 指标 | 判定方法 |
|---|---|
| SR 计算 P95 | ≤25 ms。列出已启动并完成的全部 SR 作业及无有效计时的数量，包括最终回退的作业 |
| 可确认上屏帧的取帧至显示 P95 | ≤33⅓ ms。起点为应用取得该解码帧前的单调时钟采样，终点为实际呈现；另列原画基线及增强/回退各自的分布 |
| 漏显或超时率 | <5%。分子为未确认上屏的源帧，加已显示但超出上述任一时限的源帧；同帧只计一次 |
| 按时增强上屏率 | >95%。分子只计确认显示 CuNNy 且按时的唯一源帧。按时显示原画算作播放成功；所有已显示的原画回退另列次数，迟到的原画仍只占恒等式中的“迟到显示”一项 |

同时报告连续漏显、帧间隔、回退原因与恢复、音频/视频播放位置漂移、可取得的内存和热状态。功耗或声画物理同步有独立测量时才给实测值。功能验收另含主动关闭、强制单帧回退后恢复、切模式不重新起播、seek/切源后无旧帧、后台返回和资源释放。

**覆盖表。** 以 Android 8/API 26 为兼容目标，从已有 TECNO KM9/API 35 真机开始，加入能借到的 API 26/27 低版本真机及 Adreno、Mali 等不同 GPU/驱动样本。模拟器用于兼容和控制流。每行列 OS/API、机型/SoC/GPU/驱动、GLES 能力与显示计时能力、GMS 状态、片源规格、实际后端、画质、短测/长测和证据位置；无设备的格子标“未测”。一个机型的结果只覆盖该行。未达到增强门槛的机型如实给出最先失败的条件及原画表现。

## 交付及后续对照

首轮交付独立 Android Demo、Android 的 KMP 控制适配、可复算的逐帧报告、同帧画质样本和版本/机型矩阵。Wiki 的 Android 实时播放小节按上述矩阵记录成功、失败及未测，不与已有 LiteRT 导出数据混列。具体目录和渲染器内部组织由实现者确定。优先采用 Android 现有平台 API，生产接入时复用已验收的渲染与控制接口。

第二阶段在配有 GMS 的候选机型上验证 Media Enhancement API 的 **Upscale**，记录实际能力、模型就绪状态、放大倍率、离线重开及支持的输入。在相同输入、倍率、显示大小、原画和计时口径下与应用内路线对照；条件不同则并列注明。首次未安装或设备返回 unsupported 时直接播放原画或由自动模式选择已验证的 GLES。Vulkan 的启动条件是有具体机型、可复现的 GLES 瓶颈，以及同一套像素和实际呈现对照。首轮设备验收与这两项研究可独立完成。

依据见 [iOS 播放器交接](../../validation/ios-realtime-sr-demo-handoff.md)、[KMP 播放状态](../../../kmp-binding/src/commonMain/kotlin/io/ondeviceai/binding/RealtimeVideoSR.kt)、[Android 离线验证](../../validation/android-video-sr-demo.md)、[飞鸟](../../validation/f11-android-sr-controlled-20261106.md)及[瀑布](../../validation/f11-android-sr-waterfall-20261106.md)对照。平台参考见 [EGL 帧时间戳规范](https://registry.khronos.org/EGL/extensions/ANDROID/EGL_ANDROID_get_frame_timestamps.txt)、[Android OpenGL ES](https://developer.android.com/develop/ui/views/graphics/opengl/about-opengl) 和 [Google Media Enhancement](https://developer.android.com/media/ai-enhancement/overview)。
