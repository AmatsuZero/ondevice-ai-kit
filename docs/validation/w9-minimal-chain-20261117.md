# W9-0 最小文件变声链路验收（2026-11-17）

结论：**主代理接受双端离线文件变声可行性，工程合同已冻结；W9 产品与本周交付仍 not-ready。** 原型已实际执行原声复制、升/降4半音并输出有效PCM16 WAV；SDK、KMP/Demo、录音/试听/导出与真机尚未交付。W3/W6继续暂停。

## 实际验证

| 执行环境 | 结果 | 原始收据 |
| --- | --- | --- |
| macOS / AVAudioEngine | 74/74 | `apple-file-acceptance.json` |
| JVM / Kotlin | 74/74 | `android-file-acceptance.json` |
| Android API37 arm64模拟器 / ART app_process | 74/74 | `android-emulator-file-acceptance.json` |
| iOS27.2模拟器 / IOSSIMULATOR minOS15二进制 | 74/74 | `ios-simulator-file-acceptance.json` |

共296项文件检查。每环境包括27组稳态音（16/44.1/48kHz × 220/440/880Hz × 三预设）、27组静音/单帧/30秒边界、14组坏/空/超限输入、4组非法半音、既有输出保持和同路径拒绝。核对实际PCM格式、精确帧数/采样率、原声逐字节复制、输入保持、错误不留意外文件与稳态频率。原始收据内通用checker的host边界文字保留；实际执行环境以上表及适配器日志为准，不改写旧收据。

- Android纯算法27组渲染及非法参数/静音/短/最长输入通过；27组f32信号频率检查最大相对误差0.00582743，约0.583%。采用中位零交叉估计，不是FFT、MOS或人声质量指标。
- 3段已有公开LibriSpeech人声×双host×三预设，18个输出格式/帧数/能量通过；不是可懂度或听感接受。来源为既有fixture，固定revision `5be91486e11a2d616f4ec5db8d3fd248585ac07a`，CC BY4.0，摘要与归一化来源见 `public-voice/provenance.json`。
- 4组首尾80ms能量/中段静音检查通过，仅证明该控制信号未整体丢失，不证明逐样本瞬态保真。
- Android首个模拟器输出与JVM逐字节一致；实际ART测试不是App/OEM/最低API26接受。Apple iOS15 typecheck通过不是iOS15实体设备接受。

## 候选与复现

原型：[源码与校验入口](../../probes/voice-effects-feasibility/README.md)。[冻结合同](../voice-effects/contract-v1.md) SHA-256：`87f890278a07a457d656970a16ca3001ef034cd05e9001b655eb2b84779b86d2`。

候选由已授权本地 `qwen3-coder:30b` 提供，主代理审查、精确组合订正并集成；未新增库或模型。源码身份见 `candidate-source-identity.json`、`integration-map.json`。验收时Apple候选原字节5935B，SHA `d2bd542ead8a287e3966bdb0ba0d4dc93cd0b4a7e942d2aa2c74d0025e2a6b02`；PitchCore1637B，SHA `31e74b2b536b8108a99e98c6166e826c5edb3ace8c95bf9a02c0cc74a8c55c90`；WaveProbe4174B，SHA `d515eec4b56bdd0eea28a886a693c8588b0f228fb94bc25416441740d94889ae`。

证据根：`.omx/artifacts/w9-minimal-20261117T071421Z/`；保留各次prompt/raw/候选/拒收内容、编译与运行日志、实际输出及收据。复现脚本另归档 `harness/`，checker见原型verification目录。Android使用既有OpenJDK17与缓存Kotlin2.4.0；`android-file-build/runtime-classpath.txt`、`harness/compile-core.py`保留实际编译上下文，`harness/emulator-adapter.py`及 `emulator-adapter-events.jsonl`保留ART入口；Apple编译、iOS平台字段与首运行见 `apple-reviewed/`、`apple-ios15-typecheck.log`、`ios-simulator/first-run.json`。

首稿出现Android负窗口索引崩溃、订正API/Float-Double编译错误；Apple首稿/订正有API、RIFF长度和条件编译问题，另一个订正仅返回占位片段，均未接受为最终实现。最终文件来自已验证的精确小补丁，不能将首稿或模型自报结果当通过。

## 保留限制与后续门

1. 输入为canonical 44-byte mono PCM16 WAV、三种采样率、1帧至30秒；非canonical导入须宿主归一化。Apple输出合法附加RIFF chunks，直接重喂原型被拒；Android重喂可接受，见 `reimport-observation.json`。
2. Android双读头窗化有补零/边界伪影；两端算法不同，未承诺相同音色或自然男女声转换。人声听感、可懂度、爆音/截断与物理设备仍须验收；本轮未录音或硬件播放。
3. 原型使用独占路径，尚无SDK admission/cancel/drain/close/release租约与并发发布保证，不能直接包装为完成的SDK。
4. D8成功产生DEX且ART全套通过，但原 `android-d8.log` 有Kotlin metadata2.4超过当前解析器2.2的警告/异常；不声称release工具链已兼容或clean build。Apple编译警告亦保留。
5. 最终独立合同/代码复核收到 `429 Too Many Requests`，无本轮最终结论，线路已停止。此前文档ACCEPT与较早原型局部意见均不冒充本次完整接受；主代理可行性接受与独立复核缺口分列。
6. A/B/C可按同一合同交付Apple SDK、Android SDK、KMP/Demo候选，当前尚未派发；共享配置/Host/factory由主代理统一接线。独立消费者、生产组合构建、资源/通知/最低系统及同候选发布复核仍开放。

任务专用模拟器清理与1261项原dirty/untracked保护结果见本证据根 `cleanup.json`、`final-verification.json`；没有提交、推送、tag、上传或发布，未修改生产SDK/Demo或普通App/模型。

## 提交前核验补记

2026-11-17提交整理仅删除Apple源空行的尾部空白，词法内容保持；提交源5823B，SHA `23ba6772a9ca287d8cc01295e498e8813a1ebb9bd7300f620863522ad629d447`，原候选与原收据不改写。新构建及双host文件检查另存 `.omx/artifacts/w9-submit-20261117T080139Z/`。首轮Android复验launcher误写 `WaveProbeKt`，实际为`object WaveProbe`的`@JvmStatic main`；原ClassNotFound失败保留，只修launcher并复验，不归为算法回归。原独立复核429缺口保持。

提交前Apple与Android当前源码重新编译并各74/74文件检查通过，另有Android27组算法渲染通过；摘要见[机器收据](w9-minimal-chain-20261117.json)。源码未接生产SDK/Demo。
