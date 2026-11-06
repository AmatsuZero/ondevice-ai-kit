# F10 local dataset manifest preflight

The first engineering deliverable from the [calibration exploration](f10-calibration-exploration-20261107.md)
is `tools/identity_calibration_manifest.py`. It reads an explicit local manifest,
checks declared use, hashes every referenced evidence/media file, and rejects
calibration/test subject or media-group leakage. Identity enrollment and probe can
share subjects within one split but cannot share a subject/session or source media
group. Passive PAD labels and PAI groups are separate from identity roles.

```sh
rtk proxy python3 -m tools.identity_calibration_manifest /path/to/dataset/manifest.json
rtk proxy python3 -m unittest tools.tests.test_identity_calibration_manifest -v
```

All file references are relative to the manifest directory. The validator refuses
symlinks, special files, traversal and digest mismatches, does not decode media,
and writes only an aggregate JSON report to stdout. Errors produce nonzero exit and
a generic diagnostic. Keep reports under the same controls as the experiment:
dataset/model revisions, aggregate PAI counts and digests can still be sensitive.

The exact Schema 1 is in the [design](../superpowers/specs/2026-11-07-calibration-manifest-design.md).
Use dataset-scoped pseudonyms for subject/session/group IDs; no names, embeddings
or image bodies belong in the JSON. Pseudonyms do not make data anonymous.
Each evidence/media reference is `{"path":"relative/file","sha256":"64 lowercase hex"}`.
`pai` is JSON null for bona-fide inputs and a token such as `print` for an attack.
The tool supports verification, identification and passive-pad data preflight;
these are not full comparison/gallery/search protocols or score formats.

`authorization.allowedPurposes` is a declaration backed by a local evidence file.
It must include the selected `local-calibration` or `local-evaluation` purpose.
Redistribution permission is checked only if dataset redistribution is requested.
The tool neither interprets the license/EULA nor verifies the signer's authority,
consent, declared grouping, subjects or label correctness. A real dataset still
requires applicable human authorization review before use.

Every success says `validationStatus=pass-bounded`,
`authorizationStatus=declared-scope-only`, `calibrationStatus=unverified`.
The canonical manifest SHA-256 binds the exact declarations for subsequent score
tools; it is not a signature or trusted source assertion. The model digest is a
declared binding, not a model-file verification performed by this tool.

No calibration/evaluation data has been imported by this change. Tests use tiny
synthetic byte files, never faces. Next work: comparison/search/presentation score
schemas and integrity checks, offline FAR/FRR/probe-level FPIR and per-PAI PAD
metrics, then reviewed candidate-policy/report binding. Production continues to
reject unverified policies with `THRESHOLD_UNVERIFIED`; F10, P4/P11 and release
readiness remain open. This tool does not replace those gates.

2026-11-07验证：23项聚焦测试、合成文件CLI smoke通过；与Nexus新工具合计32项聚焦，
最终完整tools489项/47.714秒通过。Copilot限定源码终审的Unicode Cc URL漏检已用实际
RED→GREEN修正；本机Ollama意见逐条处置，没有以其不支持的建议修改代码。
全部证据与当前源码摘要保留于`artifacts/verification/f10-calibration-manifest-20261107/`。
这些结果仅证明工具边界，不证明真实身份或PAD性能。
