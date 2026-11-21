# W3 Apple 标点同期配对文件结果 — 2026-11-16

一次固定六输入、12 请求批次已完成：中文和近场英文均满足预先冻结的标点改善探索条件，未增加内容类错误。它是隔离实验结果，尚未采用到生产；R4/R5/P1–P4 和 W3 仍为 open。

用户明确授权“允许这一次配对文件批次”。实际识别 App 来自 `0afb436`，runId 为 `w3-apple-punctuation-paired6-20261116-01`，bundle 为 `io.ondeviceai.probe.w3punctpair20261116`；物理 iPhone17,2，iOS 27.2.1。既有证书/profile 仅用于签名副本，原材料未改。没有麦克风、下载、AiAi、旧 Apple65 重放或 Android 操作。

六输入按原 hash 规则固定，使用同期 AB/BA/AB/BA/AB/BA 顺序。A 保持 `addsPunctuation` unset，不推断系统默认 false；B 仅增加 availability 保护的 true 赋值。两个 arm 使用独立动态 framework，共享同一 Core/Speech；每请求 fresh backend/client 并完成 drain/close。全部 65 WAV 参与完整性校验，只有选定六输入各识别两次。

| 同期三输入组 | A 原分数 | B 原分数 | 格式类编辑 A→B | 内容或混合编辑 A→B |
| --- | --- | --- | --- | --- |
| 中文 CER | 18/86 = 20.93% | 16/86 = 18.60% | 7→5 | 11→11 |
| 近场英文 WER | 13/54 = 24.07% | 10/54 = 18.52% | 5→2 | 8→8 |

评分沿用固定工具、NFC、casefold、空白归一化，保留标点与数字。每组独立满足：格式编辑严格减少、内容或混合编辑未增加、双臂全部非空完成、无新增 typed error 或清理失败。B 补出部分逗号，但没有补齐所有标点；人名、词语、缩写缺失等内容错误仍在。格式分类只是固定对齐路径上的机械分类，不是语义等价证明。小样本已披露历史结果，非盲测、held-out、产品阈值或统计因果证明。

12/12 recognitionAttempted、recognized-nonempty、terminal、inputPostcheck 和 cleanupCompleted；每行输入、顺序、arm/configuration、locale、source identity、计时和 RSS 重算通过。报告终态双读同字节：38,538 B，SHA-256 `a415ada1068abe5fbf302bf8ad45673a36e1f232a02f9e4bc09efc734081c95b`。实际报告窗为上海 18:48:42.031–18:48:45.494，3.463 s；request wall sum 3.333877414 s，recognition sum 3.276168331 s。49 个 App PID RSS 样本、0 缺失，采样最大 119,029,760 B，最大间隔 110.297708 ms，0 个 >200 ms 间隔。这些只描述此短批次，不包括系统 Speech 内存、true peak、GPU、能耗、thermal、持续负载或预算通过。

## UI 故障与零识别恢复

首轮 UI test 在 Speech 授权弹窗检查处失败：`NSUnknownKeyException`，`NSString` 对 `label` 不符合 KVC。该驱动继承的 predicate 表达式要求对象具有 label 属性，却传入 `alert.label` 字符串；此前 build-only 检查不能发现这条运行分支。首轮 Documents 空、Run tap 为 0，进程自然结束 exit65；没有识别请求。

本次隔离 runner 的主代理实现例外覆盖这一处驱动修正：把 predicate 输入精确改成 `["label": alert.label]`。保留原失败、空目录和原 App；六个授权文本 host 检查通过，修正 driver build-for-testing 通过（5.032 s），副本签名/代码段/资源核对通过。在首进程终态和 fresh 空报告/锁屏守卫后，继续同一未消费批次；修正后的唯一 UI test 通过（8.997 s，进程 exit0）。两次 UI invocation 合计仅一次 Run tap、12 次识别，无识别重试。未通过泛化按钮处理权限。

签名检查器另有独立初错：把一架构 FAT Mach-O 的测试启动器当普通资源，签名后字节变化触发断言。保留失败，仅补充只读 FAT 切片解析；原批次 3 个 App、13 映像 section 和 80 份资源核对通过，没有因此重新签名/构建。修正 driver 单独 2 个 App、6 映像和 7 份资源核对通过。

## 恢复、来源与余项

终态双读后，只卸载实际出现的本次 probe 和 xctrunner；driver host 没有在清理前清单中出现，不假定已安装。普通 `io.ondeviceai.combined.demo` launch accepted exit0，原 45 个 App 完整元数据相同；这不证明渲染前台。模型目录没有操作，也未重新哈希。

manifest SHA-256 `7e2133c9292e978ac45050349d03d4b1432f247da6dceda6facd328bb7a62453`；source identity `feb8981eba97cd017353aee32bcc23ecbef1c05ee3cbb89350476cbde3d52c74`；输入选择 `97e70bf9f22c8d0d09465dc52952f88d6d3d00f50cf0b6d06badabbfebcb97cc`。两份评分工具及固定规则摘要见[机器收据](w3-punctuation-paired6-device-20261116.json)。准备时的 `executionAuthorized=false` 合同保持历史快照；新的用户授权、签名、安装和运行各自留独立证据，不改写旧收据。

可携带原报告、逐请求诊断、UI 故障/零识别恢复及恢复摘要位于 `probes/ios-punctuation-paired6/device-evidence-20261116/`。全部私有设备清单、签名选择、原始构建/测试日志、两份 xcresult、双读报告及只读核验脚本保留在 `.omx/artifacts/w3-punctuation-paired6-device-20261116/`。无新的独立最终源码或发布复核；旧 native/CBC 429 不重试，本次不冒充 CBC 交付或 P4。

这项结果支持后续准备显式标点的生产候选，但本轮没有改生产代码或宣布采用。远场漏词、历史两次 speech typed1110、负例非空、Android 41 个未完成槽位、生产候选身份、完整供应链/发布接受和 R5 预算均保持未决；不能以新六输入分数替换旧 65 全量结果。当前批次已消费，不重放。

## 提交与同步

本地证据/UI修正提交 `8e293a4`（18 文件）；GitHub #15 comment `6914478597`、GitLab #15 note `45419` 均以 GET 回读同 POST 响应。GitLab 只移除一个末尾 LF，首次准备文稿的严格相等失败也保留并核实相同差异。GitHub Wiki commit `386b32bb3747ca042e670235a1fcde43b3a81e21` 已推送并 fetch/show 回读，页面 SHA-256 `0bb8067bf746b765df4611a0676864986de6fdfb29e64dc0af5296999a483eef`，原页面内容完整保留。GitLab Wiki 旧 EOF 通道无新 GET/PUT，仍 pending；源码分支未推送。见[发布收据](w3-punctuation-paired6-device-sync-20261116.json)。
