# W9 Android 依赖通知增量（2026-11-19）

**隔离 unsigned Release 构建通过；正式发布仍 NO-GO。** 基于 `70877961f7073841e769973df0c10e61f39ed2b8` 及相同的四个组合开关重新构建。原 APK `a0b7627f2a90e521fb12eb9b24489349bdcef0e5c71a367f65590f18aa18dcc6` 的 725 个成员字节全部不变，包括 DEX、原生库、资源和已有通知。新 APK `765148e03136d39f2cffe85a345e9a14e11a732c214a43ca85c6cae8660f2d25` 共 734 个成员，正好新增 8 份原文和 1 份索引，均进入 `assets/third-party/w9-pinned-jvm-notices/`。包名、版本仍为 `io.ondeviceai.demo`、`0.1.0 (1)`；未签名、未安装、未发布。

| 依赖 | 原文来源 | 与原包的关系 |
| --- | --- | --- |
| Gson `2.14.0` | `google/gson` `3ff35d6269894901ab8006258395aafc4b9765cd` | 通用 Apache 正文有相同字节；带 Google Gson 项目声明的模块 `gson/LICENSE` 未命中，因此保留两份原件。APK 有 221 个匹配类定义。 |
| Kotlin Reflect `2.4.0` | `JetBrains/kotlin` `add726ca8c82922b6ab4cb2a27ae738d6a780817` | 通用 LICENSE 和 COPYRIGHT 已有相同字节；新增明确归属、上游 Compiler NOTICE 原文与 Protobuf BSD 原文。Reflect 构建包含 `protobufLite()`；APK 有 74 个重定位到 Reflect 的 Protobuf 类，包括 `CodedInputStream`。Compiler NOTICE 按上游上下文保留，不当成 Reflect 专用或完整的适用性结论。 |
| Coroutines Android / core JVM `1.11.0` | `Kotlin/kotlinx.coroutines` `8564f65764d3d05893cec026c6e94250e2b23874` | 两份 `1.11.0` 原文分别与旧路径下 `1.10.2` LICENSE/NOTICE 字节相同。索引分别绑定两个 `1.11.0` JAR 摘要和 6 / 868 个匹配类；不再仅靠旧路径推断版本覆盖。 |

原文按固定提交获取，通过 Git blob SHA-1 校验来源，并记录 SHA-256、实际 Gradle 解析产物和 APK 目标路径。APK 的新增成员再次按字节核验。完整证据在 `.omx/artifacts/w9-notice-source-delta-20261119/`：`fixed-upstream-texts.json`、`additional-inputs.jsonl`、`apk-member-delta.json`、`review-input.json` 和包内 `INDEX.json`。

Android 上轮识别出 49 个具有类贡献证据的产物；本轮把 4 个选定版本的关联加入隔离增量，合计 8 个，仍有 41 个没有这种关联。另 2 个缺正向类证据的产物不能据此排除。Sherpa/ORT/NDK 的嵌套来源、Kotlin Reflect 的全部嵌入内容及适用性仍未裁定。iOS 原样复核了 197 个文件的单图 App 清单 `c263a82418116e8342529de40ca9325a539937ac9831f8d1eecdc21afa750fab`；历史 Unicode、文件/头部例外仍开放，实包也仍缺两个版本字段。

本增量不继承上一 APK 摘要对应的 TECNO 隔离副本设备结论。双端 W9 用户功能验收保持有效，无需重测。本增量的独立复审完成。初审要求补齐 Kotlin Reflect 正式版来源：固定提交标题写着 RC2，原证据未给出正式版 tag 的指向。JetBrains 的正式发布 `v2.4.0` 与 `v2.4.0-RC2` 都指向索引已有的 `add726ca8c82922b6ab4cb2a27ae738d6a780817`。按正式版 tag 复取四份 Kotlin/Protobuf 原文，Git blob、SHA-256 和 APK 成员字节全部一致。复审员确认关闭这一条发现；APK、索引与初审输入均未改变。初审与复核证据见 `kotlin-final-tag-review/{receipt,independent-followup}.json`。正式签名组合及其独立最终复审仍待完成。

