# Foundation 后续专项

2026-10-28 用户决定：结束本轮持续执行，将遗留问题转入专项。F01 的 sandbox 子项现已完成，其余原始验收仍是未通过或未验证；编号用于续接，不表示已创建远端 issue。负责范围指模块责任边界，尚未指派人员。专项未完成前，P11 发布结论保持 `not-ready`。

## 当前可执行队列（2026-10-28）

用户进一步明确：先排除目前确有外部卡点的项目，低版本系统真机暂时无法提供。下面只调整执行顺序，**不删除原需求、不改变 P4/P11 或 P11 `open/not-ready` 状态**。每项只认它自己的退出标准，不把可完成的子项当作整个能力通过。

| 优先级 | 现在可做的有界工作 | 为什么可做 / 停止点 |
| --- | --- | --- |
| 已完成子项 | **F01：iPhone 沙盒合成 EXIF2/6/7 →生产 DemoController→Vision** | 真机 3/3，结果与边界见 [P4](p4-camera-stability.md)；真实 PHPicker 仍开放。 |
| 1 | **F04 的 iOS 麦克风生命周期部分** | iOS 后端、Demo 与真机已有基础链路；可先验证真实录音终态、取消、关闭、重入、后台及 lease drain。自然语音 CER/WER、TTS 主观质量和完整离线矩阵另需授权语料/参考文本，不能由合成 fixture 代替。 |
| 2 | **F08 的 Apple 现有模型短视频/Demo 链路** | 固定 MLX 模型、公开 fixture 与现有 iPhone 已有链路；聚焦当前源码的帧/时间语义、真实输出和 UI 终态。自然质量需固定参考答案与评分规则，先记录原始输出，不提前给出通过结论。 |
| 3 | **F06 的 Apple 非零进度取消专项** | 已有原生导出 `waiting→exporting→cancelled` 三轮，但进度均为 0；用现有公开输入和独立 probe 做一次有界尝试，只有观察到非零进度才可验部分文件清理。若仍为 0，记录无区分力并停止重试。Android 生产后端另列技术方案包。 |
| 4 | **F11 的现有能力报告补齐** | 使用上述新原始数据，补按能力/设备/后端的真实报告和缺口；不重新包装旧 3 组报告，不凭报告格式提升质量、GPU 或能耗结论。 |

**暂不进入近期队列的外部卡点**：F03 需要 iOS15/API26 真机；F05 需要设备支持且授权的 Android ASR 离线资源；F07 真实推理需要可授权 LiteRT-LM 权重，native drain 另缺受支持证明；F10 需要生产模型/PAD、授权数据和校准集。真实 PHPicker 也需要将合成资产写入 Photos；当前按用户要求不做这项写入，因此保留为 F01 的独立后续。输入出现后再恢复对应专项。

**独立技术难题，后置且不冒充外部设备卡点**：F02 需要能直接观察析构/free/TLS 的研究构建；F06 的 Android 生产超分需要先确定可运行且可评价的后端；F09 增强 MLX binary archive 有 `OrderedCollections` swiftinterface 问题；F12 完整复审此前遇服务 429，待形成新的候选差异后重试。它们仍是交付要求，只是不占用上述短队列。当前长期 roadmap 目标已按用户选择暂停；此表是恢复时的推荐顺序，不自动启动专项。

