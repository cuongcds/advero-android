plugins {
    id("com.android.application")
}

android {
    namespace = "com.advero.example"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.advero.example"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        // Ad-serving host, per build config rather than hardcoded in source —
        // lets debug/release (or a staging variant) point at different
        // Advero backends without touching Kotlin code.
        buildConfigField("String", "ADVERO_BASE_URL", "\"https://advero.shplinks.com\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

dependencies {
    // Local module, so the example runs against the SDK sources in this repo.
    implementation(project(":advero-sdk"))

    // The published artifact via JitPack — same as any real integrating app
    // would use (see ../README.md "Install"); swap with the line above to
    // smoke-test the published dependency.
    // implementation("com.github.cuongcds:advero-android:0.2.3")
    implementation("androidx.appcompat:appcompat:1.8.0")
}
