# W3 JVM 依赖声明与 stdlib 嵌套来源

2026-11-15：为当前已解析的四个 JVM 依赖补齐声明来源，并发现 Kotlin stdlib 不能只凭 POM 的 Apache 声明选材。新增12份原始来源文本81755B及四条派生源示例绑定；尚未进行生产通知集成，P1–P4与W3继续open。

## 固定输入与结果

沿用[当前 SDK 实际消费图](w3-sdk-consumer-binding-20261115.md)的四个 JAR，大小/SHA256重核不变，不重新解析或改变依赖。四份官方 Maven POM 坐标均精确匹配且有许可声明；coroutines两份的SHA512通过，Kotlin/annotations使用已发布SHA1并与已有缓存逐字节相等。后两项不冒充SHA512或独立签名证明。

| 依赖 | 固定来源 | 已保留材料 |
| --- | --- | --- |
| coroutines core-jvm/android 1.10.2 | Kotlin/kotlinx.coroutines，commit `5f8900478a8e20c073145b1608fbc71fe3d7378b` | 根LICENSE和 `license/NOTICE.txt`，NOTICE明确指向该library |
| Kotlin stdlib 2.4.0 | JetBrains/kotlin，commit `add726ca8c82922b6ab4cb2a27ae738d6a780817` | `license/`五份根文本及四份第三方文本；其中README/版权模板属支撑，Compiler NOTICE不自动当stdlib通知 |
| JetBrains annotations 23.0.0 | 标签为annotated tag，解析至commit `5d06c0c48e0a4fe44b2bc0d5d3e8b113c7a57de2` | 根LICENSE；不把tag object本身当源码commit |

所有12份文本的原始大小、GitBlob、SHA256与固定tree对应，原文不格式化。这里只接受声明来源增量，不声称整个组件源码、所有class或最终静态组成已经重建/穷尽。

## stdlib 的实际嵌套声明

固定 `license/README.md` 将相关stdlib路径关联至GWT、Guava、ThreeTenBp与Boost。源包757669B、SHA256 `bca27f834e278d08c1b85a40e636450867e8251c284fd4c24e9879bb99e301c5`，官方发布SHA1相同；以下四个成员还与固定commit逐字节相同，原归属注释片段保留。

| 来源 | 源包成员 | 当前runtime JAR中对应类 | 已取原文 |
| --- | --- | --- | --- |
| GWT | commonMain/kotlin/collections/AbstractList.kt | kotlin/collections/AbstractList.class | gwt_license.txt，Apache2 |
| ThreeTenBp | commonMain/kotlin/time/Instant.kt | kotlin/time/Instant.class | threetenbp_license.txt，BSD3 |
| Boost | jvmMain/kotlin/util/MathJVM.kt | kotlin/math/MathKt__MathJVMKt.class | boost_LICENSE.txt，BSL1.0 |
| Guava | jvmMain/kotlin/util/UnsignedJVM.kt | kotlin/UnsignedKt.class | guava_license.txt，Apache2 |

这四条证明所选源示例与固定上游相同、对应class成员存在；不能据此声称字节码等价、每段派生实现都保留到最终DEX，或stdlib/其他三个依赖的递归组成已完整。实际通知选择还需与最终候选/收缩配置及完整适用清单核对，不能盲拷Kotlin Compiler的NOTICE或整个仓库的测试/工具条款。

## 失败保留与执行边界

首缓存读取对coroutines POM不存在而中止；先核目录事实后取固定坐标元数据，没有把缺缓存当缺许可。Kotlin POM本体已取但 `.sha512`返回404，复用原POM并单独校已发布SHA1/缓存，不重取或声称SHA512通过。首根级LICENSE筛选漏掉Kotlin的 `license/` 目录而失败；按既有pitfall读取精确子tree，已成功的POM/ref/tree和coroutines LICENSE不重取。完整失败与恢复见[记录](../../probes/sherpa-runtime-materials/jvm-dependency-notices-20261115/first-failures-and-recovery.json)。

本轮161冻结通过后，唯一ADB设备清单查询exit0/空；没有prepare、消费run、设备识别或USB恢复。没有新helper/构建/模型下载/生产修改。原native/CBC429停线及已提出的新免费线路选择保持；不重新请求已消费的hy4free任务。

完整暂存diff-check为exit2：30项来自原annotations POM的30个CRLF行，另2项来自原Kotlin许可README第13/24行尾空格；固定摘要/缓存/原tree核对通过，原文保持。其他本轮文字diff-check为exit0，不通过改写上游证据或全局配置消除这些提示。

[机器收据](../../probes/sherpa-runtime-materials/jvm-dependency-notices-20261115/inspection-receipt.json)、[源码与声明清单](../../probes/sherpa-runtime-materials/jvm-dependency-notices-20261115/source-notice-manifest.json)、[四源绑定](../../probes/sherpa-runtime-materials/jvm-dependency-notices-20261115/stdlib-source-binding.json)和[原归属注释](../../probes/sherpa-runtime-materials/jvm-dependency-notices-20261115/source-attribution-excerpts.json)固定本次材料；生产通知实现仍按[现行任务](../../probes/sherpa-runtime-materials/notice-delivery-20261115/P3-INTEGRATION-BRIEF.md)继续，P3/最终APK保留及独立发布接受未完成。
