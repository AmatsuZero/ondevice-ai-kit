#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p artifacts/archives artifacts/xcframeworks
for platform in iphoneos iphonesimulator; do
 if [ "$platform" = iphoneos ]; then destination='generic/platform=iOS'; else destination='generic/platform=iOS Simulator'; fi
 xcodebuild -project apple/OnDeviceAIKit.xcodeproj -scheme SDKDistribution -configuration Release -destination "$destination" -archivePath "artifacts/archives/$platform.xcarchive" CODE_SIGNING_ALLOWED=NO archive > "artifacts/archives/$platform.log" 2>&1 || { tail -40 "artifacts/archives/$platform.log"; exit 1; }
done
for product in ODAIKitCore ODAIKitModelStore ODAIKitFace ODAIKitAppleVision; do
 output="artifacts/xcframeworks/$product.xcframework"
 if [ -e "$output" ]; then echo "Output exists: $output; use a fresh artifacts output directory" >&2; exit 1; fi
 xcodebuild -create-xcframework -framework "artifacts/archives/iphoneos.xcarchive/Products/Library/Frameworks/$product.framework" -framework "artifacts/archives/iphonesimulator.xcarchive/Products/Library/Frameworks/$product.framework" -output "$output"
done
