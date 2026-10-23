pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ArcanaTable"
include(":app")
// Custom cards: written in Argentum's card language, exported to app/src/main/assets/argentum/custom-cards.jsonl
include(":custom-cards")
