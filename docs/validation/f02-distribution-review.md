# F02 固定 Skiko 候选分发审查（2026-11-03）

状态：**本地候选，未批准对外分发或切换生产依赖**。本记录只检查归档的内容与许可材料可追溯性，不裁定许可合规。

## 已复核的候选

- 版本 `0.144.6-ondevice-f02.1`，固定 Skiko `9a5b398bb2044fff7e7a84fbfd6f4b803e4427c0`、Skia `22f58c9fd43d55bde818821c04b48fda5d7ec939`，修复补丁 SHA-256 `c4ba9412811a6c68df3f3c9e14e53cb684c7eb849f3f16c0f683c10f6b55fc30`。
- ignored 本地归档 `artifacts/verification/f02-versioned-consumer-20261103/skiko-0.144.6-ondevice-f02.1-maven-repo.tar.gz`：109,379,413 B，SHA-256 `d042a71f6a931320f367387580341f6d05b404196099d51d969308a0a0c2e480`。重新运行 `verify_versioned_archive.py`，125 个常规文件与正常本地 BuildRepo 发布摘要逐项匹配，另有 4 个目录成员。
- 归档只含 root、AWT、iOS arm64、iOS simulator arm64 四个 Maven 版本目录。四份 POM 均声明 Apache-2.0。归档成员路径中没有 LICENSE/NOTICE；逐个检查包内 16 个可读取的 JAR/Klib 的条目路径，也没有 LICENSE/NOTICE。路径扫描不证明二进制中不存在版权信息。
- 固定源码的 Skiko `LICENSE` 为 Apache-2.0，`NOTICE` 提及 Android Open Source Project；Skia 根 `LICENSE` 为 BSD 风格，明确要求二进制分发随文档或其他材料重现版权声明、条件和免责声明。对应本地文件 SHA-256 分别为 `c71d239df91726fc519c6eb72d318ec65820627232b2f796219e87dcf35d0ab4`、`35dd077c24768b53625101cfcc0ad853ae7e2abc08bd2a111a0267760d1406d8`、`5f787c1dee3c56547f09ccc2906ab5f5293c4d8dd6c8654e573216c38e908dbd`。源码树还有多个第三方许可文件；尚未按实际链接内容确定应随分发物附带的完整清单。

## iOS 静态库的源码映射

用固定 Skia 的两份 `args.gn` 与 Ninja 对象规则，分别拆开 device/simulator `libskia.a` 的 arm64、arm64e 切片。四个切片各有 **1,288 个对象**（不含 archive symbol table），每个对象名都唯一映射到一个编译源文件，**0 个未映射或歧义对象**；四份对象→源码映射完全相同，共 **1,245 个不同源文件**。两份静态库 SHA-256 分别为 `c90798bb2ce3fd7aff7f7de0a1ca9e37c19d837749c05b4010b1742dcccc845f`、`5f4790af17452aaac834a0b80bd9afa2b503ff9a55ce737a9a93eaf1b9dab1ca`，均与固定版本 Skiko Klib 内嵌库逐字节相同；正常 publication 校验也重新通过。

对象源码路径指向九个 `third_party/externals` 组件：`dng_sdk`、`expat`、`harfbuzz`、`libjpeg-turbo`、`libpng`、`libwebp`、`piex`、`wuffs`、`zlib`。其余映射路径没有其他 `third_party/` 目录；`modules/skcms` 属 Skia 源码树。完整对象映射、四切片身份和 20 份候选许可/NOTICE 文件哈希保存在 ignored `artifacts/verification/f02-versioned-consumer-20261103/linked-source-inventory.json`（SHA-256 `86ee2e6e9d957788cec2157904c2cf40ffa6a384c7f13a3adbab75aa26d1995b`）。仅供审查的 `f02-distribution-review-materials-v1.tar.gz` 包含该清单、修复补丁、复现脚本和上述 20 份原样材料：38,223 B，SHA-256 `b4d55de0e6de69f8080ebb17f7f25489cfddd0861b421ce6f97f659bec0b37e4`；不包含 109 MB Maven 归档。两份包均未上传。

对象→编译源文件映射不能判定头文件、单个源码文件的额外许可、生成代码或 AWT 模块的全部义务；这 20 份材料只是供逐项审查的候选集合，尚非分发附件定稿或许可批准。

## AWT 与 Maven 依赖范围

归档的四个模块为 `skiko`、`skiko-awt` 和双 iOS slice。根 `.module` 的三个 AWT 变体仍指向 `skiko-awt`。后者的二进制 JAR 有 702 个非目录条目，类仅在 `org/jetbrains/skia` 与 `org/jetbrains/skiko` 包，另有两个 `META-INF` 文件；没有 `.so`、`.dylib`、`.dll` 或 `.a` 条目。这是 JAR 内容检查，不是桌面运行验收。

