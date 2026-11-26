plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aurapdf.editor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aurapdf.editor"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "5.0.0"
    }

    signingConfigs {
        create("auraRelease") {
            storeFile = file("aura-ai.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "auraai2026"
            keyAlias = System.getenv("KEY_ALIAS") ?: "auraai"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "auraai2026"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("auraRelease")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("auraRelease")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // 100% Offline PDF Engine (Create, Edit, Annotate, Draw, Sign, Organize, Encrypt, Compress)
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // 100% Offline OCR (Google ML Kit Text Recognition)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Pro Auto & Manual Document Scanner (Auto-capture, Manual capture, 4-Corner Crop, Filters, ML Clean)
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
}
