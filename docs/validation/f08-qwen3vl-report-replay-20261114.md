# F08报告序列化生命周期回放

2026-11-14，作者诊断，W6仍open/not-ready。此轮无模型、无MLX/Metal推理、无手机操作、无下载/新依赖/生产变更。按[预冻结方案](../superpowers/plans/2026-11-14-qwen3vl-report-replay.md)执行；证据根`artifacts/verification/f08-qwen3vl-report-replay-20261114/`。

## 结果

原报告循环每题把不断增大的typed资源采样数组编码成Data，再桥接为Foundation JSON对象，并原子写出整个报告。同步循环中，该报告代码本身确实可以造成跨题临时内存积累；给同一同步body加autoreleasepool显著降低增长。不能据宿主夹具把手机约196.16MiB残余增长全部归因此处，也不是已验证的模型路径修复。

| 同一原36题回放 | raw，PID36642 | scoped，PID36692 |
| --- | ---: | ---: |
| 完成/退出 | 36/0 | 36/0 |
| 第2→第36题after-body phys增长 | 124,551,288 B，118.78MiB | 22,233,112 B，21.20MiB |
| 同区间RSS增长 | 124,469,248 B | 22,216,704 B |
| 边界采样phys峰 | 139,215,424 B | 36,831,712 B |
| body中位 | 24.00ms | 25.15ms |
| 外层pool排空后phys变化 | −104,267,800 B | 0 B |

phys增长减少102,318,176 B（97.58MiB，约82.15%），达到预注册raw≥64MiB、减少≥64MiB且≥50%的诊断判据。此峰仅指before/after-body边界采样，不保证捕获瞬时峰。不能相加RSS/phys或把排空差值当唯一对象/库owner的字节计数。

## 输入及输出核验

输入只读复用`f08-qwen3vl-iphone-graph36-20261114/ios-output/result.json`的36原row、11,411个typed资源样本及其他原元数据。每题取对应`caseId:after-perform-return`为止的前缀（782→11,399样本），原并发snapshot精确时刻没有重演；最后12个完成阶段样本不属于循环body前缀。typed七字段同原sample定义。

两独立串行进程使用同一编译产物、同一reportingBody。scoped仅在调用此body处加同步pool；没有跨await。原子写入与sorted-key JSON保留。每题hash观察使用两臂相同的独立短pool、位于测量body之后。最后report/rows在最终after-body采样仍保留。为固定释放边界，两臂明确使用覆盖整个同步回放的外层pool；它不代表真实异步运行时的默认pool行为。

全部36对序列化SHA精确相等，最终文件逐字节相同、JSON等于原36row及最后采样前缀；原row/完整typed样本语义精确保持，三项独立破坏（丢row、改sample、错completedCount）均被拒绝。`execution-freeze.json`核验源结果、原消费者、方案、两旧terminal snapshot、所有fixture、脚本、binary及无权重/网络沙盒。原report内的手机runID/PID/指标仅为只读复制数据；本轮实际进程与结果以各臂`receipt.json`/process收据为准，不能把复制内容称新手机运行。

## 边界与下一步

此轮支持进一步验证“实际模型消费者的报告同步作用域”这一新假设。尚未改ModelSuite、生产runtime或App；下一应另冻实际模型对照，保持同输入、数学、guard，验完整向量/报告内容/weak及阶段资源，再决定手机复验。不要把97.58MiB直接从原手机196.16MiB相减，或认定全部残留内存已解释；真实async、模型内存压力与iOS分配器行为都未重现。旧16/36和高细节3/14→4/14不变，质量采用门仍未通过。

没有新helper/native调用，作者核验不等于独立复审。本轮只关闭报告回放诊断；质量/资源预算/正式采用与集成回归仍开放。编译缓存仅在确认本轮两进程已终态后清理，最终保护/终态文件清单见`final-audit.json`和`final-file-freeze.json`。

首final审计严格比较旧terminal snapshot，因四份持续维护文档新增记录而失败，原checker/失败保留。r2绑定八个旧/当前精确SHA；七项能将新增段落移除后精确还原旧SHA，较早phone快照的W6文档不作字节未改声明，其较新detail14版本可精确还原。此例外仅覆盖四份列明的living docs，全部旧实验数据/源码及本轮execution输入仍严格相等，无模型重跑。运行后短暂误加ledger至冻结方案，已精确恢复预运行SHA，恢复收据单列；运行期方案未变。
