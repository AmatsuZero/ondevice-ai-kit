# W8 Sherpa sustained observation — 2026-11-09

The distinct current-source Sherpa create/transcribe/close observation is **accepted**. W8 remains open: this closes its paced Sherpa measurement subtask, without approving quality, GPU memory, energy or a production resource budget. W1/W2/W4/W5 stay closed; W3/W6/W7/W8 remain open.

After hy4free naturally returned `empty_model_response` without source files, the user explicitly selected the primary agent to implement this isolated candidate. No failed helper was retried, no paid model was called and no native child was started.

## Protocol and actual result

- TECNO KM9, physical Android API35/arm64; one run, PID11299, run ID `w8-sherpa-20261109-1113-6fdcbfe6-1470-4b8d-a341`.
- Twenty fresh backends, two measured fixed-file requests per backend and one initial warmup. All20 closes and all40 measured calls completed; the expected synthetic phrase matched and caller input stayed unchanged.
- Actual paced window **600.503s**, including **506.508s pacing idle** and20.055s close waits. Measured request wall time totals71.503s. This is not ten minutes of continuous decoding or microphone speech.
- Transcribe nearest-rank p50/p95 **1782.006/1818.381ms**. Timings include production model verification, VAD, native decode/release and result postconditions; they are not decode-only timings.
- **26,222** process VmRSS samples, sampled maximum **457,220,096B**. Final-process immediate/wait RSS267,972,608/268,607,488B; first/last cycle close-wait RSS267,665,408/271,605,760B. Includes the probe; not true peak, GPU allocation or proof of zero leaks.
- Foreground lifecycle, teardown, sampler health/join and durable persistence all passed. Zero microphone starts; audio and transcripts were not persisted.

The nine production sources and their isolated copies matched the frozen source manifest; two test-only sources are separately bound. Four existing model hashes and the public synthetic PCM hash matched. Main APK SHA `01ad2993e52b092c8339ab6a17f4a549e038ff201c28d6dd99abee3c4592cde3`; test APK SHA `42a6f860310550cf0b3df7c55c81ba258c568b270e4b5d7a81076f07327fc982`. Production code, the nine protected paths and Apple lock stayed unchanged.

## Validation and restoration

The real completion predicate passed19 host checks, including short/partial/overlapping cycles, wrong counts, failed close, changed input and unhealthy lifecycle/sampler/teardown/persistence rejection. The final offline dual-APK build passed in12.355s; signatures and the exact runner were verified. The single physical instrumentation command ended with exit0.

Parent terminal validation passed **265/265 checks**. A second independent raw read was byte-identical:1,993,788B, SHA `7f4854fc0d05176fda1421602a5fd02ebfc4577e8c0d5ca9a8c55002cbda31d5`. Before restoration, installed APKs, four model hashes and ordinary package paths were checked again. Only the two new aliases and their exact staging directory were removed; all six pre-existing `io.ondeviceai` package paths stayed unchanged.

Evidence root: `artifacts/verification/w8-sherpa-sustained-20261109-1113/`. Retain `protocol-freeze.json`, `source-manifest.json`, build/package/transfer receipts, `device-report.json`, `second-read.json`, `parent-acceptance.json`, `restoration.json` and host checks. Cleanup retains these sources, APKs and unique raw reports; exact local regenerable cache paths and before/after space are recorded in `cache-cleanup-plan.json` and `task-cleanup.json`.

No equivalent batch is needed. Remaining W8 work is the capability-specific missing measurements and prospective resource-budget decisions in [the resource disposition table](w8-resource-dispositions-20261109.md), followed by final review. Mandarin attribution and representative speech quality remain W3 work.

Post-restoration operator check: ADB became `unauthorized` after both aliases and the exact stage had already been removed and ordinary paths verified. Returning the ordinary App to the foreground was not executed; it remains an operator step, not a failed/repeated measurement. Exact errors are retained in `foreground-return-readonly-check.json`. No further installation, inference or global ADB restart was attempted.

Cleanup completed11:43: three exact inactive local cache directories removed259,645,133 nominal file bytes;76 retained file hashes matched. Concurrent available space changed26,293,788,672→26,428,993,536B; no exclusive recovery claim. APKs, sources, output/signing metadata and raw reports remain.
