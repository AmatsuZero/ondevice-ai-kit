# W3 离线 ASR 候选重选

用户要求继续修正 W3。此设计由主代理制定，不表示用户批准了某个质量数字或模型上线。

## 保留与修正

保留 capability Client、显式 backend 选择、取消/释放、USB 离线供应与原生产模型。
当前 Whisper tiny 是已运行的基线；中文 CER44/92 和 AMI 两次 no-speech 尚不足以交付。
base/no-VAD/固定增益的旧拒收与功能信号均保留，不重跑或改写。已有 SDK/native 对照
仅覆盖一个 prepared 输入，不能把模型容量当作唯一已证根因。

## 候选比较与选择

| 路线 | 可核实事实 | 本次决定 |
| --- | --- | --- |
| Whisper small int8 | 现有架构兼容、MIT；约375MB模型文件；base此前仅改善1错且首片退步 | 不继续在原三片逐级扫Whisper大小 |
| SenseVoiceSmall | 中英文；模型卡仍为other/model-license，非仅凭代码许可证即可分发 | 不作为本次直接替换候选 |
| Dolphin CTC | Apache-2.0；官方40语言表未列en | 不作为当前统一中英文候选 |
| Qwen3-ASR0.6B int8 | 官方中英文、Apache-2.0模型卡；Sherpa2.0.1源与实际AAR均有接口 | 仅选择这一项，先Mac筛查，尚不提升生产默认 |

Sherpa固定v2.0.1，源码树af2d8d81dbfa61c4cea4c568da78f3982edca005。
模型使用官方Sherpa release资产390698077，
`sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25.tar.bz2`，878702423B，
SHA256 `393f8a14e2f5fb96746aaab342997a40641001fbd5bf9592a080a8329178ee96`。
上游模型卡revision为5eb144179a02acc5e5ba31e748d22b0cf3e303b0；模型卡为许可声明，
发布前仍须保留实际供应包与许可证/NOTICE链。实测支持不从master文档推断。

## 本次筛查合同

只在隔离Mac环境运行公开文件。原幅度、16kHz/s16LE/mono，以整片输入，不运行VAD、
增益、prompt、热词或后处理；这评估新候选管线，不是仅模型变量的因果对照。
采用官方2.0.1 Python API，CPU/2threads/128mel/greedy/seed42/temp1e-6/top_p0.8，
max_total_len512/max_new_tokens128，逐片设置Chinese或English。每片<=30s。

开发集固定为原FLEURS中文3片、原LibriSpeech英文3片、原AMI远场3片；
另加5秒全零PCM，zh/en各一次，仅检验无语音幻觉。顺序、输入与模型SHA在推理前冻结。
整片与旧Android分段不同，分别报告，不把差值全归因新模型。失败逐行记录，不灌空hypothesis。
评分继续使用原speech_error_metrics.py的NFC/casefold，保留标点/数字/简繁差异。

筛查进入独立确认的必要条件（主代理事前制定）：所有9条语音成功且非空；中文原始
CER<=25%；近场英文WER<=25%；AMI WER<=50%，且三段各WER<100%；两次纯静音不生成文本。
这些是候选筛查门，不能作为生产验收或从旧分数反推W3通过。
Mac每条推理超过120s或进程RSS超过4GiB时终止该次并保留失败；不扫参数或自动换模型。

## 独立确认与手机门

只有开发筛查通过，才冻结独立集：FLEURS zh/en各20片，排除全部旧ID；
AMI另一会议12段，以reference与时长规则选取，不按识别结果换样本。仍保留原始评分，
另行讨论标点差异不能改写旧主指标。工程质量目标由主代理建议为中文CER<=20%、
近场英文WER<=20%、远场英文WER<=35%，语音请求全成功、纯静音无文本。
该集规模只支撑此工程验收，不证明所有设备/噪声/真实用户分布。

手机评测仅在上述通过后设计，提前冻结延迟/内存采样合同：当前TECNO文件路径候选
目标为单调用warm RTF<=1、采样RSS<=1.5GiB；进程4GiB是Mac实验保护线，不是手机预算。
候选文件总大小先报告；不暗改已有SDK10秒短句合同，长文件切分另行设计。
未测峰值、系统服务、GPU/能耗须明确，不能由Mac性能或旧tiny持续批次代替。

## 退出与责任

主代理负责计划/供料、验收脚本、实际运行和裁定；免费CBC负责新runner候选；
其输入只含公开源码/协议，禁工具、禁递归委派，原始prompt/输出/拒收保留。
不改生产源、普通App/模型、默认系统服务/VPN；不录音、不请求AiAi资源。
筛查失败就明确拒绝此候选及当前原因，不继续等价重试；通过则进入独立确认，仍不关闭W3。
官方原始快照、下载与执行证据在`.omx/artifacts/w3-model-reselection-20261110/`（本机留存）。
