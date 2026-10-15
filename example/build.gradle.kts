plugins {
    id("com.android.application")
}

android {
    namespace = "com.advero.example"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.advero.example"
        minSdk = 21
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
    // The published artifact via JitPack — same as any real integrating
    // app would use (see ../README.md "Install"), not the local :advero-sdk
    // project reference, so this example doubles as a smoke test that the
    // published dependency actually resolves and works.
    implementation("com.github.cuongcds:advero-android:c")
    implementation("androidx.appcompat:appcompat:1.8.0")
}
