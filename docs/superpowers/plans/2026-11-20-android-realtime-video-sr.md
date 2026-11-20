# Android 播放时视频超分 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付一个无 GMS 也能边播边做 CuNNy 2× 的 Android Demo，并能回答“哪台手机能持续实时播放、看起来是否更好”。

**Architecture:** 独立 APK 使用 MediaExtractor、两个 MediaCodec 和 AudioTrack 主时钟；一个原生 GLES 渲染器持有输入纹理、增强作业和显示窗口。KMP 只读写开关及呈现状态，Python 从逐帧原始记录复算结果。Google 路径单独安排，首轮 APK 不引入它。

**Tech Stack:** Kotlin、Android 平台 API、C++/JNI、EGL/GLES 3.1、Gradle wrapper、Python/ffprobe。沿用仓库 AGP、JDK 17，本机可用 NDK 27.0.12077973、CMake 3.22.1。

**Spec:** [已确认的设计](../specs/2026-11-20-android-realtime-video-sr-design.md)（初稿提交 `1423e20`）；[第二阶段 Google 同片对照](2026-11-20-android-realtime-video-sr-google.md)。

## Global Constraints

- API 26 是兼容目标。GLES 3.1 是增强前提，不能成为 APK 安装或原画播放前提。首档：本地单路、有声 H.264、1080p/30、BT.709 SDR，2× 输出 3840×2160，另报实际显示尺寸。HDR、DRM、直播、4K 输入不纳入本轮增强。
- 固定输入 `fixture.mp4` 的 SHA-256 为 `df677798a8604a7a6289fcf50a0fef3844a047631e0041ae7d5563c55f3746ec`，30 秒/900 帧/AAC；不入 Git。iOS 比较入口为 UHPlayerSDK `7d40dfe`，固定 CuNNy 上游 `906031bb00c15dd6a6bbbaa21c0eb0b724ca8437`。实验源码及本地安装；保留 LGPL-3.0-or-later 头、全文、修订和修改说明。
- 像素：8-bit 亮度与独立 CPU 参考最大差 ≤1；最终 RGB 有限、alpha=1。实时：SR GPU P95 ≤25 ms、可确认显示帧取帧至显示 P95 ≤33⅓ ms、漏显或超时率 <5%、按时增强上屏率 >95%。性能场不回读像素。
- 30 秒原画/增强交换顺序；短测通过再做 30 分钟。缺实际显示时间只能报告功能、画质及可测阶段，长测实时门槛标“未验证”。模拟器不代表真机。现有 LiteRT 4× 离线报告单列。
- 不替换普通 App、模型或现有离线入口。候选修改由仓库约定的免费 helper 交付，主代理集成和做设备验收；native 最多一个，只做只读审阅。只提交本任务明确文件，不做发布。

## Review Focus

1. 输入有重复 PTS、视频缓冲合并或解码器漏帧：保留源帧分母，不能把别帧的增强画面显示出来（任务 1、3）。
2. 暂停后对比、seek 或 Surface 重建时旧回调到达：不能回写新画面或后端状态（任务 2、5）。
3. 黑位、白位、画面方向或裁剪改变：原画与增强使用同一视野，不能二次做限幅扩展或输出透明黑（任务 3、4）。
4. GPU 很慢、分配失败或上下文丢失：保住原画和声音；迟到的增强结果释放，窗口仍迟到则记漏显（任务 3、5）。
5. 时间戳缺失、乱序或计时 disjoint：提交不算显示，CPU 等 fence 不算 GPU 时间，不能据此给设备发通过结论（任务 1、5、6）。

---

**文件与命令约定。** 新模块 `D=examples/android-realtime-sr`，仅由 `-PenableRealtimeSrDemo=true` 在根 `settings.gradle.kts` 加入。下文 `K/`、`T/` 分别是 D 内 `src/main/kotlin/io/ondeviceai/realtimesr/`、`src/test/kotlin/io/ondeviceai/realtimesr/`；`C/` 是 `src/main/cpp/`，`S/` 是 `src/main/assets/sr/`。测试/工具使用仓库根目录。以下三个 shell 函数是计划中的命令简写，安装及运行均限定显式选择的测试设备：

