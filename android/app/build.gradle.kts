plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.arcanatable"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.arcanatable"
        minSdk = 26
        targetSdk = 36
        versionCode = 87
        versionName = "0.15.68-argentum-integration"
        manifestPlaceholders["appLabel"] = "Arcana Table · Argentum · Build 87"
    }

    buildTypes {
        debug {
            // Separate package so this build installs beside the experimental/targeting test build.
            applicationIdSuffix = ".argentum"
            manifestPlaceholders["appLabel"] = "Arcana · Argentum B87"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric UI tests render real resources and Compose screens on the JVM.
            isIncludeAndroidResources = true
            all { test ->
                test.maxHeapSize = "3g"
                // Opt-in live test against Argentum's server: ./gradlew testDebugUnitTest -Pargentum.live=true
                test.systemProperty("argentum.live", (project.findProperty("argentum.live") ?: "false").toString())
                // Robolectric fetches android-all jars itself; use the Maven Central mirror Gradle already uses.
                test.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
                test.systemProperty("arcana.screenshots", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
            }
        }
    }
}

kotlin {
    // Match compileOptions so the build works on JDK 17 and JDK 21 hosts alike.
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // JVM unit tests exercise online snapshot JSON. Android's org.json stubs
    // throw "Method ... not mocked" outside an emulator, so use the JVM port.
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
