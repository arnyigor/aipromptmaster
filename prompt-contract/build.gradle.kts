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
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
    }
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    providers.environmentVariable("PROMPT_CATALOG_ARCHIVE").orNull?.let { archive ->
        inputs.file(archive)
        environment("PROMPT_CATALOG_ARCHIVE", archive)
    }
}
