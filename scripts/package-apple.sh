#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
artifacts_root=${ODAI_ARTIFACTS_DIR:-artifacts}
products='ODAIKitCore ODAIKitModelStore ODAIKitFace ODAIKitAppleVision ODAIKitSpeech ODAIKitAppleSpeech'
for platform in iphoneos iphonesimulator; do
 if [ -e "$artifacts_root/archives/$platform.xcarchive" ]; then echo "Archive exists; set ODAI_ARTIFACTS_DIR to a fresh directory" >&2; exit 1; fi
done
for product in $products; do
 if [ -e "$artifacts_root/xcframeworks/$product.xcframework" ]; then echo "Output exists; set ODAI_ARTIFACTS_DIR to a fresh directory" >&2; exit 1; fi
done
mkdir -p "$artifacts_root/archives" "$artifacts_root/xcframeworks"
for platform in iphoneos iphonesimulator; do
 if [ "$platform" = iphoneos ]; then destination='generic/platform=iOS'; else destination='generic/platform=iOS Simulator'; fi
 xcodebuild -project apple/OnDeviceAIKit.xcodeproj -scheme SDKDistribution -configuration Release -destination "$destination" -archivePath "$artifacts_root/archives/$platform.xcarchive" CODE_SIGNING_ALLOWED=NO archive > "$artifacts_root/archives/$platform.log" 2>&1 || { tail -40 "$artifacts_root/archives/$platform.log"; exit 1; }
done
for product in $products; do
 output="$artifacts_root/xcframeworks/$product.xcframework"
 xcodebuild -create-xcframework -framework "$artifacts_root/archives/iphoneos.xcarchive/Products/Library/Frameworks/$product.framework" -framework "$artifacts_root/archives/iphonesimulator.xcarchive/Products/Library/Frameworks/$product.framework" -output "$output"
done
