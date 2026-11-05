// The module that generates app/src/main/baseline-prof.txt.
//
// It is a com.android.test module, not a library: it installs the app on a
// device, drives it, and records which classes and methods the first run
// actually touches. Nothing here ships. What ships is the text file it
// writes, which profileinstaller hands to the runtime on first launch so
// the hot path is compiled ahead of time instead of interpreted.
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.srineere.airbridge.baselineprofile"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        // 28 is this app's floor, but a profile can only be recorded on 33
        // and up; the generator refuses anything older and says so.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        getByName("main") { java.srcDirs("src/main/kotlin") }
    }

    targetProjectPath = ":app"
}

// One device, the one that is plugged in. A managed device would be
// reproducible and would also mean downloading a system image; this app is
// developed against a real phone and the profile should come off one.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.espresso:espresso-core:3.6.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
}
