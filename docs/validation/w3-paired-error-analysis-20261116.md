# W3 Apple / Qwen 逐项误差对照 — 2026-11-16

本次只分析已执行的公开文件结果，没有新增识别、录音、设备操作或下载。结论是：Apple 的中文和近场英文有较多标点格式差异，远场则主要是内容遗漏，不能用补标点替代质量修复。R4/R5、P1–P4 和 W3 继续开放。

使用原评分器 `tools/speech_error_metrics.py`，SHA-256 `d398bb35438b9f4eb399fc404e02c0c2a7184a7cea7c7a6d32e4a321f8e2b39e`。NFC、casefold=True、空白折叠；标点、数字、重音均保留。typed1110、缺失行和挑战项不参与 CER/WER；没有以空 hypothesis 代替错误，也没有生成去标点后的替代分数。

## 同一批输入的描述性比较

三个历史 run 的 ID、cohort、locale、PCM 摘要、样本数和 reference 已逐项核对。比较跨设备、后端及实现配置，不能归因为单一变量；Qwen 结果此前已披露，不是新盲测。

| 配对范围 | Apple | Qwen | 对照边界 |
|---|---:|---:|---|
| 中文20条，590字符 | CER 95/590 = 16.10% | TECNO 40/590 = 6.78% | 两端均20条完成 |
| 近英共同4条，67词 | WER 14/67 = 20.90% | TECNO 9/67 = 13.43% | 仅4条；不代表其余16条 |
| 中文20条，590字符 | CER 95/590 = 16.10% | Mac 42/590 = 7.12% | Mac不能代替Android真机 |
| 近英20条，375词 | WER 75/375 = 20.00% | Mac 32/375 = 8.53% | 同20条 |
| 远英共同成功10条，197词 | WER 103/197 = 52.28% | Mac 51/197 = 25.89% | Apple另外2条typed1110保留，不是全12条通过 |

TECNO 的既有 run 只有24个完成项，其余41个分析槽位明确标为 missing；其中原第25项被内存守卫中断，后续项未执行，不能把41项统称未尝试。Apple为50条speech非空、2条挑战非空、13条typed1110；Mac为52条speech非空、13条挑战成功空输出。挑战项的状态保留，不把Apple错误计作成功空输出。AMI负例没有人工听辨，不能据其标签建立完整真实噪声误报率。

## 标点格式与内容编辑的分布

诊断沿用原评分器的最小编辑路径与固定回溯优先级。只有当一对对齐单元移除Unicode标点后相同、且至少一侧含标点时，才把该次编辑标为 formattingOnly；其余记 contentOrMixed。此分类依赖确定的对齐路径，不是唯一语言学归因，也不证明语义等价（例如撇号可能改变词义），不是新评分口径。每个S/D/I及总错误数均与原评分逐项相等。

| Apple组 | 原评分错误数 | 仅标点格式编辑 | 内容或混合编辑 |
|---|---:|---:|---:|
| 中文20条，字符 | 95 | 45（全部为删除） | 50 |
| 近英20条，词 | 75 | 36（全部为替换） | 39 |
| 远英成功10条，词 | 103 | 11（全部为替换） | 92，其中删除68词 |

远场的具体失败不只是格式。例如 `ami-ES2003a.sync.171-C` 的reference有22词，Apple仅输出 `Great memories and began`，原WER计20次编辑，其中18次删除；`ami-ES2006a.sync.277-C` 的18词仅输出 `And that's it`，计16次编辑，其中15次删除。没有用Mac结果填补这些错误。

中文同样有真实内容错误：`fleurs-cmn_hans_cn-1965` 中的“新西兰圣诞树”被Apple输出为“信息男生难受”。该条原CER为9/32，Qwen Mac为1/32；这只是已公开样本的描述性观察，不能据此调参后仍称独立验收。

## 下一项可验证的改进

当前文件识别桥在创建 `SFSpeechURLRecognitionRequest` 后设置 on-device 和 partial-results，没有显式设置 `addsPunctuation`。本机Apple SDK27.2头文件说明，iOS16/macOS13起可显式将该属性设为true，请求系统补标点；未据此推断默认值或实际识别效果。Context7工具未提供，web工具三次未返回内容，本条依据实际安装SDK头文件，原摘录和摘要留在本地证据中。

因此可以准备一次显式自动标点的独立文件控制，目标是验证格式编辑能否减少；尚未改动生产桥或启动该控制，也没有新运行授权。它不能被宣称能解决远场内容遗漏、两次typed1110或静音非空。Android仍优先沿既有未消费单片合同定位内存问题；没有新的实体连接变化，本次未重试ADB或旧FILE批次。

## 可复算材料与验收

公开输入投影保留全部65个输入及三个run的原始文本/状态；错误是null hypothesis，缺失是显式missing槽位。完整对齐输出留本地；Git内的机器摘要保存逐项评分、分类计数、配对集合及原件身份。

```sh
rtk proxy python3 -B tools/w3_paired_error_analysis.py probes/w3-paired-error-analysis-20261116/canonical-input.json --output /private/tmp/w3-paired-errors.json
rtk proxy python3 -B probes/w3-paired-error-analysis-20261116/verify-analysis.py tools/w3_paired_error_analysis.py
```

工具候选由免费CBC hy3供应。初稿25个语音评分与原casefold口径不符，并错误评分2条挑战非空输出；首稿、实际失败与精确修正均保留，没有集成首稿。限定供应上下文复核接受修后分析源码、无finding；其后附带的元数据补丁使用未加引号的Python字典键，实跑NameError，已由另一次单行候选修正。四次helper均完整终态、观察到的tool-use为零、报告cost=0；不把工具禁用参数或自报当实际权限证明。

最终源码SHA-256 `b801dfdf4f79311e6b943c159531817725de6db1ab65ecf86322e35bd7f103f3`，由主代理机械集成后实际通过10,879项核验，含独立原评分复算、全部对齐操作重放、S/D/I及汇总对账、确定性重跑与13项拒收负例。完整输出SHA-256 `340c6b6878ee8769fa4ca07b201c5f9f24835efefeb3256f7a9b0aa72116a175`。见[机器记录](w3-paired-error-analysis-20261116.json)；限定源码/证据复核没有独立执行父端检查，不等于P4，元数据修正后的最终实跑由父端负责。

本地证据：`.omx/artifacts/w3-paired-error-analysis-20261116/`。179份原工作内容在仅移除本次5份明确前缀后摘要全部保持，普通App/模型未操作。源码/证据已按精确10文件提交 `83b3658`，不含其他dirty/untracked改动；源码分支未推送。

GitHub Issue#15 comment `6911184049`、GitLab project461/Issue15 note `45183` 均回读确认，GL仅移除一个末尾LF；GitHub Wiki commit `9e42f76ff38f7d514bb5e0737921a2d61b31ee41` 页面逐字回读、marker唯一。GL notes首次EOF保留，按既有文档仅对该子进程移除代理并设NO_PROXY=*后恢复；不改变全局配置。Wiki首次中文路径检查因Git转义显示失败，保留原错误，按既有pitfall改NUL输出后仅目标页提交/推送；没有重发Issue。GitLab Wiki已知EOF通道零新GET/PUT尝试，本增量仍待同步。[同步收据](w3-paired-error-analysis-sync-20261116.json)。
