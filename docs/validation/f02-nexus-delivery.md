# F02 内部 Nexus 交付准备

用户后续回答“我本人”，已确认Skiko与Apple包由用户本人维护，见[维护归属](delivery-maintenance.md)。
下文各历史“维护团队未定”不再是当前待答问题；组织名称与具体hosted选择仍未指定。
本决定没有触发新上传或修改冻结候选。

## 2026-11-06：.4 精确坐标预检与输入校验补丁审查

当前仍使用冻结的`0.144.6-ondevice-f02.4`作为交付候选，归档108,785,484 B，
SHA256为`e59efab02e31d2f609e7328b5f8118c86e47e47029c74c4437a70c2b9dc68869`。
已从正常publication图形成115个版本文件的逐项URL/字节/SHA清单，本地全部匹配；
明确排除15个可变模块级metadata/sidecar。对已发现hosted候选
`https://nexus.ugaiot.com/repository/maven-ugreenhome-release/`的115个精确路径
逐项匿名HEAD，均404且无重定向；未使用凭据、管理API或任何写操作。
这是时点读证据，不能当作原子不覆盖保证、目标仓库最终选定或上传批准。
计划与观测：`artifacts/verification/f02-nexus-dot4-preflight-20261106/upload-plan.json`。

第四输入校验补丁和回归工具已取得新的限定native独立审查APPROVE/0 findings，
见[校验审查边界](f02-notice-validation-20261106.md)。它不在冻结.4发布脚本或旧agy七文件
审查中；不因此重写.4或称完整.5已发布。公开条款提取经父代理逐条核验，错误解释已否决；
历史来源适用、维护归属、具体hosted选择、发布授权与远端匿名消费者验收仍开放。

## 2026-11-06：.4 补齐 HarfBuzz ISC 并修复根项目收集

[HarfBuzz 核对与 .4 交付](f02-harfbuzz-notices.md)：实际编译的 hb-ucd.cc 有独立 ISC
授权/版权，冻结 .3 材料曾遗漏；新 .4 保留 115 个原样文件头及生成输入来源记录，材料增至
72 份。正常发布、实际 SDK 双 iOS 链接、unsigned App 构建及 72 文件逐字节保留已通过。
收集脚本显式声明 common/kotlin-metadata 属性，修复未应用 KMP 插件的 SDK 根项目解析失败。
.3 通过的打包检查不能证明许可完整；.1/.2/.3 均不改。原生 40 份库摘要不变。
仅本地交付验证，无上传/生产切换/装机。Microsoft/SIL/历史数据条款、独立许可批准、远端
匿名消费、维护责任和生产资源仍待闭合；Nexus 匿名拉取及发布权限已明确，不再询问。


## 2026-11-06：本地 App 材料保留已证明

[unsigned App 证据](f02-app-notices.md)确认 .3 候选69文件通过正常Xcode资源阶段保留，
并非仅Maven解析；下方最终App保留未验为历史。Nexus匿名拉取/发布权限无需再确认，
本轮无上传或远端消费。条款审核、远端实际消费与生产资源验收仍未完成。

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

最新冻结候选 `0.144.6-ondevice-f02.3` 已通过正常双iOS-only Gradle发布：
3模块/115文件/6引用闭合，69文件许可材料在3份notices及3份source JAR内完整存在。
修改源码声明已核实，独立消费者显式取件并保留69文件、双iOS框架链接通过。
归档108,522,716 B，SHA256 `3276597b2f422091ee13a5854c79625d5248ca49c54fa9d1c136eb427836ca93`。
[发布和消费者步骤](../../probes/skia-atlas-text-cache/Skiko-ios-notices.md)已跟踪。

文件级/历史Unicode条款和独立许可批准、最终App材料保留、远端匿名取件/编译与生产资源
仍未验；不把正常发布/材料收集当作合规裁定或远端通过。
冻结.1/.2/.3保持原样，后续制品修改使用新版本；详见[分发审查](f02-distribution-review.md)。

Nexus读取按用户说明使用匿名HTTPS，发布账号上传/覆盖/不可删除权限无需再次确认或查询
管理API。未使用发布凭据，无上传、覆盖、删除或生产切换；远端匿名消费待制品就绪后验证。
本轮没有设备操作/新benchmark，F02和发布仍open/not-ready。
