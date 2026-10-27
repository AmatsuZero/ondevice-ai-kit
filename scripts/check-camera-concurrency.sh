#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
camera_sdk=$(xcrun --sdk iphonesimulator --show-sdk-path)
camera_products=${ODAI_CAMERA_SWIFT_PRODUCTS:-artifacts/iOSDemo/Build/Products/Debug-iphonesimulator}
mkdir -p artifacts/verification/camera-concurrency-module-cache
for camera_mode in 5 6; do
    camera_log="artifacts/verification/camera-concurrency-swift${camera_mode}.log"
    if ! xcrun swiftc -typecheck -swift-version "$camera_mode" \
        -strict-concurrency=complete -warnings-as-errors \
        -target arm64-apple-ios15.0-simulator -sdk "$camera_sdk" \
        -module-cache-path artifacts/verification/camera-concurrency-module-cache \
        -I "$camera_products" \
        -F demo/shared/build/bin/iosSimulatorArm64/debugFramework \
        demo/iosApp/CameraSource.swift demo/iosApp/DemoController.swift \
        demo/iosApp/IOSFaceBridge.swift demo/iosApp/CameraLifecycle.swift \
        demo/iosApp/PhotoSubmission.swift \
        probes/ios-camera-lifecycle/CameraConcurrencyContract.swift > "$camera_log" 2>&1; then
        cat "$camera_log"
        exit 1
    fi
    # Some imported pre-concurrency diagnostics remain warnings even with Werror.
    if grep -Eq ': (warning|error):' "$camera_log"; then
        cat "$camera_log"
        exit 1
    fi
done
echo 'Camera/controller Swift5+Swift6 strict transfer checks passed (iOS15 deployment target).'
