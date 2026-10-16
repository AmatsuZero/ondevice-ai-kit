#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p artifacts/verification
swift test --package-path apple > artifacts/verification/swift-tests.log 2>&1 || { tail -60 artifacts/verification/swift-tests.log; exit 1; }
# Some Swift toolchains print failed target summaries despite a successful process exit.
if grep -Eq "Some test targets reported failures|Test Suite .* failed|error:" artifacts/verification/swift-tests.log; then tail -60 artifacts/verification/swift-tests.log; exit 1; fi
tail -15 artifacts/verification/swift-tests.log
python3 scripts/gradle-local.py :kmp-binding:iosSimulatorArm64Test :kmp-binding:linkDebugFrameworkIosSimulatorArm64 :demo:shared:iosSimulatorArm64Test :demo:shared:linkDebugFrameworkIosSimulatorArm64 --console=plain
sh scripts/check-swift-bridge.sh
sh scripts/check-camera-lifecycle.sh
sh scripts/check-photo-submission.sh
xcodebuild -project examples/ios-native/NativeConsumer.xcodeproj -scheme NativeConsumer -destination 'generic/platform=iOS Simulator' -derivedDataPath artifacts/NativeConsumer CODE_SIGNING_ALLOWED=NO build > artifacts/verification/native-ios-build.log 2>&1 || { tail -50 artifacts/verification/native-ios-build.log; exit 1; }
xcodebuild -project demo/iosApp/OnDeviceAIDemo.xcodeproj -scheme OnDeviceAIDemo -destination 'generic/platform=iOS Simulator' -derivedDataPath artifacts/iOSDemo CODE_SIGNING_ALLOWED=NO build > artifacts/verification/demo-ios-build.log 2>&1 || { tail -50 artifacts/verification/demo-ios-build.log; exit 1; }
echo 'Apple host tests, native consumer, bridge and Demo builds passed. Device gates remain separate.'
