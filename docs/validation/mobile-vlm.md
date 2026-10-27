# P8 移动端 VLM 候选调研

更新时间：2026-10-16。P8 只建立候选和验证门槛；不下载权重、不同时接入多个模型，也不把桌面推理结果当作移动端通过。

进度口径于 2026-10-26 [复核](roadmap-progress-audit-20261026.md)：P8 调研与 P9 单模型/单平台试验里程碑已达到原定停止点，基础里程碑计数为 10/12。下文各次 5/12 是历史扩展交付口径；Android LiteRT-LM、自然质量、资源、最低系统和统一验收的未完成状态继续保留。

## 2026-10-27 LiteRT-LM 可选 Kotlin availability consumer

新增公开 `LiteRtLm.availability` 与独立 `examples/android-litertlm-native`。查询使用真实 `ModelStore`、API/ABI，固定 `UNPROVEN`；不构造 production backend。缺资产返回 `REQUIRES_ASSETS/MISSING_ASSETS`，已安装资产仍返回 `UNAVAILABLE/UNSUPPORTED_DEVICE`，不会返回 `AVAILABLE`。consumer 只显示查询结果，无下载、generation 或视频路径。设计与计划已明确记录此 Task 5 分段交付裁定，production factory/generation/close 消费继续开放。

TECNO KM9/API35 backend instrumentation **4/4**、consumer Activity UI **1/1**，均无 failures/errors/skips。新增公开查询用例使用真实 Android `Build` 和已安装合成资产，并验证查询后可移除模型、没有 generation cache；UI 用例实际启动 Activity 并读取 `TextView` 的缺资产状态。合成文件不是模型权重，这些结果不证明 native drain 或推理。

backend JVM **25/25**、consumer **2/2**、multimodal **48/48**；project consumer、默认 Demo、AAR/test APK 与 lint 构建通过（594 tasks）。lint 无 error，backend 保留 2 条版本提示；consumer 有目标 API、图标、字符串资源 3 条 warning。四个 SDK 模块发布到 Maven Local 后，consumer 以 `-PusePublishedSdk=true` 构建通过（37 tasks），实际 runtime 精确解析 `litertlm-android:0.18.1`；默认 Demo/base multimodal 新鲜依赖图均不含 LiteRT-LM。Python AAR 工具 **7/7**、vendor AAR 校验通过，SHA-256 保持 `05c6bdc8acda834efcca12c0ad5d06d2cd8c83e60770f056d8d222f699f3012b`。

原始 XML、构建/依赖日志、产物和源码哈希见 `artifacts/verification/android-litertlm-consumer-20261027/summary.json`。独立审查首选模型容量不足，备用模型 429，无审查结论；作者检查不能替代独立审查。初次设备执行的自动权限审核超时，按提示重试后才实际运行成功；ADB 在受限沙箱中的错误不作为设备断连结论。授权模型、真实 drain/推理、生产 factory、API26 与完整审查仍未验证；基础 **10/12，P4/P11 未完成**。

## 2026-10-27 LiteRT-LM unavailable 真机合同覆盖

批准计划 Task 5 的 Kotlin `LiteRtLmUnavailableInstrumentation` 使用真实 Android `ModelStore` 和每个用例独立的 UUID 私有目录，覆盖缺资产 `REQUIRES_ASSETS/MISSING_ASSETS`、合成文件安装后且 probe 为 `UNPROVEN` 的 `UNAVAILABLE/UNSUPPORTED_DEVICE`、重复 close 后的 `UNAVAILABLE/CLOSED`。每个状态均检查 generate 的具体拒绝类型、factory 调用和输出次数为零、generation cache 未创建；安装用例还通过真实 remove 检查无残留 lease，teardown 检查仅本用例目录已删除。

TECNO KM9 / Android 15（API35）初次 **3/3**；负向对照临时把测试 setup 改为 `PROVEN_CUMULATIVE`，已安装资产用例按预期失败（`expected UNAVAILABLE but was AVAILABLE`，3 项中 1 failure），恢复 `UNPROVEN` 后正式复验 **3/3、0 failures、0 skips**。这只是测试输入负向对照，没有修改生产 gate，也不代表获得了真实 drain 证明。强制重跑 backend JVM **23/23**；最终 lint **0 errors/2 既有版本提示**、debug AAR/测试 APK 构建通过。新鲜依赖图确认默认 Demo 无 LiteRT-LM，backend 生产 runtime 固定 `0.18.1` 且无 runner/JUnit。

