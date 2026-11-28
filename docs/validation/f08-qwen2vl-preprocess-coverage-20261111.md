# F08 官方预处理与计数帧覆盖对照

**最新组合对照已完成：**[原始权重报告](f08-qwen2vl-original-bf16-20261111.md)补官方ICC-sRGB8帧+原始值FP32：B/C/C/A、3/4；同原始值配iPhone像素C/C/B/A、1/4。进一步复用冻结官方20帧，计数题B/A、1/2，旧4bit同输入A/A、0/2。1811全部20张人工复核可辨三次释放/收回，仍答6，关键事件完全漏采不足以解释。预处理能翻转答案、精细计数仍不可靠；非模型理论不可能或iPhone已修复。跨臂输入/完整向量与40帧摘要通过，见精度根`completed-arm-audit/comparison.json`。下载/推理均终态，下面下载中/待授权/下一原权重分支均为历史。生产就绪仍false。

**2026-11-11后续状态：**用户已明确“允许下载并运行对照”，下文待授权/未下载描述为准备阶段历史。原始BF16两shard通过指定aria2RPC下载中；小分片429,441,656B已complete且父独立SHA核验与官方一致（`92540d83…e7b833`），大分片仍active，尚无BF16模型结果。唯一监控为`precision-preparation/download-recovery2/watch.py`（session33673）；实际状态及小分片验收见同目录`watcher-status.jsonl`和`small-shard-parent-verification.json`。旧大分片因终态错误被服务hook删除，旧监控85164已终态，不应再续等；恢复记录及当前GID见主交接文档顶部。下载完成后依次执行精确权重合同、现有隔离MLX Metal推理及父离线验收，不使用Torch/CPU模型，不改App或项目依赖。结果仍须限定精度/转换来源组合，F08保持open/not-ready。

本轮推进了两个未完成的分离实验：实际官方预处理、两道计数题20帧覆盖。**预处理变化能改变答案，但增加帧数仍未解决计数错误。** 当前没有可靠视频理解验收通过；原始模型能力与4bit量化尚未拆开。生产依赖、默认模型、评分与iPhone App均未修改，未下载新权重。

证据根：`artifacts/verification/f08-qwen2vl-preprocess-reference-20261111/`（下称预处理根）及`artifacts/verification/f08-qwen2vl-count-coverage-20261111/`（20帧根）。均有事前`experiment-plan.json`、源码、完整stdout/stderr、启动终态和原始向量。父独立复算见预处理根`parent-independent-verification.json`及`verify_retained.py`。

## 官方处理器可不依赖Torch，颜色配置必须控制

