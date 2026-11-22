# Android Demo 默认接入文件超分 — 2026-11-22

普通 Android Demo 的 `enableLiteRtSr` 先前仅在显式 `true` 时启用，默认因此选中 noLitertsr 占位工厂、没有 SR 依赖，能力卡显示“当前构建未接入”。Host 和共享 CMP 绑定已经存在，问题位于构建选择。

现默认包含既有 `:android:backend-litert-sr`；`-PenableLiteRtSr=false` 仍可剔除该 runtime，显式 true 兼容。未改变 LM、Sherpa、OpenCV 默认值、依赖版本、SDK 处理合同或 native/CMP 分工。缺少 ESRGAN 时仍报告 MISSING_ASSETS，并显示“导入超分模型”。不随包携带或由 Gradle 下载 ESRGAN；ML Kit 自有人脸模型仍正常随包。

## 本轮验证

- RED：修复前，真实 Gradle 默认依赖/源码选择断言失败，缺少 `:android:backend-litert-sr`。
- GREEN：修复后的默认、显式 true、显式 false 三种实际依赖/源码选择均通过；普通默认 assembleDebug、Demo JVM、lintDebug 通过。
- 显式启用构建首次执行 Demo JVM 67、SR JVM 10、Video JVM 18，合计 95 项，无 failure/error。默认构建复验 Demo JVM 为 UP-TO-DATE，未计作新增执行。lint 0 error / 13 warning，含已有 ExifInterface、依赖版本、target API、icon、Ktx、SetTextI18n 等提示。
- 默认 APK `217c9c4a40438ce1e3d475dc59b9e8ac077be423e29a203e08f8b727e89abd04`，74,111,902 bytes：DEX 含真实 LiteRtVideoSuperResolutionBackend；无固定身份 ESRGAN。首次包检查器误要求没有任何 tflite，因已有四份 ML Kit 人脸模型失败；保留失败，改为核对 ESRGAN 名称/哈希，同一 APK 通过，未改包求通过。
- 专用 ARM64 API37 模拟器：显式启用 APK 的普通系统模型导入、视频选择、真实 4× 导出成功；随后安装普通默认 APK，重新选片并处理，同样 `SUCCEEDED`，32×24 → 128×96、2 帧、0.083332 秒、0 音轨。未连接 Android 物理设备。
- 显式启用 APK 与普通默认 APK 的独立 VideoSrInstrumentation 均终态 `passed / code 0`：128×96 / 2 帧、PTS/轨道/首帧解码、音轨 DISCARD、取消/drain/reentry、输出 close 后存活及 release 清除通过。此素材无音轨，不能据此宣称真实 AAC passthrough 本轮覆盖。
- 本轮 iOS 文件超分 UI 测试实际执行 1 项通过：系统选片取消、空输入禁用、advanced 后端原因；模拟器如实返回 unsupportedDevice。曾同时请求的 UnifiedDemoUITests 未出现在本轮执行列表，不计为本轮通过。探索页另有实际截图。

截图为本日真实原始 PNG，包含 Android 探索、人脸、TTS、ASR、文件超分成功及 iOS 探索、文件超分、播放增强空态。Android 截图初次采自显式启用构建；默认 APK 的独立普通操作复验与额外成功截图已留存。模拟器功能/截图不证明物理呈现、自然画质、实时性能、持续资源或发布验收。文件 SR 是 CPU 逐帧离线导出，与播放时增强为不同入口；原合同仍限 H.264、BT.709 SDR、偶数 16×16..320×180、≤2 秒/48 帧。

## 实现来源与证据

免费 CBC hy4flash-free 提供通用布尔表达式，父端仅将形参绑定到既有 provider 后集成。首候选返回 Provider 而非 Boolean，未接入；独立窄修给出 `propertyValue.getOrElse("true") == "true"`，经真实 Gradle RED/GREEN 验证后接受。原计划外发三份完整项目文件被自动审批拒绝，未发送；后续通过审批的请求仅含通用需求，无项目源码/路径/文件。文档由父端本地更新。

本地证据：`/private/tmp/odai-android-file-sr-wiki-20261122/`，含 default-graph-red.log、default-verification.json、enabled-build.log、default-apk.json、default-ui-result.json、sr-report.json、default-sr-report.json、ios-ui-result.json、wiki-image-manifest.json。通用助手原始材料：`/private/tmp/odai-gradle-boolean-candidate-20261122/`。Wiki 发布及回读收据在前一目录中，分别核对两端页面与图片哈希。
