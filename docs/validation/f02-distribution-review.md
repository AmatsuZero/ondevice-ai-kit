# F02 固定 Skiko 候选分发审查（2026-11-03）

## 2026-11-06：最终 App 许可材料保留通过

[App 保留证据与用法](f02-app-notices.md)：隔离 .3 SDK 双 iOS framework 14任务通过，
unsigned device App 正常构建，69文件目录/摘要完全一致；312工具测试通过。
仅关闭本地App保留子项；下方“最终App未验”是此前状态。文件条款/独立批准、远端匿名
消费与生产资源仍未关闭，F02/P4/P11保持open/not-ready。

## 2026-11-06：.3 修改声明、正常附件与消费者保留通过

当前新冻结候选 `0.144.6-ondevice-f02.3`，正常release发布59任务（最终51执行/8up-to-date）；
三个模块115文件。三个notices JAR和三个source JAR均含相同69文件原文及SHA256索引。
7个修改源文件均有实际注释，分发的6个Kotlin文件在3份source JAR中的18处声明全部核实。
消费者通过显式notices classifier收集材料，69文件与输入摘要完全一致；负向缺失/篡改/额外
文件检查均拒绝。独立KGP2.4消费者10任务、commonMain和双iOS framework链接成功。

许可输入核对新增保留Ninja depfiles：两个target各1902对象均VALID、无未映射/缺失本地文件，
覆盖3994本地输入、2115头文件，仍10个external组件。新增24份头部原文（含3份内联许可）、
DNG PATENTS和嵌套Expat COPYING，保留原23上游文本/2Unicode参考/14份libjpeg内联声明及
IJG/DNG归属。40内嵌Skia静态库摘要全部保持；不是对全部条款的独立许可裁定。

归档108,522,716 B/115文件，SHA256
`3276597b2f422091ee13a5854c79625d5248ca49c54fa9d1c136eb427836ca93`。
证据 `artifacts/verification/f02-notice-delivery-20261106/`，原.1/.2不变。
[三补丁顺序、发布及消费者收集](../../probes/skia-atlas-text-cache/Skiko-ios-notices.md)已跟踪。
源码/材料/双iOS消费者复核 `verify.py`通过；保留root sourcesJar漏附与nested git apply跳过的
RED记录，修复后均校验真实字节，不只看命令exit0。没有上传、远端消费或生产切换。

已关闭的是**修改声明写入分发源码、正常许可附件、独立消费者材料保留**等本地技术子项。
文件级例外/历史Unicode条款、独立许可批准、Nexus远端匿名消费、最终App材料保留与生产
资源验收仍未关闭；F02/P4/P11仍open/not-ready。下方.2的尚未应用/尚未附带结论为历史。

## 2026-11-06：iOS-only 发布闭包已修复，许可附件继续准备

当前候选为 **`0.144.6-ondevice-f02.2`**，固定Skiko/Skia revision与下方.1相同。
[专用源级补丁](../../probes/skia-atlas-text-cache/Skiko-ios-only.patch) SHA256
`bad349963f7fc09593f00e8b17b0428858da7922a8c938f7aedcdc62abc94413`，只用于iOS发布。
修复严格双iOS shared Native metadata的JVM可选注解，并按supportAwt关闭独立AWT runtime
publication；不修改生成POM/module、不抑制编译诊断。原1,022个注解错误的离线RED已保留，
修复后13任务metadata编译与58任务正常release发布均通过。

正常输出恰为 `skiko`、`skiko-iosarm64`、`skiko-iossimulatorarm64`，100文件及6个
available-at引用全部闭合，无AWT/Android/Web变体。归档108,176,625 B，SHA256
`631806c3cf1bee881a41e2b99da4490e368dc37aa0f7d2057f376a4d683eb2de`。
KGP2.4.0的独立commonMain/native consumer及SDK71276364的实际Compose consumer
均链接device/simulator framework成功（9/14任务）；后者仅增加隔离仓库和版本overlay，
dependencyInsight确认Compose的Skiko0.144.6解析到.2。不是装机或资源预算验收。

全部40份内嵌静态库已逐字节匹配固定Skia输入，所有架构slice对象映射到1,860个不同编译源，
0未映射，覆盖10个external组件（新增ICU444源）。旧清单仅覆盖libskia.a，不能代表整个Klib。
全部1,860文件头已重新分类；7个无头线索文件包括原5项和两个生成ICU数据汇编。
两汇编的.long/.byte内容逐字节匹配同一iOS icudtl.dat（6,296,800 B），输入含Unicode版权/条款引用。
23份上游文本、2份Unicode参考、14份libjpeg原样内联声明已暂存，**尚非完整分发附件批准**。
另备[7源文件修改声明补丁](../../probes/skia-atlas-text-cache/Skiko-ios-source-notices.patch)，
通过在.2源树上的apply检查；冻结.2源码JAR尚未包含它，需新版本重新构建，不能替换.2。