原始设备 XML、负向对照日志、JVM XML、依赖图和源码哈希冻结于 `artifacts/verification/android-litertlm-unavailable-20261027/summary.json`。限定独立审查因 429 无结论，不计为 review pass。合成文件不是模型权重，Task 5 consumer、Task 4 production factory、授权模型、真实 native drain/推理和 API26 实机验收继续开放；基础 **10/12，P4/P11 未完成**。

## 2026-10-27 LiteRT-LM drain 判据修正（生产门禁仍关闭）

固定 0.18.1 AAR 证明 `Conversation.isAlive` 是对象生命周期标志：close 先置 false，再进入 native delete；原 `terminalCallbackThenDeadConversation` 判据无效。probe 已改为 schema 2、`drainProven=false`，累计 callback 也只返回 `unverified-native-drain-contract`；不能据该 flag 关闭 post-send native 资源。两项误判回归先 RED，随后 JVM **9/9**、lint/APK/test APK 构建通过。官方 C++ 析构等待忽略错误且默认 10 分钟，目前仍无可用于生产的替代 barrier；fake 30 秒 deadline 不能证明阻塞 JNI 的结束。详见[合同修正与证据边界](android-litertlm-drain-contract.md)。本轮没有模型运行、设备推理或新完成阶段，当前基础 **10/12，P4/P11 未完成**。

以下 2026-10-26 记录保留历史验证范围；其中 lifetime flag、terminal/dead 与 candidate-probe 的旧成功判据已由上述修正撤销。

## 2026-10-26 LiteRT-LM Kotlin probe/模块合同（部分完成）

最新独立复审发现 2 MEDIUM：lease 早于 cache 清理释放，以及未强制 drain 截止时间。两项均经 RED→GREEN 修复：lease 在 cache 成功删除后才释放，删除失败则保留 lease/cache 到进程结束并关闭 admission；session owner 用 30 秒门禁包住 cancel/drain，非返回的 fake drain 超时后返回 `DrainFailure`、保留 native/lease/cache 并进入 poisoned terminal。新鲜模块 JVM **23/23**；后续 lint、debug/release AAR、POM/Gradle metadata、probe host/APK/test APK、基础 multimodal JVM 和默认 Demo APK 构建 **BUILD SUCCESSFUL**（567 tasks，40 executed、527 up-to-date）。这是对复审发现的本地回归验证，不是 native drain 的设备证明，也没有把修复后的 diff 记成独立复审通过。

本地 release 打包断言及冻结副本位于 `artifacts/verification/android-litertlm-package-20261026/summary.json`：release AAR SHA-256 `b14f143f178064f921a280784e0876c28d93eb8b980f1fa1ab5dad2e4d7c0e08`，AAR/source JAR 与 metadata hash 一致，无内嵌 LiteRT-LM classes/JNI；POM 使用 runtime scope，Gradle API variant 不引入 vendor runtime，runtime variant 固定 `0.18.1`。新鲜依赖图确认默认 Demo runtime 没有 LiteRT-LM。仅生成本地 package，生产 factory、公开 consumer、模型资产和设备推理门禁仍未完成。

本轮还补充了[模型资产候选记录](android-litertlm-model-assets.md)：Google-owned Gemma 3n E2B 固定 revision 的通用 `.litertlm` 为约 3.66 GB，需手动授权，完整 hash、当前文件的 CPU/image 与 TECNO 兼容性均待验证；没有下载权重或接受条款，尚不满足真实 manifest 冻结条件。

后续 Task 3 host 合同继续补齐：private cache 删除失败经可控 cleaner 测试先 RED 后 GREEN，返回不泄漏路径的 `RuntimeFailure` 并关闭 backend admission；512 token 上限、513 拒绝、16 MiB 超额拒绝和空结果拒绝均有 JVM 回归。availability/close join 以受控查询挂起验证，close 在查询结束前不返回，查询结束后返回 `CLOSED`。这些结果只覆盖 fake/host，真实模型与 native drain 仍未验证。

上述新增回归此前进入聚焦本地门禁：`.superpowers/sdd/2026-10-26-android-litertlm-backend/focused-final2.log` 为 **BUILD SUCCESSFUL**（548 tasks，16 executed、532 up-to-date）；模块 JVM **22/22**、probe host **9/9**、Python AAR 工具 **7/7**、`git diff --check` 通过。重新只读检查本地真实 AAR 的 `status=passed`、`errors=[]`；AAR/POM SHA-256 和两个保护文件 hash 与 handoff 顶部一致。当时 cleanup/query 独立复审因 `429 Too Many Requests` 未取得结论，后续新鲜复审及 **23/23** 修复门禁见本节顶部。

