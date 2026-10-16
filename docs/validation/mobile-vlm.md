# P8 移动端 VLM 候选调研

更新时间：2026-10-16。P8 只建立候选和验证门槛；不下载权重、不同时接入多个模型，也不把桌面推理结果当作移动端通过。

## 候选比较

| 候选 | 模态/任务 | 运行时候选 | 许可证与风险 | 当前结论 |
| --- | --- | --- | --- | --- |
| SmolVLM2 | 图片/短视频描述、问答 | Transformers/MLX/Core ML 转换需验证 | 以具体 checkpoint model card 和权重许可证为准；移动端内存与 tokenizer 需实测 | 首选轻量候选，先锁一个 checkpoint |
| Florence-2 | 图片描述、检测、区域任务 | ONNX Runtime/Core ML/LiteRT 需逐模型验证 | 代码与权重许可需分开核对；任务头输出不等于通用对话 | 任务型候选，不与通用 VLM 混合 benchmark |
| MiniCPM-V | 图片问答、OCR/多模态对话 | llama.cpp/厂商 runtime/MLX 需验证 | 模型体积、量化、图像编码器和上下文内存风险较高 | 备选；只有资源探针通过后再选 |

参考模型页（接入前必须重新读取具体 revision）：

- <https://huggingface.co/HuggingFaceTB/SmolVLM2-256M-Video-Instruct>
- <https://huggingface.co/microsoft/Florence-2-base>
- <https://huggingface.co/openbmb/MiniCPM-V-2_6>

## 统一验证门槛

1. 锁定 checkpoint revision、tokenizer、图像预处理和量化配置，记录 SHA256。
2. 固定 3 张不含个人信息的测试图和 1 个短视频；保存输入 hash 和结构化输出，不提交图像内容。
3. 分别记录模型加载、首 token、完整输出、峰值内存、取消和连续两次会话资源释放。
4. 至少一个目标平台实机或明确标记 unavailable；构建/转换成功不算推理成功。
5. 质量采用任务特定的人工可复核记录；不把不同任务或不同模型合并成总分。

P8 当前状态：候选和验证门槛已记录；尚未选择 checkpoint、下载权重或运行探针。P9 只在 P8 复核许可和资源约束后选择一个模型、一个平台和一个 runtime。
