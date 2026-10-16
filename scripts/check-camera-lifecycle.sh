#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p artifacts/verification
swiftc demo/iosApp/CameraLifecycle.swift tools/tests/CameraLifecycleChecks.swift -o artifacts/verification/camera-lifecycle-checks
artifacts/verification/camera-lifecycle-checks
