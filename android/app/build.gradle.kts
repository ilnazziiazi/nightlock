import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release key lives outside the repo: ~/.nightlock/release.jks, described by android/keystore.properties.
val keystore = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f ->
    Properties().apply { f.inputStream().use { load(it) } }
}

android {
    namespace = "io.github.ilnazziiazi.nightlock"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.ilnazziiazi.nightlock"
        minSdk = 33
        targetSdk = 36
        versionCode = 2
        versionName = "0.2"
    }
    keystore?.let { p ->
        signingConfigs.create("release") {
            storeFile = file(p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
        }
    }
    buildTypes {
        // Both build types share the release key, so debug (run-as for testing) and release
        // builds can be installed over each other.
        release { isMinifyEnabled = false; signingConfig = signingConfigs.findByName("release") }
        debug { signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
