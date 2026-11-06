# F02 内部 Nexus 交付准备

2026-11-06，用户提供内部 Maven 服务 `https://nexus.ugaiot.com`。
当前结论为 **渠道已发现，制品尚不可发布**；本页是交付准备，不是发布批准。
按用户要求 solo 执行，未使用或保存提供的凭据，未上传、覆盖、删除制品或改生产依赖。

用户确认 **Nexus拉取无需鉴权**。消费者使用匿名HTTPS读取，不配置用户名、密码或token；
发布凭据仅用于上传。管理API的403不代表制品下载需要鉴权，不再作为消费权限缺口。
制品就绪后的远端消费验证仍需检查实际依赖解析、字节摘要与编译结果。

用户最新明确：提供的账号**可上传、可覆盖、不可删除**。账号权限以该说明为准，
不再作为待确认项，也不再为此查询管理API。禁止覆盖既有release是发布流程约定，
无需服务器禁止覆盖；无删除权限不妨碍新增版本发布。消费可先使用hosted直连，
group成员查询不作为打包修复的前置条件。当前实际阻塞是包的依赖闭包和许可附件；
远端连接与独立消费在制品准备完成后验证。

用户随后明确授权一次 hosted 策略 GET 重试。该次审批通过，实际 curl 退出35：
`Recv failure: Connection reset by peer`，HTTP000，无HTTP响应或策略结论。
授权次数已用完，不自动重试；回执为同证据目录的 `authorized-retry-outcome.json`。
以下429记录是首次未执行的历史结果，group403和其他分发门槛不变。

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
这仅说明匿名可见搜索结果中没有该版本，不能证明全库不存在；写权限已由用户明确，发布前须重新核对
精确制品路径和所有拟发布坐标，不覆盖现有版本。

原始响应及只读校验入口保存在
`artifacts/verification/nexus-skiko-readonly-20261106/`，摘要为 `discovery-summary.json`。
TLS校验保持开启。没有制品下载；本地109 MB归档仅做SHA256和文件数复核。

## 可审阅的交付方案

1. 上述hosted为发布候选，也可直接作为消费入口；group成员与维护责任在交付时记录，
   不以管理API可读或服务器禁止覆盖为技术修复前置条件。当前没有维护团队指派，不从CI账号名推定。
2. 专用版本只递增；已发布版本不移动、不覆盖、不复用。修复使用新版本，撤销以消费者
   禁用／升级和登记原因处理，不静默替换旧字节。此为拟采用约定，尚非服务器强制策略的证据。
3. CI通过受控secret注入凭据；源代码、命令参数、报告及归档均不携带凭据。
4. 从固定源码产生受支持的完整publication并定稿同版本许可附件，验证每个坐标／依赖／
   checksum。发布前形成精确文件清单；获授权发布后，从空缓存独立消费者核验远端字节、
   Gradle variant、两份iOS Klib及实际App，不能沿用本地消费结果代替。

## 当前制品门槛

2026-11-06新候选 `0.144.6-ondevice-f02.2` 已通过正常双iOS-only Gradle发布：
3模块/100文件/6引用闭合，独立consumer及SDK Compose双架构framework链接成功。
归档108,176,625 B，SHA256 `631806c3cf1bee881a41e2b99da4490e368dc37aa0f7d2057f376a4d683eb2de`。
[专用补丁和复现](../../probes/skia-atlas-text-cache/Skiko-ios-only.md)关闭此前metadata/AWT技术阻断，
无需扩展桌面/Web/Android发布范围。

冻结.2尚缺修改源文件声明及完整许可交付；声明补丁、23上游文本、2Unicode参考和14份libjpeg
内联声明已备好，文件/头文件与生成数据审查、正常发布附带和consumer保留仍需完成。
下一候选使用新版本，不修改冻结.1/.2；详见[分发审查](f02-distribution-review.md)。
旧.1归档109,379,413 B/125文件/SHA256
`d042a71f6a931320f367387580341f6d05b404196099d51d969308a0a0c2e480` 保持原样。

Nexus制品拉取按用户说明使用匿名HTTPS；现有账号权限无需再次确认或查询管理API。
未使用发布凭据，无上传、覆盖、删除或生产切换；远端匿名消费待制品就绪后单独验证。
本轮没有设备操作/新benchmark，F02和发布仍open/not-ready。
