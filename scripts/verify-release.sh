#!/usr/bin/env bash
set -euo pipefail

./gradlew clean
./gradlew testDebugUnitTest --stacktrace
./gradlew lintDebug --stacktrace
./gradlew lintRelease --stacktrace
./gradlew assembleDebug --stacktrace
./gradlew assembleRelease --stacktrace

test -s app/build/outputs/apk/debug/app-debug.apk
test -s app/build/outputs/apk/release/app-release-unsigned.apk

echo "Release verification passed."
