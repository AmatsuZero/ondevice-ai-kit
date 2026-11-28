## 2026-11-16 Qwen3.5 SFT 36/valid18正式完成，证据通过但质量门失败

[冻结协议与结果](validation/f08-qwen35-video-sft-train36-valid18-plan-20261115.md)，证据`artifacts/verification/f08-qwen35-video-sft-train36-valid18-20261115/`。用户授权主代理直接实现后，runner、监督器、独立verifier与RED→GREEN合同检查均落地；同一MLX Metal进程按固定顺序执行baseline train36、baseline valid18、36次AdamW更新、post train36、post valid18，未使用Torch/CPU模型/Ollama且未保存adapter。train `18/36→34/36`、mean loss `0.506275→0.148356`；valid `9/18→8/18`、mean loss `0.656392→0.370421`。valid有2条非camera/semantics错→对，但有3条对→错，净正确数-1，未达至少+2且回归最多1条的预注册门。峰MLX11,174,146,446B、最低磁盘38,859,603,968B、supervisor exit0/stderr0。独立verifier明确`evidenceVerification=passed`、`experimentStatus=completed`、`qualityGate=failed`。因此配方证明训练拟合和loss下降，但没有独立正确率泛化；停止该配方，不在valid18上调learning rate/epoch/顺序/prompt/门槛，不保存adapter、不进iPhone、不改App/SDK默认。F08/W6仍open/not-ready。

## 2026-11-15 Qwen3.5视频SFT 12/12 pilot质量门失败

[报告](validation/f08-qwen35-video-sft-pilot24-20261115.md)，首轮证据`artifacts/verification/f08-qwen35-video-sft-pilot24-20261115/`，顺序控制证据`artifacts/verification/f08-qwen35-video-sft-pilot24-order-control-20261115/`。从expanded72排除旧pilot12后冻结12train/12held-out，24不同视频、每组六类各2、A/B/C各4，selection SHA`5f3d3495…`，不使用模型结果。预注册门要求held-out accuracy不退、mean loss下降、至少两个非camera/semantics类别改对及资源边界。首轮原顺序实际`AAAABBBBCCCC`，train11/12→10/12、held-out9/12→8/12、loss0.425835→0.521036；保留失败后只改训练顺序为确定性`ABCABCABCABC`，两轮baseline稳定字段逐项完全一致，控制臂train11/12→12/12且loss0.320435→0.000613，但held-out仍9/12、loss升至0.556318，零新增改对。两轮峰MLX约12.842GB、最低磁盘40.553GB以上、exit0/stderr空、adapter未保存。故旧6+6的2/6→3/6不可当可重复泛化信号；当前配方只证明记忆，不支持保存adapter或跑valid36。若继续SFT须先冻结更大训练设计和独立验证集，不在当前held-out上调参。W6/F08仍open/not-ready、goal active。

## 2026-11-15 Qwen3.5视频SFT smoke与6+6微型pilot完成

[报告](validation/f08-qwen35-video-sft-smoke-20261115.md)，证据`artifacts/verification/f08-qwen35-video-sft-smoke-20261115/`。固定Qwen3.5-2B-4bit、MLX0.32.4/Metal与8帧video_9431，显式`<|video_pad|>`后8160视频token/特征精确匹配，completion仅3token且无截断；plain prompt负对照有视频特征但0视频token，证明row必须显式占位。完整BF16 logits `[1,8246,248320]` forward通过、峰MLX12.162GB。模型已有`logits_to_keep=3`与`return_hidden+lm_head`两路逐元素/loss完全相同，把理论投影从4.095GB BF16/8.190GB FP32缩为1.490MB。LoRA rank8/alpha16默认语言侧186模块、8.410M trainable，checkpointing下单次backward梯度finite/nonzero；AdamW一步实际改变参数。独立4步单样本loss `0.21670→0.00111`，更新后eval `0.000179`，下降99.92%。随后冻结不依赖模型结果的6 train/6 held-out选择，六类与A/B/C均衡、12个不同视频；6步后train `4/6→6/6`、held-out `2/6→3/6`，held-out mean loss `0.551249→0.319344`，唯一新增答对为semantics，memory/abstraction/physics仍错。pilot峰MLX11.062GB、最低磁盘40.575GB、exit0/stderr空，adapter未保存，项目依赖/App/SDK默认未改。held-out仍来自官方train split且只有6条，只是积极早期信号，不是valid36或产品质量改善。下一应冻结12/12或18/18 pilot并预注册跨类别门；W6/F08仍open/not-ready、goal active。

## 2026-11-15 Qwen3.5 valid36完成：崩溃已修，质量17/36未晋级

[报告](validation/f08-qwen35-valid36-20261115.md)，证据`artifacts/verification/f08-qwen35-valid36-20261115/`。首次模型加载后第一题exit-5/0row，经单题原子marker定位：processor、pixel、外层rope、vision forward与576特征/token合并均成功，最后停在`prepareContinuation`内部`getRopeIndex`。根因是`Qwen35`两处调用漏传checkpoint `visionEndTokenId=248054`，落入Qwen3默认151653，视频position合同找不到end token后`preconditionFailure`/SIGTRAP；不是模型本身“无法识别”。隔离runtime只补两处参数，同题exit0/完整logits。原probe还错误要求24层offset相同；已按pinned测试订正为6个full-attention层检查offset、18个Mamba层检查两个recurrent state。新的唯一Mac Metal valid36 exit0、36/36、72完整logits/EOS/rope/cache独立通过，严格**17/36、invalid0**，对Qwen3仅+1（both10/Qwen3.5-only7/Qwen3-only6/both-wrong13）；峰phys3,784,232,104B、MLX2,721,266,819B为Mac观察。未达20/+4，明确不进iPhone、不改App/SDK默认。两行修复另已整理为[独立上游补丁](validation/f08-qwen35-upstream-patch-20261115.md)，同时通过pinned revision和检查时upstream main的apply check，项目依赖未改。运行时阻塞已关闭，但Qwen3.5候选质量失败，W6/F08仍open/not-ready。

## 2026-11-15 下一候选下载门完成：仅Qwen3.5-2B 4-bit晋级准备

[下载门报告](validation/f08-next-candidate-download-gate-20261115.md)，证据`artifacts/verification/f08-next-candidate-download-gate-20261115/`。重核官方及当前MLX元数据后，唯一eligible候选为`mlx-community/Qwen3.5-2B-4bit`固定revision`674aaa7240b91e8012fcad5d791b7dfe5ba90207`，仓库1,749,081,927B、权重1,722,271,785B。官方Qwen3.5-2B相对Qwen3-VL-2B的VideoMME无字幕/VideoMMMU/MLVU/MVBench为69.0/62.1/76.2/64.9对62.1/54.1/69.2/64.5；纠正准备摘要中旧MVBench数字。pinned Swift revision`6e338e5d...`已注册`qwen3_5`/`Qwen35`/`Qwen3VLProcessor`/fallback resolver，但官方时间戳视频prompt、真实BPE/token IDs、THW/三轴positions/rope delta仍未证明。LFM3因1000万美元商业许可阈值及Swift PR仍open阻塞；Gemma4 E2B it 4bit权重3.307GiB只留约198MiB诊断余量而no-go；LFM2.5缺原生视频合同，MiniCPM-V超出MLX Metal执行面。此门零下载/推理/手机/Torch/CPU/Ollama；下一先冻结下载与协议/valid36计划，再按固定revision下载，Mac需≥20/36且相对Qwen3≥+4才可进iPhone。F08/W6仍open/not-ready、goalactive。

## 2026-11-15 valid36双模型离线路由最高19/36，本地候选路线耗尽

[双模型处置报告](validation/f08-valid36-ensemble-disposition-20261115.md)，证据`artifacts/verification/f08-valid36-ensemble-disposition-20261115/`。只读既有Qwen3/Smol valid36首步完整logits，预先冻结四种通用规则与20/36、相对Qwen+4双门；独立验72个logits SHA/词表/输出token并重算通过。Qwen基线16、Smol14；raw margin18、归一化margin最高19（+3）、低entropy15、等权softmax17，均未晋级；oracle上限21。9题都对、15题都错、12题分歧，达到20要求路由至少选对11/12，最好规则10/12；不允许在同集按答案拟合阈值补1题。双权重仅文件已合计3.018GiB，质量门先失败，故不做双模型手机资源运行。结合Qwen2原始权重no-go及更小候选旧证据，已知本地F08候选没有剩余有独立质量依据的未测分支；下一只能收窄产品任务合同，或评估新模型/训练、蒸馏方案。无新推理/下载/手机/生产/helper，F08/W6仍open/not-ready、goalactive。

## 2026-11-15 Qwen2-VL原始权重valid36处置：no-go，不启动模型

[只读处置审计](validation/f08-qwen2vl-bf16-valid36-disposition-20261115.md)，证据`artifacts/verification/f08-qwen2vl-bf16-valid36-disposition-20261115/`。Qwen2原始BF16值提升FP32在旧fixed24为14/24，低于Smol2.2B4bit的16/24；相对Qwen2 4bit的四项严格改善仅两项为内容改对、两项为去句点，仍九项合法选错。原始BF16 tensor payload 4,418,050,768B/4.114GiB，是4bit权重3.546倍，单独已超过现有3.5GiB实验保护线，且当前无streaming/offload证据；已有4bit真机峰phys2.213GiB。故valid36最多补充非移动容量信息，不能改变当前移动候选决策，`runQwen2OriginalWeightValid36=false`，不做Mac模型运行或iPhone36题。此结论不证明原始权重理论上不能达到20/36；3.5GiB也不是批准产品预算。重开仅限明确接受该资源包络、实现流式/卸载、出现有独立质量依据的移动量化/蒸馏候选，或任务改为非移动容量研究。无下载/PyTorch/CPU/Ollama推理/手机/生产/helper变更，F08/W6仍open/not-ready、goalactive。

## 2026-11-15 Smol2.2B valid36完成：14/36，未达手机晋级门

[报告](validation/f08-smolvlm2-valid36-20261115.md)，证据`artifacts/verification/f08-smolvlm2-valid36-20261115/`。复用冻结Qwen3 valid36的36×8原PNG/实际时间/问题选项，不复制媒体/下载数据；独立生成Smol384px/81-token输入。唯一Mac MLX Metal进程36/36 exit0，严格14/36、invalid1；既有Qwen3为16/36、invalid0。逐题both9/Smol-only5/Qwen3-only7/both-wrong15，净值-2；预注册需≥20/36且净值≥+4，故`advanceToIPhone36=false`，停止该候选、不做手机36题。反事实4/6、相机4/6、记忆/物理/语义各2/6、抽象0/6；换Smol未解除共同抽象/计数/时序质量阻塞。288帧/36像素模板/86全logits含EOS/24层offset及保护哈希通过；峰MLX2.429GiB、模型循环20.826s仅Mac观察，不与Qwen手机资源直接比较。Torch/Torchvision不存在，无CPU模型/Ollama推理/设备/生产改动。共享ollama服务为空模型，原过严“无进程”条件在推理前修正并保留初稿/收据。**下一不再追这两个2B4bit候选的同题算子/输入微调；应先明确更窄产品任务合同，或仅评估有新独立质量依据的模型/训练方案。F08/W6仍open/not-ready、goalactive。**

## 2026-11-14 iPhone有限settling36完成：0.6/127.3/60.9MiB回落，未达128MiB观察阈值

[报告](validation/f08-qwen3vl-iphone-idle36-20261114.md)，证据`artifacts/verification/f08-qwen3vl-iphone-idle36-20261114/`。新隔离F08Qwen3Idle36、唯一PID8709 completed36/无stop，72完整向量/所有非计时row同旧，360阶段/weak图像题后全0。保持原手机cache/math/36inputs，0/2/18/36各800ms等待；三题后回落0.625/127.34375/60.921875MiB，无一≥冻结128MiB，不四舍五入/延长等待续扫。active各wait前后同，但跨题+32B，未认严格恒基线通过。峰phys2.870GiB、normal→fair、total125.304s含3.2s等待；即时clear第二→最后仍+113.25MiB、wait-after仍+48.75MiB，非稳态/长期预算通过或原连续吞吐修复。原collector的continuous36字段仅行数，qualification明确false，不作吞吐证据。候选卸载/45普通App完整元数据保持/UgreenHome恢复，1095源/模型/旧证据保护通过，限定自身缓存清理，未新下载/改生产/helper/native。原16/36/高细节3/14→4/14与质量/预算/正式采用门保持，W6open/not-ready、goalactive。**下一优先回到质量根因：同权重/同输入独立MLX实现边界审读/对照，区分runtime与模型能力，Metal/隔离且不用PyTorch/CPU模型，不再扫旧题prompt/分辨率/等待。**

## 2026-11-14 idle归属与纯等待完成：部分phys会延迟回落，非手机稳态通过

[报告](validation/f08-qwen3vl-idle-reclamation-20261114.md)，证据`artifacts/verification/f08-qwen3vl-idle-{vmmap,wait}-20261114/`。两独立Metal进程各28/exit0，112完整向量/全部非计时row同旧、560阶段/图像题后weak全0。VM PID95396在同步stream+exact ack阻挡count0/2/14/28，前后report/phase/vector清单不变，四次真正idle摘要：IOSurface0→16→3.95→2.27MiB、graphics打印均1.7G，MallocSmall/empty驻留增而allocated仅39.1→54.3MiB，非owner/泄漏证明。另PID7150不调用vmmap、只等0.8s，三题后phys下降397.39/297/277.83MiB；每wait MLX同/cache0，但跨题active较绝对基线多32/64B，原恒基线checker失败保留，新observer明确predeclaredCriterionPassed=false，不放宽门/重跑。证实大幅回落可无VM工具发生，但异步wait改变调度，不归唯一driver/宣称手机196MiB已解决或长期稳定。下一可隔离手机增加有限settling遥测，保持连续与idle合同分开，不用等待冒充吞吐通过；质量16/36/高细节3/14→4/14仍独立主阻塞，W6open/not-ready、goalactive。双final输入保护/终态通过，仅清自有缓存；无手机/新下载/生产/helper/native变更。

## 2026-11-14 真实模型报告scope对照完成，收益不稳健，暂不采纳

[报告](validation/f08-qwen3vl-model-report-scope-20261114.md)，证据`artifacts/verification/f08-qwen3vl-model-report-scope-20261114/`。rawPID60714/scopedPID63016各唯一host28/exit0，112完整向量及所有非计时row同旧，560阶段/weak题后全0，10输入+6输出破坏拒绝。原报告body同步pool的预注册全区间增长判据通过（54.80→−2.41MiB），但第2题起点差53.13MiB、最后占用仅差4.08MiB；baseline同臂54.73→58.97MiB反而增，detail76.00→55.94MiB仍增。两组normal→normal/cache256，峰3.226/3.215GiB；不能将全区间阳性当稳定内存收益，更不能从原手机196MiB相减。不采纳为已证修复/不直接手机重跑，原ModelSuite/正式App不改。下一真实模型明确idle阻挡点的VM/宿主归属证据，勿跨prefill采集后称idle或继续盲扩pool。原质量16/36、高细节3/14→4/14、iPhone16ProMax范围与预算/采用/集成门保持，W6open/not-ready、goalactive。506输入/旧证据全SHA在更新living docs前通过，双方终态/只清自身缓存；无新helper/native。

## 2026-11-14 无模型报告回放阳性，实际模型路径待验

[报告](validation/f08-qwen3vl-report-replay-20261114.md)，证据`artifacts/verification/f08-qwen3vl-report-replay-20261114/`。原手机36row/11411typed样本按after-return前缀回放，两新串行host进程各36/exit0、36对输出SHA精确，三破坏拒绝；仅同步report body加pool使第2→36题phys增长118.78→21.20MiB，减少97.58MiB/82.15%，达到预注册判据。明确外层pool覆盖同步循环，非真实async或手机池行为复现，不能据此把手机196.16MiB全部归因/直接相减。无MLX/模型/手机/下载/默认变更，原ModelSuite未改。下一另冻实际消费者报告作用域对照，保持输入/math/guard，完整向量和报告/资源验收后才考虑手机；不重跑质量集刷分。原16/36、高细节3/14→4/14与质量/预算/集成门保持，W6open/not-ready、goalactive。当前无新helper/native调用，作者审计非独立复审。

## 2026-11-14 graph修复后固定14细节对照完成，质量仍阻塞

[报告](validation/f08-qwen3vl-graph-detail14-20261114.md)，证据`artifacts/verification/f08-qwen3vl-graph-detail14-20261114/`。复用原28输入/实际时间/顺序，唯一新hostPID3356 completed28/exit0，56完整向量及280阶段/weak通过，前20/40向量同旧direct20。baseline3/14→detail4/14，仅1255错变对、零退化；五线索可见仍0/5，六细节不确定0/6→1/6，三个原对control3/3保持，6111细纹仍错。峰phys3.223GiB/normal→normal/cache256与原守卫保持，非高细节手机验证/独立benchmark；原16/36及旧running/1/139、running/21/70不改。图像题后槽均0，phys同臂仍+44/64MiB，非内存完全解决。固定14假设结束，不再扫同题分辨率/prompt/帧；当前配置仍不准通用视频理解采用。下一有区分力的新假设/独立质量或残余内存归属，W6open/not-ready，goalactive。

## 2026-11-14 直接graph作用域真机连续36完成，仍有残余内存增长

