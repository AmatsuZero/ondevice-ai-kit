# P7 视频超分后端调研

更新时间：2026-10-25。P7 只比较后端边界和一条固定输入链路的探针条件，不把单帧放大或桌面运行结果当作移动端视频超分交付。

## 自然视频 iPhone 真机复核（2026-10-25，非镜像）

沿用固定公开 Common swifts fixture，在授权的 iPhone 16 Pro Max / iOS 27.0.1（UDID `<iPhone-UDID>`）上重新安装签名的 `IOSVideoSRProbe.app`，通过 `devicectl` 将 `artifacts/P7/natural/prepared/input-180p-4x.mp4` 复制到 probe 的 App Documents，并以 `--process-video` 启动。全程使用 `xcodebuild`/`devicectl` 真机路径，没有使用 iPhone 镜像、镜像画布或坐标点击。

设备报告 `artifacts/verification/p7-natural-device-20261025/device-documents/ios-video-sr-probe-777236F5-1760-4698-A44E-8EABAFAD81A5.json` 显示：SR 与 bicubic 均 `state=success`，输入/输出均为 48 帧、1280×720、PTS 单调；输入 SHA-256 为 `97682e3f9703e326bab3fbed06aa7a298b506d1965502288f5ed5037c51f2f1f`，与仓库 fixture 和设备副本一致。两路首帧和编码声明均为 sRGB 中间链路、BT.709 primaries/transfer/matrix、HEVC/MOV，SR 常驻内存采样峰值增量为 `88,113,152 B`，bicubic 为 `507,904 B`；这些仍是 probe 进程采样，不是 GPU 或全机峰值。

对设备 MOV 运行 `tools/probes/video_sr_quality.py`，按解码帧序号配对 48 帧且 `colorComparability=matched-metadata`：

| 输出 | PSNR dB | SSIM | 设备输出 |
| --- | ---: | ---: | --- |
| VideoToolbox SR | 36.776212 | 0.979250 | `sr-output-FEF9C597-569D-4FD4-B044-F229CAA17E7A.mov` |
| Core Image bicubic | 38.872866 | 0.983506 | `bicubic-output-F71BB40C-359C-4C32-84DA-0013F9A4D794.mov` |

本次 SR 分数低于同链路 bicubic，且这只是单一自然样本的编码输出保真度比较；`qualityVerdict` 保持 `unverified`，不据此判定模型质量失败或通过。该复核补充了自然视频统一色彩/编码链路的真机运行证据，但不升级主观质量、传递函数、GPU/能耗、最低系统或 P7/P11 完成状态。

## 候选路线

| 后端 | 平台 | 优点 | 主要风险 | 当前结论 |
| --- | --- | --- | --- | --- |
| `apple.videotoolbox.sr` | iOS 26+/macOS 26+ 真机 | `VTSuperResolutionScalerConfiguration` 与 `VTFrameProcessor` 提供系统超分处理器，模型资产由系统管理 | 设备、倍率和资源状态需运行时查询；模拟器没有该 API，不能替代真机 | iOS 26+ 首个探针后端，不改变 iOS 15 基础下限 |
| `apple.coreml.sr` | iOS/macOS | 系统模型运行时、可与 Metal 纹理衔接 | 模型转换、内存峰值、设备算力和模型许可必须逐项验证 | iOS 15 路线候选；模型单独锁定 revision |
| `android.litert.sr` | Android | Android 生态和 NNAPI/GPU delegate 选择较多 | delegate 差异、包体积、模型输入布局和 API 级别差异 | Android 探针候选，先用固定短视频和 CPU baseline |
| `onnxruntime.mobile` | iOS/Android | 跨端模型接口和可裁剪 package | 移动端 EP、二进制体积、算子支持和许可证核对 | 作为跨端备选，不与系统路线同时接入 |

官方资料：

- <https://developer.apple.com/documentation/videotoolbox/vtsuperresolutionscalerconfiguration>
- <https://developer.apple.com/documentation/coreml>
- <https://ai.google.dev/edge/litert>
- <https://onnxruntime.ai/docs/tutorials/mobile/>

