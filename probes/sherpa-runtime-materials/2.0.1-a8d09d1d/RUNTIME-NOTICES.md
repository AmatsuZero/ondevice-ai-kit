# Sherpa-Onnx 运行时许可材料目录

## 1. 已完成的物料范围、计数与二进制绑定

- 运行时：`sherpa-onnx` v2.0.1；AAR SHA256 `a8d09d1d…ee7`；源码提交 `af2d8d81…ca005`。
- 支撑材料共 **49 份**：20 份许可/声明文本 + 26 份构建源文本 + 2 份 AAR 清单 + 1 份 [manifest](material-manifest.json)；manifest 仅登记上述支撑材料哈希，不含本目录文档自身。另附本 RUNTIME-NOTICES.md，便携文件总计 **50 份**。
- **Sherpa 构建/源文本 = 19 件**（初始 13 + 补充 5 + hclust 1）。
- **既有 Sherpa 声明存在**（[LICENSE](licenses/sherpa-onnx/LICENSE)），并非缺失。其文本同时作为构建源条目 [source-evidence/sherpa-onnx/LICENSE](source-evidence/sherpa-onnx/LICENSE) 收录，字节与上游 GitBlob `d6456956…` 一致，属同源第二登记，非新增许可。
- 二进制绑定见 [aar-members.json](aar-members.json) 与 [aar-composition.json](aar-composition.json)：**26 个 ZIP 条目（含目录）、21 个非目录文件、16 个 `.so` 跨 4 ABI**（arm64-v8a / armeabi-v7a / x86 / x86_64）。4 个 arm64 库均已知：`libonnxruntime.so`、`libsherpa-onnx-{jni,c-api,cxx-api}.so`。
- 动态 `NEEDED`/`SONAME`/`build-id` 已登记；未提供的 build-id 为 `null`（见 [aar-composition.json](aar-composition.json) 的 `gnuBuildId`）。**静态内嵌组件与"源码↔预构建"对应关系未核实**。

## 2. 组件表（源引用 / 相对许可链接 / 证据限定）

"源支持构建候选"指该文本对应上游源码分支；各 `.so` 库成员与哈希已在 [aar-members.json](aar-members.json) 中列举（成员资格已观测），但源码候选的确切版本、静态内嵌组件与预构建的对应、以及"源码↔预构建"溯源（`sourceToPrebuiltProvenanceVerified=false`）尚未核实。许可条款以链接原文为准，不作改写。

- **onnxruntime** v1.28.2 — [LICENSE](licenses/onnxruntime/LICENSE)（MIT）+ [ThirdPartyNotices.txt](licenses/onnxruntime/ThirdPartyNotices.txt)。上游文本，非本 AAR 实际内嵌组件的已验证清单。
- **kaldi-native-fbank** v1.22.4 — [LICENSE](licenses/kaldi-native-fbank/LICENSE)（Apache-2.0，与 sherpa/kaldi-decoder 同源字节）。
- **utf8proc** v2.12.0 — [LICENSE.md](licenses/utf8proc/LICENSE.md)。含**两段 MIT 声明 + Unicode 数据许可声明**（Unicode 条款），勿简化为单一 MIT。
- **kaldi-decoder** v0.3.0 — [LICENSE](licenses/kaldi-decoder/LICENSE)（Apache-2.0）。源码含 **kaldifst 与 Eigen** 传递依赖；二者文本已单列。
- **openfst** v1.8.5-2026-07-09 — [COPYING](licenses/openfst/COPYING)。
- **simple-sentencepiece** v0.7 — [LICENSE](licenses/simple-sentencepiece/LICENSE)。
- **nlohmann/json** v3.12.0 — [LICENSE.MIT](licenses/json/LICENSE.MIT)。
- **kissfft** `febd4c…` — [COPYING](licenses/kissfft/COPYING)（BSD-3-Clause）+ [BSD-3-Clause](licenses/kissfft/LICENSES/BSD-3-Clause) + [Unlicense](licenses/kissfft/LICENSES/Unlicense)。Unlicense 为上游备选，**非整体再许可**。
- **kaldifst** v1.8.0 — [LICENSE](licenses/kaldifst/LICENSE)（Apache-2.0）；构建见 [source-evidence/kaldifst/cmake/openfst.cmake](source-evidence/kaldifst/cmake/openfst.cmake)。
- **hclust-cpp** 2026-02-25 — [LICENSE](licenses/hclust-cpp/LICENSE)。**两段式（源码/二进制）再分发条件**，BSD-2 风格；标签不确定时以原文为准。
- **eigen** 3.4.0（`3147391d`）— [COPYING.MPL2](licenses/eigen/COPYING.MPL2)（主许可）+ [COPYING.README](licenses/eigen/COPYING.README)（说明主要 MPL2，部分文件 BSD/LGPL）+ [COPYING.BSD](licenses/eigen/COPYING.BSD)、[COPYING.MINPACK](licenses/eigen/COPYING.MINPACK)、[COPYING.GPL](licenses/eigen/COPYING.GPL)、[COPYING.LGPL](licenses/eigen/COPYING.LGPL)。**后两者为保留的源备选，非本预构建含 GPL/LGPL 代码或新增产品许可义务之证明**。
- **sherpa-onnx** v2.0.1 — [LICENSE](licenses/sherpa-onnx/LICENSE)（Apache-2.0）；既有声明已存在。

