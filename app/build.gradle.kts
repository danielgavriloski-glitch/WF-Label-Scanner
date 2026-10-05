plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("com.google.gms.google-services")
}
android {
 namespace = "mk.wf.labelscanner"
 compileSdk = 35
 defaultConfig { applicationId = "com.mbidesign.app"; minSdk = 26; targetSdk = 35; versionCode = 2; versionName = "2.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation("androidx.appcompat:appcompat:1.7.0")
 implementation("androidx.activity:activity-ktx:1.9.3")
 val cameraX = "1.4.2"
 implementation("androidx.camera:camera-core:$cameraX")
 implementation("androidx.camera:camera-camera2:$cameraX")
 implementation("androidx.camera:camera-lifecycle:$cameraX")
 implementation("androidx.camera:camera-view:$cameraX")
 implementation("com.google.mlkit:text-recognition:16.0.1")
 implementation("com.google.mlkit:barcode-scanning:17.3.0")
 implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
 implementation("com.google.firebase:firebase-auth")
 implementation("com.google.firebase:firebase-firestore")
 implementation("com.google.guava:guava:33.3.1-android")
 testImplementation(kotlin("test"))
}