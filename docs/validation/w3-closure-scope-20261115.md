# W3 当前验收范围与充分证据边界

2026-11-15：W3 仍未完成。R1–R3 已按当前 TECNO 已知限制完成范围裁定；R4 质量、R5/W8 资源与生产发布要求分别保持 open。本次只修正清单分类和证据定义，不改变质量门槛、资源预算、历史失败或执行授权。

## 当前分类

| 类别 | 当前结果 | 关闭所需结果 |
| --- | --- | --- |
| R1–R3 系统语音场景 | 当前 TECNO 范围裁定完成；R1 实测失败，R2/Android R3 因资源前置未满足而未执行 | 保留已批准的已知限制及原始失败/未执行记录；不写成识别或生命周期成功 |
| R4 文件质量 | 原 tiny/Apple 的中文、噪声错误保留；Qwen Mac 两阶段独立确认通过；Android 中文 20/20、CER 6.78%，整批在 24/65 后内存停止 | 固定目标真机上的完整适用质量覆盖与质量裁定；英文仅 4 项，AMI/挑战未执行，不能由 Mac、中文子集或来源补证替代 |
| R5/W8 资源 | Apple/Sherpa 固定持续观测已验收；缺测和生产预算未通过 | 同设备、同输入/运行时的可靠资源证据、明确缺测边界及适用预算裁定；采样 RSS 不等于 true peak，paced idle 不等于持续高负载 |
| P1–P4 生产发布 | 部分来源材料、归档、四 ABI ORT 成员与日志已验收；四项均 open | 按下表分别证明实际交付边界，并完成独立发布复审 |

原 [R1–R5 矩阵](w3-remaining-acceptance-cases-20261108.md)中，R4 是中文/噪声文件质量，R5 是 own-App-PID 资源观测。当前 [W3 roadmap](../roadmap/W3-speech.md)还明确列有生产发布余项；它们属于 W3 整包关闭要求，不能因旧矩阵未单列就删除，也不再混写为 R4/R5 指标。

## P1–P4 生产发布证据

| ID | 必须证明的边界 | 充分证据的验收条件 | 当前缺口 |
| --- | --- | --- | --- |
| P1 源码、构建与运行时对应 | 固定实际采用的 runtime/AAR、全部交付 ABI 和运行时成员；逐成员关联实际源码 revision、补丁、构建输入/配置及构建输出 | 可复核的来源链把上述输入和输出绑定到实际 AAR 成员及最终采用的二进制摘要；逐项说明覆盖和差异，不留未解释的运行时成员。可以组合上游构建记录、工件记录、成员核对或同输入重建等证据；任何方法均须接受覆盖与可信度复审 | 四 ORT 成员/日志及[CI 整 AAR、四 ABI/16 库字节对应](w3-sherpa-ci-members-20261115.md)已核；[21 成员/九输入组矩阵、实际补丁及既有隔离消费者绑定](w3-p1-member-matrix-20261115.md)增量已接受。[历史 a18292ac SDK 本地发布与独立库消费者绑定](w3-sdk-consumer-binding-20261115.md)已父验，[免费hy3限定供应文本复核](w3-sdk-binding-hy3-review-20261115.md)已补交；旧hy4free429原件保留，native通知51b705c/5ae065d2保留为历史候选，本次[ORT嵌套通知候选](w3-ort-nested-notice-integration-20261115.md)Release AAR为188a558c，不将历史6de95f56称最新HEAD；[JVM链接解析到真实源码](w3-jvm-source-resolution-20261115.md)补齐22源字节及声明集合，旧链接blob历史保留；最终生产App采用及逐输入组完整性/可信度接受仍缺，类名/SourceFile不证明字节码等价，P1保持open |
| P2 递归最终组成 | 固定交付物中的直接、静态嵌入及传递组件；覆盖实际版本/源码、补丁、适用许可、声明和归属 | 逐层关联最终二进制成员与其嵌入组件，清单与构建/链接材料相互核对；未纳入、可选或未启用项有依据，不能把源码仓库的依赖声明直接当实际链接组成 | [simple-sentencepiece三文件上游Android补丁、四ABI构建观察及Darts嵌套声明](w3-sentencepiece-patch-20261115.md)已补核；[已知native通知选择及原上游修改说明](w3-native-notice-integration-20261115.md)已交付，ORT两sed命令与四ABI实际diff差异已记录，历史材料不改；新增[ORT嵌套逐项选材、四ABI编译观察及7组件11补丁/21文件本地重建](w3-ort-nested-notice-integration-20261115.md)：31参照26选入/5明确不选，原文/声明/实际guard边界已核；Found Patch不证明历史apply，局部编译/声明不证明最终LTO保留。另新增[两条NDK/C++ runtime来源增量](w3-ndk-runtime-provenance-20261115.md)：八条实际ABI命令、两枚arm64产物note、69源路径/61唯一字节与30tree已核；Sherpa r30/ORT r27d及Linux GNU/Android STL分支明确分开。75/59 patch引用和基础许可/whole-toolchain NOTICE仅为输入材料；新增[固定CMake3.31.6及两条STL默认分支](w3-cxx-linkage-rules-20261115.md)：五原日志/两CI ZIP、30源元数据路径/22唯一文件、6tree/18子对象边已核，Sherpa legacy与ORT非legacy给出条件化静态libc++规则；未恢复历史cache/完整link命令，默认/编译flags不证明最终archive成员保留，实际覆盖、retention与适用性仍缺；现有归档、源码和构建文本仍不是完整递归最终组成清单，另补[四JVM依赖声明及stdlib四条派生源示例](w3-jvm-dependency-notices-20261115.md)，原POM顶层声明不足以覆盖嵌套归属；历史CI补丁后源码及最终保留机器码未直接取得，未覆盖项保持显式缺口 |
| P3 实际交付物通知保留 | 固定生产候选的 AAR/SDK/App 及其实际打包、消费链 | 从真实候选交付物提取适用许可/通知并与 P2 清单逐项核对；记录原文、路径及内容摘要，验证消费/打包后仍按适用要求保留，记录所需修改说明与归属 | [17份现有产物及嵌套JAR检查](w3-notice-delivery-20261115.md)确认当前Sherpa SDK/runtime没有独立通知资源；thin library-consumer不代表最终App或打包丢弃。新增[已知JVM子集的实际通知集成与独立Maven最终APK保留](w3-jvm-notice-integration-20261115.md)：10原文本/归属及索引均在新AAR和独立APK保留，缺失/改单字节负例拒收。新增[已知native子集及上游修改说明的实际集成](w3-native-notice-integration-20261115.md)：23原文本及索引/修改说明25新资产，合并JVM共36项，在新AAR及独立Maven最终APK原字节保留，六负例拒收。另新增[ORT嵌套通知及补丁说明](w3-ort-nested-notice-integration-20261115.md)：26原文109766B及子索引，合并63通知697877B在新AAR/独立Maven APK保留、十负例拒收、四ABI16SO不变；notice-bearing包字节改变。完整适用清单及全部所需修改说明仍受P2未决项限制，P3保持open；原141文件材料包不代替交付 |
| P4 独立发布复审 | 同一固定生产候选、P1–P3 证据及适用再分发条件 | 独立复审明确候选身份、审阅材料、覆盖限制、问题处置及接受结论；未解决的发布问题不能由实现者自报或局部技术复核关闭 | 当前没有该完整发布接受记录；限定源码审查、hy3 日志审阅和本次范围复核均不能替代 |

