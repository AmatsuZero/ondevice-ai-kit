# Apple MLXVLM 短视频可行性

2026-10-23。固定模型/runtime的项目侧8帧adapter、Swift typed video client与native runtime现已完成，并有固定合成视频真机生成/取消/同PID重入/close证据。原上游二次采样8→7失败仍保留；自然质量/资源/最低系统/分发/独立review门槛未关闭。roadmap仍5/12/P9active。

## 最新结果：固定合成视频设备链路与哈希内存修复

Task2公共契约`c9ea298`、runtime设计`b5e206d`、共享runtime`eebc670`、显式consumer/严格validator`30e5e8e`已本地提交。视频直接消费`MLXVLMVideoInputAdapter.Prepared.input`，image/video共用session/model lease；generator/生成producer明确join后才清理owned snapshot，未恢复上游二次采样。

首次设备PID22015无最终report且被Jetsam `per-process-limit`终止（原始`JetsamEvent-2026-10-23-234110.ips`保留，约3.30GiB进程驻留页估算，**不是Metal峰值**）。阶段诊断`4eefb34`/`4a4ec77`将失败定位到ModelStore acquire前，尚未decode/model推理。相同Foundation哈希循环读约1GB固定权重三次的host phys_footprint约3.061GB；逐1MiB read+SHA256.update置于autoreleasepool后约5.7MB、hash不变。真实ModelStore64MiB合成文件回归实际RED（202440848B增量峰值）→GREEN（3440640B；全套后续约1MiB）。独立[哈希内存设计](../superpowers/specs/2026-10-23-modelstore-hash-memory-design.md)/[计划](../superpowers/plans/2026-10-23-modelstore-hash-memory.md)，修复`5e39cb8`，保持完整byte/size/hash校验，没有调整MLX全局cache、帧数、模型或runtime。RSS与phys_footprint口径分开，不将此回归当作通用资源验收。

**修复后的真实物理iPhone16ProMax（iPhone17,2）/iOS27.0.1设备链路通过**：tested source=`5e39cb8b06a1dc3862e0f0a034ec6397951eac48`，PID=`22072`，runId=`2B747E73-3D02-4585-8F13-969685C84892`，与独立devicectl启动记录一致。App逐一核对14model文件size/hash，真实固定tokenizer/权重生成；29FPSfixtureSHA256=`9786524a69579ffd69d75c49048a566f31eaec090b1487b69aa4ce6d55e1ee51`。

- success实际返回非空文本、22个update；首update主动取消只收到1个update，取消请求约1904.29ms，snapshot清理约1928.77ms，返回及drain约1929.93ms（单次观测，不是SLA）；同PID重入再次非空、22个update。
- 三次native decode与adapter都保留8真实帧，actualTime原值`[0,3584,7168,10752,14848,18432,22016,25600]/14848`；clip duration=2s、sample span=`25600/14848`约1.724138s，始终分开。三次副本SHA相同、preparedFrameCount=8、owned snapshot close后实际目录不存在。
- 七个gate均true：生成前lease保护、取消后lease仍保护、close后lease可移除、closed图片/视频均拒绝、源fixture未变、没有新owned snapshot目录。raw严格validator实际exit0，未从输出文本推断时序/自然质量。

raw/summary/sourceinventory：`artifacts/verification/apple-mlxvlm-video-suite-20261023/`；raw文件`device-raw-memory-fixed.json` SHA256=`a5efcc79134c1ec4cc01313169e09c3cb56303d6011d8eecfdc33254f4e69ec2`。所有失败/诊断/build argv/log、implementation-checks/final-review在`.superpowers/sdd/2026-10-23-apple-mlxvlm-short-video-runtime/`，哈希fix自身RED/GREEN/fullbase日志在`.superpowers/sdd/2026-10-23-modelstore-hash-memory/`。

最新验证：完整基础Apple **470/470**、增强host **41/41，无skip**（真实tokenizer2/2）、tools **133/133**；新独立consumer generic iOS build及随后实际签名device build均成功。签名沿用既有team/profile、无allowProvisioningUpdates/签名资产变更；12checkout HEAD/clean、原/consumer lock、canonical资源与2个保护文件hash保持匹配。本会话作者自审，**独立review仍pending，无APPROVE**；仅本地提交、无push/merge/publish。

原Task2/3的本轮固定native范围完成，Task1仍partial：短于8实际样本明确拒绝，不承诺任意<=5s视频。原上游8→7失败未修复，失败证据保留。仍5/12/P9active；自然视频/时序质量、Metal/GPU/energy、Android真实后端、最低系统、binary archive exit65、物理触摸/真实system picker、旧Video UI、P6实录等缺口保留。下一独立包为Android类型化video contract/client对等，之后再独立KMP/Demo接线；不能把Android契约称为真推理。下方为历史检查点，不重复已完成包。

