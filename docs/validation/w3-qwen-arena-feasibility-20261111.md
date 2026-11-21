# W3 Qwen CPU arena feasibility — 2026-11-11

The fixed source supports a one-variable `EnableCpuMemArena=0` hypothesis through `provider="cpu:<absolute config path>"`. Parent source-fragment checks passed18 assertions; the actual fixed AAR JVM DTO passed4 checks. No recognizer/model/device was started in this investigation. Native consumption, memory savings, Android quality and W3 acceptance remain unverified.

The previous physical FILE run remains consumed. It was stopped by the parent at sampledRSS1703575552B above1610612736B, with0 completed cases and nonterminal counters. This investigation neither retries it nor changes the1.5GiB gate. [Original measurement](w3-qwen-android-file-observed-20261111.md).

## Verified chain and limits

Pinned upstream tag-tree commit is `af2d8d81dbfa61c4cea4c568da78f3982edca005`. Seventeen public source files have URL/size/SHA receipts; fixed AAR SHA is `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`.

- `session.h:43` generic `GetSessionOptions(const T&)` passes `config.num_threads/config.provider` verbatim. The helper did not receive this header; its forwarding gap was real in its supplied context and is closed here by the parent.
- `jni/offline-recognizer.cc:77` reads the Kotlin provider; file constructor validates at544 then constructs at551. `offline-recognizer.cc:72–130` delegates to model validation. `offline-model-config.cc:68–188` permits the Qwen path and returns its validation; Qwen validation checks files/tokenizer/length/sampling, with no provider suffix rejection. This is source evidence, not proof that the Android binary executes those exact lines.
- Qwen's three session options are built at`offline-qwen3-asr-model.cc:126–128`. The constructor's exact CPU flag is false for `cpu:/path`, but its sole use at200 is `!is_cpu_provider_ && IsCudaProvider(provider)`. The second operand is false for a CPU suffix, so CUDA IoBinding stays disabled. The initial partial-read concern was resolved; no provider-flag code fix is needed for this source.
- `session.cc:106–142` splits provider/path; `StringToProvider("cpu")` maps to CPU. `EnableCpuMemArena=0` invokes `DisableCpuMemArena` at266–272. Memory-pattern is independent at257–263; the candidate leaves it at the runtime default. The CPU switch adds no alternate execution provider.
- Actual AAR native JNI/C-API libraries contain both option names and parser strings. Actual `classes.jar` SHA `0ee4991c56c19ef5a8a573b01891193d7298ffe7ebcc68f42b944361d8869d93` exposes provider setters/getters. JDK21 execution round-tripped3 exact provider strings plus the Qwen DTO, with a nonexistent native-library path and no OfflineRecognizer class initialization. These facts do not establish JNI parsing, native option application, source/binary identity or memory reduction.

## Validation and independent review

Parent C++ characterization extracted exact upstream parser, trim/integer parsing, provider mapping, forwarding template, CUDA predicate and option blocks. With explicit file/log/option adapters,18 checks passed under clang++17 mode/`-Wall -Wextra -Werror`. Checks cover independent switches/defaults, invalid value, empty/missing config, exact provider forwarding and CUDA conjunction. No ORT runtime is linked; this is source characterization only.

The first adapter suppressed logging arguments, producing two unused-variable errors. The original failure/source was preserved; the adapter now uses `fprintf`, without modifying extracted upstream fragments. The first system javap had no Java runtime; exact installed JDK21 succeeded. The first JVM runtime selected a nonruntime stdlib artifact; exact `kotlin-stdlib-2.2.20.jar` with `Intrinsics.class` resolved it. No global Java/Kotlin configuration changed. A guessed upstream `offline-recognizer-config.cc` returned404; fixed tag tree identified `offline-recognizer.cc`, and successful sibling downloads were recovered against Git blobSHA1 without refetching.

Independent free hy3 report completed naturally in275.550s, with one7879B report, SHA`c41429bad8ba176c0b3db4f15cc6541bd7af96b7ce6876fcf8ba8d86948448d6`. [Original report](w3-qwen-arena-feasibility-original-20261111.md) is retained unchanged. Actual tool events:15Read/1Write, with two retained read errors (whole pitfalls token cap, directory-as-file); relevant pitfalls were then read in slices, and all12 supplied-context hashes remained unchanged. Tested write sandbox permitted only its task root and exact PID trace root. No native-child retry or paid call occurred.

Parent corrections: the forwarding template is in`session.h`, not the recognizer implementation suggested in the report; source validation gap is now inspected above. “Crash” is imprecise: this run was stopped by the parent budget gate, with no independent crash conclusion. Report wording about “peak” must retain the original sampled-RSS limitation. Public current ORT documentation describes independent arena/pattern controls and possible arena preallocation; it does not prove behavior or savings for this fixed binary. Sources: [OrtApi](https://onnxruntime.ai/docs/api/c/struct_ort_api.html) and the pinned Sherpa receipts in the [machine record](w3-qwen-arena-feasibility-20261111.json).

## Next bounded item

Prepare one small external Kotlin policy module for exact config bytes/path/SHA with exclusive creation and read-only revalidation. Its real filesystem checks are written first and have absent-candidate RED. No native run is frozen by this work. Wrapper integration, actual package/review and a separately frozen one-shot diagnostic contract remain prerequisites; do not replay the old run by changing only its ID. [Design](../superpowers/specs/2026-11-11-w3-qwen-arena-policy-design.md) / [plan](../superpowers/plans/2026-11-11-w3-qwen-arena-policy.md).

The hypothesis is that removing CPU arena preallocation/retention could reduce the observed resource excess. Allocation overhead may grow, and memory use may remain high or increase; zero savings is a valid result. No thread/graph/token/audio/model change, additional key, threshold relaxation or production adoption accompanies this preparation. W3 remains open.
