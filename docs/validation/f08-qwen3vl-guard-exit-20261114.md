# F08隔离守卫终止修正：绕过全局析构，原预算不变

2026-11-14。[原细节实验](f08-qwen3vl-detail14-20261114.md)的退出异常已有一个受控回归修正：新隔离ResourceSampler仅把两个紧急`exit(70)`改为`Darwin._exit(70)`，避免执行进程全局清理；原3.5GiB/serious/180s守卫、正常stop和报告写入保持。**这只修正探针的紧急退出机制，不是内存降低、识别改善或W6完成。**未重放模型/28臂、未操作手机、未改正式SDK或原证据。

计划`docs/superpowers/plans/2026-11-14-qwen3vl-guard-exit.md`；证据`artifacts/verification/f08-qwen3vl-guard-exit-20261114/`。

## 证据与回归

本机macOS `exit(3)`明确exit会调用atexit/flush/close；`_exit(2)`为直接进程终止。保留原手册。当前无Context7工具，未伪称查询远端最新文档。旧模型崩溃是sampler在exit全局析构、另一线程在Metal编码的同时现场；本次不强行归为唯一内部根因。

`check_exit.py`运行真实sampler分支和原子收据写入，只在测试生成文件替换遥测/热读数并用无MLX占位满足编译。atexit标记通过真实文件副作用检查。覆盖11条：default、nominal、footprint下界/等号、thermal下界/serious等号、elapsed下界/180s等号、报告写失败、采样错误、采样错误报告写失败。

| 检查 | 结果 |
| --- | --- |
| 原sampler RED | 正常5通过；6个紧急分支均错误运行atexit，真实失败 |
| 最小候选 GREEN | 11/11通过；紧急分支exit70且无atexit，正常exit0且atexit仍执行 |
| 收据与错误 | unsafe notCompleted/samplingOnly/run身份保持；采样错误phys/resident为null；写失败stderr仍可观察且退出 |
| 真MLX Metal小型检查 | 同缓存对象链接，512×512矩阵warmup实际值512；normal完成5000次小乘法并exit0，triggered写收据exit70且无atexit |
| iOS类型检查 | 新sampler单文件arm64 iOS18目标exit0；不是真机运行或完整App构建 |

Metal夹具仅将**测试副本**内存阈值替为1字节以避免主动分配3.5GiB，候选保持原阈值。触发样本69,272,176B/0.024ms，正常样本峰71,533,168B；均无模型。由于触发可以早于循环首个新命令，不能把此夹具称为确定重现了原prefill期间的并发现场，也不证明所有驱动析构异常都消失。可验证范围为已完成真实Metal warmup后，所测守卫路径可靠exit70且不进入atexit。父进程可回读原子收据，不是断电持久性证明。

`Darwin._exit`不会flush未提交的stdio；因此紧急边界依赖已经写完的独立stop收据，主报告保持原partial，不尝试伪造completed。真实模型消费者尚未采用本候选，原exit139/stop/report不改。

## 内存组成线索

`memory-analysis.json`仅读取旧遥测/配置/safetensors header，无模型或张量分配：旧基线末持久化样本在3906.678ms为MLXactive1,895,534,922B、cache1,461,798,918B、phys3,586,427,808B；后续detail4474.441ms独立stop的phys3,791,358,976B**不含同一时刻active/cache**。不能把两个时刻拼成确定的峰值分配或将不同口径相加。

首对实际packed输入13.5→23.625MiB，增加10.125MiB；按两字节假设、无capacity取整的文本KV尺寸约73.72→120.97MiB。它们是单张量静态尺寸，不能当瞬时峰值或精确释放量。当前1024/16=64的visionhead走已有per-image fusedSDPA，不应套用代码中其他型号1152/16=72的注释，臆测存在联合序列denseattention矩阵。

固定MLX `Memory.swift`说明active+cache才是MLX已分配总量，默认cacheLimit随memoryLimit可能很大，支持为内存受限应用显式减小缓存。**缓存约1.46GB是值得受控验证的资源候选，不是已证实越界唯一原因。**下一可新冻结显式256MiB缓存上限、保留原输入/守卫的资源对照；不能续写原28臂或称手机内存已通过。

## 原始工具失败与审阅

首派生脚本误断言exit字面出现5次，实际为4次（2代码/2注释），在写候选前失败；依赖测试随后缺文件也失败，均保留`derivation-first-failure.json`和空green目录，真正GREEN为green-r2。首次静态对象链接缺cLogSupport模块，按已记录pitfall添加精确缓存modulemap/header/object后成功；未装包或改全局配置。最终链接仅原Device.setDefault弃用warning，iOS类型检查无输出。

本次作者审计，无新native/helper调用或独立复审。最终保护/旧实验原文/源码差异/11回归/小型Metal/类型检查/语法空白链接/精确清缓存收据见`final-audit.json`。旧2471保护文件保持，W6open/not-ready、goalactive，内容质量仍16/36且真机连续热失败未恢复。
