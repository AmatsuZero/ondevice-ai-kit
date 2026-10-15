# Toolchain probe (2026-10-15)

Pinned: Kotlin/KGP 2.4.0, Compose MPP 1.11.1, AGP 9.1.0, Gradle 9.4.1, bundled ML Kit face-detection 16.1.7. iOS15 / Android26 baseline; compileSdk36.

Installed Xcode27.0 (27A266a), Swift6.4. Kotlin compatibility matrix lists Xcode26.4; Xcode27 support is a local probe, not an upstream guarantee. The cached AGP9.2.1 was rejected because KGP2.4.0's matrix caps AGP9.1.0.

No registered JDK found. Toolbox JBR25 is stripped (missing java.instrument), cannot run Gradle. Provision a checksum-verified JDK21 under ignored artifacts/toolchains, without global environment changes. Download and build status will be recorded after actual completion.

Official references:
- https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html
- https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
- https://developer.android.com/kotlin/multiplatform/plugin
- https://developer.android.com/build/releases/agp-9-1-0-release-notes
- https://developers.google.com/ml-kit/vision/face-detection/android

JDK fallback: OpenJDK21.0.2 GA archive from download.java.net; SHA256 b3d588e16ec1e0ef9805d8a696591bd518a5cea62567da8f53b5ce32d11d22e4 verified. Temurin/GitHub transfer failed; no global changes. Later process inspection also found an SDKMAN JDK17 under the Homebrew keg; it was not registered by java_home.
Gradle wrapper SHA256 2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb from official services.gradle.org. Existing local global init script rewrites Central to Aliyun; no changes to that global file. Proxy helper reuses existing HTTPS_PROXY only for this invocation.

PASS: Xcode unsigned NativeConsumer simulator build targeting iOS15; KMP simulator test (injected backend observed), exported framework compilation; Android Core2 + ModelStore2 + Face1 unit tests; Swift conforming class typecheck against exported FaceBridge. Swift conformance probe first failed missing backendId(), then passed with method. The KMP test separately failed with placeholder return then passed delegation. These are compile/unit/simulator probes, not iOS15 device evidence.
