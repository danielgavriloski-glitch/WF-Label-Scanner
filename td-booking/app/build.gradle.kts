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
        versionCode = 3
        versionName = "0.3"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
