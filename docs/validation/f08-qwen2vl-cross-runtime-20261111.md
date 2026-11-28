# F08 Qwen2-VL 同张量跨实现 Metal 对照

当前：已确认并在隔离iPhone验证一处视频网格路由缺陷；原24题修正后跨进程聚合9/24，连续运行另因严重热状态被保护中止。最新[原视频四题特征对照](f08-qwen2vl-residual-four-20261111.md)另确认Python参考的视觉分段缺陷；隔离控制后四题首token全与iPhone一致，仍三错一对。F08仍open/not-ready。没有生产补丁或新权重下载。Python仅独立模型层参考，移动端仍用Swift/Metal，不引入PyTorch、CPU模型推理或Ollama。

**参考有效性补充：**下文历史Python视频原生/分块结果保持，但mlx-vlm0.3.9的视觉attention并未正确隔离时间段；只校准MRoPE不等于完整官方参考。不能再凭历史某个Python正确答案唯一指认Swift视觉缺陷。最新四题只改Python诊断wrapper分段后logits NRMSE降至0.00213–0.00430，四题A/C/B/A与真机相同。新证据根`artifacts/verification/f08-qwen2vl-residual-four-20261111/`；没有重跑旧端点输入或覆盖旧结果。已确认的Swift网格路由缺陷及其iPhone因果结果仍成立。

**后续预处理/覆盖结果：**[实际官方处理器与20帧对照](f08-qwen2vl-preprocess-coverage-20261111.md)已完成。相同时间宿主帧先按HDTV ICC转sRGB再官方处理，四题完整C/C/C/A（2/4），7915由B→C但margin仅0.015625；不是iPhone修复或总体质量通过。两道计数题提高到20帧仍0/2；剩余解码/resize小差异与量化/固定模型能力未唯一分离。原始BF16权重4.115GiB对照已有具体版本/SHA/预算方案，等待新权重授权，尚未下载。

证据根：`artifacts/verification/f08-qwen2vl-cross-runtime-20261111/`。固定模型 `mlx-community/Qwen2-VL-2B-Instruct-4bit` revision `01af461cdb9574acc09084a0ef94e216e142b085`，权重 SHA256 `3534d3dcfaacd0cd00e935469bc34b6d692b2ba0749c5d9ee422f3f2175cf992`。Swift MLX0.32.3 / mlx-swift-lm6e338e5；Python MLX0.32.3 / mlx-vlm0.3.9 / transformers4.57.6。完整版本、源摘要与环境冻结见 `python-provenance.json`、`python-packages-freeze.txt`。

## 最终24题观察：修正后分批聚合9/24，质量阻塞未解除

原24题回归没有完成连续运行：PID3303在thermal2退出，只留下前12项；随后两个明确预注册的6题冷进程PID3324/3330收齐剩余结果，前12不重放。无模型thermal-only预检PID与时间绑定，两个batch均nominal起步、终态双读一致并正常终止；首批0→0、末批0→1，无守卫触发。**下表为三个进程的24个唯一结果聚合，不是连续24题完成或正式benchmark分数。**

| 类别 | 原固定运行 | 修正配置的分批结果 |
| --- | --- | --- |
| camera_motion | 0/4 | 1/4 |
| memory | 1/4 | 1/4 |
| abstraction | 3/4 | 0/4 |
| counterfactual | 2/4 | 1/4 |
| physics | 2/4 | 2/4 |
| semantics | 4/4 | 4/4 |
| 聚合 | 12/24 | 9/24 |

1项strict改善、4项退步，格式违规仍5项。唯一strict改善是10982的`A.`→`A`，不是新的语义选项修正；退步含3个合法字母选错、1个`A`→`A.`格式违规。原7915整段视频memory仍B错、3094仍C错；不能把端点重复帧诊断7915变C外推原整段视频修好。

父`perception-batches/verify_combined.py`已逐项核24唯一IDs、原题/选项/promptSHA/媒体SHA/实际物理帧时间/ordinal时间/pixel shape/token数/ground truth/trim-only评分，两个batch确切6attempted/0failed/0skipped、有效采样与峰值、14原保护文件及源码冻结保持。与原24运行没有完整prepared pixels逐字节对照；分批/温度条件不同，也不将9/24退步唯一归于某个数学层。原始报告与聚合JSON保持分开，不生成假的单次completed24报告。

新两批采样phys峰值3,001,601,208B/3,018,984,440B；连续运行78s热停止已经是实际资源限制，分批完成不关闭持续热/产品预算门。尾批候选已卸载，普通Combined重开，所有原有App安装元数据保持。最小模型补丁仅保留在隔离证据和可审阅patch文件；未修改共享源码/生产依赖/lock/默认模型。

