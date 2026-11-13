plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
}
layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("shared-ui"))
kotlin {
    jvm()
    jvmToolchain(17)
    sourceSets {
        commonMain.dependencies {
            api(project(":prompt-contract"))
            api(libs.compose.foundation)
            api(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
