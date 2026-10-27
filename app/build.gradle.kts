plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.thanhtaxi.smartdrive"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.thanhtaxi.smartdrive"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-alpha"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("org.maplibre.gl:android-sdk:11.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
