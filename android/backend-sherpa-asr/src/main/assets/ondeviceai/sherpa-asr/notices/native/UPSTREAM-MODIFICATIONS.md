# Upstream modifications known to this SDK

This statement records only the supplied changed-file evidence for the fixed Sherpa 2.0.1 AAR (SHA-256 `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`). It is not a legal approval, does not decide version/SONAME behavior or legal sufficiency, and does not alter any original license or attribution. The SDK addition copies the known notices plus this statement; no runtime patch is applied, and no runtime AAR, Kotlin, dependency, model, global config, signing, or device setting is changed by this task.

## Sherpa ASR 2.0.1

### `sherpa-onnx/csrc/version.cc`
Source revision `af2d8d81dbfa61c4cea4c568da78f3982edca005`. Source blob `fad4f4608123d9fe30c9e2543f79952dd403a5fd`; reconstructed observed patch blob `149297a10a8ba54619e57615d844bbf5c7b21b1b` (SHA-256 `c4addca669aa59aa7aef9d8862acc761d94d3a36eb26ab0d0ea60eb5a41e106d`). Both official-job diff prefixes matched in repository-relative logs `.omx/artifacts/w3-runtime-materials-20261111/official-provenance/job-logs-output-corrected/android-libs.log` (lines 480-498) and `.omx/artifacts/w3-runtime-materials-20261111/official-provenance/job-logs-output-corrected/android-aar.log` (lines 481-499). Recorded changes exactly:
- `static const char *date = "Mon Oct 26 21:50:15 2026";` -> `static const char *date = "Mon Oct 26 14:57:12 2026";`
- `static const char *sha1 = "96255a1b";` -> `static const char *sha1 = "af2d8d81";`
Scope: recorded tracked version/date changes only; no claim of absence of other or generated input changes.

### simple-sentencepiece v0.7
Tree `62dd423df1d51da5ea06f1c3a046fc04f01b4f39`; source archive SHA-256 `1748a822060a35baa9f6609f84efc8eb54dc0e74b9ece3d82367b7119fdc75af`; reconstructed diff SHA-256 `3cbcc4df51bd9fedf17023ce1b8bb74d7bb0331a10659d2a2eb7819dff7a1b7b`; supplied input `probes/sherpa-runtime-materials/2.0.1-sentencepiece-patch-20261115/android-threading-removal.diff`. The reconstructed diff shows three changed paths:
- `ssentencepiece/csrc/threadpool.h`: the original ThreadPool implementation and notice are replaced by an empty Android/Termux stub comment with include guards.
- `ssentencepiece/csrc/ssentencepiece.h`: removes the `threadpool.h` include, pool initialization, and `std::unique_ptr<ThreadPool> pool_`; constructors change their default from `std::thread::hardware_concurrency()` to `1`.
- `ssentencepiece/csrc/ssentencepiece.cc`: batch `Encode` and `Decode` no longer enqueue work/futures on the pool and instead process items sequentially.

The shown change set leaves original Darts code untouched; retain the original Darts BSD-2-Clause notice unchanged. The original removed ThreadPool notice does not prove which implementation is embedded in the final AAR, and it does not prove the absence of any other threading.

### Evidence limits for Sherpa
All four ABI logs report patch/static compile steps. That is log observation plus reconstructed patch input, not full historical captured patched sources and not exact retained-machine-code proof. A thin AAR, notices, and these logs do not prove final APK recursive composition.

## ONNX Runtime

Source revision `33ca9628233dc8f002435e868d4c2e9f82766ca1`; executed workflow `bb069649de9edc4689b2c5f9609e5499ae0bee9d`. The workflow patch step ran:
- `sed -i.bak '/SOVERSION/d' ./cmake/onnxruntime.cmake`
- `sed -i.bak '/onnxruntime PROPERTIES VERSION/d' ./cmake/onnxruntime.cmake`

The supplied observed four-ABI diff for `cmake/onnxruntime.cmake` shows removal of SOVERSION/comment lines (`#See: ...SOVERSION.html`, `SOVERSION 1`, and the Windows `# Omit the SOVERSION setting in Windows build` comment). The shown multiline `set_target_properties(onnxruntime PROPERTIES ... VERSION ${ORT_VERSION} ...)` remains. Do not summarize this as all target `VERSION` being removed; the second sed pattern matches a single line containing `onnxruntime PROPERTIES VERSION`, and the supplied observed bytes do not show that command changing the shown `VERSION ${ORT_VERSION}` block. Evidence here is the actual workflow command plus the observed diff, not a claim that there are no other modifications.

## Open scope

Complete patch/input inventory, exact retained machine code, recursive composition proof, and P4 remain open. No new downloads or builds were performed by this helper.

## Fixed ORT nested dependency patches (append)

Limited to the supplied nested-patch evidence for source revision `33ca9628233dc8f002435e868d4c2e9f82766ca1` and workflow `bb069649de9edc4689b2c5f9609e5499ae0bee9d`, this section supplements the existing Sherpa/ORT sections without replacing them. This notice integration changes notification assets and the resulting notice-bearing AAR/APK package bytes; it does not modify runtime/library logic, the fixed runtime binary, models, dependencies, or global settings. It does not claim no AAR/build-output changes, legal completeness, exhaustive composition, or full release acceptance.

