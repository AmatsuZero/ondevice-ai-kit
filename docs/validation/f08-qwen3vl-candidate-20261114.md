# F08 Qwen3-VL 2B 4-bit 候选对照（宿主诊断完成）

用户已授权下载并运行对照，并明确允许主代理直接实现本次隔离脚本。选择 `mlx-community/Qwen3-VL-2B-Instruct-4bit`，固定 revision `9c4f5209e57b31f4b9dfba735de3fb983739c9cc`；官方模型元数据标注 Apache-2.0。三组 Mac MLX Metal 实验均已完成，F08 仍 open/not-ready；候选尚未采用，iPhone诊断后续见下，Android与产品质量/资源接受仍缺。

## iPhone后续实测

[手机valid18报告](f08-qwen3vl-iphone-valid18-20261114.md)：协议修复及32题prepare前置通过后，唯一iPhone Metal模型臂18/18完成、严格9/18/invalid6，全部原回答/IDs/EOS与本报告Python对照相同。72全向量/28KV/rope delta父核通过；整臂58.3s/采样物理峰2.94GiB只是诊断观察。候选卸载、45普通App一致，剩格式与内容质量门未解，不改下方基线。

## Swift后续前置

[隔离Swift协议修复](f08-qwen3vl-swift-parity-20261114.md)已完成主机32题完整processor及真实tokenizer/位置核验；像素仍有小量残差。iPhone preparation-only32题及全部像素同Mac已验、候选卸载恢复；独立valid18模型臂后续已完成（见上），尚无产品质量接受，下列严格基线不变。

## 最新结论

Qwen3 在部分相反答案视频上表现出明确的画面依赖：固定文本、时间和位置，仅交换 packed pixels，三对相机题的选项双向随画面正确改变。不能再把这批误判笼统解释为“完全没接入视觉”或“视频理解本来做不到”。但它没有解除当前可靠性阻塞：valid18 严格9/18，6个格式无效；首字母与正确选项一致14/18仅作诊断，其中五题选对却追加说明，另四题选项错误。格式修复与内容判断必须分别处理。

| 固定实验 | 严格正确 | 格式无效 | 首字母描述 | 独立完整 logits（含 EOS） |
| --- | --- | --- | --- | --- |
| 原 valid18，18题 | 9/18 | 6 | 14/18对应正确选项 | 72 |
| 配对剩余14题，另复用valid18两题 | 1/14 | 10 | 合并16唯一视频为11/16 | 57 |
| 六个预先固定不重叠视频对，双向换像素12题 | 2/12 | 7 | 8/12对应像素来源答案；6/12改变选项 | 45 |

历史同18题 Smol2.2B 严格10/18、Qwen2-VL严格7/18。不能将Qwen3首字母14/18与这些严格得分比较，或宣称整体质量已胜出。三组宿主MLX峰值分别2,842,278,190 / 2,790,410,646 / 2,790,361,494 bytes；不是iPhone App内存。

valid18分项严格为counterfactual2/3、memory2/3、abstraction1/3、physics3/3、semantics1/3、camera0/3。四个首选项错误是video_10249（逆向字母顺序）、video_4505（记忆）、video_9676/video_9500（抽象计数）；不能只修格式即宣布质量通过。

配对审计固定原库存的六组16唯一视频、10跨答案对，其中六对/12视频词典序不重叠；不是12对。合并16视频严格2/16、invalid11，双侧严格0/10；首字母双侧对应正确答案6/10、不重叠3/6，仅4/10对选项不变（旧Smol10/10、Qwen2为9/10）。12相机题首字母10/12正确，四个static全部选对，video_9130/video_5702把moving选成static。这是描述性敏感性证据，不是新的评分合同。

换像素实验全部六对双向运行，不按输出挑样本。三对成功的相机视频为8055↔9291、10969↔5418、369↔4991；抽象10153↔9540、相机5702↔7894、physics8935↔valid1755均没有双向正确变化。12首步完整向量全变、8条原文变、6条首字母变；六对双侧严格仍0/6。输入刻意保持目标时间却使用另一视频像素，属于干预，不能当自然视频benchmark或证明所有任务都可靠grounding。

