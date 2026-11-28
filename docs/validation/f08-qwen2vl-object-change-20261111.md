# Qwen2-VL 物品识别与变化比较诊断（2026-11-11）

目标：将 [8/16 帧均为 1/4 的记忆题失败](f08-qwen2vl-memory-coverage-20261111.md) 拆成单帧物品识别、两图变化比较、视频入口三个可区分边界。用户授权主代理修正后，隔离程序已通过验收，并完成一轮 iPhone Metal 28 项诊断；报告双读一致、父独立校验通过，结果仍未达到产品质量。

固定 Qwen2-VL-2B-Instruct-4bit revision `01af461cdb9574acc09084a0ef94e216e142b085` 和 MLX Swift 0.32.3 / mlx-swift-lm `6e338e5`，只使用原公开视频 `video_7915`、`video_3094` 已冻结 8 帧的第 0、7 帧 PNG。使用两个端点是统一规则，不依据答案挑选时刻。PNG 与上轮实际宿主 snapshot 完全相同；不宣称 PNG 是 iPhone 原生视频解码的逐字节副本。

## 预先固定的诊断集合

| 分组 | 输入与问题 | 数量 | 用途 |
| --- | --- | --- | --- |
| single-presence | 每个视频三个物品，前后各一帧，YES/NO 两种选项顺序 | 24 | 区分物品存在性判断和答案位置偏向 |
| pair-images | 两张按前后顺序排列的端点图，原题问题/选项加统一顺序说明 | 2 | 检查静态前后状态比较 |
| pair-video | 同一前图重复四次、后图重复四次，经原 video 入口；问题与 pair-images 完全相同 | 2 | 与两图入口比较，辅助定位时间聚合路径 |

`video_7915` 检查水瓶、包装食品、透明杯；`video_3094` 检查白瓶、黄瓶、纸卷。透明杯、黄瓶、纸卷是前后保持存在的控制物品。水瓶、食品、白瓶在后图中缺失。

单帧题24个字母答案恰好12A/12B；但语义标签包含18YES/6NO，因此“总答YES”基线为18/24，“总答A/B”各12/24。不能只用总正确数证明物品识别可靠；需要分别检查六个缺失判断、YES/NO顺序一致性及各物品状态。

两图与视频入口的视觉 token 数不同，输入位置/模态编码也不同。本对照是边界诊断，并非单一张量变量实验，也不直接证明某个 runtime bug。这里使用的顺序提示和单帧问题均属于新诊断，不能加入原24题成绩或替换原始提问。

## 验收与边界

清单固定28题及顺序，SHA `ae59f42bfd1c813f632a470e904f2ce0dfeadbddeebd41b2313e2bd41c9b2814`；每题 prompt SHA、PNG SHA/字节数、原视频 SHA、源帧索引和物理时间均记录。每题使用新 generation cache，temperature=0、maxTokens=32；评分仍仅 trim 空白后精确匹配大写字母。物品存在性问题因交换选项产生的差异单独报告，禁止去标点或根据输出改题。

启动 thermalState 必须 nominal。复用上一轮已验 ResourceSampler：10ms 采样 phys_footprint≥3.5GiB、thermal≥serious、采样时钟≥180s 或遥测读取失败时，写独立停止收据并退出，不将部分结果标为完成；这不是硬内存界限或产品预算。无自动重试。完成后双读终态、独立校验，再卸载隔离 App 并恢复普通 Combined App。

证据根：`artifacts/verification/f08-qwen2vl-object-change-20261111/`；初始 `experiment-plan.json`、`device-fixtures/manifest.json`。候选由免费 hy3 在独立写沙盒生成，主代理审查、集成并运行父验收和设备；原生产代码、依赖、模型、旧评估及评分保持。

## 历史阻塞：诊断程序修正未交付（已由用户授权主代理接手）

免费hy3初稿自然结束631.328s并交付4文件，但父验收拒收：实际pure编译String→Data错误；iPhoneOS unsigned Release构建exit65，另有不存在的VLMModelContainer类型、timing枚举参数错误。源码还有生成Task<Void>误取token数、真实hyphen caseID被拒、终态/计数/报告字段及持久化失败停止等契约问题。原稿、编译RED、原DELIVERY保留，不能将这些诊断程序缺陷当成模型误判原因。

独立hy4free定点修正请求自然结束7.008s，CLI exit0但实际result为error_during_execution/is_error=true、HTTP429/code6004/categoryquota，零文件、context未变、无permission denial。服务端提示2026-11-11 15:12:49 UTC+8重置，这不是届时额度恢复证明。原件及父裁定见`.omx/artifacts/cbc-hy4free-qwen-object-change-correction-20261111/{raw.jsonl,result.json,parent-assessment.json}`。该线路停止；用户随后明确允许主代理直接修正，未重试或切换助手。

以下为本轮已完成结果。上述编译/助手故障均发生在新推理之前，不能解释先前的模型错答。

## iPhone Metal 结果与原因边界

唯一设备运行 PID2797、runId `26B74161-3D49-4E85-987D-00E9C2F1DDCD`，iPhone17,2 / iOS27.2.1，28/28运行完成，无runtime error。终态双读字节相同，SHA `36bfa4a7383097fd9360f165dd53e2b4f8d4ffdfd868f48dc6edd44596d5ca85`；`device-run-1/assessment.json` evidenceValid=true。总严格15/28，仅作为新诊断，不并入官方原24题或原8/16帧成绩。

