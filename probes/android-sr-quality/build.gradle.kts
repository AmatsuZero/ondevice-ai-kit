plugins { alias(libs.plugins.android.application) }

val generated = providers.gradleProperty("srQualityGeneratedDir").orNull
    ?: error("Run probes/android-sr-quality/prepare.py and set srQualityGeneratedDir")
android {
    namespace = "io.ondeviceai.probe.srquality"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.ondeviceai.probe.srquality"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    sourceSets.getByName("main").kotlin.srcDir("$generated/kotlin")
    sourceSets.getByName("main").assets.srcDir("$generated/assets")
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation(project(":android:video"))
    implementation("com.google.ai.edge.litert:litert:1.4.2")
}
