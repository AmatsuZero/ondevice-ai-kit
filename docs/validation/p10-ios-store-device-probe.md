# P10 iOS IdentityStore / Keychain 真机探针

日期：2026-10-18。SDK 基线 `8824c36`；设备 iPhone 16 Pro Max / iOS 27.0.1（24A446）。探针使用合成 fixture、随机 Application Support 根目录与 Keychain namespace，不使用照片或业务身份数据。

## 可复现宿主

源码在 `tools/probes/apple_identity_store_probe.swift`，仅在 `DEBUG && os(iOS)` 编译，以 `@testable import ODAIKitIdentity` 使用内部合成 backend 与 template lease；未增加生产公开向量接口。XcodeGen 配置只链接 Identity product，工程和二进制均输出到忽略目录。复现时应使用宿主已授权的 bundle 与开发团队，以下为本次测试环境：

```sh
rtk proxy xcodegen generate --spec tools/probes/apple_identity_store_probe.yml --project artifacts/P10/identity-store-device --project-root .
rtk proxy xcodebuild -project artifacts/P10/identity-store-device/IOSIdentityStoreProbe.xcodeproj -scheme IOSIdentityStoreProbe -destination id=00008140-0008703436D3001C -derivedDataPath /tmp/odai-ios-store-probe-signed-final DEVELOPMENT_TEAM=4X996UV8LC build
rtk proxy xcrun devicectl device install app --device 00008140-0008703436D3001C /tmp/odai-ios-store-probe-signed-final/Build/Products/Debug-iphoneos/IOSIdentityStoreProbe.app
PASS_RUN_ID="store-pass-$(rtk proxy uuidgen | rtk proxy tr '[:upper:]' '[:lower:]')"
rtk proxy xcrun devicectl device process launch --device 00008140-0008703436D3001C --terminate-existing io.ondeviceai.probe.speech -- --run-id="$PASS_RUN_ID"
rtk proxy xcrun devicectl device copy from --device 00008140-0008703436D3001C --domain-type appDataContainer --domain-identifier io.ondeviceai.probe.speech --source Documents/identity-store-probe.json --destination artifacts/P10/identity-store-device/report-pass-final.json
rtk proxy python3 tools/probes/validate_identity_store_report.py artifacts/P10/identity-store-device/report-pass-final.json --run-id "$PASS_RUN_ID"
```

unsigned iphoneos 与现有开发签名构建均 `BUILD SUCCEEDED`。构建仅提示 AppIntents metadata 未生成和宿主屏幕方向警告。未创建签名资产、未修改 Demo Xcode 工程。本次复用已有语音探针 bundle；验证后原已签名 `IOSSpeechProbe.app` 已重新安装并启动，未执行卸载或读取恢复后容器内容。

## 成功与受控失败证据

成功报告 `runID=store-pass-dbf16ead-74e6-43af-9cd3-6911b7af65da`、`status=pass`、`errorCode=null`、`managedKeyCount=3`，以下 flags 全部为 true：

- `applicationSupportRoot`、`backupExcluded`：检查 SDK 创建目录后的备份排除属性，探针未代为设置。
- `encryptedFiles`、`keychainAttributes`：认证 envelope 文件、精确 service/account 的仅属性查询；Keychain 为 `WhenUnlockedThisDeviceOnly`，以 `synchronizable=false` 查询且无同步属性为 true。
- `sameProcessMembershipAndDecrypt`：重建 provider 与 Store，opaque membership 保持，内部合成向量实际解密比较成功。
- `receiptAndTombstonePersisted`：lease 持有时 `purgePending`，释放后 payload/key 缺失，重复删除 receipt 一致；重开仍 `purged` 且模板拒绝访问。
- `domainRevocationPersisted`：域撤销 receipt 重开保持，旧 epoch/candidate/template 拒绝，可信新 epoch 只创建新 candidate。
- `cleanup`：关闭全部 handle/lease，逐个删除跟踪的实际 key ID 并验证缺失，再删除本次随机私有 root。未枚举或清理其他 Keychain 项。

第二次启动传入新的 UUID run ID（本次 `store-failure-6dd037da-b3bf-48fb-a8ac-cd37fa4df7ec`）与 `--force-failure`，在 journal/template 持久化后触发受控失败。导出报告为 `status=fail`、`errorCode=forcedFailure`、`forceFailure=true`，前置目录/加密/Keychain flags 和 `cleanup=true`。失败仍完成精确清理；清理失败会覆盖成功结果为 `cleanupFailed`。报告只含状态、标志、计数、运行标识和错误码，不含密钥或向量。

固定文件名只是设备端单次运行的传输入口；每次启动先删除旧文件并写入当前唯一 UUID `runID` 的 `running` 标记，导出必须核对期望 `runID` 与最终 `status`，因此异常退出或延迟导出不会被旧报告误认为成功。删除或初始 marker 写入失败时探针直接 fail-closed。

独立复审 APPROVE，两项报告 freshness/hash 问题均关闭。`tools/tests/test_identity_store_report.py` 验证匹配的有效报告通过、running 与旧 run ID 拒绝；移除 run ID 判断的 mutation 会使该回归失败。完整工具测试使用现有 Ruby 3.4.11 后 **45/45** 通过；默认系统 Ruby 2.6 误加载 3.4 的 gems 时 distribution 测试报语法错误，未修改依赖绕过。

## SHA-256

| 对象 | SHA-256 |
| --- | --- |
| 探针 Swift 源码 | `76fac8ed926508526f86f3667c9676dca22ba9b6efd7dcde43e2e13a676c6692` |
| 已签名 App executable | `b588b445479429863bbcf761ed87acec19bbd234f2c063f211028b8da79b85d7` |
| 已签名 Debug dylib | `17c4f95bdc5fceee95abe2f918a3284379d99f697ee1fa11cc84626e755c0ff4` |
| 成功报告 | `92dce6d4f2e28a087a66d9a1127636b6d544c3c467f32d7d3d8ab6306e591f10` |
| 受控失败报告 | `6041b95700d3cde31042cd401264d3906c8777314b3397a50f62bf7b95925374` |

原始报告（`report-pass-final.json`、`report-failure-final.json`）、install/launch/export/restore JSON 与设备元数据保留在忽略目录 `artifacts/P10/identity-store-device`；最终构建日志为 `/tmp/odai-ios-store-final-build.log`。

## 边界

报告明确 `sameProcessOnly=true`。本探针不证明进程被杀/重启、设备重启、锁屏后台访问、容器路径迁移或文件系统故障恢复，也不替代 iOS15 最低系统设备验证、真实身份模型许可或阈值/PAD 校准。
