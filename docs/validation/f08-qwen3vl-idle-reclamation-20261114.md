# F08空闲边界观测：图像累积消失，部分phys等待后回落

2026-11-14。新真实MLX Metal idle-vmmap与wait-only进程各28条完整，112份完整向量和所有非计时row均与旧结果精确一致。明确idle阻挡避免了旧VM摘要跨prefill问题；纯等待也观察到较大phys回落。但未定位唯一owner、未证明手机/长期稳态，**wait-only的绝对MLX基线门确实失败，不能写成全门通过**。原16/36与高细节3/14→4/14不改，W6open/not-ready。

证据根：`artifacts/verification/f08-qwen3vl-idle-vmmap-20261114/`、`artifacts/verification/f08-qwen3vl-idle-wait-20261114/`。两份预冻结[VM方案](../superpowers/plans/2026-11-14-qwen3vl-idle-vmmap.md)、[纯等待方案](../superpowers/plans/2026-11-14-qwen3vl-idle-wait.md)均保留；无helper/native/手机/下载/新依赖/生产改动。

## 明确空闲的原理与验证

同原full28 direct-graph/raw-report消费者，只增加身份、声明及count0/2/14/28的诊断gate。使用pinned MLX的`Stream.gpu.synchronize()`同步当前GPU stream，写immutable ready(PID/runID/count/case/nonce/sample)，随后await exact ack，10s超时失败；下一题在ack前不调度。sampler/原3.5GiB/thermal2/180s守卫仍运行，未池包await。父对ready、report、阶段日志、完整向量文件清单在观察前后逐项绑定，模型live保持；这不等于驱动/采样线程静止或底层原子快照。

Gate独立无模型测试：一合法、六错误ack拒绝、ack前确实不返回、ack后继续、已消费拒绝、超时。自有sleep子进程live vmmap预检成功并回收。原actual processor28与10输入变异也通过。候选可逆恢复原消费者，模型/数学/输入/原cache256不变。两独立模型进程均normal→normal，28/exit0、各56完整151936维向量同旧、各280阶段顺序精确及weak图像题后全nil。无后缀补跑或重试。

## idle-vmmap PID95396

四次VM工具仅在gate内执行，各约0.71–0.73秒；ready/report/phase/vector收据前后全保持，之后才ack。原先跨prefill观察不再作为此次idle证据。

| gate完成数 | 0 | 2 | 14 | 28 |
| --- | ---: | ---: | ---: | ---: |
| IOSurface resident MiB | 0 | 16 | 3.953 | 2.266 |
| IOSurface dirty MiB | 0 | 16 | 0.016 | 0.016 |
| IOAccelerator graphics resident打印值 | 1.7G | 1.7G | 1.7G | 1.7G |
| owned-unmapped graphics MiB | 无行 | 72 | 72 | 72 |
| Malloc Small resident MiB | 76.7 | 90.5 | 110.2 | 118.7 |
| Malloc Small(empty) resident MiB | 3.797 | 2.531 | 10 | 20 |
| Malloc Large(empty) resident MiB | 11.5 | 无行 | 52.2 | 52.2 |
| Malloc总allocated打印值 MiB | 39.1 | 49.3 | 51.6 | 54.3 |
| Malloc总resident打印值 MiB | 101.7 | 114.3 | 141.7 | 160.2 |
| gate ready→resume phys下降 MiB | 约0 | 400.734 | 356.813 | 277.969 |

图像scope修复后没有旧261MiB IOSurface持续累积；本轮GPU graphics打印值保持1.7G。Malloc驻留与empty区增长比live allocated增长大，支持研究宿主allocator保留/高水位，而非认定同量live对象泄漏。打印K/M/G有舍入，不能把1.7G换算为精确底层字节、将类别相加或认定库owner。Malloc表resident不等于所有VM类别之和。

单进程total22,173.89ms（包含gate/采集等待），采样phys峰3,389,443,024B约3.157GiB。题后MLX active均1,781,949,568B/cache0；0gate加载状态不同（active1,781,949,872/cache4,240）。无手机资源预算结论。

## wait-only PID7150：大幅回落可在无vmmap时出现，但绝对基线门失败

另冻新run，仅改上述消费者的身份/声明，父在同四gate各等待0.8s后ack；不调用vmmap/sample/heap/debugger或暂停目标。report/phase/ready/vector前后保持，只有采样线程正常活动。实际sample ready→resume约824–862ms。

| gate完成数 | ready→resume phys下降 | active ready=resume | cache ready=resume |
| --- | ---: | ---: | ---: |
| 2 | 397.391MiB | 1,781,949,568B | 0 |
| 14 | 297.000MiB | 1,781,949,600B | 0 |
| 28 | 277.828MiB | 1,781,949,632B | 0 |

每个等待区间active/cache/peak本身均前后完全一致；跨题active却从绝对基线多32、64B。原`accept.py:30`严格恒定断言失败，原checker/hash/错误保留。新`accept_observation.py`仅核验并分类实际证据：`evidenceVerified=true`、`experimentCompleted=true`、三个>128MiB回落、`withinEachWaitMLXUnchanged=true`，同时明确`absoluteBaselineCriterionPassed=false`和`predeclaredCriterionPassed=false`。**未改原阈值、未重跑模型，不能用64B很小为理由把原门判成通过。**

该事实足以否定“只有vmmap才可能出现这些回落”，但不能证明唯一是Metal driver、autoreleasepool或malloc回收；原异步gate本身改变调度与pool/回收机会。亦不能因三次phys下降或after-clear首末负差而证明完全无泄漏/长期资源安全。64B分配来源未定，不能外推为线性增长。此轮总22,486.10ms，采样峰3,390,180,328B约3.157GiB；非手机延迟。

## 当前裁定

题后立即phys不是已完全settled的所有权快照；原手机196MiB残余不能仅凭即时值认定永久泄漏，也不能由Mac等待回落宣称手机已解决。旧图像跨题保留已修复；报告scope持续收益未证，仍不采用。待研究的是宿主allocator驻留/empty与实际设备settling/稳态，而非盲改模型缓存/扩pool。

下一可将同样的有限idle阶段遥测带到隔离iPhone，明确“无等待连续运行”和“题后短暂空闲”两种使用合同，保持原手机cache/输入/guard；这属于资源观察，不修改原质量成绩、不能用插入等待冒充原连续吞吐或验收通过。识别质量仍是独立主阻塞，当前Qwen3VL2B4bit仍不准作为可靠通用短视频理解采用。作者验收不等于独立复审。

最终两进程终态、旧输入/证据SHA、限定缓存清理与四living-doc精确新增记录收据见各根final-audit/terminal freeze。没有改正式App/模型/依赖或重跑任何旧失败现场。
