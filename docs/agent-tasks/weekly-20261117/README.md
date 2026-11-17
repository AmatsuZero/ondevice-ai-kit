# 本周并行 Agent 派工入口

最新：A/B已交付并本地集成验证，主代理分支 `feat/w9-ab-integration-20261117`；C从 `46c7e4a` 启动，使用[本次完整C Prompt](C-kmp-demo-start.md)。这是准备好的派工文本，用户启动后才能登记running。旧ABC模板保留为历史模板，不重复派A/B；[集成证据](../../validation/w9-ab-integration-20261117.md)。

先读[总计划](../../superpowers/plans/2026-11-17-weekly-delivery-voice-effects.md)和[实际状态](../../validation/weekly-delivery-status-20261117.md)。本文是可复制任务模板，**不表示已经启动助手**。

调度：W9-0 原型经主代理接受、合同已冻结（最终独立复核429无结论）；其后 A/B/C 并行，R 可独立先行；Q 是唯一 native 只读代码/验收证据 reviewer。最多三条外部候选线并行，shared Host/配置/设备与集成验证由主代理独占。

| 任务 | 提示 | 放行条件 |
| --- | --- | --- |
| W9-0 合同/可行性 | [W9-0](W9-0-contract-feasibility.md) | 主代理已接受原型，见验收报告；不重复派发 |
| A/B/C 实现候选 | [平台与Demo任务模板](W9-ABC-implementation.md) | 已实现并本地集成验证；不重派同一实现，按当前交接继续设备/交付接受 |
| R 交付材料 | [R](R-release-materials.md) | 本地清单/限制、消费和Apple分发增量已验；最终实包通知/设备/复审待补，不上传/发布 |

每次派发复制为独立不可变 prompt，注入必需源码/合同摘要和适用 pitfalls 原文；工具可读时要求亲读。输出落隔离目录，记录 prompt/raw/终态/实际文件SHA、工具失败、未测项；实际写沙盒没有证明时使用无工具小候选，不能把 cwd/提示当权限边界。

禁止自动切换付费或绕已知429换同供应商模型；不能因用户要求“几个 Agent”就解除现有 native≤1/只读、新Copilot暂停、逐次付费确认等规则。尚未启动的任务保持 ready/waiting，不能写 running。

当前合同：[contract-v1.md](../../voice-effects/contract-v1.md)，SHA-256 `87f890278a07a457d656970a16ca3001ef034cd05e9001b655eb2b84779b86d2`；[W9-0结果](../../validation/w9-minimal-chain-20261117.md)。新候选可使用已授权本地Ollama，GPU推理串行；这不表示三条本地推理可同时运行，也不代表已知额度线路恢复。
