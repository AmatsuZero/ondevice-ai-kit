# F08：Qwen3 在线选择语法已通过宿主和 iPhone 生成，内容错误仍在

2026-11-14。隔离候选把已验证的 A/B/C→EOS 规则接入实际 Swift 生成循环。宿主和 iPhone 各完成同一组原valid18：**14/18、invalid0**；原无约束手机基线 **9/18、invalid6** 完整保留。五条纯格式失败转正确，另一个格式失败的首选项本来就错，四条内容错误均仍在。**格式阻塞在该隔离候选中已修复，F08整体仍open/not-ready。**

这次是新模型推理，不是旧向量离线回放或截取首字母；同一旧18题只能验证集成行为，不能当新独立benchmark或模型泛化提升。未修改正式App、共享SDK默认或依赖锁。

## 实现与验证范围

[宿主计划](../superpowers/plans/2026-11-14-qwen3vl-online-choice.md)、[手机计划](../superpowers/plans/2026-11-14-qwen3vl-iphone-choice.md)。证据根：`artifacts/verification/f08-qwen3vl-online-choice-20261114/`。

- 复用 `mlx-community/Qwen3-VL-2B-Instruct-4bit` revision `9c4f5209e57b31f4b9dfba735de3fb983739c9cc`、原18题顺序/八PNG/问题/选项/严格评分、已验Swift时间块和位置修补、balanced512/temperature0/32token安全上限。
- `candidate/ChoiceGrammar.swift` 从原已验源逐字节复制。生成时使用实际checkpoint tokenizer确认A/B/C为单token32/33/34，从实际generation配置核EOS151643/151645。
- `ModelSuite.swift` 在当前完整logits中选择允许集合的argmax，选中的token进入实际 `nextTokenLogits`；下一步仅允许EOS，终态必须恰好一个字母+EOS。问题、答案标签、评分均不进入语法选择器。
- 保存每步未遮罩151936维logits、全局原argmax、允许集合/阶段、实际token、28层offset和carried rope delta。每题fresh cache，不读取旧logits生成新答案。
- 手机版本与宿主版本的选择/模型循环相同，只更改runID和host/device声明；`ios-probe/ModelSuite.swift` 与手机实际构建绑定。

## 实际执行证据

| 门 | 证据与结果 |
| --- | --- |
| 行为RED | 新输出合同对原真实手机结果失败，精确检出6条格式失败；无模型重跑。`contract-red*` |
| 语法合同 | 原24项检查重新执行通过，含首EOS/解释抑制、tie、非法token集合/向量和失败不推进状态。`grammar-checks*` |
| 主机编译 | 新macOS目标Release成功，冻结源保持；11个缓存依赖revision与原resolved一致。`build-result.json`、`dependency-revisions.json` |
| iOS类型检查 | 34个Swift源通过。首轮缺yyjson，按既有modulemap补命令后通过，原失败保留；只有原AVAsset及Device.setDefault弃用warning。`ios-typecheck-r2-result.json` |
| 宿主新推理 | 一个断网sandbox下的MLX Metal进程完成18/18，严格14/18、invalid0。`host-output/`、`model-process.json` |
| 手机构建/签名 | 独立 `io.ondeviceai.f08qwen3choice20261114` Release构建及deep/strict签名校验通过，既有profile/原entitlements未改。`ios-build-result.json`、`ios-signing-receipt.json` |
| 手机新推理 | 同一PID8105、唯一launch；18/18终态双读逐字节一致，严格14/18、invalid0。`ios-terminal-receipt.json`、`ios-output/` |
| 独立验收 | 两臂共72个完整有限向量：全局及允许集argmax、BPE解码、EOS、28层offset、rope delta和原协议/像素均通过。`parent-verification.json`、`ios-parent-verification.json` |
| 手机恢复 | 新候选及其模型容器已随卸载移除，无候选进程，45原App完整元数据相同，恢复UgreenHome。`ios-restoration.json` |

仅传输已有权重至新候选容器，没有下载、新模型、Torch、CPU模型、Ollama推理、新媒体采集或依赖/default修改。每次推理前ComfyUI/Ollama队列为空。宿主完成后才开始手机模型臂。

## 答案、前缀与数值

两臂18个首选项均与原无约束手机相同。手机6条第二步原全局argmax被EOS规则替代，分别为4505、78、7063、3469、10278、1550；宿主仅5条，7063在该宿主第二步的原argmax已经是EOS。这是实际向量上的观察，不能按旧格式失败数量预写遮罩次数。

手机36个第一/第二步完整向量与原手机同前缀向量**逐字节相同**：本次可直接验证规则改变了允许输出/停止，而未改变这些前缀上的模型分数。这个精确一致范围仅限本次共同前缀；不推论所有平台/内核都数值等价。宿主相对旧手机36向量均不完全相同，相对RMSE为0.018426–0.087586，尽管最终首选项相同。

| 内容错误 | 正确 | 两臂输出 |
| --- | --- | --- |
| video_10249 字母逆序 | A | C |
| video_4505 首个物品引用 | B | C |
| video_9676 不同物品计数 | B | C |
| video_9500 动作次数 | C | A |

9676同八帧覆盖对照也已失败，见[内容诊断](f08-qwen3vl-oracle-eight-20261114.md)。在线语法不能修正这些合法选错。

## 资源观察

| 指标 | 宿主Mac | iPhone16ProMax |
| --- | --- | --- |
| 整臂计时 | 9.816s | 56.018s |
| 模型加载 | 315.742ms | 627.107ms |
| 每题完整准备/生成中位 | 256.227ms | 2748.438ms |
| sampled phys_footprint峰 | 3,723,611,304B | 3,171,831,472B（约2.95GiB） |
| 资源样本数 | 682 | 5063 |
| thermal raw起→止 | 0→0 | 0→0 |

沿用3.5GiB/serious/180s采样诊断guard，均未触发；它们不是批准的产品预算，采样不保证瞬时峰。时间含输入验证/准备等既有定义，不能将宿主数据替代手机或宣称正式冷启动优化。

## 下一步与未过门

本分支完成，停止重跑同一18题。在线语法仍只在隔离实验候选中，尚未成为正式SDK/App配置；是否及如何采用，需要与明确产品输出合同相符。

用户随后确认“先按这个测试机”，本轮设备范围固定iPhone16ProMax，不再等待最低机型选择。其他机型/Android是后续跨平台验证，不作为这一轮iPhone评估前置；该回复未批准现有时延/内存或质量结果。

当前主要未过门是内容可靠性与产品范围：通用语义、身份去重、动作计数不能仅凭14/18的旧题集接受；需要事先定义的产品任务及独立质量数据。当前设备范围已定，加载/端到端耗时、内存和持续热运行仍缺准入验收，正式采用与生产集成未完成。Android质量另列后续。保持F08/W6 open/not-ready、总goal active。