用户批准的可选后端设计与实施计划分别位于 `docs/superpowers/specs/2026-10-26-android-litertlm-backend-design.md` 和 `docs/superpowers/plans/2026-10-26-android-litertlm-backend.md`。已新增独立 Kotlin `probes/android-litertlm` 和可选 `android/backend-litertlm`；`android:multimodal` 与默认 Demo 均未加入 LiteRT-LM。固定坐标为 `com.google.ai.edge.litertlm:litertlm-android:0.18.1`。本机代理不可用时，通过显式 `-PlitertlmMavenRepo=.superpowers/sdd/2026-10-26-android-litertlm-backend/maven` 使用已核验 AAR/POM 的临时 Maven 仓库；该路径已验证可选库 AAR 打包并精确解析 Gson 2.14.0、Kotlin reflect 2.4.0、coroutines-android 1.11.0，不代表当前网络下 Google Maven 可达。

- Probe host 单测、main/test APK build 和 AndroidTest 编译通过。真实 TECNO KM9 / API35 / `arm64-v8a` 上 `Message.user(Contents.of(Content.ImageFile, Content.Text))` 的角色/顺序映射 instrumentation **1/1 passed**；修正后的原始设备日志为 `artifacts/verification/android-litertlm-probe-20261026/message-user-exact-device.log` / `.exit`。AAR 的 Java class major 65 不能在本机 JVM 17 执行，因此真实 LiteRT-LM 类的行为只由 Android instrumentation 检查。纯 host 测试现在拒绝 terminal/dead 观察倒序、超 30 秒和少于 3 个可区分的 callback snapshot；模型运行尚未触发这些实际路径。
- TECNO KM9/API35 上显式启动 probe 的无模型入口只生成脱敏 JSON `{"status":"unverified-missing-app-private-assets","schemaVersion":1}`；见 `artifacts/verification/android-litertlm-probe-20261026/missing-assets-device-report.json`。可选模块固定 spec、manifest/路径/版本、ModelStore 短 lease availability、内部 fake generation/session、正常 drain/close、poisoned lease/cache 保留与取消晚回调测试已通过；完整聚焦 JVM、`lintDebug`、可选库 AAR/probe APK 构建为 `BUILD SUCCESSFUL`（402 tasks）。缺资产时明确 `REQUIRES_ASSETS/MISSING_ASSETS`，已装模型但 probe 未证明时固定 `UNAVAILABLE/UNSUPPORTED_DEVICE`。
- 没有 LiteRT-LM 模型 manifest/权重，也没有真实双路径 probe 报告；callback 累计/增量语义、`onDone/onError` 与 `isAlive` 顺序、cancel→native drain、真实图像生成和模型兼容性全部 **unverified**。生产 factory 与公开 consumer 尚未提供，设备 content mapping/fake 测试不能升级为 Android 推理证据。该条当时沿用 **5/12、P9 active**；现按原里程碑统计为 **10/12，P4/P11 未完成**，P9 Android 扩展包继续推进。

独立审查后的 host seam 已强制所有 native 生命周期调用在 `Dispatchers.IO`，并让 session 显式声明已验证的 `CUMULATIVE`/`DELTA` 模式；fake delta 更新转成累计文本，probe state 不匹配则 poisoned。真实 probe 目前只能将非累计模式识别为 `delta-or-reordered`，该含糊结果现固定报告 `unverified-callback-mode`，不会给 `candidate-probe`。probe send 前失败的 native 所有权已由 `ProbeNativeOwner` 处理，send 尝试后无 drain 证明则保留到进程退出；独立复审确认最初 2 HIGH / 1 MEDIUM 的本地代码问题均已处理。此前聚焦门禁为 **548 tasks / BUILD SUCCESSFUL**，模块 JVM **18/18**、probe host **9/9**、Python artifact **7/7**；后续扩大到 **22/22** 的本地门禁见本节顶部。真实模型 callback/drain 仍未验证，生产 gate 关闭。

## 2026-10-26 LiteRT-LM Android AAR 真实 artifact 门禁（未接入生产）

在不修改 Gradle 依赖、不下载模型权重的前提下，取得 Google Maven 公共 artifact
`com.google.ai.edge.litertlm:litertlm-android:0.18.1` 并完成独立只读检查。AAR 原始副本、POM、清单、API inventory、原生库 `file` 输出和检查 JSON 保存在
`artifacts/verification/android-litertlm-aar-20261026/`。

