# W3 现有 SDK 交付物的通知缺口

2026-11-15：当前 Sherpa backend SDK AAR 和固定 Sherpa runtime AAR 都没有独立的许可/通知资源。P3 从“尚未检查实际交付物”进一步定位为“已检查的 SDK 候选缺少通知交付，最终 App 保留仍未验”；本轮没有生产实现或独立发布接受，W3 保持 open。

## 实际检查

沿用[当前 SDK 发布与消费者绑定](w3-sdk-consumer-binding-20261115.md)的固定产物，先重核大小/SHA256及 47 份源码输入不变，没有重建或下载。SDK 来源为 `a18292ac56e7c694fd2e7efb2550864a0b3ef21e`；本轮检查前 HEAD 为 `e375bad52b86163fea5525ca979c551f83f827f6`。

| 产物范围 | 数量 | 结果 |
| --- | ---: | --- |
| 当前本地发布的五 SDK AAR | 5 | 没有独立通知成员；Sherpa backend AAR 38862 B，SHA256 `6de95f567932747fb98ffc7b15ee66e0cca75a6674f63c87e40e8bc9b2852028` |
| 固定 runtime AAR | 1 | 49441341 B，SHA256 `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`；外层 21 个非目录成员为 R.txt、manifest、classes.jar、空 proguard、metadata、16 SO，无通知资源 |
| 独立 library-consumer AAR | 2 | Debug/Release 均无通知成员；它们未合并传递依赖，不能据此认定打包丢弃通知，也不是最终 App |
| 已记录 Debug 配置实际解析的其他 JAR | 4 | coroutines core/android、Kotlin stdlib、annotations；未发现独立通知成员，本轮未补取这些组件的来源声明 |
| 已发布 sources JAR，辅助检查 | 5 | 无独立通知成员；源码 JAR 不能代替二进制交付物的保留验收 |

共 17 个外层产物、25 个含嵌套 JAR 的容器、2243 个非目录成员记录。Python zipfile 与实际 Info-ZIP 有序成员清单、串接解压负载逐字节一致；成员计数同时含外层 JAR 文件及其内部成员，不是独立文件总量。

所有成员名均未命中所列通知命名；全部解压字节未找到 58 份已知运行时来源材料的精确/包含匹配，UTF-8 成员未出现所列许可特征。对 Sherpa SDK/runtime 的完整成员结构检查支持“没有独立通知资源”这一具体结论。命名/内容扫描不是任意等价条款的穷尽搜索或法律符合性裁定。

58 份参照材料是旧集合去掉 Qwen 模型许可后的 56 份，加 Darts 与原 ThreadPool 两份新声明，包含历史候选及未确认的组成项；不是完整或全部适用的生产通知集合。不能直接复制旧 141 文件复审包来声称完成 P2/P3。

## 下一项修正

[精确集成任务](../../probes/sherpa-runtime-materials/notice-delivery-20261115/P3-INTEGRATION-BRIEF.md)已准备：为 backend SDK 增加唯一命名的通知资源、原文字节/归属/index/上游修改说明，构建新 AAR，并用独立最小最终 APK 验证消费后的逐文件保留。先完成组件选择表，补足已解析依赖声明和 P2 未决项；不改 runtime、模型、版本或识别逻辑。

生产候选仍按既有分工由用户所选外部 helper 交付。原 native 429 不重试；hy4free 的真实 429/code6004 服务端重置观察为 **2026-11-16 10:15:32 UTC+8**，不据旧选择重新调用，也不自动换模型。新线路问题已提出，等待答复期间本地核验继续；本轮新 helper 调用为零。

## 证据及边界

[检查收据](../../probes/sherpa-runtime-materials/notice-delivery-20261115/inspection-receipt.json)、[17 产物观察](../../probes/sherpa-runtime-materials/notice-delivery-20261115/artifact-notice-observations.json)、[25 容器交叉核对](../../probes/sherpa-runtime-materials/notice-delivery-20261115/archive-comparison.json)、[Sherpa 与消费者的 189 成员](../../probes/sherpa-runtime-materials/notice-delivery-20261115/sherpa-and-consumer-members.json)及[实际检查脚本](../../probes/sherpa-runtime-materials/notice-delivery-20261115/inspection-source.py.txt)固定结果；其余完整成员清单留在本地 `.omx/artifacts/w3-notice-delivery-20261115/`，摘要见收据。

没有重跑构建、设备操作、模型下载或生产修改；161 文件冻结合同通过。实际解析图仍仅为先前的三个 Debug runtime 配置，不扩大成 Release runtime 解析或完整最终组成。R1–R3 已批准范围裁定保持；R4 质量、R5/W8 资源及 P1–P4 各自保持 open。
