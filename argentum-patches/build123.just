# All upstream builds use its shared Gradle semaphore.
engine-build:
    #!/usr/bin/env bash
    set -euo pipefail
    cd "${ARCANA_ENGINE_DIR:-../argentum-engine}"
    scripts/gradle-locked --no-daemon --max-workers=2 -PkotlinCompileParallelism=1 -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=768m' :mtg-sdk:jar :mtg-sdk-tooling:jar :rules-engine:jar :ai:jar

engine-test:
    #!/usr/bin/env bash
    set -euo pipefail
    cd "${ARCANA_ENGINE_DIR:-../argentum-engine}"
    scripts/gradle-locked --no-daemon --max-workers=2 -PkotlinCompileParallelism=1 -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=768m' :rules-engine:test :mtg-sets:scenarioTest :ai:test


engine-ai-deadline-test:
    #!/usr/bin/env bash
    set -euo pipefail
    cd "${ARCANA_ENGINE_DIR:-../argentum-engine}"
    scripts/gradle-locked --no-daemon --max-workers=2 -PkotlinCompileParallelism=1 -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=768m' :ai:test --tests '*GameSimulatorTest' --tests '*DecisionBudgetTest'

ai-test:
    #!/usr/bin/env bash
    set -euo pipefail
    cd "${ARCANA_ENGINE_DIR:-../argentum-engine}"
    scripts/gradle-locked --no-daemon --max-workers=2 -PkotlinCompileParallelism=1 -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=768m' :ai:test --tests '*Test'
