# W4 既有输出质量裁定 — 候选决策（仅关闭决策缺口）

> 本文件是**候选决策文档**，由子代理在只读约束下产出。范围仅补上"既有输出是否构成已建立 / 失败 / 未验证的保真度收益"这一决策缺口；**不自动关闭 W4**、不构成质量或生产放行。Main 复核 / 整合 / 执行。

## 0. 执行边界（已遵守）
- 仅 Read / Write / Edit；无 shell、网络、MCP、构建、测试、设备、下载、推理、签名、凭据或生产改动；未读取任何私有数据文件；无 W6 工作；未编造人工偏好。
- 未重跑"第二纹理瀑布 48 帧匹配 SR / bicubic"实验（父级已核验存在，原样保留）；未声称执行任何步骤；未使用 / 未声称任何助手时钟截止期。
- 仅写三个自有候选文件；未改任何源文件；未触碰他人编辑。
- 仅读 `context/input-manifest.json` 列出的 8 份核验来源；`docs/agent-pitfalls.md` 实体文件未在供给上下文中出现，适用摘录已在任务内提供并据此执行。
- `productionSDKInference=false`；`qualityVerdict=unverified`。

## 1. 核验来源（manifest 哈希逐字保留，未重算）
完整 8 条路径 + SHA-256 见 `decision.json:verifiedSources`。交叉一致：`plan.json` 的 `productionAlphaHelperSHA256` 与 `AppleVideoOpaqueFrame.swift` 的 manifest 哈希一致（59198e58…）；`summary.json` 内 `fixture.json` / `plan.json` 哈希与 manifest 一致。

## 2. 观测值（第二纹理瀑布，`sr-waterfall-20261107-2220`）
48 帧、4×、sRGB、同 HEVC / BT.709 编码、生产 opaque helper 原样复制。`plan.json` 协议明确"不做像素位移、亮度校正或阈值拟合"。

| 指标（保真度） | SR-sRGB | bicubic-sRGB | SR−bicubic |
| --- | ---: | ---: | ---: |
| PSNR dB | 29.191663 | 30.413137 | -1.221474 |
| SSIM | 0.834227 | 0.864989 | -0.030762 |
| 空间 luma RMSE | 12.604873 | 10.932351 | +1.672522 |
| 时域 luma RMSE | 15.131861 | 12.277824 | +2.854037 |
| 空间 luma bias | -2.287254 | -0.120648 | — |
| 时域 luma bias | -0.039450 | +0.000432 | — |

状态：`measuredFidelityBenefitOverBicubic=false`；四路颜色约定实验已完成；alpha 边界已通过 / 关闭；`humanPlaybackAssessment=not-performed; synchronized playback artifact prepared`；`comparison-playback.html` 已保留。

## 3. 保真度 ≠ 感知（关键区分）
- **保真度指标**：上述 PSNR / SSIM 与空间 / 时域 luma 残差，是解码输出相对参考的**编码输出保真度**，含有损 HEVC 编码与缩放环节，不是主观质量、不是模型固有质量、更不是整包 SDK 验收。
- **感知指标**：预先登记判读口径（亮度保持、细节、运动伪影 / 时域闪烁）的**盲化、原分辨率、连续播放 A/B 偏好**。供给数据中**不存在**，且不得编造。
- 保真度更低**不等于**感知更差：Android 受控对照（`f11-android-sr-controlled-20261106.md`）曾出现 ESRGAN 边缘更锐利但 SSIM 更低。故保真度结论不能代替感知裁定。

## 4. 受支持解释（缺陷 vs 假设）
证据支持的事实：
- 该已完成场景里，SR 在所有保留的保真度指标上均低于匹配 bicubic → 收益主张被本次测量**否定**（bounded 实测输出特征，非生产代码缺陷）。
- SR 存在系统性空间亮度偏置（更暗，bias −2.287）及更高的空间 / 时域 luma 残差（实测）。早前分解（`sr-acceptance-20261107.md`）显示 SR 空间 MSE 约 79.2143% 来自每帧均值残差，但去均值后局部时域残差仍高于 bicubic，故"全局偏暗"不能解释全部差异。
- **无任何由现有证据足以确认的生产 SDK / 实现缺陷**。alpha 边界的既有真实回归（7 条断言失败）已由最小 opaque-alpha1 修复关闭并通过；生产 opaque helper 哈希与实验一致。

