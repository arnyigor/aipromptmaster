import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization") version "2.1.10"
    kotlin("kapt")
}

// Функция получения версии без внешних процессов
fun getVersionFromProperties(): Triple<Int, Int, Int> {
    // 1. Проверяем project property (приоритет для CI/CD)
    val versionNameProperty = project.findProperty("versionName") as? String
    if (versionNameProperty != null) {
        println("📦 Using version from project property: $versionNameProperty")
        return parseVersion(versionNameProperty)
    }

    // 2. Проверяем environment variable
    val versionFromEnv = System.getenv("VERSION_NAME")
    if (versionFromEnv != null) {
        println("📦 Using version from environment: $versionFromEnv")
        return parseVersion(versionFromEnv)
    }

    // 3. Читаем из version.properties файла
    val versionFile = rootProject.file("version.properties")
    if (versionFile.exists()) {
        val properties = Properties()
        properties.load(versionFile.inputStream())
        val version = properties.getProperty("version")
        if (version != null) {
            println("📦 Using version from version.properties: $version")
            return parseVersion(version)
        }
    }

    // 4. Fallback версия
    println("📦 Using default version: 0.1.0")
    return Triple(0, 1, 0)
}

fun parseVersion(versionString: String): Triple<Int, Int, Int> {
    return try {
        val parts = versionString.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 1
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        Triple(major, minor, patch)
    } catch (e: Exception) {
        println("⚠️ Error parsing version '$versionString': ${e.message}")
        Triple(0, 1, 0)
    }
}

android {
    namespace = "com.arny.aipromptmaster"
    compileSdk = 36

    // Получаем версию без использования внешних процессов
    val (vMajor, vMinor, vPatch) = getVersionFromProperties()

    defaultConfig {
        applicationId = "com.arny.aipromptmaster"
        minSdk = 23
        targetSdk = 36
        versionCode = vMajor * 10000 + vMinor * 100 + vPatch
        versionName = "$vMajor.$vMinor.$vPatch"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        println("🚀 Building version: $versionName (code: $versionCode)")
    }

    // Остальная конфигурация остается без изменений...
    signingConfigs {
        create("release") {
            val storeFile = project.findProperty("SIGNING_KEY_STORE_PATH") ?: System.getenv("SIGNING_KEY_STORE_PATH")
            val storePassword = project.findProperty("SIGNING_KEY_STORE_PASSWORD") ?: System.getenv("SIGNING_KEY_STORE_PASSWORD")
            val keyAlias = project.findProperty("SIGNING_KEY_ALIAS") ?: System.getenv("SIGNING_KEY_ALIAS")
            val keyPassword = project.findProperty("SIGNING_KEY_PASSWORD") ?: System.getenv("SIGNING_KEY_PASSWORD")

            if (storeFile != null) {
                val storeFilePath = storeFile.toString()
                // Если путь не абсолютный, считаем его относительно корня проекта
                val keystoreFile = if (storeFilePath.startsWith("/")) {
                    file(storeFilePath)
                } else {
                    rootProject.file(storeFilePath)
                }
                this.storeFile = keystoreFile
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

    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                val outputFileName =
                    "AiPromptMaster-${variant.baseName}-v${variant.versionName}.apk"
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

    kapt {
        correctErrorTypes = true
        useBuildCache = true
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