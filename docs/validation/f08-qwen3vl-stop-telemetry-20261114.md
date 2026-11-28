# F08停止收据补齐与生命周期证据

2026-11-14。新隔离sampler补齐停止样本的MLX active/cache/peak三字段；真实Swift回归旧5项RED、新12项GREEN，4个错误实现变异均拒绝，实际固定MLX小型Metal夹具的6个收据值精确等于触发样本；iOS18 arm64单sampler类型检查通过。**只是诊断链修复，不是模型内存或质量修复；W6仍open/not-ready。**

证据目录：`artifacts/verification/f08-qwen3vl-stop-telemetry-20261114/`。计划：[stop telemetry](../superpowers/plans/2026-11-14-qwen3vl-stop-telemetry.md)。不重放模型、不操作手机、不下载，不改生产默认、依赖、旧候选或原始实验。当前设备范围继续iPhone16ProMax，通用短视频理解目标保持。

## 诊断缺口与最小改动

[原cache256实验](f08-qwen3vl-cache256-detail14-20261114.md)在6111-detail prefill停止，独立stop只有phys/RSS，主报告最后样本更早。实际sampleData已经读取三项MLX值，writeStopReceipt却遗漏；若采样失败，writeSamplingErrorReceipt也未显式提供这些不可用字段。因此不能从旧最后主样本补写或推定stop组成。

新`candidate/ResourceSampler.swift`仅添加6个字典项：正常停止从**触发的ResourceSample**取`mlxActiveBytes`、`mlxCacheBytes`、`mlxPeakBytes`；采样失败对三项明确写JSON null，不假造零。原采样/校验/阈值、10ms调用方设置、原子写收据、失败stderr及两处Darwin._exit70保持。合法零与不可用null分开。

“同样本”不表示底层读数原子一致：task_info、三个MLX计数器按顺序读取；采样不保证捕捉瞬时峰，MLXpeak是历史active峰，不能与当前cache相加求当前phys。保持分口径，不将RSS/phys与MLX相加。

## 验证

| 检查 | 实际结果 |
| --- | --- |
| 旧sampler RED | 12中7通过；footprint/thermal/elapsed/合法零及采样失败5项因缺MLX字段失败；退出行为原本正确 |
| 新sampler GREEN | 12/12；边界、normal atexit、emergency无atexit、写失败stderr、null/zero与非零值运输通过 |
| 变异 | active/cache错配、peak写零、writer重新snapshot、采样错误伪造active0，4项均被真实执行拒绝 |
| 实际MLX/Metal | normal0、trigger70，warmup矩阵值512；stop6字段逐项等于测试钩子记录的真实触发ResourceSample |
| iOS | 现有pinned MLX模块、arm64 iOS18、实际iOS SDK类型检查exit0；不是完整App编译或手机运行 |

确定性夹具只替换sampleData外部遥测输入、热读数和MLX模块，保留真实guard/writer/periodic/exit代码。MLX mock仅服务无GPU的序列化测试，不执行CPU模型。真实Metal夹具链接已有对象，无SPM更新；**测试副本**阈值1字节，并在writer前以只观察钩子保存触发sample，正式候选仍3.5GiB。实际trigger在采样开始约0.020ms触发，不能说重现了原模型prefill并发现场或证明手机内存通过。现有Device.setDefault弃用warning保留。

## 生命周期核查与新观察

源码及SHA见`lifetime-source-freeze.json`，消费者为原cache256版本，未改。读实际源码确认：

1. 每题`container.perform`内重新创建frames/input/prepared/positions/28层KV/current；输出为Data，题间rows保存JSON标量/数组，不保存CIImage或MLXArray。SerialAccessContainer串行等待body返回，不保存历史结果。作用域结束不等于已经测得所有框架/驱动资源归还。
2. 原消费者已逐token用autoreleasepool，**frame decode、processor.prepare和首prefill不在这些同步池内**；题末perform返回、写主报告之后调用MLX.Memory.clearCache。现有Swift代码没有题前/准备后/prefill后/返回后/清缓存后明确采样，无法判定增长所属边界。
3. MediaProcessing全局CIContext已配置cacheIntermediates=false；每次asMLXArray渲染后已clearCaches。不能把“再加一次clearCaches”当已证实修复。其渲染仍产生临时RGBAf Data，MLX转换/图像/filter对象与宿主分配器的实际释放时间尚未测定。
4. Qwen3的VisionInputs、deepstack和prepared像素在当前prepare调用局部持有，cold长prompt进入balanced512 continuation，vision结果跨语言chunk使用；当前源码检查未发现按题存入模型的历史列表，但不构成驱动/底层无泄漏证明。不能仅凭泛用注释归因dense attention。

既有1132个样本只有periodic/post-load，无per-case phase。`existing-samples-analysis.json`保留分时窗描述，各字段极值不作同刻组成。进一步固定筛选monotonic≥5000ms、active=1,781,949,568B、cache=0，取得**90样本**，见`matched-mlx-counters.json`：

| 匹配样本 | 时间ms | active B | cache B | phys B | RSS B |
| --- | --- | --- | --- | --- | --- |
| 首 | 5587.476 | 1,781,949,568 | 0 | 2,095,074,160 | 1,999,650,816 |
| 末 | 13398.813 | 1,781,949,568 | 0 | 2,650,819,584 | 2,447,425,536 |

同MLX当前计数下phys差555,745,424B（约530MiB）、RSS差447,774,720B。这是观测，不是内存归属分解；不证明两点均恰在题末、autorelease泄漏、CoreImage唯一原因、模型权重常量或手机同现象。它支持下一步优先做**无模型的实际预处理生命周期测量**，而非继续只扫MLXcache上限。

下一独立冻结输入/次数/边界，先测真实processor重复准备、局部返回与clearCache后的三口径；若使用autoreleasepool对照，须在同步安全边界、保持输入像素/文本/位置精确并与原路径对照后才谈效果。此分支未实施该对照，也未重新运行6111或补旧后缀。新sampler留给以后新run，旧stop不改写。

## 状态与证据边界

本轮作者验证，没有新独立复审。最终保护摘要、候选派生/实际夹具来源、测试结果、链接及本分支精确缓存清理见`final-audit.json`、`cleanup.json`与`final-file-freeze.json`。原16/36、真机23题热停止、旧detail14 exit139及cache256 running/21 exit70保持。识别内容、持续资源、产品预算和正式集成未通过；本轮不关闭W6。
