# W3 审查物料合并索引（非生产就绪）

本文件是 W3 Qwen3-ASR 模型物料与 Sherpa 运行时通告文本的**合并审查物料索引**，仅用于资料归集与后续人工核验，**不代表生产就绪、发布批准或设备验收**。本索引随本地纯文本审查 ZIP 一并交付，置于包根目录 `REVIEW-INDEX.md`；其字节可在不触发生产审批的前提下独立核对。

## 归档指纹（取自 selection-context.json）

- 模型归档完整包 SHA256：`393f8a14e2f5fb96746aaab342997a40641001fbd5bf9592a080a8329178ee96`
- 运行时 AAR SHA256：`a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`

上述两值仅标识既有归档/产物身份，不证明递归静态组成、源码到精确构件的同一性或最终生产通知保留。

## 已选文本清单（五类，共 57 份，703471 字节）

| 类别 | 数量 | 包内相对路径示例 |
| --- | --- | --- |
| 上游模型代码许可 | 1 | `probes/qwen-model-materials/393f8a14/licenses/Apache-2.0.txt` |
| 基座运行时源码通告 | 20 | `probes/sherpa-runtime-materials/2.0.1-a8d09d1d/licenses/`（onnxruntime、eigen、kissfft 等） |
| 实测构建补充源码通告 | 9 | `probes/sherpa-runtime-materials/2.0.1-build-observed-20261111/licenses/`（eigen-5.0.1、websocketpp、asio） |
| ORT 归档根级通告 | 24 | `probes/sherpa-runtime-materials/2.0.1-ort-source-notices-20261111/notices/` 与 `.../2.0.1-ort-source-archives-complete-20261112/notices/` |
| mp11 源码声明文件 | 3 | `probes/sherpa-runtime-materials/2.0.1-ort-source-notices-20261111/notices/mp11-source-evidence/`（README.md、mp11.hpp、version.hpp） |

五类合计 **57 份文本，703471 字节**，全部按各 `sourceManifest` 声明的仓库相对路径保留于本包内；本索引中的链接均使用同一包根相对路径，不引用本地绝对路径或既有文件外的虚构文件。

## Eigen 依赖说明

实际 Sherpa 官方构建 job 选中的是 **Eigen 5.0.1**（四 ABI 均下载/configure 5.0.1，且固定 `cmake/eigen.cmake` 显式指定 5.0.1 与归档 SHA256）。基座运行时物料索引中保留的 **Eigen 3.4.0** 仅为其时 kaldi-decoder 递归源码候选的一条历史证据，不代表父工程实际选中版本；嵌套 ORT 内的 Eigen 是另一独立的精确固定依赖边。本索引保留上述历史文本，但**不就最终二进制组成作出任何结论**。

## 模型许可与未知项

模型 Apache-2.0 全文取自上游 **QwenLM 代码仓库**许可文件副本，模型卡与供应方均声明 `Apache-2.0`。但原始 Sherpa 归档与已检查的固定导出 tree **均未发现 LICENSE/NOTICE 文件**；**转换所用的精确原始权重 revision 仍未知**，且不得据此在归档/tree 之外声称穷尽缺失，亦不得臆造版权方或额外声明。

## mp11 与 ORT 根级文本

mp11 被检查归档中**没有独立 LICENSE 文件**，保留的 README/头文件承载 BSL-1.0 声明；ORT 根级 LICENSES 文本与全部源码替代文本保持完整。须知：**许可文本存在并不证明代码被链接，也不自动产生覆盖性新产品义务**。

## 仍待验收的确证缺口

以下证据仍属未接受：穷尽式递归/最终二进制组成；源码到精确 AAR 及模型转换的出处限度；实际生产包通知保留；独立发布复审；完整 TECNO 质量/资源与适用的 W8 资源决策。本索引**不重新开启 R1–R3、AiAi、麦克风、资源下载、旧 FILE/heap 运行或既往仿真测试**。

## 本地审查 ZIP 的边界

本地纯文本审查 ZIP 仅为便利候选，不含任何模型/运行时二进制或测试音频；其字节可在无生产审批下核对，但**不据此确立任何实际最终包通知保留**，亦不证明发布接受。

## 关联清单与文档链接

- 物料/构建/ORT 清单：
  - [probes/qwen-model-materials/393f8a14/manifest.json](probes/qwen-model-materials/393f8a14/manifest.json)
  - [probes/sherpa-runtime-materials/2.0.1-a8d09d1d/material-manifest.json](probes/sherpa-runtime-materials/2.0.1-a8d09d1d/material-manifest.json)
  - [probes/sherpa-runtime-materials/2.0.1-build-observed-20261111/material-manifest.json](probes/sherpa-runtime-materials/2.0.1-build-observed-20261111/material-manifest.json)
  - [probes/sherpa-runtime-materials/2.0.1-ort-source-archives-complete-20261112/complete-manifest.json](probes/sherpa-runtime-materials/2.0.1-ort-source-archives-complete-20261112/complete-manifest.json)
- 三份出处/父验收文档：
  - [docs/validation/w3-runtime-official-provenance-20261111.md](docs/validation/w3-runtime-official-provenance-20261111.md)
  - [docs/validation/w3-runtime-ort-parent-assessment-20261111.md](docs/validation/w3-runtime-ort-parent-assessment-20261111.md)
  - [docs/validation/w3-runtime-build-dependency-reconciliation-20261111.md](docs/validation/w3-runtime-build-dependency-reconciliation-20261111.md)
- 未来索引（主代理接受本文后生成）：[NOTICE-INDEX.json](NOTICE-INDEX.json)
