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
        maven { url = uri("https://jitpack.io") }
    }
    // NOTE: the "libs" version catalog is auto-created from
    // gradle/libs.versions.toml by convention - do not redeclare it here.
}

rootProject.name = "CupCake"
include(":app")
include(":esp") // For reference, not a Gradle module