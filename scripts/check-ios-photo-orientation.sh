#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p artifacts/verification/clang-cache artifacts/verification/swift-cache
CLANG_MODULE_CACHE_PATH="$PWD/artifacts/verification/clang-cache" \
SWIFT_MODULE_CACHE_PATH="$PWD/artifacts/verification/swift-cache" \
swiftc -parse-as-library tools/tests/IOSPhotoOrientationChecks.swift \
    -o artifacts/verification/ios-photo-orientation-checks
artifacts/verification/ios-photo-orientation-checks
