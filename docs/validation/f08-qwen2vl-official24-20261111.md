# F08 Qwen2-VL官方预处理24题对照

2026-11-12协议补记：旧24的 `video_8981=179`、`video_8295=155` 有官方截断要求，此前使用全片。下方分数保留为全片预处理诊断，不能当官方截断协议基准；仅离线剔除这两条后，宿主4bit10/22、原始值FP32为13/22，未新运行。新72已排除全部需截断视频，见 [新72报告](f08-expanded72-20261112.md)。

固定24题上，现有2B4bit配官方预处理严格答对 **10/24（41.7%）**；同输入的原始BF16存储值提升FP32后答对 **14/24（58.3%）**。基础语义题两者均4/4，精细计数/反事实/记忆等仍有错误。原始值FP32的10项失败中，9项选错选项、1项仅因多余句点失败，不能把剩余问题仅归因于输出格式。该配置尚未证明可满足短视频理解需求，F08继续open/not-ready。

本轮没有新的产品通过阈值；结果是同一训练split固定小样本诊断，并非heldout benchmark或连续真机验收。不改正式模型、依赖、默认或评分。没有Torch/CPU模型/Ollama推理、新模型下载或iPhone操作。

## 质量结果

| 类别（各4题） | 已修网格路由的旧iPhone4bit | 宿主官方像素4bit | 同官方像素原始值FP32 |
| --- | --- | --- | --- |
| 相机运动 | 1/4 | 1/4 | 1/4 |
| 记忆/变化 | 1/4 | 2/4 | 2/4 |
| 抽象/计数 | 0/4 | 0/4 | 2/4 |
| 反事实 | 1/4 | 1/4 | 2/4 |
| 物理状态 | 2/4 | 2/4 | 3/4 |
| 基础语义 | 4/4 | 4/4 | 4/4 |
| 合计 | 9/24 | 10/24 | 14/24 |
| 不符合字母格式 | 5 | 5 | 2 |

旧iPhone列来自此前因热停止而分三进程收集的24个不同输出，不是一个completed连续运行。宿主4bit同样聚合本次20题与旧4题输出；其余20题与旧设备的原文全部相同，24题中只有11448由A错变C错、7915由B错变C对，未出现退步。不能将旧设备→宿主24题差异完全单归像素：其余20题没有已导出的设备完整张量或匹配的Python原像素臂，运行时与预处理仍混合。

原始值FP32与4bit在全部24题上匹配相同pixels/tokens/positions/THW/prefill协议，只有权重及计算精度组合变化。四项严格改善：

- 11448：物体数量，由C（5）改B（3），选项判断改善。
- 1738：反事实取物，由A改C，选项判断改善。
- 11297：`A.`改`A`，选项不变，去掉多余句点。
- 8295：`C.`改`C`，选项不变，去掉多余句点。

没有退步项，但1811三次发射仍答2/C；原始值FP32即使配20帧也未答对的结论保持。4bit的14个失败中11项选错、3项仅格式失败；原始值FP32的10个失败中9项选错、1项仅格式失败。此处仅作失败分类，**没有去标点重算通过率或放宽rubric**。原始值FP32仍有8个格式正确但选错的病例，因此单独约束输出格式不能解除语义阻塞。

## 输入与参考边界

复用原manifest，24段视频SHA/大小、六类各4题、原问题/选项/正确字母完全保持。本次只新增其余20题；旧4bit官方像素四题和原始值FP32官方像素四题分别复用原始结果/向量，未重跑。

20段经此前固定的AVFoundation宿主snapshot工具各导出8帧，160张PNG实际时间与旧设备报告逐项一致；全部HDTV ICC经固定Pillow13.0.0 LittleCMS转sRGB，再调用固定Transformers4.57.6 `Qwen2VLImageProcessor`。真实官方输出均为[2880,1176]、THW[4,20,36]。父独立重新处理20组，全部packed数组逐元素相同；160帧与283输入条目SHA通过。

