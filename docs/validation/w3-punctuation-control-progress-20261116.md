# W3 标点控制：单臂准备与配对实现边界 — 2026-11-16

最新结果：用户随后明确批准一次配对批次，已在真机完成 12/12 非空请求及清理。中文 CER 18/86→16/86，近英 WER 13/54→10/54，格式编辑分别 7→5、5→2，内容类分别保持 11、8；符合冻结探索条件，尚未生产采用。首 UI 故障在零 Run/空报告时修正一处 predicate 输入后继续同一未消费批次，实际仅一次 Run。见[设备结果](w3-punctuation-paired6-device-20261116.md)。下文保留授权前的准备状态，非当前执行状态；W3/R4/R5/P4 仍 open。

原 `w3-apple65-file-20261116-01` 已执行并消费授权，不再重放。本次已按用户明确例外，由主代理完成隔离六输入/12 请求同期配对 runner：37 项计划检查、506 项继承 Core 检查、未签名 App/driver 构建及 330 项实包核验通过。没有设备动作或识别，新批次执行授权仍未获得。此前完整 65 文件单臂配置包保留为准备证据，不作为下一次运行，也不能用历史 Apple65 充当同期 A 臂。

## 当前可审阅的配对 runner

源码目录 `probes/ios-punctuation-paired6/`；runId `w3-apple-punctuation-paired6-20261116-01`；bundle `io.ondeviceai.probe.w3punctpair20261116`。两份 arm framework 共同动态链接相同 Core/Speech，A 的四份源码与生产源相同，B 仅在 `shouldReportPartialResults=true` 后插入 availability 保护的 `addsPunctuation=true`；逆变换恢复 A 全部字节。它没有单臂包的额外 getter/错误控制；App 在 iOS16 以下于识别前拒绝配对实验。

实际 App 同时链接两个独立 arm 动态二进制，各自摘要和 UUID 另存，不称二进制相同。5 份真实 SwiftFileList 固定 17 个 Swift 输入，另有 1 份工程配置；测试源码未打包。65 WAV 仅供完整性核验，初始化和执行计划严格为固定六输入的 12 行。报告逐行增加 arm、pairOrdinal、request index 和配置来源；每次构建 fresh backend/client，原输入前后核验、availability、timeout、cancel、drain/close、报告 I/O 与重放拒绝逻辑保持。

| 新鲜核验 | 结果 |
| --- | --- |
| 计划测试 | 空实现先以“exactly twelve requests”失败；实现后 37 项通过，含固定 AB/BA、身份错/缺失/重复拒绝及输入枚举逆序不改变计划 |
| 继承输入检查 | 506 项通过；首调用误将 manifest 路径传给要求 fixture 根目录的参数，原错误保留，仅修调用参数后通过，未改输入/源码求通过 |
| App | unsigned Debug arm64 build exit0，6.762 秒；5 项 AppIntents metadata skipped 和 2 项 App orientation/launch warning 保留 |
| Driver | build-for-testing exit0，4.481 秒；没有执行 UI tests。原 activate、Speech 精确 firstMatch 授权处理、opt-in runId、唯一 run.tap 均保留 |
| 实包 | 330 项父检查通过；65 WAV/PCM、18 源码/配置摘要、17 实际编译输入、4 动态 framework 与 App 实际加载引用、B 独有 setter selector、unsigned 状态全部核对；selector 不证明运行效果 |
| 准备脚本 | 实际执行通过：只复制已固定本地源/既有公开 WAV，拒绝覆盖不同字节；无网络、签名、安装或识别 |

manifest SHA-256 `7e2133c9292e978ac45050349d03d4b1432f247da6dceda6facd328bb7a62453`，source identity SHA-256 `feb8981eba97cd017353aee32bcc23ecbef1c05ee3cbb89350476cbde3d52c74`。执行合同、输入选择、原评分/classification 摘要和逐 arm 二进制固定材料均可审阅。主代理已读审集成及核验；没有新的独立最终源码审核，不以旧六输入草案审阅或旧 native429 充当 P4。

## 已核验的单臂准备

本地目录 `probes/ios-apple65-punctuation/`，runId `w3-apple65-punctuation-20261116-01`，bundle `io.ondeviceai.probe.w3apple65punct20261116`。源码基线 `8c4751508050ec56b37a1bd4309f90a08eaa0d4c`；manifest SHA-256 `9a8b4517d17de39e778db6707a799c4b83e09890ba084385d1da55e664dcaadf`；source identity SHA-256 `4be505b30b9070d26b16a252e3fc7d7ea9e22ccbe8f0ab2cc10794a119af07ba`。

| 范围 | 实际证据 |
| --- | --- |
| 输入 | 原 65 项的顺序、reference、locale、WAV/PCM 字节全部保持；独立 WAV 解码和摘要通过 |
| 候选来源 | 第一项 CBC 请求 `hy3`，46.178 秒；UI 标识候选 22.14 秒。完整结果及零观察到的工具调用留档；两份 result 均无 modelUsage 字段，实际供应模型未由结果证明，不能用请求名推定 |
| 隔离 runtime | 生产源码不改；复制包仅 FILE request 创建后增加显式 `addsPunctuation=true`、availability/readback 守卫。其它 runtime 输入逐字节同生产副本 |
| Host | 506 项继承 Core 检查通过；不创建识别器的 request 配置检查验证 punctuation/on-device/partial 三项 getter，不是运行效果证据 |
| App 构建 | 通用 iOS Debug arm64 未签名构建 exit0，8.559 秒；保留三项常规 warning，不称 warning-free |
| Driver | build-for-testing exit0，4.923 秒；没有运行 UI tests 或触发 Speech 授权 |
| 实包终检 | 新鲜执行 762 项父检查通过；4 份实际 SwiftFileList 对应 12 个 Swift 编译输入；Package.swift/config 不计入编译输入；72 个 App 文件逐项存摘要 |
| 执行状态 | 新签名、安装、设备运行、识别、麦克风、资源/模型下载均为 0；候选没有新执行授权，也未获独立最终源码审核 |

