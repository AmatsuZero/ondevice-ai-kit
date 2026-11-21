# W3语音验收与后续改进 — 2026-11-10

<!-- w3-result-snapshot-20261110 -->

截至 2026-11-10，已完成本轮固定实验与证据归档；**W3仍open，质量和资源预算尚未通过**。用户最新明确要求“如果你还有可以改进的方式就继续改进吧”，已选择继续寻找和执行有证据支持的改进，不按接受现有限制关闭W3。

## 最新推进决定

用户无需自行制定技术阈值；主代理负责提出质量目标、改进方案及资源验收建议，再按真实证据判断。此前“待答R4/R5”是决定前的检查点，现继续改进，不重复要求用户接受现状。原TECNO R1–R3已知限制的接受继续有效；不新增麦克风采集、AiAi下载或重试。新实验先冻结假设、输入、评分和停止条件，不盲目扫模型或重放同等实验。

关联原跟踪：#4（iOS Speech/TTS）、#5（Android ASR）、#11（质量与资源）。原 issue 的完整范围不因本记录自动关闭。

## 双端质量与诊断结果

| 固定实验 | 已观察结果 | 验收边界 |
| --- | --- | --- |
| FLEURS 中文三片、两端同一 PCM 波形 | Apple CER **16/92**；Android Sherpa/Whisper tiny **44/92** | 文件链路可运行；样本小、后端及分段不同，不能唯一归因模型或宣布生产准确率通过 |
| no-VAD / Whisper base 对照 | no-VAD **53/92**，不是修复；base **43/92**但首片从15错退步至18错，按事前规则拒绝 | 生产模型/VAD/评分规则未因此改变，不继续扫参 |
| FLEURS1906 第二 slice 固定前缀 | 仅恢复被裁掉的2,432 samples（152ms），输出由`也是新天章的开始`变为`结束也是新天将的开始` | 两臂真实报告、62项host断言、构建/lint及97项父检查已验；无独立crop reference，不能声称CER改善 |
| AMI 英文真实会议远场三片 | Android **1/3 recognized、2次typed no-speech**；Apple **2/3 recognized、1次平台1110** | 六个端/片段组合跨不同run已有真实观测；只补原未执行组合，原失败批次保留，不能拼接为同批全成功 |

公开来源：Google FLEURS `cmn_hans_cn/test`，revision `70bb2e84b976b7e960aa89f1c648e09c59f894dd`；AMI `ES2002a/Array1-01`，三段共12.8s；两者均按已核实CC-BY-4.0材料保留来源与reference。AMI仅覆盖一个英文会议，不能外推中文或全部噪声场景。原scorer保持NFC/casefold、保留标点/数字/简繁差别；typed error不伪造为空hypothesis。

## 当前 TECNO 系统 ASR 的已知限制

用户已明确选择“将TECNO限制纳入验收范围裁定”，仅针对当前TECNO KM9/API35的系统on-device ASR配置：

- R1单次普通App显式系统后端请求仍为`FAILED / BACKEND_UNAVAILABLE / MISSING_ASSETS`；仅留状态/错误码，不保存音频或自然转写。
- R2背景语音、Android R3中断/取消/重入余项因资源前置未满足而未执行；按本机已知限制接受范围，**不是功能通过**。iPhone既有生命周期证据保留原范围。
- 实际服务是AiAi，与Google TTS的voice默认服务不同。唯一获批资源SDK调用返回`AsrResourceException / error15 / ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS`，`readyClaim=false`。
- 错误15是“不支持下载事件监听”，**不是VPN/网络失败证据**，也不证明所有资源供应路径均不可行。一次授权已消耗，无备用API或重试。
- 不再为R1–R3等待解锁、换机或安排录音/下载；未来扩范围须另行确定。Sherpa文件路径未被排除，不能概括为整个TECNO或全部Android不支持语音。

## 已接受的持续资源观测

| 路径 | 已测范围 | 进程RSS观测 |
| --- | --- | --- |
| TECNO / 当前来源Sherpa | 20 fresh backends、40 measured calls全成功；600.503s，含506.508s pacing idle；265项父检查、双读及恢复通过 | 26,222样本，sampled max **457,220,096B** |
| iPhone17,2 / iOS27.2.1 / Apple FILE ASR | 20 fresh cycles、40 measured successes，另1 excluded warmup；626.211590s，含593.928s paced idle；428项父检查、双读及44原App记录/路径恢复通过 | 898 measured samples，sampled max **113,082,368B**；仅识别调用期间采样，包含探针UI、不连续覆盖idle、不含系统Speech服务内存 |

这些是有界观测，不是600秒连续解码、真实峰值、零泄漏、GPU或能耗证明；生产预算仍`unverified`。Apple配置on-device API要求，网络隔离未在此批测量。原App已恢复；Apple本批未操作普通模型目录，也未重新hash普通模型内容。已验Apple TTS、Sherpa及Apple FILE ASR批次不重跑。

