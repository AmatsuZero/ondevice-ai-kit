plugins { alias(libs.plugins.android.application) }
android {
    namespace = "io.ondeviceai.example.multimodal"
    compileSdk = 36
    defaultConfig { applicationId = "io.ondeviceai.example.multimodal"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "0.1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    if (providers.gradleProperty("usePublishedSdk").orNull == "true") {
        implementation("io.ondeviceai:sdk-core:0.1.0-SNAPSHOT")
        implementation("io.ondeviceai:multimodal:0.1.0-SNAPSHOT")
    } else {
        implementation(project(":android:sdk-core"))
        implementation(project(":android:multimodal"))
    }
}
