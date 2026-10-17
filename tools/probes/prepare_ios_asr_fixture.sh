#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
directory=artifacts/P6IOSASR/fixture
fixture="$directory/asr-fixture.caf"
expected_sha256=ea1e97d99f859f4bb1eafb75f6090bb9fe98f89ab86bd903f7c325013f3dd98e
mkdir -p "$directory"
if [ -e "$fixture" ]; then
    echo 'Fixture exists; preserving the frozen input.'
else
    /usr/bin/say -v Samantha --data-format=LEI16@22050 --channels=1 -o "$fixture" 'The blue bicycle is beside the green house.'
fi
/usr/bin/afinfo "$fixture"
actual_sha256=$(shasum -a 256 "$fixture" | awk '{print $1}')
if [ "$actual_sha256" != "$expected_sha256" ]; then
    echo "Fixture SHA256 mismatch: expected $expected_sha256, got $actual_sha256" >&2
    echo 'The host voice or an existing fixture changed; do not package this input.' >&2
    exit 1
fi
echo "SHA256 $actual_sha256 (frozen fixture verified)"
