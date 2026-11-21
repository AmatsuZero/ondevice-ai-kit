#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
sdk=$(xcrun --sdk iphonesimulator --show-sdk-path)
xcrun swiftc -typecheck -target arm64-apple-ios15.0-simulator -sdk "$sdk" -F kmp-binding/build/bin/iosSimulatorArm64/debugFramework examples/bridge-probe/SwiftBridgeProbe.swift
