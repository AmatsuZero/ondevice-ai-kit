# Google Media Enhancement 同片对照 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在具备 Google Play 服务的 Android 手机上查明官方 Surface 视频路径能否对本片启用 Upscale；只有能验证时才比较画质与播放表现。

**Architecture:** 复用[首轮 Demo 与报告合同](2026-11-20-android-realtime-video-sr.md)，用实验构建属性引入 GMS 适配。保留无 GMS 的 CuNNy/原画路径；提供方不暴露某项能力或时间时，报告保留该项未知。

**Tech Stack:** Android、Google Play services Media Enhancement、既有 Kotlin 播放器、EGL、Python。实施时从[官方接入资料](https://developer.android.com/media/ai-enhancement/get-started)核实实际版本、[Surface 视频接口](https://developer.android.com/media/ai-enhancement/surface-mode-lifecycle)和精确的 Upscale 能力，再锁定实验专用版本。

**Spec:** [已确认的设计：交付及后续对照](../specs/2026-11-20-android-realtime-video-sr-design.md)。

## Global Constraints

- 首轮 APK 仍以 API 26 为目标且不依赖 GMS。官方路线从 Android 11/API 30 的候选机开始，验证设备、模块、输入及具体效果，实测覆盖只填该机型。需要更高 minSdk 时仅提高实验 APK，不能提高首轮门槛。
- 只测 Surface 视频的 Upscale。有 Surface 会话、成功初始化、Bitmap 照片放大、去模糊或色调映射，都不能代替这项证据。
- 同固定片、同显示大小、同倍率、同原画和同计时口径才可直接排名；条件不同并列描述。沿用 30 秒/30 分钟及 >95% 等首轮门槛，不用整个不透明会话的耗时替代内部 GPU 运算时间。
- 不把 SDK 放入正式 App/AAR，不发布。固定素材、证据、隔离设备和 helper 约束见首轮计划。

## Review Focus

1. 系统版本够，但设备没有 GMS 或硬件能力：直接继续原画（任务 G1）。
2. 模块未安装、下载失败或离线重开：播放不等待下载（G1）。
3. 设备只宣告一般增强，不能证明视频 Upscale：单列“能力未证实”，不能贴超分标识（G1）。
4. 服务返回帧晚于 seek/换 Surface：不能代替新帧，不能改写最近显示状态（G2）。
5. 实际倍率、显示证据或 GPU 计时不可得：不可比较项单列，不能判实时达标（G2）。

---

### G1：独立构建检查“指定片源能否使用 Upscale”

**Files:** 修改 `examples/android-realtime-sr/build.gradle.kts`；新建该模块 `src/googleSr/kotlin/io/ondeviceai/realtimesr/GoogleSurfaceSession.kt`、`src/noGoogleSr/kotlin/io/ondeviceai/realtimesr/GoogleSurfaceSession.kt`、`src/main/kotlin/io/ondeviceai/realtimesr/GoogleSrAvailability.kt` 和 `src/test/kotlin/io/ondeviceai/realtimesr/GoogleSrAvailabilityTest.kt`；扩展首轮运行驱动。

**Interfaces:** 两种构建的共同入口 `checkVideoUpscale(width: Int, height: Int): GoogleSrAvailability` 读取异步探测快照（主线程不阻塞）；枚举 `CHECKING/MISSING_SERVICES/MODULE_NOT_READY/UNSUPPORTED/UNVERIFIED/READY`。证据注明来源、不把一般 session success 映射成 READY。

- [ ] 写 `GoogleSrAvailabilityTest.surfaceWithoutVideoUpscale`：断言“可建 Surface 会话、只支持照片”不返回 READY；下载失败和无 GMS 分别为 MODULE_NOT_READY、MISSING_SERVICES。运行首轮 `srtest GoogleSrAvailabilityTest` 先见缺少策略失败。
- [ ] 用 `-PenableGoogleSr=true` 只为实验构建选择源码集和 SDK，并设不同 applicationId。以设计记录的示例坐标 `com.google.android.gms:play-services-media-effect-enhancement:16.0.0-beta09` 为起点，核实可解析版本、制品签名/API 文档、视频管线可请求/确认的效果，以及输入/输出限制。若公开接口无法区分“视频 Upscale 已启用”与其他增强，保留证据、交付此负结果，停止 G2 的效果排名。
- [ ] 单测通过后分别检查默认和显式带属性构建的 `debugRuntimeClasspath`（`srgradle dependencies --configuration debugRuntimeClasspath`，及同命令附加实验属性）；只有后者出现上述 SDK。驱动遇到 google 系列 case 时构建带属性的独立包，按其新包名安装、校验摘要并取回结果。`srdevice --case google-capability` 区分冷启动未装、模块装好、离线重开及无 GMS，记录指定片源与实际服务版本。模块不就绪立即播放原画或已验收的 GLES；不清普通 App 数据，不人为卸载系统服务。
- [ ] 提交：`feat: probe Google video upscale capability in a separate demo build`。

### G2：同片出图、呈现记录及比较

**Files:** 扩展 GoogleSurfaceSession、`PlayerActivity.kt`、`AndroidRealtimeSRHost.kt`、`tools/probes/android_realtime_sr_device.py`、`tools/probes/android_realtime_sr_report.py` 和其测试；更新 `docs/validation/android-realtime-sr-playback.md`。

**Interfaces:** `openVideo(input: Surface, output: Surface, generation: Long)` 使用 G1 验证过的配置，`close()` 释放会话。窗口收到可追踪的 Google 输出才可报告 `SYSTEM`，UI 标注 Google Play services。驱动增加 `google-quality`、`google-short`、`google-soak`。

- [ ] 给报告测试加入 `test_google_output_must_be_attributed`：旧代次帧、单纯提交、未知倍率不能产生已验证同口径结果；缺有效显示时间则 `presentedBackend == null`，内部无 GPU 计时则该项为未验证。运行首轮报告 unittest，先观察缺规则导致失败。
- [ ] 接入官方 Surface 会话，在允许的接口上保留源帧与输出对应关系，用同一原生窗口测实际呈现。处理 pause/seek、关闭、服务中断和 session 释放；效果或帧身份失去依据时回到已验证路径。不能绑定逐帧结果的设备保留功能/画质观察，不套用 CuNNy 的交付率。
- [ ] 用同机同片分别跑 `google-quality`、`google-short` 及首轮对应场；能取得的输出尺寸、动态观感、时段、回退分开报告。短测通过才运行 `google-soak`；GPU 内部耗时拿不到可另做功能长跑，仍标性能门槛未验证。
- [ ] 重跑报告测试并回读同 runId 的输入、实际后端及输出证据，更新矩阵。不能运行的保留具体限制、所需设备与已验证的原画表现。提交：`docs: compare verified Google and GLES playback SR results`。
