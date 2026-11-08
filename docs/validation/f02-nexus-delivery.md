# 当前状态与范围（2026-11-08 02:23 Asia/Shanghai）

本页为 F02 Nexus 交付准备记录，**非发布批准**。截至本时点的当前状态优先于下方所有历史段落中的过期“当前验收/待办”表述：

- **Skiko `.6` 交付全部完成**：115/115 上传、匿名精确字节/SHA 回读、全新缓存双 iOS 消费者构建/74 通知材料、生产默认接入（settings.gradle.kts + demo/shared）与有界独立生产接入审查均已完成。**无新上传、消费者构建或接入工作**。资源/最终整分支审查仍分别计 W8/W2 开放。证据：`artifacts/verification/f02-nexus-dot6-published-20261107/{delivery-receipt,parent-run,parent-assessment}.json`、`artifacts/verification/f02-nexus-dot6-remote-consumer-copilot-toolchain-20261107/summary.json`、`artifacts/verification/f02-production-integration-20261107-2150/summary.json`、`.omx/artifacts/copilot-skiko-production-final-review-20261107-2213/parent-assessment.json`。
- **Identity 本地提取已完成（取代旧"实际评分未跑"表述）**：116 个已准备样本完成本地提取，3 genuine（本人独立拍摄probe对注册照）与112 target-vs-other enrollment 描述性比较已在本地评估；有界单探针人功能基线，非 held-out 多人生产 FAR/FRR/阈值/PAD 验收。证据：`artifacts/verification/f10-host-extractor-accepted-20261107-2234/actual-local-summary.json`。
- **HarfBuzz 固定版本已提交软件的根 Old MIT 授权未按类别排除生成代码**：既有已接受源码范围报告支持该内部托管软件分发（受通知约束）；缺失原始数据集来源或 SIL403 为证据局限，非已确立禁止或自动新分发/重打包阻断。整分支发布批准仍为 false。证据：`.omx/artifacts/copilot-harfbuzz-applicability-20261107-2010/host-launch/candidate/REPORT.md`、`.omx/artifacts/copilot-harfbuzz-applicability-20261107-2010/host-launch/parent-assessment.json`、`.omx/artifacts/cbc-harfbuzz-specific-inputs-20261106/parent-assessment.json`、`.omx/artifacts/cbc-public-permissions-20261106/parent-assessment.json`；无需臆造独立作者许可或强制外部法律签署。
- **唯一延期项 F03 低系统实体设备**：用户离开设备；模拟器安全函数允许，但模拟器不能认证相机/活体/物理 GPU/性能/低系统硬件。
- 付费 CBC 暂停，最多一个原生子助手；不把当前实时助手计数或额度重置写死进持久队列。基础 roadmap 仍记 10/12（历史计数，非完成百分比），P4/P11 开放，release **not-ready**。

下方 2026-11-07 及更早段落为历史记录，原文保留。

---

## 2026-11-07 当前验收（历史记录，2026-11-08 已取代）：Skiko .6 匿名远端消费已通过

Copilot直接交付的消费runner经订正/8项离线检查后，父用既有Gradle9.4.1/JDK21、全新Gradle依赖与项目缓存实际执行7个任务，424.377秒/exit0，双iOS framework链接及74材料通过。新缓存下载文件按实际module files.name→files.url映射，逐项对冻结115文件计划核对字节/SHA通过；独立回算framework摘要与既有checker再次验收通过。`artifacts/verification/f02-nexus-dot6-remote-consumer-copilot-toolchain-20261107/summary.json`记录remoteConsumerValidated=true；通用材料checker自己的remoteConsumerValidated=false仍原样保留其窄范围。**不能再列为“.6未上传/未消费”**。实际上传115/115早已通过，此处是后续独立消费，不重做上传。完整F02来源许可/生产依赖与预算不随此关闭。

