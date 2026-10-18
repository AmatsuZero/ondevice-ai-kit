# P10 身份与活体实施进展

更新：2026-10-18。状态：**实施中；生产身份与活体未启用**。

## 已验证切片

| 工作包 | 提交 | 验证 | 结论边界 |
| --- | --- | --- | --- |
| Task 1：引用、授权声明、时钟、错误、选择与 codec | `10029b8` | Swift 12/12；Android 12/12；共享 canonical JSON 夹具 | 仅字段/取值契约，解码不代表签发真实性或授权有效 |
| Task 4：后端绑定与独立身份/PAD 策略 | `850fe02` | Swift 8/8；Android 8/8；两端 backend space 夹具一致 | fixture 后端为模块内部类型；无默认生产模型/阈值；判定归后续引擎 |
| Task 2 加密子步骤：AES-256-GCM envelope 与原生 key provider | `7803999` | Swift 6/6；Android 6/6；Android lintDebug | 密钥缺失不重建、AAD/篡改拒绝、随机 nonce、严格 256 位与字节快照已验证；Android Keystore envelope 已在 API35 真机验证；iOS Keychain 与完整 Store 真机路径尚待验证 |
| Task 2 journal 子步骤：独占 writer、加密原子提交、恢复加固 | `976d973`、`a006bbb` | Swift journal 14/14；Android journal 13/13；完整 identity 模块及 Android lintDebug | 构造与每次覆盖前认证现有 journal；丢失记录/密钥不重建；fsync、原子替换、目录同步；有界读取；只清理已认证临时文件。独立增量复审 APPROVE，4 项问题全部关闭；仅 journal primitive，不代表完整 Store 生命周期 |
| Task 2 授权状态子步骤：可信 provider、不可变声明与域 epoch | `6dba20b` | Swift 授权 11/11；Android 授权 10/10；identity 模块及 Android lintDebug | provider 独立权威 epoch、同域撤销/新 grant 拒绝、声明不可改绑、ownership 独立授权、严格快照恢复。独立复审实现可接受，LOW issuedAt 测试缺口已补；candidate 持久化接入见下行 |
| Task 2 后端停止子步骤：per-operation stop signal | `0d401fb` | Swift stop 8/8；Android stop 8/8；identity 59/59、57/57 及 Android lintDebug | 幂等停止、锁外 native stop 回调、停止前后检查、停止覆盖普通后端错误；致命 JVM Error 透传。独立复审 APPROVE；尚未接入 Store 在途 ticket/close drain |
| Task 2 candidate Store 子步骤：持久授权绑定与 admission | `ea8e92b` | Swift Store 9/9；Android Store 9/9；identity 68/68、66/66 及 Android lintDebug | actor/synchronized 门内 staged candidate 状态在 journal 提交后发布；恢复持久 storeId/授权epoch/immutable grant；发布前重检；密钥失败退休 writer。独立复审无实现缺陷，Android 并发/恢复测试缺口已补齐 |
| Task 2 持久撤销子步骤：域 epoch、目标 tombstone 与稳定回执 | `8230437` | Swift Store 19/19、identity 78/78；Android Store 18/18、identity 75/75；Android lintDebug | 独立 ownership、同域多 grant 共用回执、跨 epoch 目标去重、v1→v2 恢复、严格目标集校验、失败提交退休。Android 回调重入拒绝。独立审查 APPROVE、无遗留发现；尚无在途任务停止、模板 purge 或 close drain |
| Task 2 operation ticket 子步骤：撤销停止、发布抑制与 close drain | `ab7a4bc` | Swift Store 28/28、identity 87/87；Android Store 28/28、identity 85/85；Android lintDebug | ticket 固定 candidate/grant/scope；撤销或新 epoch 先提交再锁外 stop；旧 backend error 被 authorizationRevoked 抑制；跨域/跨 store 拒绝；close 等待 drain，Android 中断后仍释放 writer。独立复审 APPROVE；模板/lease/purge 尚未实现 |

