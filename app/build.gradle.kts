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
        versionCode = 8
        versionName = "1.7"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core:1.15.0")
}
