# Foundation / Demo 新会话交接 — 2026-11-05

本文件是新的恢复入口；优先于旧 [2026-10-23 长交接](HANDOFF-2026-10-23-next-session.md)中的历史下一步。
用户最新要求：**先写交接文档，在新会话继续**。本次收尾不再构建、测试或操作设备。

## 目标、当前结论与会话状态

- 完整目标：按照原 roadmap 推进 Demo 原型；用户要求功能优先，部分 corner case、最低系统及长期质量/资源专项可后置，后置不计通过。
- 基础里程碑 **10/12**；**P4/P11 与发布仍 `open/not-ready`**。不要因本机测试、固定样本或截图通过宣称整个目标完成。
- 工作目录：`/Users/daubert/Github/ondevice-ai-kit/.worktrees/foundation`；分支 `feat/sdk-foundation`。
- 写本交接前 HEAD：`ac07d36354ee3572f8e156024ca73e9d5a73e172`。本交接若单独提交，恢复时以 `git rev-parse HEAD` 为准。
- 最近提交：`ac07d36` Android 精确候选真机 TTS 观察；`03be979` Apple 两变体稳定首屏；`c5b258a` Apple 精确编译变体真机 smoke。
- 当前没有本代理仍在执行或待恢复的命令、推理、instrumentation、工具 session handle。之前所有设备测试均已确认结束。
- 目标曾因外部门槛标记 `blocked`，用户随后恢复为 `active`。恢复后的本轮只读检查了交接、Gradle配置和技能；**没有实施下面的 TTS 持久化方案，没有生成新 APK，没有新增设备操作**。
- 旧 blocked 审计计数不可沿用；用户恢复后的审计重新计数。

## 开始前保护与授权边界

所有 shell 命令以 `rtk` 开头，通常用 `rtk proxy`。读取 `/Users/daubert/.codex/RTK.md` 和适用 AGENTS.md。
使用普通 understand → execute → verify → report；本交接下一项先 solo，不重试已失败的 native reviewers。

当前 index 空。以下 **9个原有 dirty/untracked 路径不得覆盖、暂存、回退或夹带提交**：

```text
demo/iosApp/OnDeviceAIDemo.xcodeproj/project.pbxproj
docs/validation/face-identity.md
probes/ios-camera-lifecycle/CameraPageResourceValidation.swift
probes/ios-camera-lifecycle/CameraResourceFailureReportChecks.swift
probes/ios-camera-lifecycle/CameraResourceValidation.swift
probes/ios-camera-lifecycle/README.md
probes/ios-camera-lifecycle/ThreadInventory.swift
probes/ios-camera-lifecycle/ThreadInventoryChecks.swift
scripts/generate-camera-resource-app.rb
```

保护摘要清单：`artifacts/verification/identity-gallery-demo-20261103/protected-guard-final.json`。
写本交接前逐一复核相同。生产 `apple-mlx/Package.resolved` SHA-256：
`e67e985970770991f1eb2384ca9e9b6267fb8a8f1569051be5cfb2175f0ef858`。

- 不新增下载、依赖、模型、私人媒体或签名资产变更；不 push/merge/tag/发布。
- 既有设备、现有签名身份和已经授权的固定模型/公开合成素材可沿用。Gemma 用户许可已经确认，不重复要求接受。
- 现有普通 App 和模型存储必须保留可用；Android 临时替换测试后恢复原 main/test APK、runner、权限与模型摘要，不清 App 数据。
- 麦克风单次授权已经消耗；本轮和下一项 TTS 不采音、不拍照。不得自动触发新的录音。
- 真实 Photos provider 写入、最低系统设备、长期资源和校准专项保持原后置边界。
- `artifacts/verification/` 为本地 ignored 证据，**不在普通 Git clone 中**。不要覆盖或“重新生成”冻结原件。

## 已有完整候选与验证，避免重做

### Android 精确候选

