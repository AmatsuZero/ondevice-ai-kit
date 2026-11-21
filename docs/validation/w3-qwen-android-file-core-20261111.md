# Android Qwen two-stage core: local acceptance

The dependency-free isolated Kotlin case runner is integrated. It implements strict empty auto-language admission followed by a fresh forced-language stream over the identical full PCM. This is source/host evidence only: no new Android inference, quality or resource acceptance is claimed.

- Final external candidate: `probes/android-qwen-file/QwenTwoStageCore.kt`, SHA256 `2d14b1a3ed7c1f1c8a7ba9bb38379d7a25459e326cf2458e38140d365b57eaba`.
- Parent actual Kotlin compilation succeeded; 42 groups / 264 assertions passed. Covers zh-CN/en-US, exact empty versus whitespace, fresh streams, full PCM identity, both-stage native faults, actual dispatch counters, input mutation, persistence/clock/gate failures and release faults.
- Initial candidate passed the original 38 groups / 224 assertions. Added review regressions exposed premature stage completion and missing post-stage foreground gates. The first external correction still continued after captured release failures. Both rejected versions and all RED results are retained; final correction stops further work and preserves cleanup errors.
- Three bounded free hy3 tasks completed in 423.993s, 202.207s and 145.578s. Actual task-directory write restrictions passed; terminal provider results and tool events were checked, with no observed tool errors. These durations are helper generation, not ASR runtime.
- Final candidate was copied unchanged. The primary agent authored the acceptance checks and reviewed the bytes; no native child or primary implementation substitution occurred.

Local evidence: `.omx/artifacts/w3-qwen-file-20261111/core-{red,candidate-first,review-red,corrected,correction-release-red,final}/` and `helpers/`. The helper original prompts, raw events, candidates, sandbox checks and parent dispositions remain immutable.

The separate Android wrapper, host lifecycle/failure checks, build/lint/package verification, independent review and new fixed public-file device run remain pending. W3 stays open; ordinary Apps/models and production code were not changed. The existing TECNO load-only result is not replayed or expanded into inference acceptance.
