# RUNBOOK — W3 Mandarin Profile Candidate (build-time replacement plan)

Scope: stage the existing production-Sherpa `NaturalAsrInstrumentation` as a **fixed** Mandarin
natural-file variant for exactly the 3 frozen public FLEURS clips, locale `zh-CN`. This is a
build-time replacement applied to an **isolated probe copy**; it does NOT replace or weaken the
accepted English baseline. Main owns integration/build/device/testing/cleanup.

## Exact argument differences vs. English baseline

The probe copy is produced by applying `mandarin-replacements.json` to `NaturalAsrInstrumentation.kt`.
After the replacement, the only argument-shape differences are:

- `locale` MUST be explicitly `zh-CN` (the variant hard-fails if absent or `en-US`).
- Three `inputSha_<frozen-id>` values, taken verbatim from `manifest.json` `rawPcmSha256`
  (these are the parent-attested supplied fingerprints; not internally measured here):

| frozen id             | inputSha_ arg key                       | rawPcmSha256 (manifest)                                                            |
|-----------------------|------------------------------------------|------------------------------------------------------------------------------------|
| fleurs-cmn-test-1906  | `inputSha_fleurs-cmn-test-1906`          | `8c8996345c5ffbf90f802ed67d9d3a8257ef12a0768453492ce351e03754912a`                 |
| fleurs-cmn-test-2006  | `inputSha_fleurs-cmn-test-2006`          | `333699e4542733689279698f52497c652dd23ed063f81a2ab861f6ab533f02b9`                 |
| fleurs-cmn-test-1883  | `inputSha_fleurs-cmn-test-1883`          | `b5176ee748e64389a64d0eb56d7114020290e676a90fa48cf49e2db5b53ae33c`                 |

Unchanged required args (main-owned, pass through as before): `runId`, `sourceFingerprint`,
`installedAppSha256` (64-hex each).

## Summary of applied behavior

- **3 rows / 5 requests.** `EXPECTED_SAMPLES = [166080, 132480, 221760]`.
  `computeSlices` is unchanged and already handles these counts:
  - 166080 > 160000 → 2 slices `[0,83040) [83040,166080)`
  - 132480 ≤ 160000 → 1 slice `[0,132480)`
  - 221760 > 160000 → 2 slices `[0,110880) [110880,221760)`
  - total = 2/1/2 = **5 planned requests** (`plannedRequests` set to 5).
- **No `resourceWindow` by default.** The opt-in `resourceWindow` arg is left accepted-but-absent
  (default disabled, byte-equivalent report). Do NOT pass `-e resourceWindow true` for this candidate.
- **Backend/model unchanged.** Pinned multilingual tiny; no model SHA/path change, no new SDK/scorer/
  executor/observer. Zero mic, report atomicity, lifecycle/foreground/error/cleanup/input-ownership
  paths and public APIs are preserved.

## Main-owned integration steps (NOT done here)

1. Apply `mandarin-replacements.json` to the **isolated probe copy** of `NaturalAsrInstrumentation.kt`
   (sequential, pairwise-nonoverlapping string replacement; each `old` appears once).
2. Copy the 3 new PCM assets into the probe APK assets as exactly:
   `fleurs-cmn-test-1906.pcm`, `fleurs-cmn-test-2006.pcm`, `fleurs-cmn-test-1883.pcm`
   (raw 16-bit LE PCM, matched by `rawPcmSha256` above; bytes 332160 / 264960 / 443520).
3. **Adapt parent assertions from old 4 to exact 5** planned/completed requests.
4. Android: launch with `-e locale zh-CN` plus the three `inputSha_*` args above.
5. Apple: existing manifest parser already accepts exactly 3 arbitrary frozen clips; **no Swift
   changes** needed beyond explicit `--odai-asr-locale zh-CN` and these exact AudioFixtures
   (`fleurs-cmn-test-1906/2006/1883`, per manifest `pcmPath`/`wavPath`).

## Scoring notes (honest boundaries)

- **Chinese CER is applicable** via the existing scorer against the verbatim `reference` text.
- **Whitespace-token WER is NOT standard Chinese lexical WER** — do not use it as the Mandarin metric.
- Do **not** set a pass/fail threshold after seeing outputs, **not** manufacture results,
  **not** classify this natural clean speech as real-world noise, and **not** claim network isolation.

## Explicitly out of scope

- No quality/device pass claimed or required here.
- No generic profile, new SDK, scorer, executor, or observer invented.
- English baseline and production model/gates are untouched.