- 源提交 `e0438c030a987ceeff9f9207c2475684749e4d8b`，tree `1a79aabdc997ca99fe441a02b9cb54982ce2c34c`，953份 committed 文件。
- `artifacts/verification/android-committed-candidate-20261105/source.tar` SHA：
  `c3b5966e09fdd44a957e3da1f3a2674a289857f99e02211a18f1f772155373b4`。
- `android-committed-candidate-20261105/candidate.apk` SHA：
  `b90e783062548368f12e5262a1dc8437d511119c6dddf1c1ffb66aabafa64d8d`；包名 `io.ondeviceai.demo`。
- 四 opt-in 后端同时启用；offline fresh build 997 tasks，586 JVM tests，20模块 lint 无 error、52既有 warnings。
- AVD5582 上同一 APK：普通 LM 初始化后取消/drain/同PID重入/成功/清理44项；补充固定ASR及原生生命周期、SR双帧AAC/lease、Identity/purge/未校准拒绝、普通人脸picker/布局均有证据。
- 两个 AVD 均无 TTS 引擎；这是真失败，不重复查询/下载。手机已有 Google TTS，见下一节。
- 构建及补充运行详情：[Android 候选](validation/android-committed-candidate.md)。不要重跑已通过的997任务来解决报告传输问题。

### Apple 与 F09

- 同一 e0438c0：Apple481 + KMP24 + shared80 + 注入桥接133 = 718次执行、0失败/跳过；基础 unsigned App/consumer 构建已通过。
- MLX unsigned device/arm64 simulator、独立 OpenCV Identity device 整 App 已构建；12固定包/2 C子模块/6878原始依赖文件，无F09 overlay。3产品树/664 Swift输入已冻结，**不要重复准备和构建**。
- 生产 Skiko 仍 `0.144.6`；F02补丁没有切入生产。OpenCV 制品只有arm64 device/x86_64 simulator，不能声称arm64 simulator通过。
- iPhone `00008140-0008703436D3001C`，最近验证为 iPhone17,2 / iOS27.2 /24B119；沿用既有 team `4X996UV8LC`，未创建签名资产。
- 隔离 MLX bundle `io.ondeviceai.candidate.e0438c0.multimodal`：编译 Mach-O 不变；64×64白图真实Swift/KMP→Vision回调、0脸；14安装模型逐文件回读SHA通过。没有当前候选普通UI多模态生成结论。
- 隔离 Identity bundle `io.ondeviceai.candidate.e0438c0.identity`：仅ID/显示名和签名不同，3 Mach-O UUID/section一致。Identity与MLX稳定首屏截图已获得，**只证明显示**，不证明点击、picker、导入、同意、注册或生成。
- XCTest `enabling automation mode` 超时、WDA等待超时；CUA `Sky Computer Use native pipe startup failed`，一次受支持reset后仍失败。不要无变化重试或绕过工具使用其他UI输入通道。
- 截图可用官方 `xcrun devicectl device capture screenshot`；旧首次切换动画截图不算稳定渲染。不要再次截图当作新的功能验收。
- 两 Apple alias已停，普通 `io.ondeviceai.opencv.demo` 恢复；alias和MLX模型保留供复用。
- F09：20静态双slice XCFramework、可复用正式packager、direct/ZIP消费者、真机固定图片/视频真实推理均过；OrderedCollections262、MLX230逻辑测试/295case、tools297已过。用户批准的固定兼容补丁仅在隔离binary lane，生产lock不改。**不重建F09**。
- F09独立packager审查、SDK源码再分发许可与生产交付策略仍缺。旧reviewer429不算批准，不重试失败代理。
- 详情：[Apple 候选](validation/apple-committed-candidate.md)、[F09 分发](validation/apple-mlxvlm-distribution.md)。

## 最近实际进展：TECNO 精确候选 TTS

