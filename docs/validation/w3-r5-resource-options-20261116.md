# W3/R5 资源选项与预算草案（2026-11-16 修订候选）

**状态/边界**：本文是 supplied-context-only advisor 的 hy3 文档候选，仅做证据/边界对账与后续产品取舍输入；未执行、未读/重算原始报告、未跑设备/工具/MCP、未连接设备、未检查 live 状态。父代理如做原始重算，可在单独 receipt 中补充；本文不声称父代理执行或批准。无麦克风、AiAi、模型/ASR-runtime 下载、旧 USB 恢复循环；不改生产代码，不扩大或消费 `w3-qwen-terminal-20261114-01`。不关闭 R4/R5/W3/W8/P1-P4，不请求接受失败或关闭门禁，不新增架构、auto-only 捷径、MemPattern savings 或 native 语言输出结论。数值目标均为产品 DRAFT/NOT APPROVED；主代理只组织证据，不批准预算。TMPDIR/XDG 目录如存在由父代理创建，本文不作 filesystem sandbox claim。

**范围变更/阻塞**：删除首稿 A/B 两档案和固定短文件 ms 门槛，改为单一顺序 FILE 工程延迟档案；不变更 R4 full65 质量语料。阻塞项：future reuse/warmup policy 未固定，target-device 总/可用 RAM 与 UX 要求未验证，完整全量 run/原始 receipt 与授权 runner 未由本文提供；因此没有预算批准或门禁关闭。

## 证据对账（仅文档/证据层）

| 来源 | 已有可用事实 | 必须保留的边界 |
| --- | --- | --- |
| `docs/validation/w8-apple-asr-sustained-20261110.md` | iPhone17,2；20 周期/40 次 measured recognition；626.211590 s 含 593.928 s paced idle、10.036 s activeWork；recognition median 189.321 ms（非 nearest-rank p50）、p95 434.798 ms；measured RSS max 113082368 B。 | App PID RSS 含 UI/probe，缺系统 Speech-service 内存与 idle 连续采样；recognition duration 不是用户端到端延迟；600 s paced 不是连续解码；sampled RSS 不是 true peak/GPU/能耗/thermal/零泄漏证明；原 paced 请求 warm/cold 未知，不代表 R4 质量；不重跑。 |
| `docs/validation/w8-sherpa-sustained-20261109.md` | TECNO KM9；20 fresh backends/40 measured calls；600.503 s 含 506.508 s pacing idle；nearest-rank p50/p95 1782.006/1818.381 ms；VmRSS max 457220096 B。 | 冻结 Whisper/合成音频源，不是实验 Qwen 源，也不是代表性 R4 质量；request wall 含模型验证、VAD、native release/postconditions，非 decode-only；sampled RSS 非 true peak/GPU/能耗/thermal/零泄漏证明；不声称原请求全 warm 或全 cold；不重跑。 |
| `docs/validation/w3-qwen-arena-observed-20261111.md` | Qwen arena-off 仅 24/65 完成，第 25 项在固定 1.5GiB 实验安全停点之上中断；sampled max 1637896192 B；中文 20/20 CER 6.78%，近场英文 4/20 子集 WER 13.43%；完成片两阶段 RTF 1.040806–1.678660。 | 代表性完整 run 的 p50/p95、完整真机质量/资源验收不能编造；窗口按最近持久化 activeCaseIndex 可滞后真实阶段；force-stop 不是显式 release；两阶段 RTF 含持久化/门检查，非 native-only；未执行项不评分。 |
| `docs/validation/w3-qwen-cost-options-20261111.md` | 统一 auto-only 在开发集中文和 AMI 有已知退步；AMI C 段 known failure；源键 `EnableMemPattern=0` 存在不等于 native 消费或节省。 | 不引入 auto-only 省钱捷径；无 native detected-language、文字脚本选择器或 MemPattern savings；不新增算法/实现。 |
| `docs/roadmap/W3-speech.md`, `docs/superpowers/specs/2026-10-15-ondevice-ai-sdk-design.md`, `docs/superpowers/specs/2026-10-27-p11-grouped-benchmark-design.md`, `docs/validation/w8-resource-dispositions-20261109.md`, `docs/agent-pitfalls.md` | W3 仍 open；规范记录设备/系统/模型/冷启动/推理/峰值内存/实际后端，但未给获批逐能力数值生产阈值；benchmark 结果不能直接跨设备承诺。 | 观察验收不等于预算通过；不能用当前结果倒推门槛；构建或工具/provider green 不等于数据/验收；保留 raw/errors，不自动 quota retry 或付费 fallback。 |

## 可测、待恢复、现有证据未记录

