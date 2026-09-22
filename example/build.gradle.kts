plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.advero.example"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.advero.example"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // Local module reference here — a real app would instead depend on the
    // published artifact, e.g. implementation("com.github.cuongcds:advero-android:<version>")
    implementation(project(":advero-sdk"))
    implementation("androidx.appcompat:appcompat:1.7.0")
}