**最新runtime库检查点：** Task2契约`c9ea298`之后，按[runtime计划](../superpowers/plans/2026-10-23-apple-mlxvlm-short-video-runtime.md)接入MLXVLMBackend视频协议、共用session/model lease及取消/close屏障；Local runtime直接消费adapter.Prepared.input，保留owned snapshot至generation producer明确join后清理。新增诊断metadata含副本hash、原始requested/actual rational times、真实clip duration与sample span；无调用方路径。session6项与metadata1项均missing-API RED→GREEN，全增强host41/41无skip、tools126/126、新独立unsigned generic iOS BUILD SUCCEEDED/exit0。**这些是host/build证据，尚无视频权重设备运行；原Task3仍未完成。** 下一实现opt-in consumer/严格validator后进行固定合成视频设备success/cancel/reentry/close。当前短样本仍拒绝，独立reviewpending、5/12/P9active，binary/质量/资源/Android/minOS等缺口保留。原始logs/checks/作者自审在`.superpowers/sdd/2026-10-23-apple-mlxvlm-short-video-runtime/`。

## 前包：Task 2 类型化视频契约

纯Swift `MultimodalVideoRequest`、可选 `MultimodalVideoGenerationBackend` 与client视频overload已完成；image/video复用同一admission、update过滤、cancel/drain和close/query屏障。legacy backend在availability前明确拒绝视频且不fallback。**`MLXVLMBackend`仍只支持图片，Task3 runtime未开始；本包不是视频推理证据。**

实际API缺失RED exit1后，Multimodal22/22（新增8项）、完整基础Apple469/469、增强host34/34无skip（固定真实tokenizer2/2）、tools126/126通过；Swift6 strict-concurrency/warnings-as-errors typecheck exit0。新独立consumer unsigned generic iOS BUILD SUCCEEDED/exit0，额外compile probe确实编译公共video request/client overload，未执行该函数。旧XCFramework不作新API分发证据。raw/checks/作者自审在`.superpowers/sdd/2026-10-23-apple-mlxvlm-short-video/task-2-client-*`；12checkout HEAD/clean及原/consumer lock匹配，保护文件未变。独立reviewpending，仍5/12/P9active，仅本地提交。

下一先细化Task3 runtime设计/可验证计划，直接消费`MLXVLMVideoInputAdapter.Prepared.input`，不恢复上游`.frames`二次采样。Task1短于8实际样本仍拒绝，不扩大当前profile；原上游8→7失败及binary archive exit65、自然质量、Metal/GPU/energy、Android真后端、最低系统、旧Video UI与P6实录门槛保留。下方为历史检查点。

## 前包：profile 与 iOS 输入准备

`81ea4df`补4个真实合成负向profile测试：H.264 BT.2020/PQ、0.25s offset/edit、只有2个真实帧、带外部URL dref entry的容器，均拒绝且清理owned copy。baseline原实现4/4GREEN，**没有产品bug修复**；临时放松transfer guard后PQ测试实际RED（exit65/1failure），finally恢复原snapshot字节后完整host **34/34、无skip**；tools新跑 **126/126**。原snapshot源码没有最终diff。two-frame fixture是当前8个互异sample profile的**拒绝**证据，不是短于8样本视频支持。reference fixture只指向不存在的合成相对URL，属于结构拒绝测试，不证明任意引用容器无外部I/O。

新增[profile设计](../superpowers/specs/2026-10-23-apple-mlxvlm-video-profile-design.md)及[计划](../superpowers/plans/2026-10-23-apple-mlxvlm-video-profile.md)。原Package直接真机测试exit70，测试体未执行：`Tool-hosted testing is unavailable on device destinations`。按诊断生成本项目空App作为XCTest host，沿用现有bundle ID/team/wildcard profile、同一SDK、测试源码和fixtures；不改SDK可见性或签名资产、不启用allowProvisioningUpdates。恢复后**物理iPhone16ProMax / iOS27.0.1 / arm64**实际执行：

- snapshot/profile **12/12、0failures、无skip**，包括合法8帧解码、副本稳定、首帧后主动取消/join后清理、重复close保留foreign目录及4项新拒绝；raw summary=`device-snapshot-summary.json`。
- 后续适配器 **6/6、0failures、无skip**，29FPS真实8帧逐帧pixels/CMTime、24FPS上游tokens/pixels对照、单帧metadata、非法输入和取消。此设备测试使用ByteTokenizer；真实固定tokenizer仍只有前包macOS2/2证据。raw summary=`device-adapter-summary.json`。
- 第一次追加adapter测试因生成host缺少MLXVLM直接product链接exit65；补链接时MLX revision约束与原exact0.32.3 lock不符，离线解析exit74；改为与原manifest相同的exactVersion约束后通过。全部失败日志保留，未改lock或关闭离线flags，没有下载。不是产品失败或通过证据替换。

原始日志/argv/fixture生成脚本/ffprobe/hosted源码hash位于`.superpowers/sdd/2026-10-23-apple-mlxvlm-video-profile/`，真实xcresult位于`artifacts/verification/apple-mlxvlm-video-profile-20261023/hosted-build/Logs/Test/`。设备执行源码/fixture16项hash均与当前仓库字节一致，tested product source=`81ea4df`，后续仅文档/生成host配置变化。仍**没有新视频权重生成、自然质量、Metal/GPU/energy指标、最低系统或公共API/runtime接线证据**；不把真实CI/MLX输入准备当作推理或资源验收。作者自审、独立reviewpending，5/12/P9active。下一可独立工作为原Task2纯Swift类型化请求/client生命周期；原Task3必须使用新adapter，不能恢复原`.frames`二次采样路线。短于8实际样本的支持仍未实现，不扩大当前profile承诺。

## 项目侧 sampled-video adapter（前包）

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
