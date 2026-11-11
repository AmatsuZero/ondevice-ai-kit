# ORT 源码公告与许可证收集说明（2.0.1 / 候选）

本目录为 sherpa-runtime-materials 2.0.1 的 ONNX Runtime 源码公告候选材料。详见 [source-notice-manifest.json](./source-notice-manifest.json)。

## 实际状态

- 收集启动于 2026-11-11，定稿于 2026-11-12。
- 固定 ORT 提交：`33ca9628233dc8f002435e868d4c2e9f82766ca1`。
- 映射源码依赖 13 项；其中 11 个归档 SHA1 与声明一致，并已独立计算 SHA256。
- 已验证归档总字节数：30,858,504。
- `pytorch_cpuinfo` 与 `onnx` 流式归档因超时未验证，未做归档重试；二者 LICENSE 以精确提交 Git Blob 保留（onnx 为当前标签解析，非历史不可变快照）。
- 文本文件合计 26 个：21 个根目录/根 LICENSES 公告 + 3 个 mp11 源码证据（[README](./notices/mp11-source-evidence/README.md)、[mp11.hpp](./notices/mp11-source-evidence/mp11.hpp)、[version.hpp](./notices/mp11-source-evidence/version.hpp)）+ 2 个 Git Blob 许可证。
- mp11 归档没有独立 LICENSE 文件；上述 README/头文件中的 Boost Software License 1.0 声明是包内源码证据，不冒充完整许可文件。

## 范围与限制

- 未提交任何归档或二进制文件；全部代码/部分/原始回执存于 `.omx/artifacts/w3-runtime-materials-20261111/ort-source-archives/`，不保证经 Git 分发。
- 父级校验通过：26 文件 / 11 归档 / 13 映射；68 份旧材料未变更，原始 108/120/327/448 守护不变；原物理 heap 诊断 run 尚未消耗。W3/R4/R5 仍开放。
- 以下标志保持 false：`sourceToAAR`、`finalBinaryMembership`、`productionReady`、`w3Complete`。
- 本说明不构成法律审批，亦不声明完整性。源码归档超时非模型/服务商配额失败。

## 审查说明

源码辅助审查（148.511s）已接受，2 项低严重性来源元数据问题已修复；但范围后续变更，本候选 README 不属于变更后的独立二进制/运行时审批。