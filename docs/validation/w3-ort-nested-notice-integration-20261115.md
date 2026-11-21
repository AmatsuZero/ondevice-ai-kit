# W3 ORT 嵌套组成、通知与补丁说明增量

2026-11-15。本轮将固定 ORT 的已知嵌套通知从统一暂缓处置改为逐项选择，并实际集成到 Android Sherpa SDK。26 份原文共 109766 B、一个子索引及追加的修改说明已交付；合并既有 JVM/native 通知共 63 项、697877 B。离线 Debug/Release AAR、lint、隔离本地 Maven 发布和独立 Maven application APK 均通过；AAR/APK 内通知逐字节相同，10 个删除/同长改单字节负例均拒收。

这只接受已知子集的技术交付。P1–P4、R4/R5 和 W3 保持 open；没有运行设备、麦克风、AiAi 或模型下载，也没有用模拟器代替真机验收。既有生产逻辑、Gradle、依赖、固定 runtime 和模型未改。通知资产会改变 SDK AAR/APK 的包字节，不能写成所有 AAR 均不变。

## 固定来源与构建观察

ORT source revision 为 `33ca9628233dc8f002435e868d4c2e9f82766ca1`。复用既有 13 份已验归档，不重取归档；另外从该固定 commit 读取 16 份官方 CMake/patch 原件，逐份核 API GitBlob、大小和 SHA-256。材料入口为 [source-receipts.json](../../probes/sherpa-runtime-materials/ort-nested-composition-20261115/source-receipts.json)，保留猜测路径 `cmake/external/kleidiai.cmake` 的 404；实际声明位于已留存的 `onnxruntime_external_deps.cmake`，未重发失败请求。

四份历史构建日志与既有 observations 的大小/SHA 重新绑定后，取得以下实际编译单元计数。shared ORT 的 internal/external 链接列表、MLAS 的 KleidiAI/CPUINFO_SUPPORTED 分支另由固定源码关联。

| 组件 | arm64-v8a | armeabi-v7a | x86 | x86_64 |
| --- | ---: | ---: | ---: | ---: |
| Abseil | 124 | 124 | 124 | 124 |
| Protobuf | 32 | 32 | 32 | 32 |
| FlatBuffers | 4 | 4 | 4 | 4 |
| ONNX | 60 | 60 | 60 | 60 |
| re2 | 22 | 22 | 22 | 22 |
| cpuinfo | 18 | 18 | 20 | 20 |
| KleidiAI | 239 | 0 | 0 | 0 |

date、mp11、JSON、GSL、SafeInt、Eigen 的 header/target 声明仍须计入，不能由没有独立编译单元推断不存在。上述记录不证明最终 LTO 保留或完整递归组成。

## 选材与原文绑定

[子索引](../../android/backend-sherpa-asr/src/main/assets/ondeviceai/sherpa-asr/notices/native/ort-nested/index.json)对既有 refs 30–56 加新增 refs 101–104 共 31 项恰好各处置一次：26 项选入，5 项明确不选。父集成逐项核原件大小/SHA、来源 manifest、archive member 和版本根目录；cpuinfo/ONNX 的恢复 overlay 没有可选 `archiveRoot`，明确从已绑定 member 根目录记录来源，不猜版本。第一次预检因此抛 `KeyError`，发生于生产写入和构建之前，失败原件保留。

- JSON v3.11.3 保留 MIT 及上游完整 embedded-attributions 文档，包括 CC0/Hedley 声明。`.reuse/dep5` 将 GPL 指向 `tests/thirdparty/imapdl`、Apache 指向测试 Fuzzer、BSD 指向 `tools/amalgamate`；固定配置 JSON tests OFF，不把这几份测试/工具/泛型许可无选择复制到交付物。
- mp11 README/header 是声明/归属证据，不冒充全文许可；完整 BSL 跨引用既有 Asio 原文资产。
- KleidiAI arm64 的 239 个实际编译源头均为 Apache 声明，另保留三种年份的原始连续版权/SPDX 注释。泛型 BSD 根文本不证明保留了 BSD 代码；也不据此宣称绝对不存在。
- ORT 嵌套 Eigen 的 commit 与 Sherpa Eigen 5.0.1 分开保留；cpuinfo/clog、Protobuf/utf8_range 的 bundled archive/header 路径未被提升为最终必然存在或绝对不存在的结论。

根索引既有 23 entries/groups 保持，只替换 refs 30–56 的一个处置及对应 ORT blocker。旧 36 资产中除根索引/修改说明外保持原字节，原 Sherpa/ORT 修改说明和模块 README 的原有字节也保持。

## 上游嵌套补丁

从已验归档在新隔离目录提取受影响文件，对 7 组件的 11 份原 patch 依序执行本地 `git apply --check` 和 apply，全部成功；21 个组件内文件前后大小/SHA 已核。[重建记录](../../probes/sherpa-runtime-materials/ort-nested-composition-20261115/patch-reconstruction.json)及原始 patch/重建 diff 均保留，未编译这些重建源码。

