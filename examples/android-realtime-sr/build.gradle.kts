plugins {
    alias(libs.plugins.android.application)
}

val enableNative = file("src/main/cpp/CMakeLists.txt").exists()
val enableGoogleSr = providers.gradleProperty("enableGoogleSr")
    .map(String::toBoolean)
    .getOrElse(false)

android {
    namespace = "io.ondeviceai.realtimesr"
    compileSdk = 36

    if (enableNative) {
        ndkVersion = "27.0.12077973"
    }

    defaultConfig {
        testInstrumentationRunner = "io.ondeviceai.realtimesr.PlaybackUiProbe"
        applicationId = if (enableGoogleSr) {
            "io.ondeviceai.realtimesr.demo.google"
        } else {
            "io.ondeviceai.realtimesr.demo"
        }
        minSdk = if (enableGoogleSr) 30 else 26
        targetSdk = 36
    }

    sourceSets {
        getByName("debug") {
            kotlin.srcDir(if (enableGoogleSr) "src/googleSr/kotlin" else "src/noGoogleSr/kotlin")
            if (enableGoogleSr) {
                kotlin.srcDir("src/googleSrDebug/kotlin")
                manifest.srcFile("src/googleSrDebug/AndroidManifest.xml")
            }
        }
        getByName("release") {
            kotlin.srcDir("src/noGoogleSr/kotlin")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    if (enableNative) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }
}

dependencies {
    implementation(project(":kmp-binding"))
    if (enableGoogleSr) debugImplementation("com.google.android.gms:play-services-media-effect-enhancement:16.0.0-beta09")
    testImplementation("junit:junit:4.13.2")
}
