# Apple MLXVLM 短视频可行性

2026-10-23。从 `515ec07` 的分发交接继续。**固定模型/runtime 有单视频处理源码，本项目尚无短视频 API、解码或推理通过证据**。本包完成交接指定的本地源码/缓存核对和下一包设计，不更改 SDK、模型、lock 或依赖。roadmap 仍 5/12，P9 active。

## 新鲜离线证据

- `artifacts/P9/verified` 的 14 个文件重新逐一检查大小和 SHA256，全部匹配 `docs/validation/p9-smolvlm-manifest.json`，包括 1,015,023,993 B 的权重。
- 现有 `VLMProbe-gsskvhiuepshlpbhywyqjxgoktbb/SourcePackages/checkouts` 的 12 个 checkout：HEAD 全部匹配 `apple-mlx/Package.resolved`，tracked 修改为空。未解析、更新或下载依赖。
- 固定 model revision `fa57db46815177fbdfd65cc85a2b3416a8332268`；runtime revision `6e338e5df49424fc0db7c6d58184d622f92ed18a`；lock SHA256 `e67e985970770991f1eb2384ca9e9b6267fb8a8f1569051be5cfb2175f0ef858`。
- 本地 `preprocessor_config.json`：`processor_class=SmolVLMProcessor`、`video_sampling.fps=1`、`max_frames=64`、`max_image_size.longest_edge=512`。`processor_config.json` 的 `image_seq_len=64` 是每图 token 配置，不能当视频帧预算。
- `xcrun devicectl list devices` 显示既有 iPhone16ProMax 已连接；仅设备清单检查，未安装、启动或读取其媒体。
- 可复核记录：`artifacts/verification/apple-mlxvlm-short-video-20261023/preflight.json`，SHA256 `135c87c076307df7fbf5c1e02bfb75016bef0cb7b0e417efa7596b0362343186`。含每个资产 hash、每个 checkout HEAD 和四个上游源文件 hash；status=`source-feasibility-only`。

## 源码支持与风险

下表上游路径均相对固定 `mlx-swift-lm` checkout，行号对应上述 revision；可以从缓存读，也可查固定 revision，不能替换为浮动 main 的行为。

| 证据 | 结论 |
| --- | --- |
| `Libraries/MLXLMCommon/UserInput.swift:78–114,317–375` | `Video.Source` 有 `.url`、`.avAsset`、`.frames`；`VideoProcessing(targetFrames:)` 能显式限制采样目标。 |
| `Libraries/MLXVLM/Models/SmolVLM2.swift:371–455` | 单视频、无混合图片的独立分支；生成帧时间提示并以模型配置的 512×512 处理每帧，不是重复单图生成。 |
| `SmolVLM2.swift:403–410` | 默认 FPS 会按时长动态调整，短片不是固定 1 FPS；`max_frames=64` 也不是本项目的移动端资源预算。 |
| `Libraries/MLXVLM/MediaProcessing.swift:342–384,528–581` | URL/AVAsset 采样网格包含 duration 端点；严格零时间容差，失败分支跳过该帧。源码没有显式的逐帧 cancellation check 或 `cancelAllCGImageGeneration` 屏障。仅凭这段源码不能宣称取消后解码已停止。 |
| `MediaProcessing.swift:584–652` | `.frames` 从首尾时间戳推导 duration，并再次按目标网格选择帧；不保证任意时间戳数组原样保留。源 clip duration 与最后采样时间不是同一个指标。 |
| `Libraries/MLXVLM/VLMModelFactory.swift:143–144,572` 起 | 固定 factory 注册 `SmolVLMProcessor` 并加载本地 processor 配置；已有资产包含所需配置，不需要先换模型。 |
| `apple/Sources/ODAIKitMultimodal/MultimodalContracts.swift:4–19`；`android/multimodal/.../MultimodalContracts.kt:8` | 当前公共请求只有 `imageFile`。文件后缀或模型名字不能使它成为视频请求。 |
| `apple-mlx/.../LocalMLXVLMRuntime.swift:17–35` | 当前 backend 只做严格 PNG/JPEG snapshot 和 `UserInput(images:)`。现有 SDK 单图真机结果不能复用为视频结果。 |

## 下一包方案

设计：[有界原生短视频](../superpowers/specs/2026-10-23-apple-mlxvlm-short-video-design.md)。逐 Task 计划：[实施计划](../superpowers/plans/2026-10-23-apple-mlxvlm-short-video.md)。先原生 Swift，之后再独立接 Android 对等契约和 KMP/Demo；不把 Android 的契约实现称为实际后端。

建议保留 `MultimodalRequest(imageFile:)` 的现有语义，新增类型化视频请求及可选 backend 协议，复用同一个 client admission/cancel/drain/close。Apple 先安全复制本地视频、限定 profile、显式至多 8 帧，再交固定视频 processor。不能直接将调用方 URL 交给上游或把视频塞入 `imageFile`。

采样帧的重新选择/提示时长是实现前必须实测的兼容门槛：先以现有 runtime 验证固定网格、帧数、顺序和 timestamp；若未成立，本实现包保留失败证据并停在 native sampler，不通过修改上游 checkout 或伪造时间戳绕过。最终文档同时记录真实 clip duration 与 processor 实际使用的 sampled span。

## 验证边界与恢复

本包为源码与文档工作：没有新产品实现，不运行完整 SDK 测试、build 或推理；历史 tools126/126 与 source build 不称为本轮新跑结果。独立只读代理启动遭 429/retry-limit，未返回分析；本轮后续为当前会话作者核对和自审，无独立 APPROVE。交接规定后续继续当前会话单人执行。

短视频 decode/processor/tokenization/device generation/cancel/reentry/close、自然视频质量、Metal/GPU/energy、最低系统、Android 实际后端均未验。binary archive 的既有 exit65 门槛继续保留，不重跑。无 push/merge/publish、下载、录音、私人媒体读取或签名变更；两个保护文件保持原 hash/state/index。
