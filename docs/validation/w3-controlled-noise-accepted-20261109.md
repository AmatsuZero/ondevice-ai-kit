# W3 controlled-noise file recognition: bounded acceptance

The deterministic public English noise fixture and one functional file-recognition
run on each platform are accepted. W3 as a whole remains open.

## Fixture and source

Free CBC hy4free supplied `tools/probes/prepare_noisy_fixture.py` and its synthetic
tests after two rejected WAV-format candidates. Free CBC hy3 supplied only the
test import-path correction. The parent integrated those exact candidates and
verified default unittest discovery: 40 fixture tests passed. The existing speech
metrics suite also passed all 31 tests; no new evaluator was introduced.

The source is the existing three-clip `hf-internal-testing/librispeech_asr_dummy`
fixture, revision `5be91486e11a2d616f4ec5db8d3fd248585ac07a`, CC BY 4.0.
Seed 20261108 and target SNR 20 dB were fixed before inference. Independent stdlib
WAV decoding, header/frame/width checks, a second deterministic output, unchanged
source hashes and notice preservation passed. Independently recomputed SNR was
19.99997–20.00000 dB with zero clipping. The original failures remain retained.

Canonical manifest SHA256:
`bba4fec20e5905943c6c4d14dc9d4aa69a4851d02b059cfb6a64e1e20f8a39bc`.

## Actual functional runs

| Platform | Execution | Result | Descriptive WER | Descriptive CER |
| --- | --- | --- | --- | --- |
| Android | API 36 ARM64 emulator, existing Sherpa production source, one invocation, three clips/four requests | Completed and closed; 11 parent checks passed | 14/59 = 23.73% | 28/268 = 10.45% |
| Apple | iPhone, unchanged existing Swift caller and production Speech source, one file-only invocation, three clips | Completed, drained and closed; 25 final parent checks passed | 4/59 = 6.78% | 13/268 = 4.85% |

Both runs used the same derived PCM bytes and unchanged references. Metrics reuse
the existing evaluator with NFC, casefold and Unicode whitespace normalization;
punctuation remains significant. No quality threshold was selected after seeing
these results. The small fixture does not establish a general backend ranking.

The Apple unsigned build completed in 9.525 seconds. Existing signing inputs
were reused and executable sections/resources checked. The report's
`sdkSourceCommit` is an unchanged historical caller literal, **not** the current
HEAD; `inputs.json.speechSources` binds all four actual production Speech files
by SHA256. Apple requires on-device recognition, but model-version hashes are
not reported and physical network isolation was not measured. No microphone
operation was performed.

Each temporary test alias was removed after its terminal report was collected;
ordinary App paths, all nine protected files and the production Apple lock stayed
unchanged. Apple returned to the ordinary Combined App. Sources, original public
inputs, the canonical derivative, raw reports, failures and accepted binaries are
retained. The separately verified duplicate derivative was already removed;
cleanup receipts record the exact paths and checks.

## Evidence and review

- Fixture: `artifacts/verification/w3-controlled-noise-20261108-2300/`, including
  `real-fixture-parent-acceptance.json` and `default-discovery-tests.log`.
- Android: `artifacts/verification/w3-noisy-asr-20261109-0030/`, including
  `device-report.json`, `parent-acceptance.json`,
  `speech-error-metrics-casefold.json` and `restoration.json`.
- Apple: `artifacts/verification/w3-apple-noisy-file-20261109-0038/`, including
  `w3-file-report.json`, `parent-acceptance.json`, `inputs.json`,
  `speech-error-metrics-casefold.json` and `restoration.json`.
- Source review: [fixture review](w3-noise-fixture-source-review-20261109.md).
- Android/import evidence review:
  [bounded runtime review](w3-noise-runtime-evidence-review-20261109.md).
- Apple evidence review:
  [bounded Apple review](w3-apple-noise-evidence-review-20261109.md).

## Remaining W3 cases

This partially satisfies R4 in
[the remaining-case matrix](w3-remaining-acceptance-cases-20261108.md): controlled
synthetic English noise only. Chinese and real-world noise remain unverified.
R1–R3 still need a user-authorized microphone session and applicable system
resources; no capture was started here. R5/W8 physical resource budgets remain
open. This acceptance does not approve production accuracy, Chinese behavior,
real-noise robustness, peak/GPU/energy, calibration or release readiness.
