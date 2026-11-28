# F08：Qwen2-VL 官方短视频题集 iPhone 评估

2026-11-11，在实体 iPhone 16 Pro Max（iPhone17,2，iOS 27.2.1 / 24B156）完成24段官方 Perception Test train 视频、每段一道 MCQ 的隔离评估。修正后的独立进程 `run-2` 全24题完成，严格正确 **12/24（50%）**。输入准备阻塞已在本实验中修复；质量和移动端成本仍不支持产品就绪结论。

## 固定条件与证据

- 模型 `mlx-community/Qwen2-VL-2B-Instruct-4bit`，revision `01af461cdb9574acc09084a0ef94e216e142b085`。权重1,245,868,907B，SHA256 `3534d3dcfaacd0cd00e935469bc34b6d692b2ba0749c5d9ee422f3f2175cf992`；通过用户指定localhost:6800 aria2下载、宿主文件和真机运行入口均核验权重摘要。
- MLX Swift0.32.3，`mlx-swift-lm` revision `6e338e5df49424fc0db7c6d58184d622f92ed18a`；上游checkout保持clean。iPhoneOS包包含Metal库；推理在实体iPhone的MLX路径执行，没有PyTorch/Ollama/CPU模型对照。
- 24个不同视频、六类各4题，原素材SHA/大小保持；每段8个实际解码帧、Qwen packed THW.t4、temperature0、maxTokens32。清单SHA `37326e75d21d738e9ab516dce2a648482aa7e480393771c4107080dc26888e1f`。
- 新进程PID2483，runId `98A3CB45-5BDE-4446-A1A7-7D2054E52C95`。源指纹 `e7376228733840cdf8bc2e1a7ea4066e488ebbdd137f5583400c38c01cd50792`。终态双读逐字节一致，报告SHA `7b0e1458170fe92c5692010cc870fb0598d97b45ecf1e3c2f504469cfec5adf5`。
- [父独立评估](../../artifacts/verification/f08-qwen2vl-perception-test-20261110/assessment.json)、[原始真机报告](../../artifacts/verification/f08-qwen2vl-perception-test-20261110/device-run2-terminal-second-read.json)、[成本摘要](../../artifacts/verification/f08-qwen2vl-perception-test-20261110/resource-timing-summary.json)。原始报告含24题原文、答案、输入/提示摘要、两种时间、tokens、tensor和14,441次资源样本。

## 严格结果

只trim外围空白，必须精确返回正确的大写字母；`B.`等仍计错，没有事后放宽评分。

| 类别 | 严格正确 | 格式违规 |
| --- | ---: | ---: |
| 镜头运动 camera_motion | 0/4 | 3 |
| 记忆 memory | 1/4 | 0 |
| 抽象 abstraction | 3/4 | 0 |
| 反事实 counterfactual | 2/4 | 0 |
| 物理 physics | 2/4 | 2 |
| 场景语义 semantics | 4/4 | 0 |
| 合计 | 12/24 | 5 |

12个错误中，5个格式违规，另7个输出合法字母但选错。5个格式违规中有3个字母与预期相同，仍不计分；其余两个同时选错，不能将全部错误归于标点。记忆题1/4说明格式修正也不能消除剩余质量缺口。

这24题来自训练split的固定小样本，题目类别内也有相近模板；不等于官方完整基准、独立测试集或产品分布。答案分布A6/B10/C8，始终选B可得10/24，因此50%本身不足以证明可靠视频理解。场景语义4/4只限当前四题，不能外推所有场景。

## 查明并修复的评估程序问题

