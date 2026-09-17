import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

/**
 * Release signing.
 *
 * The keystore and its passwords are deliberately not in the repository. They
 * come from keystore.properties (git-ignored) for local builds, or from the
 * environment for CI. Without them the release build still runs, but produces an
 * unsigned APK that cannot be installed.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(key: String, env: String): String? =
    (keystoreProperties.getProperty(key) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "KEYSTORE_FILE")
val hasReleaseSigning = releaseStoreFile != null &&
    signingValue("storePassword", "KEYSTORE_PASSWORD") != null &&
    signingValue("keyAlias", "KEY_ALIAS") != null &&
    signingValue("keyPassword", "KEY_PASSWORD") != null

android {
    namespace = "com.example.kanjipractice"
    compileSdk = 35

    defaultConfig {
        // 26 = Android 8.0. Comfortably above what ML Kit and Compose need, and
        // it keeps the API surface small.
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "2.2.0"

        ndk {
            // ML Kit's recognition engine is a native library, and it ships one
            // per CPU architecture: about 29 MB of the 42 MB APK is four copies
            // of libdigitalink.so. x86 and x86_64 exist only for emulators, which
            // this app is not distributed for, so they are dropped. arm64-v8a
            // covers every phone from roughly 2017 on; armeabi-v7a is kept for
            // older ones at a cost of 4.5 MB.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    /**
     * One codebase, two apps.
     *
     * The great majority of this project is script-neutral: the review state
     * machine, the scheduler, the canvas, the recogniser plumbing and the
     * settings all work the same whether the characters are kanji or hanzi. What
     * differs is the card data, the ML Kit language tag, the reading labels and
     * the deck list, and all of that lives in `src/japanese` or `src/chinese`.
     *
     * The two application ids are separate on purpose: they install side by side
     * and have their own databases.
     */
    flavorDimensions += "script"
    productFlavors {
        create("japanese") {
            dimension = "script"
            // Unchanged since the first release, so existing installs upgrade.
            applicationId = "com.example.kanjipractice"
            resValue("string", "app_name", "Kanji Practice")
        }
        create("chinese") {
            dimension = "script"
            applicationId = "com.example.hanzipractice"
            resValue("string", "app_name", "Hanzi Practice")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Left off on purpose: R8 needs keep rules for ML Kit, Room and Hilt,
            // and a sideloaded app gains little from the size win compared with
            // the risk of a rule missing something at runtime.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        // The licences screen shows the app version.
        buildConfig = true
    }

    testOptions {
        unitTests {
            // org.json and android.util.Log are stubs in the unit-test android.jar.
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

// Room writes the exported schema here so future migrations can be diffed.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":fsrs"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Whole-character handwriting recognition. The Japanese model is downloaded
    // once on first use and works offline from then on.
    implementation("com.google.mlkit:digital-ink-recognition:19.0.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Persists the one study preference (daily new-card limit).
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
    // Android ships a stubbed org.json on the unit-test classpath; this is the
    // real implementation so the deck parser can be tested on the JVM.
    testImplementation("org.json:json:20231013")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
