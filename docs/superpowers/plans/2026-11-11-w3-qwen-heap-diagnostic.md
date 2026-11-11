# W3 Qwen heap diagnostic Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. External free CBC writes isolated candidates; the primary agent reviews, integrates exact bytes and runs acceptance.

**Goal:** 为一个尚未执行的公开文件建立heap边界与RSS可对齐证据，不改变识别算法。

**Architecture:** 独立wrapper复用arena已验模块与政策。先通过host故障注入、真实构建/包核对及外部复核，再冻结新设备合同。

**Tech Stack:** Kotlin/JVM、现有AndroidSDK/JDK21、固定Sherpa2.0.1AAR、父端Python标准库。

**Spec:** [固定合同](../specs/2026-11-11-w3-qwen-heap-diagnostic.md)

当前执行：已验实现本地提交`7f1d2f9`，单片run冻结120摘要/守卫通过。fresh preflight发现ADB清单为空、指定TECNO `device not found`，零安装/解码，run未消耗；不把连接不可用写成锁屏或授权错误。[当前准备与阻塞](../../validation/w3-qwen-heap-ready-20261111.md)。此前免费hy3原字节集成后wrapper33组274、记录器13组100、真实Android75任务/lint0e7w/65PCM四库实包、修后父103及tools691通过；双复核问题已分别处理。W3open。

此前父端准备为路由19/报告22/原回归61共102项，见[历史准备与失败记录](../../validation/w3-qwen-heap-routing-preparation-20261111.md)。当前候选/实包与双复核已具备；Task2仍需完整冻结和设备验收。构建前重核真实host及测试/源码摘要，设备前要求完整冻结与双复核；`heapBoundaryLabels`由实际源码复核写入合同。RSS保留设备uptime读取区间与一秒容差，精确时钟映射仍未证明；政策收据绑定报告SHA，min available从原始trace重算。

## Global Constraints

仅`fleurs-en_us-1776`/index25；最多1构造/2stream/2decode。原模型、AAR、两阶段/English及arena-off政策保持。1.5GiB/512MiB/120s不变；不开麦克风/AiAi/下载、不改生产或旧冻结文件。native child旧429不重试。

## Review Focus

- 所选row曾实际运行：父核旧报告planned且无stage，否则拒绝新合同。
- heap API/时钟/负值/上限异常：下一native被阻断，nullable数据与实际错误可被序列化。
- 清理区采样失败：真正release与teardown照常尝试，真实成功标记保持，无重复释放。
- 同时native失败与heap失败：primary、release和heap错误分别保留。
- report初始/中间/终态持久化失败：原fail-closed行为及错误保留，不能伪completed。

### Task 1: 独立候选与host验收

**Files:** Create `probes/android-qwen-heap/QwenHeapInstrumentation.kt` (external candidate only), `probes/android-qwen-heap/check_wrapper.py` and parent fault checks.

**Interfaces:** 新class `io.ondeviceai.acceptance.qwenfile.QwenHeapInstrumentation`；复用原`TwoStageCaseRunner`/`QwenFileReport`/`QwenArenaPolicy`。JSON字段以spec为准。

- [x] 建父host adapter：保留原wrapper24组的适用故障，完整65校验但成功rows1/stream2；增加heap故障与清理真值检查，最终33组274断言。
- [x] 缺候选先RED；外部免费CBC在真实OS沙盒/context不可写边界内交付记录器与精确接线补丁，最终仅集成一个wrapper文件。
- [x] 审阅终态/raw/实际文件，原字节集成；真实Kotlin+原core/inputs/report/policy运行全部检查，原RED与定点外部订正保留。
- [x] 单独提交已验实现与host证据`7f1d2f9`；保留旧冻结108项及其他dirty。

### Task 2: 实包、复核、冻结及唯一诊断

**Files:** 新`probes/android-qwen-heap/prepare_build.py`、`build_acceptance.py`、`device_acceptance.py`、`verify_report.py`，仅父验收路由；复用原guard与恢复流程。

**Interfaces:** 独立app/test alias与report目录见spec；冻结新sourceFingerprint与精确文件集合。父读heapEvents/错误/一row，独立核stage计数与保留政策，不推断native内部生效。

- [x] 离线build/lint，核实际manifest/DEX/四库/65assets/签名，旧APK不替换；父负向检查缺hash/错input/超调用/失败heap门。真实目录布局暴露的父路由错误已RED→GREEN。
- [x] 外部免费助手独立只读复核source/供应实包收据/父合同，漏manifest已RED→GREEN；事件数量/标签由独立源码证据解决，不重试native429。两位复核未亲自运行构建/设备。
- [ ] 冻结实际摘要后fresh设备前置；锁屏时保留pending，不自动解锁。唯一新run有阶段边界/堆数据方可判本诊断完成，资源越线/采样失败按约停止且不重放。
- [ ] 同run双读、模型/App后核及精确alias/staging恢复；结果/局限/失败同步两端issue/wiki，W3全量门仍单独裁定。

Task2 checkpoint：120文件摘要已冻结，`verify_frozen_contract()`通过；fresh只读前置停在ADB设备不存在。无prepared/run-started/collection，未产生新heap/RSS实测；整个Task2尚未完成。连接恢复后只继续同合同，不重构候选或重放旧批。
