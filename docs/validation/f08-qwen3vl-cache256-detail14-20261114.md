# F08固定14题缓存256MiB实验：10完整对后仍触内存守卫

2026-11-14。新run独立于[旧detail14中断](f08-qwen3vl-detail14-20261114.md)，复用其原28输入/顺序/模型/输出规则；两臂均显式MLX cacheLimit268435456B，采用[已验紧急_exit候选](f08-qwen3vl-guard-exit-20261114.md)，原3.5GiB/serious/180s阈值不变。唯一新host Metal PID16360完成21/28输入（10完整对+6111仅baseline），在6111-detail prefill再次越内存守卫；**按预期exit70，无SIGSEGV，experimentCompleted=false**。不补剩余输入、不并入原36题，W6open/not-ready。

证据`artifacts/verification/f08-qwen3vl-cache256-detail14-20261114/`；预注册计划`docs/superpowers/plans/2026-11-14-qwen3vl-cache256-detail14.md`。已知答案诊断，非独立质量benchmark；未操作手机、下载、改正式默认或依赖。

## 单次执行与可验证范围

全部28的原stagedpixels/tokens/positions/时间/问题/标签冻结保持；同一14selection SHA/顺序，baseline/detail仅旧解码cap512→1280、实际visual576→1008。cache256对两臂相同，新增报告读回字段268435456；safe-exit只在停止分支执行。新源从旧消费者仅改runID/claim/cache设定和已验sampler，不改模型数学/grammar。缓存旧19对象与原模型消费者源静态链接，源/对象/输入/权重execution冻结，GPU前队列为空，deny-network运行，无Torch/CPU模型/Ollama。

初始单个父校验器仍只取主报告sample峰；保留`parent-results.json`，新增`accept_resource.py`将独立stop纳入峰值，42完整151936维向量、argmax/允许集、严格解码/EOS/28KV/delta与输入协议再次核验，`parent-results-r2.json`为资源摘要裁定。修正仅离线checker，无模型重放。

## 10个完成的成对结果

| case | baseline→detail | 期望 | 诊断结果 |
| --- | --- | --- | --- |
| 4852/q9 | B→A | C | 两者仍错，选项改变 |
| 3377/q0 | A→A | C | 均错 |
| 6500/q1 | C→C | A | 均错 |
| 4096/q2 | A→A | B | 均错 |
| 7350/q0 | A→A | A | 均对，原对控制 |
| 2881/q0 | B→B | B | 均对，原对控制 |
| 1255/q1 | A→B | B | 一条错→对 |
| 8473/q0 | B→B | C | 均错 |
| 11259/q11 | C→C | A | 均错 |
| 875/q5 | A→A | C | 均错 |

完整对baseline2/10、detail3/10，1改对/0退化；仅3条细节不确定题完成对照，其中1改对；5条线索在的题仍0/5，2原对控制仍2/2。**缺少后4题完整对，不能将3/10称为固定14总体质量或证明普遍改善。**6111 baseline B/期望A，detail无答案；7277、7904、10179未开始。纸张纹理假设仍未完成模型检验。

该进程完成对的总耗时中位baseline270.818ms/detail497.681ms，仅宿主同进程观测；不与旧约2.75s手机数据当等价条件比较，也不能把单次进程warm-cache/编译/设备差异归因唯一cache设置。

## 资源与中断

| 证据 | 实测 |
| --- | --- |
| stop | 13823.723ms，phys3,772,632,064B（约3.514GiB）≥3,758,096,384B；thermal0 |
| 主report | 原`running`/completedRows21、active6111-detail；无伪completed |
| 主报告最后样本 | 13644.427ms，phys2,845,133,824B、active1,895,262,955B、cache252,686,476B |
| 主报告sample各自最大值 | phys3,748,252,648B；active2,984,117,524B；cache283,746,026B，各发生时刻不同 |
| 进程 | exit70；独立stop写出，无新崩溃/无后续臂 |

cacheLimit是缓存策略，不是瞬时硬内存上限：实际采样cache最大283,746,026B略高于配置值；不能宣称始终≤256MiB。活跃内存/缓存/phys必须分口径和时刻，stop缺同刻MLX分项，不能把最后样本当stop分配组成。缓存已不再达到旧sample1.46GB量级，但当前输入仍越守卫；**缓存无界不是充分解释，256MiB策略未使整轮资源通过**。Mac停止不证明iPhone同资源，也不代表真正OOM/jetsam。

## 更正旧文字与后续方向

旧detail14报告误写“4852原手机也是B”。原手机`f08-qwen3vl-valid36-20261114/ios-output/result.json`中4852为A/期望C；旧host baseline为B/期望C，本次host baseline仍B。报告顶部单列更正，原冻结/JSON/分数不改。输入像素同并不保证Mac与手机输出完全相同；其余已完成baseline与原手机答案同。此差异不能据本轮单变量cache比较唯一归因cache；host baseline前后未变。

下一先给紧急stop收据补足**同一个样本**的MLXactive/cache/peak并通过边界回归，同时检查每题处理/图片对象释放边界，区分活跃张量增长与宿主/CoreImage/缓存。不得靠再放宽guard、重复6111或无预注册扫cache数值“刷过”。质量上也不能继续只增加分辨率：已有五条可见线索仍错。新泛化改善仍需独立质量集和iPhone持续/预算/正式集成裁定。

本轮作者审计，无新独立复审；保护/旧实验完整性/42向量/语法空白链接/精确module缓存清理见`final-audit.json`。W6仍open/not-ready，原16/36、旧热停止保持。
