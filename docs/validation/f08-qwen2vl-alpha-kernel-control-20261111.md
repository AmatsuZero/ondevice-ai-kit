# F08 alpha与bicubic参数的单变量语义对照

两个新控制均未改善四题严格准确率：仅消除预乘alpha输出A/C/B/A，仍1/4；仅将CoreImage bicubic C从0.75改为0.5，输出C/C/B/A，也1/4。前者数值差异未翻转答案，后者只让11448换成另一个错误选项。不能以此声明任何预处理差异已无关或理论做不到，但两项都不足以单独解释/解决当前阻塞。

## 固定项与实际结果

均使用既有固定2B4bit模型的11文件，MLX0.32.3/Metal、mlx-vlm0.3.9、校准分段attention、原四题tokens/positions/THW、balanced512/fresh28KV/greedy32/EOS151643与151645。病例顺序11448/1811/7915/9431，正确B/B/C/A。没有Torch/CPU模型/Ollama推理、新依赖/权重下载/iPhone操作/生产改动。

| 臂 | 唯一输入处理变化 | 完整输出 | 严格正确 |
| --- | --- | --- | --- |
| 已保留宿主CoreImage基线 | 无 | A/C/B/A | 1/4 |
| alpha控制 | 原32帧归一化RGB除同帧alpha | A/C/B/A | 1/4 |
| C0.5控制 | 同PNG，同CoreImage方法，只设parameterC=0.5 | C/C/B/A | 1/4 |

每题均实际生成一字母后EOS151645，8份完整logits/臂（含EOS）已父端SHA/finite/argmax/token解码/严格评分复算。不是只看首token替代完整生成。28层prefill/decode offsets、原tokens/positions协议相同，11模型文件摘要保持；每个新输入只运行一次。

## alpha控制：无语义改善

从早先直接AVFoundation宿主导出的32份RGBAf读取，没有重新解码或渲染。首先重打包RGB，逐元素复现原4组packed张量；再只除alpha，finite/正alpha与每例108个直接坐标检查通过。父端独立逆打包后逐帧与原RGBA除法全元素相同。新张量与旧张量不同，四个normalized RMSE变化0.000760/0.001226/0.000508/0.000988。

真实Metal进程exit0/3.500s，全部输出保持；首logits相对旧宿主NRMSE0.00497/0.00644/0.00309/0.02886。因此有数值影响，但没有纠正这四题的语义错误。不能把此负向结果外推所有输入，也不应据此修改生产代码并称识别修复。

证据根`artifacts/verification/f08-qwen2vl-alpha-control-20261111/`：`experiment-plan.json`、`prepared.json`、`runner-source.diff`、`alpha-launch-result.json`、`pixel-inference/`及`parent-alpha-verification.json`。57输入、18旧输出、32帧精确变换与11模型通过。4项前置门检查通过。父verifier末尾误读历史plan不存在的`protectedFiles`，实际为未使用行；保留`parent-verifier-field-error.json`，删除该行后重新离线验收通过，没有模型重跑或编造28保护项新复核。

## bicubic控制：像素更近，但答案未改善

实际系统filter读回B=0、C=0.75；本机SDK `CIFilterBuiltins.h`明确对应cubic B/C参数。固定安装Pillow13.0.0官方源码`src/libImaging/Resample.c`使用`a=-0.5`，对应B=0、C=0.5的cubic核。源码HTTP200/35404B/SHA880ea659…保留于alpha根`pillow-pinned-Resample.c`及`pillow-resample-source.json`；不声称相同参数等于完整采样实现等价。

在独立阶段目录只给复制方法增一行`filter.parameterC = 0.5`；与原源码去除此行后完全相同，32份tone阶段数组SHA逐一相同，颜色/normalize/目标504×280/alpha处理均不改。99新阶段数组、官方重建及输入摘要通过，C0.5使相对官方RGB255 RMSE如下变化：

| 病例 | C0.75 | C0.5 | 下降 |
| --- | --- | --- | --- |
| 11448 | 0.692670 | 0.492404 | 28.9% |
| 1811 | 0.724116 | 0.431702 | 40.4% |
| 7915 | 0.595402 | 0.482222 | 19.0% |
| 9431 | 0.869859 | 0.665626 | 23.5% |

新的重要等价证据：默认C0.75的同PNG normalized RGBA与早先直接AVFoundation宿主32份RGBA逐字节相同，重打包也全元素相同。因此复用已留宿主模型基线，无需重跑。原准备稿错误假定PNG输出必不同，在任何模型运行前断言失败；保留`baseline-equality-discovery.json`及`initial-default-preparation-retained/`，修正计划后只运行C0.5候选臂，不是模型失败重试。等价仅限本次32个输出数组，不扩展到原CGImage或所有媒体/设备。

真实C0.5模型进程exit0/3.246s，完整输出C/C/B/A，1/4。11448 C-A仅0.015625，仍错；7915仍B，C-B=-0.078125。像素相似性提升未转为正确率提升，故不能直接将此参数作为产品修复。剩余色彩/浮点舍入/采样边界或模型可靠性仍未全部分离。

阶段证据在精度根`kernel_stage_plan.json`、`kernel-{runner,verifier}-source.diff`、`kernel-stage-output/{parent-stage-verification,parent-result}.json`。语义证据根`artifacts/verification/f08-qwen2vl-bicubic-control-20261111/`：`pair-plan.json`、`pair-source.diff`、`c05/{experiment-plan.json,launch-result.json,pixel-inference/}`、`verify_kernel.py`及`parent-kernel-verification.json`。57输入、50旧输出、32tone不变/32基线RGBA等价、32新打包帧/8logits/11模型与协议通过；4项前置门检查通过。

## 决策与下一步

保留数值边界事实，停止逐个微调参数追四题答案。现在有意义的是用已验证的官方预处理路径扩展固定24题诊断覆盖，复用已完成4题，避免重放；报告六类别质量，仍不设未经批准的通过阈值。该宿主质量对照不能替代已中断的连续iPhone24题/热停止、内存加载预算或生产验收。当前原权重官方8帧3/4、官方20帧计数1/2保持，F08open/not-ready。

前置已实际核验24媒体size/SHA、六类各4题、原三份报告摘要/24原prompt/8帧时间；从四个真实完整token数组抽出共同模板，四题全数组精确复现，24题长度与旧Swift报告匹配。其余20无完整设备张量导出，只能称prompt/长度一致，尚未运行新20题。收据`official24-preparation-inventory.json`、`official24-token-template-preflight.json`。alpha/kernel各4项前置gate tests通过，输入/源码/旧输出摘要最终重验，见`final-offline-check.json`；未重复模型推理。
