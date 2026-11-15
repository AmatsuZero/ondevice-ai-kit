# W3 NDK notice 增量集成复核

报告路径：`docs/validation/w3-ndk-notice-integration-20261115.md`。复核仅依据父级回执/证据文本；父级负责集成与验收，复核者未执行构建、设备、下载或源码请求。参考：[运行时补丁选择](w3-ndk-runtime-patch-selection-20261115.md)、[closure 范围](w3-closure-scope-20261115.md)、[验证材料 README](../../probes/sherpa-runtime-materials/ndk-notice-integration-20261115/README.md)。

## 已接受范围
- 范围未扩张，未发现需 revise 的新 blocker：新增 4 份真实原始 `LICENSE.TXT` 共 66823B，加 1 个 `native/ndk-cxx/index.json`；旧 63 个 notice 资产逐字节不变，合计 68 assets/770479B；backend README append mechanical exact。
- 原文保留不变，无 SPDX 标签、摘要或通用正文替代；固定 source map 将 r30 原始身份绑定到既有规范 r27d 副本（正文逐字节相同），未虚构本地 r30 副本。
- 父级 build exitCode=0，backend lintDebug 成功；435 tasks 中 29 executed、406 up-to-date，backend Kotlin 可能 up-to-date，因此不作全量源码重建声明。
- Debug/Release AAR 均为 noticeCount=68、noticeBytes=770479B；Python/InfoZIP 完整归档清单与 notice payload 一致；non-notice AAR members 与上一候选相同；isolated local Maven publication 与 release AAR 一致。
- thin library AAR 不是最终 app；本轮使用无 project deps 的真实 standalone Maven application fixture，解析 exact isolated backend AAR 与 fixed runtime 后比较实际 APK。APK noticeCount=68/770479B；4ABI16SO 与 fixed runtime AAR 完全一致；新 APK 中 stripped 上游库按原样打包，SDK XML version 4 警告保留。
- 10 个 NOTICE-ONLY ZIP 负例（4 个 license + index，delete 或 same-length byte flip）被同一 verifier 拒绝，该 verifier 同样用于真实 AAR/APK；它们是 notice-only fixtures，不是 modified signed APKs。
- 首轮跨工具失败保留：请求名 sorted 与 unzip archive order 不一致，两边均 770479B；unzip 与 archive-order payload 一致而非 sorted payload。仅离线改变比较顺序，无新构建；guard 不得隐藏失败或推断 packaging corruption。
- 证据卫生：161 frozen contract files pass；未准备或消费新 run；无 device/AiAi/mic/download/source-host calls；集成基线时 174 protected files 在本次 report/shared 更新前均未改变。

## 边界与剩余 gates
- 本结论仅是 bounded increment，不是 P4 独立发布验收。四个组件 `finalBinaryPresenceVerified=false` 指 native-code 存在性/组成未证，不是 notice 字节验证失败。
- 源、默认 STL flags 与 DT_NEEDED 不能建立最终静态/内联组成、补丁体覆盖、sanitizer/架构纳入或法律发布接受；无 whole-toolchain NOTICE、完整性或法律批准。
- R1-R3 的 TECNO 范围已裁定；R4/R5/W8、P1-P4 与 W3 仍开放，完整用户目标仍在进行。剩余 gates 可组合补丁选择、源文本、AAR/APK 字节保留、负例 verifier 与运行时 SO 哈希等证据，不强加唯一密码学证明。