仅为假设，不予断言：
- 模型所需传递函数为 linear / sRGB 约定错误（Apple 公开 SR 头未规定传递函数，probe 自记 `colorContract=experimental-AB-v1; model preferred convention unverified`；linear 实验降空间误差却不改善时域）。
- 亮度偏置是可"加亮修正"的 bug（协议禁止亮度校正；去均值后时域残差仍高）。
- 存在 SDK bug 导致差距（`sr-acceptance` 明确"不能由此归因 SDK bug"）。
- 单剪辑 / 单设备 / 固定顺序的差距可外推到广泛场景或整包 SDK。

## 5. 裁定
| 维度 | 裁定 |
| --- | --- |
| 实测保真度收益（本场景） | **FAILED** — 已完成匹配测量中 SR 全面低于 bicubic，`measuredFidelityBenefitOverBicubic=false`；"FAILED" 仅指本次测量否定收益，不是通用模型劣后或产品缺陷判定。 |
| 感知收益 | **UNVERIFIED** — 无盲化连续播放 A/B，未评定且不得编造。 |
| 适用场景 / 广泛质量 | **UNVERIFIED** — 单一有界剪辑。 |
| 生产 SDK 质量 / 部署 | **UNVERIFIED / 阻断**（`productionSDKInference=false`）。 |
| 整体 W4 | **OPEN** — 本交付仅关闭决策缺口。 |

未引入任何 post-hoc 阈值，未改产品准入标准：仍以 `plan.json` 的 `successBoundary`「overall quality requires demonstrated applicable benefit, not mere terminal success」为准，不以"运行成功"或"接近基线"折抵 PASS。

## 6. 唯一最小判别性剩余验收步骤（先用保留输出）
**一步**：先预先登记有界判读口径（亮度保持 / 细节 / 运动伪影与时域闪烁），再对**已保留**的 SR / bicubic / reference 三路输出，用已备好的 `comparison-playback.html` 做盲化、原分辨率、连续播放 A/B，记录一条有界的感知偏好或失败模式结论。
- 依据：22:24 记录明确"下一步根据既有输出判断可感知细节收益 / 失败模式"；`summary.json` 已保留 `comparison-playback.html` 与三路解码输出，`humanPlaybackAssessment=not-performed`。
- 该步**先用保留输出**：无新推理、无新采集、无下载、无设备运行、无模型下载、不重跑实验；本交付不执行、不编造结果，由 Main 执行。

## 7. 必须持续阻断的质量 / 部署主张
1. "SR 已证明相对 bicubic 的可感知 / 适用质量收益并通过产品质量门禁（W4）"。
2. "SR 后端已验证、可用于生产 SDK 推理或发布"（`productionSDKInference=false`、`qualityVerdict=unverified`）。
3. "整包 / 发布验收（F06 / F11、P4 / P11）已达成"——历史摘要仅对应有界记录运行，非当前整包 SDK 验收。
4. 把本 `DECISION` / `decision.json` / `DELIVERY` 文档当作质量或生产放行（新文档不计入质量 / 生产批准）。

## 8. 生产实现是否需改动
**否**。现有证据不支持任何生产代码缺陷，故不触发"精确文件 / 行 + 证据"的修复条件。若后续 Main 经真实缺陷确认才需改动，应以下列位置与证据为准，而非现在就实施推测性修复：
- 约定定义仅在验证探针 `f06-sr-waterfall-20261107-2220/src/SRColorABRunner.swift`（`processingTransfer` / `processingColorSpace` L15 / L18、`makeBuffer` 附件 L261–L265）；`.../src/AppleVideoOpaqueFrame.swift` 为生产 opaque helper，哈希匹配、alpha 边界已过。
- 对传递函数或亮度在生产侧动手，正是被禁止的推测性修复，且协议明确禁止亮度校正与阈值拟合。本交付不做任何源码改动。

## 9. 原始失败与哈希保全
未改任何源；原始失败原样保留（见 `decision.json:rawFailuresPreserved`）：run2130 过严诊断失败、run2134 未控制 alpha 实验、`empty_model_response` / 0 文件、Ollama alpha 回归候选编译错误被拒收、原转换表达式两次回归复现 7 条断言失败。8 条来源哈希逐字保留、未重算（只读，无 shell）。