## 已验证

- 使用用户指定 `localhost:6800` aria2 RPC，GID `fecba999fd2b4223`，下载至 `/Users/daubert/Github/CrawlerBot/aria2/downloads/odai-qwen3vl-2b-4bit-9c4f5209-20261114`。RPC complete；父端独立整文件 SHA-256 `4750d95a2162829e127a94e83ac350d498d02070aab216c4687da48804a06ffb`、1,782,040,921 bytes、1019 tensor/index 与 14 小文件核验通过。未复制权重至仓库。
- 固定 Swift 原始 `replacePaddingTokens` 函数，经原字节核对和实际 `swiftc` 编译/执行，在 `[4,20,36]`、merge2 下产生 720 pads、一个视觉块；官方 Transformers 4.57.6 文本展开 AST 产生同样 720 pads、四个带时间戳的视觉块。这是辅助函数/官方文本步骤的差异证据；Swift 使用测试 tokenizer，不是完整处理器/BPE/模型等价证据。该差异针对新候选，不能当作旧 Smol/Qwen2 的错误原因。
- 官方 Qwen3 `get_rope_index` 源码在视频位置前把原 grid 按 T 展开且每块 T=1。父端以 NumPy 验证 shim 执行原方法 AST，三组合成实际 checkpoint BPE fixture 与复用参考位置逐元素一致；没有导入 Torch 或运行模型。支持范围是 batch1、无 padding 的纯视频诊断。
- Qwen2.5-VL 3B 官方固定 LICENSE 实际为 Qwen RESEARCH LICENSE，2(a) 仅非商业研究/评估，2(b) 商用需另申请；不能沿用其他 Qwen 型号的 Apache 许可判断。本轮没有下载它的权重。

## 对照合同

复用既有 valid18 的原视频八帧 PNG、实际时间、题目/有序选项/官方标签，不重新选题。保持 trim-only A/B/C 严格评分、greedy32 和每题新 cache。新模型使用自身模板和 patch16、temporal2、merge2、mean/std 0.5；空间预算每帧不超过 512×512，属于有界诊断配置。官方文本 AST 展开四个时间块并保存完整 prompt/BPE；位置通过官方 AST 独立核对后显式传入，避免已安装 Python 原生 Qwen3 rope 的多块索引问题。

像素准备复用可无 Torch 执行的 Qwen2 慢处理器，并显式设置 Qwen3 参数；必须先独立核对 packing/归一化再启动模型。这不等于完整官方 Qwen3 video processor 数值等价。模型仅 MLX Metal，单 GPU 进程、ComfyUI/Ollama 队列空；不安装依赖，不修改共享运行时/正式 App/模型默认。

候选代码交付、静态核查和真实执行分别验收；首 Swift 候选转义/错 sentinel、后稿两个编译错误均保留原文，经外部精确补丁后成功。CLI success/伪 tool-call 文本不作为实际文件写入或编译成功证据。

18 题已经在历史模型评估中使用，本轮不是盲评、未见训练数据或官方 benchmark 声明。模型原生输入和 full prefill 与历史 balanced prefill 不同，因此只比较候选配置的表现，不做单变量模型容量因果归因。宿主结果不代替 iPhone、Android 或产品验收。

证据根：`artifacts/verification/f08-next-candidate-feasibility-20261113/`。实际18+14输入的ICC→sRGB/Pillow/归一化/官方packing AST独立重算通过；所有18+14+12输入的官方位置AST核验通过。三次Metal运行均exit0、无自动重试；父端核174份完整向量、argmax/真实tokenizer解码/EOS/28层cache offsets及全部冻结输入、模型、源和旧输出摘要。`parent-combined-results.json`和`parent-swap12-results.json`是结果收据。

