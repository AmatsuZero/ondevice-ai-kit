# W3 iOS 独立关闭清单与材料合同草案 — 2026-11-16

状态：`DRAFT`。本文记录已验收的离线输入绑定和仍为草案的运行合同；不将资料准备视为质量或发布验收。W3、R4、R5、P4 均 `open`。不运行设备、下载、录音或抓取；普通可逆本地准备继续，不新增审批门。已授权 Android 单片诊断合同与 guard/preflight 独立，不受新产品预算前置。

边界来源：[iOS 交接](../HANDOFF-W3-IOS-STATUS-2026-11-16.md)、[当前范围](w3-closure-scope-20261115.md)、[质量证据](w3-quality-evidence-20261110.json)与[持续观测](w8-apple-asr-sustained-20261110.md)。Apple 源绑定已接受并提交于 `2fa129b`，同步 `b262aeb`；当前检视 HEAD `b262aebd13339f2f871c3ef7525ce5f244273b77` 不是历史 App 身份。按 `docs/agent-pitfalls.md` 摘录：`Package.swift` 非 compiled-input；四份 SwiftFileList raw 不保留、不重建、不推断缺失原因；SwiftCompile records 不改写为 swift-frontend 全命令；并发状态变化不推断操作者。

## 一、候选身份与采用边界

- 历史 App 为 `636a6eb` Debug/generic arm64/minOS 18 unsigned combined App；已证明的范围为 10 个 Core/Speech/AppleSpeech Swift 源文件、16 个选定输入、14 个实际 Swift 编译输入的绑定，不证明整个 App 等价，也不否认既有 SDK Swift 接入。
- 当前 HEAD 仅作检视身份。final shipping candidate 接受证据缺失；未固定同一最终候选前，不得把历史 probe、历史 combined App 或当前 HEAD 写成生产采用。
- [候选采用 JSON](w3-ios-candidate-adoption-20261116.json) 已存在，但仅作未采用的历史 review reference；其字节保留，`finalProductionCandidate = null`。该记录原字节保留，继续仅作为未采用的历史 review reference；`finalProductionCandidate = null`、`historicalReferenceAdoptedAsFinalProductionCandidate = false`。
- 后续候选需记录 source overlay/config/bundle/App+Framework digests/backend/locales/distribution boundary；历史 10/16/14 证据只引用为 selected inputs only。

## 二、R4：FIXED 65 输入 prospective Apple cross-backend FILE 评估

离线绑定已完成；使用既有 FIXED 65 输入，不新选样、不新处理波形、不授权识别。

| cohort | cases | PCM duration |
| --- | ---: | ---: |
| Mandarin | 20 | 151.08s |
| English near | 20 | 147.70s |
| AMI English far | 12 | 71.322s |
| annotation-negative | 9 | 45s |
| all-zero | 4 | 26s |
| total | 65 | 441.102s |

原 host manifest SHA `e5144d7901d4e5f7729b13e11ac7a92e3586efe2d6f1591352b3aae4cccce1e6`；Android envelope SHA `8d1cfd7465e70f413b741701250d61c31170b25a6978a256a4df0349efa0910f`。整份 manifest 字节不同；六个有序字段 `(id,samples,locale,cohort,sha256,reference)` 65/65 匹配。63 个唯一 PCM digest，因为两个 zero signal 跨 locale 重复。本轮新增 816 项离线检查；审计见 [输入绑定收据](w3-apple-65-input-binding-20261116.json)。

限制：这 65 输入不是新的 globally blind independent heldout；Mac Qwen 输出已暴露。它排除旧 Apple 中文 ID `1906/2006/1883` 与旧 AMI meeting `ES2002a`，但比较只限于 `w3-quality-evidence-20261110.json` 中报告，不是全运行历史穷举。FLEURS pinned revision `70bb2e84b976b7e960aa89f1c648e09c59f894dd`。AMI source range payloads 21/21 exact；部分整源 WAV 有意保留为 partial。无网络审计不建立新的远程 provenance 或 license adjudication。4 个 all-zero 为 synthetic；9 个 annotation-negative 是未听审 annotation gaps，不是已证明 human-audited nonspeech。

执行状态与质量指标正交。结果须显式分列 recognized-nonempty、completed-empty、typed-error、timeout/cancel、not-run；Speech 52 必须先 successful/nonempty，才可考虑完整质量 pass。指标为 Mandarin CER、English-near WER、English-far WER；normalize 限 NFC+casefold+whitespace，保留 punctuation/digits/Traditional-Simplified differences；scorer SHA `d398bb35438b9f4eb399fc404e02c0c2a7184a7cea7c7a6d32e4a321f8e2b39e`。typed 1110 保持 error；不得为 error 造 empty hypothesis，也不得把 conditional scores 当 full batch pass。Apple negative behavior 按原始 typed form 报告，不移植 Qwen strict-empty admission 或 two-pass semantics。

产品阈值未获批。原 Qwen draft limits：CER 0.20 / near WER 0.20 / far WER 0.35、52 successful，仅作为未来 prospectively frozen Apple contract 的 DISCUSSION REFERENCE；不是 Apple approved gates，也不是对历史 Apple 16/92 或 AMI 2/3 加 typed 1110 的裁定。这项产品裁定不阻挡已授权的 Android 单片诊断。

## 三、R5/W8 Apple 资源协议

`recognitionDuration/PCMduration` 与 `wholeRequestWall/PCMduration` 分开定义。wholeRequestWall 从 request admission 开始，早于 input loading/prepare，到 terminal callback 结束；lifecycle close/drain 单独跟踪。prospective manifest 须含 exact sampling windows、warmup vs measured、per-cycle reuse topology、missing samples、sampling gaps、close/drain、timeouts/failures、PCMduration、terminalStatus 与 disposition；missing 写 missing，不写 0，不写 pass。Apple 不是 Qwen two-pass。

历史资源证据：626.211590s 含 593.928s paced idle；898 个 App RSS 样本 max 113082368 B；recognition median 189.320854ms，p95 nearest-rank 434.797708ms。它不表示 E2E，也不是 systemSpeech memory、true peak、GPU、energy、thermal。批准预算仍未设置；不复制 Android 1 GiB/1.5 GiB budgets，不要求重跑 20 cycles/40 calls 历史批次。

## 四、P4 接受字段

P4 需对同一候选具备 actual source overlay/config/bundle/App+Framework digests/backend/locales/distribution boundary，以及 independent reviewer、material digest、date、acceptance。适用 P1/P2/P3 按 Apple 范围裁剪；Android AAR/NDK 不是自动 Apple 要求。若 shipping combined App，则 MLX/OpenCV/shared 进入适用审查。P4 不是自身前置；本文的资料准备不关闭发布门。

## 五、有序下一步

1. Offline binding completed：FIXED 65 输入与 816 项离线检查已形成审计链接。
2. 下一项可离线推进的交付是固定 Apple 运行合同与候选身份，明确输入封装、顺序、每项终态、资源采样和恢复；产品阈值须在用于通过裁定的评测前确定。
3. 仅执行已授权的 fresh work；当前不运行设备、不下载、不抓取；普通可逆本地准备继续自动推进。
4. Review same candidate results，并保持 Android 诊断合同与 guard 独立，不新增审批 barrier。

范围结论：本稿记录离线核验与合同准备，不改变历史失败，不关闭任何 gate；W3、R4、R5、P1–P4 均保持 `open`。