# W3 AMI 英文会议远场小样本 — 2026-11-10

后续状态：原批次保持以下失败/未执行事实；新的[单片补测](w3-ami-unattempted-observed-20261110.md)
仅补原未执行的三个端/片段组合，现六组合均有观测。不得倒写原批成功；质量仍 unverified。
当前 TECNO R1–R3 已按用户接受的[已知限制](w3-tecno-system-asr-scope-disposition-20261110.md)
完成范围裁定；本文末尾的资源/补测建议属于原批次时状态，不再据此派发重试。R4/R5仍open。

**本次未通过三段对照的功能门，质量仍为 `unverified`。** 用户已选择 AMI 来源；本次双端各运行
一批固定公开文件、零麦克风启动。Android 首段为 typed no-speech；Apple 首段有结果，第二段
为平台错误。两个既有 runner 都在首个错误处终止，未执行行不能计为识别失败或完成。

证据根：`artifacts/verification/w3-ami-pilot-20261110/`，逐项状态见
`comparison-summary.json`；原始终态均双读逐字节相同。

## 来源、选择与同波形合同

- 官方 [AMI download](https://groups.inf.ed.ac.uk/ami/download/) 与
  [许可](https://groups.inf.ed.ac.uk/ami/corpus/license.shtml)，AMI Consortium，CC BY 4.0。
  网页、annotations ZIP、包内 `LICENCE.txt` 与原始音频均保留。未下载整库。
- manual annotations 下载名为 v1.6.2（2017-04-10 许可更新）；包内旧 README 称 release 1.7
  （2014-06-16）。保留差异，以 URL 和实际 archive SHA 绑定，不擅自改版本原文。
  ZIP 为 22,887,865 B，SHA-256 `b56e5babb2496b8795deeeda7e71178d7fbc9963f94276cf2a3f4b56ebbc9f9d`。
- 单一会议 `ES2002a`、单一远场通道 `Array1-01`；原 WAV 为 40,727,594 B，mono/16 kHz/
  signed16LE PCM，SHA-256 `38887e3bf7b597fe348f3818e9fc9844d7a1e15382aab52e5316dc3228fb6c1d`。
- 推理前 `selection-contract.json` 固定：按四人全部 segment 的起止时间、speaker、id 排序，
  取前三个 3–8 秒、至少 8 个 lexical words、完整且非截词、无其他事件重叠的片段；
  前后各 250 ms context。保留全部 113 个已检查候选及排除理由。不是依结果挑片。
- reference 使用官方人工转写的完整 word-id range，保留口头词、大小写和标点；
  `punc=true` 附着前词。没有另造独立人工听写。所选精确 range/reference 与所有四人
  annotation event 的排除复核在 `reference-alignment-receipt.json`。
- 原始 PCM 精确取帧，未重采样、增益、降噪或人工加噪。Apple WAV 解码 payload 和 Android
  PCM 用 Python wave 及独立 ffmpeg 解码逐字节核对。三段共 **12.8 秒**。

| 固定 id / 原始 frame 范围 | 秒数 | 原样 reference |
| --- | ---: | --- |
| `ami-ES2002a-D-330` / `[1301200,1357216)` | 3.501 | `And I'm Andrew and I'm uh our marketing` |
| `ami-ES2002a-C-294` / `[4069808,4126880)` | 3.567 | `Well, my favourite animal would be a monkey.` |
| `ami-ES2002a-B-146` / `[7443808,7535520)` | 5.732 | `Okay, uh we now need to discuss the project finance. Um` |

三份 PCM SHA-256 依次为：

```text
6f9370f42ec71311823ff5820cb97b88beb9c04db838685c2100a82830dbaf61
0d65b45518193214e1be06a2bbffd5cafa03696727a0c5addea7e61c72e4ff0d
3e884a0ffe26fc41bc9c499102471231ac2c11479581ed3a85fad0cbebc6a0f0
```

`AudioFixtures/manifest.json` SHA-256 为
`498bed3b1ce1315f18d54871e7b8a3c8452ed43d014edbe56392964f12e64b85`，推理后未改变。

补充全局时间顺序检查曾失败：官方 `ES2002a.B.words1587` 的 laugh 起止为
1109.39→1107.09。原字节未修；即使用两端点的保守区间，也完全在三段 crop 之外，所选
target 时间/range 和无重叠结论经独立复核保持。不能宣称整份 annotations 时间均合法。

## 双端真实结果

| 片段 | Android Sherpa tiny / TECNO KM9 API35 | Apple URL backend / iPhone17,2 iOS27.2.1 |
| --- | --- | --- |
| D-330 | 首个 request 抛 `io.ondeviceai.speech.SpeechNotDetectedException`；零成功行 | `I'm marketing` |
| C-294 | 未执行：runner 已停止 | `kAFAssistantErrorDomain / 1110` |
| B-146 | 未执行：runner 已停止 | 未执行：runner 已停止 |

Android runId 为 `w3-ami-20261110-01`，PID 9207，`completedRequests=0/plannedRequests=3`；
`finalCloseSucceeded=true`、前台启动/resume/finish 已回执，无麦克风/resourceWindow。
报告没有 error row，仅顶层 `errorClass`；失败在首个 request，根据固定顺序映射到 D-330。
不能据零成功行推定从未做模型/JNI 工作。生产源码中的 VAD/最少 speechSamples gate 未改。

Apple runId 为 `F27D555C-D503-4083-B5A2-AB63BB8AA598`，一次按钮 reservation；
首次 Speech 权限弹窗已允许，同次请求继续，未重复点击。`state=failed`，
`failure.stage=recognize-clip-2`；`drained=true/closed=true`，全部三份 WAV 的 post-close
SHA 保持。1110 按实际平台错误记录，不反推资源缺失或采集条件。

本次只能给 Apple 首段的描述性 **CER 20/32、WER 6/8**（NFC/casefold、CER 去空白、保留
标点/数字，既有 `tools/speech_error_metrics.py` 未改）。这不是完整三段或双端汇总；
Android 没有成功输出，未执行行也不作为空 hypothesis 灌入分数。没有批准数值质量门槛，
不能反推 pass。原中文 Apple 16/92、Android 44/92 及前缀局部结果不变。

波形 RMS 约 -44.86/-47.13/-50.03 dBFS，只是幅度观测，不是 SNR、噪声类型或 VAD 根因证明。
未扫增益、VAD、模型、prompt 或 pre-roll。单一会议/通道不能推广为中文或全部真实噪声质量。

## 构建、来源、恢复与限制

- Android 复用既有 natural-file runner，只在隔离副本注入三份固定数据/长度和 plannedRequests=3；
  九份生产 Kotlin 源逐字节相同。offline build+lint：75 tasks executed，22.468s，零 lint errors、
  8 warnings 留存。签名、APK 内三份真实 PCM、四 native libraries、安装 APK SHA 及四模型
  传输前后 size/SHA 绑定；不使用 direct-native 前缀探针代替本项。
- Apple Swift runner 未改，七份当前生产 speech 源已核。XcodeBuildMCP device build 通过，
  两项 UI packaging warnings 留存；已有匹配 profile/identity 签名验证通过。
  report 的 `sdkSourceCommit` 是旧硬编码字段；当前来源以 `apple/inputs.json`、构建日志及
  实际签名 Mach-O sections 收据绑定，不把旧字段当当前 checkout。
- 当前 devicectl 实测 iOS **27.2.1**；Mobile MCP 的 27.2 清单较旧。初查 DDI=false，后续
  正常 build/install/launch/copy 全部实际完成；未改签名资产或全局配置。不要把此次时间与
  旧 OS 的时延作性能对照。
- Android 两个临时别名和 manifest-bound stage 已移除，普通 App 路径及四模型路径状态/hash
  保持，返回普通 App。Apple 只移除本次 `io.ondeviceai.probe.w3ami20261110`，安装后到恢复的
  App 清单差异精确为此别名，并返回 `io.ondeviceai.combined.demo`。本批遗漏了安装前清单，
  不宣称有本批安装前/后全清单一致性证据。
- 免费 hy3 提交的独立公共 XML selector 候选已自然终态（536.761s/exit0/result success），
  真实执行却因把 `ES2002a.A.words0` 误当四段 id 而全拒；零 selected/exit1，未集成或用于
  选片。另有未知 tags 过滤等 fail-closed 缺口。原 prompt/raw/source/拒收在 `selector/`，
  不修改原候选、不把 CLI 成功当验收，也不自动重派。
- 未新增 native reviewer 请求；既有 429 不自动重试。没有独立复审通过声明，无提交/推送。

## 原批次裁定与当时续项（后续状态见页首）

R4 的来源、固定输入及双端失败证据已经形成，但**三行均有成功或 typed error 的事前功能门未达**，
仍 `open`。不能把两条未执行 Android 行或 Apple 第三行写为完成，不能将缺结果当质量改善。
保留同一次失败，不重放已冻结的一批、不依输出替换片段。

后续可先让既定外部实现渠道给现有 runner 提供逐行 typed-error 持久化/继续下一固定行的
独立候选，主代理只复审/验证；新的整批执行合同需先冻结，不能借修报告自动重试本批。
R1 仍是系统麦克风 `MISSING_ASSETS`，R2/R3 活动识别前置未满足；需先解决系统资源，
任何新麦克风采集仍需新的用户开始提示。W8 已验持续测量不重跑，W3 整包不关闭。