valid18计划SHA `f426f1b02ea95120eda870cd61b43bf026ce83cc240d360805d8e841ccfab249`；pair14为`187e4650869823ba8862f950bf9f76a915afc76946bb14c36d3412c78ccc8adf`；swap12为`bd6826ba9e7f9b07fb898c7cd86cbe30a614ff96a525e652247897e1da8feedf`。各自保护1081/1218/1327份既有文件，模型15文件/runtime26文件；这些是重叠守卫，不能相加当唯一文件数。

本分支停止条件已满足：不重跑同输入、不继续扫prompt/帧数，也不把首字母诊断改成历史分数。下一独立分支应先修复隔离Swift的Qwen3时间块及位置合同，与已验证宿主输入逐元素对齐，再做有界iPhone Metal可行性检查。输出约束需另立实验，不能覆盖当前9/18。正式质量、最低设备、加载/内存预算和持续热/释放仍缺；不改默认，不进入生产采用。

最终离线验收`parent-final-audit.json`通过：重新核1483份唯一冻结文件、174完整向量和全部EOS/offset/raw评分，13份本轮Python源语法通过；四项合同测试实际通过（`contract-final.*`）。ComfyUI/Ollama队列空、无本轮Qwen推理进程，剩余磁盘76,900,675,584 bytes（约71.6GiB）；没有为此删除唯一证据或复制模型。所有首个真实生成token都恰为A/B/C，因此现有valid18中的五纯格式错可精确归为后续追加文字；这仍不是已运行约束解码器的14/18成绩。

下一Swift分支的具体前置：固定源`Qwen3VLProcessor.prepare`的视频路径当前只积累`sequence.frames`，再调用共享`replacePaddingTokens`；需要保留实际采样时间，并按temporal2配对平均时间生成四块。`Qwen3VLLanguage.getRopeIndex`当前按pad token数计视频、直接消耗原`videoGridTHW`，没有官方按T拆块步骤；不能只修字符串而沿用原位置输入。先在新隔离目录以既有32个自然输入为固定fixture核真实BPE、每轴positions、delta、THW/pixels和prefill/续写位置，再运行模型。不能直接改共享运行时或把纯fixture通过当iPhone质量通过。

## 生成任务状态

输入准备的独立hy4free请求自然终态，CLI0/raw0、上游`finish_reason=error`空流（900293ms），没有候选。另一个runner请求也已自然终态，CLI0/raw262144 bytes，JSON在reasoning字符串中截断，无可用实现。原prompt/raw/stderr/父端收据归档于`.omx/artifacts/ask-cbc-qwen3-{prepare18,run18}-20261114/`。没有父端超时、观察到的quota/auth错误或自动重发。用户随后允许主代理直接实现，原等待授权阻塞解除，目标active。隔离prepare/runner四项真实RED→GREEN通过；不把助手exit0当成功交付。

换像素父校验器初稿少一个右括号，首次仅SyntaxError、未校验或推理；原`parent-swap12.stderr.txt`保留。只修离线checker并去掉预写的成功对数，第二次实际通过，`parent-swap12-r2-*`与结果保留；没有重跑模型。实际invalid为7，不能按先前估计6填报告。

## Python 原生位置索引的独立夹具

父端仅执行已安装 mlx-vlm0.3.9 Qwen3 `get_rope_index` 的两个原始索引 AST 表达式，以 NumPy shim 和实际 checkpoint BPE 验证：四视觉块 fixture 的真实 start 索引为 `[7,195,383,571]`，原表达式把其相加为1156，序列仅756token，下一索引产生 IndexError。两块fixture虽未越界，但相加也不是 start 列表。没有执行完整 MLX 方法/模型或修改共享包；此证据支持显式官方参考位置，不能当旧模型失败原因。见 `parent-native-python-rope-index-fixture.json`。

后续[独立约束回放](f08-qwen3vl-choice-constraint-20261114.md)明确区分原严格基线与约束规则结果：valid18离线14/18、invalid0，四内容错保持。原自然配对双侧约束6/10、不重叠3/6，非新benchmark或线上真机验证。