[报告](validation/f08-qwen3vl-iphone-graph36-20261114.md)，证据`artifacts/verification/f08-qwen3vl-iphone-graph36-20261114/`。先新direct processor28精确、host20/40向量同原graph；再隔离iPhone16ProMax唯一PID8576 completed36，无stop，360阶段/72完整向量核验、所有题后source/normalized weak槽0。原36答案/72向量SHA全部同旧，原16/36质量不变。峰phys约2.994GiB、normal→fair、整臂122.26s；题后MLXactive同/cache0而第二至最后phys仍+196.16MiB，非整体内存/长期热预算通过。手机保持旧cache策略，原3.5GiB/serious/180s守卫不变。候选卸载/45普通App完整元数据保持/UgreenHome恢复。前轮未完成final审计以精确非输入live日志例外补齐，原失败保持、零模型重跑。下一可新冻结detail14验证或残余内存归属，勿把连续36本轮通过写成热唯一归因/质量或W6完成；W6open/not-ready。

## 2026-11-14 归一化图像创建作用域对照有效，下一iPhone验证

[报告](validation/f08-qwen3vl-image-scopes-20261114.md)，证据`artifacts/verification/f08-qwen3vl-image-scopes-20261114/`。预冻结source/graph/both三组各no-model28精确后，各唯一model20全部120向量同旧、12次live摘要成功。source仍160+160 weak槽活/IOSurface267.8MiB；graph题后两类槽全0、最终IOSurface6.77MiB、同臂预热后phys+90.78/+28.80MiB，满足预注册诊断门；both也满足但增加非必要source池。选择较小graph-only进一步验证，未采用生产。有效边界是同步归一化图像创建，既有render池阴性，非唯一owner/永久leak证明。selected-graph-only.diff只审阅，实际测的是shared helper mode=graph，直接形式尚未编译。下一冻结新隔离iPhone候选并验资源/连续运行，不把host结果写成手机通过。原16/36/热失败、iPhone16ProMax与W6open/not-ready保持。

## 2026-11-14 弱引用发现：模型路径跨题保留320个图像对象

计数口径补充：320为非nil弱引用注册记录，不是已去重的320个独立对象；未采集ObjectIdentifier。跨题保留证据仍成立，后续不要把登记次数当实例数或surface数。

[报告](validation/f08-qwen3vl-image-weak20-20261114.md)，证据`artifacts/verification/f08-qwen3vl-image-weak20-20261114/`。原MediaProcessing字节不变、只加weak观察；真实CIImage强引用变体RED/weak GREEN、五收据负例拒绝。无模型28/448注册下一题前旧对象均0；唯一host PID88658 completed20/40向量同旧/200阶段，source+normalized每题16累计320全部题后/clear后仍活。是实际图像对象跨题保留证据，非owner/永久泄漏/字节归属证明。四VM IOSurface驻留末271.9MiB、dirty267.8MiB；采样余11.75MiB非资源通过。下一分别区分source加载与normalized图谱同步释放边界，weak和像素/向量双验，勿pool跨await/采用旧render阴性候选。无手机/质量更新，原16/36/持续热失败、iPhone16ProMax与W6open/not-ready保持。

## 2026-11-14 同步render池对照为阴性，不采纳内存修复

[报告](validation/f08-qwen3vl-render-pool20-20261114.md)，证据`artifacts/verification/f08-qwen3vl-render-pool20-20261114/`。隔离MediaProcessing仅给同步bitmap render+原clear加pool，先实际无模型28像素/协议/224时间精确，再唯一host PID57733 completed20/exit0、40向量同旧/200阶段/四live摘要通过。IOSurface仍6.77→267.8MiB、增长261.03MiB，预注册128MiB减少未达到；同臂预热后phys增长也未改善，采样余13.33MiB不称资源通过。候选不采纳、不扩大池/换阈值续扫。编译0后首包装器误找mlx.metallib，实际bundle链接恢复、无重编译或模型重试，原失败保留。下一先查render外图像获取/图谱对象生命周期的保留释放证据，不将类别直接称泄漏。无手机/质量更新；原16/36/持续热失败、当前iPhone16ProMax与W6open/not-ready保持。

## 2026-11-14 VM区域观测完成：IOSurface增长，归因仍待验证

[报告](validation/f08-qwen3vl-vmmap20-20261114.md)，证据`artifacts/verification/f08-qwen3vl-vmmap20-20261114/`。唯一host PID16028 completed20/exit0、40向量/200阶段通过且同旧host，四live vmmap均成功。IOSurface驻留约6.77→76.7→207.2→267.8MiB、region44→65→131→180；是区域增长证据，非库owner/泄漏证明。四采集均跨阶段，最后进入下一prefill，graphics1.7G→2.7G不能写成idle累积。题后active同1,781,949,568B/clear-cache0，同臂预热后phys仍增约244/221MiB。下一聚焦同步图像加载/渲染释放边界的单变量对照，不盲加已有chunk池/已有CI清缓存、不扫分辨率/续6111。无新手机/质量更新，原16/36及持续热失败保持，当前设备iPhone16ProMax，W6open/not-ready。

## 2026-11-14 模型prefix20阶段完成：active归位，phys仍增长

[报告](validation/f08-qwen3vl-model-lifetime20-20261114.md)，证据`artifacts/verification/f08-qwen3vl-model-lifetime20-20261114/`。新host PID92478原prefix20独立run completed20/exit0，无6111/补后缀；40全向量/200阶段/5负例通过，20答案/40向量同旧host。题后active均1,781,949,568B、clear后cache0；同臂第二至最后phys仍+290.4/+268MiB，非活跃KV持续累积或分配归属证明。采样phys3,750,611,944距守卫仅7.14MiB，非资源通过。pinned逐chunk池/Metal scoped pools已存在，不盲加池。下一新隔离活进程vmmap summary归属观测，保持数学/guard，无手机/质量更新，W6open/not-ready。

## 2026-11-14 无模型预处理28完成，尚未复现原模型增长

[报告](validation/f08-qwen3vl-preparation-lifetime-20261114.md)，证据`artifacts/verification/f08-qwen3vl-preparation-lifetime-20261114/`。唯一新host Metal实际processor28/28、像素/tokens/位置精确/224时间通过，170阶段/424总样本、5收据负例拒绝；PID80576 exit0、thermal0→0，权重读被实际沙盒拒绝，无模型生成/手机。每题返回active0、clear后active/cache0；同臂第二至最后phys baseline+6.47/detail+6.30MiB，未复现原模型匹配计数的约530MiB。不能排除模型互动/框架/驱动，非泄漏定位或手机资源通过；不盲加池。下一新隔离模型阶段观测，保留原输入合同/守卫，原16/36/热停止/旧中断保持，W6open/not-ready。

## 2026-11-14 stop遥测已补齐：宿主生命周期方向待验证

[报告](validation/f08-qwen3vl-stop-telemetry-20261114.md)，证据`artifacts/verification/f08-qwen3vl-stop-telemetry-20261114/`。新sampler仅补正常stop三MLX字段/采样失败三null，不改阈值/安全_exit；旧5RED、新12GREEN、4变异拒绝、真实小Metal触发样本6字段精确运输、iOS单sampler类型检查通过。非模型/手机试验。旧采样筛出active同1,781,949,568B/cache0的90点，首末phys仍差约530MiB，不能认定泄漏或CoreImage归属；实际消费者每题新KV、题末clear，CIContext已禁中间缓存/渲染后clear，但准备及首prefill不在token池内、缺阶段采样。下一无模型实际processor生命周期对照，先定位释放边界，不盲扫cache/放宽guard/补6111。原分数/停止记录保持，W6open/not-ready。

## 2026-11-14 cache256新实验：10完整对，一条改对，仍越内存

[报告](validation/f08-qwen3vl-cache256-detail14-20261114.md)，证据`artifacts/verification/f08-qwen3vl-cache256-detail14-20261114/`。全28原输入/顺序保持，两臂cacheLimit256MiB，采用safe-exit不改3.5GiB守卫；唯一新host PID16360完成21输入/10对，baseline2/10→detail3/10，1255错变对/无退化，五条线索在仍0/5。6111-detail prefill在13823.723ms phys3,772,632,064B/thermal0再越守卫，可靠exit70、report仍running/21；纸纹detail无答案，不补跑后三题/6111。42全向量/grammar/28KV/协议通过。采样cache峰约283.7MB（非硬≤256MiB），stop缺同刻MLX；原无限缓存不是充分解释。旧4852报告文字更正：原手机A、hostB，不是都B；原JSON/16/36不变。下一补同刻stop遥测与生命周期边界，不继续扫分辨率/放宽预算。非手机/独立benchmark，W6open/not-ready。

## 2026-11-14 隔离紧急退出回归通过，缓存预算待对照

[报告](validation/f08-qwen3vl-guard-exit-20261114.md)，证据`artifacts/verification/f08-qwen3vl-guard-exit-20261114/`。新sampler仅2处exit70→Darwin._exit70和2注释，原阈值不变；实际atexit副作用回归旧6RED/新11GREEN，含采样/写报告失败。真实MLX小矩阵warmup后normal0/trigger70无atexit，两夹具无模型，测试副本阈值1字节不改候选；触发很早，不称确定原并发现场复现。iOSsampler类型检查0，非手机/完整App。旧detail14原running/1、SIGSEGV139和16/36均保持。旧基线末MLXcache约1.46GB、active约1.90GB，后续stop无同刻MLX分项；支持新冻结256MiB显式cache对照，不证明其为唯一越界原因。下一新资源设计，不续写旧28臂/不放宽守卫；W6open/not-ready。

## 2026-11-14 固定14题细节对照被宿主内存守卫中断

[报告](validation/f08-qwen3vl-detail14-20261114.md)，证据`artifacts/verification/f08-qwen3vl-detail14-20261114/`。全部6细节不清+5线索在+3原对题预冻结，唯一解码cap512→1280，processor cap262144保持；112actualTime精确，14baseline手机像素同，实际THW[4,18,32]→[4,24,42]/visual tokens576→1008，28协议/10负例通过。唯一host Metal PID52977第一baseline完成B/C错，首detail prefill在4474.441ms越3.5GiB守卫（3,791,358,976B，thermal0），完整对0/14。原report仍running/1，无细节答案、不补跑；最终exit139/SIGSEGV，精确崩溃栈见sampler exit析构与AGX编码同时执行，不能报整洁exit70或iPhone内存结论。原校验器冻结保留，新崩溃绑定checker验失败证据和2向量通过，experimentCompleted=false。下一先独立审查Metal并发退出与内存预算，非继续扫题/放宽守卫。原16/36、真机连续热失败与W6open/not-ready保持。

## 2026-11-14 原20错题输入可见性已完成后验审读

[报告](validation/f08-qwen3vl-error-visibility14-20261114.md)，证据`artifacts/verification/f08-qwen3vl-error-visibility14-20261114/`。新增原14非abstraction错误实际packed输入审读（手机SHA/逆repack精确），加旧6条覆盖20错题；5线索在/6细节不确定/2稀疏运动不确定/1完整序列不确定，仅作者已知答案的后验类别，不是根因百分比。6500前后蓝包明显消失而模型答无变化；4852纸HEART/卡HEARD与逆序目标可能混淆。6111原八actualTime精确同源1280导出可见更细纹理、实际512弱化；零新模型推理、无默认/手机/下载变化，不声称提高分辨率答对。下一可行分支为预注册独立固定题组的单一细节预算控制；不得只扫6111或复扫251/9676。原16/36及连续热保护仍失败，W6open/not-ready，goal active。

## 2026-11-14 video251末帧覆盖对照已完成，W6仍未通过

证据`artifacts/verification/f08-qwen3vl-video251-tail8-20261114/`，[报告](validation/f08-qwen3vl-video251-tail8-20261114.md)。仅将末帧26.393s替换为29.897s（第三个物体可见），八帧数/前七帧/模型/512/grammar保持；同一Swift Metal进程原输入、自然新时间和人工像素控制均A=No，期望C=Yes。六全向量/28KV/七负例核验通过，原手机A同host基线，原16/36保持。已证漏采不能单独解释此题，但内部形状/OCR/计数/temporal编码环节未定位；不再同题扫描，不重跑原36或以host替代手机热门。下一可推进的是其余独立失败实际可见性与任务合同审读，以及独立持续资源验收准备；不擅自缩成运动检测。无新设备/下载/生产改动，W6open/not-ready，goal active。

## 2026-11-14 valid36离线补证：video251漏掉最终第三个物体

对六条抽象失败实际Swift packed pixels逆/正打包精确检查和主代理后验审读；原video251最后输入26.393s只有两个桌上物体、纸上三个形状，已有原视频尾部约29.396s/29.897s出现第三个小黄色三角物体。确认uniform8排除最后约3.8s导致必要终态未入输入，零新模型推理，不改16/36。不是独立盲评/模型内部因果证明；另五题审读仍不确定。证据首根`visibility-audit/`和[报告](validation/f08-qwen3vl-valid36-20261114.md)。**下一只值得预冻结video251同8帧末帧覆盖控制，自然时间与仅换像素分开，检验补齐是否改答；不扫题/不改原成绩/不放宽热保护。**

## 2026-11-14 新valid36真机：内容16/36，连续热保护未过

见[验证](validation/f08-qwen3vl-valid36-20261114.md)。预冻结排除114旧视频的新36，原8帧/512/在线规则/同Qwen3VL2B4bit。初始唯一PID8182完成23题8对，75.013s serious守卫exit，原report仍running/23、stop与46向量通过，未冒completed；仅原剩余13另一次PID8309完成8/13，未重复原23。两进程汇总16/36、invalid0，抽象0/6，相机4/6，其他各3/6；不是连续36通过或产品预算通过。周边App/探针进程活动存在，热来源不唯一定位。72全向量/手机实际像素协议/28KV位置通过；两候选卸载、45原App完整元数据保持、UgreenHome恢复。全部下载/构建/设备/收取进程终态，网关已停。

证据根`artifacts/verification/f08-qwen3vl-valid36-20261114/`、`f08-qwen3vl-valid36-remainder13-20261114/`。`aggregate36.json`只作两进程质量汇总；原`device-stop.json`/`guard-parent-verification.json`保留持续失败，勿重跑旧18/已完成36刷分。主代理已获直接实现授权；不重启quota-stopped helper/native。**下一对新失败项离线可见性审计，先分辨8帧漏事件与已可见仍答错，不改默认/预算/保护，不新采集媒体。W6/F08仍open/not-ready，goal active。**

## 2026-11-14 用户设备范围已定：先按现有iPhone16ProMax，W6仍未完成

用户答复“先按这个测试机”，并询问W6是否完成。当前设备验收固定iPhone16ProMax，不继续把“未选最低机型”列为该轮前置；其他设备/Android单列后续，不自动扩为本轮iPhone关闭阻塞。此答复未批准现有延迟/内存或质量门。

[W6当前判定](roadmap/W6-video-understanding.md)：运行/协议/在线格式修复有真机证据，14/18invalid0；仍缺独立代表性质量评估与正式裁定、资源预算比较和正式采用/集成回归复审，故W6open/not-ready。不要把隔离候选已修复写成W6整包完成，也不把下方历史24项待执行快照当当前Qwen3结果。

## 2026-11-14 在线约束已通过宿主+iPhone：14/18、invalid0，内容质量仍阻塞

[在线集成报告](validation/f08-qwen3vl-online-choice-20261114.md)：原真实基线6格式错的行为RED→纯Swift24检查/Release构建/iOS34源typecheck→宿主和真机各唯一新Metal臂18/18完成，均严格14/18、invalid0。原手机9/18invalid6保持；两臂18首选项全同原，五纯格式错修复、4505仍选错。两臂72完整向量/允许集argmax/EOS/28KV/rope delta/原输入协议像素父核通过；手机36共同前缀向量与旧手机逐字节同，宿主不数值同。不是离线回放或新独立benchmark。

手机候选`io.ondeviceai.f08qwen3choice20261114` PID8105仅launch一次，终态双读相同；56.018s、load627.107ms、每题中位2748.438ms、5063资源样本、phys_footprint峰3,171,831,472B约2.95GiB，thermal0→0，guard非预算。新候选已卸载/无进程，45普通App完整元数据同并恢复UgreenHome。旧签名资产/普通模型/共享依赖默认保持；无下载/CPU模型/Torch/Ollama。首iOStypecheck缺yyjson按缓存modulemap恢复，失败保留。

证据根`artifacts/verification/f08-qwen3vl-online-choice-20261114/`，`ios-parent-verification.json`、`ios-restoration.json`；隔离消费者实际生成链修复，未改正式SDK/App。该分支完成不重跑旧18题；四内容错误与独立产品数据/最低设备/延迟加载内存预算/持续运行/Android门仍缺，F08/W6open/not-ready、goalactive。下一需产品任务及质量资源裁定；下方“在线集成待做”仅为历史。

## 2026-11-14 同八帧补齐展示仍答3：漏采不足以解释计数失败

[oracle八帧报告](validation/f08-qwen3vl-oracle-eight-20261114.md)：9676固定同模型/题目/八帧预算，两杯三笔展示在实际packed输入可见后，自然新时间和仅换像素/原时间两控制均仍C=3，期望B=5。唯一MacMetal进程exit0，4完整logits/EOS/28层offset/2076旧文件核验通过；原valid18成绩不改。支持当前细粒度身份/去重计数链路仍不可靠，不能唯一归因内部环节或推论视频理解普遍不可能。

初选14/19秒笔运动模糊，零推理拒收保留；限定展示区间离线取帧后，推理前冻结r2的14.75/19.6秒。已知答案后验oracle、非通用采样/新benchmark；末尾32.49秒无清楚绿杯，纠正原可见性收据额外索引7的误标，第1帧已保留该身份。证据根`artifacts/verification/f08-qwen3vl-oracle-eight-20261114-r2/`，原目录完整保留。

该分支完成且停止同题扫描，不再重放模型或为相同现象重复手机。无设备/下载/生产默认变更；F08/W6open/not-ready、goalactive。下一需依据实际产品任务及独立质量/资源门裁定路线；输出约束在线集成是另一个格式门，不能修正合法C。下面“下一oracle”是历史已执行项。

