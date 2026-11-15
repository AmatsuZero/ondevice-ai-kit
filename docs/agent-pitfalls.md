## W3 DEX 对应性边界（2026-11-16）

不要把完整 baksmali 原文本相等写成 whole DEX 字节或形式语义等价；相等包含 metadata/registers/instructions/labels/debug，未做 normalization/stripping。D8 Release 的 75 类差异、intermediate/merge 控件、BOOTCLASSPATH 单一变量对照与 NEGATIVE MUTANT 负控须分别保留；带 Android bootclasspath 的 intermediate 后接同一 merge 为 49/124，no-lib intermediate 后接同一无 `--lib` 的 no-desugaring merge 为 124/124，只说明本实验管线，不证明历史 AGP bootclasspath。不得再做参数扫描或用负控替代穷尽语义证明。

证据与有效范围见 [DEX 对应报告](validation/w3-dex-correspondence-20261116.md)；首轮差异、单变量对照和真实 DEX 损坏负例均留存，不能靠剥注解、debug 信息或修改输出求通过。

## W3终检：Git status比较须固定untracked参数（2026-11-16）

原保护快照按--untracked-files=all逐文件保存；终检漏该参数，Git默认把三个原untracked文件归并成两个目录，触发状态原字节比较失败。原失败/两份输出保留；相同--porcelain=v1 -z --untracked-files=all后9698B完全相同，176原内容摘要另独立通过。只修检查调用，不改文件/配置/忽略规则，不能把目录汇总当文件丢失。证据.omx/artifacts/w3-ndk-offline-recovery-20261116T0710/terminal-status-{initial-rejection,corrected}.json。

## W3 Gitiles与CBC流：按真实封装解析原字节（2026-11-16）

离线恢复首读Gitiles直接json.loads失败，原响应以精确`)]}'\n`防XSSI行开头；仅在内存移除首5字节后解析、原响应不改。CBC launcher明确使用stream-json，若按单文档json.loads会报Extra data: line2 column1(char2514)，不能归因候选JSON损坏/配额；按每非空行解析后需另验完整result、is_error、modelUsage与真实tool_use。两hy3终态51.601/34.411s、零观察工具调用；目录/root→patches→三blob原字节身份与原receipt对应已验，不冒充commit对象/签名或历史apply。证据.omx/artifacts/w3-ndk-offline-recovery-20261116T0710/{initial-read-error.json,parent-read-errors.json,parent-acceptance.json}；无重发/源站请求。

## W3 R5：统计目标、原值精度与完整批次边界（2026-11-16）

预算文稿首稿固定毫秒未约束文件时长、以completed复杂片段建档，容易把部分成功统计当全批通过；修正版改为未来固定候选/设备/PCM时长档案的各语音组两阶段RTF与App采样RSS草案，失败/缺测先报、完整终态缺失不能预算通过，原65质量范围和1.5GiB实验守卫不变。限定hy3复核另指出p95≤1只约束第95百分位，不保证每请求；精确old/new已集成。父离线checker手抄过精Apple时长常量触发断言，保留初稿/失败后直接与原raw acceptance的median/p95比较，210项含161freeze通过；未改原报告或重跑设备。记录草案、独立供应上下文审阅、父复算与产品预算批准分开，不以文稿/模型success关闭R5/W3。见[方案及收据](validation/w3-r5-resource-options-20261116.md)、.omx/artifacts/w3-r5-options-20261116T005018/。

## W3 native版本检查：Thumb模式、指令位域与ELF夹具偏移（2026-11-16）

四ABI原C-API库版本函数已导出，但普通符号表空不代表无动态符号；nm显示会清Thumb低位，raw dynsym为odd。默认objdump按ARM误解Thumb成strbt/svc/unknown，强制thumbv7后恢复；POP后的fn+12是literal pool，不是执行异常。hy3首稿ADD位条件自相矛盾/寄存器移位错、i386 CALL偏移9而实际6、三返回值解包成二导致7tests/6errors及真实仅1/4通过，保留拒收原件；七精确修正后12指令引用/8损坏控制通过。新增signed-ADRP夹具又误写ELF64 p_paddr+24，须分别扩filesz+32/memsz+40；ADD shift位为22，独立两处测试修正后12方法全过。parent只接受原字节helper解码实现和限定复核，版本字符串可伪造、不证明全native源码/组成或执行；检查参考NDK beta3不冒充历史工具链。见[验证](validation/w3-native-version-binding-20261116.md)及.omx/artifacts/w3-native-version-20261116T002000/；原quota/源站429未重试。

## W3编译工具传输超时与JVM重建证据边界（2026-11-16）

Kotlin1.7.20不在本机缓存；默认代理路径传输首compiler JAR于5505024B读超时，原部分文件/错误保留，不能当hy3配额或编译失败。确认原进程终态后一次新调用仅直连同官方Maven host，8工具60939801B完整核SHA1/SHA256；无全局代理/凭据/生产依赖改变，路径及分块参数均变，不能唯一归因某一因素。后续唯一CLI编译3.059s，124class+module原始payload全相同并经父InfoZIP独立流核；不是JAR ZIP/DEX/native或完整AGP环境重放，不以剥SMAP/注解求通过。driver只验22真实resolved源码GitBlob/SHA，symlink blob由先前来源审计验证，不能混写为本工具重验链接。文稿首稿“未新增运行/下载”、旧live/stage措辞和175保护绝对未改等已拒收，17处helper精确修正及父删一歧义短语留档；真实编译工具下载不冒充模型/ASR runtime下载。原preparation receipt为编译前快照，现有产物见probes/sherpa-runtime-materials/jvm-raw-reconstruction-20261115/，所有进程终态、无待poll；P1/P4/W3不自动关闭。

## W3共享文档保护：并发新增段不一定是前缀（2026-11-15）

四APK提交前fresh检查移除自己的前缀/反向状态替换后，仍在docs/agent-pitfalls.md触发AssertionError：范围外F08 GPU预检929B段插在既有两段之间。保留首失败；核其精确边界/摘要后仅在内存移除，恢复原268388B及SHA2568d06e26d76ef4eaf5c7ff5f0ffb26880b9fc34f163907929c1a129c69f42f0a6，实际工作文件不回滚。两其他F08文件为前缀保留，175保护不能统称全部字节或suffix相等；精确暂存仍为新W3内容加HEAD，排除并发变化。该诊断只说明原文有序字节仍在，不授权忽略任意diff或扩大提交；无重建/产品/设备改变。见probes/sherpa-runtime-materials/app-variants-sync-20261115/fresh-protection-insertion-diagnosis.json及原失败收据。新hy3发布稿4046字超4000限制，原候选保留，仅精确删冗余/整理空行后接受，不将exit0当文稿自动验收。

## W3真实Demo原生库全集不等于ASR运行时集合（2026-11-15）

当前父checker首断言把真实Demo所有SO当固定Sherpa/ORT16库，实际Demo有32：16ASR和16既有graphics.path/face_detector/image_processing/surface_util（各四ABI）。历史原Demo已含后16，两个新Demo Debug/Release extras与历史逐字节相同；最小独立消费者才恰有16ASR。有效恢复是保留原checker/失败，显式核16ASR精确子集及16历史extras边界，不删库、不改产品/依赖、不放宽ASR字节门或重建。真实Demo的project依赖、独立Maven消费及Release未签名状态分别报告；ASR通知保留不能覆盖全Demo许可/组成。证据`.omx/artifacts/w3-app-variants-20261115T145000Z/`及`probes/sherpa-runtime-materials/app-variants-delivery-20261115/`。helper README误称小材料目录含APK、状态修正漏最终native保留缺口和无关词已拒收，由新小修正文稿精确修正；APK仅留本地ignored artifacts，不能由文稿位置误述把二进制纳Git或缩减P2门。

## W3 InfoZIP串接按archive顺序而非请求参数排序（2026-11-15）

新68通知全部逐文件hash通过，首跨工具checker仍断言失败；原脚本将请求名sorted后拼Python payload，而`unzip -p archive <names...>`实测按归档成员顺序输出。独立诊断两侧均770479B，unzip与archive-order完全相同、与sorted不同。有效恢复只改checker为已核archive清单顺序，保留初脚本/失败JSON，不重建产物、不改通知或削弱逐文件校验；随后两AAR/独立APK清单和payload全同。十个负例为notice-only ZIP、同一校验器拒删除/同长改单字节，不能写成修改签名APK或设备执行。证据`.omx/artifacts/w3-ndk-notice-integration-20261115T143311Z/`及`probes/sherpa-runtime-materials/ndk-notice-integration-20261115/`。这只核技术保留，不证明全部native组成或法律发布接受。

## W3 NDK source-info URL与批次/落盘边界（2026-11-15）

r27d source-info的两个dash后缀patch URL实际404，固定formatter只认fullhash和可选`_vN`，将`-v0`/`-r522817`错归root；metadata实际为`cherry/`。仅记录正确路径，来源站429后不自动重取/切镜像。134请求以concurrency4先排程后汇总，76正文/58失败（56HTTP429、2patch404），不能描述fail-fast；独立BUILD.md404不计入58。独立r30模块五HTTP成功body因父目录未创建而未落盘、另三429，八份均不可作文件证据，也不能外推r27d生成器语义。恢复是新任务先创建目录/保留逐请求回执/验证真实字节；源站停线与hy3配额分开。r27d实际类名`DeviceLibcxxBuilder`，误找`LibCxxBuilder`或非唯一匹配导致本地准备失败，未调用模型；完整失败保留。证据`probes/sherpa-runtime-materials/ndk-runtime-patch-selection-20261115/`和`.omx/artifacts/w3-ndk-runtime-patches-20261115T140704Z/`。tree/blob/成功应用声明和源码路径分类均不证明历史执行、最终archive保留或完整许可适用性。

## W3 CMake默认规则：legacy与非legacy不可合并（2026-11-15）

hy3首稿把Sherpa默认legacy与ORT显式legacy=false混在CMake Common分支，并把未设置STL的投影null写成命令观察，已拒收且原件保留。固定r30 wrapper默认legacy=true、include后return；只有该分支NOT ANDROID_STL时默认c++_static并加-static-libstdc++。ORT按实际CMake3.31.6 Common的无truthy STL/无旧GNU目录及unified分支条件解释；四ABI编译flags的-stdlib=libc++仍不证明历史最终静态archive抽取。查过的hooks/flags没有选择覆盖，不等于所有cache/target覆盖已穷尽。两既有CI ZIP的20/1成员无cache/link/response，不能重新下载同包或用进度行假充完整命令。父投影KeyError:logPath仅因observed-toolchains含一条path聚合row；保留首错，选择实际日志row恢复，不重跑编译。见[验证](validation/w3-cxx-linkage-rules-20261115.md)及`.omx/artifacts/w3-cxx-linkage-20261115T134154Z/`，全部W3门槛保持。

## W3 NDK：Linux GNU开关不能排除Android C++ runtime（2026-11-15）

固定Sherpa option仅在NOT BUILD_SHARED_LIBS且CMAKE_SYSTEM_NAME=Linux分支控制GNU libstdc++/libgcc；Android共享构建的OFF不选择libc++，x86命令又没有显式OFF。ORT实际NDK27.3.13750724/legacy=false与SherpaNDK30.0.16248370的默认legacy路线须分开，本机r27/beta3仅参考。两枚arm64原产物note分别为r27d/13750724/API27与r30/16248370/API21，ORT comment可对应compiler manifest；JNI缺comment警告不是缺NDK note。上游Python解码仍用同一参考LLVM定位，不能称完整独立解析器；tree/blob核对不等于签名/可信构建。75/59 patch引用、whole-toolchain NOTICE及基础LLVM LICENSE不证明所有补丁已应用、最终保留或完整适用通知。hy3首两稿的缩小W3范围、工具零执行混写和ORT JNI误名已拒收，最终稿保持完整目标与真实成员。见[验证](validation/w3-ndk-runtime-provenance-20261115.md)及`.omx/artifacts/w3-ndk-runtime-20261115T130823Z/`，不重放旧构建或全库扫描来替代缺失link证据。

官方源码的build/cmake路径亦会命中仓库通用build/忽略规则：初次git add exit1且仅四原文未暂存；有效恢复是核check-ignore后仅对这四个已绑定原文件精确git add -f，不改ignore规则、不将其当生成缓存删除。首失败和部分暂存状态保留。

## W3 ORT补丁：带timestamp的旧文件头不是实际目标（2026-11-15）

Eigen的s390x-build-werror.patch旧`---`行为`.orig<TAB>timestamp`，首父提取脚本按旧整行找archive member而失败。有效恢复是按`+++`真实目标并去tab timestamp，保留初始失败/目录，只补失败组件；其余六组件不重跑。11原patch在新归档提取目录本地check/apply成功及21文件前后hash，不是历史CI post-patch tree、GNU patch重放或最终机器码效果。四原日志只有Found Patch直接行，不能由工具被发现宣称历史apply已证。证据[验证](validation/w3-ort-nested-notice-integration-20261115.md)及`.omx/artifacts/w3-ort-nested-composition-20261115T122051Z/`。

## W3 ORT通知：恢复overlay可省略archiveRoot（2026-11-15）

complete-manifest的cpuinfo/ONNX恢复项已有绑定archiveMember/摘要却没有可选archiveRoot，父集成预检KeyError发生在生产写入/构建前。有效恢复是保留失败，按已验member根目录记录来源派生，先核完31项恰一处置和全部原字节再写/构建；不猜版本、不覆盖旧归档/manifest。首候选“通知集成不改变AAR/build output”亦不准确，独立hy3措辞修正已应用：仅非通知成员及固定runtime/library未变，63通知资产改变新SDK/APK字节。完整产物与十负例才支撑子集交付，exit0或helper自报不单独作证。

## W3 native通知：可选字段及完整候选构建门（2026-11-15）

父集成假定58项参照都有sourceManifest，Darts id57无此可选字段，触发KeyError；23原文本已复制而索引/修改说明未完成时又误启构建。该12.285s/exit0仅证明不完整状态可构建，明确拒作验收。有效恢复是保留失败/首AAR/旧m2，依据真实Darts material-manifest核绑定，确认25新资产完整后在新m2-complete重建；原文不改。完整AAR/独立Maven APK保留36资产、六负例拒收才构成本轮技术接受，仍不证明完整组成/发布。证据`probes/sherpa-runtime-materials/native-notice-integration-20261115/`及`.omx/artifacts/w3-native-notice-integration-20261115T115026Z/`。

## W3 ORT：sed模式与实际diff不能混写（2026-11-15）

旧patchSummary概括为删除SOVERSION和target VERSION；实际workflow确实执行两条sed，但四ABI保留diff只显示SOVERSION/相关注释删除，多行VERSION ${ORT_VERSION}仍在。命令意图不能代替实际修改范围。有效恢复是逐一关联四diff与固定executed-android-shared.yaml，新修改说明及ort-patch-interpretation.json记录该差异，旧历史材料保持原字节；所示diff不是所有输入/补丁已穷尽的证明。见同轮native通知材料与[验证](validation/w3-native-notice-integration-20261115.md)。

## W3 hy3候选：伪工具正文与空tools的观测边界（2026-11-15）

hy4free实际429不证明hy3不可用。用户新选hy3后首调用5.676s/exit0/result success，但正文仅伪Bash工具文字，无候选JSON/真实工具事件，已拒收。空tools参数下init仍列69工具名称；列表本身不证明实际权限或执行，不能声称工具禁用已被验证。独立小清单任务同时缩小提示、调用级deny/空setting-sources/纯文本system prompt后28.537s完整交付；另独立APK候选68.832s完整交付，均零已观察真实工具事件。多项同时改变，不能把成功归因于某单一选项。父端核id/唯一目标/原字节并实构建，11资产在AAR/独立Maven APK保留、缺失/改单字节负例拒收；这是JVM子集技术证据，不代替P2完整组成/P4发布接受。保留首失败及三份raw收据，不付费/自动fallback/重试原quota；见`probes/sherpa-runtime-materials/jvm-notice-integration-20261115/`和`.omx/artifacts/w3-p3-jvm-hy3-selection-20261115T111916Z/`。

## W3 ELF：no symbols与NEEDED阴性不能排除静态组成（2026-11-15）

固定AAR四arm64库的llvm-nm普通读取exit0/stdout空/stderr no symbols；16库实际section均无SHT_SYMTAB而有SHT_DYNSYM。有效核对是保留原件，分别读动态导出和DT_NEEDED，核同ABI内部边及参考桩库，不把普通表缺失当无组件/成功组成审计。16SO共92边、五外部名称/20已有NDK桩库已核；readelf/readobj同LLVM只能算两前端核对，不能称独立实现。NEEDED没有libc++_shared不证明无静态C++；导出阴性不能排除隐藏/内联代码，dlopen分支也不被穷尽。已有NDK30 beta3桩库只作名称/字节参考，不能冒充历史job工具链、真机加载或最低API兼容。证据`.omx/artifacts/w3-native-linkage-20261115/`及[验证](validation/w3-native-linkage-20261115.md)。