```sh
srgradle() { rtk proxy ./gradlew -PenableRealtimeSrDemo=true ":examples:android-realtime-sr:$1" "${@:2}"; }
srtest() { srgradle testDebugUnitTest --tests "io.ondeviceai.realtimesr.$1"; }
srdevice() { rtk proxy python3 tools/probes/android_realtime_sr_device.py --serial "$SR_SERIAL" --fixture "$SR_FIXTURE" --out "$SR_RUNS" "$@"; }
```

`SR_FIXTURE` 指向设计所列本地片源，`SR_RUNS` 为预先建立的仓外本地证据目录，每次执行使用新的 runId 子目录；`SR_SERIAL` 明确指定已分配测试机。设备驱动负责安装该 case 对应的独立 debug 包并记录 APK 摘要。每个任务通过后只提交其 Files 中的变更；下面给出提交标题。对缺机器、证据或能力的检查保存具体原因。

### 任务 1：先让“900 帧里有几帧按时增强”可独立复算

**Files:** 新建 `tools/probes/android_realtime_sr_report.py`、`tools/tests/test_android_realtime_sr_report.py`。

**Interfaces:** CLI `index --fixture FILE --out index.json`、`assess --index index.json --run DIR --out result.json`。输入固定为源帧清单、`run.json`、`events.jsonl`；产出四种互斥结果、分子分母、分位数、计时覆盖率和判定。后续端侧按此合同写原始事件。

`run.json` 固定 schema 版本、runId、片源/着色器/APK 摘要、设备能力、场次及计划播放区间。源表每行含 sourceOrdinal、pts 和 timeBase；事件含 runId、帧 ID、ptsUs、阶段（decodeRelease/latch/srStart/srEnd/swap/display）、requestedBackend、submittedBackend、reason、单调时间。分别保存 targetNs/nextTargetNs、latchBeginNs、eglFrameId、displayNs、gpuElapsedNs 及计时有效标志；不可观测用 null。未返回显示证据时不生成虚构的 display 事件。

- [ ] 写 `test_duplicate_pts_missing_and_boundaries`：构造 100 个应显示帧，其中两个同 PTS 不同序号；断言 `(onTimeSR,onTimeOriginal,late,unconfirmed)==(95,2,1,2)`，且 `srGate==false`（恰好 95%）。另验证恰好 5% 漏显也失败；缺显示能力或 disjoint 覆盖不足为 `unverified`。
- [ ] 运行 `rtk proxy python3 -m unittest discover -s tools/tests -p test_android_realtime_sr_report.py`，应先因缺少聚合器失败。
- [ ] 实现 `index`：按 ffprobe 解码显示顺序编号，保存时间基、PTS、同 PTS 出现序号、源摘要。实现 `assess`：以 (generation, loop, sourceOrdinal) 左连接应有帧，拒绝重复终态/异 runId；比较整数纳秒（30 × 延迟 ≤ 10⁹）和音频给出的显示时隙。按最近秩算 P95；分别列 per-minute/全场、增强/原画、最长连续漏显、帧间隔、回退原因、声画时钟差。GPU 时长覆盖所有完成的作业（含回退）；缺样本不能证明门槛时标未验证，保留未完成数。
- [ ] 重跑同一测试应全绿；运行 `rtk proxy python3 tools/probes/android_realtime_sr_report.py index --fixture "$SR_FIXTURE" --out "$SR_RUNS/index.json"`，核对固定 SHA、900 个序号。时长 1800 秒的完整 60 轮分母为 54,000；中止场仍列计划帧数与终止位置，不冒充完整场。
- [ ] 提交：`test: define playback SR frame accounting and gates`。

### 任务 2：独立 Demo 先播放有声原画

