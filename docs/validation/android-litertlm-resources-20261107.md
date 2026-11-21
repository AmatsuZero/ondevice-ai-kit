# Android LiteRT-LM fixed-image resource measurements

On 2026-11-07, the retained ordinary `afed156b…926c461` APK completed one warmup
and five measured SDK calls on TECNO KM9/API35, PID11157. All six returned the
fixed Gemma identity, a red-word output match, unchanged synthetic input and
no remaining session cache files. The report is bounded fixed-input resource
characterization; natural image quality and production budgets remain unverified.

| Measurement | Observed value |
| --- | --- |
| SDK whole-generate wall p50 / p95 | 56,477.465 / 57,068.401 ms |
| Measured min / max | 53,844.486 / 57,068.401 ms |
| Sampled process RSS peak range | 3,905,445,888–3,978,801,152 B |
| Measured post-idle RSS first / last | 291,729,408 / 286,613,504 B |
| RSS before / after final close / after 1s wait | 236,244,992 / 279,027,712 / 237,228,032 B |
| RSS points across all six calls | 15,275 |
| Actual interval median / maximum | 22.325231 / 63.891154 ms |
| Reported thermal status | 0 for all six completed rows |

Timing begins immediately before `LiteRtLmBackend.generate` and ends on return,
including availability, ModelStore integrity reads, image/cache snapshot, native
engine initialization, blocking send and session release. Each call creates a new
native session. Join of the sampling thread, validation,200ms idle and report
persistence are excluded. VmRSS is sampled for the whole process and bracketed
around the call; it is neither a true peak nor an attribution to the SDK or GPU.
A retained native-log excerpt confirms Mali-G57 MC2/Vulkan integrated GPU and
1448/1448 vision nodes delegated to LITERT_WEBGPU for an observed request; it
does not provide six complete teardown pairs or GPU allocation measurement.
The observed duration includes reading/hashing the existing3.6GB model and is not
an isolated decode/token rate. Five measurements do not establish sustained,
cross-device or energy budgets.

The fixed image is `probes/android-litertlm/solid-red-256.png`, SHA256
`991b54f01af493986b674dc2eedbc0b3cdf5aa9c9518f16dc379ccd6d9c9d3aa`.
Prompt and32-token output budget are fixed in the probe. Output text is not
persisted; rows retain SHA256/length and the bounded red-word match. Existing
Gemma3n E2B revision `c03b6f60b8da6c5400b6838a2cf26420f80c0a01`, weight SHA256
`2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6`,
`litertlm-android:0.18.1` and explicit `CPU_TEXT_GPU_VISION` were used. No new model,
license acceptance, microphone/camera or download was needed.

The ordinary main APK was retained. Final test-only overlay294,380B was built
offline from953 frozen `e0438c0` sources and14 fixed external inputs; all original
source bytes were rechecked. Thirteen frozen LM/ModelStore main sources match
historical LM source records. This is context, not a new whole-installed-App
source attestation; `e0438c0` identifies test compilation only. Current SDK cleanup
changes elsewhere were not installed or validated by this measurement.

The host ADB command returned255 with empty output during the last request.
The same runId was observed without resubmission and later wrote its completed,
terminal six-row report. Exact760,666B were collected and matched the device
SHA256 `d1580dad6fb640233ea8a7a1b62059861f3ef8de0b3b53ad66d8da7c6e19ad4a`.
A later USB gap blocked the first restore attempt before any restore mutation;
that failure is retained separately. After reconnection, the original test APK
and Identity runner were restored;25 private hashes,4 SR files,permissions and
TTS setting match the baseline. The exact report/staged APKs and owned work
folder were removed, and the ordinary App was returned. These restoration
receipts are independent from the inference report.

cbc hy3 returned a complete reducer candidate in194.892s with no tool-use blocks.
It met the supplied prompt-hash shape contract; parent strengthened it to the
exact approved prompt identity, proving the wrong-valid-hex rejection RED/GREEN.
Parent also fixed a provenance schema bool acceptance found by a new negative
case. Eleven reducer methods and44 focused checks pass; final complete tools
suite440/440 passed in44.302s. Initial absent-module and unsupported-kind errors
are retained as setup/feature-absence observations, not product regressions.
Local Ollama qwen3-coder:30b inspected the final probe in8.279s, empty findings;
that one-file opinion is not independent release approval. Original prompts,
raw outputs and parent dispositions are under `.omx/artifacts/`.

Evidence is retained under ignored
`artifacts/verification/android-litertlm-resources-20261107/`.
The new `litertlm-android-resource` group adds five measured samples, taking
P11 from6 capabilities/9 groups to7 capabilities/10 groups. The previous nine
groups are byte-equivalent as decoded JSON; the one warmup is excluded. Current
pointers are in `benchmark/p11/grouped-report-manifest.json`; readiness and
raw/source/model identities are verified independently. Read-only reproduction:

```sh
rtk proxy python3 artifacts/verification/android-litertlm-resources-20261107/freeze.py --verify
```

P4/P11 remain open, foundation10/12, release not-ready. Quality, true/GPU peak,
energy, sustained budgets, minimum OS, exact whole-candidate acceptance,
calibration and the existing delivery/review gates remain separate.
