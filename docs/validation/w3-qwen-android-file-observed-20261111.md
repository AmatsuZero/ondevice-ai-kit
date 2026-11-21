# W3 Android FILE: stopped at the fixed memory limit

The one-shot TECNO run reached the first input, then the parent stopped the exact new alias when sampled RSS reached **1703575552 B = 1.586578 GiB**, exceeding the fixed 1.5 GiB limit by 92962816 B. No case completed. The remaining 64 rows are planned/unattempted. The current candidate does not pass the Android execution/resource gate; Android recognition quality and complete two-stage cost remain unverified. W3 stays open.

| Observation | Evidence |
| --- | --- |
| Run | `w3-qwen-file-20261111-01`, consumed; no retry or trial batch |
| Device/process | TECNO KM9 / API35 / arm64; PID32722, startTicks222151341 |
| Actual constructor | One attempt returned; 9019536847 ns = 9.019537 s |
| Parent observation | 38 RSS samples, 17.742325 s host window; stop `sampled_rss_above_1_5_gib`; no observation gap |
| System memory | Minimum sampled available 1774850048 B; the 512 MiB gate did not trigger |
| First row | `fleurs-cmn_hans_cn-1805`, 121920 original samples, still running/auto |
| Last durable stage | One created stream; option and waveform acceptance attempted; persisted decode counter 0, no result or decode timing |
| Terminal state | `terminal=false`, `status=running`; no explicit stream/recognizer release or Activity destroy acknowledgement retained |
| Collection | Same-run double read identical, SHA `98d978911f42fae673317ddd63d6f30415d31096dfe9d9ebe197cc10aa0d77b7`; six model digests still match |
| Restoration | Removed only this run's two aliases and exact staging; ordinary App paths/four model hashes unchanged; actual ordinary Demo foreground, no remaining alias/PID/instrumentation |

An interrupted native dispatch may occur after the last persisted snapshot. **The stored counter 0 does not prove zero actual decode calls.** Exact final decode count and native release acknowledgement are unknown; process disappearance after the parent's force-stop is not a substitute for an explicit destructor acknowledgement. No missing or incomplete row is scored as an empty hypothesis. There are zero completed/scored rows and no Android CER/WER or warm RTF for this batch.

Parent evaluation was tightened after this observation: nonterminal counts are explicitly unknown, while the last persisted counters remain available separately. The new regression first failed (`0 is not None`), then all 8 parent tests passed. The original raw report and initial evaluation are unchanged; the clarified evaluation is `parent-file-acceptance-counts-explicit.json`. No device inference was repeated for that offline correction.

Source fingerprint `ef4665190423630afa548d4c6b4ea1a4706f98ab29761437887742a7872f8696`, APK hashes and 65 original inputs remain those in the [package receipt](w3-qwen-android-file-package-20261111.md). [Independent wrapper review and parent corrections](w3-qwen-android-file-wrapper-review-20261111.md) were completed before device preparation. Host/build/package gates passed; they do not override this physical memory failure. Previous Mac quality and TECNO load-only results retain their separate scope.

This run used public files only: no microphone, AiAi, network permission, new model download, model/default-service/production change or parameter sweep. The 1.5 GiB threshold remains unchanged. Next work is a bounded source/API investigation of memory-control options using the existing runtime/model, before proposing any distinct future protocol; this consumed run is not reopened.

[Portable observation receipt](w3-qwen-android-file-observed-20261111.json). Full local evidence is `artifacts/verification/w3-qwen-file-20261111/`, including raw double reads, 38 samples, parent stop log, model/ordinary-App receipts and checked restoration. These local artifacts are not guaranteed to be remotely available.
