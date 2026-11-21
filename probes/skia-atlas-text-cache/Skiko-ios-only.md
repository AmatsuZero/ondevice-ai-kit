# Fixed Skiko iOS-only publication

Apply `Skiko-ios-only.patch` to a fresh local export of Skiko
`9a5b398bb2044fff7e7a84fbfd6f4b803e4427c0`. This patch is for the **iOS-only** artifact;
do not use the patched source to publish JVM, Android or Web variants.
It is separate from the Skia TLS-owner patch and changes no native C++ source.

With only native targets, KGP compiles commonMain as shared Native metadata. The fixed
source's JVM optional annotations fail there. This patch removes `@JvmStatic`, `@JvmInline`
and the optional-expectation marker; the existing native `ExternalSymbolName` actual remains.
It also stops creating an AWT runtime publication when AWT is disabled. No compiler
diagnostics are suppressed and no generated metadata is edited.

Use existing Gradle8.14.3, JDK21, KGP2.2.20 caches and the fixed Skia clean TLS-owner output
directory containing both `out/Release-ios-arm64` and `out/Release-iosSim-arm64`:

```sh
rtk proxy git apply --check /abs/Skiko-ios-only.patch
rtk proxy git apply /abs/Skiko-ios-only.patch
# From the exported source's skiko/ directory:
rtk proxy ./gradlew --offline --no-daemon --console=plain --max-workers=2 \
  publishAllPublicationsToBuildRepoRepository \
  -Pdeploy.version=0.144.6-ondevice-f02.2 -Pdeploy.release=true \
  -Pskia.dir=/abs/fixed-skia-clean-candidate \
  -Pskiko.awt.enabled=false -Pskiko.android.enabled=false -Pskiko.wasm.enabled=false \
  -Pskiko.native.enabled=false -Pskiko.native.ios.enabled=false \
  -Pskiko.native.ios.arm64.enabled=true -Pskiko.native.ios.simulatorArm64.enabled=true
```

`deploy.release=true` is required to avoid the upstream default SNAPSHOT suffix.
Use a fresh output directory; do not replace the existing f02.1 archive. The output must
contain exactly `skiko`, `skiko-iosarm64`, and `skiko-iossimulatorarm64` at the new version.
Copy their exact version directories to the candidate repository; include checksum sidecars,
sources, C interop klibs, and host-specific metadata. Validate file hashes, all six root
available-at references and same-group POM dependencies before consumption.

Consumers may use this version for their iOS graph only. No production dependency override
is introduced here. An isolated KGP2.4.0 consumer verifies common metadata, geometry factories,
ColorMatrix and native raster calls by linking both iOS frameworks. These are compile/link
checks, not runtime or new device resource acceptance.

Evidence: `artifacts/verification/f02-ios-publication-20261106/`. License/notice attachment,
remote anonymous consumer validation, and broader production acceptance remain separate gates.

The frozen `.2` source JARs do not yet include prominent modification notices. Apply the
supplemental `Skiko-ios-source-notices.patch` **after** the main patch for the next candidate,
and use a fresh version and output. It adds a dated modification comment to all seven changed
files; its apply check passes against the retained `.2` source. Do not replace frozen `.2`
files. Final notices, including ICU and file-specific/Unicode obligations, still need attachment.

Read-only frozen-evidence check (existing ignored artifacts are required):

```sh
rtk proxy python3 artifacts/verification/f02-ios-publication-20261106/verify.py
```

This verifies exact archive/repository membership and hashes, checksum sidecars, references,
all 40 embedded static archives, four linked framework identities and protected inputs.
`license-progress.json` records preparation after the frozen build summary; no compliance or
remote acceptance is inferred from this check.
