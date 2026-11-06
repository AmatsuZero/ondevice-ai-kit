## 2026-11-06 当前：hy4free完成新版LiteRT-LM核查，维护归属已记录

用户选择`cbc + hy4`后，按本地文档调用`cbc --model hy4free`，仅提供官方公开源码，
148.31秒自然结束、success、0工具请求；无需继续轮询或重发。固定0.19.1公开Kotlin/JNI仍无
可报告成功/失败的异步native cancel/join接口，内部WaitUntilDone的失败/超时状态在析构被忽略。
主代理核对关键行/摘要并否决“Engine析构完全不等待”和若干“新版新增”推断，
详见[版本核查](validation/android-litertlm-later-barrier-20261106.md)。不升级0.18.1、不改同步factory，
不重做已有推理；本次API研究结束，异步能力继续后置。原native429不重试。
公开来源与任务原始输出分别保留于`.omx/artifacts/litertlm-later-api-public-20261106/`和
`.omx/artifacts/cbc-litertlm-later-barrier-20261106/`；没有新源码、测试、构建、设备或上传。

## 2026-11-06 当前：Apple/Skiko维护负责人已明确为用户本人

用户对内部SDK接收/维护归属问题回复“我本人”。[维护归属](validation/delivery-maintenance.md)
记录Apple候选包和Skiko修复包均由用户本人维护；不再重复询问负责人，不从CI账号推断组织。
接收组织名称尚未提供，Skiko具体hosted仍是候选；没有因此上传/覆盖或修改旧冻结包/历史计划。
源码/测试保持7e96485不变；P11最新指针仍以`benchmark/p11/grouped-report-manifest.json`为准。

## 2026-11-06 上一审查轮：两个增量技术审查通过，Skiko .4 Nexus精确预检完成

新native code-reviewer只读审查已分别返回APPROVE/0 findings：F09生成器的App notice复制修复
及聚焦回归、F02第四输入校验补丁及portable Gradle回归。四文件摘要/有界结论保留于
`.omx/artifacts/native-incremental-reviews-20261106/`。这是新的限定范围，不是重启旧失败任务，
也不扩大旧agy七文件授权/审查，更不是完整分支、法律或发布批准。旧“新补丁/生成器修改
未审查”现在是历史；原Copilot仍只覆盖修复前版本，新修改由本次独立审查覆盖。

Skiko .4的115个版本文件清单/本地字节/SHA已核对，15可变metadata不进上传计划；
对既有hosted候选的115个路径匿名HEAD均404，未上传、未用凭据或管理API。
证据`artifacts/verification/f02-nexus-dot4-preflight-20261106/`；其404是时点观察而非原子保证。
具体hosted最终选择、源条款适用、发布授权与匿名远端消费者仍需闭合；维护负责人现已明确为用户本人。

cbc hy3仅收到公开Unicode/HarfBuzz/MS-LCID原文，110.63秒完成、无工具调用；三段授权引文
核验通过，反读MS条款/泛化排除生成表/新增专利门槛/解释文字冒充exact quote均被父代理否决。
原文/处置`.omx/artifacts/cbc-public-permissions-20261106/`，公开抓取摘要另存；不据此批准许可。
新的资源探路确认现有无recorder候选无同generation析构/free/线程退出信号，不能靠重复旧trace
补齐；保留已完成58线程配对的instrumented边界。九保护路径/Apple lock保持。
基础10/12、P4/P11/release不晋级，无新benchmark/质量样本。最新P11快照以manifest指针为准。

## 2026-11-06 上一实现轮：F09 App材料遗漏已修复，25.3 MB内部候选已上传并回验

[本轮验证](validation/f09-app-notice-retention-20261106.md)：冻结SDK生成consumer的真实成功
模拟器App缺全部24notice；旧实现回归RED后，最小post-build路径保留修复通过19 consumer回归、
完整tools414/414（58.757秒）和generic模拟器/设备双目标unsigned Release构建，App24摘要全匹配。
SDK未重编译，设备未操作。首次启动器Python入口错误保留；修正模块入口后的构建才是有效结果。
本机Ollama15.85秒返回候选核查，父代理复现/修复/实测；不是独立批准。

用户对精确25.3 MB候选上传问题回复`ok`后，私有极狐项目461/home/common/ondevice-ai-kit
单次PUT HTTP201，认证下载25,288,674 B/SHA639205a7…a152ed完全匹配。
包名`odai-apple-mlxvlm-internal-candidate`，版本`20261106-sha256-`加完整SHA；无Release/tag。
具体URL/授权/单次上传/下载收据见`.omx/artifacts/f09-git-channel-preflight-20261106/`及验证文档。
不用重复上传；原坐标已存在，原本地包/冻结证据不改写。108.8 MB Skiko仍走Nexus，本轮未上传。

原agy7/cbc4源码仍匹配；生成器已改变，原Copilot审查仅覆盖修复前版本，新增修改仍缺独立审查。
不能再写当前12/12全匹配。接收组织/维护人员、源条款、完整复审与生产验收继续开放。
本轮两个工程子项关闭，基础10/12、P4/P11 open、release not-ready；无新benchmark或质量帧对。
九保护路径和Apple lock保持；新证据`artifacts/verification/f09-app-notices-20261106/`，
P11快照以`benchmark/p11/grouped-report-manifest.json`当前指针为准。继续剩余门槛，不重跑本轮片段。

## 2026-11-06 上一准备轮：F09完整内部交付包25.3 MB，F02已获授权并完成补充审查

[F09交付候选](validation/f09-internal-git-delivery-20261106.md)：用户选择Git，超过50 MB走Nexus。
Apple完整ZIP25,288,674 B，按规则准备内部Git制品/Release；Skiko `.4`108,785,484 B走Nexus。
新Apple副本含20框架/4C/18资源/24份notice。原编译提交f3899bb和全部非notice/原build receipt保持，
许可独立绑定3b5150d，149源码输入等价；ZIP解压和新consumer生成/24材料逐字节保留通过。
不重编译native/consumer、不操作设备、不上传。接收组织/维护团队、实际上传端点/远端消费仍未定。
本机Ollama14.74秒返回方案建议，主代理采纳来源JSON结构/值校验，逐条处置及原文保留。
原agy7/cbc4文件摘要与Copilot最终consumer正文仍匹配，专项清单已移除将这些补充审查写作未取得结果的表述。

用户明确回复“1.可以”，授权原F02七文件/61651字符外发。agy225.17秒完整SUCCESS/1turn，
无新finding；全部7份源码未变。它未指出已用真实Gradle复现的旧publisher缺陷，不能推翻RED；
3123f3c第四验证补丁不在packet，未据此取得审查。原拒绝记录保留，后续授权/结果/父处置新增，
证据 `.omx/artifacts/agy-f02-notice-review-20261106/`。不要再把该包写作未发送或重复申请授权。
详见[F02边界](validation/f02-notice-validation-20261106.md)。不升格为完整分支、许可或发布批准。

本轮交付证据 `artifacts/verification/f09-git-delivery-20261106/`；P11对应快照
`artifacts/verification/p11-readiness-20261106-internal-delivery/summary.json`，以manifest当前指针为准。
九保护路径/Apple lock保持；10/12、P4/P11 open、release not-ready，无新增benchmark。

## 2026-11-06 历史：F02新发布输入完整性缺陷已复现并补齐校验

[F02新补丁与证据](validation/f02-notice-validation-20261106.md)：原notice发布脚本只检查索引存在，
真实Gradle在文本篡改后仍正常发布两份错误JAR（RED保留）。新增第四补丁
`probes/skia-atlas-text-cache/Skiko-ios-notice-validation.patch`校验schema/路径/集合/SHA/符号链接，
并在归档依赖中再次验证。14项离线Gradle回归通过；实际修复后buildSrc编译和72材料notice-only
JAR通过，up-to-date时validator仍执行、真实篡改副本拒绝。首次嵌套git apply跳过的结果已否决并保留。
冻结.4的130文件/6notice-source JAR/40原生库/消费者和App材料复核仍通过，源码/制品原样保留。
本轮没有完整.5 Maven发布/原生重建/SDK-App重链/上传/设备操作；.5仅单独notice JAR测试名。
完整tools suite新鲜414/414，50.034秒；与14项Gradle检查分开计数。

