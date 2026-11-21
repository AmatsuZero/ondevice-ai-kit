# Qwen 两阶段独立确认通过，Android 尚待验收

**65/65 输入完成，52/52 语音成功非空，13/13 非语音挑战为空，实际117次decode。**
中文、近场英文及真实会议远场的原始评分均满足事前独立确认门，允许进入手机候选设计。
这验证的是本次 Mac 候选管线，不是生产发布或 W3 整包完成。
[固定协议](w3-qwen-independent-protocol-20261110.md)、[逐条reference/hypothesis/评分](w3-qwen-independent-observed-20261110.json)。

| 组别 | 结果 | 事前独立确认门 |
| --- | --- | --- |
| FLEURS 中文20片 | CER42/590=7.12% | <=20% |
| FLEURS 英文20片 | WER32/375=8.53% | <=20% |
| AMI 远场12片 | WER62/226=27.43% | <=35% |
| 新3s/10s全零PCM，zh/en各一次 | 4/4空文本，未进第二次 | 全部空 |
| 三会议各3个annotation-negative间隙 | 9/9空文本，未进第二次 | 全部空 |

四名说话人均无定时标注事件的会议间隙尚未经人工听审，因此不将其标签写成已证
纯噪声或证明所有真实环境无语音均可拒绝。AMI仍是小规模英文会议场景，不能外推所有
中文远场/重叠语音/口音/噪声。FLEURS每语言20句、3–10s，原reference未改，
原scorer保持NFC/casefold及标点/数字/简繁差异；没有后处理或依结果换样本。

输入在2026-11-10T09:00:14Z冻结，run `w3-qwen-two-pass-independent-20261110-01`，
PID67475，CLIexit0，终态双读一致。全部52语音有真实自动准入结果和强制语言第二次结果；
每片两次decode，其余13片各一次。未复用开发集hypothesis，也未重放失败样本。

协议SHA256 `dd4d8bc7273ac44277e56b881029194e5a1970b82b38918e96ac0716609ec31e`；
runnerSHA256 `809fe34fbed820e2e77a78249adba5866a6011826054932ecb72c84a6dad1f07`；
原始报告SHA256 `8bc5f6b2d65d02e3c51cd43cf4ef6940dade13d24d4dc54b36feab088fee6024`。
源码/模型/所有PCM/reference绑定及调用计数核对通过，120s/4GiB保护未触发。

Mac加载1.431s，52语音总370.102s，完整两阶段耗时总75.124s，最大单片2.134s，
语音时长加权warm RTF约0.203。整进程82.899s，163个约0.5sRSS采样，
sampled max2828926976B；Darwin ru_maxrss2828959744B。不能作为Android1.5GiB目标、
系统服务、true peak、GPU、能耗或预算批准。Python引用清理/GC不冒充native析构确认。

来源：固定FLEURS revision，CC-BY-4.0，流式取得40个原WAV，逐个保留实取SHA与转换信息；
官方AMI手工标注ZIP及Array1-01公开WAV，Range逐次核206/Content-Range/总size/精确样本数。
ES2003a另取得完整源文件，全部适用局部输入与整文件相应区间逐字一致；ES2004a/ES2006a
仅声称已核头和局部输入，不冒充整文件SHA。无增益/重采样，原输入为16kHzmono，
FLEURS转s16LE；AMI本身s16LE，按冻结区间直接取字节。

FLEURS两路完整所选文件和AMI21个精确区间都取得后，停止PID41412的冗余整AMI下载；
exit143是主代理明确SIGTERM，原始进度、已完成ES2003a及另外两份partial均保留。
不是传输成功的误报或自动重试。最初只选一个/两个会议分别不足12段的断言失败保留，
在独立零解码时按会议名顺序扩至12段，筛选条件和评分线未放松。

本机完整证据 `.omx/artifacts/w3-model-reselection-20261110/`，ignored材料不保证远端可访问。
生产源码/依赖/普通App/模型/默认服务/网络未改，无麦克风、AiAi或设备操作。
下一步验Android真实包/两阶段行为与资源，并完成供应包许可证/NOTICE链及独立审查；
原有TECNO系统ASR限制与下载授权用完的裁定不受本候选影响。
