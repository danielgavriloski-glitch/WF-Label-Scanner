plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "mk.td.booking"
    compileSdk = 35
    defaultConfig {
        applicationId = "mk.td.booking6"
        minSdk = 26
        targetSdk = 35
        versionCode = 81
        versionName = "6.21"
    }
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-QA"
            manifestPlaceholders["appLabel"] = "MarK Booking QA"
        }
        getByName("release") {
            isMinifyEnabled = false
            manifestPlaceholders["appLabel"] = "MarK Booking"
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