| 层次 | 内容 |
| --- | --- |
| 既有同边界内可复核 | 原始记录存在且哈希绑定时，可复核 cycle/request 计数、completed 状态、采样数、sampled RSS max、close/final RSS、recognition/request wall 的源文件定义统计（Apple median；Sherpa nearest-rank p50/p95；不得改名）及按 completed cohort 的质量。父代理重算只进单独 receipt。 |
| 传输/授权恢复后待测 | 另案固定 future fixed candidate、device、input identity、backend reuse/warmup policy 后，在授权 runner 与 instrumentation channel 真实可用时测完整 Qwen 两阶段 case wall、RSS 采样、close/release/teardown 与原范围质量；本文不批准执行协议。 |
| 现有证据未记录/未批准预算 | Qwen 代表性完整 p50/p95、完整 R4 真机质量、生产资源通过、user 端到端 timing、service RSS、true peak、GPU、能耗、thermal、Speech-service 内存、连续解码稳定性、零泄漏、native detected-language、MemPattern savings 均缺证据或无获批 per-workload budget。它们不是被证明 intrinsically unmeasurable；未来 feasibility/method 另案，不虚构 instrument。Missing 保持 missing。 |

状态记录必须把 null/unknown 与 0 分开，把 failure 与 not executed 分开；负 sentinel、all-zero power rows 和空字段不能转成通过或零值。

## 单一工程档案（产品 DRAFT/NOT APPROVED）

适用范围：仅未来固定 Qwen 两阶段 FILE 候选、TECNO KM9/API35 的顺序 FILE 工程延迟档案；对当前源不构成验收。planner-provided 设计假设（非经验结论）：decoded PCM 1–15 s、sequential two-stage calls、backend reuse topology 由单独未来协议固定。1–15 s envelope 只服务本 prospective latency profile，不变更 R4 原 full65 quality corpus，也不是其他时长输入豁免；每个原 R4 输入仍需适用质量/执行结论。

计时名不得混用：recognizer entry→DTO return 是 duration，不是 RTF denominator，也不等同于本文 full two-stage case wall；后者含两阶段、stream release、persistence 和 guard checks。

| 指标 | DRAFT/NOT APPROVED 值 | 冻结边界 |
| --- | --- | --- |
| 全两阶段 case RTF p95 | ≤ 1.0，按 Mandarin、near English、far English speech cohort 分别计算 | numerator = case wall 秒数，含两阶段、stream release、persistence、guard checks；denominator = exact decoded samples / 16000 Hz；匹配 frozen case boundaries；不得 pure-decode 或 single-request relabeling。 |
| App sampled RSS max | ≤ 1073741824 B（1GiB，binary） | optional large backend 的 provisional product allocation cap；process sampled RSS 含 UI/probe；不是 installed RAM 比例、headroom proof、true peak、system-service/GPU/energy budget。 |

Rationale：1GiB 是供产品讨论的显式 process allocation ceiling；p95 RTF≤1 表示第 95 百分位处理不慢于音频时长，不保证每个请求。两者都是 prospective engineering targets，故意未按当前结果校准，不代表 universal usability 或 memory safety。批准前仍需产品验证 target-device 总/可用 RAM 与 user-experience 要求。

p95 nearest-rank = sorted[ceil(0.95*n)-1]。仅在 full planned run 有 valid terminal result 且全部 planned inputs accounted for 后，从 complete success-eligible requests 按 cohort 计算；先报告 attempted/completed/failed/unattempted 计数和全部 failures。Missing/full-run failures 即使 completed-subset percentile 好看也 block budget acceptance。不得 pooling languages、service modes 或 sources。Challenge/silence cases 仍是 R4 适用执行/质量要求，未被移除。

Cold/init、first request per process/backend、post-close checkpoints 与 warm steady requests 必须分层；future reuse/warmup policy 固定后才能测量，绝不 retro-label existing first requests as warm。已知 load 9.075710385 s 仅作 descriptive，不是门槛。不发明 cold/init/reclamation/thermal/energy/GPU 数值预算；这些及 service RSS、true peak、E2E timing 均列为开放方法/预算项。

**既有实验安全约束（EXISTING APPROVED EXPERIMENTAL CONTRACT，不变）**：max 1610612736 B/1.5GiB。它不是 DRAFT、不是 product budget，与上述草案分离，不放宽、替换或重定义。未消费的 `w3-qwen-terminal-20261114-01` 仍是既有 lower-scope one-file diagnostic，不是 a65-file benchmark。

## 交付边界

- 现在可接受：仅限 supplied-context 记录/边界对账；无 raw 独立裁定、无设备动作，不扩大消费或发布。
- 需产品取舍：DRAFT RTF/RSS 是否符合 UX 与质量优先级；主代理不能代产品批准。
- 传输/授权恢复后可测：固定候选/设备/输入/复用协议下的完整 case RTF、RSS、release/teardown、质量 cohort；具体执行仍需另立合同。
- 当前缺证：Qwen 完整 p50/p95、完整 R4 质量、生产预算结论、service RSS、true peak、GPU/能耗/thermal、Speech-service 内存、零泄漏；R4/R5/W3/W8/P1-P4 全部保持 open。