员工照获本地Identity评估/校准授权已记录，113原照+3本人独立自拍已作匿名、去元数据、尺寸归一化准备；实际评分未跑。hy4free提取器910.473秒自然上游empty_model_response/0文件/无父超时/无429，已停止不自动重试，用户接手线路待答。Copilot汇总工具310.376秒直接3文件/5离线测试及600独立数值比较/实际CLI通过。飞鸟guard旧候选已读审，隔离同版本依赖副本追加后实际编译88.805秒/BUILD SUCCEEDED，无新模型推理/生产锁修改。CBC付费暂停、无新原生助手。

## 2026-11-07T18:02:51+08:00：Skiko .6实际交付115/115完成，消费验证交本地Ollama

固定`.6`/原115路径新交付实际完成：全115匿名preflight404，逐项just-before404后单次PUT，115匿名byte/SHA回读通过、0冲突/0错误/0删除/无自动重试。证据`artifacts/verification/f02-nexus-dot6-published-20261107/{delivery-receipt,parent-run,parent-assessment}.json`；凭据仅父进程关闭回显stdin/env，不入argv/文件/助手。**不能再将本次`.6`列为未上传**，旧404/prepared记录保留其历史范围。新独立Gradle消费仍待实际运行，不预先打勾。

用户要求简单消费验证交Ollama；本地qwen3-coder:30b任务`.omx/artifacts/ollama-dot6-consumer-verification-20261107`/unified23968已实际启动，只通用消费runner/离线测试/DELIVERY，无凭据或私有数据，父验收后执行新缓存双iOS link/74材料。免费hy4free飞鸟guard订正unified15288已实际启动，只改hook校验前置，父随后隔离build/forward；不重发既有任务，无耗时超时。cbc付费暂停、原生最多1且当前0运行。

已完成本人iPhone评估1参考+3独立自拍及恢复；SR本地工具/6000比较/真实48帧双路对照通过，质量仍unverified。10/12、P4/P11/not-ready保持，本轮真实增量以上述精确子项计，下一步远端消费与飞鸟内部边界取证。

# F02 内部 Nexus 交付准备

## 2026-11-07 16:12：实时状态核实，保留已完成子项

按用户要求重新核实，匿名GET当前`0.144.6-ondevice-f02.6`三个模块的`.module`：
`skiko`、`skiko-iosarm64`、`skiko-iossimulatorarm64`，在既有hosted
`maven-ugreenhome-release`与消费group `UgreenHome-Maven-group`共6个精确URL全部HTTP404。
无凭据、重定向、上传或管理API。原始响应及摘要：
`artifacts/verification/nexus-dot6-status-recheck-20261107/summary.json`。
这只证明上述6地址当前无法取件，不证明所有仓库/版本均不存在。

**已经完成**：`.6`本地115文件发布/归档一致性、双iOS SDK链接和74材料App保留。
原`actual-final-receipt.json`仍明确`uploaded=false`、`remoteConsumerValidated=false`，
远端consumer的`prepared.json`仍为`prepared-not-run`。本地工作不重做；本版本在上述
入口的实际远端发布/消费尚无通过证据。Apple F09的GitLab上传、远端消费者构建及
真机图片/视频推理已通过，是另一制品；不能合并成“Nexus交付都未做”或“都已完成”。

用户后续回答“我本人”，已确认Skiko与Apple包由用户本人维护，见[维护归属](delivery-maintenance.md)。
下文各历史“维护团队未定”不再是当前待答问题；组织名称与具体hosted选择仍未指定。
本决定没有触发新上传或修改冻结候选。

## 2026-11-07：Copilot 补充远端消费者验收准备

[匿名消费者清单](f02-nexus-consumer-checklist.md)来自一次47.217秒的完整限定审查，
父处置已区分已知门槛、重复意见与实际待补收据：独立缓存／匿名配置、实际组件／variant、
逐项GET字节和74份许可材料摘要。外部wrapper复用本身不是缺陷，已保留三份来源摘要。
未执行消费者、取件或上传；具体hosted／本次范围待答复项保持，不重问、不刷新旧404。

## 2026-11-07：当前 .6 归档与发布计划逐文件核对