- AAR 大小 **20,905,360 bytes**，SHA-256
  `05c6bdc8acda834efcca12c0ad5d06d2cd8c83e60770f056d8d222f699f3012b`；POM SHA-256
  `3d6ea70f41bc6305c5985a52c5f5d00da97402d6531c9a395f3145cb5c1a89a5`。ZIP 无 traversal/symlink，包含 `AndroidManifest.xml`、`classes.jar` 和 `jni/`。
- 真实 manifest 是文本 XML（不是 binary AXML），声明 `android:minSdkVersion="24"`，没有 `targetSdkVersion`；因此 artifact gate 的 SDK 状态为 **partial**：`minSdk=24` 已验证，target SDK 未声明，不能填写代理值。
- JNI ABI 路径为 `arm64-v8a` 与 `x86_64`；两份 `liblitertlm_jni.so` 均由 `file` 确认为 ELF 64-bit shared object（AArch64 / x86-64）。没有 `armeabi-v7a` 或 `x86` 产物。
- 许可证文件在 AAR 根目录 `LICENSE`，第三方声明在根目录 `THIRD_PARTY_NOTICE.txt`（不是 `META-INF`）；两者都已纳入工具的 inventory。POM 声明 Apache-2.0。
- `classes.jar` 公开了 `Engine.initialize()/close()`、`Conversation.sendMessageAsync()` 的 Flow/callback 入口和 `Conversation.cancelProcess()`；内容类型实际包含 text/image/audio，没有视频内容类型。故本项目继续强制 `supportsVideo=false`，不能把 LiteRT-LM 坐标或 AAR 存在当作视频 backend。

该门禁只证明公开 AAR 的静态 artifact 属性；没有加入 Gradle、没有下载/验证 LiteRT-LM 模型、没有在 TECNO 上运行 LiteRT-LM 推理，也没有验证 GPU/NPU、自然质量、延迟、能耗或取消后的 native drain。因此 Android 生产 VLM/video backend、设备推理、自然质量、GPU/energy 仍为 **unverified**。

工具修正后真实 AAR 检查为 `status=passed`、manifest SDK `partial`；标准库回归为 **7/7**：
`python3 -m unittest tools.tests.test_android_vlm_artifact -v`。

## 2026-10-26 Android 生产后端路线复核（只读官方资料，未接入）

为继续收口 P9 的 Android 缺口，本轮只复核官方运行时文档，没有新增依赖、下载权重、修改 Android 生产路径或把真机连接状态当作推理证据。结论如下：

- **LiteRT-LM** 是首选候选：官方 Android 文档提供 Kotlin/JVM API、离线模型路径、流式 `Flow`/callback 和图像消息，运行时面向 GPU/NPU；`Engine.initialize()` 可能耗时，必须放到后台。官方接口未承诺视频语义或视频帧序列，因此在本项目中先保持 `supportsVideo=false`，视频只能由仓库侧显式 `MediaExtractor`/`MediaCodec` 抽帧适配层提供，不能把一组帧静默当作视频后端通过。适配前必须实测 AAR manifest 的 `minSdk`、ABI、GPU/NPU 原生库和真实模型支持矩阵，不能默认满足 API 26。
- **MediaPipe LLM Inference** 官方 Android 页面标为 maintenance-only 并建议迁移到 LiteRT-LM；其高端设备优化、模拟器不可靠和非视频 API 边界，不作为新生产后端。
- **ExecuTorch** 的 Android `LlmModule`/多模态入口仍是 experimental，虽然有显式 `stop()`，但需要额外的 `.pte` 导出、tokenizer、backend/ABI 和模型许可核验；作为后续实验 lane，不作为当前 P9 第一实现。
- **MLC LLM** 的 Android 文档偏向高端 GPU 的文本运行时，明确不支持模拟器，当前没有可复用的多模态图像/视频 API，不作为本包路线。

2026-10-26 对 Google Maven 的只读元数据查询返回 `com.google.ai.edge.litertlm:litertlm-android` 最新版本 **0.18.1**（`lastUpdated=20261020175555`）。真实 AAR 门禁见上节；本项目仍尚未下载模型、加入 Gradle 或启动生产后端。AAR 的 `minSdk=24` 只代表库 manifest 声明，不能替代 API 26/TECNO 上的模型兼容性、GPU/NPU 和运行时实测。

下一实施包的硬门槛固定为：先以独立 artifact 检查 LiteRT-LM AAR 的 `minSdk`/ABI/许可证和离线模型 manifest，再实现 `ModelStore` 路径/哈希校验、单 operation 的 coroutine cancel→native drain→close 顺序和 late-callback 过滤；抽帧适配层须保留时间戳并显式报告 `supportsVideo=false`，直到有真实视频模型契约。没有这些证据前，Android 生产 VLM/video backend、自然质量、GPU/energy 和设备推理继续标记 **unverified**，不计入 P9/P11 通过率。

