# W3：语音识别质量与剩余场景验收

更新：2026-11-11。状态：**open；用户已明确要求继续改进，R4/R5 不再等待用户选择接受现状**。当前 TECNO 的 R1–R3 系统 ASR 已按用户接受的已知限制完成范围裁定；固定前缀、AMI 六个端/片段及两端持续观测已归档，质量与生产预算未通过。

最新[arena-off 真机结果](../validation/w3-qwen-arena-observed-20261111.md)：唯一新 run 完成24/65项，中文20/20非空、CER40/590=6.78%；近场英文仅4/20、子集WER9/67=13.43%，AMI与挑战全部未执行。第25项期间采样RSS1637896192B=1.525410GiB超过固定1.5GiB，父按约停止；413样本/258.937s，已完成片段两阶段RTF1.04–1.68。非终态精确最终调用数未知，无整recognizer释放/destroy ack。报告双读、六模型后核、精确alias/staging清理与普通App前台恢复通过。framework与shell路径字面值误判的原评分保留，同run只读device/inode及有界20B补核通过；108冻结文件/APK未改，native内部effect仍unverified。批次已消耗，不重放/提高预算；中文指标有进展，完整执行、Android全量质量/成本和发布仍未过。下方“等待解锁/零安装”均为此前准备记录。

新增[运行时材料](../validation/w3-runtime-materials-20261111.md)：50文件含20许可/声明、26构建源，46来源GitBlob/hash、39链接与50压缩成员通过；实际AAR21文件/16so四ABI已枚举。递归静态组成、source-to-prebuilt、最终生产通知与发布复审仍缺。[成本复算](../validation/w3-qwen-cost-options-20261111.md)拒绝统一auto-only捷径：已知开发远场WER17/27，原两阶段13/27；后验独立集较好aggregate不足以取消此失败。零新推理/设备动作，先追固定runtime的分配/生命周期与发布出处，不因源键存在即开新run。

最新设备诊断准备（2026-11-11）：父工具独立复核发现已修正，22检查+原8父/31评分通过；arena单变量新合同已冻结108项摘要。TECNO fresh preflight Awake=false/unlocked=false、新alias不存在，零安装/构造/解码，等待本次解锁。原FILE批保持失败并已消耗；新run只请求关闭arena，1.5GiB/512MiB/120s不变。[最新准备与边界](../validation/w3-qwen-arena-ready-20261111.md)。下方“尚未冻结/父复核中”是此前准备记录。

内存变体实包准备完成（2026-11-11）：外部 arena-off 外壳原样集成，30组4113断言、政策重验13组41、离线构建27.24s/lint0error7warning及实包16类/65PCM/四arm64摘要/零权限通过；独立源码复核无Critical/Important代码缺陷。父端独立诊断工具17检查通过、复核进行中；新设备合同尚未冻结/安装/运行，native消费、省内存和Android质量仍未验证。[实包](../validation/w3-qwen-arena-wrapper-20261111.md)／[复核](../validation/w3-qwen-arena-wrapper-review-20261111.md)／[拟定合同](../validation/w3-qwen-arena-diagnostic-protocol-20261111.md)。下方“外壳仍生成中”是此前准备记录。

内存改进准备（2026-11-11）：固定源码17文件/18片段检查及实际AAR DTO4检查支持`cpu:config`下单独关闭CPU arena，尚未证明native消费或省内存。外部政策模块修正有界读取后13组41断言/集成重验及AndroidAPI34classpath通过，本地523ea07/6a9618e；双端#15/Wiki已发布回读，GH Wiki5e5dea4。独立Android外壳仍在生成；无新APK/安装/模型调用，原FILE批已消耗不重放，1.5GiB门保持。[可行性](../validation/w3-qwen-arena-feasibility-20261111.md)／[政策模块](../validation/w3-qwen-arena-policy-20261111.md)／[外壳计划](../superpowers/plans/2026-11-11-w3-qwen-arena-wrapper.md)。

最新[Android FILE真机结果](../validation/w3-qwen-android-file-observed-20261111.md)：用户解锁后唯一65文件合同实际执行，
构造1/返回9.019537s；首片处理期间RSS1703575552B=1.586578GiB，超过固定1.5GiB，父进程按约停止。
38样本/17.742s、零完整片段、余64planned；非终态最后持久化decode0不能解释为实际零调用，精确最终次数未知。
同run双读/六模型后核/普通App及模型恢复通过；未重试、未提高门槛。当前候选Android资源门未过，质量仍unverified。
后续先查既有runtime/model的内存控制可行性，不重放已消耗run、不再请用户接受R4/R5现状。

新增[Android FILE实包检查](../validation/w3-qwen-android-file-package-20261111.md)：外部候选原样集成，
真实Kotlin外壳24组/364断言、支持模块与原core通过；离线构建25.378s、lint0错误/7警告，
实际APK13类/65PCM/零权限/四原生库摘要及同debug证书已核。[独立外壳/包复核](../validation/w3-qwen-android-file-wrapper-review-20261111.md)完成，
无Critical/Important；一处线程说明按官方文档订正。之后实体FILE执行按上述内存停止结果裁定，不能倒写质量通过。
输入模块/Activity/报告已本地提交2b64ba4；原漏DTO、异常未序列化/失败片段绑定丢失的RED均保留。

