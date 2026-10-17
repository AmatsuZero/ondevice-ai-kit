plugins { alias(libs.plugins.android.library) }

android {
    namespace = "io.ondeviceai.speechconsumer"
    compileSdk = 36

    defaultConfig { minSdk = 26 }

    flavorDimensions += "backend"
    productFlavors {
        create("contract") { dimension = "backend" }
        create("systemTts") { dimension = "backend" }
        create("systemAsr") { dimension = "backend" }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val usePublishedSdk = providers.gradleProperty("usePublishedSdk").orNull == "true"
val sdkVersion = "0.1.0-SNAPSHOT"

dependencies {
    add("systemTtsImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    add("systemAsrImplementation", "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    if (usePublishedSdk) {
        add("contractImplementation", "io.ondeviceai:speech:$sdkVersion")
        add("systemTtsImplementation", "io.ondeviceai:speech:$sdkVersion")
        add("systemTtsImplementation", "io.ondeviceai:backend-system-tts:$sdkVersion")
        add("systemAsrImplementation", "io.ondeviceai:speech:$sdkVersion")
        add("systemAsrImplementation", "io.ondeviceai:backend-system-asr:$sdkVersion")
    } else {
        add("contractImplementation", project(":android:speech"))
        add("systemTtsImplementation", project(":android:speech"))
        add("systemTtsImplementation", project(":android:backend-system-tts"))
        add("systemAsrImplementation", project(":android:speech"))
        add("systemAsrImplementation", project(":android:backend-system-asr"))
    }
}
