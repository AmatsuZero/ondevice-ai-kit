# Foundation 后续专项

2026-11-07 状态复核：[逐项审计](roadmap-status-audit-20261107.md)已核对P0–P11停止点及F01–F12，
修正维护归属、当前候选真机、PAD smoke与8能力/11组等过期待办；下方日期追加项保留历史，
当前队列表已按实际完成子项更新。基础10/12、P4/P11 open、最低系统deferred和release not-ready保持。

2026-11-07：[Identity资源子项](android-identity-resources-20261107.md)已完成当前主包的受控35行：
PID4454、72生命周期检查、30测量/133.174ms p50，原状态恢复与临时路径清理已核验。
旧冻结中断PID31731只恢复原请求并排除干净基线。test-only397任务/42秒，51聚焦/34协议/531tools过，
Copilot42.21秒限定方案审查已处置。P11新增30样本至8能力/11组、旧10组不变，校准/PAD/质量/长期预算
与F02实际远端交付仍未完成，10/12保持。下方“Kotlin未实现/未操作设备”均为此前轮次历史。

2026-11-07：[F10分数工具](f10-calibration-scores.md)完成绑定／verification／1:N／PAD离线
聚合，21新+23既有聚焦44过，完整tools510/510、48.281秒；Copilot限定审查38.11秒无finding。
cbc hy3独立CLI候选119.325秒返回，漏test split的实际失败保留，父修正后三成功+一错绑拒绝过。
[Nexus消费者准备](f02-nexus-consumer-checklist.md)另由Copilot47.217秒完成审查；父已逐项
处置匿名／缓存、variant、许可摘要及wrapper意见，未取件或上传，精确出口／本次范围仍待答复。
Identity97856自然终态1715.484秒／exit0但262144B截断JSON，无完整源码；不再重发，主代理接手。
Kotlin／test-only构建／资源测量未开始，P11仍10组；九保护／lock保持、无设备操作。
后续授权数据、ROC／DET／置信推断、生产阈值／策略、远端消费和原P4／P11门槛保持，10/12不变。

2026-11-07 新工具增量：[F10 manifest预检](f10-calibration-manifest.md)已实现，
23合成测试/独立CLI smoke过，声明用途与证据/媒体摘要、主体/会话/源媒体组隔离受校验，
不取得数据或校准阈值。[Nexus归档核对](f02-nexus-delivery.md)经Copilot候选/父RED→GREEN修正，
9测试过，实际`.6`115成员/108791304B与原计划一致；旧归档/计划保持，无取件或上传。
Copilot限定终审的Cc URL漏检已由两工具5真实失败复现并修正，最终聚焦32过，最终完整tools489项/47.714秒通过；收据见
`artifacts/verification/f10-calibration-manifest-20261107/tools-suite-after-review.json`。
Identity hy4free原8608因`Max turns (1) exceeded`自然终态/无候选；仅改调用轮数配置，独立97856仍运行，
不超时/重发。未构建测试包或操作设备，P11仍10组；授权数据/score指标/生产校准、Nexus具体上传范围/
远端消费及原资源门槛继续开放，基础10/12、P4/P11/release不晋级。

2026-11-07：[F10校准探索](f10-calibration-exploration-20261107.md)由cbc hy3完成候选研究，
主代理核验官方来源并更正IJB停止分发、Replay许可和SiW归属等错误。London Set有明确CC BY版权
许可但本项目具体用途／同意范围未确认；常见PAD数据不能自动用于企业生产校准。
该探索轮未实现工程；后续manifest已实现见上，score协议与离线指标仍待推进；未下载／采集／联系第三方／改阈值。
F10不再作为“完全无事可做”的等待项，生产数据和验收门槛仍保留。


2026-11-07：[当前Android TTS](android-current-tts-20261107.md)沿用636a6eb/dfccaa95原主APK，
仅构建203KB test-only包，1046原源码保持；一次真机normal/活动取消join/followup/close/关闭拒绝终态通过。
ADB255和第一份非终态保留后只回读同run；两记录器自检分开校验，34离线协议检查通过。
新传输协议提前改为noclobber复制，设备重建exit0；四份APK全量SHA在安装前核对。
原App/test/Identity runner、25私有摘要/4SR文件/权限/TTS恢复，临时文件精确清理并返回普通App。
cbc hy3限定审查295.361秒自然完成，父核对两意见未改源码；Qoder403/原生429线路不重启。
关闭当前修复TTS真机子项；未新增P11样本/采集/下载/上传，10/12、P4/P11及release not-ready保持。

2026-11-07：[当前Apple真机链路](apple-current-runtime-20261107.md)复用636a6eb组合App，
不重建宿主，3原Mach-O section/UUID保持。hosted非UI XCTest身份/原生两方法均通过，
86内部检查覆盖18输入SHA、真实factory→KMP→MLX图片/视频生成、OpenCV1脸/128特征及lease/输入释放。
私有终态两次回读与xcresult一致，各推理一次；临时候选/私有模型已移除、原44安装URL保持并返回普通App。
cbc hy4free候选678.693秒自然完成，父修正Xcode接线，不计审查批准；旧403/429线路保持停止。
关闭当前候选原生链路子项；普通Files交互/其他能力、质量资源/校准/交付继续，P11无新样本、10/12不晋级。

2026-11-07：[当前Apple组合候选](apple-current-candidate-20261107.md)与Android同一636a6eb归档，
1046源码/6878原始上游输入核验；组合MLX＋OpenCV device和MLX-only arm64 simulator构建通过，
82项当前bridge实际通过、645Swift编译输入及实际App符号/资源/完整commit固定。
两shared framework按源码等价复用，仍属e0438c0/Skiko0.144.6，未重建；OpenCV无arm64 simulator保持。
CODE_SIGNING_ALLOWED=NO；模拟器linker-only adhoc的首次父检查误拒已记录更正，无App重建。
九保护/lock保持，无手机/采集/推理/下载/上传；关闭当前Apple构建和变更bridge子项，真机及完整F12继续。
cbc限定检查清单已处置，不计完整审查；P11无新增样本，基础10/12及release not-ready保持。

2026-11-07：[同一当前Android候选真机](android-current-device-20261107.md)ASR13/SR6 native检查通过，
各一次持久终态；两帧SR完整解码/尺寸/PTS/AAC保持或丢弃通过。手机原包+9.85MB增量真实重建新签名APK，
五份全量SHA在安装前核验；原App/test/Identity runner、25私有摘要/4SR文件/权限/TTS恢复并精确清理。
27离线协议检查通过；599原构建测试不重跑。当前修复的这两项真机缺口关闭，其他F12门槛保持。
无新benchmark/采集/下载/上传，10/12、P4/P11 open、release not-ready保持。

2026-11-07：[当前Android候选](android-current-candidate-20261107.md)冻结636a6eb/1046源码/14输入，
四后端离线997任务全执行，599JVM通过、20模块lint0error；包/签名/46native/7ASR资源已核验。
cbc候选经父四项负向RED修正后10检查通过，本地以原包+9.85MB新数据精确还原456.35MB签名APK。
手机原App/25私有摘要/4SR文件等仅只读核对保持；新包未装机、设备端重建未验，不用旧运行证据晋级。
10/12、P4/P11 open、release not-ready保持。

