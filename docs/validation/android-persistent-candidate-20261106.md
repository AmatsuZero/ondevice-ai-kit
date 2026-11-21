# Android candidate private reports and helper checkpoint

## Completed physical-device slice

The exact frozen `e0438c030a987ceeff9f9207c2475684749e4d8b` candidate (`b90e...64d8d`) completed fixed-input ASR lifecycle and SR runs on TECNO KM9/API35. Four recorder self-checks also completed and were correctly rejected as inference evidence. No new recording, model download or main APK rebuild was involved.

- ASR: `asr-fixed-inference-20261106`, PID20393, all 13 native cancel/close/reentry flags passed. The private terminal report was read back with its exact SHA; microphone starts remained zero.
- SR: `sr-fixed-inference-20261106`, PID21382, native run `0ac8554d-ec79-4047-8786-56870a1112dc`. All six native flags passed. The retained authorized input is **64×48**, two H.264 frames plus AAC, SHA `8277f1daa2e8d0aab1425393c929e7b5a18e4bc241901f0012319dd43ca65d8c`. The x4 output is **256×192**. Host decoding confirmed both output frames, preserved/discarded audio tracks, and matching video/audio PTS.

The initial SR receipt was rejected because the validator and its unit fixture both incorrectly expected 128×96 from a different input. The strict expected dimensions and README are corrected; a regression rejects that other profile. The original rejection remains unchanged in `run-sr-fixed-inference-20261106-collected.json`; `run-sr-fixed-inference-20261106-revalidated.json` validates the **same original report and retained media**. Inference was not repeated and no report bytes were rewritten.

The original main/test APKs, runner, 25 private model/journal hashes, permissions/TTS setting and all four original SR files were restored and verified. Five staged APKs and 113 exact manifest-listed chunks were removed; the ordinary MainActivity was returned. SR directories were preserved because their pre-experiment absence was not proven. Recovery also retained known report sidecars and failure/nonterminal bytes without requiring a successful inference result.

Local evidence: `artifacts/verification/android-persistent-candidate-20261106/device-summary.json`, `restore-preservation.json`, `restore-state-3.json`, `restore-complete.json` and `restore-device-result.json`. Device restoration completed in 96.88 seconds; these receipts are local ignored evidence, not clone-distributed files.

Verification now includes **412 tools tests passed in 47.682 seconds**, plus **25 separate offline protocol checks** (16 restoration, one failed-SR collection, eight lifecycle boundaries). The first 412-test sandbox run had three Swift cache permission errors; its failure log is preserved. Running the same suite with normal host cache access passed; Ruby3.4.11 was selected only for the subprocess environment. No global toolchain/cache configuration or production locks changed.

Copilot's concrete restoration review found four blockers, fixed with RED/GREEN checks: partial scratch recovery, atomic local receipts, stable output retention names and preserving preexisting empty directories. Its supplied-context review had zero tool executions. Ollama `qwen3-coder:30b` generated the earlier eight boundary checks; parent review corrected the candidate before use. Its later protocol-only review added no validated issue. The exact prompts/raw results and dispositions are under `.omx/artifacts/` and the helper invocation recipes remain in the root local `AGENTS.md`.

This closes the bounded ASR/SR physical-run and restoration slice. Natural quality, resource/energy, minimum-OS device, calibration, licensing and independent release gates remain outside it. Roadmap remains 10/12, P4/P11 open and release not-ready.

## Historical pre-device checkpoint

Test-only report tooling is implemented and built. No candidate installation or inference has been performed in this slice.

`probes/android-candidate-report/generate.py` adds separate ASR lifecycle and video SR runners to a frozen SDK source snapshot. The original 953 source files remain unchanged. The runners write a private receipt before starting and an atomic terminal report before finishing. Unique run IDs cannot be reused. Recorder self-check modes remain distinguishable from inference, and `tools/probes/validate_android_candidate_report.py` refuses to treat them as inference evidence.

Both offline test APK builds passed. The main candidate remains the original `b90e783062548368f12e5262a1dc8437d511119c6dddf1c1ffb66aabafa64d8d` APK; a rebuilt main APK must not replace it. The new ASR test APK is `b631b9225e94642afe618b30693625145c3c31e324826439c1bc07cdda27f958`; the SR test APK is `79fdb56710a229fa5c47ec549e4c03e31accb78af10b1ae82a2b279a445b541c`.

The retained complete tools-suite run passed 405 tests in 54.923 seconds. Its log and receipt are in `artifacts/verification/android-persistent-candidate-20261106/`. Copilot generated a local provenance verifier; parent review tightened identities and path checks before integration. Fresh verification passed, including eight negative checks, and confirmed the nine protected files and production Apple lock. Run the read-only verifier with:

```sh
rtk proxy python3 artifacts/verification/android-persistent-candidate-20261106/verify.py
```

Helper prompt, raw result, usage and parent verification are retained under `.omx/artifacts/copilot-candidate-verifier-20261106/` and summarized in `ask-copilot-android-verifier-20261106T091703.md`. Local root AGENTS.md records all helper invocation recipes. Copilot's auto route selected `gpt-6-luna`; this proves a completed candidate task, not independent release approval. cbc hy3 supplied earlier validator/audit candidates; agy supplied the README and chunker candidates. The separate hy4free F09 fix lane ended naturally with truncated JSON and no usable candidate; it was not terminated for silence or elapsed time.

Transfers repeatedly encountered EOF followed by offline or unauthorized state. Each resumed attempt checked the exact retained chunks rather than trusting `1 file pushed`. The latest attempt completed all **five** APK copies: 55 candidate chunks, 55 rollback chunks, and one chunk each for original test/ASR test/SR test. Every whole-APK hash matches the independent fixed expected value; receipts and `stage-resume-06.json` are retained. `stage-chunks.py` now verifies local manifests against those fixed expected APKs before transfer; the install mode verifies the complete five-APK rollback chain before replacing either package.

The ordinary App/test/runner and private models were not replaced. An agy run/collect candidate was reviewed and tightened for strict ADB errors, exact report bytes, run-ID validation and stale SR detail rejection; its device execution is still unverified. Before installation, finish and verify the complete restore protocol and preserve the backed-up SR paths and all 25 private hashes. Never run the original Identity instrumentation unchanged because its journal namespace belongs to existing App state. USB recovery must not trigger automatic inference retries.

The F09 consumer audit separately reproduced two local races. They are now fixed with a frozen manifest and exclusive publication; [verification](f09-consumer-generation-20261106.md) retains RED/GREEN evidence and the complete 411-test pass. The hy4free fix response was truncated, so the primary agent implemented the fix. No SDK/native artifacts were rebuilt for this Python change.

[Shared helper pitfalls](../agent-pitfalls.md) records the actual CLI, quota, Ruby, ADB and race failures. Root local AGENTS.md requires helpers to read it before work and on errors; no-tools candidates receive the applicable excerpts in their prompt.

Roadmap remains 10/12, P4/P11 open, release not-ready. Minimum-OS physical-device validation is explicitly deferred; F10 has no authorized calibration dataset and remains `THRESHOLD_UNVERIFIED`. Local build/provenance verification does not close device, quality, licensing or release gates.
