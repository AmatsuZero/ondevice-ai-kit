#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p artifacts/verification
CLANG_MODULE_CACHE_PATH="${CLANG_MODULE_CACHE_PATH:-$PWD/artifacts/verification/ModuleCache.noindex}" \
  swiftc -parse-as-library probes/ios-speech/ASRProbeState.swift tools/tests/ASRProbeChecks.swift -o artifacts/verification/asr-probe-checks
artifacts/verification/asr-probe-checks
