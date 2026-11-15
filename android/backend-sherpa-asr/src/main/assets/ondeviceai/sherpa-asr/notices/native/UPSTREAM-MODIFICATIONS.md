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
