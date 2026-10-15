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
        versionCode = 84
        versionName = "0.15.65-targeting"
        manifestPlaceholders["appLabel"] = "Arcana Table Targeting · Build 84"
    }

    buildTypes {
        debug {
            // Keep the installable test build separate from earlier debug APKs.
            applicationIdSuffix = ".targeting"
            manifestPlaceholders["appLabel"] = "Arcana Table Targeting 0.15.65 · Build 84"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
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
    // JVM unit tests exercise online snapshot JSON. Android's org.json stubs
    // throw "Method ... not mocked" outside an emulator, so use the JVM port.
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
    }
}
