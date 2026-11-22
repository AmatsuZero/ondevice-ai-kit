# iOS Demo UGREEN 开发签名 — 2026-11-22

用户要求将 iOS Demo 团队设置为 UGREEN GROUP LIMITED。本机 Xcode 团队记录确认 Team ID 为 `4X996UV8LC`，现有 Apple Development 身份属于该团队；本地已有未过期且匹配 Demo Bundle ID 的通配开发描述文件。

- `OnDeviceAIDemo` 与 `OnDeviceAIDemoUITests` 的 Debug/Release 共四项配置统一为 Automatic、`4X996UV8LC`。
- `scripts/configure-ios-ui-tests.rb` 保留此设置，重新生成工程不会恢复为空团队/手动签名；可通过 `ODAI_DEVELOPMENT_TEAM` 覆盖。完整工程生成器连续两次执行字节一致。
- 本机 Android Studio 的 `.idea/runConfigurations/OnDeviceAIDemo2.xml` 同步团队；该个人配置不提交。
- 当前 main 的 `:demo:shared:linkDebugFrameworkIosArm64` 离线构建成功，27 秒，保留原有 Kotlin 弃用等警告。
- 默认根 workspace 的 `OnDeviceAIDemo` / Debug / generic iOS 构建成功，没有命令行 Team、签名方式或 framework 搜索路径覆盖。
- 实际 App 的 `codesign --verify --deep --strict` 通过，签名 Authority 为 Apple Development，TeamIdentifier 为 `4X996UV8LC`，应用 entitlement 为 `4X996UV8LC.io.ondeviceai.ondeviceaidemo`。

使用现有 `iOS Team Provisioning Profile: *`，到期日为 2027-10-16；未传入 `-allowProvisioningUpdates`，未创建/撤销/更新证书或描述文件。首次签名检查复用上一任务的已验收 KMP framework；随后已补齐 main 的 framework，并用默认工程独立完成上述最终检查。

未安装或启动真机，也不据此替代真机功能/性能验收。现有 SDKDistribution scheme 与 NativeConsumer 工程逐字节保持。原日志、构建结果与验签收据：`/private/tmp/odai-ios-demo-signing-20261122/`，最终默认构建见 `default-signed-build.log`，结构化核验见 `verification.json`。