## 2026-11-14 Qwen3输出约束离线验证完成；计数题发现事件覆盖缺口

[约束与内容审计](validation/f08-qwen3vl-choice-constraint-20261114.md)：纯Swift A/B/C→EOS状态机原贪心真实RED→24检查GREEN、iOS类型检查通过；62既有输入/124全向量断网回放、独立mask/BPE/前缀/134文件核验通过。所有首字母ID与原一致，第二步可复用同前缀真实向量；分歧拒读下一向量/篡改/offset三负例有证。iPhone原valid18基线9/18invalid6保持，离线约束结果14/18invalid0；Mac同题也14/18，额外自然pair14为9/14，swap12为8/12，非新模型推理/benchmark或手机在线集成。真实10对双侧6/10、不重叠3/6，仍失败。

手机实际packed像素四错题逆还原/重packing精确。4505首帧H杯已保留仍选手机；10249文字YOU/OVL可见仍选干扰项。9676现8帧缺三支不同笔的清楚展示，原视频1fps取证35帧能见两杯+三笔五身份，证实事件覆盖缺口；9500现8帧有三次准备但缺释放轨迹，2fps26帧确认三组过程。均后验AI审读，非盲评或独立重标，不唯一归因模型容量。

下一有区分力分支：9676固定同8帧数量的oracle事件覆盖控制，保持问题/模型/评分，不再无目的帧数/prompt扫描；不能把它冒充独立质量评估。本轮零新模型/设备/权重/依赖/默认改动、无活动运行句柄。F08/W6open/not-ready，goalactive；在线约束集成、独立产品数据/质量与资源预算、Android/持续运行余门保持。

## 2026-11-14 Qwen3 iPhone valid18完成：9/18，全部回答与Mac相同

[真机报告](validation/f08-qwen3vl-iphone-valid18-20261114.md)：先32题手机prepare/像素与Mac精确同，再独立固定valid18原18题、同权重和协议、greedy32/balanced512。iPhone16ProMax Metal唯一模型臂完成18/18，严格9/18、invalid6；全部原始回答/生成IDs/EOS与Python Mac相同，72完整logits/28层offset/rope delta/冻结源父验收通过。向量本身有数值差异，不声称数值等价。五纯格式错、四选项错仍在；支持剩余错误非本轮iPhone特有故障，不证明视频理解不可能或约束格式就能全部解决。

整臂58.313s、加载807.73ms、单题准备生成中位2.827s、采样phys_footprint峰3,160,231,504B/5273样本、thermal nominal→fair；未触发沿用诊断guard，不是预算通过。新候选`io.ondeviceai.f08qwen3metal20261114`及prepare-only候选均已卸载，45普通App完整元数据一致、恢复UgreenHome；原模型/依赖锁/默认保持，无活动模型或helper，不重放同臂。

证据`artifacts/verification/f08-qwen3vl-swift-parity-20261114/{parent-ios-model-verification,parent-ios-python-logits-comparison,ios-model-restoration}.json`。下一独立输出约束验证保留9/18严格基线，并围绕实际产品任务/独立相反答案数据检查内容错误；产品最低设备/耗时/内存、持续热、Android与生产门仍缺。F08/W6open/not-ready、总goalactive。下方待手机/尚未执行等为历史。

## 2026-11-14 Qwen3 Swift协议修复已通过主机完整准备链，下一iPhone前置

[Swift协议报告](validation/f08-qwen3vl-swift-parity-20261114.md)：隔离候选修正带时间视觉块和位置索引，原真实BPE32题全不一致已复现，最终235 helper检查、真实Swift tokenizer32题/29编码、28源iOS typecheck、实际MLX位置32×2、完整processor32题0协议失败均有证。主机tokens/THW/shape/三轴位置/delta精确对应；像素仍有0.27–1.15/255 RMSE，非逐像素等价。CMTime4.1纳秒构造误差已定位修正，首6题失败及新r2全32题输出分别保留，未重跑模型。

iPhone preparation-only候选`io.ondeviceai.f08qwen3prepare20261114`已完成32题/256PNG，Metal且无权重/推理；终态双读相同，949文件重核、协议全对应，32/32像素与Mac逐字节一致。候选已卸载，45普通App元数据一致并恢复UgreenHome。下一新候选`io.ondeviceai.f08qwen3metal20261114`固定原valid18全18题、greedy32/balanced512/完整logits/28层offset+delta、既有诊断guard，计划`ios-model-plan.json`已冻结，尚未执行模型。原Swift/runtime/依赖锁/普通模型保持；不调用失败helpers，不重放宿主模型。F08/W6仍open/not-ready、goal active，手机模型/Android/产品预算门未过。下方“下一Swift修复”为历史。

## 2026-11-14 Qwen3三组Metal对照完成：部分视觉依赖已证，质量仍阻塞

最新[候选报告](validation/f08-qwen3vl-candidate-20261114.md)。用户已明确允许主代理直接实现，原授权阻塞解除、总目标active；两个hy4free任务均自然终态且无可用候选，不再等待/重发。隔离脚本四项RED→GREEN；18原valid题严格9/18、invalid6，首字母描述14/18。相比旧Smol严格10/18/Qwen2严格7/18，不能宣布Qwen3整体胜出；五题仅格式错、四题选项错，格式修复不足以全部解决。

复用固定6组16唯一视频/10跨答案对，另跑14题、复用valid18两题；合并严格2/16、invalid11，双侧严格0/10；首字母双侧正确6/10，不重叠3/6，选项不变4/10。额外在全部六不重叠对上双向仅换packed pixels，目标文本/时间/positions/THW保持：12题严格2/12、invalid7；三对相机题8055↔9291、10969↔5418、369↔4991首选项均随画面双向正确改变。其余抽象/相机/physics三对不成功。它支持这些样本上的画面依赖，不能推论视频理解普遍可靠或“本来做不到”。

证据`artifacts/verification/f08-next-candidate-feasibility-20261113/`的`parent-combined-results.json`、`parent-swap12-results.json`：三进程Metal均exit0，174完整logits含EOS/实际decode/28层offset和全部冻结守卫通过。原8PNG→ICC/sRGB/packing独立重算、官方文本AST+真实BPE、全部位置AST核验；未宣称完整官方video resize parity。宿主MLX峰约2.65GiB，不是手机App内存。换像素checker首次括号SyntaxError保留，修离线验证后通过、未重放模型。

F08/W6仍open/not-ready，无iPhone/Android新模型验收、产品预算或生产默认改变。本轮宿主分支完成，不继续扫旧题。下一有价值的独立工作是隔离Swift的Qwen3带时间视频块及位置合同修复，与宿主实际输入对齐，再做有界iPhone Metal可行性；输出约束另立实验、保留严格基线。最低设备/内存/加载预算仍待用户定义，不拿诊断guard当产品预算。

## 历史：2026-11-14 授权恢复前等待状态（已解除）

Qwen3权重下载与协议/官方位置/packing夹具验收保持；实际18题未准备、无新模型推理。hy4free准备候选自然CLI0/raw0、上游finish_reasonerror空流，符合已记录重复供应商失败，按AGENTS停新该线路调用并等待用户选择hy3小片段或授权主代理直接实现。此条件连续多轮未解，目标标blocked，非模型/磁盘/Metal失败或因为运行慢。独立runner原exec29384/PID33948仍live（23:29、raw/stderr0），未取消；恢复时先查同一任务终态/实际文件，不重复提交。精确live状态和终态失败收据在候选根及ask-cbc-qwen3-prepare18-20261114；既有证据和普通App/依赖保持。

## 历史：2026-11-14 Qwen3下载及协议前置（后续结果见顶部）

最新[候选报告](validation/f08-qwen3vl-candidate-20261114.md)。Qwen3VL2B4bit固定9c4f5209经用户localhost:6800完成1,782,040,921B下载，独立SHA/1019tensor/index/14小文件通过；无共享依赖/正式App/默认改动。原字节Swift helper实际编译运行720pad/1视觉块，官方文本AST同720pad/4带时间块；仅测试tokenizer/helper与文本步骤，不归因旧Smol/Qwen2错误。官方Qwen3位置原AST的三BPE fixture与split-T1参考逐元素同、无Torch。外部候选准备复用valid18原八帧/实际时间/题目/标签；下一实际文本/像素/位置验收→唯一Metal推理→严格质量比较。Qwen2.5VL3B官方实际research许可仅非商用，未下载其权重。F08/W6仍open，目标active，候选质量和iPhone资源未验。

## 2026-11-13 真实视频同问题配对审计：两模型均无双侧通过，当前答案模式不可靠

最新[真实配对报告](validation/f08-real-video-pair-audit-20261113.md)。仅复用train72+valid18的90视频/旧结果，精确问题与有序选项分组，6组16唯一视频、10跨答案配对；按caseId词典序另取6对/12不重叠视频。Smol10/10对首选项不变，Qwen9/10不变；两模型均0/10双侧严格对、不重叠也是0/6。16题严格Smol6/16invalid2、Qwen4/16invalid7。此为回溯、混合split、非盲评/独立标注，10对有复用，不能当独立benchmark。

全部15camera题追加描述：Smol15/15首选项等于列表首个非“不知道”项，Qwen15/15首字母映射moving；严格8/15和3/15，不能用首字母描述放宽评分。所有20次模型-配对首全向量比较不同；Smol9/10对tokens受时间标签影响，Qwen10/10对tokens/positions实际字节、THW及shape均相同而像素不同，生成AST仅展示总数归一后相同。并非没接视觉/内部唯一根因证据。Smol384输入6接触表覆盖128帧：相机组已有较强背景/视角差别，重复动作/通电仍有稀疏帧或音频不足解释；不是Qwen packed输入还原或背景配准。

证据`artifacts/verification/f08-real-video-pair-audit-20261113/`；免费CBC hy3供应上下文复核97.951s完成，父纠正其“12配对”等错误并保留原文，无native429重试。无下载/推理/手机/默认改动。下一候选路线需要产品最低设备/耗时/内存预算（已问，未答不设门），以及视觉线索充分的独立相反答案对照；不继续同题prompt/帧数扫描。F08/W6仍open/not-ready、目标active。

## 2026-11-13 Smol valid18中性像素对照完成：10/18→9/18，正确率不等于视觉理解

最新[中性像素报告](validation/f08-valid18-smol-neutral18-20261113.md)。同18题仅将Smol像素改成同shape正float32零（归一化中性灰），tokens/问题/选项/模型/runtime/评分全部保持；一次Mac Metal、exit0，严格9/18、invalid0，对照原10/18、invalid1。13题选项相同；共同对8/仅原对2/仅中性对1/共同错7，memory仍3/3。原文变6项，其中5项换选项、1项仅去错误答案句点。18首步完整logits均不同，不能由相同选项推断无视觉，也不能由向量变化证明正确视觉依据或唯一归因。

证据`artifacts/verification/f08-valid18-smol-neutral18-20261112/`：父核18精确零数组及除像素路径外整行相等、306输入/25模型/1062旧文件/10实验源/18runtime守卫、新36全向量含EOS及24层offset通过。实际运行计划SHA`bbf9c50182e971507a782493c3f5e15916fc4461bf9dd053993b674765518f79`；原finalizer输出后父端在推理前补484baseline守卫，不混用早期SHA。首输出` A/B/C` token330/389/340与原labelLogits独立A/B/C token49/50/51不同，子集argmax变化2项不是实际选项变化5项，评分不改。native审阅429停止，未重试；无新下载/依赖/手机/默认变更。

本次分布外中性干预不是text-only或产品验收，未重放同输入量化数值噪声；原正确10项中8项在移除语义画面后仍正确，故旧正确率不足以证明视觉理解。停止本分支，不追加同题扫描。下一需固定问题/选项而真实画面与正确答案成对变化、实际输入线索可见的验收样例；F08质量仍open/not-ready，总目标active。

## 2026-11-12 官方valid18双模型对照完成：Smol10/18、Qwen7/18，质量阻塞仍开放

最新[valid18报告](validation/f08-valid18-20261112.md)。用户授权后的18个官方valid视频，与旧train24/train72无视频重叠，六类各A/B/C一题且全cut=-1。只读取约193MB选中成员的HTTP Range、未下载75GB全包；**此分支未走6800 aria2 RPC**，不混称先前aria2任务。18媒体CRC/size/SHA父验收、144帧及两模型native像素/模板/positions父复算通过。

两臂串行Mac MLX Metal各一次、均exit0：Smol2.2B4bit严格10/18（55.6%，invalid1），Qwen2VL4bit严格7/18（38.9%，invalid2）；父核37/38份完整logits含EOS及24/28层offset通过。配对Smol独对4/Qwen独对1/共同对6/共同严格错7。Smol7合法选错+1内容及格式均错；Qwen9合法选错+2纯格式错，不能仅靠格式约束解除阻塞。MLX峰2.61GB/2.54GB不是iPhone预算。

证据根`artifacts/verification/f08-valid18-20261112/`：冻结278输入/25模型/578旧输出/14实验源/18runtime源，12离线合同检查、两父验收与`parent-final-audit.json`通过，无活动模型进程。CBC错误schema/空工具请求候选及父fixture/路径错误全部保留，外部preparation/runner候选经审核后精确组合；不把exit0当交付成功。独立结果审查PARTIAL仅限host证据，不是手机/产品门通过。

该分支已完成，停止同题prompt/帧数/评分扫描。18题不是完整valid、总体统计优势或训练去污染证据；现结果支持Smol方向但两者仍不可靠，不能唯一归因模型容量或称视频理解做不到。下一有价值的分支是复核共同语义错题在既有8帧中的线索可见性，再结合明确产品任务和质量/加载/内存预算决定模型路线；不改默认，F08继续open/not-ready、总目标active。

新增[valid18共同失败题可见性审计](validation/f08-valid18-visibility-assessment-20261112.md)：7题中只有字母顺序题`video_10249`在原始帧和Smol384输入中都有较直接的`YOU`/`OVL`线索，支持干扰项决策错误；碰撞轨迹、物体身份计数、书/袋连续性、设备通电、瓶盖细节、相机运动均不能仅凭8张静帧可靠判断。`video_1550`的Qwen `C.`语义正确但格式错，不能算双方语义错。此审计不使用音频/连续轨迹，不证明加帧必然有效，也不改变评分或模型结果。

## 2026-11-12 高可见性三例16帧对照完成：仍0/3，停止同题帧数扫描

新增[16帧三样本报告](validation/f08-smolvlm2-dense16-visible3-20261112.md)。固定Smol2.2B4bit/384px/81token/原问题选项评分，嵌套8→16帧；48帧独立公式及模板复核，24原帧字节和归一化数组精确保持。唯一Metal宿主进程exit0，字母顺序/准备什么/静止相机仍B/A/A，期望C/B/C，与原8帧答案全同，严格0/3、格式错0。父核6完整logits含EOS、24层offset、94输入/14模型/579旧输出通过。更多帧未改善这三例，不能再只以关键线索完全漏采解释；但不证明所有视频理解做不到或唯一归因模型容量。

原准备路径导出器未编译，失败发生在模型前；原目录保留。独立`artifacts/verification/f08-smolvlm2-dense16-visible3-20261112-r2/`补编译原Swift、准备一次并冻结新计划，随后唯一模型运行。父verifier初稿额外runtimeSources字段假设失败保留文字记录；字段实际已在frozenSources覆盖，父修正后离线通过。运行前补计划runtimeSources别名供runner provenance，未改runner/模型。没有新下载/依赖/设备/默认改动，MLX峰2.54GB不是iPhone预算。

本分支已达停止条件：不扫更多帧数/prompt。下一应面向明确产品任务和预算作模型选择或独立数据评估，F08仍open/not-ready。已清本任务64MiB编译缓存和小量pycache；全部48PNG、数组、源码、结果和旧失败保持。

只读独立审查为PARTIAL、无新增阻断发现：16帧同时改变帧标签、占位token总量、序列长度和prefill分段，不能称纯采样单变量；三条可见性挑选样本不代表总体，host不替代iPhone/产品门。详见r2的`native-readonly-review.md`。

## 2026-11-12 真机配置阻塞已定位并解除：12行生成，严格5/12，F08仍开放

最新报告 [Smol2.2B真机seq_len81对照](validation/f08-smolvlm2-mobile-seqlen81-20261112.md)。原固定Swift工厂只从preprocessor配置读图像序列长度，模型processor配置声明81未合并而默认64；相同8帧实际只产生512图像token。前置真机prepare同像素/去图像token后文本一致的对照明确该差异。仅内存补81、保持模型/12输入/问题/评分/停止阈值的新隔离App PID4078全部12行完成（Metal），严格5/12，0格式失败；原PID4061是preparation失败0生成，不是12题全错。

同12题既有host与真机原始答案12/12相同，都是5/12；counterfactual2/2、physics2/2、semantics1/2、memory/abstraction/camera各0/2。不能把配置修正当整体质量解决，也不能以这批结果证明所有视频理解做不到。7,972采样、约88.0s、phys_footprint峰3,206,056,856B（2.99GiB）、thermal0→1；未触发诊断guard，不是产品预算。加载240.175ms；每条完整totalMs中位7.04s，短generate计时不含TokenIterator构造，不能冒充端到端延迟。

终态双读一致/6报告检查/21合同检查/751源产物+27输入+306旧输出hash保持，证据`artifacts/verification/f08-smolvlm2-mobile-seqlen81-20261112/`。隔离App已卸载、普通Combined已打开，45原App元数据相同。无新下载/CPU/Torch/Ollama/生产依赖默认改动。新增[7错误样本可见性检查](validation/f08-smolvlm2-visibility-assessment-20261112.md)：字母顺序、准备茶、静止相机至少三条有较强“线索已在8帧中但仍答错”证据；袋中取物/重复动作计数仍受遮挡或稀疏帧影响。`video_8635`补看尾段后仍G–M–S，不能再用漏掉结尾解释该错误。heldout/产品预算/持续热/释放内存/生产适配门未过，F08open/not-ready。启动参数后置导致无launchJSON及离线路径错误保留，未重启模型；审查子代理额外跑过纯JSON verifier的委派偏差也保留。

