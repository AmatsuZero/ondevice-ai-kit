# Qwen3-ASR-0.6B 导出模型物料归属与许可说明（MODEL MATERIALS）

本文件为一份**事实归属文档**，仅基于所提供的上下文证据编写，具体适用性和生产交付仍须另行验收。

## 1. 范围声明（Scope）

本地的 **MODEL MATERIALS** 物料包是一组单独汇编的归属/许可说明与证据指针，对应导出的 Qwen3-ASR ONNX 模型工件。

- 它**不是**模型二进制，也**不是**运行时包。
- 它**不是**生产再分发批准、运行时传递性许可批准、设备验收或 W3 完成。
- 本物料包**不包含**任何模型二进制、运行时库或测试音频。
- 本物料包**不分发**任何导出代码。
- 运行时打包与独立发布复审仍是独立的关卡（separate gates）。

## 2. 模型基础与供应来源（Model base & supplier）

- 基础模型（model base）：`Qwen/Qwen3-ASR-0.6B`
- ModelScope 供应仓库（supplier）：`zengshuishui/Qwen3-ASR-onnx`
  - 仓库页面：https://modelscope.cn/models/zengshuishui/Qwen3-ASR-onnx/files
- 供应方声明的关系（base relation）：`quantized`
- 供应方声明的许可：`apache-2.0`
- 供应方元数据为可变页面的当次快照（mutable snapshot），不虚构历史许可声明时间。

## 3. 工件描述（Artifact description）

所提供的工件描述为 **ONNX / int8 / quantized**。

- 据此描述记录工件形态，**但不声称**已证明特定的导出 commit 产生了这些精确字节。

## 4. Sherpa 发布归档（Sherpa release archive）

- 资产（asset）：`asset390698077`
- 完整包 SHA256：`393f8a14e2f5fb96746aaab342997a40641001fbd5bf9592a080a8329178ee96`
- 对实际包的遍历结果：仅发现两份 README，**未发现 LICENSE/NOTICE 文件**。

## 5. 导出仓库（Export repository）

- 仓库：https://github.com/Wasser1462/Qwen3-ASR-onnx
- 精确 commit：`0829a577ee741e408e65763dfe01ff4fe75408e9`
- 该 commit 下固定、非截断的 tree：**未发现 LICENSE/NOTICE**，GitHub license 字段为空。
- README 中的归属（按原文保留，未臆造版权方或额外声明）：
  - 作者（author）：https://github.com/Wasser1462
  - 导出脚本位于 https://github.com/Wasser1462/Qwen3-ASR-onnx
  - 致谢（Acknowledgements）："Thanks to the QwenLM team. A large portion of the code is from https://github.com/QwenLM/Qwen3-ASR"
  - 关联资料：https://qwen.ai/blog?id=qwen3asr 与 https://github.com/QwenLM/Qwen3-ASR

## 6. 六份原始运行文件（Exact six runtime files）

以下 size/SHA256 来自所提供的 `supply-observed.json`，与包内 README 指向的 ModelScope 供应仓库逐项一致。运行时总字节 `987015347 B`，6/6 文件匹配供应方 size 与 SHA256。

| runtimePath | bytes | sha256 | supplierPath | supplierFileRevision |
| --- | --- | --- | --- | --- |
| conv_frontend.onnx | 44148281 | d22dc4423e0940e49884e903d2ea2f7e5567c14fc1aed97e4e26d6b8f208ef9e | model_0.6B/conv_frontend.onnx | c69fb1666ccb59a82c09840c511a6c894e6a2482 |
| decoder.int8.onnx | 755914231 | 4f6885be5959ae26af3089d38ee7972c5fafbeeb1cf8d5e76eab6d8b61ca5771 | model_0.6B/decoder.int8.onnx | de9e449eb376dcb472c1ce8141fbae8d524fabbd |
| encoder.int8.onnx | 182491662 | 60748d3e6744a57c9c91e1b17424a6c2990567e8adceb0783940c03ed98fa9d9 | model_0.6B/encoder.int8.onnx | c69fb1666ccb59a82c09840c511a6c894e6a2482 |
| tokenizer/merges.txt | 1671853 | 8831e4f1a044471340f7c0a83d7bd71306a5b867e95fd870f74d0c5308a904d5 | tokenizer/merges.txt | c69fb1666ccb59a82c09840c511a6c894e6a2482 |
| tokenizer/tokenizer_config.json | 12487 | 4942d005604266809309cabc9f4e9cb89ce855d59b14681fdc0e1cc62ea26c4c | tokenizer/tokenizer_config.json | c69fb1666ccb59a82c09840c511a6c894e6a2482 |
| tokenizer/vocab.json | 2776833 | ca10d7e9fb3ed18575dd1e277a2579c16d108e32f27439684afa0e10b1440910 | tokenizer/vocab.json | c69fb1666ccb59a82c09840c511a6c894e6a2482 |