enrollment/template 已提交 `0bc6761`：Swift 100/100、Android 102/102，Android identity lintDebug 通过，独立审查 APPROVE（无 Critical/Important 发现）。root 验证日志为 `/tmp/odai-p10-enrollment-accept-{swift,android}.log`；审查者另跑 Swift Store/payload 41/41，未能访问 Gradle 缓存，不替代 root 的 Android 验证。测试使用合成夹具及临时内存密钥，不包含个人生物数据、模型权重或商用精度结论。两端测试组织粒度不同，总数不表示平台行为差异。

Android 原生 Keystore envelope 已在 TECNO KM9 API 35 真机通过，[探针记录](p10-android-keystore-probe.md)列出密钥不可导出、合成数据 round-trip、篡改拒绝与删除后禁止重建的结果；不替代完整 Store、API 26 或 iOS Keychain 验收。

## 可复现命令

```sh
rtk proxy env CLANG_MODULE_CACHE_PATH=/tmp/odai-p10-clang-cache SWIFTPM_MODULECACHE_OVERRIDE=/tmp/odai-p10-swift-cache swift test --disable-sandbox --package-path apple --filter ODAIKitIdentityTests
rtk proxy ./gradlew :android:identity:testDebugUnitTest :android:identity:lintDebug
```

最新完整 Apple 包在正常本机权限下运行 `/tmp/odai-retirement-swift-full-native-final.log` 通过 206/206（Speech 31、ModelStore 13、Identity 148、Face 9、Core 5），包含真实 Vision 空白图测试；沙箱内全包仍出现 Vision Code9（identity 148 项通过）。独立 retirement/frame scoped 复审已通过，详见文末。

## Task 2 待实现

- 已有可信 provider 契约及不可变授权状态，后续 Store 构造仍必须注入 provider 和持久化根目录；禁止以 request epoch 或单纯内存字典作为权威状态。
- journal 已接入 candidate/template admission；模板采用独立 envelope，持久 stage 所有权与故障恢复已接入；内部 vector lease 和模板单目标 purge 已通过单测；candidate/domain 固定目标集及物理清理也已通过单测。
- candidate/template 与 immutable grant 的持久绑定、域撤销 tombstone 和稳定 revoke receipt 已实现；模板单目标删除和逐目标 purge 状态已通过单测；candidate/domain 固定目标集和共享逐模板 purge 状态已实现。
- admission/revoke/template publish 共用串行门；quality/embed 在门外执行，撤销立即请求停止。模板单目标已接入 lease 后两阶段删除，candidate/domain 清理已接入，精确在途模板绑定也已通过本地测试。
- operation ticket 已固定 grant/candidate 并接入 public enroll、撤销 stop、结果抑制与 close drain；任务对象的完整 Core 终态封装仍需后续整合。
- 单目标已覆盖持有多 lease 删除、purge 失败/重试/重启；candidate/domain 全目标集已有覆盖；deadline/cancel 等待已在 `80d887e` 完成；退休锁 drain 与 Swift 多 handler 交错回归已通过，独立 scoped re-review PASS；已有 scope/expiry、同域新 grant ID 绕过、enrollment 撤销竞态和正常 close lease drain 测试不能替代它们。
- 平台原生密钥与恢复行为真机验证；iOS 15/API 26 最低设备验收另记。

只有这些门全部通过，才能标记 Task 2 完成。后续 Task 3/5/6/7 的 snapshot、身份引擎和活体状态机尚未实现。

### candidate Store 当前边界

- Swift actor / Kotlin synchronized 门负责候选 admission、journal 持久化及发布；创建候选不会调用生物后端，也不存储图像/向量。
- 元数据目前使用严格 64 KiB JSON 格式（包含授权快照），超限在提交前拒绝；后续模板/索引规模需另行设计存储格式。
- Kotlin public open 只使用 Context.noBackupFilesDir 内的受管目录；其路径构造尚无 Context/真机验收，单测使用 internal 临时目录入口。
- close 已请求 operation ticket stop，并等待 operation ticket 和内部 template lease 同时 drain。当前 retirement 差异将 journal poison 与物理解锁分离，writer 保持到 operation、lease 和 stop delivery 全部 drain；旧/关闭 Store 的回调只修改旧内存。双端已通过专项复审，Swift 多 handler 派生 Task 的 close 等待边界已有 RED→GREEN。

