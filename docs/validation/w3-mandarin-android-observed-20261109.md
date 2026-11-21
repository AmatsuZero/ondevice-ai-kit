# W3 Mandarin Android file observation — 2026-11-09

Three frozen public Mandarin natural-speech files completed on TECNO KM9/API35
through the actual pinned multilingual Sherpa Whisper tiny backend. This records
a functioning file path and measured errors; Chinese quality and W3 remain open.

## Reproducible input and source boundaries

Source: Google FLEURS `cmn_hans_cn/test`, official dataset revision
`70bb2e84b976b7e960aa89f1c648e09c59f894dd`, card-declared CC BY 4.0. The failed
rows endpoint returned HTTP502 and is retained. The parent streamed only the
first three matching WAV entries from the same pinned archive, reading 1474560
compressed bytes, without fetching the whole archive. Originals, full TSV, card,
raw references and SHA receipts are retained locally.

The installed ffmpeg converted mono16kHz float WAV to mono16kHz s16le WAV with
dither disabled and unchanged frame counts; raw PCM bytes were extracted and
hashed. No trimming, noise, resampling or output-based selection was used.
The original and converted byte differences are an explicit conversion, not
an assertion of sample-exact float fidelity.

Free CBC hy3 delivered a fixed isolated test-profile plan in 86.117s. Five unique
replacements reverse exactly back to the accepted English probe; references and
166080/132480/221760 samples match the frozen manifest. The unchanged160000-sample
production bound produces 2/1/2 slices, five requests. The explicit-zh-CN guard
runs before report reservation/backend work. Current nine production sources and
model hashes remain unchanged. The isolated APK pair built offline in16.254s.

The sole independent native review failed429 without a report and was not
retried. The separate parent source review is explicitly non-independent:
[w3-mandarin-profile-parent-review-20261109.md](w3-mandarin-profile-parent-review-20261109.md).

## Actual device result and metric

One reserved run, no inference replay: terminal completed, three rows, five
requests, nonempty hypotheses, backend close, zero microphone starts, exact
locale, references/hashes/sample counts and slice coverage. Nineteen parent
checks passed. No resourceWindow or observation holds were requested. Summed
segment recognition duration was10.643208001s, not an end-to-end user latency.

The existing scorer's unchanged primary policy is NFC+casefold, whitespace
excluded for CER; punctuation, digits and simplified/traditional script are
preserved. Corpus CER is **44/92=47.8261%**, comprising27 substitutions and17
deletions, zero insertions. This includes visible script/punctuation differences
as well as lexical/numeric mistakes; their causal contribution has not yet been
isolated. The whitespace-token output is not Chinese lexical WER. No threshold
was selected after seeing outputs; these results do not approve Chinese quality.

The three clean clips are a tiny, nonrepresentative sample and do not satisfy
real-world noise, all language/domain quality, network isolation, long duration
resource budgets or W3 closure. Apple same-input runtime remains pending.

After collection only this run's two new aliases and exact transfer stage were
removed. Ordinary App paths, protected nine files and Apple lock were unchanged.
No private/user/employee media was used or supplied to any helper.

## Evidence and next step

- Frozen input receipts: `artifacts/verification/w3-mandarin-fixture-freeze-20261109-0823/`.
- Source/build/runtime/metrics/restoration:
  `artifacts/verification/w3-mandarin-profile-staging-20261109-0833/`.
- Free CBC hy4free owns an independent public-source/evidence diagnosis only in
  `.omx/artifacts/cbc-hy4free-w3-mandarin-quality-diagnosis-20261109-0840/`.
  Its delivery is not a model/API defect finding or quality pass. Parent will
  run the same-input Apple comparison and assess its bounded controls.
