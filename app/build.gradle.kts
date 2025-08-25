import java.util.Properties
import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization") version "2.1.10"
    kotlin("kapt")
}

// Функция для получения версии из Git
fun getVersionFromGit(): Triple<Int, Int, Int> {
    return try {
        // Пытаемся получить версию из project property (для CI/CD)
        val versionNameProperty = project.findProperty("versionName") as? String
        if (versionNameProperty != null) {
            println("Using version from project property: $versionNameProperty")
            parseVersion(versionNameProperty)
        } else {
            // Получаем последний тег из Git
            val output = ByteArrayOutputStream()
            val result = exec {
                commandLine("git", "describe", "--tags", "--abbrev=0")
                standardOutput = output
                isIgnoreExitValue = true
            }

            if (result.exitValue == 0) {
                val gitTag = output.toString().trim()
                println("Git tag found: $gitTag")
                // Убираем префикс 'v' если есть
                val versionString = if (gitTag.startsWith("v")) {
                    gitTag.substring(1)
                } else {
                    gitTag
                }
                parseVersion(versionString)
            } else {
                println("No git tags found, using default version")
                Triple(0, 1, 0) // Default версия
            }
        }
    } catch (e: Exception) {
        println("Error getting version from git: ${e.message}, using default")
        Triple(0, 1, 0) // Fallback версия
    }
}

// Функция парсинга версии из строки
fun parseVersion(versionString: String): Triple<Int, Int, Int> {
    return try {
        val parts = versionString.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        Triple(major, minor, patch)
    } catch (e: Exception) {
        println("Error parsing version '$versionString': ${e.message}")
        Triple(0, 1, 0)
    }
}

android {
    // Загружаем переменные из .env файла, если он существует
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        val properties = Properties()
        properties.load(envFile.inputStream())
        properties.forEach { (key, value) ->
            project.ext.set(key.toString(), value.toString())
        }
    }

    namespace = "com.arny.aipromptmaster"
    compileSdk = 36

    // Получаем версию динамически
    val (vMajor, vMinor, vPatch) = getVersionFromGit()

    defaultConfig {
        applicationId = "com.arny.aipromptmaster"
        minSdk = 23
        targetSdk = 36
        versionCode = vMajor * 10000 + vMinor * 100 + vPatch
        versionName = "$vMajor.$vMinor.$vPatch"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        println("Building version: $versionName (code: $versionCode)")
    }

    signingConfigs {
        create("release") {
            val storeFile = project.findProperty("SIGNING_KEY_STORE_PATH") ?: System.getenv("SIGNING_KEY_STORE_PATH")
            val storePassword = project.findProperty("SIGNING_KEY_STORE_PASSWORD") ?: System.getenv("SIGNING_KEY_STORE_PASSWORD")
            val keyAlias = project.findProperty("SIGNING_KEY_ALIAS") ?: System.getenv("SIGNING_KEY_ALIAS")
            val keyPassword = project.findProperty("SIGNING_KEY_PASSWORD") ?: System.getenv("SIGNING_KEY_PASSWORD")

            if (storeFile != null) {
                this.storeFile = file(storeFile)
                this.storePassword = storePassword as String?
                this.keyAlias = keyAlias as String?
                this.keyPassword = keyPassword as String?
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Настраиваем имя APK файла
    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                val outputFileName = "AiPromptMaster-${variant.baseName}-v${variant.versionName}" +
                        ".apk"
                output.outputFileName = outputFileName
            }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":presentation"))
    implementation(project(":data"))
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.jakewharton.timber)
    implementation(libs.dagger)
    implementation(libs.dagger.android)
    implementation(libs.dagger.android.support)
    implementation (libs.okhttp)
    implementation (libs.retrofit2.retrofit)
    implementation(libs.gson)
    kapt(libs.dagger.android.processor)
    kapt(libs.dagger.compiler)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}