# Android Speech 最终复核（证据记录，2026-11-07）

汇总父级已核验的既有事实，不新增审查、不推断新发现、不替代发布批准。基线 base `71df0fb`（HEAD `71df0fb5f04367e34dfd0acb01d9c946786b72bb`），源未改动（`sourceModified: false`）。

## 范围与六份源码/测试

两份外部 `cbc hy4free` 咨询复核均限定 6 份仓库文件、10 份上下文，源/输入/候选哈希与父级现值一致。

| 仓库路径 | SHA-256（来自 facts） |
| --- | --- |
| `android/speech/src/main/kotlin/io/ondeviceai/speech/SpeechClient.kt` | `48fc19674c5c9e53414f76d449227031239249962754a3101cd2d7064ceaf7e9` |
| `android/speech/src/test/kotlin/io/ondeviceai/speech/SpeechCloseFailureTest.kt` | `fb021fcf076e4448778dd94fd19bcc78195915c73ef83f822a8a596f62ec73ce` |
| `android/backend-system-asr/src/main/kotlin/io/ondeviceai/systemasr/AsrResources.kt` | `a270dc027d4ce6c0d20b40539062be65ced73cefadbcb048e3bd40c66fbb13dc` |
| `android/backend-system-asr/src/test/kotlin/io/ondeviceai/systemasr/AsrResourcesTest.kt` | `9cdf7ece08a40978d58c561dc3423ffbd72e87edfe56d590c6ac51e717998ce1` |
| `android/backend-sherpa-asr/src/main/kotlin/io/ondeviceai/sherpaasr/SherpaSession.kt` | `a0882a5adbafd685127e67dfc091e53c113bec95d8330bafb596511e687165f0` |
| `android/backend-sherpa-asr/src/test/kotlin/io/ondeviceai/sherpaasr/SherpaSessionTest.kt` | `9831364045076b50255789b59e491dcc32d95d1d252cd6578866d723db9fde7e` |

## 额度与回退

`agy`（`gemini-3.8-flash-medium`）在取得结果前返回 429 `RESOURCE_EXHAUSTED`、exit 3、收到文件 0；未重试，仅把约 2026-11-14 08:25:21（Asia/Shanghai）重置记录为估算值、无恢复保证。按用户选择回退到 `cbc hy4free`；`hy3` 亦按用户指令暂停并记录重置时间，未自动付费回退。

## 两份独立 cbc 复核

| 复核 | 耗时 | 完整事件 | 工具调用 | 工具错误 | 退出码 |
| --- | --- | --- | --- | --- | --- |
| lifecycle（`cbc-speech-final-review-20261107`） | 611.550s | 50 | 15 | 0 | 0 |
| defects（`cbc-speech-defects-review-20261107`） | 680.198s | 48 | 13 | 0 | 0 |

两份均：工具严格在给定范围内、上下文哈希未变、父级 source/input/candidate 哈希一致、报告 finding 0、接受新缺陷 0、未重跑测试、未改源码。零新增确认缺陷；不把"零 finding"写成新补丁或整体无缺陷。

## 解释性更正（父评估）

原长评四处解释性说法已在两份 `parent-assessment.json` 中被驳回/更正，此处仅陈述更正结论、不复述被驳回说法：`CancellationException` 在 JVM 属 `Exception`，相应 catch 解释不成立；`NonCancellable` 不强制挂起后端清理完成，仅限定于正常协作取消与可完成的后端拆卸；`SherpaSessionTest` 使用私有 `Decoder` + `CountDownLatch` 的 JVM 测试替身，**不是真实 JNI**；ASR 恢复路径检查 `continuation.isActive`（`AsrResources.kt:89`），所提同步回调后抛错导致双重恢复未证实。原审查文档保持不变。

## 既有测试证据（沿用，不重跑）

沿用既有 102 项合并已执行测试证据（ASR 14 + speech 29 + TTS 3 + Demo 56）；另据 Sherpa 冻结轮次记录，70 项实际执行（14 后端 + 56 启用 Demo）、speech 29 为 up-to-date 缓存。本范围 `newTestsExecuted: 0`、`testsRerun: false`，未新增、未重跑测试，不把修正前失败称为成功。

## 有界冻结复核

两份冻结 summary（`artifacts/verification/speech-cleanup-20261107/summary.json`、`artifacts/verification/sherpa-cleanup-20261107/summary.json`）各含 56 项哈希、其中 53 项一致；仅 3 份可变协调文档 `docs/HANDOFF-2026-11-05-next-session.md`、`docs/agent-pitfalls.md`、`docs/validation/foundation-followups.md` 漂移，源/测试/原始证据未变，9 条受保护路径与 production lock 未变。保留原 `exit 1` 与旧冻结 summary/脚本，不重跑推理；**不得把旧全量 `--verify` 说成当前通过**。

## 门槛状态（不变）

原生双审 / 全分支 gate 与发布批准均不可用（`nativeDualReviewAvailable: false`、`releaseApproval: false`）；roadmap 仍 10/12、P4/P11 open、release not-ready，最低系统版本仅延期。

## 链接与证据

- 跟踪文档（相对 docs/validation）：[foundation-followups.md](foundation-followups.md)、[android-speech-cleanup-20261107.md](android-speech-cleanup-20261107.md)、[android-sherpa-cleanup-20261107.md](android-sherpa-cleanup-20261107.md)、[agent-pitfalls.md](../agent-pitfalls.md)。
- 原始复核产出与父评估（本机保留证据，仓库相对文本，不保证随 Git 分发）：`.omx/artifacts/cbc-speech-final-review-20261107/`（`candidate/REVIEW.md`、`candidate/findings.json`、`candidate/DELIVERY.md`，非任务根目录）、`.omx/artifacts/cbc-speech-final-review-20261107/parent-assessment.json`、`.omx/artifacts/cbc-speech-defects-review-20261107/`（`candidate/REVIEW.md`、`candidate/findings.json`、`candidate/DELIVERY.md`，非任务根目录）、`.omx/artifacts/cbc-speech-defects-review-20261107/parent-assessment.json`。
- 既有验证证据：`artifacts/verification/speech-cleanup-20261107/`、`artifacts/verification/sherpa-cleanup-20261107/`、`artifacts/verification/roadmap-status-audit-20261107/`。
- 关联记录：[f06-transfer-evidence-20261107.md](f06-transfer-evidence-20261107.md)。
