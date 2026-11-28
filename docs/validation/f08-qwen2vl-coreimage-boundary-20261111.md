# F08 同源 CoreImage 宿主／iPhone 预处理边界

四段原视频在 Mac 执行同源 CoreImage 处理后，与冻结 iPhone prepared pixels 的 NRMSE 为 **0.00219–0.00384**，RGB255 平均绝对差 **0.103–0.132**。相比先前 ICC-sRGB＋官方 Pillow 路径，RMSE 小 **3.36–5.00 倍**。后续显式注册的新输入 Metal 推理四题完整输出 **A/C/B/A**，逐项复现 iPhone，仍严格 **1/4**。这缩小了宿主／设备像素边界，**没有证明逐字节等价或解决视频理解质量阻塞**。

证据根：`artifacts/verification/f08-qwen2vl-coreimage-boundary-20261111/`。初始像素边界臂不加载模型；后续另行注册新像素输入臂，仅加载现有固定4bit权重作Mac Metal推理，不重放旧输入、不操作 iPhone、不下载权重；生产、共享依赖、默认和评分均未改。上一轮原始四题及颜色控制完整生成结果见 [官方预处理与覆盖对照](f08-qwen2vl-preprocess-coverage-20261111.md)。

## 固定条件与测量

使用保留的实际 `MLXVLMVideoSnapshot.swift`，同原视频 SHA、同8帧实际时间。复制固定 runtime `MediaProcessing.swift` 的 `inSRGBToneCurveSpace`、`resampleBicubic`、`normalize`，使用相同 `CIContext(options: [.cacheIntermediates: false])`，目标504×280、同模型 mean/std；`asMLXArray` 的 RGBAf 渲染部分保持原样，仅在创建 MLXArray 前返回已渲染 Data。NumPy 按原合同直接丢弃 alpha、转 CHW 并打包为 `[2880,1176]`，不调整 RGB 或 alpha。

`source-receipt.json` 保留源摘要、复制方法摘要和完整原／新 render 文本；`main.swift`、原编译二进制 `export`、每病例8个原始 RGBA float32 输出和 `snapshot.json` 保留。第一例复用前次已成功提取的8帧；其他三例各只提取一次。未重复编译、解码第一例或运行任何模型。

以下 NRMSE 统一以 iPhone 张量 RMS 为分母；RGB255 指将归一化差值乘原 std×255，未执行 uint8 截断或裁剪。

| 病例 | 同源 CoreImage NRMSE | RGB255 MAE | RGB255 RMSE | 旧官方颜色控制 NRMSE | 旧／新 RMSE |
| --- | ---: | ---: | ---: | ---: | ---: |
| 11448-q0 | 0.002820 | 0.1045 | 0.1688 | 0.011913 | 4.224 |
| 1811-q1 | 0.002191 | 0.1251 | 0.1804 | 0.008985 | 4.100 |
| 7915-q1 | 0.003838 | 0.1315 | 0.1832 | 0.012891 | 3.359 |
| 9431-q2 | 0.002386 | 0.1033 | 0.1782 | 0.011932 | 5.000 |

四组均非逐字节相等；归一化最大绝对差0.02365–0.02479。宿主与 iPhone 的原解码帧没有共同中间导出，剩余差异仍混合解码、色彩／CoreImage kernel 等，不能唯一归因。更接近 iPhone 也不等于更接近模型训练预处理或更高语义质量。

## 真实失败与恢复

首轮 `run_boundary.py` 在第一例8帧导出后，额外要求 `alpha == 1`，真实 AssertionError 退出；原 stdout/stderr、终态和 `alpha-assumption-red.json` 保留。实际全部 RGBA 有限，alpha 范围0.98508668–1.00013804。原 `asMLXArray` 直接丢弃第四通道，并没有 alpha 恒1合同；失败来自检查器额外假设，不据此认定模型／CoreImage 缺陷或调整像素。

独立恢复脚本 `continue_boundary.py` 先绑定原源／配置／脚本、二进制及已存首例摘要，保留原失败，只将检查改为 finite 并记录 alpha；恢复终态为 `continuation-result.json`。首例所有原文件摘要保持；不覆盖首轮记录。