候选状态为 **verified-unexecuted-single-arm-preparation-not-selected-for-next-run**。此包保留作准备证据，下一次执行不自动采用它。原 baseline 未设置 addsPunctuation，不推断默认 false；该包包含 readback 失败控制，也不是下述仅属性赋值不同的双臂实现。

## 保护检查与并发改动

首终检严格摘要比较失败，原脚本与失败收据保留。184 份保护文件中 182 份逐字节不变；131 份 SDK/历史探针/历史结果输入均不变。另两份为本任务范围外变化：

- `docs/agent-pitfalls.md` 增加了两段精确前缀，共 1306 B；只在内存移除这两段即可恢复原摘要 `a91607a02a784a1806fdf895861d9bac79bb598bc65a89ec852c9cad6e51d1c4`，实际文件保留。
- `probes/ios-file-asr-punctuation-control/acceptance.json` 已更新，旧字节没有保留，不能声称未变；当前完整文件按观察摘要保留，未回滚或覆盖。

修正检查器仅接纳这两项已记录的精确当前摘要，未来其它差异仍失败，不泛化为忽略并发改动。两段前缀及另一任务收据不纳入本任务提交。

## 下一配对实验

既有[协议](../../probes/ios-file-asr-punctuation-control/protocol.json)的草案保持原样；当前[执行合同](../../probes/ios-punctuation-paired6/execution-contract.json)已实现并构建，执行仍未授权。六份已披露结果的公开 FLEURS 输入按固定 SHA 排序选取，每语种三份；不是盲测或独立 heldout。A 保持属性未设置，B 仅在 availability guard 内赋 true；每请求 fresh backend/client，逐项 drain/close 后进入下一项。六对固定次序 AB、BA、AB、BA、AB、BA，总 12 请求。分别固定两臂源码、配置和动态 framework 二进制身份；两臂不能称相同二进制。

配对方案复用原完整 65 文件输入完整性验证，但只执行固定六输入的 12 请求，不代替原 W3 全量质量范围。各语种独立报告原 CER/WER、formatting-only 与 content/mixed；B 的 formatting edits 必须严格减少、content/mixed 不增加、两臂语音完整非空且无新增 typed error/cleanup 失败，才可报告该探索性标点信号。远场漏词、原 1110、负例非空及其它设备缺口继续保留。

实现候选请求 `hy3` 在 3.028 秒结束，进程 exit0、stdout0，stderr 明确报告 **429**，重置时间 **2026-11-17 13:40:39 UTC+8**。没有候选、没有实际 modelUsage；重置时刻不保证恢复。停止该线路，无自动重试、模型轮换或付费回退。用户随后明确回复“允许主代理本地完成本次隔离配对 runner”，因此本次实现由主代理交付，来源不归给 CBC；该例外不扩大为生产实现、其它任务或新识别授权。

## 可追溯证据

- 单臂：`.omx/artifacts/w3-apple65-punctuation-preparation-20261116/`，含提示、原始输出、两候选、构建日志、host 日志、保护首失败、明确范围外变化与 `parent-acceptance.json`。
- 配对实现限流：`.omx/artifacts/w3-punctuation-paired6-preparation-20261116/`，含固定设计、完整提示、原始空 stdout/限流 stderr、进程收据与 `parent-assessment.json`。
- 已完成的误差分析：[原报告](w3-paired-error-analysis-20261116.md)，不改原 scorer、不去标点求通过。

本记录不关闭 R4/R5、P1–P4 或 W3，不构成最终生产候选或发布接受。源码分支尚未推送。

清理仅删除本任务 DerivedData 和 DriverDerivedData 下各自的 `ModuleCache.noindex`、`Index.noindex` 四个确切目录，逻辑大小 64,614,924 B；删除前确认无跟踪文件、软链接或打开文件。保留全部编译 FileLists、中间编译证据、App/driver 产物、候选、提示、原始结果与失败。清理后重新执行 762 项检查通过；磁盘剩余约 20.68 GB，空间变动不作本任务独占归因。

随后配对包独立执行相同四目录精确清理，逻辑大小 69,418,312 B；80 个 App 实际文件、四份 framework、driver、5 份编译 FileLists 及全部日志/首失败保留，清理后 330 项实包检查再次通过。两项准备合计删除可重建缓存 134,033,236 B；第二次清理后剩余约 20.10 GB。配对准备起点 205 份保护文件中 201 份字节不变，另外 4 份仅本任务精确前缀或本任务 README 段落变化，内存移除后恢复原摘要；另核 131 份历史输入不变及四份共享文档的原完整字节保留。没有范围外新差异，旧单臂准备检测到的两处其它任务变化不回滚、不混入本次提交。
