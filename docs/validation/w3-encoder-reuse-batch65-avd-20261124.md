# W3 编码复用候选：Android 37 模拟器 65 条连续验收

## 结论

运行号 `w3-reuse-batch65-avd-20261124-02` 在专用 Android 37 arm64、16 KiB 页、6 GiB AVD 上完成固定 65 条语料，模拟器工程质量门通过。该结果覆盖固定模拟器语料和端上生命周期，不关闭 TECNO 真机质量、真机资源预算、true peak RSS 或生产发布门。

## 运行和构建

- APK、模型、PCM 顺序和候选实现沿用已通过第 25 条的冻结输入。新运行号只用于避开已消费的 `...-01`，不重放旧运行。
- 新测试包在本地 Sherpa Maven 输入和 Gradle 缓存下离线构建，`assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 通过。Lint 保留既有警告，无错误。
- AVD 为 `W3BoundRow25Avd20261123`、序列号 `emulator-5594`、API 37、`arm64-v8a`、16 KiB 页、6 GiB 内存、无窗口、无音频、无快照。

## 端上结果

- 65/65 行按冻结顺序完成，65 次自动语言解码和 52 次指定语言解码，共 117 个 stream、117 次 native decode。
- 65 份 `caseCallLogs` 的索引、长度和 SHA-256 与端上报告一致。65 次自动阶段均记录 `W3CALL conv`、`W3CALL encoder` 和有效帧数。52 次强制语言阶段均记录 `W3REUSE hit` 和相同帧数。
- 第 25 条 `fleurs-en_us-1806` 的自动、强制英语文本均与参考文本逐字一致。新报告与既有 Android 36 基线的 65 条输入、PCM 摘要、阶段文本逐行一致。
- 普通话 CER 为 `40/590 = 6.7797%`，近场英语 WER 为 `37/375 = 9.8667%`，远场英语 WER 为 `64/226 = 28.3186%`。三项均低于固定工程门限 20%、20%、35%。4 条静音和 9 条非语音噪声均严格返回空文本。

## 资源和终态

- 主机 402 次有效 RSS 采样，峰值 `1,458,241,536 B`（约 `1.3581 GiB`），低于 `1.5 GiB` 停止线。最小设备可用内存 `2,315,075,584 B`，高于 `512 MiB` 停止线。首个越线样本为空。
- 主机结果 `exitCode=0`、`stopReason=null`、`failure=null`、`terminalObservationGap=null`、`success=true`，运行耗时 `106.263 s`。
- 端上终态报告 SHA-256 为 `a7a0d313d74573124b6a6ff6ec3fea56a68b2f3553b97358d1e8423f5d6e21a6`。原始报告和第二次读回字节一致。
- ACK 使用 `renameat2-RENAME_NOREPLACE` 发布，ACK 中的报告 SHA、PID `4944` 和启动时钟 `2313` 与终态报告一致。`terminalAckReceived=true`，`INSTRUMENTATION_CODE=-1`。
- 两个专用 APK 均卸载，`restored.json` 表示包集合无变化，AVD `devices-delta.txt` 表示设备集合无增删。

## 退出竞态订正

首轮新运行号 `...-01` 实际完成 65 条并收到 ACK，但主机在 instrumentation 正常退出和最后一次 PID 采样之间记录了 `terminalObservationGap=true`，冻结评分拒绝该轮。该轮原件保留为失败证据，没有重放。

第二轮在发布 ACK 前取得同 PID、同启动时钟的终态样本，并等待 instrumentation 返回后再结束主机观察。这样得到 `terminalObservationGap=null`，没有修改端上终态报告或事后清除缺口。

## 证据位置

- [两轮原始报告、调用日志、采样、主机评分和 Android 36 对照文本](w3-encoder-reuse-batch65-avd-20261124.zip)。ZIP 内含逐文件校验清单，整体 SHA-256 为 `ecfc378fb6b9871ca2f2f97925b0332a0454d408769e2e4cc1f71c16caffba5f`。
- ZIP 中的第二轮目录为 `run-02/`，以下路径以该目录或其 `avd-evidence/phone-attempt/` 子目录为起点。
- 批次评分：`avd-evidence/phone-attempt/batch-score.json`
- 主机监控：`avd-evidence/phone-attempt/host/host-result.json` 和 `host/rss-samples.jsonl`
- 端上报告和双读：`device-report.raw`、`readback-accepted.json`、`readback-*/`
- ACK 和 instrumentation：`ack-published.json`、`finish-verified.json`、`host/instrumentation.log`
- 65 份调用日志：`captured/calls-w3-reuse-batch65-avd-20261124-02-*.stderr`
