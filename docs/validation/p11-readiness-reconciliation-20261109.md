# P11 evidence inventory reconciliation — 2026-11-09

The new derived inventory validates structurally; **release remains not-ready**.
It resolves a stale inventory pointer, without replaying device acceptance or
approving resource/quality/calibration gaps. Original stage10/12 and deduplicated
W1/W2/W4/W5closed versusW3/W6/W7/W8open are different counts.

The old immutable summary remains at
`artifacts/verification/p11-readiness-20261108-service-transport-accepted/summary.json`,
SHA256 `dc428125199cdfceb1f9cfa32213eebdf6145995758d6fe4b231be9e2010a734`.
It no longer validates against the current checkout:231source paths are absent
Xcode compiler/intermediate/cache members and18present files have changed
(13documents,5sources). Its original failure and hashes are retained.

The derived summary at `artifacts/verification/p11-readiness-20261109-current-0940/`
retains the historic checksums of every absent member in
`evidence-reconciliation.json`, rather than fabricating files or rerunning their
build. All are individually constrained to the recorded consumer's Xcode build
intermediate/module directories. Only the SDKExplicitPrecompiledModules directory
removal is matched in the historical cleanup receipt; removal provenance of the
other missing members is **unverified**. Retained canonical build log/result,
remote manifest/input/runtime reports, cleanup receipt and167Build/Products files
remain hash-bound. These replace the inventory's reliance on disposable compiler
members, not the actual remote runtime evidence. No unique runtime trace or raw
acceptance report was removed by this reconciliation.

Mutable documents and changed source context are copied with exact hashes into
`frozen-context`. The mapping records historical and current hashes. This keeps
future management edits from invalidating a past frozen report. Freezing current
source context does **not** mean an earlier binary was tested on those new bytes;
historical per-run source/binary receipts remain authoritative.

The unchanged validator passed9rows,6271evidence references and6175source hashes.
All9rows still contain gaps; gate statusopen/finalReleaseConclusionnot-ready.
The unchanged grouped-report tool also generated11groups with the same open/
not-ready result. No validation tool, raw measurement, scoring rule or release
threshold was weakened. The manifest's readiness pointer alone was updated.

Frozen context is as of this derivation; later VAD/no-VAD measurements are in
separate current documents and do not claim to have existed in this snapshot.
W4/W5 bounded closures and current accepted resources are distinguished from W8
resource budgets. W2Nexus/Apple delivery stays closed. W6 remains expert-owned;
W7 multiperson/PAD data gaps remain original-goal blockers, not deferrals.

Evidence: `validation-result.json`, `grouped-report.json`, original drift receipt
`artifacts/verification/progress-source-reconcile-20261109-0850/readiness-drift.json`,
and original/raw inputs retained under the referenced run directories.
