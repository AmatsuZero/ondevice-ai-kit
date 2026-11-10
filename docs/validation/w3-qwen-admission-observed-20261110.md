# Qwen 后续准入诊断：Silero v5 未满足条件

13/13 VAD 调用完成，**拒绝当前准入候选：9 条语音只准入 8 条**。
四条非语音均拒绝，未调用 ASR，也未修改原幅度或裁剪送识别的输入。
事前条件与固定配置见[协议](w3-qwen-admission-protocol-20261110.md)，
逐条 segment、输入摘要和错误见[机器结果](w3-qwen-admission-observed-20261110.json)。

| 输入组 | 观察 | 事前要求 |
| --- | --- | --- |
| FLEURS 中文三片 | 3/3 准入 | 3/3 |
| LibriSpeech 英文三片 | 3/3 准入 | 3/3 |
| AMI 原幅度远场三片 | D/B 准入，C-294 无 segment | 3/3，失败 |
| 两条 5 秒全零 PCM | 2/2 拒绝 | 2/2 |
| 固定 seed 高斯噪声，nominal -40/-20 dBFS | 2/2 拒绝 | 2/2 |

这是独立分类诊断，不是 Qwen 组合管线质量验收。Qwen 已能识别 C-294，
但此准入器会阻止它进入识别；因此不能用该 VAD 直接补上 Qwen 的静音缺口。
不对同一批调阈值、增益或最短时长，也不把合成非语音当真实环境噪声验收。

固定 Sherpa 2.0.1、Silero v5 实取 SHA256
`6b99cbfd39246b6706f98ec13c7c50c6b299181f2474fa05cbc8046acc274396`，
官方资产没有发布 digest；CPU1thread/window512/threshold0.5/minspeech0.25s/
minsilence0.5s/maxspeech30s。每片 fresh VAD，全文件处理、末窗补零、flush。
13/13 原始浮点波形处理前后摘要一致，总耗时约 0.71s，`asrCalls=0`。
Python 引用清理不作为 native 析构确认；本批不证明手机性能或生产预算。

免费 CBC hy3 的原稿因终态过早、持久化及输入预检问题拒收，修正后 5/5
fake-runtime 故障检查通过，才执行真实批次。原稿、修正、原始事件及失败均保留。
真实报告终态双读一致，原始 SHA256
`53afe63714f7d931cd7acd3e315042c768ad92a4c01cce45180dc77d467e737d`；
协议 SHA256 `e4fbb162bc82cfc0bdaa2920596dce62dd9124d2c0f7ba8241e85cd9a57622b5`；
runner SHA256 `8c8d1aa49ad7347cadc22dfc9e81ef4652363823ee80f2cb8a99dac79a694904`。
证据根为本机 `.omx/artifacts/w3-model-reselection-20261110/`。

TEN VAD 许可证含额外竞争/应用限制，本次未下载或运行，也未作为通用 SDK 依赖。
生产源码、普通 App/模型、默认服务及网络配置保持；无麦克风或 AiAi 资源请求。
W3 保持 open，下一步核查原生静音处理及语言提示的单变量对照。
