# W3 prefix candidate contract review — 2026-11-10 07:18 +08

The fixed-prefix experiment remains unrun and W3 remains open. Local acceptance
checks found two additional blocking defects in the retained candidate. This
review changes the next correction's acceptance requirements; it does not
accept or repair the candidate or change any production source.

## Frozen reference was changed by the candidate

The pinned FLEURS manifest preserves both quotation marks:

> “这并不是告别。这是一个篇章的结束，也是新篇章的开始。”

Its UTF-8 SHA-256 is
`6f20dd84a6c686c60507428e037063762c5a15bb7e9e9777c1047049b62a9152`.
Both the original CBC plan and the later local plan omit the two quotation marks
and instead contain reference SHA-256
`e7624a6e0e29758a7584dccf7778ac9c542ea8613cf69ad8d88285ad6dffcc76`.
The supplied CBC prompt contained the correct reference, so this drift occurred
in candidate delivery and was inherited by the local correction.

Calling the existing, unchanged scorer on the existing full-clip tiny transcript
reproduces **15/28** with the canonical reference and **13/26** with the stripped
candidate reference. Recognition was not rerun. The latter is an invalid change
to the frozen reference, not a quality improvement. Existing accepted CER
reports remain unchanged. Future candidate acceptance must check exact reference
text/hash against the manifest, including punctuation.

The canonical reference is for the entire clip. The prospective two-crop
diagnostic tests whether restoring the prefix changes the output; it has no
independently time-aligned crop reference. Do not report a crop-level quality
improvement by scoring either partial crop against the full-clip reference.

## Both report branches emit invalid JSON

The exact serializer section (original instrumentation lines 181–315) was copied
byte-for-byte into an isolated JVM evidence harness, alongside the original
pure plan object before its invalidly placed test imports. The wrapper supplied
synthetic metadata/hypotheses and false lifecycle flags; it opened no models,
created no recognizer, and used no Android classes. The original candidate files
were not modified or compiled as an Android App.

The pure extraction compiled and ran with the existing Gradle JDK/cache. Both
success and failure outputs fail Python's strict JSON parser at the first
`source` object's `clipBytes` trailing comma (line 17). The same append pattern
also occurs in later objects. The initial harness launch omitted the compiler's
reflection dependency; its exact failure is retained separately and is not a
candidate or provider failure. Adding an already-cached reflection jar enabled
this offline extraction; no dependency was downloaded or project lock changed.

Future correction acceptance must exercise the real serializer for success and
failure, parse both outputs strictly, retain observed evidence on failure, and
serialize release status only after the corresponding release completes. A
terminal file's existence alone cannot establish a valid report.

## Fixed crop identities independently rechecked

The original source is `google/fleurs`, `cmn_hans_cn/test`, revision
`70bb2e84b976b7e960aa89f1c648e09c59f894dd`, CC-BY-4.0. Existing PCM bytes were
read locally; there was no download, conversion or ASR invocation.

| Input | Bytes | SHA-256 |
| --- | ---: | --- |
| Whole clip 1906 | 332160 | `8c8996345c5ffbf90f802ed67d9d3a8257ef12a0768453492ce351e03754912a` |
| Second slice `[83040,166080)` | 166080 | `374dbab2ab6f565e9c15aa1a969252d7583ea68eea176d037aa86065ba865ef6` |
| Baseline slice-local `[2432,36480)` | 68096 | `3488fa8276e5fd655b23ebcc99e8d4c5fe49808580d90d9ccdf80731a8d7c5a3` |
| Candidate slice-local `[0,36480)` | 72960 | `8e408ea77635aadf6e77dae482d3e3149a78461de587010e18aefab0547ca7c8` |

The candidate bytes after the first 2432 samples are exactly equal to the
baseline bytes. These are verified input identities, not recognition results.
The existing no-sweep/no-production-VAD-change boundary is unchanged.

## Evidence and remaining execution gate

Local evidence is retained under
`artifacts/verification/w3-prefix-contract-audit-20261110-0720/`:
`audit.py`, `parent-acceptance.json`, both exact extraction sources, compiler and
runtime logs, and the two original invalid report outputs. The audit checked
seven source/input hashes before and after; all stayed equal. No helper request,
private data read, model/device run, signing change or production edit occurred.
The evidence directory is about 84 KiB; no large disposable build/cache was
created or removed.

The original compile, model-file verification and lifecycle defects also remain
open. CBC correction previously failed during local startup; further local
model retries for this slice were stopped. The content-specific Qoder external
transfer request remains pending after automatic review rejected it; do not
send the same files through another external helper to bypass that boundary.
The native reviewer is terminal with 429 and was not retried. The next actual
implementation needs an authorized viable route, followed by focused offline
acceptance before any device discriminator. W8 ASR and accepted TTS/Sherpa runs
remain untouched.