用户要求将Nexus任务分给Copilot。本次限定准备脚本审查65.85秒完整返回，随后
只读核对器候选96.6秒返回，实际均无工具调用。主代理发现候选Maven路径层级错误，
保留真实失败后修正；新增URL歧义路径与nofollow能力缺失的6项真实失败也已修正。
9项合成测试涵盖更改成员后重算外层摘要、缺失/新增/重复成员、链接/特殊文件、实际读取中变更。
原候选不能称为原样通过，Copilot结果也不是法律或发布批准。

新增`tools/nexus_plan_archive.py`不依赖网络、凭据或磁盘解包，已对当前实际`.6`
计划/归档的115个普通文件逐项核验路径/大小/SHA256；108,791,304B归档仍为
`2f426cf8ff6a868abbe49a919445720108c19d7e9a1e58d23ebc3707c9d18fe6`，
旧计划摘要`d7ebde9aac7877f050f29e13e94b6f766b4d3317b902c990ec666c347854baf8`保持。
证据：`artifacts/verification/f02-nexus-plan-archive-20261107/actual-receipt.json`。

```sh
rtk proxy python3 -m tools.nexus_plan_archive \
  artifacts/verification/f02-nexus-dot6-preflight-20261106/upload-plan.json \
  artifacts/verification/f02-dng-notice-delivery-20261106/skiko-0.144.6-ondevice-f02.6-maven-repo.tar.gz
```

本次关闭“计划条目与归档内容直接一致”的本地检查子项，没有改旧冻结文件、重新发布、
上传、查询管理API或取件。输出明确`uploaded=false`、`remoteConsumerValidated=false`。
此前具体hosted/本次候选上传范围待回复项继续保留，不重复询问；旧115次404不会因此刷新。
获相应授权且发布完成后，仍需无凭据空缓存的逐项HTTPS实际取件、大小/摘要核验、Gradle
variant解析和消费者编译。来源条款适用、独立批准、生产依赖/资源及原P4/P11门槛不变。

## 2026-11-06：.6 补齐 DNG 原始通知，当前本地候选递增

[DNG补充](f02-dng-notices.md)确认冻结.4漏收实际编译头的AOSP原始声明；新本地候选
`0.144.6-ondevice-f02.6`含74材料，115版本文件归档108,791,304 B，SHA256
`2f426cf8ff6a868abbe49a919445720108c19d7e9a1e58d23ebc3707c9d18fe6`。
正常publication、六notice/source JAR、两consumer及unsigned App材料保留已通过；
40原Skia库摘要不变，App复用.4链接框架。没有上传或生产切换，旧候选/计划均保持原样。
下方.4的115次404仅覆盖旧坐标，不是新.6预检或本次发布授权。维护负责人已明确，
无需重复询问；具体hosted选择、精确新候选发布与远端消费继续单列。

追加：`.6`自身的SDK双iOS框架实际重链15任务/107.28秒和新unsigned App74材料保留已通过，
不再只依赖.4框架。新.6的115个精确路径匿名HEAD均404，完整新计划在
`artifacts/verification/f02-nexus-dot6-preflight-20261106/upload-plan.json`；无凭据或写操作。
具体hosted/本次内部候选上传范围的确认已发出，待答复；远端消费者已准备但未执行。

## 2026-11-06：.4 精确坐标预检与输入校验补丁审查

本历史预检使用冻结的`0.144.6-ondevice-f02.4`作为交付候选，归档108,785,484 B，
SHA256为`e59efab02e31d2f609e7328b5f8118c86e47e47029c74c4437a70c2b9dc68869`。
已从正常publication图形成115个版本文件的逐项URL/字节/SHA清单，本地全部匹配；
明确排除15个可变模块级metadata/sidecar。对已发现hosted候选
`https://nexus.ugaiot.com/repository/maven-ugreenhome-release/`的115个精确路径
逐项匿名HEAD，均404且无重定向；未使用凭据、管理API或任何写操作。
这是时点读证据，不能当作原子不覆盖保证、目标仓库最终选定或上传批准。
计划与观测：`artifacts/verification/f02-nexus-dot4-preflight-20261106/upload-plan.json`。