- **本项目未改动这六份二进制字节。**

## 7. 许可声明（License declarations）

- 模型卡声明（model card）：`Apache-2.0`
  - 来源：https://huggingface.co/Qwen/Qwen3-ASR-0.6B/raw/5eb144179a02acc5e5ba31e748d22b0cf3e303b0/README.md
  - sha256：`5058416891bc47a2051557765997e8c42f8eb78a0e33c3e775bd17d4b0ba4d50`，bytes：`57456`，`licenseDeclared: Apache-2.0`
- 供应方声明（supplier）：`apache-2.0`

> 模型的 Apache-2.0 声明来自所提供的模型卡和供应方元数据；许可证全文另取自上游 QwenLM 代码仓库。供应方声明本身并不证明转换所用的精确原始权重 revision 或所有转换细节。

## 8. 许可证全文来源（Full license text source）

- 源 URL：https://raw.githubusercontent.com/QwenLM/Qwen3-ASR/7c6daf77a2421100f5fb066495372c00129d39ff/LICENSE
- 仓库 commit：`7c6daf77a2421100f5fb066495372c00129d39ff`
- bytes：`11343`
- sha256：`a44a6081c73ad75f0255bb2bb5cab74ef1829565a895a24e53a4f11290ab7655`

> **该许可证全文复制自上游 QwenLM 代码仓库（upstream repository license），并非模型工件内部内容的额外证据。请勿将源代码许可证文件表述为在下载的权重内部发现。** 本物料包的 `licenses/Apache-2.0.txt` 为来自该收据的未修改副本。

## 9. 边界与未知（Boundaries & unknowns）

- 原始包（Sherpa 归档）**无 LICENSE/NOTICE 文件**；固定导出 tree **无 LICENSE/NOTICE**。
- 供应方声明支持模型谱系（lineage），但**不**自行证明转换所用的精确原始权重 revision 或所有转换细节。
- **转换所使用的精确原始权重 revision 未知。** 现有元数据未证明转换使用的精确原始权重 revision；不能从匹配的导出 SHA 反推出这一点。
- **不**在固定观察 tree/归档之外声称穷尽的 NOTICE 缺失。
- 本物料包**不分发**任何导出代码。
- 运行时打包与独立发布复审仍是独立关卡。本文件**不是**生产再分发批准、运行时传递性许可批准、设备验收或 W3 完成。

## 10. 物料包内容（Materials bundle contents）

- 不含模型二进制、运行时库或测试音频。
- `licenses/Apache-2.0.txt`：未修改副本，来自 QwenLM 收据。
- `manifest.json`：含六份原始运行时文件名/size/SHA。
- `provenance/`：所提供的证据来源（见下）。

## 11. 关联证据（Provenance links）

- [licenses/Apache-2.0.txt](licenses/Apache-2.0.txt)
- [manifest.json](manifest.json)
- [provenance/supply-observed.json](provenance/supply-observed.json)
- [provenance/upstream-license-receipt.json](provenance/upstream-license-receipt.json)
- [provenance/model-card-receipt.json](provenance/model-card-receipt.json)
- [provenance/model-card-header.txt](provenance/model-card-header.txt)
- [provenance/archive-README.md](provenance/archive-README.md)
- [provenance/export-README.md](provenance/export-README.md)
