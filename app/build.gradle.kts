import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing comes from key.properties (local, never committed) or
// AIR_RELAY_* environment variables (CI). Without either, release builds fall
// back to the debug key and must not be distributed.
val releaseKey = Properties().apply {
    rootProject.file("key.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}
fun releaseSetting(name: String, env: String): String? =
    releaseKey.getProperty(name) ?: System.getenv(env)

android {
    namespace = "com.srineere.airbridge"
    compileSdk = 36

    defaultConfig {
        // Final: Play and GitHub releases share this ID; it can never change.
        applicationId = "com.srineere.airbridge"
        minSdk = 26
        targetSdk = 36
        versionCode = 24
        versionName = "0.3.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main") { java.srcDirs("src/main/kotlin") }
        getByName("test") { java.srcDirs("src/test/kotlin") }
    }

    signingConfigs {
        create("release") {
            releaseSetting("storeFile", "AIR_RELAY_KEYSTORE")?.let { path ->
                storeFile = file(path)
                storePassword = releaseSetting("storePassword", "AIR_RELAY_KEYSTORE_PASSWORD")
                keyAlias = releaseSetting("keyAlias", "AIR_RELAY_KEY_ALIAS")
                keyPassword = releaseSetting("keyPassword", "AIR_RELAY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // The Flutter build still owns com.srineere.airbridge on the phone, so
        // debug installs beside it rather than over it.
        debug {
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "Air Bridge dev")
        }
        release {
            resValue("string", "app_name", "Air Bridge")
            val release = signingConfigs.getByName("release")
            signingConfig = if (release.storeFile != null) release else signingConfigs.getByName("debug")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":air"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-service:2.8.6")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.navigation:navigation-compose:2.8.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
