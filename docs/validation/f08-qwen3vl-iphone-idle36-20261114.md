# F08 iPhone36有限空闲遥测：观察到回落，未达到128MiB观察阈值

2026-11-14，iPhone16ProMax实际Metal，唯一新PID8709完成原36条，无保护停止。72个完整有限151936维向量、所有非计时row字段同原手机，360阶段/图像weak题后全nil；原内容16/36不变。4个固定800ms空闲点均完成，题后回落0.625/127.34375/60.921875MiB，**没有一个达到预设≥128MiB观察阈值**。不把127.34四舍五入成128通过，也不延长等待续扫。W6仍open/not-ready。

[预冻结方案](../superpowers/plans/2026-11-14-qwen3vl-iphone-idle36.md)，证据根`artifacts/verification/f08-qwen3vl-iphone-idle36-20261114/`。隔离候选`io.ondeviceai.f08qwen3idle3620261114`，显示`F08 Qwen3 Idle36`，已卸载；45普通App完整元数据与安装前相同，UgreenHome已恢复。普通模型/依赖/默认不改，无新下载/媒体采集/helper/native调用，作者核验非独立复审。

## 输入及执行合同

从已验iPhone graph36消费者只变身份/声明、显示名及load/count2/18/36处的settling观察，可逆还原原消费者。Qwen3-VL2B4bit固定revision9c4f5209、runtime6e338e5d、原36题/八帧/时间/512/在线A/B/C→EOS、direct graph数学精确保持。手机保持原cache策略，未复制host256MiB override。每处同步当前GPU stream，before sample→await800ms→after sample、不可重复的原子收据；下一prefill在after之后，sampler/3.5GiB/thermal2/180s守卫全程保持。

预先无模型夹具实际编译/运行：等待≥800ms、样本阶段运输、已消费/非法count拒绝、两处采样错误向上传播且不留伪完成收据。复用旧exact direct processor28/host20前置，明确不是新host或processor推理。iOS实际xcodebuild86.73s退出0、只用既有缓存包/Package.resolved精确保持；既有授权的profile/entitlements只读复制，签名验证0，无新签名材料。

当前设备初查解锁，安装/启动前进程与锁状态核验，未见其他F08/xctest冲突。App安装、模型传输、唯一launch、同PID观测、两次终态report逐字节一致；全部72向量及phase/weak/4settling文件收取校验。原collector沿用的`collection.json.continuous36DiagnosticCompleted`只数36行，不能作吞吐证据；`collector-qualification.json`和正式phone-acceptance明确continuousThroughputValidated=false，插入等待合同单列。

## 真机观测

| count | before phys MiB | after phys MiB | 回落MiB | 实际采样间隔ms | active before=after | cache before=after |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| load0 | 1795.878 | 1795.878 | 0 | 810.816 | 1,781,949,872B | 4,240B |
| 2 | 1835.191 | 1834.566 | 0.625 | 807.514 | 1,781,949,568B | 0 |
| 18 | 1932.207 | 1804.863 | 127.34375 | 804.633 | 1,781,949,600B | 0 |
| 36 | 1944.238 | 1883.316 | 60.921875 | 802.119 | 1,781,949,600B | 0 |

每个等待区间的active/cache保持，跨题active仍较绝对基线多32B，未定位来源；不将微小字节漂移改成严格恒定通过。题后clear cache均0，图像weak槽全nil。第2→第36题即时after-clear phys仍增长118,751,352B（113.25MiB），第2→第36题wait-after也仍增长48.75MiB，因此“等待后回落”不是长期无增长/已settled/无泄漏证明。两者是不同时间边界，不能混用。

采样phys峰3,081,293,368B约2.870GiB，thermal normal0→fair1。总125,304.27ms包括预设3,200ms等待，不是无等待连续吞吐；原无等待graph36总122,256.61ms/峰3,214,249,576B作为历史描述，非同时随机对照，峰较低不能唯一归因等待。MLX/RSS/phys不可相加，当前同步stream不保证所有后台线程/驱动冻结。

## 裁定

Mac上不用vmmap等0.8s可回落约278–397MiB，手机本轮只见0.6–127.3MiB，未达到冻结阈值；不能直接搬用Mac字节/基线/回收节奏。原手机196MiB残余不再只按即时值称永久泄漏，但此轮也未解除稳态/预算门。保留观察事实、不放宽阈值/延长等待或重跑原36刷结果；不把sleep加入生产作为修复。

资源诊断已使当前边界更清楚，**识别内容仍是独立主阻塞**：16/36与高细节3/14→4/14不变，Qwen3VL2B4bit当前配置不准可靠通用视频理解采用。下一优先回到质量原因，审查同权重/同输入在独立MLX实现的关键边界，区分runtime计算差异与模型/任务能力；保持Metal、隔离、无PyTorch/CPU模型和原固定质量成绩。正式采用/集成回归、产品预算仍未过。

最终源码/权重/旧证据保护、设备普通App恢复、所有进程终态及精确自有缓存清理见final-audit/terminal file freeze。