最终结论：存在真实编码缺陷且一条端点病例修正有效；同时仍有跨实现共同误判、原视频错误、格式错误、以及连续运行热限制。固定Qwen2-VL2B4bit配置目前不适合作为可靠视频理解产品方案。仍不能断言模型理论上无法识别，也尚未拆开剩余错误中的量化、共同预处理与其他运行时数值差异。下一项若继续应是定位残余运行时差异或另行授权更高精度权重对照；不要重复同输入调prompt或把正确路由撤回以追逐分数。

证据：`perception-batches/combined-comparison.json`（24输出/三PID/三报告SHA/逐行差异）、两批终态与无模型温度预检、`perception-regression/partial-guard-disposition.json`及`perception-batches/restoration-receipt.json`。父补充校验最初误用endpoint的`counts`字段，真实Perception合同为`summary.overall`；原KeyError保留，按实际schema补核通过，无新模型重放或报告修改。

## 最新真机因果结果：网格修正使7915由错变对，仍有共同误判

用户确认其他测试结束后，同一签名binary新进程PID3283/runId`EEA7F468-A4C3-4E62-83FC-E5FDB6A3CFDD`完成8/8，双读报告SHA`58d8f322b484c3cc11a50e332cb4131d68fe46ce324145cd80d955074245c43c`一致，父严格评分及`verify_device.py device-run-2`通过。基线/补丁8项tokens、pixels、mask逐字节相同；新导出的实际MRoPE positions8/8逐项匹配官方参考翻译。前文“尚无iPhone因果结果”属于历史。

- 白瓶四单图仍A/B/A/B：前图都对，后图都误判存在。
- 两图仍C/C：7915对，3094错。
- 视频由B/C变C/C：7915从错误B改为正确C；3094仍错。两视频严格0/2→1/2，非完整视频质量通过。
- 新Swift对Python参考logits NRMSE：两视频0.06795/0.02104（原0.09195/0.08241）；完整logits仍未等价。位置数组已匹配不代表所有实现边界均相同。

因此确认：固定Swift Qwen2VL两处把视频网格错路由到image参数，是实际编码缺陷；此隔离修正在同输入真机上修复了7915错误。本实验未加未修正位置导出臂，不将源推导的旧线性位置冒充直接读取。静态2RED→2GREEN与真实7915语义RED→GREEN分开记录。捕获位置仅增加State键/二进制导出，不改变生成输入；尚未有撤掉诊断seam的独立生产补丁回归或native审查。

本次load1015.295ms、4506样本，phys峰值3,026,144,416B（约2.818GiB）、resident1,598,128,128B、thermal0→1，8题全部完成无guard触发；含额外导出forward，不能当生产预算或公平性能提升。candidate已终止卸载、普通Combined重开，本run所有既存App安装元数据保持。证据`grid-routing-control/device-run-2/`、`device-run-2-causal-comparison.json`、`source-verification.json`、`restoration-run2.json`。

当前仍F08open/not-ready。这个缺陷足以推翻“所有错误仅是模型能力”的判断；也不能用一题改善推翻白瓶共同误判/3094错误。后续应让相同两行修正接受原24题/原birds控制回归，再评估固定4bit模型的剩余能力；不换权重、不改评分、不把本8题当总体质量。

## 结果与边界

新 iPhone PID3127 导出真实 prepared tokens、pixels、mask、THW 和完整首 token logits，再用 fresh cache 原样生成。8 个原文 `A/B/A/B/C/C/B/C` 及输入元数据逐项复现旧28题诊断；终态双读一致。新消费者 fingerprint `a8a7324ec2d228f993e998ff1649aa73582b3fec3095e966ba2147af612bc3d8`。Python 直接读取同数值张量和同权重，不经过另一处理器；仅比较首 token，不能算完整生成的官方评估成绩。

| 病例 | iPhone 首 token | Python 首 token | 判断 |
| --- | --- | --- | --- |
| 白瓶前图，YES优先 / NO优先 | A / B | A / B | 两边都正确 |
| 白瓶后图，YES优先 / NO优先 | A / B | A / B | 两边都错误，均误答存在 |
| 7915 两图 | C | C | 正确 |
| 3094 两图 | C | C | 错误，期望B |
| 7915 端点视频 | B | C | Swift错、Python首token正确 |
| 3094 端点视频 | C | C | 都错，期望B |

单图 Python 原生位置与官方参考一致；完整 logits NRMSE 0.00127–0.00410。白瓶错误跨实现复现，削弱该单图错误由 Swift 独有模型实现引起的假设；仍未区分固定4bit权重能力、量化损失与两边共用输入预处理。

Python0.3.9 的多图 `get_rope_index` 将多个 vision-start 索引求和，而安装的官方 Transformers 源使用逐个索引。保留原生结果，并增加只替换 position_ids 的明确参考控制臂；两图 NRMSE 从0.04173/0.03980降至0.00211/0.00322，首token仍C/C。参考 NumPy 翻译仅 batch1/unpadded，7项检查通过；未导入Torch或修改安装包。不能用旧Python原生多图位置错误指认Swift缺陷。

