# Qwen2-VL 记忆题采样覆盖诊断（2026-11-11）

当前结论：iPhone Metal 上 8/16 帧对照均完成四题，严格成绩均为 **1/4**，原始回答逐项完全相同。实际输入张量从 `[2880,1176]` 增至 `[5760,1176]`，THW.t 从 4 增至 8；增帧确实进入模型，但本批题没有改善。两道桌面变化错题已经采到清晰的变化前后状态，不能用“8 帧没有采到关键状态”单独解释失败。

本轮接续 [24 题真机评估](f08-qwen2vl-perception-test-20261111.md)，固定 Qwen2-VL-2B-Instruct-4bit、原 24 题 manifest 中的全部四道 memory 题。原报告不改。

## 已核查的输入证据

父诊断直接编译并调用上一轮隔离 `MLXVLMVideoSnapshot.swift`，导出原视频的 8 个 CGImage。四组实际采样时间与 iPhone run-2 报告逐项完全相同，帧顺序保持，四次临时 snapshot 均成功删除。这里只是宿主 AVFoundation 帧导出与人工观察，**没有模型推理，也未证明宿主与 iPhone 解码像素逐字节相同**；这些图也不是 Qwen 归一化后的张量。

图中按左至右、上至下排列，索引从 0 开始。JPEG 拼图只供观察；导出的原 PNG、时间与 SHA-256 保留在证据目录。

| 视频 | 原答案 / 正解 | 8 帧中可见的证据 | 裁定 |
| --- | --- | --- | --- |
| video_7915 | B / C | 索引 0–2：水瓶、包装食品、透明杯、盘子；索引 6–7：杯和盘子仍在，水瓶和食品不在 | 变化前后状态已覆盖，高置信；无法把错答全部归于缺帧 |
| video_3094 | C / B | 索引 0–3：白瓶、黄瓶、纸卷；索引 6–7：白瓶消失，黄瓶和纸卷仍在 | 变化前后状态已覆盖，高置信；“黄瓶被加入”与这些状态不符 |
| video_2996 | B / C | 桌面物品依次减少：pouch 在 0→1、dictionary 在 2→3、wallet 在 3→4、marker 在 4→5、cap 在 6→7 间离开；密集预览与此顺序一致 | 状态序列支持正解；若干完整入包动作落在采样间隔内，不能据此证明所有动作都被直接看见 |
| video_7967 | B / B | 两本 notebook、diary、pen 依次离开桌面；保留原来答对的控制题 | 能观察到顺序线索，但单题答对不证明模型掌握了记忆机制 |

证据根：`artifacts/verification/f08-qwen2vl-memory-coverage-20261111/`。入口为 `memory-items.json`、`snapshot-export-receipt.json`、每个视频的 `snapshot.json`、`actual-eight.jpg` 与 `dense-2fps.jpg`。密集预览为 FFmpeg 2fps 诊断输出，不冒充模型的实际 8 帧。

## 下一对照的冻结原则

假设：增加统一采样覆盖可能改善四道 memory 题，但如果两个变化题仍错，单纯缺少前后状态的解释会进一步削弱。实验只改变输入帧数 8→16；两臂各独立新进程，固定模型/权重、runtime、四题及顺序、问题/选项、prompt、temperature=0、maxTokens=32 和严格 trim-only 字母评分。保留原来答对的题，禁止依据答案挑帧、换提示或去标点。

完整原 24 题 manifest 仍须校验通过，再选择全部四道 memory 题。8 帧臂使用原采样算法；16 帧臂按同一算法均匀采样。两臂均给 Qwen 重复采样入口提供 ordinal 时间，报告保留实际物理时间；THW.t 分别为 4/8，不能把 THW.t 当成原始帧数。

诊断保护条件：启动前 thermalState=nominal；10ms 采样若 phys_footprint ≥3.5 GiB、thermalState≥serious 或整轮采样时钟≥180 秒，写独立 stop 收据并退出，不把部分主报告判作完成。这是采样式停止保护，不能保证没有瞬时越线，也不是产品预算。触线、进程消失、报告异常均保留证据并停止该臂，不自动重试。成功运行后双读终态并独立重算评分、对照 prompt/media/hash/frame/THW；随后卸载本次隔离 App，恢复普通 Combined App。

## 真机对照结果