1. 原候选调用固定版本不存在的 `Generation.token`，iOS编译失败。外部免费hy3候选改读最终 `Generation.info.generationTokenCount`，同时修复最后一题异常被计成completed、弱manifest校验和吞掉报告写入错误。父实际Swift检查从原稿36项中13失败变为36/36通过；原编译错误和RED保留。
2. 旧snapshot入口只接受5秒/32MiB、显式709、segment sourceStart0；全部官方视频9.3–35秒、有普通MP4时间偏移和未声明transfer，旧入口实际拒绝24/24。仅在新隔离consumer副本采用40秒/128MiB、sourceStart≤0.1秒、轨道与容器时长差≤0.05秒、未声明或显式709；H264、尺寸、单轨/单segment、无时间缩放、文件边界及清理保持。原视频未转码/截短/改标签，生产入口不变。父实际24段解码/8帧/清理全部通过。
3. 第一轮真机PID2422在第5题以preparation停止：attempted5、failed1、skipped19，前4题原文`B.,A,A,B.`。该失败完整保留，不能作为24题质量结论。
4. 固定Qwen处理器对已解码帧再次按时间抽样；有抖动的8帧时间不能保证8帧保留。父编译执行固定上游采样/选择代码：24组实际host解码时间中11组少于8帧，第5题选中索引[0,1,2,4,5,7]六帧，THW.t3；7帧还可能padding成t4而隐藏漏帧。改为处理器专用ordinal时间0...7后同24组均保留全部8帧。
5. 只在隔离Qwen套件提供ordinal时间，图像/顺序不变，真实帧时间继续写入timestampSeconds，另写processorTimestampSeconds与frameTimingPolicy。固定Qwen2VL.prepare只消费处理后的有序图像，不使用其时间语义。加入准备失败详细诊断。新run-2通过第5题并完成24题，原run-1不覆盖。这不是生产adapter修复，也不适用于一般使用真实时间语义的模型。

详见[连续进展与裁定](../../artifacts/verification/f08-qwen2vl-perception-test-20261110/progress-20261111.md)、[采样边界复现](../../artifacts/verification/f08-qwen2vl-perception-test-20261110/resampling-boundary/result.jsonl)。所有实施候选来自授权的免费hy3；主代理审查、逐字节集成并执行验收。旧native复核429未重试，本轮缺少独立native源码复核；不能把父检查写成独立审查通过。

## 真机成本与边界

- 新进程模型加载405.63ms；此前同机已运行过失败批次，不能称重启设备/冷文件缓存加载成本。
- 单段总耗时中位6.182s，最小5.374s，最大8.127s；24段总计158.314s，不含模型加载和文件传输。总耗时包含媒体核验/解码、prepare及生成。报告generateMs中位77.31ms在TokenIterator构造后才开始，不能代替包含视觉编码/预填充的完整推理成本。
- 采样phys_footprint最大3,026,242,624B（约2.82GiB），resident最大1,460,682,752B（约1.36GiB），MLX peak2,188,999,490B（约2.04GiB）。这是采样/MLX边界值，不是真实瞬时峰值或能耗；三个量含义不同，不能相加。
- thermalState从0/nominal变为2/serious；没有测得能耗，也不能仅凭结束状态量化节流幅度。当前成本不应忽略。
- 每段最多35秒只采8帧，可能漏掉短暂事件/顺序变化；本轮不能区分稀疏采样、模型能力、量化及runtime各自的贡献。未知transfer依赖AVFoundation隐式颜色转换，未做参考实现像素parity。

## 恢复与后续

隔离`io.ondeviceai.f08candidate2b`已卸载，候选tmp目录0文件；普通`io.ondeviceai.combined.demo`重新启动，安装metadata及bundle URL前后完全一致。未采集普通App数据文件摘要，不夸称数据级完整性校验；本任务未修改其模型/数据。原consumer12个Swift文件摘要保持，生产模型/依赖/default/lock没有替换。宿主约81GiB可用；本轮新增约1.2GiB固定模型及不足1GiB构建缓存，无新整数据集展开。[恢复收据](../../artifacts/verification/f08-qwen2vl-perception-test-20261110/restoration-receipt.json)。

当前裁定为continue-evaluation，qualityAccepted/productionReady/thresholdApproved均false。下一项有区分力的实验是先核对最弱记忆题的8帧是否覆盖答案事件，固定问题与标签，只改变采样覆盖进行对照；单纯去标点或重复同输入不能解决时序质量问题。此前否决的SmolVLM尚未在这24题上运行，不能据此声称完成两模型公平比较。产品内存/持续运行预算、独立复审和更广数据验证仍未完成。
