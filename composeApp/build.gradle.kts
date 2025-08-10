// aiprompts-kmp/composeApp/build.gradle.kts

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
}

kotlin {
    jvm("desktop"){
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
        compilations.all {
            compilerOptions.configure {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }

    sourceSets {
        val desktopMain by getting
        // --- НОВАЯ СЕКЦИЯ ДЛЯ ТЕСТОВ ---
        val desktopTest by getting {
            dependencies {
                implementation(libs.junit)
                implementation(libs.truth)
                implementation(libs.mockk)
                // Зависимость на основной код, чтобы тесты его "видели"
                implementation(kotlin("test"))
            }
        }

        commonMain.dependencies {
            // Здесь общие зависимости, доступные на всех платформах
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.compose.material.icons.extended)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.components.resources)


            // Kotlinx Ktor & Serialization (интерфейсы и общая логика)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core) // ТОЛЬКО -core
            implementation(libs.kotlinx.serialization.json)

            // Decompose, Ktor, Room, Koin-core и т.д.
            implementation("com.arkivanov.decompose:decompose:${libs.versions.decompose.get()}") {
                exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-android")
            }
            implementation("com.arkivanov.decompose:extensions-compose:${libs.versions.decompose.get()}") {
                exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-android")
            }
            implementation("com.arkivanov.essenty:lifecycle-coroutines:${libs.versions.lifecycle.coroutines.get()}") {
                exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-android")
            }
            implementation(project.dependencies.platform(libs.ktor.bom))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation("androidx.room:room-runtime:${libs.versions.room.get()}") {
                exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-android")
            }
            implementation("androidx.room:room-ktx:${libs.versions.room.get()}") {
                exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-android")
            }

            implementation(libs.sqlite.bundled)
            implementation(libs.koin.core)
            // Объявляем зависимость явно, чтобы можно было применить exclude
            implementation("io.insert-koin:koin-compose:${libs.versions.koin.get()}") {
                exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-android")
            }

            implementation(libs.uuid)
        }

        desktopMain.dependencies {
            // Здесь зависимости только для Desktop (JVM)
            implementation(compose.desktop.currentOs)

            // Конкретные реализации для JVM
            // Платформенные реализации
            implementation(libs.kotlinx.coroutines.swing) // ПРАВИЛЬНОЕ МЕСТО
            implementation(libs.ktor.client.cio)
            implementation(libs.logback.classic) // Логирование для JVM

            // Web Scraping Libraries
            implementation(libs.selenium.java)
            implementation(libs.jsoup)
        }
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
}

// Загружаем свойства из local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

compose.desktop {
    application {
        mainClass = "com.arny.aiprompts.MainKt" // Точка входа в наше приложение

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "aiprompts"
            packageVersion = "1.0.0"
        }
    }
}

dependencies {
    // Указываем, что room-compiler - это KSP процессор для каждой цели
    add("kspCommonMainMetadata", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
}