最近验证手机 serial `15365375C9007986` / TECNO KM9 /API35，ADB已恢复授权，但USB会短断。
这不是新会话已验证的在线状态，开始设备操作前重查。

证据目录：`artifacts/verification/android-exact-physical-tts-20261105/`。
summary SHA：`0ff5a3dacc89409ccd2257875368eb9b027c7ed03107ada1ebae975ae5d7ac58`。

1. 手机已有 `com.google.android.tts` 服务，`tts_default_synth` 仍为 `null`；实际可以绑定，不再描述为手机无引擎。
2. 临时安装精确 b90e 普通 APK 与现有 `TtsProbeInstrumentation` 测试 APK，test SHA
   `b6d7dc915254f8ad3e3fd908bdde0ed63aa0dd72b3c4ad959830b6fe4fbacb1a`。
3. PID23421、23591、23879均有同序 `normal-start → normal-done → cancel-request completed=false → cancel-joined → followup-done`。
   证明当前候选实际执行了正常播放、活动取消等待和后续重入。
4. 三轮host均exit255；每轮确认目标PID/活动instrumentation缺席，且系统exit-info为 `due to finished inst` 后才进入下一轮。
   没有因观察中断并发重启；这些退出记录不等于pass。
5. 第一轮stdout、第二轮设备端文本、第三轮官方 `am instrument -f` protobuf都只留下开始状态。
   `-f /data/local/tmp/…` 实际文件在 `/sdcard/data/local/tmp/…`，已回读SHA，153字节只有start-status，无session-result。
6. **最终pass/result bundle、close断言、voice/outputBytes未证**；三个已观察生命周期不得写成3项最终测试通过。
   不重复这三种旧观察方案。新会话若继续，应改变结果持久化机制，而不是重新跑相同命令。
7. 原包首次流式恢复因USB失败；确认仍为候选后，完整USB push原APK、设备SHA校验、本地 `pm install -r` 恢复成功。
   临时APK、文本、protobuf均按精确路径/摘要清理。

最后确认恢复的普通手机状态：

- main：`artifacts/verification/sherpa-asr-20261105/final-demo.apk`，SHA
  `afed156b40bcd8be0bcf0c5b373a0b30dd5a3daa4c7bd392b3655c7b6926c461`。
- test：`artifacts/verification/sherpa-lifecycle-20261105/original-test.apk`，SHA
  `7b9e47ce9a9b9fe51c673c002697a16c3fe4dd6ee382f70c3a32c3c9493a621d`。
- runner：`io.ondeviceai.demo.test/io.ondeviceai.demo.IdentityModelInstrumentation`。
- 12份模型/附属摘要、相机麦克风权限、TTS服务/设置与前置快照相同，普通MainActivity已启动。
- 授权LM、ASR、VAD和SR模型保留。旧手机afed上的ASR“基本正确、少量漏错词”是历史用户验收，不是b90e新采音证明。

## 下一项：刚提出、尚未实现的 TTS 私有报告方案

用户恢复目标后，代理提出：**测试结束前在target App私有临时目录持久写入最终状态，回读报告不依赖ADB全程在线**。
仅提出后读了既有文件，用户随即要求交接；**没有源文件、构建配置或设备状态被此方案改变**。

建议新会话按以下顺序执行，不预先假定它已经可用：

1. 新鲜检查worktree、9保护摘要、index、生产lock、手机授权/包/runner/模型。不要再次执行旧三轮TTS命令作预检。
2. 阅读原测试：`demo/androidApp/src/androidTest/kotlin/io/ondeviceai/demo/TtsProbeInstrumentation.kt`；
   原构建方法：`artifacts/verification/android-candidate-runtime-20261105/build-test.py`。
   Gradle已支持 `-PodaiTestRunner=<class>`，原测试build收据/命令在同目录；依赖与工具链都使用既有缓存。