### 持久撤销当前边界

- 回执仅包含 `id` 与 `storeId`，不是物理回收完成证明。加密元数据记录固定的 grant/candidate 目标集，重复撤销及重启后均返回原回执；重放旧回执不会撤销新 epoch 的对象。
- 元数据严格校验完整 grant/candidate 目标集、候选所属关系、重复目标和域 epoch；仍可读取 v1/v2/v3 记录，下次提交写 v4（含 templates/stages/deletions）。
- 撤销提交成功后才发布内存和回执；提交失败退休 writer。operation ticket 和 enrollment 发布已接线；gallery/lease/purge 的撤销链路尚未验收。
- Android 门内宿主回调若重入 Store，返回 `busy`，防止重入提交被外层 staged 状态覆盖。

### operation ticket 当前边界

- ticket 只在 Store 内部存在，固定 candidate、完整 grant、scope 和 process-local stop signal；`finishOperation` 总会消费它，并在撤销、expiry、关闭或 backend error 时重新取权威状态。
- 撤销及可信更高 epoch 的 candidate admission 在持久提交后标记相关 ticket；stop handler 在 actor/monitor 外执行。close 拒绝新 admission，等待 ticket 释放后才关闭 journal；Android 被中断时继续 drain 并恢复中断标记。
- public `enroll` 已将票据连接到门外 quality/embed 和门内最终模板发布；阻塞后端测试覆盖撤销抑制成功、低质量和普通后端错误。内部 lease 与模板单目标 purge 已实现，精确模板在途绑定见下节；后续身份引擎仍未实现。

### enrollment/template 当前边界

- `EnrollmentOptions` 要求显式 qualityFloor、qualityPolicyId 和 revision；SDK 不提供数值门槛，也不将该质量规则当作身份判定校准。空脸/无质量分数/低质量返回 insufficientQuality，多脸返回 multipleFaces；不保存图像或返回向量。
- backend 实例由宿主选择，固定实际 backend/version/model/embedding space，质量和 embedding 后重验；显式不可用或不兼容不回退。成功结果包含 opaque templateRef、质量、输入/选项/preprocessing revision、session/run ID 与单调时钟耗时。
- v3 journal 在模板密钥/文件创建前登记 stage UUID；模板独立 AES-GCM key 与 AAD 绑定 store/record，文件同步后才提交 membership。门内最终检查和元数据发布不拆开。
- 存储失败保留持久 stage 并退休 writer；重启先验证全部元数据再逐项清理 stage 文件和密钥，成功后清除 manifest。不存在按前缀扫描删除模板目录的操作；未登记文件保留。
- 已覆盖密钥创建失败、payload 后 journal 提交失败、篡改/换包/缺钥、完整 space 绑定、同域另一 grant 恢复、stage 重复/重叠和无效 membership。模板 envelope 的持久 codec 为平台内部实现，不构成跨设备特征导出接口。
- 模板单目标后续切片已加入 DeletionStatus/receipt、物理 purge 和重启重试。真实 Keychain/Keystore 和最小系统版本仍须真机验证。

### template lease 当前边界

