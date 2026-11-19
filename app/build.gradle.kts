plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.teamboard.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.teamboard.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 46
        versionName = "0.44.005f"
    }
    signingConfigs {
        create("fixed") {
            storeFile = file("keystore/teamboard.jks")
            storePassword = "teamboard"
            keyAlias = "teamboard"
            keyPassword = "teamboard"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("fixed") }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.11.0")
    }
