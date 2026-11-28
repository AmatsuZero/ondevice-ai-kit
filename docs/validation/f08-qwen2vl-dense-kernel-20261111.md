# F08 现有4bit反量化矩阵算子对照

将现有4bit权重在内存中重建为FP16，所有量化矩阵改用普通Metal矩阵计算后，四题完整输出仍 **A/C/B/A**，与相同CoreImage输入的量化算子臂、原iPhone逐项一致，严格 **1/4**。这削弱了“量化矩阵算子单独造成三道错答”的解释，**没有排除量化信息损失、证明原始模型能力不足或解除质量阻塞**。

证据根：`artifacts/verification/f08-qwen2vl-dense-kernel-20261111/`。事前计划`experiment-plan.json`，已运行源码`run_dense.py`与`dense_control.py`，基线差分`source.diff`，完整终态`dense-inference/result.json`、`launch-result.json`，父离线复算`parent-independent-verification.json`。

## 这项控制改变什么

同前轮[CoreImage语义臂](f08-qwen2vl-coreimage-boundary-20261111.md)的四份host RGB pixels、原tokens/positions/meta（16个文件摘要冻结）、原THW、现有固定revision权重、已校准Python分段attention、fresh28层KV、balanced512、temp0/max32及EOS151645/151643。

仅把实际327个`QuantizedLinear`／`QuantizedEmbedding`替换为诊断Module：调用官方`mx.dequantize`，保留group64/4bit/affine，重建FP16权重；Linear调用`x @ weight.T`及原bias，Embedding调用索引或同矩阵投影。全部327模块替换、无残留量化模块的运行时断言通过。模型文件只读，没有写回或保存新的大模型文件。

现有量化权重重建约4.1105GiB；Mac物理内存48GiB。此次展开仅留在进程内存，进程结束已释放，不据此声称iPhone内存预算通过。FP16重建引入显式存储舍入，不能把算子臂解释为数学完全等价或原始BF16模型：原始量化丢失的信息不能反量化恢复。

实际MLX0.32.3安装源码的`QuantizedLinear.__call__`使用`quantized_matmul`，Embedding使用同一`dequantize`／量化投影；已核对官方[dequantize文档](https://ml-explore.github.io/mlx/build/html/python/_autosummary/mlx.core.dequantize.html)的affine公式`scale * quantized + bias`，HTML保留。Context7 resolve调用实际返回`Invalid API key`，未重试／换凭据，按官方文档及固定本地源码核验，原错误摘要见`documentation-receipt.json`。

## 运行前与运行中验证

先运行小型已知值GPU控制，未加载模型权重：精确nibble解包、Linear含bias、量化算子已知值、Embedding lookup、tied输出及模块替换六项通过，`operator-check.json`。未使用Torch、CPU模型或新包。正式运行前ComfyUI running/pending均空，Ollama无模型，实际设备`Device(gpu,0)`。

每个真实量化矩阵的第一行／最后一行，运行时以独立NumPy位移解包U32、原scales/biases，按FP32公式→FP16复算，与实际重建行精确相同，327项均通过；同时检查完整重建权重finite。运行时收据记录这些断言与模块元数据，**未保存完整dense矩阵或样本数组**，父离线验收只核真实执行源码／断言收据与原header对应，不能称父独立复算了已释放的完整dense权重。

唯一新计算臂自然exit0，进程总时长5.199s；不是产品冷加载、单题延迟或设备内存测试。每题生成一个字母后EOS151645，首token及EOS共8个完整151936维logits均保留。

| 病例 | 正确 | 原量化算子 | FP16重建普通矩阵 | 对量化算子首logits NRMSE | FP16最高两标签margin |
| --- | --- | --- | --- | ---: | ---: |
| 11448-q0 | B | A | A | 0.003640 | 0.046875 |
| 1811-q1 | B | C | C | 0.001930 | 0.140625 |
| 7915-q1 | C | B | B | 0.002010 | 0.125 |
| 9431-q2 | A | A | A | 0.001876 | 1.828125 |

NRMSE分母统一为原量化算子首logits RMS。答案相同、logits不同，不能称完全数值等价，也不能外推其他输入。四题均非格式问题。

父`verify_retained.py`无需MLX／模型重放，核原16输入、源码、基线报告、327模块路径/形状/dtype与safetensors U32 header集合完全一致，核全部8向量字节数/SHA/finite/argmax、tokenizer原文、严格评分、EOS、各步28层cache offsets及11模型摘要，实际exit0/passed。模块独立样本校验的上述留存限制明确保留。

## 下一项因果缺口

相同输入的普通FP16矩阵计算没有解决计数与变化误判，因此不以替换量化kernel作为生产修复。已确认的Swift视频网格路由错误与Python视觉分段错误仍有独立证据，但校准后剩余错答仍在。

接下来需原始非量化BF16权重同输入对照，才能继续分离原始模型与量化／转换源组合；不是再反量化一次或扫prompt。该分支需要新增约4.115GiB权重，现有结构化授权问题尚无明确答复，本次没有aria2任务或新权重。无iPhone操作、生产/依赖/默认/评分改动，F08 **open/not-ready**，goal active。
