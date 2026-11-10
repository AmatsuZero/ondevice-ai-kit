# W3：语音识别质量与剩余场景验收

更新：2026-11-10。状态：**open；用户已明确要求继续改进，R4/R5 不再等待用户选择接受现状**。当前 TECNO 的 R1–R3 系统 ASR 已按用户接受的已知限制完成范围裁定；固定前缀、AMI 六个端/片段及两端持续观测已归档，质量与生产预算未通过。

最新决定及两端在线记录见[结果报告](../reports/w3-speech-status-20261110.md)和[发布回读收据](../validation/w3-issue-wiki-sync-20261110.json)。主代理负责提出可验证的质量改进及资源预算建议，先固定假设、输入、评分和停止条件。新增 AMI 原三片/+20 dB 六臂 VAD 真机对照：原幅度 false/false/true，放大后 true/false/true；按事前规则拒绝固定增益路线，不继续扫参或进入该路线 ASR。零 Whisper/麦克风/下载，已双读及恢复，[结果](../validation/w3-ami-gain-observed-20261110.md)。

随后独立复用既有单行 FILE no-VAD 控制，三段原幅度 AMI 全部返回非空文本，但 WER21/27；D/C 各8/8词错误，B仍5/11。功能信号满足不代表质量通过，不采用生产全局绕过。唯一实际批次已双读及恢复，[结果](../validation/w3-ami-no-vad-observed-20261110.md)。本轮两条前处理假设已结束；下一阶段先评审模型/场景匹配与资源取舍，不继续扫前处理参数，也不把实验失败变成待用户接受现状的阻塞。

## 目标与当前裁定

完成语音识别的质量归因及原场景矩阵，保留真实质量结论。固定前缀诊断已完成，不能据此关闭 W3。完整范围以[R1–R5 矩阵](../validation/w3-remaining-acceptance-cases-20261108.md)及[TECNO 范围裁定](../validation/w3-tecno-system-asr-scope-disposition-20261110.md)为准；资源 R5 的执行和预算归 [W8 资源裁定](../validation/w8-resource-dispositions-20261109.md)。

| 已有进展 | 当前证据与边界 |
| --- | --- |
| 同波形中文双端运行已完成 | Apple CER **16/92**，Android Sherpa **44/92**；两端解码波形逐字节相同，但后端/分段不同，不能唯一归因模型。[矩阵](../validation/w3-remaining-acceptance-cases-20261108.md) L21–L34；[归因记录](../validation/w3-acceptance-reconciliation-20261109.md) L115 起 |
| no-VAD 和 base 候选已经裁定 | no-VAD **53/92**，不是修复；base 因首片退步被拒，不能重新扫模型。[归因记录](../validation/w3-acceptance-reconciliation-20261109.md) L126 起 |
| 英文受控噪声、部分 iPhone 生命周期已有验收 | 保留原范围；不等于真实环境噪声、Android 麦克风全场景已过。[矩阵](../validation/w3-remaining-acceptance-cases-20261108.md) L82–L99 |
| 固定前缀修正及对照已验收 | 主代理一次性本地修正；62 host assertions / build+lint / 97 parent checks 通过。原 crop `也是新天章的开始`，恢复前缀后 `结束也是新天将的开始`。本局部输出改变，**不宣称 CER 改善**；失败首 run 与恢复均保留。[本次验收](../validation/w3-prefix-control-accepted-20261110.md) |
| TECNO 的 R1–R3 已按已知限制完成范围裁定 | 用户明确选择将 TECNO 限制纳入验收。R1 实测仍 FAILED/BACKEND_UNAVAILABLE/MISSING_ASSETS；R2/Android R3 未执行，未补造功能通过。仅当前 TECNO KM9/API35 系统 ASR 余项按限制接受，不再等待解锁或安排重试；iPhone 既有证据及 Sherpa 文件路径保持适用。[裁定](../validation/w3-tecno-system-asr-scope-disposition-20261110.md) |
| 系统资源单次请求已执行，错误15，资源未ready | 首次reservation失败/SDK0已恢复；App进程诊断后hy3单行修正，11路径/31故障断言及fresh build/lint/签名通过。用户解锁后run02唯一SDK调用返回ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS；不是VPN失败证据。报告双读/30父检查及恢复通过，原授权次数已用完，不重试/备用API。[当前记录](../validation/w3-system-resource-provision-observed-20261110.md) |
| AMI 原批失败保留，未执行组合已补齐 | 官方 CC-BY-4.0、原三段12.8s/同波形/reference不变；新单片选择合同仅补原未执行的三个组合。现 Android 1/3 recognized、2 typed no-speech；Apple 2/3 recognized、1 平台1110。跨 run 逐片观测覆盖完成，原批不能倒写通过，质量unverified；别名/暂存/普通App已恢复。[补测与结果](../validation/w3-ami-unattempted-observed-20261110.md) |
| R4 裁定材料已归档 | 8份原始报告SHA复核，既有scorer复算中文及AMI成功行一致，无新识别；`cfd5518`保留公开reference/hypothesis及真实错误。没有批准质量门槛，不能自行收口。[裁定材料](../validation/w3-quality-disposition-ready-20261110.md) |
| Apple FILE ASR 持续观测已验收 | `2cb32d1`：20 fresh cycles/40 measured successes/626.212s，含593.928s paced idle；898 measured RSS samples/max113082368B。428父检查、双读及44原App恢复通过；系统Speech服务内存/true peak/GPU/energy未测，预算仍unverified。[本批验收](../validation/w8-apple-asr-sustained-20261110.md) |