### Evidence boundary
The supplied inputs report `Found Patch` and matching source/fetch/compile/link observations in actual build logs, but no historical post-patch trees were directly retrieved. The supplied local reconstruction check-then-applied the exact patch files to newly extracted, hash-verified retained source archives and reports exit code 0 for each listed patch result. The reconstruction covers 11 patch files across 7 components and 21 component files. It is not a replay of the original patch job, not compilation or runtime equivalence, and `Found Patch` alone does not directly prove historical apply success. Source-file changes below are not evidence of effective Android machine-code changes; no compiled machine-code effect was verified.

### Declared patches and observed source/build scope
- `abseil_cpp` (2 patch files / 6 source files): original paths `cmake/vcpkg-ports/abseil/absl_cuda_warnings.patch` and `cmake/vcpkg-ports/abseil/absl_cuda13_member_template.patch`. Changes: CUDA warning/parse fixes in `hash.h`, `raw_hash_set.h`, `common.h`, `raw_hash_map.h`, and `btree_container.h`, including `size_t(-1)`, initialized `HashState`, corrected `HashStateBase` friend declarations, pointer seed masking, and a top-level `IfRRefAddPtr` alias used by container macros. Declaration guard: when `Patch_FOUND`, WIN32 applies the Windows patch plus both listed CUDA source patches; non-WIN32, including Android, still applies both CUDA-related source patches if `Patch_FOUND`. CUDA being off does not establish Android source unmodified.
- `protobuf` (3 patch files / 5 source files): original paths `cmake/patches/protobuf/protobuf_cmake.patch`, `cmake/patches/protobuf/protobuf_android_log.patch`, and `cmake/patches/protobuf/protobuf_s390x.patch`. Changes: CMake/MSVC warning suppression list becomes extensible via `${onnxruntime_PROTOBUF_EXTRA_WARNING_DISABLEMENT}`, Android link entries change from `log` to `-llog`, Apple timing moves to `<time.h>` and `clock_gettime_nsec_np(CLOCK_UPTIME_RAW)`, and s390x is excluded from the clang `musttail` guard. Guards are platform-specific; Android source changes are declared, but retained Android machine-code effects are not proved.
- `date` (1 patch file / 1 source file): original path `cmake/patches/date/date.patch`. Change: `CMakeLists.txt` stops forcing `CMAKE_CXX_STANDARD` as a sticky global cache variable so the parent project standard can govern. This is a build-configuration source change only.
- `microsoft_gsl` (1 patch file / 1 source file): original path `cmake/patches/gsl/1213.patch`. Change: under the MSVC-without-Intel/NVCC guard, `GSL_SUPPRESS(x)` stringizes the argument as `[[gsl::suppress(#x)]]`. This is guard-specific header source text, not proof of Android machine-code impact.
- `flatbuffers` (1 patch file / 2 source files): original path `cmake/patches/flatbuffers/flatbuffers.patch`. Changes: `FLATBUFFERS_CXX_FLAGS` appending also adds `-Wno-error=stringop-overflow`, and `TypeCode` receives an AIX+clang packed attribute while retaining explicit bitfield signedness. Guards are build-flag and AIX/clang-specific.
- `onnx` (1 patch file / 2 source files): original path `cmake/patches/onnx/onnx.patch`. Changes: adds `ONNX_MINIMAL_BUILD`; when enabled, the build adds only `data_type_utils.cc`, forces `ONNX_ML` on, disables Python/tests, and skips the full `add_subdirectory(onnx)` path; otherwise the original path remains. It also removes `.Deprecate()` from the GroupNormalization version 18 schema. No Android machine-code effect is inferred.
- `eigen` (2 patch files / 4 source files): original paths `cmake/patches/eigen/s390x-build.patch` and `cmake/patches/eigen/s390x-build-werror.patch`. The declaration applies both via `Patch_EXECUTABLE`. `s390x-build.patch` bundles six mailed patches, not one commit and not merely three headers: `0e0d3fcf4a829391e4d06cf648fb2137816fa483`, `ab2ca76bfa39a0c70504a8a453f32463d525e560`, `255654a39e8f0fcea006652e5ef9b2e58c8e10e0`, `30bffb05324439a1878a5c14b12f13bb723dabba`, `0045fae6306a9a7846f4d5ecb4cfcfa3f8beafa9`, and `9ef6acb9f880e805a86e7152211e7c67ffba58ca`. They cover s390x/ZVector test compilation, big-endian random long double generation, `pround`, `vec_xl`/`vec_xst`, march warning form, and compound-literal warnings. The werror patch comments out an unused `p4f_MZERO` definition. Source guards target ZVector, s390x, big-endian, and z15/vec_float conditions; no Android machine-code effect is proved.

### Limits
The above records declared paths, observed source/build-level changes, guards, and local reconstruction only. It does not prove exhaustive upstream inputs, final retained artifacts, recursive composition, legal sufficiency, full P2/P4 closure, or any release decision.