## 后续改进与关闭条件

- [x] R1–R3：当前TECNO配置已知限制的范围裁定完成，真实failed/not-executed状态保留。
- [ ] R4：按用户最新选择继续改进。主代理先给出可验证的质量目标和单一改进假设，固定独立评测与停止条件；当前质量限制未被接受为关闭依据。
- [ ] R5：由主代理继续提出适用缺测项、可靠测量可行性及预算建议；需要产品取舍时给出具体可审结果。W8其他能力不因此豁免，不把旧测量重跑当进展。
- [ ] 根据上述决定逐项写回结果并完成最终关闭复核；当前不得关闭W3或宣称release ready。

原设计规定报告CER/WER、时延、内存及有可靠手段时的能耗等，没有给定每能力/设备的数值生产阈值；不能从已知得分反推通过线，也不能因未设阈值而默认通过。基线已经完成；用户已选择继续改进，仍不能据现有分数事后宣布产品通过。

## 可追溯性与存放位置

本地已提交：`b25ba84`（固定前缀探针/回归）、`cfd5518`（可复算质量材料）、`2cb32d1`（Apple持续文件ASR）、`17a6c6c`（本轮验收检查点）、`37f1e25`（有界缺口复审）。**这五笔尚未由本任务推送**，不把这些本地路径冒充远端可访问源码链接。

文档路径：`docs/roadmap/W3-speech.md`、`docs/validation/w3-quality-evidence-20261110.json`、`w3-acceptance-checkpoint-20261110.json`、`w3-exit-audit-20261110.md`及对应W8验收记录。原始报告保留在foundation的ignored `artifacts/verification/`，没有作为issue附件上传。

关键原始SHA-256：

- 固定前缀run02：`6b23710526d8c8d8dbde78075cff6d70f419fa7dd898b0f361ae4e7a2bba6d74`。
- 系统资源error15：`580d628a6daffd33c3319d1d15195fc51a92f7938a09c39a7e1c9b34cf161f7c`。
- Sherpa持续观测：`7f4854fc0d05176fda1421602a5fd02ebfc4577e8c0d5ca9a8c55002cbda31d5`。
- Apple FILE ASR持续观测：`1d6f4185cee55521f0f87d4a4d9077a4a3a245af552774ccfebf63dfd02b5862`。

8份唯一公共质量原始报告、5份TECNO裁定证据、20份生产/保护文件摘要及提交内容已由主代理核对。有界免费hy3复审未指出当前边界内新的可直接执行验收项目；它没有工具/运行时访问，不是独立重验原始报告或最终产品批准。没有因本次记录启动新的识别、录音、下载或模型调整。


## 在线跟踪

- [GitHub W3 #15](https://github.com/AmatsuZero/ondevice-ai-kit/issues/15)
- [GitLab W3 #15](https://clients-gitlab.ugreeniot.com/home/common/ondevice-ai-kit/-/issues/15)
- [GitHub W3 Wiki](https://github.com/AmatsuZero/ondevice-ai-kit/wiki/W3语音验收与后续改进)
- [GitLab W3 Wiki](https://clients-gitlab.ugreeniot.com/home/common/ondevice-ai-kit/-/wikis/W3语音验收与后续改进)

发布回读已完成：两端 #15 保持开放，原 #4/#5/#11 各一次关联评论；GitLab 四页 Wiki 逐字回读，GitHub 四页和 Home 导航以 Wiki 提交 `94b19255c3e585e9654f814ae7cc2be502ea9fc9` 发布并逐文件核对。GitLab issue 服务裁去正文末尾换行，其余内容一致；未因回读差异重复发布。

本任务仅 push GitHub Wiki 仓库。同期只读复查发现 GitLab 源码分支已由 `4ee9b72` 独立前进至 `37f1e25`，GitHub 源码仍为 `11134567`；两端源码不同步，不能将该变化归因本次发布。完整 URL、评论 ID、页面 SHA 和前后源码状态见[机器收据](../validation/w3-issue-wiki-sync-20261110.json)。

## 最新改进实验

新增六臂VAD实测：原AMI三片检出false/false/true，+20dB后true/false/true，未解决C漏检，按事前规则拒绝固定增益。55host断言/构建/lint及原App恢复通过；[完整结果](../validation/w3-ami-gain-observed-20261110.md)。

随后独立复用已有FILE no-VAD控制，三段原文件均返回非空文本，但WER21/27（77.78%），D/C各8/8词错误、B仍5/11；功能信号满足，准确率没有解决，不采用生产全局绕过。[完整结果](../validation/w3-ami-no-vad-observed-20261110.md)。两批均唯一提交、终态双读和模型/App恢复完成，未开麦克风或下载。原基线及全部失败保持，不据此宣称W3完成。下一阶段评审模型/场景与资源取舍，停止当前前处理参数尝试。
