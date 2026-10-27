# Android LiteRT-LM 模型资产候选

2026-10-26。本文记录本轮只读官方 metadata 调查，供后续资产授权与冻结使用；尚未选择生产模型。没有下载权重、登录账户、接受条款或改变设备模型状态。

## 已取得的候选 metadata

| 字段 | 当前证据 |
| --- | --- |
| 上游 | `google/gemma-3n-E2B-it-litert-lm` |
| 固定 revision | `c03b6f60b8da6c5400b6838a2cf26420f80c0a01` |
| 通用文件 | `gemma-3n-E2B-it-int4.litertlm` |
| 文件大小 | `3,655,827,456` bytes（约 3.66 GB） |
| 许可标记 | Hugging Face API `license=gemma`；不等于已确认本项目的再分发权限 |
| 访问条件 | API `gated=manual`，需要登录并接受 Google Gemma usage license |
| SHA-256 | **unverified**；当前 gated file metadata 未提供可用于 `ModelManifest` 的 LFS/Xet 内容 hash |
| 当前 repo pipeline | `text-generation`，不能据此宣称该文件已经支持图像输入 |
| LiteRT-LM 0.18.1 CPU/image 支持 | **unverified** |
| TECNO KM9/API35 兼容性、资源预算 | **unverified** |

本候选是本轮取得 metadata 的较小通用 `.litertlm` 文件。E4B 对应通用文件为 `4,919,541,760` bytes。这个比较不证明完整候选空间的最小值，也不构成生产模型选择。

官方旧 preview 仓库 `google/gemma-3n-E2B-it-litert-preview`（revision `66c93e118deff9961db659f241678e61b847d165`）标记 `image-text-to-text`，但其 `gemma-3n-E2B-it-int4.task` 为另一种 artifact，大小 `3,136,226,711` bytes。不能把旧 preview 的 modality 标记移植到当前 `.litertlm` 文件，也不能只改扩展名用于本计划。

## 冻结前仍需取得的证据

- 已授权的本地资产路径、许可与归属来源，以及项目所需使用/再分发权限。
- 精确文件集合、实际大小和完整 SHA-256，随后才创建真实 `ModelManifest`；不把含未知 hash 的候选文档当成 manifest。
- 当前文件在 LiteRT-LM 0.18.1 中的 `ModelInfo`：vision modality、vision token budget、runtime floor，以及 CPU 路径兼容性。
- 固定公开 fixture 的正常生成和取消双路径、明确 callback 模式、terminal/dead barrier、同进程重入与 close/cache 清理的真实设备报告。

在这些门槛通过前，生产 factory 不可构造，consumer/device inference 继续 **unverified**；本地 fake 生命周期与 AAR/package 构建结果不提升这些门槛。已向用户异步询问是否有已授权本地资产；在得到实际路径与授权依据前不启动模型操作。

## 官方来源与读取限制

本轮 researcher 读取了下列 Google-owned Hugging Face API metadata/tree；受 manual gate 和本机失效代理 `127.0.0.1:7897` 影响，没有取得需要认证的模型卡内容或完整内容 hash，也没有稳定刷新官方 Android 文档正文。

- [当前 E2B metadata](https://huggingface.co/api/models/google/gemma-3n-E2B-it-litert-lm)
- [固定 E2B revision 文件清单](https://huggingface.co/api/models/google/gemma-3n-E2B-it-litert-lm/tree/c03b6f60b8da6c5400b6838a2cf26420f80c0a01?recursive=true&expand=true)
- [E4B metadata](https://huggingface.co/api/models/google/gemma-3n-E4B-it-litert-lm)
- [旧 preview metadata](https://huggingface.co/api/models/google/gemma-3n-E2B-it-litert-preview)
- [固定 preview revision 文件清单](https://huggingface.co/api/models/google/gemma-3n-E2B-it-litert-preview/tree/66c93e118deff9961db659f241678e61b847d165?recursive=true&expand=true)
- [LiteRT-LM Android 文档](https://developers.google.com/edge/litert-lm/android)
- [Gemma 条款](https://ai.google.dev/gemma/terms)
