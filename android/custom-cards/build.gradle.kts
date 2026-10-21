// Arcana custom cards: cards Argentum doesn't have yet, written in Argentum's own card language
// (the mtg-sdk DSL from app/libs) and shipped to the app as DATA (custom-cards.jsonl), never as code.
//
//   ./gradlew :custom-cards:test          scenario test per card, run on the real rules engine
//   ./gradlew :custom-cards:exportCards   rewrites app/src/main/assets/argentum/custom-cards.{jsonl,index.tsv}
//
// CustomCardDataTest fails when the shipped data no longer matches these definitions.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    sourceSets.getByName("test").kotlin.srcDir("../app/src/main/java/com/arcanatable/engine/ai")
    sourceSets.getByName("test").kotlin.srcDir("../app/src/main/java/com/arcanatable/engine/priority")
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val argentumJars = fileTree("../app/libs") { include("argentum-*.jar") }
val assetsDir = rootProject.layout.projectDirectory.dir("app/src/main/assets/argentum")

dependencies {
    implementation(argentumJars)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.slf4j:slf4j-api:2.0.18")
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    maxHeapSize = "2g"
    systemProperty("arcana.assets", assetsDir.asFile.absolutePath)
    // Engine threads recurse deeply on big boards.
    jvmArgs("-Xss16m")
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

tasks.register<JavaExec>("exportCards") {
    group = "arcana"
    description = "Writes the custom cards as data into the app's assets"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.arcanatable.customcards.ExportKt")
    args(assetsDir.asFile.absolutePath)
}
