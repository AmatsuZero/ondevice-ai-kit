# Android 身份功能冒烟验收（2026-11-07）

本文仅依据 `facts.json` 与 `IdentityFunctionalInstrumentation.kt` 源码，记录一次本地功能-only 验收。完整证据汇总见 `artifacts/verification/android-identity-functional-final-20261107/summary.json`。

探针文档：[探针用法](../../probes/android-identity-functional/README.md)

## 输入与采样
- 12 个哈希排序、等距抽取的派生输入，来自既有两个目录；不做身份/会话推断，不作代表性（representativeness）声明。
- 不枚举私有输入，也不复制源码哈希。

## 几何可用性 ≠ 准确率
`geometricallyUsable = (faceCount == 1 && score == 1.0)` 仅表示几何可用性，不构成识别准确率、真伪或身份判定。本次 `recognitionAccuracyClaim=false`。

## 12 × 128 维归一化嵌入 / 注册 / 拒绝 / 删除
- 嵌入：维度 128，且归一化 `abs(||v||² - 1.0) <= 1e-4`。
- 事实计数全部相等：`inputCount=12`、`processedCount=12`、`usableCount=12`、`enrolledCount=12`、`unverifiedRefusalCount=12`、`deletedCount=12`。

## 实际 THRESHOLD_UNVERIFIED 保留
未验证策略返回错误码 `THRESHOLD_UNVERIFIED`；`thresholdPolicyStatus=UNVERIFIED`、`thresholdChanged=false`。本次不做任何阈值变更。

## 缺失输入：预期终态失败
负向用例（缺失输入）确认 `expected-failure-confirmed`：`durableTerminalFailure=true`、`noFalseSuccess=true`、`noPrivateInputs=true`、自有工作目录已移除。

## 主机 255 vs 同运行持久终态
主进程 `hostExitCode=255` 实际输出为空（EMPTY）；同 `runId` 的私有持久报告已完成且 `terminal=true`（非非终态），不得视作推理失败；**未发起第二次推理（无重跑）**。

## 测试构建与恢复
- 测试-only 构建：397 个 Gradle task，exitCode 0（约 18.562s），非生产发布。
- 恢复：普通 App/test/runner、25 项私有模型哈希、4 份 SR 文件、权限与 TTS 默认值均已匹配/恢复；自有探针工作与 APK 暂存缺席；`releaseApproval=false`。
- 清理：设备私有输入已移除、本地派生输入已移除。
- 原始 226 张 JPEG 未被改动（`originalJPEGsUnchanged=226`）。

## 不覆盖的生产边界
`calibrationAllowed=false`、`PADClaim=false`、`recognitionAccuracyClaim=false`、`releaseApproval=false`、`benchmarkSamplesAdded=0`、`thresholdChanged=false`。本次不含生产校准、活体（PAD）、阈值、基准或发布晋级。

## 范围之外
- F09（consumer 工程 notice / App 材料）为独立事项，不在本探针范围。
- 基础 roadmap 仍为 10/12，P4 与 P11 保持 open/not-ready（`p11Status=open/not-ready`），本次功能探针不关闭这些门槛。