同一 iPhone17,2 / iOS 27.2.1（24B156），固定模型 revision `01af461cdb9574acc09084a0ef94e216e142b085`、MLX Swift 0.32.3 / mlx-swift-lm `6e338e5`。源码 fingerprint `519ca00ea60159c61b0149bfd78ac8dcef3d430c4076c44a4feaeaf434b91ede`。两臂各独立进程，主报告终态双读字节一致，父独立校验均 `evidenceValid=true`；未修改原 24 题报告、模型、生产依赖或评分。

| 视频 | 正解 | 8 帧原文 | 16 帧原文 |
| --- | --- | --- | --- |
| video_7967 | B | B | B |
| video_7915 | C | B | B |
| video_3094 | B | C | C |
| video_2996 | C | B | B |

四行均符合单字母输出合同，三道失败是选错答案。8 帧臂也逐项复现原 24 题报告的四个原始回答。两臂四题的素材 SHA 和 prompt SHA 完全相同，8/16 个实际物理时间与 ordinal processor 时间分别独立核验；每行 packed pixel shape 分别为 `[2880,1176]` / `[5760,1176]`，temporal grid 分别为 4/8。

| 观测指标 | 8 帧 | 16 帧 |
| --- | --- | --- |
| 进程 PID | 2735 | 2737 |
| 正确率 | 1/4 | 1/4 |
| 单题总耗时中位数 | 5.444 s | 10.817 s |
| 四题总耗时之和 | 24.931 s | 43.004 s |
| 采样最大 phys_footprint | 3,049,245,856 B（2.840 GiB） | 3,216,444,480 B（2.996 GiB） |
| 采样最大 resident | 1,459,978,240 B | 1,500,545,024 B |
| MLX peak 观测 | 2,188,996,842 B | 2,008,186,722 B |
| 起止 thermalState | nominal→nominal | nominal→fair |
| 资源样本数 | 2,412 | 3,987 |
| 模型加载 | 1,041.61 ms | 387.08 ms |

总耗时包含素材校验、snapshot 解码、准备及生成。文件缓存并未清空，不能从两次加载时间推断帧数影响加载；allocator/cache 口径也不同，MLX peak 不是进程 phys_footprint。资源均为采样观测，不是真实瞬时峰值或能耗测量。两臂未触发 3.5 GiB/serious/180s 停止条件。

本轮增加统一采样密度使单题中位耗时约为原来的 **1.99 倍**，没有带来这四题的正确率收益。证据进一步指向当前固定 Qwen2-VL 2B 4-bit/运行时/输入方式组合的视觉状态比较与顺序判断仍不可靠；**尚不能唯一归因于原始模型能力、量化转换或运行时实现，也不能外推视频理解普遍不可实现**。这里只有四道已选 train 题，每臂一次，未作跨实现或未量化权重对照。F08 质量和产品集成继续 open，不改默认模型。

更有区分力的下一步是把两道变化题拆为“单帧能否识别相关物品”和“同一前后帧能否比较变化”两个诊断边界，保留原题成绩，避免继续单纯加帧或重复同输入。

## 交付、检查与恢复

全部实现来自独立免费 hy3 候选，父完成审查、原样集成和验收。初稿两处编译错误及“遥测异常仅结束 monitor”的故障已保留 RED；定点修正后，50 项真实纯逻辑检查、四视频×两臂真实 host snapshot、7 个非法帧数拒绝、7 组注入 guard 检查、13 组父报告校验检查及最终签名 iPhoneOS Release 构建通过。Host/stub 检查不冒充模型推理；本节真机结果来自随后实际运行。

`io.ondeviceai.f08candidate2b` 已卸载，普通 `io.ondeviceai.combined.demo` 已重新启动，安装元数据逐项不变；候选 tmp 目录为空；旧 consumer 13 个 Swift 文件、原 manifest、24 题原报告 SHA 保持。保存实际签名 App 后，只删除本轮可重建 DerivedData（名义 0.831 GiB），保留源码、模型、原始报告、失败记录和日志。没有新 native/Copilot/付费助手调用。

关键证据：`comparison.json`、两臂 `terminal-second-read.json` / `assessment.json` / `outcome.json`、`source-freeze.json`、`experiment-frozen.json`、`accepted-app-receipt.json`、`restoration-receipt.json`、`cleanup-receipt.json`。8 帧报告 SHA `7ada5299189d66874ff25fef79c0e07b4d83d444adfffe329e08c2c90962e2e5`；16 帧报告 SHA `adccab1ba8c13fc54a3a1783e6dbe6fa8820c53b075973ceab6943b6d60772e1`。本轮全部 helper 已终态，无待收结果或正在运行的设备推理。
