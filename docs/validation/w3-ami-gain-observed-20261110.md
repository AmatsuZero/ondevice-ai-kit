# AMI 固定 +20 dB VAD 真机对照

**固定增益候选按事前规则拒绝。** 三片原幅度检测为 false/false/true，+20 dB 为 true/false/true，仍未解决 C-294 漏检。未继续扫增益、阈值、样本或模型；未启动 Whisper，不能给出转写质量改善结论。

本批使用[冻结协议](w3-ami-gain-protocol-20261110.md)、既有三片 public AMI PCM 和同一 Silero 模型/Android runtime。TECNO KM9/API35，PID27834，唯一 run `w3-vad-gain-20261110-01`；六个 fresh VAD、六个释放成功，前台退出真实 ack、终态双读一致。原始报告 SHA-256：`b333bdf753cc5c04adff72bd5f0dc68746a05797026c3d63cb9b4b140cdb1d5e`。零识别器调用、零麦克风、零下载。

| 片段 / 增益 | 分类窗口 | speech windows | speech samples | 最大概率 | 检出 | 准备后采样数 |
| --- | ---: | ---: | ---: | ---: | --- | ---: |
| D-330 / 原幅度 | 109 | 0 | 0 | 0.461406 | false | 0 |
| D-330 / ×10 | 109 | 84 | 43,008 | 0.993117 | true | 46,416 |
| C-294 / 原幅度 | 111 | 0 | 0 | 0.100003 | false | 0 |
| C-294 / ×10 | 111 | 5 | 2,560 | 0.639810 | false | 0 |
| B-146 / 原幅度 | 138 | 63 | 32,256 | 0.983136 | true | 38,656 |
| B-146 / ×10 | 179 | 160 | 81,920 | 0.998950 | true | 89,792 |

原幅度检出标记与此前实际 SDK 的两次 no-speech/一次成功一致，满足事前等价性检查。增益提高了本批分类概率，但 C-294 仍只有五个 speech windows，低于原 4,096 speech samples 门槛；**不事后下调该门槛**。B-146 原幅度在70,784输入采样处 endpoint，×10处理完91,712；这是输入变换影响，不证明额外保留内容正确或质量更好。增益同时放大语音和噪声，不能称 SNR 提升或证明所有错误均来自低幅度。

离线 55 项 host 断言、实际 Android build/lint、APK 签名及无权限检查已验。三份 PCM asset 与原输入逐字相同，四份 native 库 SHA 与原 AMI 相同，12份生产/保护文件保持。主代理仅集成免费 hy3 的原样文件/精确修正，原编译与 host 失败全部保留；不把助手成功响应当验收。

只卸载本批两别名、清理精确远端暂存；六个原 App 路径、普通 App 四份模型 SHA 前后相同，真实 resumed Activity 已回到 `io.ondeviceai.demo/.MainActivity`。恢复、主机验收和原始报告保留在本机 `.omx/artifacts/w3-ami-gain-investigation-20261110/`；可跟踪[数值报告](w3-ami-gain-observed-20261110.json)。

当前 R4/W3 仍 open。这个结果结束固定增益假设，没有生产变更或新增 ASR 结论；后续其他假设须独立冻结协议，不复用本批为识别或噪声安全通过。
