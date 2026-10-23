# Apple MLXVLM 短视频可行性

2026-10-23。从 `515ec07` 分发交接继续；源码可行性/设计已提交 `52fc766`，内部snapshot/上游characterization为`eb1287d`。**最新：内部项目侧sampled-video适配器已通过host真实帧与固定tokenizer验证；上游29FPS重采样仍失败，公共短视频 API/设备推理尚未通过**。未改模型/lock/上游依赖。roadmap 仍5/12、P9active。

## 最新续接：项目侧 sampled-video adapter

独立[设计](../superpowers/specs/2026-10-23-apple-mlxvlm-video-adapter-design.md)与[逐Task计划](../superpowers/plans/2026-10-23-apple-mlxvlm-video-adapter.md)。`5be1b57`新增内部`MLXVLMVideoInputAdapter`，`c9ef8ce`加入真实固定tokenizer验证。适配器不调用上游帧选择，而逐一处理全部snapshot帧；复用公开sRGB/Lanczos512/normalize和SmolVLM2MessageGenerator，保留固定视频block/每帧64image tokens/THW。block在真实chat template前装配，不通过imageFile或单图tiles处理。固定config错配、非法时间/帧数/尺寸/媒体标签prompt均fail closed。

- 29FPS实际8帧逐帧pixel身份/顺序保留，timestamps原样为`[0,3584,7168,10752,14848,18432,22016,25600]/14848`；pixels=`[8,512,512,3]`。真实clip duration=2s；sample span=`25600/14848`≈1.724138s，分别返回，未改时间戳或补帧。
- 24FPS适配器完整pixels、tokens及mask与固定上游一致。单帧、非零首时间也保留原始CMTime，sample span=0，prompt duration使用真实clip而非span。模型视频prompt沿用ceil到整秒的H:MM:SS，**没有亚秒文本精度承诺**。
- 真实固定`LocalTokenizerLoader`在macOS离线使用已验证model目录，只读tokenizer/template，不加载权重；2/2、无skip。适配29FPS为8帧/512实际imageIDs（含16边界、8globalIDs），固定上游仍7帧/448imageIDs。旧ByteTokenizer characterization仍单独存在；本项没有把旧测试提升为真实tokenizer证据。
- RED：`task-1-red.log`缺失适配接口；`task-2-red.log`缺失host tokenizer入口，均exit65。首实现测试跨Task返回非SendableLMInput的编译失败保留于`task-1-first-green.log`；测试改为在同一Task内消费，未添加uncheckedSendable。
- 最新完整增强host **30/30、0failures、无skip**；tools **126/126**（显式Ruby3.4.11）；新独立consumer unsigned generic iOS **BUILD SUCCEEDED**，适配器确实参与iOS编译。日志为新ledger目录`.superpowers/sdd/2026-10-23-apple-mlxvlm-video-adapter/`的`task-2-full-host.log`、`task-2-tools.log`、`task-2-ios-build.log`；argv和post-build-checks.json保留。既有AppIntents/orientation与上游warnings未抹掉。
- 使用前14model文件size/hash匹配；构建后12checkout HEAD/clean、原lock及consumer lock均匹配。无需下载、upstream edits或binary archive重跑。两个保护文件hash/state/index保留。

**该内部适配包通过离线input preparation兼容门槛，不等于原上游`.frames`修复、公开API集成、权重生成、设备或质量验收。** 原Task1 profile/iOSdecode仍未齐，原Task2/3继续未开始。下一可独立项为补齐snapshot profile测试和iOS实际decode/cancel；完成后仍须另行验证runtime接线与视频权重推理。作者逐Task/最终自审，独立reviewpending、无APPROVE；仅本地提交，无push/merge/publish。

## Task1 实际实施与阻塞

新增内部 `MLXVLMVideoSnapshot`：fd/O_NOFOLLOW/fstat 有界私有复制（32MiB）、5s/单视频轨/零起点无编辑/H.264/明确BT.709 transfer/尺寸与像素profile、至多512px的8个采样点且不含duration端点。AVURLAsset显式 `AVAssetReferenceRestrictions.forbidAll`；取消请求会取消asset loading和generator，消费剩余generation结果后才进入私有目录清理。同步observer用于copied/decodedFrame诊断，不作为释放屏障。

