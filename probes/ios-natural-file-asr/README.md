# W3 Apple Explicit Locale Argument (Prerequisite Only)

This change adds a **single, opt-in** launch argument to the existing W3 Apple
FILE-only ASR app, so the same frozen public-file app can be reused for Chinese
(`zh-CN`) frozen public-file testing without altering any app behavior, fixture,
or quality path.

## Flag

`--odai-asr-locale <value>`

- Accepted values (exact, case-sensitive): `en-US` or `zh-CN`.
- Absent: preserves `en-US` and the existing default report shape / behavior.
- The locale is **never** derived from phone settings, and English references are
  not reinterpreted.

## Behavior

| Input | Result |
|-------|--------|
| absent | en-US default, unchanged behavior |
| `--odai-asr-locale en-US` | explicit en-US |
| `--odai-asr-locale zh-CN` | explicit zh-CN, passed to backend + request |
| duplicate flag | error (safe fixed category) |
| missing value / empty | error (safe fixed category) |
| unsupported / case variant (e.g. `ZH-cn`, `zh_CN`) | error (safe fixed category) |

Unrelated OS/test launch arguments are **ignored** (not rejected, not reported).

## Strictly out of scope (prerequisite only)

- **No Chinese audio fixture** is added, downloaded, or referenced.
- **No quality validation or scoring** of any transcript is introduced.
- **No changes** to the public manifest parser, clip paths/references, hash
  verification, receipt lifecycle, atomic write, error categorization, the
  `sdkSourceCommit` historical literal, permissions, AppID, or the production
  ODAIKit source.
- **No microphone / AVAudioSession / camera API**, no new dependencies, no
  network / download / build / test / device / publish / deletion / global
  config changes.

## Reporting

The report `locale` field is reported **honestly** (normalized form of the
chosen value). The existing default report **shape** is preserved — this is a
prerequisite enabling flag, **not** a dynamic-byte-equality guarantee and **not**
a behavior change for English runs. Default behavior / shape is what is retained,
not byte-for-byte equality of produced output.
