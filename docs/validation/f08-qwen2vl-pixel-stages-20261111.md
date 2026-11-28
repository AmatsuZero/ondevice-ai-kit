# F08 同PNG预处理阶段分离

**后续语义与核参数检查已完成：**[alpha/bicubic报告](f08-qwen2vl-alpha-kernel-control-20261111.md)。只除alpha仍1/4；只将bicubic C0.75→0.5，像素RMSE降19%–40%，完整输出仍1/4。默认同PNG32份normalized RGBA与早先直接AVFoundation宿主逐字节相同，旧基线可复用；该输出等价不证明所有源CGImage等价。下面“未运行alpha语义臂”为本轮阶段历史。

本轮只对32张既存PNG做无模型检查。官方路径与CoreImage的残差主要出现在缩放/浮点与量化组合阶段；另确认丢弃预乘alpha会留下可复算的数值偏差，但消除alpha只使相对官方输入的RMSE下降约0.3%–0.8%。未证明该项导致答案错误，未修改生产代码或运行新模型臂。F08仍open/not-ready。

## 冻结输入及执行边界

计划为精度根`pixel_stage_plan.json`。从既存预处理根四病例各8张PNG读取相同字节，32张SHA与原`result.json`逐一核对；没有重新视频解码。沿用已固定CoreImage宿主源码中的完整`Pipeline`方法和context，实际生成源码及原/复制方法SHA保留。依次输出tone、resized、normalized的RGBAf；另有已知sRGB上下双色块，检查中性色与纵向顺序。只用系统Swift/CoreImage和既存隔离Python/NumPy/Pillow，无模型/MLX/Torch导入、下载、依赖或iPhone操作。

执行前ComfyUI队列为空、Ollama无加载模型；单进程渲染。`pixel-stage-output/{compile,render}-result.json`均exit0。99个阶段数组（32×3加见证3份）finite/shape/摘要通过，Pillow ICC→sRGB再bicubic的重建结果与已留官方张量逆归一化最大差3.0518e-5/255，验证比较对象和方向。输入与旧源均保持。

## 色彩与下游阶段

为避免将两种resize混入色彩差异，先将CoreImage转换后的原尺寸RGB舍入至8-bit，再让它与官方ICC-sRGB8结果走相同Pillow resize，得到中间桥接数组。该划分是操作性分离；下游仍包含CoreImage/Pillow采样核、浮点/舍入/裁剪/alpha，不能唯一命名某个错误滤镜。

表内均为RGB255 RMSE：

| 病例 | 相同Pillow缩放后的色彩分量 | CI最终输出相对桥接数组 | CI最终输出相对官方 |
| --- | --- | --- | --- |
| 11448 | 0.295094 | 0.647302 | 0.692670 |
| 1811 | 0.139047 | 0.719695 | 0.724116 |
| 7915 | 0.274237 | 0.544743 | 0.595402 |
| 9431 | 0.534693 | 0.723775 | 0.869859 |

不能将RMSE直接相加或按比值当贡献百分比；分量相关。仅在最终输出舍入8-bit使部分MAE下降，却令全部四例RMSE变大（0.737548/0.741097/0.650232/0.912409），因此“只补8-bit舍入即可对齐”不成立。

## 已证alpha公式及影响范围

32帧保留数组均满足：

`normalizedRGB = (resizedPremultipliedRGB - mean * alpha) / std`

独立逐元素公式最大残差≤7.153e-7（normalized单位），说明原归一化图中丢弃alpha后的RGB并非通常的非预乘RGB归一化。原报告字段`normalizationGraphFusionResidual`只是初始描述名；后续已用预乘alpha公式解释，不应宣称CoreImage图融合数值错误。

四视频缩放alpha范围均0.98508668–1.00013804，每帧3948/141120像素与1相差超过1e-5，其中裁去四周4px后仍1360像素受影响。因此不能把它只描述为外边缘问题。首稿“仅4px边缘”额外假设真实失败，保留`alpha-first-error.json`；只移除未经支持的范围假设、改记录实测空间数量，没有重渲染或修改原数组。

离线以`normalizedRGB / alpha`作代数控制，四例相对官方RGB255 RMSE为0.690745/0.718329/0.593522/0.864298；原值0.692670/0.724116/0.595403/0.869859，只下降约0.3%–0.8%。独立双色块见证的平坦区域偏差从27.1246/255降至2.28e-5/255，但该见证从56×28放大到504×280，与真实PNG缩放比例不同，不能把该幅度外推真实视频。

这证明数值边界存在，尚未检验其答案因果关系；小残差也可能触发当前小margin，不能按误差大小直接排除语义影响。不存在已完成的alpha控制模型推理或iPhone修复。后续若要测此因素，应冻结独立新输入，与相同来源/相同权重的原臂配对，只改alpha，不顺便改变颜色、resize、prompt或评分。

## 收据

证据根为`artifacts/verification/f08-qwen2vl-preprocess-reference-20261111/precision-preparation/`。

- `run_pixel_stages.py`、`pixel_stage_plan.json`及`pixel-stage-output/{started.json,main.swift,manifest.json}`：输入、方法复制和源码绑定。
- `verify_pixel_stages.py`及`pixel-stage-output/parent-stage-verification.json`：99阶段数组、32输入、官方重建及分量。
- `verify_alpha_stages.py`及`pixel-stage-output/parent-alpha-formula.json`：32帧公式、空间范围与代数控制。

PNG经ImageIO重建CGImage，不是原始AVFoundation直接CGImage，也未证明设备同字节。已有四题原始权重官方8帧3/4和计数20帧1/2不变。本轮没有新质量、移动端性能/内存/热预算结论，不改变F08状态。
