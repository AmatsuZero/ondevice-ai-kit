# 参考音色 Demo：实现与验收（2026-11-28）

当前 CMP iOS / Android Demo 已接入“参考音频 + 内容音频 → 本地 OpenVoice 转换”。参考模式保留真实波形、原声/结果对比、点击或拖动定位、试听、清空与 WAV 导出；原有固定 A/B/C、升降调和实时角色入口继续保留。

这是本地开发、模拟器及 iPhone 样例验证记录。真机麦克风双录音、耳机/外放听感和音色相似度尚未验收，不能据此宣称完整真机验收或发布就绪。

## 实现

| 范围 | 文件 | 行为 |
| --- | --- | --- |
| CMP 状态与界面 | `demo/shared/src/commonMain/kotlin/io/ondeviceai/demo/VoiceEffectsViewModel.kt`、`VoiceEffectsScreen.kt` | 固定/参考子模式，01/02/03 卡片，等宽且 48 dp 高的输入按钮，双输入及结果生命周期 |
| 波形 | `VoiceWaveform.kt`、`demo/shared/src/iosMain/kotlin/io/ondeviceai/demo/VoiceWaveData.kt` | 读取实际 PCM16；128 个峰值区间；比较图共用时间长度与 −60 至 0 dBFS 标尺；播放进度线 |
| iOS | `demo/iosApp/VoiceEffectsHost.swift` | 参考录音/选取、输入租约、CPU 转换、定位播放、导出、页面离开清理 |
| Android | `demo/androidApp/src/main/kotlin/io/ondeviceai/demo/VoiceEffectsHost.kt`、`OpenVoiceAudio.kt` | 对应 Android 录音/选取、JNI、媒体焦点与租约清理 |
| 原生模型 | `demo/openvoice/OpenVoice.cpp`、`OpenVoice.h`、`AndroidOpenVoice.cpp` | `openvoice_render_reference`，参考声纹只在单次转换内存中持有 |
| 样例 | `demo/openvoice/assets/reference-content.wav`、`CREDITS.txt`、Xcode 资源清单 | 第二段普通话语音，可直接载入样例完成转换 |
| 验证 | shared tests、iOS UI tests、`ReferenceVoiceInstrumentation.kt`、`demo/openvoice/tests/reference_render.py` | 状态/波形、真实模型、双端界面验收 |

参考模式接收 0.5–30 秒单声道 PCM16 WAV，输入采样率为 16 / 44.1 / 48 kHz，输出为 22,050 Hz。沿用已有 ONNX 模型，没有新增依赖。固定路径保留原先最低 0.05 秒的输入合同和 Core ML 转换器静态形状限制。

## 已取得的证据

证据目录：`.omx/artifacts/reference-voice-demo-20261128/`。完整候选和结果包保留在 `/private/tmp/odai-reference-mobile-1795836187868/`。

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| shared iOS simulator 单元测试 | 127 通过、0 失败，包含 11 个变声状态、9 个实时会话、5 个波形用例 | `unit-tests.json`、`compile-and-shared-tests.log` |
| Android 单元测试 | 108 通过、0 失败（VoiceEffects 30、Demo 78） | `unit-tests.json`、`android-checks.log` |
| Android lint / 编译 | 通过 | `android-checks.log` |
| iOS framework、App 与测试包构建 | 通过 | `xcode-build.log` 及 UI 运行日志 |
| 原生真实参考转换 | 相同输入重复输出逐字节一致；换参考音频后结果不同；短参考/内容被拒绝；固定模式 0.4 秒输入仍成功 | `native-verified.log` |
| 固定 A/B/C 回归 | 本轮与修改前 HEAD 的原生输出逐字节一致 | `fixed-differential.json` |
| iOS A/B/C 转换并播放 | 通过，46.092 秒为整个 UI 用例耗时 | `reference-accepted-ui.log` |
| iOS 参考模式完整流程 | 通过，57.330 秒为整个 UI 用例耗时；包含样例、按钮尺寸、波形、转换、播放/定位/停止、清空、重新载入、离页再返回 | `reference-cache-ui.log` |
| iOS 实时/文件模式回归 | 通过，39.660 秒；角色切换不启动麦克风，返回文件模式保留已有输入与输出 | `live-final-ui.log` |
| Android 参考模式完整流程 | 12 个步骤通过，包含旧固定/实时入口、清空和重新载入后离页清理 | `android-cache-ui.log` |
| Android 系统保存 | DocumentsUI 实际保存；回读 457,772 字节，与转换输出完全一致 | `android-export-verified.json` |
| Android 波形定位 | 点击结果波形中心后进入播放态，截图可见位于中段的进度线 | `android-seek-verified.json`、`android-seek-playing.png` |
| Android 输入文件清理 | 独立回读加载前/加载后/离页后的文件集合；新增输入在离页后删除，原有文件保留 | `android-input-cleanup.json` |

不同 UI 用例的首次组合运行含测试定位失败；上表逐项标明通过所在运行，并不把失败的整组日志当作全绿。最终汇总为 `final-summary.json`，含测试来源及当前源文件摘要。

