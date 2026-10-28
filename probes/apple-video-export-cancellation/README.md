# AppleVideo export cancellation probe

This diagnostic-only probe copies the current `AppleVideoAudioMuxer` into a new ignored build directory, adds status observation around the real `AVAssetExportSession`, and builds an isolated iOS app. It does not modify the shipping mux, Demo project, signing configuration, or input fixtures.

Prepare a directory that does not exist (its parent must exist):

```sh
probe_dir=artifacts/verification/video-export-cancellation-new
rtk proxy python3 probes/apple-video-export-cancellation/prepare.py "$probe_dir"
```

The directory contains frozen mux/profile/probe source, the two synthetic
fixtures, and a source-hash manifest. Preparation refuses an existing directory
or symlink, missing inputs, or an ambiguous export insertion point.

Run the macOS version:

```sh
rtk proxy swiftc -parse-as-library -swift-version 5 -D EXPORT_CANCELLATION_CLI \
  "$probe_dir"/*.swift -o "$probe_dir/probe"
rtk proxy "$probe_dir/probe" "$probe_dir" "$probe_dir/host-run"
```

For the negative control, add `-D EXPORT_CANCELLATION_NEGATIVE_CONTROL` to the
compile command and use another executable/output directory. It suppresses the
probe's `Task.cancel()` call: the suite must exit 1, with native status 3
(`completed`) and `callerCancellationError=false` in `cancel-runs.json`.

Generate the iOS project with the existing project Ruby/xcodeproj environment.
Set `ODAI_EXPORT_PROBE_TEAM` to the existing signing team:

```sh
rtk proxy /Users/daubert/.rvm/wrappers/ruby-3.4.11/ruby \
  probes/apple-video-export-cancellation/device-project.rb "$probe_dir"
rtk proxy xcodebuild -project "$probe_dir/ExportCancellationProbe.xcodeproj" \
  -scheme ExportCancellationProbe -configuration Debug -sdk iphoneos \
  -destination generic/platform=iOS -derivedDataPath "$probe_dir/device-build" build
```

The app deliberately reuses the existing `io.ondeviceai.probe.videosr` probe
bundle ID and its installed provisioning. Installing it replaces that dedicated
probe; the normal Demo is separate. Do not pass `-allowProvisioningUpdates`.
Install the generated app and launch it with `--export-cancellation-suite`.
Copy only `Documents/export-cancellation-<runId>.json` and `Documents/<runId>`
from its container. The report records native status, caller error, output
absence, input hashes, and same-process AAC reentry. Stop the finished probe and
launch the normal Demo without arguments afterward.

The probe intentionally cancels on the first `.exporting` observation. A report
with `waiting → exporting → cancelled`, `CancellationError`, no output, and
unchanged inputs demonstrates cancellation after native export starts. KVO
`status` is documented by the SDK headers; its deprecation warnings (and the
legacy reader warnings) are expected in this diagnostic, which reads the exact
terminal native state. It does not prove cancellation after non-zero progress or
removal of a file that already contains media bytes. Those remain unverified.
