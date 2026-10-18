# P10 身份与活体实施进展

更新：2026-10-18。状态：**实施中；生产身份与活体未启用**。

## 已验证切片

| 工作包 | 提交 | 验证 | 结论边界 |
| --- | --- | --- | --- |
| Task 1：引用、授权声明、时钟、错误、选择与 codec | `10029b8` | Swift 12/12；Android 12/12；共享 canonical JSON 夹具 | 仅字段/取值契约，解码不代表签发真实性或授权有效 |
| Task 4：后端绑定与独立身份/PAD 策略 | `850fe02` | Swift 8/8；Android 8/8；两端 backend space 夹具一致 | fixture 后端为模块内部类型；无默认生产模型/阈值；判定归后续引擎 |
| Task 2 加密子步骤：AES-256-GCM envelope 与原生 key provider | `7803999` | Swift 6/6；Android 6/6；Android lintDebug | 密钥缺失不重建、AAD/篡改拒绝、随机 nonce、严格 256 位与字节快照已验证；真实 Keychain/Keystore 成功路径尚未真机验证 |
| Task 2 journal 子步骤：独占 writer、加密原子提交、恢复加固 | `976d973`、`a006bbb` | Swift journal 14/14；Android journal 13/13；完整 identity 模块及 Android lintDebug | 构造与每次覆盖前认证现有 journal；丢失记录/密钥不重建；fsync、原子替换、目录同步；有界读取；只清理已认证临时文件。独立增量复审 APPROVE，4 项问题全部关闭；尚无 IdentityStore 生命周期 |

| Task 2 授权状态子步骤：可信 provider、不可变声明与域 epoch | 本次提交 | Swift 授权 11/11；Android 授权 10/10；identity 模块及 Android lintDebug | provider 独立权威 epoch、同域撤销/新 grant 拒绝、声明不可改绑、ownership 独立授权、严格快照恢复。独立复审实现可接受，LOW issuedAt 测试缺口已补；尚未接入 Store 持久化门 |

授权切片验收时身份模块定向合计：Swift 51/51、Android 49/49，Android identity lintDebug 通过。测试使用合成夹具及临时内存密钥，不包含个人生物数据、模型权重或商用精度结论。journal 两端测试数差异来自 Swift 独立的 record identifier 边界用例，Android 在组合用例中覆盖同一行为。

## 可复现命令

```sh
rtk proxy env CLANG_MODULE_CACHE_PATH=/tmp/odai-p10-clang-cache SWIFTPM_MODULECACHE_OVERRIDE=/tmp/odai-p10-swift-cache swift test --disable-sandbox --package-path apple --filter ODAIKitIdentityTests
rtk proxy ./gradlew :android:identity:testDebugUnitTest :android:identity:lintDebug
```

当前完整 Apple 包测试另有既有环境失败：`VisionFaceDetectorTests.testBlankImageUsesRealVisionAndReturnsSuccessfulEmptyResult` 返回 `com.apple.Vision Code=9`。未将其改为跳过或通过；上述身份定向结果与该缺口分别记录。

## Task 2 待实现

- 已有可信 provider 契约及不可变授权状态，后续 Store 构造仍必须注入 provider 和持久化根目录；禁止以 request epoch 或单纯内存字典作为权威状态。
- 将已实现的 journal 接入 IdentityStore；模板数据仍需独立持久化与恢复，不能把 journal 单元测试当作完整存储验收。
- candidate/template 所属关系、持久化授权域 epoch、tombstone 和反向索引。
- admission/revoke/publish 串行门、撤销立即请求停止、lease 后两阶段删除。
- 进程重启、持有 lease 删除、scope/expiry、同域新 grant ID 绕过与撤销竞态测试。
- 平台原生密钥与恢复行为真机验证；iOS 15/API 26 最低设备验收另记。

只有这些门全部通过，才能标记 Task 2 完成。后续 Task 3/5/6/7 的 snapshot、身份引擎和活体状态机尚未实现。

### 授权状态当前边界

- 内部版本化授权快照沿用严格 contract parser，编码与恢复均限 64 KiB；超限拒绝编码，后续 Store 不能先发布内存再尝试持久化。
- 这里只提供生成候选状态的纯操作；尚未公开 revoke API、签发 receipt、实现 candidate/template membership 或持久化线性化点。
- 授权声明与 epoch 保存在快照内，快照需由 Store 包进已认证 journal，不能写入明文或日志。

### journal 当前边界

- 内部 record ID 长度为 1–64 字节，加密记录总长不超过 16 MiB；拒绝超限输入和文件。
- AAD 绑定 canonical root 路径和 record ID；移动目录会拒绝解密。接入真实应用前仍需验证平台容器路径变化的影响，不能推断为可迁移存储。
- 加密写入临时文件先同步，再同目录原子替换并同步父目录；异常会退休当前 writer。已有测试覆盖密钥拒绝、旧记录保留、关闭/重开、篡改和恢复；真实进程终止及设备文件系统故障注入仍待后续验收。
- Swift 根目录显式排除备份；Android primitive 仅为 internal，后续生产 IdentityStore 构造器必须强制使用 `noBackupFilesDir`。
- 未认证、损坏或不属于此 record 的临时文件会保留，不按名字删除。未提供全盘清理或闪存擦除承诺。

## 生产门槛

[模型预研](p10-identity-model-selection.md)没有选型或下载权重。Task 9 的商用模型许可、授权数据和身份阈值，以及 Task 10 的 PAD 后端/攻击数据/校准仍为独立门槛。P10 与整个 roadmap 均未完成。

## 加密平台依据

- [Apple AES.GCM](https://developer.apple.com/documentation/cryptokit/aes/gcm)：框架随机 nonce 与 AAD 认证。
- [Keychain device-only 可访问性](https://developer.apple.com/documentation/security/ksecattraccessiblewhenunlockedthisdeviceonly)与[非同步设置](https://developer.apple.com/documentation/security/ksecattrsynchronizable)：解锁访问、不迁移、不 iCloud 同步。
- [Android KeyGenParameterSpec](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec)与[GCMParameterSpec](https://developer.android.com/reference/javax/crypto/spec/GCMParameterSpec)：AES/GCM/NoPadding、256 位 key、128 位 tag。
- [Android noBackupFilesDir](https://developer.android.com/reference/android/content/Context#getNoBackupFilesDir())：后续存储根应位于该目录，持久化相对路径。

这些资料只约束实现，不替代真实设备和故障恢复验收。
