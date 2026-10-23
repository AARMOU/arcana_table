#!/usr/bin/env bash
# Run Arcana's existing engine-backed and Android JVM regressions without changing source.
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/android"

if [[ ! -x ./gradlew ]]; then
  echo "ERROR: expected executable android/gradlew" >&2
  exit 2
fi

echo "Repository source revision: $(git rev-parse HEAD)"
echo "Android root: $PWD"
echo "Test output: android/custom-cards/build/test-results/test and android/app/build/test-results/testDebugUnitTest"

# Preserve the exact failure exit status. Do not silently continue after a failed suite.
./gradlew --no-daemon :custom-cards:test
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:lintDebug

echo "PASS: custom-card scenarios, Android JVM/Robolectric tests, and Android lint."
echo "NOTE: Device testing, APK verification and live-engine tests are NOT included."
