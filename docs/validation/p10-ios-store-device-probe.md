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

上面的原同进程报告明确 `sameProcessOnly=true`，不证明进程被杀/重启。下节补充了两个已提交检查点后的进程终止恢复；设备重启、锁屏后台访问、容器路径迁移、提交中断与文件系统故障恢复仍未验证，也不替代 iOS15 最低系统设备、真实身份模型许可或阈值/PAD 校准。

## 两次 SIGKILL 后的持久恢复（2026-10-19）

SDK 源码基线 `40f51f2`；本次只增加 Debug 验收 helper，不修改生产 SDK。设备仍为 iPhone 16 Pro Max / iOS27.0.1。`tools/probes/apple_identity_restart_probe.swift` 通过原宿主的 `--restart-stage=` 路由运行，使用 UUID 限定的私有 Application Support 目录、独立 Keychain namespace 和合成 `[1,2]` embedding。向量不进入报告或 Git；仅模板/candidate/receipt 的 opaque 编码保存在备份排除的测试目录内，供下一进程恢复。

最终运行 UUID：`15179388-0fa7-42af-9537-e8901cf0aec5`。同一安装的可执行路径和不同 PID 已用四份 launch JSON、两份 SIGKILL JSON 与报告交叉核对：

| 阶段 | PID | 实际结果 |
| --- | --- | --- |
| create | 15878 | enrollment 已提交，writer 保持打开，报告 ready；随后 devicectl 确认发送 SIGKILL/9 |
| delete | 15880 | 新进程恢复相同 Store/membership，实际解密向量比较成功；持有 lease 提交删除，观察 purgePending 与 payload 仍在，报告 ready；随后第二次 SIGKILL/9 |
| recover | 15881 | 第三个进程恢复 tombstone，原回执为 purged；payload 与模板 key 缺失，旧模板拒绝，重复删除回执相同；close 后报告 pass |
| cleanup | 15882 | 仅删除该 UUID ledger 中的精确 key，并逐项验证缺失；删除本次私有目录，报告 pass/cleanup=true |

成功 ready 路径不调用 close，Store/lease 保持在挂起函数内直到外部强制终止。Key ID 在创建前写入私有 ledger，清理不枚举 Keychain 或其他目录。验证后原已签名 `IOSSpeechProbe.app` 已重新安装并启动，无卸载操作。

### 复现

生成、build/install 命令与前节相同，工程输出换成新的 `artifacts/P10/identity-restart-device`（先创建父目录），derivedData 为 `artifacts/P10/identity-restart-dd`。不要复用已完成运行的 UUID。按以下阶段分别启动，每次导出 `Documents/identity-restart-probe.json` 到 `report-<stage>.json`；核对本次 UUID、stage/status 和 PID 后才执行下一步。

```sh
rtk proxy xcrun devicectl device process launch --device 00008140-0008703436D3001C --json-output <evidence>/launch-create.json io.ondeviceai.probe.speech -- --run-id=<UUID> --restart-stage=create
rtk proxy xcrun devicectl device process terminate --device 00008140-0008703436D3001C --pid <create-report-pid> --kill --json-output <evidence>/kill-create.json
rtk proxy xcrun devicectl device process launch --device 00008140-0008703436D3001C --json-output <evidence>/launch-delete.json io.ondeviceai.probe.speech -- --run-id=<UUID> --restart-stage=delete
rtk proxy xcrun devicectl device process terminate --device 00008140-0008703436D3001C --pid <delete-report-pid> --kill --json-output <evidence>/kill-delete.json
rtk proxy xcrun devicectl device process launch --device 00008140-0008703436D3001C --json-output <evidence>/launch-recover.json io.ondeviceai.probe.speech -- --run-id=<UUID> --restart-stage=recover
rtk proxy xcrun devicectl device process launch --device 00008140-0008703436D3001C --terminate-existing --json-output <evidence>/launch-cleanup.json io.ondeviceai.probe.speech -- --run-id=<UUID> --restart-stage=cleanup
rtk proxy python3 tools/probes/validate_identity_restart_report.py <evidence> --run-id <UUID>
```

最终原始报告、kill/launch/恢复安装 JSON 均在 Git 忽略的 `artifacts/P10/identity-restart-reviewed-device/`，校验器对该目录 exit0。初轮 `artifacts/P10/identity-restart-device/` 的 create 命令将 JSON 参数误放在 `--` 后，缺失 launch-create.json；独立审查还指出空 `withExtendedLifetime` 位于 sleep 之前不能保证等待期间的对象寿命。初轮不作为最终通过证据，按修订校验器会被拒绝。修复为每次 sleep 后使用 Store/lease 引用，并以新 UUID 重跑全部四阶段；两项 HIGH 经原 reviewer 复核关闭。

校验器检查四阶段同 UUID、状态与必需 flags、不同 PID、同设备/同安装路径的成功 SIGKILL 与全部 launch 参数。四个新增工具用例含 stale/running/失败、缺 flags、同 PID、SIGTERM、错 PID/设备/参数、同名旧安装路径、缺 kill JSON 及 create launch 缺失/错参数/错 PID 等反例。缺校验器、旧安装路径误接受和 create 三反例均先 RED 后 GREEN；新旧报告校验器定向工具测试共 5/5 通过，最终真实证据目录校验 exit0。首次探针编译因误用 RevocationReceipt 失败，改用实际 DeletionReceipt 后构建成功；审查修订后的最终 build 也 `BUILD SUCCEEDED`。编译失败不是 SDK 缺陷或行为 RED。

| 本次对象 | SHA-256 |
| --- | --- |
| restart helper 源码 | `4d04b493e648bce6cd15dbb4f676f2afbb1ca7e0d36b8424555f376c3042a54e` |
| Debug dylib | `36bb080d7a2c2e3504a2fcad7240b04ed7489e04d3e37dafc29c45ec8d3c074f` |
| create 报告 | `d45d1501d4f4c8b7813cdf3ed676938cf847c025be1c478f04a878fa4024bed1` |
| delete 报告 | `986db4327217d604ab2ba12995b4559ea2062c65521ffaa18e1e556dfe484d4c` |
| recover 报告 | `bc60095c2253a2d1a416521283be44e4633b2b8a3e6f99cc1caa3488c9ed37bb` |
| cleanup 报告 | `06bf21bbab2bd4571ab5f7fb87545e30c5a587fb1f538a29417f2f87bfe79516` |

最终构建日志 `/tmp/odai-ios-restart-build-reviewed-20261019.log`。本次只证明上述两个 durable checkpoint 之后的恢复；未在 journal rename/fsync、模板 stage 或撤销提交过程中杀进程，未注入磁盘故障，也没有 Android 跨进程结果。相关门槛继续未完成。
