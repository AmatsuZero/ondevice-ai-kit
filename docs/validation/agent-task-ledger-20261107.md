# AI助手任务台账 — 2026-11-07

本台账由主代理维护。状态取自真实进程/完整结果/验收收据，历史任务不因本表刷新而重发。
所有助手先看`docs/agent-pitfalls.md`；禁工具任务由父提供适用摘录。供应商输出不等于验收通过。

| 任务 | 助手及能力匹配 | 当前状态 | 范围/句柄/证据 | 下一步 |
| --- | --- | --- | --- | --- |
| F10经验曲线旧正文请求 | cbc hy4free，复杂统计纯函数候选 | terminal-unusable；1647.306秒自然结束，344064B JSON截断 | unified session52594 / PID19468；`.omx/artifacts/cbc-f10-empirical-curves-20261107/`；无工具/数据下载/阈值选择 | 旧请求不修JSON/不重发/不再轮询；用户后续明确改为直接文件交付，见下行，主代理不代写实现 |
| F10经验曲线直接文件交付 | cbc hy4free，用户新指定由Agent写文件 | completed；467.413秒自然终态，模块/测试原样集成；18定向、3110穷举核对及完整563tools通过 | `.omx/artifacts/cbc-f10-file-delivery-trace-fix-20261107/`；56完整JSONL事件/最终success，原RED/GREEN与父验收保留 | 不再轮询session98174；原DELIVERY“无权限失败”与实际一条Bash拒绝/缺文件Read不符，已另存父更正；文档与限定静态复核见下行 |
| F10 PAD接入梳理初稿 | cbc hy3，独立源码/接口关系梳理并直接写文档 | terminal-needs-corrections；106.841秒写出6675B文档，源快照未变 | `.omx/artifacts/cbc-pad-integration-map-20261107/`；初稿与父处置保留 | 已有复制/缺配置测试不能再当新缺口；raw score不能凭空包装成必选outcome的LivenessDecision；修正任务见下行，不轮询session2004 |
| F10文档定稿与限定静态复核 | cbc hy3，模块使用文档/PAD图修正/源码测试限定意见 | terminal-rejected；420.615秒，实际API/并列阈值/PAD路径有误，三文档未集成 | `.omx/artifacts/cbc-f10-docs-final-20261107/parent-assessment.json`；原文件另存rejected-candidate，进程成功不等于内容正确 | 不再轮询10691，不重复广泛文档任务；保留可用实现及563测试事实 |
| Android持续重开探针 | cbc hy4free，复杂有界Kotlin实现 | terminal-provider-error；931.375秒自然终态、empty_model_response，无文件 | `.omx/artifacts/cbc-identity-reopen-probe-20261107/`；独立新探针；20周期/至少10分钟，无自动任务超时 | 原始上游finish_reason=error保留，无父超时/已观察quota；不再轮询44550；用户选定agy免费接手，见下行 |
| Android持续重开探针接手 | agy gemini-3.8-flash-medium，用户明确选择免费路线 | terminal-needs-corrections；410.031秒实际写出19690B Kotlin，父已完整读审，两处需修正，未集成/编译 | `.omx/artifacts/agy-identity-reopen-probe-20261107/`；原文件SHA497b47c1…；保持全局会话写入拒绝日志 | 不轮询66400；交回助手修正KEEP_SCREEN_ON清理失败吞错与测量结束时间改写，见下行 |
| Android持续重开探针两处修正 | agy免费，同一已选线路的最小修正 | terminal-no-edit；22.852秒，read_file缺headless读取规则，空response/文件未变 | `.omx/artifacts/agy-identity-reopen-probe-fix-20261107/parent-assessment.json`；result外层SUCCESS不能当交付 | 不轮询32085；不是quota/auth或任务超时。修正精确session-only读取规则后新任务见下行 |
| Android持续重开探针读取范围修正 | agy免费，实际文件修改由助手完成 | terminal-delivered；136.692秒，19928B Kotlin两处diff原样集成；父397构建任务/43秒及签名/DEX/1046源码检查过 | `.omx/artifacts/agy-identity-reopen-probe-fix-readscope-20261107/`；SHA22179df7…；普通App25私有文件/四SR/runner/权限/TTS预检过，未安装 | 父随后从Android SDK证实main runnable抛错无法被外层catch可靠接住；不称最终验收，进一步修正见下行 |
| Android持续重开主线程清理修正 | agy免费，最小失败路径修正 | terminal-delivered；74.402秒实际20243B Kotlin，唯一mainthread清理diff原样集成 | `.omx/artifacts/agy-identity-reopen-mainthread-fix-20261107/`；SHAd60781e7…；34编排边界检查过 | 最终397构建任务/49秒通过；唯一20周期/600.9秒真机及全部恢复/清理通过，见文末当前结果；63373/77249均终态，不再轮询 |
| Android持续重开汇总 | cbc hy3，严格纯函数与负向测试 | terminal-needs-corrections；351.851秒交付，父独立复现两个缺陷，未集成 | `.omx/artifacts/cbc-identity-reopen-reducer-20261107/parent-red.json`；后段1秒周期误接受，warmup峰值遗漏/输出schema错 | 原件保持，不再轮询47486；定向修正交回助手，见下行 |
| Android持续重开汇总定向修正 | cbc hy3，已复现两处错误的最小修正/回归 | completed；161.615秒原样集成；父15测试/221变异、完整578tools通过 | `.omx/artifacts/cbc-identity-reopen-reducer-fix-20261107/`；共享协议保持，不允许为迎合错误修改协议 | 新回归对原候选真实4fail，修正15GREEN；助手自行加cd使测试命令被拒已保留，实际测试由父执行；完整578/53.677秒、源码/九保护/lock保持 |
| F09远端消费者 | 主代理，既有工具的实际网络/构建/运行验收 | bounded-pass；新GET/344文件→全新双平台consumer编译→模拟器资源运行→精确卸载 | `artifacts/verification/f09-remote-consumer-20261107/summary.json`；787文件摘要、两App各24材料、九保护保持 | 实际iPhone资源及另建新消费者图片/视频推理已通过，新增别名清理；其他生产门槛保持，同一Mac不称另一机器通过 |
| F10经验曲线绑定到真实score报告 | cbc hy4free，现有纯模块有界集成 | completed；1959.539秒，模块/16新测试原样集成 | `.omx/artifacts/cbc-f10-bound-curves-20261107/`；父78聚焦/1024报告/2048曲线/512识别差分与594tools通过 | 不再轮询95982；无阈值/生产校准，限定复核见下行 |
| F10绑定曲线限定复核 | Copilot个人免费额度auto，实际gpt-6-luna | completed；30.26秒，五文件无finding/0工具/0session错误 | `.omx/artifacts/copilot-f10-bound-curves-review-20261107/`；父核对源hash一致 | 不再轮询35383；非整分支/生产批准 |
| F10内部知情同意清单 | cbc hy3，用户选定渠道的直接文档交付 | completed；初稿101.253秒/schema拒绝，98.49秒修正仍需两处纠错，最终123.550秒文档原样集成 | `.omx/artifacts/cbc-f10-consent-collection{,-fix,-final}-20261107/`；最终13816B/SHA8be1b7fe…，父按真实validator/statistics核验 | 98161/70209/78215均终态，不再轮询；未采集/签署，身份与PAD用途分开准备 |
| F09运行时字节绑定说明 | cbc hy3，短证据文档直接文件交付 | completed；119.291秒初稿、88.631秒计数/语义修正、44.304秒引用修正；最终7049B原样集成 | `.omx/artifacts/cbc-f09-runtime-equivalence{,-fix,-final}-20261107/`；父核对339→341/338相同、135045627B、仅许可材料差异；最新状态另见新推理记录 | 不再轮询58439/42312/41131；byte绑定不伪造新consumer执行，本次已另跑新consumer真实推理 |
| F09远端制品新消费者推理 | 主代理，现有探针与真实制品验收 | completed；全新consumer Release11.037秒/24App材料，iPhone图片3067.592ms/视频2281.231ms，两项passed | `artifacts/verification/f09-remote-inference-20261107/summary.json`；14模型摘要、close/租约释放、无新快照/输入保持及新别名清理过，原44App保持 | 不再轮询18730/35688；同一Mac、来源许可与完整复审/质量预算仍缺，不重建SDK |
| 员工照功能探针首次请求 | agy免费，通用Kotlin探针直接文件交付 | terminal-no-delivery；25.417秒，未供应README被headless规则拒绝，无文件 | `.omx/artifacts/agy-identity-functional-probe-20261107/`；无quota/耗时超时，私有数据读取拒绝边界实测通过 | 28829终态，不再轮询；明确13个源码路径后新任务见下行 |
| 员工照功能探针明确上下文 | agy免费，中等复杂实现 | terminal-compile-failed；600.547秒实际26129B Kotlin，父编译两处Android JSONObject.keySet unresolved | `.omx/artifacts/agy-identity-functional-probe-contextfix-20261107/`；独立构建原失败保留；未安装/未传图；DELIVERY字节数与实际不符不采信 | 3550/53924均终态；不改生产边界，交回实际编译/文件保持/路径/清理修正 |
| 员工照探针编译与父审修正 | agy免费，边界明确的小范围纠错 | terminal-delivered；243.732秒，26551B源码，父按diff确认JSON keys/实际输入hash/父目录/清理状态修正 | `.omx/artifacts/agy-identity-functional-probe-compilefix-20261107/`；源码尚待独立审查问题修正/最终编译，不计设备通过 | 3402终态，不再轮询；保留候选/实际工具失败，主代理未替写实现 |
| 员工照通用探针独立复核 | Copilot个人免费auto，实际gpt-6-luna | completed；78.96秒，0工具/0session错误；3条finding | `.omx/artifacts/copilot-identity-functional-probe-review-20261107/`；终态persist失败/KEEP_SCREEN_ON原状态捕获/精确错误码记录；父确认getter为defensive clone | 66405终态；是限定旧候选复核，不是最终源码或发布批准 |
| 员工照探针独立复核最小修正 | cbc hy3，三处快速实现纠错 | terminal-delivered；187.206秒三项修正已核对；实际编译暴露局部val重复赋值 | `.omx/artifacts/cbc-identity-functional-reviewfix-20261107/`；93460终态，无父超时，照片/原名/私有清单未供应 | 不再轮询；失败编译原件保留，下一行仅改声明关键字 |
| 员工照探针最终编译修正 | cbc hy3，单关键字实际文件交付 | completed；18.405秒，val→var，最终27643B Kotlin原样集成，397任务18.562秒构建通过 | `.omx/artifacts/cbc-identity-functional-compileval-20261107/`；9951/60684终态；实际源码SHA917134df… | 唯一功能run与负向/清理均已过，不再轮询或重跑 |
| 功能探针用法与验收文档 | cbc hy3，两个有界文档直接交付 | completed；初稿102.878秒，四项文档事实修正70.607秒，最终两文档原样集成 | `.omx/artifacts/cbc-identity-functional-docs-20261107/`；仅通用源码/aggregate facts，无照片/原名/私有清单 | 93643/40973均终态，不再轮询；实际norm绝对值/空host输出/相册措辞/Markdown链接修正通过；不是生产校准批准 |
| F08时序质量定位 | cbc hy4free，较复杂的固定源码/真实证据比较 | running；44815，无自动超时/无付费调用，隔离文件交付 | `.omx/artifacts/cbc-f08-temporal-rootcause-20261107/`；11份明确上下文，旧native/tensor/logit/margin/两场景失败及当前adapter摘要保持 | 比较pinned upstream与当前adapter，区分已证bug/有意差异/未知假设；只交一个最小新诊断，不重复旧生成或放宽正式评分 |
| cbc长输出诊断与后续交付规则 | 主代理，本地原字节/启动器核查，不调用模型 | completed；原启动器已重定向文件且无超时；2MiB合成链路逐字节通过，内部根因未确定 | `.omx/artifacts/cbc-output-diagnosis-20261107/diagnosis.json`；更新根AGENTS和踩坑记录 | 后续新复杂任务使用真实沙盒限制的候选文件交付/短收据；本轮直接交付已验收，但不证明旧截断根因，不重发旧请求 |
| Nexus74材料核对器 | Copilot auto，个人额度的小工具 | candidate已由父修正并集成，32聚焦及545全tools通过 | `.omx/artifacts/copilot-nexus-notice-materials-20261107/`；40.77秒完整响应；实际`.6`73索引材料+index通过，保留父schema错误RED与修正 | 最终源码独立复核待下行任务；远端消费/许可仍未通过 |
| Roadmap当前表交叉审计 | cbc hy3，短一致性审查 | completed，父已处置 | `.omx/artifacts/cbc-roadmap-status-crosscheck-host-20261107/`；119.69秒/0工具；原受限启动失败在不带host的同名目录，未计模型失败 | 2条采纳、1条部分采纳；父额外发现已改当前表，不再运行此任务 |
| Nexus材料核对器最终审查 | Copilot auto，独立限定源码复核 | completed，25.75秒/0工具/0finding；父核对源码摘要保持 | `.omx/artifacts/copilot-nexus-notice-final-review-20261107/`；供应4份最终源码/测试及真实schema边界，只读结果 | 最终限定审查已记录，不再轮询session27684；不是整分支批准 |
| .6真实variant/消费者核查 | cbc hy3，短metadata/config审计 | completed，84.803秒/0工具；父逐项处置 | `.omx/artifacts/cbc-dot6-variant-audit-20261107/`；3份实际module原字节已对冻结计划摘要，含当前未运行消费者配置 | 12个variant逐字段对原metadata、3notices路径及既有property核实；期望清单已存，否决新缺失推断；不再轮询session37246 |

