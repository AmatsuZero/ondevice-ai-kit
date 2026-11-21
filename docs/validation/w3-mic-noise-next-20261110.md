# W3 麦克风与真实噪声余项 — 2026-11-10

**最新范围裁定：用户已接受当前 TECNO 系统 ASR 的已知限制，R1–R3 的本机范围裁定完成；原 FAILED/MISSING_ASSETS 和未执行结果保留，不再安排资源或录音重试。** 详见[正式裁定](w3-tecno-system-asr-scope-disposition-20261110.md)。后续唯一获批资源请求实际返回错误15，见[资源观察](w3-system-resource-provision-observed-20261110.md)；下文按日期保留原合同与观察，R4/R5仍open。

固定前缀单项已完成，见[验收](w3-prefix-control-accepted-20261110.md)。
本页记录 R1–R4 的合同和结果；AMI 实测详见[双端失败及边界](w3-ami-pilot-observed-20261110.md)。
后续[单片补测](w3-ami-unattempted-observed-20261110.md)已补齐原未执行的三个组合；Android1/3、
Apple2/3有成功输出，其他为真实typed error，质量仍unverified。所查AiAi正常设置只显示许可，
语言包页未导出、字幕页disabled；没有下载/改设置/新采集。原批及下文事前合同保留。

## R1–R3：Android 系统麦克风

最新无录音刷新已在 TECNO KM9 / API35 运行既有冻结 metadata APK：8 份生产源与当前
逐字节相同，probe 仅 KDoc 说明文字不同，实际非 KDoc 源相同。初次全文件 SHA gate
因此阻断在安装前，差异与恢复说明保存在 `source-currentness.json`；没有重建或改生产源。

实际 on-device service available=true、querySupported=true；en-US/zh-CN 逐项 queried=true，
均 `assetStatus=NOT_REPORTED`。该结果不能说成语言资源 installed；metadata alias 的
RECORD_AUDIO 仍 denied，不能据此断言普通 App 的权限也 denied。query-only 没有
recognize、capture、download、permission mutation；全部包清单已恢复。
证据：`artifacts/verification/w3-system-asr-refresh-20261110/metadata-parent-acceptance.json`。

用户随后明确回复 **“已解锁，开始 en-US 麦克风一次”**。本次已在普通 App
`io.ondeviceai.demo`、en-US 下确认显式 `android.speech.recognizer` 后请求一次；
终态为 **FAILED / BACKEND_UNAVAILABLE / MISSING_ASSETS**，R1 未通过。
没有观察到平台 numeric error，不推定实际 error13；也未验证平台实际采集到了音频。
只保存状态/错误码，没有保存音频或自然转写文本。开始提示已用于此次请求，不能自动再录。
证据：`artifacts/verification/w3-microphone-r1-20261110/{start-cue,backend-confirmed,status-only-result}.json`。

启用 Sherpa 的普通 App 默认后端是 Sherpa；本次通过精确显式选择确认系统后端。
输入法曾将标识改成中文标点，早期操作仍停留 IDLE；最终精确选择后的单次请求才算本项。
剪贴板恢复原空值。一次 `uiautomator dump /dev/tty` 状态读取卡住，已结束本次 host 进程；
未保存 raw XML，此工具失败不算 App 缺陷。后续只过滤状态控件，不输出自然转写内容。

R2/R3 的活动识别场景受资源前置阻塞；不自动下载、换后端或重试采集。
解锁许可或固定文件对照许可不等于麦克风开始提示，后续采集仍需新的开始提示。

事先判定：

- R1 quiet en-US：记录实际 system backend `android.speech.recognizer`；非空成功为功能证据，
  不是未给 reference 的质量评分。error13/MISSING_ASSETS 与其他 typed error 原样保留；
  不自动改为 Sherpa/file/network 后端，不下载资源或重复采集。
- R2 background speech：须另一次开始提示和真实背景语音场景。当前合同没有目标说话人/
  背景说话人分离规则或质量门槛，先明确场景与验收行为，再录音；合成文件不能替代。