本地证据 `artifacts/verification/f02-ios-publication-20261106/`：`summary.json`、
`publication-graph.json`、`all-static-source-inventory.json`、`all-source-header-audit.json`、
`icu-data-provenance.json`、`license-progress.json`及原始build logs。
只读 `verify.py`复核100文件/sidecars、6引用、归档成员、40内嵌库、4framework和保护输入；
[复现命令](../../probes/skia-atlas-text-cache/Skiko-ios-only.md)已跟踪。

剩余实际门槛：新候选应用修改声明；文件/头文件例外与HarfBuzz生成Unicode条款核对；
正常publication附带同版本notices并验consumer保留；Nexus匿名远端取件/编译与生产资源验收。
发布账号权限已明确，管理API不再是前置条件；尚无独立审查批准、维护团队指派或远端发布。
.1/.2均保留不覆盖，F02/P4/P11仍open/not-ready。
下文为.1及探索历史；其“未解决iOS-only”结论已被本节的实际源级修复取代。

2026-11-06：用户已指定内部 Nexus host，渠道不再是完全未知；匿名发现、候选入口与
尚未核实的仓库策略见 [Nexus交付准备](f02-nexus-delivery.md)。本地归档未上传，
下文publication闭包、许可定稿、维护团队和远端消费者等门槛仍有效。

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

### 编译源文件的文件头线索复核

在上述固定 revision 与对象清单上，ignored `artifacts/verification/f02-file-license-audit-20261103/audit.py` 扫描 **1,245** 个实际链接编译源文件的前 80 行，并记录每个源文件哈希。重跑得到 **1,224** 个有许可文字或引用线索、**16** 个有内联授权文字、**5** 个没有文件头线索；`summary.json` SHA-256 为 `64c1d6ac094c38cc283446003a6e7d90594ab83465e78ec48a4445c3d9ed400b`，重跑前后相同。这是检索分类，不是文件级许可认定，也未覆盖头文件、生成输入或 AWT。

5 个无头线索文件为 DNG SDK 的 `dng_jpeg_memory_source.cpp`、`dng_safe_arithmetic.cpp`，以及 HarfBuzz 的 `OT/Var/VARC/VARC.cc`、`hb-ot-shaper-indic-table.cc`、`hb-ot-shaper-vowel-constraints.cc`。前两者直接包含的同名头文件中，`dng_safe_arithmetic.h` 有 Android Open Source Project 的 Apache-2.0 头，`dng_jpeg_memory_source.h` 没有许可头；不能直接把任一头文件声明外推为对应 `.cpp` 的许可。HarfBuzz 根 `COPYING` 提醒子目录可能另有许可；生成的 `hb-ot-shaper-vowel-constraints.cc` 明示输入来自 `src/ms-use/IndicShapingInvalidCluster.txt`，而原 20 份材料缺少 `src/ms-use/COPYING`。该文件为 Microsoft MIT 声明，SHA-256 `c2cfccb812fe482101a8f04597dfc5a9991a6b2748266c47ac91b6a5aae15383`。Indic 表和 vowel constraints 还标明 Unicode 数据输入，相关生成输入与许可需专项核对。14 个 libjpeg-turbo Arm NEON 编译源文件含内联许可文字，仍需逐文件比对现有 `LICENSE.md` 与 `README.ijg`，不能仅凭组件级文件判定覆盖。

为便于审查，ignored `build_materials.py` 生成只供本地审查的 `f02-distribution-review-materials-v2.tar.gz`：**27** 个文件、**154,160 B**、SHA-256 `39ce3868d66afb22529356189cc587a8074d1134d66186cbf3a67371ccb2cef6`，包含原 20 份候选材料、新增 `ms-use/COPYING`、对象映射、文件头审计、脚本和补丁；没有 Maven 二进制归档。v1 原包保留不覆盖，v2 未上传，21 份许可材料仍只是候选集合。