第四输入校验补丁和回归工具已取得新的限定native独立审查APPROVE/0 findings，
见[校验审查边界](f02-notice-validation-20261106.md)。它不在冻结.4发布脚本或旧agy七文件
审查中；不因此重写.4或称完整.5已发布。公开条款提取经父代理逐条核验，错误解释已否决；
历史来源适用、维护归属、具体hosted选择、发布授权与远端匿名消费者验收仍开放。

## 2026-11-06：.4 补齐 HarfBuzz ISC 并修复根项目收集

[HarfBuzz 核对与 .4 交付](f02-harfbuzz-notices.md)：实际编译的 hb-ucd.cc 有独立 ISC
授权/版权，冻结 .3 材料曾遗漏；新 .4 保留 115 个原样文件头及生成输入来源记录，材料增至
72 份。正常发布、实际 SDK 双 iOS 链接、unsigned App 构建及 72 文件逐字节保留已通过。
收集脚本显式声明 common/kotlin-metadata 属性，修复未应用 KMP 插件的 SDK 根项目解析失败。
.3 通过的打包检查不能证明许可完整；.1/.2/.3 均不改。原生 40 份库摘要不变。
仅本地交付验证，无上传/生产切换/装机。Microsoft/SIL/历史数据条款、独立许可批准、远端
匿名消费、维护责任和生产资源仍待闭合；Nexus 匿名拉取及发布权限已明确，不再询问。


## 2026-11-06：本地 App 材料保留已证明

[unsigned App 证据](f02-app-notices.md)确认 .3 候选69文件通过正常Xcode资源阶段保留，
并非仅Maven解析；下方最终App保留未验为历史。Nexus匿名拉取/发布权限无需再确认，
本轮无上传或远端消费。条款审核、远端实际消费与生产资源验收仍未完成。

2026-11-06，用户提供内部 Maven 服务 `https://nexus.ugaiot.com`。
当前结论为 **渠道已发现，制品尚不可发布**；本页是交付准备，不是发布批准。
按用户要求 solo 执行，未使用或保存提供的凭据，未上传、覆盖、删除制品或改生产依赖。

用户确认 **Nexus拉取无需鉴权**。消费者使用匿名HTTPS读取，不配置用户名、密码或token；
发布凭据仅用于上传。管理API的403不代表制品下载需要鉴权，不再作为消费权限缺口。
制品就绪后的远端消费验证仍需检查实际依赖解析、字节摘要与编译结果。

用户最新明确：提供的账号**可上传、可覆盖、不可删除**。账号权限以该说明为准，
不再作为待确认项，也不再为此查询管理API。禁止覆盖既有release是发布流程约定，
无需服务器禁止覆盖；无删除权限不妨碍新增版本发布。消费可先使用hosted直连，
group成员查询不作为打包修复的前置条件。当前实际阻塞是包的依赖闭包和许可附件；
远端连接与独立消费在制品准备完成后验证。

用户随后明确授权一次 hosted 策略 GET 重试。该次审批通过，实际 curl 退出35：
`Recv failure: Connection reset by peer`，HTTP000，无HTTP响应或策略结论。
授权次数已用完，不自动重试；回执为同证据目录的 `authorized-retry-outcome.json`。
以下429记录是首次未执行的历史结果，group403和其他分发门槛不变。

## 已核实与待核实

匿名 `/service/rest/v1/repositories` 返回200，清单确认以下两个入口存在：

| 用途候选 | 类型 | URL |
| --- | --- | --- |
| 发布 | Maven hosted | `https://nexus.ugaiot.com/repository/maven-ugreenhome-release` |
| 消费 | Maven group | `https://nexus.ugaiot.com/repository/UgreenHome-Maven-group` |

名称和类型只能支持候选方案；用户没有明确选定具体仓库或维护团队。
清单未给出 Maven version/write policy。匿名 group 配置 GET 返回403且响应体为空，
组成员未知；hosted 配置 GET 在执行前遇到自动审批服务429，未发出该请求，策略未知。
没有改用其他入口绕过审批，也没有重试失败审批。

