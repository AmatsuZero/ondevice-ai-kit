# W3 标点 SDK 的完整 Apple 组合 App 构建 — 2026-11-16

使用当前已提交的 SDK 源码与既有生成器，完成了 Speech、Video、MLX、OpenCV 同时启用的完整组合 App 构建。实际 Xcode 构建 76.984 秒、exit0；App 内的标点后端来自此前已验证的源码候选，补上 SDK 模块到真实消费 App 的本地构建链路。本轮没有签名、安装、识别或重跑旧批次；不是产品质量或发布接受。

## 输入与实际产物

| 项目 | 核验结果 |
| --- | --- |
| 源码 | 固定提交 `c8038cc29980b5acf7169a48c074af75a53e2335` 的 203 份选定 Swift/Demo/生成器输入，独立归档并在构建后核摘要；没有复制受保护的 dirty 工程 |
| 依赖 | 12 个原固定 SwiftPM checkout、2 个 C 子模块，构建后逐文件/模式/Git blob 和 revision 核对；两份 Package.resolved 相同，未更新依赖 |
| shared framework | 两个平台均复用已有 F02 `.6` 产品，二进制分别匹配原生产接入收据；构建配置仍为 `0.144.6-ondevice-f02.6`。没有误用旧组合包的未打补丁 `0.144.6`，也没有声称重新构建 Kotlin |
| OpenCV | 已有 static XCFramework 完整文件树匹配后隔离复制，没有新下载或 native 重建；实际 App 未动态 embed opencv2.framework |
| 实际编译 | 30 份真实 SwiftFileList、643 个唯一 Swift 源输入均保留。包含当前 SpeechHost 与 AppleSpeechRecognizerBackend；后者 SHA-256 为 `e202d3d0d0efa80410f22d3e9d475ea51cdeb810acba2930a86d23afd626d952` |
| App | `io.ondeviceai.combined.demo`、arm64、MinimumOSVersion 18.0、ODAISDKCommit 为上述提交；92 个实际文件逐一固定摘要 |
| 通知 | 74 份 F02 通知与 3 份 OpenCV/YuNet/SFace 通知在实际 App 中逐字节保留；不是全部组件许可适用性或完整生产通知接受 |
| 签名 | codesign 回读为未签名，没有 embedded.mobileprovision 或资源签名目录；没有创建/使用签名身份，未开启 Documents 共享 |

实际 `SpeechDemo.debug.dylib` SHA-256 为 `aa5b3faa72eb3b697939e20e1359c228aa1ca45be3208198693204458cd93a9d`。符号回读包含 AppleSpeechRecognizerBackend、SpeechHost、MLXVLMBackend、IdentityOpenCvHost；二进制包含 `setAddsPunctuation:` selector 字符串。结合编译输入和源码摘要，这些支持源码到当前包的本地绑定；不把字符串/符号存在视作分支执行、模型推理或整个递归 native 来源证明。

## 实际核验与边界

203 份源文件、3 份复用 framework 文件树、12 个 checkout/2 个子模块及 206 份已有工作文件在构建后保持原内容。主代理只调用既有 generator、构建和核验，没有新生产实现、helper 调用或配额重试。完整 App 使用 MLX/OpenCV，不以缩减功能的 Speech-only 包代替现有组合目标；选定源输入仍不等于完整历史 Kotlin/native 构建输入闭包。

构建日志有 25 条按完整文本去重的诊断行，不能计作 25 个独立警告原因；stderr 另含 generic iOS placeholder 缺 operatingSystemVersion 提示，原文保留。构建通过不称 warning-free。首实包核验错误要求 SwiftPM 的 artifacts 为空；实际唯一新增记录是已绑定的本地 OpenCV XCFramework，source.type=local、prebuilts 为空。保留失败与初版 checker，只改为精确核对这条本地记录后，读取同一 App 验收通过，没有重新构建或下载。

App、真实 FileLists、对象/中间产物、xcresult、源归档、固定依赖及所有日志保留在 `.omx/artifacts/w3-apple-sdk-app-candidate-20261116/`。可携带记录位于 `probes/apple-speech-combined-candidate-20261116/`；[机器收据](w3-apple-sdk-app-candidate-20261116.json)记录 App/编译输入与通知身份。清理仅限本任务闲置、可再生的模块/编译缓存与源码索引，精确清单另存，不删除产品或唯一证据。

这是当前源码的本地未签名 App 候选，尚未固定为最终 shipping App。Apple 远场/负例质量、Android 41 项缺失及内存诊断、适用预算、同候选完整来源/组成与独立发布接受仍未完成；R4/R5/P1–P4/W3 继续 open。没有新增设备、麦克风、AiAi、模型下载或识别请求；已消费批次不重放，旧准备/实验收据保持历史身份。
