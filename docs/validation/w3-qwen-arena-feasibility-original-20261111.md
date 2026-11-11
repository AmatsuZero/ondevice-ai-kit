# MEMORY-FEASIBILITY: Sherpa 2.0.1 (commit af2d8d81) Android Qwen3 ASR memory config

Bounded read-only reference/code feasibility. Purpose: assess whether the
existing source supports a `cpu:/absolute/config` provider carrying
`EnableCpuMemArena=0` as a defensible (non-root-cause) memory-reduction
hypothesis. No inference, no device/model run, no implementation edits, no
production/dependency changes. Scope per task: only this report file is written.

Evidence root read: `/private/tmp/odai-hy3-w3-qwen-memory-feasibility-20261111/context`
(absolute, not candidate-relative — avoids the shared context/path pitfall,
agent-pitfalls.md:128). Source receipts pin all files to tag-tree commit
`af2d8d81dbfa61c4cea4c568da78f3982edca005` (source-receipts.json).

## 1. VERIFIED FACTS (from supplied material)

- Kotlin provider field exists: `OfflineModelConfig.getProvider()/setProvider(String)`
  (OfflineModelConfig-jdk21-javap.txt:45-46). Default provider is `"cpu"`
  (offline-model-config.h:54). Successful javap rerun under installed exact
  JDK21 produced those dumps; the first system Java launcher FAILED and is
  retained (aar-static-evidence.json:65-74, exitCode 1, "Unable to locate a
  Java Runtime").
- JNI reads provider verbatim into C++ `model_config.provider`
  (jni-offline-recognizer.cc:77-78).
- Three Qwen sessions built from one `OfflineModelConfig`: conv_frontend,
  encoder, decoder — each via `GetSessionOptions(config)` then `Ort::Session`
  (offline-qwen3-asr-model.cc:126-128, 135, 140, 145).
- Parser in `GetSessionOptionsImpl` splits `provider[:config_path]` at the
  first `:` (session.cc:106-142), and applies `EnableCpuMemArena=0 ->
  sess_opts.DisableCpuMemArena()` and `EnableMemPattern=0 ->
  sess_opts.DisableMemPattern()` (session.cc:257-273). `kCPU` case appends no
  EP (session.cc:308-310).
- AAR native libs contain the parser strings: `EnableCpuMemArena`,
  `EnableMemPattern`, `SessionConfig.`, `SplitProviderAndConfig`,
  `Provider config path is empty`, `Qwen3ASR`, `GetSessionOptionsImpl` have
  positive offsets in libsherpa-onnx-c-api.so and libsherpa-onnx-jni.so
  (aar-static-evidence.json:19-63). `libonnxruntime.so` shows -1 (expected).
- Observed run: constructor returned once; sampled RSS 1703575552B >
  1610612736B (1.5GiB); parent force-stopped; completedCases 0;
  lastPersistedDecodeCalls 0 but actualDecodeCallsExact=null and
  countsAreExactTerminalEvidence=false; explicitRecognizerReleaseAcknowledged
  false; truePeakMeasured false; productionReady false
  (device-observed.json:8,11,16-17,23-26,46).

## 2. Q1 — provider chain, is_cpu_provider_ / IsCudaProvider, conjunction

Uses of `is_cpu_provider_` (offline-qwen3-asr-model.cc): ctor init :132 and
:163; used only at :200; declared :790. Uses of `IsCudaProvider`: definition
:111-117; used only at :200.

Constructor comparison is EXACT: `config.provider == "cpu" ||
config.provider.empty()` (:132,:163). For `cpu:/absolute/config` this is
**false** (suffix breaks exact match). But `InitIoBindingConfig` (:198-205)
computes `use_cuda_iobinding_ = (!is_cpu_provider_ && IsCudaProvider(provider))`.
`IsCudaProvider("cpu:/absolute/config")` lowercases then tests `== "cuda"` or
starts-with `"cuda"` (:111-117) → **false**. Conjunction ⇒
`use_cuda_iobinding_ = false`.

Conclusion: the false `is_cpu_provider_` is gated by `IsCudaProvider`; it does
NOT switch dispatch (cuda IoBinding only wraps `Run` when true, :348-483; with
it false the plain CPU `Run` path is used). **False CPU flag alone is not a
blocker** — confirmed by the conjunction, not assumed.

End-to-end support: the *parser* (GetSessionOptionsImpl) does support
`cpu:/absolute/config` + `EnableCpuMemArena=0`. Two supplied-slice gaps (see
blocked claims) mean the full chain is not provable from supplied files alone.

## 3. Q2 — EnableCpuMemArena vs EnableMemPattern

Distinct ORT controls (onnxruntime-context7.json: when EnableMemPattern is set
and shapes static, ORT pre-allocates one large buffer via Reserve bypassing
the arena). `DisableCpuMemArena` changes the BFC arena allocator;
`DisableMemPattern` changes repeated-run buffer reuse. They are independent.

ONE-variable arena-off (`EnableCpuMemArena=0`) is a **defensible future
hypothesis** only: it may lower peak/over-allocation from arena binning, but
ORT arena can also *reduce* fragmentation/peak, so savings are NOT guaranteed.
Risks/limits: possible allocation slowdown; no change to graph/threads/tokens/
audio front-end (correctness orthogonal); effect is build/device-specific.
Cannot be established by string evidence — static offsets only show the option
is compiled in, not that it executes, lowers the 1.5GiB peak, or that the AAR
binary == source commit. No attribution to arena has been proved (per task).

## 4. Q3 — smallest zero-inference static validation

1. Close the forwarding gap: confirm `GetSessionOptions(const OfflineModelConfig&)`
   (referenced by receipt offline-recognizer-qwen3-asr-impl.cc, NOT in supplied
   slice) forwards `config.provider` verbatim to `GetSessionOptionsImpl`, and
   that `OfflineModelConfig::Validate()` (not supplied) does not reject a `:`
   suffix before parsing.
2. Host-only unit test of the supplied static helpers `SplitProviderAndConfig`
   + `ParseConfigFile` (session.cc:48-142) with a synthetic file containing
   `EnableCpuMemArena=0` and a separate `EnableMemPattern=0`, asserting
   DisableCpuMemArena vs DisableMemPattern are independently toggled.
3. Assert `StringToProvider("cpu")==kCPU` and kCPU appends no EP (session.cc:308-310).
All zero-inference: no model load, no decode, no device.

Unverified for the actual Android binary: production actually passes
`provider="cpu:/absolute/config"` (default is bare `"cpu"`); the config file
exists/readable at that absolute device path; the AAR binary contains this
session.cc logic; arena-off lowers the observed peak; and whether the crash
was memory-only.

## 5. Q4 — next safe work item + stop condition

Next safe item: perform Q3 validation (read the two not-supplied files via the
pinned commit, add the host-only parser unit test) WITHOUT touching production,
model files, or rerunning the failed device run; preserve the failed run and
current W3open state.

Stop condition: stop and report upward (a) when the
OfflineModelConfig→GetSessionOptionsImpl→DisableCpuMemArena path is fully
closed by confirmed source, or (b) when a gap blocks it (Validate rejects `:`,
or no overload forwards the suffix). Do NOT proceed to any device/model run,
production edit, or dependency change; do NOT accept failed R4/R5 or propose
thresholds.

## 6. BLOCKED CLAIMS / SCOPE CHANGES (reported upward)

- B1: OfflineModelConfig→GetSessionOptionsImpl forwarding and
  OfflineModelConfig::Validate are not in the supplied slice (receipt lists
  offline-recognizer-qwen3-asr-impl.cc/.h; not provided). Chain end-to-end
  unproven from supplied source alone.
- B2: Applying `EnableCpuMemArena=0` requires changing provider from bare
  `"cpu"` to `"cpu:/absolute/config"` AND shipping that config file on device —
  a configuration/scope change, not a single boolean flip.
- B3: AAR string offsets are observations of the fixed AAR, NOT proof of
  execution or source/binary identity; libonnxruntime.so lacks them (expected).
- B4: Observed run is nonterminal; 0 persisted decode is not 0 actual decode
  (agent-pitfalls.md:7-9; device-observed.json:23-25). No rerun proposed.

## RECEIPT
- Report: /private/tmp/odai-hy3-w3-qwen-memory-feasibility-20261111/candidate/MEMORY-FEASIBILITY.md
- Boundaries: read-only; wrote only this file. No Bash/network/device/model/
  inference/download/implementation/global-config/dependency/production changes.
- Did NOT run host/device checks or claim any; no quota failure encountered,
  none retried. Citations are filename:line against supplied context.