Swift固定 `Qwen2VL.swift` 的 `prepare` 与 `prepareContinuation` 均把 gathered frames 传给 `imageGridTHW`、`videoGridTHW:nil`。视频分支需要后者；缺失时提前 break，余下token落到线性位置。该源码行为已确认，**尚未完成同一iPhone最小修正因果验证**。视频 Python 原生位置与官方参考一致；相对Swift NRMSE0.09195/0.08241。强制Python线性位置后降至0.04243/0.02739，但7915仍选C，未复现Swift的B，因此不能宣称网格路由是唯一原因。

后续有区分力的控制：Swift836/823 token视频进入默认 balanced512 分块，而先前Python是全量forward；按固定 `PrefillParameters.forEachChunk` 实际边界，分别为418+417+1与411+411+1。先保留旧结果，再用独立缓存控制分块；随后仅隔离依赖副本修正两处网格路由，进行真实RED/GREEN与同输入iPhone验证。两图478/465与单图241未进入长输入分支。

## 验证与恢复

`parent-output-verification.json` 逐项验证8病例/12输出的SHA、finite、151936维、argmax、A/B/C数值及RMSE/NRMSE/maxAbs，并重核11模型文件；19保护文件摘要保持。`restoration-receipt.json` 核所有既存App安装元数据保持、candidate已卸载；普通Combined已重开。初始Swift `decode(tokens:)` 编译RED保留，改正确的 `decode(tokenIds:)` 后签名构建/严格codesign通过，不能归为模型失败。Python安装网络超时与成功恢复分开保存，未自动重试模型调用。

尚无独立native复核（旧429不重试），未更改生产依赖/默认模型/评分，旧28/24/8/16题结果保持。单图共同误判与视频运行时差异并存，不能据此宣布Qwen普遍无法识别或功能修好。

## 分块控制与隔离修正进度

`chunked-control/result.json`完成两视频×参考/线性位置×cached-full/balanced共8forward，各层缓存offset均核对。cached-full与原全量logits逐字节相同；balanced仍两题首tokenC，未复现Swift7915的B。父独立SHA/finite/argmax/NRMSE复算通过，不能把分块单独当剩余原因。

`grid-routing-control/`复制完整固定SourcePackages，仅Qwen2VL.swift两处网格路由语义修正，并用额外诊断State键捕获实际位置。未改共享缓存/生产源码；两项静态源码回归先2失败再2通过（不冒充行为测试），新签名Release88.756s成功，源码摘要在构建后保持。消费者只增加位置导出，原sampler与生成保持。

首个隔离修正iPhone PID3211完成前5题后报告停在第6题（3094两图），宿主210s超时SIGTERM；无终态/guard收据，视频0题。已导出的前5题tokens/pixels与logits均逐字节等于基线；这不是8题修复完成。中途前台观察为SpringBoard且PID仍存在；未观察到匹配App crash文件。同期UgreenHome与UITest Runner安装URL变化，说明存在其他安装活动，未证明其为挂起原因。已卸载候选、重开普通Combined且其元数据保持；其他两App变化原样记录，不声称全App元数据不变。用户随后确认其他测试已结束，允许继续F08；明确记录新run2，原中断与报告保持，不静默重放。此刻run2正在准备，尚无修复因果结果。

## 原24题回归：连续运行被热保护中止，后半分批诊断中

`perception-regression/`保留原37326e75…清单/24视频/同权重/同题与trim-only评分，复制原snapshot/tokenizer/Suite；Suite仅启用已验证ResourceSampler3.5GiB/serious/180s，模型源码只有两个网格路由修正（已移除诊断State键）。签名Release86.786s、严格codesign/源摘要/24媒体检查通过。最小补丁保留为[Qwen2VL视频网格路由补丁](patches/f08-qwen2vl-video-grid-routing.patch)，对原固定checkout只读`git apply --check`通过，未改共享/生产依赖。

唯一连续回归PID3303从nominal启动，78,049ms进入thermal2，守卫exit70，guard收据二次读取相同。当前保留12行、state仍running，前12严格2/12（原同前缀4/12），不得外推为完整新24题分数。保护时采样phys2,858,585,392B，因热而停，非超内存。候选已卸载、普通App重开；旧报告不覆盖、热阈值不调高。

已预注册`perception-batches/`：只收后12项结果，分原索引12..<18和18..<24两批，原完整清单验证后选片，题目/prompt/媒体不变；要求每批模型加载前nominal，另有无模型thermal-only新进程收据。Sampler与两处model修正保持。新签名构建64.439s。最终如收齐，只能报告跨进程24输出聚合，不能伪造一个完成的连续24报告或热/性能预算通过。此刻两批尚未收齐。
