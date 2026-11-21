# W3 现有交付物通知检查

2026-11-15，仅检查已经留存的本地发布候选及实际 Debug 解析产物；没有重建、设备操作、模型下载或生产代码修改。

`artifact-notice-observations.json` 固定 17 份产物的路径、大小、SHA256、角色及检查结果。`archive-comparison.json` 固定包括嵌套 JAR 的 25 个容器的成员列表和解压负载摘要；Python zipfile 与实际 Info-ZIP 的有序成员列表、串接解压负载逐字节相同。共读取 2243 个非目录成员，包含外层 JAR 成员和它们内部成员，计数不是独立文件数量。

`sherpa-and-consumer-members.json` 保留 Sherpa SDK AAR、固定 runtime AAR、Debug/Release 两份 library-consumer AAR 的全部 189 个非目录成员及嵌套类清单。其余成员的完整本地记录及摘要见 `inspection-receipt.json`。`inspection-source.py.txt` 为实际父端检查脚本的原文，输入产物来自先前已核的 sdk-consumer-20261115 材料；不是新生产校验器。

17 份产物中均未观察到名称含 license/licence/notice/copying/copyright/attribution/third-party 的成员，也未找到 58 份已知运行时来源文本的精确或包含匹配，UTF-8 成员未命中所列声明特征。Sherpa SDK 外层仅 R.txt、AndroidManifest.xml、classes.jar、AAR metadata；runtime 外层另有空 proguard.txt 与四 ABI 的 16 个 SO，没有独立通知资源。

58 份参照文本来自原复审集合的 56 份运行时相关材料及新增 Darts/原 ThreadPool 声明，**不是完整或全部适用的生产通知清单**；包含历史来源候选、未确认嵌入项和源码声明，不能直接整包复制后称满足 P2/P3。名字/内容扫描也不是法律符合性裁定。

消费者是 thin library AAR，没有合并传递依赖；它不包含通知不能证明打包过程丢弃了通知。最终 App 尚未检查。已确认的修正对象是 SDK 候选缺少通知交付；后续范围及验收见 [集成任务](P3-INTEGRATION-BRIEF.md)。P1–P4、R4/R5、W3 保持 open。
