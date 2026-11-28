# F08无模型实际预处理：28组完成，未复现模型运行的大幅增长

2026-11-14。唯一新host Metal进程完成原28组输入的实际processor准备及像素/位置核验，exit0、thermal0→0，**不加载模型权重、不生成答案**。每题函数返回后active均0，清缓存后active/cache均0；同臂预热后的phys仅约6–7MiB增长，没有复现原模型运行匹配MLX计数时约530MiB的增长。**这不是模型内存修复或手机验收；W6仍open/not-ready。**

证据`artifacts/verification/f08-qwen3vl-preparation-lifetime-20261114/`；[预注册计划](../superpowers/plans/2026-11-14-qwen3vl-preparation-lifetime.md)。前序[停止遥测/生命周期检查](f08-qwen3vl-stop-telemetry-20261114.md)及[cache256模型中断](f08-qwen3vl-cache256-detail14-20261114.md)保持。设备范围为iPhone16ProMax，但本轮仅宿主诊断。

## 冻结与执行

复用detail14原28输入及交替顺序，全部问题/标签/时间/像素/协议期望冻结，输入JSON SHA `ec097672b7a897c461e67baf2fae49eb303db74cf8a171a77bfbce6fcfe761c5`。新任务局部main直接调用原Qwen3VLProcessor.prepare/getRopeIndex，原min4096/max262144/targetFrames8和cache256MiB保持；不实例化ModelContainer或模型、不加载safetensors，原pinned对象/源码链接。只使用已验新sampler，3.5GiB/serious/180s守卫不变；未加autorelease干预。

运行的deny-network沙盒额外拒绝该权重文件的file-read-data，实际Python读取探针得到拒绝，`weight-read-denied.json`保留；不是只依赖报告自称未加载。可执行文件虽静态链接现有VLM实现，但测量main没有loadContainer、模型prepare或生成调用。读取本地tokenizer/config/PNG，MLX Metal用于processor张量与位置计算，非CPU模型。

测量前先实际preflight：篡改question的manifest在任何GPU/tokenizer或output创建之前因SHA不符拒绝，Swift顶层throw产生exit133，原stderr保留，非GPU/模型崩溃；精确原件preflight exit0。随后唯一新测量run `f08-qwen3-preparation-lifetime-20261114-host01`，实际PID80576，launcher PID80575，耗时3.215s、exit0，无stop。未重跑模型6111或补旧14题后缀，原分数不变。

## 阶段证据与结果

每题6个显式阶段：before-decode、after-decode、after-prepare、after-validation、after-return、after-clear；另2个tokenizer前后点。170条JSONL逐条写入并synchronize，原主报告424个显式/周期样本包含这些点。新async函数仅返回Data；after-return在该函数返回之后采，clearCache后再采。处理及报告路径同模型run不同，见下述限制。

28组像素SHA、完整tokens/THW/shape、三轴positions/delta精确同旧冻结，224时间值在0.501ns运输界内；不是重新生成期望。父校验`parent-results.json`通过，另5个篡改收据（像素、时间、漏阶段、负内存、不完整计数）均拒绝，不重执行processor。

| 同臂比较 | baseline14 | detail14 |
| --- | --- | --- |
| 第一次after-clear phys B | 239,764,416 | 368,673,872 |
| 第二次after-clear phys B | 309,232,672 | 369,165,392 |
| 最后after-clear phys B | 316,015,648 | 375,768,144 |
| 第二次至最后差 B | 6,782,976（约6.47MiB） | 6,602,752（约6.30MiB） |
| 全部after-return active | 0 | 0 |
| 全部after-clear active/cache | 0/0 | 0/0 |

首次baseline在首detail之前，第一次至最后约72.7MiB差含首次较大尺寸预热，不应拿它与其他稳态臂混比。detail首次至最后差7,094,272B，约6.77MiB，仍不能称绝对零增长。不同图像与累积JSON/采样/文件同步、宿主分配器及框架驻留都可能影响占用，未分配归属。

全采样最大phys378,963,024B（约361.4MiB）、RSS327,630,848B、active82,579,908B、cache82,604,214B；各自峰不同时刻，不求和作组成。MLXpeak82,579,980B是历史active峰。原after-return缓存约47/82MB，clear后0而同刻phys未立即下降，提示不能把“清缓存调用成功”当OS瞬时回收证明。

## 可以改变的下一步与不能下的结论

本轮支持“在此无模型处理边界和单次28输入条件下，单独反复prepare并未复现模型run约530MiB的匹配计数增长”。因此暂不盲加autoreleasepool或重复清CI缓存，应下一步在**新隔离模型诊断**中加prepare前后、prefill前后、返回和清缓存边界，保持相同输入/输出合同，观察增长首先出现在哪里。

这不排除预处理与模型/驱动/内存压力互动，也不证明prepared对象在原container async闭包中的释放等价。此main省略模型加载、vision/language prefill和token循环，复用一个processor而旧模型main每题重建，耗时也更短，并增加阶段日志与文件同步；不得把两个进程直接作唯一变量因果比较。没有vmmap归属或所有权实测，不能认定CoreImage泄漏、Metal泄漏、模型实现错误或仅OS缓存。来源质量16/36没有改善证据，手机持续热保护仍未验收。

## 验证与保存

原前序文件及2,471保护摘要、源码/输入/对象freeze、阶段与5负例、两PID终态/空GPU队列、链接语法和本任务精确module-cache清理见`final-audit.json`、`cleanup.json`及`final-file-freeze.json`。构建仅已有Device.setDefault弃用warning，无依赖更新。无新独立复审、下载、手机或生产更改。W6仍open/not-ready。
