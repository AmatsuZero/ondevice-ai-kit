# W3 AMI 未执行片段补测 — 2026-11-10

**六个端/片段组合现均有真实成功或 typed error；W3 质量仍未通过。** 本次只补原批次未执行的
Android C-294、B-146 和 Apple B-146，各一次，没有重放原已尝试的组合，没有新麦克风采集。
原批次仍是失败/不完整批次，不能倒写成同一批全通过。

后续状态：当前 TECNO R1–R3 已按用户接受的[已知限制](w3-tecno-system-asr-scope-disposition-20261110.md)
完成范围裁定；唯一获批[资源请求](w3-system-resource-provision-observed-20261110.md)已返回错误15，
授权次数已用完，不再等待解锁、下载或采集重试。Apple FILE ASR 持续观测亦已验收，
见[本批记录](w8-apple-asr-sustained-20261110.md)。本文末尾保留补测结束时的待办；当前仅R4/R5未裁定。

证据根：`artifacts/verification/w3-ami-unattempted-20261110/`。
推理前 `execution-contract.json` SHA-256：
`2c37bbfeb9e33a293f3ae18dc93a7aba459aed3abf46fc3f0e7dcda7660462de`。
来源、许可、三段 reference/样本边界及全部 PCM/WAV 字节沿用[原 AMI 批次](w3-ami-pilot-observed-20261110.md)，
manifest SHA 仍为 `498bed3b1ce1315f18d54871e7b8a3c8452ed43d014edbe56392964f12e64b85`。

## 当前双端结果（明确区分 run）

| 片段 | Android Sherpa tiny | Apple URL backend |
| --- | --- | --- |
| D-330 | 原 run `w3-ami-20261110-01`：SpeechNotDetectedException | 原 run `F27D555C-D503-4083-B5A2-AB63BB8AA598`：`I'm marketing` |
| C-294 | 新 run `w3-ami-C294-20261110-01`：SpeechNotDetectedException | 原 run：kAFAssistantErrorDomain/1110 |
| B-146 | 新 run `w3-ami-B146-20261110-01`：`I need to discuss the project finance.` | 新 run `BFE82AA1-8460-44E1-94F2-5E2E9C2ED85A`：`I need to discuss project finance` |

Android 是一段成功、两段 typed no-speech；Apple 是两段成功、一段平台错误。
三份新终态均双读逐字节相同，明确绑定 selectedClipId；Android 各 expectedRows=1/plannedRequests=1，
Apple 实际只有一个 clip receipt，drained/closed=true。Android close 成功，前台 teardown 的 finishing
真实为 true，未把 destroyed=false 写成已销毁。

只对新成功的 B-146 给描述分数（原 scorer、NFC/casefold、CER 去空白、保留标点）：

| 平台 | CER | WER |
| --- | ---: | ---: |
| Android | 14/45 | 5/11 |
| Apple | 18/45 | 7/11 |

这是单片成功输出的描述，不是三段总体或噪声质量通过。typed-error 没有伪造为空 hypothesis。
原 Apple 首段 CER20/32、WER6/8 及中文 Apple16/92、Android44/92 保持。没有批准数值门槛，
不从这些已知结果反推通过线，不扫增益、VAD、模型、prompt 或 pre-roll。单一英文会议远场
仍不能推广到中文或全部真实噪声。

## 探针修正、构建和失败保留

免费 hy3 提交两端可选单片选择补丁，缺省仍保留原三片行为。原交付的外层嵌套 JSON
不合法；保留 raw/首次解析失败，仅按已校验的前缀和尾部解码其完整返回 payload，没有补写
或改动候选源。Android 初稿漏了实际 inference loop 的跳过逻辑，被拒绝装机；一个独立的
短修正交付该 guard。父验收原稿真实 RED，修正版 23 项 Kotlin 断言、21 项 Swift parser/
manifest narrowing 断言通过，Swift 全文件 parse 通过。

两端选择错误在 backend/permission/recognition 前拒绝；Android 保留原输入全量校验和
global index，Apple 先校验完整三行 manifest，再缩为一个 clip，原 parseError/metadata 保持。
只在隔离副本集成 helper 的精确替换及既有 AMI 数据常量包装，未改仓库生产源或原 probes。

- Android offline build+lint 成功，75 tasks；四份 native libraries 和全部三份 PCM assets
  与原批次相同。main APK SHA 仍为
  `fdca50591bc9a35f1527c58de5e3363327dda897fd10071c7e8083cf347361ec`，新 test APK SHA 为
  `04441055b547829a119a39f27c039e21db6d7ecebb3c5aa3f78ad31262c3a596`。
- Apple device build 9.633s 成功，原两项 packaging warnings 保留；既有 identity/profile
  签名核验通过，签名前后 Mach-O sections 保持。七份生产 speech 源绑定当前工作区。
- Apple 首次启动误用工具参数 `args`，UI 保持 idle、尚未点击/识别；核工具 schema 后停止
  该 PID，以正确 `launchArgs` 重启，随后只点击一次。保留两次 launch 和修正收据。
- Android 首次 host preflight 在读缺失的父级 manifest metadata 时失败，尚未 reservation/
  instrumentation；核对原冻结 SHA 后原样复制 metadata，再进入唯一真实 run。原错保留，
  不是识别重试。
- Apple 原硬编码 `sdkSourceCommit` 仍是旧字段，当前来源以 inputs/build/Mach-O 绑定。
  UI/status 的 `all 3 clips recognized` 也是旧固定文字；本次真实行数以 selectedClipId、一个
  clip receipt 和唯一 runId 为准，不能据此称三段成功。这两项未为本次测试另改实现。

## 恢复与系统资源入口调查

Android 仅移除本次两别名及 manifest-bound stage，普通 App 路径/四模型 SHA 保持。
Apple **本次安装前**已保存完整清单；移除唯一新增 alias
`io.ondeviceai.probe.w3amiremaining20261110` 后，44 个原 App 的完整记录及路径逐项相同，
已回到普通 Combined。XcodeBuildMCP 会话也已恢复原全局 profile。没有重跑 W8 测量。

另有只读/设置页证据：`artifacts/verification/w3-resource-path-20261110-0940/`。
实际 on-device 服务是 AiAi，和默认 voice service 的 Google TTS 不同。AiAi 语言包 Activity
未导出、字幕 Activity disabled；没有尝试绕过或启用。正常已导出 UserSettings/AsiSettings
入口实际仅显示开源许可，未找到语言资源操作。第一次 implicit action 因 isDefault=false
无法启动，随后仅用已确认 exported=true 的明确 component 打开；原错保留。
这不证明所有系统/API provisioning 都不可能。服务默认值前后相同，没有下载请求、设置修改
或新采集，普通 App 已恢复。

## 补测结束时的余项（后续状态见页首）

1. R1 仍是系统麦克风 MISSING_ASSETS；R2/R3 活动识别受此前置阻塞，新采集仍需新的开始提示。
2. R4 的逐片观测覆盖已补齐，但真实 no-speech/1110 和中文质量差距保持，质量仍 unverified；
   不能以覆盖完成代替质量裁定或擅自修改通过门槛。
3. R5 继续引用 W8 的缺测/预算裁定；本次短文件识别不能替代持续资源测量。

主代理继续负责验收和设备操作，未扩大前缀两份 Kotlin 的一次性本地实现例外；所有新行为候选
来自既定外部 helper。资源下载仍需单独明确的用户操作/授权，不能因用户解锁或继续文件补测
而自动发起。一次资源 provisioning 即使成功，也不等于 R1 麦克风成功或 W3 关闭。
