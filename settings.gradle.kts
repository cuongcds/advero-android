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
        // Required for :example, which depends on the published
        // com.github.cuongcds:advero-android artifact (JitPack builds
        // straight from this repo's GitHub tags) instead of the local
        // :advero-sdk module — the same way a real integrating app would.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "advero-android"
include(":advero-sdk")
include(":example")