2026-11-07：[Android LiteRT-LM资源](android-litertlm-resources-20261107.md)新增普通afed APK
在TECNO/API35的1预热+5测量，6次固定红图/模型身份/输入保持/缓存清理均通过；整SDK调用
p50/p95=56477.465/57068.401ms，采样进程RSS峰值3,905,445,888–3,978,801,152B。
P11现7项能力/10组，旧9组不变；原App/test/Identity runner和私有文件已恢复/保持，普通App已返回。
完整tools440通过；cbc候选经父固定prompt身份、Ollama限定意见无finding。ADB255由同run持久终态
核实，不重发推理；临时掉线只恢复原test。基础10/12、P4/P11 open及release not-ready保持。

2026-11-07：[Android ASR资源补测](android-asr-resources-20261107.md)新增普通afed APK在TECNO/API35
5warmup+30measure，固定英文35/35，整调用p50/p95=1807.033/1827.525ms，进程采样RSS峰值
471,339,008–481,583,104B。原App/test、25私有摘要、4SR文件与权限设置保持或恢复。
cbc汇总候选修正严格类型后通过10项检查，P11聚焦40/完整tools426通过；当时6项能力/9组。
无新采音/模型/下载/上传，不证明当前SDK新清理代码或自然质量/长期资源预算，10/12及发布门禁不变。

2026-11-07：[真实SR输出释放错误](android-sr-output-cleanup-20261107.md)经旧租约2项RED后修复。
文件/目录删除失败现在上抛，保留bridge重试句柄和原处理错误；不递归删除其他条目。
SR10+启用SR的Demo56实际执行通过、video18缓存通过，lint0error/2既有warning。
cbc候选经父代理修正，Ollama最终三文件限定意见无finding；无设备/下载/采集/上传，原发布门槛不变。

2026-11-07：[Android视频限定审查](android-video-client-review-20261107.md)由cbc hy3完成。
唯一同帧数去重建议经实测会丢totalFrames补充，已否决并逐字恢复原生产源码；新增一项回归。
完整video18+Demo56实际执行通过，SR4缓存通过，video lint0error/1NewerVersionAvailable warning；不计产品修复或设备/发布验收。
原429/403助手未重试，9保护/lock保持；10/12、P4/P11/release not-ready及既有待答复不变。

2026-11-06 F02/F12增量：`.6`自身SDK双iOS链接15任务及基于新框架的unsigned App74材料
保留通过；115个新Nexus精确路径匿名HEAD均404，上传范围待答复，远端消费未执行。
2026-11-07：[Sherpa原始错误/取消保留](android-sherpa-cleanup-20261107.md)两项原实现RED后，
改用stdlib use保留原错误和suppressed释放错误；实际后端14+启用Sherpa Demo56全过、lint0error/1warning。
本机Ollama确认一项、另一即时放行JNI建议否决，不计最终独立审查；无设备/下载/采集/上传，门禁不变。

2026-11-07：[Android语音两个清理缺陷](android-speech-cleanup-20261107.md)经真实RED后修复：
registry逐backend关闭并聚合错误，ASR异步progress异常归入session并释放recognizer。
speech29/ASR14/TTS3/Demo56共102JVM测试通过，两模块lint无error/6warning；新四文件复审429无结果。
qoder新通用测试任务403/pricingUrl/error112停止，无候选，不自动重试；不算独立review/设备或发布验收。
九保护/lock保持，无上传/下载/采集；10/12、P4/P11及release not-ready不变。

[TTS五文件限定源码审查](tts-scoped-review-20261106.md)code-reviewer无finding，
architect429无结果并停止；不计综合批准。显式JDK21实际重跑TTS/Demo JVM59项全过。
补充架构意见供应商待选择，不重试原生429；10/12及原设备/质量/资源缺口不变。

2026-11-06 F02：[DNG原始通知/.6本地交付](f02-dng-notices.md)修复实际编译头部AOSP声明遗漏。
qoder完成首项原文提取并经父代理核验；新候选74材料/6JAR/40原Skia静态库、两consumer和
新unsigned App通知保留通过。App复用.4框架，不计新SDK链接/设备验收；无上传或生产切换。
OpenCode额度复查仍429，已按用户选择转cbc hy3；qoder配方和已证实新坑已记录。
旧.4预检仅是历史，当前新.6精确hosted/发布授权/远端消费与资源门槛继续，10/12不变。

2026-11-06 F07只读核查：用户选择的cbc hy4free在148.31秒完成官方0.19.1源码审查、0工具请求。
主代理核验后确认新版仍无公开Android异步native cancel/join结果接口；已否决助手对版本新增和
析构不等待的过度推断，详见[版本核查](android-litertlm-later-barrier-20261106.md)。
固定0.18.1同步factory/真实推理保持，不升级或重跑；异步后置边界不变。

2026-11-06 维护归属：用户明确回答“我本人”，Apple/Skiko包均由用户本人维护，
见[维护记录](delivery-maintenance.md)。不再重复询问负责人；接收组织名称/Skiko具体hosted
选择与其余许可/生产验收继续单列。原制品和旧收据不改写。

2026-11-06 最新增量：F09 App材料修复两文件及F02第四输入校验补丁/回归工具已分别取得
fresh native只读审查APPROVE/0 findings，限于本次四文件技术范围，不是全分支或发布批准。
Skiko .4 Nexus候选115版本文件清单/本地摘要及逐项匿名HEAD404已保留，15可变metadata排除，
无上传/凭据使用。cbc hy3公开条款提取110.63秒完成，错误解释经父代理否决；实际来源适用
仍未闭合。旧trace不重跑，生产无recorder同generation线程退出信号仍缺受支持取证路径。
基础10/12及P4/P11不变，详见[F02预检](f02-nexus-delivery.md)及[F09审查](f09-app-notice-retention-20261106.md)。

2026-11-06 当前：[F09 App许可保留/内部上传](f09-app-notice-retention-20261106.md)已完成：
真实构建复现App缺24材料后，最小复制修复通过19 consumer回归、完整tools414项和双目标
unsigned App构建/24摘要核验。25,288,674 B Apple候选在用户具体确认后单次PUT到私有极狐，
HTTP201及认证回下载SHA通过；无Release/tag。agy7/cbc4摘要仍匹配，新增生成器修改超出原
Copilot审查，不能再称当前12文件全匹配。Nexus、维护归属、复审及生产质量/资源门槛继续。
基础10/12、P4/P11/release不升级；无新增benchmark。下段原准备轮的未上传/源码匹配描述为历史。

2026-11-06 上一准备轮：F09已形成[完整内部交付候选](f09-internal-git-delivery-20261106.md)，
20框架/4C模块/18资源和24份notice的完整ZIP为25,288,674 B，按用户规则准备Git内部制品交付；
108,785,484 B的Skiko `.4`走Nexus。原二进制和编译来源不变，新许可来源单独绑定，未上传。
F09的agy7文件、cbc4文件和Copilot最终consumer限定源码审查已有结果，当前源码身份仍匹配；
完整分支/法律/发布批准继续开放。F02原7文件packet获用户具体授权后已完成agy审查，
225.17秒/无新finding；已知publisher缺陷的第四补丁不在其范围，见[边界](f02-notice-validation-20261106.md)。
下一步是实际接收/维护与远端交付、未关闭的来源条款和生产资源验收；不重跑已验设备/构建。
基础10/12、P4/P11/release不升级。下方各次“尚未审查/许可未定/未获授权”以当前范围说明为准。

