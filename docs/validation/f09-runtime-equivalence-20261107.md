# F09 运行时等价性证据备忘（20261107）

日期 2026-11-07。范围：评估 OLD 已验收固定图像/视频推理制品（20261105，`bd41785d…`）与 NEW 实际远端交付消费者制品（20261107，`dbafbc31…`）之间的运行时等价性。比对由父进程本地哈希完成，结果见 `artifacts/verification/f09-runtime-equivalence-20261107/comparison.json`。本备忘仅基于既有证据撰写，未运行任何测试/构建/推理。

## 1. 制品级文件比对（来自 comparison.json）
- `oldManifestSHA256` = `bd41785dde0cccf0c26f58d1abb363ceac2e9e52e822c5efb929e97585097efc`
- `newManifestSHA256` = `dbafbc31d118d3962fba69a5b27e9bc2c4acfdc68d72ed516f7ac663920dbf3d`
- 计数：`oldCount` 339 → `newCount` 341；`identicalCount` 338。
- 差异：`different`=1（`notices/LOCAL-VALIDATION-ONLY.txt`：old `33da6691…` 306B → new `5f9c96dc…` 324B）；`newOnly`=2（`notices/ondevice-ai-kit/LICENSE` `c224aafd…` 1930B；`notices/ondevice-ai-kit/LICENSE-PROVENANCE.json` `34a2d390…` 58188B）；`oldOnly`=[]。
- `manifestDiffs` 仅位于 `/notices` 与 `/sourceAttributions`（4 个 ODAIKit* 模块 license 字段由 “Project license unspecified; local validation only” 改为 “See notices/ondevice-ai-kit/LICENSE”），与新增 LICENSE 文件一致，属允许边界。

## 2. 全量类别确认（均字节一致，来自 comparison.json oldTree/same）
338 个 identical 文件共 135045627 B，归并为以下类别（目录/模块计数与文件计数分别列出，避免混用）：
- **xcframeworks**：20 个目录 / 260 identical 文件 / 129617892 B（ComplexModule, Crypto, EventSource, Hub, HuggingFace, InternalCollectionsUtilities, Jinja, MLX, MLXLMCommon, MLXNN, MLXOptimizers, MLXVLM, Numerics, ODAIKitAppleMLXVLM, ODAIKitCore, ODAIKitModelStore, ODAIKitMultimodal, OrderedCollections, RealModule, Tokenizers）。
- **CModules**：4 个模块 / 37 identical 文件 / 438924 B（Cmlx, cLogSupport, _NumericsShims, yyjson）。
- **resources**：18 个文件 / 4863570 B（device 9 + simulator 9）。
- **provenance**：2 个文件 / 8649 B（`provenance/device.json` `e5d2c4cd…`、`provenance/simulator.json` `53416faf…`）。
- **notices**：21 identical 文件 / 116592 B；另有 1 改变 + 2 新增（见 §1）。
- **运行时/非 notice 子集**：317 文件 / 134929035 B（= 338 文件 135045627 B − notices 21 文件 116592 B）。
结论：全部偏差收敛于 notices / sourceAttributions；xcframeworks / CModules / resources / provenance 路径均字节一致。

## 3. ConsumerApp.swift 差异（关键边界）
- OLD（推理 harness）含 `BinaryInferenceProbe.run()`，实际执行 install→acquire→generate 图像+视频推理。OLD报告 顶层字段精确为：`status: "passed"`、`phase: "complete"`、`scope: "binary-fixed-image-video-inference"`、`unverified: [quality, performance, minimumOS, release, cancelReentry]`；`modelId: "mlx-community/SmolVLM2-500M-Video-Instruct-mlx"`、`modelRevision: "fa57db46815177fbdfd65cc85a2b3416a8332268"`、`runtimeRevision: "6e338e5df49424fc0db7c6d58184d622f92ed18a"`。
- 报告**无**顶层 `backendId` 字段，也**无**顶层 `identityMatched`。二者均为 `runs[]` 内每一条 run 的字段：image 与 video 两条 run 各自携带 `backendId: "mlx.smolvlm2-500m"`、`identityMatched: true`、`status: "passed"`、`output` 文本字符串。`identityMatched` 为模型/bundle 身份布尔，**非面容/人脸身份断言**，本备忘不对其作人脸解读。
- NEW（仅资源）：`@main` 仅调用 `resourceSmoke()` 并持久化资源报告，无 `BinaryInferenceProbe` 调用，`artifactManifestSHA256`=`dbafbc31…`；远端验收记录 明载"未执行模型推理"。
- 比对本身**不证明** NEW 消费者执行过模型推理。