## 已完成：固定 1906 前缀判别对照

问题：VAD 裁掉的 2,432 samples（152 ms）前缀是否改变这个局部输入的识别输出？不以这一个对照宣称整体 CER 改善。

固定输入合同来自[候选复核](../validation/w3-prefix-candidate-contract-review-20261110.md) L12–L73：

- `google/fleurs`，`cmn_hans_cn/test`，revision `70bb2e84b976b7e960aa89f1c648e09c59f894dd`，CC-BY-4.0。
- clip 1906 的第二 slice `83040,166080)`；两个 slice-local crop 为 `2432,36480)` 与 `[0,36480)`，只恢复前缀，共享后缀不变。
- 整片参考文本必须保留引号：`“这并不是告别。这是一个篇章的结束，也是新篇章的开始。”`；UTF-8 SHA-256 为 `6f20dd84a6c686c60507428e037063762c5a15bb7e9e9777c1047049b62a9152`。
- baseline crop SHA-256：`3488fa8276e5fd655b23ebcc99e8d4c5fe49808580d90d9ccdf80731a8d7c5a3`；prefix-restored crop：`8e408ea77635aadf6e77dae482d3e3149a78461de587010e18aefab0547ca7c8`。
- 没有独立时间对齐的 crop reference；两次输出只能用于这个因果对照，不能拿整片 reference 给局部 crop 宣称质量改善。

