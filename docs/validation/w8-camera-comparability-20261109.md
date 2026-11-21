# W8 camera resource evidence and `.6` comparability

Parent check on 2026-11-09: the historical camera measurements remain valid for their recorded binaries. **The repaired Skia libraries match between `.1` and `.6`; the whole native library set and current App do not have byte equivalence.** W2 delivery stays closed. W8 camera GPU, energy and production budget remain open.

The free CBC hy3 supplied-context audit completed in 334.045 seconds and wrote three files. The parent verified their byte counts and SHA-256 values. Its original files remain unchanged under `.omx/artifacts/cbc-hy3-w8-camera-source-comparability-20261109-1044/candidate/`. This report limits the accepted conclusions using later evidence that was absent from that helper's context.

## Evidence retained and freshly checked

- The 2026-10-27 controller and rendered-UI runs contain real physical lifecycle and RSS observations. The controller receipt explicitly measures `resident_size`, not peak or footprint. Allocation and Leaks evidence comes from separate traces; these scopes must not be merged into one claimed measurement. A later `camera_isolation_review` approved its bounded scope with zero findings, superseding the earlier 429/no-verdict entry; see [P4 source isolation](p4-camera-stability.md).
- The 2026-11-03 recorder-free `.1` App completed ten fresh page cycles, 86 samples and ten 30-second active windows. All observed page objects were released at the final idle observations. The page-absent RSS first/last difference was +10,731,520 bytes. Its same-PID Leaks trace lasted 870.750431 seconds. Empty Leaks details do not prove zero leaks; final live TLS-registration/cache rows had no observed holder-thread exit. The parent rehashed all 11 raw/source/binary entries declared in `camera-page-versioned10b-summary.json`; all matched. See [P4 fixed-version run](p4-atlas-text-cache.md).
- Both `.1` Klibs match their historical publication receipt. Both `.6` Klibs match the frozen 115-file delivery plan in size and SHA-256. Reading the actual Klibs without extraction compared 21 included native archives per architecture: **20/21 are identical in each architecture, including `libskia.a`**. Only `skiko-native-bridges-ios-arm64.a` / `skiko-native-bridges-iosSim-arm64.a` differ. This does not establish why the bridges differ or whether their runtime behavior differs.
- Five of seven files in the historical camera source manifest still match: `CameraSource`, `CameraLifecycle`, `IOSFaceBridge`, `PhotoSubmission` and `ThreadInventory`. `DemoController` and `CameraPageResourceValidation` differ. Identical Skia bytes therefore do not prove current whole-App runtime or resource equivalence.

The Skia archive SHA-256 values in both versions are `c90798bb2ce3fd7aff7f7de0a1ca9e37c19d837749c05b4010b1742dcccc845f` (device) and `5f4790af17452aaac834a0b80bd9afa2b503ff9a55ce737a9a93eaf1b9dab1ca` (simulator).

## Disposition and next gate

Accept the helper's distinction between historical runtime and the supplied `.6` build receipts. Reject its project-wide “no device run of any kind” wording, missing-review claim and combined RSS/footprint/Leaks description. Its rebuild/rerun suggestions are proposals, not executed acceptance work. Two supplied build receipts having no device run does not mean the project has no device evidence.

The exact next gate is to identify an existing current-source `.6` camera App, or prepare a new isolated App if none has adequate provenance, before deciding the minimum distinct device window. Do not repeat upload, fresh-cache consumption, the old ten-cycle `.1` run or native cache regression. Preserve the old measurements as historical evidence. Any new camera window requires device coordination and must record its own source/framework/binary identity. No new camera session, image capture, device install or resource recording occurred in this comparability check.

Reproduce this parent check with `rtk proxy python3 artifacts/verification/w8-camera-comparability-20261109-1100/verify.py`. Its `parent-comparison.json` retains the two native inventories, source comparisons, 11 raw hash results and helper exclusions. This is not a camera budget pass or final release approval.
