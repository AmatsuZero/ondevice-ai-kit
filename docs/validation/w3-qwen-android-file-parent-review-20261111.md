# Android FILE ASR parent preparation and review

The fixed input manifest, isolated build/package verifier, bounded device parent and coverage-aware scorer are prepared. Android wrapper generation is still pending; these scripts have not been used to install or run the new FILE probe.

`probes/android-qwen-file/cases.json` retains all 65 independent public inputs, unchanged order, sample counts, PCM digests and original references. SHA256 `8d1cfd7465e70f413b741701250d61c31170b25a6978a256a4df0349efa0910f`; no audio/model/runtime binary is included in the source commit. All actual PCM and six runtime files were rehashed against the frozen protocol before preparation.

The independent free hy3 supplied-context review completed in 295.065s and wrote an actual report (SHA256 `b8e58b2cbf1efcda6b69f19d08134ccb14d3f2c3d156c51cbb42513e38a1d337`). It found no Critical/Important issues in the parent scripts. Its five Low observations were adjudicated:

- A: preflight's verified label follows actual top-level source/APK digest assertions; the claim that no verification computes it overlooks those checks.
- B: device model hashes and reported model hashes each match the same expected specifications; the review's suggested direct comparison with a Boolean map is unnecessary.
- C: six files is the intentional frozen contract. Unexpected cardinality must fail.
- D: deferred minor recovery limitation: staging mismatch after exact-alias removal leaves staging and withholds the final restoration receipt for investigation. Unknown files must never be recursively removed to satisfy cleanup.
- E: fixed with RED/GREEN regression: a stopped run without a report now produces structured rejection, with unknown decode count rather than fabricated zero or a file-not-found error.

Seven parent tests pass, including budget boundaries, partial coverage, changed references/digests/counters, missing release, stopped host and missing report. The unchanged shared scorer's 31 tests pass. Core Kotlin acceptance remains 42 groups / 264 assertions from the separate source gate.

The review did not inspect Android wrapper/core/scorer/APK/device bytes and ran no tests; parent execution supplies the checks above. Model-manifest contents were absent from reviewer context; the primary agent separately verified six model files and expected manifest values. Actual cadence, native operation counters and restoration require the new device run. Sampled RSS is not true peak; no GPU/energy/sustained or production acceptance is implied.

Evidence: `.omx/artifacts/w3-qwen-file-20261111/helpers/odai-hy3-w3-qwen-file-parent-review-20261111/` (original prompt/context/raw/result/report and parent disposition). The source changes only move the identical fixed manifest into a committable probe file and add the reviewed missing-report recovery. W3 stays open, no new microphone/system-ASR/native/device action, no source push.