## 2026-11-12 2.2B 4bit 同24题对照完成；质量方向明显改善但F08仍未关闭

此前被否决的 `smdesai/SmolVLM2-2.2B-Instruct-4bit` 固定revision `0a4ab666f99095e6c6bd0e1e49aca8822fc46df2` 已按用户授权通过现有 `localhost:6800` aria2 恢复。下载中两次EOF及错误hook删除partial均保留，最终恢复GID `d9ce4ee76da2b8a8` 完成14文件，权重字节数1,458,105,543、SHA-256 `dbdf6ff7b0e7e160f3b135c15c3afbe9580214747e62967585b417d966cade37`通过。未把下载传输故障当模型失败。

2.2B使用独立384px/81图像token、24层量化输入合同（不能复用500M的512px/64 token）。唯一Metal宿主运行 `Device(gpu, 0)` exit0，24题严格 **16/24**、0格式失败、8项选错；类目为camera2/4、memory1/4、abstraction4/4、counterfactual2/4、physics3/4、semantics4/4。48份完整logits、EOS/offset、输入/14模型文件父核验通过；MLX宿主峰值约2.61GB，不能当iPhone预算。

相对Qwen官方4bit 10/24，2.2B改善8题、退步2题；相对原始BF16值提升FP32 14/24，改善4题、退步2题。相对Smol500M 5/24，改善12题、退步1题。证据根 `artifacts/verification/f08-smolvlm2-22b-fixed24-20261111/`，报告 `docs/validation/f08-smolvlm2-fixed24-20261111.md`。这使“换更大Smol模型有价值”成为当前固定24题上的宿主证据，但不等于手机端通过：仍缺iPhone 2.2B资源/热持续门、held-out集、获批阈值和产品集成验证；F08保持 `open/not-ready`，不改默认模型。

## 2026-11-12 SmolVLM2 500M 同24题宿主对照完成（历史准备记录）

按用户要求，在不使用Torch/CPU/Ollama、不改生产依赖和默认模型的前提下，使用现有 `mlx-community/SmolVLM2-500M-Video-Instruct-mlx`（revision `fa57db46815177fbdfd65cc85a2b3416a8332268`）对与Qwen相同的固定24题运行一次 Metal 宿主对照。24题沿用同一批题目、8张已保留PNG、原始采样时间、问题/选项和严格单字母评分；使用实际无Torch的 `SmolVLMImageProcessor`（512方形、视频不分块）及固定Swift Smol视频提示模板。独立复算192帧、24份chat tokens和14个模型文件通过；唯一推理进程 `Device(gpu, 0)` exit0，110份完整logits含EOS保存并父核验通过。

严格正确率 **5/24**：camera 0/4、memory 0/4、abstraction 0/4、counterfactual 2/4、physics 0/4、semantics 3/4；12项输出格式无效，其中13项选错选项、6项是选对但附加正文/句点。与Qwen官方4bit **10/24**、原始BF16值精确提升FP32 **14/24**相比，500M没有解除阻塞。完整证据见 `docs/validation/f08-smolvlm2-fixed24-20261111.md` 和 `artifacts/verification/f08-smolvlm2-fixed24-20261111/`；F08 仍 `open/not-ready`。

本节下载中状态为历史；2.2B已恢复下载、运行并核验，详见顶部最新记录。

## 2026-11-11 固定24题官方预处理已完成：4bit10/24、原始值FP32为14/24

最新[24题对照报告](validation/f08-qwen2vl-official24-20261111.md)。复用旧四题，仅新增其余20题，真实MLX Metal两进程均终态exit0：4bit9.559s、原始BF16值内存精确提升FP32为22.054s。4bit严格10/24（5格式失败），同像素原始值FP32严格14/24（2格式失败），无新阈值/生产通过。六类依次camera/memory/abstraction/counterfactual/physics/semantics：4bit1/2/0/1/2/4，原始值FP32为1/2/2/2/3/4（每类4题）。原始值改善4项、无退步，其中11448/1738选项变正确，11297/8295仅去句点；仍9项选错选项及1纯格式失败。不是只修格式就能解决。

父重算160帧/20官方数组/提示模板和参考位置、283输入/21旧输出；4bit新45完整logits含EOS加旧4首向量通过，旧4EOS全向量缺口保持。原始值729完整参数精确提升断言/父shape合同、新42+旧8共50全向量含EOS、同pixels/tokens/positions/prefill、283输入/87旧输出/11旧模型通过。证据`artifacts/verification/f08-qwen2vl-official24-20261111/{parent-prepared-verification,parent-result-verification,parent-original24-verification}.json`及两启动收据/源码diff/失败分类。源新20与设备原文全部相同，24仅11448错换错/7915错转对；不能外推24题改善全由像素独立导致，其余20设备张量未导出。多进程host诊断聚合，不是连续iPhone、移动端预算或heldout benchmark。

原始值改善只能归精度/转换来源组合（旧4bit源revision未固定），不是训练FP32权重，未证明所有模型理论不能理解视频。alpha/bicubic微调和原始权重均不足以解除此配置的可靠性阻塞，暂不改默认。准备器Path类型错误/源码替换次数guard/旧preflight文案均在模型前保留并订正，无同输入推理重试。当前无模型或下载活动、无Torch/CPU模型/Ollama/iPhone/新依赖/生产改动。下一公平横向分支为用户此前要求的SmolVLM2同24题评估；先核已有权重/可复用Metal执行面和输入合同，不再扫Qwen同题参数。F08open/not-ready、目标active。

## 2026-11-11 alpha及bicubic语义控制完成：像素更近未改善准确率

最新[alpha与bicubic控制报告](validation/f08-qwen2vl-alpha-kernel-control-20261111.md)。原4bit/同四题/同原tokens及positions/分段attention/fresh28KV/balanced512/greedy32，只除原宿主normalized RGB的alpha，完整仍A/C/B/A、1/4；实际3.500s，父8全logits含EOS、32变换/57输入/18旧输出/11模型通过。单独改CoreImage bicubic C默认0.75→0.5，32tone字节保持，官方像素RMSE降19%–40%；实际3.246s却只得C/C/B/A、仍1/4，11448换成另一个错误答案。父8全向量/57输入/50旧输出/32新打包帧/11模型与原缓存协议通过，无生产参数修复或质量通过。

本次补证默认同PNG normalized RGBA与先前直接AVFoundation宿主全部32RGBA逐字节相同，重打包相同，故复用旧模型基线而非重跑。准备稿“PNG必不同”额外断言失败保留；新计划只运行C0.5。alpha父verifier末尾未使用的旧plan字段假设KeyError保留，删除该行后离线通过；均非模型重试。证据根`f08-qwen2vl-alpha-control-20261111/`与`f08-qwen2vl-bicubic-control-20261111/`及精度根`kernel-stage-output/`。实际filter默认B0/C0.75及固定Pillow13.0.0官方a=-0.5源码已保留；同核参数不等于完整resize等价。

两条新臂均终态、无模型/下载活动；不再重复同输入或扫描参数。下一拟用已验官方预处理扩展固定24题的其余20题，复用4题输出，报告六类别质量而不新设产品阈值；先核现有样本/张量/时间与旧结果完整性。无Torch/CPU模型/Ollama/iPhone/新依赖/默认改动。原权重官方8帧3/4、20帧计数1/2仍为小样本宿主证据，F08open/not-ready、目标active。

**下一分支前置已完成，尚未新20题推理：**bicubic根`official24-preparation-inventory.json`核24原媒体size/SHA、六类各4题、旧4题复用与其余20caseIds。`official24-token-template-preflight.json`从四个已导出真实token数组提取相同前后模板，四题全token精确复现；24prompt逐字匹配官方manifest构建及旧报告SHA，重建token长度全部匹配，8帧时间/原pixelShape保持。源三报告按旧aggregate SHA重验；其余20无设备完整张量导出，不能称20题全token独立比对。后续可据实际旧时间用现有host snapshot/官方ICC-sRGB路径准备余20，不改题目/选项/评分。8前置gate tests（alpha4、kernel4）及最终源/输入/旧输出摘要通过，`final-offline-check.json`保留。

## 2026-11-11 同PNG阶段检查完成：下游缩放残差较大，alpha偏差已证但非质量修复

新增[同PNG预处理阶段报告](validation/f08-qwen2vl-pixel-stages-20261111.md)。对32已留PNG复用原CoreImage方法，实际渲染tone/resize/normalize及已知双色块共99数组；未运行模型/新视频解码/下载/iPhone。相同Pillow缩放后的色彩分量RMSE0.139–0.535/255，下游组合0.545–0.724/255；不能据此唯一认定采样核或把RMSE相加。只在最终舍入8bit反而令四例RMSE变大，不能直接作为对齐修复。

32帧完整验证`normalizedRGB=(resizedPremultipliedRGB-mean*alpha)/std`，最大残差≤7.153e-7。丢弃alpha确有数值偏差；每帧3948像素alpha偏1，其中去4px边缘后仍1360，先前“边缘偏差”口头描述应收窄为包含少量内部像素。离线除alpha使相对官方RMSE仅下降约0.3%–0.8%，仍未测答案因果；不能凭小误差排除语义影响，也不能认定已解决误判。首稿4px边缘额外断言失败已保留，公式与原数组不改。证据`precision-preparation/pixel-stage-output/{parent-stage-verification,parent-alpha-formula}.json`；输入/源码/阶段SHA、compile/render终态通过。

下方原权重对照结果保持：官方8帧3/4、20帧两计数1/2，仍非真机产品验收。下一有区分力的alpha语义控制必须用相同源/同权重，显式冻结新输入、只改alpha并保持其他项；本轮尚未运行该臂。停止无新假设的同输入重放。F08open/not-ready、目标active，无活动模型或下载进程。

## 2026-11-11 原权重20帧对照完成：计数1/2，三次发射过程进入采样仍答错

最新[原始权重报告](validation/f08-qwen2vl-original-bf16-20261111.md)及离线跨臂收据`artifacts/verification/f08-qwen2vl-preprocess-reference-20261111/precision-preparation/completed-arm-audit/comparison.json`已记录本轮终态。固定原始BF16存储值精确提升FP32、复用冻结官方20帧输入，11448输出B正确、1811输出A错误，严格1/2；原权重官方8帧B/C也是1/2，既有4bit官方20帧A/A为0/2。无新下载、解码、iPhone操作或模型重放。

1811全部20张保留PNG已按时间人工核对：帧4→5→6→7、8→9→10→11、12→13→14→15→16可辨三次持球/释放/收回过程，释放后画面约2.5025、4.5045、6.5065秒，与官方计数3相符。不能再仅以关键事件完全漏采解释；这是稀疏帧人工观察，非独立重标注/完整视频事件边界，也不证明resize及embedding后所有线索可辨。模型选6/A而非3/B，A-C仅0.001621，原始权重/更多帧没有解除此计数不可靠。

新Metal进程12.502s、父验收3.752s均exit0；30输入绑定、40帧SHA、非视觉文本token保持、729参数精确提升/shape、4完整logits含EOS、28层KV、11旧模型/28保护项、49旧输出摘要通过。16门检查通过，证据`precision-preparation/{fp32-dense-plan.json,fp32-dense-launch-result.json,parent-fp32-dense-verification.json,fp32-dense/}`及source/verifier diff。跨臂额外核匹配pixels/tokens/prefill、8帧原/官方位置相同、20帧位置对应旧4bit，以及五臂首向量；首稿对EOS列表次序的额外假设失败已保留`completed-arm-audit-red.json`，改为同臂次序一致及固定集合后通过，未改旧运行。

现证据支持多层原因：Swift视频网格路由和Python参考分段attention确有已证缺陷；校准后预处理仍会影响选择（原权重官方8帧3/4，相同权重原iPhone像素1/4）；剩余精细时序计数在此配置仍不可靠。不能唯一归因量化、认定所有视频理解做不到或宣称iPhone已修复。下一有效实施分支是隔离Swift预处理与官方控制的数值对齐，再以既定4bit/固定病例验真实语义；先找出具体像素阶段，不能直接把宿主原权重3/4当移动端提升。停止相同输入重放/任意帧数与prompt扫描。F08仍open/not-ready，目标active。

## 2026-11-11 原权重FP32配官方像素：3/4，预处理影响已实测，稳定性仍不足

最新[原始权重组合报告](validation/f08-qwen2vl-original-bf16-20261111.md)补新真实结果。同原权重FP32，仅像素换为此前已验官方ICC-sRGB8帧，原文B/C/C/A、严格3/4；同权重原iPhone像素C/C/B/A为1/4。11448及7915由错转对，故不能把剩余全错笼统称原始模型能力不足；小像素路径确能翻转。两个新增正确margin0.021706/0.029209，未证明稳定修复，非iPhone质量或产品通过。

新Metal进程11.038s、父3.588s均exit0；24输入SHA、729全参数精确提升/shape、8完整logits含EOS、28层offset、11旧权重/28保护项与35旧输出摘要通过。14门检查passed（新3项RED保留），证据`precision-preparation/{fp32-official-plan.json,fp32-official-launch-result.json,parent-fp32-official-verification.json,fp32-official-pixels/}`及source/verifier diff。原BF16/原像素FP32/旧4bit不重跑、不覆盖。当前无模型进程。

本节后续20帧已完成，结果及下一步以上方最新节为准；以下保留原实验顺序。

## 2026-11-11 同原始权重FP32计算已完成：仍1/4，不能只归因量化或BF16并列

最新[原始BF16及FP32控制报告](validation/f08-qwen2vl-original-bf16-20261111.md)已补真实结果。新`run_fp32.py`与父verifier独立冻结，仅同729原始BF16参数内存精确提升FP32，完整逐元素相等/finite检查通过，仍MacMetal。四题C/C/B/A，严格1/4；BF16的A/C/B/A也1/4，11448只变成另一个错误答案。7915不再并列但仍B错，因此BF16舍入不是三题错误的充分解释。无新权重/落盘转换/CPU/Torch/Ollama/iPhone/生产改动；旧BF16输出全摘要保持。

新进程14.085s、父3.601s均exit0；8完整新logits含EOS/729shape布局/28层KV/11旧模型/28保护项通过。BF16与FP32两臂原tokens/pixels/positions/ranges/offsets/评分逐项相同，见`precision-preparation/parent-bf16-fp32-comparison.json`。事前plan/source diff、11门检查及真实RED/GREEN保留；旧缺权重测试曾误依赖真实输出目录不存在，已仅改用临时测试目录，不改旧runner/运行数据。主收据`fp32-{compute-plan,launch-result}.json`、`parent-fp32-{result,verification}.json`、`fp32-compute/`。当前无活动下载或模型进程，原监控33673终态0、expiry1032终态143，不重启。

下一尚未执行且有区分力的本地分支：同原始权重FP32，只换成已验证官方ICC-sRGB8帧像素，与既有4bit官方像素结果对照，检查预处理/权重交互；先冻结新输入臂，不重放相同4bit/BF16、不调prompt/评分、不另下模型。现证据只能说此固定配置在精细计数/变化上不可靠，不能说所有视频理解本来做不到。F08open/not-ready、目标active。

## 2026-11-11 原始BF16已完成：仍A/C/B/A、1/4（FP32检查前历史）

最新入口：[原始BF16对照报告](validation/f08-qwen2vl-original-bf16-20261111.md)。原始两片精确SHA/size/index/729BF16合同通过，用户授权后的唯一MacMLXMetal四题完整输出A/C/B/A、严格1/4，逐项复现4bit/iPhone。父验收8全logits含EOS、28层offset、20输入、11旧模型与28保护项passed；启动进程9.949s，不是移动端预算。主证据`precision-preparation/{download-contract-result,precision-launch-result,parent-precision-result,parent-precision-verification}.json`及`inference/`。

下载实际已结束：ModelScope Qwen固定revision a25f63f…的两片公布SHA/size与目标官方895c3a49完全一致，同1MiB片段SHA通过后仅同大分片GID换地址、保持inode/断点/checksum，随后超100MB/s完成。完整SHA通过才由原watcher33673自动执行模型与验收，现session33673终态exit0；expiry维护1032经精确PID/源码/cwd核验后已终态143，不重开。全部传输收据见`download-recovery2/`；无全局配置/生产/依赖/默认/iPhone变更，无Torch/CPU模型。下方下载进度及“仍未运行”均为历史。

结论只削弱4bit独有问题解释；不能称理论做不到，旧4bit源revision仍未固定，不能单归量化。两错误题BF16标签并列，另错误题margin0.125；下一新诊断臂拟同已验BF16权重仅内存精确提升FP32，保留原四输入/分段attention/缓存/评分，仍Metal、独立输出一次，不下载或落盘转换权重、不重跑旧4bit/BF16。先冻结新分支再执行，不能把原始BF16存储叫FP32权重。F08仍open/not-ready，目标active。

## 2026-11-11 最新恢复2：小分片完成，大分片继续传输，BF16尚未运行（已完成前历史）

**下载路径补核：**RPC全局实测`numActive=1`，本次总下载速度与大分片相符，无其他活跃aria2任务竞争。对同官方签名地址的1MiB Range有界探针：宿主经当前代理HTTP206/约94.9KB/s，宿主直连HTTP206/约149.9KB/s；但实际aria2容器直连exit35/0B，经同任务代理HTTP206/1MiB/约116.8KB/s。宿主结果不能外推容器可直连，未移除代理、未改RPC任务/全局配置。证据`download-recovery2/{bounded-direct-proxy-range,container-direct-proxy-range}.json`；片段探针不构成整文件完成或SHA通过。随后同GID仍active16连接，约836MB/3.99GB，原两监控存活，尚无BF16结果。

