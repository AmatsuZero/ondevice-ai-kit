# F08 SmolVLM2 2.2B：三条高可见性错误样本的 16 帧宿主对照

状态：**诊断完成；F08 仍 open/not-ready**。

## 目的

可见性检查指出 `video_8635`（字母顺序）、`video_10100`（准备什么）和
`video_4991`（相机静止）在原 8 帧里已经有较强线索。这里固定同一
`smdesai/SmolVLM2-2.2B-Instruct-4bit` revision
`0a4ab666f99095e6c6bd0e1e49aca8822fc46df2`，只把每条视频的输入从 8 帧加密为
16 帧；偶数帧与原 8 帧逐字节相同。问题、选项、384px/81 image-token 配置、
balanced prefill、greedy32 和评分规则均保持不变。

这不是新的 benchmark 分数，也不是 iPhone 资源或产品验收。

16 帧也必然使模板里的帧数、时间标签、图像占位 token 总数、序列长度和
prefill 分段改变；因此这是原生 8→16 输入配置对照，不能声称只隔离了采样密度。

## 结果

| case | 期望 | 原 8 帧 | 新 16 帧 | 严格结果 |
|---|---:|---:|---:|---:|
| `video_8635-q1` 字母顺序 | C | B | B | 错 |
| `video_10100-q0` 准备什么 | B | A | A | 错 |
| `video_4991-q0` 相机运动 | C | A | A | 错 |

16 帧严格正确率 **0/3**，格式无效 **0/3**。三条答案都与 8 帧基线相同；
每条都保存了完整首 token logits 和 EOS，24 层 KV offset、输入 hash、14 个模型文件
和 579 个受保护旧输出由父校验通过。运行设备为 `Device(gpu, 0)`，无 Torch/CPU
模型或 Ollama 推理，MLX 峰值约 **2.54 GB**。

## 结论边界

这三条样本中，增加时间密度没有改变答案。因此当前阻塞不能继续归因于“8 帧完全
漏掉了线索”。更符合现有证据的是：模型在已有可见线索上的时序记忆、语义判别或
相机运动归因仍不可靠。此前已证实的预处理差异、视觉路由/分块边界和小样本限制
仍然存在，不能把本次 0/3 归结为唯一的模型容量原因，也不能推出所有视频理解
任务都做不到。

停止规则已满足：不再对同三条样本继续扫描 16/24/32 帧、prompt 或评分阈值。
下一有效工作应转向固定产品目标的模型选择/移动端预算门，或增加独立、预先冻结的
样本集，而不是重复这组反例。

## 证据

- 原始准备路径错误（导出器未编译，模型未运行）保留在
  `artifacts/verification/f08-smolvlm2-dense16-visible3-20261112/`。
- 修正版唯一运行和父验收在
  `artifacts/verification/f08-smolvlm2-dense16-visible3-20261112-r2/`：
  `parent-independent-prepared.json`、`metal-inference/result.json`、
  `parent-result-verification.json`、`execution-corrections.json`。
- 修正版准备阶段实际检查 48 帧；其中 24 帧与原 8 帧逐字节相同。

本轮未修改生产默认模型、共享依赖、锁文件或真机 App。

只读独立审查结论为 PARTIAL：支持“三个指定可见线索样本未因原生8→16输入改善”，
不支持模型普遍不可能、单变量采样因果或iPhone产品通过。审查报告为同证据根
`native-readonly-review.md`。