## W3 Demo产物：配置对应的真实APK与project消费边界（2026-11-15）

旧默认Demo APK不含Sherpa资产，隔离main.apk也不是普通Demo交付证据。有效恢复是先保留旧APK，再用已有任务显式enableSherpaAsr/assets/m2离线构建，保存本次新产物及参数；系统Java shim无JRE仅在子进程复用已有JDK21，不改全局。新APK四ABI16SO、7资产/3许可原字节相等及124runtime/18backend类名已核，backend Kotlin为up-to-date，不声称全量重建。Demo的project依赖不能冒充独立Maven最终消费者，三根/模型许可不能覆盖完整通知或递归组成；SDK XML warning原件保留，构建成功不等于安装/推理/资源/生产接受。证据`.omx/artifacts/w3-demo-apk-delivery-20261115/`及[验证](validation/w3-demo-apk-delivery-20261115.md)。

## W3 JVM通知：POM顶层声明与Compiler NOTICE不能覆盖stdlib嵌套来源（2026-11-15）

四解析JVM坐标POM均有Apache声明，但固定Kotlin许可README及实际sources JAR包含GWT/Guava/ThreeTenBp/Boost来源；四示例与固定commit逐字节同、当前JAR四class存在，BSD3/BSL1.0及原归属不能遗漏。Kotlin license/NOTICE自称Compiler distribution，勿直接当stdlib所需NOTICE；类存在仍非重建等价/最终R8或DEX保留。根级筛选又漏license目录的失败按既有坑恢复：只读精确subtree，不重取已成功输入。固定Kotlin POM.sha512实际404，复用本体、校已发布SHA1与原缓存；不把无缓存POM/缺某种checksum当无声明，或把SHA1写成SHA512。原失败、12原文本81755B与元数据见`probes/sherpa-runtime-materials/jvm-dependency-notices-20261115/`；只补材料，非生产通知/完整组成或发布通过。

## W3通知交付：thin消费者不证明最终打包丢失（2026-11-15）

当前17份产物（含5 sources JAR辅助项）全成员检查没有独立通知资源；Sherpa SDK外层仅4非目录成员，runtime外层21含16SO，通知集成本身尚缺。两份8KB级consumer是thin library AAR，未合并传递runtime；其无通知不能证明AGP丢弃，也不能作最终App保留验收。有效后续是先固定适用组件/原声明及修改说明，集成SDK通知，再用独立最终APK提取比对；58历史/新来源参照不能不加选择直接当完整适用清单。本轮Python/Info-ZIP有序列表及串接解压负载相同，未构建/安装/改生产；名字与精确文本阴性不是法律符合性裁定。证据`probes/sherpa-runtime-materials/notice-delivery-20261115/`及[验证](validation/w3-notice-delivery-20261115.md)。

## W3第三方构建目录补丁与bundled header声明也属于输入（2026-11-15）

Sherpa主仓库git diff只有version.cc，不证明下载的第三方源未改。固定simple-sentencepiece.cmake在Android分支会改threadpool.h/ssentencepiece.h/ssentencepiece.cc；四ABI日志均报告patch并编译静态目标。有效核验为归档URL_HASH+全部35普通文件同固定tree、仅执行原helper/body并保留三文件前后/diff，再关联实际日志与声明链接边；本地重建仍不等于直接取得历史CI补丁后源码/最终机器码。该固定归档darts.h另带BSD2/Susumu Yata声明，不能被根Apache文本代替；原ThreadPool为zlib-style文本，Android补丁换空stub，不能误记MIT或由原源码存在断言最终必然嵌入。补丁产生的两处空白行保留原字节，不为diff检查格式化证据。证据`.omx/artifacts/w3-sentencepiece-patch-20261115/`及[验证](validation/w3-sentencepiece-patch-20261115.md)。

## W3 Git mode120000的Kotlin blob是链接文本（2026-11-15）

旧class-source映射按SourceFile匹配固定tree路径，却未区分mode；AAR模块22个.kt均为120000，总1568B，gitBlobSHA1固定的是相对链接文本，不能当真实Kotlin源码hash。有效恢复是取实际link blob、规范化相对目标、要求同固定tree内100644普通blob，再核真实源码GitBlob/大小/SHA；不得按同名猜目标或创建真实本地symlink。22真实源141269B/124实际SourceFile与声明集合通过；共享kotlin-api中第23个OfflineDiacritization.kt无模块链接，明确排除，不盲加输入。旧已接受快照保留并新增resolved字段，class元数据对应仍不等于重建字节码/完整构建输入。原系统javap shim无JRE错误保留，复用既有JDK21绝对路径即可，不安装或改全局。证据`.omx/artifacts/w3-jvm-source-links-20261115/`及[验证](validation/w3-jvm-source-resolution-20261115.md)。

## W3当前SDK消费复核实际hy4free配额失败（2026-11-15）

当前SDK/独立consumer已真实本地构建及制品解析，随后hy4free只读复核3.092秒自然exit0，但result为error_during_execution/is_error=true、HTTP429/code6004/categoryquota、0token/无报告；不是仍在运行、构建失败或已完成复核。服务端重置2026-11-16 10:15:32 UTC+8仅是时间观察，不证明恢复。保存raw/prompt/父停线裁定，停止该线路并提用户选择，禁止自动换模型/付费/重试；本地核验与旧独立接受范围不受撤销。证据`.omx/artifacts/w3-sdk-consumer-binding-20261115/`与[验证](validation/w3-sdk-consumer-binding-20261115.md)。

## W3固定源码仍须纳入实际发布脚本补丁（2026-11-15）

Sherpa固定source `af2d8d81dbfa61c4cea4c568da78f3982edca005` 的官方libs/AAR两job仍由 `new-release.sh` 修改 `version.cc` 日期和短SHA；实际日志diff为 `fad4f460..149297a1`。按固定源码及脚本重建两处修改，前后GitBlob前缀均匹配，已记录为实际补丁。不能从固定tag/source或短tracked diff宣称“无补丁”或所有生成输入无修改；逐成员矩阵须保留来源、实际观察及未覆盖项。证据 `.omx/artifacts/w3-p1-member-matrix-20261115/` 与[验证](validation/w3-p1-member-matrix-20261115.md)。

## W3复核JSON截断不是独立接受（2026-11-15）

用户选定hy4free只读复核自然627.210秒、exit0/stderr空，但原JSON数组恰262144B、字符串未闭合，只有一条完整message、没有完整result；不能由exit0或部分分析恢复成接受结论。保留原prompt/raw/父拒收，独立缩小供应输入、去重投影并改stream-json交付；该变化不证明原截断的内部原因，也不能预先声称是通用修复。未观察provider429，不自动付费或切供应商。完整终态及父裁定另留收据；报告必须说明实际供应范围与未执行限制。

## W3 GitLab EOF：调用级路由恢复与幂等发布（2026-11-15）

Issue同步已完成GH写入/回读后，GL notes的20行和独立5行读取均立即EOF；不能重发GH或把读取失败写成GL发布完成。同一精确host的无凭据curl直连返回HTTP404、继承路由报connection reset；随后仅该glab子进程移除HTTP_PROXY/HTTPS_PROXY/ALL_PROXY及小写同名项，并设NO_PROXY/no_proxy为`*`，相同20行GET成功。此对照确认本次可用的调用级路由，不证明代理是所有历史EOF的唯一根因。恢复只作用当前子进程，不改全局代理/凭据；原错误保留，完整分页查唯一marker后才写，已存在GH marker仅回读。证据`.omx/artifacts/w3-closure-scope-20261115/publication/`；不把Issue/Wiki同步或网络恢复当W3质量/资源/发布接受。

## W3 gh日志：终端转义保护不是job失败（2026-11-14）

三份公开ORT job日志的gh api初读均exit1/stdout0，stderr明确要求--allow-escape-sequences；不是provider429、远端构建失败或缺日志。按本机gh api --help，仅对三个精确日志请求加该输出选项并捕获到文件，三份完整原始字节exit0/stderr0；不向终端渲染原始控制序列，不改全局配置/凭据，保留首错误。对供助手的摘录逐行核原始log和SHA，不把CMake ON标志当实际CPU执行/静态组成。证据.omx/artifacts/w3-ort-all-abi-20261114/及[验证](validation/w3-ort-all-abi-20261114.md)。

## W3 TECNO准备：真实ADB参数重组与不存在包exit1（2026-11-14）

原140freeze/376host通过后，真实prepare的`stat -c '%u %d %i'`仍远端拆参，stdout2000、stderr把%d/%i当文件；原fakeADB多argv保持token边界掩盖已知问题。自动恢复同类stat失败且把TECNO的`pm path`不存在包exit1/双空误判异常。零安装/模型传输/native；保留原restoration=false，仅二次核nonce/UID/dev/inode/唯一marker后精确rm marker+rmdir空目录，补充恢复不追改失败。未来三处stat单字符串shlex.join；包不存在仅exact int0/1且stdout/stderr exactemptybytes，其他错误/畸形保留。首候选错误删CR且只验prefix，4畸形真实RED；外部窄修后384方法/64AST/新161freeze通过，原140实际字节快照经历史guard证明。新run/output隔离，不重放原失败prepare/consumed FILE/heap、不改APK/Kotlin/预算。复审勿猜ctx语义：真实FILE call/text默认accepted0、非零先抛，rm也一样；补绑定与真实父夹具后hy3撤回medium/low。首绑定复现缺test ! -L支持/错误消息表示断言过严均保留，不改正确实现。新设备fresh前置途中ADB掉线，USB枚举不证明ADB/锁屏/授权。证据`.omx/artifacts/w3-terminal-{physical-20261114-0036,portability-20261114}/`、[验证](validation/w3-terminal-portability-20261114.md)。

## W3复审组包：复用校验器先核路径合同（2026-11-13）

父首包纳入两份原`.github/workflows`支撑文本，既有nexus_notice_materials对组件首字符的约束实际拒收ValueError；原文/hash无漂移，不能改正确校验器迁就组包。保留原失败collection/索引/脚本及可重复拒收，只把这两支撑路径映射为github-source，supportFiles.repositoryPath保留原位置，57许可/声明仍原路径原字节。修后141文件/8链接/ZIP逐成员/独立再打包通过，两隔离复制件的同长篡改与删除均拒收；只是文本保留，不等于最终组成/生产包保留/发布接受。证据`.omx/artifacts/w3-release-review-20261113/`及docs/validation/w3-release-review-materials-20261113.md。

## W3 完整driver：真实ctx、消费证明与验收入口（2026-11-13）

两份免费hy3片段完整交付并组合后，原launch6通过；扩展负例实际揭示dispatcher丢adb（collector真实KeyError）、消费计数字段等值接受bool/startedNs越int64、marker证明读取二次中断替换主异常，以及preflight漏绑定/输入和collection/poll输出。外部六块精确修正后，launch8/dispatch13/真实本地fakeADB六action链路2通过；完整回归和双完整源码复核各有独立收据，不能据候选CLI0提前宣称通过。父夹具另补真实/data祖先映射、临时候选BASE指向不可变FILE和ACK exec-in；resource失败的两次精确force-stop分别来自lifecycle和restore，不是facade重复stop。完整回归首命令误选旧artifact13测试，缺新增必需模块导致2ERROR；保留原日志并使用仓库现行check_device_contract.py取得18GREEN，不改正确guard迁就旧夹具。证据driver-parent/、driver-{launch,dispatch}-candidate/、driver-fix1-candidate/及driver-repository-acceptance/。仅host，非真机/质量/原生政策effect。

## W3 policy：逐读持久化与 JSON 返回形状（2026-11-13）

首本地片段把实际顶层 allocatorPolicy 误放 config；外部定点修正后保持 config.provider/requestedEnableCpuMemArena 绑定。首 wrapper 为源码占位符，未验收、未执行伪工具。首8方法两子例失败是返回 tuple 与落盘 JSON list 不同；hy3 仅把三个返回记录转 list，内部身份校验不改。独立中断故障实证第二读抛错时首读未保存；外部把首 durable write 移至第二读之前，canonical-root 旧源 RED/新源 GREEN 保留。父首 green 用未 resolve 的临时根遭共享路径守卫拒绝，只修父夹具，不放宽实现。最终348方法/56AST、保护摘要及 bounded source review accept；host 非 Android/原生政策 effect/质量通过。证据 policy-parent/、policy-repository-acceptance/。

## W3 collect：模块简称、实际ctx与类型等值（2026-11-13）

整份603s/root262144B截断仅完整输入无源码；拆分B终态也只伪Bash文字，无工具执行。A片段完整JSON但模块简称restore/lifecycle、猜测ctx扁平字段/文件list与local路径误加remote前缀；主代理早期提示也用了模块简称及不足的ctx表达式，需给真实源码和精确容器。远端独立3方法实测1FAIL2ERROR（第二文件名、未拒双读变化、dict属性），外部窄修后3GREEN；完整12方法再抓finish嵌套dict 1==True/0==False三FAIL，逐字段exact type+value后GREEN。首缺env/缺candidate的父包装器未跑实际验收，分别保留、不计通过。复核high错误要求坏本地proof写outcome，按零写出约定撤回；真实缺package/plan先ValueError，models/adb KeyError也零ADB/零写出，统一诊断仅low延后。最终accept/338host，非Android；证据collect-parent/与collect-repository-acceptance/。

## W3 restore：null不是缺失、前台子串及inode夹具（2026-11-13）

原交付raw262144B截断，只提取完整event2sourceJSON、不补tail。首liveuser期望抛错为父误读，实际合同durablefalse/零mutation；inode初hookctxcall未拦text原闭包，改真实fakeADBls子进程rename/copy取得1RED。广义catch把坏/链接created当缺失、bool/duplicate/missingordinaryhash/扩大children/foreignAPKclaim共8子例真实RED，严格本地证明后18GREEN。再发现JSONnull返回None与缺失混淆（1方法3RED），resumed仅substring误认foreignsuffix/u10及主异常errors空（2方法5RED），外部精确小补丁后20GREEN/完整325GREEN。readoptional只catchFileNotFoundError，present顶层必须dict；APKclaim绑定冻结+实际；删除前重验nonce/stat，unknown保留；当前resumed须精确组件和u0。原BaseException身份与次生journal中断note保持。恢复单次已有recovered/outcome即拒，未来closure检查allRestored并传播不确定性，不自动重试。独立reviewaccept，缺preflight全文的限制已用实际定义核对；host非Android。证据restore-parent/与restore-repository-acceptance/。

## W3 prepare：printf夹具、marker长度与原异常API（2026-11-13）

首11方法1FAIL/3ERROR实际来自父fakeADB把shlex的literal backslash-n误比为换行；真实/bin/sh产生nonce+NL后只修夹具，原候选11GREEN。真实边界另揭旧两APKtransfer收据漏preflight、宽runner、bool/float/nonce尾换行、祖先链接、stat及marker33字节前缀接受suffix；head34/exact33bytes、canonicalASCIIstat和完整祖先检查经外部窄修通过。首patch一块old含真实换行count0未应用，不手改；下一小块精确修正。最终type(marker)isbytes仍遗漏，实际2RED再外部单条件修正。普通pm路径不证明APK内容，前后ordinaryAPKHashes及最终新alias重hash有2真实RED→GREEN。独立review误把零参cleanup()解读为cleanup(exc)，补原明确API/真实首错身份与次生中断、TEST-alone/activeinstrumentation拒绝后accept撤回major。保留原revise，不改正确API。root=actualOUT、digest/parse_kb为modulelevel须显式注入；29父/304全套仅host，不证明cleanup真实恢复或完整driver。证据prepare-parent/与prepare-repository-acceptance/。

## W3新增源依赖：合同和双复核的coherent漏项（2026-11-13）

源码适配片段8GREEN后，真实负例同时从合同files与两份复核sourceSHA256删新terminal_source_adapter.py，旧guard仍接受。只在可选files里列依赖不充分；hy3单组把它加入DRIVERS，带动REQUIRED/REVIEWED同时强制，14守卫/完整274GREEN。纯适配pin旧FILE确切SHA、保存安全helperAST、去旧四action尾和__main__、逐call加freezehook；仍未实现所有权/prepare/恢复，不能把sourcecallbacks占位当完整driver。原RED/候选/复核在source-adapter-parent/及source-adapter-repository-acceptance/，无设备作用或旧run重放。

## W3 outer lifecycle：终态原文哈希与中断恢复（2026-11-13）

