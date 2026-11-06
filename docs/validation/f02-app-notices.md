# F02 iOS App notice retention (2026-11-06)

The generated unsigned Speech Demo now preserves explicitly supplied third-party material
through Xcode's normal resources phase. This closes the local final-App retention subitem for
candidate `0.144.6-ondevice-f02.3`; F02/P4/P11 and release remain open/not-ready.

`scripts/generate-speech-demo.rb` accepts optional `ODAI_THIRD_PARTY_NOTICES_DIR`, a prepared
folder containing `NOTICE-INDEX.json` with schemaVersion1 and nonempty files[path,sha256].
It rejects missing/malformed indexes, unsafe/duplicate paths, invalid hashes, missing/extra/
changed files and symlinks before creating output. Validated bytes, including the index, are
snapshotted into `ThirdPartyNotices/` and added as one folder resource, preserving hierarchy.
Default generation has no notice resource. Re-run into a fresh output after material changes.

After [collecting the Maven notice classifier](../../probes/skia-atlas-text-cache/Skiko-ios-notices.md),
pass that collected directory when generating the isolated candidate:

```sh
rtk proxy env ODAI_THIRD_PARTY_NOTICES_DIR=/abs/collected/notices \
  ODAI_SPEECH_DEMO_DIR=/abs/fresh-demo ruby scripts/generate-speech-demo.rb
```

Evidence: `artifacts/verification/f02-app-notice-retention-20261106/` (ignored local artifacts).
The fresh SDK export is commit `aa21182bf9fa1b82bf505b46a34e10d159205268` plus three recorded
overlays: exclusive local Skiko repository, .3 version selection, and the generator change.
No frozen .2/.3 source, publication or consumer was modified. Gradle ran offline; both real
SDK Compose iOS frameworks linked (14 tasks executed). dependencyInsight selected .3.
Xcode built the basic device App with local `apple` package and CODE_SIGNING_ALLOWED=NO;
normal Copy resources retained all69 files with exact membership and SHA256. The complete
App bundle, including debug dylib, and both SDK frameworks have retained hashes.

Fresh checks:16 generator tests pass (14 invalid-material subcases);312 tool tests pass.
The first full run had three Swift compiler-cache sandbox errors; the same suite passed with
approved existing cache access. Ruby syntax and scoped git diff checks passed. Nine protected
files and production MLX lock match their prior hashes. Review is an author-only separate pass,
per solo instruction; no independent approval is claimed.

Read-only check (requires retained local artifacts):

```sh
rtk proxy python3 artifacts/verification/f02-app-notice-retention-20261106/verify.py
```

This proof covers this generated basic App's notice retention. It does not establish legal
approval, automatic embedding by every SDK consumer, Nexus remote anonymous consumption,
production dependency selection, signing/install/runtime correctness, or long-run resources.
Remaining provenance questions include generated HarfBuzz tables sourced from Unicode16,
Microsoft OpenType language tags, IANA language-subtag-registry and the SIL mapping input;
root grants and source-specific terms still need bounded review. No new source downloads.
