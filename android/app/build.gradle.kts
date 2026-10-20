plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ai.opencode.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "ai.opencode.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Track A: arm64 only (upstream musl binary is arm64).
        // Track B (PLAN.md §4) adds "armeabi-v7a" via the Node-bundle engine.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    packaging {
        jniLibs {
            // REQUIRED: keep `lib*.so` files extracted (not compressed in place) so the
            // engine binary lands in nativeLibraryDir with the exec bit set.
            // (Equivalent of legacy extractNativeLibs="true", AGP 8 Kotlin DSL form.)
            useLegacyPackaging = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // TODO(M6): signing configs sourced from CI secrets
        }
    }

    buildFeatures {
        buildConfig = true
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
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