| 编号 / 负责范围 | 问题与已有证据 | 恢复输入 / 下一动作 | 专项退出标准 |
| --- | --- | --- | --- |
| F01 / iOS Demo | [P4](p4-camera-stability.md)：真机 sandbox EXIF2/6/7→生产 `useImage`→Vision 已完成；真实 PHPicker→controller 仍未验 | sandbox 子项已完成，证据见 `artifacts/verification/p4-picker-sandbox-device/summary.json`；后续若恢复 PHPicker 专项，才考虑合成资产的 Photos provider 路径；当前不写入 Photos | 三例实际选择同一指定合成资产，尺寸/镜像像素及 `apple.vision.face` 成功结果均可复核；sandbox 结果不替代 PHPicker，且不读取私人照片 |
| F02 / Compose/Skia 资源 | [AtlasTextOp](p4-atlas-text-cache.md)：18×GrOp/2304B 最终 live；页面 owner/bridge 已释放，native 析构/free/TLS 仍未知 | 新建隔离 instrumented Skiko/Skia research build 的设计；无新的观测机制不重跑旧 LLDB/301秒 trace | 匹配 runtime UUID、对象地址、分配/析构/free/TLS 与线程生命周期；修复或有证据的边界裁定，并证明持续重入行为 |
| F03 / 最低系统 | [设备矩阵](device-matrix.md)：iOS15/API26 缺设备 | 取得相应真机后恢复启动、availability、权限、选图/相机、取消、前后台、离线门禁 | 两平台最低系统实际运行报告；编译下限不替代运行验收 |
| F04 / Speech iOS/TTS | [语音](speech-backends.md)、[麦克风计划](../superpowers/plans/2026-10-20-ios-microphone-asr.md)：自然麦克风终态、中文质量/取消重入/后台、TTS 自然质量与完整离线矩阵仍缺 | 受控授权录音和文本/语言任务集，保留准确转写参考及网络观测方法 | 麦克风终态、资源 drain、取消重入/后台、CER/WER、自然 TTS 与离线矩阵逐项报告 |
| F05 / Speech Android | [语音](speech-backends.md)：on-device ASR 返回 MISSING_ASSETS | 提供设备支持且授权的系统离线语音资源 | 真实转写、取消/close/重入、离线与自然质量验收；仍缺资产时明确 unavailable |
| F06 / Video | [超分](video-sr-backends.md)、[导出取消](apple-video-audio.md)：Android 生产后端、自然主观质量/传递函数、非零导出进度部分文件清理缺证据 | Android 后端独立选择/实现；固定自然视频与质量裁定；构造可观测非零进度取消输入 | 两端各自支持 profile 的真实输出/质量、取消与完整文件清理、持续运行报告，不把插值基线当算法效果 |
| F07 / Multimodal Android | [drain 合同](android-litertlm-drain-contract.md)、[VLM](mobile-vlm.md)：LiteRT-LM 0.18.1 缺授权权重、production factory 和 native drain barrier | 固定可授权模型 manifest/hash/revision；取得受支持的 native drain 证明方案 | Kotlin consumer 实际生成、callback/取消/drain/close/重入/lease、质量资源报告；证明前维持 UNPROVEN/unavailable，supportsVideo=false |
| F08 / Multimodal Apple | [图像](apple-mlxvlm-backend.md)、[短视频](apple-mlxvlm-short-video.md)、[Demo](multimodal-video-demo.md)：自然图/短视频质量、完整短视频及触控验收仍缺 | 固定自然任务/参考答案、短视频语义与验收 profile | 保留真实 KMP/SDK 输出、时间/帧语义、自然质量及完整交互报告；synthetic 30/30 不替代自然质量 |
| F09 / Apple MLX 分发 | [分发](apple-mlxvlm-distribution.md)：基础十 XCFramework 不能证明增强 MLX binary；OrderedCollections swiftinterface archive 失败 | 独立处理固定工具链/上游 library-evolution 兼容性，保留当前可搬移 source lane | 增强 release archive、swiftinterface 与搬移 binary consumer 实际构建通过 |
| F10 / Identity/PAD | [身份/活体](p10-face-identity.md)：生产模型、PAD、授权数据、阈值/准确率校准未交付 | 授权模型/数据/使用范围、独立训练/评测集及校准要求 | 1:1、1:N、PAD 指标与阈值、授权撤销/删除和真实设备流程验收；fixture 引擎不提升为生产能力 |
| F11 / P11 质量/资源/性能 | [状态矩阵](p11-status-matrix-20261027.md)、[分组报告](p11-grouped-benchmark.md)：9行均有 gap，仅2能力/3组兼容报告；Face 无自然质量，GPU/energy/持续资源未统一 | 固定每能力任务集/阈值、同构测量方法与设备；Power Profiler 工具崩溃需新方案 | 所有实际交付能力正确性/质量/延迟/峰值/取消/持续运行有原始逐run数据与分组报告；不能可靠测量项保持 null 并明确发布裁定 |
| F12 / 最终复审与候选冻结 | [验收](acceptance.md)：部分限定审查因429未取得结论；完整分支审查/统一模型及fixture冻结尚缺；protected signing不属于可交付修改 | 每专项结束一次有界复审；最后从已提交源码重建实际候选，对 SDK/model/fixture/device 身份冻结 | 未解决 finding 和 review 缺口逐项裁定，所有 P11 原验收通过后才可改变发布结论；合并/tag/发布另受授权边界约束 |

原扩展 SDK/Demo 计划仍保留，以上是执行延期清单，不删除需求。后续按编号启动专项，完成一个关闭一个；本轮收尾后不再自动循环重试这些项目。