从两个模拟器的 App 容器实际回读的输出均为 228,864 帧、10.3793 秒、非静音单声道 PCM16。跨端 228,844 个样本完全一致，另外 20 个样本相差 1 个量化值（99.9913% 一致）。这证明该样例两端输出接近，不等于主观音色相似度达标。

- iOS：`ios-reference-output.wav`，SHA-256 `c95feba6602ec3b1acaf4065c8e2d7eb268126f5d5c524eab3208d8c26d2f869`。
- Android：`android-reference-output.wav`，SHA-256 `3e488c74713f196ddb074ce80e5e0cea37b5550a04af693430903fbf82166117`。
- 原生 Mac 两次不同参考转换分别为 3.588 / 3.285 秒；计时覆盖原生转换调用，不是手机性能数据。UI 测试总耗时包含导航、滚动和断言，不能当作推理耗时。

预览运行使用独立 bundle/package `io.ondeviceai.demo.referencepreview`。普通已装 Demo、模型与物理设备未被本轮模拟器验收覆盖或卸载。

## 验证边界

- 未进行物理设备“录参考 → 录内容 → 转换 → 实际听音”的完整链路；录音入口已实现并编译，不能用内置样例替代麦克风验收。
- iOS 系统文件保存与双端系统文件选择器导入没有在本次新参考模式中完成自动化验收；iOS 只验证结果导出按钮可用，Android 额外验证了实际系统保存。
- 未测真机转换峰值内存、耗电、温度和延迟分布。自定义参考路径为 CPU；本次没有重做固定模式 Core ML 真机测试。
- 模型只在后台任务终态释放输入；取消会丢弃迟到结果，不中断底层 ONNX kernel。共享状态测试覆盖迟到回调/释放语义，不能据此宣称每种真机中断组合都已覆盖。
- 临时页面输入与输出会在正常离页时退役；进程被测试框架直接终止后遗留的旧临时缓存不作为正常离页清理证据，也未扩大范围清理其他测试数据。

## 本地查看

进入 CMP Demo 的“离线变声 → 文件变声 → 参考音色”，点击“载入样例”，再点“按参考音色转换”。第一张卡片为目标音色，第二张为要说的内容，第三张展示原声与结果波形。录制或导入自己的两段音频时，仍走同一个本地转换入口。

原型文件继续保留在 `docs/superpowers/prototypes/2026-11-27-realtime-voice/`。初次开发阶段未提交或推送；后续主干合入与真机验证见下节。

## 主干合入前复验与 iPhone 真机截图（2026-11-28）

用户先要求本地合入，随后明确授权推送代码并更新 Wiki。本地 main 已快进到当时两个远端一致的 `3cef6a3`，保留其 4 个 CI 提交；本次参考音色改动单独提交。原型和既有固定/实时能力继续保留。

- 重新执行 127 项 shared 与 108 项 Android 单元测试，全部通过；Android lint 和 iOS arm64 framework 构建通过。
- 新主干的离线 CI 测试清单在本地执行 **574 项，全部通过**。这是本地验证，远端流水线状态另行回读。
- 独立预览包 `io.ondeviceai.demo.referencepreview` 在 **iPhone 16 Pro Max / iOS 27.2.1** 上构建、签装并运行。`testReferenceVoiceConvertsSampleAndClearsInputs` **1 项通过、0 失败**，用例总耗时 77.932 秒。
- 真机覆盖内置双输入、等宽按钮、实际波形、本地参考转换、结果播放、波形中点定位、导出按钮可用、清空、重新载入、离页再返回以及固定/实时入口。结果界面显示 **ONNX Runtime · CPU、22,050 Hz、228,864 帧**。77.932 秒包含 UI 操作和断言，不能当作转换耗时。
- 真机首次运行在清空后的状态断言失败。保留原始日志与录像；测试驱动在查询清空按钮前复用已有的有界滚到顶部操作，并增加按钮 enabled/hittable 与参考名回到占位符的断言后通过。产品代码未因此修改。相同最终测试在 iOS 模拟器复验也通过，1 项、61.954 秒。首错只证明当次清空操作未生效；不据此泛称已定位框架内部缓存根因。
- Wiki 的两张新版图直接来自**通过的真机用例**的 XCTest PNG 附件，分别为双输入波形与转换结果。截图文件、SHA-256 和采集时间见 [可下载验证摘要](reference-voice-release-20261128.json)。原始证据保留于 `.omx/artifacts/reference-voice-release-20261128/` 及其 `task-dir.txt` 指向的任务目录。

本轮没有启动真人录音，没有完成新参考模式的系统文件导入、iOS 实际系统保存、音色相似度听评或资源/性能分布测量。Android 真机未连接；已有 Android 模拟器结果及旧版真机截图分别标注，不替代新版真机验收。波形仍是在录音结束、导入或转换完成后从 PCM 生成；播放进度实时更新，录音过程不实时绘制波形。
