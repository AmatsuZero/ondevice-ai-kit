# W9-0 文件变声原型

最小链路：canonical mono PCM16 WAV → 原声/+4/-4半音 → 有效PCM16 WAV；保持采样率和精确帧数。原型不包含SDK生命周期、录音/UI或发布集成。

- `apple/AppleProbe.swift`：macOS可执行入口与iOS可编译的转换函数，使用AVAudioEngine离线渲染。
- `android/PitchCore.kt`：无新依赖的原创双读头变调；`android/WaveProbe.kt`：canonical WAV文件入口，Java/JVM与Android ART都已执行。
- `verification/`：主代理的独立验收程序。`ProbeChecks.kt`覆盖纯算法；`check_signals.py AUDIO_DIR RECEIPT.json`分析27组f32；`check_files.py TASK_ROOT SURFACE COMMAND_JSON`对同一文件入口执行74项检查。TASK_ROOT须已具备本轮的fixtures且结果目录不能已存在，避免覆盖旧证据；命令中Android的classpath须来自真实已编译产物。

本轮实际命令、候选身份、失败与最终结果保存在 `.omx/artifacts/w9-minimal-20261117T071421Z/`，可读结论见[验收报告](../../docs/validation/w9-minimal-chain-20261117.md)，后续实施遵循[合同](../../docs/voice-effects/contract-v1.md)。

候选来源是已授权本地 `qwen3-coder:30b`，主代理审查并组合精确候选订正；校验脚本由主代理提供。保留首次崩溃/编译失败与拒收订正，不能将模型自报完成当成接受。

限制：输入只支持canonical头，Apple生成的合法附加chunk输出再次导入前须归一化；Android窗化有边界补零/伪影；两端音色不保证一致。原型仅使用独占任务目录，普通move/rename不构成SDK并发发布保证；不应直接用于生产入口。不得自动录音、播放到硬件、改普通App/模型或新增依赖。
