# P8 移动端 VLM 候选调研

更新时间：2026-10-16。P8 只建立候选和验证门槛；不下载权重、不同时接入多个模型，也不把桌面推理结果当作移动端通过。

## 候选比较

| 候选 | 模态/任务 | 运行时候选 | 许可证与风险 | 当前结论 |
| --- | --- | --- | --- | --- |
| SmolVLM2 500M | 图片/短视频描述、问答 | iOS 18+ / MLXVLM，沿用现有总设计路线 | 官方 HuggingSnap 路线使用 500M-Video-Instruct-mlx；Apache-2.0 需以具体 checkpoint card 复核，MLX 权重和内存需实测 | **P9 首选**：保持现有设计，不改成 256M |
| SmolVLM2 256M | 图片/短视频描述、问答 | 官方 card 提供 ONNX 组件，Android ONNX Runtime 可探针 | Apache-2.0；model.safetensors 约 1.03GB，官方视频推理 GPU RAM 约 1.38GB；这些不是移动端实测 | Android 备选，不与 P9 同时接入 |
| Florence-2-base | 图片描述、检测、区域任务 | 官方示例依赖 Transformers `trust_remote_code=True`；移动端转换需另证 | MIT；权重约 463MB，完整 repo 约 930MB；没有官方开箱即用移动 runtime | 任务型备选 |
| MiniCPM-V-2_6 | 图片问答、OCR/多模态对话 | 专用 runtime/llama.cpp 路线需验证 | Apache-2.0 代码不等于权重许可；INT4 repo 约 5.97GB，模型卡约 7GB GPU memory，且有商业登记要求 | 不作为 P9 首选 |

参考模型页（接入前必须重新读取具体 revision）：

- <https://huggingface.co/HuggingFaceTB/SmolVLM2-500M-Video-Instruct>
- <https://huggingface.co/HuggingFaceTB/SmolVLM2-256M-Video-Instruct>
- <https://github.com/huggingface/swift-huggingface>
- <https://huggingface.co/microsoft/Florence-2-base>
- <https://huggingface.co/openbmb/MiniCPM-V-2_6>

## 统一验证门槛

1. 锁定 checkpoint revision、tokenizer、图像预处理和量化配置，记录 SHA256。
2. 固定 3 张不含个人信息的测试图和 1 个短视频；保存输入 hash 和结构化输出，不提交图像内容。
3. 分别记录模型加载、首 token、完整输出、峰值内存、取消和连续两次会话资源释放。
4. 至少一个目标平台实机或明确标记 unavailable；构建/转换成功不算推理成功。
5. 质量采用任务特定的人工可复核记录；不把不同任务或不同模型合并成总分。

P8 当前状态：候选、许可证/体积风险和统一验证门槛已记录。P9 选择 **SmolVLM2 500M-Video-Instruct-mlx + iOS 18+ MLXVLM**，保持现有总设计；256M ONNX 仅作为后续 Android 备选。尚未下载权重或运行探针。