**后续活连接恢复：**同大分片GID实际连接从16衰减到3、约47.8KB/s，未终态；先核partial/backup inode及原checksum，取得同revision官方新签名HEAD并验证大小/来源/更晚Expires后，仅同GID`changeUri`加原split16选项刷新。aria2内部重启连接，GID/inode/断点保留，无新下载任务、无全局配置变更。`bounded-live-connection-refresh.json`保留单次操作；实际16连接/瞬时4.2MB/s，随后约0.24MB/s，不能称持续提速或根因已修复。观察见`connection-refresh-observations.jsonl`及原watcher状态。此手工操作已更新URL有效期，续期进程1032将读取最新URI，尚未到自动续期触发点。

**后续续期保护：**实测大分片官方CDN URL有`Expires`，按当前低速可能下载超过签名期限。新增仅维护原大分片GID的`download-recovery2/renew_uri.py`（工具session **1032**；PID/sourceSHA在`uri-maintenance-started.json`），每30秒观察，距最新签名到期600秒内才从固定官方revision做HEAD，验证相同3,988,609,112B/官方CDN/更长有效期后`changeUri`，最多12次；不新增下载任务、不调用模型、不改原推理监控。不能重开维护实例。HEAD预检已HTTP200/大小一致/新签名延长，尚未实际触发续期；只把签名expiry数值写证据，不保存URL。7续期guard检查及原7门检查通过；原门首次误用系统Python导致2项`mlx-metal`元数据缺失，已改为直接隔离Python3.12运行，原错与订正收据保留`uri-maintenance-parent-assessment.json`。这不是模型错误或包安装需求。续期维护终态/错误见`uri-maintenance.jsonl`，原推理监控仍唯一session33673。

本节覆盖下方旧下载进程状态。用户“允许下载并运行对照”仍有效，不重复请求授权。唯一当前监控为`precision-preparation/download-recovery2/watch.py`，工具session **33673**；PID与冻结源码摘要在该目录`watcher-started.json`。当前大分片GID **01fa8a1eac8202a8**，小分片GID **f18edddaee01dc75**。最新已读观察中小分片429,441,656B已complete，大分片456,114,176/3,988,609,112B仍active；这是时间点值，续接必须读`download-recovery2/watcher-status.jsonl`或poll同session。尚未完成整套SHA门，也未运行BF16推理，F08仍open/not-ready。

旧大分片00731ae6320126ff实际终态error2 `No URI available.`，旧watcher session85164已exit1，不能再当活进程。服务全局`on-download-stop=/config/script/delete.sh`实际删除了旧partial及`.aria2`，旧累计进度不可加入新任务。保留`terminal-large-shard-error.json`和恢复决定；未改服务全局hook、配置或其他任务。

恢复2只重建已失败大分片；小分片保持原GID及partial inode，以`changeUri`换成同revision官方HEAD解析的CDN地址，两项实际曾观察16连接。签名URL只在内存传给RPC，不写证据。目标内`.resume-backup/`用hardlink保护当前两个文件、每20秒复制小型控制记录，无权重块副本；若服务再删除canonical路径，先检查备份，不盲目重提。入口`download-recovery2/{decision.json,submissions.jsonl,small-uri-recovery.json}`。

当前监控仅观察同两GID；两者complete后唯一执行权重contract→MLX Metal BF16对照→父离线验收，任何终态错误停止、不自动重跑。不另启监控或`run_precision.py`。四组原iPhone张量、原评分及precision/conversion-source归因边界保持；不做iPhone操作、不用Torch/CPU模型/Ollama、不改生产依赖或默认。

## 2026-11-11 用户已授权原始BF16下载与运行：现有aria2任务传输中（恢复2前历史）

用户明确“允许下载并运行对照”，此前下载授权阻塞解除，goalactive。原计划两shard/4,418,050,768B/固定revision895c3a49/6GiB预算不变，授权收据在预处理证据根`precision-preparation/download-authorization.json`。已通过指定localhost6800RPC提交；初两GID7f83e0e4ae34552e/57e7596f4ca3d0f4都error2 Timeout/0B保留，不当活进程续poll。

**当前已启动主代理监控程序**`precision-preparation/watch_download_and_run.py`，工具session **85164**，PID在`watcher-started.json`；首次真实poll两个GID均active，合计约135MiB、约0.12MiB/s（时间点值）。每20s观察同GID，无重提交；下载complete后自动按已授权顺序运行download-contract→precision-launch→parent-precision，完整stdout/stderr/exit收据独立保存。精确源码摘要已冻结；权重/版本/输入/GPU门由原runner重验。任一下载终态error或验证/推理失败保留`watcher-failed.json`并终止、不自动重跑；观察RPC瞬时失败单列且续poll同任务，不能因等待到期重启。后续先poll **85164**／查同PID及`watcher-status.jsonl`、`watcher-completed.json`，不要另起run_precision或监控重复实例。

低速补查：实际`split16/maxconnections16`但仍2连接；继承reuse-uri=false，核官方文档后仅同两GID改true，保留partial文件，未观察稳定提速，不认定唯一原因。`changeOption`官方文档说明除少数限速项外会由aria2内部restart连接，不能写成完全无连接重启；GID和部分文件保持。原官方64KiB Range探针HTTP206/65536B、36.9KB/s；hf-mirror同revision64KiB探针HTTP206但仅16KB/s，不优于原点，未给下载任务加镜像或另开完整下载。状态仍active持续增长，来源/全局配置不变，不把低速当终态或标blocked。证据`task-reuse-uri-control.json`、`task-reuse-uri-observations.jsonl`、`range-network-probe.json`、`mirror-range-probe.json`。

后续连接降至各1条，容器日志有Timeout及EOF（仅保留净化的错误类别，不保留签名URL）。针对活跃网络连接仅两GID将max-tries1→3、retry-wait5s、connect-timeout60s/timeout120s；证据`bounded-connection-recovery.json`。这是有界传输恢复，不新建GID、不重试模型；尚未证实提高并发或稳定速度，不能称原因已修复。最后一次工具session85164真实poll仍active各1conn、累计约266MiB/4.115GiB；后续查实时状态，勿照抄本时间点数字。

已确认传输原因：全局proxy旧LAN192.168.116.7:7897不可达，实际en0为192.168.38.7；实际aria2容器同文件HEAD旧proxy10s timeout、新proxyHTTP200/1.26s。仅两项新任务override当前proxy，不改服务全局配置/凭据/其他任务。当前有效GID **00731ae6320126ff**（3,988,609,112B）／**f18edddaee01dc75**（429,441,656B），同容器`/downloads/odai-qwen2vl-bf16-895c3a49-20261111`映射宿主CrawlerBot/aria2/downloads。自然实时状态见`aria2-recovery-status.jsonl`；不因观测等待到期重开下载。保留原失败、recovery决定、两container探针和提交journal。任务级split/maxconnection曾从4调16，同GID不重建，全局无带宽限速；实际进展须看最新tellStatus，不以配置改动当提速证明。

config/index已复制到目标，权重正在下载、尚未完成SHA验证或BF16模型运行。`run_precision.py --preflight`重新绑定四病例20文件和版本通过，7单测通过；父离线`verify_precision_result.py`已写且只做语法检查，尚无真实结果验收。下载complete后先verify_weight_contract.py --downloaded-dir精确两shard/size/SHA/index/729BF16/config；检查GPU队列再唯一run_precision.py，记录launch/stdout/stderr，再实际执行父verifier。只用原四组iPhone prepared张量、不改App/生产/依赖/默认、不用Torch/CPU/Ollama、不重放旧4bit。旧权重源revision未固定，结论仍限定precision/conversion-source组合。下方“待授权／blocked”均为历史，不能再重复问授权。

## 2026-11-11 阻塞审计：现有权重分支已验，待原始BF16下载授权

CoreImage边界/语义与327模块反量化kernel控制均终态通过证据校验，但质量仍1/4，F08open/not-ready。本次只读复核计划的BF16下载目录与官方/社区两个定点HF缓存根均不存在，原始权重未就绪；前一运行handle11571终态exit0，没有可等待的存活推理任务。上一goal回合是实际新对照进展，不把它写成空等待；同一新权重授权条件已跨至少三个连续目标回合存在，现在无更多有区分力且不越过现有边界的分支。记录`artifacts/verification/f08-qwen2vl-dense-kernel-20261111/authorization-blocker-audit.json`，本轮按blocked门收敛目标，未标完成。

恢复条件：收到已发结构化问题关于原始BF16约4.115GiB/6GiB预算/现有aria2RPC下载的明确允许，或用户提供同revision及SHA的现有权重。普通“持续推进”不自动改写此前no-new-weights边界。恢复后按precision-preparation已备工具核精确两shard/hash/index/BF16，再单GPU原四组iPhone张量推理、保存完整logits并比较；不再重跑相同4bit、任意扫prompt/帧数或重启quota失败helper。下方goalactive属各实验完成时历史状态。

## 2026-11-11 现有4bit反量化算子对照完成：普通Metal矩阵仍三错一对

最新入口：[现有4bit反量化矩阵算子对照](validation/f08-qwen2vl-dense-kernel-20261111.md)。显式冻结与前轮相同host CoreImage四题pixels/原tokens/positions/meta16文件，现有权重只在内存重建FP16，将全部327量化模块替换为普通matmul/indexing，保持分段attention/fresh28KV/balanced512/greedy32。实际MacMetal唯一进程exit0/5.199s，四题完整仍A/C/B/A、严格1/4，逐项复现量化算子与iPhone；首logits差NRMSE0.00188–0.00364。削弱量化kernel单独归因，不能恢复原权重、排除量化信息损失或宣称原始模型做不到。

6项已知值GPU控制先通过；真实327矩阵完整finite及各首末两行独立nibble解包→FP16精确断言通过。父离线核327header元数据/路径/shape、8全向量含EOS/SHA/argmax/严格原文/28层offset/11模型通过。完整dense权重及sample数组未落盘，不能称父独立复算实际已释放矩阵；约4.11GiB仅内存、进程已终态。源diff/事前计划/转换收据/完整向量与父报告见`artifacts/verification/f08-qwen2vl-dense-kernel-20261111/`。Context7认证错保留且未重试，官方文档HTML及固定源码核验。

无新权重/iPhone/生产/依赖/默认/评分变更。下一原始BF16同输入分支仍待已发问题明确答复，不能默认下载或拿反量化替代原BF16。F08open/not-ready，goalactive。

## 2026-11-11 同源CoreImage边界完成：宿主／iPhone接近，未解除语义阻塞

**后续新输入语义臂已完成（不覆盖原无模型臂）：**显式冻结host同源CoreImage新pixels及原tokens/positions/meta16文件，仅现有4bit+校准分段attention、fresh28KV/balanced512、greedy32在Mac Metal四题各一次，完整输出A/C/B/A，逐项复现旧iPhone、仍严格1/4。8份完整logits含EOS保留，父独立SHA/argmax/原文/严格评分/28层offset/11权重复核passed；实际进程exit0/4.165s非产品预算。新top两标签margin0.03125/0.125/0.125/1.828125，小差异未使这四题翻转，不等于所有预处理无关或证明模型做不到。证据根下`pixel-inference-plan.json`、`pixel-inference/result.json`、`parent-inference-verification.json`，源码diff保留。无新权重/iPhone/生产/旧输入重放；下一BF16分支仍待原结构化问题明确答复，不重复问或默认授权。

新入口：[同源CoreImage宿主／iPhone预处理边界](validation/f08-qwen2vl-coreimage-boundary-20261111.md)。相同四原媒体/8帧实际时间/固定源方法/mean/std/504×280/RGBAf，Mac同源路径相对冻结iPhone pixels NRMSE0.00219–0.00384，RGB255 MAE0.103–0.132；比ICC-sRGB官方Pillow路径RMSE小3.36–5倍。统一iPhone RMS分母后比较；旧报告原值采用official RMS，原收据保留。宿主／设备原解码帧未共同导出，仍非逐字节等价，不能排除小差异语义影响或称质量通过。

首轮额外alpha==1断言真实失败，8帧已成功输出且所有RGBA有限；原asMLXArray直接丢第四通道，无恒1合同。原错保留，新显式恢复复用首例8帧、其余三例各仅一次，不改alpha/RGB、不重编译或重解首例。父直接裁片独立重建11520packed行全元素相同、32帧finite、媒体/时间/张量/原报告SHA保持；11模型及28保护项保持。证据根`artifacts/verification/f08-qwen2vl-coreimage-boundary-20261111/`，`continuation-result.json`与`parent-independent-verification.json`终态通过；初稿NRMSE约定不一致失败另保留。

无新模型推理/iPhone操作/新权重/生产变更。现有4bit+校准参考的四题仍1/4，官方颜色控制2/4、计数20帧0/2不能外推为理论做不到。下一BF16精度分支仍待已发问题明确授权，尚未RPC下载，不把继续任务当下载批准；现有工具和6GiB预算准备保持。目标active，F08open/not-ready。

## 2026-11-11 官方预处理与计数20帧完成：像素会影响边界，密集计数仍失败

最新入口：[官方预处理、颜色配置、密集计数与精度对照准备](validation/f08-qwen2vl-preprocess-coverage-20261111.md)。实际固定Transformers4.57.6 Qwen2VLImageProcessor无Torch可运行；从原四视频导出同时间host8帧，原媒体SHA/实际帧时间保持。PNG嵌入HDTV ICC，直接Pillow RGB比iPhone平均差6–10/255，先LittleCMS转sRGB后仅0.44–0.59/255；宿主/设备解码与重采样仍非逐字节等价，不能宣称唯一CoreImage缺陷。

新官方颜色控制像素+已校准分段attention，固定同tokens/位置/权重/缓存完整greedy四题C/C/C/A（旧iPhoneA/C/B/A，正确B/B/C/A），严格2/4；7915改善但C-B仅0.015625，不称可靠修复。另两道约10s计数题8→20帧（THW10,20,36，1800visualtokens、原文本token保持）完整均A，仍0/2；不要重复相同模型试跑或把密集失败泛化为理论做不到。

父复算10组实际官方预处理字节/6首token全向量/11模型/旧保护清单/28层缓存offset通过；生成实际单字母后EOS151645，第二步EOS全向量未保留，不能称其独立复算。首稿缺images=None的TypeError、deprecated警告、原坐标收据96实际144父订正保留。无新iPhone操作/生产/共享依赖/默认/权重改动。证据根`artifacts/verification/f08-qwen2vl-preprocess-reference-20261111/`及`f08-qwen2vl-count-coverage-20261111/`，此次均终态。

**下一分支待新权重授权，不能默认下载：**已只读核官方非量化bfloat16模型Qwen/Qwen2-VL-2B-Instruct revision895c3a49bc3fa70a340399125c650a463535e71c，两权重共4,418,050,768B（4.115GiB），11关键架构匹配现有4bit；config/index/API元数据和SHA在预处理根`precision-preparation/`，完整ready-plan.json，工作磁盘预算6GiB/观察可用77.85GiB。结构化问题已发，收到允许前不发aria2RPC任务；获准后用用户指定localhost6800服务、实际容器/downloads映射，不把secret写证据。仅现有隔离PythonMLXMetal对照原四组真机张量，无Torch/CPU模型/iPhone替换/生产依赖。4bit源revision未被模型卡固定，不能把两模型差异单归量化。若用户拒绝新权重，保留该分支并继续可用的现有源验证。任务目标active、F08open/not-ready。

等待期间已新增精度工具预检：`precision-preparation/run_precision.py --preflight`绑定四病例20张量/元数据及固定版本、不importMLX；正式模式在精确两权重SHA/index/729BF16/config门后才加载，逐步保存所有logits包括EOS。尚未运行模型分支、未下载。实际sanitize键映射729/729匹配4bit基础参数；5权重错误拒绝+2运行前门检查通过，BF16小型conv3d/SDPA/matmul三GPU已知值检查通过（无模型权重，不能替代全模型验收）。首稿路径parent多一层的FileNotFoundError在import前保留`runtime-path-red/`。详见新报告“精度对照工具预检补充”；等待新权重问题的明确答复，不能重复模型试跑代替授权。

## 2026-11-11 四题视觉边界已完成：Python参考也有分段缺陷，校准后同真机三错一对

最新入口：[原视频四题同特征与分段注意力对照](validation/f08-qwen2vl-residual-four-20261111.md)。只取原索引8/11/5/20：11448、1811、7915原完整视频及9431阳性控制；原媒体/题目/真实帧时间/评分不改。已修正两处网格路由的iPhone PID3369同进程completed4/4，原文A/C/B/A复现前次对应结果，严格1/4；双读报告SHA2ec60b5b…，实际positions4/4匹配官方参考。实际合并特征原dtype float16，导出存float32、注入恢复dtype并核精确往返。

Python原生视觉特征NRMSE0.798–0.959、文本逐位相同；11448原生B正确，注入Swift特征后A错误。但这不能指认Swift视觉故障：实际mlx-vlm0.3.9的`[1,N,N]`浮点mask用两维slice且初值1，未隔离时间段。真实Metal已知值四段1/3/7/11全输出5.5，保留1失败/1通过RED；按仅读取的官方Transformers4.57.6非flash路径在新Python诊断wrapper分段attention，复用原投影、不改安装包后2检查通过。同四题控制视觉NRMSE降0.024–0.039、logits0.00213–0.00430，首token全同iPhone A/C/B/A。Python仅首token，不能写完整生成或官方评估通过。

因此历史Python视频/分块对照的视觉参考有缺陷，只修MRoPE不能称完整官方参考；原向量/历史结果保持，未重跑旧端点输入、不编造其校准后答案。Swift网格路由bug及同输入iPhone改善仍成立；当前四题不再支持另一处Swift独有语义故障，仍未区分4bit损失/固定模型能力/两侧共同8帧与预处理，不能断言模型理论无法识别。后续真正有区分力的是同输入精度或独立官方预处理对照；本轮没有新权重下载、生产/共享依赖/lock变更，F08仍open/not-ready。

