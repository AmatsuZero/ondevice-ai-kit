# Demo third-party notice resource delivery

Goal: preserve explicitly supplied, hash-indexed third-party material in the generated iOS App
using the normal Xcode resources phase. Default generation remains unchanged.

Use ODAI_THIRD_PARTY_NOTICES_DIR for a prepared directory with NOTICE-INDEX.json (schema1,
files[path,sha256]). Validate exact file set/hashes, reject symlinks, unsafe paths and missing
inputs before output creation. Snapshot material as ThirdPartyNotices inside the fresh generator
output; add one folder resource reference to preserve relative paths in the built App.

No dependency/version/production-lock/signing/device changes. Existing isolated .3 SDK consumer
gets the same source generator overlay and collected69-file material. Build unsigned device App
with fixed local Swift packages and actual .3 KMP framework; compare App resource hashes exactly.
This proves retention for this generated candidate, not remote distribution/legal approval.

- [x] RED/GREEN generator tests: opt-in resource snapshot, missing/malformed/tampered material,
  unsafe paths/symlinks, default absence, preserved original project.
- [x] Implement generator input validation and normal folder resource phase.
- [x] Targeted generator suite and actual isolated .3 SDK consumer dual-iOS framework links.
- [x] Unsigned generated App build and exact69-file resource check; no install or signing.
- [x] Author review, protected hashes and readiness/handoff update; commit after final checks.

User autonomous solo instructions apply; no new design or reviewer permission handoff.

Execution ledger (base aa21182):
- Generator RED: corrected subtest output isolation, then two tests fail because the option is ignored
  (14 invalid inputs accepted, valid snapshot absent). GREEN: all16 generator tests pass.
- Current task: offline fresh SDK export at aa21182, exclusive .3 repository/version overlay and
  generator overlay. Frozen .2/.3 consumers are read-only.
- Ruling: create a fresh SDK consumer rather than reuse the frozen .3 consumer — preserve prior
  evidence; extra disk/build cost only. No independent reviewer per the user's solo instruction.
- Full tool suite initially309/312 pass; three Swift subprocess errors are cache sandbox access,
  retained in tools-tests.log. Re-running with approved existing cache access.
- Fresh .3 SDK device/simulator links passed:14 executed tasks in224 seconds; dependencyInsight
  selected .3. Normal unsigned device App build passed,69 exact notices and74 total App files.
- Full suite rerun with approved existing-cache access:312/312 pass. Ruby syntax OK.
- Separate author review: inspected validation before output, exact resource hierarchy, complete
  App/debug-library identity, optional/default behavior, rejection tests and source overlays.
  No Critical/Important findings. No deferred minors. Independent approval remains pending.
- Retained receipts verify:69notices/74App files/2frameworks/987SDK inputs; frozen .2/.3 verify
  unchanged. Readiness1556evidence references/1138hashes and8benchmark groups pass. Local commit next.
