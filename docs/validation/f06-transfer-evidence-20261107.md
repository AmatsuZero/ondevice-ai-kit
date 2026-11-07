# F06 传递函数取证（证据记录，2026-11-07）

## 有界结论（先行）

在本次检视的三份 Apple 符号文档与三份 iPhoneOS27.2 SDK 头文件中，未发现明确的"首选/必需 RGB 传递函数"契约。这不等于别处无 Apple 指引；未抓取官方示例、未穷尽检索。不得据此断言 sRGB 或线性为必需/正确、模型不受约束，也不得声称无色彩错误、全配置文件支持或质量通过。既有未验证 gate 保留。

## 三份官方页面（原始 GET 200）

- https://developer.apple.com/documentation/videotoolbox/vtsuperresolutionscalerconfiguration
- https://developer.apple.com/documentation/videotoolbox/vtsuperresolutionscalerconfiguration/sourcepixelbufferattributes
- https://developer.apple.com/documentation/videotoolbox/vtsuperresolutionscalerconfiguration/destinationpixelbufferattributes

精确字节与 SHA 见 `.omx/artifacts/f06-transfer-parent-evidence-20261107/retrieval.json`（`.md` 后缀指向同页）。

## SDK 版本与三份头文件

SDK：`iPhoneOS27.2.sdk`（VideoToolbox.framework/Headers）。

| 头文件名 | SHA-256 |
| --- | --- |
| `VTFrameProcessor_SuperResolutionScaler.h` | `c23c793a4bb09edd5fe40e4e17c46ab3656841a3faa9fb276d39c3b0143bc62f` |
| `VTFrameProcessorConfiguration.h` | `4b11c81dda034aa706ab44ab8bb909280e77b1d54ab5b7cbc8c3614aef507a8b` |
| `VTFrameProcessorFrame.h` | `c707fbaec9cfe598883c316fecfaa81e98c6b1d9dfa75aec39171f660ffd9446` |

## 四类事项须区分

像素格式 / 像素缓冲属性字典、像素缓冲附着键值、RGB 传递函数本身、配置模型期望（超分处理器可能需下载 ML 模型，须查 `configurationModelStatus`）互不等价。已确立的只有：`sourcePixelBufferAttributes` 与 `destinationPixelBufferAttributes` 分别描述源帧/参考帧、目标帧的像素缓冲要求，并用 `CVPixelBufferCreateResolvedAttributesDictionary` 与自定义字典合并；像素存储格式本身不能证明传递函数。配置文件状态与模型下载不改变这一点。

## 检索过程与限制

- 新有界 researcher 在出结果前即 429 Too Many Requests，父级未重试。
- Context7 首次 resolve 返回无关 Apple Support，细化后 `/websites/developer_apple_de` 的 query 仅给通用色彩空间与 `supportedPixelFormats`，无明确的超分必需传递函数结果。
- 网页工具对 text/markdown 解析不支持（400），但三份官方文档原始 GET 均 200，精确字节/SHA 已记录。

## 当前源码与停止点

`apple/Sources/ODAIKitAppleVideo/VideoToolboxSREngine.swift:10-13` 仍为明确标注 experimental 的 sRGB 中间约定；`probes/ios-video-sr/VideoSRRunner.swift:13-14` 记头文件未指定传递函数。二者由父级读取、未改动。停止本次有界查找：不因缺少明确契约而做新推断、新测试、设备/运行时/代码变更、模型下载或生成、上传/推送；未来 A/B 需具体输入/输出/评分协议，不能把现有输出元数据倒推为模型质量证明。

## 证据与关联

本机保留证据（仓库相对文本，不保证随 Git 分发）：`.omx/artifacts/f06-transfer-parent-evidence-20261107/`、`.omx/artifacts/native-f06-transfer-evidence-20261107/`，以及 `.omx/artifacts/f06-transfer-parent-evidence-20261107/retrieval.json`、`.omx/artifacts/f06-transfer-parent-evidence-20261107/header-inventory.json`。关联记录：[android-speech-final-review-20261107.md](android-speech-final-review-20261107.md)；跟踪文档：[foundation-followups.md](foundation-followups.md)。