原拒收文件曾在 `/private/tmp/odai-cbc-w3-vad-prefix-control-20261110-0448/candidate/`，现已不存在；原样 Plan/serializer 提取及失败输出仍在仓库证据目录。修正版两文件在 `artifacts/verification/w3-prefix-control-20261110-local/`。原验收合同在 [CORRECTION-ACCEPTANCE.md（本机留存：`artifacts/verification/w3-prefix-contract-audit-20261110-0720/CORRECTION-ACCEPTANCE.md`）。

以下为原冻结执行合同，已按本次验收履行，不再重新运行：

1. **路线已解决。** 用户在本轮明确批准主代理本地一次性修正两份隔离 Kotlin 文件。没有外传、云端助手调用或 native 429 重试；[07:54 队列（本机留存：`.omx/artifacts/current-helper-queue-20261110-0754.json`） 的待答状态是历史。
2. **只修隔离探针。** 修正合法 imports / `modelDir` 作用域；四个模型文件在 JNI 前真实读取并核 size/SHA；保留 canonical reference；success/failure JSON 严格可解析；释放完成后才写释放状态，失败保留已取得的前一臂证据。不改生产 VAD、模型、依赖或 scorer。
3. **离线验收后再构建。** 实测磁盘 hash 负向、两个 crop/共享后缀、reference hash、两种 JSON 和释放失败路径；核实际文件摘要，构建原隔离 Android 项目。候选交付、构建通过分别记录。
4. **设备前置满足后，只做两次顺序 direct-native decode。** 模型、runtime、locale、后缀完全相同；不重新 VAD、不跑其它 clip、不扫参。当前可用设备通道和恢复方案需执行前重查。
5. **当次裁定并恢复。** 回收同一 run 的原始报告，复核输入/模型/源码绑定及释放，移除本次别名/暂存并返回普通 App；有非终态/传输错误先回读同次结果，不自动重发。

本子项的退出标准：有两臂真实报告与可复核结论，或有明确的失败/阻塞证据；`qualityVerdict` 仍为 `unverified`。输出不变就结束这个假设，不继续扫 pre-roll、prompt 或模型。

当前已达到两臂真实报告退出标准：恢复 152 ms 前缀改变本局部输出，baseline 与既有 SDK slice 相同；不能提升为整体质量结论。后续 metadata 已刷新，按用户新提示执行的 R1 单次系统请求仍为 MISSING_ASSETS；不自动下载或再录。AMI 原批首错误停止的失败保留，新合同仅补原未执行组合，现六个端/片段均有真实观测；不是同批全成功，不换样本或事后改门槛。资源入口调查已确认 AiAi 与 Google TTS 不同，所查正常设置无资源操作，不绕过非导出/disabled 组件。

## W3 剩余完整清单

以下来自[R1–R5 矩阵](../validation/w3-remaining-acceptance-cases-20261108.md)。R1–R3 的勾选表示当前 TECNO 范围裁定完成，**不表示功能实测通过**；历史失败和未执行结果保留：

- [x] R1 范围裁定：当前 TECNO 系统 on-device en-US 识别按用户接受的已知限制收口；实测仍 BACKEND_UNAVAILABLE/MISSING_ASSETS，未通过。无新的下载或录音授权，不再安排重试。
- [x] R2 范围裁定：当前 TECNO 系统麦克风背景语音因资源前置未满足而未执行，按已知限制接受；没有用合成噪声文件代替通过。
- [x] R3 范围裁定：当前 TECNO 系统麦克风中断/取消/重入余项因资源前置未满足而未执行，按已知限制接受；已验 iPhone 生命周期保留原范围，不重复录制。
- [ ] R4：单一前缀判别已完成；AMI 原批失败保留，跨 run 六个端/片段观测已补齐，但 Android两片no-speech、Apple一片1110及中文质量差距仍在。新增六臂 VAD 对照已按事前条件拒绝固定增益；当前质量仍unverified，不事后反推通过线，不重放等价识别批次。其他改进须独立冻结有区分力的协议。
- [ ] R5：Apple FILE ASR与Sherpa同来源持续观测均已验收，不再重跑；主代理继续提出可靠可测项、缺测边界和预算建议，需产品取舍时提交具体方案。不能把采样RSS或长达600s的paced批次当预算通过。[Apple本批](../validation/w8-apple-asr-sustained-20261110.md)，整包资源要求继续归W8。

## 责任、风险与交付

主代理负责本包计划、集成、验收和设备恢复；实现按已授权外部助手路线，native 仅可只读复审且最多一个，原 429 不自动重试。执行前读根 `AGENTS.md` 和[踩坑记录](../agent-pitfalls.md)。

每次只领取上面一个检查项。结果写回本文件的进展表，再更新 `docs/ROADMAP.md` 的一行状态；保存原失败、源码/输入/模型摘要和原始输出，检查本次缓存是否可精确清理。受保护文件、原 App/模型不动。

**W3 整包关闭**需上述适用场景和质量要求逐项有证据、未通过项得到原范围内的解决/批准裁定及复审；诊断结束、工具通过或文件生成均不足以关闭。没有获批的新质量门槛时，不自行从当前结果反推“通过”。