| 组件 | patch 文件 | 改变文件 |
| --- | ---: | ---: |
| Abseil | 2 | 6 |
| Protobuf | 3 | 5 |
| date | 1 | 1 |
| GSL | 1 | 1 |
| FlatBuffers | 1 | 2 |
| ONNX | 1 | 2 |
| Eigen | 2 | 4 |

Abseil 在 non-Windows 且 Patch_FOUND 时仍声明应用两份 CUDA 命名补丁，不能因 Android/CUDA OFF 称其源码未改。Eigen 的 `s390x-build.patch` 串联六份邮件补丁，另一 werror patch 的 `---` 行含 `.orig<TAB>timestamp`；首父脚本错误取旧路径而失败，改按 `+++` 的真实目标去 timestamp 后恢复，没有重跑其余六组件。此发现已记录到共享 pitfalls。

四份原日志仅直接显示 `Found Patch`，没有可用的历史逐文件 patch 输出；本地成功重建不等于直接取得 CI post-patch tree、重放原 GNU patch job 或验证最终 Android 机器码效果。逐组件变更/平台守卫见交付的 [UPSTREAM-MODIFICATIONS.md](../../android/backend-sherpa-asr/src/main/assets/ondeviceai/sherpa-asr/notices/native/UPSTREAM-MODIFICATIONS.md)。

## 实际产物验收

- AAR/lint/五 SDK 隔离 Maven 发布：10.257 秒、exit 0，435 tasks 中 29 执行。两个新 AAR 的非通知成员与各自构建前快照逐字节相同，63 项通知经 Python/Info-ZIP 两读一致；隔离 Maven Release AAR 与保存的 Release AAR 全字节相同。
- 独立最终 APK：复用已验三文件 application fixture，不改夹具；6.279 秒、exit 0，35 tasks 全执行。`verifyNoticeConsumer` 拒绝非根 project component，实际 resolver 选中本次隔离 Maven SDK 和原固定 runtime。
- APK 内 63 项通知逐字节同来源，四 ABI/16 SO 全字节同原 runtime AAR；分别对 Darts、修改说明、根索引、新 JSON attribution、新子索引做删除/同长改单字节，共 10 负例均拒收，原 APK 摘要不变。
- 原 SDK XML 版本警告及不能再次 strip 的四库提示原样保留；不把构建通过当安装、推理、质量、资源或发布接受。

| 产物 | 字节 | SHA-256 |
| --- | ---: | --- |
| Release SDK AAR | 219809 | `188a558c72e89abd4cee7ca2e411527a7d4d88b0961b16ce415875f3bbe964ab` |
| 独立 Maven APK | 128075591 | `88a208e7a585a6497020b1d0126369ed08cd3d17bb20178eba8aa798fefa3858` |

产物路径、实际摘要与负例收据以材料中的 `aar-verification.json`、`apk-verification.json` 为准。构建输出在 `artifacts/verification/w3-ort-nested-composition-20261115/`；原始请求、输出和失败在 `.omx/artifacts/w3-ort-nested-composition-20261115T122051Z/`。

## 复核与剩余边界

免费 hy3 三项候选分别 66.897/54.700/24.628 秒，均完整 success、无已观察真实工具事件、无配额错误。工具 init 列表不等于权限隔离证明。通知说明中“不会改变 AAR/build output”的首候选措辞已拒用，以单独的 hy3 修正原文替换，原候选/原结果保持。

另一次 hy3 限定供应文本技术复核 54.255 秒完整 success，接受该 bounded increment；一项 MEDIUM 要求双端明确全部验收项仍 open，三项 LOW 要求构建收据不单独作证、限定“未变”的成员范围及保留补丁证据边界，均由本报告/父处置及待发布正文明确落实。原复核见 `bounded-review.json`，父处置见 `review-disposition.json`。该复核未执行工具/验收，也不代表完整法律或生产发布接受。P1 最终生产采用/输入完整性、P2 完整递归组成、P3 全部适用通知和 P4 同一候选完整发布接受仍未齐；R4 真机全量覆盖及 R5/W8 可靠资源/预算保持原缺口，不降低门槛。

173 个原保护文件的原字节/共享原余字节及 161 个冻结文件通过。完整 staged diff-check 的原许可、上游源码/patch 和重建 diff 空白诊断均逐路径核绑定原字节，未格式化原件；其余本轮文字 diff-check 通过，具体见 `diff-check-disposition.json`。

代码已本地提交为 `910ac8c`（99 文件），双端 Issue/Wiki 已逐字回读同步，见 [同步收据](w3-ort-nested-notice-integration-sync-20261115.json)。源码分支未 push。
