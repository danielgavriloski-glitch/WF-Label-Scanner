plugins {
    id("com.android.application")
}

android {
    namespace = "mk.td.booking"
    compileSdk = 35
    defaultConfig {
        applicationId = "mk.td.booking"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