`skiko-awt` POM 还引用同版本的 `skiko-awt-runtime` 和 macOS/Linux/Windows 各 x64/arm64 runtime，共 **7 个模块**，当前归档均无对应 POM 或二进制。AWT Gradle runtime variant 列出的依赖与该 POM 不同，没有列出这七项；因此应分别验证 Maven POM 消费和 Gradle metadata 消费，不能以 iOS variant 已链接推断桌面分发闭包。机器可读清单在 ignored `artifacts/verification/f02-versioned-consumer-20261103/maven-scope-summary.json`，SHA-256 `83a616b762f694d3fc5e88a2a597b9a7eadf44e3d6892ee48b4e96089177c259`；同目录 `audit_maven_scope.py` 可重算。

### iOS-only 正常发布的隔离构建对照

从固定 Skiko `9a5b398bb2044fff7e7a84fbfd6f4b803e4427c0` 本地克隆出新的 ignored `artifacts/verification/f02-ios-only-publication-20261103/skiko-spike/`，仅在该副本测试 `skiko.awt.enabled=false`、两个 iOS arm64 target、其他 native 总开关关闭。原 KGP 2.2.20 的 `compileCommonMainKotlinMetadata --offline --rerun-tasks` 在 `@OptionalExpectation` 报错，log SHA-256 `7c42b831048bd9da405875d69c61e50bf244ef21c6adf48c0c70823a882d5426`。只把隔离副本 KGP 改为 2.4.0 后，同一任务仍在该错误失败（SHA-256 `7ed47d189d6f405386362167397e21324af54ed396b4234684baea836233f370`）；恢复 2.2.20 并暂时停用 Skiko 自定义 source hierarchy 后也仍失败（SHA-256 `d8d910c3ed42f0ddfa05b8024d4ccb59be98f225b93253e196ec1ce2dbcb29fb`）。这两个单因素对照均未找到受支持的 iOS-only 正常发布配置。

KGP 2.4.21 的离线试验在 `kotlin-stdlib-common:2.4.21` 未缓存时、进入目标编译前停止（log SHA-256 `495327cbac7b55767355b4dad41b25ca12c8325496cdd9532620ad990d163886`），**没有该版本通过或失败的编译结论**；未下载依赖。恢复隔离副本的跟踪文件后，只有本轮日志保留为 untracked。

AWT 开启时的正常任务列表只含 `publishAwtRuntimeElementsPublicationToBuildRepoRepository`、`publishSkikoJvmRuntimeMacosArm64PublicationToBuildRepoRepository` 和 `...MacosX64...`，没有 Linux/Windows 的同类发布任务（ignored `awt-tasks.log` SHA-256 `ba41bace0dfc43fa748461b31a293b16ea44537f35abb159387d3f29a3aa5925`）。该主机任务配置不足以补齐 AWT POM 所列全部七个 runtime。保留 AWT 目标仅作为编译锚点会让根 `.module` 和项目结构 metadata 继续声明 AWT；直接删改生成元数据不是正常 Gradle publication。后续应调查 KGP/Skiko 的 source-level 元数据配置，或明确跨主机完整 AWT 发布方案，再重新审查。

## 分发前的未关闭事项

1. 对上述九个实际编译的第三方组件、Skia/Skiko 及 AWT 包逐项审查头文件、文件级例外和所需 NOTICE，定稿与制品同版本、同哈希索引的分发附件。当前对象清单与 20 份候选材料不能单凭路径映射或四份 POM 元数据视为许可材料齐全。
2. 明确发行范围：若只支持当前 iOS 消费图，需产生不宣称桌面可用的受支持 publication，并重验 metadata；若保留 AWT 变体，需正常发布、审查和消费上述七个 runtime 模块。当前归档的 AWT Maven POM 依赖不闭合。
3. 明确项目专用版本的维护者、托管地址、CI/消费者读取方式、不可变版本规则与撤销流程。当前 109 MB 归档只在本机，未上传 GitHub/GitLab，也没有远程消费者验证。
4. 选定分发方案后，从空缓存的独立消费者按归档哈希取件，重验两份 iOS Klib、Gradle variant、最终 App 身份和真机资源预算。现有本地构建与十轮 trace 不自动证明远程分发和生产验收。

F02、原 P4/P11 及发布结论继续为 `open/not-ready`。相机权限和同 PID 十轮无 recorder trace 已完成，见 [AtlasTextOp 调查](p4-atlas-text-cache.md)；无需再次申请相机授权。