## 固定探针协议

输入使用 2 秒、24 fps、短边 360 的无音轨视频；记录帧数、时间戳单调性、输出尺寸、解码/模型/编码阶段耗时、峰值内存和取消行为。质量至少保留双三次基线与 SR 输出的 PSNR/SSIM；若模型不适合该输入，记录 `unavailable`，不填代理分数。所有报告按设备、OS、实际后端、模型 revision、delegate 和输入 hash 分组。

P7 当前状态：候选比较和探针协议已完成；`tools/probes/video_sr_fixture.sh` 已生成 48 帧、24 fps、无音轨的 360p 输入、720p reference 和双三次 baseline，并通过帧数/时间戳/尺寸校验。2026-10-17 复核发现 Matroska 每次写入随机标识，旧记录的文件级 SHA 无法用作跨次 fixture 身份；验证器已改为对解码后的 YUV420p 帧序列求 SHA-256。两次独立生成的稳定摘要分别是 reference `5cf853741c9ac22f9da6a23a61f9fedada870fdc7a87591731dfb6dbc3d86bf1`、input `a1cd2682cf78ea244906f631a116d3bda602d077e3734b70aeb182a643b151ed`、bicubic `2a9ec0d0539c7ac253b5016820c56a9df4195b6acfe2fa915e03dfafbb418c95`；均为 1280×720/640×360、48 帧、24 fps、无音轨。此前 baseline 对 reference 的 PSNR=33.404676、SSIM=0.970067，仅作为质量基线，不是 SR 模型结果。另生成同一 180p 输入的 H.264/MP4 传输副本，供 iOS `AVAssetReader` 使用；质量摘要仍以无损 FFV1 fixture 为准。

内存采样口径：`VideoSRRunner` 在固定 48 帧处理循环开始、每帧处理和编码后、以及结束时读取当前进程的 `mach_task_basic_info.resident_size`，报告 `residentMemoryBaselineBytes`、`residentMemoryPeakBytes`、`residentMemoryEndingBytes` 和 `residentMemoryPeakDeltaBytes`。该指标只代表探针进程常驻内存，不代表全机内存、GPU 专用内存或系统内存压力。

2026-10-17 iPhone 16 Pro Max / iOS 27.0.1 签名运行独立 `probes/ios-video-sr` 能力探针。系统报告 `isSupported=true`、revision 1、`supportedScaleFactors=[4]`；原定 640×360→1280×720 的 2× 配置不受支持。改为 320×180→1280×720 的 4× 配置后，首次报告模型状态 `downloadRequired`、进度 0；该报告只证明系统 API 与 4× 配置可查询，不证明模型可运行。后续重新读取同一配置时状态变为 `ready`、进度 1（未推断触发方式），随后处理固定 48 帧 H.264/MP4 输入成功，生成可解码的 HEVC/MOV 输出，输出为 1280×720、48 帧、PTS 单调。设备报告记录每帧处理耗时 p50 `0.09339725` 秒、p95 `0.09589062` 秒；追加的 `--cancel-video` 真机运行产生 `state=cancelled` 报告且未留下输出文件，证明任务取消和失败清理路径可用。4× fixture 的输入帧摘要为 `9a5470e699904301402aef22d9aff86d358462e80eabee6d095618b70e6fb2bc`，双三次基线摘要为 `b18ebdd8b33fb87cd228093ad7b5b320ecad463905964afa2e58c6e9dba23562`；reference 摘要仍为 `5cf853741c9ac22f9da6a23a61f9fedada870fdc7a87591731dfb6dbc3d86bf1`。SR 输出与双三次基线的质量值暂不并列解读，须先核对两路颜色转换和编码路径是否可比；资产和输出均不进 Git。

