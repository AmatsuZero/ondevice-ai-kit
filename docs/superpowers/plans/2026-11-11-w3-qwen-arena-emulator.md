# W3 Qwen Android 模拟器全量文件对照

## 目标与范围

完成原固定65公开文件在 Android/JNI 模拟器上的全量对照，补充中文/英文/真实会议远场/非语音观测。原TECNO arena批次已消耗且失败，原heap真机run未消耗；均保持不变。本项是新环境的明确全量合同，不是原run续跑、独立新数据集或真机验收替代。

复用已验 `android-qwen-arena` 原APK/sourceFingerprint/四arm64库/六模型/65PCM，CPU2、128mel、seed42、temperature1e-6、topP0.8、maxTotalLen512/maxNewTokens128、greedy、auto准入→请求语言两阶段、arena-off及memory pattern默认全部保持。原顺序/评分/质量门不变，无音频增益/截断/选样。不修改Kotlin、生产、原APK或冻结文件，不下载镜像/模型/runtime binary，不开启麦克风/AiAi。

独立run `w3-qwen-arena-emulator-20261111-01`，证据根 `artifacts/verification/w3-qwen-arena-emulator-20261111/`。最多1constructor/130stream/130decode，语音52项均准入且13挑战均拒绝时预计117decode；实际以终态为准。RSS1.5GiB/available512MiB/每片120s/整批7920s、观测失败即止，不自动重试或提高预算。重复旧文件只用于固定跨环境对照，不称独立held-out确认。

隔离AVD `W3_Qwen_Arena_20261111`，serial `emulator-5580`，已有Android36/default/arm64-v8a revision2，RAM4096MiB/2CPU/硬件加速，独立AVD/user/emulator目录，音频/摄像头/快照关闭。精确serial、model/API/ABI、qemu=1、AVD控制台名（允许CRLF逐行）、user0必须全部通过，再做任务设备变更；不动普通Medium_Phone。

## Task 1: 适配、复核、冻结

主代理只写验收脚本：机械复用原FILE父执行流程，改OUT/runId/serial/模拟器identity/Launcher恢复；保持65/130原循环与预算、一次reservation、报告双读、包和模型前后摘要。政策收取复用已验heap父端的same UID/device/inode和有界21B规则，shell tokens预先引用（已有实测恢复），不复用旧arena的路径字符串误判。

先运行9错身份拒绝/预算边界/原65顺序/原APK和源绑定检查。Expected: 全部通过。免费hy3只读独立复核新适配/合同，无阻断后冻结原108+120、原报告/恢复及新副本/父工具/计划/复核；任何ADB验收操作前复核冻结。旧native429不重试，实现如有需要仍交外部免费CBC，不由主代理修改生产或Kotlin。

## Task 2: 唯一全量运行与恢复

已有镜像/空间/硬件加速/精确空闲端口通过后创建任务AVD并boot。fresh preflight→prepare→全SHA→唯一run→同run双读/六模型后核/政策有界读取→评分。预期可能completed或合同停止；两者均保留原始材料，不重放失败片段。结束只卸任务alias/manifest绑定stage、回Launcher、关闭精确AVD；精确删除停止的任务虚拟磁盘，保留报告/源码/配置/日志。

Expected: 新环境实际覆盖、计数、中文CER/英文及AMI WER、13挑战空结果和错误、RTF/RSS边界清晰；不把模拟器指标作为TECNO资源门、跨文件泄漏/无泄漏或policy native effect证明。

## Task 3: 双端对照、记录与同步

用固定scorer复算全部成功终态行，保留失败/planned行；与Mac原52/13和TECNO已有24行按相同ID/波形/归一规则逐项对齐。不得把不同后端/平台差异唯一归因runtime或事后修改算法。写MD/机器收据、更新W3/交接，精确提交本任务增量；已授权两端issue/wiki新marker同步并回读。

Expected: 全量模拟器结果或明确停止证据已记录并同步，原真机heaprun保持未消耗；W3/R4/R5中仍缺的实体质量/资源/发布门明确保持open。

## 接口与复核重点

Task1的独立新合同→Task2唯一run，Task2原始报告/恢复→Task3事实。执行前后核原arena108/heap120及旧报告不变，保护已有dirty内容。复核重点是防误发真机、原65/130不缩减、原门不放宽、路径identity及shell quoting、完整质量与模拟器局限不混淆。
