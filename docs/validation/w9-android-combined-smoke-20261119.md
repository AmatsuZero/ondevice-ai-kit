# W9 Android 组合包真机冒烟（2026-11-19）

**TECNO KM9 通过。** 从冻结 Android Release 组合构建隔离副本，在真机冷启动后打开「离线变声」，通过系统选择器导入唯一的合成 WAV，选择「升调」并转换一次。页面显示「转换完成」和 `16000 Hz · 16000 帧 · 1.0 秒`。这是一次组合包冒烟；双端用户已确认的听感、录音、停录和重录无需重做。未调用录音、播放或导出。

- 原 unsigned Release APK SHA-256 `a0b7627f2a90e521fb12eb9b24489349bdcef0e5c71a367f65590f18aa18dcc6`。隔离源码唯一改动为 `applicationId` → `io.ondeviceai.demo.w9combinedsmoke20261119`，复用四个组合开关、相同本地依赖及通知输入。隔离 unsigned APK SHA-256 `8b1a33f76bb5d06d62791e18fb3a590bb15181f75fc0eb2e8a461ec6e1a5dfd5`；两包各 725 个成员，无增删。变化为 `AndroidManifest.xml`、`classes3.dex`、`resources.arsc`、ART 基线配置文件 `assets/dexopt/baseline.prof`；原生库、通知及其余成员字节一致。
- 使用本地开发密钥签名，验证 APK v2/v3。签名副本 SHA-256 `5006d4c0f6ed9fa6fcf99221035312f99af38c4caea55d82db72090d4798056a`，与设备暂存文件和实际安装的 `base.apk` 摘要一致。安装未替换任何原包。合成文件为单声道 PCM16、1 秒，源文件与设备文件摘要一致。
- 文件选择器的「下载」为 ListView 行，未标记 `clickable`；按该行实际范围进入后选定唯一合成文件。App 用「导入 WAV」作为来源标签；通过选择器记录、导入后可用的转换按钮和转换结果确认，无需重导或重装。
- 测完卸载隔离副本，删除本次暂存 APK、合成 WAV、目录和 UI 转储。普通 Demo 和既有验收包的安装路径和 APK 摘要前后一致。未读私人音频。

证据目录：`.omx/artifacts/w9-combined-candidate-20261118T084303Z/candidate-pair-acceptance/android-tecno-smoke-20261119/`。主要文件为 `apk-comparison.json`、`signature-verification.txt`、`device-smoke.json`、`06-picker-owned-folder.json`、`09-result-0.xml`。原组合摘要索引保留为 2026-11-18 历史快照。

**正式发布仍 NO-GO。** Android 结果绑定本地开发签名副本；当前 iOS 组合仍只有隔离开发签名副本的启动证据。最终签名组合、通知适用性和独立复审仍开放。W3/W6 继续暂停。
