# Test-only runner. No runtime rebuild or release task.
profile:
    #!/usr/bin/env bash
    set -euo pipefail
    cd ../android
    mkdir -p "$HOME/.cache/argentum"
    flock "$HOME/.cache/argentum/gradle.lock" ./gradlew --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m' :custom-cards:test --tests '*EndStepBacklogInvestigationTest'
