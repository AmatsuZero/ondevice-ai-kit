# Android candidate private reports and helper checkpoint

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