下一批先验证了 AndroidX Arch Core `2.2.0` 的 `core-common` 和 `core-runtime`。Google Maven 的发布二进制与实际解析产物 SHA-256 一致，对应 APK 中 9 和 5 个类；两个同版本 sources JAR 均可取得，6 个 Java 源文件都有 Apache 2.0 头部，但两个 sources JAR 都没有独立 LICENSE/NOTICE，POM 也未给出精确 SCM tag。来源材料已取得，适用通知与入包关联仍开放，仍计入 41 个待核项。此路径可用于其余 AndroidX 的来源核对，后续逐项处理缺件。证据：`androidx-arch-core-source-pilot/receipt.json`。

## 第二份隔离 Release APK：五个发布版本的来源关联

此前待核的 41 个依赖均已取得同版本发布二进制、POM 和 sources JAR；发布二进制的 SHA-256 全部匹配实际 Gradle 解析产物。用于归因的前两份 APK 已重新核对摘要，三个 DEX 均一致。扫描发布源码包、二进制及嵌套 JAR 未发现命名规则匹配的独立通知，这个结果不能作为无需通知的依据。449 个源码文件中，446 个有 Apache 2.0 标记；其余 3 个 `kotlinx.serialization` 文件已匹配固定发布 tag 的原件，文件级适用性仍开放。

| 新关联的依赖 | 与固定发布 tag 逐文件匹配 | 入包原文 |
| --- | ---: | --- |
| `auto-value-annotations:1.6.3` | 39 | `LICENSE.txt` |
| `error_prone_annotations:2.48.0` | 31 | `COPYING`、`AUTHORS` |
| `jspecify:1.0.0` | 5 | `LICENSE`、`AUTHORS` |
| `ui-backhandler-android:1.9.1` | 4 | `LICENSE.txt` |
| `kotlinx-serialization-core-jvm:1.7.3` | 52 | `LICENSE.txt` |

这五个来源共匹配 131 个发布源码文件。两份 `AUTHORS` 用作归属上下文。离线 Gradle Release 构建耗时 45 秒，含 `lintVitalRelease`；`aapt2 dump badging` 与前包全文相同，仍为 `io.ondeviceai.demo`、`0.1.0 (1)`。第二份 APK 的 SHA-256 为 `0ec07f47db38c844bb341e13cc7ff9303cc3c6ac884deb4a49a4994e8ee5a069`；前包 `765148e0…` 的 734 个成员全部按字节保留，仅在 `assets/third-party/w9-tagged-jvm-notices/` 增加 5 份根许可、2 份 `AUTHORS` 和 1 份索引，总计 742 个成员。第二份 APK 未签名、未安装、未发布。

累计 **13/49** 个有类贡献证据的依赖具备选定版本的入包关联；**36** 个仍待绑定。AndroidX 32 项已取得同版本来源，旧 APK 中有 53 个路径与固定 AndroidX 主干提交 `eef1fc76d4e22eb9b1b6b524e98c66d79818037b` 的共享 Apache 原文逐字节匹配；还需核对历史版本来源和模块例外。另外 4 项是 `firebase-annotations:16.0.0`、`firebase-encoders:16.1.0`、`listenablefuture:1.0` 与 `javax.inject:1`。13 项关联和以上两批入包均不能替代完整适用性裁定。

第二份 APK 的独立复审返回 `exceeded retry limit, last status: 429 Too Many Requests`，没有复审结论，主代理未重试。第一份 APK 的 Kotlin Reflect 正式版来源复核仍有效。用户的双端变声功能验收继续有效；本轮没有新增设备验证，也不把旧包的冒烟证据计入新包。正式发布仍 **NO-GO**。

证据均位于 `.omx/artifacts/w9-notice-source-delta-20261119/`。`source-sweep-01/` 保存 `receipt.json`、`header-followup.json`、两份 `tag-source-matches*.json`、`upstream-root-texts.json` 和 `upstream-apk-byte-matches.json`。`tagged-notice-apk-02/` 保存 `review-input.json`（精确 36 项队列、复审状态和证据摘要）、`apk-member-delta.json`、`expected-members.json`、`apk-badging.txt`、`build.log` 及 APK。原 41 项 `remaining-source-queue.json` 保留为历史记录。