父复算初稿另因 NRMSE 分母约定不同而失败：旧 `stats(device, official)` 取第二参数 official 的 RMS，新控制取 device RMS。`parent-metric-convention-red.json` 保留具体差值；复算分别验证旧原值，并统一分母后比较，原像素／收据均未修改。表格旧列因此与旧报告末位略有差异，不是像素变动。

## 独立验证与结论边界

父 `verify_retained.py` 不导入 MLX，以直接裁片构造每个 packed 行，独立于采集器 reshape 链。四例11,520行全元素相同；32帧有限；实际时间、媒体／张量／旧官方像素 SHA、原真机报告 SHA、首例原始文件保持均通过。重新计算全部误差、统一分母比值；现有11个模型文件和两清单28项受保护文件保持。终态：`parent-independent-verification.json`，exit0。此为主代理独立复算，不冒充 native 审查。

上述初始边界臂没有产生新答案。之前同真机张量、修正 Python 分段 attention 后四题仍 A/C/B/A（严格1/4）；官方颜色控制 C/C/C/A（2/4），两计数题20帧仍0/2。现有证据已确认 Swift 视频网格路由缺陷并在隔离 iPhone 副本验证，但剩余错误不能只归于该缺陷或简单缺帧；也未证明模型理论上做不到。

## 后续新输入语义臂：四题完整复现 iPhone，仍1/4

显式计划 `pixel-inference-plan.json` 冻结新host CoreImage pixels及原tokens/positions/meta共16个输入文件摘要；与已完成像素边界臂分开注册。`run_coreimage_pixels.py` 基于前轮实际运行的官方像素脚本，仅替换输入路径／摘要，并补齐每步完整logits保存；源diff保留 `inference-source.diff`。固定现有权重11文件、分段attention wrapper、28层freshKV、balanced512、temp0/max32、EOS151645/151643，运行前ComfyUI队列空、Ollama无模型。只执行四题一次，Mac MLX GPU终态exit0、4.165s（整个进程，非产品加载／延迟预算），无Torch/CPU模型。

| 病例 | 正确 | 旧iPhone | 新host CoreImage完整输出 | 旧官方Pillow完整输出 | 新最高两标签margin |
| --- | --- | --- | --- | --- | ---: |
| 11448-q0 | B | A | A | C | 0.03125 |
| 1811-q1 | B | C | C | C | 0.125 |
| 7915-q1 | C | B | B | C | 0.125 |
| 9431-q2 | A | A | A | A | 1.828125 |

新臂四题各生成一个字母，下一步完整151936维向量argmax均151645停止；首token与EOS共8份实际全logits保留，不沿用旧EOS仅收据的限制。父 `verify_inference.py` 不导入MLX、不重推模型，独立核8向量SHA/finite/argmax、tokenizer原文、严格评分、每个prefill/decode的28层offset、所有输入/源码/权重保持。`parent-inference-verification.json` 终态passed。相对实际iPhone首logits的NRMSE0.00719–0.01444，答案四项相同；不能把答案相同当完整数值等价。

该对照直接检验了“相同CoreImage代码的宿主／设备残余像素差异会否在这些病例翻转答案”：本次四题没有翻转，计数两题仍错。同源CoreImage更接近iPhone并未带来语义改善；官方Pillow在7915的正确C仍是不同输入路径的边界结果，不能当可靠iPhone修复。小样本结果不排除其他视频受预处理影响，也没有唯一证明量化或原模型能力是剩余根因。现有权重继续扫同输入或任意调prompt的因果收益有限，下一步保持下述BF16对照授权门。

下一有区分力的分支仍是同输入比较官方 BF16 非量化权重与现有4bit，区分原始模型与量化／转换源组合的影响。具体两shard、SHA、约4.115GiB和6GiB磁盘预算、Metal检查及运行前门已准备，授权问题仍待明确答复；本次未添加 aria2 任务。由于4bit模型卡没有固定原权重revision，即便精度臂改善也不能单归量化。F08维持 **open/not-ready**，任务目标 active。