首审误把terminal-observed.json当binding/sample receipt，建议startup hash；实际adb_terminal_observation.py把双读first原bytes写observed、另写terminal-receipt.json。真实fake-ADB先running后terminal，两SHA不同，finish必须用终态raw；补协调器/Kotlin原文后hy3复核accept，原revise保留。另presence用==会接受bytearray/bytes子类，transport须exactbytes；except Exception漏KeyboardInterrupt/SystemExit，实际RED为stops0/日志未关。hy3精确修正BaseException及secondary恢复后51新方法/完整265GREEN。恢复有两次各5s等待，不能称总5s。副作用/冻结/ownership仍由未完成adapterC证明，host不等于Android。证据lifecycle-source-review/、startup-parent/interrupt-fixed-candidate/及lifecycle-repository-acceptance/；旧run不重放。

## W3启动进度：首报告快照必须在同一总时钟下衔接（2026-11-12）

`initial_progress` 的有效形状是精确普通dict：phase非空str、activeCaseIndex为None或精确int0、startedNs为非负有界精确int；非法值须在任何callback/transport/write前拒绝，快照必须复制后写入poll-start。旧None默认保持行为。首clock晚于startedNs才允许进入poll；首phase使用startedNs，回调跨121s失败、120s等号通过，phase变化才从新鲜read_ns重置。测试先把合法None和合法完整dict误列为负例，导致候选假失败；修正后5/5真实fake-ADB通过。outer启动仍须传真实首报告并共享Popen前的240s时钟，不能以新loop重置预算；host证据不等于Android。证据`initial-progress-parent/`及`w3-terminal-initial-progress-20261112.{md,json}`。

## W3阶段窗口：首phase尚未命名也不能免预算（2026-11-12）

先前194GREEN/静态accept未覆盖第一轮：anchor_ns已从loopstart建立，却以anchor_phase!=None为五处预算前提；第一poll/sample/read耗121s时均真实错误ACK。新增2方法取得3RED，120s三等号案例原本通过；外部仅删五处phase-known条件，完整196观察+13守卫GREEN。不要把后续phase跨回调已修当首轮也已覆盖；未知phase时仍有保守起点和预算。startup接线还须显式传递首阶段起点、共享240s时钟，不能另起loop后重置窗口。证据poll-first-window-parent/red.*、poll-first-window-candidate/及同名repository-acceptance/；无真机/旧run重放。

## W3冻结守卫：最终裁定须区分类名、组件和异常继承（2026-11-12）

独立review原revise，父真实证据表明UnicodeDecodeError已继承ValueError；包收据exactRunnerVerified是qualifiedclass，RUNNER是package/class组件，拆分后相等，不能据两者表示不同改正确协议。11Python driver/adapter复核范围与既有Kotlin/build/实包证据分开。固定已验包19unique非空str类名作为本次profile加强，四类coherent收据实际4RED→外部单块修正→完整13GREEN；其他正确表示不做无据改动，保留原revise与父裁定。正确literal重复和非法中间路径原OSError两minor延后；不把纯guard207host方法当真实freeze或设备完成。证据outer-freeze-source-review/及outer-freeze-final-candidate/。

## W3轮询：回调耗时不能被新阶段重置（2026-11-12）

22方法初绿后，父端分别在实际poll/sample/read回调使host时钟推进121s，新terminal阶段重置anchor并错误ACK，三个子例真实RED。应在每个回调返回后先检查旧阶段120s窗口，再接受新阶段；外部仅加三处守卫后23方法/全套194GREEN。开始文件也须在clock/process/transport前持久化，process poll须精确int或None。静态accept遗漏该故障，不能代替真实时钟边界验收；原复核、RED与裁定保留于poll-source-review-hy3/、poll-order-fixed-candidate/、poll-final-candidate/。首阶段从loop起计时目前更保守，minor延后；此项只证明host stand-in，不证明Android/完整outer/旧gap恢复。

## W3片段接线：给出真实ACK API和nullable类型（2026-11-12）

首片段合并真实6编译错误：主代理窄prompt把TerminalAckFile误写为File单参数/read()、把FileRunState身份字段误写为nonnull；实际API是TerminalAckFile(existingCanonicalDir,runId)、readOrNull()、state.runId/sourceFingerprint与局部ticks/ACKfile可空。给出实际声明后外部精确patch，又出现File(File)多余包装真实1error；外部单行直传dir后58组783断言GREEN。不能改正确共享API/状态/测试迎合错误prompt，原失败与主代理上下文限制均保留。writer serialization须留在原try/catch内以保留wrapperPersistError；最终hash只取成功持久化返回bytes，ACKerror不改终态。证据`wrapper-{combined-host,api-fix-host,corrected-host,integrated-host}/`。

构建工具也误把真实relative adaptermap当absolute；首有效收据FAIL，修后8GREEN。下一canonical alias/dangling receipt真实4ERROR，不能先resolve再称拒绝alias，也不能用exists漏掉悬空链接；外部再修后10GREEN。旧source/报告不动。证据`build-tools-{candidate,key-fix,canonical-red,canonical-fix,integrated-host}/`。两条host编译参数名warning来自既有doubles，不能宣称-Werror。APK构建/核验不等于Android运行时/全W3完成。

## W3 wrapper整份空流：保留失败后拆独立片段（2026-11-12）

原hy4free整份wrapper请求自然903s终态CLI0/raw0，stderr精确`Empty stream: upstream ended the turn with finish_reason=error after emitting reasoning but no text or tool call`（17474chunks/3773031bytes/900757ms）；未交付源码、无观察到quota/auth错误、无主代理时长终止。原prompt/raw/stderr/收据保留于`wrapper-whole-empty/`，不把内部推理或退出0算候选、不重发原整份任务。按既有小片段路线独立分出IO helper、preflight/snapshot/writer与后续final接线，完成前不得集成或称wrapper已验。实际host编译/实包/设备门不变。

## W3三适配器组合：真实嵌套argv与复核上下文（2026-11-12）

- 初始父10检查3fail中，两条真实sourceFingerprint格式预检迟于callback，精确外部补丁补64位小写hex；另一条父测试错误只允许OSError，既有durable writer对stale路径抛ValueError也合法，按实际合同纠正。测试不能为假定异常类改正确writer。
- 外部fake-ADB测试把run-as嵌套脚本当裸cat/head/ln，实际3fail/3error；外部transport补丁又漏nonlocal，实际5errors。分别精确外部窄修、原件/真实失败保留。直接缺模块RED必须在无源码目录执行；一次同目录误测不算RED，另取真正RED。最终14新方法/整套171通过，非Android证据。
- 源码复核误归属_probe_path并怀疑gap=None；真实函数定义在terminal_observation_coordinator:62，validator要求gap存在且None。父import/validator复现后限定裁定撤回，无源码改动。供给依赖说明含糊属于主代理上下文限制，原revise/裁定均保留，禁止为不可复现意见改正确协议。证据`.omx/artifacts/w3-terminal-observation-20261112/remote-{review-reproduction,review-adjudication,external-final-red,repository-acceptance}/`。

## W3远端ACK：精确类型、完整标识符与占位路径（2026-11-12）

- 首发布器外部8方法2fail：bytes子类响应被接受，测试又把stage占位词当字面文件名。父边界再真实8fail/2error：五阶段均接受bytes子类，三个标识符的match/$接受尾随换行，math.isfinite先转换超大整数触发OverflowError。外部精确补丁改exact bytes/fullmatch/仅float有限性检查后，外部8+父6真实本地subprocess/file+父4边界及完整Python157通过。原件、RED和修补JSON均保留于`.omx/artifacts/w3-terminal-observation-20261112/publisher-*`。
- 测试用STAGE完整路径，禁止命令不能用rm子串匹配，因为合法w3-qwen-terminal本身含rm；单独外部测试修正，不改实现迎合假路径。exec-in退出0不证明完整stage，必须有界逐字节回读再fsync/回调/link/目录fsync。link一旦调用即保留publication_possible，目录fsync失败可留下完整final，停止且不重试/清证据。host /bin/sh与os.link/os.fsync站位不证明Android OEM命令、恶意同UID竞态防护或硬件崩溃持久性。

## W3报告读取：context400不是交付，提示形容词不是路径（2026-11-12）

- 原13方法测试请求自然终态CLI0/raw0，stderr为400 prompt is too long:100001>100000；实际prompt3696B，不能推断有效context来源、quota或主代理超时。原件保留，独立缩为4方法后87.465s完整交付；不重启live请求、不把外层0当成功。
- 首读取器缺serial/package/runId exact-str guard，父端真实3fail/9error；外部整块修正后GREEN。小测试又把父prompt中形容词generated写进路径字面量，3pass/1fail是测试/提示歧义，外部单行修正而非改实现迎合假路径。外部与父组缺模块RED、修后11GREEN/完整139GREEN及源码复核accept均保留于`.omx/artifacts/w3-terminal-observation-20261112/report-reader-*`。真实本地fake-ADB/shell不证明Android命令与新host-loop已验。

## W3 ACK文件：规范路径与复核意见需真实复现（2026-11-12）

- 首候选Kotlin缺return真实编译失败；下一版比较rootPath.normalize()与realPath，13+6检查绿但父dot/parent别名检查失败。应比较传入File仍可观察的原Path，保留原始祖先symlink检查；外部整块修正后23GREEN、API33无JDK编译通过。
- 最终源码复核把重复斜杠归一化误归因于toPath，且引用当前源码不存在的非局部return out。JDK21与真实reader复现证明File构造时已去重斜杠，File.path也无法恢复历史String；dot/parent路径仍真实拒绝。原revise保留，限定复核撤回两条并accept；不为不可复现意见改正确源码。证据`.omx/artifacts/w3-terminal-observation-20261112/ack-file-{candidate-check,corrected-green,boundary-green,review-reproduction,review-adjudication}/`。这仍非Android运行时或旧末端gap验收。

## W3 ADB采样器：完整消息恢复与精确补丁验收（2026-11-12）

- CBC测试响应自然退出0、raw恰262144B、最终result字符串截断；4个完整事件中event3仍含完整可解析17测试源码。只恢复这个完整JSON消息、核语法/摘要并做真实缺实现RED，不补截断尾、不宣称根JSON/最终result完整，也未重发测试请求。event1另有CLI自行“Connection interrupted. Retrying automatically...”文字；只能称主代理未重发，不能笼统称CLI无重试或推断quota错误。原件/完整事件/恢复源码与边界在`sampler-tests-candidate/`。
- 首实现只输出伪Write文字、没有实际候选文件/所需JSON；仅抽文本到隔离目录做拒收验收，26方法真实6fail/3error（ticks构造溢出、str/bytes子类、空字段AttributeError）。外部精确old/new窄修又只换if行而移错旧body，IndentationError在执行前拒收；独立单块修正后26GREEN、整套128GREEN。候选/语法失败/结构化替换均保留；伪工具不当实际落盘，补丁存在不当通过。证据`.omx/artifacts/w3-terminal-observation-20261112/sampler-{implementation-candidate,boundary-fix,indentation-fix,parent-acceptance}/`。
- 真子进程stand-in证明默认subprocess错误/超时传播和参数引用，不证明真实Android读取或旧末端gap已修复。进程采样器只返回测量，零值不被补成默认成功值；预算继续由协调器独立拒绝。

## W3 协调器：预检时机、时钟即停与源码复核复现（2026-11-12）

- 首实现22+7检查绿，但扩展父检查真实9fail/1error：output全路径lstat跟随中间symlink且缺父目录到发布才失败；倒退时钟收齐四次才拒绝；str子类/NaN与binding类型校验遗漏。全部输出父链须在callback前nofollow预检，时钟首次倒退立即停止，快照/严格有限JSON再发布。原候选拒收，独立修正34GREEN、最终102GREEN；具体原失败计数仅对应保留的那一轮，不外推后来新增子例。
- 最终静态复核accept附minor称缺PID/startTicks会KeyError；父实跑两者均在sample校验先ValueError、无ACK/写出，故不为不可复现建议改源码。保留原意见与父裁定并补回归。证据`.omx/artifacts/w3-terminal-observation-20261112/coordinator-{implementation-candidate,boundaries-fix,final-review}/`。本地回调与文件顺序不是实际设备采样/传输证明；host monotonic不得混作device uptime。

## W3 文件原语：skip-green、目录 FD 自比较与候选循环回归（2026-11-12）

- 缺实现时吞 ImportError 并 skip 全套会得到 exit0，不能当 tests-first RED。必须直接导入并保存真实 ModuleNotFoundError；本次首稿 30 skipped 已拒收。实际 growth/replacement 用原始 os.read delegate 修改真实 fixture，以 FD dev/ino 选目标并确认修改确实执行，不能绑定 os.open 的绝对路径拼写。
- 持有目录 FD 后再次 fstat 同一个 FD，不能发现该目录的名字已被替换。原32项绿色后，父端11项仍6fail：exact int/str 子类、root symlink祖先、root/父目录真实置换。应复核命名路径与保留目录链的 dev/ino；文件比较用 ns 元数据，最终路径 stat 不跟随 symlink。静态审查的 low 评级不能豁免已明确且被复现违反的合同。
- 下一候选在 staging 的 while True 成功 open 后漏 break，实测43项4fail/21error，出现 EMFILE；是本次源码循环回归，不能归因为配额、CBC启动、系统fd限制或设备故障。原件/失败保留、零设备，独立窄修后须重跑完整套件；不要提高全局限制或接入失败候选。
- 证据 `.omx/artifacts/w3-terminal-observation-20261112/io-{tests-helper,tests-correction,implementation-candidate,implementation-correction,boundaries-correction}/`。原子发布需先 staging 写完整/fsync，再无覆盖发布；目录 fsync 失败不能宣称新名字已持久化。私有单写者范围不提供恶意同UID写者下的原子 compare-and-unlink 保证，也不代替 Android/真机验收。

## TECNO USB 枚举不等于 ADB 可用（2026-11-12）

- **现象/已证原因层级**：Mac IORegistry 能枚举 TECNO POVA Slim 5G，但 `adb devices -l` 为空、Mobile MCP 仅列 iPhone；native USB backend 日志持续 `usb_read/usb_write failed with status: e00002ed`。本机 IOKit 定义该状态为 `kIOReturnNotResponding`，只能说明 USB 读写未响应，不能推断 USB 调试关闭、锁屏、线材坏或其他 App 抢占。
- **恢复与边界**：本次 `adb reconnect`、一次 host daemon kill/start 后仍未恢复；不重复重启或触发推理。需先恢复实体传输再 fresh preflight，不能用 USB 清单冒充可执行设备，也不能重放已消耗 heap run。用户侧解锁/插拔后的恢复仍待观察。证据 `artifacts/verification/w3-android-retry-20261112-092504/receipt.json`；原诊断 `w3-android-transport-20261112-080920/` 保留。
- **后续已观察恢复**：用户确认重新插拔并解锁后，ADB 先空、`reconnect` 已识别精确 serial，随后列表/`get-state` 均为 device；fresh shell 读取到 KM9/API35/arm64、Awake、`showing=false`、MemAvailable 3023384 kB。只能确认此时恢复，不能把具体根因归给线材/锁屏/daemon 或宣称持续稳定。证据 `artifacts/verification/w3-android-recovered-20261112-0936/preflight.json`；零推理、零安装。

## W3 实体 heap：末端采样缺口与同 run 续取（2026-11-12）

- **现象/已证边界**：原物理 heap 单片报告 completed/1 构造2解码，但主机在 instrumentation 结束附近读取 `/proc/PID/status` 遇 ENOENT；最后观察报告仍 running，与最终双读报告不同。冻结验收严格拒绝 non-null `terminalObservationGap`，实际 19/20，只缺 typedResourceObservations。不能把 exit0、完整终态或低于 RSS 停止线写成诊断通过，不能事后清 gap 或重放已消耗 run。
- **恢复/范围**：政策 stat 再次遭遇已知 ADB 参数重组，只把原同 run 读取适配为 `shlex.join` 后续取；冻结源码不改、零新解码。首包装器误引用物理 adapter 未导出的 EVIDENCE，AttributeError 在 ADB 前终止；独立 v2 用已固定路径并先重跑 preflight 后成功。原 collect 没单独重定向 stderr，仅工具结果观察可用；不能宣称磁盘原 stderr 已保留。收据 `docs/validation/w3-qwen-heap-physical-observed-20261112.json`，raw/续取 `.omx/artifacts/w3-qwen-heap-policy-continuation-20261112/`。两个别名与 staging 已精确恢复；后续采样器修复须面向未来独立合同，不追改本次结果。

## 原始 partial 保持不能只检查长度（2026-11-12）

