#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p artifacts/verification
swift test --package-path apple > artifacts/verification/swift-tests.log 2>&1 || { tail -60 artifacts/verification/swift-tests.log; exit 1; }
# Some Swift toolchains print failed target summaries despite a successful process exit.
if grep -Eq "Some test targets reported failures|Test Suite .* failed|error:" artifacts/verification/swift-tests.log; then tail -60 artifacts/verification/swift-tests.log; exit 1; fi
tail -15 artifacts/verification/swift-tests.log
python3 scripts/gradle-local.py :kmp-binding:iosSimulatorArm64Test :kmp-binding:linkDebugFrameworkIosSimulatorArm64 :demo:shared:iosSimulatorArm64Test :demo:shared:linkDebugFrameworkIosSimulatorArm64 --console=plain
python3 -m unittest discover -s tools/tests -p "test_*distribution*.py" -v
sh scripts/check-swift-bridge.sh
sh scripts/check-camera-lifecycle.sh
sh scripts/check-photo-submission.sh
sh scripts/check-ios-photo-orientation.sh
sh scripts/check-ios-asr-probe.sh
xcodebuild -project examples/ios-native/NativeConsumer.xcodeproj -scheme NativeConsumer -destination 'generic/platform=iOS Simulator' -derivedDataPath artifacts/NativeConsumer CODE_SIGNING_ALLOWED=NO build > artifacts/verification/native-ios-build.log 2>&1 || { tail -50 artifacts/verification/native-ios-build.log; exit 1; }
xcodebuild -project demo/iosApp/OnDeviceAIDemo.xcodeproj -scheme OnDeviceAIDemo -destination 'generic/platform=iOS Simulator' -derivedDataPath artifacts/iOSDemo CODE_SIGNING_ALLOWED=NO build > artifacts/verification/demo-ios-build.log 2>&1 || { tail -50 artifacts/verification/demo-ios-build.log; exit 1; }
sh scripts/check-camera-concurrency.sh
# Binary consumer gates use a fresh package output supplied by the caller.
if [ -n "${ODAI_MULTIMODAL_CONSUMER_PROJECT:-}" ]; then
 for platform in 'generic/platform=iOS' 'generic/platform=iOS Simulator'; do
  case "$platform" in *Simulator*) suffix=simulator ;; *) suffix=device ;; esac
  xcodebuild -project "$ODAI_MULTIMODAL_CONSUMER_PROJECT" -scheme MultimodalBinaryConsumer -destination "$platform" -derivedDataPath "artifacts/MultimodalConsumer-$suffix" CODE_SIGNING_ALLOWED=NO build > "artifacts/verification/multimodal-consumer-$suffix.log" 2>&1 || { tail -50 "artifacts/verification/multimodal-consumer-$suffix.log"; exit 1; }
 done
else
 echo 'Multimodal binary consumer not checked: set ODAI_MULTIMODAL_CONSUMER_PROJECT after packaging.'
fi
echo 'Apple host tests, native consumer, bridge and Demo builds passed. Device gates remain separate.'
