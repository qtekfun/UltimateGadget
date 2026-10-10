import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

android {
    namespace = "com.qtekfun.ultimategadget.mapsdl"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.qtekfun.ultimategadget.mapsdl"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        // Companion downloader signed with the same UltimateGadget release key as the main app,
        // driven by the release CI env vars (absent locally -> release is left unsigned).
        create("release") {
            System.getenv("UG_KEYSTORE_FILE")?.let { ksFile ->
                storeFile = file(ksFile)
                storePassword = System.getenv("UG_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UG_KEY_ALIAS")
                keyPassword = System.getenv("UG_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (System.getenv("UG_KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation("androidx.documentfile:documentfile:1.0.1")

    testImplementation("junit:junit:4.13.2")
}
