# W3 集成发布证据覆盖与充分性复审（2026-11-16）

文稿由hy3供应文本复审，主代理另行验收；提交/同步另留收据。原始review.json保留不覆盖。本稿不是发布接受、不是法律结论、不是fresh rebuild。审阅者未独立访问原件、未执行构建/设备/下载、未读取二进制；父端此前fresh核12个已有APK/解析制品与161freeze，newBuildOrDevice=false。父端dispatch后另直接读当前SDK/Demo源，观察BACKEND_ID/defaultBackendId=sherpa.onnx.whisper、NativeWhisper、tiny资产目录，QwenProductionIntegrationProved=false、sourceChanges=0；这是父端新增观察，未供应给原复审，不能说原复审已读源，也不能证明Qwen生产集成。

## 结论

hold_release。P1–P4、R4、R5六门仍未通过，P1–P4完整范围不缩减。Whisper-tiny打包候选轨道是实际打包证据：固定AAR/四APK身份、68项通知770479B打包保留、16 ASR SO和consumer绑定等局部证据有效但有限。Qwen实验质量轨道只有模型/归属材料与24/65探针，中文20/20 CER 6.78%，第25项因1.5GiB实验守卫内存停止；失败和未执行项保留。两轨不同，当前尚未接合为同一固定实际候选。24/65后内存停止和1.5GiB守卫不变；R5的sampledRSS≤1GiB是未批准产品草案，与实验守卫独立。

## 证据边界与门状态

| 门 | 结论 | 实质缺口 | 下一充分证据方向 |
|---|---|---|---|
| P1 | 未闭合 | 21成员、JVM 125 payload、124运行时类、JNI 480覆盖和native版本字段均为有界子项；完整输入闭包与同一候选绑定未足；JNI 6处Companion接收者差异原样保留 | 本地可逆清单/已有输入绑定/差异核验在现有授权内；闭合可组合上游记录、revision/补丁/配置映射、同输入重建或覆盖-可信度说明；不要求唯一加密证明或全历史AGP重放 |
| P2 | 未闭合 | 递归最终组成、archive/链接/机器码保留为资料缺口；55缺失body按最终保留与适用性判断，不能要求所有NDK测试patch；source-host 429不重试 | 从最终成员反推直接、静态嵌入和传递组件，结合构建/链接材料、适用补丁和通知归属逐层核对，允许组合证据并接受覆盖与可信度复审 |
| P3 | 未闭合 | 68通知770479B已证实际打包保留，但不是完整适用清单；实际交付/消费链的完整适用性和Qwen若入选的通知仍未验 | 从实际候选交付物提取通知并与P2逐项核对，保留原文、路径、摘要、修改说明与归属；真实打包/消费链优先于材料ZIP |
| P4 | 未闭合 | 无同一固定候选的独立完整发布接受；本稿和父端局部核验都不能替代 | P1–P3齐备后由独立复审明确候选身份、材料范围、覆盖限制、问题处置和接受/不接受结论 |
| R4 | 未闭合 | 原65范围不缩减；tiny/Apple失败、Qwen 24/65内存停止、英文4/20及未执行项保留；最近ADB为空证据不等于本轮fresh状态 | 先按已授权诊断合同定位内存根因/修复，再另立固定合同做原65完整覆盖；旧65/heap不重放，模拟器不替代真机 |
| R5 | 未闭合 | RTF≤1与sampledRSS≤1GiB是待批草案；1.5GiB是实验守卫；采样RSS非true peak，paced idle非持续高负载 | 产品批准预算和复用策略后，同设备/输入/运行时取得完整资源证据，区分cold/warm、duration与case wall、采样RSS与true peak |

## 已授权诊断与闭环顺序

单文件诊断合同w3-qwen-terminal-20261114-01已获授权，代码/161freeze已准备，未prepare/消费。实体连接条件改变后，主代理先重新通过fresh守卫/preflight，再按固定合同执行诊断；这不需要重新批准产品RTF/RSS预算，也不需要最终生产采用选择。诊断用于观察终态与阶段成本，为内存根因分析及后续修复提供证据；单片不保证独立定位完整根因，不自动填充原65，不关闭R4/R5。

可继续条件按“诊断→内存根因/修复→完整质量/资源验收→最终候选/发布”分段成立：第一段在实体连接与preflight后可按已授权合同执行；第二段依诊断结果决定后续根因分析或修复；第三段需另立固定全量合同、授权runner、原65与可靠资源证据，并按产品预算裁定；第四段需同一固定实际候选的P1–P3完整材料及适用再分发条件齐备后进行P4；W3整包仍须R4/R5通过，不能以发布复审替代。后续预算或采用结果不能倒过来阻挡已授权的前段诊断。

## 下一路径、阻断与范围变化

第一优先是上述诊断；执行前feasibleNow=false，原因是实体连接/preflight条件未在本supplied-context轮满足，不是因为需要新审批。P1–P3的本地可逆候选准备、清单/已有输入绑定与差异核验在现有任务授权内；supplied context未给出一条非重复的已授权本地闭环方法时，明确写“未识别”。不得要求用户现在选择Whisper旧失败或Qwen未通过作为推进前提，也不得把产品先选唯一轨道强加为所有P1–P3补证和已授权设备诊断的串行前置。最终P1–P4必须同一固定实际候选，当前两轨未接合仍是真缺口。

可以停止无依据地继续累积等价静态证明，但不能把supplied-context限制说成仓库穷尽调查或权限不足。阻断为：实体连接条件未改变、固定全量合同未立、P1–P3闭合方法未识别、产品预算/采用结果未形成、同一候选材料未齐。范围变化无：不新增事实、审批门或范围；保留其他编辑，不递归委派；本轮不碰签名资产。R1–R3按TECNO已知限制保留，不重开AiAi、麦克风或下载。

## 审阅与父端分工

hy3供应文本复审只提供可集成文稿和证据边界判断，不冒称直接执行、不替代父端验收。原始rankedNextActions中的本地准备项是泛化建议，没有给出非重复具体交付物，父端不把它当成已找到可关闭缺口的新执行任务。父端负责实际字节核验、fresh preflight、构建/设备收据和最终接受裁定；提交/同步另留收据。
证据入口：[25份供应上下文清单](../../probes/sherpa-runtime-materials/integrated-release-review-20261116/context-manifest.json)、[父端12制品身份检查](../../probes/sherpa-runtime-materials/integrated-release-review-20261116/fresh-candidate-identities.json)、[父端当前SDK源码观察](../../probes/sherpa-runtime-materials/integrated-release-review-20261116/parent-current-backend-observations.json)、[原始复审](../../probes/sherpa-runtime-materials/integrated-release-review-20261116/initial-review.json)、[外部修正稿](../../probes/sherpa-runtime-materials/integrated-release-review-20261116/corrected-review.json)、[父端处置](../../probes/sherpa-runtime-materials/integrated-release-review-20261116/parent-disposition.json)。供应上下文原文、提示与完整流事件留在本地忽略目录 `.omx/artifacts/w3-integrated-release-review-20261116/`；本轮未新增构建、下载或设备操作。
