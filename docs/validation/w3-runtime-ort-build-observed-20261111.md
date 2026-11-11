# ORT 嵌套 Android 构建出处研究复核（有限范围）

范围：仅观测上游 `csukuangfj/onnxruntime-libs` 的嵌套 ORT Android 构建与归集，未下载任何归档或二进制；本复核未执行任何 build/device/验收，外层 CLI 退出 0 不构成正确性证明，验收由上游/父流程负责。本文件不主张加密级源码→AAR 对应、递归 SBOM、生产就绪、设备验收或 W3 闭合。

来源链接
- 运行：<https://github.com/csukuangfj/onnxruntime-libs/actions/runs/33892225094>
- arm64 job：<https://github.com/csukuangfj/onnxruntime-libs/actions/runs/33892225094/job/101086389995>
- collect job：<https://github.com/csukuangfj/onnxruntime-libs/actions/runs/33892225094/job/101094996702>
- 执行工作流头 commit：<https://github.com/csukuangfj/onnxruntime-libs/commit/bb069649de9edc4689b2c5f9609e5499ae0bee9d>（blob `5a5b7595e53d1aaac298498b829d91d7b98de8fa`）
- tag 工作流 commit：<https://github.com/csukuangfj/onnxruntime-libs/commit/5cc3d2e84d9eade2562cf29a93fa3a520a75ca57>（blob `7919f6b338f7e8991684d2bb45056475fd77acb3`）
- Release asset：<https://github.com/csukuangfj/onnxruntime-libs/releases/download/v1.28.2/onnxruntime-android-1.28.2.zip>

## 1. 实际 arm64 来源与构建旗标（仅 arm64 构建日志审阅）
- 来源 commit `33ca9628233…` = ORT `v1.28.2`，与既有 provenance 中 AAR 自报 `git-commit-id=33ca962`/`VERS_1.28.2` 相符（arm64 日志 L370）。其余 3 ABI 构建日志未审阅，不推广完整 source commit/config。
- build.py 实际参数（L452）：`--use_nnapi --android_abi arm64-v8a --android_api 27 --enable_lto --build_shared_lib --skip_submodule_sync --build_java --cmake_generator Ninja --config Release`。
- 实际 CMake 行（L455）含 `-Donnxruntime_USE_NNAPI_BUILTIN=ON -Donnxruntime_USE_MIMALLOC=OFF -Donnxruntime_ENABLE_LTO=ON`，且 DNNL/TENSORRT/ACL 等大量 OFF。**USE_MIMALLOC=OFF 因实际 CMake 行存在而成立（仅 arm64 实测）。**

## 2. Release ZIP asset 元数据
- `onnxruntime-android-1.28.2.zip`，asset id `544593317`，size `33765715`，digest `sha256:01518867…`，`created 2026-09-04T16:24:05Z`。此为发布元数据，非源码/构建证明。

## 3. arm64 工件上传/下载摘要匹配
- 上传（arm64 L7348）：`4a63146d05fe…`；下载（collect L194）：`4a63146d05fe…`——一致，artifact id `9945299009`。其余 ABI 下载摘要：armeabi `3bfd9d59…`、x86 `ea012b74…`、x86_64 `a184e6e9…`。

## 4. 4 个最终 SO 大小印证 vs 缺失成员哈希证明
- 最终 ZIP 成员大小（collect L477–486；arm64 `22249560`、x86_64 `25581120`、armeabi `15359592`、x86 `26491148`）与 `matchingSOFileSizes` 一致。
- 但 `sizeMatchIsNotDigestEquality=true`：归集仅记录大小，未给最终 ZIP 逐成员 SHA；arm64 工件 ZIP 摘要匹配的是工件 ZIP，非最终发布 ZIP 的逐成员哈希。**故为大小印证，非成员摘要等价证明。**

## 5. 1.26.0 AAR 回退后 4 SO 覆盖
- collect L306：`onnxruntime-android-1.26.0.aar` → `onnxruntime-android-1.28.2.aar`（主 wget 失败，回退 Maven 1.26.0）。
- 随后 L359/373/387/401 将 `/tmp/{abi}/libonnxruntime.so` 覆盖进 `./jni/{abi}/`，最终 ZIP 即「1.26.0 AAR 结构 + 1.28.2 自编译 .so」。

## 6. tag 工作流与执行头差异（已纠正两者互换）
- tag 工作流（commit `5cc3d2e84…`，blob `7919f6b338…`）：默认 `1.27.1`、触发分支 `release-v1.27.1-2`、`upload-release overwrite:false`。
- 执行工作流头（commit `bb069649…`，blob `5a5b7595e5…`）：默认 `1.28.2`、触发分支 `test-ci`、`overwrite:true`。
- 实际运行以 `ONNXRUNTIME_VERSION=1.28.2` 执行；参数须取自实际运行头/日志，而非当前 tag。collect L501 `overwrite:true` 与执行头一致，无矛盾（已移除原误报的 overwrite 矛盾）。

## 7. 成功 collect job 掩盖失败 HF 镜像上传 + 空重试
- collect L609：`[remote rejected] main -> main (pre-receive hook declined)`——首次 HF 推送被拒。
- 重试（L625–628）：`mv: cannot stat '../onnxruntime-android-1.28.2/headers'`、`.../jni` 等——源已缺失。
- L632/636 `nothing to commit`、L637 `Everything up-to-date`、L638 `Command completed after 2 attempt(s)`——重试提交为空。
- 故镜像 `csukuangfj2/android-onnxruntime-libs` 当前仅有 `.gitattributes`（无 digest），collect 整体“成功”掩盖了 HF 镜像未更新。

## 边界（保留上游窄补丁范围）
上游 patch 仅 `sed -i.bak '/SOVERSION/d' ./cmake/onnxruntime.cmake` 与 `sed -i.bak '/onnxruntime PROPERTIES VERSION/d'`（移除 SOVERSION/VERSION 属性），范围窄；不据其推断更多改动，亦未据其主张 final AAR 无其他补丁。4 ABI 替换已观测，但仅 arm64 构建日志/配置被审阅，不将完整 source commit/config 推广至其余 ABI。未主张源码→AAR 字节对应、递归 SBOM、生产就绪、设备验收或 W3 闭合；构建/归集观测本身不能加密级证明这些二进制进入 exact Sherpa AAR。attestation 查询端点 404（未验证，无签名出处证明）。

## 裁定
- 观测支持的有界主张：arm64 来源=ORT v1.28.2（与既有 AAR 自报相符，仅 arm64 审阅）；构建旗标 NNAPI/API27/LTO/USE_MIMALLOC=OFF（仅 arm64 实测 CMake 行）；asset 元数据；arm64 工件上传=下载摘要一致；4 SO 大小印证；1.26.0 回退+4 SO 覆盖；执行头≠tag；collect 成功掩盖 HF 失败。
- 保留的异常/矛盾：(1) HF 推送被拒+空重试却 job 整体成功，镜像仅余 `.gitattributes`。
- 非矛盾澄清：最终发布 ZIP 摘要 `01518867…` 不同于 arm64 工件 ZIP `4a63146d…` 属不同容器，非矛盾；但缺最终 ZIP 逐成员 SHA 仍是证据边界。
- 剩余最小证据：最终 ZIP 逐成员 SHA（现仅大小）；1.26.0 Maven AAR 真实性及其 .so 覆盖成发布 asset 的确认；attestation 查询端点 404（未验证，无签名出处证明）；递归静态组成未建立。以上不闭合 W3。
