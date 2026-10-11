import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Network/download logic shared by the :mapdownloader companion and the `play` flavor of :app
// (maps, Huawei golf courses, A-GNSS, Huami token). Pure Kotlin + android.net.Uri/DocumentFile.
// The A-GNSS bound service and the signature permission stay in the companion on purpose: both apps
// would otherwise declare the same permission and clash on install.
plugins {
    alias(libs.plugins.android.library)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

android {
    namespace = "com.qtekfun.ultimategadget.mapsdl.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api("androidx.documentfile:documentfile:1.0.1")

    testImplementation("junit:junit:4.13.2")
}
