# ORT 源码/声明依赖与公告边界映射（有界，非正式 SBOM）

- **sourceCommit**: `33ca9628233dc8f002435e868d4c2e9f82766ca1`
- **输入 run**: https://github.com/csukuangfj/onnxruntime-libs/actions/runs/33892225094
  - arm64 job: https://github.com/csukuangfj/onnxruntime-libs/actions/runs/33892225094/job/101086389995
  - collect job: https://github.com/csukuangfj/onnxruntime-libs/actions/runs/33892225094/job/101094996702
- **范围**: 14 条 arm64 Fetch 行 = 13 源码依赖 + 1 个 Linux x86_64 host protoc 工具；仅声明/公告映射，**非**最终保留二进制成员。
- **源码声明（固定 commit 链接）**:
  - `cmake/deps.txt`: https://github.com/microsoft/onnxruntime/blob/33ca9628233dc8f002435e868d4c2e9f82766ca1/cmake/deps.txt
  - `cmake/external/onnxruntime_external_deps.cmake`: https://github.com/microsoft/onnxruntime/blob/33ca9628233dc8f002435e868d4c2e9f82766ca1/cmake/external/onnxruntime_external_deps.cmake
- **公告（固定 commit 链接）**: `ThirdPartyNotices.txt`: https://github.com/microsoft/onnxruntime/blob/33ca9628233dc8f002435e868d4c2e9f82766ca1/ThirdPartyNotices.txt
- **本映射状态**: `productionReady=false`、`exhaustiveRecursiveCompositionVerified=false`、`sourceToAARVerified=false`；所有 `archiveDigestVerifiedHere=false`、`finalBinaryMembershipVerified=false`。无自行声明的法律审批。

## 14 行映射表

| # | observedName | role | depsKey | depsL | logL | noticeHeading | 公告行 | mapping | 声明SHA1(前8) | URL |
|---|---|---|---|---|---|---|---|---|---|---|
|1|abseil_cpp|source|abseil_cpp|12|868|abseil-cpp|2841-3046|component|a9eb1d64|abseil/abseil-cpp/archive/refs/tags/20250814.0.zip|
|2|re2|source|re2|54|926|google/re2|2240-2268|component|646e1728|google/re2/archive/refs/tags/2024-07-02.zip|
|3|protoc_binary|host|protoc_linux_x64|46|928|protocolbuffers/protobuf|92-128|component|33846200|protocolbuffers/protobuf/releases/download/v21.12/protoc-21.12-linux-x86_64.zip|
|4|Protobuf|source|protobuf|43|941|protocolbuffers/protobuf|92-128|component|7cf27339|protocolbuffers/protobuf/archive/refs/tags/v21.12.zip|
|5|date|source|date|15|1009|HowardHinnant/date|2075-2108|component|2dac0c81|HowardHinnant/date/archive/refs/tags/v3.0.1.zip|
|6|mp11|source|mp11|36|1025|Boost|2327-2353|generic-Boost|9bc9e01d|boostorg/mp11/archive/refs/tags/boost-1.82.0.zip|
|7|nlohmann_json|source|json|32|1037|nlohmann/json|3075-3098|component|5e887951|nlohmann/json/archive/refs/tags/v3.11.3.zip|
|8|pytorch_cpuinfo|source|pytorch_cpuinfo|53|1039|pytorch/cpuinfo|4419-4452|component|e58d4b47|pytorch/cpuinfo/archive/4628dc060ce4e82345dc166bbac875609db4ff69.zip|
|9|GSL|source|microsoft_gsl|33|1040|Microsoft GSL|1040-1063|component|1094e3bb|microsoft/GSL/archive/refs/tags/v4.2.1.zip|
|10|safeint|source|safeint|55|1041|dcleblanc/SafeInt|3101-3124|component|23f25204|dcleblanc/SafeInt/archive/refs/tags/3.0.28.zip|
|11|flatbuffers|source|flatbuffers|25|1053|google/flatbuffers|2563-2768|component|59422c3b|google/flatbuffers/archive/refs/tags/v23.5.26.zip|
|12|onnx|source|onnx|40|1140|onnx|208-414|component|2b2cd58a|onnx/onnx/archive/refs/tags/v1.22.0.zip|
|13|Eigen3|source|eigen|24|1180|Eigen|417-799|component|05b19b49|eigen-mirror/eigen/archive/1d8b82b0740839c0de7f1242a3585e3390ff5f33/eigen-1d8b82b0740839c0de7f1242a3585e3390ff5f33.zip|
|14|kleidiai|source|kleidiai|62|1184|KleidiAI|6051-6121|component|6895e72b|ARM-software/kleidiai/archive/refs/tags/v1.20.0.tar.gz|

（SHA1 为 deps.txt 声明字符串，仅显示前 8 位；完整值见 [机器映射](../../probes/sherpa-runtime-materials/2.0.1-ort-dependency-map-20261111/dependency-notice-map.json)。表格 URL 省略 `https://github.com/` 前缀以适配长度。）

## 关键边界说明

- **通用 Boost / mp11 边界**：mp11 映射到通用 `Boost` 公告段落（行 2327-2353，`generic-Boost-section-not-package-specific`）。该段未指明 mp11 的具体版本/文件，故**不是** mp11 的归属或完整性证明；仅证明公告中存在 Boost 许可文本。
- **精确 Eigen commit 与 Sherpa 5.0.1 边**：Eigen 必须用精确 commit `1d8b82b0740839c0de7f1242a3585e3390ff5f33`（deps.txt 行 24）。**不得**臆测为 3.4.0/5.0.1。Sherpa 侧 Eigen 5.0.1 与 ORT 嵌套 Eigen 是两条独立边，URL/commit 不可互相复制。
- **mimalloc 公告存在 ≠ 运行时包含**：实际 arm64 配置 `onnxruntime_USE_MIMALLOC` 关闭（external-deps.cmake 行 96 起为条件块），这 14 行 Fetch 中无 mimalloc。通用 `ThirdPartyNotices.txt` 含 mimalloc 文本，仅说明上游公告内容，**不构成**本次 arm64 构建的运行时包含证明。
- **条件化补丁逐条（修正）**：protobuf（L187-193）与 flatbuffers（L456-460）的 `PATCH_COMMAND` 受 `Patch_FOUND` 守卫（未找到则置空）；date（L277-278）与 GSL（L410-412）在审阅声明中含 `PATCH_COMMAND` 但**无** `Patch_FOUND` 守卫。源码存在或 `FoundPatch` 均**不证明**历史 job 中补丁成功应用；`finalBinaryMembershipVerified=false`。
- **mp11 无显式 URL_HASH 不证明无校验**：mp11 声明（external-deps.cmake 行 289-295）缺失 `URL_HASH SHA1=…` 参数，但**不能**由此推断全仓任何位置均无校验。
- **host protoc 与运行时 Protobuf 分离**：`protoc_binary`（protoc_linux_x64，行 46/150）是 Linux x86_64 **host 构建工具**，非运行时源码库；运行时 `Protobuf`（行 43/196）单独计为源码依赖。二者公告同段但角色不同。

## caveats（摘要）

- 13 源码依赖 + 1 host 工具，非 14 运行时库。
- SHA1 为 deps.txt 声明字符串，本任务未下载/重校验任何归档。
- 非穷举递归依赖图、非最终静态 SBOM、非法律背书、非 source-to-AAR 等价、非 W3 闭合。
- 本文件为有用的「有界源码/公告映射」，非生产就绪判定。
