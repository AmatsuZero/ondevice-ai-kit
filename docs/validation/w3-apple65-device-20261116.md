# Apple65 设备端文件批处理验证（2026-11-16）
相关：[路线图](../roadmap/W3-speech.md) · [总交接](../HANDOFF-2026-11-05-next-session.md) · [iOS交接](../HANDOFF-W3-IOS-STATUS-2026-11-16.md) · [陷阱](../agent-pitfalls.md)

状态：单次批次 `w3-apple65-file-20261116-01` 已消费，禁止重放；不构成产品质量或预算通过。物理 iPhone17,2 / iOS27.2.1(24B156)，`apple.speech.recognizer`，隔离包 `io.ondeviceai.probe.w3apple65file20261116`。probe `69a8d9c`、执行 HEAD `38306591fe1b3478b6d6761bf278af98d7f92ab3`，`8537933` 仅 sourceBase；506checks/unsigned build 属前段、非P4，本段仅 sign/install/run 一次。`distributionBoundary` 保持历史 isolated-unsigned-probe-not-production；实际副本已签名，source manifest 未变，不称安装二进制未签名。

结果按每行 recognitionAttempted/cleanup，manifest 行不证明请求。real report：65 attempted / terminal / input postcheck / cleanup。

|组|原始观察|
|---|---|
|中文|20/20 speech|
|近英|20/20 speech|
|远英|10/12 speech + 2 typed1110|
|噪声|1 nonempty No + 8 typed1110|
|静音|1 nonempty No + 3 typed1110|

总52 nonempty + 13 typed1110，typed1110 仍错误、不转 synthetic empty；SPEECH 仅50/52。噪声负例未人工听辨、无完整噪声误报率结论，合成静音 nonempty 为假非空；52-speech成功条件未满足。

指标仅成功行条件计算：NFC+casefold+whitespace，标点/数字保留；普通话95/590=16.1016949% CER，近英75/375=20% WER，远英103/197=52.28426396% WER（仅10行，全12未验证）。无批准Apple阈值、不套Qwen阈值。Android旧arena 20/20中文、40/590=6.78%、近英仅4、24/65停1.5GiB，非新跑；Apple/Qwen为固定65输入/参考/locale的描述性跨设备比较，非单变量因果或双端通过；Qwen旧已披露、非新盲测。输入441.102s/63 unique PCM。

运行时07:16:50.519Z–07:17:05.657Z（上海15:16:50.519–15:17:05.657）；实跑15.138s，非UI全程/非600s。request wall sum14.745126917、p95 0.540385958；recognition sum14.633831755、p95 0.490243125；whole-request RTF p95 0.073491703265903（全65含错）。AppPID RSS 231样本、0缺失、max117669888B、maxgap112.569416ms且无>200ms；cancel/join/最终样本与清理时序已查。仅AppPID采样，非systemSpeech/真峰值；无GPU/能耗/热/持续负载或产品预算，无E2E指标或产品通过；旧600s/40calls未重放。

边界：无mic/AiAi/下载/模型/生产变更，模型目录未操作/哈希未复核；同一既有签名材料，3份Mach-O的section内容和69份资源未变，未签名原件保留。UI仅隔离hy3候选、无生产App变更；镜像SCStreamError-3811、XcodeGen signal5、profile选择65、CLI -test-iterations 1不可用（默认单次为不同恢复路径）、首轮UI在Run前因iOS27嵌套allow标识重复失败（Documents空/无Run/allow tap）。修正spec和firstMatch并签名driver后，最终UI 1/1 passed 20.569s/process0；r3 app.activate、启动即已授权（确认来源未归因）、无setup/allow tap、正好1次Run；r2有setup但无显式allow。source/candidate/failed requests保留、无quota重试。清理前仅probe+runner、host可能从未安装；父all3断言先失败后改owned子集；仅卸载probe+runner，45原始App记录/URL一致；ordinary demo前台launch accepted 0，非渲染前台证明。截图/AX保留。

证据：`.omx/artifacts/w3-apple65-device-20261116/` 下 report-terminal{,-confirm}.json、parent-acceptance.json、scores-*.json、signed-receipt.json、driver-r3-signed-receipt.json、test-r3-process.json、single-batch-r3.xcresult、restoration.json及失败/helper原始件。real report两次终读同SHA `9942aac31ea59a91684bc29e722f59945bd25f91196faab0f38fa296371d53b3`/168754B；manifest `99584dec071c0e8a3b47f510443e002d80058482cb48d580122a0af88c86a1fb`、inputbinding `b3838b23136aa1be8c760915b565a2f192976ae3299631fcd3470f8df5334e88`、source identity `fe1c00efb4cb5d0157ec2e690c515b6dee184ed867cfca16cbe60296e306c0e6`、scorer `d398bb35438b9f4eb399fc404e02c0c2a7184a7cea7c7a6d32e4a321f8e2b39e`；实际14 selected overlay hashes在frozen identity。protected176据前段验证未变；本地证据不保证远端可用。[portable summary](w3-apple65-device-20261116.json)由父节点机械复制实际receipt。[限定独立证据复核](w3-apple65-device-review-20261116.json)已返回 accept-bounded-evidence、无 findings；其仅阅读 supplied context，未独立执行哈希/设备/签名/恢复检查，不是P4。父端已执行实际检查；复核的缺失上下文与限制原样保留。本地提交与发布同步已按下节回读记录。

W3 R4/R5/P1-P4仍开放，production candidate仍null，ordinary SDK/Demo Whisper与experimental Qwen不变，无缩范围/豁免。下一步推进本批离线误差分析及生产候选/阈值/供应链工作；不重放本批。Android无新物理变更，不走USB/ADB/old FILE；实体变化后仍可按既有未消费单片合同先做fresh守卫/preflight，不增加产品预算或采用选择前置。GLWiki EOF route停/pending，无GET/PUT/retry。

## 提交与同步

本地证据提交 `1fa949a27f5c26dc1ff15748916f9c78c93127f2`（11文件）；GH#15 comment `6910441325`、GL#15 note `45138` 及 GitHub Wiki commit `5576d2198b7e6c31e95a745921cda2477951f836` 均已回读。GL仅移除一个末尾LF。GitLab Wiki旧EOF通道未重试、本节仍待同步；源码分支未推送。见[同步收据](w3-apple65-device-sync-20261116.json)。

本任务精确移除9处无open/writer的可重建缓存（66,591,238逻辑字节），保留已接受App、driver产物、全部候选/raw/失败及两次实际UI测试xcresult。未清全局缓存或普通App模型；清理初始进程子串匹配误将xcodebuildmcp当xcodebuild的失败也保留，修正为实际可执行basename后执行。