为使该门槛可复用，新增只读工具 `tools/inspect_android_vlm_artifact.py`：它对 AAR 做 SHA-256/size、ZIP 路径与 symlink 安全检查、`AndroidManifest.xml`/`classes.jar` 必需项、JNI ABI、`META-INF` 及根目录 license/notice inventory；文本 XML manifest 直接解析，binary AXML 可用本机 `aapt2` 的临时 wrapper 解析 `minSdk`/`targetSdk`。缺失工具或字段时明确返回 `unverified/partial/unavailable`，不会把 AAR 存在当作运行时通过。合成与文本 manifest 回归为 **7/7**：`python3 -m unittest tools.tests.test_android_vlm_artifact -v`。

官方依据：

- <https://developers.google.com/edge/litert-lm/android>
- <https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/maven-metadata.xml>
- <https://dl.google.com/dl/android/maven2/com/google/ai/edge/litertlm/litertlm-android/0.18.1/litertlm-android-0.18.1.pom>
- <https://developers.google.com/edge/mediapipe/solutions/genai/llm_inference/android>
- <https://docs.pytorch.org/executorch/main/llm/run-on-android.html>
- <https://llm.mlc.ai/docs/deploy/android.html>
- <https://developer.android.com/reference/android/media/MediaExtractor>
- <https://developer.android.com/reference/android/media/MediaCodec>

## 2026-10-25 自然图真机 smoke evidence（3 帧，非镜像）

为补齐 P9 固定模型的自然图输入样本，本轮只使用公开自然视频抽帧 `frame-0`、`frame-1`、`frame-18`，将输入复制到已安装的 `com.ondeviceaikit.probe.vlm` App Documents，并在物理 iPhone 16 Pro Max / iOS 27.0.1（`iPhone17,2`，UDID `<iPhone-UDID>`）上通过 `xcrun devicectl` 启动 `--p9-auto-run`。没有使用 iPhone 镜像、坐标点击、私人媒体或新模型/依赖。

- 三份原始设备报告：`artifacts/verification/p9-natural-20261025/reports/frame-0.json`、`frame-1.json`、`frame-18.json`；汇总：`artifacts/verification/p9-natural-20261025/summary.json`。
- 三次均为 `status=success`，backend `mlx.smolvlm2-500m`，模型 revision `fa57db46815177fbdfd65cc85a2b3416a8332268`，runtime revision `6e338e5df49424fc0db7c6d58184d622f92ed18a`，设备与 OS identity 全部一致。输入 SHA-256 分别为 `9c7e7a78…d89eaec1`、`ee41c2ea…3b65bec`、`f861e688…ea632d8`，与本地 PNG 逐一匹配。
- nearest-rank 统计：首 token p50/p95 `1314.134208/1321.358042 ms`；total p50/p95 `1800.751500/1884.726460 ms`。进程 RSS 采样峰值 p50/p95 `1,196,851,200/1,198,440,448 B`，方法为 `process-resident-mach-task-basic-info-10ms`。
- 输出为两次 `A flock of birds flying in the sky.`、一次 `A flock of birds flying in the sky against a clear blue sky.`。这证明固定模型在 3 个公开自然帧上完成真机推理并留下可复核文本/延迟/RSS，但没有任务特定 ground truth 或独立人工质量裁定；`naturalImageQuality` 仍为 **unverified**，不升级 P9/P11 完成。

## 2026-10-25 Metal System Trace 资源证据（固定公开 fixture）

在已授权 iPhone 16 Pro Max / iPhone17,2 / iOS 27.0.1（24A446）上，沿用已安装的 `com.ondeviceaikit.probe.vlm`、项目已有固定模型/manifest 和公开 `fixture.png`，用 `--p9-auto-run` 启动一次真实 MLXVLM 图像生成，同时录制 15 秒 `Metal System Trace`。没有下载或更新依赖，没有读取私人媒体，没有改签名资产。