- 实现提交 `9bb91e7`，独立复审 APPROVE（无遗留 Critical/Important 发现）；这不构成 Task 2 完成。
- 内部 `acquireTemplateLease` 仅供 verification/identification 使用：校验完整 template ref、embedding space 的能力集合、scope、purpose、同域同 epoch 以及可信 provider；解密后重验授权再 pin。允许同域另一有效 grant，其完整声明持久保存以支持撤销和重启绑定校验。
- lease 的向量只在模块内部读取，close 后清除 lease 自身持有值并拒绝读取；已经由 SDK 消费者取出的值仍须任务 defer/finally 释放，不宣称内存覆写。release 幂等；活跃 writer 的最后一个 lease 可触发精确 purge，关闭/退休 writer 的 release 不执行 I/O。
- 最新身份模块单测：Swift 107/107（lease 7 项），Android 110/110（Store 47 项）及 lintDebug。日志 `/tmp/odai-p10-lease-{swift,android}-final.log`。审查提出能力集合缺口，双端 verification-only 模板回归先 RED 再 GREEN；初轮 Android 宿主回调重入覆盖撤销状态也已 RED→GREEN。
- 全 Apple 包本轮 `/tmp/odai-p10-lease-swift-full.log` 仍有既有 `VisionFaceDetectorTests.testBlankImageUsesRealVisionAndReturnsSuccessfulEmptyResult`（Vision Code 9）；身份模块 106 项在该全包运行通过，随后新增能力回归的定向身份模块 107 项通过。未宣称全 Apple 包通过。

### 模板单目标删除当前边界

- 本切片独立架构审查及代码复审 APPROVE（0 findings）；不构成完整 Task 2 验收。回执固定 `id/storeId/templateId`，其完整 ref、candidate/grant/space/revision 绑定保留在加密元数据，清理后仍拒绝伪造引用和跨 store 回执。
- 每次删除（包含重复请求）均验证独立 ownership，不要求生物 grant 有效；先持久化 `purgePending` tombstone，再在无 lease 时删除精确 payload/key，最后提交 `purged`。文件/密钥失败提交 `purgeFailed`，重启按固定 tombstone 重试。
- 所有恢复元数据校验完成后才清理；不会按目录扫描删除。正常运行的最后一个 lease release 可触发清理；关闭/退休 Store 的旧 release 不再执行 I/O。物理完成后的 journal 失败不发布成功状态，writer 退休后重开从 durable tombstone 重试。
- `awaitDeletion(receipt)` 读取已提交状态；带单调 deadline 的等待在超时返回当前 `purgePending`，取消映射为 `cancelled` 且不撤回 tombstone。Swift Task cancellation、Android interruption（含中断标志恢复）均有回归覆盖；candidate/domain 所有目标清理和精确模板 ticket 绑定已实现。
- 最新单元测试 Swift 116/116、Android 121/121 及 Android identity lintDebug；覆盖多 lease、清理失败恢复、伪造/独立所有权、未提交逻辑删除不清理、物理清理后提交失败、全元数据验证先于恢复 I/O、旧 writer 回调隔离。日志 `/tmp/odai-p10-deletion-final-swift.log`、`/tmp/odai-p10-deletion-android-reentry-final.log`。
- 全 Apple 包 `/tmp/odai-p10-deletion-swift-full.log` 身份模块 116 项通过；仍只有既有 `VisionFaceDetectorTests.testBlankImageUsesRealVisionAndReturnsSuccessfulEmptyResult`（Vision Code 9）失败。未宣称全包通过，未跳过该测试。

### candidate 删除当前边界

- v5 元数据持久化 candidate tombstone 和固定模板目标集；空 candidate 也保持删除状态。`DeletionReceipt` 严格区分 template/candidate 目标，重复请求重验独立 ownership，旧 template receipt 继续查询共享 purge 状态。
- 删除事务先提交全部 tombstone 与缺失的逐模板清理事实，再在门外停止该 candidate 的在途 ticket，随后尝试精确 purge。持有 lease 时保持 pending；清理失败可重启重试，其他 candidate 不受影响。
- Swift 审查修复了全局 receipt ID 冲突分配及未排序目标集的恢复拒绝，回归先 RED 再 GREEN。最新 identity 单元测试 Swift 125/125、Android 131/131 及 lintDebug 通过；日志 `/tmp/odai-p10-candidate-review-final-{swift,android}.log`。
- grant/domain 物理清理、精确模板 ticket、带 deadline/取消的等待已完成；完整退休 writer drain 和真实平台 provider 验证仍待完成。全 Apple 包仍单列既有 Vision Code 9 环境失败，不宣称全包通过。

