import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("kapt")
    id("com.google.devtools.ksp")
    alias(libs.plugins.android.room)
    alias(libs.plugins.kotlin.serialization)
}

// Функция получения версии без внешних процессов
fun getVersionFromProperties(): Triple<Int, Int, Int> {
    // 1. Проверяем project property (приоритет для CI/CD)
    val versionNameProperty = project.findProperty("versionName") as? String
    if (versionNameProperty != null) {
        return parseVersion(versionNameProperty)
    }

    // 2. Проверяем environment variable
    val versionFromEnv = System.getenv("VERSION_NAME")
    if (versionFromEnv != null) {
        return parseVersion(versionFromEnv)
    }

    // 3. Читаем из version.properties файла
    val versionFile = rootProject.file("version.properties")
    if (versionFile.exists()) {
        val properties = Properties()
        properties.load(versionFile.inputStream())
        val version = properties.getProperty("version")
        if (version != null) {
            return parseVersion(version)
        }
    }

    // 4. Fallback версия
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

        // Читаем ключ из корневого local.properties
        val localProperties = Properties()
        // rootProject.file(...) - это важно, ищет файл в корне проекта, а не модуля
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))
        }
        val apiKey = localProperties.getProperty("API_SECRET_KEY", "DEFAULT_KEY_IF_NOT_FOUND")

        // Создаем поле в BuildConfig ИМЕННО ДЛЯ МОДУЛЯ :data
        buildConfigField("String", "API_SECRET_KEY", "\"$apiKey\"")
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

room {
    // Указываем путь, куда Room будет сохранять JSON-файлы со схемами.
    // ИСПРАВЛЕНИЕ: Это не свойство, а функция. Вызываем ее.
    schemaDirectory("$projectDir/schemas")
}

dependencies {

    // Koin core + Android support
    implementation(libs.koin.android)
    // Compose‑специфичная интеграция
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.core.ktx)
implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.retrofit2.kotlinx.serialization.converter)
    implementation(libs.retrofit2.retrofit)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logging.interceptor)
    implementation(libs.material.icons.extended)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.paging.compose.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.navigation.compose)
    // Navigation 3
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.markdown.renderer)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.markdown.renderer.android)
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.datastore.preferences.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.coil.compose)
    // Source: https://mvnrepository.com/artifact/com.jakewharton.timber/timber
    implementation(libs.timber)

    // Serialization для Navigation 3
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.compose.animation)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}