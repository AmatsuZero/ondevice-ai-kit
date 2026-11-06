# P11 精确候选生命周期证据更新

本轮将已经完成的两项真机验收和F09工具修复纳入统一索引，不重复运行设备或重建SDK。
基础阶段仍10/12，P4/P11 open，发布not-ready。

- Android固定ASR：精确 `e0438c030a987ceeff9f9207c2475684749e4d8b` SDK、`b90e7830…64d8d` 主APK，TECNO/API35，PID20393。私有终态确认13项native取消、关闭、重入断言；麦克风启动0次。
- Android固定SR：同一主APK，PID21382，native run `0ac8554d-ec79-4047-8786-56870a1112dc`。6项native断言通过，原输入64×48、输出256×192；保留/丢弃音轨和音视频PTS已核对。修正校验器的错误尺寸后重验同一报告及媒体，原拒绝收据保留，没有重跑推理。
- 普通App恢复：原main/test APK、runner、25份私有摘要、权限/TTS及四份SR文件核对；5份暂存APK和113块精确清理。恢复收据不作为推理通过证明。
- F09 consumer：manifest只读一次，冻结字节供验证、拷贝、Swift摘要和receipt共用；native exclusive rename防止发布覆盖既有目录。macOS实际回归通过，Linux运行未验证。详见[修复记录](f09-consumer-generation-20261106.md)。

上述两项固定输入真机验收不提供自然质量、持续资源、能耗、最低系统或独立发布批准。
四项记录器自检不计推理；完整tools suite412项通过，25项协议检查单列。未改生产模型/锁文件。

本机快照为 `artifacts/verification/p11-readiness-20261106-candidate-lifecycle-final/summary.json`，
其前序是 `p11-readiness-20261106-android-sr-waterfall/summary.json`。
新快照保留历史原始记录，只追加当前报告/恢复/修复来源和来源摘要变更说明。
`benchmark/p11/grouped-report-manifest.json` 指向该快照；分组报告仍为原8组、原始样本与p50/p95不变。
生命周期报告不加入延迟/资源benchmark，新增benchmark样本0、质量帧对0、基础阶段关闭0。
九项能力原有未验证门槛全部保留；最低系统设备按用户要求延期，F10仍为 `THRESHOLD_UNVERIFIED`。

快照和原始设备文件保留在本机忽略目录，不保证随Git克隆分发。新快照的 `refresh.py` 记录生成方法，
`verification.json` 记录来源摘要、既有分组不变及保护文件检查。

2026-11-06 F09补充审查：cbc hy3只读核查4份validator/ZIP源码，251.47秒返回3条候选意见。
主代理核实schema v2按平台构建Metal、共享校验器先验证实际文件摘要；构建收据不认证不可信发布者。
另一条要求static framework二进制可执行，实际有效ar归档为0644。10项现有static回归及额外篡改检查通过，
没有确认新增阻断或修改生产代码。这不等于whole-branch、许可或发布批准。
依据为既有[分发契约](apple-mlxvlm-distribution.md#2026-11-05-manifest-driven-static-artifacts-and-zip-consumers)，
本机审查原文/逐条处置见 `.omx/artifacts/cbc-f09-packaging-supplement-20261106/parent-assessment.json`。

agy的独立7文件snapshot/packager/overlay/license补充审查也已完成：首轮认证失败原样保留，
用户说明网络恢复并要求一次重试后，`gemini-3.8-flash-medium`在348.12秒返回完整SUCCESS和空findings。
7份当前文件摘要与冻结packet一致。它列出的validator、inspector、完整overlay manifest等缺失上下文仍保留；
只计限定源码审查，不计whole-branch、native runtime、许可或发布批准。
本机证据 `.omx/artifacts/agy-f09-packaging-supplement-retry1-20261106/parent-assessment.json`。