匿名 search 对 `repository=maven-ugreenhome-release`、`group=org.jetbrains.skiko`、
`version=0.144.6-ondevice-f02.1` 返回200、空items、空continuationToken。
这仅说明匿名可见搜索结果中没有该版本，不能证明全库不存在；写权限已由用户明确，发布前须重新核对
精确制品路径和所有拟发布坐标，不覆盖现有版本。

原始响应及只读校验入口保存在
`artifacts/verification/nexus-skiko-readonly-20261106/`，摘要为 `discovery-summary.json`。
TLS校验保持开启。没有制品下载；本地109 MB归档仅做SHA256和文件数复核。

## 可审阅的交付方案

1. 上述hosted为发布候选，也可直接作为消费入口；group成员与维护责任在交付时记录，
   不以管理API可读或服务器禁止覆盖为技术修复前置条件。当前没有维护团队指派，不从CI账号名推定。
2. 专用版本只递增；已发布版本不移动、不覆盖、不复用。修复使用新版本，撤销以消费者
   禁用／升级和登记原因处理，不静默替换旧字节。此为拟采用约定，尚非服务器强制策略的证据。
3. CI通过受控secret注入凭据；源代码、命令参数、报告及归档均不携带凭据。
4. 从固定源码产生受支持的完整publication并定稿同版本许可附件，验证每个坐标／依赖／
   checksum。发布前形成精确文件清单；获授权发布后，从空缓存独立消费者核验远端字节、
   Gradle variant、两份iOS Klib及实际App，不能沿用本地消费结果代替。

## 当前制品门槛

最新冻结候选 `0.144.6-ondevice-f02.3` 已通过正常双iOS-only Gradle发布：
3模块/115文件/6引用闭合，69文件许可材料在3份notices及3份source JAR内完整存在。
修改源码声明已核实，独立消费者显式取件并保留69文件、双iOS框架链接通过。
归档108,522,716 B，SHA256 `3276597b2f422091ee13a5854c79625d5248ca49c54fa9d1c136eb427836ca93`。
[发布和消费者步骤](../../probes/skia-atlas-text-cache/Skiko-ios-notices.md)已跟踪。

文件级/历史Unicode条款和独立许可批准、最终App材料保留、远端匿名取件/编译与生产资源
仍未验；不把正常发布/材料收集当作合规裁定或远端通过。
冻结.1/.2/.3保持原样，后续制品修改使用新版本；详见[分发审查](f02-distribution-review.md)。

Nexus读取按用户说明使用匿名HTTPS，发布账号上传/覆盖/不可删除权限无需再次确认或查询
管理API。未使用发布凭据，无上传、覆盖、删除或生产切换；远端匿名消费待制品就绪后验证。
本轮没有设备操作/新benchmark，F02和发布仍open/not-ready。

### 2026-11-07T13:51:16.218736+00:00 已关闭：Skiko `.6` 生产默认依赖接入

Copilot两轮候选（461.195秒4文件、328.714秒2文件订正）经父校验，生产settings与demo/shared已默认只对两个iOS target替换`.6`，精确module/version路由到匿名Nexus。真实生产Gradle **79.891秒/exit0，319任务（77实际/242up-to-date）**；双iOS debug framework链接通过，四compile/test配置实际选中patched root/native，8条klib artifact及notice jar摘要与已发布计划保持，74材料逐字节摘要通过；Android APK成功、compile配置无Skiko模块、没有Android替换规则。**生产默认接入不再列待办**，不重复115文件上传或全新cache消费。

证据`artifacts/verification/f02-production-integration-20261107-2150/summary.json`及`selection-report.txt`；portable verifier已原样保存`probes/skia-atlas-text-cache/verify-production-selection.gradle.kts`。首次命令因无效继承JAVA_HOME在Gradle前失败，保留原错，进程级选用项目现有JDK21后通过；无全局配置变更、device安装或upload。完整F02资源/最终审查仍分别计W8，不把此子项通过冒作整个release。