**Files:** 修改 `settings.gradle.kts`；新建 D 内 `build.gradle.kts`、`src/main/AndroidManifest.xml`、`K/PlayerActivity.kt`、`K/CodecPlayer.kt`、`K/PlaybackClock.kt`、`T/PlaybackClockTest.kt`；新建 `tools/probes/android_realtime_sr_device.py` 和 D 的 `src/debug/kotlin/io/ondeviceai/realtimesr/SrRunController.kt`。

**Interfaces:** `CodecPlayer.open(uri: Uri, output: Surface)`、`play()`、`pause()`、`seekTo(ptsUs: Long)`、`close()` 均返回 Unit；`PlaybackClock.targetNs(ptsUs: Long): Long?` 尚无音频锚点时返回 null。调试驱动 `srdevice --case original`；后续任务扩展 case，不把调试入口编入 release。

- [ ] 写 `PlaybackClockTest.pauseSeekAndWrap`：断言 `targetNs(1_000_000)==null`（未建立时钟），锚定 48 kHz 后 48,000 个已播放采样帧对应 1 秒；暂停不前进，seek 清旧锚，处理播放位置回绕。运行 `srtest PlaybackClockTest` 先见缺模块或类型的失败。
- [ ] 建立独立应用 ID `io.ondeviceai.realtimesr.demo`；仅用平台 View、SurfaceView、系统文件选择器，依赖现有 `:kmp-binding`。为两个轨道使用独立 extractor、解码 H.264/AAC、PCM 写 AudioTrack；以实际已播放采样帧建立单调时钟。初始原画可直出窗口；暂停和 seek 同步清音视频旧队列，从先前同步帧恢复。原画可播放但不满足增强输入条件时告知原因。
- [ ] 实现主机驱动：验证输入摘要和指定序列号，只安装独立 debug 包，以 `run-as` 写其私有目录，不重装普通 App。记录版本/设备/runId，等待并读回匹配 runId 的私有终态；断连保留现场。原画首测以 900 个源帧、AAC 已播放位置推进、暂停冻结、seek 后位置恢复为断言，不能据 codec 回调宣称已显示。
- [ ] `srtest PlaybackClockTest`、`srgradle assembleDebug` 和 `srdevice --case original` 应分别给出单测通过、独立 APK 和原画/音频终态。无设备仅记前两项。
- [ ] 提交：`feat: play the fixed AVC and AAC clip in an isolated Android demo`。

### 任务 3：把解码帧送到可追踪的原画呈现管线

**Files:** 新建 `K/FrameBinding.kt`、`T/FrameBindingTest.kt`、`K/SrRenderer.kt`、`C/CMakeLists.txt`、`C/jni_bridge.cpp`、`C/egl_renderer.cpp`、`S/oes_to_rgb.frag`；修改任务 2 的 build、播放器和调试驱动。

**Interfaces:** `FrameId(generation: Long, loop: Int, sourceOrdinal: Int)`；`SrRenderer.decoderSurface: Surface`；`latch(id: FrameId, ptsUs: Long): Boolean`、`present(id: FrameId, targetNs: Long): Long?` 返回是否成功取得纹理、以及可用的 EGL frame ID；`invalidate(generation: Long)` 清旧工作。所有 JNI 调用遵循 EGL 线程所有权。