Ollama本机两文件补充审查12.58秒返回，父代理复现一条、否决删除source JAR材料的另一条。
新的公开条款native researcher启动429无结果并停止；父代理核对上游来源/Windows具体许可参考，
不据此裁定Microsoft/SIL/历史数据或独立批准。SIL主站仍403，不绕过。详见同一验证文档。
原agy7文件/61651字符packet未改、仍未发送，既有具体载荷授权问题待答复；新第四补丁不在原packet内。
下一次不要把旧packet的审查范围扩大到此补丁。九保护路径/Apple lock保持。
agy当前纯文本连通复测15.00秒完成SUCCESS/1turn，使用gemini-3.8-flash-medium，未发送项目源码；
证据 `.omx/artifacts/agy-availability-check-20261106-5043fdc1/result.json`。不据此替代F02审查。
F02本轮汇总为 `artifacts/verification/f02-notice-validation-20261106/summary.json`；
P11对应快照 `artifacts/verification/p11-readiness-20261106-notice-validation/summary.json`。
最新P11指针以 `benchmark/p11/grouped-report-manifest.json` 为准；10/12、P4/P11 open、release not-ready。

## 2026-11-06 历史：F09 Linux独占发布实测通过，F02外部审查待具体载荷授权

[F09当前验证](validation/f09-consumer-generation-20261106.md)：生成器570b64a0原字节在本机已有
Linux/aarch64、Python3.12.15/glibc2.41镜像通过13项检查；并发只一胜者、文件/符号链接/既有目录
保留、跨文件系统EXDEV不copy均有实测。新增两项仓库回归在Linux通过，macOS consumer模块18/18。
镜像不下载、容器禁网/只读源码/非root，未改其他容器/生产代码/普通App/模型。
Ollama候选22.79秒返回，原候选缺导入/异常和mock问题由主代理修正；原文及处置保留。
证据 `.omx/artifacts/ollama-linux-publish-checks-20261106/` 和
`artifacts/verification/f09-linux-publication-20261106/`。不要把18项聚焦结果当作新的完整412项套件。

已备好F02 .4独立技术审查的7文件/61651字符packet，自动审批拒绝向agy发送这份新内部源码载荷，
要求具体授权；尚未启动、没有发送。用户问题仍待答复，不能改用其他外部线路绕过。
prompt/scope/拒绝记录 `.omx/artifacts/agy-f02-notice-review-20261106/`；选择只做本地则不发外部。
该审查不裁定Microsoft/SIL/历史数据条款，也不授权上传Maven制品。
最新P11指针仍以 `benchmark/p11/grouped-report-manifest.json` 为准；基础10/12、P4/P11/open/not-ready保持。

## 2026-11-06 历史：P11纳入精确候选真机终态，历史审查表述已澄清

[P11更新范围](validation/p11-candidate-lifecycle-20261106.md)：本次追加已有ASR13项/SR6项原生断言、
原始拒绝及同次SR重验、普通App完整恢复和F09竞态修复证据。快照为
`artifacts/verification/p11-readiness-20261106-candidate-lifecycle-final/summary.json`。
两项有界真机验收关闭；原8组benchmark及p50/p95不变，新增样本0、质量帧对0，九行原gap保留。
不重传/安装/重跑已完成Android片段。412tools测试与25协议检查仍单列。

用户询问的“三个审查实例容量不足”是10月19日P10历史记录，保存内容没有具体供应商/模型名。
10月24日代码/架构限定审查已完成，Android专项曾429；ROADMAP历史段落已标明，不能用于判断当前助手可用性。
cbc hy3已完成新的4文件F09 validator审查；3条意见按v2契约/实际ar归档核对后无新增已验证阻断，
10项static回归通过，处置见P11更新文档；不升格为独立发布批准。

agy新的7文件packager补充审查首轮认证失败（num_turns0），原错保留；用户说明网络恢复并明确要求一次重试。
retry1已正常完成：保存登录认证成功、完整SUCCESS/1turn、348.12秒，7文件哈希匹配、findings为空；
缺失上下文仍保留，不升格为whole-branch/许可/发布批准。conversation `aee4c8b7-f83c-4429-a13c-c01425931416`。
cbc与agy这两项任务均终态，无需继续轮询或重发。证据 `.omx/artifacts/agy-f09-packaging-supplement-retry1-20261106/`，
其中 `parent-assessment.json` 和对应 `ask-agy-...md`保留核验。无凭据/默认配置改动。
基础10/12、P4/P11/open/not-ready及F10、最低系统、许可/资源/质量边界保持。

## 2026-11-06 最新：精确候选 ASR/SR 私有终态通过，普通 App 已恢复

[本轮实际验收](validation/android-persistent-candidate-20261106.md)：TECNO/API35 上，
精确 e0438c0/b90e 主 APK 的 ASR 固定输入13项原生生命周期断言通过；SR六项原生断言通过。
四项记录器成功/故意失败自检均被正确排除在推理证据之外；没有新录音、模型下载或重建主包。
ASR PID20393；SR PID21382，native runId `0ac8554d-ec79-4047-8786-56870a1112dc`。

SR 首次被校验器拒绝：真实已批准输入为64×48/2帧H.264+AAC，校验器/单测误用了另一夹具的128×96。
核对原输入 SHA `8277f1da...65d8c`、冻结源码的4倍请求、实际解码及音视频PTS后，
修正严格输出256×192并新增拒绝其他尺寸的回归。同一原始报告/保留媒体重验通过，**没有重跑推理**；
原始拒绝 `run-sr-fixed-inference-20261106-collected.json` 保留，最终见同目录 `...-revalidated.json`。

恢复协议已实现、离线/真机IO检查通过，并实际完成恢复：原main/test APK、runner、25私有摘要、
权限/TTS和四份SR原文件均核对；五份暂存APK/113块精确清理，普通MainActivity返回。
SR目录因未证明原先不存在而保留。证据目录 `artifacts/verification/android-persistent-candidate-20261106/`，
摘要 `device-summary.json`，恢复终态 `restore-complete.json` / `restore-state-3.json`。
**无需重传或重跑本轮ASR/SR。不要再运行旧preflight覆盖before/备份。**

最新完整 tools suite **412项通过/47.682秒**；另有25项离线协议检查，不能相加冒充同一套测试。
第一次412项运行的三个Swift缓存权限错误日志保留，正常host缓存访问的同套测试通过。
Copilot恢复代码审查的四处问题已修复并做RED/GREEN；Ollama完成真实测试候选任务，
复杂协议审查的五条重复建议未采纳。调用、适用范围、无自动超时和踩坑提醒已记入根本地AGENTS。

已提交：`8a1e940`安卓报告工具、`22389f8`F09竞态、`f46c420`助手知识/交接。
后续尺寸修正和最终验收说明属于新的本地提交；九保护路径/Apple lock保持不变，无push。
10/12、P4/P11 open、release not-ready和F10 `THRESHOLD_UNVERIFIED`保持；
下一步沿原followups处理尚未关闭的真实门槛，不再重复本轮固定输入和已完成F09构建。

## 2026-11-06 历史：F09 竞态修复通过，安卓五份 APK 已暂存

本机新增 Ollama `qwen3-coder:30b`（约18GB）已实际接到离线测试候选任务，
localhost 推理19.99秒完成；父代理修正硬编码路径/导入和边界用例后，8项离线检查通过。
只覆盖 `lifecycle_run.py` 的 runId/ADB错误边界，不是整个设备协议验证。
提示词已附踩坑摘录；调用/API/本地额度策略已补入根目录本地 `AGENTS.md`，
证据 `.omx/artifacts/ollama-lifecycle-tests-20261106/`。未用新的付费 cbc 模型。

[F09 修复及验证](validation/f09-consumer-generation-20261106.md)：manifest 单次冻结，
验证/拷贝/Swift 常量/receipt 同源；最终发布使用 native exclusive rename，目标存在即失败，
API/文件系统不支持则拒绝，不退回普通覆盖 rename。Copilot 指出的占位目录替换窗口已修复。
最终独占重命名实现的 Copilot 复核已返回完整结果，findings 为空；不替代独立发布批准。
完整 tools suite **411 项通过，54.756 秒**；第一轮 Ruby2.6/3.4 gem 环境冲突的失败日志保留，
只修正测试子进程 PATH 后重跑。Linux 分支仅文档核实，未在 Linux 运行。
hy4free 修复调用自然结束但 JSON 截断，无可用候选；没有监督者超时或循环重试。

