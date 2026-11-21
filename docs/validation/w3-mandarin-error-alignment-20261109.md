# W3 Mandarin error alignment — 2026-11-09

## Scope and frozen inputs

This is a deterministic, offline, descriptive alignment of already accepted
transcripts. No recognizer, model, device, threshold, or production setting was
run or changed. It does not replace or alter the frozen CER scorer.

All three clips come from Google FLEURS `cmn_hans_cn/test`, dataset revision
`70bb2e84b976b7e960aa89f1c648e09c59f894dd`, under CC BY 4.0. The source notice
SHA-256 is `3fe93e8c3fd0b84fa64df60e8ffe5b5f905ebe6ea1cdd1ba6d3cf6a853c7eba0`.
The canonical reference text for each clip is unchanged across reports. Android
reports identify raw PCM input hashes; Apple identifies WAV-file hashes. The
three WAV payloads were decoded with Python's standard-library `wave` reader;
each is mono, 16 kHz, 16-bit PCM with uncompressed `NONE` encoding. The decoded
payload bytes are exactly equal to the corresponding Android raw PCM file.

| Clip | Android PCM SHA-256 | Apple WAV SHA-256 |
| --- | --- | --- |
| `fleurs-cmn-test-1906` | `8c8996345c5ffbf90f802ed67d9d3a8257ef12a0768453492ce351e03754912a` | `601158e75e991eeff3dface860bf33716a1577243bbfad1fea0a1dc1bef6680b` |
| `fleurs-cmn-test-2006` | `333699e4542733689279698f52497c652dd23ed063f81a2ab861f6ab533f02b9` | `10d8a5ac432a79914e008fd1dc8c551ab672dde42b2fd2048a211b445cbe9b6e` |
| `fleurs-cmn-test-1883` | `b5176ee748e64389a64d0eb56d7114020290e676a90fa48cf49e2db5b53ae33c` | `30f12f16ca5ff1866adcd6d29b761b32c223b3020610759911fe38f36952f55f` |

| Clip | Sample frames | PCM payload bytes | SHA-256 after WAV decode | Exact byte match |
| --- | ---: | ---: | --- | --- |
| `fleurs-cmn-test-1906` | 166,080 | 332,160 | `8c8996345c5ffbf90f802ed67d9d3a8257ef12a0768453492ce351e03754912a` | yes |
| `fleurs-cmn-test-2006` | 132,480 | 264,960 | `333699e4542733689279698f52497c652dd23ed063f81a2ab861f6ab533f02b9` | yes |
| `fleurs-cmn-test-1883` | 221,760 | 443,520 | `b5176ee748e64389a64d0eb56d7114020290e676a90fa48cf49e2db5b53ae33c` | yes |

## Method

For each existing reference/transcript pair, apply the current scorer's Unicode
NFC + casefold normalization, remove whitespace only for character alignment,
then compute minimum Levenshtein alignment with exact match, substitution,
deletion, insertion tie order. Keep the scorer's punctuation and digits intact.
Separately label each non-match operation as punctuation-related if either
edited character is Unicode punctuation; otherwise label it digit-related if
either is a decimal digit; otherwise label it other. This is a descriptive
classification of one deterministic alignment, not a semantic judgment about
whether an error matters. Alignment tie choices can affect these categories.

## Results

The first error column is the unchanged CER numerator. The category columns sum
to that numerator and do not change scoring or acceptance.

| Existing output | Clip 1906: errors (punct / digit / other) | Clip 2006 | Clip 1883 | Total errors | Ops (S / D / I) | Descriptive classes (punct / digit / other) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Android Sherpa tiny | 15 (5 / 0 / 10) | 14 (3 / 0 / 11) | 15 (4 / 2 / 9) | 44/92 | 27 / 17 / 0 | 12 / 2 / 30 |
| Android Sherpa tiny, no-VAD control | 19 (5 / 0 / 14) | 14 (3 / 0 / 11) | 20 (4 / 1 / 15) | 53/92 | 37 / 16 / 0 | 12 / 1 / 40 |
| Android Whisper base int8 candidate (rejected) | 18 (5 / 0 / 13) | 12 (3 / 0 / 9) | 13 (3 / 1 / 9) | 43/92 | 24 / 19 / 0 | 11 / 1 / 31 |
| Apple Speech | 5 (5 / 0 / 0) | 6 (3 / 0 / 3) | 5 (3 / 1 / 1) | 16/92 | 4 / 12 / 0 | 11 / 1 / 4 |

