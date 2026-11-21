# OnDevice AI Kit 双端项目月报（2026 年 10 月）

> 面向：项目负责人、架构与产品评审
>
> 更新时间：2026-10-30
>
> 当前代码基点：`feat/sdk-foundation` / `0309a0c0ebf05ee60cbd3507c8c1f03c8ca29cf5`
>
> 结论口径：本页是月度进展汇报，不替代最终发布验收。基础路线图已收尾为 `closed-with-followups`；产品发布门禁仍为 `open/not-ready`。

## 0. 摘要

### 0.1 项目目标与范围

OnDevice AI Kit 为 iOS / Android 提供可复用的端侧 AI SDK、KMP 桥接协议和 Demo 宿主。当前覆盖人脸检测、身份与活体契约、语音、视频超分、Apple 多模态 VLM，以及模型存储、取消/关闭、输出 lease 和 benchmark 证据工具。

本月继续按“统一任务模型、分平台 backend、宿主按需接入”的路线推进。iOS 主要验证 Apple Vision / Speech / VideoToolbox / MLXVLM；Android 主要验证 ML Kit、系统语音和可选 LiteRT-LM 接口。最低系统真机（iOS 15、Android API 26）按已确认的设备缺口暂缓。

### 0.2 本月结论

1. **基础路线图：10/12**。P0–P3、P5–P10 已达到各自阶段停止点；P4 Demo 交互闭环与 P11 统一质量/资源验收仍开放。
2. **现代设备链路已形成可复核证据**。iPhone 16 Pro Max / iOS 27.0.1 与 TECNO KM9 / Android 15/API 35 上，已完成多项 SDK、Demo、分发和生命周期子项。
3. **iOS Apple 多模态已从单图扩展到视频和真实 Files provider**。自然视频生成、Home 往返取消、前台重入、固定单素材 5 次语义 rubric 均有原始报告；temporal exact-label 任务仍为 `observed-failure`。
4. **F09 源码 consumer 的当前图像/视频 archive 已通过**，但增强 XCFramework、device/simulator slices、20 个本地 Swift 模块的传递边界和独立 binary consumer 尚未完成，因此不能宣称二进制发布完成。
5. **最终发布结论保持 `not-ready`**。主要原因是授权模型和生产后端缺口、最低系统设备缺口、P4 原生资源残留、自然质量/GPU/energy/持续资源尚未形成统一可比报告，以及最终复审仍有缺口。

### 0.3 本月完成的工作

- F08 Apple MLXVLM：真实 Files provider、Home 后台取消与前台重入、native request/backend/drain provenance、固定自然视频 5 次语义 rubric、静止帧语义边界与缓存归因记录。
- F11 资源补测：iOS TTS、文件 ASR、生产 SDK ASR consumer、Video SR 的逐 run 延迟与采样 RSS；所有结果保留原始 JSON、来源清单和哈希。
- F09 分发诊断：当前源码 source consumer 的 unsigned Release library-evolution App archive 通过；补齐公共 `MLXVLMGenerationEvent` 未知 case 的 Swift 6.4 兼容处理。
- 分组 benchmark/readiness 工具持续保持确定性校验；只在输入、设备、后端、模型和测量方法一致时合并样本。
- 已将 F01–F12 建立为 GitHub/GitLab 对齐的 milestone 与跟踪 issue；父 issue 保持开放，直到各专项完整退出标准通过。

## 1. 业务与数据流

宿主负责输入来源、权限、下载/选片、业务编排、输出入库和分享；SDK 负责对已可读的本地输入执行能力调用、后端选择、取消/关闭、lease 和结构化结果。

```text
本地文件 / 系统 Files / 相机 / 录音
        ↓
宿主权限与输入编排、模型/资源准备
        ↓
统一 SDK request + capability preflight
        ↓
iOS / Android backend（显式选择，不静默换路）
        ↓
progress / result / cancellation drain / output lease
        ↓
宿主预览、入库、分享；原始 JSON 进入 readiness 与 grouped report
```

Demo 同时保留多个 backend 只用于对照和验证，正式 App 是否打包某个 backend 仍由产品规格、包体、许可和设备支持决定。

## 2. 双端交付与证据