- R3 interruption/cancel/reentry：每次新采集有新提示；取消后 drain 再重入、后台不自动
  重启。只有服务实际进入识别后，才记实际活动取消证据；若资源错误先终止，记 blocked。
  保留已有 iPhone 生命周期结果，不重复已验录音/文件场景。

## R4：代表性真实噪声来源与冻结合同

已查官方来源的候选：AMI Meeting Corpus 的真实会议远场 microphone-array 通道及官方
人工转写。官方 [download](https://groups.inf.ed.ac.uk/ami/download/) 提供独立 audio
streams、manual annotations v1.6.2（约 22 MB），并声明 signals/transcription 为 CC-BY-4.0；
[官方许可页](https://groups.inf.ed.ac.uk/ami/corpus/license.shtml) 同样注明该许可。
只使用官方当前许可材料，不把 OpenSLR 的旧 1.6.1 镜像许可描述当新材料许可。

用户已选择 **“采用 AMI 英文会议远场小样本”**，不再等待来源选择。
这是英文会议远场小样本，不能泛化到中文、街道/车内或所有噪声；真实录音也仍包含
情景设计的会议。官方 annotations ZIP、来源/许可网页及单一 `ES2002a.Array1-01.wav`
已下载；随后按冻结合同各执行一次双端文件批次。来源收据及推理前选择合同在
`artifacts/verification/w3-ami-pilot-20261110/{source-receipt,selection-contract}.json`。
原始 WAV 为单声道/16kHz/16-bit PCM、40,727,594 B，SHA-256
`38887e3bf7b597fe348f3818e9fc9844d7a1e15382aab52e5316dc3228fb6c1d`。
下载页标为 v1.6.2（2017 许可更新），包内旧 README 仍称 1.7（2014）；两者原文均保留，
以实际 URL/archive SHA 绑定材料，包内 `LICENCE.txt` 明确 CC-BY-4.0。

三段共12.8秒，Android首段 `SpeechNotDetectedException`、零成功行；Apple首段 `I'm marketing`，
第二段 `kAFAssistantErrorDomain/1110`。既有 runner 首错误停止，后续 Android两行/Apple一行
未执行；事前完整功能门未达。只报 Apple首段 CER20/32、WER6/8，不给整批或双端汇总。
两份终态双读一致、close/drain及恢复已核；没有重跑/增益/VAD/模型扫参，W3保持open。

以下为本批推理前合同；本次结果不满足第6项的完整行覆盖要求：

1. 固定官方 meeting/session、单一远场通道和版本/URL；本地 SHA-256/字节数、许可及
   attribution。只取小规模子集，不下载整库，也不做人工加噪。
2. 选择规则在识别前确定，独立于两个后端输出；先处理 XML 时间/单词边界、重叠说话人
   的 reference 规则，跨边界词/不确定或无法唯一归属片段排除并记录，不能事后删困难样本。
3. 两端共用同一 canonical mono/16 kHz PCM；保存原文件/转换命令、切片范围及逐片 PCM
   SHA。Apple WAV payload 解码后必须与 Android PCM 相同。不把时间对齐正确性留到评分后。
4. reference 逐字冻结，标点/大小写规则沿用现有 NFC/casefold、CER 去空白且保留标点/
   数字/script；英文补充同一现有 scorer 的 WER，不事后换主指标。保存 reference SHA。
5. 固定既有 Apple file/Sherpa tiny 后端与 locale、分段策略、单次执行计划，无模型/prompt/
   VAD sweep；分别记录设备、实际模型/runtime/input 源码绑定、失败/取消及恢复。
6. 事前功能门：所有预定行均有同 run 的成功或 typed error 及释放/恢复；任何缺行不是完成。
   数值质量目标目前未获批准，只能给描述性 CER/WER/逐片结果，**qualityVerdict=unverified**。
   若需要质量 pass，先给定符合产品范围的目标并冻结，再碰该评价集；不能从本次得分反推。

本页不扩展主代理的一次性实现例外。若后续需要修改实际探针代码，恢复既定外部助手实现
路线和真实隔离约束；主代理继续负责计划、集成、设备操作与验收。现有阻塞的具体源码
外传请求仍未执行，也不授权换云端助手绕过。
