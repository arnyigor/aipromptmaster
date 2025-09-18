// shared/build.gradle.kts
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
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
}

kotlin {
    // Android target
    androidTarget {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    // Desktop target
    jvm("desktop") {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
//        testRuns["test"].executionTask.configure {
//            useJUnitPlatform()
//        }
    }

    configurations.all {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-android")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Compose
                api(compose.runtime)
                api(compose.foundation)
                api(compose.material3)
                api(compose.ui)
                api(compose.materialIconsExtended)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                api(compose.components.resources)
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
                implementation(libs.compose.markdown)
                implementation(libs.richeditor.compose)
            }
        }

        val desktopMain by getting {
            dependencies {
                // desktop-специфичные зависимости если нужны
            }
        }

        val desktopTest by getting {
            dependencies {
                // Убираем все test зависимости временно
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(libs.kotlinx.coroutines.android) // ПРАВИЛЬНОЕ МЕСТО
                implementation(libs.koin.android)
                implementation(libs.koin.androidx.compose) // Для viewModel()
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
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


compose.desktop {
    application {
        mainClass = "com.arny.aiprompts.MainKt"
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
}