| 能力 | iOS 当前证据 | Android 当前证据 | 月末状态 |
| --- | --- | --- | --- |
| 人脸检测 | Vision 真机闭环、XCFramework/consumer、benchmark | ML Kit 真机与 Maven consumer | `pass-bounded`；最低系统、质量阈值、峰值资源未闭合 |
| TTS | Apple Speech 真机 5/5；p50/p95 约 2779.727/2779.792 ms | 系统 TTS 基础链路与分发 | `pass-bounded`；自然质量、完整离线矩阵未闭合 |
| ASR 文件 | 强制 on-device 文件识别；生产 SDK consumer 5/5 | on-device 资源返回 `MISSING_ASSETS` | iOS `pass-bounded`；Android `unavailable-assets` |
| ASR 麦克风 | 契约、capture、bridge、host 已实现 | 仅有权限与模块边界 | 真实录音终态、取消/重入和自然 CER/WER 未闭合 |
| Video SR | VideoToolbox 固定 profile；48 帧输出、取消清理、同进程重入；SR p50/p95 约 93.408/94.587 ms | 生产后端未形成可评价证据 | iOS `pass-bounded`；Android 与质量/资源未闭合 |
| Apple MLXVLM | 图像/视频 native consumer、真实 Files、Home 往返、前台重入、5 次语义 rubric | — | `pass-bounded`；temporal exact-label 0/3、跨场景质量/GPU/energy 未闭合 |
| Android LiteRT-LM | — | contract、ModelStore lease/drain 门禁和明确 unavailable 路径 | `unverified-production-gate-closed`；授权权重、factory、native drain 缺失 |
| Identity / PAD | 生产契约、加密存储、1:1/1:N、活体会话、consumer | 对应 bridge、Demo 与 consumer | `unverified-production-model`；生产模型/PAD/授权数据/校准缺失 |

### 2.1 代表性真机证据

- **Apple MLXVLM**：iPhone 16 Pro Max / iOS 27.0.1，公开 Common swifts 视频经真实 Files provider 选择，连续运行成功；Home 后取消后不自动重启，回前台可重入。固定单素材语义 rubric 为 5/5，但 motion/control temporal exact-label 为 0/3，结论保留为 `observed-failure`。
- **iOS ASR SDK**：固定合成 CAF，生产 `SpeechRecognitionClient` 路径预热后 5/5 成功，p50/p95 为 76.484/93.436 ms；该数据不代表自然语音 CER/WER 或麦克风路径。
- **iOS Video SR**：48 帧 / 24 fps / 1280×720 输出，SR 与 bicubic 均通过结构检查；SR 采样 RSS 峰值增量约 127.5 MB。单一素材的 PSNR/SSIM 仅作边界数据，质量裁定仍为 `unverified`。
- **Android**：API 35 相机、picker、Kotlin 选择器、模块编译和部分 consumer 已验证；LiteRT-LM 真实模型推理等待可授权权重与受支持的 native drain 方案。

## 3. SDK 与 Demo 架构

| 层级 | 责任 |
| --- | --- |
| Common contract / KMP bridge | request、capability、progress、result、cancel、close、lease、错误和状态协议 |
| iOS native products | Core、ModelStore、Face、Speech、Video、Multimodal 及 Apple backend products |
| Android native modules | sdk-core、model-store、face、speech、video、multimodal 及系统/ML Kit backend |
| Host / Demo | 权限、系统选择器、输入 staging、模型准备、页面状态、预览/分享和可操作反馈 |
| Evidence tools | 原始逐 run JSON、来源哈希、readiness 校验、分组 benchmark 与发布状态矩阵 |

后端选择分为运行时选择和构建时依赖。公共契约保持平台无关；后端失败时返回明确结果，不把另一条路线的输出冒充为原定结果。

## 4. 里程碑与当前进度

### 4.1 基础路线图

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| P0–P3 | 完成 | 快照、生命周期、ModelStore、iOS 最小真机闭环 |
| P4 | 部分完成 | 双端相机/选择器/前后台等已有证据；真实 PHPicker EXIF、原生 residual、最低系统仍开放 |
| P5–P10 | 完成阶段停止点 | benchmark、语音、视频、VLM、身份契约与本地分发已有阶段性证据；生产模型、自然质量和扩展设备验收仍在后续专项 |
| P11 | 未完成 | 9 行能力均存在 gap，统一发布结论为 `open/not-ready` |

### 4.2 F01–F12 后续专项

GitHub 和 GitLab 使用同名 milestone 与跟踪 issue：