完整归档检查器原先只核两个历史partial尺寸，父端另有SHA核验但未纳入可重跑入口。隔离副本中保持尺寸、翻转一个字节，两次真实负向检查均被误接受（2fail）；补固定SHA256后3/3回归通过，原始归档/partial不改。任何“历史原件保持”结论须由对应内容摘要支撑，不能只凭文件存在、大小或manifest中的true。证据`.omx/artifacts/w3-runtime-materials-20261111/partial-integrity-20261112/`及`docs/validation/w3-runtime-partial-integrity-20261112.md`；不因此扩大source-to-AAR或发布结论。

## 超时归档可独立恢复，但不能抹去失败或唯一归因（2026-11-12）

固定cpuinfo/onnx归档最初经默认代理读取TimeoutError；保留原终态/partial并核SHA后，独立调用级`ProxyHandler({})`的HEAD返回200，随后各一次GET取得完整归档且声明SHA1、独立SHA256/尺寸匹配。有效恢复是新目录保留直连收据，不改全局代理、不覆盖旧11/13记录、不自动重试；HEAD本身不等于完整下载成功，不同时点的成功也不证明代理是所有超时的唯一根因。合并13/13归档及27文本的检查仍不证明最终AAR成员、source-to-AAR或生产通知。证据`.omx/artifacts/w3-runtime-materials-20261111/ort-source-archives-recovery-20261112/`和`docs/validation/w3-runtime-source-archives-complete-20261112.md`。

## 固定源码归档不一定含根级LICENSE文件（2026-11-12）

ORT依赖材料逐归档重核时，mp11的boost-1.82.0归档没有任何独立license/licence/copying命名文件，但README/头文件明确BSL1.0声明；KleidiAI v1.20.0两全文位于根LICENSES/，只筛根级普通文件实际AssertionError。保留首partial后仅加入根LICENSES直接子文件，并保留mp11三份源码声明，不能补造包内LICENSE。cpuinfo/onnx归档流超时保留partial、不自动重试，只补精确commit LICENSE GitBlob且archiveDigestVerified=false。最终11归档SHA1匹配、26文本逐字节核对、68旧材料及108/120/327/448守卫通过；不是最终二进制组成/生产通知通过。证据.omx/artifacts/w3-runtime-materials-20261111/ort-source-archives/与validation/w3-runtime-source-archives-20261112.md。

## KV288政策收取须保留真实variant（2026-11-11）

旧heap collector为原arena变体创建`normalized=dict(report,probeVariant='cpu-arena-off-v1')`；复制到KV288后虽然已换verifier，仍将真实kv288身份写回旧值，父离线政策测试真实1fail/6。只将适配中的该句改为`normalized=report`，原报告不改、容量288/实际variant仍严格检查，最终完整冻结7组GREEN；本次唯一run双读/有界21B政策收取及18父门通过。原512 collector/合同保持，不通过放宽新verifier或重新推理恢复。证据`artifacts/verification/w3-qwen-kv288-emulator-20261111/{parent-first.stderr,parent-frozen.stderr,allocator-policy-collected.json}`。

## KV288父端prepare：跳过唯一旧文件后需建目标父目录（2026-11-11）

新隔离prepare跳过原唯一test-src文件后，该目录未由复制循环创建；copy2新wrapper真实FileNotFoundError，后续build被缺candidate收据阻断，零Gradle/device。保留partial source并补明确mkdir后fresh79文件/75-taskbuild/实包及32组6003host通过。未来跳过整目录唯一文件时须显式建新目标父目录；准备失败后先核恢复再执行依赖步骤，不把后续阻断当第二根因。证据artifacts/verification/w3-qwen-kv288-emulator-20261111/{prepare-first-failure.json,source-prepare-missing-test-dir/}。

## 采集适配计数断言失败与只读恢复（w3-qwen-arena-emulator-20261111）
- 症状：采集在报告双读与六模型后哈希后失败于冻结父文件 line130 断言 `collector.count(old)==1`：实际完整堆源码含两处精确报告目录字面量（prior_collection + 未用 main REPORT 赋值），故计数=2。
- 已验证成因：327 项冻结合同所绑定的原 heap 父脚本有两处报告目录字面量，适配误假设仅一处；非 adb 远程 stat 分割错误（本批已用 shlex.join 修复，未发生）。
- 有效只读恢复：独立 `collect_policy_report_path.py` 仅改这两处字面量后重绑新 OUT/RUN/APP/带引号 adb，仅调用 preflight+collect_policy，无模型重放；三个测试用例的一次离线回归加一次同 run 只读续取成功。
- 边界：恢复仅限采集脚本字面量重绑，不动原始 327 冻结/适配/APK/报告/stderr；`nativePolicyEffectVerified=false` 仍 false；跨环境差异不证明运行时唯一成因。
- 证据：`artifacts/verification/w3-qwen-arena-emulator-20261111/collection.json`、`collect.stderr`、`policy-continuation-frozen.json`、`allocator-policy-collected.json`。
- 注：运行前免费 hy3 静态审阅接受/无发现，但仅审阅局限且漏掉此适配计数错误，非完整运行后批准，不可称为运行后全量批准。

## W3 模拟器政策收取：ADB 远程参数需引用（2026-11-11）

- **现象与已证原因**：模拟器唯一 heap run 已完成且报告双读成功，但政策收取 `adb shell run-as ... stat -c '%u %d %i %s' no_backup` 的 Python 多参数形式在远端被重新拆开，真实 exit1，stderr 把 `%d/%i/%s` 当文件。仅将远端 tokens 用 `shlex.join` 合成一个 shell 参数后，同一只读 stat 返回完整四数字、exit0，证明是远端参数边界问题。
- **有效恢复与边界**：原冻结父脚本/APK/报告不改；独立 `collect_policy_quoted.py` 先核原冻结、精确模拟器身份及同 run 双读/六模型/包摘要，再只读续取，保留 same UID/device/inode 和有界21B/精确20B门。没有重新解码、放宽路径/资源或消耗物理合同。原错误与续取收据均保留于 `artifacts/verification/w3-qwen-heap-emulator-20261111/`；本次20诊断检查通过仅是模拟器单片证据，不关闭W3/R4/R5。参见 `docs/validation/w3-qwen-heap-emulator-observed-20261111.md`。

## W3 ORT 依赖映射：host 工具、声明摘要与补丁 guard（2026-11-11）

- **现象/已证实原因**：有界映射首稿把 protobuf/date/GSL/flatbuffers 四组补丁全部写成受 Patch_FOUND 控制；固定源码实际只有 protobuf/flatbuffers 有同一条件，date/GSL 在所查声明中没有。补丁命令存在或 Found Patch 行均不足以证明历史 patch 成功。首稿归档后交免费 hy3 定点订正，父逐行核对。
- **恢复/边界**：14 条 Fetch 区分 13 源码依赖与 Linux host protoc；deps.txt 的 SHA1 是声明而非本次归档实测。mp11 没有显式 URL_HASH 参数不证明别处没有校验，通用 Boost 通知不证明 mp11 的完整归属；嵌套 ORT Eigen 精确 commit 与 Sherpa 5.0.1 是不同依赖边。通知存在不证明最终静态成员。证据 `docs/validation/w3-runtime-ort-dependency-map-20261111.{md,json}` 与本机 `ort-dependency-notice-map/`；不由这份映射关闭 W3 或发布门。

## W3 嵌套 ORT：release tag 不等于实际执行头（2026-11-11）

- **症状/已证实原因**：出处首稿将 tag 与 executed 工作流参数写反，虚构 overwrite 矛盾；将 attestation 404 当不存在，并将组合 ZIP 与单 ABI 工件 ZIP 摘要不同当矛盾。固定 GitBlob 和真实 job/head/log 证实 tag 为 1.27.1/release-v1.27.1-2/false，实际执行为 1.28.2/test-ci/true；collect 日志与后者一致。
- **恢复/边界**：保留拒收首稿，免费 hy3 定点订正后父核 2 GitBlob/50 精确摘录。404 只代表具体查询，大小相符不是成员摘要相同；不同容器摘要不作等价判断。只审 arm64 构建配置，不能推广四 ABI 或证明 final AAR 无其他补丁。HF 首次拒绝后空重试使整体 job success 不能证明镜像发布。证据 `docs/validation/w3-runtime-ort-parent-assessment-20261111.md` 与本机 `ort-provenance-followup/`。
- **父校验器也须匹配收据形状**：既有 fileHashes 的值为 `{bytes, sha256}`，与实际 SHA 字符串直接比较的断言失败是脚本类型错误；按字段比较后原 50+15 文件均保持。候选创建前 Read 的 tool ID 必须实际关联，不能照搬最终文字对错误路径的误报。

## W3 heap实包与复核：真实布局及冻结清单（2026-11-11）

- **合成夹具不能复制错误目录假设**：实际prepare找不到`android-qwen-file/QwenLoadCore.kt`，真实文件位于`android-qwen-load`。首次在写source/Gradle前停止；按真实目录先改夹具得到9errors RED，再修prepare/build/device三处，19检查GREEN。未在错误目录补源码或改旧冻结文件。
- **冻结必需集合要与实际evaluator一致**：hy3父复核发现基础`cases.json`漏项；只删其hash的完整合成合同仍触及mock ADB，真实1RED。补入设备守卫required后20路由/103定向/完整tools691通过。父复核没读Kotlin，不能把其事件数量/标签疑问当源码定论：原core必有initial/final persist，七标签由另一份独立源码复核确认，冻结需使用该映射。原changes-required报告与父裁定分别保留。
- **core返回错误需先进入根状态再做易失败采样**：新增同时decode+heap失败检查发现外壳根错误被heap错误覆盖，真实1RED；外部定点补丁只在case-returned采样前传递既有case错误，33组274通过。清理真实结果和primary/persist/heap错误仍分别记录。证据`docs/validation/w3-qwen-heap-wrapper-20261111.md`及同名父/源码复核JSON；以上不证明设备、内存收益或W3完成。

## W3 heap记录器：JSON null与首错身份（2026-11-11）

- **`isNull`通过不证明nullable字段存在**：外部heap记录器首稿实际Kotlin-Werror及13组80断言通过，但`JSONObject.put(key, null)`会移除key，未采到的数字、无活动case和成功事件errorClass未显式持久化。补`has(key)`与溢出后首错对象身份检查，原稿真实12项失败；独立hy3只修`JSONObject.NULL`和先检查sticky首错后检查事件上限，13组100断言通过。原稿/初绿/RED/修正版保持；不弱化报告字段门，不把片段验收当外壳/Android/设备通过。整份wrapper此前自然939.548s空响应、15Read/0Write/零候选，非429或监督超时，内部原因未知；只走独立小片段和接线任务，不重发原请求。证据`.omx/artifacts/w3-qwen-ort-lifecycle-20261111/heap-hy3-resume1/`。

## W3父工程构建依赖与ABI（2026-11-11）

- **子依赖默认版本不等于父工程实际include，开关也不能跨ABI泛化**：原kaldi-decoder候选eigen模块写3.4.0，但固定Sherpa父CMake先登记自身模块路径、再附加decoder路径；官方实际四ABI均下载/configure5.0.1。ARM WebSocket ON并下载websocketpp/asio，x86两ABI OFF。核固定父模块和原始job行号后，补实际5.0.1与两组件许可，原3.4.0候选材料不改；下载/全局开关仍不证明最终so内嵌成员。13来源GitBlob/20摘录/四ABI/原50材料摘要通过，无重建或设备动作。证据docs/validation/w3-runtime-build-dependency-reconciliation-20261111.md及本机build-dependency-reconciliation/。

## W3 heap父端路由补充（2026-11-11）

- **准备时核过host不代表构建时仍有效**：build首稿只核host/base收据字节，prepare后改HeapWrapperChecks.kt仍进入renderer；初测试被后续其他断言掩盖而意外通过，加sentinel后真实RED为RuntimeError。恢复是在渲染/工具前重跑verify_host并逐个绑定仓库与准备源码；19路由/22报告检查通过，原误通过与RED保留。RSS读取仅保存uptime前后区间及一秒容差，不能写成精确时钟对齐/峰值。所有本项为合成/离线父验收，未构建或操作设备；外部wrapper、实包和独立复核仍缺。证据docs/validation/w3-qwen-heap-routing-preparation-20261111.md及本机heap-routing-preparation/。

## W3 heap父验收补充（2026-11-11）

- **单片准备不能沿用全量完成判断或缺省资源观测**：新heap父端以原65输入清单固定index25；保留原共同模型/配置/释放检查，但不合成其余64行，也不输出全量quality/production通过。原host摘要没有min available，必须从同PID/startTicks原始RSS重算；终态观测gap不能忽略。扩展加载时间/typed数值/观测gap先7fail，修正后21项通过。采样API为非原子，不强加三项求和；原守卫允许uptime取整windowAge>=-1，新parser先误拒负0.005、巨大整数溢出，RED1fail/1error后修正。边界非空label不足以证明覆盖，须实际source复核后冻结语义映射；传递evaluator必需hash漏项的RED也已保留。所有本项为父端合成/离线验证，没有新Kotlin/实包/设备或native根因证明。证据docs/validation/w3-qwen-heap-parent-preparation-20261111.md及本机heap-parent-preparation/。

# 助手执行踩坑记录

适用于本项目主代理、cbc、dsh、OpenCode、Copilot、agy、Ollama、qoder 和原生子代理。执行前读取与任务相关的条目；遇到相同错误先查本文，再决定是否继续。只允许输出候选、禁止工具的任务，由主代理把相关条目附到提示词，不能为读本文扩大工具权限。

记录日期：2026-11-06。版本、额度、设备连接状态都是当时观察，继续工作时须重新核对。以下本地证据路径均相对 foundation worktree；`artifacts/`、`.omx/artifacts/` 是本机保留证据，不保证随 Git 分发。

## W3 host probes: validation and native dispatch receipts (2026-11-10)

- **不能把固定维示例套成当前ONNX图事实**：2026-11-11只读内存报告正确指出“正数导出KV维可覆盖config”，却将未读取的当前模型称为固定512，并称pattern缓存体量小；父端核实际decoder SHA后按官方protobuf schema读取60输入，56KV均为float32 `[batch,max_total_len,8,128]`，长度是动态符号，当前走config512分支，基础KV静态数据量112MiB。7项解析检查通过，首名称筛选/手工fixture长度错误保留；pattern体量、allocator保留和峰值仍未测，不能由源生命周期推定实际RSS或跨片泄漏。公开官方构建日志另实证TTS/diarization=ON，不能因应用仅ASR排除对应依赖。边界与证据见`docs/validation/w3-qwen-memory-lifetimes-20261111.{md,json}`、`w3-runtime-official-provenance-20261111.{md,json}`及本机`.omx/artifacts/w3-qwen-ort-lifecycle-20261111/`。

- **framework/shell目录同inode不保证readlink字面相同**：arena真机政策收取把framework `/data/data/.../no_backup` 与shell relative解析出的 `/data/user/0/.../no_backup`比较，误记receipt/file false；首补核又假定两种absolute path均解析到同一字符串而断言失败。原报告/评分保持，后仅续取同run，精确三路径目录及配置各自sameUID device/inode相同、有界21B读取精确20B/SHA/receipt/provider全部一致，5错inode/错路径变异拒绝。补核原冻结源码/APK未改，也不新增模型调用；完整验收仍因内存停止等六门失败，不能用路径恢复声称native配置生效。证据`docs/validation/w3-qwen-arena-observed-20261111.md`及本机`framework-path-diagnostic.json`、`allocator-policy-framework-collected.json`；未来父端不能单凭shell规范化字符串代替framework路径身份。

- **复核清单要区分直接绑定与传递绑定，同run续取不等于重放**：arena父工具只读复核指出essential集合遗漏，部分Kotlin其实已受candidate/sourceFiles与APK摘要传递绑定，但baseline检查确未直接必需；以完整有效合成合同为基线逐一删四项，实测4fail后补直接集合。政策收据缺失则结构化失败；基线collection完成、政策读取中断时新增collect-policy，先绑原双读/run/source/六模型收据和当前APK/设备报告SHA，仅有界续取；评分改用新安全basename保留旧失败。相关RED/原报告保留，22父+原8父/31metricsGREEN；零新native，合同冻结后只读preflight锁屏零安装。证据`docs/validation/w3-qwen-arena-parent-review-20261111.md`与本次`parent-review-{red,fixed-green}/`；“复核无阻断”不能代替未过设备门。

- **新父端合同不能允许空摘要清单，政策取回也须有界**：2026-11-11 arena父端首稿17项扩展检查中，essential hashes可为空、收政策用cat无上限；两故障回归实际2fail后，要求关键实包/计划/生产保护/父源码/双复核/协议摘要全部存在，并仅head读取21B核精确20B。新增main入口无报告时又缺显式计数/coverage，KeyError RED后补countsAreExactTerminalEvidence=false/completedConsistentRows=0；最终17检查通过。全部synthetic/host，零设备/native；不把通过当合同已冻结或配置native生效。证据`.omx/artifacts/w3-qwen-arena-wrapper-20261111/{parent-boundary-red,parent-entrypoint-red,parent-final-green}/`，旧父工具/已消耗run不改。

