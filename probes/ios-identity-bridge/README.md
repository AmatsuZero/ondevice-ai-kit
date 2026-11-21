# iOS native Identity bridge integration tests

This standalone simulator XCTest links the real exported KMP ABI and the native Apple Identity SDK. The Apple package keeps no KMP dependency; the Demo signing project is not edited. Test backends and calibrated values are synthetic and do not establish biometric accuracy.

Prerequisites: Xcode with an available arm64 iOS simulator, the repository Gradle toolchain, and Ruby with the existing `xcodeproj` gem. No new dependency installation is required.

```sh
rtk proxy ./gradlew :kmp-binding:linkDebugFrameworkIosSimulatorArm64
rtk proxy ruby scripts/generate-identity-bridge-tests.rb
rtk proxy xcrun simctl list devices available
rtk proxy xcodebuild \
  -project artifacts/verification/ios-identity-bridge/IdentityBridgeTests.xcodeproj \
  -scheme IdentityBridgeTests \
  -destination 'platform=iOS Simulator,id=DF2DC7EA-257B-4E5F-9AFD-EC4BD2ED6D7F' \
  -derivedDataPath artifacts/verification/ios-identity-bridge/Build \
  CODE_SIGNING_ALLOWED=NO test
```

The destination above is the simulator used for the recorded validation; replace its ID with an available local device. The generator refuses existing output. To generate another independent project, set `ODAI_IDENTITY_BRIDGE_TEST_DIR` to a fresh ignored path under `artifacts/verification/`. Existing generated projects can be reused for subsequent source builds/tests.

The adapter source belongs to the host KMP integration layer. A host compiles `kmp-binding/apple/IOSIdentityBridge.swift` with `ODAIKitBinding` and `ODAIKitIdentity`, supplies the real stores/engines/backend registry and opaque probe resolver, and explicitly closes the adapter before disposing its borrowed dependencies. No production backend is enabled by this test harness.

For a Demo umbrella host, build `demo/shared` and generate a fresh harness with `ODAI_IDENTITY_BRIDGE_KMP_MODULE=ODAIKitDemo`; the generator adds `ODAI_DEMO_BINDING` and links only `ODAIKitDemo`. The same adapter tests cover both module selections.
