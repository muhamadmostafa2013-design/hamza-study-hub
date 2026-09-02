plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hamza.studyhub"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hamza.studyhub"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.2.0"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.github.Keule0010:WebUntisAPI:a93bf6407440cc9ec38d5b31cb797f1865165a8a")
}
