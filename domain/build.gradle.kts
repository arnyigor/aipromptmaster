plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization") version "2.1.10"
    kotlin("kapt")
}

android {
    namespace = "com.arny.aipromptmaster.domain"
    compileSdk = 36
    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        getByName("test") {
            java.srcDirs("src/test/java", "src/test/kotlin")
        }
        getByName("androidTest") {
            java.srcDirs("src/androidTest/java", "src/androidTest/kotlin")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                it.useJUnit()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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

    implementation(libs.kotlin.stdlib)
    implementation(libs.dagger)
    implementation(libs.dagger.android)
    implementation(libs.dagger.android.support)
    kapt(libs.dagger.android.processor)
    kapt(libs.dagger.compiler)
    implementation(libs.kotlinx.serialization.json)

    // ТОЛЬКО JUnit 4
    testImplementation(libs.junit)
    testImplementation(libs.mockk.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

}