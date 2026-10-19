#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
python3 scripts/gradle-local.py :android:identity:testDebugUnitTest :android:identity:lintDebug :android:speech:testDebugUnitTest :android:backend-system-tts:testDebugUnitTest :android:backend-system-asr:testDebugUnitTest :android:sdk-core:testDebugUnitTest :android:model-store:testDebugUnitTest :android:face:testDebugUnitTest :android:backend-mlkit:testDebugUnitTest :demo:androidApp:testDebugUnitTest :demo:androidApp:lintDebug :demo:androidApp:assembleDebug :examples:android-native:assembleDebug :examples:android-identity-native:assembleDebug :examples:android-identity-native:lintDebug --console=plain
echo 'Android JVM tests, lint and APK builds passed. Device inference remains a separate gate.'