固定 HarfBuzz `08b52ae2e44931eef163dbad71697f911fadc323` 的两份生成源码标明 Unicode 16.0.0 的 `IndicSyllabicCategory`、`IndicPositionalCategory`、`Blocks`、`Scripts` 输入，以及 `ms-use/IndicShapingInvalidCluster`。官方 [UCD 16 ReadMe](https://www.unicode.org/Public/16.0.0/ucd/ReadMe.txt)指明版本，[Unicode Terms of Use](https://www.unicode.org/terms_of_use.html)与[Unicode License v3](https://www.unicode.org/license.txt)给出数据文件条款。已下载原样 ReadMe（SHA-256 `14cafa23788d3a20dd21d6b0cdcb8d6dab520781fcd9ad9392f3b88ea607e633`）和当前 License v3（SHA-256 `e7a93b009565cfce55919a381437ac4db883e9da2126fa28b91d12732bc53d96`）；后者版权行目前为 1991–2026，与 UCD 16 的 2024 源头时间不同。ignored `build_materials_v3.py` 在 v2 基础上加入两份官方文本、生成输入/输出哈希来源表及说明，生成本地 v3 包：**32** 个文件、**157,138 B**、SHA-256 `dfb89b662477a93fe0511f4acfc626b9238eae820735aa728b815c722dc69d2b`。Unicode 条款对生成 C++ 的适用范围与应附的历史版本文字仍需许可审查；v3 未上传，也不构成分发附件定稿。

## AWT 与 Maven 依赖范围

归档的四个模块为 `skiko`、`skiko-awt` 和双 iOS slice。根 `.module` 的三个 AWT 变体仍指向 `skiko-awt`。后者的二进制 JAR 有 702 个非目录条目，类仅在 `org/jetbrains/skia` 与 `org/jetbrains/skiko` 包，另有两个 `META-INF` 文件；没有 `.so`、`.dylib`、`.dll` 或 `.a` 条目。这是 JAR 内容检查，不是桌面运行验收。

`skiko-awt` POM 还引用同版本的 `skiko-awt-runtime` 和 macOS/Linux/Windows 各 x64/arm64 runtime，共 **7 个模块**，当前归档均无对应 POM 或二进制。AWT Gradle runtime variant 列出的依赖与该 POM 不同，没有列出这七项；因此应分别验证 Maven POM 消费和 Gradle metadata 消费，不能以 iOS variant 已链接推断桌面分发闭包。机器可读清单在 ignored `artifacts/verification/f02-versioned-consumer-20261103/maven-scope-summary.json`，SHA-256 `83a616b762f694d3fc5e88a2a597b9a7eadf44e3d6892ee48b4e96089177c259`；同目录 `audit_maven_scope.py` 可重算。

### iOS-only 正常发布的隔离构建对照

从固定 Skiko `9a5b398bb2044fff7e7a84fbfd6f4b803e4427c0` 本地克隆出新的 ignored `artifacts/verification/f02-ios-only-publication-20261103/skiko-spike/`，仅在该副本测试 `skiko.awt.enabled=false`、两个 iOS arm64 target、其他 native 总开关关闭。原 KGP 2.2.20 的 `compileCommonMainKotlinMetadata --offline --rerun-tasks` 在 `@OptionalExpectation` 报错，log SHA-256 `7c42b831048bd9da405875d69c61e50bf244ef21c6adf48c0c70823a882d5426`。只把隔离副本 KGP 改为 2.4.0 后，同一任务仍在该错误失败（SHA-256 `7ed47d189d6f405386362167397e21324af54ed396b4234684baea836233f370`）；恢复 2.2.20 并暂时停用 Skiko 自定义 source hierarchy 后也仍失败（SHA-256 `d8d910c3ed42f0ddfa05b8024d4ccb59be98f225b93253e196ec1ce2dbcb29fb`）。这两个单因素对照均未找到受支持的 iOS-only 正常发布配置。

KGP 2.4.21 的首次离线试验在 `kotlin-stdlib-common:2.4.21` 未缓存时、进入目标编译前停止（log SHA-256 `495327cbac7b55767355b4dad41b25ca12c8325496cdd9532620ad990d163886`）。首次联网重跑确实进入 metadata 编译并遇同类错误（SHA-256 `a2e0aedda5ece66e62654d23a809e7bbd25966af8a633423436fd635b3d18124`），但命令误设 `skiko.native.enabled=true` 与 `skiko.native.ios.enabled=true`：Skiko 的 `supportAllNative` / `supportAllNativeIos` 会扩展目标集，**该日志不是双 iOS arm64 严格对照**。更正后明确关闭这两个总开关，仅开启 `skiko.native.ios.arm64.enabled` 和 `skiko.native.ios.simulatorArm64.enabled`；`--info` 日志确认 commonization 目标恰为 `(ios_arm64, ios_simulator_arm64)`，KGP 2.4.21 仍在 `compileCommonMainKotlinMetadata` 因 `@OptionalExpectation` 失败（14 个执行任务，SHA-256 `ac17af5b366628b59bbc3d895b1f108b96f79770bd7353007a445fe93ff2e25c`）。升级 KGP 不能解决这项 metadata 编译阻断。

同一双 iOS arm64 配置与 KGP 2.2.20 下，只把 `skiko.awt.enabled` 改为 `true`，metadata 编译 **BUILD SUCCESSFUL**（6 个执行任务，log SHA-256 `39923208477ac222ec062899fbaaf850cf75ff2a80aa5f79a2d0ac31f70a5fbc`）。这证明在固定源码/版本/目标下，AWT target 的存在会改变该编译结果；不证明 AWT 是唯一底层原因。KGP 2.4.21 打开 AWT 的对照在项目配置阶段因 `awtApiElements` 不允许 dependency constraints 而停止（SHA-256 `2c8ef667c5b4a820fcec0ae5e6fe57e17911e25552feff3912063a8dbb49ff37`），未进入 metadata 编译，不能与 2.2.20 的通过结果并列解释。

源级单变量诊断：Skiko `src/commonMain/.../Expects.kt` 的 `ExternalSymbolName` 是一处 `@OptionalExpectation expect annotation`，移除该注解后错误缩至 `commonMain` 中 **15 处** `@JvmInline` / `@JvmStatic`，仍 `BUILD FAILED`（SHA-256 `1e8a5a51a80a4ca04fdad7a19d0c0249ab307f7cb9d742125f984188d1f63896`）。问题涉及共用源集中的多种 JVM 注解，不能以删除一处自定义注解修复；把 JVM target 当编译锚点也会保留不完整的桌面分发图。后续应在固定源码版本上设计并验证受支持的源集/发布结构，或完成全目标分发闭包。上述实验仅在 ignored clone 中进行，跟踪文件已恢复；项目生产依赖和归档未改。Kotlin 官方[expect/actual 规则](https://kotlinlang.org/docs/multiplatform/multiplatform-expect-actual.html)和[多平台发布规则](https://kotlinlang.org/docs/multiplatform/multiplatform-publish-lib-setup.html)可作为设计约束。

AWT 开启时的正常任务列表只含 `publishAwtRuntimeElementsPublicationToBuildRepoRepository`、`publishSkikoJvmRuntimeMacosArm64PublicationToBuildRepoRepository` 和 `...MacosX64...`，没有 Linux/Windows 的同类发布任务（ignored `awt-tasks.log` SHA-256 `ba41bace0dfc43fa748461b31a293b16ea44537f35abb159387d3f29a3aa5925`）。该主机任务配置不足以补齐 AWT POM 所列全部七个 runtime。保留 AWT 目标仅作为编译锚点会让根 `.module` 和项目结构 metadata 继续声明 AWT；直接删改生成元数据不是正常 Gradle publication。后续应调查 KGP/Skiko 的 source-level 元数据配置，或明确跨主机完整 AWT 发布方案，再重新审查。

非 AWT 对照：在同一 ignored Skiko 副本、KGP 2.2.20 下，保持 AWT/Android 关闭和双 iOS arm64 target，单独开启 `skiko.wasm.enabled=true`（上游同时声明 JS 与 Wasm）。离线强制重编 `compileCommonMainKotlinMetadata` **BUILD SUCCESSFUL**，log SHA-256 `aead34e5f6f58561f24670ea5f2ec2be72e70b5562fb7fd5ff56de39c6a641e0`；正常 `generateMetadataFileForKotlinMultiplatformPublication` 通过，root `.module` SHA-256 `310fbc0cbbb21033c9e779fabfc49875f0190a427b4691a5488a4df6b954c278`。生成的 root metadata 声明双 iOS、JS、WasmJs，未声明 AWT。此对照说明该目标组合能通过 metadata 编译，**不是 iOS-only publication**，也不证明完整 Web runtime、消费者和附加许可义务已闭合；不能用它代替已失败的严格双 iOS 对照或切换生产依赖。

Web 后续核对：`compileKotlinJs` 与 `compileKotlinWasmJs` 均实际通过，log SHA-256 分别为 `027125ef7a5d7e977d66064fd141b3eb94a35a7400198fa91be0f8c8d9cf14cf`、`fcc05d701a1dedeae26ee6c79654fbde23613b29722211b2dffbce4811c6bf23`；在线解析依赖后的完整本地发布 **dry-run** 通过，但任务图仍包含独立的 `publishAwtRuntimeElementsPublicationToBuildRepoRepository`，即使 root metadata 无 AWT。Web runtime 的 `skikoWasmJar` 依赖 `compileWasm`/`linkWasm`，而 `emcc` 不在当前 PATH；没有执行真实 Web 发布。Web 根 metadata 已另存 ignored `artifacts/verification/f02-ios-only-publication-20261103/web-ios-root.module.json`，重生哈希与上述值相同。

### Android + 双 iOS 的正常 metadata 对照

同一固定 Skiko 源码、KGP 2.2.20 下，关闭 AWT/Web，启用 Android release 与双 iOS arm64 target。离线强制 `compileCommonMainKotlinMetadata` **BUILD SUCCESSFUL**（log SHA-256 `a8fb5c370500c1d7b9612f49dca809d419d10e61b43c048da7d8c7632b46c04d`）；正常生成 root `.module`，另存 ignored `artifacts/verification/f02-ios-only-publication-20261103/android-ios-root.module.json`，SHA-256 `0d39419464d6806e90516b419954cc3c78265f7f6a8e7cb47dd9793737df3ee7`。重生字节一致；11 个 root 变体仅引用 Android release 与两份 iOS target，没有 AWT/JS/Wasm。隔离副本的 `compileReleaseKotlinAndroid` **BUILD SUCCESSFUL**（log SHA-256 `d733d40d23ab1cb2672456bc46e8e197feee103329219b7526ddc2ba3ee29977`）。

为检查完整本地任务图，安装了固定上游构建需要的 Android API 33 平台与 Build Tools 34.0.0，未改源码。离线 `publishAllPublicationsToBuildRepoRepository --dry-run` 通过（log SHA-256 `71371ed77a7d56ce360078c919108337cb82a710a83680045a1ae8b54daba21f`），共 131 个跳过执行的任务、7 个发布任务：Android release、双 iOS、root、Android arm64/x64 runtime，以及上游脚本无条件创建的独立 AWT runtime publication。**dry-run 不生成制品**；若采用此配置，需显式选取完整且不含 AWT 的发布集合或修正上游 publication 配置，不能直接把 `publishAll` 视作闭合分发。

真实本地 `publishAndroidReleasePublicationToBuildRepoRepository` 未完成：离线初次缺 Android lint 工件；在线取得 `lint-gradle:31.2.2` 后，`extractReleaseAnnotations` 的传输长期无输出，人工停止。停止后的离线重试明确缺 `com.android.tools.external.com-intellij:intellij-core:31.2.2` JAR（log SHA-256 `5236529a1b144d95d7a148177430c922e682b4e4dc82c1fd770fcdbb720ffa4e`），[官方 Maven 制品](https://dl.google.com/dl/android/maven2/com/android/tools/external/com-intellij/intellij-core/31.2.2/intellij-core-31.2.2.jar)的 HEAD 为 200、文件大小 30,973,352 B；这属于依赖取得未闭合，尚无 Android release AAR 发布结论。Android JNI runtime、两架构 Skia 来源及补丁、二进制消费者、额外许可和资源预算仍未验。该跨平台组合可能比 AWT/Web 的分发图更贴近本项目双端范围，但只是技术候选，不能代替 iOS-only 出口或提升 F02/P4/P11 状态。

## 分发前的未关闭事项

1. 对上述九个实际编译的第三方组件、Skia/Skiko 及任何新增目标逐项审查头文件、文件级例外和所需 NOTICE，定稿与制品同版本、同哈希索引的分发附件。v2 的 21 份组件候选材料与 v3 的 Unicode 参考文本仍不能单凭路径映射或四份 POM 元数据视为许可材料齐全。
2. 明确发行范围：若只支持当前 iOS 消费图，需产生受支持的 iOS-only publication 并重验 metadata；若保留 AWT 变体，需正常发布、审查和消费上述七个 runtime 模块；若采用 Web 锚点，需完成 JS/WasmJs 目标和 runtime 的正常发布、消费与许可审查。当前四模块归档的 AWT Maven POM 依赖不闭合。
3. 明确项目专用版本的维护者、托管地址、CI/消费者读取方式、不可变版本规则与撤销流程。当前 109 MB 归档只在本机，未上传 GitHub/GitLab，也没有远程消费者验证。
4. 选定分发方案后，从空缓存的独立消费者按归档哈希取件，重验两份 iOS Klib、Gradle variant、最终 App 身份和真机资源预算。现有本地构建与十轮 trace 不自动证明远程分发和生产验收。

F02、原 P4/P11 及发布结论继续为 `open/not-ready`。相机权限和同 PID 十轮无 recorder trace 已完成，见 [AtlasTextOp 调查](p4-atlas-text-cache.md)；无需再次申请相机授权。
