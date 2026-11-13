plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
layout.buildDirectory.set(rootProject.layout.buildDirectory.dir("shared-ui"))
android {
    namespace = "com.arny.sharedui"
    compileSdk = 36
    defaultConfig { minSdk = 23 }
    sourceSets["main"].java.srcDir("src/commonMain/kotlin")
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":prompt-contract"))
    implementation(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    implementation(libs.material.icons.extended)
}