安卓 candidate/rollback/原test/ASRtest/SRtest 五份暂存均完成，整包 SHA 匹配固定值，
见 `artifacts/verification/android-persistent-candidate-20261106/stage-resume-06.json`。
安装前五份整包链路核验和安装后私有数据/权限检查已补齐；**尚未安装、尚无本轮推理通过**。
agy 的 `lifecycle_run.py` 候选已审查补强，但设备执行尚未验证；restore 协议未完成，
下一步先完成并验证恢复脚本，然后才安装/自检/执行固定 ASR、SR 和恢复普通 App。
不可用旧 `device.py` 的 preflight 重写已保留 before/SR backup；先刷新设备连接与活动 instrumentation。

新增[助手踩坑记录](agent-pitfalls.md)：cbc/dsh/OpenCode/Copilot/agy、Ruby、ADB、报告与竞态。
根目录本地 `AGENTS.md` 要求开工前/遇错查阅，no-tools 提示词由主代理附相关摘录；
已用于新的 Copilot 最终发布复核任务。免费优先；有限非免费 cbc `glm-5.3-flash` /
`space-bunny` 仅用于有理由的高优实现，尚未实测或消费其额度。
九保护路径/Apple lock 继续保留；10/12、P4/P11 open、release not-ready 和 F10 边界不变。

## 2026-11-06 历史：私有报告候选已构建，Copilot 已投入任务

[本轮检查点](validation/android-persistent-candidate-20261106.md)：Android ASR/SR
test-only 私有报告 overlay、校验器和回归已完成；两份离线 test APK 构建通过，
完整 tools suite 405 项通过。普通 App/test/模型未替换，尚无本轮候选装机或推理通过。
根目录本地 `AGENTS.md` 已增加 Copilot 1.0.106 的可复制调用、额度/无超时约束；
首条实际任务 71.34 秒返回校验器，auto 选中 `gpt-6-luna`，无工具调用。
父代理审查补强后，953 份源码/两份测试 APK/构建日志/九保护路径/Apple lock 复核通过，
8 项负例拒绝通过；只读脚本位于
`artifacts/verification/android-persistent-candidate-20261106/verify.py`。

APK 分块暂存已核对 candidate 的前 8 块；第 9 块 EOF 后手机 offline，
保留断点，不据 push 输出宣称整包成功，不立即重复要求用户重连。
先检查第 9 块实际状态再续传；完整 rollback/test 暂存和 run/collect/restore 协议
尚未齐备，不可直接安装。详见上方检查点及保留 `stage-chunks.py`。

F09 hy4free 审查已完成并复现 manifest 多次读取与输出目录覆盖两处竞态；
独立修复候选仍在运行，无自动超时，不重复发起。
现有句柄/证据：`.omx/artifacts/cbc-f09-consumer-fix-20261106/`，
统一执行 session `39472`（仅诊断信息，续会话先核对进程再用）。
收到候选后先将新测试运行在旧实现取得 RED，再集成修复和验证；不重建 native SDK。
两处 pre-fix 实测在 `artifacts/verification/f09-consumer-race-20261106/before-fix.json`，勿覆盖。
10/12、P4/P11 open、release not-ready 与 F10/最低系统设备边界保持。

## 2026-11-06 历史：waterfall 同机 SR 对照与外部 CLI 分工

沿用本地获授权 Kawaida Waterfall 素材，无新增下载或采集。TECNO/API35 的
`waterfall-20261106-01`、PID7242 已完成 nearest/bilinear/ESRGAN 三条48帧路径；
同一软件解码器与编码器、输入/转换/编码设置/封装。输出均480×720/24fps、无音轨，
设备/host/report摘要一致。PSNR依次30.444918/30.706528/29.970242，
SSIM0.852670/0.861147/0.858508。ESRGAN低于nearest8/48帧、低于bilinear39/48帧；
平均SSIM高于nearest低于bilinear，不同于鸟类场景全部低于两对照。
两场景不足以给出主观/时域质量或模型整体优劣结论；不新增资源benchmark或关闭门禁。
见[第二场景结果](validation/f11-android-sr-waterfall-20261106.md)。

新fixture白名单只改隔离probe；offline assemble/lint332任务通过、0error/7warnings。
清理前确认terminal及无probe进程/活动instrumentation；专用包与三份摘要匹配staging删除。
原main/test APK及路径、runner和25项模型/私有状态摘要不变，普通App前台；9保护路径/Apple lock不变。
本地证据：`artifacts/verification/android-sr-waterfall-20261106/`，`verify.py`只读复核。

用户最新允许外部CLI分工，覆盖此前一律solo限制：hy3承担简单纯工具或下载/摘要任务，
hy4free承担中等复杂任务，主代理负责设备、集成和验证。本轮不需要下载。
`cbc --model hy3`产出报告校验器，`cbc --model hy4free`产出观察器；父代理审查修订并集成。
观察器识别exit0空白/损坏JSON为尚不可读，不将host退出当成功，不重启设备请求；
报告校验器核验身份/源码/codec/尺寸/48帧和未验证质量结论。二者已用于保留报告/采集脚本；完整tools回归369项通过（53.4秒）。
OpenCode免费模型返回`provider.auth401/api_key_expired`，未改凭据、未循环重试，任务转hy4free。
提示词/原始输出在`.omx/artifacts/simple-delegation-20261106/`和`ask-*-sr-tools-20261106.md`。
外部分工是实现辅助，不等于独立发布审查；不重试旧429 reviewer。
最新readiness：`artifacts/verification/p11-readiness-20261106-android-sr-waterfall/summary.json`。
基础仍10/12，P4/P11 open，release not-ready；F10无授权校准数据、THRESHOLD_UNVERIFIED保持。

# Foundation / Demo 新会话交接 — 2026-11-05

## 2026-11-06 当前入口：Android 同设备 SR 三路对照已完成

[结果与限制](validation/f11-android-sr-controlled-20261106.md)：TECNO/API35，
`controlled-20261106-02`/PID3027，三路同decoder/encoder/color/mux各48帧完成；
nearest SSIM0.990077、Android bilinear0.992679、ESRGAN0.984451，SR对两基线均48/48较低。
新SR decoded SHA与历史普通App输出完全相同；本片固定profile输出保真度未改善，
不归因于不同桌面codec，也不升级为主观质量/普遍模型结论，不改生产模型/halo/encoder。
初始无页面probe被TECNO Hiber冻结（所有线程do_freezer_trap），已保留现场；
仅增加非导出前台Activity后，同一媒体源码快照完整成功，build/lint和2项源码准备测试通过。
缺输入终态失败报告已实测；额外cancel检查因USB/观察脚本问题无启动/终态证据，保留unverified。
独立工具包/四个哈希核验staging已清理，原App/test/runner/25份模型私有摘要核对见证据。
证据 `artifacts/verification/android-sr-controlled-foreground-20261106/`，只读`verify.py`。
最新readiness：`artifacts/verification/p11-readiness-20261106-android-sr-controlled/summary.json`。
solo、10/12、P4/P11/open/not-ready及原后置门槛保持。不要重复本片三路实验刷新记录。
后续需有不同获授权场景或针对质量问题的具体假设；不凭本轮负向分数任意换模型。

## 2026-11-06 当前入口：Android 同编解码 SR 对照工具已构建，待真机

[运行协议](../probes/android-sr-quality/README.md)：独立 test-only 包
`io.ondeviceai.probe.srquality`，不覆盖普通 App/test/模型。五份生产输入快照仅增加
codec-name observer；nearest → Android bilinear → ESRGAN 共用原 decoder/color/encoder/mux。
私有 AtomicFile 记录进度/失败/终态，输出要求 1280×720、48帧、无音轨及相同 codec 选择。
首次构建的 completedFrames 字段错误已修复，离线 assembleDebug/lintDebug 通过
（0 errors/4 warnings），318 tools tests 通过。未运行设备/取消/失败报告验证，不是质量通过。

