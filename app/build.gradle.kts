import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    // Note: In AGP 9.0+, built-in Kotlin support is enabled. 
    // The "org.jetbrains.kotlin.android" plugin is deprecated.
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
    //Playstore automation
    id("com.github.triplet.play") version "4.0.0"
}

android {
    namespace = "com.breakfree.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.breakfree.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            val properties = Properties()
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                properties.load(FileInputStream(localPropertiesFile))
            }

            // Provide a fallback string to prevent config failures if the file is missing
            storeFile = file(properties.getProperty("keystore.path") ?: "missing-keystore.jks")
            storePassword = properties.getProperty("keystore.password")
            keyAlias = properties.getProperty("key.alias")
            keyPassword = properties.getProperty("key.password")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
}

// Gradle 9 / Kotlin 2.x Deprecation Fix
// Replaces the deprecated 'kotlinOptions' block previously inside the 'android' block
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Coil (Image loading)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // DataStore (settings + break state persistence)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // WorkManager (periodic asset sync)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

play {
    // Point to the JSON key file you placed in the root directory

    val properties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        properties.load(FileInputStream(localPropertiesFile))
    }

    val keyPath = properties.getProperty("play.key.path")

    // Check if the property exists and isn't just an empty string
    if (!keyPath.isNullOrBlank()) {
        val playKey = file(keyPath)

        // Final safety check: does the file actually exist on the disk?
        if (playKey.exists()) {
            serviceAccountCredentials.set(playKey)
        } else {
            println("⚠️ WARNING: play.key.path is defined, but the file was not found at: ${playKey.absolutePath}")
        }
    } else {
        println("ℹ️ INFO: Play Publisher credentials skipped (play.key.path is missing or empty).")
    }

    track.set("interanl")
}