2026-11-06 F02 增量：[HarfBuzz ISC 补充/.4 交付](f02-harfbuzz-notices.md)已完成本地正常发布、
实际双 iOS SDK 链接和 unsigned App 的 72 份材料保留；修复根项目 notice 变体选择。
来源条款/独立批准、远端匿名消费、生产资源仍开放，基础 10/12、P4/P11/release 不升级。

2026-11-06 F02 App许可保留：.3真实SDK双iOS链接14任务通过，unsigned App正常构建，
69文件目录/摘要完全匹配；312工具测试通过。关闭本地App保留子项，详见
[证据](f02-app-notices.md)。文件条款/独立批准、远端匿名消费和生产资源仍未验。

2026-11-06 F02许可交付推进：新.3正常发布3模块115文件，69文件材料随6份notice/source
JAR交付；修改声明和独立消费者69文件保留/双iOS链接通过。Ninja输入核对3994文件/2115头文件。
文件级/历史Unicode/独立许可、远端消费和最终App/资源仍未验，详见[F02分发审查](f02-distribution-review.md)。


2026-11-06 历史输入：当时用户要求保持 solo，F09 新独立审查因 429／中断没有批准，勿重启旧失败代理。
F10 已确认没有获授权的身份／PAD 校准数据集；生产阈值和 LIVE/SPOOF 判定继续不可用，
不以既有 NASA 图片或合成样本补作校准，不新增采集。F02 已有用户指定 Nexus host，
匿名仓库清单／候选版本检索通过，组详情403、hosted策略查询被自动审批服务429阻断；
见 [Nexus交付准备](f02-nexus-delivery.md)。候选仓库未定为生产出口，维护团队／策略／
publication闭包／许可附件与远端消费仍缺；未使用或保存凭据、未上传。10/12及P4/P11不晋级。

2026-11-06 F09：用户明确选定内部闭源使用/分发，`3b5150d` 已加入项目条款和精确提交
LICENSE→snapshot→notices 接线，关闭“项目自身许可未选择”缺口；第三方许可、独立审查及
生产验收不据此通过。新notice-only检查150文件/20归属/23notice，未重建原生制品。
同时复现并修复mlx/mlx-c未跟踪.cpp漏检，真实缓存重新校验通过；完整tools309/309。
见[内部分发约定](internal-distribution.md)和[F09当前结果](apple-mlxvlm-distribution.md)。
P4/P11及10/12保持；下方“SDK源码许可未定”是历史，不能作为当前仍待选择的问题重复询问。