同日再次在同一 iPhone 16 Pro Max / iOS 27.0.1、同一 320×180→1280×720、48 帧输入上运行带内存采样的探针，两次均 `state=success`、输入/输出均 48 帧、PTS 单调。第一次进程常驻内存基线/采样峰值/结束值为 `51,412,992`/`139,182,080`/`139,116,544` B，采样峰值增量 `87,769,088` B，p50/p95 为 `0.092243875`/`0.094348667` 秒。修正采样失败处理并重新构建后，第二次为 `51,544,064`/`138,919,936`/`138,919,936` B，采样峰值增量 `87,375,872` B，p50/p95 为 `0.092315583`/`0.094685708` 秒。这些是两次设备运行的观测值，后续仍需在 P5/P11 的重复 benchmark 中评估稳定性，不能直接作为跨设备预算。

质量复核新增一条更接近设备路径的双三次基线：从同一 H.264/MP4 输入放大，再用 HEVC 编码。对固定 720p FFV1 reference 的 FFmpeg PSNR 分别为 SR 输出 `21.297621 dB`、该基线 `27.140302 dB`。两者编码器（VideoToolbox 与 libx265）、编码参数和颜色处理仍不同，且 `testsrc2` 合成图案未必符合系统 SR 的训练分布；这些数值只说明当前测试条件下观察到差距，不能据此判定模型在真实视频上的质量。质量评估下一步使用自然视频与统一色彩/编码链路，并保留逐帧可视检查。

## 帧配对质量复核

2026-10-17 新增 `tools/probes/video_sr_quality.py`。旧式 FFmpeg framesync 直接对齐 Matroska 的毫秒时间基与设备 MOV 的 1/600 时间基时，实际产生 49 对帧；因此上段旧数值不再作为当前比较依据。新工具先验证两路尺寸、帧数、帧率、严格递增 PTS 和恒定帧间隔，再统一时间基按解码帧序号配对；PSNR 与 SSIM 的逐帧日志数量必须等于源帧数。输出 JSON 保留解码摘要、视频/色彩元数据、FFmpeg 版本和比较口径；缺帧或时序不符直接失败，完全相同帧的无限 PSNR 用 `psnrInfinite=true` 与 `psnrDb=null` 表示。

对现有固定 48 帧资产重新测量：SR 编码输出 PSNR `21.831679 dB` / SSIM `0.907318`，双三次 HEVC 输出 `30.084519 dB` / `0.936982`，两路均只比较 48 对帧。原始 JSON 位于忽略目录 `artifacts/P7/quality-decoded-output.json` 和 `quality-bicubic-decoded-output.json`。这是编码输出相对 reference 的保真度测量；两路缺少完整匹配的色彩声明，编码器与转换路径也未统一，故 `colorComparability` 和 `qualityVerdict` 均为 `unverified`。自然视频、统一色彩/编码链路和可视质量验收仍未完成。

运行方式：

```sh
python3 tools/probes/video_sr_quality.py reference.mkv candidate.mov > artifacts/P7/quality.json
```

## 共用色彩与编码的设备对照

2026-10-17 进一步读取本机 Xcode iPhoneOS 27.1 SDK 的 `VTFrameProcessor_SuperResolutionScaler.h`、`VTFrameProcessorConfiguration.h`、`CIContext.h` 和 `AVVideoSettings.h`。Apple 的公开头文件要求通过配置的 source/destination attributes 协商缓冲区，但没有规定 SR 模型的传递函数；`64RGBAHalf` 只定义像素布局。Core Image 默认工作空间为线性 extended sRGB，简化 render 方法会根据目标 buffer 色彩空间进行匹配。仅凭 half-float 格式不能推断 SR 要求线性输入。

旧链路的首帧真机诊断发现：未标色彩的 180p 输入被 AVAssetReader 解码为 SMPTE-C primaries、BT.709 transfer 和 BT.601 matrix；随后分配的 RGBAHalf 和编码前 BGRA 没有色彩附件，Core Image 将其按 sRGB 解释。设备配置返回的 source/destination attributes 只有尺寸、IOSurface 和 `RGhA`（1380411457），没有色彩要求。原始记录在 `artifacts/P7/device-runs-color-contract/`。该诊断最初把“存在但无名称的 CGColorSpace”与缺失空间都写成 unspecified，后续已区分 unnamed；这里不据旧字段推断解码图像没有色彩空间。

