import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.kiroland.mediacenter"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kiroland.mediacenter"
        // The target TV runs Android 10; older devices are out of scope.
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "0.1"
        // TV boxes are ARM; dropping x86 halves the FFmpeg payload.
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }

        // TMDB "API Read Access Token" from the untracked local.properties (tmdb.token=...).
        val localProperties = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        buildConfigField("String", "TMDB_TOKEN", "\"${localProperties.getProperty("tmdb.token", "")}\"")
        // OpenSubtitles API key, also untracked (opensubtitles.key=...); the upload page can override it.
        buildConfigField("String", "OPENSUBTITLES_KEY", "\"${localProperties.getProperty("opensubtitles.key", "")}\"")
    }

    // Play upload key from the untracked keystore.properties (storeFile, storePassword, keyAlias, keyPassword).
    val keystoreProperties = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("upload") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The Play build (bundleRelease); without keystore.properties (CI) it falls back to the debug key.
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
        // Release code signed with the debug key, so a sideloaded install updates in place (assembleSideload):
        // a different key would mean uninstalling, and with it the library and the watched history.
        create("sideload") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
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
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.material.icons.extended)
    implementation(libs.tv.material)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.zxing.core)
    // SMB2/3 client for network folders (pure Java; crypto through its bundled BouncyCastle provider).
    implementation(libs.smbj)
    implementation(libs.tvprovider)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.hls)
    // Software decoders for AC3/E-AC3/DTS/TrueHD: the target TV has no hardware decoder for them.
    implementation(libs.media3.ffmpeg)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    // XmlPullParser implementation for JVM tests (Android provides its own on the device).
    testImplementation(libs.kxml2)
}
