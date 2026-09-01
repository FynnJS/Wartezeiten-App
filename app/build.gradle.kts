import java.io.File
import java.io.FileInputStream
import java.util.Properties
import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

val googleServicesFile = listOf(
    rootProject.file("app/google-services.json"),
    rootProject.file("google-services.json"),
).firstOrNull(File::isFile)
val firebaseValuesFromJson: Map<String, String> = googleServicesFile?.let { file ->
    @Suppress("UNCHECKED_CAST")
    val root = JsonSlurper().parse(file) as Map<String, Any?>
    val projectInfo = root["project_info"] as? Map<String, Any?>
    val clients = root["client"] as? List<Map<String, Any?>> ?: emptyList()
    val androidClient = clients.firstOrNull { client ->
        val clientInfo = client["client_info"] as? Map<String, Any?>
        val androidInfo = clientInfo?.get("android_client_info") as? Map<String, Any?>
        androidInfo?.get("package_name") == "de.wartezeiten.app"
    }
    val clientInfo = androidClient?.get("client_info") as? Map<String, Any?>
    val apiKeys = androidClient?.get("api_key") as? List<Map<String, Any?>> ?: emptyList()
    mapOf(
        "FIREBASE_APPLICATION_ID" to clientInfo?.get("mobilesdk_app_id")?.toString().orEmpty(),
        "FIREBASE_API_KEY" to apiKeys.firstOrNull()?.get("current_key")?.toString().orEmpty(),
        "FIREBASE_PROJECT_ID" to projectInfo?.get("project_id")?.toString().orEmpty(),
        "FIREBASE_GCM_SENDER_ID" to projectInfo?.get("project_number")?.toString().orEmpty(),
    )
}.orEmpty()

android {
    namespace = "de.wartezeiten.app"
    compileSdk = 35

    val keystorePropertiesFile = rootProject.file("keystore.properties")
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
            }
        }
    }

    defaultConfig {
        fun optionalStringBuildConfig(name: String): String {
            val value = providers.gradleProperty(name).orNull
                ?.takeIf { it.isNotBlank() }
                ?: firebaseValuesFromJson[name].orEmpty()
            val escapedValue = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
            return "\"$escapedValue\""
        }

        applicationId = "de.wartezeiten.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 10207
        versionName = "1.2.7"
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
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

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

val verifyReleaseFirebaseConfiguration by tasks.registering {
    group = "verification"
    description = "Fails release builds when the Firebase Android configuration is missing."

    doLast {
        val requiredProperties = listOf(
            "FIREBASE_APPLICATION_ID",
            "FIREBASE_API_KEY",
            "FIREBASE_PROJECT_ID",
            "FIREBASE_GCM_SENDER_ID",
        )
        val missingProperties = requiredProperties.filter { name ->
            providers.gradleProperty(name).orNull.isNullOrBlank() && firebaseValuesFromJson[name].isNullOrBlank()
        }
        check(missingProperties.isEmpty()) {
            "Release builds require Firebase Android configuration. Missing Gradle properties: " +
                    missingProperties.joinToString()
        }
    }
}

val verifyReleaseSigningConfiguration by tasks.registering {
    group = "verification"
    description = "Fails release builds when the stable release signing configuration is missing."

    doLast {
        check(rootProject.file("keystore.properties").isFile) {
            "Release builds require keystore.properties from scripts/configure-release-signing.ps1. " +
                    "Do not publish APKs signed with a debug or throwaway key, because Android cannot install them over existing releases."
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseFirebaseConfiguration)
    dependsOn(verifyReleaseSigningConfiguration)
}
