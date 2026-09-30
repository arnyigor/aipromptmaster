plugins {
    id("org.jetbrains.kotlin.multiplatform")
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
