# F08模型阶段20：活跃张量归位，进程占用仍增长

2026-11-14。唯一新host Metal run完成预冻结原顺序前20输入，40完整向量与200阶段记录验收通过，exit0、未触guard。所有题返回后active精确回到1,781,949,568B，clear后cache0，但同臂预热后phys仍增长约268–290MiB。**跨题活跃张量/KV持续累积不是本次观测，仍缺增长的分配归属；不等于资源修复或手机验收，W6open/not-ready。**

证据`artifacts/verification/f08-qwen3vl-model-lifetime20-20261114/`；[计划](../superpowers/plans/2026-11-14-qwen3vl-model-lifetime20.md)。此前[无模型预处理28](f08-qwen3vl-preparation-lifetime-20261114.md)同臂预热后仅约6–7MiB增长，不能单独解释原模型增长。

## 身份与执行

原detail14冻结28的精确prefix20（十对，末875-baseline），6111和后续输入在推理前排除；新run非原28续跑，也不补失败纸纹题。原模型revision9c4f5209/权重SHA、runtime、8输入/实际时间、processor cap262144、视觉576/1008、balanced512/temp0/max32/在线A/B/C→EOS、cache268435456B和3.5GiB/thermal2/180s守卫保持。

新consumer只改run/claim、prefix20与阶段观测；派生收据可逆恢复原ModelSuite字节，模型数学、grammar和clear位置未改。新sampler逐字节同已验stop遥测候选。PhaseLog真实Swift夹具验证追加写不截断旧内容、值精确运输、采样失败不写、文件错误传播，非模型测试。现有对象编译通过，仅已有Device.setDefault弃用warning，无依赖更新。

唯一新run `f08-qwen3-model-lifetime20-20261114-host01`，launcher92477、实际模型PID92478，14.059s、exit0，report completed20；原cache256 running21/exit70和旧detail14 running1/exit139保持，不混用。此20是故意有界诊断，**不称原28资源通过、连续36手机通过或新的质量benchmark**。

## 输入与阶段验收

20题完整tokens/实际packed pixelSHA/THW/shape/positions/delta与原冻结精确；40个151936维完整有限logits、allowed argmax/实际解码/EOS/28KV offset核验。原host前20答案20/20相同，40/40向量逐字节相同；这支持本轮数值行为保持，不证明未来所有设备/输入不变量。保留原16/36，不更新质量分数。

每题10阶段：before-perform、before-decode、after-decode、after-prepare、after-input-validation、before-prefill、after-prefill、after-generation、after-perform-return、after-clear。200个同步写JSONL点全部属于最终1316个ResourceSample、严格原顺序，after-prefill在logits/cache eval之后；新增采样/文件同步会影响时序。真实副本正控与5篡改（pixel、选择token、阶段顺序、KV、行数）全通过/拒绝，无模型重放。

## 结果与边界

| 指标 | 实测 |
| --- | --- |
| 所有after-perform-return active | 同1,781,949,568B |
| 所有after-clear cache | 0；active同上 |
| baseline第二至最后after-clear phys | 2,457,127,888→2,761,640,936B，+304,513,048B（约290.4MiB） |
| detail第二至最后after-clear phys | 2,467,220,432→2,748,222,440B，+281,002,008B（约268.0MiB） |
| 采样phys最大 | 3,750,611,944B，距离3.5GiB守卫仅7,484,440B（约7.14MiB） |
| 采样RSS/active/cache各自最大 | 2,483,650,560 / 2,984,117,524 / 283,746,026B，不同时刻 |

首次baseline未经历detail预热，因此不以首次到最后的约616MiB差当稳态泄漏；同臂第二点比较也仍受输入/顺序/运行时间影响，不等于固定每题增长率。post-load active约1.782GB，题后同值，说明本次观测中每题张量已回到共同水平；不证明底层缓存、驱动或宿主分配无保留。

同题阶段差中位，baseline/detail的before-prefill→after-prefill phys约+318/+339MiB；prepare/输入核验期间phys却可下降而RSS上升，说明口径和回收时序复杂，不能把一段差当分配所有权或持续增长唯一来源。clear时cache约269MB→0，phys只下降约61/97MiB，其他时间仍会变化。详见`lifetime-analysis.json`的独立字段/同臂/阶段差，不把RSS、phys和MLX相加。

新增源码复核：pinned PrefillParameters.forEachChunk**已逐chunk autoreleasepool**，Cmlx Metal eval/allocator/device也已有scoped pools；原consumer逐token池亦在。不能从“整个prefill调用外没有池”推导必然缺池泄漏或盲加外池。`upstream-lifetime-source-freeze.json`记录实际文件，Context7当前无可调用工具，使用所运行的固定源码，不冒充最新在线文档。

下一有区分力步骤是在新隔离run、少量固定检查点收集**活进程vmmap summary**，定位增长主要落在malloc/VM/IOKit/其他区域，保持模型数学与守卫；预先验证工具能读目标并记录诊断暂停/开销。未取得归属前不更改池/分辨率/缓存预算。summary也不等于准确责任栈或真正泄漏，须按实际结果再选单变量控制。

## 保存与状态

本轮作者验收，无新独立复审/手机/下载/正式默认变动。2,471保护、上轮完整性、源码/执行freeze、原分数/中断、空GPU队列/两PID结束、链接语法及本分支精确module-cache清理见`final-audit.json`、`cleanup.json`、`final-file-freeze.json`。本次20完成不关闭资源门；内容16/36、真机持续热失败、产品预算/集成门保持，W6open/not-ready。