| 分组 | 严格正确 | 格式无效 | 合法字母选错 | 单题总耗时中位 |
| --- | --- | --- | --- | --- |
| 单帧物品存在性 | 14/24 | 7 | 3 | 1.501s |
| 前后两张图片 | 1/2 | 0 | 1 | 3.203s |
| 相同端点的8帧视频 | 0/2 | 0 | 2 | 5.671s |

单帧按truth分：存在12/18，缺失2/6；14/24低于始终回答YES的18/24理想字母基线，不能说物品存在判断可靠。7个格式无效均为`A.`/`B.`；不修改严格评分。另3个合法字母错答分别为食品前图no-first与白瓶后图两种选项顺序。食品前图一条无效`B.`也指向错误答案，但不将无效字母算正式语义标签。

12个物品/状态的双顺序组，5组两侧都合法且语义一致，其中4组两侧都正确，另一组为白瓶后图稳定错误；其余7组至少一侧格式无效，不能笼统称为7组语义翻转。保留逐行raw和原提示，不追问刷分。

| 原视频 | 两图输出 / 期望 | 端点视频输出 / 期望 |
| --- | --- | --- |
| video_7915 | C / C（水瓶及包装食品被拿走） | B / C（错误称没有变化） |
| video_3094 | C / B（错误称黄色瓶子被加入） | C / B（同样错误） |

本轮重新视觉核对4个冻结PNG：7915前图有水瓶/食品/杯，后图水瓶/食品消失而杯保留；3094前图白瓶、黄瓶、纸卷，后图白瓶消失而黄瓶/纸卷保留。实际图片尺寸均512×288。单图preparedTHW `[1,20,36]`/packed `[720,1176]`，两图两个该grid/packed `[1440,1176]`，视频 `[4,20,36]`/packed `[2880,1176]`，28行均符合固定模式。

这次证据收窄了三个边界：

1. **不能只归因缺帧或时间记忆。** 白瓶后图即使单张输入，YES/NO交换顺序仍两次误答存在；食品前图也有合法字母错误。无需时序比较就能触发错误。
2. **不能只归因输出格式。** 单图有3条合法错答，4条比较题均合法、其中3条语义错误。去句点不是修复。
3. **输入方式仍有影响。** 7915两图正确、相同端点视频错误，提示视频聚合/模态表示值得继续查；但视频与图片token数、时间patch/位置编码不同，这不是同张量实验，不能直接判定adapter或temporal patch有bug。

当前最可靠结论是固定Qwen2-VL2B4-bit/MLX组合同时存在物品存在性、变化比较和格式遵循缺口，已不能把问题局限为视频抽帧或单一记忆能力；尚不能唯一拆开原始模型能力、量化/转换权重、图像预处理与MLX模型实现，更不能外推原始Qwen2-VL或视频理解本来做不到。F08保持open/not-ready。

下一项有区分力的验证应固定失败的白瓶后图和对应前图阳性控制，在独立实现中使用同一已有MLX权重及完全相同tokens/归一化pixels比较首token/logits；若两实现一致，再考虑原始/更高精度权重对照以分离量化。此建议尚未执行，本轮没有新下载、增加依赖或更改模型；不重复盲目增帧或修改rubric。

## 修正、资源和恢复证据

用户直接授权主代理实现后，只修正新consumer的`EndpointDiagnosticSuite.swift`，保留外部原稿和原DELIVERY。27真实pure检查及21组host故障注入通过；host仅验证调度/错误/停止，不运行CPU/GPU模型。包括缺/重复/非法digest、模型load失败、第二题prepare失败且保留原异常/题号、缺生成完成信息、空/带点输出仍算运行完成但严格错误、采样/snapshot失败、各阶段写失败停止、错模态、PNG篡改/超限/文件及祖先symlink拒绝。

首次主代理真实构建发现Swift6捕获可变images错误（原日志保留），改捕获验证后的不可变数组；最终unsigned Release exit0/16.219s、signed Release exit0/13.759s、codesign strict verify通过。保留AppIntents无依赖/未声明全部orientation两类构建warning。`ResourceSampler.swift`与上轮逐字节相同，未降低guard。源码fingerprint `eaeeca7d368d4c62d29a3adecf1cc791234c31ecd0e7b7ffeac06d09cd36e02d`；sourceCommit `9e6e909159b6c25460dbd206a2d8c6ce4c2591ee`只是checkout身份，完整dirty实验源码由sourceFiles摘要固定。旧native429未重试，独立native源码复核仍缺，不能冒作外部审查通过。

模型load975.611ms，非文件/设备冷缓存保证；5316资源样本，采样时钟最大58.559s，host完整收取63.758s。采样phys峰值3,011,497,024B=2.805GiB，resident1,440,759,808B=1.342GiB，MLXactive/peak2,188,720,978B、cache1,568,299,872B。thermal0→1，未触发3.5GiB/serious/180s guard。各口径分开，非产品预算/硬allocator上界。

终态校验后终止PID2797、卸载隔离`io.ondeviceai.f08candidate2b`、重新打开普通Combined；安装前后普通App完整metadata一致，17项旧consumer/manifest/报告保护摘要及11模型文件前后摘要一致。签名App保留于`accepted-app/MLXVLMConsumer.app`；原始/修正源码、编译RED/GREEN、测试、双读报告、评分和恢复收据均保留。仅本轮Generated DerivedData在签名App摘要核对后清理，名义约0.83GiB，未清模型或原实验。

核心文件：`diagnostic-summary.json`、`source-freeze.json`、`primary-correction.diff`、`runtime-primary-expanded/result.json`、`device-run-1/{terminal-second-read,assessment,outcome}.json`、`restoration-receipt.json`。
