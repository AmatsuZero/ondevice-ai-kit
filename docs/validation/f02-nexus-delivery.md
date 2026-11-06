# F02 内部 Nexus 交付准备

2026-11-06，用户提供内部 Maven 服务 `https://nexus.ugaiot.com`。
当前结论为 **渠道已发现，制品尚不可发布**；本页是交付准备，不是发布批准。
按用户要求 solo 执行，未使用或保存提供的凭据，未上传、覆盖、删除制品或改生产依赖。

## 已核实与待核实

匿名 `/service/rest/v1/repositories` 返回200，清单确认以下两个入口存在：

| 用途候选 | 类型 | URL |
| --- | --- | --- |
| 发布 | Maven hosted | `https://nexus.ugaiot.com/repository/maven-ugreenhome-release` |
| 消费 | Maven group | `https://nexus.ugaiot.com/repository/UgreenHome-Maven-group` |

名称和类型只能支持候选方案；用户没有明确选定具体仓库或维护团队。
清单未给出 Maven version/write policy。匿名 group 配置 GET 返回403且响应体为空，
组成员未知；hosted 配置 GET 在执行前遇到自动审批服务429，未发出该请求，策略未知。
没有改用其他入口绕过审批，也没有重试失败审批。

匿名 search 对 `repository=maven-ugreenhome-release`、`group=org.jetbrains.skiko`、
`version=0.144.6-ondevice-f02.1` 返回200、空items、空continuationToken。
这仅说明匿名可见搜索结果中没有该版本，不能证明全库不存在或具备写权限；发布前须重新核对
精确制品路径和所有拟发布坐标，不覆盖现有版本。

原始响应及只读校验入口保存在
`artifacts/verification/nexus-skiko-readonly-20261106/`，摘要为 `discovery-summary.json`。
TLS校验保持开启。没有制品下载；本地109 MB归档仅做SHA256和文件数复核。

## 可审阅的交付方案

1. 由维护团队确认上述 hosted/group 是否为本项目正式出口，并核实group成员、读取范围、
   release-only及禁止覆盖策略。当前没有维护团队指派，不从CI账号名推定。
2. 专用版本只递增；已发布版本不移动、不覆盖、不复用。修复使用新版本，撤销以消费者
   禁用／升级和登记原因处理，不静默替换旧字节。此为拟采用约定，尚非服务器强制策略的证据。
3. CI通过受控secret注入凭据；源代码、命令参数、报告及归档均不携带凭据。
4. 从固定源码产生受支持的完整publication并定稿同版本许可附件，验证每个坐标／依赖／
   checksum。发布前形成精确文件清单；获授权发布后，从空缓存独立消费者核验远端字节、
   Gradle variant、两份iOS Klib及实际App，不能沿用本地消费结果代替。

## 当前制品门槛

既有 `skiko-0.144.6-ondevice-f02.1-maven-repo.tar.gz` 保持原样：109,379,413 B，
125文件，SHA256 `d042a71f6a931320f367387580341f6d05b404196099d51d969308a0a0c2e480`。
其四模块包含AWT，但AWT POM引用七个缺失runtime；严格双iOS metadata构建失败。
AWT/Web/Android编译锚点各有额外发布义务，不能手改生成metadata后上传。
第三方许可候选材料亦未定稿，详见 [分发审查](f02-distribution-review.md)。

服务器可达不关闭上述技术门槛，也不关闭独立审查、生产资源和P4/P11。
当前F02和发布仍 `open/not-ready`，本轮0新benchmark、0验收项通过，无设备操作。
