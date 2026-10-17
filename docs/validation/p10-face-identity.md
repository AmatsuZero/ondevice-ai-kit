# P10 身份与活体实施进展

更新：2026-10-18。状态：**实施中；生产身份与活体未启用**。

## 已验证切片

| 工作包 | 提交 | 验证 | 结论边界 |
| --- | --- | --- | --- |
| Task 1：引用、授权声明、时钟、错误、选择与 codec | `10029b8` | Swift 12/12；Android 12/12；共享 canonical JSON 夹具 | 仅字段/取值契约，解码不代表签发真实性或授权有效 |
| Task 4：后端绑定与独立身份/PAD 策略 | `850fe02` | Swift 8/8；Android 8/8；两端 backend space 夹具一致 | fixture 后端为模块内部类型；无默认生产模型/阈值；判定归后续引擎 |
| Task 2 加密子步骤：AES-256-GCM envelope 与原生 key provider | `7803999` | Swift 6/6；Android 6/6；Android lintDebug | 密钥缺失不重建、AAD/篡改拒绝、随机 nonce、严格 256 位与字节快照已验证；真实 Keychain/Keystore 成功路径尚未真机验证 |

最新身份模块定向合计：Swift 26/26、Android 26/26，Android identity lintDebug 通过。测试使用合成夹具及临时内存密钥，不包含个人生物数据、模型权重或商用精度结论。

## 可复现命令

```sh
rtk proxy env CLANG_MODULE_CACHE_PATH=/tmp/odai-p10-clang-cache SWIFTPM_MODULECACHE_OVERRIDE=/tmp/odai-p10-swift-cache swift test --disable-sandbox --package-path apple --filter ODAIKitIdentityTests
rtk proxy ./gradlew :android:identity:testDebugUnitTest :android:identity:lintDebug
```

当前完整 Apple 包测试另有既有环境失败：`VisionFaceDetectorTests.testBlankImageUsesRealVisionAndReturnsSuccessfulEmptyResult` 返回 `com.apple.Vision Code=9`。未将其改为跳过或通过；上述身份定向结果与该缺口分别记录。

## Task 2 待实现

- 必须注入可信宿主授权提供者和持久化根目录；禁止以 request epoch 或单纯内存字典作为权威状态。
- 加密 journal、备份排除、单 writer、原子持久化与崩溃恢复。
- candidate/template 所属关系、持久化授权域 epoch、tombstone 和反向索引。
- admission/revoke/publish 串行门、撤销立即请求停止、lease 后两阶段删除。
- 进程重启、持有 lease 删除、scope/expiry、同域新 grant ID 绕过与撤销竞态测试。
- 平台原生密钥与恢复行为真机验证；iOS 15/API 26 最低设备验收另记。

只有这些门全部通过，才能标记 Task 2 完成。后续 Task 3/5/6/7 的 snapshot、身份引擎和活体状态机尚未实现。

## 生产门槛

[模型预研](p10-identity-model-selection.md)没有选型或下载权重。Task 9 的商用模型许可、授权数据和身份阈值，以及 Task 10 的 PAD 后端/攻击数据/校准仍为独立门槛。P10 与整个 roadmap 均未完成。

## 加密平台依据

- [Apple AES.GCM](https://developer.apple.com/documentation/cryptokit/aes/gcm)：框架随机 nonce 与 AAD 认证。
- [Keychain device-only 可访问性](https://developer.apple.com/documentation/security/ksecattraccessiblewhenunlockedthisdeviceonly)与[非同步设置](https://developer.apple.com/documentation/security/ksecattrsynchronizable)：解锁访问、不迁移、不 iCloud 同步。
- [Android KeyGenParameterSpec](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec)与[GCMParameterSpec](https://developer.android.com/reference/javax/crypto/spec/GCMParameterSpec)：AES/GCM/NoPadding、256 位 key、128 位 tag。
- [Android noBackupFilesDir](https://developer.android.com/reference/android/content/Context#getNoBackupFilesDir())：后续存储根应位于该目录，持久化相对路径。

这些资料只约束实现，不替代真实设备和故障恢复验收。