- Probe report `artifacts/verification/p9-metal-20261025/p9-run-39CEE4F5-7F1E-4645-A621-7892811267B0.json`：`status=success`、`actualBackend=mlx.smolvlm2-500m`、`runId=74E7A0E7-01DB-47A5-9E7F-B07B7C251519`、`firstTokenMs=490.557542`、`totalMs=997.558208`、`peakMemory=1,170,046,976`（`process-resident-sampled`），fixture hash `60e52854…b847a606`，模型/runtime revision 与既有 manifest 一致。该报告仍是固定合成图结果，不是自然质量结论。
- Trace bundle `artifacts/verification/p9-metal-20261025/vlm-metal-all.trace`，TOC `all-toc.xml`：录制窗口 15.747300 秒，设备 GPU 为 A18 Pro；筛出的 VLMProbe PID `23718` 行有 **405** 条 GPU interval，其中 **399** 条为 Compute command、6 条为 CoreImage/输入准备。导出副本为 `all-metal-gpu-intervals.xml`，筛选行保存在 `vlmprobe-gpu-rows.xml`。
- `metal-current-allocated-size` 导出在本窗口只观察到 VLMProbe 的一次 80.00 KiB 记录，不能解释 MLX 模型的完整 GPU 分配；Metal trace 的 GPU counter set 为 null、shader timeline disabled，且导出时记录了 dylib-overlap timeline warning。因此本次只提升为“已捕获真实 GPU 执行活动”的证据，不填写 `metalGpuPeak`，不声称能耗、GPU 峰值或全机资源通过。

本轮把 P9/P5 的 GPU 观测从完全缺样本推进到有原始 trace，但 `energy`、可比较的 GPU 峰值、自然质量、最低系统和 Android 生产后端仍为 **unverified**。

## 2026-10-25 固定单图 1 warm-up + 30 samples 真机 benchmark（非镜像）

为补齐 P9 固定单图的规模化统计，本轮从当前源码生成独立 `MLXVLMConsumer` artifact，使用既有本地缓存和签名 Team，在物理 iPhone 16 Pro Max / iOS 27.0.1（`iPhone17,2`）上运行显式 `--sdk-mlxvlm-benchmark-30`。全程只使用 `xcodebuild`、`xcrun devicectl` 和 App Documents 文件服务；没有使用 iPhone 镜像、镜像画布、坐标点击或私人媒体。公开 fixture 与 14 个固定模型文件均在设备端复制/清单核对后才启动。

- 原始报告：`artifacts/verification/apple-mlxvlm-benchmark-30-20261025/device-report.json`（SHA-256 `dd75f54cb84b821261e06639d032af2bba539fbdf1706023691479eb2e7c9f66`）；汇总：同目录 `summary.json`（SHA-256 `3d970842aec57e0e79519f6588a49beb0b7ff3f9bdd776611aa3648d57fb7b83`）。
- `runId=D824ACA7-42E8-492D-B9A5-6FE3868551F5`，`sampleProfile=1-warmup-30-samples`，warm-up 成功，30/30 测量项；backend `mlx.smolvlm2-500m`，模型/runtime/fixture identity 与固定 manifest 一致。
- nearest-rank 延迟：load p50/p95 `334.141875/372.181459 ms`；首 update `782.097334/819.266917 ms`；total `943.978709/981.462959 ms`。30 次输出均命中 `synthetic-red-square-v1`（30/30）。进程 RSS baseline/peak/delta 为 `34,127,872 / 1,186,676,736 / 1,152,548,864 B`，只代表 `process-resident-mach-task-basic-info-10ms` 采样。

这补齐的是固定合成图片的 30 样本统计，不升级为自然图片质量、Metal GPU peak、energy、冷启动、跨设备资源预算或 P11 统一验收；报告中的 `metalGpuPeak`、`energy`、`naturalImageQuality` 继续为 **unverified**。默认 5 样本入口仍保留，validator 通过 `--expected-samples` 防止样本数混淆。

## 2026-10-25 review 修复后 1 warm-up + 30 samples 重跑（非镜像）

独立 code review 指出 30 样本 raw 缺少 profile 时不应被重新标记，以及 benchmark 失败不应显示 `Finished`。修复后从当前源码重新生成签名 consumer，并在同一物理 iPhone 16 Pro Max / iOS 27.0.1 上再次运行；全程仍只使用 `xcodebuild`/`xcrun devicectl`，不使用镜像、不读取私人媒体。

- raw：`artifacts/verification/apple-mlxvlm-benchmark-30-20261025-fix2/device-report.json`（SHA-256 `186a2077bf966dfb69cd89034d940c79a09b91a6d09d74b05172dc862c3c73d1`）；summary：同目录 `summary.json`（SHA-256 `4cf8f2430d896addd2f2d821d815b795647924c83f09b9d825ed5987a6fa61d6`）。
- `sampleProfile=1-warmup-30-samples`，warm-up 成功，30/30 测量和 30/30 rubric；nearest-rank load p50/p95 `337.240167/364.418917 ms`，首 update `779.335084/809.109292 ms`，total `940.974208/971.115375 ms`。
- RSS baseline/peak/delta `40,124,416 / 1,192,296,448 / 1,152,172,032 B`，仍只代表进程采样；`metalGpuPeak`、`energy`、`naturalImageQuality` 继续 **unverified**。

