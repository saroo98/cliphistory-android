import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { id("com.android.application") }

android {
    namespace = "app.cliphistory"
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "app.cliphistory"
        minSdk = 34
        targetSdk = 37
        versionCode = 8
        versionName = "1.3.4"
        manifestPlaceholders["appLabel"] = "@string/app_name"
        manifestPlaceholders["tileLabel"] = "@string/tile_name"
        testInstrumentationRunner = "app.cliphistory.UiSmokeInstrumentation"
    }
    buildFeatures { aidl = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    val signingFile = rootProject.file(".signing/cliphistory.jks")
    val passwordFile = rootProject.file(".signing/password.txt")
    if (!providers.gradleProperty("unsignedRelease").isPresent && signingFile.exists() && passwordFile.exists()) {
        signingConfigs.create("personal") {
            storeFile = signingFile
            storePassword = passwordFile.readText().trim()
            keyAlias = "cliphistory"
            keyPassword = passwordFile.readText().trim()
        }
    }
    buildTypes {
        getByName("debug") {
            if (providers.gradleProperty("validationBuild").isPresent) {
                applicationIdSuffix = ".validation"
                versionNameSuffix = "-validation"
                manifestPlaceholders["appLabel"] = "ClipHistory validation"
                manifestPlaceholders["tileLabel"] = "Clipboard validation"
            }
        }
        getByName("release") {
            isMinifyEnabled = false // UserService entry point is reflectively loaded by Shizuku.
            isDebuggable = false
            if (signingConfigs.findByName("personal") != null) signingConfig = signingConfigs.getByName("personal")
        }
    }
    lint { abortOnError = true; checkReleaseBuilds = true }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    testImplementation("junit:junit:4.13.2")
}