证据与冻结 APK：`artifacts/verification/android-sr-controlled-probe-20261106/`。
本轮新鲜 ADB 清单为空；设备连接后先检查包/活动 instrumentation，验证缺输入失败报告，
再用已有授权 CRF18 clip/ESRGAN 模型执行一次对照，回读同一 runId，不因断连重启。
先核对 APK/source 输入摘要；源代码若变更需重新 prepare 到新目录。
不重测旧历史分数、不改 halo/生产 encoder、不替代成模拟器结论。
仅补本地工具，没有新 benchmark 样本；readiness 仍引用下节已提交 fidelity 快照。
solo、九保护路径/生产 lock、10/12、P4/P11/open/not-ready 和全部后置门槛保持。

## 2026-11-06 当前入口：Android SR 真实输出逐帧质量已测量

复用历史 TECNO 48帧输出，无新推理/装机/采集：[实际结果及限制](validation/f11-android-sr-fidelity-20261106.md)。
SR PSNR/SSIM=43.646096/0.984451，同CRF18输入FFmpeg bicubic lossless基线=47.334832/0.993559；
48对均较低，但decoder/色彩/encoder混杂，不裁定模型单因或主观质量通过。
工具新增逐帧score；第24帧损坏回归RED→GREEN，未改模型/halo/生产encoder。
证据 `artifacts/verification/f11-android-sr-fidelity-20261106/`；不要重跑相同分数来刷新记录。
最新 readiness：`artifacts/verification/p11-readiness-20261106-android-sr-fidelity/summary.json`。
下一质量实验需要可比同设备baseline和更广授权场景；原后置边界、solo及10/12不变。

## 2026-11-06 当前入口：Apple Identity 声明随 App 交付

两 Apple 生成器遗漏 OpenCV/YuNet/SFace App 资源的问题已修复，生成前读取三份原文，
缺失/空文件/symlink 拒绝；Combined 与独立 Demo/probe 使用正常 folder resource。
新 unsigned probe App 三文件 SHA 原样匹配，生成器回归覆盖三配置和错误输入。
证据 `artifacts/verification/identity-app-notices-20261106/`；生产许可批准不因保留声明而通过。
普通手机 App/模型未更新。最新 readiness 指向
`artifacts/verification/p11-readiness-20261106-identity-app-notices/summary.json`。

下方 TTS“尚未实现”段落仅为历史，私有终态已在 b4db1fe 实现并取得真机最终 pass，
不要重新实现或运行。继续 solo；10/12、P4/P11/open/not-ready 与原后置边界保持。

## 2026-11-06 来源跟进：上游事实已定位，停止重复检索/构建

[官方历史及维护者回复](validation/f02-upstream-origin.md)：已确认 Arabic PUA 的原始加入
与后续纯改名/generator 提交；behdad 对 packTab 输出的条件性说明已记录，数据来源具体
问题仍交给原作者。Microsoft Typography 公开仓库只是 issue placeholder，其许可证不自动
覆盖私有文档中的固定输入；SIL primary403未绕过。讨论对应另一revision/IANA2025-01-21，
本候选继续绑定实际2025-03-10/header/hash。无新附件义务结论，不造.5、不重跑构建。
证据 artifacts/verification/f02-upstream-origin-20261106/；最新 readiness 位于
artifacts/verification/p11-readiness-20261106-f02-upstream-origin/summary.json。
下一步需要来源作者澄清或获授权的适用性审查；没有新输入时不重复相同页面检索。
.4 本地交付提交 fa0080c，生产/远端/独立批准和10/12、P4/P11/not-ready边界均保持。

## 2026-11-06 最新检查点：.4 ISC 补充与实际 SDK/App 交付通过

[详细证据/复核](validation/f02-harfbuzz-notices.md)。332 个实际 HarfBuzz 输入发现
hb-ucd.cc 的 ISC/Grigori Goronzy 声明在冻结 .3 中遗漏；115 个原样声明块、11 个生成表
和来源边界已保留。新 .4 正常离线发布 59 任务全部执行，三模块 115 个版本文件（另有
15 个 module metadata/checksum），六份 notices/source JAR 各含 72 份材料。
归档 108,785,484 B，SHA256 e59efab02e31d2f609e7328b5f8118c86e47e47029c74c4437a70c2b9dc68869。

SDK544cb15 的全新导出仅叠加隔离仓库/版本/collector 三处配置，实际双 iOS 链接
14 执行任务通过。SDK root 未应用 KMP 插件，原无 attributes 的 collector 解析失败；
显式 common/kotlin-metadata/library 后 root 与 KMP standalone 均成功保留 72 文件。
条件化 useVersion 的早先猜测未修复，已否定、恢复原版本规则；两份 RED 保留，勿重复。
未修改发布 metadata。dependencyInsight 选择 .4；原提交 generator 正常生成并构建
unsigned device App，全部 72 notice 和 77 App 文件（含 debug dylib）摘要已核对。

证据：artifacts/verification/f02-harfbuzz-license-review-20261106/（历史准备快照）及
artifacts/verification/f02-harfbuzz-notice-delivery-20261106/（实际本地交付，read-only verify.py）。
最新 readiness：artifacts/verification/p11-readiness-20261106-f02-harfbuzz/summary.json。
九保护路径/生产 lock 未改，旧 .1/.2/.3 及旧消费者/App 证据保留，不重复已过构建。
只本地提交；无上传/生产切换/装机/新源码模型下载。Nexus 匿名读取、账号发布权限已明确；
不再询问权限或查询管理API。继续 solo，不重试 reviewer/Apple 自动化/已完成手机流程。

下一步只处理未关闭门槛：生成输入的历史 Unicode 例外、Microsoft/SIL/Arabic PUA 来源适用
条款及独立许可批准；远端匿名消费与生产维护/资源验收。SIL 主站403未绕过，不能用第三方
转载当授权。F10 无获授权校准数据，保持 THRESHOLD_UNVERIFIED。
基础仍 10/12，P4/P11/release open/not-ready；本次修复实际遗漏和 collector，未新增 benchmark。
下方 .3 的“完整保留”仅指当时69材料逐字节一致，不构成许可完整性结论。

## 2026-11-06 最新检查点：F02 unsigned App 许可保留已通过

[实现/证据/复核入口](validation/f02-app-notices.md)。生成器增加可选
ODAI_THIRD_PARTY_NOTICES_DIR，创建输出前核验索引/精确文件集/摘要及安全路径、拒绝symlink，
快照已验证字节并使用正常folder resources阶段；默认行为不变。
新SDK export aa21182加隔离repo/version和generator三overlay，实际.3双iOS链接14任务通过，
unsigned basic Speech Demo正常构建；App中69文件逐字节与冻结.3消费者材料一致，
完整App（含debug dylib）与双framework摘要已保留。16生成器测试/312工具测试通过；
最初三个Swift缓存沙箱错误保留日志，获准缓存访问后全绿。九保护路径/生产lock不变。

证据：artifacts/verification/f02-app-notice-retention-20261106/，只读verify.py。
最新readiness：artifacts/verification/p11-readiness-20261106-f02-app-notices/summary.json。
本次仅本地提交；无上传/生产切换/装机/新模型下载，冻结.1/.2/.3不改。
继续solo，无独立审查批准。基础10/12、P4/P11与release仍open/not-ready。
下一步：有界核对已定位HarfBuzz生成表的Unicode16/Microsoft OpenType/IANA/SIL来源条款；
再处理独立远端消费/生产验收。F10无授权校准数据、保持THRESHOLD_UNVERIFIED。
不重复手机流程、失败Apple自动化、管理API查询或已通过的发布与链接构建。
下方“最终App保留未验”为历史结论，本次只关闭该本地技术子项。

## 2026-11-06 最新检查点：F02 许可随包交付与消费者保留已通过

新冻结候选 **`0.144.6-ondevice-f02.3`** 已完成正常Gradle发布及独立KGP2.4消费；
[许可交付复现与消费者任务](../probes/skia-atlas-text-cache/Skiko-ios-notices.md)。
三个模块/115文件，每个模块的notices JAR及sources JAR均含同一69文件材料；
6个源码文件在3份source JAR内共18处修改声明已核实，buildSrc修改声明另在真实输入核实。
消费者显式取root notices classifier，Sync输出69文件与索引逐字节一致；
commonMain/device/simulator framework链接通过，10任务成功。未将Maven解析当作最终App自动嵌入。