父复算9完整logits/4组实际张量/各层cache offset/11模型/旧19及本轮9保护摘要通过，7位置+2分段共9项检查通过；旧36纯合同保持。无新native/helper或模型自动重试。真机thermal0→1、phys采样峰值3,108,916,408B、无guard，含额外导出forward不能当产品预算。candidate卸载、普通Combined重开、所有原App安装元数据保持；签名App留存后只删本轮DerivedData名义851,044,503B，SourcePackages保留。证据根`artifacts/verification/f08-qwen2vl-residual-four-20261111/`，`parent-independent-verification.json`、`cleanup-receipt.json`。此项已完成，不要再启动旧build/run或重放相同四题。

## 2026-11-11 修正后原24题结果收齐：跨进程聚合9/24，F08仍阻塞

最新完整入口：[原因、两处补丁、真机结果与边界](validation/f08-qwen2vl-cross-runtime-20261111.md)。已确认Swift Qwen2VL.prepare/prepareContinuation视频网格错路由；同输入端点7915B错→C对，实际MRoPE8/8匹配参考。然而去掉诊断导出、仅两处model修正后，原24题连续PID3303在78.049s触发serious热保护，仅12项；nominal温度预检后分6+6的PID3324/3330收齐余项，不重放前12。三个进程24个唯一输出strict9/24（旧单次12/24），1strict改善/4退步、5格式违规；camera_motion1/4、memory1/4、abstraction0/4、counterfactual1/4、physics2/4、semantics4/4。唯一strict改善是A.→A；端点7915改善没有推广到原整段memory，仍B错。

不合成连续24completed报告，不把分批完成当热/性能通过；原24与新分批未比完整prepared pixels字节，已核相同原媒体SHA/物理帧时间/题目/prompt/token数量等。两处缺陷修正真实有效，但固定2B4bit配置仍不可靠；剩余模型/量化/共同预处理/运行时数值因素未唯一归因，不能说模型理论无法识别。下一有区分力工作是残余层级对齐或另行授权精度对照，不继续同输入prompt扫参或回滚正确位置求分。

保留可审阅最小[patch](validation/patches/f08-qwen2vl-video-grid-routing.patch)，对原checkout只读apply--check通过；生产/共享依赖/默认/lock均未改。batch原报告、guard双读和独立24聚合验证通过，旧14+19保护摘要/11模型保持。candidate已卸载，普通Combined重开，末批所有既存App元数据保持；无活动模型/helper。详见新证据根perception-regression/、perception-batches/。下方“原24回归尚缺/尚未跑完”是历史。

## 2026-11-11 已确认Qwen视频网格路由缺陷：同输入iPhone一题由错变对

最新入口：[同张量Metal与真机补丁结果](validation/f08-qwen2vl-cross-runtime-20261111.md)。独立Python先区分单图共同误判/视频运行时差异，再cached分块控制未复现7915B；仅隔离SourcePackages修Qwen2VL.prepare/prepareContinuation两处image/videoGrid路由，加实际位置导出。静态2RED→2GREEN、签名Release88.756s；首PID3211在其他App安装期间中断只5题，保留无视频结果；用户确认其他测试结束后同binary独立run2 PID3283完成8/8，双读SHA58d8f322…/父严格校验通过。

8项tokens/pixels/mask与基线逐字节相同，实际position IDs全部匹配官方参考。原A/B/A/B/C/C/B/C→新A/B/A/B/C/C/C/C；7915视频B错→C对，两视频0/2→1/2；3094仍错，白瓶后图两序仍误判存在。确认真实视频编码缺陷影响该病例，但非全部错误唯一原因；完整logits仍有差异。生产/共享依赖/权重/题目/评分未改，F08仍open/not-ready，原24题与birds的补丁后回归尚缺；不能沿用旧12/24作为修正后质量。

run2约2.818GiB采样phys峰值、thermal0→1，无guard触发（含额外forward，非产品预算）。candidate已卸载，普通Combined重开且本run既存App元数据均保持。原19文件/11模型、共享Qwen源码及sampler保持。全部模型/助手进程终态；无CPU模型/PyTorch/Ollama/新权重。下方“尚未iPhone验证”均为历史。

## 2026-11-11 同张量跨实现 Metal 已完成：单图共同误判，视频差异待隔离

最新入口：[跨实现结果与剩余因果缺口](validation/f08-qwen2vl-cross-runtime-20261111.md)。新iPhone PID3127导出8病例且原文逐项复现旧28题；Python独立MLX Metal同已有权重/tokens/pixels完成12次首tokenforward，父重算SHA/finite/logits指标通过，11模型及19保护文件保持。白瓶后图两选项顺序仍跨实现误答存在；不能只归Swift。Python旧0.3.9另有多图position索引错误，明确参考位置控制后两图logits更接近Swift、答案未变。视频7915 Swift B错/Python C对；3094均C错。

已核Swift两处把视频frames路由到imageGrid、videoGrid=nil，位置退化为线性；Python线性控制使logits接近但未复现7915的B，尚非唯一因果结论。下一步按实际balanced分块(418+417+1、411+411+1)控制缓存，再隔离修正两处路由做同iPhone验证。F08仍open/not-ready，无生产修复/新权重/CPU模型/PyTorch/Ollama。candidate已卸载、普通Combined重开、既存App元数据保持；当前无活动模型进程。下方“下一独立对照尚未执行”是历史。

## 2026-11-11 单图/两图/视频拆分真机完成：基础物品误判与模态差异并存

入口：[物品识别与变化诊断](validation/f08-qwen2vl-object-change-20261111.md)。用户明确允许主代理接手后，只修新隔离Suite，原hy3拒稿/hy4free429保留，不重试helper/native。27pure/21host故障场景通过；Swift6可变捕获RED后修不可变副本，最终unsigned/signed Release通过，sampler字节/guard不变。冻结sourceFingerprint eaeeca7d…，不改生产/依赖/模型/题目/严格评分。

唯一iPhone Metal PID2797/runId26B74161-3D49-4E85-987D-00E9C2F1DDCD完成28/28，报告双读SHA36bfa4a7…一致、父校验通过。单图严格14/24（7无效带句点、3合法选错；存在12/18、缺失2/6），两图1/2，端点视频0/2。白瓶后图两种顺序均误答存在，食品前图也有合法错答：故不能只归时序/缺帧或格式。7915同端点两图C正确、视频B错误；3094两入口均C错误期望B。图片/视频packed1440/2880与时间patch不同，模态差异不是adapter bug或同张量证明；原始模型/4bit/processor/runtime未唯一归因。12顺序组仅5合法语义一致（4正确1稳定错），其余含无效输出，勿统称语义翻转。旧24/8/16成绩保持，F08open/not-ready。

load975.611ms，5316样本/58.559s，phys2.805GiB、resident1.342GiB、thermal0→1，无guard触发，非产品预算。candidate已终止卸载、普通Combined重开metadata保持；17旧文件及11模型摘要保持，签名App保留后仅清本轮DerivedData名义约0.83GiB。证据`artifacts/verification/f08-qwen2vl-object-change-20261111/diagnostic-summary.json`及device-run-1终态/评分、restoration-receipt.json。无活动helper/推理，无CPU/PyTorch/Ollama/新下载。

下一有区分力步骤：失败白瓶后图+前图阳性，在独立MLX Metal实现同已有权重/同tokens与pixels比logits；再视结果考虑原始或高精度权重分离量化。尚未执行，不能声称根因已唯一确定或功能修好。下方quota等待/未推理属历史。

## 2026-11-11 物品/变化诊断候选被拒，修正助手额度429终止

新入口：[物品识别与变化比较诊断](validation/f08-qwen2vl-object-change-20261111.md)。28题及清单SHA ae59f42b…冻结，80素材/15父报告synthetic检查通过；尚未新安装/推理。原24题及8/16帧结果保持，F08仍open/not-ready。

免费hy3初稿631.328s终态并交付4文件，父真实pure编译RED、iPhoneOS unsigned build65（不存在VLMModelContainer、timing枚举参数、String→Data）及流/报告/失败停止合同不符，拒收。修正hy4free请求7.008s终态，CLI0但result error_during_execution/is_error=true，HTTP429/code6004/categoryquota，零文件、context保持、无权限拒绝；服务端提示2026-11-11 15:12:49 UTC+8重置，不是恢复证明。两helper均终态，PID70008已不在；不按下方历史“生成中”续poll或重发。证据`.omx/artifacts/cbc-hy4free-qwen-object-change-correction-20261111/parent-assessment.json`及同目录raw/result。

按AGENTS.md停该线路、保留原错，已请求用户选择恢复（主代理接手实现例外或指定helper），未获回复前不自动换hy3/付费/native/Ollama。取得合格修正后：父真实pure/故障注入/构建→冻结→fresh设备门→唯一28项Metal运行→双读/独立评分→恢复。故障注入脚本目前仅Python语法检查，尚未验证Swift stub编译/运行。新证据根`artifacts/verification/f08-qwen2vl-object-change-20261111/`，旧consumer/manifest/reports保护不变。

## 2026-11-11 记忆题8/16帧iPhone对照完成：均1/4，增帧无收益

新入口：[记忆题覆盖诊断](validation/f08-qwen2vl-memory-coverage-20261111.md)。父直接调用原隔离snapshot导出四题8帧，四组物理时间与iPhone run-2报告逐项相同，四次snapshot清理通过；仅host观察，未证设备逐字节像素parity。video_7915的水瓶/食品、video_3094的白瓶在前后帧清晰出现/消失，两题错答不能仅以缺少前后状态解释。video_2996有物品依次减少线索，但完整入包动作仍可能落在采样间隔；正确控制video_7967保留。

新证据根`artifacts/verification/f08-qwen2vl-memory-coverage-20261111/`。固定Qwen2-VL2B4-bit/runtime/四题及顺序/prompt/严格评分，8帧PID2735和16帧PID2737均完成4/4，终态双读/父独立校验通过；四题原文均为B/B/C/B，严格各1/4且全部合法字母。8帧逐项复现原24题；16帧packed shape2880→5760、THW4→8证明新帧进入模型，增帧仍无收益。单题总耗时median5.444→10.817s，采样phys2.840→2.996GiB，thermal0→0/0→1；保护3.5GiB/serious/180s未触发，不是产品预算。源码fingerprint519ca00e…，原始报告/成本/边界见comparison.json及新报告。

免费hy3初稿839.240s与三项定点修正均终态；源码仅新consumer副本，父50纯检查/真实8组snapshot/7非法帧拒绝/7guard故障注入/13报告校验/最终签名Release通过。两处编译RED及采样错误只停monitor的RED保留；后者已改独立未知telemetry收据并exit70。无新native/Copilot/付费/Ollama/PyTorch路线。candidate已卸载、普通Combined重开且元数据保持，旧consumer13Swift/manifest/24题原报告SHA保持，tmp为空；签名App保留后只清本轮DerivedData名义0.831GiB。无活动helper/设备推理。

F08仍open/not-ready；这四题的失败不能归为单纯缺帧，仍未唯一拆开模型/量化/runtime。下一项有区分力的诊断是同一两道变化题拆成单帧物品识别与前后变化比较，保持原题成绩，不继续盲目加帧或去标点。

## 2026-11-11 官方Perception Test真机24题完成：严格12/24，质量仍阻塞

最新入口：[24题评估、原因和成本](validation/f08-qwen2vl-perception-test-20261111.md)。固定Qwen2-VL2B4-bit/MLX在iPhone17,2/iOS27.2.1，修正后run-2 PID2483完成24/24，原报告双读一致，父独立校验通过。严格12/24（50%）：camera_motion0/4、memory1/4、abstraction3/4、counterfactual2/4、physics2/4、semantics4/4；5题格式违规，另7题合法字母选错，未放宽评分。模型有部分视频语义能力，但仍不足以作为可靠产品方案，也不能把全部失败归于模型本身。

本轮查明旧snapshot的5s/32MiB/容器与颜色入口不适配原官方视频；仅新隔离副本兼容40s/128MiB及已核普通容器边界。首轮仍在第5题preparation停止，保留原始失败。固定上游再次按物理时间抽样会丢掉已解码帧：父对24组真实host帧时间复现11组少帧，第5题8→6、THW3。隔离Qwen套件仅给重复抽样提供ordinal0...7时间，原图/顺序/物理时间报告保持，生产依赖和snapshot不改；新进程全24题通过准备。此修正不外推timestamp-aware模型。

新进程加载405.63ms（非设备/文件缓存冷启动），单段总耗时中位6.182s，采样phys_footprint最高2.82GiB、resident1.36GiB，结束thermalState2/serious。24题训练split小样本、8帧覆盖/颜色parity/独立native源码复核（旧429不重试）及产品预算仍有缺口。SmolVLM尚未跑这24题，不能声称两模型对比完成。下一步先核记忆题8帧是否漏掉答案事件，再固定题目对照采样覆盖；不要只重跑同输入或去标点。

证据根`artifacts/verification/f08-qwen2vl-perception-test-20261110/`，关键`assessment.json`、`resource-timing-summary.json`、`device-run2-terminal-second-read.json`（SHA7b0e1458…）、`progress-20261111.md`及`restoration-receipt.json`。所有本轮helper已终态；无待收结果/设备推理。隔离candidate已卸载、普通Combined重开、安装metadata保持；原consumer12Swift摘要保持。宿主约81GiB可用。下方历史结果保留，不要将12题控制集10/12替代本轮24题质量。

## 2026-11-09 Qwen2-VL 三次独立冷进程评估：稳定但仍不够进入产品集成

在同一台实体 iPhone17,2 / iOS 27.2 上，用固定 `mlx-community/Qwen2-VL-2B-Instruct-4bit` revision `01af461cdb9574acc09084a0ef94e216e142b085`、固定 runtime `6e338e5`、同12段公开视频、原简短问题、8帧、temperature0/maxTokens32和严格评分，完成三个不同 PID／runId 的独立进程运行。三份原始报告均通过 schema2 严格校验，36行输出逐行完全一致：每次均 **10/12**，birds配对 **1/3**、waterfall **3/3**；稳定失败仍是 birds pair-0 与 pair-1 的 frozen-control，期望 `STILL`、实际 `MOVING`。因此此前10/12不是偶然波动，但也没有解除剩余短视频语义缺口。

冷进程模型加载 min/median/max 为 **844.16 / 858.56 / 1218.26 ms**；36次生成 duration median约 **352.77 ms**、p95最近秩约 **373.21 ms**，首chunk median约 **274.12 ms**。10ms采样观测到最大 process `phys_footprint` **2,375,781,464 B**、resident **1,496,678,400 B**、MLX active/peak **1,740,005,908 B**、MLX cache **938,514,272 B**；三次起止 thermal raw value均为0。RSS、phys footprint和MLX内存口径保持分开，这些只是观测值，尚无批准的产品预算。

裁定为 **`continue-evaluation`**，不是 `ready-for-product-integration-planning`：候选质量优于现有SmolVLM2组合，且Qwen自身三次运行逐行稳定，但固定12行仍有2行稳定失败，且缺更广标注集、内存／加载预算和生产集成门。完整父核验及三份不可变报告见 [parent-verification.json](../artifacts/verification/f08-qwen2vl-mobile-evaluation-20261108/parent-verification.json) 与同目录 `run-{1,2,3}-device-report.json`。隔离 bundle `io.ondeviceai.f08candidate2b` 已卸载，普通 `io.ondeviceai.combined.demo` 已从原安装URL重新打开；生产依赖、默认模型、lock、rubric和签名资产未修改。

## 2026-11-08 Qwen2-VL iPhone 候选：主因已收敛到模型能力，但候选尚未全过

在用户授权的隔离范围内，下载并逐字节核验公开 `mlx-community/Qwen2-VL-2B-Instruct-4bit` revision `01af461cdb9574acc09084a0ef94e216e142b085`；`model.safetensors` 为1,245,868,907 bytes，SHA-256 `3534d3dcfaacd0cd00e935469bc34b6d692b2ba0749c5d9ee422f3f2175cf992`。固定 `mlx-swift-lm` revision `6e338e5` 的 `Qwen2VL.swift` 与隔离探针在 iPhone17,2/iOS27.2 实际编译、签名、安装并运行。12行均使用原 birds/waterfall motion/frozen 素材、原简短问题、8帧、temperature0/maxTokens32及仅trim空白的严格评分；每行 prepared video 为 packed `[1056,1176]`、8个输入帧、`temporalGridT=4`。

原简短问题下严格单行为 **10/12**：6个 motion 全过，6个 frozen 中4个过；配对结果 birds **1/3**、waterfall **3/3**。这与正式500M及隔离SmolVLM2 2.2B两套均 birds/waterfall 0/3 形成明确架构对照：视频确实进入 iPhone Metal 模型路径，Swift-only实现或“根本没送视频”不再是合理主因，当前正式 SmolVLM2 权重/模型的任务能力是领先根因。Qwen2-VL 仍漏判两组 frozen birds，故只能记为有希望的候选，不能改默认模型或关闭F08。原始报告与父核验见 [parent-verification.json](../artifacts/verification/f08-iphone-qwen2vl-20261108/parent-verification.json) 和同目录 `all-device-report.json`。

另做只改问题措辞的隔离实验，强调比较8帧首尾并忽略常识；其12行全部答 `MOVING`，退化到 **6/12**、birds/waterfall配对均0/3，已拒绝。说明剩余birds失败不是靠增加“逐帧比较”文字即可解决，提示更长还会放大标签偏置。此前首轮探针把 `THW.t` 误当原始帧数并要求8而失败；固定源码证明Qwen `patchify` 以 `temporalPatchSize=2` 得到 `gridT=8/2=4`，修正探针后立刻完成推理。这是探针错误，不是模型加载/视频处理失败。临时 `io.ondeviceai.f08candidate2b` 已卸载，普通 `io.ondeviceai.combined.demo` 已重新打开；生产依赖、默认模型、rubric均未修改。