## 4. 可前推 / 仍绑定的运行时证据
- **字节绑定解决的是"NEW SDK/runtime 代码是否需要重新推理验证"**：OLD 已验收固定推理制品所针对的 SDK/runtime 字节（260 xcframework 文件、37 CModule 文件、18 resource、2 provenance，SHA 与字节均一致）现已逐字节交付 NEW 远端消费者。因这些字节与 OLD 完全相同，OLD 对这些字节的运行行为（模型 bundle 查找、设备 Metal 库加载、图像+视频生成、`runs[].identityMatched` 模型身份匹配）可在"相同 SDK/runtime 字节"前提下前推，**无需为这些字节对 NEW 重跑推理**。
- **此绑定并未执行任何 NEW 消费者推理**：NEW 消费者仅复验资源存在/Metal 加载（资源级 smoke），OLD报告 的 `resources.inference` 字段亦为 `"not-run"`。要主张 NEW 消费者执行推理，仍需对 NEW 制品跑一次镜像 OLD `BinaryInferenceProbe` 的推理。OLD 报告中 quality/performance/minimumOS/release/cancelReentry 仍 unverified；集成缺口、法律许可批准、发布复审各自独立。

## 5. 已知缺口与边界
- 不主张：整个制品逐字节等价（3 份 notice 文件不同）；无 manifest 不一致（两份 manifest SHA 预期不同，属 notices/sourceAttributions 范围）；发布就绪；法律批准；无集成缺口；或 NEW 已推理。
- 精确未变字节（来自 comparison.json oldTree/same，已汇总，非估算）：338 identical 文件共 135045627 B，其中运行时/非 notice 子集 317 文件 134929035 B。差异字节精确为 306 / 324 / 1930 / 58188 B（见 §1）。

## 6. 接受检查清单
- [PASS] old/new manifest SHA 精确记录（`bd41785d…` / `dbafbc31…`）。
- [PASS] 339→341、338 identical、1 changed、2 added、0 old-only（与 comparison.json 一致）。
- [PASS] 全量类别字节一致：xcframeworks 260 文件/129617892 B、CModules 37 文件/438924 B、resources 18 文件/4863570 B、provenance 2 文件/8649 B、notices 21 文件/116592 B；合计 338 文件 135045627 B。
- [PASS] manifestDiffs 仅 notices/sourceAttributions。
- [PASS] OLD 推理报告（`old-report.json`）image/video 两 run `status: "passed"`，`runs[].backendId` 均为 `mlx.smolvlm2-500m`，`runs[].identityMatched` 均为 `true`（逐运行字段，非顶层）。
- [UNVERIFIED] NEW 消费者执行过模型推理——比对未见，资源 smoke 不含推理。
- [UNVERIFIED] NEW 消费者推理等价——需独立全新推理运行。
- [UNVERIFIED] 质量/性能/minimumOS/发布/取消重入/法律批准/集成。

引用：`artifacts/verification/f09-runtime-equivalence-20261107/comparison.json`；OLD manifest `artifacts/verification/f09-binary-inference-20261105/consumer/artifact-manifest.json`；NEW manifest `artifacts/verification/f09-remote-consumer-20261107/consumer/artifact-manifest.json`；OLD source `artifacts/verification/f09-binary-inference-20261105/consumer/ConsumerApp.swift`；NEW source `artifacts/verification/f09-remote-consumer-20261107/consumer/ConsumerApp.swift`；OLD报告 `artifacts/verification/f09-binary-inference-20261105/report.json`；远端验收记录 `docs/validation/f09-remote-consumer-20261107.md`；`docs/agent-pitfalls.md` §1,§10,§11。