该重跑验证的是修复后的固定合成 benchmark 契约，不升级自然质量、GPU/energy、冷启动、跨设备预算或 P11 统一验收。

## 候选比较

| 候选 | 模态/任务 | 运行时候选 | 许可证与风险 | 当前结论 |
| --- | --- | --- | --- | --- |
| SmolVLM2 500M | 图片/短视频描述、问答 | iOS 18+ / MLXVLM，沿用现有总设计路线 | `mlx-community/SmolVLM2-500M-Video-Instruct-mlx` card 标记 Apache-2.0；BF16 权重约 1.02GB，**不是**设备峰值内存 | **P9 首选**：保持现有设计，不改成 256M |
| SmolVLM2 256M | 图片/短视频描述、问答 | 官方 card 提供 ONNX 组件，Android ONNX Runtime 可探针 | Apache-2.0；model.safetensors 约 1.03GB，官方视频推理 GPU RAM 约 1.38GB；这些不是移动端实测 | Android 备选，不与 P9 同时接入 |
| Florence-2-base | 图片描述、检测、区域任务 | 官方示例依赖 Transformers `trust_remote_code=True`；移动端转换需另证 | MIT；权重约 463MB，完整 repo 约 930MB；没有官方开箱即用移动 runtime | 任务型备选 |
| MiniCPM-V-2_6 | 图片问答、OCR/多模态对话 | 官方专用 llama.cpp 分支需验证，不能视为上游通用 runtime | 主权重访问受限；Apache-2.0 仅覆盖代码，权重使用独立 MiniCPM Model License，商业用途需登记；INT4 repo 约 5.97GB，模型卡称约 7GB GPU memory | 不作为 P9 首选 |

参考模型页（接入前必须重新读取具体 revision）：

- <https://huggingface.co/HuggingFaceTB/SmolVLM2-500M-Video-Instruct>
- <https://huggingface.co/mlx-community/SmolVLM2-500M-Video-Instruct-mlx>
- <https://huggingface.co/HuggingFaceTB/SmolVLM2-256M-Video-Instruct>
- <https://github.com/huggingface/HuggingSnap>
- <https://github.com/cyrilzakka/mlx-swift-examples>
- <https://huggingface.co/microsoft/Florence-2-base>
- <https://huggingface.co/openbmb/MiniCPM-V-2_6>
- <https://huggingface.co/openbmb/MiniCPM-V-2_6-int4/tree/main>

证据等级：模型仓库的许可证、权重大小和 revision 属于**上游模型资产证据**；HuggingSnap 的 iOS 18/MLXVLM 集成属于**上游移动端实现证据**；本项目还没有下载哈希、构建、iPhone 推理或峰值内存证据。Florence-2 的 463MB 是 FP16 权重大小，930MB 是含重复格式的仓库大小；256M 的 1.38GB 与 MiniCPM 的 7GB 是各自模型卡报告的 GPU 内存，不是本项目设备 RSS。Florence-2 官方示例使用 `trust_remote_code=True`，移动端转换及代码供应链需单独核验；还需明确 base 与 base-ft 的任务质量差异。

P9 候选 revision：2026-10-16 只读查询官方模型 API，`mlx-community/SmolVLM2-500M-Video-Instruct-mlx` 的当前 SHA 为 `fa57db46815177fbdfd65cc85a2b3416a8332268`，card 的 license 为 `apache-2.0`。这是待下载前重新读取和校验的观察值，不把浮动分支名作为锁定版本。MLXVLM 示例源码也须锁定 commit，而不是跟随 `main`。

## 统一验证门槛

1. 锁定 checkpoint revision、tokenizer、图像预处理和量化配置，记录 SHA256。
2. 固定 3 张不含个人信息的测试图和 1 个短视频；保存输入 hash 和结构化输出，不提交图像内容。
3. 分别记录模型加载、首 token、完整输出、峰值内存、取消和连续两次会话资源释放。
4. 至少一个目标平台实机或明确标记 unavailable；构建/转换成功不算推理成功。
5. 质量采用任务特定的人工可复核记录；不把不同任务或不同模型合并成总分。

