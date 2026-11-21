# W3 Mac Qwen 两阶段 Runner 独立代码与证据审查

**审查范围与约束（已遵守）**：仅使用所提供上下文，无工具，未运行/未观察原生或设备行为；未将此前 429 失败的"原生子代理审查"当作证据；未改动模型/参考/归一化/阈值；保留全部既有限制。本任务为独立复核（来源候选由另一早期助手交付），仅输出审查结论。

## 已检工件与哈希

| 工件 | 声明/记录 SHA256 | 备注 |
|---|---|---|
| `host_qwen_asr.py` | `809fe34f…dad1f07`（文件头 + 报告 runnerSHA256） | 一致 ✓ 审查代码=产出证据代码 |
| `check_two_pass.py` | `a74866fd…43b67c8` | fake 运行时，realNativeCalls:0 |
| `w3-qwen-independent-protocol-20261110.md` | `14f89a3c…388266b` | 协议说明 |
| `w3-qwen-independent-observed-20261110.md` | `00c6e1ee…2333158` | 通过报告 |
| `independent-assessment.json` | runner `809fe34f…` / protocol `dd4d8bc7…` / raw `8bc5f6b2…` | 与报告互证 ✓ |
| 原始完整报告 | protocol `dd4d8bc7…` / runner `809fe34f…` / raw `8bc5f6b2…` / ru_maxrss 2828959744 | 与评估互证 ✓ |
| `independent-scores.json` | 内部聚合 | 归一化 NFC/casefold/保留标点 |

**交叉校验（已验证）**：
- runner 哈希 == 审查文件哈希 → 证据由被审代码产出 ✓
- `117 = 52×2 + 13×1` ✓（52 语音各 2 次、13 非语音各 1 次；max 130 在全部未拒时成立）
- 门限聚合重算一致：中文 CER 42/590=7.12%、近场英文 WER 32/375=8.53%、远场英文 WER 62/226=27.43% ✓
- 逐行 referenceUnits 求和匹配聚合（中文 char 590、近场 word 375、远场 word 226）✓

## 结论总览

**未发现 Critical 或 Important 级别缺陷。** 代码逻辑、计数边界、预检绑定、异常/部分证据完整性与评分/门限声明在所提供的证据内自洽，且所有既定限制均被声明保留。

## 排名发现

### Critical
无。

### Important
无缺陷。下列为**已确认保留的证据边界**（非缺陷）：
- `check_two_pass.py` 为 fake 运行时逻辑测试（receipt `realNativeCalls:0`），仅验证计数/异常处理逻辑；本审查中的原生计数取自真实运行报告，未与 fake 测试混淆。该边界在 receipt 中已正确标注。

### Minor（含假设性，已标注）

- **M1（假设性）— 纯空白准入不触发严格空门**：`host_qwen_asr.py:283` `if admit_text == ''`。若原生 auto 准入返回如 `" "` 的空白串，将绕过"严格空准入"而进入强制语言二次解码，可能在非语音片段上产出文本。真实数据中 13 条挑战均返回真正空串，故未触发；协议明确 9 条会议间隙未经人工听审，该残余风险已被承认。触发条件：真实音频 auto 解码恰得空白串。**假设/未验证**。

- **M2（真实代码观察，低严重度）— 内存擦除范围有限**：`host_qwen_asr.py:309` `samples.fill(0)` 仅清零 `astype(...)` 生成的本地 float 副本；原始 `pcm` 字节仍保留在 `loaded` 列表整个运行期。相对"擦除音频"意图存在轻微卫生缺口，无正确性影响。

- **M3（设计澄清，非缺陷）— 计数语义**：`nativeDecodeCalls` 在 `accept_waveform` 之后、`decode_stream` 之前递增（`host_qwen_asr.py:272/294/323`）。因此 create/setter/accept 抛错不计入，decode 抛错仍计入——与文档"dispatch 尝试"语义及 `check_two_pass` 故障断言一致。观察计数无偏差。

- **M4（证据缺口，Minor）— 协议 JSON 未直接提供**：报告记录的 `protocolSHA256 dd4d8bc7…` 对应实际运行用协议 JSON，本审查未获该 JSON 文件，故哈希仅被记录、未被独立重算。runner 与 rawReport 哈希与被审文件/展示报告自洽。

- **M5（澄清）— 中文词级指标无意义但门限正确**：`independent-scores.json` 中 mandarin 词级 rate 73.33%（空格切分对中文无效），但门限与报告均正确采用 CER（字符级 7.12%）。无虚报，仅提示勿误读 scores 文件词级字段。

## 重点验证项

- **原生派发计数器**：三处递增均置于真实派发边界（accept 成功后、decode 前）；故障用例下计数与 `actual_calls` 一致；decode 抛错仍计数。✓
- **预检绑定实际模型/PCM**：`verify_model`（行 150）+ `verify_cases`（行 167）在 `from_qwen3_asr` 加载前完成大小+SHA 绑定；`safe_join` 逐组件拒绝 symlink/越界（行 44–70）。✓
- **严格空 auto 准入 → 新流强制语言全量 PCM**：准入空则 `admissionRejected=true` 且只 1 次 decode（行 283–288）；非空则新建流并重新 `accept_waveform` 同一 `samples`（行 291–293），全量 PCM 复用（`check_two_pass` `snapshots[0] is snapshots[1]` 印证）。13 条非语音全部准入空，规避了强制语言静音幻觉。✓
- **异常/部分证据完整性**：每状态变更即 `write_report`（truncate+fsync，行 36–41）；终态在 `gc.collect()` 之后于 finally 写入（行 350–365）；`cleanup.note` 明确"Python 引用清理/GC 不冒充原生析构确认"，与限制一致。✓
- **计数 vs 预期调用**：117 = 52×2 + 13×1，与协议"满足准入预期则 117"一致。✓
- **评分/门限声明与限制**：CER/WER 门限正确套用聚合；归一化保留标点/数字/简繁、NFC/casefold；`qualityVerdict:"unverified"`、`productionReady:false`；`androidAcceptance:false`、`productionPromoted:false`。✓

## 已保留限制（逐项确认）

- 此前原生审查 429 无结果 → 不视为证据；本审查仅基于所供代码/报告。
- 仅审查所供代码与报告，不推断未观察原生/设备行为。
- 未做模型调参、未改参考/归一化/阈值。
- Independent 52 语音 + 13 挑战、实际 117 decode（已核对）。
- 原 scorer NFC/casefold 保留标点（已核对 scores JSON）。
- 9 条会议间隙仅"无定时标注事件"，未经人工听审，不得称已证纯噪声（报告已声明）。
- Mac RSS ≈2.83 GB（ru_maxrss 2,828,959,744 B ≈2.64 GiB）> Android 1.5 GiB，不能证明 Android 目标；报告明确不作为系统服务/GPU/能耗/预算批准（已声明）。
- Python del/gc 非 C++ 析构 ack（报告 `cleanup.note` 已声明）。
- 强制语言静音幻觉、Silero/auto 失败在上游仍另验，未反推全部模型缺陷（协议/报告已声明）。

## 阻塞与范围变更

- **无阻塞**。此前原生 429 审查缺失不影响本任务（本任务明确只审所供代码/报告）。
- **无范围变更**：所有聚焦项均可在所供材料内完成核验。
- **无配额重试**：本会话无工具，无配额操作。

**最终判定**：在所供证据范围内，runner 代码与工程确认自洽、计数/门限/评分声明正确，所有限定声明保留，无 Critical/Important 缺陷；仅有前述 Minor/假设性观察，不改变"独立确认通过、Android 与发布尚待验收"的结论。