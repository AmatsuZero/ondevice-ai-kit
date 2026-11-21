# F02 iOS publication source repair

Goal: produce the existing fixed Skiko/Skia TLS-owner fix as a complete, normally generated
iOS-arm64/device+simulator Maven publication. Internal closed-source project distribution does
not replace upstream notices. No upload or production dependency change belongs to this repair.

Observed cause: KGP classifies a source set used only by native targets as a shared native
compilation. Skiko commonMain contains optional JVM annotations and an optional SymbolName
expect annotation, rejected by that compilation (fresh offline baseline: 1,022 errors).
Official cached KGP source: KotlinMetadataTargetConfigurator.createMetadataCompilation and
KotlinSourceSet.isNativeSourceSet, version2.4.0; baseline uses fixed2.2.20.

Approaches: completing desktop runtime distribution expands six platform obligations; using
Android/Web as a metadata anchor adds unsupported delivery obligations. The selected agent
implementation choice is a source patch dedicated to iOS: remove commonMain JVM annotations
and make ExternalSymbolName a required expect with its existing native actual. Validate this
hypothesis before retaining it. Do not rewrite generated POM/module metadata or suppress diagnostics.

Use an isolated local source export at Skiko9a5b398bb2044fff7e7a84fbfd6f4b803e4427c0,
Kotlin2.2.20 and existing fixed Skia22f58c9fd43d55bde818821c04b48fda5d7ec939 outputs.
Disable AWT publication creation when AWT is disabled, at source configuration level.
Keep JVM/Android/Web out of the resulting metadata and require every same-version coordinate
reference to resolve locally. Native API behavior must remain unchanged; JVM ABI is outside
this explicitly iOS-only artifact. An independently linked consumer must validate source-set
metadata as well as the actual klibs before any adoption claim.

Standing user instructions authorize solo local implementation without extra approval turns.
No new dependencies/downloads/devices/capture, no reviewer retries, no production lock or
protected-file changes. Keep old evidence and archive immutable; use a fresh candidate version.

Success: normal Gradle publication for root and both iOS targets, complete references, real
consumer compile/link, reproducible patch and provenance. License attachment work remains
separate until fixed publication scope is proven; no release-ready claim from compilation alone.