`F01 Picker/EXIF` · `F02 Compose/Skia 资源` · `F03 最低系统` · `F04 iOS Speech/TTS` · `F05 Android ASR` · `F06 Video SR/Export` · `F07 Android LiteRT-LM` · `F08 Apple MLXVLM` · `F09 Apple MLX Distribution` · `F10 Identity/PAD` · `F11 P11 Quality/Resources` · `F12 Final Review/Freeze`

已完成的是专项内的有界子项，不代表父专项关闭。当前优先级是：可直接推进的 F04 生命周期、F08 后续质量与 F11 报告收口；F03、F05、F07、F10 等等待设备或授权资产的专项保留为 `deferred/unverified`。

## 5. 风险与外部依赖

| 风险/依赖 | 当前判断 | 影响 |
| --- | --- | --- |
| 最低系统设备 | iOS 15 与 Android API 26 真机暂缺 | 不能以现代设备或模拟器替代最低系统发布验收 |
| LiteRT-LM 模型与 native drain | 未取得可授权权重、production factory 和受支持 drain 证明 | Android 多模态无法升级为生产通过 |
| Identity/PAD 生产资产 | 模型、PAD、授权数据和校准集未交付 | 身份能力保持设计/契约级，不得宣称生产识别或活体通过 |
| Apple MLX 二进制分发 | source archive 已通过；增强 XCFramework 和独立 binary consumer 未通过 | F09 和正式分发仍开放 |
| P4 原生资源 | AtlasTextOp 的 18×GrOp/2304B 归属与析构/free/TLS 未闭合 | 相机 Demo 资源验收保持未完成 |
| 统一质量与资源 | 自然质量、GPU peak、energy、持续 RSS 尚无同构全量报告 | P11 无法形成发布签字材料 |
| 审查与候选冻结 | 部分 reviewer 服务曾返回 429；受保护签名工程不在自动修改范围 | F12 需要新的有界复审与候选重建 |

## 6. 11 月推进建议

1. 先完成 F04 iOS 麦克风生命周期：真实录音终态、取消、close、重入、后台和 lease drain，使用受控授权语料保留 CER/WER 边界。
2. 继续 F08/F11：把已有 Apple 视频、ASR、TTS 的逐 run 数据整理成按设备/系统/backend 的质量、延迟、资源报告；不把单素材结果升级为通用质量结论。
3. 为 F09 单独建立 binary packaging design，明确 device/simulator slices、20 个本地 Swift 模块、C module map、模型 JSON、Metal bundle 和独立 consumer 的边界，再决定是否进入实现。
4. 设备或授权资产到位后恢复 F03、F05、F07、F10；在此之前保持 `deferred/unverified`，不以模拟器或编译结果替代真机/真实模型证据。
5. 所有专项完成后执行 F12：从 committed tree 重建候选，统一复审未解决 finding，并重新生成 P11 一页发布裁定。

## 7. 证据入口

- 路线图与阶段口径：[ROADMAP](https://github.com/AmatsuZero/ondevice-ai-kit/blob/feat/sdk-foundation/docs/ROADMAP.md)
- 后续专项：[foundation-followups.md](https://github.com/AmatsuZero/ondevice-ai-kit/blob/feat/sdk-foundation/docs/validation/foundation-followups.md)
- 双端 milestone/issue 映射：[foundation-tracking.md](https://github.com/AmatsuZero/ondevice-ai-kit/blob/feat/sdk-foundation/docs/validation/foundation-tracking.md)
- P11 状态矩阵：[p11-status-matrix-20261027.md](https://github.com/AmatsuZero/ondevice-ai-kit/blob/feat/sdk-foundation/docs/validation/p11-status-matrix-20261027.md)
- Apple MLXVLM 与自然视频：[multimodal-video-demo.md](https://github.com/AmatsuZero/ondevice-ai-kit/blob/feat/sdk-foundation/docs/validation/multimodal-video-demo.md)
- Apple MLX 分发：[apple-mlxvlm-distribution.md](https://github.com/AmatsuZero/ondevice-ai-kit/blob/feat/sdk-foundation/docs/validation/apple-mlxvlm-distribution.md)
- 发布验收总览：[acceptance.md](https://github.com/AmatsuZero/ondevice-ai-kit/blob/feat/sdk-foundation/docs/validation/acceptance.md)

GitLab 镜像仓库：<https://clients-gitlab.ugreeniot.com/home/common/ondevice-ai-kit/-/tree/feat%2Fsdk-foundation/docs/validation>
