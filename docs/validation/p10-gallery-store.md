# P10 Task 3：不可变图库与删除恢复

2026-10-19。原生实现与本地验收已完成，可继续 Task 5；P10 与 roadmap 尚未完成。

## 实现与证据

| 契约 | 当前证据 |
| --- | --- |
| 不可变 revision、内容摘要、成员授权域/epoch、完整 policy 与 store 绑定 | 双端 `GallerySnapshotTests` / `GallerySnapshotTest` 与共享 canonical fixture |
| single-writer/single-use builder、base-revision CAS、构建失败保留旧 revision | 双端 `IndexStore` 测试；失败 builder 不能复用 |
| 显式移除失效成员、policy 升级 | 旧 snapshot 不自动过滤；新 revision 必须通过所有保留成员的最终授权检查 |
| read/rebuild 使用原 snapshot 操作授权 | gallery ticket 固定 requiredGrants；撤销原操作域会停止已有读 lease，并拒绝新读或 rebuild |
| 派生 payload 生命周期 | read lease 固定实际 payload；rebuild 保持原 GalleryRef；冷重开返回 rebuildRequired |
| 删除整个 lineage、保护共享模板与其他 lineage | 逻辑 tombstone 先提交；活动 build、read lease、stop delivery 排空前保持 purgePending |
| 清理失败与重开恢复 | 双端新增公共 IndexStore 测试：marker 写入失败、marker 成功后 removal 失败；所有 receipt 仍可查询，其他 lease 的 payload 保留，下一次重开完成清理 |
| v2 容量迁移 | 双端构造 65,530-byte 合法 v2 Catalog；无法容纳 v3 marker 时在 journal 写入前拒绝，原密文字节不变；较小 v2 的成功升级另有测试 |
| deadline/cancel/close | 等待只观察已提交状态；取消不恢复删除；关闭后拒绝观察；writer 锁保留至排空 |

### 最终 CAS、缓存和删除的顺序

Swift 的最终授权检查与 Catalog CAS 在 IdentityStore 的同步发布闭包中完成。IndexStore 随后恢复 actor 并安装缓存，因此需要同时审查两种顺序：

- 删除先提交：Catalog tombstone 拒绝旧 builder 的 CAS。
- CAS 先提交：即使删除在缓存安装前进入，`activePublishes` 仍阻止物理清理；新读被 tombstone 拒绝，发布 defer 在所有模板 lease 释放后再执行清理。
- 关闭进入：`inFlight` 和 stop-delivery 计数保持 writer；最终发布排空后 `finishClose` 释放缓存。

新增 Swift 测试用已有 key provider 确定阻塞最终 Catalog commit，确认关闭已经进入、第二个 writer 被拒绝、提交完成后缓存释放且持久 snapshot 可恢复。这个测试覆盖关闭与最终提交重叠，**不是**对 fsync 完成到 actor 恢复之间的删除顺序进行确定性调度；后一窗口由上述不变量审计与既有活动发布删除测试共同支撑。没有添加生产测试钩子。Android 另有缓存安装前的 closed/deleted guard。

## 本轮验证

- Apple 完整原生测试：276/276；Identity 218、Speech 31、ModelStore 13、Face 9、Core 5。日志 `/tmp/odai-task3-audit-all-apple-native.log`。沙箱运行仅既有 Vision 空图用例 Code 9 失败，原生运行通过。
- Android identity：220/220，failures/errors/skips 均为 0；`lintDebug` 通过。日志 `/tmp/odai-task3-audit-all-android.log`。
- 新测试先在既有实现通过，再执行临时 mutation：省略关闭缓存释放、恢复失败误报 pending、放宽 Catalog 容量限制分别被 Swift 新测试捕获；Android 的恢复状态与容量 mutation 也失败。生产源码已完整还原；本轮代码 diff 只有测试。
- `git diff --check` 通过；用户 Xcode 工程改动与未追踪草稿保留，不纳入本轮提交。

复现命令：

```sh
rtk proxy env CLANG_MODULE_CACHE_PATH=/tmp/odai-p10-clang-cache \
  SWIFTPM_MODULECACHE_OVERRIDE=/tmp/odai-p10-swift-cache \
  swift test --disable-sandbox --package-path apple
rtk proxy ./gradlew :android:identity:testDebugUnitTest --tests '*' :android:identity:lintDebug
```

## 审查与边界

独立 code-reviewer 本轮再次在返回审查前遇到 HTTP 429，不能视为批准。作者已另做 Task 3 范围的只读审查；独立复审仍是合入/发布前的未完成门槛。

满容量旧 v2 journal 只能安全拒绝超限 marker，不能将其计为已自动完成清理。当前新 publication 已预留 receipt 与 marker 空间。进程终止、文件系统故障、最低系统及真实模型/校准/设备验收仍分别保留；本地 fixture 测试不代表生产身份识别或活体能力已交付。