实际导入固定Transformers4.57.6的`Qwen2VLImageProcessor`成功，使用`return_tensors='np'`；无Torch/Torchvision、包安装或模型CPU推理。4.57.6保留的video路径会提示未来5.0删除，警告已保留；没有为消除警告升级。Context7核对4.57.3文档仅作API补充，实际执行与源摘要以安装的4.57.6为准。[官方文档](https://huggingface.co/docs/transformers/v4.57.3/en/model_doc/qwen2_vl)。

从前轮已编译、源摘要核对的AVFoundation snapshot宿主工具导出11448/1811/7915/9431四段原媒体的8帧PNG；媒体SHA及实际时间与iPhone原报告逐项一致。**宿主与设备原始解码像素没有逐字节证明相同**，PNG路径也不能冒充真机原CGImage。实际官方处理器的原坐标数组打包/逆打包全部元素相同，另144个直接坐标断言验证通道/时间/空间顺序。初始`coordinate-check.json`误记96断言，父独立重算确认实际2×4×3×2×3=144，不覆盖原收据。

直接对PNG原始RGB字节运行官方处理器，归一化像素相对iPhone差异NRMSE0.095–0.239、RGB255平均绝对差6.23–9.90。PNG带660B ICC，Pillow识别描述`HDTV`；普通`.convert('RGB')`不执行该ICC→sRGB转换。原Swift路径通过CoreImage色彩工作空间和`toSRGB()`处理，不能将不同transfer字节直接当处理器bug。

新增明确颜色控制：同一批PNG只先经Pillow LittleCMS从嵌入ICC转sRGB，再调用完全相同官方处理器。四题RGB255平均绝对差分别0.486/0.471/0.439/0.593、RMSE0.709/0.736/0.613/0.884；归一化NRMSE0.01192/0.00899/0.01288/0.01191。打包shape与THW全部相同，文本tokens/位置保持。说明原巨大差异主要与颜色表述有关；剩余差异仍混合解码、ICC实现、Pillow量化及bicubic重采样，未唯一定位一个滤镜缺陷。

初始API调用漏`images=None`，在任何host解码/模型推理之前真实TypeError退出。按安装源码签名修正并保留`initial-api-error/`，不是模型失败或供应商错误；没有隐式模型重试。

## 官方颜色控制像素：四题完整生成2/4，但小margin不稳定

明确注册新输入臂`official-pixel-inference-plan.json`：用上述ICC-sRGB官方像素替换原prepared pixels，保留同一四题tokens/MRoPE/THW、现有固定2B4bit权重、已验证Python分段attention、balanced512缓存边界与28层offset。Mac MLX0.32.3 Metal、mlx-vlm0.3.9，独立GPU空闲预检通过；不重放原iPhone张量或操作设备。

本轮保留完整greedy生成：temp0/max32，每题freshKV，固定EOS151645/151643；每题实际生成一个字母后151645停止。续写位置为原MRoPE最大值+1，逐步核28层offset。保存首token全151936维logits、实际生成tokenIDs和停止收据；未保留第二步EOS全向量，父不能独立复算其全向量，缺口明确。

| 原病例 | 正确 | 旧iPhone8帧原文 | 官方颜色控制8帧完整原文 |
| --- | --- | --- | --- |
| 11448：放了几个物体 | B（3） | A（4） | C（5） |
| 1811：发射几次物体 | B（3） | C（2） | C（2） |
| 7915：离开视野时物品变化 | C | B | C |
| 9431：准备什么食物/饮品 | A | A | A |

严格1/4→2/4仅为诊断四题、非正式质量提升或benchmark。7915 C24.25000/B24.234375，margin仅0.015625；11448换成另一错误字母。小像素变化会翻转选择，不能认为剩余预处理无关或可靠修复成立。也不能将host路径改善直接宣称iPhone已修复。

## 两道计数题20帧：0/2，增加覆盖未解决

针对尚未做过密集覆盖的两道abstraction题，独立注册20帧实验。11448时长9.8117s、1811时长10.077s，均从8帧提高到20帧（约2fps），沿用同一AVFoundation snapshot逻辑，只将常量maxFrames8→20及driver成功计数8→20，源diff可审阅；guard/admission不放宽、旧源不改。

两段均真实解码20帧、时间严格递增、临时snapshot删除、全部PNG带HDTV ICC；沿用上节ICC-sRGB及实际官方处理器。THW`[10,20,36]`，visual token720→1800，完整prompt790/791→1870/1871；除视觉占位符数量外所有原文本token逐字保留，重新计算官方MRoPE。

两题新完整原文均`A`：11448选4、1811选6，正确均应3/B，因此严格0/2。相同官方8帧基线为C/C，也0/2。该结果不能泛化“增加帧数永远没用”，但此时继续只增帧/扫prompt没有足够根因收益。没有iPhone20帧验收或移动端内存预算结论。

## 验证、下一实验与权限边界

父独立重新执行实际官方预处理10组（4原RGB、4ICC-sRGB、2密集），逐项确认输出字节SHA；复算像素/RGB/各通道指标、原媒体与帧摘要、6份首token全向量/label logits/argmax、生成token解码、固定EOS收据、prefill和decode各28层offset、新MRoPE/原文本token保持；11模型文件及两套旧保护摘要保持。此为父复算，不冒充独立native审查或EOS向量复算。初始TypeError、deprecated警告、计数元数据订正均保留。

下一个有区分力的实验已准备：官方原始**bfloat16非量化**`Qwen/Qwen2-VL-2B-Instruct` revision`895c3a49bc3fa70a340399125c650a463535e71c`，两份权重合计4,418,050,768B（约4.115GiB）。API元数据、index、config已只读获取，11项关键架构配置与当前4bit一致；权重本身尚未下载。计划只用Mac MLXMetal模型层和原四组真机prepared张量，不改App/依赖/默认、不用Torch。经用户指定aria2RPC下载，工作磁盘预算6GiB，已观察可用约77.85GiB。摘要与完整计划见预处理根`precision-preparation/ready-plan.json`。

当前4bit模型卡未固定源非量化权重revision，因此即便新原始权重更好，也只能先归为“量化/转换源组合差异”，不能单独证明量化损失。新权重超出此前明确“不下载新权重”的实验范围，已发结构化授权问题；在收到允许前不提交RPC任务。等待期间其他本地复算和记录继续。目标保持active，F08仍open/not-ready。

## 精度对照工具预检补充（尚未下载或运行原始模型）

本轮在等待授权期间准备了`precision-preparation/run_precision.py`：`--preflight`只校验现有四病例20份实际张量/元数据、版本及Torch缺席，不导入MLX或加载模型；真实模式先核精确两shard集合、字节数、官方SHA、index归属、729张量BF16/config，然后才允许加载，且只在新的独立输出目录运行。无下载代码；不能将脚本文件存在当成已获授权或已经推理。

按实际安装的纯`Model.sanitize`方法映射官方index，729个原参数键与既存4bit文件头的基础参数集合完全对应；既存存储为F16/U32，原始config声明bfloat16。此为参数名/元数据合同，不证明原始张量形状、实际加载成功或4bit来源revision。缺shard/错大小/错SHA/超大头长度/截断头五项拒绝检查通过，另两项验证无权重时在MLX import/输出目录创建前停止及20输入绑定。

GPU实时空闲预检后，用已知值小型BF16 conv3d（Qwen时间patch几何但仅4输出通道）、SDPA与matmul在`Device(gpu,0)`逐项通过，结果dtype保持BF16、无精度回退；不包含Qwen模型权重，不是完整模型兼容/资源/语义验收。证据`bf16-metal-operators.json`、`weight-contract.stdout.txt`、`runtime-preflight.stdout.json`及`final-preparation-checks.json`。首稿artifact路径多取一层parent导致FileNotFoundError，发生在MLX import之前，原错保留`runtime-path-red/`，只订正新脚本路径；不得算模型加载失败。

实际对照脚本逐步保存完整151936维logits（含EOS步），维持原四组iPhone输入、同MRoPE/分段attention/28层freshKV/default-balanced512、greedy32，完整输出与先前4bit首向量及真机原文分别比较。尚未真实执行该脚本的模型分支，授权问题仍待明确答复。
