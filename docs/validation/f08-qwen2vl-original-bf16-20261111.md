# F08 原始 BF16 权重对照

后续无模型[同PNG阶段检查](f08-qwen2vl-pixel-stages-20261111.md)已完成，定位到预乘alpha的数值差异及更大的下游缩放/浮点组合残差，未改变下面任何模型结果或证明语义修复。

**最新20帧组合臂已完成：**同原始权重FP32、复用冻结官方20帧输入，两道计数题输出B/A，严格1/2；官方8帧B/C也是1/2。1811三次释放/收回过程在20张保留PNG中可辨，仍错选6/A，不能仅归因完全漏采。完整跨臂表及人工观察见下节；F08仍open/not-ready。

**最新官方像素组合臂：**同原始权重FP32、只换成已验官方ICC-sRGB8帧pixels，输出 **B/C/C/A，严格3/4**；原iPhone pixels的同权重FP32为C/C/B/A、1/4。11448和7915由错转对，支持残余像素处理路径能影响选择，不能把三错全部归于模型能力。新增两正确题margin仅0.021706/0.029209，不能当稳定修复或产品通过。旧4bit官方像素为C/C/C/A、2/4；该差异仍受未固定4bit源revision及计算精度组合限制。

新分支实际Metal进程exit0/11.038s，父验收exit0/3.588s；4组新增pixels与原20输入共24文件绑定、729原参数精确提升、8新完整logits含EOS/28层KV/11旧模型/28保护项通过，额外35旧输出文件摘要保持。事前`fp32-official-plan.json`、source/verifier diff、3项新RED→14门GREEN、`fp32-official-launch-result.json`、`parent-fp32-official-{result,verification}.json`、`fp32-official-pixels/`均保留。无新下载/解码/设备/包或生产改动。

下面BF16/原像素FP32结果保持为原诊断，不被3/4覆盖。20帧分支已完成；不扫新的帧数/提示。

## 完成的对照与计数可见性复核

四题顺序为11448/1811/7915/9431，正确B/B/C/A；20帧只含前两题。

| 权重与计算 | 像素输入 | 输出 | 严格正确 |
| --- | --- | --- | --- |
| 原始BF16值提升FP32 | 原iPhone8帧 | C/C/B/A | 1/4 |
| 同原始值FP32 | 官方ICC-sRGB8帧 | B/C/C/A | 3/4 |
| 既有4bit | 同官方8帧 | C/C/C/A | 2/4 |
| 同原始值FP32 | 官方20帧 | B/A | 1/2 |
| 既有4bit | 同官方20帧 | A/A | 0/2 |

新20帧运行只替换权重组合，不重新解码/下载；30输入绑定、全部40帧SHA、非视觉文本token序列保持、THW[10,20,36]、7200×1176 packed pixels、1870/1871 prompt tokens、原20帧MRoPE相同。729参数提升及全元素相等/finite、28层KV、4完整新logits含EOS、11旧模型/28保护项及49旧输出摘要均通过。Metal启动进程12.502s、父验收3.752s均exit0，不是iPhone性能。新2项RED与16门GREEN保留。

| 病例 | 原权重20帧答案 | A / B / C logits | 前两标签差 |
| --- | --- | --- | --- |
| 11448：放置数量 | B，正确3 | 22.544287 / 22.574390 / 22.334087 | 0.030104 |
| 1811：发射次数 | A，错误6；正确B/3 | 23.309383 / 23.120817 / 23.307762 | 0.001621 |

1811人工逐张查看全部20张保留PNG，可辨三组“持球→释放后球与手分离→收回”：帧4/5/6/7（约2.00–3.50s）、8/9/10/11（4.00–5.51s）、12/13/14/15/16（6.01–8.04s）。释放后画面约2.5025、4.5045、6.5065s，与官方答案3一致；后面17–19为再次持球。关键过程并非完全未进入采样，仍需时序事件区分/累计。人工稀疏帧解读不替代独立标注或完整视频事件时间，也不证明resize/embedding后每个线索均清晰。

证据在原精度根：`fp32-dense-plan.json`、`fp32-dense-{source,verifier}.diff`、`fp32-dense-launch-result.json`、`parent-fp32-dense-{result,verification}.json`、`fp32-dense/`、`fp32-dense-green.txt`。`compare_completed_arms.py`只读旧结果/张量，不importMLX或推理，核五臂完整首logits/已有EOS向量、匹配像素与token/prefill、原权重协议、位置及40帧SHA；通过收据`completed-arm-audit/comparison.json`绑定五份结果摘要及人工观察。首稿额外假设EOS列表顺序151645/151643，实际三臂均151643/151645，RED保留；只改比较器为固定集合且跨臂列表相同，原实验不改。

下一排查应聚焦隔离Swift预处理数值边界：原始权重结果证明像素小差异会翻转，尚未唯一定位色彩/重采样/量化哪一步。先做无模型阶段对齐，再决定是否存在足以验真机的新输入，停止重复现有输入或任意prompt/帧数扫描。该模型配置的精细计数目前不可靠；不能从四题推出理论不可能，也不能把宿主3/4当iPhone修复或产品验收。

原始非量化权重在同四组 iPhone prepared 输入上完整输出仍为 **A/C/B/A，严格1/4**，逐项复现既有4bit及iPhone结果。下载、权重合同、Mac MLX Metal推理和父离线验收均实际完成。该结果削弱“仅4bit导致当前三题错误”的解释，不能证明模型理论做不到，也不构成产品质量通过。

## 权重与传输

