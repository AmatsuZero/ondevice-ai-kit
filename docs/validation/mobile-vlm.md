# P8 移动端 VLM 候选调研

更新时间：2026-10-16。P8 只建立候选和验证门槛；不下载权重、不同时接入多个模型，也不把桌面推理结果当作移动端通过。

## 候选比较

| 候选 | 模态/任务 | 运行时候选 | 许可证与风险 | 当前结论 |
| --- | --- | --- | --- | --- |
| SmolVLM2 500M | 图片/短视频描述、问答 | iOS 18+ / MLXVLM，沿用现有总设计路线 | `mlx-community/SmolVLM2-500M-Video-Instruct-mlx` card 标记 Apache-2.0；BF16 权重约 1.02GB，**不是**设备峰值内存 | **P9 首选**：保持现有设计，不改成 256M |
| SmolVLM2 256M | 图片/短视频描述、问答 | 官方 card 提供 ONNX 组件，Android ONNX Runtime 可探针 | Apache-2.0；model.safetensors 约 1.03GB，官方视频推理 GPU RAM 约 1.38GB；这些不是移动端实测 | Android 备选，不与 P9 同时接入 |
| Florence-2-base | 图片描述、检测、区域任务 | 官方示例依赖 Transformers `trust_remote_code=True`；移动端转换需另证 | MIT；权重约 463MB，完整 repo 约 930MB；没有官方开箱即用移动 runtime | 任务型备选 |
| MiniCPM-V-2_6 | 图片问答、OCR/多模态对话 | 官方专用 llama.cpp 分支需验证，不能视为上游通用 runtime | 主权重访问受限；Apache-2.0 仅覆盖代码，权重使用独立 MiniCPM Model License，商业用途需登记；INT4 repo 约 5.97GB，模型卡称约 7GB GPU memory | 不作为 P9 首选 |

参考模型页（接入前必须重新读取具体 revision）：

- <https://huggingface.co/HuggingFaceTB/SmolVLM2-500M-Video-Instruct>
- <https://huggingface.co/mlx-community/SmolVLM2-500M-Video-Instruct-mlx>
- <https://huggingface.co/HuggingFaceTB/SmolVLM2-256M-Video-Instruct>
- <https://github.com/huggingface/HuggingSnap>
- <https://github.com/cyrilzakka/mlx-swift-examples>
- <https://huggingface.co/microsoft/Florence-2-base>
- <https://huggingface.co/openbmb/MiniCPM-V-2_6>
- <https://huggingface.co/openbmb/MiniCPM-V-2_6-int4/tree/main>

证据等级：模型仓库的许可证、权重大小和 revision 属于**上游模型资产证据**；HuggingSnap 的 iOS 18/MLXVLM 集成属于**上游移动端实现证据**；本项目还没有下载哈希、构建、iPhone 推理或峰值内存证据。Florence-2 的 463MB 是 FP16 权重大小，930MB 是含重复格式的仓库大小；256M 的 1.38GB 与 MiniCPM 的 7GB 是各自模型卡报告的 GPU 内存，不是本项目设备 RSS。Florence-2 官方示例使用 `trust_remote_code=True`，移动端转换及代码供应链需单独核验；还需明确 base 与 base-ft 的任务质量差异。

P9 候选 revision：2026-10-16 只读查询官方模型 API，`mlx-community/SmolVLM2-500M-Video-Instruct-mlx` 的当前 SHA 为 `fa57db46815177fbdfd65cc85a2b3416a8332268`，card 的 license 为 `apache-2.0`。这是待下载前重新读取和校验的观察值，不把浮动分支名作为锁定版本。MLXVLM 示例源码也须锁定 commit，而不是跟随 `main`。

## 统一验证门槛

1. 锁定 checkpoint revision、tokenizer、图像预处理和量化配置，记录 SHA256。
2. 固定 3 张不含个人信息的测试图和 1 个短视频；保存输入 hash 和结构化输出，不提交图像内容。
3. 分别记录模型加载、首 token、完整输出、峰值内存、取消和连续两次会话资源释放。
4. 至少一个目标平台实机或明确标记 unavailable；构建/转换成功不算推理成功。
5. 质量采用任务特定的人工可复核记录；不把不同任务或不同模型合并成总分。

P8 当前状态：候选、许可证、体积、运行平台和证据等级已记录。P9 选择 **SmolVLM2 500M-Video-Instruct-mlx + iOS 18+ MLXVLM**，保持现有总设计；256M ONNX 仅作为后续 Android 备选。P9 权重及全部支持文件已在忽略的 `artifacts/P9/verified` 完成 SHA256 校验，`model.safetensors` 为 1,015,023,993 字节、SHA256 `a9839c8f79ecc93e54a00dc73cc0e68ba477debcd065d50c1c289fbb1075f981`。探针尚未完成构建或真机运行。

权重不进入 Git 或 LFS。重新取得资产时，使用固定 revision 的 `tools/fetch_vlm_asset.py stage docs/validation/p9-smolvlm-manifest.json artifacts/P9/staging` 下载到被忽略的 staging；运行 `python3 tools/validate_vlm_manifest.py docs/validation/p9-smolvlm-manifest.json`，再运行 `python3 tools/fetch_vlm_asset.py publish docs/validation/p9-smolvlm-manifest.json artifacts/P9/verified`。发布器会复核每个文件的大小及 SHA256。
