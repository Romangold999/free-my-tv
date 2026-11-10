plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.romgold.geelyyoutube"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.romgold.geelyyoutube"
        minSdk = 21
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
