# W3 Qwen 导出供应链核对

六份实际运行文件（987015347B）的 size/SHA256 全部与包内 README 指向的 ModelScope
供应仓库一致。该仓库当前元数据明确声明 `apache-2.0`、基础模型 `Qwen/Qwen3-ASR-0.6B`、
关系 `quantized`。这补强了实际导出产物的来源与许可声明；没有下载或替换任何模型。

| 来源 | 固定证据与边界 |
| --- | --- |
| Sherpa release 资产390698077 | 已有完整包SHA256 `393f8a14e2f5fb96746aaab342997a40641001fbd5bf9592a080a8329178ee96`；本次遍历实际包，只有两份README，未发现LICENSE/NOTICE文件 |
| [ModelScope供应仓库](https://modelscope.cn/models/zengshuishui/Qwen3-ASR-onnx/files) | 当前元数据/完整文件清单已保存；六文件逐项size/SHA一致，实际文件修订分别保留于[收据](w3-qwen-supply-chain-observed-20261110.json)。元数据是可变页面的当次快照，不虚构历史许可声明时间 |
| [导出仓库](https://github.com/Wasser1462/Qwen3-ASR-onnx/tree/0829a577ee741e408e65763dfe01ff4fe75408e9) | 完整非截断tree在commit `0829a577ee741e408e65763dfe01ff4fe75408e9`未发现LICENSE/NOTICE，GitHub license字段为空。README承认大量代码来自QwenLM；本探针不分发导出代码，不把缺文件等同于权重必然无许可 |
| [上游模型卡](https://huggingface.co/Qwen/Qwen3-ASR-0.6B/tree/5eb144179a02acc5e5ba31e748d22b0cf3e303b0) | 原固定模型卡声明Apache-2.0；本次固定tree无LICENSE/NOTICE。另已保存QwenLM源仓库实际Apache-2.0全文，其来源与SHA保留，不将代码许可证自行改称包内权重许可证 |

生产再分发门仍未完成：需要在具体交付物中组装并验收实际模型/runtime的许可全文、归属与转换说明，
核实最终包确实包含这些材料。现有元数据未证明转换使用的精确原始权重revision；不能从匹配的导出
SHA反推出这一点。独立发布复审仍缺；原native429没有产生复审结果，不自动重试。

本机原始HTTP响应、URL、摘要与导出脚本快照：
`.omx/artifacts/w3-model-reselection-20261110/license-chain/`。
这里只裁定已取得的证据，不把供应链调查记为Android加载/质量或W3整体通过。
