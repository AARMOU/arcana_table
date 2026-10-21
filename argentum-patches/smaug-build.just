# Build only the four runtime libraries the Android app ships, through the engine semaphore.
set working-directory := "../argentum-engine"

build:
    scripts/gradle-locked --no-daemon --max-workers=2 -PkotlinCompileParallelism=1 -Pkotlin.compiler.execution.strategy=in-process '-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=768m' :mtg-sdk:jar :mtg-sdk-tooling:jar :rules-engine:jar :ai:jar
