# W3 Android two-stage FILE package checks

The fixed isolated probe has passed parent host checks, an offline Android build/lint and actual APK inspection. Physical FILE execution and independent wrapper review remain separate gates. No new alias, constructor or decode is implied by these local results.

| Evidence | Result |
| --- | --- |
| External wrapper | `7dc3eaa6834c4ee3ebd1cbcf80e77f7c1b721a31feda7b2786ab3f80163583fa`, integrated unchanged |
| Actual Kotlin wrapper checks | 24 groups / 364 assertions; real core and fixed PCM with Android/native doubles |
| Supporting modules | Inputs 8 groups / 406373 assertions; Activity 6; Report 22; core 42 groups / 264 assertions |
| Offline build | 25.378 seconds; both APKs and lint pass; 0 lint errors / 7 warnings |
| Actual APK inspection | 13 required classes, nonexported Activity, zero requested permissions, arm64 only; all four native library digests match existing Sherpa 2.0.1 AAR |
| Fixed assets | All 65 actual packaged PCM files and manifest size/SHA checked; original reference/order retained |
| Signing | Both APKs verified with the same existing debug certificate; no signing asset modified |
| Other checks | Parent 7 tests; Speech tools 56 tests pass after subprocess-only Ruby alignment; no global config change |

Frozen source fingerprint: `ef4665190423630afa548d4c6b4ea1a4706f98ab29761437887742a7872f8696`.

| Artifact | Bytes | SHA-256 |
| --- | --- | --- |
| `main.apk` | 32464692 | `ada512f0fc39e12955fe039432655b328489e60a2a5b2bd99c02983050a8971b` |
| `test.apk` | 7896828 | `889196d87d67abe62af95801e5b6a788a1822e092b5d16a0489037993b82c18e` |

Wrapper first delivery naturally completed in 317.118 seconds. Initial 21 groups / 343 assertions passed, then supplemental actual-exception and failed-case-binding checks exposed five failing groups. A distinct free hy3 correction completed in 445.411 seconds and passed all 24 groups / 364 assertions, including elapsed-clock faults and simultaneous decode/release failure. The first source, raw events, missing/over-budget Read errors and RED output remain retained. The correction's separate task-root delivery receipt is retained alongside its actual Kotlin file.

Parent scaffold preparation first failed on an absent Windows `gradlew.bat`. The POSIX harness does not require it. The parent now checks existing required scaffold files before creating the source directory, and the partial preparation directory is retained separately. No Gradle/device/native execution happened in that failed preparation.

The new run contract is `w3-qwen-file-20261111-01`, 65 inputs, at most 130 decodes (117 if admission matches the prior Mac result). Sampled RSS above 1.5 GiB, device available below 512 MiB, or a case/load window above 120 seconds stops the exact new alias. There is no automatic retry or changed quality threshold. The old load-only run is consumed and will not be replayed.

Local build/package evidence is `artifacts/verification/w3-qwen-file-20261111/{candidate-accepted.json,run-plan.json,build-result.json,package-verified.json,build.log}`. Host/helper evidence is `.omx/artifacts/w3-qwen-file-20261111/`. These local directories are not guaranteed to be present remotely. This receipt does not prove physical quality, sampled resource acceptance, production licensing or W3 completion.