原生许可输入扩至3994个本地文件/2115头文件；每目标1902个对象均有VALID Ninja依赖，
无缺本地文件或新external组件。材料包括原23份上游文本、2份Unicode参考、14份libjpeg源码
内联声明，另补24份头部原文、DNG PATENTS及嵌套Expat COPYING；保留IJG/DNG归属文字。
全部40内嵌静态库继续匹配固定输入。**文件级/历史Unicode条款、独立许可批准、远端匿名消费、
最终App保留与生产资源验收仍未关闭**；不把材料打包成功计为许可裁定。

证据：`artifacts/verification/f02-notice-delivery-20261106/`，
`summary.json`、`publication-graph.json`、`patch-application.json`、`consumer-retention.json`、
`native-header-summary.json`及logs。只读 `rtk proxy python3
artifacts/verification/f02-notice-delivery-20261106/verify.py`。
归档108,522,716 B/115文件，SHA256
`3276597b2f422091ee13a5854c79625d5248ca49c54fa9d1c136eb427836ca93`。
.1/.2复核未变，九保护路径/生产lock不变。所有命令已结束，无待恢复session。

真实RED/修复记录保留：错误JAVA_HOME在Gradle前退出；根sourcesJar大小写漏附已修；
导出目录在父Git下时git apply会exit0但Skipped，已在独立tmp重验三份补丁并逐字节匹配7输入。
恢复时不要重跑这些已修问题或已通过构建，也不要修改冻结.3；后续制品变更使用新版本。
Nexus匿名拉取和发布权限不再询问；无凭据使用/上传/生产切换、无装机、新下载或benchmark。
继续solo，无独立审查批准；F10无授权校准数据，基础10/12、P4/P11/发布仍open/not-ready。
最新readiness：`artifacts/verification/p11-readiness-20261106-f02-notices/summary.json`。
下一步对已定位的文件级/生成Unicode条款做有界核对，再进入独立远端消费/生产验收门槛。
下方.2“声明补丁尚未应用/许可只暂存”等为历史：已在.3应用并验证材料随包交付。

## 2026-11-06 最新技术检查点：F02 双 iOS 正常发布与实际 SDK 消费已通过

F02 的 iOS-only metadata／AWT 闭包阻断已修复，详见
[分发审查](validation/f02-distribution-review.md)和[复现入口](../probes/skia-atlas-text-cache/Skiko-ios-only.md)。
固定源码的独立 iOS 补丁移除 JVM 可选注解并按 supportAwt 关闭独立 AWT publication；
没有改生成 metadata、抑制诊断或切换生产依赖。严格双 iOS RED 为1,022个注解错误，
修复后13任务通过；正常 release publication58任务通过，生成完整三模块100文件。
新候选 `0.144.6-ondevice-f02.2`，归档108,176,625 B，SHA256
`631806c3cf1bee881a41e2b99da4490e368dc37aa0f7d2057f376a4d683eb2de`。
KGP2.4.0独立consumer9任务、精确SDK71276364+隔离仓库/版本overlay的Compose consumer14任务
均完成device/simulator framework链接；dependencyInsight确认Compose选择修复版本。
这关闭本地publication与消费者链接技术子项，未新增benchmark／手机验收或关闭完整F02。

许可范围现覆盖全部40份内嵌静态库、1,860个编译源文件、0未映射对象，新增发现ICU；
23份上游文本、2份Unicode参考和14份libjpeg内联声明已暂存。两份ICU生成汇编均逐字节还原
到同一6,296,800 B的iOS数据输入。7个修改源文件的补充声明补丁已备好并通过apply检查，
**尚未应用到冻结.2或重新出包**。下一项实际工作：在新候选版本应用声明，完成文件／头文件／
Unicode条款核对，正常发布任务附带notices并验证consumer保留；不要覆盖.1/.2。

证据：`artifacts/verification/f02-ios-publication-20261106/`，`summary.json`为冻结构建记录，
`license-progress.json`为后续许可准备；只读复核 `rtk proxy python3
artifacts/verification/f02-ios-publication-20261106/verify.py`。
四framework、100文件及sidecars、6引用、40内嵌库、旧.1归档、9保护路径和生产lock已复核。
所有构建/归档命令均已结束，无待恢复session。不重跑已通过的原生构建来刷新记录。

Nexus匿名读取、发布账号上传/覆盖/不可删除权限已明确，不再查询管理API或重复问权限。
凭据未使用或落盘，无上传、远端消费、生产切换。solo，无独立审查批准；F10仍无获授权校准数据。
基础仍10/12、P4/P11与发布仍open/not-ready；本次进展是两个真实构建/消费子项通过。
最新readiness：`artifacts/verification/p11-readiness-20261106-f02-ios-publication/summary.json`。
下方“publication尚未解决”“本轮仅渠道澄清”等均为此前检查点的历史结论。


## 2026-11-06 最新决定：solo、Nexus 渠道与校准数据缺口

用户确认Nexus拉取无需鉴权；消费者使用匿名HTTPS，发布凭据仅用于上传。
不再将管理API403解释为制品读取权限缺口；制品就绪后验证实际匿名拉取与编译。
用户进一步明确账号可上传、可覆盖、不可删除；权限不再待确认，不再查询管理API。
release不覆盖由发布流程保证，无删除权限不阻塞新增版本；消费组不明时可用hosted直连。
下一步聚焦F02包的依赖闭包／许可附件，远端连接和独立消费留到制品准备好后验证。
本节下方仓库权限／不可覆盖策略未核实的描述按此更新理解。
最新readiness：`artifacts/verification/p11-readiness-20261106-nexus-anonymous-read/summary.json`。

后续用户明确允许重试一次 hosted 策略 GET。此次审批通过，但 curl35
`Recv failure: Connection reset by peer`、HTTP000，未取得响应或策略；一次额度已用完，
不再自动重试。回执：`artifacts/verification/nexus-skiko-readonly-20261106/authorized-retry-outcome.json`。
最新 readiness：`artifacts/verification/p11-readiness-20261106-nexus-retry/summary.json`；
下方审批429为此前尝试的历史结果，仓库策略仍未核实。

用户最新要求保持 **solo**；不再启动或重试独立审查代理。1be81e0 的 architect 审查
因 429 失败，code-reviewer 按用户要求中断，**没有独立批准**。此前的一次审查授权已被此决定覆盖。

用户提供内部 Nexus `https://nexus.ugaiot.com`，匿名清单与候选版本检索已成功；
release hosted / consumer group 的候选入口及证据见 [F02 Nexus 交付准备](validation/f02-nexus-delivery.md)。
组配置查询 403，hosted 策略查询因自动审批服务 429 未执行；不推定组成员、不可覆盖策略或发布权限。
凭据未使用、未写入文件；没有上传或切换生产依赖。现有四模块包仍缺 AWT 闭包及许可定稿，不能直接发布。

用户确认 **没有获授权的 F10 身份／PAD 校准数据集**。生产校准、阈值、识别准确率和
LIVE/SPOOF 判定仍未验；保留 `THRESHOLD_UNVERIFIED`，不以 NASA 图片或合成数据代替，
不新增拍摄、不重复要求用户操作手机。已有注册／删除／停用和图像生成验收保持。

本轮仅完成渠道发现与外部输入澄清，0 新功能／benchmark／验收项通过；基础10/12、
P4/P11 与发布仍 `open/not-ready`。最新 readiness：
`artifacts/verification/p11-readiness-20261106-delivery-inputs/summary.json`。
恢复时先看本节，不重复问许可、数据集是否存在或审查代理授权。下一项技术工作仍是
F02 受支持的完整 publication 与许可附件；仓库策略和维护团队未核实前保留分发门槛。

## 2026-11-06 最新检查点：F09 内部闭源许可已选定并接线

