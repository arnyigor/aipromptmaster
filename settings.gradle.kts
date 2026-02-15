enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://maven.google.com/")
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven ("https://jitpack.io")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "aiprompts-kmp"
include(":shared")
include(":desktopApp")
include(":androidApp")