源文件后续修改不会改变私有副本；close幂等且不删除原路径被重新创建后的foreign文件。该项测试先真实失败（第二次close删除foreign.txt），加锁保护closed状态后通过。首次合法fixture失败也保留：本机FFmpeg general color flags未写出transfer/primaries；经ffprobe与真实AVFoundation format descriptions定位，使用显式x264/x265 color params修正合成fixture，没有放宽产品SDR校验。

### 实际 pinned processor 结果

不是源码字符串检查：macOS host中执行既有固定MLXVLM的 `MediaProcessing.asProcessedSequence` 和 `SmolVLMProcessor.prepare`。使用固定模型的byte-identical `preprocessor_config.json` 和测试用ByteTokenizer；验证真实图像/MLX帧处理及prompt结构，**不验证真实模型tokenizer或权重推理**。

| 合成输入 | native snapshot | 固定 processor 结果 |
| --- | --- | --- |
| 160×90/24FPS/2s | 8帧，actual=0,0.25,...,1.75s | 8帧时间戳/每帧原始pixel身份保留；prepare pixels=`[8,512,512,3]`、8个THW、8个frame提示。真实clip duration=2s，sample span=1.75s，分别记录。 |
| 160×90/29FPS/2s | 8帧，actual time值（timescale14848）=`[0,3584,7168,10752,14848,18432,22016,25600]` | `.frames`重新采样只保留原索引`[0,1,2,3,4,5,7]`，得到7帧。原8帧count与timestamp断言均真实失败。**集成门槛未通过**。 |

原始失败：`.superpowers/sdd/2026-10-23-apple-mlxvlm-short-video/task-1-processor-characterization.log`（exit65，两条断言失败），原断言源码保留为 `processor-eight-frame-gate-red.swift`。仓库中的characterization测试明确断言上述固定版本的已知7帧行为，以检测依赖行为漂移；该测试GREEN不代表8帧集成门槛通过，不称为上游bug已修复。

按设计停止Task2/3，不接公共video API，不修改上游、不伪造timestamps、不把视频塞进imageFile。内部snapshot仍是实验性前置代码，不承诺任意<=5s视频可用；短于8个可解码样本、HDR/offset/reference-container等额外测试及iOS实际解码/取消仍需补齐。

### 本轮验证

- 新snapshot测试8/8：合法8帧/副本稳定/清理、文件类型/symlink、编码字节、duration/codec/dimensions/pixels/多轨、预取消、首frame后主动取消及清理、重复close保留foreign目录、profile常量。
- pinned processor characterization2/2（包含**已知失败边界的断言**）；完整增强host **22/22、0 failures**，日志 `task-1-snapshot-and-characterization-green.log`。
- 测试target只增加已存在的MLXVLM/MLXLMCommon/MLX直接测试依赖与合成资源；未新增package、改lock或基础SDK。所有合成fixture来源/命令/hash见测试Fixtures/README.md；无自然/私人媒体。
- tools完整 **126/126**（显式Ruby3.4.11），日志 `task-1-tools.log`；新生成独立consumer的unsigned generic iOS **BUILD SUCCEEDED**，日志 `task-1-ios-build.log`。固定SourcePackages与三项禁止解析/更新flags保持不变，lock未改。
- `MLXVLMVideoSnapshot.swift`确实进入该iOS编译；没有新设备安装/运行、视频推理/质量/资源/最低系统验收。该内部snapshot/characterization不是增强binary分发证据。

## 前置源码核对的离线证据（52fc766）

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

最初52fc766为源码与文档工作，没有产品实现或新build/推理。后续Task1的实际host执行结果见顶部，历史tools126/126和source build不充作该Task的新跑结果。独立只读代理启动遭429/retry-limit，未返回分析；后续为当前会话作者核对和自审，无独立APPROVE。继续当前会话单人执行。

仅顶部明确列出的host decode/processor范围有新证据；真实模型tokenization、device generation/cancel/reentry/close、自然视频质量、Metal/GPU/energy、最低系统、Android实际后端仍未验。binary archive既有exit65门槛继续保留，不重跑。无push/merge/publish、下载、录音、私人媒体读取或签名变更；两个保护文件保持原hash/state/index。
