# W3 fixed Mandarin profile — parent source review 2026-11-09

Verdict: ACCEPT_SOURCE_SLICE (parent review only). The requested sole independent
native review returned `429 Too Many Requests` without a review report; it was
stopped without retry. This document is not an independent review substitute.

The free CBC hy3 delivered three actual files in 86.117s. Parent rechecked every
file against its launcher byte-count/SHA receipt. Five exact replacements apply
once each; reversing all five restores the current accepted English probe byte
for byte. The baseline in Git is unchanged. Only the isolated staged test copy
is Mandarin-specific.

The three ids/raw references match the frozen public FLEURS manifest exactly.
Fixed 166080/132480/221760 samples retain the 160000 per-request bound and full
coverage; midpoint splitting yields 2/1/2 requests, and plannedRequests is five.
The existing strict locale parser is unchanged; the added explicit-zh-CN guard
executes before input-hash setup, report reservation or model/backend work.
Source, input SHA, report atomicity, ownership zeroing, foreground opt-in and
cleanup paths have no modifications outside the five disclosed replacements.
No algorithm, model, dependency, scorer, executor or observer was added.

The runbook hashes/bytes agree with the frozen manifest. Its statement that
runId is 64 hex is a narrower parent run choice, not the actual parser contract
(which permits safe 1–80 characters); no source change is required.
The no-resourceWindow instruction keeps this a language-file trial; the
foregroundWindow may still be used for the known test-host lifecycle path.
Apple already accepts a three-clip manifest; no Swift feature is requested.

Staging receipt: `artifacts/verification/w3-mandarin-profile-staging-20261109-0833/source-preflight.json`.
Source review does not establish successful compilation, backend/device execution,
Chinese quality, real-world noise, representative accuracy or W3 closure.
These require separate actual evidence.