The calculated per-clip edit distances match the existing `metrics.json` files:
tiny 15/14/15, no-VAD 19/14/20, base 18/12/13, Apple 5/6/5. The four scorer
input artifacts were read directly and their hashes are recorded below.

## Interpretation and limits

- **Single-request boundary clip 2006:** the fixed PCM is 132,480 samples, below
  Android's 160,000-sample per-request limit; the Android probe reports exactly
  one segment, and Apple submits the whole file. On this clip Android tiny and
  its no-VAD control produce the exact same transcript and 14/24 errors; the
  accepted same-prepared SDK/direct-native control also reproduces that text
  exactly. Apple returns a different transcript at 6/24. This rules out the
  Android wrapper's multi-slice split as the explanation for this clip's
  cross-platform difference and provides no evidence that SDK orchestration adds
  the Android errors on this input. Apple internals, model/backend behavior and
  other preprocessing remain confounded; the result does not isolate a model.
  The base candidate yields 12/24 on this clip, but is still rejected overall
  under the frozen per-clip non-regression rule.
- Tiny has 30/44 edits classified as other characters, 12 punctuation-related
  and 2 digit-related. No-VAD has 40/53 other, 12 punctuation-related and 1
  digit-related. Thus punctuation handling alone cannot explain the observed
  errors or the no-VAD regression under this alignment.
- No-VAD remains worse than tiny (53 vs 44); the prior fixed-run comparison is
  unchanged. This descriptive breakdown does not identify a model, VAD, locale,
  segmentation, or integration root cause.
- The base candidate remains rejected by its frozen per-clip rule: 43 total
  errors is one fewer than tiny, but clip 1906 regressed from 15 to 18.
- Apple has 16/92 on the recorded run, but this offline categorization alone is
  not a causal comparison or production-quality acceptance. Its decoded WAV
  inputs are confirmed byte-identical to Android PCM; the recognizer backend and
  segmentation strategy still differ, so the score gap does not isolate a
  model effect.
- W3 remains open. Further quality attribution requires an independent,
  representative audio source with verified permission, fixed reference text,
  and recorded input hashes. No model sweep or equivalent-input rerun is
  authorized by this report.

## Source artifact hashes

| Artifact | SHA-256 |
| --- | --- |
| Android tiny `device-report.json` | `29b5c67e607d7b18114f1094954dd78341e131346d88ca2846e85adb2ee5f1b1` |
| Android tiny `metrics.json` | `e86b636e6e2df097a1678c6181ee6a783c048c772962b3d4a20e63eb21c58827` |
| no-VAD `device-report.json` | `94c68298be4e2b8a198b31a8618141d46f3643871c96bbb9f2c501dd6944fb8d` |
| no-VAD `metrics.json` | `3519539ed53c16400835a8c4f4a76e7f4c7ed5755fb5fbc21211f926c0cf418a` |
| rejected base `device-report.json` | `389bd4386eefa1128894a00ea213f3359c322c4157ed51d456869027afe9c727` |
| rejected base `candidate-score.json` | `de5ebb44e932c63fbd1018dcb09fd43f69cd15fbf3126e19cf92f754154699f7` |
| Apple `w3-file-report.json` | `fe656824d5fa5f75582fb25da3c94c4a80568a0bbc0a9a206698847f7ba167d4` |
| Apple `speech-error-metrics-casefold.json` | `50796fcffef0765bdb0cc948626df3fdde942750beed08243b8e6257358a97c5` |

The input check read only the six existing PCM/WAV files, validated the WAV
format and decoded payloads, and compared exact bytes and SHA-256. It ran no
recognizer and changed no files. Original raw reports and score files remain at
their existing paths under `artifacts/verification/`; none was modified.
Machine-readable receipt: `artifacts/verification/w3-cross-platform-input-parity-20261109/parent-acceptance.json`
(SHA-256 `75908e783aec72450d2735bafaeb00176c909a78873432c97785d3f79a1ae1a0`).
