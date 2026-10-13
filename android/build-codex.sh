#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOL_CACHE="$PROJECT_DIR/.codex-build-cache"

# Keep a full JDK beside the project when available. Some hosted workspaces
# only provide a JRE, which Gradle cannot use for Android compilation.
JDK_HOME="$TOOL_CACHE/jdk-17"
if [[ ! -x "$JDK_HOME/bin/javac" ]]; then
  # The short link can point at an older workspace path after copying this
  # project. Fall back to a full JDK stored directly in the tool cache.
  while IFS= read -r candidate; do
    if [[ -x "$candidate/bin/javac" ]]; then
      JDK_HOME="$candidate"
      break
    fi
  done < <(find "$TOOL_CACHE" -mindepth 1 -maxdepth 1 -type d -name 'jdk-17*' -print | sort -r)
fi

if [[ -x "$JDK_HOME/bin/javac" ]]; then
  export JAVA_HOME="$JDK_HOME"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

# Hosted builds may use an HTTPS inspection proxy. Prefer the host's trusted
# Java certificates so Gradle can resolve Android/Maven dependencies.
if [[ -f /etc/ssl/certs/java/cacerts ]]; then
  export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts"
fi

# Keep Gradle's distribution and dependency cache beside this project instead
# of in a disposable per-session home directory.
export GRADLE_USER_HOME="$TOOL_CACHE/gradle"

# Prefer a project-local Android SDK when one is installed; otherwise use the
# standard SDK location configured on the machine.
if [[ -d "$TOOL_CACHE/android-sdk" ]]; then
  export ANDROID_HOME="$TOOL_CACHE/android-sdk"
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
fi

if [[ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ]]; then
  echo "Android SDK not found. Install it once or set ANDROID_HOME, then rerun this script."
  exit 2
fi

cd "$PROJECT_DIR"
./gradlew --build-cache assembleDebug

APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -s "$APK" ]]; then
  echo "Gradle finished, but the debug APK was not found at: $APK"
  exit 3
fi
echo "APK ready: $APK"
