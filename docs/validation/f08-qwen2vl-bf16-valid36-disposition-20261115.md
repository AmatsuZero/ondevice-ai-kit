# F08 Qwen2-VL 原始权重 valid36 处置审计

状态：**no-go；不运行 Qwen2-VL 原始权重 valid36，也不进入 iPhone 36 题。F08/W6 仍为 open/not-ready。**

## 裁定

这次只读审计没有启动模型。现有原始权重对照是 Mac MLX Metal 上将 BF16 存储值提升为 FP32 的容量诊断，并不是可直接部署到手机的运行配置。它在 fixed24 上严格 **14/24**，低于同集 SmolVLM2 2.2B 4-bit 的 **16/24**。相对 Qwen2-VL 4-bit 的 10/24，四项严格改善中只有两项改变了错误选项，另两项只是去掉句点；仍有九项合法格式但选错。

原始 BF16 tensor payload 为 **4,418,050,768 B（4.115 GiB）**，是 4-bit 权重的 **3.546 倍**。它单独已经比现有 **3.5 GiB** 实验保护线多 **629.4 MiB**，还没有计入 KV、视觉输入、中间张量、缓存、运行时和 App。既有 Qwen2 4-bit 在 iPhone 16 Pro Max 上已测最大 process phys_footprint **2,375,781,464 B（2.213 GiB）**、MLX active **1,740,005,908 B（1.621 GiB）**。当前没有 streaming/offload 实现证据。

因此，继续跑 valid36 最多补充一个非移动容量结果，不能解除当前移动产品选择阻塞。停止该分支不会证明 Qwen2-VL 原始权重无法达到 20/36；结论仅是：**在当前移动端范围、当前 eager MLX 路径和现有保护线下，这次运行没有足够产品决策价值。**

## 证据表

| 项目 | 已有结果 | 对本次决策的意义 |
| --- | ---: | --- |
| Qwen2 4-bit fixed24 | 10/24 | 当前量化基线 |
| Qwen2 原始值 FP32 fixed24 | 14/24 | +4 strict；其中 +2 内容、+2 格式 |
| Smol2.2B 4-bit fixed24 | 16/24 | 更小候选在旧集仍高 2 题 |
| Smol2.2B 4-bit valid36 | 14/36，invalid1 | 已失败 20/36 且净增 +4 晋级门 |
| Qwen3 4-bit valid36 | 16/36，invalid0 | 当前冻结比较基线 |
| Qwen2 4-bit iPhone 峰值 | 2.213 GiB phys | 1.246 GB 权重下已有显著运行开销 |
| Qwen2 原始 BF16 权重 | 4.115 GiB | tensor payload 已越 3.5 GiB 实验保护线 |

## 边界

- 3.5 GiB 是既有诊断保护线，不是已批准产品预算。当前产品内存和加载预算仍未确定。
- BF16 文件大小不是 iPhone phys_footprint 实测值；本审计没有伪造资源预测。它是当前 eager tensor 路径的硬输入规模，而已有证据没有 streaming/offload。
- fixed24 是旧 train split 小样本，不能直接预测 valid36 分数；本次依靠的是“移动配置不可用 + 质量信息增益低”的组合裁定。
- 不删除原始 BF16 权重；磁盘清理由独立授权和精确引用审计处理。

## 重开条件

只有满足以下任一条件才重开：

1. 产品合同明确接受至少 4,418,050,768 B 的模型包，并允许高于当前 3.5 GiB 保护线的资源包络。
2. 已实现并独立验证 streaming/offload，使原始权重不需按当前 eager 路径常驻。
3. 出现有独立质量依据、资源可控的 Qwen2 衍生量化或蒸馏候选。
4. 任务目标改为非移动容量研究。

## 执行与验证

证据根：`artifacts/verification/f08-qwen2vl-bf16-valid36-disposition-20261115/`。`decision-plan.json` 冻结规则，`evidence-snapshot.json` 绑定七份既有证据 SHA，`decision.json` 记录 no-go。没有模型推理、下载、iPhone 操作、生产改动或新 helper。实时检查仅确认 Ollama 无加载模型、ComfyUI 队列为空；未停止任何共享服务或其他会话。
