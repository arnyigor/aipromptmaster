// shared/build.gradle.kts
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.*

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kover)
    alias(libs.plugins.buildconfig)
}

kotlin {
    // Android target
    androidTarget {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    // Desktop target
    jvm("desktop") {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    configurations.all {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-android")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Compose
                api(libs.compose.runtime)
                api(libs.compose.foundation)
                api(libs.compose.material3)
                api(libs.compose.ui)
                api(libs.compose.material.icons.extended)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                api(libs.compose.components.resources)
                api("com.arkivanov.decompose:decompose:${libs.versions.decompose.get()}")
                api("com.arkivanov.decompose:extensions-compose:${libs.versions.decompose.get()}")
                api("com.arkivanov.essenty:lifecycle-coroutines:${libs.versions.lifecycle.coroutines.get()}")
                api("androidx.room:room-runtime:${libs.versions.room.get()}")
                api(libs.androidx.room.ktx)
                api("io.insert-koin:koin-compose:${libs.versions.koin.get()}")
                api(libs.kotlinx.datetime)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
                api(project.dependencies.platform(libs.ktor.bom))
                api(libs.ktor.client.core)
                api(libs.ktor.client.content.negotiation)
                api(libs.ktor.client.logging)
                api(libs.ktor.serialization.kotlinx.json)
                api(libs.sqlite.bundled)
                api(libs.koin.core)
                api(libs.uuid)
                api(libs.jsoup)
                implementation(libs.selenium.java)
                implementation(libs.compose.markdown.render)
                implementation(libs.compose.markdown.render.coil)
                implementation(libs.russhwolf.settings)
                implementation(libs.russhwolf.settings.datastore)
                implementation(libs.russhwolf.settings.coroutines)
                implementation(libs.ui.tooling.preview)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(libs.keytar.java)
                // Платформенные реализации
                implementation(libs.kotlinx.coroutines.swing) // ПРАВИЛЬНОЕ МЕСТО
                implementation(compose.desktop.currentOs)
                // Обязательно добавьте это для работы @Preview
                implementation(libs.ui.tooling.preview)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.junit.jupiter)
                implementation(libs.junit.jupiter.params)
                implementation(libs.truth)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.selenium.java)
                implementation(libs.kotlinx.datetime)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.koin.android)
                implementation(libs.androidx.security.crypto)
                implementation(libs.androidx.datastore.preferences)
                implementation(libs.koin.androidx.compose) // Для viewModel()
                implementation(libs.ktor.client.okhttp)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.junit.jupiter)
                implementation(libs.junit.jupiter.params)
                implementation(libs.truth)
                implementation(libs.mockk.common)
                implementation(libs.kotlinx.coroutines.test)
            }
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

fun getProperty(key: String): String? {
    return localProperties.getProperty(key)
}

// 2. Используем (Lazy не обязателен, если чтение быстрое, но хорош для порядка)
val isDebug = getProperty("DEBUG_MODE")?.toBoolean() ?: false

buildConfig {
    packageName("com.arny.aiprompts")

    buildConfigField("Boolean", "DEBUG", isDebug.toString())
    buildConfigField("Boolean", "IS_IMPORT_ENABLED", isDebug.toString())

    // Дополнительные поля
    buildConfigField("String", "VERSION", "\"${project.version}\"")
}

dependencies {
    // Указываем, что room-compiler - это KSP процессор для каждой цели
    add("kspCommonMainMetadata", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
    add("kspAndroid", libs.androidx.room.compiler)
}

android {
    namespace = "com.arny.aiprompts.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }
}

// Kover configuration
kover {
    reports {
        total {
            xml {
                onCheck = true
            }
            html {
                onCheck = true
            }
        }
    }
}
