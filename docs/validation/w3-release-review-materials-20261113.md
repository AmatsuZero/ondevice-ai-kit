# W3 模型与运行时复审材料合并

现有模型、运行时及后续构建补核材料已合并为一个本地文本复审包。免费 hy3 完整交付 5033 B [索引正文](../../probes/speech-release-materials/20261113/REVIEW-INDEX.template.md)，父端核对原始 manifest 后逐字节集成；未改写历史材料。本文只确认材料归集与字节保留，W3、R4/R5、最终生产通知保留及发布复审继续 open。

## 内容与验证

| 已核内容 | 结果 |
| --- | --- |
| 选定许可/声明 | 57 份、703471 B：模型上游代码许可 1、基础运行时 20、实际构建补充 9、ORT 归档根文本 24、mp11 源声明 3 |
| 支撑材料 | 81 份既有来源/构建/原始清单文本；原 Eigen 3.4.0 候选、实际 Sherpa 5.0.1 及独立 ORT Eigen 依赖边均保留，未合并为一种版本结论 |
| 总计 | 141 个包内文件，包含上述 138 文件、复审正文、选材清单及 NOTICE-INDEX.json；无模型、运行时二进制或音频 |
| 复用验收 | `tools.nexus_notice_materials.validate_materials` 实际返回 `pass-bounded`、`validatedMaterialCount=141`；未新增或放宽校验器 |
| 包内保留 | 141 个 ZIP 成员逐字节等于已验文件，无重复名；独立再打包摘要相同；根索引 8 个链接均有实际包内目标 |
| 负向验收 | 独立复制件内同长度单字节修改及删除许可文件均被原校验器拒收；未改来源或通过包 |
| 保护 | 本轮前既有 100 个 dirty/untracked 文件摘要不变；没有新设备、下载、推理或生产变更 |

[选材和映射](../../probes/speech-release-materials/20261113/text-selection.json)记录所有来源路径与摘要；[索引原文](../../probes/speech-release-materials/20261113/NOTICE-INDEX.reference.json)是包内 NOTICE-INDEX.json 的逐字节参考。复审正文的相对链接以**复审包根目录**解析，仓库中的 `.template.md` 用于保留同一原文。

## 首次拒收与修正

父组包首稿自动纳入了原支撑材料中的两份 `.github/workflows` 文件，触发现有校验器对路径组件的限制，实际返回 `ValueError: notice material validation failed`。原失败 collection、索引、脚本和复现收据留在 `.omx/artifacts/w3-release-review-20261113/`，未覆盖。

通过包仅将这两个支撑路径的 `.github` 组件映射为 `github-source`，逐项 `repositoryPath` 保留原路径，文本字节不变。57 份选定许可/声明仍使用原路径。没有删除正确支撑材料、修改许可文本或改校验器迁就父组包。

## 本机交付与复核

包：`artifacts/verification/w3-release-review-20261113/accepted/w3-release-review-texts.zip`，375246 B，SHA256 `d542e194cf2da358efe2c04ab8f404c4c87b9db2157c378c9cdcdae564eb26ba`。

NOTICE-INDEX.json SHA256：`cb8ebb281953bebbbe0f48a256791aaa6012478529fe6d303e053b876a47b97f`。完整结果见[机器收据](w3-release-review-materials-20261113.json)。ZIP 仅本机留存、未上传；本地 collection 可用既有入口重新复核：

```python
from pathlib import Path
from tools.nexus_notice_materials import validate_materials

root = Path("artifacts/verification/w3-release-review-20261113/accepted/collected")
reference = Path("probes/speech-release-materials/20261113/NOTICE-INDEX.reference.json").read_bytes()
print(validate_materials(reference,
    "cb8ebb281953bebbbe0f48a256791aaa6012478529fe6d303e053b876a47b97f",
    "w3-20261113-review", root))
```

## 剩余门

此包是已知来源材料的复审集合，包含历史源码候选及其原始限制，不是最终静态组件清单。它不证明递归最终二进制组成、精确 source-to-AAR、模型转换权重出处或真实生产包通知保留；独立发布复审、TECNO 完整质量/资源与适用 W8 决策仍缺。不会重开已裁定 R1–R3、AiAi、麦克风或已消耗的 FILE/heap 批次。
