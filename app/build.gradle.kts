plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.digitalvision.listapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.digitalvision.listapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 11
        versionName = "1.8.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core:1.15.0")
}
