# Sherpa 2.0.1 CI 工件与发布 AAR 对照

固定官方 run `40348432396`、源码 `af2d8d81dbfa61c4cea4c568da78f3982edca005` 的两个 CI 工件，已与现存官方发布 AAR 做完整字节对照：CI AAR 成员与整份发布 AAR 相同，CI libs 的四 ABI/16 库也与 AAR 的全部 native 成员相同。Python zipfile 和实际 `/usr/bin/unzip` Info-ZIP 6.00 分别读取通过。

- `artifact-metadata.json`：本次 API 原始 JSON，含固定 artifact ID、ZIP 摘要及 run/source 身份。
- `release-asset-metadata.json`：本次官方 Release asset 选定字段，与原本地 AAR 完整摘要一致。
- `observations.json`：完整 AAR、16 成员路径/尺寸/摘要和双工具字节对应结果。
- `verification-source.py.txt`：实际执行的核验源码原字节归档；其 `OUT` 指原执行目录，不是新增的公共命令入口。
- `hy3-source-review.txt`、`hy3-pins-candidate.json`、`parent-disposition.json`：源码复核、固定摘要/Info-ZIP 最小修正候选及父裁定；复核不替代实际执行。

本目录只含文本，不包含 AAR、ZIP、SO 或模型。两个 ZIP、超时 partial、失败 range、最终精确 suffix、原日志及实际执行收据保留在本机 `.omx/artifacts/w3-sherpa-ci-members-20261115/`。不得因材料字节对应而声称递归最终组成、生产通知、发布接受或 W3/R4/R5 完成。详见[验收记录](../../../docs/validation/w3-sherpa-ci-members-20261115.md)。