- [ ] 写 `FrameBindingTest.coalescedAndDuplicatePts`：相同 PTS 的第 7、8 帧必须有不同 ID；回调合并且无法消歧则两帧都不算确认；最多一个已释放但待取的解码输出。运行 `srtest FrameBindingTest`，应因缺少绑定器失败。
- [ ] 实现独立渲染线程、SurfaceTexture/OES→稳定 2D 纹理池，保存 transform/crop、旋转、颜色范围与取得前单调时间。一个 release 在途，验证后放行下一个；超时要解除背压并记录缺失。保留原画至它的呈现或 GPU fence 完成；不能覆写未归还纹理。以音频时钟调度最终窗口。无可用 EGL 时切回 codec 直出，标记呈现不可验证。
- [ ] 在窗口支持时启用 `EGL_ANDROID_get_frame_timestamps`，把 swap 前取到的 ID 与返回的实际显示时间匹配；异步处理 pending/invalid。原画路径也写同一合同事件，不能把 swap 或 decoder rendered 通知当最终显示。
- [ ] `srtest FrameBindingTest` 应通过。设备跑 `srdevice --case original-egl` 和 `srdevice --case color-bars`：核对方向、裁剪及黑白色阶，验证无二次扩幅、RGB 有限、alpha=1；同 run 保存已确认/未确认明细。能力缺失要验证直出仍有声音和画面。
- [ ] 提交：`feat: correlate decoded textures with EGL window presentations`。

### 任务 4：移植 CuNNy，以像素差验收

**Files:** 新建 `C/cunny.cpp`、`S/cunny0.comp`、`S/cunny1.comp`、`S/cunny2.comp`、`S/composite.frag`、`S/LICENSE`、`S/SOURCE.md`、`tools/probes/cunny_reference.py`、`tools/tests/test_cunny_reference.py`；扩展 renderer、CMake、设备驱动。

**Interfaces:** `SrRenderer.tryEnhance(id: FrameId): Boolean`（只表示接受作业），`isEnhancedReady(id: FrameId): Boolean`（零等待 fence 检查）；像素测试 CLI `srdevice --case pixel-parity`。此步先在独立校验场使用，不打开自动增强。

- [ ] 从固定 iOS 版本 `Examples/SuperResolution/CuNNy.metal` 和原许可证确认权重排列、三段计算、边界 clamp 与 2× 子像素位置；记录源字节摘要。独立 CPU 标量参考用源权重，不读取 GLES shader 的输出或生成代码。建立平坦、角上脉冲、非对称斜边及真实帧裁剪；测试 `max(abs(actual8-reference8))<=1`，且在指定非平坦图上 `actual8 != bilinear8`。
- [ ] 运行 `rtk proxy python3 -m unittest discover -s tools/tests -p test_cunny_reference.py`，应先见缺实现；固化方向/子像素交换的负例后使 CPU 参考测试通过。用同一 Android 实际输入亮度做 GPU/CPU 配对。
- [ ] 实现三段 GLES 3.1 compute，按需探测版本、尺寸、图像格式可写、着色器编译、分配和已知像素；合成源色度，记录内部分辨率与屏幕尺寸。保留源头和许可。用共享上下文的工作线程、跨上下文同步和显式 flush，拒绝并行第二个作业；不阻塞原画提交。
- [ ] 运行 `srgradle assembleDebug` 和 `srdevice --case pixel-parity`；保存每组实际输入/参考/输出、最大差及其坐标、alpha/颜色检查。验证 1920×1080→3840×2160。若失败先查中间通道、范围、采样方向；未通过不做实时达标声明。
- [ ] 提交：`feat: validate GLES CuNNy 2x against an independent pixel reference`。

### 任务 5：把自动、CuNNy 和原画接到播放控制

**Files:** 新建 `K/AndroidRealtimeSRHost.kt`、`K/PresentationPolicy.kt`、`T/PresentationPolicyTest.kt`；修改 Activity、CodecPlayer、SrRenderer、原生窗口/作业与运行驱动。复用现有 [KMP 接口](../../../kmp-binding/src/commonMain/kotlin/io/ondeviceai/binding/RealtimeVideoSR.kt)，不改签名。

**Interfaces:** `AndroidRealtimeSRHost : RealtimeSRHost` 实现 `applyEnabled(enabled: Boolean)`、`readStatus(): RealtimeSRStatus`；Demo 另提供 `select(mode: PlaybackMode)`，枚举 `AUTO/CUNNY/ORIGINAL`。选择、实际显示、原因分别呈现。