官方`Qwen/Qwen2-VL-2B-Instruct` revision`895c3a49bc3fa70a340399125c650a463535e71c`，原始存储BF16，共4,418,050,768B、729张量。用户已明确授权“允许下载并运行对照”。两片最终SHA分别为：

- `model-00001-of-00002.safetensors`：`994ac2b03f97de8bc647d0fe5eba2e4b632b3e28dc03574c29bdfc36cf47e1b9`
- `model-00002-of-00002.safetensors`：`92540d8353c8d226a589a3b179bdb33851c970ee2cc2ac7ba035f79425e7b833`

均经用户指定localhost6800 aria2RPC下载至CrawlerBot/aria2/downloads下的独立目录。HF大分片长时间低速；补查ModelScope Qwen同模型固定revision`a25f63f92197c0b352182256f1ae0fe7f5c10185`，公布两片SHA/size均与官方目标一致，容器实际1MiB Range与原HF片段SHA相同。只给现存大分片GID`01fa8a1eac8202a8`更换传输地址、保留inode/断点/原checksum，无新任务；实际随后超100MB/s并完成，小分片沿用HF原任务。最终整文件SHA与index/BF16合同通过，来源一致性不依赖片段或元数据推断。

原HF expiry维护进程1032在核验精确PID/源码/cwd后显式SIGTERM，终态143；首次进程名guard过窄而在任何修改前停止，实际macOS进程为Python.app的`Python`，纠正收据保留。原推理监控33673未重启，现已终态exit0。没有改aria2全局配置、其他任务、凭据或已有模型。

## 对照与验收

现有隔离Python3.12、MLX/Metal0.32.3、mlx-vlm0.3.9，实际`Device(gpu,0)`。无Torch/CPU模型/Ollama或iPhone操作。只替换权重，保持原四组输入20文件SHA、同tokens/pixels/THW/MRoPE、已校准分段视觉attention、fresh28层KV、balanced512、greedy32/EOS151645及151643。实际每题生成一个字母后EOS151645。

| 病例 | 正确 | 原iPhone/4bit | 原始BF16 | BF16前两标签margin |
| --- | --- | --- | --- | --- |
| 11448：物体数量 | B | A | A | 0，A/C并列22.5，B22.25 |
| 1811：发射次数 | B | C | C | 0.125，C23.375/B23.25 |
| 7915：物品变化 | C | B | B | 0，B/C并列23.875 |
| 9431：准备饮品 | A | A | A | 2.75 |

父验收实际通过8份完整151936维logits（含EOS步）的SHA/finite/argmax/解码/严格评分、prefill和decode各28层offset、729原始BF16张量、11旧模型文件与28保护项。首logits相对4bit NRMSE为0.1232/0.1438/0.1763/0.1541；答案相同不意味着数值等价。模型启动进程9.949s含加载/校验开销，不是iPhone性能或产品内存预算。

证据根：`artifacts/verification/f08-qwen2vl-preprocess-reference-20261111/precision-preparation/`。主收据为`download-contract-result.json`、`precision-launch-result.json`、`parent-precision-result.json`、`parent-precision-verification.json`、`inference/`。网络切换、片段比对和旧失败均在`download-recovery2/`保留，无签名URL或RPC secret。

## 结论边界与下一检查

**FP32计算控制已完成：**原四组输入、同一已验原始BF16权重，全部729参数只在内存提升到FP32；完整逐元素相等和finite检查通过，不落盘转换权重。实际仍在Metal执行，唯一进程exit0/14.085s，父离线验收exit0/3.601s。输出 **C/C/B/A，严格仍1/4**，没有质量改善；原BF16各文件摘要保持。

| 病例 | BF16 | FP32 | FP32标签分数A / B / C |
| --- | --- | --- | --- |
| 11448 | A | C | 22.538303 / 22.320011 / 22.609209 |
| 1811 | C | C | 22.779900 / 23.336384 / 23.489880 |
| 7915 | B | B | 21.338146 / 23.915894 / 23.794323 |
| 9431 | A | A | 26.147709 / 19.864374 / 23.909353 |

BF16并列确实消失，但11448改选另一个错误答案，7915仍错。因此不能用BF16标签并列单独解释三题误判。FP32仅提高这些BF16存储数值的计算精度，不是原始训练FP32权重。完整16份BF16/FP32 logits分别保留，新父验收另核729参数名/卷积布局/FP32shape、8新向量含EOS、原11模型/28保护项；两臂tokens/pixels/positions/分块/28层offset/评分逐项相同。收据为`fp32-compute-plan.json`、`fp32-compute-source.diff`、`fp32-launch-result.json`、`parent-fp32-result.json`、`parent-fp32-verification.json`、`parent-bf16-fp32-comparison.json`及`fp32-compute/`。

新工具先有4项缺实现失败，再准备独立runner；原缺权重测试对真实BF16输出目录“不存在”的旧前置条件在本阶段已失效，产生1项真实失败。只把该测试输出换为临时独立目录，原运行和原runner不改；最终11门检查通过（含原7与新4）。此为诊断工具测试修正，不是模型重试；旧失败`fp32-output-fixture-red.txt`保留。

4bit模型卡未固定原非量化源revision，因此本实验比较的是精度/转换来源组合，不能单独定量归因量化。原始BF16实际也三错一对，现阶段无证据支持仅换回原权重即可解决，也不能从四题推出所有视频理解不可能。

上述原像素FP32控制未改善；其后的官方像素及20帧控制均已完成，见顶部最新结果。预处理与权重组合存在交互，仍不能唯一认定原始模型理论不可能。F08仍open/not-ready，目标active。
