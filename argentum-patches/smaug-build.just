# Build through Argentum's machine-wide Gradle semaphore.
# Shebang recipes keep the directory change in the same shell and work with just 1.21.
build:
    #!/usr/bin/env bash
    set -euo pipefail
    cd ../argentum-engine
    scripts/gradle-locked --no-daemon --max-workers=2 -PkotlinCompileParallelism=1 -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=768m' :mtg-sdk:jar :mtg-sdk-tooling:jar :rules-engine:jar :ai:jar

test-rules:
    #!/usr/bin/env bash
    set -euo pipefail
    cd ../argentum-engine
    scripts/gradle-locked --no-daemon --max-workers=2 -PkotlinCompileParallelism=1 -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=768m' :rules-engine:test :mtg-sets:scenarioTest
