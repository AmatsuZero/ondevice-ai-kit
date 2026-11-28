# F08 Qwen3-VL 2B 4bit：iPhone valid18 诊断

修正 Swift 视频协议后，iPhone16 Pro Max 已完成固定18题，严格 **9/18**、格式无效 **6**，全部18份原始回答、生成 token IDs 和 EOS 与现有 Mac Python Metal 对照相同。**手机能够运行，但质量阻塞仍在；本轮错误未表现为 iPhone 特有问题。** F08 保持 open/not-ready。

## 实验与验收

证据根：`artifacts/verification/f08-qwen3vl-swift-parity-20261114/`，运行 ID `f08-qwen3-metal-valid18-20261114-01`，独立 bundle `io.ondeviceai.f08qwen3metal20261114`。模型固定 `mlx-community/Qwen3-VL-2B-Instruct-4bit` revision `9c4f5209e57b31f4b9dfba735de3fb983739c9cc`，权重1,782,040,921B、SHA256 `4750d95a2162829e127a94e83ac350d498d02070aab216c4687da48804a06ffb`，15文件在手机加载前和父端均核验。

前置 [Swift协议验收](f08-qwen3vl-swift-parity-20261114.md) 已覆盖32自然视频输入，真实手机 processor 与 Mac 的 tokens/THW/三轴位置/delta 一致、全部32像素逐字节相同。模型臂固定原 valid18 全部18题和原顺序、8 PNG/物理时间/问题/选项，无按手机结果筛选。每题实际 processor 再验所有协议字段及像素SHA后，才创建 fresh28KV 并生成。

仅 MLX Metal，greedy、最多32 token、EOS151643/151645，balanced512预填，不约束输出格式。独立10ms采样监控沿用3.5GiB物理内存、serious温度、180s停止；没有触发，不是产品预算或硬分配上界。没有新下载、Torch、CPU模型、Ollama、生产依赖/锁/默认改动。

实际终态双读及目录内报告逐字节相同。`parent-ios-model-verification.json`核验72份完整有限 float32 全词表 logits（151936维，含EOS）、每步真实argmax、28层offset、携带rope delta、完整token decode、严格评分、15模型文件与296冻结文件。`parent-ios-python-logits-comparison.json`补核全部18生成IDs/EOS相同；72向量本身不逐字节相等，relative RMSE范围0.013985–0.101507，maxAbs0.25–1.375。此为描述值，没有设数值等价阈值。

Swift/CoreImage像素与Python/Pillow仍有0.27–1.15/255 RMSE；Swift balanced512与Python full-prefill、runtime和硬件均不同。相同回答支持本轮错误不依赖手机特有故障，不能外推数值完全等价或唯一归因模型架构。

## 结果

| 类别 | 严格正确 | 格式无效 |
| --- | --- | --- |
| counterfactual | 2/3 | 0 |
| memory | 2/3 | 1 |
| abstraction | 1/3 | 0 |
| physics | 3/3 | 0 |
| semantics | 1/3 | 2 |
| camera_motion | 0/3 | 3 |
| 总计 | **9/18** | **6** |

首字母对应正确选项14/18仅作诊断，不能替换严格9/18。五题选对但追加句点或说明；四题选项错误是video10249、4505、9676、9500。video4505同时有选项和格式错误。因此独立约束输出可能减少格式失败，但不能独自解决四个内容错误，也不能据此宣布模型不可能理解视频。原配对/像素干预已证部分相机题有视觉依赖，可靠性仍不足。

| 资源观察 | 本轮值 |
| --- | --- |
| 模型加载计时 | 807.7285ms |
| 整臂时间（含校验、加载、18题） | 58.3132s |
| 单题完整准备与生成中位数 | 2.82657s |
| 采样物理内存峰值 | 3,160,231,504B（约2.94GiB） |
| MLX peak | 2,484,161,851B |
| 资源采样数 | 5273 |
| 温度 | nominal→fair |

加载计时不含传输/文件校验；单题时间包含实际processor与数值证据导出，也不是最终产品延迟。未验证最低设备、持续热稳定、模型释放后内存或用户批准的预算。

## 恢复与下一步

`ios-model-restoration.json`确认独立候选已卸载、候选进程消失，45普通App完整元数据与运行前相同，已恢复UgreenHome；普通模型未写入。模型副本随候选容器删除，宿主原模型及独立结果保持。唯一一次模型运行完成，无自动重放；所有构建/传输/监控均终态。

下一应独立验证受约束的单字母输出，保留本轮严格基线；同时围绕实际产品任务检查四条内容误判与独立相反答案视频，不再反复扫描同题prompt/帧数。单字母约束的离线诊断不能冒充新模型实验。Android、独立产品数据、最低设备/耗时/内存预算、持续运行、生产适配与发布门仍开放。

最终审计`parent-final-audit.json`通过：2366个旧/新冻结文件复核，72完整向量保持，两个候选均卸载、无候选设备进程或活动模型/构建；磁盘剩余约64.9GiB（该时点）。总目标active，未将本诊断标成F08完成。

后续[输出约束离线验证](f08-qwen3vl-choice-constraint-20261114.md)以相同生成前缀的真实向量验证14/18、invalid0；没有新手机推理，原9/18不改。实际输入内容审读另发现9676事件覆盖缺口，不能全归为模型能力。