## 2026-11-08 飞鸟相同张量跨实现 Metal 对照完成

宿主环境随后恢复了 MLX Metal：隔离 Python 环境确认 `default_device=Device(gpu, 0)`、`metal_available=True`。因此完成此前阻塞的 birds pair-0 同张量对照，全程不使用 CPU 推理、Python processor、PyTorch 或新权重。固定 Swift 工具重新导出 motion/frozen 各720个 token、`[8,512,512,3]` 归一化像素和49,280维首位置 logits；adapter 与固定上游 prepare 的 token、全部像素和8个 THW 逐项相同，Swift 原文两行仍均为 ` MOVING.`。随后 `mlx-vlm 0.3.9` 模型层在 Mac Metal 上加载同一500M本地MLX BF16转换权重，直接消费完全相同的 tokens/pixels。motion 与 frozen 两行的 Swift/Python top token 均为 `15720`（`MOVING` 首片）；完整 logits NRMSE 分别约 `0.01690`、`0.02431`，不是逐位一致，但没有改变首 token 决策。原始结果见 [python-comparison.json](../artifacts/verification/f08-cross-runtime-birds-20261108/python-comparison.json) 和 `runs/birds-pair0-v1/report.json`。

这项证据显著削弱了“Swift 模型实现单独造成 pair-0 误判”的假设，并把下一步重点收窄到固定500M MLX转换权重／SmolVLM2任务能力，以及原始权重与转换权重的差异。它只覆盖一对 birds 和首 token，不能证明两个实现全层等价，也不能替代正式 birds/waterfall 0/3 验收。下一项最有区分力的根因实验是用原始 SmolVLM2 对完全相同语义输入做参考；产品选型实验则是用不同架构的视频VLM跑同一12段真机套件。

## 2026-11-08 iPhone 上游视频提示文本对照

发现固定 `mlx-swift-lm` revision `6e338e5` 的 `SmolVLMProcessor` 视频提示与 Transformers 4.57.6 参考格式有可复现差异：Swift 将8帧写成 `8 frames`、以 `ceil` 处理小数秒并给帧标签写 `H:MM:SS`；参考处理器写 `eight frames`、截去小数秒并给帧标签写 `MM:SS`。在**隔离依赖副本**中按参考格式修正这几项（总时长仍保留 `H:MM:SS`），成功编译独立 iPhone App。原2.2B 4-bit 权重、12个视频素材 SHA、问题、8帧像素形状、实际时间和生成参数均保持；birds 输入 token 从720变703，说明提示变更实际进入模型。真机全12行完成，严格配对 birds **0/3**、waterfall **0/3**；仅诊断性去句点后也都 **0/3**。首对运动与冻结飞鸟仍均答 ` MOVING.`。逐行原/改报告、源补丁 SHA 与核验见 [parent-comparison.json](../artifacts/verification/f08-iphone-hf-prompt-20261108/parent-comparison.json)。因此这处提示格式偏差是真实问题，但**不能单独解释或修复当前语义阻塞**；未改生产依赖或默认模型。隔离 App 已卸载，普通 Combined App 已重新打开。

## 2026-11-08 iPhone 隔离 2.2B 候选全量对照

获准新增隔离权重后，将公开第三方 4-bit `smdesai/SmolVLM2-2.2B-Instruct-4bit` 固定在 revision `0a4ab666f99095e6c6bd0e1e49aca8822fc46df2`，只装入独立 `io.ondeviceai.f08candidate2b` 诊断 App。iPhone17,2/iOS27.2 真机以固定 birds/waterfall 各三对公开 motion/frozen 视频、原问题、8 帧、temperature0/maxTokens32 运行；12 行均完成且各有 `[8,384,384,3]` 像素输入。设备原报告和逐行素材 SHA、帧数、时间、身份独立核验见 [parent-verification.json](../artifacts/verification/f08-iphone-candidate-2b-20261108/parent-verification.json) 与同目录 `all-device-report.json`、`model-receipt.json`。

严格原标签规则（仅 trim 空白）下 birds 与 waterfall 均为 **0/3 配对、0/6 单行**；全部输出至少有句点或完整句子。只作诊断的去末尾句点后，birds 有 **1/3 配对、4/6 单行**正确，waterfall 仍 **0/3、0/6**。例如 birds pair-0 的运动与冻结都答 ` MOVING.`；birds pair-2 分别答 ` MOVING.` / ` STILL.`；waterfall pair-0 运动答 ` STILL.`，冻结答 ` The waterfall water is moving.`。因此 2.2B 在一个飞鸟对照中能作出区分，但本任务的表现仍不可靠；单纯扩大到这个 4-bit 候选未解除阻塞。原500M正式结果仍各0/3，F08/P11继续 open/not-ready。

当前最直接的失败层是模型/运行时组合在已提供不同帧的情况下作出的时间语义与指令遵循决策；既有500M真机中性像素/重排对照证明视觉信号进入决策，既有上游 prepare 对照没有发现这批 birds 输入的生产适配器差异。这个新候选也使用第三方量化权重和相同 Swift MLX 运行时，**不能**唯一排除权重转换或模型实现问题，更不能证明原始未量化模型“本来做不到”或视频理解普遍不可能。下一项有区分力的验证应固定同一输入，在独立实现或原始权重上比较；此前 Mac CPU 路线在 Metal 初始化前失败，不作为 iPhone 门槛。此次没有改变生产模型、依赖、评分或普通 App；已从设备卸载临时候选 App，并重新打开 `io.ondeviceai.combined.demo`，收据在同目录。

## 2026-11-08 真机与平台边界订正

当前本机已重新发现实体 iPhone `00008140-0008703436D3001C`（iPhone17,2、iOS 27.2、online），原 `io.ondeviceai.mlxvlmconsumer.temporal.v2` App 及其公开 birds motion/frozen 六段和模型目录仍在设备 Documents。此前 `f08-cross-runtime-birds-20261108/blocked-run.json` 只说明**Mac 上独立 Python/MLX 对照**的运行前 GPU 空闲检查遭自动审批额度失败；这不是 iPhone GPU 的前置条件，不能据此称真机排查被 GPU 检查阻断。当前设备前台是普通 `io.ondeviceai.combined.demo`，未为复现打断它。

原 F08 三对飞鸟 motion/frozen 及首token诊断已经在同一型号实体 iPhone（当时 iOS 27.0.1）运行，前者严格0/3；固定模型对三段 frozen 输入的首token `MOVING` 相对 `STILL` 的 logit 差依次为+1.0、+1.375、+1.25，而三段 motion 为+1.625、+1.625、+1.75。视觉输入改变了分布，却没有令 frozen 选择 `STILL`。这些是**当前固定模型/提示/权重的直接决策机制**，不证明原始HF权重、训练能力或所有平台的最终根因。Android现有LiteRT-LM只验同步图片，状态矩阵明确标记“当前不支持视频”；Android视频UI/导入和视频超分不是这套飞鸟语义验收。见`artifacts/verification/apple-mlxvlm-natural-video-20261028/{temporal/summary.json,label-margin/summary.json}`和`docs/validation/p11-status-matrix-20261027.md`。

## 2026-11-08 续查：无 PyTorch 的相同张量跨实现对照

用户指出 Python 对照引入 PyTorch 与移动端问题不相称，已停止临时 Torch/Torchvision 安装（exit130）；临时环境中两者均未安装，生产项目依赖/权重/lock未变。固定模型卡虽称 `mlx-vlm 0.1.13` 转换，但该版本包没有 `smolvlm` 模型实现或消息模板；改用只在临时环境的 `mlx-vlm 0.3.9`、MLX0.32.3。Python `load()` 的处理器在 Transformers5.22.2 和4.57.6下均要求 Torch/Torchvision，故完整处理器对照未执行，原始失败见 `artifacts/verification/f08-mlx-vlm-independent-20261108/run-events.jsonl`。这条 Python 依赖不属于 iOS App。

改为从固定 Swift 上游图片 `prepare` 导出完整 token IDs、归一化 NHWC 像素和首token全向量 logits，独立 Python `mlx-vlm` **只加载相同本地MLX权重的模型层**，从原始数值输入计算；不调用 Python processor 或 PyTorch。红/绿两行 Swift 的原文、token数186、top ID23675及全向量SHA与旧图片logit报告逐项相同；两实现同样把 ` GREEN` 首片排为最高，红图仍误判。两实现完整logits的 NRMSE 红约0.015、绿约0.036，不是逐位一致；只说明该首token错误不是 Swift 单方输出，不能证明所有模型计算或原始权重正确。原始张量/两侧结果见 `artifacts/verification/f08-cross-runtime-logits-20261108/`，含 `python-comparison.json`、两份原始Swift logits及运行收据。

同法飞鸟 motion-0-2/frozen-0-2 探针已固定实验并成功构建，但运行前 ComfyUI/Ollama/进程空闲检查的宿主提权被**自动审批服务额度失败**拒绝，三个检查均未执行；没有启动飞鸟推理或 Python 对照。见 `artifacts/verification/f08-cross-runtime-birds-20261108/blocked-run.json`。待正常审批可用后先重做实时GPU空闲检查，再运行已构建的这一个配对实验；不能绕过审批。正式birds/waterfall仍各0/3，F08/P11仍open/not-ready，无确认生产补丁。

## 续接补录：上游参考对照已有新增交付，先读这些入口

原07:02交接中的同模型上游参考比较建议，现已有两个新增交付入口：`artifacts/verification/f08-upstream-path-compare-20261108/parent-independent-summary.json`与`artifacts/verification/f08-upstream-birds-compare-20261108/parent-independent-summary.json`。摘要分别记录红绿2段、birds6段的token/像素/THW逐项相同及仍为相同负向输出；birds正式配对仍0/3、未确认adapter bug。新增原始实验由专家线路推进，主代理此处只读取交付摘要/索引，未重跑实验，不以摘要代替全链路质量验收。请专家先核对这两处完整证据，勿重复执行下文历史建议中的同一对照。旧交接全文保留；下一实验由专家按新增结果选择有区分力的变量。

---

# F08 飞鸟／自然视频语义：专家 Agent 交接

## 2026-11-08 09:19 iPhone 时序重排与模型归因边界

