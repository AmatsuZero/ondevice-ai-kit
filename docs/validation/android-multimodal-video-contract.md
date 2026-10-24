# Android 类型化多模态视频契约

2026-10-24，从foundation `05c67d2`的固定Apple短视频native检查点继续。本包实现Android request/client契约对等；Android生产多模态后端仍未提供。仅本地提交，作者自审，独立review pending；roadmap仍5/12、P9 active。

## 实现与边界

[设计](../superpowers/specs/2026-10-24-android-multimodal-video-contract-design.md)、[逐Task计划](../superpowers/plans/2026-10-24-android-multimodal-video-contract.md)。设计`333e414`，SDK/API及新测试`bd52496`，callback测试修正`f2f7bd1`，native compile consumer `2164ebd`。新增`MultimodalVideoRequest(videoFile:File,prompt,maxOutputTokens=128)`，只校验prompt非空/budget正值，不读文件。新增可选`MultimodalVideoGenerationBackend`与client视频overload；与图片共用同一Operation、worker、callerJob、update过滤、admission/cancel/drain/join/close/query屏障。legacy backend在availability前明确拒绝video，无fallback；总体availability不代表video支持。保留原图片API/Android API26及既有依赖。

不新增Android推理backend、解码/模型、KMP/Demo输入、picker或设备验收。示例仍是compile/link consumer，显式unavailable stub；两个suspend probe只编译不执行，不读媒体、不启动generation。

## 实际验证

- API缺失真实RED：`task-1-red.log` exit1，缺MultimodalVideoRequest/VideoGenerationBackend。首12新项GREEN；追加callback错误测试后，真实35项/1failure：`assertSame`不适用于coroutines异常恢复副本。该失败后误提交`bd52496`，不是通过检查点。固定缓存coroutines1.10.2的StackTraceRecovery源码确认tryCopyException；仅改测试断言type/message及drain/reentry，`f2f7bd1`后最终 **35/35、0failures/errors/skips**（video13、原client12、原Androidbridge10），保留全部失败日志。
- 新测试覆盖无I/O值检查、显式/未知/不可用/legacy、image/video双向busy、availability取消、late success/error/drainerror取消优先、drain前不重入、ordinary/drain错误及suppression、callback序列/旧操作隔离、同步callback取消/抛错、close admission/video drain/query join/取消waiter、预取消caller与非取消drain。fake backend仅模拟外部工作，真实client在测。
- offline模块test/lint与`:kmp-binding:compileAndroidMain`成功；完整既有Android verify task列表 **725 tasks、452项JUnit/45 suites、0failures/errors/skips**（11个test模块，数量来自XML，不能混同task数）；完整tools **133/133**，显式Ruby3.4.11。原有Kotlin/Gradle warnings保留。
- native consumer assemble/lint+Core/Multimodal local Maven publish **366 tasks成功**。实际release AAR含video request/interface；javap确认两种generate overload；sourceJar两SDK文件与仓库逐字节一致。POM仅Core、Kotlin2.4、既有coroutines1.10.2。
- fresh独立Gradle consumer有自己的settings，只消费本地Maven Core+Multimodal坐标，没有SDK project依赖。首次AGP9.1 bundledKotlin2.2无法读2.4metadata，exit1保留；对齐仓库既有Kotlin2.4 plugin classpath后，实际assemble/lint/dependency graph **45 tasks成功**，不关闭metadata检查、不下载/改SDK版本。仅证明固定Kotlin2.4 consumer；旧Kotlin兼容性未通过。

AAR SHA256=`da2d6db7c459711598a93612f0e348f97f9cd27b7fa2424cb5c62108988a9d14`；sourceJar=`1c2f39342a67f2fbd91d6ca316a5527d81fa4ca83d911f1388b57c3a818c6445`。产物/完整inventory/独立consumer在`artifacts/verification/android-multimodal-video-contract-20261024/`。raw logs/argv/XML汇总/checks/作者复审保留于`.superpowers/sdd/2026-10-24-android-multimodal-video-contract/`。没有远程Maven发布、设备推理或自然质量结论。

## 同步与后续

用户授权先同步既有HEAD `05c67d2a85c050d70ad51e255d0ed8deabc1d833`后继续。仓库sync-remotes脚本两次均在极狐预检失败（SSL_ERROR_SYSCALL/connection reset），后续独立refresh及HTTP1.1诊断仍失败，尚未进入任何push。GitHub fresh ls-remote仍`eb1287dc7ecac4c3ce17f3503292d1967b8852a9`；极狐fresh SHA无法读取，**双远端同步pending**，未宣称相等。初始logs在上包runtime ledger的`remote-sync-20261024*.log`，后续在本包`sync-jihu-recheck.log`；未改代理/路由/凭据/aTrust。新Android包仅本地commit，初始同步授权不自动扩为继续实现后的新包push。

保护文件2hash/state/index保持原样，没有并发代码变化。下一独立包先设计KMP typed video bridge/opaque input生命周期，再单独Demo/picker。独立review pending，无APPROVE；保留Apple短实际样本拒绝、原上游8→7失败、binary archive exit65、自然视频/时序质量、Metal/GPU/energy、Android真后端、最低系统、物理触摸/真实picker、旧Video UI与P6实录等缺口。
