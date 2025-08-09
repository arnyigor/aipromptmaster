// aiprompts-kmp/build.gradle.kts

plugins {
    // Подключаем плагины из нашего каталога libs.versions.toml
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.kotlinx.serialization) apply false
}