用户已选择“仅内部闭源使用和分发”；[内部分发约定](validation/internal-distribution.md)
与根LICENSE已落地，`3b5150d` 把提交时LICENSE纳入snapshot和notices，旧快照不借用新条款。
真实notice-only验证150文件/20归属/23notice，未重建原生框架、未发布。
另修复mlx/mlx-c新增.cpp漏检，真实缓存/149源码/12依赖校验通过；定向13/13、全tools309/309。
首轮Ruby2.6/3.4混用失败已保留；后续工具命令固定
`RUBY=/Users/daubert/.rvm/rubies/ruby-3.4.11/bin/ruby`，不需安装gem。
详情[F09验证](validation/apple-mlxvlm-distribution.md)；证据分别在
`artifacts/verification/f09-internal-license-20261106/` 和 `f09-native-cache-inputs-20261106/`。
最新readiness：`artifacts/verification/p11-readiness-20261106-internal-distribution/summary.json`。
项目许可选择不再阻塞；独立审查、实际生产渠道/接收组织及其余校准/质量门槛保持。
不重复询问许可、不重做已验手机流程；9保护路径/生产lock保留，10/12及P4/P11仍open/not-ready。

## 2026-11-06 最新检查点：Apple 文件夹导入完整普通流程已验收

先读 [Apple 导入验收收尾](HANDOFF-2026-11-06-apple-import-accepted.md)。用户已完成取消、
错误 ONNX 目录、正确目录重试导入、一次普通生成及清除输入；无需再操作或杀 App。
安装14文件SHA匹配，无私有回退源；隔离App冷启动复用就绪后已停止，原Combined PID2911
已返回前台，43个原App安装URL保持。本次关闭1个实际验收子项，0新benchmark，
基础10/12、P4/P11及发布仍open/not-ready。下方准备记录和等待问题均为历史，不重复。
最新readiness：`artifacts/verification/p11-readiness-20261106-apple-import-accepted/summary.json`。
本轮命令已结束；下一步聚焦独立审查、生产交付/许可和获批校准数据等剩余门槛，勿循环已验流程。

## 2026-11-06 当前交互起点：MLX文件夹导入隔离验收已准备

先读 [隔离导入验收起点](HANDOFF-2026-11-06-apple-import-check.md)。原App两条人工流程
不重复；仅为尚未验证的新MLX文件夹入口新增ODAI Import Check隔离副本，原43个App及
主App模型保持。未重建源码，既有签名资产未变，三个Mach-O代码section相同。
固定14文件已放入IdentityInput/ODAI-MLX-14-20261106，隔离App普通启动显示模型未就绪、
导入按钮可用。已请用户打开导入选择器后取消并反馈，尚待答复；0新子项通过，不计准备为验收。
直接从该人工步骤继续，不重装/重签/重复制模型。最新readiness：
`artifacts/verification/p11-readiness-20261106-apple-import-prepared/summary.json`。

## 2026-11-06 最新检查点：Apple 新版更新与两条人工普通流程已通过

先读 [Apple 更新与手动验收交接](HANDOFF-2026-11-06-apple-updated.md)。用户已明确允许
结束当前会话并更新；既有df46772签名产物安装成功，无参数PID2891启动，页面显示本地
图像理解模型已就绪。完整回读14份安装模型SHA匹配、其余42个App安装地址保持；模型与
数据容器未清除。旧进程更新前已自然结束。新版普通人工Files选取公开NASA图片并生成SUCCEEDED，后端/模型revision显示正确；
用户已完成IdentityInput/models整文件夹导入、显式启用、NASA注册ENROLLED（1用户/1模板）、
未校准验证拒绝、删除0/0与整会话停用；拒绝/中间0计数为用户目击，注册/停用另有实际截图。
用户已清除MLX输入；只结束本轮PID2891并冷启动PID2911，稳定首屏确认两模型就绪、授权关闭、
图片预览为空。两条普通人工流程无需再做；新MLXVLM文件夹导入/取消重试和完整交互矩阵仍未验。
不再询问更新授权、不重建或重签名；下方“更新待选择”和普通生成未验为历史。
最新readiness：`artifacts/verification/p11-readiness-20261106-apple-updated/summary.json`。

## 2026-11-06 最新检查点：Android 普通 Identity 实际子项已通过

先读 [Android 普通 App Identity 验收](HANDOFF-2026-11-06-android-identity-accepted.md)。
设备重连后在保留的原 `afed156b…` App 完成模型导入、显式同意、公开授权图片注册、
未校准拒绝、删除与停用。冷启动确认模型仍可用、授权仍关闭、图片预览已清除；原 APK、
12 份模型及权限不变。没有换包、重建、采集或下载。关闭这一普通 UI 子项，基础10/12、
P4/P11仍 open/not-ready；不推广为精确b90、Apple UI、校准或发布通过。
最新 readiness：`artifacts/verification/p11-readiness-20261106-android-identity-accepted/summary.json`。
不重复本次流程；Apple 更新选择仍待答复。下方 USB 中止为已保留的历史记录。

## 2026-11-06 最新设备检查点：Android UI 未验，原 App 已恢复

先读 [Android 交互中止与恢复](HANDOFF-2026-11-06-android-ui-transport.md)。已尝试普通
Identity 导入，但 USB 反复断连/unauthorized，未完成模型选择、同意或注册；没有新验收项通过。
原 `afed156b…` App/test/runner、12 份模型摘要与权限已恢复核验，原 MainActivity 冷启动成功。
不重做不稳定 USB 下的换包循环；稳定连接后再恢复该流程。Apple 更新选择仍待答复。

## 2026-11-06 最新恢复入口：固定模型导入功能与签名包

先读 [模型文件夹导入交接](HANDOFF-2026-11-06-model-folder-import.md)。`df46772` 已补齐
Apple 增强 Demo 的 Files 固定模型文件夹入口和准备失败后的显式重试；Swift82/tools304、
组合整包构建与既有资产签名通过，IPA 已备好。设备检查发现旧组合 App 有 PID2562，
因此未覆盖当前会话；已询问是否允许更新，尚待答复。不重建已完成产物、不重试失败 UI 工具。
最新 readiness：`artifacts/verification/p11-readiness-20261106-model-folder-import/summary.json`。
新导入入口尚未装机验收；P4/P11 和发布仍 open/not-ready。下方为此前交付的历史记录。

## 最新恢复入口：组合 App 已完成装机与普通启动

续接请先读 [本地模型启动修复与组合装机交接](HANDOFF-2026-11-05-local-model-startup.md)。
`bad6362` 已移除 MLXVLM 隐藏启动参数要求，并修复已安装模型仍依赖原导入目录的问题；
79 项 Swift / 304 项 tools 回归及新隔离整包构建通过。**ODAI Combined** 已用既有签名资产装到
授权 iPhone，无参数启动后生成固定模型安装 manifest，首屏同时显示 Identity 设置与共享功能入口。
新 App/模型保留，42 个旧 App 安装 URL 不变，普通 Demo 已切回。不是点击/picker/生成验收。
证据 `artifacts/verification/apple-combined-startup-20261105/`；不要重做已完成的装机/固定样本验证。
最新 readiness：`artifacts/verification/p11-readiness-20261105-combined-startup/summary.json`。
P4/P11、质量/触摸/校准与发布仍 open/not-ready；下方“尚未装机/下一项 TTS”等是历史。

## 最新功能交付：Apple组合Demo

用户反馈推进长期停在补证后，停止了新一轮精确Android LM重复验证；该轮仅只读预检，
未换APK/未新增推理，既有缓存保留。
新增[Apple组合Demo](validation/apple-combined-demo.md)：现有Speech/Video/MLXVLM生成入口
支持`ODAI_OPENCV_IDENTITY_DEMO=1`，同一App接入真实Identity host/bridge/runtime，
不再仅交付两个分别开启的变体。新的unsigned iOS18/arm64组合产物已编译链接成功，
实际Mach-O中Identity、MLXVLM、KMP符号与MLX资源存在；304项tools回归通过。
该产物为`e0438c0`源基点加已登记generator overlay，不能冒充原冻结候选；未签名安装/UI验收。
未重试失败Apple UI工具，未操作手机、下载、修改生产lock或九个保护路径。
本地证据`artifacts/verification/apple-combined-demo-20261105/`，复核`verify.py`。
最新readiness：`artifacts/verification/p11-readiness-20261105-combined-demo/summary.json`。
后续优先未实现功能/实际使用障碍，缺授权数据/生产决策的门槛单列；不再循环固定样本补证。


## 恢复后新增检查点：TTS私有报告已实现并验证

