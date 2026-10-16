#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p artifacts/verification
swiftc demo/iosApp/PhotoSubmission.swift tools/tests/PhotoSubmissionChecks.swift -o artifacts/verification/photo-submission-checks
artifacts/verification/photo-submission-checks
