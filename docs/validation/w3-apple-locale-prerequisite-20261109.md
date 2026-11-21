# W3 Apple explicit locale prerequisite — 2026-11-09

The free CBC hy3 source candidate was accepted as a bounded engineering
prerequisite, not a Chinese-language or overall W3 acceptance result.

The existing file-only app now accepts one exact `--odai-asr-locale en-US|zh-CN`
launch argument. Absence preserves en-US behavior and report shape. Duplicate,
missing, empty, unsupported or case-variant values reach the existing bounded
failed-report path before backend/client construction, Speech authorization or
recognition. Unrelated OS launch arguments remain ignored. The app still starts
only from its explicit button; no microphone or production source changed.

Evidence:

- Free hy3 delivered seven unique source replacement blocks and two documents
  in 152.75 seconds. The parent applied those exact blocks without source edits.
- The complete iPhoneOS app built with signing disabled in 10.251 seconds.
- Twelve host-executed cases used the exact candidate parser method, with only
  its private visibility removed in the isolated test wrapper. They cover
  defaults, unrelated arguments, both accepted values and malformed forms.
  This is parser execution, not execution of the App failure-report pipeline.
- The independent source review is
  [APPROVE_SLICE](w3-apple-locale-source-review-20261109.md).
- Receipts and build/parser logs:
  `artifacts/verification/w3-apple-locale-20261109-0814/`.

No App was installed or launched for this acceptance; the actual failed-report,
authorization and valid-locale device paths remain runtime-unverified. Existing
English fixtures and their references are unchanged. No Chinese input, quality
threshold, CER/WER, physical resource budget or overall W3 pass is claimed.
The historical `sdkSourceCommit` literal remains historical; current source
identity is bound by the parent input SHA receipts.

Task cleanup retains the exact candidate/source, unsigned App, raw build/parser
logs and source receipts for later fixture validation. Disposable task caches
are assessed separately; no global cache, private input, model or ordinary App
is removed.