探针现在使用一个明确的**实验约定**：输入必须是标有 BT.709 的 SDR 320×180 视频；两路共用 AVAssetReader BGRA 解码、显式 sRGB RGBAHalf 转换、sRGB 工作空间、输出 BGRA 转换及同一 AVAssetWriter HEVC/MOV 设置。中间只切换 VideoToolbox SR 与 Core Image bicubic（B=0、C=0.75、4×）。SR 缓冲区通过 `CVPixelBufferCreateResolvedAttributesDictionary` 合并系统要求；RGB 缓冲区附加 sRGB 色彩空间/传递函数与 BT.709 primaries。writer 统一输出 BT.709，平均码率请求 8 Mbps、24 fps、关键帧间隔 48、关闭帧重排。相同设置不保证两路实际码率或编码误差相同，比较仍包含编码损失。

`video_sr_fixture.sh` 默认生成完整 BT.709/tv 声明的 fixture，解码帧摘要保持不变；显式 `--untagged` 仅用于重现旧路径的负向验证。默认生成方式为：

```sh
sh tools/probes/video_sr_fixture.sh artifacts/P7/color-pair
```

将生成的 `input-180p-4x.mp4` 复制到探针 Documents 后，用 `--process-video` 或界面处理按钮运行两路。成功报告包含 `baseline`、两路输入文件 SHA-256、请求的编码设置和首帧各阶段色彩附件；只有两路均完成并成功写入报告后才保留输出。旧的未标色彩输入已在真机被明确拒绝，且未留下新 MOV。探针仍仅处理固定小输入；文件 hash 暂时整文件读取，扩大输入前应改为流式 hash，当前内存采样也不覆盖这一步。

同一 iPhone 16 Pro Max / iOS 27.0.1 的对照结果如下，两路输入文件 SHA-256 均为 `8e5bf599efd877bfecc88e0a0e3ba96b6a611338caba1cefc7137fc64f124dc0`，输入/输出均 48 帧、24 fps，PTS 单调。设备报告记录解码图像为 CoreMedia709，中间及编码前图像均为 sRGB。FFprobe 独立确认两个实际 MOV 均只有 HEVC 视频轨、1280×720、YUV420p、tv range，primaries/transfer/matrix 均为 BT.709；与 reference 的声明一致，质量工具均为 `colorComparability=matched-metadata`、恰好 48 对帧。

| 输出 | PSNR dB | SSIM | 每帧缩放 p50 / p95 秒 |
| --- | ---: | ---: | ---: |
| VideoToolbox SR | 26.945055 | 0.920472 | 0.093907 / 0.096132 |
| Core Image bicubic | 28.087569 | 0.919197 | 0.001453 / 0.002219 |

原始设备报告为 `artifacts/P7/device-runs-color-pair/ios-video-sr-probe-34696DD3-F92D-4148-B331-550490B6389C.json`，质量报告为 `artifacts/P7/color-pair-quality-sr.json` 与 `color-pair-quality-bicubic.json`。上述时间只覆盖缩放调用，不是含解码/编码的端到端耗时；基线在 SR 后运行，不能作为独立冷启动或内存比较。两路首帧附件及编码设置已逐字段复核。

Python 工具测试 28/28 通过，新增 fixture 测试覆盖默认输出的尺寸/色彩/帧时序和 H.264 传输副本的解码像素一致性。独立审查发现并修正默认 fixture 不匹配输入门槛、报告失败被成功状态覆盖，以及最后一次内存采样前过早保留输出的问题；最终修订的现有签名 iPhoneOS 构建通过。手机在最终重跑前变为 unavailable，因此这两处失败路径的故障注入和新增双路流程的取消设备复核仍待补，旧版单路取消证据继续保留其原范围。

当前只证明实验约定下两路色彩/编码路径可核对，`qualityVerdict` 仍为 `unverified`。Apple 模型最适合的传递函数、自然视频与逐帧可视质量、音轨保留、Android、最低系统和 SDK/Demo 接入仍未验收；不能用这些合成图案分数下最终模型质量结论。

## 自然视频下载与固定制备

