plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}


android {
    namespace = "com.example.gabai"
    compileSdk = 35 // Use stable version 35

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    defaultConfig {
        applicationId = "com.example.gabai"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    implementation("io.noties.markwon:core:4.6.2")
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-auth-ktx") // D1: User Database
    implementation("com.google.firebase:firebase-firestore-ktx")
    // Also add this for the App Check debug provider you added in Step 1
    implementation("com.google.firebase:firebase-appcheck-debug")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    // CameraX core library using the camera2 implementation
    implementation("androidx.camera:camera-camera2:1.3.1")
// CameraX Lifecycle library
    implementation("androidx.camera:camera-lifecycle:1.3.1")
// CameraX View class (Provides PreviewView)
    implementation("androidx.camera:camera-view:1.3.1")
    implementation("com.google.guava:guava:31.0.1-android")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.mlkit:language-id:17.0.4")


// Source: https://mvnrepository.com/artifact/com.github.barteksc/android-pdf-viewer
    implementation("com.github.mhiew:android-pdf-viewer:3.2.0-beta.3")

}