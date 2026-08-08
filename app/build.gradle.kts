import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.voiceassistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.voiceassistant"
        minSdk = 26          // Foreground service microphone type needs 26+; Porcupine needs 21+
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Reads NEWS_API_KEY from local.properties (git-ignored) so the real key never
        // gets committed. Add a line like NEWS_API_KEY=your_key_here to local.properties.
        buildConfigField(
            "String", "NEWS_API_KEY",
            "\"${project.findProperty("NEWS_API_KEY") ?: ""}\""
        )
    }

    buildFeatures {
        buildConfig = true
    }

    // Signing config for a release APK you can hand directly to your client (not
    // going through Play Store). Reads keystore details from local.properties so
    // nothing sensitive is hardcoded or committed — see the README for how to
    // generate the keystore and what to put in local.properties.
    signingConfigs {
        create("release") {
            val keystoreProps = Properties()
            val keystorePropsFile = rootProject.file("local.properties")
            if (keystorePropsFile.exists()) {
                keystoreProps.load(keystorePropsFile.inputStream())
            }
            storeFile = keystoreProps["RELEASE_STORE_FILE"]?.let {path-> file(path) }
            storePassword = keystoreProps["RELEASE_STORE_PASSWORD"] as String?
            keyAlias = keystoreProps["RELEASE_KEY_ALIAS"] as String?
            keyPassword = keystoreProps["RELEASE_KEY_PASSWORD"] as String?
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Vosk for offline wake-word detection — no account/API key needed at all.
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("net.java.dev.jna:jna:5.13.0@aar")

    // Coroutines for async STT/TTS/network orchestration
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Lightweight HTTP client for the News API handler
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON parsing for the News API response
    implementation("org.json:json:20240303")
}