- **固定20B配置也需有界读取，不能先readAllBytes再校验**：W3arena候选首稿Kotlin-Werror/12组39断言通过；补2GiB稀疏文件替换后，verify实际抛`OutOfMemoryError: Required array size too large`，无大数组分配成功。长度预检后仍readAllBytes有增长窗口；独立外部定点修正两处均最多读21B并核EOF/内容，13组41断言/集成后重验/AndroidAPI34classpath通过。原稿/RED保留，不改测试期待，修正版原样集成但未接native/跑设备。证据`docs/validation/w3-qwen-arena-policy-20261111.md`与`.omx/artifacts/w3-qwen-file-20261111/memory-investigation/policy-oversize-red/`；该稀疏fixture在finally精确删除，其他测试临时根保留。


- **provider后缀须追完整条件；host适配器与真实runtime分开**：W3静态调查初见Qwen只认精确cpu，完整源码确认该flag仅与IsCudaProvider合取，cpu:config仍禁用CUDA，不需据局部条件修模型。session.h模板原样转发provider；固定AAR字符串/JVM DTO和18项源码片段检查仍不证明native配置生效或节省RSS。首次C++检查将日志宏设空导致两项unused Werror，只改测试日志适配器为fprintf；首次JVM stdlib选到非runtime jar导致NoClassDefFoundError，明确使用精确kotlin-stdlib-2.2.20.jar并核Intrinsics.class后4项通过。保留初始失败，未改全局环境/生产/旧run；证据`docs/validation/w3-qwen-arena-feasibility-20261111.md`与本次memory-investigation目录。


- **强停后的decode0仅是最后持久化计数**：2026-11-11 TECNO Qwen FILE首片时采样RSS1703575552B超过1.5GiB，父按合同force-stop。原报告非终态、stage.createReturned=true/acceptAttempts1/decodeAttempts0，计数写入在dispatch之前，不能据此宣称实际零decode/显式release成功。父评估新增“非终态精确计数未知、另保留lastPersisted计数”回归，RED(0isnotNone)→8testsGREEN；原报告/初次评分不改，同run双读及恢复通过，无重跑。独立复核另把Instrumentation.onStart误称App主线程并建议额外smoke；官方Android参考确认start()新建线程调用onStart()，runOnMainSync需非App主线程，原说明保留并由父订正，不加试跑。父prepare另曾复制不存在的gradlew.bat，零build/device失败后保留partial，仅复制POSIX所需wrapper并前置核必需文件。证据`docs/validation/w3-qwen-android-file-{observed,wrapper-review}-20261111.md`及本次`.omx/artifacts/w3-qwen-file-20261111/`。

- **failed 状态不能代替异常与片段绑定**：2026-11-11 Android FILE外壳首稿21组/343断言通过，但补充检查发现外层err只写局部Throwable，未进入FileRunState，锁屏/model/constructor失败的报告errorClass/message均null；case失败又提前清空activeCaseIndex/起始时间。加入实际异常类与失败片段绑定回归后24组有5组失败；原稿/RED保留，交独立免费hy3定点修正，不因status=failed就认定故障证据完整。同次扩展Speech tools首跑56项12fail/4error，命中本文第7节Ruby2.6混用3.4gem；只对测试子进程对齐已安装Ruby3.4.11后同56项通过，未改生成器/global环境。证据`.omx/artifacts/w3-qwen-file-20261111/{wrapper-errors-red,wrapper-clock-red,speech-tools-ruby-aligned-result.json}`。

- **success终态也可只剩工具语法标记**：2026-11-11免费hy4free Android FILE外壳候选自然结束2119.316s，CLI0/result success，但result原文仅`｜DSML｜｜tool_calls`，8Read/0Write/0Edit、零文件、无tool error或quota错误。写沙盒与context摘要均保持；拒收，不把模型内部/工具语法当源码，不因时长终止，不重发原多文件任务。恢复为独立小模块（输入字节校验、可见Activity、薄调度）逐项host验收；实包/设备仍须后续。证据`.omx/artifacts/w3-qwen-file-20261111/helpers/odai-hy4free-w3-qwen-file-wrapper-20261111/parent-assessment.json`。

- **两阶段结果取回不等于阶段完成**：2026-11-11 Qwen Kotlin core首稿38组/224断言通过，但补充阶段状态与最后前台门回归后42组有16组失败。源码在`text()`返回时提前标stage completed，从未写stage.errorClass，且缺少每阶段释放后的gate；因此释放失败仍标completed、最后结果/释放后失去前台可使整case虚报completed。新失败已保留并交外部助手定点修正；回归要求先释放、再检查阶段结束gate、最后标completed，原错误与releaseError分开保留，gate失败不新增下一阶段。此时尚无新设备调用或修正通过证据；见`.omx/artifacts/w3-qwen-file-20261111/core-review-red/`及隔离`CoreChecks.kt`。

- **Git中文路径盘点用NUL输出，别按quoted显示名比对**：Wiki精确stage后`git diff --cached --name-only`将中文名转成带引号的八进制转义，父端按真实文件名比较失败；当时零commit/push。只改为`--name-only -z`并按NUL拆分，确认仅目标页后提交/推送，远端内容逐字回读通过；不更改全局`core.quotePath`，也不因本地检查失败重复发布issue。原错与恢复见`.omx/artifacts/w3-model-reselection-20261110/redistribution-materials/publication/`。

- **AVD控制台身份响应含CRLF，逐行精确校验**：模拟器父脚本首preflight假定`name\nOK`，实际ADB回`W3_Qwen_Load_20261110\r\nOK\r\n`，零安装/构造时断言失败。保留raw与首稿，仅对控制台响应使用`splitlines()==[精确AVD名,"OK"]`；三种错名/失败状态/多余行仍拒绝。随后唯一模拟器load-only completed、28父检查/双读/恢复通过；不能借换行订正放宽serial/qemu/API/ABI，也不改实体run。结束时最后一次`/proc`读取在instrumentation退出后失去PID，已保留terminalObservationGap，不能称连续采样或true peak。证据`artifacts/verification/w3-qwen-load-emulator-20261110/`及`docs/validation/w3-qwen-emulator-load-accepted-20261110.md`。

- **Kotlin copy参数的副作用会被旧receiver覆盖**：Qwen加载core首修正中`s = s.copy(loadNanos = elapsed(t0))`先求旧`s`，elapsed捕获计时异常并更新`s.errorClass`，随后旧copy清掉错误；真实17组host检查15过、2组错误completed。外部定点订正先取duration局部值，再从当前s.copy，17组/63断言通过；保留首稿NoSuchFileException(File/String)编译错误。native/Android设备尚未运行，不能写成真机析构已验。证据`.omx/artifacts/w3-model-reselection-20261110/android-load-host/candidate-core-{fixed-diagnostic,final}-result.json`。

- **CBC Empty stream 与退出0分开判定**：Android Qwen多文件候选在988.938s自然结束，CLI exit0，真实result为`error_during_execution`/`empty_model_response`，原文`Empty stream: upstream ended the turn with finish_reason=error after emitting reasoning but no text or tool call`，候选目录零文件。已验证的写sandbox与无permission denial不证明交付；本次不是429、没有监督超时。保留原prompt/stream/result，不重发同任务。可推进不同的小范围纯core任务，但不能假造源码或把助手内部推理作为交付。证据`.omx/artifacts/w3-model-reselection-20261110/helpers/odai-hy4free-w3-qwen-android-load-20261110/parent-assessment.json`。

- **fsync前先flush；终态在清理后写**：隔离VAD原稿对buffered Python file先fsync且提前completed，不能证明结果已落盘或清理完成。修正后先flush再fsync，持久化running/planned rows，异常保留原错误，清理后写终态；5/5fake-runtime及13组实际报告双读通过。不要将Python引用清理/GC写成C++析构ack。
- **路径与异常检查不能被清理掩盖**：Qwen首稿有list<=set、realpath后再检查symlink、del recognizer后读取、setter错误被未初始化samples掩盖。保留原RED，逐项外部候选修正并通过9项fake检查；在realpath前逐组件检查且拒绝有symlink祖先的model root，finally变量先初始化，释放后不再读已删除局部。
- **计数放在真实native调用边界**：两阶段Qwen原稿在create/setter/accept前就递增decode数，14组故障检查有6组误报。免费hy3将三处递增与报告持久化移到accept成功后、decode前；最终14/14通过，真实13输入22次decode一致。计数是dispatch尝试，decode抛错仍计数；不能把计划数当实际调用。
- **构建含静音修复不等于当前模型已拒绝静音**：实际Sherpa2.0.1/af2d8d81源码含#3907，但它检查encoder特征而非原PCM。本批强制语言的全零输入仍出文本；自动语言4非语音为空却将AMI英文C段误判中文。两阶段仅开发集通过，独立集/手机/许可仍另验，不反推上游全部模型有缺陷。
- 证据：`.omx/artifacts/w3-model-reselection-20261110/`、`probes/host-qwen-asr/`及`docs/validation/w3-qwen-*-20261110.*`。原失败、helper原始事件和各版本摘要保留。

## 1. 所有助手：进程成功不等于任务成功

- **当前表格与历史日志分开**：2026-11-07复核发现F09维护人、F10普通UI/PAD smoke、F11的7/10和
  F12 Apple原生运行仍沿用旧待办。以精确报告/commit/APK核实后更新当前F表和一页矩阵；历史条目保留日期。
  原P0–P11阶段10/12不等于生产完成率。辅助审查误把F02本地publication解释成远端上传，并建议旧`.4`；
  父核实最新为`.6`本地已过、远端未执行，不能整体照抄。证据`artifacts/verification/roadmap-status-audit-20261107/`。
- **冻结运行不要绑定可变全局指针作唯一完成判据**：Apple原`freeze.py --verify`最后比较全局benchmark manifest，
  后续新增Identity组后必然失败；440冻结文件/86原生检查未变。保留原exit1，补只读原生证据与旧分组保持检查，
  不改旧冻结summary/脚本、不重跑推理，也不把原全量命令说成通过。
- **给助手实际schema再定契约**：notice checker最初父提示只给index两字段，但真实`.6`有七字段含来源/未批准说明。
  实际74材料验证RED后，允许冻结reference里的provenance字段、严格可选schemaVersion，仍绑定外部SHA和全部字节；
  不删metadata来迎合检查，不把preservation变成许可批准。见`f02-nexus-notice-materials-20261107/`及助手父处置。


- **活动测试也可能停在内核冻结**：Identity原run01停7行，PID31731/活动instrumentation仍在，
  主/测试线程wchan均 `do_freezer_trap`，ActivityManager isFrozen=false；不能据此判OpenCV死锁。
  确认普通staging空且保留私有hash后，仅前台化同PID一次，原请求自然完成35行/close/lease/清理。
  该受干预运行排除干净基线。恢复原包后，新的受控Schema2在计时前启动首页并做72个RESUMED检查点，
  同一新请求完整通过且原状态再次恢复。内核/OEM具体触发仍未证明，不改系统freezer设置、不杀进程重跑。
  主线程读状态、instrumentation线程断言，避免检查异常直接杀主looper而丢失败报告。
  RSS必须标识包含UI，检查点不等于连续可见；证据 `artifacts/verification/android-identity-foreground-resources-20261107/`。
- **传输prepare/stage分开**：新目录先执行本地prepare生成payload-manifest，再stage。
  父遗漏prepare时stage先创建了登记的空目录后报FileNotFound；无APK复制/安装/推理。
  保留错误、补prepare，再校验同目录/intent继续，不能另开推理或把准备失败记为SDK失败。

- **预编译宿主与测试包装分开**：当前Apple标准hosted非UI XCTest首次外部`TEST_HOST`先被
  build-for-testing的宿主目标选择、再被build的产物相对路径检查拒绝，均未运行设备。
  仅编译新test bundle、动态链接实际App根目录的debug dylib，由安装版`xcodebuild.xctestrun(5)`
  参数提供独立测试副本宿主；加入Xcode运行库后签名，实际两方法/86内部检查通过，原3个Mach-O
  section/UUID保持。不是UI自动化或普通Files验收。XCTest Swift overlay还需`Developer/usr/lib`的`-I`，
  单加`Developer/Library/Frameworks`不足。`-test-iterations 1`会在CLI层拒绝且可留下空xcresult目录，
  默认一次运行应省略该选项、每次使用新结果路径，不删除旧失败/开启自动重试。
  optional quality score写JSON前显式解包，避免`Double? as Any`；编译warning在实际推理前处理。
  cbc hy4free本候选678.693秒自然完成，0工具，父修正后验收；不是原样候选成功或发布批准。
  原日志仍有OpenCV/system Objective-C重名及PNG iCCP警告，不能把两测试通过写成无警告/所有影响已排除。
  证据`artifacts/verification/apple-current-runtime-20261107/`及对应cbc助手目录；候选已卸载、原44App保持。

- **关闭开发者签名不等于模拟器无任何签名标记**：当前Apple构建指定`CODE_SIGNING_ALLOWED=NO`，
  设备App确实unsigned，模拟器主程序却带`adhoc,linker-signed`、无TeamIdentifier/资源封印。
  父检查统一要求codesign退出1，实际在模拟器误拒；原检查和两平台输出保留后，按平台核验无profile/
  `_CodeSignature`及精确linker-only状态，读取同一App通过，无重建/开发者签名/安装。
  不要改包移除合法linker标记来迎合检查；证据`artifacts/verification/apple-current-candidate-20261107/`。

- **ADB255与持久终态分别核验**：LiteRT-LM资源测量主机命令255/空输出，不能据此宣布失败并重跑。
  同一runId后续自行写出6行completed/terminal，760666原始字节与设备SHA一致；没有提交第二次推理。
  后续not-found使首次restore在变更前停止，恢复连接后仅继续原test恢复，推理不重做。
  固定prompt不能只核对摘要形状；cbc按给定合同返回shape校验，父集成时新增wrong-valid-hex
  用例后固定精确值。父新增P11适配器的schema bool接受也经负向RED修正。44聚焦/完整tools440通过。
  证据`artifacts/verification/android-litertlm-resources-20261107/`。小样本/采样RSS仍不是质量或预算通过。

- **JSON整数不能仅用相等判断**：cbc的ASR资源汇总候选把`True==1`、`False==0`和整数等值float
  接受为schema/count/index等合法整数；11项负向子用例复现后，用`type(value) is int`逐字段修正。
  不能靠`isinstance(value,int)`排除bool。10项reducer/40项聚焦及完整tools426通过；
  原候选、RED/GREEN见`artifacts/verification/android-asr-resources-20261107/`和
  `.omx/artifacts/cbc-asr-resource-summary-20261107/`。该运行明确绑定普通afed APK，不能冒作新SDK清理代码。
- **delete返回false不是释放成功**：SR输出租约忽略两次`File.delete()`，真实非空条目删除失败却向bridge报告成功，导致重试句柄被移除。原租约两个JVM探针RED后，共用精确路径`Files.deleteIfExists`并保留双错误/处理错误；不递归删其他条目。cbc候选只把第二错误放进消息且若干测试未清理目录，均由主代理修正。SR10+启用SR的Demo56实际通过、video18缓存通过；证据`artifacts/verification/sr-output-cleanup-20261107/`。正式测试不依赖临时反射探针的编译器类名。
- **工程材料不等于App材料**：F09 consumer工程含24份notice，真实成功构建的App却全部缺失。
  修复需实际执行构建复制动作并检查App字节，覆盖同名LICENSE/带引号路径；旧审查不覆盖新修改。
  现已通过双目标unsigned App的24摘要及414工具测试，证据`artifacts/verification/f09-app-notices-20261106/`。
- **Python包入口**：依赖`from tools...`的生成器用`python -m tools.generate_mlxvlm_binary_consumer`，
  直接执行文件可能在构建前报`ModuleNotFoundError`。记录启动器失败，不能当作SDK构建失败。
- **现象**：退出码 0，但没有正文、JSON 不完整，或只有模型目录/连通测试结果。
- **处理**：同时检查进程退出码、完整响应、结构化错误和任务验收结果。保留原始 stdout/stderr，解析失败就报告未取得可用结果；不能补齐截断 JSON 后当作模型成功输出。
- **等待**：用户明确要求助手任务不按耗时或静默自动终止。短轮询只用于观察同一个进程，不是任务超时；不要因此重复发起或切换付费模型。
- **额度**：429、认证失败或明确额度耗尽时停止该线路，保留原错，按用户选择换供应商；其他独立线路继续。禁止循环换模型规避供应商共享额度。
- **证据边界**：候选生成、主代理集成、单元测试、构建、真机验收、独立发布批准分开记录。任何一项都不能自动替代下一项。
- **零finding不推翻复现**：F02原7文件agy审查未报告已被真实Gradle RED证明的旧publisher输入缺陷；第四补丁在packet外。保留审查结果及父处置，不能把无新finding写成新补丁/整个发布无缺陷。证据 `.omx/artifacts/agy-f02-notice-review-20261106/parent-assessment.json`。