已知停止线路：原生审查429、Qoder403、dsh429、OpenCode401/free429不自动重试。
agy本轮已按用户选择实际调用，状态见表；旧终态不得反复轮询。
Ollama已有本机模型可承担小型候选，但本轮未新发任务。免费优先；用户最新覆盖：每次付费cbc调用前先确认具体任务/模型/范围，不因耗时自动切换。

唯一获准延期项为低系统版本设备实机验证（当前F03，旧对话编号不一致）；F10授权校准/攻击数据、Nexus精确出口/本次上传范围属于本周待解决输入，不是新增豁免。完整拆分见 `.omx/plans/roadmap-critical-path-20261107.md`。持续执行期间每小时汇报真实增量；未配置跨会话后台推送。已完成的GitLab上传、本地SDK链接、普通导入/注册/清除不重复。

最终持续重开probe SHAd60781e7…、test APK0f1564cc…（215574B）已构建397任务/49秒；签名/DEX/1046源码检查过，fresh原App/25私有文件/四SR/runner/权限/TTS预检过，分块staging/安装过。pipeline unified77249已终态，唯一`identity-reopen-20261107-20cycles-a1`已在下段记录验收通过。编排在`artifacts/verification/android-identity-sustained-reopen-final-20261107/pipeline.py`，任一步失败停止，不重放。