2026-10-17 新增 `tools/probes/natural_video_manifest.json` 与 `natural_video_fixture.py`，固定一个公共领域实拍小样本：Commons 的 [Common swifts](https://commons.wikimedia.org/wiki/File:Common_swifts._Hubala_residetials_in_Tomaszow_Mazowiecki,_Lodz_Voivodeship,_Poland,_CC0.ogv)，作者 `User:WrS.tm.pl`，页面 wikitext 声明 own work 和 `PD-self`（公共领域奉献；文件名同时写有 CC0）。原片为 1280×720、30 fps、6 秒 Theora 视频，另有 Vorbis 音轨，838,446 B。下载时与上游 SHA-1、固定字节数一致，独立 `shasum` 复核 SHA-256 为 `29cd840505e5b4e4a981888b3752c15f47e3801481a7eba7be4b46d7339f9f0b`，现已写入 manifest。媒体与原始 JSON 只在忽略目录 `artifacts/P7/natural/`，不进入 Git/LFS。

原片 FFprobe **没有** primaries、transfer、matrix、range 声明。默认 prepare 因此拒绝；本次显式使用 `--assume-bt709`，将未声明字段按 limited-range SDR BT.709 解释并在报告中记为 `colorBasis=assumed-bt709-for-unspecified-source`。这是一条可复现的实验约定，不证明拍摄/上游编码时的真实色彩，也不构成 HDR 转换。即使传该参数，已知 HDR 或其他冲突色彩标签仍会被拒绝。

```sh
python3 tools/probes/natural_video_fixture.py fetch \
  tools/probes/natural_video_manifest.json artifacts/P7/natural/source.ogv
python3 tools/probes/natural_video_fixture.py prepare --assume-bt709 \
  --manifest tools/probes/natural_video_manifest.json \
  artifacts/P7/natural/source.ogv artifacts/P7/natural/prepared-verified
```

`fetch` 流式下载，固定大小、SHA-1、SHA-256 全部匹配后才以不覆盖方式发布；同名有效资产可离线复用，无效已有资产保留并报错。HTTP 429/网络错误直接失败并清理临时文件，遵守站点 Retry-After 后重跑，不循环刷请求。`prepare` 在处理前后核验 source 与 manifest，一律从首帧开始，30→24 fps，固定前 48 帧、移除音轨，产生 FFV1 reference/180p input/bicubic 以及与 180p 解码像素相同的无损 H.264 MP4 传输副本。先在临时目录生成并验证全部文件，再独占创建目标目录逐项发布，最后发布 `fixture.json` 作为完成标志；消费者必须要求该报告存在。目标目录、symlink 或发布竞态均不能被覆盖。

本次自然素材验证通过：1280×720 reference / 320×180 input / 1280×720 bicubic 均 48 帧、24 fps、无音轨，PTS 从零开始且恒定帧间隔，四项输出色彩标签均为 BT.709/tv。MP4 与 MKV input 的解码帧摘要一致。FFmpeg 版本为 9.0.2；容器文件摘要会受 muxer 元数据影响，跨次 fixture 身份仍使用解码帧摘要：

| 资产 | 解码 YUV420p SHA-256 |
| --- | --- |
| reference | `ede2e6a54da3d74fbe4f906d05d8781468a232614e8ab956f865d7d096264d87` |
| 180p input（MKV/MP4） | `ac21f5f140376567324e592b98b7c1a2dde3731c8a6b4d88a775781cf86eab3e` |
| FFmpeg bicubic | `406b5ac031aba645cd0009258c4e69bbcbfdc8bc72b8d63d22a2a7c6d4cf709a` |

FFmpeg bicubic 对 reference 的 48 帧保真度为 PSNR `50.651335 dB`、SSIM `0.997509`，报告在 `artifacts/P7/natural/quality-bicubic.json`。首帧已目视核对为真实天空/云层/飞鸟；画面以平滑天空为主，且原片已有压缩损失，这个小样本不能代表高纹理自然视频或模型质量。它与设备 Core Image baseline 也不是同一实现，不把该分数直接并列为 SR 胜负。

在该下载/制备工作包验收时，iPhone 再次查询仍为 unavailable，自然视频的设备 SR/CI 双路对照、逐帧可视质量和新增双路取消尚未验收；后续设备结果见下一节。`qualityVerdict` 保持 unverified；后续还需更有纹理的实拍样本和可确认的源色彩。

本包 Python 工具完整测试 41/41 通过（自然 fixture 13 项），日志在 `artifacts/P7/natural/tools-tests-final.log`；`py_compile` 与 `git diff --check` 通过。独立审查指出的 SHA-256 固定/制备源绑定、发布竞态和中断清理测试已修正并复核。SDK/探针 App 源码本包未改，未将历史设备构建视为新自然视频的设备验收。

后续复核发现 prepare 的标准输出混入 validate 诊断文本，现通过 validate 的 quiet 选项让 CLI 只输出一个 JSON 报告。新增真实 CLI 子进程回归先复现 JSON 解析失败、修正后通过；完整工具测试已重跑为上述 41/41。

## 自然视频 iPhone 对照与取消复核

2026-10-17 在同一 iPhone 16 Pro Max / iOS 27.0.1 上完成自然素材的双路真机运行。输入是固定 Common swifts fixture 的 BT.709 实验约定传输副本，文件 SHA-256 为 `97682e3f9703e326bab3fbed06aa7a298b506d1965502288f5ed5037c51f2f1f`。VideoToolbox SR 与 Core Image bicubic 均将 320×180 放大到 1280×720，输入/输出 48 帧、24 fps、PTS 单调；独立 ffprobe 确认两路 MOV 为 HEVC、YUV420p、tv range、BT.709 primaries/transfer/matrix。质量工具按解码帧序号配对 48 帧，结果仍标记 `qualityVerdict=unverified`：

| 输出 | PSNR dB | SSIM | 每帧缩放 p50 / p95 秒 | 常驻内存采样峰值增量 |
| --- | ---: | ---: | ---: | ---: |
| VideoToolbox SR | 36.776212 | 0.979250 | 0.093611 / 0.096591 | 87,883,776 B |
| Core Image bicubic | 38.872866 | 0.983506 | 0.001502 / 0.001705 | 475,136 B |

这些数值只描述这条自然样本、当前编码输出和实验色彩约定；不构成模型质量或跨设备性能结论。基线在 SR 之后顺序执行，内存为各自循环的进程常驻内存采样增量，并非独立冷启动、GPU 或真实峰值对比；时间只覆盖缩放调用。逐帧接触表只用于人工核对画面结构，未建立主观质量阈值。SR 传递函数仍未由 Apple 公开约定确认，音轨、Android、最低系统和 SDK/Demo 接入仍未验收。

随后启动 `--cancel-video`，导出的新报告为 `state=cancelled`，未产生新的 MOV；取消前后既有 8 个 MOV 的文件名和 SHA-256 清单完全一致。取消发生在 SR 阶段，不覆盖基线执行期间取消或报告写入失败的故障注入。取消后以 `--terminate-existing` 重新启动 App 并执行 `--process-video`，两路再次完成 48 帧；这次只证明重启后可再次处理，当时未证明同进程重入或资源释放；后续同进程重入的独立复核见下节。

证据均在忽略目录 `artifacts/P7/natural/`，不进入 Git 或 LFS：

- 首次双路成功：`device-pair-20261017/ios-video-sr-probe-A579B9E7-6B0E-4934-B0D8-5AE4DAD8A0B2.json`。
- 质量：`quality-device-sr-20261017.json`、`quality-device-ci-20261017.json`。
- 取消及既有文件导出：`device-cancel-20261017/ios-video-sr-probe-0B838A78-CFE6-459E-8742-3588121C7D15.json`。
- 重启后成功：`device-reentry-20261017/ios-video-sr-probe-A4622B0E-F5BC-4123-B040-800CE2A95BF0.json`。
- 抽查第 0/12/24/47 帧的接触表：`visual-20261017/contact-sheet.png`，从左至右为 reference、SR、Core Image bicubic。缩略图可见对应云层和飞鸟，不能替代全分辨率逐帧或时域质量验收。

本次只改验证文档；相关质量/自然素材工具的 15 项 `unittest` 重跑通过，日志为 `tests-evidence-20261017.log`。现有签名的探针 iPhoneOS 构建成功，构建日志为 `artifacts/P7/p7-final-device-build-20261017.log`。用户签名设置与本地身份草稿的 SHA-256 均保持不变。

## 同进程取消后重入复核（2026-10-17）

- 以既有 `--cancel-video` 参数启动 `IOSVideoSRProbe`，取消完成后通过已配置的 iOS UI runner 激活同一进程并点击“处理 Documents 视频”。取消完成及重入后的 `devicectl device info processes` 的 PID 都是 `13670`；两次操作之间没有重新启动或重新安装探针。
- UI 用例 `VideoSR-same-process-reentry-20261017.xcresult` 为 **1/1 通过、0 失败，7.702 秒**：取消状态后再次处理，最终显示 `Processed 48 frames + baseline`。导出报告 `ios-video-sr-probe-10F22A33-9D8E-4F16-9C07-3D799D337801.json` 的 SR 与 Core Image 两路均为 `success`，输入/输出各 48 帧，输出 1280×720，PTS 单调。
- 取消前已有 10 个 MOV 的 SHA-256 清单保持不变；取消没有留下部分输出。重入只新增 2 个 MOV，独立 `ffprobe`/FFmpeg 解码均通过：两路均 HEVC、48 帧、1280×720、24 fps，BT.709 primaries/transfer/matrix；摘要见忽略文件 `artifacts/P7/same-process-evidence-20261017.json`。
- 该结果关闭“取消后同进程再次提交”的缺口，仍只覆盖 SR 阶段取消；后续基线取消复核见下一节。全量资源/GPU 释放、主观质量阈值、Android/最低系统与 SDK/Demo 接入继续保持未验证。

## 基线阶段取消复核（2026-10-17）

- 旧探针 RED 运行接受同一参数但仍正常 `success`；新增确定性取消回调后，iPhone 16 Pro Max / iOS 27.0.1 的 GREEN 报告为 `state=cancelled`、`cancelledPhase=baseline`、`baselineProcessedFrameCount=1`。
- 取消发生在第一帧双三次结果已 append 后，`VideoSRRunner` 的 `defer` 取消 reader/writer 并移除基线临时输出，外层流程的 `defer` 移除已完成的 SR 输出；基线取消前已有 14 个 MOV 的 SHA-256 清单在取消后完全一致，新增 MOV 数为 0。
- 取消后的同一进程 UI 重入用例为 **1/1 通过、0 失败，7.759 秒**；`devicectl` 快照显示重入前后探针 PID 都是 `13784`，重入再次显示 `Processed 48 frames + baseline`。仅新增 SR 与基线各一个 MOV：独立 ffprobe/FFmpeg 检查均为 HEVC、1280×720、48 帧、24 fps、BT.709，解码通过；成功报告两路 PTS 均单调。
- 证据位于忽略目录 `artifacts/P7/baseline-cancel-20261017/`：`red-check.log`、`green-check.log`、`device-build.log`、`evidence.json`、`Reentry.xcresult`、`reentry-test.log` 以及三次 Documents 导出；视频、报告和测试 bundle 不进入 Git/LFS。
- 快照 `before` 是 RED 运行前，`after-red` 是旧代码正常成功运行后，`after-green` 是新代码基线取消后；GREEN 哈希检查以 `after-red` 为基线，保留 RED 生成的两个旧 MOV。复核命令为 `python3 artifacts/P7/baseline-cancel-20261017/assert_baseline_cancel.py artifacts/P7/baseline-cancel-20261017/after-red artifacts/P7/baseline-cancel-20261017/after-green`。独立审查已按这组快照复跑通过，未发现阻断；Python 工具完整测试 41/41 通过。
- 本节只证明探针的基线阶段取消、临时文件清理和同进程重入；不证明生产 SDK、全量资源/GPU 释放或主观质量门槛。
