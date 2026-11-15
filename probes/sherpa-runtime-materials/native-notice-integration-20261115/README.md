# W3 native 通知集成与上游修改说明

本轮把23份已核原文本452769B、机械绑定索引及独立上游修改说明写入 `android/backend-sherpa-asr/src/main/assets/ondeviceai/sherpa-asr/notices/native/`，新增25资产477865B；与原JVM11资产合计36份550583B。原许可/归属逐字节保持，未改Kotlin、Gradle、依赖/坐标/锁/版本、模型或runtime AAR。

选择覆盖Sherpa/ORT根许可和完整上游ThirdPartyNotices、已知直接依赖、实际Sherpa构建使用的Eigen5.0.1、ARM观察的websocketpp/asio及simple-sentencepiece内原Darts BSD2声明。58份参照的每一项均有selected/unselected/deferred处置；历史Eigen3.4、KissFFT额外Unlicense和被stub替换的ThreadPool不被误说成当前必然链接/不适用，ORT嵌套27参照仍待组成选材。不是58份整包复制，也不把上游ThirdPartyNotices当所有最终组成证明。

免费hy3的选择49.151s/修改说明34.180s完整交付；独立supplied-context技术文本复核49.827s返回accept_bounded_candidate、无findings，明确未执行源码字节/构建/最终组成或法律符合性核验。父端按来源核字节、路径、完整58项处置及目标唯一性，使用复核给出的精简README段。三个调用均零已观察实际工具事件或quota，无原native/hy4free429重试/付费/自动fallback。提示和raw摘要见helper收据，完整原件留对应.omx目录。

原上游修改说明分别记录Sherpa version.cc日期/短SHA、simple-sentencepiece三个文件移除线程池/改顺序处理，以及ORT两条sed命令与四ABI实际diff。`ort-patch-interpretation.json`纠正旧patchSummary范围：命令匹配删除不证明所有VERSION都被删除，所示多行VERSION仍在；原历史证据不重写，完整patch/input仍不闭合。

首次父绑定漏处理Darts可选sourceManifest字段，在完成索引/修改说明前误启动构建。该12.285s构建虽然exit0，因候选不完整拒作验收，原件与failure记录保留。待其终态后按真实Darts材料manifest补绑定，23原文本不改，完整候选在新m2-complete目录离线重建11.135s通过（435任务/29执行，backend Kotlin仍up-to-date），Debug/Release AAR及lint通过。新Release161636B/SHA `5ae065d23647898eed3cbc258395f1c9c98804b002e4f9b4934546ae55bb7dd5`；相较上轮Release，原15非目录成员（含JVM11资产）全字节相同，只增25native资产。

复用上一轮hy3交付的独立application夹具，无源码project依赖/模型/普通Demo，经本地Maven新坐标解析；7.709s构建，35任务全执行。APK128014927B/SHA `20be0aa3e9c8a3a3ef40952ca7029c2f5a171761187ef98cfffa68329debef2a`，71非目录成员；36通知资产经Python/Info-ZIP原字节核对，16SO四ABI同固定runtime。SDK XML及原库不可strip警告保留，非无警告或设备运行通过。

`verify-delivery.py`以固定expected-assets.json检查通知成员集合、唯一性、大小/SHA。在复制件分别删除/同长改单字节Darts声明、UPSTREAM-MODIFICATIONS和native索引，六组均拒收，原APK不变。脚本只操作指定归档/输出复制件，不安装设备。

173原保护路径在新增文档前保持，161冻结文件通过；唯一fresh ADB清单为空，无server/backend重置、prepare/解码/麦克风/AiAi/模型下载。P1–P4/R4/R5/W3仍open；本轮只证明已知native/JVM子集的实际SDK及独立最终APK保留，不是完整组成、所有修改说明、最终生产App采用或P4发布接受。
