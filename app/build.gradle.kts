import java.io.File
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "de.wartezeiten.app"
    compileSdk = 35

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val defaultDebugKeystore = File(System.getProperty("user.home"), ".android/debug.keystore")
    var hasValidKeystore = false

    signingConfigs {
        create("release") {
            val keystoreProperties = Properties()
            if (keystorePropertiesFile.exists()) {
                FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }
                storeFile = file(keystoreProperties["storeFile"].toString())
                storePassword = keystoreProperties["storePassword"].toString()
                keyAlias = keystoreProperties["keyAlias"].toString()
                keyPassword = keystoreProperties["keyPassword"].toString()
                hasValidKeystore = true
            } else if (defaultDebugKeystore.exists()) {
                storeFile = defaultDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
                hasValidKeystore = true
            }
        }
    }

    defaultConfig {
        fun optionalStringBuildConfig(name: String): String {
            val value = providers.gradleProperty(name).orElse("").get()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
            return "\"$value\""
        }

        applicationId = "de.wartezeiten.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 10100
        versionName = "1.1.0"
        buildConfigField("String", "UPDATE_BASE_URL", "\"https://wartezeiten-app.tutorialfynn.workers.dev/\"")
        buildConfigField("String", "PUSH_API_BASE_URL", "\"https://wartezeiten-app.tutorialfynn.workers.dev/\"")
        buildConfigField("String", "FIREBASE_APPLICATION_ID", optionalStringBuildConfig("FIREBASE_APPLICATION_ID"))
        buildConfigField("String", "FIREBASE_API_KEY", optionalStringBuildConfig("FIREBASE_API_KEY"))
        buildConfigField("String", "FIREBASE_PROJECT_ID", optionalStringBuildConfig("FIREBASE_PROJECT_ID"))
        buildConfigField("String", "FIREBASE_GCM_SENDER_ID", optionalStringBuildConfig("FIREBASE_GCM_SENDER_ID"))
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            if (hasValidKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    testImplementation("junit:junit:4.13.2")

    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-compiler:2.59.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("com.google.firebase:firebase-messaging-ktx:24.1.0")
}
