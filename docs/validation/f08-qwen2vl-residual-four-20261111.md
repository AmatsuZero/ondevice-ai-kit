# F08 原视频四题：实际视觉特征与参考实现分段注意力

本轮已完成：同一iPhone进程四题、实际输入/位置/合并特征/首token向量导出、独立Python MLX Metal及分段注意力控制。**修正Python参考实现的视觉分段缺陷后，四题首token全部与已修正网格路由的iPhone一致，仍三错一对。** 当前没有证据指向这四题存在另一处Swift独有的语义故障；也不能据此证明原始模型理论上无法识别。F08仍open/not-ready，无生产变更。

证据根：`artifacts/verification/f08-qwen2vl-residual-four-20261111/`。父独立复算入口`parent-independent-verification.json`，脚本`verify_outputs.py`。本轮不是新24题benchmark或质量通过。

## 固定输入与实际真机结果

模型仍为`mlx-community/Qwen2-VL-2B-Instruct-4bit`，revision `01af461cdb9574acc09084a0ef94e216e142b085`，权重SHA256 `3534d3dcfaacd0cd00e935469bc34b6d692b2ba0749c5d9ee422f3f2175cf992`。无新权重、Torch、CPU模型推理或Ollama。Python只作Mac Metal独立诊断，移动端仍为Swift/Metal。

从原清单SHA`37326e75d21d738e9ab516dce2a648482aa7e480393771c4107080dc26888e1f`选原索引8、11、5、20；先核完整24题，再按注册顺序运行。原媒体、问题、选项、真实帧时间、ordinal处理时间、评分均保持。四题均用原视频8帧、THW`[4,20,36]`，不是端点重复帧。

| 原病例 | 正确选项 | iPhone完整原文 | Python原生首token | Python分段控制首token |
| --- | --- | --- | --- | --- |
| video_11448-q0：放置物体计数 | B | A | B | A |
| video_1811-q1：抽象判断 | B | C | C | C |
| video_7915-q1：原完整视频记忆 | C | B | B | B |
| video_9431-q2：稳定语义阳性控制 | A | A | A | A |

Python原生7915的B/C logits恰好同值23.546875，argmax选择B，不能把该原生结果说成有稳定正确margin。Python均仅首token，不能冒充完整输出或格式评分。iPhone四题完整生成各一个字母，严格1/4，与前次修正后的相应原文`A/C/B/A`逐项复现。

iPhone17,2/iOS27.2.1、PID3369、runId`ACA3E226-1E73-481E-8719-97A7AF6F792A`，四题同进程completed、终态双读一致，报告SHA`2ec60b5b12a6c662f306d21cc72e1e0035654946a430395f0a0968d710d27e47`。thermal-only新PID预检为nominal；运行thermal0→1，无guard触发，采样phys峰值3,108,916,408B（约2.895GiB），load905.078ms。含额外特征导出forward，不用于生产性能预算或持续热验收。

## 视觉边界实验与参考缺陷

隔离Swift副本保留已验证的两处视频网格路由修正；只在`LMOutput.State`增加实际`embeds`引用，导出模型真实合并输入。构建后源码SHA保持、签名Release91.149s通过、严格codesign通过。consumer fingerprint `efcd6b3fc06c90a277ca3aa5fbbb599b56574b816f22247727c14c8bdf588d2d`。不修改共享SourcePackages、生产代码或lock。

四题实际tokens/pixels/mask/positions/embeds/logits各二进制SHA、shape、finite均校验；实际MRoPE4/4与官方源翻译逐元素匹配，也与Python原生单视频位置匹配。特征原始dtype是float16，以float32存储；注入时恢复float16并核数值往返精确，未误用float32注入。

Python0.3.9原生视觉特征相对Swift的NRMSE为0.798–0.959，文本特征则全部逐位相同。11448原生Python首选B，但将实际Swift合并特征注入同Python语言模型、相同位置/默认balanced512分块/每臂fresh28层KV后变A，与iPhone相同；logits NRMSE从0.07775降到0.00204。这只将该差异定位到视觉特征之前，不能直接证明Swift视觉实现有错。

核实际安装源码发现`mlx_vlm/models/qwen2_vl/vision.py`创建`attention_mask`形状`[1,N,N]`、初值1，却使用两个维度`[start:end,start:end] = 0`，没有在最后两个轴写块；浮点1也只是加性bias，不会阻断跨段注意力。原生四段已知值测试：Q/K为0、V分别恒为1/3/7/11，实际每段都输出全局均值5.5，而应分别保持各段值。两项检查真实1失败/1通过，原RED保留。

只在本轮Python诊断wrapper改为按`cu_seqlens`拆Q/K/V、各段无mask独立attention后拼回，复用全部原投影/权重；与安装的官方Transformers4.57.6 `VisionAttention`非flash路径相同。两项已知值检查通过。无site-packages修改或依赖升级。官方源码仅读取、未导入Torch；完整源SHA/摘录见`official-source-provenance.json`、`official-VisionAttention-excerpt.txt`。MLX加性mask语义另通过Context7 `/ml-explore/mlx`核对，[官方MLX API源](https://github.com/ml-explore/mlx/blob/main/python/src/fast.cpp)。

新分段控制是一项明确独立实验，计划为`segment-control-plan.json`；不是原生run重试，原生结果完整保留。四题控制后视觉NRMSE降至0.02405–0.03909，首token全与iPhone一致，logits NRMSE分别0.004302/0.002129/0.002188/0.002995。仍非逐位数值等价，不能外推所有输入/设备或后续解码一致。

## 裁定、验证与后续边界

1. 已确认的Swift视频网格路由缺陷仍成立；本轮不撤销其真实同输入因果验证，也不以较高旧分数撤回正确路由。
2. 本轮巨大视觉差异主要来自Python参考的视觉分段错误，而非已证明的Swift第二处缺陷。既有Python视频原生/分块实验保留，但其中视觉参考并非完全符合官方实现；仅修正MRoPE不足以称完整官方参考。本轮没有重跑既有端点视频，不追溯编造它们修正后的答案。单图/多图的其他边界也不借此外推。
3. 这四题在位置正确、分段视觉参考接近、语言输出接近后仍三题共同误判，进一步削弱Swift独有实现导致全部剩余错误的假设。尚未分离4bit量化、固定模型自身能力、两侧共同使用的8帧/分辨率/预处理。无法据同prepared张量证明原视频处理正确，更不能断言所有模型做不到。
4. 当前固定2B4bit配置仍不能作为可靠短视频理解方案。后续有区分力的是同输入更高精度权重或独立官方预处理对照；前者涉及新权重，未在本轮下载。不要继续把调prompt、放宽评分或重复同一负例当根因进展。

父独立复算全部4组导出、8份视觉/文本特征比较、9个151936维logits向量及label数值/argmax/RMSE/NRMSE、28层cache offset，4题原媒体元数据/问题/帧时间/评分；11模型文件、本轮9保护文件和旧19保护文件保持。7位置参考+2分段行为共9项通过，先前36项纯合同检查保持。无独立native复核：此前429不自动重试，不能用父复算冒充独立审查。

candidate已卸载、普通Combined已重新打开，所有原有App安装元数据逐项相同。源码/原张量/原生RED/两臂结果均保留；签名App先留存再精确清理本轮DerivedData，清理收据见`cleanup-receipt.json`。F08、持续热和产品质量门保持open。
