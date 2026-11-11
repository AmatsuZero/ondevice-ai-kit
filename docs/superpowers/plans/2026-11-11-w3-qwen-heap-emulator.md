# W3 Qwen heap 模拟器补充诊断

目标：用户离开机器时，继续一段公开文件的 Android/JNI 功能及 heap 边界诊断。使用已有镜像、原已验 APK 和六模型；不消耗 TECNO 合同，不关闭 W3/R4/R5。

## 约束与合同

基础规格为 `../specs/2026-11-11-w3-qwen-heap-diagnostic.md`，输入和模型参数全部保持。唯一补充 run 为 `w3-qwen-heap-emulator-20261111-01`，证据根为 `artifacts/verification/w3-qwen-heap-emulator-20261111/`。最多一构造、两 stream、两 decode；RSS 1.5GiB、available 512MiB、单片120s、整批240s保持。模拟器4GiB guest RAM不能证明手机资源门通过。

新 AVD `W3_Qwen_Heap_20261111`，serial `emulator-5578`，已有 Android36/default/arm64-v8a revision2，4GiB RAM、2CPU、硬件加速，独立 AVD/user/emulator home。关闭音频、摄像头、快照，不下载镜像/模型/runtime binary，不开启麦克风/AiAi，不修改普通 AVD/App/模型。身份必须同时满足精确 serial、qemu=1、AVD名/API/ABI。

父验收脚本可由主代理机械适配并测试；实现候选仍由外部免费 CBC 负责，不修改 Kotlin。独立免费 hy3 只读复核新适配及边界，旧 native429不重试。

## Task 1: 准备独立合同与适配

先验证原120冻结及未消耗状态，复制原源码/实包/收据；run-plan仅变更runId。适配仅改证据根、runId、serial、身份与恢复Launcher，复用原heap采样及收政策逻辑。新合同冻结原合同摘要、适配、全部复制文件、规格和本计划；逐次ADB前核验。保存精确替换收据。

验收：错serial/qemu/AVD/API/ABI、额外控制台行、重复报告/运行和超资源均拒绝，原APK/source及物理合同不变。Expected: 全部父检查通过；独立hy3复核无未处理阻断后才安装/运行模型。

## Task 2: 一次模拟器诊断与恢复

先检查已有镜像/硬件加速/空闲端口，再创建隔离AVD并等待boot。启动后核身份/无活动instrumentation/current-user0/亮屏；只在该无安全锁AVD设置保持亮屏。fresh preflight→prepare，逐块/整文件摘要、同UID/proc与包摘要通过后唯一run。失败只收取同run，不自动重试。Expected: 原始报告双读、六模型前后SHA、heap及RSS阶段分析和明确pass/fail。

收取政策时沿用同UID/device/inode及有界21B检查。只卸本次alias和manifest绑定stage，恢复Launcher，关闭精确本任务AVD。保留原始报告/日志/配置；不因非终态声称精确计数或实际release。

## Task 3: 记录与同步

根据实际结果记录模拟器诊断范围及剩余真机/质量/发布门，核原120冻结和物理run未消耗、已有dirty保护。精确提交本任务文件；已授权的issue/wiki用新marker同步并逐字回读。Expected: 本地证据和双端记录一致，不提升W3/R4/R5验收状态。

## 接口及裁定

Task1输出的新冻结合同是Task2前置；Task2原始双读及恢复是Task3事实来源。原物理合同在所有任务前后均保持。模拟器只补充功能/阶段证据；用户已明确提出此路线，继续执行无需重复授权。

## Review Focus

检查精确模拟器身份是否在任何变更前验证、物理合同和旧批是否完全保持、原预算/一次运行/非终态计数/政策路径身份是否不被放宽；报告保留模拟器及资源观测局限。
