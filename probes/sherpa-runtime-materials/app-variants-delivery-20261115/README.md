# app-variants-delivery-20261115

本目录将仅放置 bounded increment 的小型证据（JSON/日志/fixture 源码）；四个二进制 APK 仅位于本地忽略 artifact 目录 `artifacts/verification/w3-app-variants-20261115/`，不在 probes 中且未提交。对应报告：[W3 App Variants Delivery 证据报告](../../../docs/validation/w3-app-variants-delivery-20261115.md)。

## 内容
- `app-variants-verification.json`
- `consumer-runtime-graphs-verified.json`
- `apk-signature-observations.json`
- `fixture/`

## 解读
- 4 APK 均有 68 notices / 770479B 与 4 ABI 下 16 个 ASR SO。Demo 另含 7 个原始 model/root-license assets 和 16 个历史非 ASR native extras；独立消费者正好 16 SO、0 model assets。
- 归档对比遵循 InfoZIP archive order，聚焦 ASR members 与相关 payload；不等于全部 Demo payload 完整性证明。
- apksigner 只读结果：debug verify 0、release-unsigned verify 1，符合预期 unsigned 状态；签名资产未改。
- 本增量无设备/AiAi/mic 推理、无签名分发、无模型或运行时新下载；既有 429 未重试。

## 状态
Bounded increment accepted；P1/P2/P3/P4/R4/R5/W8/W3 仍开放。