### grant/domain 物理清理当前边界

- v6 撤销记录保存不可变的精确模板目标集；`awaitDeletion(RevocationReceipt)` 查询与 template/candidate receipt 共享的逐模板 purge 状态。回执和别名重启后保持稳定，新 epoch 的模板不会加入旧回执。
- revoke 将 epoch、固定候选/模板目标与缺失的清理事实放进同一提交。可信 provider 提升 epoch 时，createCandidate 同样提交旧域模板清理事实；相关 stop 回调在门外执行，结束后才清理。逐模板计数阻止并发 createCandidate、重复 revoke 和 lease release 抢跑未结束的 stop 交付。
- v1–v5 恢复先校验完整元数据，再从不可变 membership 推导目标与清理事实，并提交 v6 后才开始物理清理。v6 在清理前拒绝伪造/重复目标、缺失 fact、跨域 membership 与 receipt 碰撞；不扫描目录删除模板。
- 最新 identity 测试 Swift 136/136、Android 142/142 及 Android lintDebug 通过。双端 stop 交付竞态、Android legacy migration/重复 receipt/无回执可信 epoch 清理均有 RED→GREEN 记录。日志 `/tmp/odai-p10-domain-{swift,android}-final.log`，全 Apple 包 `/tmp/odai-p10-domain-swift-full.log` 仍单列既有 Vision Code 9 失败。
- 本切片独立跨端复审 APPROVE（0 findings）。提交前 Swift identity 136/136；Android identity 142/142（XML 失败/错误/跳过均为 0），Gradle test/lintDebug 再次成功（命中已有验证缓存）。并发回归覆盖 stop 回调未完成时同 epoch 清理及 lease release 禁止提前 purge。
- 本切片不完成 Task 2：journal 退休后的完整 lock drain，以及真实 Keychain/Keystore 与最低系统版本设备验证仍待完成。

### 停止信号当前边界

- 每次后端调用可接收独立 stop signal；原生适配器可登记实际底层 stop 回调。
- 默认适配器检查原同步 primitive 的执行前后，不能声称已中断不可取消的同步 native 工作。
- 停止请求的第一个原因保持稳定；已被请求停止时晚注册的回调立即调用，回调执行不持有信号内部锁。注销只撤回尚未被请求方领取的回调；SDK 使用方需 defer/finally 关闭 registration。
- stop signal 已连接 Store 在途 ticket、enroll 和 close drain；默认同步 adapter 仍只做边界检查，真实 native 停止能力需逐后端验收。

### 授权状态当前边界

- 内部版本化授权快照沿用严格 contract parser，编码与恢复均限 64 KiB；超限拒绝编码，后续 Store 不能先发布内存再尝试持久化。
- 授权状态提供纯操作；Store 已接入候选/模板持久化、公开 revoke API 和 receipt。模板所属 candidate/domain/epoch 不可换绑。
- 授权声明与 epoch 保存在快照内，candidate Store 已将其包进已认证 journal，不能写入明文或日志。

### journal 当前边界

- 内部 record ID 长度为 1–64 字节，加密记录总长不超过 16 MiB；拒绝超限输入和文件。
- AAD 绑定 canonical root 路径和 record ID；移动目录会拒绝解密。接入真实应用前仍需验证平台容器路径变化的影响，不能推断为可迁移存储。
- 加密写入临时文件先同步，再同目录原子替换并同步父目录；异常会退休当前 writer。已有测试覆盖密钥拒绝、旧记录保留、关闭/重开、篡改和恢复；真实进程终止及设备文件系统故障注入仍待后续验收。
- Swift 根目录显式排除备份；Android primitive 为 internal，生产 IdentityStore.open 将目录限定在 `noBackupFilesDir` 内，真实 Context 路径仍待设备验证。
- 未认证、损坏或不属于此 record 的临时文件会保留，不按名字删除。未提供全盘清理或闪存擦除承诺。

