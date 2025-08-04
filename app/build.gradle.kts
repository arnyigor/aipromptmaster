import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization") version "2.1.10"
    kotlin("kapt")
}

android {
    // Загружаем переменные из .env файла, если он существует
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        val properties = Properties()
        properties.load(envFile.inputStream())
        properties.forEach { (key, value) ->
            // Устанавливаем переменные как project extra properties
            project.ext.set(key.toString(), value.toString())
        }
    }

    namespace = "com.arny.aipromptmaster"
    compileSdk = 36
    val vMajor = 0
    val vMinor = 1
    val vBuild = 0
    defaultConfig {
        applicationId = "com.arny.aipromptmaster"
        minSdk = 23
        targetSdk = 36
        versionCode = vMajor * 100 + vMinor * 10 + vBuild
        val name = "$vMajor" + ".${vMinor}" + ".${vBuild}"
        versionName = "v$name($versionCode)"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Сначала проверяем project extra properties,
            // а если их нет, то переменные окружения (для CI/CD).
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

            // Связываем нашу release-сборку с созданной конфигурацией подписи
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Configure APK file name
    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                val outputFileName = "AiPromptMaster-${variant.baseName}-${variant.versionName}-${variant.versionCode}" +
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