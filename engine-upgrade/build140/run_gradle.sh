#!/usr/bin/env bash
# run_gradle.sh LABEL CMD...  Run a Gradle command, keep its full log, and on failure surface the key
# diagnostic lines as GitHub annotations (readable through the checks API without downloading logs).
set -uo pipefail
label="$1"; shift
log="$(mktemp)"
"$@" 2>&1 | tee "$log"
code=${PIPESTATUS[0]}
mkdir -p "${GITHUB_WORKSPACE:-.}/gradle-logs"
cp "$log" "${GITHUB_WORKSPACE:-.}/gradle-logs/${label//[^A-Za-z0-9_-]/_}.log"
if [ "$code" -ne 0 ]; then
  grep -E '^e: |^\* What went wrong|^  > |^> [^T]|FAILED$|Unresolved reference|Execution failed|Could not|tests completed' "$log" \
    | grep -vE '^> Task ' | head -12 \
    | while IFS= read -r line; do
        line="${line//'%'/'%25'}"
        echo "::error title=${label}::${line:0:900}"
      done
fi
exit "$code"