从四个真实设备导出token数组中提取完全相同的Swift前后模板，四个完整token数组精确复现；其余20题按原prompt重建，原文/长度与设备报告逐项相同，参考MRoPE重新计算并校验，mask全1。其余20无设备完整token/pixel导出，**不能声称它们与设备逐元素相同**。当前宿主两个权重臂之间则直接共享全部20新tokens/pixels/positions，原4题亦已核匹配。

已有Python分段attention诊断控制保留，不使用mlx-vlm0.3.9有缺陷的原生浮点mask。均为MLX0.32.3 Metal、mlx-vlm0.3.9/mlx-lm0.29.1、fresh28KV/balanced512/greedy32，EOS151643/151645，只trim周边空白评分。生成时实际保留完整原文及token，包含`B.`/`C.`失败，未用首token或标签logits替代评分。

原始权重仍是官方revision895c3a49的两BF16分片，完整SHA/index/729张量合同在模型import前重验；729参数内存提升FP32逐元素相等及finite运行断言全部通过，父端另核全部参数名/形状及conv布局。不是恢复原训练FP32权重，没有落盘转换。4bit卡没有固定源revision，因此权重臂差异只能归为精度/转换来源组合，不能独立量化“4bit损失”。

## 真实执行与验收

证据根：`artifacts/verification/f08-qwen2vl-official24-20261111/`。

- `experiment-plan.json`、`inference-plan.json`、`token-template.json`、`source-device-rows.json`及`prepared.json`绑定选择、题目和输入；`runner-source.diff`可审阅。
- `parent-prepared-verification.json`：20官方数组、160帧、20重建token/参考位置、283输入与21旧输出摘要通过，无模型import。
- `inference-launch-result.json`：4bit新20题进程exit0/9.559s，`pixel-inference/`留45份完整logits含EOS。`parent-result-verification.json`另复核旧4首向量，合计24题10正确/5格式失败。旧四题仅保留EOS停止收据，没有EOS全向量，此缺口保持。
- `original-fp32/{experiment-plan.json,runner-source-final.diff,launch-result.json}`：同20输入的原始值FP32进程exit0/22.054s，`fp32-inference/`留42份完整logits含EOS；加原四题8份全向量，父共核50份，见`parent-original24-verification.json`。729张量/参数shape、283输入、87旧输出及11旧模型保持。
- `4bit-failure-taxonomy.json`与`original-values-FP32-failure-taxonomy.json`仅分类失败，严格分数不变。六项选择/模板/评分合同检查通过；无模型重放。ComfyUI空队列、Ollama无加载模型的运行前证据分别保留。

首次准备器因通用read函数接收字符串路径却直接read_text而退出，发生在preparation marker/解码/模型前；保留最初stdout/stderr/exit与`prepare-recovery-decision.json`，仅统一Path后实际完成。原始值runner准备稿的source替换guard假定index只出现1次，实际2次，任何模型运行前停止；空准备目录保留为`initial-original-preparation-retained/`，按明确两处括号引用修正。preflight旧文案`modelFilesDownloaded=false`会误导已下载状态，已在模型运行前改为“预检不查分片”，最终源码diff/hash与实际启动一致。这些是诊断工具错误，不归模型/供应商，也没有重跑已完成输入。

## 决策

本轮不支持将现有4bit Qwen2-VL直接切为正式通用视频理解模型；原始权重改善仍不足以消除明显的选项错误，且不是移动端内存/性能解法。预处理差异真实存在，但只对齐alpha/bicubic或换原始精度不能充分解决阻塞。无需再扫同题prompt、帧数或微小像素参数。

下一仍有价值且此前用户明确提出的分支，是让原SmolVLM2在同一24题上接受公平评估，核实旧模型是否在基础语义等类别仍有用；不得把未执行的横向对照说成完成。现有500M和2.2B在运动/冻结反事实上的历史失败保持，不因更换数据集撤销。新的移动端选择还需更广独立数据、明确产品预算、连续热/资源及独立源码复核，F08继续open/not-ready。
