# W7 Apple local-caller source review — 2026-11-09

## Code Review Summary

**Scope:** source-only review of the Apple host’s caller-controlled local-input
switch. No build, test, device operation, private image, identity, embedding, or
score was accessed. This review makes no parity, calibration, PAD, or production
acceptance claim.

**Files Reviewed:** 3
**Total Issues:** 1

### By Severity

- CRITICAL: 0 (must fix)
- HIGH: 1 (should fix)
- MEDIUM: 0 (consider fixing)
- LOW: 0 (optional)

### Confirmed behavior

- The presence-only flag is parsed once; a duplicate is rejected. Required
  `runId`, SHA, and fingerprint remain mandatory and syntactically validated
  before request construction
  (`.omx/artifacts/cbc-hy3-w7-local-caller-20261109-0005/candidate/IdentityParityHostApp.swift:24-62`).
- Without the flag, the public astronaut SHA remains mandatory and
  `publicFixtureOnly` remains true. With one valid flag, the supplied SHA stays
  exactly bound and the probe request receives `publicFixtureOnly: false`
  (`IdentityParityHostApp.swift:54-62,88-96`).
- Invalid/missing/duplicate required arguments leave the host failed (or idle
  when `--parity-run` is absent) before `IdentityParityRequest` or the probe is
  reached. The once-only `started` gate and the prior Swift visibility repair
  remain intact (`IdentityParityHostApp.swift:67-96`).

### Issues

[HIGH] The comments incorrectly treat a command-line presence flag as consent or
authorization authority.

File: `.omx/artifacts/cbc-hy3-w7-local-caller-20261109-0005/candidate/IdentityParityHostApp.swift:5-8,24-25,55-57`

Issue: the source describes `--authorized-local-input` as “caller authorization”
and says “Consent is taken solely from this flag.” A command-line option can
select the local-input code path, but it cannot establish user consent or grant
authority. The required trust boundary is outside this host: an independently
authorized caller may opt in, but the host must not claim it verified that
authorization.

Impact: this misstates the privacy/authorization contract for real local input
and makes a simple invocation switch appear to be a consent mechanism. That can
encourage unsafe operational use even though the parser correctly binds only a
SHA and does not inspect identity data.

Fix: change the affected comments to call this a **caller opt-in** or
caller-controlled routing flag, explicitly stating that it does not establish,
verify, infer, or record authorization/consent. Preserve the parser and
`publicFixtureOnly: !p.authorizedLocalInput` behavior; no additional in-app
authorization mechanism is requested or implied.

### Validation limits

The helper’s natural completion is candidate-delivery evidence only. This source
review is unbuilt and unrun; any authorized local-input trial and device/build
validation remain external to the host and parent-owned.

### Recommendation

**REQUEST CHANGES** — correct the authorization/consent wording above, then
re-review the exact text. The functional parser and one-shot gate otherwise
meet this bounded source scope.

---

## Comments-only re-review — 2026-11-09

**Files Reviewed:** 2
**Total Issues:** 0

### By Severity

- CRITICAL: 0 (must fix)
- HIGH: 0 (should fix)
- MEDIUM: 0 (consider fixing)
- LOW: 0 (optional)

The prior HIGH finding is resolved. The corrected comments explicitly say that
`--authorized-local-input` is a caller opt-in that only selects the local-trial
path and does **not** establish, verify, infer, or record authorization or
consent; they place actual authorization outside the host
(`.omx/artifacts/cbc-hy3-w7-local-caller-comments-20261109-0012/candidate/IdentityParityHostApp.swift:4-8,27-28,58-62`).

The executable parser and invocation behavior remain unchanged: duplicate flags
are rejected, the default public SHA gate remains, the exact caller SHA is bound
only on the opt-in path, `publicFixtureOnly` is still the inverse of that flag,
and the once-only/Swift visibility protections remain
(`IdentityParityHostApp.swift:32-67,72-99`).

### Recommendation

**APPROVE_SLICE** — comments-only trust-boundary correction accepted. It is
source-only and unrun; it makes no production, calibration, parity, or PAD
acceptance claim.
