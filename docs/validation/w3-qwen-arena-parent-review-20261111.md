# W3 arena 父端工具复核与修正裁定

独立免费 hy3 完成父端设备路由、评分与拟定合同的只读复核，334.102s 自然结束；原报告 8657B，SHA `1c3d28dfba5840335c67349e430a527ef61bd5e6e1792266bfa173e85bbcfead`。[原文](w3-qwen-arena-parent-review-original-20261111.md)与错误事件保留，22 份上下文摘要保持；25 Read/1 Write。助手未执行检查或设备操作。

父裁定接受其主要发现，完成一次定点修正与回归，不再重复派发同一复核：

| 发现 | 裁定与证明 |
| --- | --- |
| I1：essential hashes 覆盖不足 | 复核将部分间接绑定说成未绑定：构建源码已受 candidate receipt/sourceFiles 与 APK 摘要约束；源码复核 JSON 也已必需。仍接受直接缺少基线 check_parent、新 wrapper/policy 与共享 Activity 的问题。先以一份实际可通过的合成合同为基线，逐一删四项，实际四个 FAIL；加入必需集合后全部拒绝，完整合同仍通过。冻结时另绑定全部实际构建源/资产与原复核原文/收据。 |
| M1：政策收据缺失直接抛异常 | 提升为同 run 诊断恢复问题：实际入口 RED FileNotFoundError 后，改为结构化 execution/quality=false、精确次数未知，提示续取同 run；未改变质量阈值。 |
| M2：排他写入与续取 | 保留原件排他写入；新增独立 `collect-policy`，仅在完整原 collection、双读/hash/run/source/六模型收据及当前安装包/设备报告摘要一致后，只读续取政策，不重跑识别或 baseline collect。新增评分输出参数只允许新的安全 basename，原失败输出字节必须保持。缺恢复入口/输出参数的 RED 均保留后 GREEN。 |
| M3/M4 | canonical alias/currentUser0 假定、同 UID TOCTOU、采样非 true peak、native effect 未证、annotation-negative 无人审以及生产许可/发布门按协议保留；无扩展操作。 |

最终父端 22 项测试全部通过，原 FILE 父端 8 项、metrics 31 项再次通过；全部 synthetic/host，零设备/native 调用。修正源码摘要与完整输出保存在 `.omx/artifacts/w3-qwen-arena-wrapper-20261111/parent-review-fixed-green/`；[机器收据](w3-qwen-arena-parent-review-20261111.json)绑定最终父工具。源码/API 的允许范围没有扩大到新的 probe 实现；外部 wrapper/policy 与 APK 均保持原字节，无重建。

据此允许形成一个完整、实际摘要绑定的新诊断合同；尚无新设备结果。冻结、设备 fresh gate 与唯一 run 是后续独立动作，不能由本报告宣称已发生。W3 保持 open。