F09远端消费者补充：`artifacts/verification/f09-remote-physical-20261107/summary.json`实际iPhone modelBundleLookup/metalLibraryLoad通过；源consumer未重编，Mach-O有效section/UUID及24材料保持，新别名已卸载，既有App安装URL/保护文件/lock/profile保持。无模型推理/新上传。

Android持续重开已真实验收并恢复：final/summary.json，20周期/200调用（160测量）/600908.639ms/462前台检查，p50/p95=128.708/139.669ms；峰值570290176B，close-wait末−首=-5492736B。首次collector非终态因host提前返回，原失败保持；回读同PID11557/同runId至terminal，未重发推理。原App/test/runner/25私有/四SR/权限/TTS及精确临时清理全部过。仅有界10min，非零泄漏/预算/生产通过；见android-identity-reopen-20261107.md。

最新员工照边界：用户明确“仅可用于功能试验”。本地226JPEG中选12个匿名缩小副本，去元数据/原图保持；不推断人数/会话，不作生产校准、准确率统计、阈值选择或PAD数据。派发助手只获通用源代码/契约，私有输入目录在OS沙盒中拒读。设备同次功能试验已在下段实际验收；该句所述未传入阶段是历史。

2026-11-07 最终功能验收：唯一`functional-photo-20261107-a1`，12processed/usable/128维归一化提取/隔离加密注册/实际`THRESHOLD_UNVERIFIED`/PURGED。主机255，保留同次持久completed/terminal报告，不重发；raw SHA017d3dbe…。缺输入负向durable failed/0图/无误报成功。最终397任务18.562秒test-only构建通过；collector对实际正例及12个错误码/计数/隐私行字段/清理变异检查过。

66846清理已exit0；额外主机只读复核84866已exit0：原App/test/runner/25私有摘要/四SR/权限/TTS恢复，精确APK暂存与两个probe工作目录缺席。受限首次ADB因本机smartsocket EPERM失败不算设备断开，原错误摘要另存；未要求用户重连。设备12图/manifest及本地12派生图/原名映射/私有清单已精确清除；226原JPEG全部重hash不变。keysCleared仅provider backing arrays/maps，不承诺所有内部SecretKeySpec副本擦除。`artifacts/verification/android-identity-functional-final-20261107/summary.json`绑定127文件，功能-only；生产校准/PAD/准确率/阈值/benchmark均未晋级。

功能文档最终：`.omx/artifacts/cbc-identity-functional-docs-final-20261107/`，README2982B/SHA413b5685…、验收2739B/SHA3db71c7d…已按源码和实际报告验收并逐字节集成；初稿及四项修正diff保留。当前仅F08时序质量44815运行。
