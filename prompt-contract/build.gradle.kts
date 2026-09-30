plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Both Gradle roots include this source module; keep their compiler outputs separate.
layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("prompt-contract"))

kotlin {
    jvm()
    jvmToolchain(17)
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    providers.environmentVariable("PROMPT_CATALOG_ARCHIVE").orNull?.let { archive ->
        inputs.file(archive)
        environment("PROMPT_CATALOG_ARCHIVE", archive)
    }
}