## 2. cbc：结果格式与长任务

- **修正文档同样要列全精确owned路径**：F08 hy4free修正提示只写“三份现有Markdown”但漏完整文件清单，原始96事件包含16条缺文件/误读目录错误，随后实际交付；父提示不充分，不能归为额度或耗时失败。下一次派发把每个候选绝对路径列进提示，只改已列文件、不用Read探目录；原错保留 `.omx/artifacts/cbc-f08-system-prompt-docs-corrections-20261107/parent-assessment.json`。这是主代理编排记录，非助手自测。


- **源码诊断不能把“输入影响logits”推成编码正确或故障在logits之后**：hy4free F08分析347.304秒/58事件/0工具错误确有文件，但distinct tensors/logits不排除所有对齐/编码缺陷。其“新token等价性诊断”已被项目既有真实tokenizer uniform parity覆盖（历史2/2无skip/30全过），提案还混用两场景输入。父保留源差异和无已证bug结论，拒绝过度推论及重复执行；以后提供既有测试覆盖，缺助手上下文不算项目缺陷。证据`cbc-f08-temporal-rootcause-20261107/parent-assessment.json`。


- **Kotlin终态回写分支的val也必须真实编译**：功能探针修正persist异常处理后，局部`val finalStatus`在try/catch两处赋值，Android编译报`val cannot be reassigned`。助手只改为`var`，397任务test-only构建通过，前两份失败日志及旧候选保留。不用外层CLI成功推断编译成功。证据`cbc-identity-functional-{reviewfix,compileval}-20261107`和`android-identity-functional-final-20261107/build-identity.log`。


- **采集说明也要逐项对照真实校验器**：hy3初稿编造manifest字段/要求train并混淆PAD指标；
  第一轮修正仍把sourceURL私有host误写禁止、把nonresponse当未定义。真实源码只检查无凭据HTTPS/
  host/空白/端口；OnResponse以有效响应为分母，nonresponse数量/总试验率单列，只有空分母为null。
  两轮错误原件与父拒绝保持，最终由助手订正后原样集成；不要从测试名称推断规则，也不改validator
  来迎合文档。身份与PAD分别准备，选内部采集渠道不等于已取得签署/数据或已授权PAD对照制作。
  证据`.omx/artifacts/cbc-f10-consent-collection-final-20261107/parent-assessment.json`。

- **上游15分钟终止不能归因为父超时**：持续重开Kotlin任务931.375秒自然结束，30完整JSONL，
  process0但result.error_during_execution/empty_model_response。供应商原错为finish_reason=error，
  900332ms仅reasoning、无text/tool call；无实际文件。父未设置任务超时，也没有观察到quota错误。
  保留原件并停该线路；用户明确选agy免费接手，不能静默改付费或重发旧请求。
  证据`cbc-identity-reopen-probe-20261107/parent-assessment.json`。


- **green测试也可能漏掉协议变量和采样阶段**：持续重开汇总候选12测试通过，却用session起点
  校验每周期30秒，后段仅1秒的周期仍被接受；RSS只聚合measurement，遗漏warmup高峰且输出
  min/max对象，违背共享协议的全样本单个max整数。父独立变异真实复现后拒绝集成，交回助手
  写定向RED/GREEN，不改协议迎合实现。证据`.omx/artifacts/cbc-identity-reopen-reducer-20261107/parent-red.json`。
  后续hy3定向修正161.615秒终态，父对原候选4fail/修正15GREEN及221独立变异、完整578tools通过；
  文件原样集成。助手命令自行加cd被精确allowlist拒绝，未绕过权限；实际检查由父执行，
  不能把父结果写成助手自行运行。

- **文档候选也必须对真实API验收**：hy3文档420.615秒完整交付，不代表内容通过。
  它将dict/严格bool/返回dict的empirical_curve写成tuple/string标签/对象方法，并把inclusive
  cutoff误写为并列拒绝；PAD源码路径/既有覆盖也有误。原件、no-findings声明及父拒绝均保存，
  三文档未集成，不再派广泛文档重试。证据`cbc-f10-docs-final-20261107/parent-assessment.json`。

- **直接文件交付首例已验收，不能连带相信交付说明**：hy4free467.413秒自然终态，56完整JSONL
  事件/最终success；模块和测试逐字节集成，无父源码修补。18定向、3110独立穷举、完整563tools通过，
  九保护保持。原DELIVERY称“无工具/权限失败”却漏掉一条被拒Bash盘点和缺文件Read；保留原件，
  原始tool trace与实际测试RED/GREEN分别另存父处置，不能照抄助手自述。
  这证明本次直接文件交付有效，不能推出旧批量JSON的内部截断原因或所有未来任务均不会截断。
  新禁Bash文档任务已把真实pitfalls副本放在已声明路径，避免助手再用未授权盘点查缺失文件。
  证据`cbc-f10-file-delivery-trace-fix-20261107/parent-tool-assessment.json`与
  `artifacts/verification/f10-calibration-curves-20261107/`。

- **直接文件交付需区分候选写权限与CLI运行目录**：2026-11-07按用户新指令启用Read/Write/Edit与
  单条unittest命令。macOS嵌套sandbox-exec先报sandbox_apply EPERM，宿主相同profile的正/负写入检查通过。
  首次真实调用随后在`~/.codebuddy/traces/<本次PID>`mkdir时报EPERM，exit0/error事件且无候选，
  属启动权限错误，不是额度耗尽。保留失败后，只为新进程的精确PID跟踪目录增加写权限，
  任务候选仍隔离，项目/凭据/全局配置不可写；不放行整个用户目录或整个traces树。
  修正调用后续实际文件/测试验收见上条；不能把沙盒预检或启动成功当F10完成。
  证据`cbc-f10-file-delivery-20261107/`与`cbc-f10-file-delivery-trace-fix-20261107/`（`.omx/artifacts/`）。

- **stdout落盘不等于候选文件交付**：F10启动器原本就将stdout直接重定向到`raw.json`，
  无任务超时；344064B原文UTF-8有效但JSON不完整。2026-11-07重新解析仅取得两个完整事件，
  随后的事件在字符串中中断；未取得可验收候选。相同`rtk proxy`链路的2097152B合成输出逐字节通过。
  只能确认本次响应落盘不完整，尚不能认定模型token上限、网络或CLI刷新退出缺陷；二进制静态线索不作运行证明。
  后续新的复杂任务优先让具备文件工具的助手在经验证的隔离任务目录写候选源码/文档，最终只回路径、
  字节数、SHA-256、检查和阻塞；主代理读取实际文件验收。先验证真实沙盒/路径限制，提示词、cwd、
  allowlist或`--add-dir`都不能单独证明隔离。不扩大旧禁工具任务权限，不重发已失败同单。
  若无法建立有界写权限，继续禁工具并把新任务拆成小交付；stream-json仅为待验证选项，不宣称已修复。
  本条最初诊断时方案未验；后续首例已验见上条，旧失败与诊断原件保持。证据`.omx/artifacts/cbc-output-diagnosis-20261107/diagnosis.json`。

- 2026-11-07经验曲线hy4free也自然退出1647.306秒/exit0、stderr空，但raw JSON恰344064B，
  在字符串中截断，无完整assistant/result/候选；保留原始字节与解析错，主代理接手，不补JSON/重发。
  原生进程句柄52594已终态，不再轮询。没有父超时或quota证据；原件`cbc-f10-empirical-curves-20261107/`。

- **自然退出也可能输出截断**：移除轮数限制后的Identity候选自然终止1715.484秒，exit0，
  stdout恰262144B；JSON在assistant reasoning字符串内截断，只有一个完整user事件，没有完整
  assistant/result/Kotlin候选。不是监督者超时，也无429证据；保留原文，不修补JSON、不再发第三次。
  主代理接手原Kotlin实现。证据`.omx/artifacts/cbc-identity-resource-probe-turns-fix-20261107/parent-assessment.json`。

- **max-turns不是耗时上限但仍会截断复杂任务**：Identity资源Kotlin候选自然运行2277.90秒后
  exit0/stdout0B，stderr明确`Max turns (1) exceeded`。无候选，不是父进程超时或额度结论。
  原任务已终态，保留原文后仅修调用配置：新独立`hy4free`任务移除`--max-turns 1`，
  工具/MCP继续禁用，不扩大权限或改全局设置；新结果须独立验收，不能提前计成功。
  根AGENTS通用cbc候选配方已去掉该限制；证据`cbc-identity-resource-probe-20261107/`
  与`cbc-identity-resource-probe-turns-fix-20261107/`（均在`.omx/artifacts/`）。

- **数据集候选必须核验当前官方入口**：cbc本次F10研究把已于2023-03-14停止分发的IJB写成可填表下载，
  把Replay-Attack的1300段写为1200、SiW-M归属／含义写错；父代理用官方页面更正。
  CelebA-Spoof／Replay的非商业限制不因“内部使用”自动消失，code license不等于数据授权。
  本地校准用途与数据再分发分别判断；伪名hash不是匿名证明，相关pair也不能当独立低FAR试次。
  89.495秒完整JSON／0工具只是研究候选成功，不是授权或校准通过；报告与处置
  `docs/validation/f10-calibration-exploration-20261107.md`及对应`.omx/artifacts/cbc-f10-calibration-exploration-20261107/`。

- 当前Apple四文件检查清单误要求静态SwiftPM/OpenCV产物出现在`App/Frameworks`，又建议启动unsigned
  真机App、错用提交号字段及简写切片范围。主代理按实际Mach-O/Swift输入/Info.plist/平台资源核验，
  多项更正、未采纳产品finding；检查清单不计审查批准。原文/处置
  `.omx/artifacts/cbc-apple-combined-checklist-20261107/`，实际两构建及82bridge通过。

- 2026-11-07设备协议审查把输入里正确的`('original-test', d.TEST, d.SHA_OLD_TEST)`错引成主包摘要，
  又把未提供的lifecycle_run模块推断为不存在。父代理核对原始prompt/实际调用者并否决四条意见，
  不为这些意见改正确实现或放宽split APK准入；之后真实恢复通过。审查末尾未执行的“已保持设备”文字
  同样不是证据。见`.omx/artifacts/cbc-current-device-protocol-review-20261107/parent-assessment.json`。
- 2026-11-07原始ZIP字节复用候选在415.712秒自然完成，不是超时失败。父代理四项负向检查复现：
  只按初始长度重算SHA会漏追加字节；固定FD未核对路径替换；失败清理无身份核对会删掉替换后的文件；
  第二输入在try外打开失败会泄漏第一FD。修正固定FD读ZIP、前后身份/size/metadata/SHA与finally关闭后，
  10离线检查通过，真实签名APK本地精确还原；这不是设备重建或发布批准。
  证据`.omx/artifacts/cbc-apk-byte-plan-20261107/`及`artifacts/verification/android-current-candidate-20261107/`。
- 2026-11-07视频审查建议把帧数过滤`<`改为`<=`；主代理实测原实现能保留`(3,null)→(3,8)`的总帧数补充，而建议会丢第二次更新。累计计数相同不等于完整事件重复，不能仅据测试名称推断严格去重协议。建议RED/原源码恢复/完整video18+Demo56实际通过及SR4缓存通过见`artifacts/verification/video-client-review-20261107/`。这是对建议的否决，不是产品缺陷修复或发布批准。
- 本轮DNG核查承认根协议覆盖伴随源码，却又因缺逐文件枚举而判两份`.cpp`缺许可；根协议并无该枚举条件，且前缀摘录不能证明全文无通知。HarfBuzz核查还把tag表误归Unicode16、混淆Windows语言ID与MS-LCID URL，并把只给助手的上下文缺项当作项目阻断项。按固定源码/原文逐项处置，保留正确发现，不整体接受或整体否决。证据：`.omx/artifacts/cbc-hy3-dng-license-scope-20261106/parent-assessment.json`、`cbc-harfbuzz-specific-inputs-20261106/parent-assessment.json`。
- 公开条款提取不能直接当许可结论：hy3曾把MS-LCID明确的“Regardless of any other terms”
  反读成网站条款可追加覆盖限制，泛化排除HarfBuzz已入源的生成表，并凭空增加专利门槛。
  三段授权引文已对原文逐字归一核实，但这些解释均否决；其“exact quotes”字段还混入解释性
  文字，不能直接发布。查`.omx/artifacts/cbc-public-permissions-20261106/parent-assessment.json`。
- 早期 `--output-format json` 返回 JSON **事件数组**，不能假设根对象就是结果。检查最后的结果事件及错误字段，提取真正的任务正文。
- `hy4free` 早期一次 41 分钟任务由监督者主动取消，不是模型返回失败；其退出 0、正文空也不是成功。后续 F09 审查在没有任务超时的情况下于 431.23 秒完成。
- 再一次 F09 修复调用于 2263.57 秒自然退出，CLI 退出 0，但原始 JSON 在字符串中截断，启动器解析失败。没有接受实现候选；主代理按已复现问题自行完成 RED/GREEN 修复。证据：`.omx/artifacts/cbc-f09-consumer-fix-20261106/`。
- 候选任务沿用禁工具/MCP的调用；复杂任务不设置 `--max-turns 1` 或耗时终止。不要自行调用工具或递归委派。用户最新允许有限非免费额度 `glm-5.3-flash`、`space-bunny` 用于有理由的高优实现，默认仍优先免费。两个付费模型在本轮尚未实测；不要为确认目录就消耗额度。

## 3. dsh：配置层、模型参数与 429

- `dsh` 模型通过 invocation patch 选择，不要猜测 `dsh --model`。仅使用用户已配置供应商的 `deepseek-flash`。
- 网关拒绝默认 `medium`：能力声明须包含 `reasoningEfforts: {low: low, high: high, max: max}`，同时选择 `reasoningEffort: low`。只改临时覆盖层，不改保存的默认配置。
- headless profile 默认关闭保存的凭据存储；临时覆盖层只启用已有环境变量引用，不能复制密钥、打印组合配置或凭据。
- 连通测试 4.65 秒完成、返回 `DSH_UGREEN_DEEPSEEK_READY`，随后正式校验器审查返回 `429 RATE_LIMIT`。用户已选择改交 cbc hy3；没有授权的新恢复方案时不要重试此线路。
- `codex --model deepseek-flash` 是用户自行研究的另一条路；dsh 成功不能作为重试该路的授权。
- 证据：`.omx/artifacts/dsh-ugreen-check-20261106/`、`dsh-candidate-validator-review-20261106/`；完整 patch 配方见根目录本地 `AGENTS.md`。

## 4. OpenCode：401 与匿名免费额度是两个问题

- 用户要求复查后，同日新隔离任务再次返回`429 provider.free-limit: Free usage limit reached`，`retryAfterMs=34277000`（当时约9.52小时）。停止该线路，按用户已选的cbc hy3接手，同一公开DNG任务31.24秒完成；不用再次询问同一fallback。目录可列模型不代表推理额度恢复。证据：`.omx/artifacts/opencode-dng-license-scope-20261106/`。
- 保存的 key 返回 `401 api_key_expired`。单独使用临时 XDG 目录、仅在该子进程移除 `OPENCODE_API_KEY` 测试匿名免费层，未修改用户保存的认证。
- 匿名层返回 `429 provider.free-limit` / `Free usage limit reached`，当时建议约 16 小时后重试。不能因此轮换免费模型或创建账号；用户选择了 agy 接手。
- 安装版本的模型目录命令是 `opencode models --standalone`；不接受猜测的 positional provider 过滤参数。帮助命令也可能写日志，隔离 XDG 路径仍有用。
- 使用输出事件判断错误，不能只看 CLI 退出码；模型出现在目录中不证明现在有免费额度。

## 5. agy：打印超时不是后台任务终态

- **通用JSON API不能直接套用Android**：2026-11-07通用功能探针调用`JSONObject.keySet()`，真实Android test-only编译在两处报`Unresolved reference keySet`；尚未安装或传入员工图。交回助手使用当前Android支持的`keys()`迭代器并保留严格schema；源码交付/外层SUCCESS不等于编译通过。原失败保留`artifacts/verification/android-identity-functional-20261107/build-identity.log`。
- **文件工具的元数据不能猜作实际字节收据**：同次DELIVERY声称Kotlin29220B而父实际读得26129B；原件不改，验收仅用实际文件大小/SHA和工具事件。`read_file`分段已指定StartLine时再叠绝对ByteOffset可因范围内长度计算而失败；有精确文件清单时不探测未供应README。候选缺席的Read、权限拒绝和后续真实交付分别记录，不作为额度错误或无工具失败。