构建源与覆盖（均见 [manifest](material-manifest.json) 的 `buildSourceEvidence`）：[build-android-arm64-v8a.sh](source-evidence/sherpa-onnx/build-android-arm64-v8a.sh)、[onnxruntime.cmake](source-evidence/sherpa-onnx/cmake/onnxruntime.cmake)、[android.yaml](source-evidence/sherpa-onnx/.github/workflows/android.yaml)、[fdroid-reproducible-aar.yaml](source-evidence/sherpa-onnx/.github/workflows/android-fdroid-reproducible-aar.yaml) 等。这些是**源默认/环境覆盖，非已验证的 AAR 构建调用**；外部 ORT `lib/include/root/version` 覆盖存在。ROOT CMake `WEBSOCKET` 默认 ON，仅 `BINARY OFF` 不构成现有 AAR 静态排除之证明；websocket/asio 等未追踪分支标注为**未决**。fdroid 工作流"not byte-reproducible"仅指该参考工作流，**非断言所有 Sherpa 构建不可复现**。

## 3. 仍存缺口（向上报告）

- 递归组合未穷尽追踪：静态内嵌组件、源码↔预构建对应未核实（[aar-members.json](aar-members.json) 中 `staticTransitiveCompositionVerified=false`；[aar-composition.json](aar-composition.json) scope 文字亦注明静态组合未核实）。
- AAR 实际源码/二进制来源未验证；最终生产分发声明保留未验证（`productionNoticeRetentionVerified=false`）；独立发布评审未接受（`independentReleaseReviewAccepted=false`）。
- 不新设法律批准或专利流程。物料准备有益，但 **W3 仍开放**；不声称生产包已变更（`productionReady=false`）。
- **不发出"全部运行时传递许可已验证"式总体声明**。

## 4. 溯源链接

- [material-manifest.json](material-manifest.json)：49 份支撑材料、哈希与 `blobVerified`/`treeVerified`、scope 说明。
- [aar-members.json](aar-members.json)：26 个 ZIP 条目（`zipEntryCount=26`，含 `directoryEntryCount=5` 目录与 `members=21` 文件），`allAbisEnumerated=true`。
- [aar-composition.json](aar-composition.json)：ELF 动态依赖/SONAME/build-id（部分 `gnuBuildId` 为 `null`），静态组合未验证（scope 文字）。
- 全部许可文本与构建源路径见 [manifest](material-manifest.json) 的 `licenses` / `buildSourceEvidence`。