原“下一项”已于本次续接实施，以下原文为历史。最新结果见
[Android私有终态验证](validation/android-committed-candidate.md#当前候选真机-tts私有报告终态验证)。
相同b90e普通APK、独立test-only overlay在TECNO完成**1次最终TTS pass**：PID26894，
正常播放/活动取消join/重入/close/关闭拒绝齐全；host仍255，私有JSON完整回读SHA验证。
报告自检成功/预设失败均真实落盘，二者不计TTS功能pass。
原普通afed App/test/Identity runner、12模型/附属摘要、权限和TTS设置已恢复；
本轮3报告/4APK临时副本精确删除，WAV缺席，普通MainActivity启动。
原9保护路径与生产lock不变。没有新采集、下载请求、benchmark或远端操作。

最新原始证据：`artifacts/verification/android-persistent-tts-20261105/`；
只读复核`rtk proxy python3 artifacts/verification/android-persistent-tts-20261105/verify.py`。
新readiness：`artifacts/verification/p11-readiness-20261105-persistent-tts/summary.json`；
移除“精确候选有引擎设备TTS未通过”这一已补证gap，其余质量/最低系统/完整离线与UI缺口保留。
基础10/12、P4/P11与发布仍open/not-ready。

下一步从F12剩余普通UI/provider或F10获批校准数据/独立审查门槛继续；
不要重跑已完成TTS、旧三轮结果传输、F09构建或无变化重试Apple失败UI工具。
本轮solo本地实现与回归不构成独立审查批准。


本文件是新的恢复入口；优先于旧 [2026-10-23 长交接](HANDOFF-2026-10-23-next-session.md)中的历史下一步。
用户最新要求：**先写交接文档，在新会话继续**。本次收尾不再构建、测试或操作设备。

## 目标、当前结论与会话状态

- 完整目标：按照原 roadmap 推进 Demo 原型；用户要求功能优先，部分 corner case、最低系统及长期质量/资源专项可后置，后置不计通过。
- 基础里程碑 **10/12**；**P4/P11 与发布仍 `open/not-ready`**。不要因本机测试、固定样本或截图通过宣称整个目标完成。
- 工作目录：`/Users/daubert/Github/ondevice-ai-kit/.worktrees/foundation`；分支 `feat/sdk-foundation`。
- 写本交接前 HEAD：`ac07d36354ee3572f8e156024ca73e9d5a73e172`。本交接若单独提交，恢复时以 `git rev-parse HEAD` 为准。
- 最近提交：`ac07d36` Android 精确候选真机 TTS 观察；`03be979` Apple 两变体稳定首屏；`c5b258a` Apple 精确编译变体真机 smoke。
- 当前没有本代理仍在执行或待恢复的命令、推理、instrumentation、工具 session handle。之前所有设备测试均已确认结束。
- 目标曾因外部门槛标记 `blocked`，用户随后恢复为 `active`。恢复后的本轮只读检查了交接、Gradle配置和技能；**没有实施下面的 TTS 持久化方案，没有生成新 APK，没有新增设备操作**。
- 旧 blocked 审计计数不可沿用；用户恢复后的审计重新计数。

## 开始前保护与授权边界

所有 shell 命令以 `rtk` 开头，通常用 `rtk proxy`。读取 `/Users/daubert/.codex/RTK.md` 和适用 AGENTS.md。
使用普通 understand → execute → verify → report；本交接下一项先 solo，不重试已失败的 native reviewers。

当前 index 空。以下 **9个原有 dirty/untracked 路径不得覆盖、暂存、回退或夹带提交**：

```text
demo/iosApp/OnDeviceAIDemo.xcodeproj/project.pbxproj
docs/validation/face-identity.md
probes/ios-camera-lifecycle/CameraPageResourceValidation.swift
probes/ios-camera-lifecycle/CameraResourceFailureReportChecks.swift
probes/ios-camera-lifecycle/CameraResourceValidation.swift
probes/ios-camera-lifecycle/README.md
probes/ios-camera-lifecycle/ThreadInventory.swift
probes/ios-camera-lifecycle/ThreadInventoryChecks.swift
scripts/generate-camera-resource-app.rb
```

保护摘要清单：`artifacts/verification/identity-gallery-demo-20261103/protected-guard-final.json`。
写本交接前逐一复核相同。生产 `apple-mlx/Package.resolved` SHA-256：
`e67e985970770991f1eb2384ca9e9b6267fb8a8f1569051be5cfb2175f0ef858`。

- 不新增下载、依赖、模型、私人媒体或签名资产变更；不 push/merge/tag/发布。
- 既有设备、现有签名身份和已经授权的固定模型/公开合成素材可沿用。Gemma 用户许可已经确认，不重复要求接受。
- 现有普通 App 和模型存储必须保留可用；Android 临时替换测试后恢复原 main/test APK、runner、权限与模型摘要，不清 App 数据。
- 麦克风单次授权已经消耗；本轮和下一项 TTS 不采音、不拍照。不得自动触发新的录音。
- 真实 Photos provider 写入、最低系统设备、长期资源和校准专项保持原后置边界。
- `artifacts/verification/` 为本地 ignored 证据，**不在普通 Git clone 中**。不要覆盖或“重新生成”冻结原件。

## 已有完整候选与验证，避免重做

### Android 精确候选

- 源提交 `e0438c030a987ceeff9f9207c2475684749e4d8b`，tree `1a79aabdc997ca99fe441a02b9cb54982ce2c34c`，953份 committed 文件。
- `artifacts/verification/android-committed-candidate-20261105/source.tar` SHA：
  `c3b5966e09fdd44a957e3da1f3a2674a289857f99e02211a18f1f772155373b4`。
- `android-committed-candidate-20261105/candidate.apk` SHA：
  `b90e783062548368f12e5262a1dc8437d511119c6dddf1c1ffb66aabafa64d8d`；包名 `io.ondeviceai.demo`。
- 四 opt-in 后端同时启用；offline fresh build 997 tasks，586 JVM tests，20模块 lint 无 error、52既有 warnings。
- AVD5582 上同一 APK：普通 LM 初始化后取消/drain/同PID重入/成功/清理44项；补充固定ASR及原生生命周期、SR双帧AAC/lease、Identity/purge/未校准拒绝、普通人脸picker/布局均有证据。
- 两个 AVD 均无 TTS 引擎；这是真失败，不重复查询/下载。手机已有 Google TTS，见下一节。
- 构建及补充运行详情：[Android 候选](validation/android-committed-candidate.md)。不要重跑已通过的997任务来解决报告传输问题。

### Apple 与 F09

- 同一 e0438c0：Apple481 + KMP24 + shared80 + 注入桥接133 = 718次执行、0失败/跳过；基础 unsigned App/consumer 构建已通过。
- MLX unsigned device/arm64 simulator、独立 OpenCV Identity device 整 App 已构建；12固定包/2 C子模块/6878原始依赖文件，无F09 overlay。3产品树/664 Swift输入已冻结，**不要重复准备和构建**。
- 生产 Skiko 仍 `0.144.6`；F02补丁没有切入生产。OpenCV 制品只有arm64 device/x86_64 simulator，不能声称arm64 simulator通过。
- iPhone `00008140-0008703436D3001C`，最近验证为 iPhone17,2 / iOS27.2 /24B119；沿用既有 team `4X996UV8LC`，未创建签名资产。
- 隔离 MLX bundle `io.ondeviceai.candidate.e0438c0.multimodal`：编译 Mach-O 不变；64×64白图真实Swift/KMP→Vision回调、0脸；14安装模型逐文件回读SHA通过。没有当前候选普通UI多模态生成结论。
- 隔离 Identity bundle `io.ondeviceai.candidate.e0438c0.identity`：仅ID/显示名和签名不同，3 Mach-O UUID/section一致。Identity与MLX稳定首屏截图已获得，**只证明显示**，不证明点击、picker、导入、同意、注册或生成。
- XCTest `enabling automation mode` 超时、WDA等待超时；CUA `Sky Computer Use native pipe startup failed`，一次受支持reset后仍失败。不要无变化重试或绕过工具使用其他UI输入通道。
- 截图可用官方 `xcrun devicectl device capture screenshot`；旧首次切换动画截图不算稳定渲染。不要再次截图当作新的功能验收。
- 两 Apple alias已停，普通 `io.ondeviceai.opencv.demo` 恢复；alias和MLX模型保留供复用。
- F09：20静态双slice XCFramework、可复用正式packager、direct/ZIP消费者、真机固定图片/视频真实推理均过；OrderedCollections262、MLX230逻辑测试/295case、tools297已过。用户批准的固定兼容补丁仅在隔离binary lane，生产lock不改。**不重建F09**。
- F09独立packager审查、SDK源码再分发许可与生产交付策略仍缺。旧reviewer429不算批准，不重试失败代理。
- 详情：[Apple 候选](validation/apple-committed-candidate.md)、[F09 分发](validation/apple-mlxvlm-distribution.md)。

## 最近实际进展：TECNO 精确候选 TTS

最近验证手机 serial `15365375C9007986` / TECNO KM9 /API35，ADB已恢复授权，但USB会短断。
这不是新会话已验证的在线状态，开始设备操作前重查。

证据目录：`artifacts/verification/android-exact-physical-tts-20261105/`。
summary SHA：`0ff5a3dacc89409ccd2257875368eb9b027c7ed03107ada1ebae975ae5d7ac58`。

1. 手机已有 `com.google.android.tts` 服务，`tts_default_synth` 仍为 `null`；实际可以绑定，不再描述为手机无引擎。
2. 临时安装精确 b90e 普通 APK 与现有 `TtsProbeInstrumentation` 测试 APK，test SHA
   `b6d7dc915254f8ad3e3fd908bdde0ed63aa0dd72b3c4ad959830b6fe4fbacb1a`。
3. PID23421、23591、23879均有同序 `normal-start → normal-done → cancel-request completed=false → cancel-joined → followup-done`。
   证明当前候选实际执行了正常播放、活动取消等待和后续重入。
4. 三轮host均exit255；每轮确认目标PID/活动instrumentation缺席，且系统exit-info为 `due to finished inst` 后才进入下一轮。
   没有因观察中断并发重启；这些退出记录不等于pass。
5. 第一轮stdout、第二轮设备端文本、第三轮官方 `am instrument -f` protobuf都只留下开始状态。
   `-f /data/local/tmp/…` 实际文件在 `/sdcard/data/local/tmp/…`，已回读SHA，153字节只有start-status，无session-result。
6. **最终pass/result bundle、close断言、voice/outputBytes未证**；三个已观察生命周期不得写成3项最终测试通过。
   不重复这三种旧观察方案。新会话若继续，应改变结果持久化机制，而不是重新跑相同命令。
7. 原包首次流式恢复因USB失败；确认仍为候选后，完整USB push原APK、设备SHA校验、本地 `pm install -r` 恢复成功。
   临时APK、文本、protobuf均按精确路径/摘要清理。

最后确认恢复的普通手机状态：

- main：`artifacts/verification/sherpa-asr-20261105/final-demo.apk`，SHA
  `afed156b40bcd8be0bcf0c5b373a0b30dd5a3daa4c7bd392b3655c7b6926c461`。
- test：`artifacts/verification/sherpa-lifecycle-20261105/original-test.apk`，SHA
  `7b9e47ce9a9b9fe51c673c002697a16c3fe4dd6ee382f70c3a32c3c9493a621d`。
- runner：`io.ondeviceai.demo.test/io.ondeviceai.demo.IdentityModelInstrumentation`。
- 12份模型/附属摘要、相机麦克风权限、TTS服务/设置与前置快照相同，普通MainActivity已启动。
- 授权LM、ASR、VAD和SR模型保留。旧手机afed上的ASR“基本正确、少量漏错词”是历史用户验收，不是b90e新采音证明。

## 下一项：刚提出、尚未实现的 TTS 私有报告方案

用户恢复目标后，代理提出：**测试结束前在target App私有临时目录持久写入最终状态，回读报告不依赖ADB全程在线**。
仅提出后读了既有文件，用户随即要求交接；**没有源文件、构建配置或设备状态被此方案改变**。

建议新会话按以下顺序执行，不预先假定它已经可用：

1. 新鲜检查worktree、9保护摘要、index、生产lock、手机授权/包/runner/模型。不要再次执行旧三轮TTS命令作预检。
2. 阅读原测试：`demo/androidApp/src/androidTest/kotlin/io/ondeviceai/demo/TtsProbeInstrumentation.kt`；
   原构建方法：`artifacts/verification/android-candidate-runtime-20261105/build-test.py`。
   Gradle已支持 `-PodaiTestRunner=<class>`，原测试build收据/命令在同目录；依赖与工具链都使用既有缓存。
3. 在**新的隔离测试构建区**实现test-only recorder/runner：固定唯一私有JSON路径，成功与失败都保存，含runId、PID、精确sdkCommit、阶段及原有断言结果；
   在 `Instrumentation.finish` 前完成写入/关闭。保留原SDK执行/取消/close断言，不能把“写报告成功”替代真实结果。
   不记录音频、用户转写或私人数据。不能修改普通 b90e APK或冻结源码/旧证据目录来伪造同候选来源。
4. 优先使用不同runner类及测试源码overlay/独立build目录，避免覆盖原冻结测试产物；具体 Gradle 注入方式尚未验证。
   只构建必要test APK，不引入新依赖，不修改生产lock，不把新test-only文件说成原953 committed源码的一部分。
5. 用实际报告行为验证终态可回读、失败不误报pass；之后最多执行必要的一次固定文本真机验证。
   ADB中断时先观察同一请求/报告/进程，不立即重启测试。报告未完成不算通过。
6. 先收集并核对报告，再精确清理本次临时文件；原main/test/runner/模型/权限必须恢复并核验。
   连接不可靠时按上一轮“完整USB复制→设备SHA→本地安装”的已验证恢复方式，避免盲目重复流式安装。
7. 如有新的可靠结果，更新Android验证文档和新readiness快照；旧原始证据不覆盖。
   如仍无终态或出现产品问题，保留失败、定位实际层，不再制造更多相同运行记录。

这只是下一条可尝试的技术路径，**不是绕过PAD校准、独立审查或发布门禁的方案**。

## 最新验收索引与只读复核

最新指针：`benchmark/p11/grouped-report-manifest.json`。
readiness：`artifacts/verification/p11-readiness-20261105-exact-android-tts/summary.json`，SHA
`7ce409aa54984b9962a3331c5f5768e6e0f77ef605d4676c0671a37bad699565`。
该轮已校验797证据引用/547来源hash；9行均有gap，8组benchmark完全不变，0新样本。

```sh
rtk proxy git status --short
rtk proxy git rev-parse HEAD
rtk proxy python3 artifacts/verification/android-exact-physical-tts-20261105/verify.py
```

TTS verifier验证日志/恢复/缺失结果记录的一致性，**不代表最终TTS test pass**。
当前新交接是独立新增文件，不修改readiness已哈希的历史文档；无须为交接本身刷新旧快照。
以后修改已索引文档时，需新快照，保留来源和gap边界，不覆盖旧summary。

## 仍未完成的真实门槛

完整清单及退出标准见 [F01–F12 后续专项](validation/foundation-followups.md) 和 [P11矩阵](validation/p11-status-matrix-20261027.md)。

- **F10 PAD/识别**：双端检测/对齐/embedding/注册删除和精确1:N已接；生产PAD/1:1/1:N决策仍缺获批协议、授权genuine/attack数据、校准阈值。
  不将原始分数实验接成已校准生产backend。原计划Task10明确缺授权数据/选择时保留typed unavailable。
- **F02/P4**：Skiko补丁本地/有界真机证据已取得，但生产分发所有权、原TLS/析构归属与长期资源门槛仍未关闭。
- **F09/F12**：独立packager/完整分支review、源码再分发许可和生产交付策略未完成；429和作者自审不算独立批准。
- **设备/UI验收**：Apple触摸/picker/当前候选生成、Android当前候选更广真机/provider和最终TTS报告仍有缺口。
- **用户已后置的专项**：最低系统iOS15/API26真机、自然质量/CER/WER/强噪声/中文、GPU/能耗与持续资源、Android异步LM、部分process-death corner。
  不为追求全绿擅自扩大采集/下载授权，不把后置项删除或改成通过。

新会话启动可直接说：

> 阅读 `.worktrees/foundation/docs/HANDOFF-2026-11-05-next-session.md`，从顶部当前交付状态续接。
> TTS/ASR/SR终态已验、F02已授权审查完成；保留原9个保护路径、普通手机App和模型，不重复已过设备/构建流程，推进尚缺的接收维护/远端交付和来源适用性门槛。
