# W3 Qwen3-ASR0.6B：Mac首次固定筛查

11/11实际调用完成，**不提升生产：2/2纯静音生成文本，违反事前筛查规则**。
语音质量有显著正向信号，下一步只验证独立准入策略；不重放本批Qwen识别。
[设计及事前门槛](../superpowers/specs/2026-11-10-w3-asr-reselection-design.md)、
[机器结果与逐条原文](w3-qwen-host-screen-observed-20261110.json)。

| 组别 | 原始评分 | 事前筛查门 |
| --- | --- | --- |
| FLEURS中文3片 | CER3/92=3.26%，三片分别3/0/0错；仅引号/标点差异 | <=25%，满足 |
| LibriSpeech近场英文3片 | WER7/59=11.86%；CER7/268 | <=25%，满足 |
| AMI原幅度远场3片 | WER13/27=48.15%，逐片5/8、3/8、5/11 | 总<=50%、每片<100%，满足 |
| 5s全零PCM/Chinese | `嗯，对。` | 不生成文本，失败 |
| 同一全零PCM/English | `The most important thing is to be able to do it.` | 不生成文本，失败 |

所有9语音返回非空文本。评分使用原scorer，NFC/casefold、保留标点/数字/简繁，
没有重写reference或删标点提高分数。中文旧Androidtiny44/92、Apple16/92保持原样；
本候选是Mac整片原幅度、无VAD管线，旧Android有分段/VAD，不能把差值全部归因模型。
AMI原typed no-speech不灌空hypothesis算基线；此前tiny/no-VAD21/27也是独立实验。

运行固定Sherpa2.0.1、CPU2threads、128mel、greedy、seed42/temp1e-6/top_p0.8、
max_total_len512/max_new_tokens128，逐stream设置Chinese/English；无prompt、热词、
增益、裁剪或麦克风。开发集11行在推理前冻结，旧文件波形/reference逐SHA保持。

官方release资产390698077，压缩878702423B，整包SHA256
`393f8a14e2f5fb96746aaab342997a40641001fbd5bf9592a080a8329178ee96`与供应方digest一致。
完整模型/词表共987015347B。供应包README指向第三方ONNX导出来源；上游Qwen模型卡
固定revision声明Apache-2.0，但完整再分发通知链仍须在生产采用前验收。

Mac模型加载1.866s，各调用0.355–1.750s，warm RTF约0.080–0.148；
0.5秒采样进程RSS max2570289152B，Darwin ru_maxrss原值2573271040B。
两者为不同测量手段；不冒充Android时延/内存、系统服务、GPU、能耗或手机预算通过。
120s/4GiB保护未触发，进程正常exit0，终态双读一致；Python引用清理/GC有报告，
未宣称C++析构ack。原始报告SHA256
`96aeb6b731d574a661602db7edab2fe49f4f332c8bb9b3166da74800f61cd85a`。

免费hy3交runner及两次定点修正，主代理精确集成，9项故障/协议检查通过。
原4项失败、后7/9阶段、所有原始prompt/CLI事件/候选均保留；原生只读复审429无结果，
未重试、未计独立review通过。模型取回的三处传输timeout保留，一次小分段补齐后整包
SHA通过，不是识别重试。未改生产源码、普通App/模型、网络/默认服务或系统资源。

当前只拒绝“无准入直通”的配置，不把正向质量信号变成W3完成。
后续[Silero v5准入诊断](w3-qwen-admission-protocol-20261110.md)固定原幅度/默认参数，
只判断是否允许送识别，不用VAD片段裁剪原输入。独立质量集与手机批次尚未执行。
完整本机证据：`.omx/artifacts/w3-model-reselection-20261110/`，ignored路径不保证远端可访问。
