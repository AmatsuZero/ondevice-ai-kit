# W3 Android NDK 与 C++ 运行时来源增量核验

## 结果
据 8 条实际命令与 2 条 arm64 产物注记固定两条 NDK/Clang 输入路线。W3 未决工作全部仍在范围内；这是增量源码证据结果，非 W3 完成。无新构建、设备调用或生产改动，范围未扩至发布接受。

## 输入路线
| 组件 | NDK/Clang/API | legacy |
|---|---|---|
| ORT | 27.3.13750724/r27d；Clang 18.0.4/clang-r522817d；android-27/MIN_SDK=27（log 455、493-494） | `ANDROID_USE_LEGACY_TOOLCHAIN_FILE=false`；跳过 legacy include-and-return；`-stdlib=libc++` 非最终实现证明。 |
| Sherpa | 30.0.16248370/r30；Clang 21.0.0/clang-r574158c；android-21（log 778/1577/2338/3133） | 未传该变量；toolchain 默认 legacy true；其他 cache 未提供，不得称显式禁用。 |

`SHERPA_ONNX_LINK_LIBSTDCPP_STATICALLY=OFF` 见 arm64、armeabi-v7a、x86_64；x86（3133）仅无显式 OFF。该开关受 `NOT BUILD_SHARED_LIBS AND CMAKE_SYSTEM_NAME STREQUAL Linux` 守护，观察为 Android 且 `BUILD_SHARED_LIBS=ON`，属 Linux 静态 GNU libstdc++/libgcc 分支，与 Android STL（NDK 的 libc++/libc++abi/unwind）无关；OFF 不证明动态 libc++ 或无静态 C++。

## 产物注记
精确成员：`jni/arm64-v8a/libonnxruntime.so` = `API27/r27d/13750724`；`jni/arm64-v8a/libsherpa-onnx-jni.so` = `API21/r30/16248370`。ORT `.comment`：编译器 18.0.4、LLD 18.0.4、指纹 `d8003a456d14a3deb8054cdaa529ffbf02d9b262`；`libsherpa-onnx-jni.so` 的 `.comment` 缺失，readelf 警告保留。`sourceDecoderVerifiedAgainstOfficialTreeBlob=true`、`sectionLocatorUsesSameReferenceLLVM=true` 仅证官方树 blob 验真与同一参考 LLVM 定位；上游 Python 注记解码器不得称为完整独立解析器。

## 来源与边界
父端核验：5 个日志哈希、30 个 Git tree 对象、69 条源码路径保留记录汇入 61 种唯一字节序列。LLVM 基础、manifest、Android patch repo 分层；75/59 仅为列出引用数，不等于应用或最终运行时证明。8 棵官方运行时库树、4 对 base LICENSE 逐字节相等（libcxx、libcxxabi、libunwind、compiler-rt）、两版 130424 字节 clang NOTICE 均为输入材料，未集成资产；whole-toolchain NOTICE 过宽，不自动等同应用全部适用 notice。来源投影 JSON 见链接。

## 阻塞与下一步
63 个现有生产 notice 资产未动；包检查仍是历史证据。R4、R5、P1-P4、W3 均未关闭。下一步需历史 CMakeCache、link.txt/response files、最终 SO/AAR/Maven/POM/源码清单，以及所有构建者的实际 cache/link/retention 与运行时相关 patch/notice 适用性证据；无需重复 x86 命令。

## 证据链接
- [toolchain-matrix.json](../../probes/sherpa-runtime-materials/ndk-runtime-provenance-20261115/toolchain-matrix.json)
- [source-composition-projection.json](../../probes/sherpa-runtime-materials/ndk-runtime-provenance-20261115/source-composition-projection.json)
- [elf-notes-decoded.json](../../probes/sherpa-runtime-materials/ndk-runtime-provenance-20261115/elf-notes-decoded.json)
- [guard-review.json](../../probes/sherpa-runtime-materials/ndk-runtime-provenance-20261115/guard-review.json)
- [material-manifest.json](../../probes/sherpa-runtime-materials/ndk-runtime-provenance-20261115/material-manifest.json)

hy3 guard 46.385s 全文成功、候选草稿为纯文本；父端执行另计。