此前[Android两阶段core验收](../validation/w3-qwen-android-file-core-20261111.md)及[父验收限定复核](../validation/w3-qwen-android-file-parent-review-20261111.md)：
外部候选已原样集成，真实Kotlin42组/264断言、父7测试及原scorer31测试通过；本地提交f124ab2/8b0a969。
65公开输入/六模型已重核；清单、原reference和1.5GiB/512MiB/每片120s停止线保持。
core/父验收检查点已发布两端#15/Wiki并回读，源码未push，见[收据](../validation/w3-qwen-android-file-core-parent-sync-20261111.json)。
本项不代表手机质量/完整成本通过；stage错误及释放后前台门回归失败和原修正均保留。

新增[TECNO真机load-only验收](../validation/w3-qwen-physical-load-accepted-20261111.md)：用户解锁后沿用冻结run，
构造1/成功9.381619386s、释放1/成功0.272898154s；49样本maxRSS1.310589GiB，未触发1.5GiB停止门。
33父检查/同run双读/六模型复核/普通App恢复通过；零stream/decode/麦克风/AiAi。
已卸本次alias并清staging，勿重跑。下一项是Android两阶段文件识别质量与完整成本；本项不关闭W3。
下文11月10日“TECNO待解锁/零加载”仅为当时历史状态。

最新改进：Qwen3-ASR0.6B int8 的“自动语言判准入→请求语言转写整片”已通过Mac开发筛查及独立确认。
独立52/52语音成功：中文CER42/590=7.12%，近场英文WER32/375=8.53%，AMI远场WER62/226=27.43%；
13/13静音/会议间隙挑战为空，实际117次decode。原强制语言静音幻觉、Silero v5漏C段及纯auto语言误判均保留，
没有降低通过线。当前允许进入Android候选设计；Mac采样RSS约2.83GB不证明手机1.5GiB目标，
模型再分发通知链和独立审查仍未通过。生产源码/普通App/模型未替换；详见[独立结果](../validation/w3-qwen-independent-observed-20261110.md)。

后续已完成[Mac代码/证据限定复核](../validation/w3-qwen-host-review-accepted-20261110.md)，未发现Critical/Important，
原native429无结果仍保留；Android和发布不在该复核范围。六份实际运行文件已与供应仓库size/SHA全匹配，
供应方声明Apache-2.0，最终许可/归属交付物仍未验收，见[供应链核对](../validation/w3-qwen-supply-chain-observed-20261110.md)。
Android先冻结[load-only合同](../validation/w3-qwen-android-load-protocol-20261110.md)，保持1.5GiB门；
首多文件helper因upstream empty_model_response零交付而拒收；拆分交付后core17组/63断言、wrapper12组/44断言、
父guard11检查及Android构建/lint/实包已通过（lint7warning/0error）。11月10日TECNO安全锁屏，
当时新alias未安装、零构造/解码；用户不在机器旁，因此先执行独立模拟器补充。
同APK在Android36 ARM64临时AVD完成一次构造/释放，采样最大RSS1.204823GiB，28父检查/双读/恢复通过。
模拟器已关闭，未替换TECNO/API35证据；11月11日真机加载子门随后通过，完整两阶段质量/成本仍未验收，
见[准备收据](../validation/w3-qwen-android-probe-ready-20261110.md)和[模拟器结果](../validation/w3-qwen-emulator-load-accepted-20261110.md)。

已另行验收[模型许可与归属材料包](../validation/w3-qwen-model-materials-20261110.md)：24文件、
六运行文件重核、21来源SHA和归属文档8链接通过；既有Apache-2.0全文与供应声明分别标明来源。
不含模型/runtime二进制，不修改冻结APK；最终生产包通知保留、运行时许可和发布复审仍另验。
材料包本项没有设备动作；模拟器和随后真机的独立load-only结果分别保留，不重复执行。

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
- [ ] R4：Qwen两阶段Mac独立确认已过，原tiny/Apple失败保留；Android arena-off已完成中文20/20且CER6.78%，但整批在24/65后内存停止，英文仅4项、AMI/挑战未执行，完整质量/成本及再分发/独立复审仍缺。不能拿Mac或中文指标代替全量手机通过。原前缀、AMI六组合、固定增益/no-VAD均已结束；不再重放已消耗诊断，见[手机本批](../validation/w3-qwen-arena-observed-20261111.md)。
- [ ] R5：Apple FILE ASR与Sherpa同来源持续观测均已验收，不再重跑；主代理继续提出可靠可测项、缺测边界和预算建议，需产品取舍时提交具体方案。不能把采样RSS或长达600s的paced批次当预算通过。[Apple本批](../validation/w8-apple-asr-sustained-20261110.md)，整包资源要求继续归W8。

## 责任、风险与交付

主代理负责本包计划、集成、验收和设备恢复；实现按已授权外部助手路线，native 仅可只读复审且最多一个，原 429 不自动重试。执行前读根 `AGENTS.md` 和[踩坑记录](../agent-pitfalls.md)。

每次只领取上面一个检查项。结果写回本文件的进展表，再更新 `docs/ROADMAP.md` 的一行状态；保存原失败、源码/输入/模型摘要和原始输出，检查本次缓存是否可精确清理。受保护文件、原 App/模型不动。

**W3 整包关闭**需上述适用场景和质量要求逐项有证据、未通过项得到原范围内的解决/批准裁定及复审；诊断结束、工具通过或文件生成均不足以关闭。没有获批的新质量门槛时，不自行从当前结果反推“通过”。
