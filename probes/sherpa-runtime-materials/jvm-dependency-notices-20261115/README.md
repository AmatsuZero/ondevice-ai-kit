# W3 已解析 JVM 依赖的声明材料

本目录补充当前 SDK 实际 Debug 消费图中四个 JVM 坐标的固定 POM、上游声明及 Kotlin stdlib 的已知派生来源，尚未集成生产通知。

`dependency-metadata.json` 关联四个已解析 JAR 的原大小/SHA256与对应 POM。两个 coroutines POM 校验官方 SHA512；Kotlin/annotations POM 校验官方 SHA1，并与本机已有缓存逐字节一致。SHA1与GitBlob摘要用于固定文件身份，不是独立签名或源码到二进制等价证明。

`source-notice-manifest.json` 固定三个上游版本标签解析出的 commit/tree及12份原始来源文本，共81755B。包含许可、NOTICE、归属、模板和范围说明，**不是12份全部适用的交付通知**。Kotlin `license/NOTICE.txt` 自称 Compiler distribution，只作为范围证据保留，不自动认定为 stdlib 必须通知。

`stdlib-source-binding.json` 记录发布 sources JAR 的SHA1校验、四个原始源码成员与固定 Kotlin commit 的逐字节相同，以及当前 stdlib JAR 中四个对应类成员的存在/摘要。GWT、ThreeTenBp、Boost、Guava原声明注释片段另保留在 `source-attribution-excerpts.json`，完整源码的摘要、GitBlob与路径仍可复核。`stdlib-source-declarations.json` 是源包内的六个声明命中，不能将六个命中等同于完整组成。

这些材料表明 POM 的顶层 Apache 声明不能代替嵌套声明检查：固定范围说明还列有 stdlib 的 ThreeTenBp BSD3、Boost BSL1.0、GWT/Guava Apache来源。四个示例不是逐class重建等价、最终R8/DEX保留或完整递归清单。

首次无缓存POM、Kotlin `.pom.sha512` HTTP404、根级文件选择漏掉 `license/` 目录的原失败均保留，见 `first-failures-and-recovery.json`。只补缺项，不重复已取得的POM、ref、根tree或coroutines LICENSE；没有改缓存、依赖版本、全局代理、生产代码或模型。

P1–P4、R4/R5及W3保持open。本轮没有构建/helper/设备执行；161冻结通过后只查询一次ADB设备清单，结果为空，未做服务或后端恢复尝试。