这里规定证据要达到的结果，**不指定唯一的“加密证明”构造**。摘要用于固定字节身份，本身不证明源码真实性、完整组成或许可满足。选择何种证据方法，要解释实际覆盖及未覆盖部分；现有局部证据不因组合数量增加而自动变为完整证明。

## 已接受材料及原始限制

- [13 份源码归档](w3-runtime-source-archives-complete-20261112.md)、[四 ABI ORT 成员对应](w3-ort-release-members-20261114.md)、[四 ABI 构建日志](w3-ort-all-abi-20261114.md)保留各自通过范围，不重取归档或重跑日志任务。
- [统一复审材料包](w3-release-review-materials-20261113.md)保留 57 份许可/声明、81 份支撑文本及完整 ZIP 成员校验；`finalBinaryCompositionVerified`、`productionNoticeRetentionVerified`、`independentReleaseReviewAccepted` 仍为 false。
- [R5/W8 预算审计](w8-resource-dispositions-20261109.md)说明尚无按能力/设备/输入批准的生产数值门槛；不能从现有观测反推通过值，也不要求用户接受已知质量失败来关闭 W3。
- [只读范围复核原文](w3-closure-scope-native-review-20261115.json)指出清单分类与充分证据定义缺口；本文件及 roadmap 的 P1–P4 分类落实该 MEDIUM finding。本次接受的是范围修正，不是 W3-complete 或生产发布接受。

## 真机继续条件

本次守卫通过后 ADB 设备清单仍为空；此前一次替代 USB 后端未恢复，已恢复 NATIVE，[恢复及同步收据](w3-adb-libusb-sync-20261115.json)保留。USB 枚举不能代替可用 ADB，不重复 server/backend 试验，不推断锁屏、授权或线材唯一根因。

新合同 `w3-qwen-terminal-20261114-01` 尚未 prepare 或消费。实体连接恢复后先重新通过冻结守卫及 fresh preflight，再按固定合同执行单次文件诊断及同 run 收取/恢复。现行单片段、构造/解码次数和资源守卫不变；它用于定位终态与成本，仍不能单独补齐 R4 全量质量或生产预算。不开麦克风、AiAi，不下载模型，不重放旧 FILE/heap，也不以模拟器替代实体验收。
