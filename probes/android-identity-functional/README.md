# Android 身份功能冒烟探针（functional-upright-png-v1）

通用、调用方批准的、正立 PNG **功能-only** 探针。仅验证端到端功能管线，不做生物特征评估、准确率打分或阈值判定。

## 调用参数（严格约束，取自 `IdentityFunctionalInstrumentation.kt`）
- `runId`：正则 `[A-Za-z0-9-]{1,40}`
- `expectedSdkCommit`：40 位十六进制 `[0-9a-f]{40}`；必须与 `BuildConfig.ODAI_SDK_COMMIT` 相等，否则立即失败。
- `manifestSHA256`：64 位十六进制 `[0-9a-f]{64}`

## 私有输入与报告路径
- 私有输入目录：`targetContext.filesDir/odai-functional-inputs/<runId>/`，含 `manifest.json` 与 `input-0000.png … input-00NN.png`。
- 报告目录：`targetContext.filesDir/odai-functional-reports/<runId>.json`。
- 用户照片从不属于受提交源码；本探针只消费调用方放置的派生 PNG 输入，不访问系统相册。

## 严格清单字段（从源码读取，不得扩充）
`manifest.json` 顶层键必须恰为 `schemaVersion`、`purpose`、`entries`：
- `schemaVersion`：整数 `1`
- `purpose`：字符串 `local-functional-test-only`
- `entries`：长度 `1..500` 的数组；每条目键恰为 `id`、`filename`、`sha256`、`bytes`、`width`、`height`，且 `id==i`、`filename=="input-%04d.png"`、`sha256` 为 64 位 hex、`bytes` 1..10485760、`width/height` 16..2048 且乘积 ≤ 4194304。

清单 SHA 与每个输入 SHA 均经 `digest` 核对；任一不符即失败关闭。

## 模型前置与未验证失败关闭
- 模型须位于 `targetContext.filesDir/identity-validation/models`，经 `verifyModels` 逐项核对文件名/字节/SHA 后由 `ModelStore.install` 装入，状态须为 `INSTALLED`。
- 任何 `check` 失败即 `recordFailure` 并令报告 `status=failed`：失败关闭（fail-closed）。
- 验证阶段使用 `OpenCvIdentityModel.unverifiedPolicy(VERIFICATION)`；实际错误码固定为 `THRESHOLD_UNVERIFIED`，`thresholdPolicyStatus=UNVERIFIED`。不做阈值判定、准确率或身份结论。

## APK 安装说明
精确 APK 布置由**每次运行的保留脚本**管理（普通 App/test/runner、模型、SR 文件、权限/TTS 的备份与恢复）。本探针不提供可复用的 `adb install` 命令，请勿臆造。

## 清理（cleanup）
- 模型：`ModelStore.remove` 至 `ABSENT`，置 `modelLeaseReleased=true`。
- 密钥：`keyProvider.clear()`，置 `keysCleared=true`。`keysCleared` **仅表示提供方底层数组/映射被清零并清空，不构成全部副本安全擦除（secure erasure）声明**。
- 存储：`IdentityStore.close`、`consent.close`、撤销授权并等待 `DeletionStatus.PURGED`。
- 工作目录：`workDirectory()`（cacheDir 下 `odai-functional-work-<runId>`）整树删除，置 `workDirectoryRemoved=true`。
- `KEEP_SCREEN_ON`：恢复调用前原始标志，置 `keepScreenOnRestored=true`。

终态 `terminal=true` 后由 `finish` 上报 `status` 与 `reportPersisted`。
