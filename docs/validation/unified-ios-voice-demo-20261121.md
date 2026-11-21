# 默认统一 iOS Demo：离线变声接入

2026-11-21，修复默认 `OnDeviceAIDemo.xcodeproj` 漏接已完成的离线变声能力。此前独立生成的体验工程包含变声产品、Host 和编译条件，默认工程没有，导致统一 CMP 首页显示“当前构建未接入”。变声 SDK、KMP bridge 和 CMP 页面无需重写。

## 变更

- 默认工程和生成器补入既有 Speech / VoiceEffects 产品、Swift Host、KMP bridge 源文件、编译条件及隐私说明。Speech 是现有 VoiceEffectsHost 的编译依赖。
- 生成器保留 App 已使用的 PlaybackEnhancementHost；UI 测试配置加入已有变声入口测试，并同步生成 `iOS Demo` scheme，避免重新生成后引用旧 target UUID。
- 删除 CameraLifecycleUITests 同一作用域内重复的两行入口操作，修复 `invalid redeclaration of 'card'`，使整个 UI test bundle 能编译。

## 当前验收

使用统一根 workspace 和 `OnDeviceAIDemo` scheme，Debug / arm64 iOS Simulator，关闭代码签名。设备为专用 `ODAI Unified CMP 20261121`，UDID `1BF8DC23-08A1-435E-90AE-A091A90E3DC1`，iOS 27.2。

| 检查 | 结果 |
| --- | --- |
| 默认工程缺 AppleVoiceEffects 的原断言 | RED，确认遗漏 |
| 临时目录重新生成工程 | 产品、源文件、编译条件和两个 scheme 的实际 target UUID 检查通过 |
| 两份 Ruby 脚本语法 | 通过 |
| 现有变声入口 UI test | 1 项通过，11.057 秒；进入、空输入限制、无开麦、返回 |
| 临时端到端验收 | 1 项通过，30.035 秒；统一入口 → 系统文件选择器 → 合成 WAV → 升调 → 转换完成 → 返回 |
| 转换结果 | 16000 Hz、32000 帧、2.0 秒；导出按钮可用 |
| tools 测试 | 使用隔离 Swift 缓存后，完整运行 725 项中 724 项通过，1 项受嵌套 sandbox 限制；该项在宿主环境单独复核通过 |
| 临时测试引用、保护文件 | 端到端测试后恢复工程正式字节；原有 dirty SDKDistribution scheme SHA-256 保持不变 |
| `git diff --check` | 通过 |

合成输入为自行生成的 220 Hz 正弦 WAV，SHA-256 `327ba2e7b80362ba61e8f2e46155bd6a37c5ca07ee8d85d03823058c10e82b63`，未启动麦克风。端到端用例只临时加入测试 target，未改变 App target 的配置与源文件。

本轮未重新进行真机录音、人声听感或正式分发验收；既有内部功能接受仍见 [离线变声真机验收](w9-physical-acceptance-20261118.md)。Android 的统一 MainActivity 已持有 VoiceEffectsHost，本轮没有修改 Android。

## 本机证据

根目录：`/private/tmp/odai-unified-voice-default-20261121-f1fRMj/`。

- `ui-test.log` / `voice-page.xcresult`：重复变量导致的首轮失败。
- `ui-test-r2.log` / `ui-test-r2-result.json` / `voice-page-r2.xcresult`：正式入口测试通过。
- `VoiceEffectsIntegrationUITests.swift` / `fixture.json` / `conversion-test.log` / `conversion-test-result.json` / `voice-conversion.xcresult`：本次统一 App 的导入、升调和返回证据。
- `screenshots/manifest.json`：统一首页、变声空页面、转换完成页面的三张原始截图。
- `project-before.pbxproj` / `project-accepted-before-conversion.pbxproj` / `protected-sha256.txt`：集成与保护文件核对。

证据位于本机临时目录，未随 Git 发布。
