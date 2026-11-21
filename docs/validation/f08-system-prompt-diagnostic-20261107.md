# F08 系统消息单变量诊断（2026-11-07）

macOS 单系统消息诊断（共四次生成）已由父代理完成并验收；本文只记录既成事实，非生产变更或质量放行。证据以 `artifacts/verification/f08-system-prompt-diagnostic-20261107/parent-validation.json` 与 `runs/system-message-birds-pair0-20261107-a1/report.json`（SHA-256 `59adb9482aa19c5b77c10bfa734e080a9440723b894947b9d00c022e95d868a6`）为准，结果值均为父代理记录、非本文自测。

## 设计

- 固定模型 `mlx-community/SmolVLM2-500M-Video-Instruct-mlx`（revision/runtime 固定）、夹具清单 SHA-256 `666fbfebdc999aefa5b9db944a090024b9478c13107685af621fb842babe1694`、prompt、`temperature 0` / `maxTokens 32`。
- 唯一变量为系统消息：`original`（描述型） vs `neutral`（`You are a helpful assistant.`）。
- 每个夹具复用同一份准备好的像素/THW 输入（shape `[8,512,512,3]`；motion 像素哈希 `b91aca…`、frozen `761c44…`）；重构的 original 全量 token 与实际 adapter token 相等；token 数 original 720 / neutral 702。
- 四次生成各自新建 cache/iterator，producer 均 join；两处快照目录 closed 且确认消失。

## 实际四行结果

| 夹具 | original 原始输出 | neutral 原始输出 |
| --- | --- | --- |
| motion-0-2 | ` MOVING.` | ` MOVING.` |
| frozen-0-2 | ` MOVING.` | ` MOVING.` |

四行原始输出均为 ` MOVING.`（含前导空格与句点），不按标点归一化，不据此判定为正式/格式通过。进程 5.211s 完成全部四次生成（非基准成绩）；最终构建 12.373s；5 项不触发生成的负向校验（manifest 缺失、manifest 不匹配、已存在输出目录、符号链接目标、非法 runId）全部通过（零行/零生成）。

## 完整性

- 21 份模型/源码/夹具哈希、6878 个上游文件、9 个受保护路径保持不变。
- 运行时推理进程禁网；最终构建在普通获批 host 上以 pinned 本地缓存 + offline 标志完成（`compile.networkDeniedByOSProfile=false`，不宣称最终构建由 OS 强制禁网）。
- 首次额外叠加网络 `sandbox-exec` 的构建因嵌套 SwiftPM `sandbox_apply` 返回 EPERM 被阻断，该失败保留，未关闭全局工具沙盒。

## 编译与告警

- 首次 Swift 编译在 `ModelContainer` 闭包报类型错误（105.773s）；helper 改为先解包可选 `[THW]` 的 `frames` 再映射、返回 `Sendable` 的 `FixtureOutcome`，并在 neutral 失败时保留已完成的 original 行；最终构建（12.373s）通过。
- 仍有 1 类 `init(cString:)` 弃用告警（`SystemPromptProbe.swift:747`），不是零告警。
- helper 候选由 agy 产出（记录 496.999s 初稿 / 305.681s 更正），`project.yml` 经 cbc 三轮配置修订（53/34/14s）；全程无付费模型调用。

## 边界

- 仅能说明：在 birds pair-0 上未观察到对系统消息这一变量的输出敏感性。
- 不能证明所有 prompt 效应均不存在、预处理正确，或模型本身不具备相应能力；` MOVING.` 的标点不归一化为正式通过。
- 正式 birds/waterfall 0/3 结论维持不变。
- 无生产改动、无质量放行、无设备验收、无新增基准样本；无设备写入、下载或上传。
