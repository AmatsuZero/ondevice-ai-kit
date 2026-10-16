#!/bin/sh
set -eu
out="${TMPDIR:-/tmp}/odai-native-tts-probe-$$.aiff"
trap 'rm -f "$out"' EXIT INT TERM
voice="${1:-Samantha}"
text="${2:-OnDevice AI Kit native speech probe}"
say -v "$voice" -o "$out" "$text"
afinfo "$out" | awk -F': ' '/File type|Data format|estimated duration|channel count|sample rate/ {print}'