P8 当前状态：候选、许可证、体积、运行平台和证据等级已记录。P9 选择 **SmolVLM2 500M-Video-Instruct-mlx + iOS 18+ MLXVLM**，保持现有总设计；256M ONNX 仅作为后续 Android 备选。P9 权重及全部支持文件已在忽略的 `artifacts/P9/verified` 完成 SHA256 校验，`model.safetensors` 为 1,015,023,993 字节、SHA256 `a9839c8f79ecc93e54a00dc73cc0e68ba477debcd065d50c1c289fbb1075f981`。探针尚未完成构建或真机运行。

权重不进入 Git 或 LFS。重新取得资产时，使用固定 revision 的 `tools/fetch_vlm_asset.py stage docs/validation/p9-smolvlm-manifest.json artifacts/P9/staging` 下载到被忽略的 staging；运行 `python3 tools/validate_vlm_manifest.py docs/validation/p9-smolvlm-manifest.json`，再运行 `python3 tools/fetch_vlm_asset.py publish docs/validation/p9-smolvlm-manifest.json artifacts/P9/verified`。发布器会复核每个文件的大小及 SHA256。

探针的固定非个人图片由 `python3 tools/probes/p9_fixture.py artifacts/P9/fixture.png` 生成（64×64 RGBA，SHA256 `60e52854b98e7e646033d70fb63b715fd90df2e8a20102ed79ea9b99b847a606`）。将 `artifacts/P9/verified`、manifest 和 fixture 复制到 iOS probe App 的 Documents 后，以 `--p9-model-directory model --p9-manifest p9-smolvlm-manifest.json --p9-input fixture.png --p9-auto-run` 启动；App 会再次逐文件校验资产，并为每次运行写入独立的 `p9-run-<UUID>.json`。

2026-10-17 验证边界更新：SwiftPM 已解析并固定 `mlx-swift 0.32.3`、`mlx-swift-lm 6e338e5df49424fc0db7c6d58184d622f92ed18a`、`swift-syntax 603.0.1`、`swift-numerics 1.1.1`，并加入 `swift-transformers` 的 `Tokenizers` 产品以满足 MLXHuggingFace 宏依赖。使用 `-skipMacroValidation` 后 iOS 27 模拟器测试 **15/15** 通过；签名设备构建、安装和模型复制成功。

在 iPhone 16 Pro Max / iOS 27.0.1 上使用固定 fixture 和完整 1,015,023,993 字节资产完成三次运行：首次成功输出 `A flag with a red square on a white background.`（首 token 3842.036542 ms、总计 4416.807959 ms）；定时取消返回唯一 `cancelled` 终态、空输出；取消后重复会话再次成功（首 token 463.658792 ms、总计 971.546583 ms）。三次结果的模型 revision、runtime revision 和 fixture hash 均与 manifest 一致。峰值内存仍为 `null/unmeasured`，不宣称资源达标；原始 JSON 与汇总仅保存在忽略目录 `artifacts/P9`。

更新后的设备构建再次运行成功：`p9-run-91404738-5192-4A0C-AA8F-D313CE091381.json` 包含 `schemaVersion=1`、唯一 `runId`、设备架构 `iPhone17,2`、`validationStatus=device` 和 OS build `24A446`；首 token 478.301875 ms、总计 997.441792 ms。该结果仍明确记录 `peakMemory=null` / `unmeasured`。

模型不进入 Git 或 LFS。完整 App build、安装、真机图片→文本、取消和重复会话均已有证据；本次新增 `ResidentMemorySampler`，在生成会话期间以 10 ms 间隔采样进程 `mach_task_basic_info.resident_size`，成功结果写入 `peakMemory`，状态为 `process-resident-sampled`。该指标不代表 GPU/Metal 或全机峰值。补齐锁定的 MLX Swift 子模块后，iOS 27 模拟器完整 XCTest **16/16** 通过。质量基准和 Android 第二平台仍未完成，P9 作为单平台探针通过不等于完整多平台能力交付。

2026-10-17 使用修订后的签名探针在同一 iPhone 16 Pro Max / iOS 27.0.1 重跑成功：`p9-run-E1E881A2-6E52-490B-B61B-AF44E0E27516.json` 的 `status=success`、`actualBackend=mlx.smolvlm2-500m`，模型/runtime revision 与 manifest 一致，fixture hash 为 `60e52854…b847a606`，输出仍为固定红色方块描述。`firstTokenMs=688.627333`、`totalMs=1343.159917`；进程常驻采样 `peakMemory=1,166,196,736` B，`peakMemoryStatus=process-resident-sampled`。这是单次设备运行的进程常驻观测，不等于 GPU/Metal 或全机峰值，不能直接作为资源预算。
