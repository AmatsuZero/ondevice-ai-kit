# F08实际模型报告作用域：全输出精确，持续内存收益尚不稳健

2026-11-14。两组真实MLX Metal各28条完成、112个完整向量全与原结果精确一致。报告同步pool达到预注册的全区间增长差判据，但末尾占用接近、同分辨率臂不一致，**不采纳为已证明的持续内存修复，不直接推进手机“修复复验”**。原质量16/36、高细节3/14→4/14和W6open/not-ready保持。

[冻结方案](../superpowers/plans/2026-11-14-qwen3vl-model-report-scope.md)，证据`artifacts/verification/f08-qwen3vl-model-report-scope-20261114/`。两新进程串行、唯一runID/输出，无重试/后缀补跑。没有手机/下载/新依赖/生产默认改动，没有helper/native调用；作者核验非独立复审。

## 对照与完整性

原消费者来自已验证direct graph full28；raw仅变身份/声明，scoped另将after-perform-return后的五条报告语句包入同步autoreleasepool。两候选均可逆向还原原字节，其他复制Swift源精确保持；没有跨await、修改模型数学/图像作用域/保护、删除报告字段或新增采样。

同一原28输入/八帧/实际时间、Qwen3-VL2B4bit revision9c4f5209、runtime6e338e5d、balanced512、在线A/B/C→EOS及cache256MiB。原actual processor28复用前独立合同核验/十输入变异拒绝。两组各56完整有限151936维向量、28层KV/协议/像素/所有非计时row字段同旧；各280阶段有序、图像weak题后全nil；after-clear active均1,781,949,568B/cache0。每组另外三项离线破坏（决策、缺向量、阶段顺序）拒绝。原3.5GiB/thermal2/180s守卫保持，实际起止均normal0。

## 资源观测

| 指标 | raw PID60714 | scoped PID63016 |
| --- | ---: | ---: |
| 完成/退出 | 28/0 | 28/0 |
| 预注册第2→第28题after-clear phys增长 | 57,458,736B，54.80MiB | −2,523,088B，−2.41MiB |
| baseline臂第2→最后增长 | 57,393,200B，54.73MiB | 61,833,288B，58.97MiB |
| detail臂第2→最后增长 | 79,691,824B，76.00MiB | 58,654,792B，55.94MiB |
| 第2题after-clear phys | 2,376,666,064B | 2,432,371,592B |
| 最后after-clear phys | 2,434,124,800B | 2,429,848,504B |
| 最后10题after-clear phys中位 | 2,431,364,096B | 2,417,363,896B |
| 采样phys峰 | 3,463,547,904B，约3.226GiB | 3,451,702,224B，约3.215GiB |
| 资源样本数 | 1,358 | 2,044 |
| 模型实验total | 15,448.66ms | 21,339.44ms |
| 单题total中位 | 472.31ms | 639.84ms |

预注册判据是raw增长≥32MiB、scoped至少减少16MiB且30%，实际减少59,981,824B（57.20MiB），故`comparison.json`保留diagnosticPositive=true。**不改变这个判据或原验收结果。** 但两组第2题边界已经差约53.13MiB，最后边界仅差4.08MiB，足以说明总区间收益对起点敏感；baseline同臂反而略增，detail同臂下降但仍约56MiB。去掉较早边界的描述性轨迹不能当新预注册门或补跑理由；不能把负的全区间差称完全无增长/稳态。

scoped耗时更长、周期样本更多；本轮未控制宿主调度/整体资源压力，也未将报告阶段与MLX.clearCache分开，after-return→after-clear差不属于序列化唯一分配计数。不能将Mac timing当手机延迟、RSS/phys/MLX相加，或将峰差自动归因pool。完整轨迹见`trajectory-analysis.json`和`endpoint-sensitivity.json`。

## 裁定与下一步

无模型固定外pool循环中报告临时积累已证，但**不能直接外推到实际async模型，更不能从手机196.16MiB减去host57.20MiB**。此真实模型对照只支持“作用域无数值副作用、全区间指标阳性但持续内存收益未稳定证明”。不扩pool、不重跑原28刷结果，也不动正式App。

下一步应取得真实模型题后活进程的VM区域/宿主分配归属及稳态边界证据（需在明确idle阻挡点采集，不能再将跨prefill的summary当idle），再决定是否值得新手机运行；同时识别质量仍是独立主阻塞，不因内存诊断而关闭。保留既有原16/36/高细节结果及所有旧失败。当前设备范围仍iPhone16ProMax，产品质量/预算/正式采用与集成回归门未过。

最终输入/旧证据保护、进程终态和本轮自有缓存清理见`final-audit.json`；living docs更新后单独终态freeze，避免把正常新增记录误报为实验输入漂移。
