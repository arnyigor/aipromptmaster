// desktopApp/build.gradle.kts
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        compilations.all {
            compilerOptions.configure {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.client.cio)
                implementation(libs.logback.classic)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
    }
}

val desktopPackageVersion = providers.gradleProperty("versionName")
    .orElse(providers.environmentVariable("VERSION_NAME"))
    .orElse(providers.fileContents(rootProject.layout.projectDirectory.file("../version.properties")).asText.map { content ->
        Properties().apply { content.reader().use { load(it) } }.getProperty("version")
            ?: error("version.properties must contain version")
    }).get()
require(desktopPackageVersion.matches(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"))) {
    "Desktop package version must be major.minor.patch"
}
val desktopVersionParts = desktopPackageVersion.split('.').map { it.toIntOrNull() ?: error("Desktop version component exceeds MSI limits") }
require(desktopVersionParts[0] in 0..255 && desktopVersionParts[1] in 0..255 && desktopVersionParts[2] in 0..65535) {
    "Desktop package version exceeds Windows Installer limits"
}

compose.desktop {
    application {
        mainClass = "com.arny.aiprompts.MainKt"
        nativeDistributions {
            targetFormats(
                TargetFormat.Exe,    // Windows EXE-установщик
                TargetFormat.Msi,
            )
            packageName = "AIPrompts"
            packageVersion = desktopPackageVersion
            windows {
                perUserInstall = true
                dirChooser = true
                upgradeUuid = "b6b9c07c-94d4-4a0f-b968-dfe5d41a4c6b"
            }
            modules(
                "jdk.accessibility",
                "java.net.http",     // Исправляет ошибку WebSocket$Listener
                "java.naming",       // Требуется для DNS-резолвинга в Selenium
                "jdk.crypto.ec",     // Требуется для HTTPS/SSL (иначе может упасть скачивание)
                "java.management"
            )
        }
    }
}