## 生产门槛

[模型预研](p10-identity-model-selection.md)没有选型或下载权重。Task 9 的商用模型许可、授权数据和身份阈值，以及 Task 10 的 PAD 后端/攻击数据/校准仍为独立门槛。P10 与整个 roadmap 均未完成。

## 加密平台依据

- [Apple AES.GCM](https://developer.apple.com/documentation/cryptokit/aes/gcm)：框架随机 nonce 与 AAD 认证。
- [Keychain device-only 可访问性](https://developer.apple.com/documentation/security/ksecattraccessiblewhenunlockedthisdeviceonly)与[非同步设置](https://developer.apple.com/documentation/security/ksecattrsynchronizable)：解锁访问、不迁移、不 iCloud 同步。
- [Android KeyGenParameterSpec](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec)与[GCMParameterSpec](https://developer.android.com/reference/javax/crypto/spec/GCMParameterSpec)：AES/GCM/NoPadding、256 位 key、128 位 tag。
- [Android noBackupFilesDir](https://developer.android.com/reference/android/content/Context#getNoBackupFilesDir())：后续存储根应位于该目录，持久化相对路径。

这些资料只约束实现，不替代真实设备和故障恢复验收。

### 精确模板 operation ticket 当前边界

- 内部 ticket 将完整 Store 验证过的模板引用固定为精确 ID 集合；拒绝伪造、跨 candidate 或已删除 target，完成前重检每个 target。单模板删除只停止与该模板相交的 ticket，不影响同 candidate 其他模板或未绑定模板的 enrollment ticket；candidate/domain 删除继续停止各自固定范围。
- 单模板 tombstone 提交后，stop 回调在 actor/monitor 外执行；共享 pending-stop 计数保护覆盖并发 candidate/domain 清理与 lease release，Android 重复删除不会释放其他请求的计数。撤销后普通 backend error 被 authorizationRevoked 抑制。
- 独立 scoped re-review APPROVE（两项初轮 HIGH 均已关闭）。Swift identity 139/139，Android identity 146/146 与 lintDebug 通过；回归先复现 stop 交付期间提前 purge、Android 持锁回调及重复删除计数问题，再修复通过。日志 `/tmp/odai-p10-template-ticket-final-{swift,android}.log`。
- 本切片不包含退休 writer 完整 drain、后续 gallery/身份引擎或生产精度验收。Task 2 与 roadmap 继续实施中。

### Task 2 writer retirement / drain（2026-10-18）

- `IdentityJournal` 的 poison 与 physical close 已分离：读/写故障进入 fail-closed admission，但保留 writer；只有显式 close 且 Store 的 operation、template lease、stop delivery 全部 drain 后才释放 writer。
- Store fault、正常 close、并发 close、lease/ticket 两种完成顺序、空目标 stop delivery、Android 中断恢复、旧回调重开隔离均有回归。Swift 还覆盖同一 stop signal 多 handler、多个 signal 交错、晚注册和 handler 派生 Task；每个 handler 使用独立 active frame，handler 返回后派生 Task 不能跳过 drain。
- RED/GREEN：Swift frame 修复前两项交错回归各失败，修复后 identity **148/148**；Android **153/153**，失败/错误/跳过均为 0，`lintDebug` 通过。完整 Apple 原生权限运行 **206/206**（Speech 31、ModelStore 13、Identity 148、Face 9、Core 5），Vision 空白图测试本次成功；沙箱运行的 Vision Code9 只作为环境差异记录。
- 独立 scoped re-review `task-2-retirement-frame-rereview.md`：spec compliance PASS、quality PASS，无 Critical/Important 遗留。Android 编译仍有既有 nullable/unchecked-cast 警告，lint 无错误。
- 该切片仍不等于 Task 2 完成：Android 完整 `Context.noBackupFilesDir` Store probe、iOS Keychain Store 真机闭环、API26/iOS15 设备门禁和进程终止/文件系统故障注入仍单列；不以单元测试替代这些证据。
