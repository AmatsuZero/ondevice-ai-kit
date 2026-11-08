# W3 noisy-fixture source review — 2026-11-09

## Code Review Summary

**Scope:** source-only review of the deterministic public three-clip synthetic
noise-fixture generator and its synthetic tests. No test, build, device, audio,
network, or private-data operation was performed. This review does not approve
ASR scoring, a new executor, model/device use, microphone access, publication,
or production quality.

**Files Reviewed:** 3
**Total Issues:** 0

### By Severity

- CRITICAL: 0 (must fix)
- HIGH: 0 (should fix)
- MEDIUM: 0 (consider fixing)
- LOW: 0 (optional)

### Confirmed source properties

- The writer emits a canonical PCM WAV layout: RIFF size equals `36 + data`,
  the `fmt ` chunk has an explicit 16-byte length, PCM fields are 16-bit mono
  16 kHz, and manifest `sampleWidth` is independently fixed to two **bytes**
  rather than conflated with 16 bits
  (`.omx/artifacts/cbc-hy4free-w3-fixture-format-final-correction-20261108-2321/candidate/prepare_noisy_fixture.py:57-58,312-328,486-512`).
- The strict reader rejects malformed/truncated/duplicate chunks and validates
  byte rate, block alignment, data parity, and full RIFF consumption
  (`prepare_noisy_fixture.py:226-309`). The output tests use stdlib `wave` for
  both independent input creation and output verification, including the raw
  RIFF/fmt/data header checks; they do not reuse the generator writer as the
  oracle (`test_prepare_noisy_fixture.py:89-105,313-337`).
- Generation remains deterministic with a fixed seed, controlled additive
  zero-mean Gaussian noise, a shared gain, measured post-quantization SNR, and
  explicit clipping accounting (`prepare_noisy_fixture.py:364-408`). The tests
  cover byte determinism, target SNR, and near-full-scale input without
  clipping (`test_prepare_noisy_fixture.py:205-267`).
- Input validation requires exactly three clips; preserves clip IDs, nonempty
  references, declared safe independent WAV paths, and the existing seven-field
  `pcmWav` output shape. Duplicate IDs and paths have independent rejection
  paths, and outputs are written to the validated declared `wavPath` rather
  than a path re-derived from the ID
  (`prepare_noisy_fixture.py:442-523,568-595`,
  `test_prepare_noisy_fixture.py:339-357,453-526`).
- Input files and `SOURCE-NOTICE.txt` are read/validated before publication;
  failures remove staging and leave the source untouched. Existing destinations
  are rejected, with focused tests for source immutability, missing notice, and
  destination preservation (`prepare_noisy_fixture.py:542-620`,
  `test_prepare_noisy_fixture.py:220-230,536-540,602-620`).

### Issues

None confirmed in the bounded source scope. The pre-existing publication
existence-check/rename TOCTOU is documented by the candidate and is not widened
here into a generic publication-framework change.

### Validation limits

The helper delivery is source-only and unrun. Parent verification must still
run the bounded tests, independently open generated output WAVs with the stdlib
reader, apply the actual public source fixture, and run the planned backend
acceptance. Those checks must not be represented as accuracy, calibration,
real-world-noise, Chinese-language, or production acceptance.

### Recommendation

**APPROVE_SLICE** — the generator and synthetic-test correction are sound on
static review for the fixed public three-clip derivative scope. Runtime and
backend gates remain pending.
