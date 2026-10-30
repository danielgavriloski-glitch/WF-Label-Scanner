plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "mk.td.booking"
    compileSdk = 35
    defaultConfig {
        applicationId = "mk.td.booking6"
        minSdk = 26
        targetSdk = 35
        versionCode = 80
        versionName = "6.20"
    }
    buildTypes { getByName("release") { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