- [ ] 写 `PresentationPolicyTest.deadlineAndGeneration`：按时原画回退→`ORIGINAL,true`，下一帧增强恢复→`CUNNY,false`；主动关闭后先 `null,false`，确认原画后 `ORIGINAL,false`。过代回报、同代乱序回报不能覆盖最近显示；从未获取有效呈现时间时维持 `null,false`。运行 `srtest PresentationPolicyTest` 应先失败。
- [ ] 实现显示截止预算和单作业策略：仅取匹配帧 ID 的就绪纹理，否则呈现该帧原画；持续争抢导致原画漏显时暂停增强并记录原因。seek/切源/开关/窗口重建使旧工作失效并安全回收；暂停 A/B 保留不可变原画副本和源序号，按新代次发起重绘，允许不同呈现 ID，不重新起播，不计入性能场。
- [ ] `srtest PresentationPolicyTest` 和 `srgradle lintDebug` 应通过；`srdevice --case lifecycle` 注入一帧迟到、分配失败、窗口丢失；覆盖暂停对比、seek、换源、前后台、显式关闭再开启。逐条核对同帧回退、音频连续、无陈旧状态、释放后的作业/窗口数归零。
- [ ] 提交：`feat: switch playback SR modes with same-frame fallback`。

### 任务 6：真机长短测、观看对照与 Wiki 底稿

**Files:** 扩展运行驱动/报告测试；新建 `docs/validation/android-realtime-sr-playback.md`（矩阵及可发布的小节）、`tools/probes/android_realtime_sr_ab.py`、`tools/tests/test_android_realtime_sr_ab.py`。大文件留在仓外；文档记录相对证据清单和摘要。

**Interfaces:** `srdevice --case short-abba|soak|quality`；`android_realtime_sr_ab.py --run DIR --out DIR` 从独立画质场生成同视野、同显示尺度的匿名 A/B 和独立答案键。使用任务 1 的 `assess`，不另写一套阈值。

- [ ] 写 `test_pair_requires_same_source_frame`：错一帧、范围不一致、重复样本或把标识泄露到 A/B 文件名应失败；`test_invalid_timing_cannot_pass` 覆盖无显示扩展/全 disjoint/全部回退。运行两个对应的 unittest 文件，先确认负例被抓住，再执行完整报告测试。
- [ ] 落实设备采集：在独立质量场做有参考的降采样重建、原画/双线性/CuNNy 同帧，另做可重放的动态 A/B。细字、运动纹理、压缩边缘、暗部、闪烁按场景标注；人评允许无明显差异，未看填待评。GPU 时间用有效 timer query，另列 CPU→fence、内存/热状态（如可用）、冷启动、循环接缝；不把耗时估作功耗。
- [ ] TECNO KM9/API 35 先做固定片的 `short-abba`：30 秒原画、增强、增强、原画，对各场分别验收。增强短测达标后运行 `soak` 连续 30 分钟并用 `assess` 复算全场和每分钟。无有效显示时间只做功能长跑；失败保留最先失守条件；中断不可填满 30 分钟。另跑 `quality` 并验证匿名 A/B 可播放。
- [ ] 按同协议补可借到的 API 26/27、Adreno/Mali 真机；API 36 模拟器只报告兼容/控制。矩阵列 OS、机型/SoC/GPU/驱动、GLES/时间戳、GMS、片源/倍率/窗口、实际后端、画质、短测、长测及证据；输出达标/不达标/未验证/未测。iOS 对照注明独立探针或播放器入口，LiteRT 离线另表。Wiki 底稿仅引用可复算行；需要发布时按项目 Wiki 流程单独同步。
- [ ] 提交：`docs: record Android playback SR quality and device coverage`（仅提交已有证据、脚本和文档）。首轮交付以像素、播放、持续呈现三项分别有结论为准；不因某台机未达标而改门槛。第二阶段依赖短测、原画和计时合同，不依赖首轮所有机型达标；只有发现可复现 GLES 瓶颈才安排 Vulkan。