- **runOnMainSync外catch接不住主线程runnable错误**：SDK的SyncRunnable先执行`mTarget.run()`，再设置complete/notify，无catch/finally；清理flag时在lambda直接check/throw可能导致main崩溃或调用者等待，不能声称外层catch必将写出失败报告。应在lambda内捕获到AtomicReference，正常返回后在instrumentation线程处理，仍保留runOnMainSync自身错误catch。本机SDK37源码行490–496/2648–2674与摘要保存在`.omx/artifacts/agy-identity-reopen-mainthread-fix-20261107/mainthread-evidence.json`；发现时未安装；随后由agy修正并原样集成，最终20周期/600.9秒真机及恢复通过，见`docs/validation/android-identity-reopen-20261107.md`。

- **headless读取范围与进程退出必须分开核验**：持续重开初稿真实写出Kotlin后，最小修正调用因context位于candidate工作目录外，read_file缺规则而auto-denied；process0/外层SUCCESS/空response且文件摘要未变，不是交付。保留终态后，新调用只用`--settings`会话内`permissions.allow`中的精确`read_file(绝对路径)`规则补齐已供应上下文读取；OS写沙盒仍只允许独立taskRoot。不要使用危险跳过权限、放行全局brain/auth/config或将此权限失败算quota。证据 `.omx/artifacts/agy-identity-reopen-probe-fix-20261107/parent-assessment.json`、`agy-identity-reopen-probe-fix-readscope-20261107/`；后者随后136.692秒实际交付；进一步主线程修正74.402秒后，最终构建/20周期真机/恢复已通过。外层SUCCESS仍须逐次核对实际文件与检查。

- 一次新任务在CLI内部60秒认证等待后返回 `authentication failed or timed out`，`conversation_id`空、`num_turns=0`；这不是模型容量或监督者任务超时。用户说明网络恢复并明确要求重试后，新进程静默认证成功，348.12秒取得完整SUCCESS/1turn和7文件审查结果。尚无完整输出时只报告“已提交/仍等待”，不能提前报可用审查。保留两次记录，不改凭据，不把失败的OAuth URL复制进提示词/文档。证据：`.omx/artifacts/agy-f09-packaging-supplement-20261106/`、`agy-f09-packaging-supplement-retry1-20261106/`。
- 使用 `--print-timeout 0`。首次 180 秒打印超时返回 `DEADLINE_EXCEEDED`，当时后台 turn 尚在运行，不能立即重发。后续保留日志确认 CLI store manager / language server 已退出，仍不构成该审查通过。
- `--conversation ID --print ...` 会提交新一轮，不能当作只读状态查询。
- `--disable-slash-commands` 与 `--mode plan` 合用会使模式无效；不要组合。`--mode plan --sandbox` 也不是 cbc 的禁工具机制，候选提示词仍要明确只用给定材料。
- JSON 输出顶层的 `status == SUCCESS`、`response` 字符串和其中候选 JSON 是不同层；先查状态，再解析正文。保留工具事件，不能仅凭提示词假定没有工具调用。
- 独立 README、chunker、run/collect 候选分别已返回；候选仍须主代理审查和验证。证据：`.omx/artifacts/agy-candidate-readme-20261106/`、`agy-apk-chunk-plan-20261106/`、`agy-candidate-run-collect-20261106/`。

## 6. Copilot：空工具列表与 JSONL

- **失败测试也要证明触发了目标行为**：Nexus GET候选的变更测试向只读验证fd `pwrite`，
  EBADF被泛化ValueError捕获，文件并未改变却显示测试通过。父新增实际字节变化断言后RED，
  改用独立写句柄修改同一inode，检查原读取字节/实际修改/拒绝结果后GREEN。
  不是核对器已证缺陷，不能把错误测试的绿灯当作fd签名覆盖。新增20+既有9聚焦29过，
  完整tools530过；证据 `artifacts/verification/f02-nexus-get-receipts-20261107/`。
- **离线收据不证明网络来源**：GET/status/auth字段与实际留存字节都正确，只能证明一致性。
  Nexus核对器固定 `anonymousExecutionVerified=false`、`remoteConsumerValidated=false`；
  实际传输匿名性、Gradle来源/variant/编译、许可适用性仍分别验收，不伪造真实GET收据。

- **路径层级/传输语义必须实际测**：本轮Nexus核对器候选把`org/jetbrains/skiko/module/version/file`
  的module/version索引提前一层，自己的成功测试真实失败；父修正后7项过。再用带`?`/`#`/
  `%2f`/空白的成员文件名和缺少nofollow支持做出6项真实RED，再以Maven文件名字符集和
  fail-closed修正，9项过，实际`.6`115成员核对通过。保留原候选，不能说模型原样完成；
  证据`.omx/artifacts/copilot-f02-archive-validator-20261107/`与
  `artifacts/verification/f02-nexus-plan-archive-20261107/`。旧冻结计划/归档保持。
- **待办不是完成声明**：首次Nexus审查把`remainingBeforeUpload`列表内“Exact hosted destination
  confirmed”误当作当前已确认。它是前置条件列表；父处置否决此finding，不改旧证据。
  同次调用内部WebSocket`ECONNRESET`后CLI自然重试并完成，不是父代理重复请求或429。

- 本轮安装 `copilot 1.0.106`，`--model auto` 实际选择 `gpt-6-luna`。这是该次计划/账户可用模型的结果，不是永久免费目录。
- `--available-tools=` 被 CLI 接受，但工具 schema 仍出现在元数据；不能声称该参数已彻底移除所有工具。候选任务加显式 shell/read/write/url/memory deny，关闭内置及保存的自定义 MCP，并审查实际工具请求/执行事件。本轮已审查的调用未发生工具调用。
- `--output-format json` 返回 **JSONL**。只提取非空 `assistant.message` 正文，检查 `session.error` 与最终 `result.exitCode`；不要把空消息、reasoning 或 opaque session payload 打到报告里。
- `--no-ask-user` 只禁用提问工具，不授予更多权限；`--sync-session=off` 只控制本次账户历史同步，不是禁止所有本地持久化。
- 使用个人免费额度和已有登录。`premiumRequests: 1` 是请求计量字段，不能据此声称付费或无限免费。不得自动开通计费、改账户或切付费模型。
- 证据：`.omx/artifacts/copilot-candidate-verifier-20261106/`、`copilot-device-protocol-review-20261106/`、`copilot-f09-fix-review-20261106/`。

## 7. Shell / Ruby：继承环境与执行路径可能不匹配

- 在父 Git 仓库内的源码副本执行 `git apply`，可能退出0却输出 `Skipped patch`。不能只看退出码：使用精确路径的 `patch --batch --forward --fuzz=0 -p1`，并核对目标源码修复后摘要/实际任务。F02首次真实buildSrc验证因此被否决，原记录保留；修正后真正执行validator并验证72材料。证据 `artifacts/verification/f02-notice-validation-20261106/actual-parent-rejection.json`、`actual-v2-result.json`。
- 所有 shell 命令加 `rtk`；原始/二进制证据用 `rtk proxy`，禁止把过滤后的输出当原始字节计算摘要。任务正文通过 Python 参数列表或文件传递，避免 shell 展开反引号、`$()` 或凭据。
- 完整 tools 测试一次出现 19 failures / 8 errors：执行的 Ruby 2.6 加载了继承的 Ruby 3.4 gem，报 `dynamic constant assignment` / syntax error。它是工具链环境冲突，不能据此修改生成器逻辑或删除失败测试。
- 修复仅限本次测试子进程：在 PATH 前放已安装的 `/Users/daubert/.rvm/rubies/ruby-3.4.11/bin`，先用 `ruby -rjson` 核对 Ruby/JSON 能加载，再跑相同完整测试。未改全局环境、gem 或锁文件；随后 411 项通过。
- 证据：`artifacts/verification/f09-consumer-fix-20261106/tools-tests.log`、`tools-tests-ruby-aligned-result.json`。首次失败保留，不覆盖。

## 8. ADB：EOF、offline、unauthorized 与暂存

- **同次功能请求255不等于失败或可重发**：12图功能run主机exit255/空输出，但唯一runId的私有持久报告completed/terminal、12次实际未校准拒绝及全部清理状态通过。只收取同次原始报告、独立核对hash/计数/错误码/负向/恢复后验收；不修host退出码或再推理。设备输入及本地派生/映射精确删除，226原图全部重hash保持；该结果仅功能，不提升校准/PAD/准确率。额外受限只读观察smartsocket EPERM属于本机沙盒，宿主只读复核通过，不能再次要求用户重连。证据`android-identity-functional-final-20261107/summary.json`。


- **提前修协议，保留非终态**：当前TTS新协议在设备暂存前已将旧最终`ln`改为`set -C; cat > 新路径`，
  partial和final全SHA都验，设备重建exit0；三项实际shell检查覆盖已有目标不可覆盖/损坏partial拒绝/成功发布。
  同次真实TTS主机255后第一份报告只到normal-start，保留后只再次回读，最终十阶段完整通过；
  不因非终态或USB中断重发推理。恢复前独立回读同SHA，原App及私有状态恢复后精确清理。
  34离线协议检查、真实运行与恢复证据`artifacts/verification/android-current-tts-20261107/`。
- **帮助文本仍须实际验证**：TECNO的toybox dd帮助列出`conv=excl`但实际拒绝；改用`set -C; cat > 新路径`。
  `iflag=skip_bytes,count_bytes`已用16固定字符stdin/stdout实测。135段重建完整SHA通过后，最终`ln`仍被设备
  Permission denied拒绝；保留exit1/已完成partial，复核整包SHA后禁止覆盖复制采用，不重跑构建或改签名。
  首次只读not-found与短unauthorized会自行恢复，不立即要求用户重连；只读观察可等待同一设备后复读一次，
  安装/删除/重建/推理不自动重放。27协议检查及真正新候选ASR/SR/恢复证据
  `artifacts/verification/android-current-device-20261107/`；旧b90e协议的“主包不可替换”仅约束它自身冻结实验。
- `adb push` 先打印 `1 file pushed` 再报 EOF，不证明设备保留了完整文件；重连后曾确认暂存目标不存在。每次以远端文件摘要为准。
- 连接会在 `device` / `offline` / `unauthorized` 间变化。先刷新状态；只有需要用户确认 USB 调试时再请用户操作。错误建议里的 `adb kill-server` 不是执行全局重启的授权，也不要反复要求杀 App。
- 本轮改用 8 MiB、`adb push -Z` 分块；只跳过已核对 SHA 的块，再核对重组整包。部分块存在但摘要不符时停止并诊断该精确文件，不能盲目覆盖或据传输输出写成功收据。
- 安装前独立核验**全部五份**候选/回滚/原 test/ASR test/SR test APK 的本地固定摘要、暂存收据及远端摘要；不能先装候选再发现回滚或 test 不完整。主包保持原始 `b90e...64d8d`，不能换成新重建的主包。
- 本轮安装前五份 APK 的整包摘要已核对，最终验收和恢复后五份APK及113块均已清理；暂存收据是历史证据，不证明文件当前仍在手机上。证据：`artifacts/verification/android-persistent-candidate-20261106/stage-*.json`、`stage-resume-06.json`、`restore-complete.json`。

## 9. Android 报告、普通 App 与恢复

- am/ADB 退出 0 不是推理通过；ASR/SR 原生 finish code 也不相同。回读同一 runId 的私有终态、核对精确字节摘要，再校验原生断言。断连后只观察原运行，不自动重启。
- `recorder-pass` / `recorder-fail` 是记录器自检，必须被 inference 校验器拒绝；completed 自检不能被当成真实推理通过。
- SR 还需独立 native detail；它的随机 runId 必须区别于之前备份的 detail，避免旧报告复用。保存二进制内容不能 `.strip()` 或重新序列化后当原始摘要。
- 不运行原 Identity instrumentation：它使用普通 App 已有的 journal namespace，可能改动原身份数据。
- 普通 App/test/runner、25 项模型/journal 摘要、权限/TTS 默认值、四份 SR 文件必须保留并恢复；不清数据、不卸载。先备好 run/collect/restore 协议，再安装；恢复必须允许失败或部分安装，不能要求推理成功才能恢复。
- 恢复日志/保留副本不能直接写最终路径：进程中断可能留下半份文件，下一次便无法续接。当前恢复脚本先完整写临时文件，再用同目录 exclusive hard-link 发布；已存在内容只接受完全相同字节。SR scratch 若因 USB 留下半份内容，先按恢复 intent 核对归属、保留精确副本和摘要，再精确清理并重写；不能把半份文件移到原路径。
- `find` 顺序不稳定，保留输出不能按遍历下标命名；改用远端路径摘要。预检只证明没有 SR 输出文件，未证明其目录不存在，因此清理保留 SR 目录，不能把原有空目录当作本次创建。失败推理也可能没有 outputs 目录，收集前须严格探测存在性。
- 以上由 Copilot 代码审查与主代理 RED/GREEN 验证确认；16 项恢复检查、1 项失败报告收集检查和8项原生命周期边界检查分开计数，不并入完整tools suite。证据：`artifacts/verification/android-persistent-candidate-20261106/protocol-offline-final.json`、`.omx/artifacts/copilot-candidate-restore-review-20261106/`。
- 单测夹具与校验器可能共享错误假设：首次两者都写成128×96，真实已批准输入 `8277f1da...65d8c` 却是64×48/2帧H.264/AAC，native按4倍输出256×192。先用保留输入/输出做 ffprobe、实际解码和PTS核对，再修正严格尺寸约定并加拒绝另一夹具尺寸的回归；不能放宽成任意尺寸或重跑推理制造新pass。原始拒绝收据和同次报告重验收据分别保留为 `run-sr-fixed-inference-20261106-collected.json` / `run-sr-fixed-inference-20261106-revalidated.json`。
- 最低系统版本物理设备缺失已明确延期；F10 无授权校准数据，保持 `THRESHOLD_UNVERIFIED`。不能用模拟器、自拍或臆造阈值填平门槛。

## 10. F09 consumer：manifest 与输出目录竞态

- 许可刷新与编译来源分开：旧20模块二进制仍绑定f3899bb；后续内部许可来自3b5150d。先证明原149 SDK输入与新150文件快照仅多LICENSE，再在新副本刷新notices，保留原build receipt和非notice字节，单列许可来源。不能把有效旧编译收据标作失效或改写成新构建。完整25,288,674 B运输包/24材料保留证据 `artifacts/verification/f09-git-delivery-20261106/`。
- 审查建议需核对schema上下文：v1保留旧固定Metal摘要，v2明确采用按平台构建输出/收据；不能给v2强加v1摘要而拒绝现有合法产物。共享validator已对实际Metal文件执行摘要校验，收据一致性不等于发布者认证；static framework内ar归档也不要求可执行位（实际0644）。cbc补充审查的3条意见按此核实后未采纳，10项static测试及字节篡改/收据不符拒绝检查通过。证据：`.omx/artifacts/cbc-f09-packaging-supplement-20261106/parent-checks.json`、`parent-assessment.json`；契约见 `docs/validation/apple-mlxvlm-distribution.md`。
- 多次读取可变 manifest，会导致 payload、验证、拷贝、Swift 常量、收据引用不同字节。只读一次，保留原始 bytes 和 hash；向只接受 Path 的校验器提供冻结临时文件，使用显式 artifact root。
- POSIX 普通 rename 可覆盖已存在的空目录，先 `exists()` 再 rename 也有竞态。第一版 mkdir 占位仍可能在最终 rename 前被替换；不能只在失败清理时核对 inode。
- 最终采用独占重命名：macOS `renamex_np(RENAME_EXCL=0x00000004)`；Linux `renameat2(RENAME_NOREPLACE=1)`。API 不可用/文件系统不支持时失败退出，绝不回退为普通覆盖 rename。macOS实际回归和本机Linux/aarch64/glibc2.41的13项检查均已通过；两项新增仓库回归也在Linux通过，完整consumer模块在macOS18/18。Linux使用已有Colima镜像、禁网/只读挂载/非root；不是仅模拟sys.platform。证据：`.omx/artifacts/ollama-linux-publish-checks-20261106/`。
- 相对 framework 内部链接、symlink 父目录并非自动错误；现有 artifact validator 已检查根目录逃逸和摘要。不要照搬助手审查中已否定的泛化问题，扩大修复范围。
- 原问题、第一版及最终 RED/GREEN 都保留在 `artifacts/verification/f09-consumer-race-20261106/` 和 `f09-consumer-fix-20261106/`；前者 `before-fix.json` 不得被修复后重跑覆盖。

## 11. 继续与交接：保护状态、只记录真实变化

