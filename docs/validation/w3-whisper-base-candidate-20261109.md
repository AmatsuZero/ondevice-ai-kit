# W3 Whisper base candidate disposition — 2026-11-09

The one fixed Whisper base int8 development candidate is rejected for promotion.
The prospective rule was frozen before the device output: total raw character
errors must be lower than tiny and no clip may regress. The existing scorer was
used unchanged (NFC + casefold; punctuation and digits retained; no Traditional/
Simplified conversion).

| Candidate | Total errors / reference chars | Per-clip errors |
| --- | ---: | --- |
| Existing tiny baseline | 44/92 | 15 / 14 / 15 |
| Pinned base candidate | 43/92 | 18 / 12 / 13 |

The total is one error lower, but the first clip regressed by three errors, so
the frozen acceptance rule fails. The disposition is `reject-current-base-candidate`.
The model is not promoted, production tiny remains unchanged, and no further
model sweep is justified by this three-clip set. This closes only the candidate
experiment; W3 quality and representative independent confirmation remain open.

The candidate was pinned to
`csukuangfj/sherpa-onnx-whisper-base@bb53ee204431c90d314c1cc08d28d23e5b7927cc`.
The single physical TECNO/API35 run completed three clips/five requests. Its raw
report SHA-256 is
`389bd4386eefa1128894a00ea213f3359c322c4157ed51d456869027afe9c727`; the frozen
protocol SHA-256 is
`330b2d732a092528591c43d9709cb1289fbb827a7315d6142d1efcd36dd2900c`.
The parent acceptance records 23 checks, a second byte-identical raw read,
unchanged production sources/protected files/model inputs and restoration to the
ordinary App. It does not claim destruction acknowledgement or production
quality. No rerun is planned.

Full evidence, including original incomplete download attempts, scoring review,
raw reports, restoration and cleanup receipts, remains under
`artifacts/verification/w3-whisper-base-candidate-20261109-1312/`.
