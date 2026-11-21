# W3 TECNO 原始 heap 单片观察（2026-11-12）

## 主要结论

在已连接 TECNO KM9（Android 15/API 35 arm64）上收集了新的 W3 物理堆观测数据，推理执行已完成。但宿主端 `terminalObservationGap` 非 null，冻结父评估器据此拒绝诊断验收（diagnosticAccepted/executionAccepted/engineeringQualityAccepted/productionReady 均为 false）。资源观测验收不成立。

## 已完成推理（确凿计数）

- 原始 run `w3-qwen-heap-20261111-01` 由 UNCONSUMED 转 CONSUMED，未重放；固定清单索引 25 `fleurs-en_us-1776`（76800 samples/4.8s），PCM SHA256 `2821c39358fc8049953945d8042d1833d86cb2245ca468cf5afc31ee1fe1a438`。原 arena 行 25 此前未尝试，其余 64 输入已验证未解码。
- 设备终态：`terminal=true, status=completed`；精确 1 构造器/2 流/2 解码；流释放、识别器释放、Activity 拆除均成功；主/堆/持久错误 null；18 堆事件；0 麦克风、0 网络。
- 报告双读精确 SHA256 `e80a4678468e736fdf20049fa2f5e4cee49a5a32c437da83b6fcb1db570b8a0e`；6/6 后运行模型哈希匹配。冻结 120 文件匹配诊断契约 SHA256 `111cb4a5705604fb23110187382490a46a41e7aa17903c9c3cee3f2ec84a7bfd`；源指纹 `a79adaa583e3707be20055e2fc6dd01f6ca58a4da5da686cf21196666d0ce7e1`；无生产变更。

## 资源与堆边界

| 指标 | 观测值 | 冻结停止边界 |
|---|---|---|
| 采样最高 RSS | 1445167104 B（1.345916748 GiB） | 大于 1.5 GiB 停止 |
| 最小观察到的可用内存 | 2157887488 B | 小于 512 MiB 停止 |
| 主机诊断窗口 | 25.022890666 s | 总窗大于 240 s 停止；单片/加载窗大于 120 s 停止 |

这些是采样与边界观察，不能建立 true peak、精确跨时钟映射、ORT 内部分配器保留根因或跨片泄漏；heap 三项读取非原子。

41 次 RSS 样本，`stopReason=null`；load 9.570995693 s / case 9.314667385 s / 识别器释放 0.253385154 s。堆分配边界（字节）：初始 6921168、构造前 21337424、构造返回 1081139296、自动解码后 1081647728、强制解码后 1081754704、用例返回 1081444768、识别器释放 27918000、Activity 拆除 28093888。

## 被拒验收

父评估器 20 检查 19 true，仅 `typedResourceObservations=false`（因 gap）。`countsAreExactTerminalEvidence=true`；解码 2/流 2/一致行 1；`heapBoundaryEvidence=true`。但 `terminalObservationGap` 非 null：`/proc/<run PID>/status` ENOENT，仪器完成时最后观测报告仍 running 且与最终报告不同。不声称连续观测至拆除，不证明间隙无害；计数/报告证据完整，资源观测验收未过。

## 策略-stat 与清理

首个策略-stat 因远程引号失败（collection 已存）；原 collect stdout/stderr 未单独重定向，仅有工具结果观察；独立只读引号包装恢复相同 UID/设备/inode 并双读 20B 策略。首续作 AttributeError（缺 EVIDENCE）失败于 ADB 调用前，已保留；v2 用精确路径成功；均未再解码，`nativePolicyEffectVerified=false` 仍成立。已删两任务别名与 `/data/local/tmp/<run>`，普通 App 恢复；文档更新前 94 个预存路径哈希不变。

## 范围与开放项

W3/R4/R5 OPEN；R1-R3 已定。完整 TECNO65 质量/资源未接受；旧 arena run24/65 超 1.5GiB 不可重放；模拟器65 与 KV288 已完成非替代物理证据。运行时到精确 AAR/递归二进制组合、生产通知、独立发布评审、真实峰值/GPU/能耗、生产预算仍 OPEN。

## 证据链接

- 原始本地产物（本机保留）：`artifacts/verification/w3-qwen-heap-20261111/`
- [历史就绪](w3-qwen-heap-ready-20261111.md)（已非当前 UNCONSUMED）

父端 22 项定向回归通过；本次只提交结果记录，没有修改冻结源码、APK、模型或验收门。机器摘要及原件摘要见 [JSON](w3-qwen-heap-physical-observed-20261112.json)。