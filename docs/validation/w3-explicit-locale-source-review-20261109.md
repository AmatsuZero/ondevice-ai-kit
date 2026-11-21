## Code Review Summary

**Files Reviewed:** 9
**Total Issues:** 0

### By Severity

- CRITICAL: 0 (must fix)
- HIGH: 0 (should fix)
- MEDIUM: 0 (consider fixing)
- LOW: 0 (optional)

### Scope and Evidence Boundary

This is a source/documentation review of the W3 explicit-locale control only.
It does not approve a Chinese fixture, Chinese recognition quality, a device
run, installation, model execution, or W3 closure. The retained build receipt
is parent-supplied evidence; this reviewer did not run a build, test, device,
network, or model operation.

### Findings

None in the reviewed source slice.

- The runner accepts only exact `en-US`, exact `zh-CN`, or an absent `locale`.
  Any other supplied value—including case or separator variants—throws before
  report-directory/file reservation and before model/native work. The gate is
  deliberately stricter than the current Sherpa normalization API, which is
  correct for this caller contract. (`probes/android-natural-file-asr/NaturalAsrInstrumentation.kt:98-109`,
  `probes/android-natural-file-asr/NaturalAsrInstrumentation.kt:118-138`,
  `android/backend-sherpa-asr/src/main/kotlin/io/ondeviceai/sherpaasr/SherpaSession.kt:62-69`)
- Absent `locale` selects `en-US`; the report gains a `locale` field only when
  the caller explicitly supplied the parameter. “Byte-equivalent” here is
  limited to preservation of the default report shape and behavior: it does
  not claim dynamic per-run fields such as run ID, PID, or timings are literal
  byte-for-byte identical across separate executions.
  (`probes/android-natural-file-asr/NaturalAsrInstrumentation.kt:101-109`,
  `probes/android-natural-file-asr/NaturalAsrInstrumentation.kt:140-155`)
- The selected exact value is passed to the existing real file-transcribe call.
  That call continues through the existing backend/session contract rather than
  a reporting-only or fallback path. (`probes/android-natural-file-asr/NaturalAsrInstrumentation.kt:267-273`,
  `android/backend-sherpa-asr/src/main/kotlin/io/ondeviceai/sherpaasr/SherpaAsrBackend.kt:47-49`,
  `android/backend-sherpa-asr/src/main/kotlin/io/ondeviceai/sherpaasr/SherpaAsrBackend.kt:112-121`)
- The candidate receipt confines the integration to four localized replacements
  and records the integrated source SHA-256
  `9f6e64f36bddea5fa30744de9a6952d18b6ac27689c1f72416d68396048be25e`.
  It declares no production-source change and no Chinese fixture/acceptance.
  The integration receipt independently records those four replacement hashes
  and the same source SHA. (`.omx/artifacts/cbc-hy3-w3-explicit-locale-20261109-0108/candidate/locale-replacement.json:1`,
  `.omx/artifacts/cbc-hy3-w3-explicit-locale-20261109-0108/candidate/DELIVERY.md:11-33`,
  `artifacts/verification/w3-locale-gate-20261109-0118/integration.json:1`)
- The fixed English assets, clip IDs, reference text, sample counts, third-clip
  split, 160000-sample bound, resource-window behavior, and close/clear/persist
  handling remain outside the locale edits. This preserves the existing
  baseline instead of reclassifying an English input under `zh-CN` as Chinese
  acceptance. (`probes/android-natural-file-asr/NaturalAsrInstrumentation.kt:51-65`,
  `probes/android-natural-file-asr/NaturalAsrInstrumentation.kt:344-407`,
  `probes/android-natural-file-asr/NaturalAsrInstrumentation.kt:409-421`,
  `probes/android-natural-file-asr/README.md:42-55`)

### Root-Cause Guard

The implementation fails invalid caller locale values explicitly at the primary
argument boundary. It adds no silent default for malformed input, no alternate
decode path, and no diagnostic-suppressing fallback. The default remains only
the documented absence case.

### Validation Status

The supplied offline build receipt records an exit code of 0 in 11.219 seconds
and says production source was unchanged. It is not device/runtime acceptance
evidence. Installation and execution remain pending the parent’s separate
acceptance process. (`artifacts/verification/w3-locale-gate-20261109-0118/build-result.json:1`)

### Recommendation

APPROVE_SLICE

Approval is limited to strict explicit-locale control and its reporting
semantics. It does not approve Chinese-language behavior, quality, fixture
coverage, thresholds, or any broader W3 acceptance claim.
