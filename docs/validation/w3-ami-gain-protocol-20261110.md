# W3 AMI 固定增益 VAD 诊断协议

这是针对既有两次 `SpeechNotDetectedException` 的新单变量诊断，非识别重放或生产修复。用户已要求继续寻找有证据支持的改进。低幅度可能影响检测，但 RMS 不是 SNR，也不能单凭幅度认定 VAD 根因。

## 固定条件

使用既有 AMI `ES2002a/Array1-01` 三片，按 D-330、C-294、B-146 顺序，每片原幅度、统一乘以 `10f`（+20 dB）两臂。输入 s16LE 解码为 float 后才在临时数组放大，不写出音频。原裁剪、采样数、reference 与输入 SHA 保持。放大同时改变语音和噪声幅度，不能声称改善 SNR。

| 片段 | 采样数 | 原 RMS dBFS | 原绝对峰值 | 放大后绝对峰值 |
| --- | ---: | ---: | ---: | ---: |
| D-330 | 56,016 | −44.8613 | 0.0377197 | 0.377197 |
| C-294 | 57,072 | −47.1281 | 0.0209045 | 0.209045 |
| B-146 | 91,712 | −50.0306 | 0.0217285 | 0.217285 |

所有放大采样均小于满幅，无剪切。使用本地既有 Silero 模型：643,854 bytes，SHA-256 `9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6`；Android 运行库仍 `com.k2fsa.sherpa.onnx:sherpa-onnx:2.0.1`。每臂 fresh VAD、CPU/单线程、512 采样窗口、阈值 0.5，原 `UtteranceBuffer.kt` 字节不变。观察只使用实际原分类器调用，不为统计再次调用。

## 验证及停止条件

1. 助手仅交付两份隔离源码候选；主代理审查、构建和验收。确认所有输入及实际磁盘模型 SHA 后才允许 JNI。
2. host 验收覆盖窗口数/端点、少于 4,096 speech samples 的拒绝、原数组不变、非法概率/输入、异常传播及精确一次释放。Android fresh build/lint 与最终 APK 权限/库摘要另行检查。
3. 真机仅在当前亮屏、已解锁且无其他 instrumentation 时执行一次六臂 VAD-only 批次。不开麦克风，不启动 Whisper，不下载、不改设置。先持久化 running；每臂及最终释放/前台退出后持久化结果。同一次结果双读后恢复本次别名和暂存。
4. 仅 `SpeechNotDetectedException` 记为 `detected=false` 并继续；其他初始化、分类、释放、报告或前台错误使批次失败，保留已取得行，不自动重发。
5. 固定增益若不能使原 D/C 两次漏检均变为 detected，或令原 B 成功片段退步，则停止该候选，不扫描其他增益、VAD 阈值、裁剪或样本。
6. 若信号成立，才冻结后续独立 ASR 质量协议。检测增加不能证明转写正确、误检安全或产品 AGC 适用，不能据此关闭 R4/W3。原失败及 scorer 保持；没有本协议产生的 CER/WER。

## 证据与当前状态

机器协议：本机 `.omx/artifacts/w3-ami-gain-investigation-20261110/protocol-freeze.json`，包括输入/模型摘要、无剪切复核及事前停止条件。外部当前上游资料仅作背景参考，不能替代固定 AAR 的语义或实测。

候选来源：免费 hy3 原始任务及独立定点修正，原稿与全部失败保留。最终源与修正逐字集成到[探针](../../probes/android-vad-gain/README.md)。55 项 host 断言、Android build/lint、实包无权限/三 PCM 一致/四 native 库与原 AMI 相同、12 份生产及保护文件摘要核验通过，见[离线验收](w3-ami-gain-offline-acceptance-20261110.json)。原稿曾 6 个 host 案例失败及编译失败，首修正仍 1 个案例失败及 Float 字面量编译错误；没有把这些候选装机。

用户解锁后唯一六臂 VAD 批次已完成，原幅度 D=false、C=false、B=true 与此前 SDK 一致，×10 后 true/false/true。按事前条件拒绝固定增益候选，不继续 ASR 或扫参；[真机结果与恢复](w3-ami-gain-observed-20261110.md)已验。旧麦克风提示及资源下载授权未复用，质量仍未通过。