3. 在**新的隔离测试构建区**实现test-only recorder/runner：固定唯一私有JSON路径，成功与失败都保存，含runId、PID、精确sdkCommit、阶段及原有断言结果；
   在 `Instrumentation.finish` 前完成写入/关闭。保留原SDK执行/取消/close断言，不能把“写报告成功”替代真实结果。
   不记录音频、用户转写或私人数据。不能修改普通 b90e APK或冻结源码/旧证据目录来伪造同候选来源。
4. 优先使用不同runner类及测试源码overlay/独立build目录，避免覆盖原冻结测试产物；具体 Gradle 注入方式尚未验证。
   只构建必要test APK，不引入新依赖，不修改生产lock，不把新test-only文件说成原953 committed源码的一部分。
5. 用实际报告行为验证终态可回读、失败不误报pass；之后最多执行必要的一次固定文本真机验证。
   ADB中断时先观察同一请求/报告/进程，不立即重启测试。报告未完成不算通过。
6. 先收集并核对报告，再精确清理本次临时文件；原main/test/runner/模型/权限必须恢复并核验。
   连接不可靠时按上一轮“完整USB复制→设备SHA→本地安装”的已验证恢复方式，避免盲目重复流式安装。
7. 如有新的可靠结果，更新Android验证文档和新readiness快照；旧原始证据不覆盖。
   如仍无终态或出现产品问题，保留失败、定位实际层，不再制造更多相同运行记录。

这只是下一条可尝试的技术路径，**不是绕过PAD校准、独立审查或发布门禁的方案**。

## 最新验收索引与只读复核

最新指针：`benchmark/p11/grouped-report-manifest.json`。
readiness：`artifacts/verification/p11-readiness-20261105-exact-android-tts/summary.json`，SHA
`7ce409aa54984b9962a3331c5f5768e6e0f77ef605d4676c0671a37bad699565`。
该轮已校验797证据引用/547来源hash；9行均有gap，8组benchmark完全不变，0新样本。

```sh
rtk proxy git status --short
rtk proxy git rev-parse HEAD
rtk proxy python3 artifacts/verification/android-exact-physical-tts-20261105/verify.py
```

TTS verifier验证日志/恢复/缺失结果记录的一致性，**不代表最终TTS test pass**。
当前新交接是独立新增文件，不修改readiness已哈希的历史文档；无须为交接本身刷新旧快照。
以后修改已索引文档时，需新快照，保留来源和gap边界，不覆盖旧summary。

## 仍未完成的真实门槛

完整清单及退出标准见 [F01–F12 后续专项](validation/foundation-followups.md) 和 [P11矩阵](validation/p11-status-matrix-20261027.md)。

- **F10 PAD/识别**：双端检测/对齐/embedding/注册删除和精确1:N已接；生产PAD/1:1/1:N决策仍缺获批协议、授权genuine/attack数据、校准阈值。
  不将原始分数实验接成已校准生产backend。原计划Task10明确缺授权数据/选择时保留typed unavailable。
- **F02/P4**：Skiko补丁本地/有界真机证据已取得，但生产分发所有权、原TLS/析构归属与长期资源门槛仍未关闭。
- **F09/F12**：独立packager/完整分支review、源码再分发许可和生产交付策略未完成；429和作者自审不算独立批准。
- **设备/UI验收**：Apple触摸/picker/当前候选生成、Android当前候选更广真机/provider和最终TTS报告仍有缺口。
- **用户已后置的专项**：最低系统iOS15/API26真机、自然质量/CER/WER/强噪声/中文、GPU/能耗与持续资源、Android异步LM、部分process-death corner。
  不为追求全绿擅自扩大采集/下载授权，不把后置项删除或改成通过。

新会话启动可直接说：

> 阅读 `.worktrees/foundation/docs/HANDOFF-2026-11-05-next-session.md`，从“尚未实现的TTS私有报告方案”续接。
> 保留原9个保护路径、普通手机App和模型，不重复旧三轮观察方案或F09构建；功能优先，发布缺口保持真实。