- 先读当前交接、Git 状态、活动进程、设备和证据；不能把之前的意图、文件名或中断命令当完成证据。
- 九份受保护路径和 Apple lock 以 `artifacts/verification/f09-native-cache-inputs-20261106/retained-inputs.json` 为摘要基线；不要恢复、暂存或提交其中用户原有改动。
- 基础 roadmap 仍 10/12、P4/P11 open、release not-ready。辅助脚本、通过单测、连通结果或负向质量分数都不能自行关闭这些门槛。
- 新坑按“现象 → 已证实原因 → 有效处理 → 不可越过的边界 → 证据路径”追加。密钥、密码、完整凭据配置、用户媒体和线程堆栈不得写入本文或提示词。

## 12. Ollama：本地连通、完成标志与候选质量

- 2026-11-07 Sherpa两文件本机审查14.429秒返回。异常覆盖发现经原实现两项真实RED确认；但“取消时立即放行下一次同步JNI”违反drain前不得重入的合同，已否决。其“协程异常身份保留”假设同样错误。使用标准库use的修复由主代理实现并实测，不把预修复静态意见当最终独立批准。证据`.omx/artifacts/ollama-sherpa-session-20261107/`。
- F02两文件审查12.58秒返回：索引/材料未校验一条已用真实Gradle复现并修复；另一条建议删除source JAR中的notices，与明确交付契约冲突而未采纳。不能把助手的一组建议全部接受，或把本地技术补充当独立许可批准。证据 `.omx/artifacts/ollama-f02-publication-review-20261106/`。
- `ollama --version` 在受限沙箱提示无法连接，不代表服务没开。经正常获授权 host 路径，只读 localhost 目录成功，确认 `qwen3-coder:30b` 已安装、约18GB；不要因为沙箱警告另起服务或重新下载。
- 固定 `127.0.0.1:11434`，不继承可能指向其他主机的 `OLLAMA_HOST`；本轮通过禁代理的标准库 HTTP 请求访问本机，没有云端调用。
- `/api/generate` 外层 JSON 的 `done: true` 不等于有任务结果：卸载请求也会返回 true。要求非空 `response`，核对 `done_reason`，再解析内层 JSON；`length` 截断不能当完成。`format: json` 只约束格式，不保证逻辑正确。
- `num_ctx` / `num_predict` 是本次上下文与输出边界，不是墙钟超时。使用无自动任务超时的请求；协调本机 GPU，不重复启动静默任务、不强停其他模型。只为本次加载的模型可用 `keep_alive: 0` 释放；原已加载的 runner 不额外停止。
- 第一次真实测试候选约19.99秒返回，但把 `/absolute/path/lifecycle_run.py` 示例硬编码，误把 `'80'` 当80字符输入，并未测到真正的尾随换行。主代理修正路径/导入和用例后，8项离线检查通过；不能据“生成了 unittest”或退出0直接宣称覆盖充分。
- 随后的恢复协议审查14.50秒返回，但5条建议都重复或忽略提示词里已有的约束；主代理逐项否决，没有据此宣称独立批准。复杂设备恢复协议继续由主代理集成验证；该本地模型优先承担小型纯函数/测试候选。证据：`.omx/artifacts/ollama-restore-protocol-20261106/parent-assessment.json`。
- Linux边界候选22.79秒返回，但缺少目标导入，把目录发布写成普通文件移动，多个拒绝场景未预期异常，缺失API的mock也没有作用于实际调用。未原样执行；主代理修正并补齐后，在真实Linux完成13项检查，并把两个关键场景加入仓库、双平台复验。生成候选不等于通过测试；证据 `.omx/artifacts/ollama-linux-publish-checks-20261106/parent-assessment.json`。
- 证据：`.omx/artifacts/ollama-lifecycle-tests-20261106/` 的原始 request/response、候选、主代理修订脚本、`tests.log` 和 `parent-verification.json`。这只证明本地任务生成及离线边界测试，不是设备推理验收。

## 13. LiteRT-LM：检索摘要、版本新增与原生静止合同

- **现象/原因**：Context7说明把空`awaitClose {}`解释成等待native callback drain；固定0.19.1
  实际先关闭Flow，JNI在Java终态回调返回后仍清理引用/attachment。检索摘要的解释不是源码合同。
- **有效处理**：查固定commit的Kotlin与JNI顺序，区分channel完成、回调栈退出和原生队列静止。
  内部`WaitUntilDone`可能超时，析构的`.IgnoreError()`也不是应用可观察的成功结果。
- **版本比较**：hy4free仅收到新版源码，却把`terminal_reached`及若干配置/方法称为新增；
  主代理对旧版摘要和原文核对，发现已存在，否决这类推断。没有旧源码时只报告新版事实。
- **边界/证据**：直接JNI delete不能证明传递析构完全不等待。缺公开barrier不代表同步factory缺席，
  不据此升级依赖或重跑同步推理。见`docs/validation/android-litertlm-later-barrier-20261106.md`、
  `.omx/artifacts/cbc-litertlm-later-barrier-20261106/parent-assessment.json`和公开原文目录。

## 13. qoder：CLI入口、受限网络与逐字核验

- 2026-11-07新通用关闭测试矩阵任务返回exit1、`error_during_execution`、HTTP403并附`pricingUrl`、`error_code:112`，0输出token/无候选。停止该线路，等待用户选择fallback，不重试/改登录/换模型；不能据此断言具体余额或支付状态。此前成功记录仍有效。原提示无项目源码，失败及父处置见`.omx/artifacts/qoder-close-cases-20261107/`。
- 后续通用落盘候选70.24秒成功，但先`O_RDONLY`打开FIFO会在`fstat`拒绝前阻塞；父代理保留负向RED后加`O_NONBLOCK|O_NOFOLLOW`并拒绝非bytes输入，修正版16项离线检查通过。候选外围多了说明文字，只提取唯一完整JSON代码块，未修补截断内容。不把修正后测试归给原始候选，不把通用函数生成当上传。证据：`.omx/artifacts/qoder-verified-download-20261106/`。
- 使用`qodercli`（本轮1.1.84）；`qoder`是另一个App入口。受限shell首次模型目录返回`Failed to fetch model list: Was there a typo in the url or port?`，正常获授权网络目录成功，唯一配置模型`Qwen3.8-Flash`。不要据沙箱失败改认证/网络或换供应商。
- 首次公开头部提取27.05秒成功，父代理对原源码逐字核对623字节/SHA通过。保留调用、原始结果和父处置；costUSD0/credits0只是本次记账，不保证无限免费。JSON仅返回结果，不能据此声称全事件零工具；调用明确禁工具/MCP。
- 已安装help不含`--max-turns`，不要从通用文档添加猜测参数；完整已测配方在根本地`AGENTS.md`。不设任务耗时终止，不改保存配置、不加权限绕过、不自动重试429。证据：`.omx/artifacts/qoder-dng-notice-extraction-20261106/`。
- 原始头部提取帮助发现冻结Skiko`.4`的72材料漏收DNG实际编译头`dng_safe_arithmetic.h`的AOSP声明。必须核对具体编译输入和原始通知，不能只确认组件根LICENSE存在。先保留缺项RED，再用新版本补齐并检查JAR/consumer/App字节；不由材料遗漏直接推断法律违规。

## 14. Kotlin协程测试：异常副本与未执行任务的旧XML

- 2026-11-07关闭/ASR回归首次GREEN在跨协程`assertSame`失败；实际错误类型和内容相同，堆栈含协程恢复边界。官方文档说明恢复可复制异常并将原异常设为cause，suppressed保留在原异常。测试改核对类型/内容、必要时原cause及实际close/destroy效果，不为测试改生产传播逻辑或全局关闭恢复。原RED/GREEN及文档见`artifacts/verification/speech-cleanup-20261107/`。
- Gradle在speech测试失败后未执行ASR任务，但ASR旧XML仍存在。不能把XML存在当本轮执行证据；检查准确task行和状态，区分executed/cached/not-run，跨模块必要时用`--continue`。本轮旧XML已明确排除，后续实际ASR14与speech29、TTS3/Demo56全过；源码/收据绑定，不能把修正测试前的失败称为成功。

## 15. Sherpa离线构建：指定已冻结的本地Maven仓库

- 直接运行Sherpa模块`--offline`会报`No cached version of com.k2fsa.sherpa.onnx:sherpa-onnx:2.0.1 available`，测试尚未启动，不能当产品RED或重新下载。复用`artifacts/verification/android-committed-candidate-20261105/inputs/maven/sherpa`，以`-PsherpaMavenRepo=<绝对路径>`显式传入；先按同目录`inputs.json`的externalInputs校验AAR/POM摘要和长度。
- Demo真实启用此后端还需`-PenableSherpaAsr=true -PsherpaAsrAssetsDir=<已冻结inputs/assets/sherpa-whisper-tiny绝对路径>`，核对全部七份既有模型/通知文件；默认Demo测试不等于启用后端配置验证。本轮14后端+56启用Demo测试实际执行通过，speech29为up-to-date。不修改全局Maven、不联网下载、不把host通过当设备JNI验收。证据`artifacts/verification/sherpa-cleanup-20261107/`。


# 探针踩坑补遗（2026-11-07）

本补遗只记 macOS 系统消息诊断探针自身的坑，**不证明生产 bug**；按「现象 → 已证实原因 → 有效处理 → 证据 → 不可越过的边界」记录，不含密钥/凭据/用户媒体/线程堆栈。固定结果事实不因本补遗改变。

## 1. XcodeGen `commandLineTool` 不是合法 spec 类型

- 现象：`project.yml` 把目标类型写成 `commandLineTool`，XcodeGen 生成阶段即失败，未进入编译。
- 原因：`commandLineTool` 非 XcodeGen 合法 `type`；初始父提示词即给错，实际 gen 在编译前报错。
- 处理：改用 `type: tool`（macOS 15 / Swift 6），经配置轮次修订后最终构建通过。
- 证据：`artifacts/verification/f08-system-prompt-diagnostic-20261107/` 的配置轮次与最终构建记录（12.373s）。
- 边界：属探针构建配置错误，非生产 target/SDK 缺陷；配置修订成功不等于发布批准。

## 2. 外层 `sandbox-exec` 阻断嵌套 SwiftPM `sandbox_apply`

- 现象：首次在构建外再加一层网络 `sandbox-exec`，嵌套 SwiftPM 的 `sandbox_apply` 返回 EPERM，构建被阻断。
- 原因：外层沙盒与嵌套 SwiftPM 沙盒叠加冲突，属沙盒层问题，非源码/依赖错误。
- 处理：保留首次失败；最终构建改用普通获批 host + 固定本地缓存 + offline/pinned 标志，不额外叠加外层沙盒。
- 证据：保留的首次失败与 `artifacts/verification/f08-system-prompt-diagnostic-20261107/parent-validation.json`（`compile.networkDeniedByOSProfile=false`）。
- 边界：不关闭全局工具沙盒；不得宣称最终构建由 OS 强制禁网，禁网结论仅针对运行时推理进程。

## 3. `LMInput.image.frames` 为可选 `[THW]`

- 现象：首次 Swift 编译在 `ModelContainer.perform` 闭包报闭包推断错（105.773s）。
- 原因：原始 `[ObservationRow]` 结果本已符合 `Sendable`，编译失败并非由非 `Sendable` 结果造成；读审另确认可选 `[THW]?` 被当作单个 `THW` 映射、neutral 失败时丢掉 original 行。已观察编译报闭包推断错；读审确认可选数组错误，修正后实际编译通过，但未单独隔离全部推断报错的因果。
- 处理：部分结果修复另计，`FixtureOutcome` 只用于承载部分行与失败元数据（neutral 失败时保留已完成的 original 行，部分结果不丢），并非证明旧结果非 `Sendable`。
- 证据：`artifacts/verification/f08-system-prompt-diagnostic-20261107/` 的首次编译（105.773s）、最终构建（12.373s）与告警记录。
- 边界：仅探针 helper 修正，不改动生产 adapter；最终仍留 1 类 `init(cString:)` 弃用告警，非零告警。

## hy3 额度 429（操作路由，非探针缺陷）

- 现象：本 docs 任务原路由 hy3 返回 `status 429` / `code 6004`，未产出任何文件；进程退出 0 不等于任务成功。
- 原因：错误为频率限制/类别额度（provider 429 / `code 6004`），不等于账单额度精确耗尽；按既定规则停止该线路、保留原错，不循环换模型规避共享额度。
- 处理：替代线路由用户明确选择 hy4free，未自动轮换；助手未递归调用模型（本 docs 任务本身已运行，确有模型调用），父代理记录操作暂停，未授权付费调用。
- 证据：`.omx/artifacts/cbc-f08-system-prompt-docs-20261107/raw.jsonl`、`.omx/artifacts/cbc-f08-system-prompt-docs-20261107/parent-assessment.json`、`.omx/artifacts/cbc-hy3-quota-pause-20261107/status.json`。
- 边界：`2026-11-07T16:18:54+08:00` 为服务端上报的 reset 时间，仅是**最早可复核时点**，不保证届时额度恢复；在此之前不调用 hy3。四次生成的诊断与各固定结果事实均不因此改变。

# 2026-11-07 Speech限定复核与F06取证补录

## 1. agy 429 与 hy4free 回退
- 现象：`agy`（`gemini-3.8-flash-medium`）出结果前 429 `RESOURCE_EXHAUSTED`、exit 3、收文件 0。
- 原因：单账号额度耗尽；估算重置 2026-11-14 08:25:21 Asia/Shanghai（约，来自服务端延迟 584292s），仅估算、无恢复保证；`hy3` 亦按用户指令暂停并记录重置时间。
- 有效处理：该线路不重试，仅重置后复核（recheck only）；按用户选择回退 `cbc hy4free`，未自动付费回退。
- 边界：不循环换模型规避共享额度；不把估算重置当恢复保证；失败 URL/凭据不入文档。
- 证据：`.omx/artifacts/ask-agy-speech-final-review-20261107.md`、`.omx/artifacts/agy-speech-final-review-20261107/parent-assessment.json`。

## 2. 两处复核解释性错误已更正
- 现象：长评的异常层级/catch 解释、以及把私有 `Decoder` + `CountDownLatch` 测试称作真实 JNI 的措辞，均被父评估驳回。
- 原因：JVM 上 `CancellationException` 属 `Exception`；该测试是 JVM 测试替身，验证协程取消/排空编排，非真实 JNI；`NonCancellable` 不强制挂起后端完成，`AsrResources.kt:89` 的 `isActive` 检查使双重恢复未证实。
- 有效处理：原 REVIEW 不改，另取真实源码/测试证据；零新缺陷不写成新补丁或发布无缺陷。
- 边界：不得把该 JVM 测试称为真实 JNI；候选审查不替代真机/发布验收。
- 证据：`.omx/artifacts/cbc-speech-final-review-20261107/parent-assessment.json`、`.omx/artifacts/cbc-speech-defects-review-20261107/parent-assessment.json`、`.omx/artifacts/cbc-speech-final-review-20261107/cancellation-hierarchy.txt`。

## 3. 冻结哈希稳定与旧 --verify
- 现象：两份冻结 summary 的旧全量 `--verify` 仍 exit 1。
- 原因：各 56 项哈希中 53 项一致，仅 3 份可变协调文档漂移（`docs/HANDOFF-2026-11-05-next-session.md`、`docs/agent-pitfalls.md`、`docs/validation/foundation-followups.md`）；源/测试/原始证据稳定，9 条受保护路径与 production lock 未变。
- 有效处理：保留原 exit 1 与旧 summary/脚本，补有界 source/test/evidence 哈希收据 `.omx/artifacts/speech-final-review-20261107/bounded-refreeze-check.json`（无新原生执行或原生研究结论），不重跑推理。
- 边界：不把旧 `--verify` 说成当前通过。
- 证据：`artifacts/verification/speech-cleanup-20261107/summary.json`、`artifacts/verification/sherpa-cleanup-20261107/summary.json`。

## 4. 官方 Markdown 工具不支持 vs 原始 GET 200
- 现象：网页工具无法解析官方 text/markdown（400）。
- 原因：工具内容类型限制，非官方页面不可用。
- 有效处理：改用原始官方 GET，三份 `.md` 均 200，精确字节/SHA 记于 `.omx/artifacts/f06-transfer-parent-evidence-20261107/retrieval.json`；结论仅为"所检视文档/头文件未见明确 RGB 传递函数契约"。
- 边界：不据"无明确契约"推断 sRGB/线性必需或正确，不把未验证 gate 当通过。
- 证据：`.omx/artifacts/f06-transfer-parent-evidence-20261107/`、`.omx/artifacts/native-f06-transfer-evidence-20261107/`、`.omx/artifacts/f06-transfer-parent-evidence-20261107/retrieval.json`、`.omx/artifacts/f06-transfer-parent-evidence-20261107/header-inventory.json`。