用户确认 F08 目标是**短视频理解**，运动/冻结是其中一个反事实验收项，并非仅检测是否有运动物体。当前固定模型为 `mlx-community/SmolVLM2-500M-Video-Instruct-mlx` revision `fa57db46815177fbdfd65cc85a2b3416a8332268`。其上游[模型卡](https://huggingface.co/HuggingFaceTB/SmolVLM2-500M-Video-Instruct/blob/main/README.md?code=true)将它定义为视频/图像/文本输入、文本输出的多模态模型，报告500M版 MVBench 39.73；该总体基准不能推出本项目固定片段的正确率。

使用在线 iPhone17,2/iOS27.2 的同一模型、公开 birds pair-0、原正式 prompt/token/时间与THW，只改模型输入的8帧像素顺序或复制帧，新诊断版真机报告6行：[父核验](../artifacts/verification/f08-iphone-temporal-order-20261108/parent-verification.json)、[原始报告](../artifacts/verification/f08-iphone-temporal-order-20261108/device-report.json)。运动原序/倒序/重复首帧/重复末帧的 `MOVING−STILL` 首片 margin 分别 `+1.625/+1.750/+1.000/+1.125`；冻结原输入 `+1.000`，归一化零像素 `+0.125`。重复首帧与正式冻结输入的完整49,280维logits SHA完全相同，倒序与原序SHA不同；6行首片top仍全是`ĠMO`。因此当前真机组合**会感知帧内容和顺序变化，却仍把无运动的重复帧判向 MOVING**，冻结片段重新编码差异也不能解释这项失败。

尝试用已存在 MLX 权重、纯 CPU 的 Swift/Python 独立模型层再分离实现归因，Swift隔离工具构建通过，但受限宿主进程启动即报 `No Metal device available`、exit255；Python在执行`mx.set_default_device(mx.cpu)`之前导入`mlx.core`同样报该错。两者均**未产生模型推理结果**，[阻塞收据](../artifacts/verification/f08-cross-runtime-birds-cpu-20261108/blocked-run.json)保留原错。不能以此判定模型或独立实现能力，也不把 Mac GPU 检查当 iPhone 的门槛。用户随后明确要求**不要 CPU 路线**；后续不再执行该尝试，诊断以 iPhone 真机与现有固定模型为准。

结论：当前固定模型/权重/运行时组合无法通过这一短视频时间语义任务，birds/waterfall正式配对仍各0/3，F08/P11维持open/not-ready。尚无证据足以在原始模型、MLX转换权重与Swift模型实现三者中唯一归因，不能靠去句点或只调评分关闭阻塞。没有改生产代码、默认模型、权重或rubric；诊断App已恢复原版，普通Combined App已重新打开。

## 2026-11-08 09:14 iPhone 真机中性像素对照

使用在线 iPhone17,2 / iOS 27.2、已装诊断 App 的固定本地模型与公开 birds pair-0，隔离构建签名探针；保持原 prompt、720 个文本 token、帧 metadata、模型和其余生成条件，仅把 `LMInput.image.pixels` 归一化值置零。真机四行首 token 结果：motion 原像素 `MOVING−STILL=+1.625`、冻结原像素 `+1.000`；两者中性像素均 `+0.125`，全 49,280 logits SHA 完全相同。两条原像素 logits SHA 又与此前 iPhone 报告逐条一致。原报告、源码、build/安装/恢复收据及父独立核对见 [真机中性像素对照](../artifacts/verification/f08-iphone-neutral-pixel-20261108/parent-verification.json) 与同目录 `device-report.json`。

该结果确认当前真机模型决策确实受视觉像素驱动，但冻结画面仍把首 token 推向 `MOVING`；小幅 `+0.125` 的中性基线不能解释原冻结输入的全部 `+1.000`。具体原因尚不能在原始模型能力、固定转换权重与 Swift 模型实现之间定论，更不能把探针当成正式质量通过。F08 birds/waterfall 仍各 0/3，保持 open/not-ready；Android 视频后端仍未验收。仅升级了 `io.ondeviceai.logitdebug` 诊断 App，随后恢复旧诊断版并重新打开普通 Combined App；模型和夹具目录仍在。

## 2026-11-08 07:42 继续排查：图片、logits 与标签顺序

用户指出上游路径对照并未解决阻塞，继续做三项预先固定的隔离诊断，均使用原红绿合成素材、固定权重／依赖，无生产改动。

- 同一视频首帧交固定上游**图片** `prepare`，红/绿各用原video问法和仅把`video`改成`image`的问法。4次均答` GREEN`或` GREEN.`，严格0/4；两个`[2,512,512,3]`图片张量的归一化通道均值分别为红图约`(+0.998,-0.803,-0.999)`、绿图约`(-0.999,-0.064,-0.999)`。红图误答不只存在于视频采帧／提示块。原报告及父验收见`artifacts/verification/f08-image-colour-discriminator-20261108/parent-independent-summary.json`。
- 对同4项图片输入做真实 `Idefics3.prepare`：每项49,280个有限首token logits；红/绿同问法的全向量SHA不同，且同输入再次运行的各行全向量SHA、top token、原文逐项相同。四项top ID均为23675，即` GREEN`的首片。` GREEN`实际分为2 token，不能把首token比较冒充完整标签概率。见`artifacts/verification/f08-image-logit-contrast-20261108/{parent-independent-summary,repeatability-summary}.json`。该对照确认视觉输入影响了当前模型的下一token分布，但没有越过这一首token选择。
- 仅交换图片问题的选项次序为`GREEN or RED`：红图改答` RED.`，绿图答` GREEN`；严格1/2。说明当前分类对问法敏感，**不是**正式rubric或飞鸟配对的修复，也不能归结为单纯固定最后选项。见`artifacts/verification/f08-image-label-order-20261108/parent-independent-summary.json`。

正式birds/waterfall仍各0/3，F08/P11仍open/not-ready。已排除这些固定输入的生产adapter相对固定上游prepare差异，并定位到固定模型／runtime在相同视觉信号下的分类决策；尚不能区分固定MLX权重、Swift模型实现与原始模型本身。当前`python3`环境未检出`mlx-vlm`/`transformers`/`torch`，默认HF hub的精确原始模型缓存路径不存在；这不证明其他环境均无副本。当前边界禁止新依赖和权重下载。下一有区分力的工作是同一**已有MLX权重**在独立`mlx-vlm`实现的隔离对照，再视结果决定是否需要原始HF权重路径；先固定版本、输入/预处理可比性和原评分，不把安装或模型更换当作已授权。既有图片／标签重复问法不再追加作质量刷分。

## 2026-11-08 07:22 上游参考路径对照结果

已完成本文建议的同固定模型上游对照，无生产源码、权重、lock、评分或设备状态改动。隔离 macOS consumer 对六段原公开 birds motion/frozen 素材逐段使用同一生产 snapshot 的8帧、实际时间、固定 system/prompt/tokenizer/temperature0/maxTokens32；分别调用生产 `MLXVLMVideoInputAdapter.prepare` 与固定 `mlx-swift-lm` 的 `context.processor.prepare`（`.frames`、`targetFrames=8`）。每段的720个 token、`[8,512,512,3]` 全部像素浮点数及8个 THW 帧索引逐项相同，随后以上游 `LMInput` 和独立新 cache 生成，六次原文均为 ` MOVING.`，与旧 iPhone 正式报告六行逐字相同，严格配对仍0/3。6次 snapshot 清理与运行前后固定输入摘要通过；成功构建、预期评分失败的 runtime exit1 和原报告均保留：[父独立摘要](../artifacts/verification/f08-upstream-birds-compare-20261108/parent-independent-summary.json)，原报告 SHA256 `4ef883fdc5c5fd51c15521462b45ea65a69e6cec5af0f93b4129ca5140add401`。另有红/绿合成视频的同样上游对照，2段输入全相同且两次原文 ` GREEN.`，见 `artifacts/verification/f08-upstream-path-compare-20261108/parent-independent-summary.json`。

这排除了**这些固定输入在 macOS 上由生产适配器相对固定上游 prepare 引入差异**的假设；不能推出所有 SDK 路径正确、模型普遍无视觉能力或瀑布路径等价。旧 iPhone 输出虽逐字相同，本次上游对照在 macOS 运行，不能冒充新的实体设备验收。没有确认生产 bug，不做无依据补丁；F08/P11 保持 open/not-ready。后续需把固定500M模型／任务适用性作为独立质量决策，若要换权重或模型需另定范围和验收，不自动下载或改默认模型。

更新：2026-11-08T07:02:46.403931+08:00。用户已将此问题交给专家 Agent；主代理转而推进其他路线图工作。本文交接问题，不声称修复或质量通过。

## 工作目录与任务边界

- 工作目录：`/Users/daubert/Github/ondevice-ai-kit/.worktrees/foundation`；当前 HEAD `1ce678ca677ec942c5383d9f5224e05da4d6190e`，工作树含大量已有 dirty/untracked 文件，不能 reset、clean、stash 或覆盖他人编辑。
- 先读仓库根 `AGENTS.md`、`docs/agent-pitfalls.md`，尤其 F08 源码诊断、报告身份绑定、外层 sandbox/SwiftPM 冲突与 `LMInput.image.frames` 可选数组条目。所有 shell 前缀 `rtk`。
- 目标：找到能区分当前 SDK 路径差异与固定模型／任务适用性的证据，若确有 SDK 缺陷，给出行为 RED、最小补丁及原评分回归。不能只增加报告、挑 prompt 或降低 rubric。
- 专家独占自己的新 `.omx/artifacts/expert-f08-<unique-id>/` 候选／实验／证据目录；先交可审阅文件及简短 receipt。主代理拥有正式集成、四份进度记录及机器 manifest。跨目录生产改动先报告精确文件与理由，避免撞写。
- 私人员工照片、姓名、embedding、个人分数完全无关，不得读取／复制／交云。只用本文指向的既有公开／本地合成素材与固定权重。
- 无新权重下载、依赖升级、默认模型／生产锁变更、commit/push/tag/发布。用户不在实体设备旁，不要求重连；主机诊断可继续，不能冒充 iPhone／低系统物理验收。
- 全部 CBC 付费停用。hy3 暂停至 **2026-11-08 11:56:42 UTC+8**，此前不探测、不重试；hy4free 的额度以实际新任务结果为准。原生飞鸟续接已因 `exceeded retry limit, last status:429 Too Many Requests` 终态，不自动重启；最多一个活动原生子助手。不要递归派发。

## 当前可确认结论

1. 正式 birds 和 waterfall 的 motion/frozen 三对均 **0/3**；F08/P11 仍 open/not-ready，未找到可确认的 SDK 编码 bug。
2. 基础静态场景并非全部失败：既有 frozen birds subject/scene 为 3/3；这不证明运动判断能力。已有真实图片／视频推理可完成，也不证明所有语义任务正确。
3. motion/frozen 输入会改变张量、特征或首 token logits；这**不能证明全部编码正确，也不能把故障定位为 logits 之后**。不要重复此前过度推断。
4. 标签映射互换实验：6 fixtures × 2 mappings 共12次新生成，全部选 A（部分带句点／空白）；strict exact0/12，诊断规范化6/12，但每条件完整 motion/frozen 配对仍0/3。不是只去掉句点就可修复的问题。
5. 最新静态阳性对照：2秒320×180、24fps、H264/BT709/yuv420p红与绿视频，按 red-1/green-1/green-2/red-2 四次 fresh cache、temperature0/maxTokens32生成，四次原文都是 ` GREEN.`，strict **0/4**。四次完整 token equality、generation join、snapshot目录删除及30项前后摘要均通过。这是有效负向诊断，不是探针未启动。
6. 新对照说明当前设置的问题不能仅归为时间推理；尚不能据此认定 SDK 编码错误或模型普遍没有视觉能力。下一个实验必须有区分力，优先考虑**同一固定模型的上游参考路径对照**，先核对既有覆盖；不把再次修改同一问题措辞当新进展。

## 最新复现与证据入口

新目录：`artifacts/verification/f08-static-colour-control-20261108-0652/`

| 文件 | 用途 |
| --- | --- |
| `parent-independent-summary.json` | 父独立验证、原文输出、有效负向裁定 |
| `reports/static-colour-v1/report.json` | 单次真实四行报告，SHA256 `4ee2f2ebc711bd3f0cbd268b2c3cb4aa4d4a8bfa8e075dd4b706d7465e393d05` |
| `parent-preflight.json` | SDK/source/model/lock指纹、哪些候选wrapper被拒用 |
| `runtime-invocation.json` | 完整argv、runtime强制禁网／私人目录拒读、30项运行前摘要 |
| `runtime-result.json` | exit1、4.162秒、全部输入摘要保持、无重试 |
| `consumer/StaticColourProbe.swift` | 实际构建运行的原生源码，SHA256 `6d427178b1a1317990eac028cec176549a4f7f308d0a750c4dc9a8c84b9f1d06` |
| `consumer/MLXVLMVideoInputAdapter.swift` | 与生产adapter相同hash的实际源码副本 |
| `fixtures/fixtures.json`／`red.mp4`／`green.mp4` | 本地合成生成命令与bytes/hash；不是私人采集 |
| `parent-fixture-admission.json` | 编码metadata及实际主色；red解码RGB均值(255,24,0)，green(0,108,0) |
| `build-result.json`／`build.log` | unsigned macOS实际构建87.932秒通过，含缓存复制路径告警，非零告警 |
| `parent-build.py`／`parent-runtime.py` | 主代理编排：原样Swift、本地缓存、串行GPU预检、禁网runtime、实际前后hash |

固定模型：`artifacts/P9/verified`；modelId `mlx-community/SmolVLM2-500M-Video-Instruct-mlx`，revision `fa57db46815177fbdfd65cc85a2b3416a8332268`，runtime `mlx-swift-lm` revision `6e338e5df49424fc0db7c6d58184d622f92ed18a`，MLX `0.32.3`；完整14文件hash在 `parent-preflight.json.fixedInputs`，生产lock SHA `e67e985970770991f1eb2384ca9e9b6267fb8a8f1569051be5cfb2175f0ef858`。

固定prompt：`What is the dominant color in this video? Answer exactly RED or GREEN.`；system保持生产文本。评分仅trim空白，不能去句点／大小写归一化改写strict结果。两个红输入内容也答GREEN，因此格式问题不是全部原因。

不要再次运行旧 `parent-runtime.py` 或覆盖旧 run-root：它们故意拒绝已有输出。新实验必须新目录、事前固定变量／顺序／计分／停止条件，保留所有原始错误。

## 已完成实验，禁止重复派发

| 范围 | 优先入口与已知边界 |
| --- | --- |
| 正式birds、frozen静态、首token/logit、label margin、原rubric | `docs/validation/multimodal-video-demo.md`；`artifacts/verification/apple-mlxvlm-natural-video-20261028/` |
| 原公开birds三对输入 | `artifacts/verification/apple-mlxvlm-natural-video-20261028/temporal/fixtures/manifest.json` |
| 内部对齐／frame/tensor／tokenizer parity／features | `artifacts/verification/f08-internal-alignment-20261107/`；实际uniform-token parity既有2/2无skip，不能当未做 |
| system prompt与prefill partitions | `artifacts/verification/f08-system-prompt-diagnostic-20261107/`、`artifacts/verification/f08-prefill-partitions-20261107/` |
| 互换A/B标签的12回答 | `artifacts/verification/f08-prompt-contrast-20261107-2322/parent-independent-summary.json`；raw report SHA `d2290d401649900e605921c24b635c874765208cc5a889494bd71c23bd1a4257` |
| 上述报告校验器 | `artifacts/verification/f08-contrast-validator-accepted-20261107-2335/verification.json`：36/36＋父5个真实报告变异拒绝，勿重写校验器 |
| 原生此次交付与429 | `.omx/artifacts/native-f08-birds-followup-20261108-0632/dispatch.json`、`parent-assessment.json`、`retained-candidate-hashes.json` |
| 新静态颜色对照 | 上述0652目录；本次已真实跑完，勿重跑 |

## 探针自身已知坑

- 原生候选 `build_static_colour_probe.py`再次套外层OS禁网sandbox，会与SwiftPM嵌套sandbox冲突，且指向旧可写SourcePackages；未采用。实际父构建新复制cache、固定resolved/offline flags、不宣称构建OS禁网。runtime单独强制禁网。
- 原候选 `run_static_colour_probe.py`把`fixtures.json`存在当`fixedInputsUnchangedAfter`；未采用。实际父验证了30项前后hash。
- 父额外假设“非主RGB通道<5”并非协议要求，red实际为(255,24,0)，初次过严观察已保留；按原编码规格及主色核验通过，没有修改素材／prompt／评分。不要把这个父检查错误当模型／SDK缺陷。
- 候选Swift成功构建与真实推理均已证明，不要因助手最后429就把现有可用证据写成“未启动”或“无候选”。429也不能被文件落盘掩盖成模型调用成功。

## 下一步建议与停止条件

先读现有覆盖，再书面固定一个能区分SDK路径与参考路径的实验：同模型、权重、帧、颜色和prompt／system／tokenizer／生成参数；明确任何不能保持相同的因素。上游正常、SDK失败时再沿最早差异定位；两路同失败只能得出该配置／任务适用性问题。若无法建立公平参考，报告精确缺口，不用额外同prompt重复替代。

若确认生产bug：先真实行为RED、再最小候选补丁；按原正式birds/waterfall配对和已有源合同验收，不改默认模型、rubric或production lock。模型更换／新权重属于另行范围，不自行下载。

GPU使用前实时确认无MLX/Ollama runner、ComfyUI `/queue`的running/pending均空；单GPU串行，不能凭旧preflight执行，不停止别人任务。专家若需GPU，请先将具体命令／输入／计划写入自己的任务目录，与主代理协调后执行。

交付应包括：确切改动／源指纹、原始结果、有效性检查、仍未排除的假设，以及是否实质关闭了哪些原门槛。候选生成、构建、主机推理、设备语义、独立审查与release是不同层级。四份进度记录由主代理按实际证据同步，历史10/12不是完成率。
# 2026-11-12 新72条 train 对照完成；Smol2.2B 40/72、Qwen4bit 29/72，F08仍未关闭

用户已授权下载并运行对照。基于官方 Perception Test MC train 标注和既有 `train_videos.zip`，冻结了72个未用过的视频：六类各12条，A/B/C正确标签各4条，排除旧24及全部 `cut_frame_mapping_train.json >= 0` 视频。选择SHA `31711b0484120c19799819e604a516b06ec19697bdb6e986862b09e449bf35ad`；训练包完整SHA `0af7a1a56c18e5e7a2ceab23e8eeebf6d68624a2320a44fb4a30be07f833292c`，仅提取约1.06GiB媒体。

两臂使用相同72条视频/8帧/实际时间/问题选项，但使用各自原生视觉预处理、tokenizer和模板。Smol2.2B4bit固定revision `0a4ab666f99095e6c6bd0e1e49aca8822fc46df2` 唯一Metal进程exit0（38.487s），严格40/72、格式无效1；Qwen2-VL4bit固定revision `01af461cdb9574acc09084a0ef94e216e142b085` 唯一Metal进程exit0（34.531s），严格29/72、格式无效9。逐题为Smol-only20、Qwen-only9、两者都对20、都错23；差距同时包含格式遵循和内容选择。Smol失败31个选错+1个格式错，Qwen失败36个选错+7个格式错。分类：Smol counterfactual8/memory3/abstraction7/physics7/semantics9/camera6；Qwen4/4/6/7/6/2。记忆、相机、物理仍有明显错误，不能称可靠产品模型。

证据根 `artifacts/verification/f08-expanded72-20261112/`：`parent-prepared-verification.json`（576共享帧、两套72组输入独立复算）、`smol-parent-verification.json`（145份完整logits含EOS）、`qwen-parent-verification.json`（153份完整logits含EOS）、`comparison.json`、`failure-taxonomy.json`、`inference-plan.json`、`execution-corrections.json`。两次运行前均确认ComfyUI队列空/Ollama无模型；无Torch/CPU模型/Ollama、新依赖、iPhone或生产改动。宿主MLX峰值/耗时不构成iPhone内存/热预算。

官方README恢复后发现旧24中 `video_8981=179`、`video_8295=155` 需要按官方协议截断；旧结果使用全片，保留为全片诊断，不能冒充官方协议基准。精确inclusive/exclusive边界本轮未解决；新72全部cut值为-1，已排除需截断视频。`cut-protocol-audit.json`只做旧结果的离线缺口标注，不重跑旧输入。

判断：2.2B在新train子集仍明显优于Qwen4bit，值得进入候选；但这不是held-out/validation、非单变量模型大小实验，也没有iPhone 2.2B资源/持续热/产品阈值证据。F08继续 `open/not-ready`；下一门是先冻结明确产品预算，再做2.2B真机资源/热与代表性质量验收。

## 2026-11-15 视觉24层定位完成：block6首次越1%，差异从block0渐进累积

[报告](validation/f08-qwen3vl-vision-layers-20261115.md)，证据`artifacts/verification/f08-qwen3vl-vision-layers-20261115/`。冻结video6500同一Swift pixels/tokens/positions/weights，唯一Swift与唯一Python MLX Metal顺序完成；Python原生vision与手工逐层重放final/三deepstack逐字节一致，Swift观察器与原输出也逐字节一致，两条旧答案向量保持且仍答C（期望A）。patch/position权重及patch、position、sum全部exact，rotary仅9.48e-9；block0为0.000235、block5为0.009295，**block6首次NRMSE>0.01（0.010503）**，final为0.085136。该曲线证明差异从首block逐步累积，不能把block6称独有缺陷；两runtime仍同错，不支持数值差为主语义根因。峰Swift2.776GB/Python3.019GB、thermal0、无stop/重试/PyTorch/CPU模型。原手机16/36与W6open/not-ready保持。下一只做block0子边界，检验attention分支是否为首个材料级差异，不重跑旧质量集。

## 2026-11-15 block0子边界完成：首个差异在norm1，不是SDPA

[报告](validation/f08-qwen3vl-block0-boundaries-20261115.md)，证据`artifacts/verification/f08-qwen3vl-block0-boundaries-20261115/`。同一exact block0输入在Swift/Python逐字节一致；两runtime native/manual重放各自逐字节一致、旧答案向量保持。首个非exact为norm1：NRMSE5.99e-6/max0.001953125；qkv1.17e-5，attention residual4.16e-5，norm2升至1.62e-4，MLP activation3.46e-4，最终block0为2.35e-4并与24层trace对应。故SDPA不是首源，LayerNorm/参数加载成为下一单变量。首离线checker只因post-rotary leading singleton shape拒绝，原失败保留、零模型重跑；r2只squeeze两已证单例边界后通过。峰Swift3.218GB/Python2.388GB、thermal0，无stop。两runtime仍同错，原16/36/W6open保持。

## 2026-11-15 norm1已定位到fused LayerNorm路径，仍不是已证语义根因

[报告](validation/f08-qwen3vl-norm1-boundaries-20261115.md)，证据`artifacts/verification/f08-qwen3vl-norm1-boundaries-20261115/`。同一实际加载的block0 norm1 weight、bias和exact input在Swift/Python逐字节一致；显式BF16与FP32的mean/variance/invstd/normalized/affine共10个边界也全部逐字节一致。唯一首差异是native fused LayerNorm，NRMSE5.993e-6/max0.001953125，因此版本/参数加载/epsilon运输/普通归约已排除，跨运行时差异收敛到两个独立构建的fused路径。Swift旧两条logits保持且仍答C，Python也同错C（期望A），不能把该数值漂移直接写成16/36内容错误原因。唯一Swift/Python Metal进程各完成，无Torch/CPU模型/stop/重试；峰值3.064GB/2.101GB、thermal0。首个离线资源checker误读不存在的per-sample thermal字段，失败保留，r2只改读顶层thermalStart/End，零模型重跑。下一隔离因果对照应统一vision LayerNorm为同一显式公式，检查最终vision/logits/答案；不得直接改生产math。W6仍open/not-ready。

## 2026-11-15 全视觉显式LayerNorm因果对照完成：答案仍错，非充分原因

[报告](validation/f08-qwen3vl-explicit-layernorm-20261115.md)，证据`artifacts/verification/f08-qwen3vl-explicit-layernorm-20261115/`。隔离Swift/Python各把24个block双norm加四个merger norm共52次统一为FP32显式公式并cast回输入dtype；两唯一Metal进程完成，无Torch/CPU模型/stop/重试。首差异由norm1后移到rotary9.48e-9，block0由2.35e-4降至1.54e-4，但final仅0.08514→0.07735（约降9.15%），block23略升；说明fused LayerNorm只是早期parity来源之一，非最终漂移主要单一来源。旧/新Swift/Python四路全部仍答C（期望A），故替换LayerNorm实际改变视觉/logits却未修复语义，fused差异不是本题错答充分原因。峰值2.765GB/2.805GB、thermal0。首preflight错套674-token分块而真实684，原失败/冻结保留，r2仅修合同，无重编译/模型重试。停止把逐算子parity当质量主线；原16/36、W6open/not-ready保持。