2026-11-05 TTS私有终态报告追加：同一b90e普通APK在TECNO/API35取得1次固定输入最终pass，normal/cancel-join/followup/close及关闭拒绝齐全，host255由finish前私有JSON回读补证；旧三轮缺终态不升级。新test-only overlay离线397任务通过，普通App/test/runner、12模型/附属摘要、权限与设置已恢复；无采集/下载请求/benchmark新增。见[证据与限制](android-committed-candidate.md#当前候选真机-tts私有报告终态验证)。9行仍有gap，P4/P11与发布open/not-ready保持。 下方缺终态描述为历史。

2026-11-05 F12当前候选真机TTS：[b90e精确候选](android-committed-candidate.md#当前候选真机-tts生命周期已观察最终结果仍缺)在恢复ADB授权的TECNO上已有Google服务，三PID均观察正常播放完成、活动取消join和重入完成；host255导致最终bundle缺失，文本/官方protobuf仅开始状态，不计最终pass或close通过，不再重试。原afed普通App/test/runner、12模型/附属摘要/权限/TTS设置恢复保持，临时文件清理，无采集/下载请求/benchmark；9行gap及P4/P11/发布门禁保持。

2026-11-05 F12首屏追加：[两种隔离Apple变体](apple-committed-candidate.md#两种隔离变体的真机首屏渲染)在同一iPhone稳定显示OpenCV Identity导入/原型/未校准提示和MLX普通首屏/五能力页签；仅显示，不含触摸/模型执行。CUA native pipe一次reset后仍失败，0用例执行；21阶段terminal，两候选已停、原Demo恢复、41既有安装URL/9保护/lock不变。无构建/采集/下载/benchmark新增；原9行gap及P4/P11/发布open/not-ready保持，不自动循环失败入口。

2026-11-05 F12真机追加：[精确编译隔离变体](apple-committed-candidate.md#隔离签名变体真机-smoke-与系统-ui-门槛)在iPhone17,2/iOS27.2完成合成白图Vision回调和14安装模型回读哈希核验；普通UI/TTS四项在系统automation初始化超时，实际0执行/0通过。无新构建/采集/benchmark；候选已停、独立runner卸载、原Demo恢复、既有App安装URL不变。不是原bundle全能力或多模态生成验收；9行gap与P4/P11/发布open/not-ready保持。

2026-11-05 F12增强Apple：[同源码整App](apple-committed-candidate.md)MLX unsigned双平台与独立OpenCV Identity设备目标构建通过，6878原始依赖文件/3产品树/664Swift输入固定，可选host契约6/6。没有F09 overlay或下载，也没有当前候选设备运行/采集结论；两个变体不称组合App，OpenCV arm64 simulator仍不支持。9保护/lock/普通手机Demo保持，P4/P11与发布仍open/not-ready。

2026-11-05 F12同源码Apple/shared：[e0438c0隔离候选](apple-committed-candidate.md)953文件回读，Apple481/KMP24/shared80及四组Swift→Demo桥接133项通过；基础Demo unsigned双平台、NativeConsumer simulator和三份KMP framework链接成功。80外部Klib文件/6产品树固定，Skiko仍生产0.144.6；无增强MLX/OpenCV整App或设备运行结论。9保护/lock保持，无新采集/benchmark/下载，P4/P11与发布仍open/not-ready。

2026-11-05 F12同候选运行：[b90e7830精确APK](android-committed-candidate.md#同一-apk-补充运行asrsridentity人脸与-tts)另经固定ASR全文/JNI生命周期、布局、双帧AAC超分/独立解码与Identity注册/未校准拒绝/purge、普通人脸选图通过，68项证据复核。TTS因专用AVD无引擎初始化真实失败并保留；临时测试/文件已清理、普通APK/模型/拒绝权限保持、真机未操作。不替代当前候选真机/Apple/真实采集及全质量资源/交付审查；P4/P11仍open/not-ready。

2026-11-05 F12精确候选：[Android独立归档重建](android-committed-candidate.md)953源码/14固定输入、四后端同时启用、997任务全执行，586项JVM及20模块lint无错误通过；新APK b90e7830在专用AVD完成普通固定图取消/drain/同PID重入/成功/清理，44项运行检查。原手机Demo未操作；同一新APK其他能力/真机与Apple全量候选仍缺，不用旧afed真机证据替代。无新增benchmark或采集，P4/P11与发布保持open/not-ready。

2026-11-05 F07真机最终包：TECNO原afed156b普通App经既有ZIP系统导入后，Mali-G57硬件GPU初始化后取消/等待/同图同PID19257重入成功及普通清除均通过，52项只读复核；授权模型留手机、临时ZIP删除，原App/test/runner/权限与五ASR/SR摘要保持。人工触摸/其他provider、异步合同与质量资源继续开放，见[真机范围](android-litertlm-ui.md#2026-11-05-最终组合包真机导入取消与重入)。

2026-11-05 F07最终组合包：专用软件GPU模拟器用afed156b最终普通APK，经系统红图选择、native初始化后取消/CANCELLING禁用、CANCELLED、同输入同PID9200重入真实SUCCEEDED及普通清除通过；38证据检查、四模型/拒绝权限/cache清理均匹配。分层264source hash/2旧测试差异明确，不是全量重建；真机最终候选/人工触摸、其他provider、异步drain及质量资源仍缺，见[范围](android-litertlm-ui.md#2026-11-05-最终组合包普通-ui-取消与重入)。

2026-11-05 F05生命周期：保持TECNO普通App/模型/权限不变，只替换测试APK，用既有合成英文在实际JNI decode阶段验证取消/drain/busy/同backend重入/活动close/关闭拒绝与输入所有权，13检查通过；原test/runner恢复。后端12项fresh JVM回归、offline build/lint通过。见[原始边界](android-sherpa-asr.md#fixed-input-native-lifecycle--2026-11-05)；不采音，真实capture/前后台与质量/持续资源/API26仍缺。

2026-11-05 F12输入冻结：[本地清单](p11-input-freeze.md)已统一5a65f51源码归档951文件、6组27份外部模型及附属文件、14媒体样本、1固定TTS请求和9能力历史身份；只读复核及状态提升/fixture篡改拒绝通过，9保护文件与生产lock保持。缺失旧人脸原fixture和系统模型字节身份明确保留，未从同一候选全能力重建重测；新packager审查429无结论。P4/P11与发布仍open/not-ready，无新增benchmark/录音/设备操作。

2026-11-05 F09推理/依赖：最终20模块产物的fresh binary-only消费者已在iPhone17,2/iOS27.2完成固定图片与29FPS合成视频真实生成，模型身份/14文件host+device hash、drain/close/lease释放/帧清理通过，PID2184终止、普通Demo恢复；8份原样上游测试在macOS27.2通过230逻辑测试/295case执行、0fail/skip。性能microbenchmark排除；首轮漏测试helper编译失败保留并补原文件后通过。详见[分发验证](apple-mlxvlm-distribution.md)。独立审查/源码再分发许可/生产交付策略和P4/P11/质量资源/minOS仍未完成，无新增benchmark样本或录音。

2026-11-05 F09正式入口：完整fresh双平台编译→20XCFramework/4C/18resource/22notice→ZIP/checksum命令已过；新产物direct/ZIP第二consumer两平台及manifest绑定真机resource通过。tools297/297，OrderedCollections host依赖262/262；MLX依赖/模型binary推理/独立packager审查/许可与更广发布验收仍开放，下段入口尚缺为历史。

2026-11-05 F09后续：正式v2清单和generator两平台build、20真实本地ZIP/checksum/SwiftPM解析及第二ZIP消费者两平台编译链接均已通过，真机正式清单绑定资源报告通过。staging审查两项缺陷已复现并修复；完整pipeline仍需正式package入口/依赖测试，binary模型推理和release许可/验收开放。详见[最新分发证据](apple-mlxvlm-distribution.md#2026-11-05-manifest-driven-static-artifacts-and-zip-consumers)。下段staging缺口为历史。

2026-11-05 F09：用户已采用隔离兼容overlay继续本地构建；fresh完整固定依赖和SDK快照实际形成20个静态双sliceXCFramework，搬移binary-only消费者device/simulator编译链接通过，真机Bundle.module/Metal加载与MLX小算术通过。正式manifest的C/static/逐平台资源来源、package入口、notices/依赖测试及ZIP第二consumer尚缺，F09不关闭。见[真实二进制证据](apple-mlxvlm-distribution.md#2026-11-05-real-static-binary-closure-and-device-resource-smoke)。

2026-11-05 F05交付追加：离线 ASR 的独立 published AAR consumer 已通过 sherpa Debug/Release、原有三 flavor Debug 及相关 lint；关闭 sherpa 选项也可在不提供 runtime 仓库时构建。真实依赖解析验证发布制品来源和可选隔离，未运行消费者录音。P11 的 Android ASR 行改为有界基本功能通过，系统 Google 资源和准确性/生命周期/资源等缺口保留。见[验证](android-sherpa-asr.md#independent-published-consumer--2026-11-05)。

2026-11-05 F05最新：可选 `sherpa.onnx.whisper` 无需 Google/系统语言包，固定样本真实推理已通过，但用户两次分别授权的普通麦克风尝试均内容错误，不能验收。现场噪音较大；合成静音/纯噪声假文本已复现，新增同runtime固定MIT Silero VAD后两者真实拒绝，干净/SNR10英文仍匹配，SNR0仍错；系统session降噪可用时启用，现场修复效果待新的授权实测。新增无语音映射与模型准备互斥/初始化防崩溃回归。详见[当前证据](android-sherpa-asr.md)。修复版第三次单独授权采音后用户确认基本正确、少量漏错词，基本功能可用；三次授权均已消耗。系统ASR、质量/生命周期/最低系统与发布缺口保留，未纳入统一benchmark。

2026-11-04 F11追加：P11总表和readiness现已纳入Android ASR资源入口/授权请求、SR完整请求/动态重播、LM真机ZIP/同步生成及双端Identity/PAD原始分数证据，移除已实现后端/模型/factory仍未实现的过时描述。当前校验226证据引用/241来源hash/9行gap；grouped的8组原始统计不变，状态和剩余gap按新证据更新。36项相关工具测试通过；仅重验既有产物，不增加benchmark样本、不升级质量/资源/校准或发布结论。见[P11矩阵](p11-status-matrix-20261027.md)与[可复算入口](p11-grouped-benchmark.md)。ASR最新只读复核仍无默认网络，其他既有待答复项不变。

2026-11-04 F06追加：复用保留输出a21c0fcf…6e47ea的精确临时副本，TECNO/Visha设备端录屏exit0、完整解码120屏幕帧；稳定52帧按序覆盖全部48输出帧，固定2秒静音结果完整动态重播已证。不是再次普通按钮/FileProvider验收，也不代表帧节奏预算、主观质量/音频/长期流畅度。多次ADB断连及exit0部分读取被保留，清理校验阻止误删后按设备hash精确清理六个临时文件；远程glob核验问题改为逐路径hash，原四export不变。BACK回同PID1551，包/权限/空input-output保持；新验证及原60/60通过。见[动态重播](android-video-sr-demo.md#2026-11-04-tecno-complete-dynamic-replay-of-retained-output)。

2026-11-04 F06最新更新：TECNO原包/模型在同PID1551完成原48帧自然派生片普通请求，输出a21c0fcf…6e47ea；实际“播放结果”进入Visha，精确export URI/hash及位置2000ms已绑定，直接截图显示蓝天/白云/飞鸟并最佳匹配输出第48帧（显示内容SSIM0.99815，不是SR质量）。正常BACK返回后普通移除输入，input/output均释放；旧3export保持、新第4份按1天策略保留。60/60证据检查通过，见[末帧显示与清理](android-video-sr-demo.md#2026-11-04-tecno-visible-output-frame-and-ordinary-cleanup)。首次录屏/移动截图255失败保留，录屏只有单帧；连续播放仍未证，历史remove-task/process-death corner不变。

2026-11-04 F07最新更新：TECNO普通Demo完成已授权3.66GB ZIP系统DocumentsUI真实选择/校验安装→系统红图选择→真实32-token SUCCEEDED→普通UI清除输入。冻结644组合候选、模型/输入/254匹配源文件身份明确；Mali/Vulkan硬件视觉与CPU/GPU销毁已记录。恢复原405APK/空LM存储/权限，新增模型副本/ZIP/恢复临时APK精确删除；流式恢复超时与终结失败会话保留后，改完整传输/SHA校验再本地安装成功。317/317证据检查通过，见[真机ZIP主流程](android-litertlm-ui.md#2026-11-04-tecno-系统-zip-导入与真实生成)。这关闭真机ZIP自动化选择子项，不是当前包取消重入、人工触摸、任意provider、自然质量/资源或完整发布通过。

2026-11-04 F05最新更新：获明确授权后，TECNO无Activity/录音发起en-US系统下载，返回requested-without-events-not-ready/unverified；后置查询仍NOT_REPORTED。实际on-device服务来自com.google.android.as，当时Wi-Fi已开但无默认网络；另一个Google Search包的“English(US)预装”不证明资源就绪。普通405APK/权限不变，原test APK/runner已恢复；34/34证据检查通过。已询问可用已保存Wi-Fi或网络恢复信息，等待输入。原生speech消费者新入口从隔离本地Maven AAR实际编译/lint通过（120/120 tasks executed、72/72证据检查），未执行消费者录音/下载。见[验证](speech-backends.md#2026-11-04-tecno-授权资源请求与独立-aar-消费)。真实安装/转写仍未证，不关闭F05/P11/发布。

2026-11-04 F05功能更新：新增系统ASR SDK/Demo语言资源检查与显式下载恢复入口，API33查询/API34可观察下载；排队/空列表/进度不当作安装，下载需用户确认，后台/退出/关闭释放监控并与麦克风互斥。该轮ASR13/13、组合Demo62/62、默认54/54、共享iOS80/80、双端compile/APK/ReleaseAAR/lint通过；实际隔离MainFlow新包644bbd…df30普通点击查询显示UNSUPPORTED_DEVICE、权限仍denied，未下载/录音。287项检查属于f946970历史候选，本次runner修改后不称旧255源码快照仍匹配当前树，见[验证](speech-backends.md#2026-11-04-android-sdkdemo-语言资源恢复入口)。此前bd89包的三后端实测仍是旧候选证据；新包仅验资源查询。真实系统下载/转写仍缺设备资源，不关闭F05/P11/发布。

2026-11-04 当前组合包主流程更新：同一冻结三opt-in APK bd89bdbf…16bd7在既有LM专用模拟器完成普通系统红图选择/真实生成/清除输入（PID3723、模型保持、SwiftShader软件视觉及CPU/GPU销毁），在MainFlow完成NASA系统选图/注册1模板/未校准验证与图库拒绝/删至0/整会话停用（PID5514）。联合297项证据检查通过；独立复审429无结论。与前轮同APK的SR成功合起来，最新包三后端主流程各自已实际运行，不能扩大为同设备全链路/当前包取消重入/硬件GPU/质量/发布通过。见[联合边界](android-mainflow-emulator.md#current-candidate-lm-and-identity-execution)。

2026-11-04 F06完整请求/当前候选更新：TECNO旧APK普通UI完成原48帧High派生片，1280×720/2秒/no-audio，独立完整解码/PTS≤1µs/BT709与精确输入输出通过（49项）。Visha收到精确export URI且hash匹配，实际画面未捕获；后置remove-task进程死亡与孤留输入手动清理明确保留，不计普通UI清理。另HEAD7132208最新三opt-in APK构建/lint及Demo56/56实际执行通过；backend33/4/6为缓存结果，仅更新既有隔离模拟器，普通双帧AAC成功/移除输入清理通过（301项），此包LM生成/Identity注册未验。见[真机48帧](android-video-sr-demo.md#2026-11-04-tecno-complete-48-frame-natural-request)与[当前候选](android-mainflow-emulator.md#current-three-opt-in-candidate)。历史失败和发布not-ready不变。

2026-11-04 F06 真机普通UI更新：TECNO/API35系统模型/视频picker取消重入、非零进度取消/部分输出删除、同输入AAC两次成功、自然素材4帧派生片320×180→1280×720、分享chooser及外部Activity返回已验证；108项检查含完整解码/PTS/BT709及两次AAC4包hash/PTS。原48帧请求遇充电75%自动退出省电/CONFIG_UI_MODE重建而中断，未计成功；Files播放入口停首次条款页，未接受条款，实际画面播放未验。清除输入后私有输入/输出释放，两份短期export保留。见[范围与证据](android-video-sr-demo.md#2026-11-04-tecno-ordinary-ui-bounded-natural-and-aac-sequence)。F06/P11/发布仍open/not-ready。

2026-11-04 F09 候选复审更新：[固定兼容候选](f09-compatibility-candidate/README.md)已获独立只读审查与临时patch重放，4个patched hash及生产lock/runtime绑定匹配，未发现阻断候选可评审性的语义缺陷。仍为draft-not-adopted；缺fail-closed applicator、完整依赖测试和20模块双slice/C/Metal资源/搬移binary consumer，待兼容维护路线明确后恢复。复审不代表采纳批准或F09完成，生产lock/缓存未变。

2026-11-04 F07 真机普通UI更新：TECNO/API35复用固定APK/已有授权模型，系统图片picker取消重入、红图成功、原生初始化后CANCELLING→CANCELLED、同PID9562自然飞鸟首帧再次生成成功；Mali-G57/Vulkan硬件视觉delegate及三轮销毁日志已记录，67项证据检查通过。本轮私有输入/cache清理，原APK/原空LM目录恢复，旧fixture保留。见[真机UI证据](android-litertlm-ui.md#2026-11-04-tecno-普通-ui-与自然图像重入)。真机ZIP导入/人工触摸、完整自然质量/性能、异步drain仍开放，不提升P4/P11/发布。

2026-11-04 F06 自然输入主流程更新：隔离 API36 普通 Demo 已将既有 Common swifts 的标准 H.264 High 派生副本 320×180/48帧实际 ESRGAN CPU 4×处理为1280×720。UI终态成功；独立完整解码、48帧/PTS、BT709及无音轨检查通过，移除输入后本轮私有输入/输出释放。原始 High444 profile 平台失败及零decoder候选证据保留，未宣称支持；派生副本不是原无损质量参考。见[证据](android-mainflow-emulator.md#natural-video-main-flow)。单自然输入主流程不代表自然/时域质量、真机UI或性能通过；F06/P11/发布仍open/not-ready。

2026-11-04 F07功能更新：授权Gemma权重已完整下载/校验；显式CPU文本＋GPU视觉在TECNO真实shell链路输出Red。普通API36隔离模拟器系统ZIP/图片picker、两次真实生成、取消/同PID重入及输入/cache清理通过，57项证据检查与独立限定补审PASS，见[UI验证](android-litertlm-ui.md)。实际模拟器delegate为SwiftShader软件GPU；物理设备UI/硬件GPU性能、自然质量、异步drain/streaming及长期资源仍开放，不提升P4/P11/发布。下载或真实图像生成不再是阻塞项。

2026-11-04 F05 资源状态复核：新增无 Activity/录音/下载的独立查询探针，TECNO KM9/API35 的 `checkRecognitionSupport(en-US)` 回调三份语言列表均空，状态NOT_REPORTED，资源就绪仍未确认；未执行转写，不沿用历史错误13冒充新结果。离线构建/lint通过，普通Demo APK不变，原test APK/runner恢复、设备Asleep、九项保护不变。见[证据](speech-backends.md#2026-11-04-android-离线资源只读复核)。

2026-11-04 F10 PAD 更新：用户明确允许固定官方模型与既有图片本机原始分数试验，最终独立 iPhone OpenCV4.12探针 **9/9**，两次有限 `1x2` 输出一致，含设备SHA384/原生诊断 RED→GREEN；RGB/NCHW及报告持久化 host RED→GREEN、构建与 immutable 证据复核通过。固定96位校验值为 SHA-384，已更正此前 SHA-512 误标，未换模型。见[证据与复审裁定](pad-raw-score-smoke.md)。本轮仅关闭目标 runtime 真实推理，不是完整 PAD 后端/活体判定；实验性整图预处理、授权攻击/校准数据与策略、SDK/Demo 接入仍待完成。九项保护及生产 lock 不变；普通 Demo 已无参数恢复；P4/P11/发布保持 open/not-ready。

2026-11-04 F10/F12 复审更新：双独立原生审查后补通 Apple OpenCV 脱敏诊断合同，新增可选 stage/category sink；optional host **6/6**、最终 iPhone native/session **24/24**、probe/普通 Demo build 通过。回调内同步 close 无死锁且模型租约释放，普通 App 已无参数恢复，保护文件及生产 lock 不变。复审 **COMMENT + architect CLEAR**，仍有1个 Medium 大图 PNG 超限 corner 按用户要求后置；不是完整分支/发布 APPROVE。见[证据](apple-opencv-identity.md#2026-11-04-independent-review-and-native-diagnostic-delivery)。PAD、识别校准与 Android LM/ASR 资产仍开放。

2026-11-04 F06/F10 Android UI 更新：隔离 API36 模拟器复用既有组合 APK 与已批准素材，系统选图→真实注册1模板→未校准验证/图库拒绝→候选物理删除/整次停用通过；系统模型/视频 picker→真实 ESRGAN 4×→重入→播放器 Activity/分享 chooser 也通过。两份导出经独立 FFmpeg/ffprobe 验证帧 PTS 与4个 AAC packet/hash/PTS；11份 UI 观察及九项保护文件校验通过。见[证据与边界](android-mainflow-emulator.md)。未解锁 Android 真机、未发送文件；这是模拟器 UI 子项，不替代真机触控、持续视听播放、自然质量/校准/PAD或发布。下方“实际 UI 未验”需按此平台边界阅读。

2026-11-04 F10 真机 UI 更新：普通 Apple Demo 已通过系统 Files 图片选择取消/重入/真实注册/未校准验证拒绝/删除停用及模型文件夹导入，**9/9 观察检查**。使用隔离本机 IdentityInput 来源，仅含既有获批图片/固定模型，不写 Photos；普通 App 已无参数恢复。见[真实界面证据](apple-opencv-identity.md#2026-11-04-ordinary-demo-system-files-ui)。这关闭本地 Files provider/自动化点击子项；人工触摸、外部 provider 与校准/PAD 仍缺，下方“实际系统 provider/触控未验”为本检查点之前的历史状态。

2026-11-04 F10 Files 输入更新：可选 Apple host 新增“从文件选择图片”，无需写入 Photos；同一生产读取 handler→opaque probe→真实注册的 iPhone 主流程 **18/18**，普通/flag-disabled Demo 构建通过，普通 App 已恢复。实际系统 provider/触控未验，独立复审 429 无结论。见[验证](apple-opencv-identity.md#2026-11-04-files-image-input)。独立 PAD [官方候选](pad-model-candidate.md)已形成具体本机试验方案，授权等待期间未下载新模型。

2026-11-04 Apple F10 更新（`82c1cc3`）：已实现[可选 Apple OpenCV 真实 Identity](apple-opencv-identity.md)，固定 YuNet/SFace 完成检测、对齐、128维 embedding、模型导入、显式同意与加密注册/整授权域删除，并接入现有[精确 cosine index/query](apple-exact-identity-index.md)和共享 Demo。iPhone native/session **21/21**、Demo host→bridge→shared ViewModel **15/15**；默认 Apple **481/481**、可选 host contracts **3/3**，device/simulator test build 与普通 Demo 构建通过，模拟器测试未执行。两候选复用同一获批图片，不证明不同人的识别或准确率；1:1/1:N 策略仍 UNVERIFIED，独立 PAD、授权校准数据及实际触控/picker 验收仍缺。独立 reviewer 两项具体意见已修，随后服务 429，无最终 APPROVE。原 P4/P11/发布门槛不变。

2026-11-04 F10 Demo 更新：已增加多候选注册/选择、多模板计数、候选删除与图库发布/删除控件；删除候选前先确认图库 purge，整会话停用按授权域撤销并清理全部模板。共享 **80/80**、Identity **327/327**、默认/组合 Demo **48/53**，真机两候选记录/单独删除/重开存储撤销与 purge 通过。真实模型图库发布仍因策略未校准而拒绝，实际触控/picker 未验；`PURGE_FAILED` 恢复后置，页面明确显示终态失败且候选删除未完成。见[验证](identity-gallery-demo.md)。

2026-11-03 F10 功能更新：新增可选 Android OpenCV 4.12.0/YuNet/SFace 真实检测→对齐→128维 embedding、模型 ZIP 导入、显式本机授权、加密注册/删除；最终源码真机正例链路通过。随后补齐[Android 精确 cosine 1:N index/query](android-exact-identity-index.md)，支持 encoded-image probe，并接入可选 Demo session；1:1 和图库发布仍因未校准策略返回 `THRESHOLD_UNVERIFIED`。独立 PAD 仍缺；Apple 真实 Identity 已由上方 2026-11-04 检查点补齐。见[真实模型验证](android-opencv-identity-demo.md)。下面的原始缺口以最新更新为准，原 P10/P11/发布门槛不变。

2026-11-03 功能优先更新：F06 已新增可选 Android LiteRT/ESRGAN 真实后端、模型/视频导入和输出查看/分享接线；小合成 AAC 视频的真机推理、包/帧时间戳、取消重入与输出所有权通过，实际 UI 和自然质量/长期资源仍开放，见[本轮验证](android-video-sr-demo.md)。F07 同步 adapter、production factory 与模型 ZIP 导入已实现；真实图像推理仍缺授权权重，异步 drain 仍 UNPROVEN，见[同步验证](android-litertlm-sync-demo.md)。下方原始缺口以此更新为准。

2026-10-28 用户决定：结束原有长周期执行，将遗留问题转入专项；随后要求按可执行队列继续推进。F01 的 sandbox 子项、F06 的 Apple 非零进度取消子项与 F08 的真实 Files provider 子项现已完成，其余原始验收仍是未通过或未验证。F01–F12 现已在 GitHub 与 GitLab 各建立同名 milestone 和跟踪 issue；链接与更新约定见[专项跟踪索引](foundation-tracking.md)。负责范围指模块责任边界，尚未指派人员。专项未完成前，P11 发布结论保持 `not-ready`。

2026-10-31 F08 已新增第二来源/场景的 Kawaida 瀑布公开素材及真机 9/9 请求；三个自由描述含瀑布/水，但运动与冻结对照严格标签 pair 0/3，诊断性去句号仍 0/3。见[跨场景原始证据](multimodal-video-demo.md#2026-10-31-f08-第二自然场景kawaida-瀑布)。这缩小了“没有第二场景输入”的缺口，F08 自然质量与 P11 发布门禁仍开放。

2026-11-03 F04 的 iPhone 真机麦克风完成三轮 `finishInput → success`，另在隔离诊断 App 验到显式取消、取消后重入、Home 后取消及回前台重入成功；`unsupportedDevice` 复现对应请求 locale `she-CN`，不是设备对 `zh-CN` 的能力结论。详见[麦克风验证](ios-microphone-asr.md#2026-11-03-真机-f04-状态与-locale-诊断)。自然质量和离线矩阵继续开放。

同日追加的[close/drain 真机探针](ios-microphone-asr.md#2026-11-03-显式-closedrain-真机探针)已在单轮插桩副本中观察 engine/tap、buffer delivery、原生任务、观察者和 session lease 顺序释放，以及关闭后拒绝重入；长期泄漏预算、并发竞态和未插桩生产二进制仍未验。

## 当前可执行队列（2026-10-28）

用户进一步明确：先排除目前确有外部卡点的项目，低版本系统真机暂时无法提供。下面只调整执行顺序，**不删除原需求、不改变 P4/P11 或 P11 `open/not-ready` 状态**。每项只认它自己的退出标准，不把可完成的子项当作整个能力通过。

| 优先级 | 现在可做的有界工作 | 为什么可做 / 停止点 |
| --- | --- | --- |
| 已完成子项 | **F01：iPhone 沙盒合成 EXIF2/6/7 →生产 DemoController→Vision** | 真机 3/3，结果与边界见 [P4](p4-camera-stability.md)；真实 PHPicker 仍开放。 |
| 已完成子项 | **F06：Apple 非零导出进度取消与部分文件清理** | 真机 3/3 在 5.33%–7.47% 进度取消，取消时已有 943–1,347 字节部分文件，调用返回前均已删除；同进程 AAC 重入 3/3。见[导出取消](apple-video-audio.md)。 |
| 1 | **F04 的 iOS 麦克风生命周期部分** | 真机已有 `finishInput → success`、显式取消/重入、Home 后取消/回前台重入及单轮插桩 close/drain；下一步验证长期资源预算与并发竞态。自然语音 CER/WER、TTS 主观质量和完整离线矩阵另需授权语料/参考文本，不能由合成 fixture 代替。 |
| 已完成子项 | **F08：真实 Files provider 选择** | 公开自然飞鸟短视频经系统 Files provider 进入共享页面并完成 native MLXVLM 输出，见[原始记录](multimodal-video-demo.md)。 |
| 已完成子项 | **F08：Home 往返取消与前台重入** | 真机 XCTest 1/1；真实进入后台，回前台观察到取消，1秒内不自动重启，同一输入再次生成成功；首 token 前 Home 诊断已观察到进入 background 后 106.301 ms drain、107.359 ms terminal；跨素材仍未覆盖。见[原始记录](multimodal-video-demo.md)。 |
| 已完成子项 | **F08：固定自然视频五次语义 rubric** | iPhone 同一进程 5 个 distinct run 的 subject/action/scene 与 semantic pass 均为 5/5，固定素材/模型/运行时/原始报告均有哈希绑定；这只关闭“更多 distinct runs”的执行子项，不是完整自然质量。见[原始记录](multimodal-video-demo.md)。 |
| 2 | **F08 的 Apple 现有模型短视频/Demo 链路** | 公开自然飞鸟短视频的 native 输出/8帧时间/取消重入、共享 Demo 的 VIDEO 路由/取消/离开/重入/AX 终态、真实 Files provider 选择及 Home 往返取消/1秒不自动重启/前台重入已完成；tagged trace 进一步确认取消在 `willResignActive` 后、`didEnterBackground` 前完成并保留 request/backend/drain provenance，见[原始记录](multimodal-video-demo.md)。固定单素材/单 prompt 的五次真机语义 rubric 已完成；同源 motion/frozen-control task-set 已执行 9/9，但 temporal exact-label suite 为 `observed-failure`（pair 0/3），不能升级为 temporal quality 通过。跨任务/跨场景自然 temporal quality、GPU/能耗及持续资源仍未验证；固定 fixture 的 in-background terminal timing 已由首 token 前诊断关闭。 |
| 3 | **F11 的现有能力报告补齐** | 使用上述新原始数据，补按能力/设备/后端的真实报告和缺口；不重新包装旧 3 组报告，不凭报告格式提升质量、GPU 或能耗结论。 |

**暂不进入近期队列的外部卡点**：F03 需要 iOS15/API26 真机；F05 的可选 sherpa 离线基本功能已获用户确认，系统 Google 资源路径仍未就绪，新增采音需用户在场授权；F07权重与同步真实推理已通过，异步native drain/streaming后置；F10 需要生产校准策略、授权数据和校准集。真实 PHPicker 也需要将合成资产写入 Photos；当前按用户要求不做这项写入，因此保留为 F01 的独立后续。输入出现后再恢复对应专项。

**独立技术难题，后置且不冒充外部设备卡点**：F02 的真实对象原生回归与修复补丁已获有界独立复审，固定版本 Skiko 本地发布、独立消费者双 iOS framework 链接及无 recorder 同 PID 真机十轮已完成；仍需来源条款适用、生产分发所有权、长期资源预算与原验收。F06 的 Android 可选后端已实现，真机普通UI完整48帧、同字节输出完整动态重播/同PID返回清理与模拟器当前组合包双帧AAC通过；帧节奏预算、自然质量与持续运行仍未验。F09 本地构建/消费者/推理及精选依赖测试已过，项目内部许可已选，限定源码审查和带许可的Git交付候选已有结果；接收维护、远端实际消费、完整复审和生产验收仍缺。F12 完整复审此前遇服务429，无批准结论。它们仍是交付要求，延期不计通过。

| 编号 / 负责范围 | 问题与已有证据 | 恢复输入 / 下一动作 | 专项退出标准 |
| --- | --- | --- | --- |
| F01 / iOS Demo | [P4](p4-camera-stability.md)：真机 sandbox EXIF2/6/7→生产 `useImage`→Vision 已完成；真实 PHPicker→controller 仍未验 | sandbox 子项已完成，证据见 `artifacts/verification/p4-picker-sandbox-device/summary.json`；后续若恢复 PHPicker 专项，才考虑合成资产的 Photos provider 路径；当前不写入 Photos | 三例实际选择同一指定合成资产，尺寸/镜像像素及 `apple.vision.face` 成功结果均可复核；sandbox 结果不替代 PHPicker，且不读取私人照片 |
| F02 / Compose/Skia 资源 | [AtlasTextOp](p4-atlas-text-cache.md)原始TLS回归RED→CacheOwner候选GREEN及限定独立补丁审查已完成；`.1`双iOS链接、无recorder十轮及870.75秒trace保留，仍不能判零泄漏。当前交付候选为`.6`（108,791,304B/115版本文件/74材料），正常本地publication、`.6`自身SDK双iOS重链和App材料保留已过；维护人为用户本人。Nexus尚未上传，生产依赖未切换 | 落实已在等待答复的精确hosted/本次候选上传范围，继续真实匿名远端消费；来源条款适用与长期资源预算保持独立门槛，不重跑已有本地发布/链接 | 固定生产 runtime UUID、对象地址、分配/析构/free/TLS 与线程生命周期；修复或有证据的边界裁定，并证明持续重入与资源预算 |
| F03 / 最低系统 | [设备矩阵](device-matrix.md)：iOS15/API26 缺设备 | 取得相应真机后恢复启动、availability、权限、选图/相机、取消、前后台、离线门禁 | 两平台最低系统实际运行报告；编译下限不替代运行验收 |
| F04 / Speech iOS/TTS | [麦克风验证](ios-microphone-asr.md#2026-11-03-显式-closedrain-真机探针)：中文真机结束、显式取消/重入、Home 后取消/回前台重入及单轮插桩 close/drain 有界通过；长期资源/并发竞态、CER/WER、自然 TTS 与离线矩阵仍缺 | 可比较的持续运行与资源预算；另备授权语料、参考文本和网络观测方法 | 麦克风终态、资源 drain、取消重入/后台、CER/WER、自然 TTS 与离线矩阵逐项报告 |
| F05 / Speech Android | [离线ASR](android-sherpa-asr.md)普通麦克风基本功能已获用户确认、独立AAR消费通过；[当前636候选ASR13项](android-current-device-20261107.md)及[当前TTS生命周期](android-current-tts-20261107.md)通过并恢复原App。另有旧afed普通包5预热+30次ASR资源测量，须保留其旧APK身份；Google系统语言资源、强噪声/中文精度、真实capture完整生命周期及长期预算仍缺 | 推进授权既有输入可覆盖的质量/资源与capture设计；新增采音仍需用户在场授权，既有三次已消耗；API26保持延期 | 固定输入native生命周期、麦克风基本功能与完整质量验收分列，不用样本或用户基本正确确认替代受控准确性验收 |
| F06 / Video | [超分](video-sr-backends.md)、[导出取消](apple-video-audio.md)Apple部分输出清理3/3通过；Android普通UI48帧、同字节完整动态重播/返回清理已有证据；[当前636候选SR6项](android-current-device-20261107.md)固定输入原生生命周期也已通过。两种APK/输入范围分列；自然/时域质量、传递函数、帧节奏预算及长期资源仍缺 | 按两端已支持profile推进固定自然素材质量裁定与可比较播放/资源测量；不重复已过48帧重播、当前SR生命周期或Apple取消 | 两端各自支持profile的真实输出/质量、取消与完整文件清理、持续运行报告，不把插值基线或固定片段重播当完整质量验收 |
| F07 / Multimodal Android | [同步接入](android-litertlm-sync-demo.md)、[普通UI](android-litertlm-ui.md)：固定0.18.1/授权Gemma/CPU文本＋GPU视觉；最终普通APK在TECNO经系统ZIP/图片选择、Mali硬件初始化后取消/同PID重入/成功清理通过，模型保留可用；软件GPU证据另保留；异步drain未证 | 人工触摸、其他provider、跨任务自然质量与可比较资源；异步drain/streaming后置 | 分列同步/软件模拟器/真机和异步未证范围，完成质量资源报告；supportsVideo=false |
| F08 / Multimodal Apple | [图像](apple-mlxvlm-backend.md)、[短视频](apple-mlxvlm-short-video.md)、[Demo](multimodal-video-demo.md)既有Files/VIDEO路由、取消重入和Home证据保留；[普通人工模型导入/生成/清除/冷启动](../HANDOFF-2026-11-06-apple-import-accepted.md)已验收。[当前636原生链路](apple-current-runtime-20261107.md)图片/视频/feature释放通过（非UI）。自然temporal任务仍observed-failure：两个来源motion/control均0/3；MLX可回收cache归因不等于预算/零泄漏 | 保留准确的App/source身份；推进跨任务自然temporal质量及可比较GPU/能耗/持续预算，当前候选完整普通交互仍未统一验收；不重复已过Files选择或原生生成 | 保留真实 KMP/SDK 输出、时间/帧语义、自然质量及完整交互报告；synthetic 30/30、单一自然视频输出或本轮失败 task-set 都不替代完整自然质量 |
| F09 / Apple MLX 分发 | [分发](apple-mlxvlm-distribution.md)20模块双slice/4C/资源及固定推理已有证据，内部许可已选；25,288,674B候选已上传私有GitLab。新[远端消费者](f09-remote-consumer-20261107.md)实际新GET/344文件验证→全新consumer/DerivedData双平台Release→两App各24材料→模拟器模型bundle/Metal运行已通过；维护人为用户本人。未创建Release/tag | 该新远端consumer的实机资源/推理仍未验，构建为同一物理Mac；来源许可适用、完整分支审查及生产验收继续。接收组织名称未记录，不能再把已明确维护人列为待定；不重复上传或已过远端编译 | 原SDK编译、App材料、远端字节及实际消费范围分列；许可适用、完整复审与发布验收逐项裁定后才能改变release状态 |
| F10 / Identity/PAD | [Android普通Identity](../HANDOFF-2026-11-06-android-identity-accepted.md)及[Apple组合App人工流程](../HANDOFF-2026-11-06-apple-updated.md)已完成注册、未校准拒绝、删除停用；图库/双端真实特征基础已实现。[当前Android资源](android-identity-resources-20261107.md)30测量通过；[独立PAD原始分数](pad-raw-score-smoke.md)固定模型iPhone9/9通过，尚无SDK/Demo PAD判定。manifest/score离线工具通过；授权校准/攻击数据和生产阈值仍缺 | 继续无真人数据的指标/协议工具；接入PAD前仍需明确capture/crop及policy，不能重复要求已过普通picker/注册流程或将原始分数写成LIVE/SPOOF | 1:1、1:N、PAD 指标与阈值、授权撤销/删除和真实设备流程验收；本机正例流程不提升为生产识别能力 |
| F11 / P11 质量/资源/性能 | [状态矩阵](p11-status-matrix-20261027.md)、[分组报告](p11-grouped-benchmark.md)现有9状态行、8能力/11组；新增当前636 Identity30测量，旧10组保持。Android ASR30/LiteRT-LM5资源测量仍绑定旧afed包；当前ASR/SR/TTS原生生命周期不新增benchmark。9行均保留gap，GPU/energy/自然质量和长期预算未统一 | 冻结每能力任务/阈值与同构测量方法；按真实原始逐run报告补剩余质量/持续资源，不能以已完成采样RSS或工具测试数量代替生产验收 | 所有实际交付能力正确性/质量/延迟/峰值/取消/持续运行有原始逐run数据与分组报告；不能可靠测量项保持 null 并明确发布裁定 |
| F12 / 最终复审与候选冻结 | [当前636 Android候选](android-current-candidate-20261107.md)1046源码/14输入、599JVM及四后端构建、[ASR13/SR6](android-current-device-20261107.md)、[TTS](android-current-tts-20261107.md)和[Identity资源](android-identity-resources-20261107.md)已完成各自范围与恢复；[同源Apple构建/bridge82](apple-current-candidate-20261107.md)及[原生2方法/86检查](apple-current-runtime-20261107.md)已通过。完整当前候选统一交互/质量资源和整分支独立审查仍缺，protected signing不属交付修改 | 按精确SDK/App/模型身份补未覆盖能力和完整审查；旧Apple全量freeze命令仅全局manifest断言已过期，原生440文件/86检查已补核对，见[本次状态审计](roadmap-status-audit-20261107.md)；不重跑已过构建/设备推理 | 未解决 finding 和 review 缺口逐项裁定，所有 P11 原验收通过后才可改变发布结论；合并/tag/发布另受授权边界约束 |

原扩展 SDK/Demo 计划仍保留，以上是执行延期清单，不删除需求。后续按编号启动专项，完成一个关闭一个；本轮收尾后不再自动循环重试这些项目。
