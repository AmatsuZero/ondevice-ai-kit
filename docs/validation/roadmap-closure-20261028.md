# Foundation 路线图收尾记录

日期：2026-10-28。用户要求尽快结束当前路线图执行，并将已有问题记录为后续专项。本文冻结当前可复核结果，不把未验证能力改写为通过，也不作为发布批准。本轮执行状态为 `closed-with-followups`；产品验收状态独立保持 `open/not-ready`。

## 收尾边界

- 当前基础里程碑仍为 **10/12**：P0–P3、P5–P10 已达到各自原定停止点；P4 与 P11 保持未完成。
- 本轮验证范围冻结在 iPhone 16 Pro Max / iOS 27.0.1 与 TECNO KM9 / Android 15/API 35。iOS 15 和 Android API 26 设备验收按用户决定延期，不计为通过。
- 当前工作树中受保护的 signing 工程、Face Identity 草稿和相机资源/线程取证文件继续保留，不恢复、不覆盖、不清理。
- P11 的统一结论保持 `open/not-ready`。候选 SDK committed tree 已冻结，但授权模型、生产后端、质量、资源、能耗和部分持续运行证据仍未具备。

## 已冻结证据

P4 已有相机并发、前后台、停止/恢复、照片选择取消、模拟器 EXIF 2/6/7 合成选择，以及页面 owner/bridge 释放观察。最新全范围审查为 0 Critical、2 Important、0 Minor；重要项已完成有界修复或记录为范围外。真机 PHPicker→Vision 的 EXIF 2/6/7 仍未执行，18×GrOp/2304B 的析构/free/TLS 归属也没有证据，因此 P4 不升级为完成。

P11 已保留哈希固定的 readiness、候选 SDK 和三组 grouped benchmark 报告。当前 9 行能力均有 gap：其中 LiteRT-LM 缺授权模型/production factory/native drain，Face Identity 缺生产模型/PAD/校准，相机缺原生 residual 根因，其他能力缺自然质量或可比较 GPU/energy；这些状态继续按 `unverified`、`unavailable-assets` 或 `deferred` 记录。

收尾时只刷新现有 readiness 对已编辑文档的 SHA-256，并同步 `benchmark/p11/grouped-report-manifest.json` 中的 readiness 指纹；未改原始设备 JSON、模型权重或历史候选归档。当前只读校验为 9 行/9 gap、21 条证据引用、19 项来源哈希；同一输入重算的 grouped report 为 3 组、9 行 coverage，发布状态仍 `open/not-ready`。

## 后续专项入口

1. **模型与授权资产**：取得可授权的 LiteRT-LM 权重、Android ASR 离线资源和 Face Identity/PAD 模型后，再执行生产 factory、native drain、真实推理与质量校准。
2. **P4 原生资源**：在有受支持的 destructor/free/TLS 观测工具时继续专项；不通过升级 Compose/Skiko、私有清理或压制 Leaks 关闭问题。
3. **统一质量与资源**：按现有 grouped manifest 为每项实际交付能力补齐自然质量、峰值内存、GPU、能耗、取消和持续运行原始 JSON。
4. **最低系统设备**：获得 iOS 15 与 Android API 26 真机后，按设备矩阵恢复验收。

上述四条是分组，逐项恢复输入、模块责任边界与退出标准见 [F01–F12 专项清单](foundation-followups.md)。该清单覆盖全部9项 readiness 能力、增强 MLX binary 分发和缺失复审，不删除原计划需求。

上述专项完成前，`docs/validation/p11-status-matrix-20261027.md` 的 `open/not-ready` 是当前发布结论。它允许路线图执行阶段收尾，但不表示 SDK/Demo 已达到最终发布门槛。

## 冻结与本轮验证边界

交付源码基点为 `90d528c60726b4fd3f3ce23690dbb6125816fc91`；本收尾提交仅改文档和 benchmark 来源指纹，不新增产品代码，不重跑模型/设备/全量构建。基础 SDK 的历史候选仍是 `7b9f7fc`，不把旧候选说成基点 HEAD 的新二进制。

本轮接续仅确认 iPhone connected、无需锁屏密码；没有安装/启动验证 App、请求 Photos 权限、写入合成资产或访问现有照片。device harness patch 写入失败且未生成文件；随用户新收尾要求停止该实现，F01 保留未执行状态。设备曾授权合成写入的范围仍在 picker 计划中，不构成已写入证据。

当前受保护文件 SHA-256：signing 工程 `b35bd984f784d4b13c0724f8c595d28ed96ddbe418e049547b762309fe9fd8f5`；身份草稿 `6f130bd94debd5e8bffaba7b0db560553f7a367818d9cf37b72468e42bb70822`。相机资源/线程 probe 和生成器作为本地未完成文件保留。
